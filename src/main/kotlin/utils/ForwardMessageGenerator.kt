package utils

import CommandRun.renderLatexOnline
import JCompilerCollection
import JCompilerCollection.logger
import kotlinx.serialization.decodeFromString
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.RawForwardMessage
import net.mamoe.mirai.message.data.buildForwardMessage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import utils.DownloadHelper.downloadImage
import utils.JsonProcessor.JsonForwardMessage
import utils.JsonProcessor.generateMessageChain
import utils.JsonProcessor.json
import utils.MarkdownImageProcessor.TIMEOUT
import utils.MarkdownImageProcessor.folder
import utils.MarkdownImageProcessor.processMarkdown
import java.io.File
import java.net.URI

object ForwardMessageGenerator {

    // 解析json并生成转发消息
    suspend fun generateForwardMessage(forwardMessageOutput: String, sender: CommandSender): Triple<ForwardMessage, String?, String?> {
        val result = try {
            json.decodeFromString<JsonForwardMessage>(forwardMessageOutput)
        } catch (e: Exception) {
            val forward = buildForwardMessage(sender.subject!!) {
                displayStrategy = object : ForwardMessage.DisplayStrategy {
                    override fun generateTitle(forward: RawForwardMessage): String = "JSON解析错误"
                    override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("[执行失败] 发生错误")
                }
                sender.subject!!.bot named "Error" says "[错误] JSON解析错误：\n${e.message}"
                val (resultString, tooLong) = trimToMaxLength(forwardMessageOutput, 10000)
                if (tooLong) {
                    sender.subject!!.bot named "Error" says "原始输出过大，仅截取前10000个字符"
                }
                sender.subject!!.bot named "原始输出" says "程序原始输出：\n$resultString"
            }
            return Triple(forward, null, null)
        }
        try {
            val forward = buildForwardMessage(sender.subject!!) {
                displayStrategy = object : ForwardMessage.DisplayStrategy {
                    override fun generateTitle(forward: RawForwardMessage): String = result.title
                    override fun generateBrief(forward: RawForwardMessage): String = result.brief
                    override fun generatePreview(forward: RawForwardMessage): List<String> = result.preview
                    override fun generateSummary(forward: RawForwardMessage): String = result.summary
                }
                var timeUsed: Long = 0
                for (m in result.messages) {
                    var content = m.content
                    when (m.format) {
                        "text" -> {
                            sender.subject!!.bot named result.name says content
                        }
                        "markdown", "base64" -> {
                            if (m.format == "base64") content = "![base64image]($content)"
                            val processResult = processMarkdown(content, m.width.toString(), TIMEOUT - timeUsed)
                            timeUsed += processResult.second
//                            sender.sendMessage("[DEBUG] timeUsed in ForwardMessageGenerator: $timeUsed")
                            if (processResult.first.startsWith("操作失败")) {
                                sender.subject!!.bot named "Error" says "[markdown2image错误] ${processResult.first}"
                                continue
                            }
                            val file = File("${folder}/markdown.png")
                            try {
                                sender.subject!!.bot named result.name says {   // 添加图片消息
                                    add(file.toExternalResource().use { resource ->
                                        sender.subject!!.uploadImage(resource)
                                    })
                                }
                            } catch (e: Exception) {
                                logger.warning(e)
                                sender.subject!!.bot named "Error" says "[错误] 图片文件异常：${e.message}"
                            }
                        }
                        "image"-> {
                            val file = if (content.startsWith("file:///")) {
                                File(URI(content))
                            } else {
                                @OptIn(ConsoleExperimentalApi::class)
                                val outputFilePath = "./data/${JCompilerCollection.dataHolderName}/"
                                val pair = downloadImage(content, outputFilePath, "image.tmp", TIMEOUT - timeUsed, force = true)
                                timeUsed += pair.second
                                if (pair.first.startsWith("[错误]")) {
                                    sender.subject!!.bot named "Error" says pair.first
                                    continue
                                }
                                logger.info("图片下载完成，用时${pair.second}秒")
                                File("${folder}/image.tmp")
                            }
                            try {
                                if (!file.exists()) {
                                    sender.subject!!.bot named "Error" says "[错误] 本地图片文件不存在，请检查路径"
                                    continue
                                }
                                sender.subject!!.bot named result.name says {   // 添加图片消息
                                    add(file.toExternalResource().use { resource ->
                                        sender.subject!!.uploadImage(resource)
                                    })
                                }
                            } catch (e: Exception) {
                                logger.warning(e)
                                sender.subject!!.bot named "Error" says "[错误] 图片文件异常：${e.message}"
                            }
                        }
                        "LaTeX"-> {
                            val renderResult = renderLatexOnline(content)
                            if (renderResult.startsWith("QuickLaTeX")) {
                                sender.subject!!.bot named "Error" says "[错误] $renderResult"
                            }
                            val file = File("${folder}/latex.png")
                            try {
                                sender.subject!!.bot named result.name says {   // 添加图片消息
                                    add(file.toExternalResource().use { resource ->
                                        sender.subject!!.uploadImage(resource)
                                    })
                                }
                            } catch (e: Exception) {
                                logger.warning(e)
                                sender.subject!!.bot named "Error" says "[错误] 图片文件异常：${e.message}"
                            }
                        }
                        // json分支功能MessageChain
                        "MessageChain"-> {
                            val pair = generateMessageChain(m, sender, timeUsed)
                            timeUsed = pair.second
                            sender.subject!!.bot named result.name says pair.first.build()
                        }
                        "json", "ForwardMessage" -> {
                            sender.subject!!.bot named "Error" says "[错误] 不支持在JsonMessage内使用“${m.format}”输出格式"
                        }
                        "MultipleMessage"-> {
                            sender.subject!!.bot named "Error" says "[错误] 不支持在ForwardMessage内使用“${m.format}”输出格式"
                        }
                        else -> {
                            sender.subject!!.bot named "Error" says "[错误] 无效的输出格式：${m.format}，请检查此条消息的format参数"
                        }
                    }
                }
            }
            return Triple(forward, result.global, result.storage)
        } catch (e: Exception) {
            logger.warning(e)
            val forward = buildForwardMessage(sender.subject!!) {
                displayStrategy = object : ForwardMessage.DisplayStrategy {
                    override fun generateTitle(forward: RawForwardMessage): String = "转发消息生成错误"
                    override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("[执行失败] 发生错误")
                }
                sender.subject!!.bot named "Error" says "[错误] 转发消息生成错误：\n${e.message}"
                sender.subject!!.bot named "Error" says "程序原始输出：\n$forwardMessageOutput"
            }
            return Triple(forward, null, null)
        }
    }


