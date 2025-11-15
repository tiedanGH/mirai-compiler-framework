package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.config.PastebinConfig
import site.tiedan.data.ExtraData
import java.util.concurrent.ConcurrentHashMap

/**
 * ### 代码请求限制
 * - 初次警告：近60秒内达到 15 次或近10分钟达到 100 次
 * - 二次警告：近60秒总次数达到 20 次或近10分钟达到 110 次
 * - 黑名单：近60秒总次数达到 25 次或近10分钟达到 120 次
 *
 * @author tiedanGH
 */
object RequestLimiter {
    enum class WarningLevel {
        NONE, FIRST, SECOND
    }

    private const val SHORT_WINDOW: Long = 60_000L
    private const val LONG_WINDOW: Long = 600_000L
    private val SHORT_THRESHOLDS = listOf(15, 20, 25)
    private val LONG_THRESHOLDS = listOf(130, 145, 150)

    private val userRequestTimes = ConcurrentHashMap<Long, MutableList<Long>>()
    private val userWarningLevels = ConcurrentHashMap<Long, WarningLevel>()

    /**
     * 记录新执行请求
     */
    fun newRequest(userID: Long): Pair<String, Boolean> {
        val isAdmin = PastebinConfig.admins.contains(userID)

        val currentTime = System.currentTimeMillis()
        val requestTimes = userRequestTimes.computeIfAbsent(userID) { mutableListOf() }

        requestTimes.removeIf { it < currentTime - LONG_WINDOW }    // 清理过期的请求

        requestTimes.add(currentTime)   // 记录新请求时间

        val shortCount = requestTimes.count { it >= currentTime - SHORT_WINDOW }
        val longCount = requestTimes.size

        // 获取用户当前的警告级别
        val currentLevel = userWarningLevels.getOrDefault(userID, WarningLevel.NONE)

        // --- 黑名单判定 ---
        val shortBlack = shortCount >= SHORT_THRESHOLDS[2]
        val longBlack = longCount >= LONG_THRESHOLDS[2]
        if ((shortBlack || longBlack) && currentLevel == WarningLevel.SECOND && !isAdmin) {
            ExtraData.BlackList.add(userID)
            ExtraData.save()
            userWarningLevels[userID] = WarningLevel.SECOND
            val reason = if (shortBlack) "短期内（60秒）频繁请求" else "长期累计（10分钟）频繁请求"
            return Pair("[警告无效处理]\n由于$reason，您已被加入执行代码黑名单，暂时无法再执行代码。黑名单将在每日8点自动重置。", true)
        }

        // --- 二次警告判定 ---
        val shortSecond = shortCount >= SHORT_THRESHOLDS[1]
        val longSecond = longCount >= LONG_THRESHOLDS[1]
        if ((shortSecond || longSecond) && currentLevel == WarningLevel.FIRST && !isAdmin) {
            userWarningLevels[userID] = WarningLevel.SECOND
            val msg = if (shortSecond) {
                "[高频二次警告]\n近 60秒 内请求次数极高。**请暂停所有代码执行请求**，并等待大约 30秒 的时间，以避免被bot拉黑的风险"
            } else {
                "[累计二次警告]\n近 10分钟 内累计请求量过高。**请暂停所有代码执行请求**，并休息大约 5分钟 的时间，以避免被bot拉黑的风险"
            }
            return Pair(msg, false)
        }

        // --- 初次警告判定 ---
        val shortFirst = shortCount >= SHORT_THRESHOLDS[0]
        val longFirst = longCount >= LONG_THRESHOLDS[0]
        if ((shortFirst || longFirst) && currentLevel == WarningLevel.NONE) {
            userWarningLevels[userID] = WarningLevel.FIRST
            val msg = if (shortFirst) {
                "[高频请求警告]\n您在短时间内多次调用执行代码请求，请适当降低指令调用频率"
            } else {
                "[累计请求警告]\n您在近 10分钟 内累计较多执行请求。请适当分散请求时间，避免对系统造成压力"
            }
            return Pair(msg, false)
        }

        // --- 降低警告级别 ---
        if (!shortFirst && !longFirst && currentLevel != WarningLevel.NONE) {
            userWarningLevels[userID] = WarningLevel.NONE
        }
        if (!shortSecond && !longSecond && currentLevel != WarningLevel.FIRST) {
            userWarningLevels[userID] = WarningLevel.FIRST
        }
        return Pair("", false)
    }
}
