package site.tiedan.core

import kotlinx.coroutines.sync.Mutex
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.data.Database
import site.tiedan.data.ExtraData
import site.tiedan.data.dao.BucketDao
import site.tiedan.data.dao.StorageDao
import site.tiedan.format.JsonProcessor.BucketData
import site.tiedan.utils.Security
import java.sql.Connection

/**
 * # 数据存储管理器
 * - 获取数据 [getGlobalData] [getStorageData] [getBucketData]
 * - 保存数据 [savePastebinStorage] [saveBucketData]
 *
 * 数据存放在 SQLite（[Database]），每次写入即刻落盘。
 *
 * @author tiedanGH
 */
object StorageManager {

    /** QQ 平台标识，QQ 平台 `platformID` 无前缀 */
    private const val PLATFORM_QQ = "qq"

    private val StorageLock = Mutex()

    fun isLocked(): Boolean = StorageLock.isLocked
    suspend fun lock() = StorageLock.lock()
    fun unlock() = StorageLock.unlock()

    /**
     * 获取 global 存储数据
     */
    fun getGlobalData(name: String): String =
        Database.read { StorageDao.getGlobal(it, name) ?: "" }

    /**
     * 获取 storage 存储数据
     */
    fun getStorageData(name: String, userID: Long, platform: String): String =
        Database.read { StorageDao.getUser(it, name, platform, userID) ?: "" }

    /**
     * 获取 bucket 存储数据
     */
    fun getBucketData(name: String): List<BucketData> = Database.read { conn ->
        BucketDao.linkedIds(conn, name).map { id -> toBucketData(conn, id) }
    }

    /**
     * 将存储库编号转换为存储库数据
     */
    fun bucketIdsToBucketData(ids: List<Long>): List<BucketData> = Database.read { conn ->
        ids.map { id -> toBucketData(conn, id) }
    }

    private fun toBucketData(conn: Connection, id: Long): BucketData {
        val bucket = BucketDao.get(conn, id)
        return BucketData(
            id = id,
            name = bucket?.name,
            content = bucket?.let {
                if (it.encrypt) Security.decrypt(it.content, ExtraData.key) else it.content
            } ?: ""
        )
    }


    /* ==================== 项目存储数据管理 ==================== */

    /**
     * 存在存储数据的项目数量
     */
    fun projectCount(): Int = Database.read { StorageDao.projectCount(it) }

    /**
     * 单个用户的存储数据
     */
    data class UserStorage(val platform: String, val userId: Long, val content: String) {
        /** QQ 为纯数字，其他平台带平台前缀 */
        val platformID: String = if (platform == PLATFORM_QQ) "$userId" else "${platform}_$userId"
    }

    /**
     * 项目的全部存储数据
     * - 不同平台的用户号可能重复，不能压成 `Map<Long, String>`
     */
    data class ProjectStorage(val global: String, val users: List<UserStorage>)

    /**
     * 获取指定项目的全部存储数据（含全部平台）
     * @return 项目不存在时返回 null
     */
    fun getProjectStorage(name: String): ProjectStorage? = Database.read { conn ->
        if (!StorageDao.projectExists(conn, name)) {
            null
        } else {
            ProjectStorage(
                global = StorageDao.getGlobal(conn, name) ?: "",
                users = StorageDao.listProject(conn, name)
                    .filterNot { it.platform == StorageDao.GLOBAL_PLATFORM }
                    .map { UserStorage(it.platform, it.userId, it.content) },
            )
        }
    }

    /**
     * 全部项目的 global 数据总大小
     */
    fun totalGlobalStorageSize(): Long = Database.read { StorageDao.totalGlobalLength(it) }

    /**
     * 全部项目的用户存储数据总大小（含全部平台）
     */
    fun totalUserStorageSize(): Long = Database.read { StorageDao.totalUserLength(it) }

    /**
     * 项目改名时迁移存储数据
     */
    fun renameProjectStorage(from: String, to: String) {
        Database.transaction { StorageDao.renameProject(it, from, to) }
    }

    /**
     * 删除指定项目的全部存储数据
     */
    fun removeProjectStorage(name: String) {
        Database.transaction { StorageDao.removeProject(it, name) }
    }


    /* ==================== 存储库 bucket 管理 ==================== */

