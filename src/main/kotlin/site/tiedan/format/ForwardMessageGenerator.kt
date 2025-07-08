package site.tiedan.format

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.RawForwardMessage
import net.mamoe.mirai.message.data.buildForwardMessage
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.format.Base64Processor.fileToMessage
import site.tiedan.format.Base64Processor.processBase64
import site.tiedan.format.JsonProcessor.JsonMessage
import site.tiedan.format.JsonProcessor.generateMessageChain
import site.tiedan.format.JsonProcessor.json
import site.tiedan.format.MarkdownImageGenerator.TIMEOUT
import site.tiedan.format.MarkdownImageGenerator.cacheFolder
import site.tiedan.format.MarkdownImageGenerator.processMarkdown
import site.tiedan.module.PastebinCodeExecutor.renderLatexOnline
import site.tiedan.utils.DownloadHelper.downloadImage
import java.io.File
import java.net.URI

object ForwardMessageGenerator {
    @Serializable
    data class JsonForwardMessage(
        val title: String = "运行结果",
        val brief: String = "[输出内容]",
        val preview: List<String> = listOf("无预览"),
        val summary: String = "聊天记录",
        val name: String = "输出内容",
        val messages: List<JsonMessage> = listOf(JsonMessage()),
        val storage: String? = null,
        val global: String? = null,
    )

    data class ForwardMessageData(val forwardMessage: ForwardMessage, val global: String? = null, val storage: String? = null)

