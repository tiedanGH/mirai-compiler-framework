package site.tiedan.core

import kotlinx.coroutines.sync.Mutex
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.command.CommandBucket.bucketIdsToBucketData
import site.tiedan.command.CommandBucket.linkedBucketID
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinBucket
import site.tiedan.data.PastebinPlatformStorage
import site.tiedan.data.PastebinStorage
import site.tiedan.format.JsonProcessor.BucketData
import site.tiedan.utils.Security
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
        return PastebinStorage.storage[name]?.get(0L) ?: ""
    }

    /**
     * 获取 storage 存储数据
     */
    fun getStorageData(name: String, userID: Long, platform: String): String {
        return when (platform) {
            "qq" -> PastebinStorage.storage[name]?.get(userID) ?: ""

            else -> PastebinPlatformStorage.storage[platform]?.get(name)?.get(userID) ?: ""
        }
    }

    /**
     * 获取 bucket 存储数据
     */
    fun getBucketData(name: String): List<BucketData> {
        return bucketIdsToBucketData(linkedBucketID(name))
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
        global?.let { globalMap[0L] = it }

        if (isQQ) {
            // QQ
            PastebinStorage.storage[name] = globalMap
            // storage
            storage?.let {
                if (it.isEmpty()) globalMap.remove(userID) else globalMap[userID] = it
            }
            PastebinStorage.save()
        } else {
            // 其他平台
            PastebinStorage.storage[name] = globalMap
            PastebinStorage.save()

            // storage
            val platformMap = (PastebinPlatformStorage.storage[platform] ?: mutableMapOf()).toMutableMap()
            val nameMap = (platformMap[name] ?: mutableMapOf()).toMutableMap()

            storage?.let {
                if (it.isEmpty()) nameMap.remove(userID) else nameMap[userID] = it
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

        val linkedBucketIDs = linkedBucketID(name)
        val seenBucketIDs = mutableSetOf<Long>()
        val ret = StringBuilder()

        bucket.forEachIndexed { index, data ->
            val bucketId = data.id
            when {
                bucketId == null ->
                    ret.append("\n[(${index + 1})无效ID] 未指定目标存储库ID")

                bucketId !in linkedBucketIDs ->
                    ret.append("\n[(${index + 1})拒绝访问] 当前项目未关联存储库 $bucketId")

                bucketId in seenBucketIDs ->
                    ret.append("\n[(${index + 1})重复写入] 检测到对存储库 $bucketId 的重复保存，单次输出仅支持写入同一存储库一次")

                data.content != null -> {
                    val content = if (PastebinBucket.bucket[bucketId]?.get("encrypt") == "true") {
                        Security.encrypt(data.content, ExtraData.key)
                    } else {
                        data.content
                    }
                    PastebinBucket.bucket[bucketId]?.set("content", content)
                    seenBucketIDs.add(bucketId)
                }
            }
        }

        PastebinBucket.save()
        return ret.takeIf { it.isNotEmpty() }?.toString()
    }
}
