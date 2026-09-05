package site.tiedan.core

import kotlinx.coroutines.sync.Mutex
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinBucket
import site.tiedan.data.PastebinPlatformStorage
import site.tiedan.data.PastebinStorage
import site.tiedan.format.JsonProcessor.BucketData
import site.tiedan.utils.Security
import site.tiedan.utils.YamlSafeValue
import kotlin.collections.set

/**
 * # 数据存储管理器
 * - 获取数据 [getGlobalData] [getStorageData] [getBucketData]
 * - 保存数据 [savePastebinStorage] [saveBucketData]
 *
 * @author tiedanGH
 */
object StorageManager {

    private val StorageLock = Mutex()

    fun isLocked(): Boolean = StorageLock.isLocked
    suspend fun lock() = StorageLock.lock()
    fun unlock() = StorageLock.unlock()

    /**
     * 获取 global 存储数据
     */
    fun getGlobalData(name: String): String {
        return YamlSafeValue.unescape(PastebinStorage.storage[name]?.get(0L) ?: "")
    }

    /**
     * 获取 storage 存储数据
     */
    fun getStorageData(name: String, userID: Long, platform: String): String {
        return YamlSafeValue.unescape(when (platform) {
            "qq" -> PastebinStorage.storage[name]?.get(userID) ?: ""

            else -> PastebinPlatformStorage.storage[platform]?.get(name)?.get(userID) ?: ""
        })
    }

    /**
     * 获取 bucket 存储数据
     */
    fun getBucketData(name: String): List<BucketData> {
        return bucketIdsToBucketData(linkedBucketIds(name))
    }

