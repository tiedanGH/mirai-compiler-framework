package site.tiedan.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSender.Companion.asCommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework.ERROR_MSG_MAX_LENGTH
import site.tiedan.MiraiCompilerFramework.MSG_TRANSFER_LENGTH
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.findFriendFromAllBots
import site.tiedan.MiraiCompilerFramework.findGroupFromAllBots
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.trimToMaxLength
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.config.PastebinConfig
import site.tiedan.data.ExtraData
import site.tiedan.format.*
import site.tiedan.format.ForwardMessageGenerator.lineCount
import site.tiedan.format.ForwardMessageGenerator.removeFirstAt
import site.tiedan.format.JsonProcessor.toJsonSingleMessages
import site.tiedan.format.JsonProcessor.toSingleChainMessages
import site.tiedan.utils.DownloadHelper.downloadImage
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.LocalTime

/**
 * # 项目输出处理器
 * - 处理程序输出格式 [handleOutputFormats]
 * - 处理并发送主动消息 [handleActiveMessage]
 * - 在线API将 LaTeX 转换为图片 [renderLatexOnline]
 *
 * @author tiedanGH
 */
object OutputHandler {

    private val OutputLock = Mutex()

     fun isLocked(): Boolean = OutputLock.isLocked
     suspend fun lock() = OutputLock.lock()
     fun unlock() = OutputLock.unlock()

