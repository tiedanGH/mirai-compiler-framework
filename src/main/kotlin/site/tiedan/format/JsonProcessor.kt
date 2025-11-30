package site.tiedan.format

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
import net.mamoe.mirai.message.data.PlainText
import site.tiedan.MiraiCompilerFramework.MSG_TRANSFER_LENGTH
import site.tiedan.MiraiCompilerFramework.MARKDOWN_MAX_TIME
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.command.CommandBucket.linkedBucketID
import site.tiedan.config.PastebinConfig
import site.tiedan.config.SystemConfig
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinBucket
import site.tiedan.data.PastebinStorage
import site.tiedan.format.ForwardMessageGenerator.lineCount
import site.tiedan.format.ForwardMessageGenerator.removeFirstAt
import site.tiedan.module.PastebinCodeExecutor.renderLatexOnline
import site.tiedan.utils.DownloadHelper.downloadImage
import site.tiedan.utils.Security
import java.io.File
import java.net.URI
import kotlin.math.ceil

/**
 * ## json 输出格式
 * 输出结构：[JsonMessage]
 *
 * @author tiedanGH
 */
object JsonProcessor {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    /**
     * ### JsonMessage
     * @param format 输出格式
     * @param at 文本消息前是否@指令执行者，**仅在`format`为text和MessageChain时生效**
     * @param width 图片的默认宽度，当以text输出时，此项参数不生效
     * @param content 输出的内容，用于输出文字或生成图片
     * @param messageList 消息链中包含的所有消息，和`content`参数之间仅有一个有效。单条消息结构：[SingleChainMessage]
     * @param active 发送主动消息，消息结构：[ActiveMessage]
     * @param storage 用户存储数据
     * @param global 全局存储数据
     * @param bucket 跨项目存储库数据
     * @param error 用于抛出中断异常。当为非空时，bot会直接发送`error`中的消息，**并停止解析输出中的其他任何参数，存储数据也不会保存**
     */
    @Serializable
    data class JsonMessage(
        val format: String = "text",
        val at: Boolean = true,
        val width: Int = 600,
        val content: String = "空消息",
        val messageList: List<SingleChainMessage> = listOf(SingleChainMessage()),
        val active: List<ActiveMessage>? = null,
        val storage: String? = null,
        val global: String? = null,
        val bucket: List<BucketData>? = null,
        val error: String = "",
    )
    /**
     * ### SingleChainMessage
     * @param format 输出格式
     * @param width 图片的默认宽度，当以text输出时，此项参数不生效
     * @param content 输出的内容，用于输出文字或生成图片
     * @param messageList 消息链中包含的所有消息，和`content`参数之间仅有一个有效。单条消息结构：[JsonSingleMessage]
     */
    @Serializable
    data class SingleChainMessage(
        val format: String = "text",
        val width: Int = 600,
        val content: String = "空消息",
        val messageList: List<JsonSingleMessage> = listOf(JsonSingleMessage()),
    ) {
        fun toJsonSingleMessage(): JsonSingleMessage {
            return JsonSingleMessage(
                format = format,
                width = width,
                content = content
            )
        }
    }
    /**
     * ### JsonSingleMessage
     * @param format 输出格式
     * @param width 图片的默认宽度，当以text输出时，此项参数不生效
     * @param content 输出的内容，用于输出文字或生成图片
     */
    @Serializable
    data class JsonSingleMessage(
        val format: String = "text",
        val width: Int = 600,
        val content: String = "空消息",
    ) {
        fun toSingleChainMessage(): SingleChainMessage {
            return SingleChainMessage(
                format = format,
                width = width,
                content = content,
                messageList = listOf()
            )
        }
    }

    fun List<JsonSingleMessage>.toSingleChainMessages(): List<SingleChainMessage> =
        this.map { it.toSingleChainMessage() }
    fun List<SingleChainMessage>.toJsonSingleMessages(): List<JsonSingleMessage> =
        this.map { it.toJsonSingleMessage() }

    /**
     * ### ActiveMessage
     * @param groupID 发送消息的目标群号
     * @param userID 发送消息的目标用户
     * @param message 发送的消息对象，支持多格式，消息结构：[SingleChainMessage]
     */
    @Serializable
    data class ActiveMessage(
        val groupID: Long? = null,
        val userID: Long? = null,
        val message: SingleChainMessage = SingleChainMessage(),
    )

    @Serializable
    data class JsonStorage(
        val global: String = "",
        val storage: String = "",
        val bucket: List<BucketData> = listOf(),
        val userID: Long = 10001,
        val nickname: String = "",
        val from: String = "",
        val images: List<ImageData> = listOf(),
    )
    @Serializable
    data class BucketData(
        val id: Long? = null,
        val name: String? = null,
        val content: String? = null,
    )
    @Serializable
    data class ImageData(
        val url: String = "",
        val base64: String? = null,
        val error: String = "",
    )

    /**
     * 解析JSON字符串
     */
    fun processDecode(jsonOutput: String): JsonMessage {
        return try {
            json.decodeFromString<JsonMessage>(jsonOutput)
        } catch (e: Exception) {
            JsonMessage(error = "JSON解析错误：\n${e.message}")
        }
    }

