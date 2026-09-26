package site.tiedan.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 外部进程内存占用：/proc 与 tasklist 输出解析，以及在当前系统上的实际读取
 */
class ProcessMemoryTest {

    @Test
    @DisplayName("/proc status 取 VmRSS 与 VmSwap 之和")
    fun procStatusSumsRssAndSwap() {
        val text = listOf(
            "Name:\tmarkdown2image",
            "State:\tS (sleeping)",
            "VmHWM:\t  612000 kB",
            "VmRSS:\t  512000 kB",
            "RssAnon:\t  480000 kB",
            "VmSwap:\t   24000 kB",
        ).joinToString("\n")
        assertEquals((512000L + 24000L) * 1024, ProcessMemory.parseProcStatus(text))
    }

    @Test
    @DisplayName("没有 VmSwap 时按 0 计")
    fun procStatusWithoutSwap() {
        val text = "Name:\tcat\nVmSize:\t17112 kB\nVmRSS:\t1024 kB\nThreads:\t1\n"
        assertEquals(1024L * 1024, ProcessMemory.parseProcStatus(text))
    }

    @Test
    @DisplayName("没有 VmRSS 视为无法读取")
    fun procStatusWithoutRss() {
        val text = "Name:\tmarkdown2image\nState:\tZ (zombie)\nThreads:\t1\n"
        assertNull(ProcessMemory.parseProcStatus(text))
    }

    @Test
    @DisplayName("tasklist 按 PID 取内存列，忽略千位分隔符")
    fun tasklistParsesMemoryColumn() {
        val output = "\"markdown2image.exe\",\"20088\",\"Console\",\"1\",\"1,234,567 K\"\r\n"
        assertEquals(1234567L * 1024, ProcessMemory.parseTasklist(output, 20088))
    }

    @Test
    @DisplayName("tasklist 兼容其他区域的千位分隔符")
    fun tasklistOtherLocales() {
        assertEquals(45678L * 1024, ProcessMemory.parseTasklist("\"a.exe\",\"7\",\"Console\",\"1\",\"45.678 K\"", 7))
        assertEquals(45678L * 1024, ProcessMemory.parseTasklist("\"a.exe\",\"7\",\"Console\",\"1\",\"45 678 K\"", 7))
    }

    @Test
    @DisplayName("tasklist 无匹配进程或 PID 不符时返回 null")
    fun tasklistNoMatch() {
        assertNull(ProcessMemory.parseTasklist("INFO: No tasks are running which match the specified criteria.\r\n", 7))
        assertNull(ProcessMemory.parseTasklist("\"a.exe\",\"8\",\"Console\",\"1\",\"100 K\"", 7))
    }

    @Test
    @DisplayName("Linux 与 Windows 上能读到当前进程的占用，不存在的进程返回 null")
    fun readsRealProcess() {
        val os = System.getProperty("os.name").lowercase()
        assumeTrue(os.contains("linux") || os.contains("windows"), "仅支持 Linux 与 Windows")
        val usage = ProcessMemory.usageOf(ProcessHandle.current().pid())
        assertTrue(usage != null && usage > 0, "应读到正数占用，实际为 $usage")
        assertNull(ProcessMemory.usageOf(999_999_999L))
    }
}
