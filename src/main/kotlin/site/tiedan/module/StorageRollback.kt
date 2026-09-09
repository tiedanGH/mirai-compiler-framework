package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.baseDataFolder
import site.tiedan.core.StorageManager
import site.tiedan.data.Database
import site.tiedan.data.dao.StorageDao
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * # 项目存储数据回滚
 * - 数据源是 [BackupManager] 生成的数据库快照（`backup/<日期>/storage.db`、`backup/shutdown/<时间>/storage.db`）
 * - 快照以只读方式打开，回滚只写当前库，任何情况下都不会改动备份本身。
 *
 * @author tiedanGH
 */
object StorageRollback {

    /** 每日定时备份 */
    const val KIND_DAILY = "每日"

    /** 关闭插件时的备份 */
    const val KIND_SHUTDOWN = "关机"

    /** global 条目的展示名 */
    const val GLOBAL_LABEL = "global"

    private val ALL_WORDS = setOf("all", "全部")
    private val GLOBAL_WORDS = setOf("global", "全局")

    private val SHUTDOWN_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")


    /* ==================== 备份快照 ==================== */

    /**
     * 一份可回滚的数据库快照
     * @param index 展示与选择用的编号，1 为最新
     * @param label 展示用时间，取自备份目录名
     */
    data class Snapshot(val index: Int, val label: String, val kind: String, val time: Long, val file: File)

    /**
     * 枚举全部可回滚的数据库快照，最新的在前
     */
    fun snapshots(): List<Snapshot> {
        val backupDir = File(baseDataFolder, "backup")
        val dirs = buildList {
            backupDir.listFiles { file -> file.isDirectory && file.name != "shutdown" }
                ?.forEach { add(it to KIND_DAILY) }
            File(backupDir, "shutdown").listFiles { file -> file.isDirectory }
                ?.forEach { add(it to KIND_SHUTDOWN) }
        }
        return dirs
            .mapNotNull { (dir, kind) ->
                val dbFile = File(dir, Database.FILE_NAME)
                if (!dbFile.isFile) return@mapNotNull null
                Snapshot(
                    index = 0,
                    label = displayLabel(dir.name),
                    kind = kind,
                    time = parseDirTime(dir.name) ?: dbFile.lastModified(),
                    file = dbFile,
                )
            }
            .sortedByDescending { it.time }
            .mapIndexed { i, snapshot -> snapshot.copy(index = i + 1) }
    }

    /** 关机备份目录名还原为可读时间 */
    private fun displayLabel(dirName: String): String =
        if (dirName.contains('_')) dirName.replace('_', ' ').replace('.', ':') else dirName

