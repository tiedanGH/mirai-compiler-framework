package utils

import MiraiCompilerFramework.logger
import MiraiCompilerFramework.save
import data.ExtraData
import data.PastebinData
import data.PastebinStorage
import net.mamoe.mirai.utils.info
import java.math.BigDecimal
import java.math.RoundingMode

object Statistics {

    fun countRun(name: String) {
        createIfNotExist(name)
        val current = ExtraData.statistics[name]?.get("run") ?: 0.0
        val currentScore = ExtraData.statistics[name]?.get("score") ?: 0.0
        ExtraData.statistics[name]?.set("run", current + 1)
        ExtraData.statistics[name]?.set("score", currentScore + 1)
        ExtraData.save()
    }

    fun countMarkdown(name: String, mdTime: Double) {
        createIfNotExist(name)
        val current = ExtraData.statistics[name]?.get("markdown") ?: 0.0
        val currentTime = ExtraData.statistics[name]?.get("mdTime") ?: 0.0
        ExtraData.statistics[name]?.set("markdown", current + 1)
        ExtraData.statistics[name]?.set("mdTime", (currentTime + mdTime).roundTo2())
        ExtraData.save()
    }

    fun countDownload(name: String, dlTime: Double) {
        createIfNotExist(name)
        val current = ExtraData.statistics[name]?.get("download") ?: 0.0
        val currentTime = ExtraData.statistics[name]?.get("dlTime") ?: 0.0
        ExtraData.statistics[name]?.set("download", current + 1)
        ExtraData.statistics[name]?.set("dlTime", (currentTime + dlTime).roundTo2())
        ExtraData.save()
    }

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
        for ((_, value) in PastebinStorage.Storage) {
            totalGlobalStorage += value[0]?.length?.toLong() ?: 0L
            totalUserStorage += getUserStorageSize(value)
        }

        return buildString {
            appendLine("📈 总执行次数：$totalRun")
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
            appendLine("💾 存储总数：${PastebinStorage.Storage.size}")
            appendLine("  - 全局总大小：$totalGlobalStorage")
            appendLine("  - 用户总大小：$totalUserStorage")
        }
    }

    fun getStatistic(name: String): String {
        val stat = ExtraData.statistics[name]
        val run = stat?.get("run")?.toLong() ?: 0L
        val score = stat?.get("score") ?: 0.0
        val markdown = stat?.get("markdown")?.toLong()
        val mdTime = stat?.get("mdTime")
        val download = stat?.get("download")?.toLong()
        val dlTime = stat?.get("dlTime")
        val storage = PastebinStorage.Storage[name]

        return buildString {
            appendLine("📈 总执行次数：$run")
            appendLine("🔥 热度指数：${"%.2f".format(score)}")
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
                appendLine()
                appendLine("·全局存储大小：${storage[0]?.length}")
                appendLine("·用户存储数量：${storage.size - 1}")
                if (storage.size > 1) {
                    val userTotal = getUserStorageSize(storage)
                    val average = String.format("%.2f", userTotal.toDouble() / (storage.size - 1))
                    appendLine("·用户存储大小：$userTotal")
                    appendLine("·用户存储平均：$average")
                }
            }
        }
    }

    fun summarizeStatistics(userID: Long?): String {
        val filtered = if (userID == null) {
            PastebinData.pastebin
        } else {
            PastebinData.pastebin.filterValues { it["userID"] == userID.toString() }
        }
        val projectCount = filtered.size
        if (projectCount == 0) {
            return "·未上传过代码项目"
        }

        val lang = filtered.values.mapNotNull { it["language"]?.lowercase() }
        val langCounts: Map<String, Int> = lang.groupingBy { it }.eachCount()
        val langStats = langCounts.entries
            .sortedByDescending { it.value }
            .joinToString(separator = "\n") { (lang, cnt) ->
                val percent = cnt.toDouble() / projectCount * 100
                val formatted = String.format("%.2f", percent)
                " 🔸 $lang: ${formatted}%"
            }

        val languageMap: Map<String, String> = filtered
            .mapNotNull { (key, valueMap) ->
                valueMap["language"]?.lowercase()?.let { language ->
                    key to language
                }
            }.toMap()
        val top10Project = languageMap.entries
            .sortedByDescending { (key, _) ->
                ExtraData.statistics[key]?.get("score") ?: 0.0
            }
            .take(10)
            .joinToString(separator = "、") { (key, language) ->
                "$key${if (userID == null) "（$language）" else ""}"
            }

        return "📁 项目总数：$projectCount\n" +
                "$langStats\n\n" +
                "🔥 近期热门项目：\n" +
                top10Project
    }

    fun dailyDecayScore() {
        for (entry in ExtraData.statistics.values) {
            val rawScore = entry["score"] ?: 0.0
            val decayedScore = rawScore * 0.9
            entry["score"] = decayedScore.roundTo2()
        }
        ExtraData.save()
        logger.info { "热度指数衰减执行完成" }
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

    fun Double.roundTo2(): Double = BigDecimal(this).setScale(2, RoundingMode.HALF_UP).toDouble()

}