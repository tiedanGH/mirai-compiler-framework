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
import utils.MarkdownImageProcessor.folder
import java.io.File
import java.io.FileOutputStream
import java.lang.management.ManagementFactory
import java.net.HttpURLConnection
import java.net.URL
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
    private val memoryLimit: Long = if (System.getProperty("os.name").lowercase().contains("windows")) 6656 else 1895

    suspend fun processMarkdown(originalContent: String, width: String = "600", timeout: Long = TIMEOUT): Pair<String, Long> {
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
                // 在后台线程中监控进程5秒后的内存使用情况
                CoroutineScope(Dispatchers.IO).launch {
                    var duration = 0
                    while (process.isAlive) {
                        val memoryUsage = osBean.totalPhysicalMemorySize - osBean.freePhysicalMemorySize
                        if (memoryUsage > memoryLimit * 1024 * 1024) {
                            duration++
                            if (duration >= 5) {
                                logger.warning("监测内存超过${memoryLimit}MB达到5秒，当前数值：${memoryUsage / 1024 / 1024}MB，程序进程被中断")
                                process.destroyForcibly()
                                break
                            }
                        }
                        delay(1000)
                    }
                }
                if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    saveErrorRecord(content, "TimeoutError($timeout)")
                    if (timeout == TIMEOUT) {
                        return@withLock Pair("操作失败：markdown2image执行超时，已强制结束进程，最大执行时间限制为${TIMEOUT}秒。如需查看详细内容请联系铁蛋", TIMEOUT)
                    } else {
                        return@withLock Pair("操作失败：执行超出剩余时间${timeout}秒限制，图片生成被中断", timeout)
                    }
                } else if (process.exitValue() != 0) {
                    saveErrorRecord(content, "ProcessError(${process.exitValue()})")
                    val endTime = Instant.now()     // 记录结束时间
                    val durationInSeconds = ceil((Duration.between(startTime, endTime).toMillis() / 1000.0)).toLong()
                    if (process.exitValue() == 137) {
                        return@withLock Pair("操作失败：markdown2image因内存占用过大被中断，运行使用的内存应在600MB以内。exitValue：137", durationInSeconds)
                    }
                    return@withLock Pair("操作失败：markdown2image程序执行异常，请联系铁蛋查看后台报错记录。exitValue：${process.exitValue()}", durationInSeconds)
                }
                tempFile.delete()
                logger.info("操作成功完成")

                val endTime = Instant.now()     // 记录结束时间
                val durationInSeconds = ceil((Duration.between(startTime, endTime).toMillis() / 1000.0)).toLong()
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

fun renderLatexOnline(latex: String): String {
    val apiUrl = "https://quicklatex.com/latex3.f"
    val outputFilePath = "$folder/latex.png"
    val postData = "formula=${java.net.URLEncoder.encode(latex, "GBK").replace("+", "%20")}&fsize=15px&fcolor=000000&bcolor=FFFFFF&mode=0&out=1"

    try {
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.doOutput = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000

        connection.outputStream.use { it.write(postData.toByteArray()) }

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            val imageUrl = Regex("""https://.*?\.png""").find(response)?.value
                ?: return "QuickLaTeX：从结果中未找到图片下载链接"

            val imageConnection = URL(imageUrl).openConnection() as HttpURLConnection
            imageConnection.inputStream.use { input ->
                FileOutputStream(outputFilePath).use { output ->
                    input.copyTo(output)
                }
            }
            logger.info("获取结果图片成功")
            return "请求执行LaTeX转图片成功"
        } else {
            return "QuickLaTeX：HTTP Status ${connection.responseCode}: ${connection.responseMessage}"
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return "QuickLaTeX：${e::class.simpleName}(${e.message})"
    }
}
