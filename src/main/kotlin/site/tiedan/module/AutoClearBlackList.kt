package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.data.ExtraData
import net.mamoe.mirai.utils.info
import java.util.*

fun executeClearBlackList() {
    if (ExtraData.BlackList.isNotEmpty()) {
        logger.info { "已自动清除代码执行黑名单记录：${ExtraData.BlackList.joinToString(" ")}" }
        ExtraData.BlackList.clear()
        ExtraData.save()
    }
}

fun calculateNextClearDelay(): Long {
    val currentTime = Calendar.getInstance()
    val nextExecTime = DateTime.getCal(8, 0, 0, 0)
    if (currentTime.timeInMillis > nextExecTime.timeInMillis) {
        nextExecTime.add(Calendar.DAY_OF_YEAR, 1)
    }
    return nextExecTime.timeInMillis - currentTime.timeInMillis
}

class DateTime {
    companion object {
        fun getCal(hour: Int, minute: Int, second: Int, millisecond: Int): Calendar {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, second)
            cal.set(Calendar.MILLISECOND, millisecond)
            return cal
        }
    }
}
