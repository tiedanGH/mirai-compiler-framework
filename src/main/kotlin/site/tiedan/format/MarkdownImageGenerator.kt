package site.tiedan.format

import site.tiedan.command.CommandRun.Image_Path
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.logger
import com.sun.management.OperatingSystemMXBean
import site.tiedan.config.SystemConfig
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import site.tiedan.MiraiCompilerFramework.TIMEOUT
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.command.CommandBucket.formatTime
import site.tiedan.command.CommandBucket.isBucketEmpty
import site.tiedan.command.CommandBucket.projectsCount
import site.tiedan.data.PastebinBucket
import site.tiedan.module.Statistics
import site.tiedan.module.Statistics.roundTo2
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@OptIn(ConsoleExperimentalApi::class)
object MarkdownImageGenerator {
    private val MarkdownLock = Mutex()
    // 操作系统相关信息（仅用于监测内存用量）
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
                        val physicalUsage = osBean.totalMemorySize - osBean.freeMemorySize
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

    fun generatePastebinListHtml(): String {
        val entriesList = PastebinData.pastebin.entries.toList()
        val pageLimit = ((entriesList.size + 19) / 20)
        val columnsPerRow = 5   // 每行 5 页
        val rowCount = ((pageLimit + columnsPerRow - 1) / columnsPerRow)

        return buildString {
            appendLine("""
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
                            appendLine("<td class='name-col$style'>${esc(key)}$fire$censorNote</td>")
                            appendLine("<td class='lang-col'>${esc(language)}</td>")
                            appendLine("<td class='author-col'>${esc(author)}</td>")
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

    fun generateBucketListHtml(showBackups: Boolean): String {
        return buildString {
            appendLine("""
            <style>
              :root{
                --card-height:80px;
                --square-size:var(--card-height);
                --stats-width:180px;
                --gap:10px
              }
              .container{color:#222;padding:12px}
              .title{font-size:20px;font-weight:700;text-align:center;margin-bottom:12px}
              .card{margin-bottom:var(--gap)}
              .bucket-table{
                width:100%;border-collapse:collapse;table-layout:fixed;
                height:var(--card-height);background:#fff;
                box-shadow:0 1px 2px rgba(0,0,0,0.04);
                border:2px solid #000;overflow:hidden
              }
              .bucket-table td{
                padding:0;border:1px solid #888;
                box-sizing:border-box;vertical-align:middle;text-align:center
              }
              .cell-image,.cell-id{
                width:var(--square-size);height:var(--square-size);box-sizing:border-box
              }
              .cell-inner{
                width:100%;height:100%;
                display:flex;align-items:center;justify-content:center;
                box-sizing:border-box;padding:8px;margin:0
              }
              .cell-image img{max-width:55px;max-height:55px;display:block;margin:0}
              .cell-id .cell-inner{font-size:38px;font-weight:700}
              .cell-meta{
                padding:0;box-sizing:border-box;width:auto;
                height:var(--card-height);
                display:flex;flex-direction:column;align-items:stretch;justify-content:center;
                overflow:hidden;flex:1
              }
              .meta-top{
                height:calc(var(--card-height)*0.75);
                display:flex;align-items:center;justify-content:center;
                font-size:18px;font-weight:600;padding:4px 8px;
                box-sizing:border-box;overflow:hidden;text-overflow:ellipsis;white-space:nowrap
              }
              .meta-divider{
                height:1px;width:100%;align-self:stretch;margin:0;
                background:#888;box-sizing:border-box;flex:0 0 auto
              }
              .meta-bottom{
                height:calc(var(--card-height)*0.25);
                display:flex;align-items:center;justify-content:center;
                font-size:12px;color:#666;padding:4px 8px;
                box-sizing:border-box;overflow:hidden;text-overflow:ellipsis;white-space:nowrap
              }
              .cell-stats{
                width:var(--stats-width);padding:0;box-sizing:border-box;
                height:var(--card-height);
                display:flex;flex-direction:column;align-items:stretch;justify-content:center;
                overflow:hidden;flex-shrink:0
              }
              .stats-row{
                height:calc(var(--card-height)*0.5);
                display:flex;align-items:center;justify-content:space-between;
                padding:4px 12px;box-sizing:border-box;overflow:hidden
              }
              .stats-row .label{
                font-size:14px;color:#000;flex:1;text-align:left;
                overflow:hidden;text-overflow:ellipsis;white-space:nowrap
              }
              .stats-row .value{
                font-size:15px;color:#000;flex:0 0 auto;font-weight:bold;
                text-align:right;padding-left:8px
              }
              .stats-divider{
                height:1px;width:100%;align-self:stretch;margin:0;
                background:#888;box-sizing:border-box;flex:0 0 auto
              }
              .empty{background:#d9d9d9;color:#7a7a7a}
              .empty .cell-image img{opacity:0.35}
              .empty .meta-top,.empty .meta-bottom,
              .empty .stats-row .label,.empty .stats-row .value{color:#7a7a7a}
              .backup-row td{padding:6px 8px;border-top:0}
              .backup-strip{display:flex;gap:8px;width:100%}
              .backup-cell{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:3px;border:1px solid #e0e0e0;background:#fafafa;box-sizing:border-box;height:42px;overflow:hidden}
              .backup-cell.empty{
                background:#e6e6e6;color:#7a7a7a;border-color:#d0d0d0;
                display:flex;align-items:center;justify-content:center;
              }
              .backup-cell.empty .backup-time{display:none}
              .backup-cell.empty .backup-name{margin:0}
              .backup-name{font-size:12px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;width:100%;text-align:center}
              .backup-time{font-size:10px;color:#666;margin-top:4px;text-align:center;width:100%;overflow:hidden;text-overflow:ellipsis}
              @media (max-width:520px){:root{--card-height:120px;--stats-width:160px}.cell-id .cell-inner{font-size:36px}.meta-top{font-size:18px}.stats-row .value{font-size:14px}}
            </style>
            """.trimIndent())
            appendLine("""<div class="container"><div class="title">bucket存储库列表</div>""")

            PastebinBucket.bucket.entries
                .sortedBy { it.key }
                .forEach { (id, data) ->
                    val empty = isBucketEmpty(id)
                    val imgPath = if (empty) "${Image_Path}bucket_e.png" else "${Image_Path}bucket.png"
                    appendLine("""
                    <div class="card">
                      <table class="bucket-table${if (empty) " empty" else ""}" role="presentation">
                        <tr>
                          <td class="cell-image"><div class="cell-inner"><img src="$imgPath" alt="bucket"/></div></td>
                          <td class="cell-id"><div class="cell-inner">$id</div></td>
                          <td class="cell-meta">
                            <div class="meta-top">${if (empty) "&nbsp;" else esc(data["name"])}</div>
                            <div class="meta-divider"></div>
                            <div class="meta-bottom">${if (empty) "&nbsp;" else "所有者：${esc(data["owner"])} (${esc(data["userID"])})"}</div>
                          </td>
                          <td class="cell-stats">
                            <div class="stats-row">
                              <div class="label">${if (empty) "&nbsp;" else "关联项目数"}</div>
                              <div class="value">${if (empty) "&nbsp;" else projectsCount(id)}</div>
                            </div>
                            <div class="stats-divider"></div>
                            <div class="stats-row">
                              <div class="label">${if (empty) "&nbsp;" else "存储库大小"}</div>
                              <div class="value">${if (empty) "&nbsp;" else data["content"]?.length ?: 0}</div>
                            </div>
                          </td>
                        </tr>
                """.trimIndent())
                    if (showBackups && !empty) {
                        val list = PastebinBucket.backups[id] ?: emptyList()
                        val firstThree = (0..2).map { idx -> list.getOrNull(idx) }
                        appendLine("""<tr class="backup-row"><td colspan="4"><div class="backup-strip">""")
                        firstThree.forEach { b ->
                            if (b == null) {
                                appendLine(
                                    """
                                <div class="backup-cell empty">
                                  <div class="backup-name">空备份</div>
                                  <div class="backup-time">&nbsp;</div>
                                </div>
                            """.trimIndent()
                                )
                            } else {
                                val bn = esc(b.name)
                                val bt = esc(formatTime(b.time))
                                appendLine(
                                    """
                                <div class="backup-cell">
                                  <div class="backup-name" title="$bn">$bn</div>
                                  <div class="backup-time">$bt</div>
                                </div>
                            """.trimIndent()
                                )
                            }
                        }
                        appendLine("</div></td></tr>")
                    }
                    appendLine("</table></div>")
                }
            appendLine("</div>")
        }
    }

    private fun esc(s: String?) = s
        ?.replace("&", "&amp;")
        ?.replace("<", "&lt;")
        ?.replace(">", "&gt;")
        ?.replace("\"", "&quot;")
        ?.replace("'", "&#39;")
        ?: ""
}