    /**
     * ## 处理程序输出格式
     * @param name 项目名称
     * @param output 输出内容
     * @param outputFormat 输出格式
     * @param width 图片宽度
     * @param messageList 消息列表参数
     * @param forwardTitle 转发消息的标题
     * @param updateStorage 更新存储数据函数
     * @return 处理后的消息内容
     */
    suspend fun CommandSender.handleOutputFormats(
        name: String,
        output: String,
        outputFormat: String,
        outputAt: Boolean,
        width: String?,
        messageList: List<JsonProcessor.SingleChainMessage>,
        forwardTitle: String?,
        updateStorage: (String?, String?, List<JsonProcessor.BucketData>?) -> Unit
    ): Any? {
        return when (outputFormat) {
            // text文本输出
            "text"-> {
                if ((output.length > MSG_TRANSFER_LENGTH || output.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                    ForwardMessageGenerator.stringToForwardMessage(StringBuilder(output), subject, forwardTitle)
                } else {
                    val messageBuilder = MessageChainBuilder()
                    if (subject is Group && outputAt) {
                        messageBuilder.add(At(user!!))
                        messageBuilder.add("\n")
                    } else {
                        val ret = JsonProcessor.blockProhibitedContent(output, at = true, isGroup = false)
                        if (ret.second) messageBuilder.add("${ret.first}\n")
                    }
                    if (output.isEmpty()) {
                        messageBuilder.add("没有任何结果呢~")
                    } else {
                        messageBuilder.add(output)
                    }
                    messageBuilder.build()
                }
            }
            // markdown转图片输出
            "markdown"-> {
                val markdownResult = MarkdownImageGenerator.processMarkdown(name, output, width ?: "600")
                if (!markdownResult.success) {
                    return sendQuoteReply(markdownResult.message)
                }
                val file = File("${cacheFolder}markdown.png")
                subject?.uploadFileToImage(file)     // 返回结果图片
                    ?: return sendQuoteReply("[错误] 图片文件异常：ExternalResource上传失败，请尝试重新执行")
            }
            // base64自定义格式输出
            "base64"-> {
                val base64Result = Base64Processor.processBase64(output)
                if (!base64Result.success) {
                    return sendQuoteReply(base64Result.extension)
                }
                Base64Processor.fileToMessage(base64Result.fileType, base64Result.extension, subject, true)
                    ?: return sendQuoteReply("[错误] Base64文件转换时出现未知错误，请联系管理员")
            }
            // 普通图片输出
            "image"-> {
                val file = if (output.startsWith("file:///")) {
                    File(URI(output))
                } else {
                    val downloadResult = downloadImage(name, output, cacheFolder, "image", force = true)
                    if (!downloadResult.success) {
                        return sendQuoteReply(downloadResult.message)
                    }
                    File("${cacheFolder}image")
                }
                if (!file.exists()) {
                    return sendQuoteReply("[错误] 本地图片文件不存在，请检查路径")
                }
                subject?.uploadFileToImage(file)     // 返回结果图片
                    ?: return sendQuoteReply("[错误] 图片文件异常：ExternalResource上传失败，请尝试重新执行")
            }
            // LaTeX转图片输出
            "LaTeX"-> {
                val renderResult = renderLatexOnline(output)
                if (renderResult.startsWith("QuickLaTeX")) {
                    return sendQuoteReply("[错误] $renderResult")
                }
                val file = File("${cacheFolder}latex.png")
                subject?.uploadFileToImage(file)     // 返回结果图片
                    ?: return sendQuoteReply("[错误] 图片文件异常：ExternalResource上传失败，请尝试重新执行")
            }
            // json分支功能MessageChain
            "MessageChain"-> {
                JsonProcessor.generateMessageChain(name, messageList.toJsonSingleMessages(), outputAt, this).first.let { messageChain ->
                    if (messageChain.lineCount > 20 && PastebinConfig.enable_ForwardMessage)
                        ForwardMessageGenerator.anyMessageToForwardMessage(messageChain.removeFirstAt, subject, forwardTitle)
                    else messageChain
                }
            }
            // json分支功能MultipleMessage
            "MultipleMessage"-> {
                "MultipleMessage"
            }
            // 转发消息生成（JSON在内部进行解析）
            "ForwardMessage"-> {
                val forwardMessageData = ForwardMessageGenerator.generateForwardMessage(name, output, this)
                updateStorage(forwardMessageData.global, forwardMessageData.storage, forwardMessageData.bucket)
                forwardMessageData.forwardMessage     // 返回ForwardMessage
            }
            // TTS音频消息生成（JSON在内部进行解析）
            "Audio"-> {
                val audioData = AudioGenerator.generateAudio(output, subject)
                updateStorage(audioData.global, audioData.storage, audioData.bucket)
                if (audioData.success) {
                    audioData.audio     // 返回Audio
                } else {
                    sendQuoteReply(audioData.error)
                }
            }
            else -> {
                sendQuoteReply("代码执行完成但无法输出：无效的输出格式：$outputFormat，请联系创建者修改格式")
            }
        }
    }

    /**
     * ## 处理并发送主动消息
     * @param name 项目名称
     * @param activeMessage 主动消息列表
     * @return 主动消息错误信息，若无错误则返回空字符串
     */
    suspend fun CommandSender.handleActiveMessage(
        name: String,
        activeMessage: List<JsonProcessor.ActiveMessage>
    ): String {
        val senderID = user?.id ?: 10000
        val senderName = this.name

        var result = ""

        /**
         * 发送消息接口函数
         */
        suspend fun <T> sendMessageToTarget(
            index: Int,
            idLabel: String,
            idValue: Long,
            target: T?,
            sendAction: suspend (T) -> Unit
        ) {
            if (target != null) {
                try {
                    sendAction(target)
                    delay(1000)
                } catch (e: Exception) {
                    result += "\n[(${index + 1})$idLabel] 消息发送出错：${e.message}"
                }
            } else {
                result += if (idLabel == "群聊") {
                    "\n[(${index + 1})群聊] 消息发送失败：bot未加入此群聊($idValue)，请检查群号是否正确或联系bot所有者"
                } else {
                    "\n[(${index + 1})私信] 消息发送失败：获取好友失败($idValue)，请检查userID是否正确"
                }
            }
        }

        activeMessage.forEachIndexed { index, active ->
            if (index >= 10) {
                return "$result\n[上限] 执行中断：单次主动消息上限为10条"
            }
            val groupID = active.groupID
            val userID = active.userID
            val activeSingleMessage = active.message
            if (groupID == null && userID == null) {
                result += "\n[(${index + 1})参数] 目标无效：groupID和userID均为空"
                return@forEachIndexed
            }

            var isMultipleMessage = false
            // 解析并生成输出内容
            val message = handleOutputFormats(
                name,
                activeSingleMessage.content,
                activeSingleMessage.format,
                false,
                activeSingleMessage.width.toString(),
                activeSingleMessage.messageList.toSingleChainMessages(),
                "$name[主动消息]"
            ) { _, _, _ -> /* ignore */ }
            val msgToSend: Message = when (message) {
                is MessageChain -> message
                is ForwardMessage -> message
                is Image -> message
                is String -> {
                    isMultipleMessage = true
                    PlainText("主动消息MultipleMessage输出模式")
                }
                null -> PlainText("[处理消息失败] 意料之外的消息结果 null，请联系管理员")
                else -> PlainText("[处理消息失败] 主动消息不支持的输出消息类型或内容，请联系管理员：\n" +
                        trimToMaxLength(message.toString(), ERROR_MSG_MAX_LENGTH).first
                )
            }

            // 群聊主动消息
            if (groupID != null) {
                val group = findGroupFromAllBots(groupID)
                sendMessageToTarget(index, "群聊", groupID, group) { g ->
                    if (msgToSend is ForwardMessage && PastebinConfig.enable_ForwardMessage) {
                        g.sendMessage(msgToSend)
                    } else if (isMultipleMessage) {
                        val groupCommandSender = user?.id?.let { g[it]?.asCommandSender(false) }
                        val ret: String? = if (groupCommandSender != null) {
                            JsonProcessor.outputMultipleMessage(
                                name,
                                activeSingleMessage.messageList.toSingleChainMessages(),
                                outputAt = false,
                                groupCommandSender,
                                extraText = PlainText("【$name[多条主动消息]】\n"),
                                forwardTitle = "$name[多条主动消息]"
                            )
                        } else {
                            result += "\n[(${index + 1})群聊] 输出多条消息出错：从目标群获取用户(${user?.id})失败"
                            null
                        }
                        if (ret != null) {
                            result += "\n[(${index + 1})群聊] 输出多条消息出错：$ret"
                        }
                    } else {
                        g.sendMessage(PlainText("【$name[主动消息]】\n").plus(msgToSend))
                    }
                }
                return@forEachIndexed
            }
            // 私信主动消息
            if (userID == null) return@forEachIndexed
            val friend = findFriendFromAllBots(userID)
            val now = LocalTime.now().hour
            val allowTime = ExtraData.private_allowTime[userID]
            if (allowTime != null) {
                if (notInAllowTime(now, allowTime.first, allowTime.second)) {
                    result += "\n[(${index + 1})私信] 权限不足：不在${userID}设置的可用时间段内"
                    return@forEachIndexed
                }
            } else {
                result += "\n[(${index + 1})私信] 权限不足：${userID}不允许任何主动消息"
                return@forEachIndexed
            }
            sendMessageToTarget(index, "私信", userID, friend) { f ->
                val preMessage = "来自：$senderName($senderID)\n【消息内容】\n"
                if (msgToSend is ForwardMessage && PastebinConfig.enable_ForwardMessage) {
                    val forward = if (activeSingleMessage.format == "text") {
                        ForwardMessageGenerator.stringToForwardMessage(
                            StringBuilder(preMessage.plus(activeSingleMessage.content)),
                            subject,
                            "$name[主动消息]"
                        )
                    } else {
                        ForwardMessageGenerator.anyMessageToForwardMessage(
                            PlainText(preMessage).plus(msgToSend),
                            subject,
                            "$name[主动消息]"
                        )
                    }
                    f.sendMessage(forward)
                } else if (isMultipleMessage) {
                    val ret = JsonProcessor.outputMultipleMessage(
                        name,
                        activeSingleMessage.messageList.toSingleChainMessages(),
                        outputAt = false,
                        f.asCommandSender(),
                        extraText = PlainText("【$name[多条主动消息]】\n$preMessage"),
                        forwardTitle = "$name[多条主动消息]"
                    )
                    if (ret != null) {
                        result += "\n[(${index + 1})私信] 输出多条消息出错：$ret"
                    }
                } else {
                    f.sendMessage(PlainText("【$name[主动消息]】\n$preMessage").plus(msgToSend))
                }
            }
            return@forEachIndexed
        }
        return result
    }

    private fun notInAllowTime(now: Int, allowStart: Int, allowEnd: Int): Boolean {
        return if (allowStart <= allowEnd) {
            now !in allowStart..allowEnd
        } else {
            now in (allowEnd + 1) until allowStart
        }
    }

    /**
     * ### 在线API将 LaTeX 转换为图片
     * @param latex 待转换 LaTeX 字符串
     */
    fun renderLatexOnline(latex: String): String {
        val apiUrl = "https://quicklatex.com/latex3.f"
        val outputFilePath = "${cacheFolder}latex.png"
        val postData = "formula=${URLEncoder.encode(latex, "GBK").replace("+", "%20")}&fsize=15px&fcolor=000000&bcolor=FFFFFF&mode=0&out=1"

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
                logger.info("获取LaTeX结果图片成功")
                return "请求执行LaTeX转图片成功"
            } else {
                return "QuickLaTeX：HTTP Status ${connection.responseCode}: ${connection.responseMessage}"
            }
        } catch (e: Exception) {
            logger.warning(e)
            return "QuickLaTeX：${e::class.simpleName}(${e.message})"
        }
    }
}