    // 字符串转ForwardMessage
    fun stringToForwardMessage(sb: StringBuilder, subject: Contact?, title: String? = null): ForwardMessage {
        val (resultString, tooLong) = trimToMaxLength(sb.toString())
        return buildForwardMessage(subject!!) {
            displayStrategy = object : ForwardMessage.DisplayStrategy {
                override fun generateTitle(forward: RawForwardMessage): String =
                    title ?: if (tooLong) "输出内容超限，已截断" else "输出过长，请查看聊天记录"
                override fun generateBrief(forward: RawForwardMessage): String = "[输出内容]"
                override fun generatePreview(forward: RawForwardMessage): List<String> =
                    if (tooLong) {
                        listOf(
                            "提示: 输出内容超出消息最大上限15000字符（5000中文字符），多余部分已被截断",
                            "输出内容: ${sb.take(30)}..."
                        )
                    } else {
                        listOf("输出内容: ${sb.take(50)}...")
                    }
                override fun generateSummary(forward: RawForwardMessage): String =
                    "输出长度总计 ${sb.length} 字符"
            }
            subject.bot named "输出内容" says resultString
        }
    }

    fun trimToMaxLength(input: String, maxLength: Int = 15000): Pair<String, Boolean> {
        var currentCount = 0
        val sb = StringBuilder()
        for (ch in input) {
            val len = ch.chineseLength
            if (currentCount + len > maxLength) {
                return sb.toString() to true
            }
            sb.append(ch)
            currentCount += len
        }
        return sb.toString() to false
    }

    private val Char.chineseLength: Int
        get() {
            return when (this) {
                in '\u0000'..'\u007F' -> 1
                in '\u0080'..'\u07FF' -> 2
                in '\u0800'..'\uFFFF' -> 3
                else -> 4
            }
        }

}
