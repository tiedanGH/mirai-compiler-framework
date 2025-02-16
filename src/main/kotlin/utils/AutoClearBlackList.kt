package utils

import JCompilerCollection.logger
import JCompilerCollection.save
import data.BlackListData
import net.mamoe.mirai.utils.info
import java.util.*

fun executeClearBlackList() {
    if (BlackListData.BlackList.isEmpty()) {
        logger.info { "代码执行黑名单为空，无需清除" }
    } else {
        logger.info { "已自动清除代码执行黑名单记录：${BlackListData.BlackList.joinToString(" ")}" }
        BlackListData.BlackList.clear()
        BlackListData.save()
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