    suspend fun generateForwardMessage(name: String, forwardMessageOutput: String, sender: CommandSender): ForwardMessageData {
        val subject = sender.subject
        val result = try {
            json.decodeFromString<JsonForwardMessage>(forwardMessageOutput)
        } catch (e: Exception) {
            val forward = buildForwardMessage(subject!!) {
                displayStrategy = object : ForwardMessage.DisplayStrategy {
                    override fun generateTitle(forward: RawForwardMessage): String = "输出解析错误"
                    override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("执行失败：JSON解析错误")
                }
                subject.bot named "Error" says "[错误] JSON解析错误：\n${e.message}"
                val (resultString, tooLong) = trimToMaxLength(forwardMessageOutput, 10000)
                if (tooLong) {
                    subject.bot named "Error" says "原始输出过大，仅截取前10000个字符"
                }
                subject.bot named "原始输出" says "程序原始输出：\n$resultString"
            }
            return ForwardMessageData(forward)
        }
        try {
            val forward = buildForwardMessage(subject!!) {
                displayStrategy = object : ForwardMessage.DisplayStrategy {
                    override fun generateTitle(forward: RawForwardMessage): String = result.title
                    override fun generateBrief(forward: RawForwardMessage): String = result.brief
                    override fun generatePreview(forward: RawForwardMessage): List<String> = result.preview
                    override fun generateSummary(forward: RawForwardMessage): String = result.summary
                }
                var timeUsed: Long = 0
                for (m in result.messages) {
                    val content = m.content
                    when (m.format) {
                        "text"-> {
                            subject.bot named result.name says content
                        }
                        "markdown"-> {
                            val markdownResult = processMarkdown(name, content, m.width.toString(), TIMEOUT - timeUsed)
                            timeUsed += markdownResult.duration
                            if (!markdownResult.success) {
                                subject.bot named "Error" says "[markdown2image错误] ${markdownResult.message}"
                                continue
                            }
                            try {
                                val image = subject.uploadFileToImage(File("${cacheFolder}markdown.png"))
                                if (image == null)
                                    subject.bot named result.name says "[错误] 图片文件异常：ExternalResource上传失败"
                                else
                                    subject.bot named result.name says image       // 添加图片消息
                            } catch (e: Exception) {
                                logger.warning(e)
                                subject.bot named "Error" says "[错误] 图片文件异常：${e.message}"
                            }
                        }
                        "base64"-> {
                            val base64Result = processBase64(content)
                            if (!base64Result.success) {
                                subject.bot named "Error" says base64Result.extension
                                continue
                            }
                            subject.bot named result.name says (
                                fileToMessage(
                                    base64Result.fileType,
                                    base64Result.extension,
                                    subject,
                                    true
                                ) ?: PlainText("[错误] Base64文件转换时出现未知错误，请联系管理员")
                            )
                        }
                        "image"-> {
                            val file = if (content.startsWith("file:///")) {
                                File(URI(content))
                            } else {
                                val downloadResult = downloadImage(name, content, cacheFolder, "image", TIMEOUT - timeUsed, force = true)
                                timeUsed += downloadResult.duration
                                if (!downloadResult.success) {
                                    subject.bot named "Error" says downloadResult.message
                                    continue
                                }
                                File("${cacheFolder}image")
                            }
                            try {
                                if (!file.exists()) {
                                    subject.bot named "Error" says "[错误] 本地图片文件不存在，请检查路径"
                                    continue
                                }
                                val image = subject.uploadFileToImage(file)
                                if (image == null)
                                    subject.bot named result.name says "[错误] 图片文件异常：ExternalResource上传失败"
                                else
                                    subject.bot named result.name says image       // 添加图片消息
                            } catch (e: Exception) {
                                logger.warning(e)
                                subject.bot named "Error" says "[错误] 图片文件异常：${e.message}"
                            }
                        }
                        "LaTeX"-> {
                            val renderResult = renderLatexOnline(content)
                            if (renderResult.startsWith("QuickLaTeX")) {
                                subject.bot named "Error" says "[错误] $renderResult"
                            }
                            try {
                                val image = subject.uploadFileToImage(File("${cacheFolder}latex.png"))
                                if (image == null)
                                    subject.bot named result.name says "[错误] 图片文件异常：ExternalResource上传失败"
                                else
                                    subject.bot named result.name says image       // 添加图片消息
                            } catch (e: Exception) {
                                logger.warning(e)
                                subject.bot named "Error" says "[错误] 图片文件异常：${e.message}"
                            }
                        }
                        // json分支功能MessageChain
                        "MessageChain"-> {
                            val pair = generateMessageChain(name, m, sender, timeUsed)
                            timeUsed = pair.second
                            subject.bot named result.name says pair.first
                        }
                        "json", "ForwardMessage" -> {
                            subject.bot named "Error" says "[错误] 不支持在JsonMessage内使用“${m.format}”输出格式"
                        }
                        "MultipleMessage"-> {
                            subject.bot named "Error" says "[错误] 不支持在ForwardMessage内使用“${m.format}”输出格式"
                        }
                        else -> {
                            subject.bot named "Error" says "[错误] 无效的输出格式：${m.format}，请检查此条消息的format参数"
                        }
                    }
                }
            }
            return ForwardMessageData(forward, result.global, result.storage)
        } catch (e: Exception) {
            logger.warning(e)
            val forward = buildForwardMessage(subject!!) {
                displayStrategy = object : ForwardMessage.DisplayStrategy {
                    override fun generateTitle(forward: RawForwardMessage): String = "转发消息生成错误"
                    override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("执行失败：发生未知错误")
                }
                subject.bot named "Error" says "[错误] 转发消息生成错误：\n${e.message}"
                subject.bot named "Error" says "程序原始输出：\n$forwardMessageOutput"
            }
            return ForwardMessageData(forward)
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
                            "提示: 输出内容超出消息最大上限30000字符",
                            "（10000中文字符），多余部分已被截断"
                        )
                    } else {
                        listOf("输出内容: ${sb.take(30)}...")
                    }
                override fun generateSummary(forward: RawForwardMessage): String =
                    "输出长度总计 ${sb.length} 字符"
            }
            subject.bot named "输出内容" says resultString
        }
    }

    fun trimToMaxLength(input: String, maxLength: Int = 30000): Pair<String, Boolean> {
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
