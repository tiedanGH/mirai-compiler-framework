package site.tiedan.utils

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * # 外部进程内存占用
 * - Linux：读取 `/proc/<pid>/status` 中的 VmRSS 与 VmSwap（物理内存 + 交换区）
 * - Windows：读取 tasklist 报告的内存使用（工作集）
 * - 其他系统或读取失败时返回 null，由调用方决定如何降级
 *
 * @author tiedanGH
 */
object ProcessMemory {

    private const val TASKLIST_TIMEOUT_SECONDS = 3L

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    /** tasklist CSV 输出中的一个带引号字段 */
    private val CSV_FIELD = Regex("\"([^\"]*)\"")

    /**
     * 读取进程的内存占用
     * @return 占用字节数；进程已退出、无权读取或系统不支持时返回 null
     */
    fun usageOf(pid: Long): Long? = runCatching {
        if (isWindows) readTasklist(pid) else parseProcStatus(File("/proc/$pid/status").readText())
    }.getOrNull()

    private fun readTasklist(pid: Long): Long? {
        val process = ProcessBuilder("tasklist", "/NH", "/FO", "CSV", "/FI", "PID eq $pid")
            .redirectErrorStream(true)
            .start()
        // 已按 PID 过滤，输出只有一行，等待结束前不会写满管道
        if (!process.waitFor(TASKLIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        // 只取引号、逗号与数字，按单字节解码即可，不受控制台代码页影响
        return parseTasklist(process.inputStream.readBytes().toString(Charsets.ISO_8859_1), pid)
    }

    /**
     * 解析 `/proc/<pid>/status`，返回 VmRSS 与 VmSwap 之和（字节）
     * - 缺少 VmRSS 时（如已退出的僵尸进程）视为无法读取
     * - 部分环境（如 WSL1）不提供 VmSwap，按 0 计
     */
    internal fun parseProcStatus(text: String): Long? {
        fun kb(key: String): Long? = text.lineSequence()
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()
        val rss = kb("VmRSS") ?: return null
        return (rss + (kb("VmSwap") ?: 0L)) * 1024
    }

    /**
     * 解析 `tasklist /NH /FO CSV` 的输出，返回指定进程的内存使用（字节）
     * - 各列依次为：映像名称、PID、会话名、会话编号、内存使用
     * - 内存使用形如 `12,345 K`，千位分隔符随系统区域变化，只取其中的数字
     * - 没有匹配进程时输出的是不带引号字段的提示文本，返回 null
     */
    internal fun parseTasklist(output: String, pid: Long): Long? =
        output.lineSequence()
            .map { line -> CSV_FIELD.findAll(line).map { it.groupValues[1] }.toList() }
            .firstOrNull { it.size >= 5 && it[1] == pid.toString() }
            ?.get(4)?.filter { it.isDigit() }?.toLongOrNull()
            ?.times(1024)
}
