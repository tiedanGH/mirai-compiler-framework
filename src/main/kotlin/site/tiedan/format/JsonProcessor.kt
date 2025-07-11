package site.tiedan.format

import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.config.SystemConfig
import site.tiedan.data.PastebinStorage
import site.tiedan.format.Base64Processor.fileToMessage
import site.tiedan.format.Base64Processor.processBase64
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import site.tiedan.utils.DownloadHelper.downloadImage
import site.tiedan.format.MarkdownImageGenerator.TIMEOUT
import site.tiedan.format.MarkdownImageGenerator.cacheFolder
import site.tiedan.format.MarkdownImageGenerator.processMarkdown
import net.mamoe.mirai.message.data.PlainText
import site.tiedan.module.PastebinCodeExecutor.renderLatexOnline
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
            throw Exception("JSON编码错误【严重错误，理论不可能发生】，请提供日志反馈问题：\n${e.message}")
        }
    }

    suspend fun generateMessageChain(name: String, jsonMessage: JsonMessage, sender: CommandSender, timeUsedRecord: Long = 0): Pair<MessageChain, Long> {
        val builder = MessageChainBuilder()
        if (sender.subject is Group && jsonMessage.at) {
            builder.add(At(sender.user!!))
            builder.add("\n")
        }
        var timeUsed = timeUsedRecord
        for ((index, m) in jsonMessage.messageList.withIndex()) {
            if (index > 0) builder.add("\n")
            val content = m.content
            when (m.format) {
                "text"-> {
                    builder.add(
                        if (content.isBlank()) "　"
                        else if(index == 0) blockProhibitedContent(content, jsonMessage.at, sender.subject is Group).first
                        else content
                    )
                }
                "markdown"-> {
                    val markdownResult = processMarkdown(name, content, m.width.toString(), TIMEOUT - timeUsed)
                    timeUsed += markdownResult.duration
                    if (!markdownResult.success) {
                        builder.add("[markdown2image错误] ${markdownResult.message}")
                        continue
                    }
                    builder.addImageFromFile("${cacheFolder}markdown.png", sender)
                }
                "base64"-> {
                    val base64Result = processBase64(content)
                    if (!base64Result.success) {
                        builder.add(base64Result.extension)
                        continue
                    }
                    builder.add(
                        fileToMessage(
                            base64Result.fileType,
                            base64Result.extension,
                            sender.subject,
                            false
                        ) ?: PlainText("[错误] Base64文件转换时出现未知错误，请联系管理员")
                    )
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
        return Pair(builder.build(), timeUsed)
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
                val content = m.content
                when (m.format) {
                    "text"-> {
                        val builder = MessageChainBuilder()
                        if (sender.subject is Group && jsonMessage.at) {
                            builder.add(At(sender.user!!))
                            builder.add("\n")
                        }
                        builder.add(
                            if (content.isBlank()) "　"
                            else blockProhibitedContent(content, jsonMessage.at, sender.subject is Group).first
                        )
                        sender.sendMessage(builder.build())
                    }
                    "markdown"-> {
                        val markdownResult = processMarkdown(name, content, m.width.toString(), TIMEOUT - timeUsed)
                        timeUsed += markdownResult.duration
                        if (!markdownResult.success) {
                            sender.sendMessage("[markdown2image错误] ${markdownResult.message}")
                            continue
                        }
                        sendLocalImage("${cacheFolder}markdown.png", sender)
                    }
                    "base64"-> {
                        val base64Result = processBase64(content)
                        if (!base64Result.success) {
                            sender.sendMessage(base64Result.extension)
                            continue
                        }
                        sender.sendMessage(
                            fileToMessage(
                                base64Result.fileType,
                                base64Result.extension,
                                sender.subject,
                                true
                            ) ?: PlainText("[错误] Base64文件转换时出现未知错误，请联系管理员")
                        )
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

    // 检查json和MessageChain中的禁用内容，发现则返回覆盖文本
    fun blockProhibitedContent(content: String, at: Boolean, isGroup: Boolean): Pair<String, Boolean> {
        val (blacklist, warning) = if (isGroup) {
            if (at) return content to false
            Pair(SystemConfig.groupBlackList, "[警告] 首条消息中检测到被禁用的内容，请开启`at`参数或修改内容来避免此警告")
        } else {
            Pair(SystemConfig.privateBlackList, "[警告] 私信输出中检测到被禁用的内容，请修改内容来避免此警告")
        }
        for (pattern in blacklist) {
            if (pattern.toRegex().containsMatchIn(content)) {
                return warning to true
            }
        }
        return content to false
    }

}