    /**
     * 编码JSON字符串
     */
    fun processEncode(global: String, storage: String, bucket: List<BucketData>, userID: Long, nickname: String, from: String, images: List<ImageData>): String {
        return try {
            val jsonStorageObject = JsonStorage(global, storage, bucket, userID, nickname, from, images)
            json.encodeToString<JsonStorage>(jsonStorageObject)
        } catch (e: Exception) {
            throw Exception("JSON编码错误【严重错误，理论不可能发生】，请提供日志反馈问题：\n${e.message}")
        }
    }

    /**
     * ###　生成消息链 MessageChain
     * @param name 项目名称
     * @param messageList 消息列表，单条消息：[JsonSingleMessage]
     * @param outputAt 文本消息前是否@指令执行者
     * @param sender 指令发送者
     * @param timeUsedRecord 执行已用时间
     */
    suspend fun generateMessageChain(
        name: String,
        messageList: List<JsonSingleMessage>,
        outputAt: Boolean,
        sender: CommandSender,
        timeUsedRecord: Long = 0
    ): Pair<MessageChain, Long> {
        val builder = MessageChainBuilder()
        if (sender.subject is Group && outputAt) {
            builder.add(At(sender.user!!))
            builder.add("\n")
        }
        var timeUsed = timeUsedRecord
        for ((index, m) in messageList.withIndex()) {
            if (index > 0) builder.add("\n")
            val content = m.content
            when (m.format) {
                "text"-> {
                    builder.add(
                        if (content.isBlank()) "　"
                        else if(index == 0) blockProhibitedContent(content, outputAt, sender.subject is Group).first
                        else content
                    )
                }
                "markdown"-> {
                    val markdownResult = MarkdownImageGenerator.processMarkdown(name, content, m.width.toString(), MARKDOWN_MAX_TIME - timeUsed)
                    timeUsed += markdownResult.duration
                    if (!markdownResult.success) {
                        builder.add("[markdown2image错误] ${markdownResult.message}")
                        continue
                    }
                    builder.addImageFromFile("${cacheFolder}markdown.png", sender)
                }
                "base64"-> {
                    val base64Result = Base64Processor.processBase64(content)
                    if (!base64Result.success) {
                        builder.add(base64Result.extension)
                        continue
                    }
                    builder.add(
                        Base64Processor.fileToMessage(
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
                        val downloadResult = downloadImage(name, content, cacheFolder, "image", MARKDOWN_MAX_TIME - timeUsed, force = true)
                        timeUsed += ceil(downloadResult.duration).toLong()
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
                "json", "ForwardMessage", "MessageChain", "MultipleMessage", "Audio"-> {
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

    /**
     * ###　输出多条消息 MultipleMessage
     * @param name 项目名称
     * @param messageList 消息列表，单条消息：[SingleChainMessage]
     * @param outputAt 文本消息前是否@指令执行者
     * @param sender 指令发送者
     */
    suspend fun outputMultipleMessage(
        name: String,
        messageList: List<SingleChainMessage>,
        outputAt: Boolean,
        sender: CommandSender,
        extraText: PlainText = PlainText(""),
        forwardTitle: String? = "$name[多条消息]"
    ): String? {
        try {
            var timeUsed: Long = 0
            for ((index, m) in messageList.withIndex()) {
                if (index >= 15) {
                    return "单次执行消息上限为15条"
                }
                val content = m.content
                when (m.format) {
                    "text"-> {
                        if ((content.length > MSG_TRANSFER_LENGTH || content.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                            val forward = ForwardMessageGenerator.stringToForwardMessage(StringBuilder(extraText.content + content), sender.subject, forwardTitle)
                            sender.sendMessage(forward)
                        } else {
                            val builder = MessageChainBuilder()
                            if (sender.subject is Group && outputAt) {
                                builder.add(At(sender.user!!))
                                builder.add("\n")
                            }
                            builder.add(
                                if (content.isBlank()) "　"
                                else blockProhibitedContent(content, outputAt, sender.subject is Group).first
                            )
                            sender.sendMessage(extraText + builder.build())
                        }
                    }
                    "markdown"-> {
                        val markdownResult = MarkdownImageGenerator.processMarkdown(name, content, m.width.toString(), MARKDOWN_MAX_TIME - timeUsed)
                        timeUsed += markdownResult.duration
                        if (!markdownResult.success) {
                            sender.sendMessage(extraText + "[markdown2image错误] ${markdownResult.message}")
                            continue
                        }
                        sendLocalImage("${cacheFolder}markdown.png", sender, extraText)
                    }
                    "base64"-> {
                        val base64Result = Base64Processor.processBase64(content)
                        if (!base64Result.success) {
                            sender.sendMessage(extraText + base64Result.extension)
                            continue
                        }
                        sender.sendMessage(extraText.plus(
                            Base64Processor.fileToMessage(
                                base64Result.fileType,
                                base64Result.extension,
                                sender.subject,
                                true
                            ) ?: PlainText("[错误] Base64文件转换时出现未知错误，请联系管理员")
                        ))
                    }
                    "image"-> {
                        if (content.startsWith("file:///")) {
                            if (!File(URI(content)).exists()) {
                                sender.sendMessage(extraText + "[错误] 本地图片文件不存在，请检查路径")
                                continue
                            }
                            sendLocalImage(content, sender, extraText)
                        } else {
                            val downloadResult = downloadImage(name, content, cacheFolder, "image", MARKDOWN_MAX_TIME - timeUsed, force = true)
                            timeUsed += ceil(downloadResult.duration).toLong()
                            if (!downloadResult.success) {
                                sender.sendMessage(extraText + downloadResult.message)
                                continue
                            }
                            sendLocalImage("${cacheFolder}image", sender, extraText)
                        }
                    }
                    "LaTeX"-> {
                        val renderResult = renderLatexOnline(content)
                        if (renderResult.startsWith("QuickLaTeX")) {
                            sender.sendMessage(extraText + "[错误] $renderResult")
                            continue
                        }
                        sendLocalImage("${cacheFolder}latex.png", sender, extraText)
                    }
                    "MessageChain"-> {
                        val message = generateMessageChain(name, m.messageList, outputAt, sender).first.let { messageChain ->
                            if (messageChain.lineCount > 20 && PastebinConfig.enable_ForwardMessage)
                                ForwardMessageGenerator.anyMessageToForwardMessage(messageChain.removeFirstAt, sender.subject, null)
                            else messageChain
                        }
                        sender.sendMessage(extraText + message)
                    }
                    // content在函数内部再次解析JSON
                    "ForwardMessage"-> {
                        val forwardMessageData = ForwardMessageGenerator.generateForwardMessage(name, content, sender)
                        sender.sendMessage(forwardMessageData.forwardMessage)
                    }
                    "Audio"-> {
                        val audioData = AudioGenerator.generateAudio(content, sender.subject)
                        if (audioData.success) {
                            sender.sendMessage(audioData.audio ?: PlainText("[未知错误] 语音转换成功但返回值为null，理论不可能发生"))
                        } else {
                            sender.sendQuoteReply(audioData.error)
                        }
                    }
                    "json", "MultipleMessage"-> {
                        sender.sendMessage(extraText + "[错误] 不支持在SingleChainMessage内使用“${m.format}”输出格式")
                    }
                    else -> {
                        sender.sendMessage(extraText + "[错误] 无效的输出格式：${m.format}，请检查此条消息的format参数")
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

    private suspend fun sendLocalImage(filePath: String, sender: CommandSender, extraText: PlainText) {
        val file = if (filePath.startsWith("file:///")) {
            File(URI(filePath))
        } else {
            File(filePath)
        }
        try {
            val image = sender.subject?.uploadFileToImage(file)
            if (image == null)
                sender.sendMessage(extraText + "[错误] 图片文件异常：ExternalResource上传失败")
            else
                sender.sendMessage(extraText + image)       // 发送图片
        } catch (e: Exception) {
            logger.warning(e)
            sender.sendMessage(extraText + "[错误] 图片文件异常：${e.message}")
        }
    }

    /**
     * 保存 storage、global、bucket 存储数据
     */
    fun savePastebinStorage(
        name: String,
        userID: Long,
        global: String?,
        storage: String?,
        bucket: List<BucketData>?
    ): String? {
        if (global == null && storage == null && bucket == null) return null
        logger.info (
            "保存存储数据: global{${global?.length}} storage{${storage?.length}} " +
            "bucket{${bucket?.joinToString(" ") { "[${it.id}](${it.content?.length})" }}}"
        )
        val storageMap = PastebinStorage.storage.getOrPut(name) {
            mutableMapOf<Long, String>().apply { put(0, "") }
        }
        global?.let { storageMap[0] = it }
        storage?.let {
            if (it.isEmpty()) {
                storageMap.remove(userID)
            } else {
                storageMap[userID] = it
            }
        }
        PastebinStorage.save()

        if (bucket == null) return null
        val ret = StringBuilder()
        val seenBucketIDs = mutableSetOf<Long>()
        bucket.forEachIndexed { index, data ->
            when (data.id) {
                null -> ret.append("\n[(${index + 1})无效ID] 未指定目标存储库ID")
                !in linkedBucketID(name) -> ret.append("\n[(${index + 1})拒绝访问] 当前项目未关联存储库 ${data.id}")
                in seenBucketIDs -> ret.append("\n[(${index + 1})重复写入] 检测到对存储库 ${data.id} 的重复保存，单次输出仅支持写入同一存储库一次")
                else -> if (data.content != null) {
                    val content = if (PastebinBucket.bucket[data.id]?.get("encrypt") == "true") {
                        Security.encrypt(data.content, ExtraData.key)
                    } else data.content
                    PastebinBucket.bucket[data.id]?.set("content", content)
                    seenBucketIDs.add(data.id)
                }
            }
        }
        PastebinBucket.save()
        return ret.takeIf { it.isNotEmpty() }?.toString()
    }

    /**
     * 检查 json 和 MessageChain 中的禁用内容，发现则返回覆盖文本
     */
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
