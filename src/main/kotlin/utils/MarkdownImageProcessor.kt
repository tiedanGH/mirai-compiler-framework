package utils

import commands.CommandRun.Image_Path
import MiraiCompilerFramework
import MiraiCompilerFramework.logger
import com.sun.management.OperatingSystemMXBean
import config.SystemConfig
import data.ExtraData
import data.PastebinData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import utils.Statistics.roundTo2
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
    val cacheFolder = "./data/${MiraiCompilerFramework.dataHolderName}/cache/"
    private val MarkdownLock = Mutex()
    // 操作系统相关信息
    private val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    data class MarkdownResult(val success: Boolean, val message: String, val duration: Long)

    suspend fun processMarkdown(name: String?, originalContent: String, width: String = "600", timeout: Long = TIMEOUT): MarkdownResult = MarkdownLock.withLock {
        var duration = 0.0
        val result = run {
            val content = originalContent.ifBlank { "[警告] `content`内容为空或仅包含空白字符" }
            if (timeout <= 0L) {
                return@run MarkdownResult(false, "操作失败：执行时间已达总上限${TIMEOUT}秒", 0L)
            }

            val startTime = Instant.now()   // 记录开始时间
            try {
                logger.info("请求调用系统命令执行Markdown转图片")

                val tempFile = File("${cacheFolder}tmp.md")
                tempFile.writeText(content)
                val process = if (System.getProperty("os.name").lowercase().contains("windows")) {
                    ProcessBuilder(
                        "${cacheFolder}markdown2image.exe",
                        "--input=${cacheFolder}tmp.md",
                        "--width=$width",
                        "--output=${cacheFolder}markdown.png"
                    ).directory(File(".")).start()
                } else {
                    ProcessBuilder(
                        "${cacheFolder}markdown2image",
                        "--input=${cacheFolder}tmp.md",
                        "--width=$width",
                        "--output=${cacheFolder}markdown.png"
                    ).directory(File(".")).start()
                }
                // 在后台线程中监控进程的内存使用情况
                CoroutineScope(Dispatchers.IO).launch {
                    var seconds = 0
                    while (process.isAlive) {
                        val physicalUsage = osBean.totalPhysicalMemorySize - osBean.freePhysicalMemorySize
                        val swapUsage = osBean.totalSwapSpaceSize - osBean.freeSwapSpaceSize
                        val totalUsage = physicalUsage + swapUsage
                        if (totalUsage > SystemConfig.memoryLimit * 1024 * 1024) {
                            seconds++
                            if (seconds >= 5) {
                                logger.warning("监测到系统总内存使用超过${SystemConfig.memoryLimit}MB达到5秒，当前总内存：${totalUsage / 1024 / 1024}MB，程序进程被中断")
                                process.destroyForcibly()
                                break
                            }
                        }
                        delay(1000)
                    }
                }
                if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    duration = timeout.toDouble()
                    if (timeout == TIMEOUT) {
                        saveErrorRecord(content, "TimeoutError($timeout)")
                        return@run MarkdownResult(false, "执行超时：执行超出最大时间${timeout}秒限制，图片生成被中断。如需查看内容请联系管理员", timeout)
                    } else {
                        return@run MarkdownResult(false, "执行超时：执行超出剩余时间${timeout}秒限制，图片生成被中断", timeout)
                    }
                } else if (process.exitValue() != 0) {
                    saveErrorRecord(content, "ProcessError(${process.exitValue()})")
                    val endTime = Instant.now()     // 记录结束时间
                    duration = Duration.between(startTime, endTime).toMillis() / 1000.0
                    if (process.exitValue() == 137) {
                        return@run MarkdownResult(false, "操作失败：因内存占用过大被中断，超出系统安全内存限制。exitValue：137", ceil(duration).toLong())
                    }
                    return@run MarkdownResult(false, "操作失败：程序执行异常，请联系管理员查看后台报错记录。exitValue：${process.exitValue()}", ceil(duration).toLong())
                }
                tempFile.delete()

                val endTime = Instant.now()     // 记录结束时间
                duration = (Duration.between(startTime, endTime).toMillis() / 1000.0).roundTo2()
                logger.info("操作成功完成，用时${duration}秒")
                return@run MarkdownResult(true, "执行markdown转图片成功", ceil(duration).toLong())
            } catch (e: Exception) {
                logger.warning(e)
                saveErrorRecord("$e\n\n$content", "${e::class.simpleName}")
                return@run MarkdownResult(false, "操作失败：Kotlin运行错误【严重错误，理论不可能发生】，请提供日志反馈问题\n${e::class.simpleName}(${e.message})", 0L)
            } finally {
                if (name != null) Statistics.countMarkdown(name, duration)
            }
        }

        result
    }

    private fun saveErrorRecord(content: String, prefix: String) {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH.mm.ss")
        val dateTime = LocalDateTime.now().format(formatter)
        File("./data/${MiraiCompilerFramework.dataHolderName}/errors/${dateTime}_${prefix}.txt").writeText(content)
        logger.warning("${prefix}报错记录已保存为txt文件")
    }

    fun generatePastebinHtml(): String {
        val entriesList = PastebinData.pastebin.entries.toList()
        val pageLimit = ((entriesList.size + 19) / 20)
        // 每行 5 页
        val columnsPerRow = 5
        val rowCount = ((pageLimit + columnsPerRow - 1) / columnsPerRow)
        // 构造HTML字符串
        return buildString {
            append("""
            <style>
                h1 {
                    text-align: center;
                    margin-bottom: 16px;
                }
                .main-table {
                    width: 100%;
                    border-collapse: collapse;
                }
                .main-table td {
                    vertical-align: top;
                    padding: 8px;
                    width: 20%;
                }
                .page-caption {
                    text-align: center;
                    font-weight: bold;
                    margin-bottom: 4px;
                    font-size: 1.2em;
                }
                .inner-table {
                    width: 100%;
                    border-collapse: collapse;
                }
                .inner-table th, .inner-table td {
                    border: 1px solid #ccc;
                    padding: 6px;
                    font-size: 1.05em;
                }
                .inner-table th {
                    background-color: #f5f5f5;
                }
                .hot-1, .hot-2 {
                    color: #D81E06
                }
                .hot-3 {
                    color: #E98F36
                }
                .inner-table th.name-col, .inner-table td.name-col { width: 45%; }
                .inner-table th.lang-col, .inner-table td.lang-col { width: 20%; }
                .inner-table th.author-col, .inner-table td.author-col { width: 35%; }
            </style>
            """.trimIndent())
            appendLine("<h1>Pastebin完整列表</h1>")
            appendLine("<table class='main-table'><tbody>")
            for (row in 0 until rowCount) {
                appendLine("<tr>")
                for (col in 0 until columnsPerRow) {
                    val pageIndex = row * columnsPerRow + col
                    if (pageIndex < pageLimit) {
                        val start = pageIndex * 20
                        val end = minOf(start + 20, entriesList.size)
                        appendLine("<td><div class='page-caption'>第 ${pageIndex + 1} 页</div>")
                        appendLine("<table class='inner-table'><thead>")
                        appendLine("<tr><th class='name-col'>名称</th><th class='lang-col'>语言</th><th class='author-col'>作者</th></tr>")
                        appendLine("</thead><tbody>")
                        entriesList.subList(start, end).forEach { (key, value) ->
                            val score = ExtraData.statistics[key]?.get("score") ?: 0.0
                            val (style, fire) = when {
                                score >= 1000 -> " hot-1" to " <img src='${Image_Path}fire1.png' width='16' height='16' alt='f1'>"
                                score >= 300  -> " hot-2" to " <img src='${Image_Path}fire2.png' width='16' height='16' alt='f2'>"
                                score >= 50   -> " hot-3" to " <img src='${Image_Path}fire3.png' width='16' height='16' alt='f3'>"
                                else -> "" to ""
                            }
                            val language = value["language"] ?: "[数据异常]"
                            val author = value["author"] ?: "[数据异常]"
                            val censorNote = if (PastebinData.censorList.contains(key)) "（审核中）" else ""
                            appendLine("<tr>")
                            appendLine("<td class='name-col$style'>$key${fire}${censorNote}</td>")
                            appendLine("<td class='lang-col'>${language}</td>")
                            appendLine("<td class='author-col'>${author}</td>")
                            appendLine("</tr>")
                        }
                        appendLine("</tbody></table></td>")
                    } else {
                        appendLine("<td></td>")
                    }
                }
                appendLine("</tr>")
            }
            appendLine("</tbody></table>")
            appendLine("<p style='text-align:center; margin-top:16px;'>共 ${entriesList.size} 条，分 $pageLimit 页显示</p>")
        }
    }

}
