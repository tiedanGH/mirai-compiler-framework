package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.data.ExtraData
import java.util.concurrent.ConcurrentHashMap

/**
 * ### 代码请求限制
 * - 初次警告：最近60秒内达到 15 次
 * - 二次警告：最近60秒总次数达到 20 次
 * - 黑名单：最近60秒总次数达到 25 次
 *
 * @author tiedanGH
 */
object RequestLimiter {
    enum class WarningLevel {
        NONE, FIRST, SECOND
    }

    private val warningTime: List<Int> = listOf(15, 20, 25)
    private const val DETECTION_TIME: Long = 60_000
    private val userRequestTimes = ConcurrentHashMap<Long, MutableList<Long>>()
    private val userWarningLevels = ConcurrentHashMap<Long, WarningLevel>()

    /**
     * 记录新执行请求
     */
    fun newRequest(userID: Long): Pair<String, Boolean> {
        val currentTime = System.currentTimeMillis()
        val requestTimes = userRequestTimes.computeIfAbsent(userID) { mutableListOf() }

        // 清理过期的请求
        requestTimes.removeIf { it < currentTime - DETECTION_TIME }

        // 记录新请求时间
        requestTimes.add(currentTime)

        // 获取用户当前的警告级别
        val currentWarningLevel = userWarningLevels.getOrDefault(userID, WarningLevel.NONE)

        val recentRequests = requestTimes.size
        when {
            // 加入黑名单
            recentRequests >= warningTime[2] &&
            currentWarningLevel == WarningLevel.SECOND -> {
                ExtraData.BlackList.add(userID)
                ExtraData.save()
                return Pair("[警告无效处理]\n您已被加入bot执行代码黑名单，暂时无法再执行代码请求。请等待每日8点黑名单自动重置", true)
            }
            // 二次警告
            recentRequests >= warningTime[1] &&
            currentWarningLevel == WarningLevel.FIRST -> {
                userWarningLevels[userID] = WarningLevel.SECOND
                return Pair("[高频二次警告]\n请暂停所有代码执行请求，并等待大约30秒的时间，以避免被bot拉黑的风险", false)
            }
            // 初次警告
            recentRequests >= warningTime[0] &&
            currentWarningLevel == WarningLevel.NONE -> {
                userWarningLevels[userID] = WarningLevel.FIRST
                return Pair("[高频请求警告]\n您在短时间内多次调用执行代码请求，请适当降低指令调用频率", false)
            }
            else -> {
                if (recentRequests < 15 && currentWarningLevel != WarningLevel.NONE) {
                    userWarningLevels[userID] = WarningLevel.NONE
                }
                if (recentRequests in 15..19 && currentWarningLevel != WarningLevel.FIRST) {
                    userWarningLevels[userID] = WarningLevel.FIRST
                }
                return Pair("", false)
            }
        }
    }
}
