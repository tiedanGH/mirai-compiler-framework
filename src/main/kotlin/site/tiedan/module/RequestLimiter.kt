package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.config.PastebinConfig
import site.tiedan.data.ExtraData
import java.util.concurrent.ConcurrentHashMap

/**
 * ### 代码请求限制
 * - 初次警告：近60秒内达到 15 次或近10分钟达到 130 次
 * - 二次警告：近60秒总次数达到 20 次或近10分钟达到 145 次
 * - 阻止执行：近60秒总次数达到 25 次或近10分钟达到 150 次（管理员不受限制）
 * - 黑名单：被阻止 2 次后仍继续请求时拉黑，被阻止的请求同样计入次数（管理员不会被拉黑）
 * - 指令集整批执行走 [admitBatch]：计入后会达到阻止标准时整批拒绝，且不记录任何请求
 *
 * @author tiedanGH
 */
object RequestLimiter {
    enum class WarningLevel {
        NONE, FIRST, SECOND
    }

    private const val SHORT_WINDOW: Long = 60_000L
    private const val LONG_WINDOW: Long = 600_000L
    /** 初次警告、二次警告、阻止执行的阈值 */
    private val SHORT_THRESHOLDS = listOf(15, 20, 25)
    private val LONG_THRESHOLDS = listOf(130, 145, 150)
    /** 达到阻止标准后最多阻止次数，仍继续请求则拉黑 */
    const val BLOCK_TIMES = 2

    private val userRequestTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val userWarningLevels = ConcurrentHashMap<String, WarningLevel>()

    /**
     * 记录新执行请求
     * - 与 [admitBatch] 共用同一把锁：单用户的请求记录是普通列表，并发写入必须串行
     *
     * @param currentTime 请求时刻，默认为当前时间
     */
    @Synchronized
    fun newRequest(userID: String, currentTime: Long = System.currentTimeMillis()): Pair<String, Boolean> {
        val isAdmin = PastebinConfig.admins.contains(userID)

        val requestTimes = userRequestTimes.computeIfAbsent(userID) { mutableListOf() }

        requestTimes.removeIf { it < currentTime - LONG_WINDOW }    // 清理过期的请求

        requestTimes.add(currentTime)   // 记录新请求时间

        val shortCount = requestTimes.count { it >= currentTime - SHORT_WINDOW }
        val longCount = requestTimes.size

        // 获取用户当前的警告级别
        val currentLevel = userWarningLevels.getOrDefault(userID, WarningLevel.NONE)

        // --- 黑名单判定：阻止 BLOCK_TIMES 次后仍继续请求 ---
        val shortBlack = shortCount >= SHORT_THRESHOLDS[2] + BLOCK_TIMES
        val longBlack = longCount >= LONG_THRESHOLDS[2] + BLOCK_TIMES
        if ((shortBlack || longBlack) && currentLevel == WarningLevel.SECOND && !isAdmin) {
            ExtraData.BlackList.add(userID)
            ExtraData.save()
            userWarningLevels[userID] = WarningLevel.SECOND
            val reason = if (shortBlack) "短期内（60秒）频繁请求" else "长期累计（10分钟）频繁请求"
            return Pair("[警告无效处理]\n由于$reason，您已被加入执行代码黑名单，暂时无法再执行代码。黑名单将在每日8点自动重置。", true)
        }

        // --- 阻止执行判定 ---
        val shortBlock = shortCount >= SHORT_THRESHOLDS[2]
        val longBlock = longCount >= LONG_THRESHOLDS[2]
        if ((shortBlock || longBlock) && currentLevel == WarningLevel.SECOND && !isAdmin) {
            val reason = if (shortBlock) "近 60秒 内请求量已达上限" else "近 10分钟 内累计请求量已达上限"
            return Pair("[请求最终警告]\n$reason，已被阻止。**请立即停止所有执行请求**，否则将被bot拉黑", true)
        }

        // --- 二次警告判定 ---
        val shortSecond = shortCount >= SHORT_THRESHOLDS[1]
        val longSecond = longCount >= LONG_THRESHOLDS[1]
        if ((shortSecond || longSecond) && currentLevel == WarningLevel.FIRST && !isAdmin) {
            userWarningLevels[userID] = WarningLevel.SECOND
            val msg = if (shortSecond) {
                "[高频二次警告]\n近 60秒 内请求次数极高。**请暂停所有代码执行请求**，并等待 30秒，以避免被bot拉黑的风险"
            } else {
                "[累计二次警告]\n近 10分钟 内累计请求量过高。**请暂停所有代码执行请求**，并休息 几分钟，以避免被bot拉黑的风险"
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

    /**
     * 批量准入结果
     * @param message 拒绝原因，或准入期间产生的最后一条警告
     */
    data class BatchAdmission(val admitted: Boolean, val message: String)

    /** 用户近60秒与近10分钟内的请求次数 */
    @Synchronized
    fun counts(userID: String, now: Long = System.currentTimeMillis()): Pair<Int, Int> {
        val times = userRequestTimes[userID] ?: return 0 to 0
        return times.count { it >= now - SHORT_WINDOW } to times.count { it >= now - LONG_WINDOW }
    }

    /**
     * 批量执行准入（指令集执行）
     * - 计入 [count] 次后会达到任一阻止标准时整批拒绝，且不记录任何请求（管理员执行跳过此项）
     */
    @Synchronized
    fun admitBatch(userID: String, count: Int, now: Long = System.currentTimeMillis()): BatchAdmission {
        if (!PastebinConfig.admins.contains(userID)) {
            val (short, long) = counts(userID, now)
            if (short + count >= SHORT_THRESHOLDS[2] || long + count >= LONG_THRESHOLDS[2]) {
                val room = minOf(SHORT_THRESHOLDS[2] - 1 - short, LONG_THRESHOLDS[2] - 1 - long).coerceAtLeast(0)
                return BatchAdmission(
                    false,
                    "执行失败：$count 个指令超出高频请求上限，已被阻止，请稍后重试\n" +
                    "近60秒请求 $short 次，近10分钟请求 $long 次，当前最多执行 $room 条"
                )
            }
        }
        var warning = ""
        repeat(count) {
            val (msg, blocked) = newRequest(userID, now)
            if (msg.isNotEmpty()) warning = msg
            if (blocked) return BatchAdmission(false, msg)  // 预检已排除，仅作兜底
        }
        return BatchAdmission(true, warning)
    }
}
