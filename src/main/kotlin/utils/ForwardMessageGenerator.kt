package utils

import JCompilerCollection.logger
import utils.JsonProcessor.JsonForwardMessage
import utils.JsonProcessor.generateMessageChain
import utils.JsonProcessor.json
import utils.MarkdownImageProcessor.TIMEOUT
import utils.MarkdownImageProcessor.folder
import utils.MarkdownImageProcessor.processMarkdown
import kotlinx.serialization.decodeFromString
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.RawForwardMessage
import net.mamoe.mirai.message.data.buildForwardMessage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File

object ForwardMessageGenerator {

    // 解析json并生成转发消息
    suspend fun generateForwardMessage(forwardMessageOutput: String, sender: CommandSender): Triple<ForwardMessage, String?, String?> {
        val result = try {
            json.decodeFromString<JsonForwardMessage>(forwardMessageOutput)
        } catch (ex: Exception) {
            val forward = buildForwardMessage(sender.subject!!) {
                displayStrategy = object : ForwardMessage.DisplayStrategy {
                    override fun generateTitle(forward: RawForwardMessage): String = "执行失败：发生错误"
                    override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("JSON解析错误")
                }
                sender.subject!!.bot named "Error" says "JSON解析错误：\n${ex.message}"
                sender.subject!!.bot named "Error" says "程序原始输出：\n$forwardMessageOutput"
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
                            } catch (ex: Exception) {
                                logger.warning(ex)
                                sender.subject!!.bot named "Error" says "[错误] 图片文件异常：${ex.message}"
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
                            } catch (ex: Exception) {
                                logger.warning(ex)
                                sender.subject!!.bot named "Error" says "[错误] 图片文件异常：${ex.message}"
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
                        else -> {
                            sender.subject!!.bot named "Error" says "[错误] 无效的输出格式：${m.format}，请检查此条消息的format参数"
                        }
                    }
                }
            }
            return Triple(forward, result.global, result.storage)
        } catch (ex: Exception) {
            logger.warning(ex)
            val forward = buildForwardMessage(sender.subject!!) {
                displayStrategy = object : ForwardMessage.DisplayStrategy {
                    override fun generateTitle(forward: RawForwardMessage): String = "执行失败：发生错误"
                    override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("转发消息生成错误")
                }
                sender.subject!!.bot named "Error" says "转发消息生成错误：\n${ex.message}"
                sender.subject!!.bot named "Error" says "程序原始输出：\n$forwardMessageOutput"
            }
            return Triple(forward, null, null)
        }
    }


    // 字符串转ForwardMessage
    fun stringToForwardMessage(sb: StringBuilder, subject: Contact?): ForwardMessage {
        val (resultString, tooLong) = trimToMaxLength(sb.toString())
        return buildForwardMessage(subject!!) {
            displayStrategy = object : ForwardMessage.DisplayStrategy {
                override fun generateTitle(forward: RawForwardMessage): String = "输出过长，请查看聊天记录"
                override fun generateBrief(forward: RawForwardMessage): String = "[输出内容]"
                override fun generatePreview(forward: RawForwardMessage): List<String> =
                    if (tooLong) {
                        listOf(
                            "提示: 输出内容超过最大上限15000字符（5000中文字符），从14000开始已被截断",
                            "输出内容: ${sb.substring(0, 30)}..."
                        )
                    } else {
                        listOf("输出内容: ${sb.substring(0, 50)}...")
                    }
                override fun generateSummary(forward: RawForwardMessage): String =
                    "输出长度总计 ${sb.length} 字符"
            }
            if (tooLong) {
                subject.bot named "提示" says "输出内容超过最大上限15000字符（5000中文字符），从14000开始已被截断"
                subject.bot named "输出内容" says resultString
            } else {
                subject.bot named "输出内容" says sb.toString()
            }
        }
    }

    private fun isWideChar(code: Int): Boolean {
        return code in 0x4E00..0x9FFF ||    // 基本汉字
                code in 0x3400..0x4DBF ||   // 扩展 A
                code in 0x20000..0x2A6DF || // 扩展 B
                code in 0x2A700..0x2B73F || // 扩展 C
                code in 0x2B740..0x2B81F || // 扩展 D
                code in 0x2B820..0x2CEAF || // 扩展 E
                code in 0x2CEB0..0x2EBEF || // 扩展 F
                code in 0x30000..0x3134F || // 扩展 G
                code in 0x31350..0x323AF || // 扩展 H
                code in 0xF900..0xFAFF ||   // CJK 兼容表意字符
                code in 0x3000..0x303F ||   // CJK 符号和标点
                code in 0x1F300..0x1F64F || // 部分 Emoji
                code in 0x1F900..0x1F9FF || // 扩展 Emoji
                code in 0x2FF0..0x2FFF ||   // 结构标记
                code in 0xFF00..0xFFEF      // 全角字符
    }

    private fun trimToMaxLength(input: String, maxLength: Int = 14000): Pair<String, Boolean> {
        var currentCount = 0
        val resultString = StringBuilder()
        val trimmedString = input.takeWhile { char ->
            val charCount = if (isWideChar(char.code)) 3 else 1
            if (currentCount + charCount <= maxLength) {
                currentCount += charCount
                resultString.append(char)
                true
            } else {
                false
            }
        }
        return Pair(resultString.toString(), trimmedString.length < input.length)
    }

}