    private fun parseDirTime(dirName: String): Long? =
        runCatching { LocalDateTime.parse(dirName, SHUTDOWN_DIR_FORMAT) }
            .recoverCatching { LocalDate.parse(dirName).atStartOfDay() }
            .map { it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .getOrNull()

    /** 单份快照中某个项目的概况 */
    data class SnapshotInfo(
        val snapshot: Snapshot,
        val globalLength: Int?,
        val userCount: Int,
        val error: String?,
    )

    /**
     * 逐份快照读取指定项目的概况，用于展示可回滚列表
     */
    fun listFor(project: String): List<SnapshotInfo> = snapshots().map { snapshot ->
        runCatching {
            val entries = readSnapshot(snapshot, project)
            SnapshotInfo(
                snapshot = snapshot,
                globalLength = entries.firstOrNull { it.platform == StorageDao.GLOBAL_PLATFORM }?.content?.length,
                userCount = entries.count { it.platform != StorageDao.GLOBAL_PLATFORM },
                error = null,
            )
        }.getOrElse { SnapshotInfo(snapshot, null, 0, it::class.simpleName ?: "读取失败") }
    }

    private fun readSnapshot(snapshot: Snapshot, project: String): List<StorageDao.Entry> =
        Database.readSnapshot(snapshot.file) { StorageDao.listProject(it, project) }


    /* ==================== 回滚目标 ==================== */

    /**
     * 回滚目标
     * @param platform 为 null 表示项目的全部存储
     */
    data class Target(val label: String, val platform: String?, val userId: Long?) {

        val isAll: Boolean get() = platform == null

        companion object {
            val ALL = Target("全部存储", null, null)
            val GLOBAL = Target("global 数据", StorageDao.GLOBAL_PLATFORM, StorageDao.GLOBAL_USER_ID)

            fun user(platform: String, userId: Long): Target =
                Target(StorageManager.formatPlatformID(platform, userId), platform, userId)
        }
    }


    /* ==================== 对照与回滚 ==================== */

    /**
     * 单条存储记录的回滚对照
     * @param backup 备份中的内容，null 表示备份中不存在此条目
     * @param current 当前内容，null 表示当前不存在此条目
     */
    data class EntryDiff(
        val label: String,
        val platform: String,
        val userId: Long,
        val backup: String?,
        val current: String?,
    ) {
        val changed: Boolean get() = backup != current

        /** 回滚后此条目的变化 */
        val action: String get() = when {
            backup == current -> "不变"
            backup == null -> "删除"
            current == null -> "恢复"
            else -> "覆盖"
        }
    }

    /**
     * 备份与当前数据的原始记录
     */
    data class Loaded(
        val snapshot: Snapshot,
        val project: String,
        val backup: List<StorageDao.Entry>,
        val current: List<StorageDao.Entry>,
    ) {
        /**
         * 解析回滚目标
         * @return null 表示该用户ID在两侧均不存在
         */
        fun resolveTarget(raw: String): Target? = when (raw.lowercase()) {
            in ALL_WORDS -> Target.ALL
            in GLOBAL_WORDS -> Target.GLOBAL
            else -> (backup + current)
                .filterNot { it.platform == StorageDao.GLOBAL_PLATFORM }
                .firstOrNull { StorageManager.formatPlatformID(it.platform, it.userId) == raw }
                ?.let { Target.user(it.platform, it.userId) }
        }

        fun plan(target: Target): Plan = Plan(snapshot, project, target, diff(backup, current, target))
    }

    /**
     * 回滚方案
     */
    data class Plan(
        val snapshot: Snapshot,
        val project: String,
        val target: Target,
        val diffs: List<EntryDiff>,
    ) {
        /** 会发生变化的条目 */
        val changed: List<EntryDiff> get() = diffs.filter { it.changed }

        /** 备份中不存在该项目的任何存储数据 */
        val snapshotEmpty: Boolean get() = diffs.none { it.backup != null }

        /** 备份与当前数据共同的指纹，用于二次确认前后比对 */
        val fingerprint: String = fingerprintOf(diffs)
    }

    /**
     * 读取备份与当前数据
     * - 必须在持有项目存储锁后调用，否则读到数据随时可能被执行进程改写
     */
    fun load(snapshot: Snapshot, project: String): Loaded = Loaded(
        snapshot = snapshot,
        project = project,
        backup = readSnapshot(snapshot, project),
        current = Database.read { StorageDao.listProject(it, project) },
    )

    /**
     * 逐条对照备份与当前数据，仅保留目标范围内的条目
     */
    fun diff(
        backup: List<StorageDao.Entry>,
        current: List<StorageDao.Entry>,
        target: Target,
    ): List<EntryDiff> = (backup + current)
        .map { it.platform to it.userId }
        .distinct()
        .filter { (platform, userId) -> target.isAll || (platform == target.platform && userId == target.userId) }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .map { (platform, userId) ->
            EntryDiff(
                label = if (platform == StorageDao.GLOBAL_PLATFORM) GLOBAL_LABEL
                        else StorageManager.formatPlatformID(platform, userId),
                platform = platform,
                userId = userId,
                backup = backup.firstOrNull { it.platform == platform && it.userId == userId }?.content,
                current = current.firstOrNull { it.platform == platform && it.userId == userId }?.content,
            )
        }

    /**
     * 应用回滚方案，整体成败一致
     * - 必须在持有项目存储锁、且 [matches] 校验通过后调用
     * @return 实际改动的条目数
     */
    fun apply(plan: Plan): Int = Database.transaction { conn ->
        plan.changed.onEach { entry ->
            if (entry.backup == null) {
                StorageDao.removeUser(conn, plan.project, entry.platform, entry.userId)
            } else {
                StorageDao.setUser(conn, plan.project, entry.platform, entry.userId, entry.backup)
            }
        }.size
    }


    /* ==================== 二次确认的过期校验 ==================== */

    private val pendingToken = ConcurrentHashMap<String, String>()

    /** 方案身份：快照来源 + 两侧数据内容 */
    private fun tokenOf(plan: Plan): String = "${plan.snapshot.label}|${plan.fingerprint}"

    /** 记录二次确认前的方案身份 */
    fun remember(userID: String, plan: Plan) {
        pendingToken[userID] = tokenOf(plan)
    }

    /**
     * 二次确认后校验方案是否仍然成立
     * - 排队期间执行进程写入了新数据、备份目录被轮转、同名备份目录被重新覆盖，都会在此被拦下
     */
    fun matches(userID: String, plan: Plan): Boolean = pendingToken[userID] == tokenOf(plan)

    fun forget(userID: String) {
        pendingToken.remove(userID)
    }

    private fun fingerprintOf(diffs: List<EntryDiff>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (entry in diffs.sortedBy { it.label }) {
            digest.update(entry.label.toByteArray())
            for (content in listOf(entry.current, entry.backup)) {
                digest.update(byteArrayOf(if (content == null) 0 else 1))
                content?.let { digest.update(it.toByteArray()) }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(12)
    }


    /* ==================== 输出 ==================== */

    /**
     * 可回滚备份列表
     */
    fun formatSnapshotList(project: String, infos: List<SnapshotInfo>): String = buildString {
        appendLine("·🕰 可回滚备份：$project")
        for (info in infos) {
            append(" ${info.snapshot.index}. ${info.snapshot.label}（${info.snapshot.kind}）　")
            appendLine(
                when {
                    info.error != null -> "❌ 读取失败：${info.error}"
                    info.globalLength == null && info.userCount == 0 -> "⚠️ 无此项目数据"
                    else -> "global ${info.globalLength ?: 0}｜用户 ${info.userCount}"
                }
            )
        }
    }

    /**
     * 备份与当前数据的逐条对照
     */
    fun formatDiff(plan: Plan): String = buildString {
        appendLine(" · 🕰 回滚对照：${plan.project}")
        appendLine("📦 备份：[#${plan.snapshot.index}] ${plan.snapshot.label}（${plan.snapshot.kind}）")
        appendLine("🎯 目标：${plan.target.label}")
        appendLine()
        if (plan.changed.isNotEmpty()) {
            for (entry in plan.changed) {
                appendLine(
                    "- ${entry.label} ${entry.action} " +
                    "${entry.current?.length ?: "无"} → ${entry.backup?.length ?: "无"}"
                )
            }
            appendLine()
        }
        append(
            when {
                plan.diffs.isEmpty() -> "⚠️ 备份与当前均无有效存储"
                plan.changed.isEmpty() -> "✅ 共 ${plan.diffs.size} 项数据，与备份完全一致"
                else -> "共 ${plan.diffs.size} 项，将回滚 ${plan.changed.size} 项\n" +
                        "（数据长度：当前 → 回滚后）"
            }
        )
    }
}
