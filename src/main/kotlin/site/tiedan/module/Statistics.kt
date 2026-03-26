package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.imageFolder
import site.tiedan.MiraiCompilerFramework.roundTo2
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.command.CommandBucket.projectsCount
import site.tiedan.data.CodeCache
import site.tiedan.data.ExtraData
import site.tiedan.data.ImageData
import site.tiedan.data.PastebinBucket
import site.tiedan.data.PastebinData
import site.tiedan.data.PastebinStorage
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

/**
 * # 数据统计
 * - 获取全部统计数据 [getAllStatistics]
 * - 获取项目统计数据 [getStatistic]
 * - 汇总语言比例和热门项目统计 [summarizeStatistics]
 *
 * @author tiedanGH
 */
object Statistics {

    /**
     * 统计运行次数和热度
     */
    fun countRun(name: String) {
        createIfNotExist(name)
        val current = ExtraData.statistics[name]?.get("run") ?: 0.0
        val currentScore = ExtraData.statistics[name]?.get("score") ?: 0.0
        ExtraData.statistics[name]?.set("run", current + 1)
        ExtraData.statistics[name]?.set("score", (currentScore + 1).roundTo2())
        ExtraData.save()
    }

    /**
     * 统计调用 markdown 次数和用时
     */
    fun countMarkdown(name: String, mdTime: Double) {
        createIfNotExist(name)
        val current = ExtraData.statistics[name]?.get("markdown") ?: 0.0
        val currentTime = ExtraData.statistics[name]?.get("mdTime") ?: 0.0
        ExtraData.statistics[name]?.set("markdown", current + 1)
        ExtraData.statistics[name]?.set("mdTime", (currentTime + mdTime).roundTo2())
        ExtraData.save()
    }

    /**
     * 统计下载次数和用时
     */
    fun countDownload(name: String, dlTime: Double) {
        createIfNotExist(name)
        val current = ExtraData.statistics[name]?.get("download") ?: 0.0
        val currentTime = ExtraData.statistics[name]?.get("dlTime") ?: 0.0
        ExtraData.statistics[name]?.set("download", current + 1)
        ExtraData.statistics[name]?.set("dlTime", (currentTime + dlTime).roundTo2())
        ExtraData.save()
    }

    /**
     * 获取全部统计数据
     */
    fun getAllStatistics(): String {
        var totalRun = 0L
        var totalMarkdown = 0L
        var totalMdTime = 0.0
        var totalDownload = 0L
        var totalDlTime = 0.0
        for (entry in ExtraData.statistics.values) {
            totalRun += entry["run"]?.toLong() ?: 0L
            totalMarkdown += entry["markdown"]?.toLong() ?: 0L
            totalMdTime += entry["mdTime"] ?: 0.0
            totalDownload += entry["download"]?.toLong() ?: 0L
            totalDlTime += entry["dlTime"] ?: 0.0
        }

        var totalGlobalStorage = 0L
        var totalUserStorage = 0L
        for ((_, value) in PastebinStorage.storage) {
            totalGlobalStorage += value[0]?.length?.toLong() ?: 0L
            totalUserStorage += getUserStorageSize(value)
        }
        val totalBucketNum = PastebinBucket.bucket.values.count { it.isNotEmpty() }
        var totalLinkedProjects = 0L
        var totalBucketSize = 0L
        for ((key, value) in PastebinBucket.bucket) {
            totalLinkedProjects += projectsCount(key)
            totalBucketSize += value["content"]?.length?.toLong() ?: 0L
        }
        val totalBackupSize =
            PastebinBucket.backups.values
                .flatten()
                .filterNotNull()
                .sumOf { it.content.length }
        val imageCount = ImageData.images.size
        val totalSize = getFolderSize(File(imageFolder))
        var totalCodeCache = 0L
        for ((_, value) in CodeCache.CodeCache) {
            totalCodeCache += value.length
        }

        return buildString {
            appendLine("📈 总执行次数：$totalRun")
            appendLine()
            if (totalMarkdown > 0) {
                appendLine("·调用markdown：$totalMarkdown")
                val avg = totalMdTime / totalMarkdown
                appendLine(" ⏱️ 总用时：${formatTime(totalMdTime)}")
                appendLine(" ⚡ 平均用时：${"%.2f".format(avg)}秒")
            }
            if (totalDownload > 0) {
                appendLine("·调用image下载：$totalDownload")
                val avg = totalDlTime / totalDownload
                appendLine(" ⏱️ 总用时：${formatTime(totalDlTime)}")
                appendLine(" ⚡ 平均用时：${"%.2f".format(avg)}秒")
            }
            appendLine("💾 存储总数：${PastebinStorage.storage.size}")
            appendLine("  - 全局总大小：$totalGlobalStorage")
            appendLine("  - 用户总大小：$totalUserStorage")
            appendLine("🗄 存储库总数：$totalBucketNum")
            appendLine("  - 关联项目数：$totalLinkedProjects")
            appendLine("  - 存储总大小：$totalBucketSize")
            appendLine("  - 备份总大小：$totalBackupSize")
            appendLine("🖼️ 图片总数：$imageCount")
            appendLine("  - 占用空间：${formatSize(totalSize)}")
            appendLine("📦 代码缓存总数：${CodeCache.CodeCache.size}")
            appendLine("  - 缓存总大小：$totalCodeCache")
        }
    }

