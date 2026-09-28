package site.tiedan.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * 批量执行准入：计入后会触及黑名单阈值时整批拒绝且不记录
 */
class RequestLimiterTest {

    /** 限制器是全局单例，每个用例使用独立的用户 */
    private fun newUser() = "test_${UUID.randomUUID()}"

    private val start = 1_000_000_000_000L

    @Test
    @DisplayName("未触及阈值时整批放行并逐次记录")
    fun admitsAndRecords() {
        val user = newUser()
        val admission = RequestLimiter.admitBatch(user, 5, start)
        assertTrue(admission.admitted)
        assertEquals("", admission.message)
        assertEquals(5 to 5, RequestLimiter.counts(user, start))
    }

    @Test
    @DisplayName("计入后会达到短期黑名单阈值时拒绝，且一次都不记录")
    fun rejectsWithoutRecording() {
        val user = newUser()
        assertTrue(RequestLimiter.admitBatch(user, 14, start).admitted)

        val rejected = RequestLimiter.admitBatch(user, 11, start)
        assertFalse(rejected.admitted)
        assertTrue(rejected.message.isNotEmpty(), "拒绝时应给出提示")
        assertEquals(14 to 14, RequestLimiter.counts(user, start), "被拒绝的批次不能计入频率")

        assertTrue(RequestLimiter.admitBatch(user, 10, start).admitted)
        assertEquals(24 to 24, RequestLimiter.counts(user, start))
    }

    @Test
    @DisplayName("警告级别与逐条执行一致：先初次警告，再二次警告")
    fun warningsEscalate() {
        val user = newUser()
        val first = RequestLimiter.admitBatch(user, 15, start)
        assertTrue(first.admitted)
        assertTrue(first.message.startsWith("[高频请求警告]"), first.message)

        val second = RequestLimiter.admitBatch(user, 5, start)
        assertTrue(second.admitted)
        assertTrue(second.message.startsWith("[高频二次警告]"), second.message)

        assertFalse(RequestLimiter.admitBatch(user, 5, start).admitted, "再计入 5 次将达到黑名单阈值")
        assertEquals(20 to 20, RequestLimiter.counts(user, start))
    }

    @Test
    @DisplayName("长期窗口同样按黑名单阈值拒绝")
    fun longWindowThreshold() {
        val user = newUser()
        // 每隔 61 秒计入 14 次，短期窗口每批都会重置
        repeat(9) { i -> assertTrue(RequestLimiter.admitBatch(user, 14, start + i * 61_000L).admitted) }
        val now = start + 9 * 61_000L
        assertEquals(0 to 126, RequestLimiter.counts(user, now))

        assertFalse(RequestLimiter.admitBatch(user, 24, now).admitted, "计入后 10 分钟内达到 150 次")
        assertEquals(0 to 126, RequestLimiter.counts(user, now))
        assertTrue(RequestLimiter.admitBatch(user, 23, now).admitted)
    }

    @Test
    @DisplayName("管理员不会被拉黑，始终放行")
    fun adminAlwaysAdmitted() {
        // 默认配置中控制台用户 10000 是管理员
        assertTrue(RequestLimiter.admitBatch("10000", 30, start).admitted)
    }

    @Test
    @DisplayName("达到阻止标准后阻止执行，被阻止的请求同样计入次数")
    fun blocksBeforeBlacklist() {
        val user = newUser()
        repeat(24) { assertFalse(RequestLimiter.newRequest(user, start).second, "阻止标准之前不应阻止") }
        for (times in 1..RequestLimiter.BLOCK_TIMES) {
            val (msg, blocked) = RequestLimiter.newRequest(user, start)
            assertTrue(blocked, "第 ${24 + times} 次请求应被阻止")
            assertTrue(msg.contains("阻止"), msg)
        }
        // 再请求一次就会拉黑；拉黑会写入插件数据，单元测试中不触发
        val total = 24 + RequestLimiter.BLOCK_TIMES
        assertEquals(total to total, RequestLimiter.counts(user, start), "被阻止的请求同样计入次数")
    }
}