    /**
     * 将存储库编号转换为存储库数据
     */
    fun bucketIdsToBucketData(ids: List<Long>): List<BucketData> = ids.map { id ->
        val bucket = getBucket(id)
        BucketData(
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
    fun projectCount(): Int = PastebinStorage.storage.size

    /**
     * 获取指定项目的全部存储数据（key 为 0 时表示 global）
     * @return 项目不存在时返回 null
     */
    fun getProjectStorage(name: String): Map<Long, String>? =
        PastebinStorage.storage[name]?.mapValues { YamlSafeValue.unescape(it.value) }

    /**
     * 获取全部项目的存储数据
     */
    fun allProjectStorage(): Map<String, Map<Long, String>> =
        PastebinStorage.storage.mapValues { (_, data) -> data.mapValues { YamlSafeValue.unescape(it.value) } }

    /**
     * 项目改名时迁移存储数据
     */
    fun renameProjectStorage(from: String, to: String) {
        PastebinStorage.storage.remove(from)?.let { PastebinStorage.storage[to] = it }
        for ((_, nameMap) in PastebinPlatformStorage.storage) {
            nameMap.remove(from)?.let { nameMap[to] = it }
        }
    }

    /**
     * 删除指定项目的全部存储数据
     */
    fun removeProjectStorage(name: String) {
        PastebinStorage.storage.remove(name)
        for ((_, nameMap) in PastebinPlatformStorage.storage) {
            nameMap.remove(name)
        }
    }

    /**
     * 持久化全部存储数据
     */
    fun saveStorage() {
        PastebinStorage.save()
        PastebinPlatformStorage.save()
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

    private fun toBucket(id: Long, data: Map<String, String>): Bucket = Bucket(
        id = id,
        name = data["name"] ?: "",
        password = data["password"] ?: "",
        owner = data["owner"] ?: "",
        userID = data["userID"] ?: "",
        projects = data["projects"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
        desc = data["desc"] ?: "",
        content = YamlSafeValue.unescape(data["content"] ?: ""),
        encrypt = data["encrypt"] == "true",
    )

    /** 槽位是否存在（空置槽位也算存在） */
    fun bucketSlotExists(id: Long): Boolean = PastebinBucket.bucket.contains(id)

    /** 槽位是否为空置状态 */
    fun isBucketEmpty(id: Long): Boolean = PastebinBucket.bucket[id]?.isEmpty() != false

    /** 获取存储库，空置槽位返回 null */
    fun getBucket(id: Long): Bucket? =
        PastebinBucket.bucket[id]?.takeIf { it.isNotEmpty() }?.let { toBucket(id, it) }

    /** 全部槽位，空置槽位的值为 null（编号顺序） */
    fun listBucketSlots(): Map<Long, Bucket?> =
        PastebinBucket.bucket.mapValues { (id, data) -> if (data.isEmpty()) null else toBucket(id, data) }

    /** 按名称查找存储库 */
    fun findBucketByName(name: String): Bucket? =
        PastebinBucket.bucket.entries.firstOrNull { it.value["name"] == name }?.let { toBucket(it.key, it.value) }

    /** 非空存储库数量 */
    fun bucketCount(): Int = PastebinBucket.bucket.values.count { it.isNotEmpty() }

    /** 分配下一个可用槽位编号 */
    fun nextFreeBucketId(): Long = generateSequence(1L) { it + 1 }.first { isBucketEmpty(it) }

    /** 查询关联了指定项目的全部存储库编号 */
    fun linkedBucketIds(projectName: String): List<Long> =
        PastebinBucket.bucket
            .filter { (_, data) -> data["projects"]?.split(" ")?.any { it == projectName } == true }
            .keys.toList()

    /** 存储库编号转名称 */
    fun bucketIdToName(id: Long): String? = PastebinBucket.bucket[id]?.get("name")

    /** 获取存储库主存储数据 */
    fun getBucketRawContent(id: Long): String? = PastebinBucket.bucket[id]?.get("content")

    /** 直接写入存储库主存储数据 */
    fun setBucketRawContent(id: Long, raw: String) {
        PastebinBucket.bucket[id]?.set("content", raw)
    }

    /** 创建存储库 */
    fun createBucket(id: Long, name: String, passwordHash: String, owner: String, userID: String) {
        PastebinBucket.bucket[id] = mutableMapOf(
            "name" to name,
            "password" to passwordHash,
            "owner" to owner,
            "userID" to userID,
            "projects" to "",
            "desc" to "",
            "content" to "",
        )
        PastebinBucket.backups[id] = mutableListOf(null, null, null)
    }

    /** 修改存储库的单个属性 */
    fun setBucketField(id: Long, field: String, value: String) {
        PastebinBucket.bucket[id]?.set(field, value)
    }

    /** 更新存储库关联的项目列表 */
    fun setBucketProjects(id: Long, projects: List<String>) {
        PastebinBucket.bucket[id]?.set("projects", projects.joinToString(" "))
    }

    /** 启用数据加密：加密主存储数据与全部备份 */
    fun enableBucketEncryption(id: Long) {
        val data = PastebinBucket.bucket[id] ?: return
        data["encrypt"] = "true"
        data["content"] = Security.encrypt(YamlSafeValue.unescape(data["content"] ?: ""), ExtraData.key)
        PastebinBucket.backups[id]?.forEach { backup ->
            backup?.content = Security.encrypt(backup.content, ExtraData.key)
        }
    }

    /** 删除存储库，保留空置槽位 */
    fun deleteBucket(id: Long) {
        PastebinBucket.bucket[id]?.clear()
        PastebinBucket.backups[id]?.clear()
    }

    /** 将项目从全部存储库的关联列表中移除 */
    fun removeProjectFromBuckets(name: String) {
        for ((_, data) in PastebinBucket.bucket) {
            val projects = data["projects"] ?: continue
            data["projects"] = projects.split(" ").filter { it.isNotBlank() && it != name }.joinToString(" ")
        }
    }

    /**
     * 存储库备份
     */
    data class Backup(val name: String, val time: Long, val content: String)

    private fun toBackup(info: PastebinBucket.BackupInfo) = Backup(info.name, info.time, info.content)

    /** 获取全部备份槽位（长度为 3，空槽位为 null） */
    fun getBackups(id: Long): List<Backup?> =
        PastebinBucket.backups[id].orEmpty().map { it?.let(::toBackup) }

    /** 获取指定槽位的备份 */
    fun getBackup(id: Long, slot: Int): Backup? =
        PastebinBucket.backups[id]?.getOrNull(slot)?.let(::toBackup)

    /** 写入指定槽位的备份，null 表示删除 */
    fun setBackup(id: Long, slot: Int, backup: Backup?) {
        PastebinBucket.backups[id]?.set(slot, backup?.let {
            PastebinBucket.BackupInfo(it.name, it.time, it.content)
        })
    }

    /** 全部存储库的备份数据总大小 */
    fun totalBackupSize(): Int =
        PastebinBucket.backups.values.flatten().filterNotNull().sumOf { it.content.length }

    /** 持久化存储库数据 */
    fun saveBucket() {
        PastebinBucket.save()
    }


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

        val isQQ = platform == "qq"
        val platformInfo = if (isQQ) "" else "($platform)"

        logger.info (
            "保存存储数据: global{${global?.length}} storage$platformInfo{${storage?.length}} " +
            "bucket{${bucket?.joinToString(" ") { "[${it.id}](${it.content?.length})" }}}"
        )

        // global
        val globalMap = (PastebinStorage.storage[name] ?: mutableMapOf(0L to "")).toMutableMap()
        global?.let { globalMap[0L] = YamlSafeValue.escape(it) }

        if (isQQ) {
            // QQ - storage
            storage?.let {
                if (it.isEmpty()) globalMap.remove(userID) else globalMap[userID] = YamlSafeValue.escape(it)
            }
            PastebinStorage.storage[name] = globalMap

            PastebinStorage.save()
        } else {
            // 其他平台
            PastebinStorage.storage[name] = globalMap
            PastebinStorage.save()

            // storage
            val platformMap = (PastebinPlatformStorage.storage[platform] ?: mutableMapOf()).toMutableMap()
            val nameMap = (platformMap[name] ?: mutableMapOf()).toMutableMap()

            storage?.let {
                if (it.isEmpty()) nameMap.remove(userID) else nameMap[userID] = YamlSafeValue.escape(it)
            }

            platformMap[name] = nameMap
            PastebinPlatformStorage.storage[platform] = platformMap

            PastebinPlatformStorage.save()
        }

        return saveBucketData(name, bucket)
    }

    /**
     * 保存 bucket 数据
     */
    private fun saveBucketData(name: String, bucket: List<BucketData>?): String? {
        if (bucket == null) return null

        val bucketIds = linkedBucketIds(name)
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
                    val content = if (getBucket(outputId)?.encrypt == true) {
                        Security.encrypt(data.content, ExtraData.key)
                    } else {
                        data.content
                    }
                    setBucketRawContent(outputId, YamlSafeValue.escape(content))
                    seenBucketIDs.add(outputId)
                }
            }
        }

        saveBucket()
        return ret.takeIf { it.isNotEmpty() }?.toString()
    }
}
