package site.tiedan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import site.tiedan.MiraiCompilerFramework.ThreadQuota

/**
 * 进程余量：全局与单用户上限同时约束
 */
class ThreadQuotaTest {

    @Test
    @DisplayName("余量取全局剩余与单用户剩余中较小的一个")
    fun availableTakesMinimum() {
        assertEquals(4, ThreadQuota(total = 6, totalLimit = 10, user = 0, userLimit = 8).available)
        assertEquals(2, ThreadQuota(total = 6, totalLimit = 10, user = 6, userLimit = 8).available)
        assertEquals(0, ThreadQuota(total = 3, totalLimit = 10, user = 8, userLimit = 8).available)
        assertEquals(0, ThreadQuota(total = 12, totalLimit = 10, user = 0, userLimit = 8).available, "超发时不为负数")
    }

    @Test
    @DisplayName("余量不足的提示：全局已满优先")
    fun limitMessagePrefersGlobal() {
        assertTrue(ThreadQuota(10, 10, 8, 8).limitMessage().contains("当前已经有 10 个进程"))
        assertTrue(ThreadQuota(8, 10, 8, 8).limitMessage().contains("您当前已有 8 个进程"))
    }
}
