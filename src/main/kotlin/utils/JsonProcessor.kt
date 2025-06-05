package utils

import commands.CommandRun.renderLatexOnline
import MiraiCompilerFramework.logger
import MiraiCompilerFramework.save
import MiraiCompilerFramework.uploadFileToImage
import data.PastebinStorage
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChainBuilder
import utils.DownloadHelper.downloadImage
import utils.MarkdownImageProcessor.TIMEOUT
import utils.MarkdownImageProcessor.cacheFolder
import utils.MarkdownImageProcessor.processMarkdown
import java.io.File
import java.net.URI

object JsonProcessor {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    @Serializable
    data class JsonMessage(
        val format: String = "text",
        val at: Boolean = true,
        val width: Int = 600,
        val content: String = "空消息",
        val messageList: List<JsonSingleMessage> = listOf(JsonSingleMessage()),
        val active: ActiveMessage? = null,
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
    data class ActiveMessage(
        val group: Long? = null,
        val content: String = "空消息",
        val private: List<SinglePrivateMessage>? = null,
    )
    @Serializable
    data class SinglePrivateMessage(
        val userID: Long? = null,
        val content: String = "空消息",
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
        } catch (e: Exception) {
            JsonMessage(error = "JSON解析错误：\n${e.message}")
        }
    }

    fun processEncode(global: String, storage: String, userID: Long, nickname: String, from: String): String {
        return try {
            val jsonStorageObject = JsonStorage(global, storage, userID, nickname, from)
            json.encodeToString<JsonStorage>(jsonStorageObject)
        } catch (e: Exception) {
            throw Exception("JSON编码错误【严重错误，理论不可能发生】，请务必联系铁蛋排查bug：\n${e.message}")
        }
    }

    suspend fun generateMessageChain(name: String, jsonMessage: JsonMessage, sender: CommandSender, timeUsedRecord: Long = 0): Pair<MessageChainBuilder, Long> {
        val builder = MessageChainBuilder()
        if (sender.subject is Group && jsonMessage.at) {
            builder.add(At(sender.user!!))
            builder.add("\n")
        }
        var timeUsed = timeUsedRecord
        for ((index, m) in jsonMessage.messageList.withIndex()) {
            if (index > 0) builder.add("\n")
            var content = m.content
            when (m.format) {
                "text" -> {
                    builder.add(if (content.isBlank()) "　" else if(index == 0) blockSensitiveContent(content, jsonMessage.at, sender.subject is Group) else content)
                }
                "markdown", "base64" -> {
                    if (m.format == "base64") content = "![base64image]($content)"
                    val processResult = processMarkdown(name, content, m.width.toString(), TIMEOUT - timeUsed)
                    timeUsed += processResult.duration
                    if (!processResult.success) {
                        builder.add("[markdown2image错误] ${processResult.message}")
                        continue
                    }
                    builder.addImageFromFile("${cacheFolder}markdown.png", sender)
                }
                "image"-> {
                    if (content.startsWith("file:///")) {
                        if (!File(URI(content)).exists()) {
                            builder.add("[错误] 本地图片文件不存在，请检查路径")
                            continue
                        }
                        builder.addImageFromFile(content, sender)
                    } else {
                        val downloadResult = downloadImage(name, content, cacheFolder, "image", TIMEOUT - timeUsed, force = true)
                        timeUsed += downloadResult.duration
                        if (!downloadResult.success) {
                            builder.add(downloadResult.message)
                            continue
                        }
                        builder.addImageFromFile("${cacheFolder}image", sender)
                    }
                }
                "LaTeX"-> {
                    val renderResult = renderLatexOnline(content)
                    if (renderResult.startsWith("QuickLaTeX")) {
                        builder.add("[错误] $renderResult")
                        continue
                    }
                    builder.addImageFromFile("${cacheFolder}latex.png", sender)
                }
                "json", "ForwardMessage", "MessageChain", "MultipleMessage" -> {
                    builder.add("[错误] 不支持在JsonSingleMessage内使用“${m.format}”输出格式")
                }
                else -> {
                    builder.add("[错误] 无效的输出格式：${m.format}，请检查此条消息的format参数")
                }
            }
        }
        return Pair(builder, timeUsed)
    }

