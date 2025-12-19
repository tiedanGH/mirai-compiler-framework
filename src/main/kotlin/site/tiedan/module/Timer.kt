package site.tiedan.module

import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.roundTo2
import site.tiedan.data.ExtraData
import net.mamoe.mirai.utils.info
import site.tiedan.MiraiCompilerFramework
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

object Timer {
    fun executeScheduledTasks() {
        backupYmlDataFiles()
        executeClearBlackList()
        dailyDecayScore()
    }

    fun calculateNextDelay(): Long {
        val currentTime = Calendar.getInstance()
        val nextExecTime = getCalender(8)
        if (currentTime.timeInMillis > nextExecTime.timeInMillis) {
            nextExecTime.add(Calendar.DAY_OF_YEAR, 1)
        }
        return nextExecTime.timeInMillis - currentTime.timeInMillis
    }

    private fun executeClearBlackList() {
        if (ExtraData.BlackList.isNotEmpty()) {
            logger.info { "自动清除代码执行黑名单：${ExtraData.BlackList.joinToString(" ")}" }
            ExtraData.BlackList.clear()
            ExtraData.save()
        }
    }

    /**
     * 每日热度指数衰减
     */
    private fun dailyDecayScore() {
        for (entry in ExtraData.statistics.values) {
            val rawScore = entry["score"] ?: 0.0
            val decayedScore = rawScore * 0.9
            entry["score"] = decayedScore.roundTo2()
        }
        ExtraData.save()
        logger.info { "热度指数衰减执行完成" }
    }

    private fun backupYmlDataFiles() {
        @OptIn(ConsoleExperimentalApi::class)
        val baseDir = File("./data/${MiraiCompilerFramework.dataHolderName}/")
        if (!baseDir.exists() || !baseDir.isDirectory) return

        val bakDir = File(baseDir, "backup")
        if (!bakDir.exists()) bakDir.mkdirs()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val todayDir = File(bakDir, today)
        if (!todayDir.exists()) todayDir.mkdirs()

        baseDir.listFiles { file ->
            file.isFile && file.extension.equals("yml", ignoreCase = true)
        }?.forEach { ymlFile ->
            val target = File(todayDir, ymlFile.name)
            ymlFile.copyTo(target, overwrite = true)
        }

        val dateDirs = bakDir.listFiles { file -> file.isDirectory }?.toList() ?: return
        if (dateDirs.size > 7) {
            val sorted = dateDirs.sortedBy { it.name }
            val toDelete = sorted.take(dateDirs.size - 7)
            toDelete.forEach { dir ->
                dir.deleteRecursively()
            }
        }
        logger.info { "YML 数据文件自动备份完成" }
    }

    private fun getCalender(hour: Int, minute: Int = 0, second: Int = 0, millisecond: Int = 0): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, second)
        cal.set(Calendar.MILLISECOND, millisecond)
        return cal
    }
}
