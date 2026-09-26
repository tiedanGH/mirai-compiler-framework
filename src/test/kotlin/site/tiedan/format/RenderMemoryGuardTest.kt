package site.tiedan.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import site.tiedan.format.RenderMemoryGuard.GRACE_MILLIS
import java.util.concurrent.TimeUnit

/**
 * 渲染进程内存守护：终止顺序与宽限期
 */
class RenderMemoryGuardTest {

    /** 仅用于读取存活状态，测试中不会终止当前进程 */
    private fun watched(startedAt: Long) = RenderMemoryGuard.Watched(ProcessHandle.current(), null, startedAt)

    @Test
    @DisplayName("按占用从高到低依次选出终止对象")
    fun picksHighestUsageFirst() {
        val low = watched(0)
        val high = watched(0)
        val mid = watched(0)
        val usages = mapOf(low to 100L, high to 900L, mid to 300L)
        assertSame(high, RenderMemoryGuard.pickVictim(listOf(low, high, mid), usages))
        assertSame(mid, RenderMemoryGuard.pickVictim(listOf(low, mid), usages))
        assertSame(low, RenderMemoryGuard.pickVictim(listOf(low), usages))
    }

    @Test
    @DisplayName("读取不到占用的排在最后，其中运行最久者优先")
    fun unknownUsageRanksLast() {
        val oldest = watched(1_000)
        val younger = watched(2_000)
        val measured = watched(3_000)
        assertSame(measured, RenderMemoryGuard.pickVictim(listOf(oldest, younger, measured), mapOf(measured to 1L)))
        assertSame(oldest, RenderMemoryGuard.pickVictim(listOf(younger, oldest), emptyMap()))
    }

    @Test
    @DisplayName("超限前启动的进程在持续超限满宽限期后才可终止")
    fun graceCountsFromOverLimitStart() {
        val process = watched(0)
        val since = 10_000L
        assertTrue(RenderMemoryGuard.eligible(listOf(process), since, since + GRACE_MILLIS - 1).isEmpty())
        assertEquals(listOf(process), RenderMemoryGuard.eligible(listOf(process), since, since + GRACE_MILLIS))
    }

    @Test
    @DisplayName("超限期间启动的进程从自身启动起享有完整宽限期")
    fun graceCountsFromProcessStart() {
        val since = 10_000L
        val late = watched(since + 8_000)
        // 已持续超限 10 秒，但该进程只运行了 2 秒
        assertTrue(RenderMemoryGuard.eligible(listOf(late), since, since + 10_000).isEmpty())
        assertEquals(listOf(late), RenderMemoryGuard.eligible(listOf(late), since, since + 8_000 + GRACE_MILLIS))
    }

    @Test
    @DisplayName("终止后记录占用、进程退出，且不再作为候选")
    fun killMarksAndExcludes() {
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val process = if (windows) ProcessBuilder("ping", "-n", "30", "127.0.0.1").start()
        else ProcessBuilder("sleep", "30").start()
        try {
            val target = RenderMemoryGuard.Watched(process.toHandle(), "测试", 0)
            target.kill(123L)
            assertTrue(target.killed)
            assertEquals(123L, target.usageAtKill)
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "进程应已被终止")
            assertTrue(RenderMemoryGuard.eligible(listOf(target), 0, GRACE_MILLIS).isEmpty())
        } finally {
            process.destroyForcibly()
        }
    }
}
