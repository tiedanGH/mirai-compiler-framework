package site.tiedan.module

import net.mamoe.mirai.utils.info
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.data.ExtraData
import java.util.*

object Timer {

    /**
     * 执行全部每日定时任务
     * - 每个子任务单独兜底：任何一个失败都不得中断其余任务，更不得让调用方的定时循环退出
     */
    fun executeScheduledTasks() {
        runTask("每日数据备份") { BackupManager.dailyBackup() }
        runTask("清除代码执行黑名单") { executeClearBlackList() }
        runTask("热度指数衰减") { dailyDecayScore() }
    }

    private fun runTask(name: String, task: () -> Unit) {
        runCatching(task).onFailure { logger.error("定时任务【$name】执行失败", it) }
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
        Statistics.decayAllScores(0.9)
        logger.info { "热度指数衰减执行完成" }
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