    /**
     * 获取项目统计数据
     */
    fun getStatistic(name: String): String {
        val stat = ExtraData.statistics[name]
        val run = stat?.get("run")?.toLong() ?: 0L
        val score = stat?.get("score") ?: 0.0
        val markdown = stat?.get("markdown")?.toLong()
        val mdTime = stat?.get("mdTime")
        val download = stat?.get("download")?.toLong()
        val dlTime = stat?.get("dlTime")
        val storage = PastebinStorage.storage[name]

        return buildString {
            appendLine("📈 总执行次数：$run")
            appendLine("🔥 热度指数：${"%.2f".format(score)}")
            val cache = CodeCache.CodeCache[name]
            if (cache != null) {
                val length = cache.replace("\r\n", "\n").length
                val emoji = if (length < 800_000) "📄" else "⚠️"
                appendLine("$emoji 代码字符数：$length")
            }
            if (markdown != null || download != null) appendLine()
            if (markdown != null) {
                appendLine("·调用markdown：$markdown")
                if (mdTime != null && markdown > 0) {
                    val avg = mdTime / markdown
                    appendLine(" ⏱️ 总用时：${formatTime(mdTime)}")
                    appendLine(" ⚡ 平均用时：${"%.2f".format(avg)}秒")
                }
            }
            if (download != null) {
                appendLine("·调用image下载：$download")
                if (dlTime != null && download > 0) {
                    val avg = dlTime / download
                    appendLine(" ⏱️ 总用时：${formatTime(dlTime)}")
                    appendLine(" ⚡ 平均用时：${"%.2f".format(avg)}秒")
                }
            }
            if (storage != null) {
                var lengthTotal = storage[0]?.length ?: 0
                appendLine()
                appendLine("·全局存储大小：${storage[0]?.length}")
                appendLine("·用户存储数量：${storage.size - 1}")
                if (storage.size > 1) {
                    val userTotal = getUserStorageSize(storage)
                    val avg = userTotal.toDouble() / (storage.size - 1)
                    lengthTotal += avg.toInt()
                    appendLine("·用户存储大小：$userTotal")
                    appendLine("·用户存储平均：${"%.2f".format(avg)}")
                }
                if (lengthTotal >= 800_000) {
                    appendLine("⚠️ 存储过大警告：单次存储调用接近最大输出限制，可能影响程序执行")
                }
            }
        }
    }

    /**
     * 汇总语言比例和热门项目统计
     */
    fun summarizeStatistics(userID: Long?): String {
        val filtered = if (userID == null) {
            PastebinData.pastebin
        } else {
            PastebinData.pastebin.filterValues { it["userID"] == userID.toString() }
        }
        val projectCount = filtered.size

        val collabCount = if (userID == null) {
            null
        } else {
            PastebinData.pastebin.values.count { map ->
                val raw = map["collaborators"] ?: return@count false
                raw.split(",").mapNotNull { it.trim().toLongOrNull() }.contains(userID)
            }
        }

        if (projectCount == 0 && (collabCount == null || collabCount == 0)) {
            return "🌱 未上传过代码项目"
        }

        val langStats = if (projectCount == 0) {
            ""
        } else {
            val lang = filtered.values.mapNotNull { it["language"]?.lowercase() }
            val langCounts: Map<String, Int> = lang.groupingBy { it }.eachCount()
            langCounts.entries
                .sortedByDescending { it.value }
                .joinToString(separator = "\n") { (lang, cnt) ->
                    val percent = cnt.toDouble() / projectCount * 100
                    val formatted = String.format("%.2f", percent)
                    " 🔸 $lang: ${formatted}%"
                }
        }

        val top10Project = if (projectCount == 0) {
            ""
        } else {
            val languageMap: Map<String, String> = filtered
                .mapNotNull { (key, valueMap) ->
                    valueMap["language"]?.lowercase()?.let { language ->
                        key to language
                    }
                }.toMap()
            languageMap.entries
                .sortedByDescending { (key, _) ->
                    ExtraData.statistics[key]?.get("score") ?: 0.0
                }
                .take(10)
                .joinToString(separator = "、") { (key, language) ->
                    val info = if (userID != null) "（$language）" else ""
                    "$key$info"
                }
        }

        return buildString {
            appendLine("📁 项目总数：$projectCount")
            if (collabCount != null && collabCount > 0) {
                append("🤝 协作项目：$collabCount")
                if (projectCount > 0) appendLine()  // 除非有项目统计，否则末尾不换行
            }
            if (projectCount > 0) {
                appendLine(langStats)
                appendLine()
                append("🔥 近期热门项目：$top10Project")
            }
        }
    }

    fun imageStatistics(userID: Long): String {
        val imageCount = ImageData.images.values.count { it["userID"] == userID.toString() }
        return if (imageCount > 0) "🖼️ 上传图片：$imageCount\n" else ""
    }

    private fun getFolderSize(folder: File?): Long {
        if (folder == null || !folder.exists()) return 0L
        var size = 0L
        val files = folder.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isFile) f.length() else getFolderSize(f)
        }
        return size
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format("%.2f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatTime(time: Double): String {
        val hours = (time / 3600).toLong()
        val minutes = ((time % 3600) / 60).toLong()
        val seconds = (time % 60).roundTo2()
        return when {
            hours > 0 -> "${hours}小时${minutes}分${seconds}秒"
            minutes > 0 -> "${minutes}分${seconds}秒"
            else -> "${seconds}秒"
        }
    }

    private fun getUserStorageSize(storage: MutableMap<Long, String>): Long {
        var userTotal = 0L
        for ((key, value) in storage.entries) {
            if (key != 0L) {
                userTotal += value.length
            }
        }
        return userTotal
    }

    private fun createIfNotExist(name: String) {
        if (!ExtraData.statistics.containsKey(name)) {
            ExtraData.statistics[name] = mutableMapOf()
        }
    }
}