    private suspend fun MessageChainBuilder.addImageFromFile(filePath: String, sender: CommandSender) {
        val file = if (filePath.startsWith("file:///")) {
            File(URI(filePath))
        } else {
            File(filePath)
        }
        try {
            val image = sender.subject?.uploadFileToImage(file)
            if (image == null)
                add("[错误] 图片文件异常：ExternalResource上传失败")
            else
                add(image)      // 添加图片消息
        } catch (e: Exception) {
            logger.warning(e)
            add("[错误] 图片文件异常：${e.message}")
        }
    }

    suspend fun outputMultipleMessage(name: String, jsonMessage: JsonMessage, sender: CommandSender): String? {
        try {
            var timeUsed: Long = 0
            for ((index, m) in jsonMessage.messageList.withIndex()) {
                if (index >= 15) {
                    return "单次执行消息上限为15条"
                }
                var content = m.content
                when (m.format) {
                    "text" -> {
                        val builder = MessageChainBuilder()
                        if (sender.subject is Group && jsonMessage.at) {
                            builder.add(At(sender.user!!))
                            builder.add("\n")
                        }
                        builder.add(if (content.isBlank()) "　" else blockSensitiveContent(content, jsonMessage.at, sender.subject is Group))
                        sender.sendMessage(builder.build())
                    }
                    "markdown", "base64" -> {
                        if (m.format == "base64") content = "![base64image]($content)"
                        val processResult = processMarkdown(name, content, m.width.toString(), TIMEOUT - timeUsed)
                        timeUsed += processResult.duration
                        if (!processResult.success) {
                            sender.sendMessage("[markdown2image错误] ${processResult.message}")
                            continue
                        }
                        sendLocalImage("${cacheFolder}markdown.png", sender)
                    }
                    "image"-> {
                        if (content.startsWith("file:///")) {
                            if (!File(URI(content)).exists()) {
                                sender.sendMessage("[错误] 本地图片文件不存在，请检查路径")
                                continue
                            }
                            sendLocalImage(content, sender)
                        } else {
                            val downloadResult = downloadImage(name, content, cacheFolder, "image", TIMEOUT - timeUsed, force = true)
                            timeUsed += downloadResult.duration
                            if (!downloadResult.success) {
                                sender.sendMessage(downloadResult.message)
                                continue
                            }
                            sendLocalImage("${cacheFolder}image", sender)
                        }
                    }
                    "LaTeX"-> {
                        val renderResult = renderLatexOnline(content)
                        if (renderResult.startsWith("QuickLaTeX")) {
                            sender.sendMessage("[错误] $renderResult")
                            continue
                        }
                        sendLocalImage("${cacheFolder}latex.png", sender)
                    }
                    "json", "ForwardMessage", "MessageChain", "MultipleMessage" -> {
                        sender.sendMessage("[错误] 不支持在JsonSingleMessage内使用“${m.format}”输出格式")
                    }
                    else -> {
                        sender.sendMessage("[错误] 无效的输出格式：${m.format}，请检查此条消息的format参数")
                    }
                }
                delay(2000)
            }
            logger.info("MultipleMessage输出完成")
            return null
        } catch (e: Exception) {
            return e.message
        }
    }

    private suspend fun sendLocalImage(filePath: String, sender: CommandSender) {
        val file = if (filePath.startsWith("file:///")) {
            File(URI(filePath))
        } else {
            File(filePath)
        }
        try {
            val image = sender.subject?.uploadFileToImage(file)
            if (image == null)
                sender.sendMessage("[错误] 图片文件异常：ExternalResource上传失败")
            else
                sender.sendMessage(image)       // 发送图片
        } catch (e: Exception) {
            logger.warning(e)
            sender.sendMessage("[错误] 图片文件异常：${e.message}")
        }
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
    fun blockSensitiveContent(content: String, at: Boolean, isGroup: Boolean): String {
        if (isGroup) {
            if (at) return content
            val blockedContent = "[警告] 首条消息中检测到指令或易引发多bot冲突的高危内容，请开启`at`参数或修改内容来避免此警告。如发生误报请反馈给铁蛋"
            if (content.trimStart().startsWith("/")) return blockedContent
            val words = listOf("transfer", "new", "join", " tiedan ", "新游戏")
            for (word in words)
                if (word in content)
                    return blockedContent
            return content
        } else {
            val blockedContent = "[警告] 私信输出中检测到被禁用的内容，请修改内容来避免此警告。如发生误报请反馈给铁蛋"
            if (content.startsWith("新游戏积分")) return blockedContent
            return content
        }
    }
}
