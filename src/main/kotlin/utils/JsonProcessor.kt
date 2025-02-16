package utils

import JCompilerCollection.logger
import JCompilerCollection.save
import utils.MarkdownImageProcessor.TIMEOUT
import utils.MarkdownImageProcessor.folder
import utils.MarkdownImageProcessor.processMarkdown
import data.PastebinStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File

object JsonProcessor {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    @Serializable
    data class JsonMessage(
        val format: String = "text",
        val at: Boolean = true,
        val width: Int = 600,
        val content: String = "空消息",
        val messageList: List<JsonSingleMessage> = listOf(JsonSingleMessage()),
        val storage: String? = null,
        val global: String? = null,
        val error: String = "",
    )
    @Serializable
    data class JsonSingleMessage(
        val format: String = "text",
        val width: Int = 600,
        val content: String = "空消息",
    )
    @Serializable
    data class JsonForwardMessage(
        val title: String = "运行结果",
        val brief: String = "[输出内容]",
        val preview: List<String> = listOf("无预览"),
        val summary: String = "查看转发消息",
        val name: String = "输出内容",
        val messages: List<JsonMessage> = listOf(JsonMessage()),
        val storage: String? = null,
        val global: String? = null,
    )
    @Serializable
    data class JsonStorage(
        val global: String = "",
        val storage: String = "",
        val userID: Long = 10001,
        val nickname: String = "",
        val from: String = ""
    )

    fun processDecode(jsonOutput: String): JsonMessage {
        return try {
            json.decodeFromString<JsonMessage>(jsonOutput)
        } catch (ex: Exception) {
            JsonMessage(error = "JSON解析错误：\n${ex.message}")
        }
    }

    fun processEncode(global: String, storage: String, userID: Long, nickname: String, from: String): String {
        return try {
            val jsonStorageObject = JsonStorage(global, storage, userID, nickname, from)
            json.encodeToJsonElement<JsonStorage>(jsonStorageObject).toString()
        } catch (ex: Exception) {
            "JSON编码错误【严重错误，理论不可能发生】，请务必联系铁蛋排查bug：\n${ex.message}"
        }
    }

    suspend fun generateMessageChain(ret: JsonMessage, sender: CommandSender, timeUsedRecord: Long = 0): Pair<MessageChainBuilder, Long> {
        val builder = MessageChainBuilder()
        if (sender.subject is Group && ret.at) {
            builder.add(At(sender.user!!))
            builder.add("\n")
        }
        var timeUsed = timeUsedRecord
        for ((index, m) in ret.messageList.withIndex()) {
            if (index > 0) builder.add("\n")
            var content = m.content
            when (m.format) {
                "text" -> {
                    builder.add(if (content.isBlank()) "　" else if(index == 0) blockSensitiveContent(content, ret.at) else content)
                }
                "markdown", "base64" -> {
                    if (m.format == "base64") content = "![base64image]($content)"
                    val processResult = processMarkdown(content, m.width.toString(), TIMEOUT - timeUsed)
                    timeUsed += processResult.second
//                    sender.sendMessage("[DEBUG] timeUsed in MessageChainGenerator: $timeUsed")
                    if (processResult.first.startsWith("操作失败")) {
                        builder.add("[markdown2image错误] ${processResult.first}")
                        continue
                    }
                    val file = File("${folder}/markdown.png")
                    try {
                        val image = file.toExternalResource().use { resource ->
                            sender.subject!!.uploadImage(resource)
                        }
                        builder.add(image)      // 添加图片消息
                    } catch (ex: Exception) {
                        logger.warning(ex)
                        builder.add("[错误] 图片文件异常：${ex.message}")
                    }
                }
                "LaTeX"-> {
                    val renderResult = renderLatexOnline(content)
                    if (renderResult.startsWith("QuickLaTeX")) {
                        builder.add("[错误] $renderResult")
                    }
                    val file = File("${folder}/latex.png")
                    try {
                        val image = file.toExternalResource().use { resource ->
                            sender.subject!!.uploadImage(resource)
                        }
                        builder.add(image)      // 添加图片消息
                    } catch (ex: Exception) {
                        logger.warning(ex)
                        builder.add("[错误] 图片文件异常：${ex.message}")
                    }
                }
                "json", "ForwardMessage", "MessageChain" -> {
                    builder.add("[错误] 不支持在JsonSingleMessage内使用“${m.format}”输出格式")
                }
                else -> {
                    builder.add("[错误] 无效的输出格式：${m.format}，请检查此条消息的format参数")
                }
            }
        }
        return Pair(builder, timeUsed)
    }

    // pastebin数据存储
    fun savePastebinStorage(name: String, userID: Long, global: String?, storage: String?) {
        if (global == null && storage == null) return
        logger.info("保存Storage数据：global{${global?.length}} storage{${storage?.length}}")
        if (PastebinStorage.Storage.contains(name).not()) {
            PastebinStorage.Storage[name] = mutableMapOf()
            PastebinStorage.Storage[name]?.set(0, "")
        }
        if (global != null) {
            PastebinStorage.Storage[name]?.set(0, global)
        }
        if (storage != null) {
            if (storage.isEmpty()) {
                PastebinStorage.Storage[name]?.remove(userID)
            } else {
                PastebinStorage.Storage[name]?.set(userID, storage)
            }
        }
        PastebinStorage.save()
    }

    // 检查json和MessageChain中的敏感内容，发现则返回覆盖文本
    fun blockSensitiveContent(content: String, at: Boolean): String {
        if (at) return content
        val blockedContent = "[警告] 首条消息中检测到指令或易引发多bot冲突的高危内容，请开启`at`参数或修改内容来避免此警告。如发生误报请反馈给铁蛋"
        if (content.trimStart().startsWith("/")) return blockedContent
        val words = listOf("transfer", "转账", "new", "游戏", "join", "加入")
        for (word in words)
            if (word in content)
                return blockedContent
        return content
    }
}
