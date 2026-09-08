package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.imageFolder
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.roundTo2
import site.tiedan.command.CommandPastebin.containsCollaborator
import site.tiedan.core.CodeCacheManager
import site.tiedan.data.Database
import site.tiedan.data.ImageData
import site.tiedan.data.PastebinData
import site.tiedan.data.dao.StatisticsDao
import site.tiedan.core.StorageManager
import site.tiedan.utils.FileSizeUtil.folderSize
import site.tiedan.utils.FileSizeUtil.formatSize
import java.io.File

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
     * 计数失败不得影响主流程
     */
    private fun count(name: String, type: String, block: () -> Unit) {
        runCatching(block).onFailure { logger.warning("项目 $name 的$type 统计写入失败，不影响本次执行", it) }
    }

    /**
     * 统计运行次数和热度
     */
    fun countRun(name: String) = count(name, "运行次数") {
        Database.transaction { StatisticsDao.countRun(it, name) }
    }

    /**
     * 统计调用 markdown 次数和用时
     */
    fun countMarkdown(name: String, mdTime: Double) = count(name, "markdown 调用") {
        Database.transaction { StatisticsDao.countMarkdown(it, name, mdTime) }
    }

    /**
     * 统计下载次数和用时
     */
    fun countDownload(name: String, dlTime: Double) = count(name, "image 下载") {
        Database.transaction { StatisticsDao.countDownload(it, name, dlTime) }
    }

    /** 获取项目的热度指数 */
    fun getScore(name: String): Double = Database.read { StatisticsDao.getScore(it, name) }

    /** 获取项目的运行次数 */
    fun getRun(name: String): Double = Database.read { StatisticsDao.getRun(it, name) }

    /** 一次性获取全部项目的热度指数 */
    fun allScores(): Map<String, Double> = Database.read { StatisticsDao.allScores(it) }

    /** 一次性获取全部项目的运行次数，用途同 [allScores] */
    fun allRuns(): Map<String, Double> = Database.read { StatisticsDao.allRuns(it) }

    /** 项目改名时迁移统计数据 */
    fun renameProject(from: String, to: String) {
        Database.transaction { StatisticsDao.rename(it, from, to) }
    }

    /** 删除项目的统计数据 */
    fun removeProject(name: String) {
        Database.transaction { StatisticsDao.remove(it, name) }
    }

    /** 按比例衰减全部项目的热度指数 */
    fun decayAllScores(factor: Double) {
        Database.transaction { StatisticsDao.decayAllScores(it, factor) }
    }

    /**
     * 获取全部统计数据
     */
    fun getAllStatistics(): String {
        val totals = Database.read { StatisticsDao.totals(it) }
        val totalRun = totals.run
        val totalMarkdown = totals.markdown
        val totalMdTime = totals.mdTime
        val totalDownload = totals.download
        val totalDlTime = totals.dlTime

        val totalGlobalStorage = StorageManager.totalGlobalStorageSize()
        val totalUserStorage = StorageManager.totalUserStorageSize()
        val totalBucketNum = StorageManager.bucketCount()
        val totalLinkedProjects = StorageManager.totalLinkedProjects()
        val totalBucketSize = StorageManager.totalBucketSize()
        val totalBackupSize = StorageManager.totalBackupSize()
        val imageCount = ImageData.images.size
        val totalSize = folderSize(File(imageFolder))
        val totalCodeCache = CodeCacheManager.totalSize()

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
            appendLine("💾 存储总数：${StorageManager.projectCount()}")
            appendLine("  - 全局总大小：$totalGlobalStorage")
            appendLine("  - 用户总大小：$totalUserStorage")
            appendLine("🗄 存储库总数：$totalBucketNum")
            appendLine("  - 关联项目数：$totalLinkedProjects")
            appendLine("  - 存储总大小：$totalBucketSize")
            appendLine("  - 备份总大小：$totalBackupSize")
            appendLine("🖼️ 图片总数：$imageCount")
            appendLine("  - 占用空间：${formatSize(totalSize)}")
            appendLine("🏷️ 标签库总数：${TagManager.libraryCount()}")
            appendLine("  - 项目使用数：${TagManager.totalUsage()}")
            appendLine("📦 代码缓存总数：${CodeCacheManager.count()}")
            appendLine("  - 缓存总大小：$totalCodeCache")
        }
    }

    /**
     * 获取项目统计数据
     */
    fun getStatistic(name: String): String {
        val stat = Database.read { StatisticsDao.get(it, name) }
        val run = stat?.run?.toLong() ?: 0L
        val score = stat?.score ?: 0.0
        val markdown = stat?.markdown?.toLong()
        val mdTime = stat?.mdTime
        val download = stat?.download?.toLong()
        val dlTime = stat?.dlTime
        val storage = StorageManager.getProjectStorage(name)

        return buildString {
            appendLine("📈 总执行次数：$run")
            appendLine("🔥 热度指数：${"%.2f".format(score)}")
            val cache = CodeCacheManager.get(name)
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
                var lengthTotal = storage.global.length
                appendLine()
                appendLine("·全局存储大小：${storage.global.length}")
                appendLine("·用户存储数量：${storage.users.size}")
                if (storage.users.isNotEmpty()) {
                    val userTotal = storage.users.sumOf { it.content.length.toLong() }
                    val avg = userTotal.toDouble() / storage.users.size
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
    fun summarizeStatistics(userID: String?): String {
        val filtered = if (userID == null) {
            PastebinData.pastebin
        } else {
            PastebinData.pastebin.filterValues { it["userID"] == userID }
        }
        val projectCount = filtered.size

        val collabCount = userID?.let { id ->
            PastebinData.pastebin.values.count { map ->
                map["collaborators"]?.containsCollaborator(id) == true
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
            val scores = allScores()
            languageMap.entries
                .sortedByDescending { (key, _) ->
                    scores[key] ?: 0.0
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

    fun imageStatistics(userID: String): String {
        val imageCount = ImageData.images.values.count { it["userID"] == userID }
        return if (imageCount > 0) "🖼️ 上传图片：$imageCount\n" else ""
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

}