    /**
     * 存储库数据类
     */
    data class Bucket(
        val id: Long,
        val name: String,
        val password: String,
        val owner: String,
        val userID: String,
        val projects: List<String>,
        val desc: String,
        val content: String,
        val encrypt: Boolean,
    )

    private fun toBucket(row: BucketDao.BucketRow): Bucket = Bucket(
        id = row.id,
        name = row.name,
        password = row.password,
        owner = row.owner,
        userID = row.userID,
        projects = row.projects,
        desc = row.description,
        content = row.content,
        encrypt = row.encrypt,
    )

    /** 槽位是否存在（空置槽位也算存在） */
    fun bucketSlotExists(id: Long): Boolean = Database.read { BucketDao.exists(it, id) }

    /** 槽位是否为空置状态 */
    fun isBucketEmpty(id: Long): Boolean = Database.read { BucketDao.isEmpty(it, id) }

    /** 获取存储库，空置槽位返回 null */
    fun getBucket(id: Long): Bucket? = Database.read { BucketDao.get(it, id)?.let(::toBucket) }

    /** 全部槽位，空置槽位的值为 null（编号顺序） */
    fun listBucketSlots(): Map<Long, Bucket?> = Database.read { conn ->
        BucketDao.listSlots(conn).mapValues { (_, row) -> row?.let(::toBucket) }
    }

    /** 按名称查找存储库 */
    fun findBucketByName(name: String): Bucket? =
        Database.read { BucketDao.findByName(it, name)?.let(::toBucket) }

    /** 非空存储库数量 */
    fun bucketCount(): Int = Database.read { BucketDao.count(it) }

    /** 全部存储库关联的项目条目总数 */
    fun totalLinkedProjects(): Long = Database.read { BucketDao.totalLinkedProjects(it) }

    /** 全部存储库主存储数据的总大小 */
    fun totalBucketSize(): Long = Database.read { BucketDao.totalContentLength(it) }

    /** 分配下一个可用槽位编号 */
    fun nextFreeBucketId(): Long = Database.read { BucketDao.nextFreeId(it) }

    /** 查询关联了指定项目的全部存储库编号 */
    fun linkedBucketIds(projectName: String): List<Long> =
        Database.read { BucketDao.linkedIds(it, projectName) }

    /** 存储库编号转名称 */
    fun bucketIdToName(id: Long): String? = Database.read { BucketDao.idToName(it, id) }

    /** 获取存储库主存储数据 */
    fun getBucketRawContent(id: Long): String? = Database.read { BucketDao.getRawContent(it, id) }

    /** 直接写入存储库主存储数据 */
    fun setBucketRawContent(id: Long, raw: String) {
        Database.transaction { BucketDao.setRawContent(it, id, raw) }
    }

    /** 创建存储库 */
    fun createBucket(id: Long, name: String, passwordHash: String, owner: String, userID: String) {
        Database.transaction { BucketDao.create(it, id, name, passwordHash, owner, userID) }
    }

    /** 修改存储库名称 */
    fun setBucketName(id: Long, name: String) {
        Database.transaction { BucketDao.setName(it, id, name) }
    }

    /** 修改存储库密码 */
    fun setBucketPassword(id: Long, passwordHash: String) {
        Database.transaction { BucketDao.setPassword(it, id, passwordHash) }
    }

    /** 修改存储库简介 */
    fun setBucketDesc(id: Long, desc: String) {
        Database.transaction { BucketDao.setDescription(it, id, desc) }
    }

    /** 转移存储库所有权 */
    fun setBucketOwner(id: Long, owner: String, userID: String) {
        Database.transaction { conn ->
            BucketDao.setOwner(conn, id, owner)
            BucketDao.setUserID(conn, id, userID)
        }
    }

    /** 更新存储库关联的项目列表 */
    fun setBucketProjects(id: Long, projects: List<String>) {
        Database.transaction { BucketDao.setProjects(it, id, projects) }
    }

