package utils

import JCompilerCollection
import JCompilerCollection.logger
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@OptIn(ConsoleExperimentalApi::class)
object MarkdownImageProcessor {
    const val TIMEOUT = 60L
    val folder = "./data/${JCompilerCollection.dataHolderName}"
    private val MarkdownLock = Mutex()
    // 操作系统相关信息
    private val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    private val memoryLimit: Long = if (System.getProperty("os.name").lowercase().contains("windows")) 6656 else 4096

    suspend fun processMarkdown(originalContent: String, width: String = "600", timeout: Long = TIMEOUT): Pair<String, Long> {
        Statistics.countMarkdown()
        val content = originalContent.ifBlank { "[警告] `content`内容为空或仅包含空白字符" }
        if (timeout <= 0L) {
            return Pair("操作失败：执行时间已达总上限${TIMEOUT}秒", 0)
        }
        return MarkdownLock.withLock {
            logger.info("请求调用系统命令执行Markdown转图片")
            try {
                val startTime = Instant.now()       // 记录开始时间

                val tempFile = File("$folder/tmp.md")
                tempFile.writeText(content)
                val process = if (System.getProperty("os.name").lowercase().contains("windows")) {
                    ProcessBuilder(
                        "$folder/markdown2image.exe",
                        "--input=$folder/tmp.md",
                        "--width=$width",
                        "--output=$folder/markdown.png"
                    ).directory(File(".")).start()
                } else {
                    ProcessBuilder(
                        "$folder/markdown2image",
                        "--input=$folder/tmp.md",
                        "--width=$width",
                        "--output=$folder/markdown.png"
                    ).directory(File(".")).start()
                }
                // 在后台线程中监控进程的内存使用情况
                CoroutineScope(Dispatchers.IO).launch {
                    var duration = 0
                    while (process.isAlive) {
                        val physicalUsage = osBean.totalPhysicalMemorySize - osBean.freePhysicalMemorySize
                        val swapUsage = osBean.totalSwapSpaceSize - osBean.freeSwapSpaceSize
                        val totalUsage = physicalUsage + swapUsage
                        if (totalUsage > memoryLimit * 1024 * 1024) {
                            duration++
                            if (duration >= 5) {
                                logger.warning("监测到系统总内存使用超过${memoryLimit}MB达到5秒，当前总内存：${totalUsage / 1024 / 1024}MB，程序进程被中断")
                                process.destroyForcibly()
                                break
                            }
                        }
                        delay(1000)
                    }
                }
                if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    if (timeout == TIMEOUT) {
                        saveErrorRecord(content, "TimeoutError($timeout)")
                        return@withLock Pair("操作失败：markdown2image执行超时，已强制结束进程，最大执行时间限制为${TIMEOUT}秒。如需查看详细内容请联系铁蛋", TIMEOUT)
                    } else {
                        return@withLock Pair("操作失败：执行超出剩余时间${timeout}秒限制，图片生成被中断", timeout)
                    }
                } else if (process.exitValue() != 0) {
                    saveErrorRecord(content, "ProcessError(${process.exitValue()})")
                    val endTime = Instant.now()     // 记录结束时间
                    val durationInSeconds = ceil((Duration.between(startTime, endTime).toMillis() / 1000.0)).toLong()
                    if (process.exitValue() == 137) {
                        return@withLock Pair("操作失败：markdown2image因内存占用过大被中断，超出服务器安全内存限制。exitValue：137", durationInSeconds)
                    }
                    return@withLock Pair("操作失败：markdown2image程序执行异常，请联系铁蛋查看后台报错记录。exitValue：${process.exitValue()}", durationInSeconds)
                }
                tempFile.delete()

                val endTime = Instant.now()     // 记录结束时间
                val durationInSeconds = ceil((Duration.between(startTime, endTime).toMillis() / 1000.0)).toLong()
                logger.info("操作成功完成，用时${durationInSeconds}秒")
                return@withLock Pair("执行markdown转图片成功", durationInSeconds)
            } catch (e: Exception) {
                logger.warning(e)
                saveErrorRecord("$e\n\n$content", "${e::class.simpleName}")
                return@withLock Pair("操作失败：Kotlin运行错误【严重错误，理论不可能发生】，请务必联系铁蛋排查bug\n${e::class.simpleName}(${e.message})", 0)
            }
        }
    }

    private fun saveErrorRecord(content: String, prefix: String) {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH.mm.ss")
        val dateTime = LocalDateTime.now().format(formatter)
        File("$folder/errors/${dateTime}_${prefix}.txt").writeText(content)
        logger.warning("${prefix}报错记录已保存为txt文件")
    }

}