    /** 启用数据加密：加密主存储数据与全部备份 */
    fun enableBucketEncryption(id: Long) {
        Database.transaction { conn ->
            val bucket = BucketDao.get(conn, id) ?: return@transaction
            BucketDao.setEncrypt(conn, id, true)
            BucketDao.setRawContent(conn, id, Security.encrypt(bucket.content, ExtraData.key))
            BucketDao.getBackups(conn, id).forEachIndexed { slot, backup ->
                if (backup != null) {
                    BucketDao.setBackup(conn, id, slot, backup.copy(
                        content = Security.encrypt(backup.content, ExtraData.key)
                    ))
                }
            }
        }
    }

    /** 删除存储库，保留空置槽位 */
    fun deleteBucket(id: Long) {
        Database.transaction { BucketDao.delete(it, id) }
    }

    /** 将项目从全部存储库的关联列表中移除 */
    fun removeProjectFromBuckets(name: String) {
        Database.transaction { BucketDao.removeProjectFromAll(it, name) }
    }

    /**
     * 存储库备份
     */
    data class Backup(val name: String, val time: Long, val content: String)

    private fun toBackup(row: BucketDao.BackupRow) = Backup(row.name, row.time, row.content)

    /** 获取全部备份槽位（长度为 3，空槽位为 null） */
    fun getBackups(id: Long): List<Backup?> =
        Database.read { conn -> BucketDao.getBackups(conn, id).map { it?.let(::toBackup) } }

    /** 获取指定槽位的备份 */
    fun getBackup(id: Long, slot: Int): Backup? =
        Database.read { BucketDao.getBackup(it, id, slot)?.let(::toBackup) }

    /** 写入指定槽位的备份，null 表示删除 */
    fun setBackup(id: Long, slot: Int, backup: Backup?) {
        Database.transaction { conn ->
            BucketDao.setBackup(conn, id, slot, backup?.let {
                BucketDao.BackupRow(it.name, it.time, it.content)
            })
        }
    }

    /** 全部存储库的备份数据总大小 */
    fun totalBackupSize(): Long = Database.read { BucketDao.totalBackupLength(it) }


    /**
     * 保存 storage、global、bucket 存储数据
     */
    fun savePastebinStorage(
        name: String,
        userID: Long,
        platform: String,
        global: String?,
        storage: String?,
        bucket: List<BucketData>?
    ): String? {
        if (global == null && storage == null && bucket == null) return null

        val platformInfo = if (platform == PLATFORM_QQ) "" else "($platform)"

        logger.info (
            "保存存储数据: global{${global?.length}} storage$platformInfo{${storage?.length}} " +
            "bucket{${bucket?.joinToString(" ") { "[${it.id}](${it.content?.length})" }}}"
        )

        return Database.transaction { conn ->
            // global
            when {
                global != null -> StorageDao.setGlobal(conn, name, global)
                // 项目必须存在 global 数据
                StorageDao.getGlobal(conn, name) == null -> StorageDao.setGlobal(conn, name, "")
            }

            // storage
            storage?.let {
                if (it.isEmpty()) StorageDao.removeUser(conn, name, platform, userID)
                else StorageDao.setUser(conn, name, platform, userID, it)
            }

            saveBucketData(conn, name, bucket)
        }
    }

    /**
     * 保存 bucket 数据
     */
    private fun saveBucketData(conn: Connection, name: String, bucket: List<BucketData>?): String? {
        if (bucket == null) return null

        val bucketIds = BucketDao.linkedIds(conn, name)
        val seenBucketIDs = mutableSetOf<Long>()
        val ret = StringBuilder()

        bucket.forEachIndexed { index, data ->
            val outputId = data.id
            when {
                outputId == null ->
                    ret.append("\n[(${index + 1})无效ID] 未指定目标存储库ID")

                outputId !in bucketIds ->
                    ret.append("\n[(${index + 1})拒绝访问] 当前项目未关联存储库 $outputId")

                outputId in seenBucketIDs ->
                    ret.append("\n[(${index + 1})重复写入] 检测到对存储库 $outputId 的重复保存，单次输出仅支持写入同一存储库一次")

                data.content != null -> {
                    val content = if (BucketDao.get(conn, outputId)?.encrypt == true) {
                        Security.encrypt(data.content, ExtraData.key)
                    } else {
                        data.content
                    }
                    BucketDao.setRawContent(conn, outputId, content)
                    seenBucketIDs.add(outputId)
                }
            }
        }

        return ret.takeIf { it.isNotEmpty() }?.toString()
    }
}
