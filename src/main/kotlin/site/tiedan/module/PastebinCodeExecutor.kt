package site.tiedan.module

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSender.Companion.asCommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.RawForwardMessage
import net.mamoe.mirai.message.data.ShortVideo
import net.mamoe.mirai.message.data.buildForwardMessage
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.MSG_TRANSFER_LENGTH
import site.tiedan.MiraiCompilerFramework.THREAD
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.trimToMaxLength
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.command.CommandBucket.bucketIdsToBucketData
import site.tiedan.command.CommandBucket.linkedBucketID
import site.tiedan.command.CommandRun.Image_Path
import site.tiedan.config.DockerConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.config.SystemConfig
import site.tiedan.data.CodeCache
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.data.PastebinStorage
import site.tiedan.format.AudioGenerator
import site.tiedan.format.Base64Processor
import site.tiedan.format.ForwardMessageGenerator
import site.tiedan.format.ForwardMessageGenerator.lineCount
import site.tiedan.format.ForwardMessageGenerator.removeFirstAt
import site.tiedan.format.JsonProcessor
import site.tiedan.format.MarkdownImageGenerator
import site.tiedan.utils.DownloadHelper.downloadImage
import site.tiedan.utils.PastebinUrlHelper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.LocalTime
import kotlin.collections.List
import kotlin.collections.set
import kotlin.text.isNotBlank

/**
 * # PastebinCodeExecutor
 * - 代码执行主进程 [executeMainProcess]
 * - 处理程序输出格式 [handleOutputFormats]
 * - 处理并发送主动消息 [handleActiveMessage]
 * - 辅助模块：
 *   + 在线API将 LaTeX 转换为图片 [renderLatexOnline]
 *   + 运行代码并返回输出字符串 [runCodeToString]
 *
 * @author tiedanGH
 */
object PastebinCodeExecutor {
    private val OutputLock = Mutex()
    private val StorageLock = Mutex()

    /**
     * ## pb代码执行主进程
     * @param name 项目名称
     * @param userInput 用户输入
     * @param imageUrls 输入图片URL链接
     */
    suspend fun CommandSender.executeMainProcess(name: String, userInput: String, imageUrls: List<String>) {

        val userID = user?.id ?: 10000

        if (ExtraData.BlackList.contains(userID)) {
            logger.info("${userID}已被拉黑，请求被拒绝")
            return
        }

        val request = RequestLimiter.newRequest(userID)
        if (request.first.isNotEmpty()) {
            sendQuoteReply(request.first)
            if (request.second) return
        }

        if (THREAD >= PastebinConfig.thread_limit) {
            sendQuoteReply("当前已经有 $THREAD 个进程正在执行，请等待几秒后再次尝试")
            return
        }
        if (PastebinData.groupOnly.contains(name) && (subject is Group).not() &&
            userID.toString() != PastebinData.pastebin[name]?.get("userID") && PastebinConfig.admins.contains(userID).not()
            ) {
            sendQuoteReply("执行失败：此条代码链接被标记为仅限群聊中执行！")
            return
        }
        if (PastebinData.censorList.contains(name)) {
            sendQuoteReply("此条链接仍在审核中，暂时无法执行。管理员会定期对链接进行审核，您也可以主动联系进行催审")
            return
        }

        try {
            THREAD++

            val language = PastebinData.pastebin[name]?.get("language").toString()
            val url = PastebinData.pastebin[name]?.get("url").toString()
            val format = PastebinData.pastebin[name]?.get("format") ?: "text"
            var width = PastebinData.pastebin[name]?.get("width")
            val util = PastebinData.pastebin[name]?.get("util")
            val storageMode = PastebinData.pastebin[name]?.get("storage")
            val nickname = this.name
            var input = userInput

            var output = ""
            var outputFormat = format
            var outputAt = true
            var messageList: List<JsonProcessor.JsonSingleMessage> = listOf(JsonProcessor.JsonSingleMessage())
            var activeMessage: List<JsonProcessor.ActiveMessage>? = null
            var outputGlobal: String? = null
            var outputStorage: String? = null
            var outputBucket: List<JsonProcessor.BucketData>? = null

            // 从url或缓存获取代码
            val code: String = try {
                if (PastebinUrlHelper.supportedUrls.any { url.startsWith(it.url) && it.enableCache }) {
                    if (CodeCache.CodeCache.contains(name)) {
                        logger.debug("从 CodeCache: $name 中获取代码")
                        CodeCache.CodeCache[name]!!
                    } else {
                        logger.info("从 $url 中获取代码")
                        val cache = PastebinUrlHelper.get(url)
                        if (cache.isNotBlank()) {
                            CodeCache.CodeCache[name] = cache
                            CodeCache.save()
                            sendMessage("【$name】已保存至缓存，下次执行时将从缓存中获取代码")
                            cache
                        } else {
                            sendMessage("【$name】保存至缓存失败且无法执行：获取代码失败或代码为空，请联系代码创建者")
                            return
                        }
                    }
                } else {
                    logger.info("从 $url 中获取代码")
                    PastebinUrlHelper.get(url)
                }
            } catch (e: Exception) {
                sendQuoteReply(
                    "[获取代码失败] 请重新尝试\n" +
                    "报错类别：${e::class.simpleName}\n" +
                    "报错信息：${trimToMaxLength(e.message.toString(), 300).first}"
                )
                return
            }

            if (code.isBlank()) {
                sendQuoteReply("[执行失败] 未获取到有效代码")
                return
            }

            Statistics.countRun(name)   // 数据统计

            // 输入存储的数据
            if (storageMode == "true") {
                if (StorageLock.isLocked) logger.debug("(${userID})执行$name [存储]进程执行请求等待中...")
                StorageLock.lock()
                val global = PastebinStorage.storage[name]?.get(0) ?: ""
                val storage = PastebinStorage.storage[name]?.get(userID) ?: ""
                val bucket = bucketIdsToBucketData(linkedBucketID(name))
                val from = if (subject is Group) "${(subject as Group).name}(${(subject as Group).id})" else "private"
                val encodeBase64 = PastebinData.pastebin[name]?.get("base64") == "true"
                val imageData = Base64Processor.encodeImagesToBase64(imageUrls, encodeBase64)

                val jsonInput = JsonProcessor.processEncode(global, storage, bucket, userID, nickname, from, imageData)
                input = "$jsonInput\n$userInput"
                logger.info(
                    "输入存储数据: global{${global.length}} storage{${storage.length}} " +
                    "bucket{${bucket.joinToString(" ") { "[${it.id}](${it.content?.length})" }}} " +
                    "$nickname($userID) $from"
                )
            }

            logger.debug("[DEBUG] input:\n$input")

            // 所有格式在这里执行代码，返回字符串输出
            val pair = runCodeToString(name, language, code, format, util, input, userInput)
            output = pair.first
            if (pair.second) {
                if (output.startsWith("[执行失败]")) {
                    sendQuoteReply(output)
                    return
                }
                outputFormat = "text"
            }

            // 解析json
            if (outputFormat == "json") {
                val jsonMessage = JsonProcessor.processDecode(output)
                if (jsonMessage.error.isNotEmpty()) {
                    if (PastebinConfig.enable_ForwardMessage) {
                        val forward = buildForwardMessage(subject!!) {
                            displayStrategy = object : ForwardMessage.DisplayStrategy {
                                override fun generateTitle(forward: RawForwardMessage): String = "输出解析错误"
                                override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("执行失败：JSON解析错误")
                            }
                            subject!!.bot named "Error" says "[错误] ${jsonMessage.error}"
                            val (resultString, tooLong) = trimToMaxLength(output, 10000)
                            if (tooLong) {
                                subject!!.bot named "Error" says "原始输出过大，仅截取前10000个字符"
                            }
                            subject!!.bot named "原始输出" says "程序原始输出：\n$resultString"
                        }
                        sendMessage(forward)
                    } else {
                        sendQuoteReply("[错误] ${jsonMessage.error}")
                    }
                    return
                }
                outputFormat = jsonMessage.format
                outputAt = jsonMessage.at
                width = jsonMessage.width.toString()
                messageList = jsonMessage.messageList
                activeMessage = jsonMessage.active
                outputGlobal = jsonMessage.global
                outputStorage = jsonMessage.storage
                outputBucket = jsonMessage.bucket
                if (outputFormat != "MessageChain") {
                    output = if (outputFormat in listOf("markdown", "base64")) jsonMessage.content
                    else JsonProcessor.blockProhibitedContent(jsonMessage.content, outputAt, subject is Group).first
                }
                if (outputFormat == "json") {
                    sendQuoteReply("禁止套娃：不支持在JsonMessage内使用“json”输出格式")
                    return
                }
            }
            // 非text输出需锁定输出进程
            if (outputFormat != "text") {
                if (OutputLock.isLocked) logger.debug("(${userID})执行$name [输出]进程执行请求等待中...")
                OutputLock.lock()
            }
            // 输出内容生成
            val message = handleOutputFormats(
                name, output, outputFormat, outputAt, width, messageList, null
            ) { global, storage, bucket ->
                outputGlobal = global
                outputStorage = storage
                outputBucket = bucket
            }
            // 根据消息类型进行回复
            when (message) {
                is MessageChain -> sendMessage(message)
                is ForwardMessage -> sendMessage(message)
                is Image -> sendMessage(message)
                is Audio -> sendMessage(message)
                is ShortVideo -> sendMessage(message)
                is String -> {
                    val ret = JsonProcessor.outputMultipleMessage(name, messageList, outputAt, this)
                    if (ret != null) {
                        sendQuoteReply("【输出多条消息时出错】$ret")
                    }
                }
                is Unit -> { /* ignore */ }
                null -> sendQuoteReply("[处理消息失败] 意料之外的消息结果 null，请联系管理员")
                else -> sendQuoteReply("[处理消息失败] 不识别的输出消息类型或内容，请联系管理员：\n" +
                        trimToMaxLength(message.toString(), 300).first
                )
            }
            // 主动消息相关
            if (activeMessage != null) {
                val ret = handleActiveMessage(name, activeMessage)
                if (ret.isNotEmpty()) {
                    sendQuoteReply("【主动消息错误】$ret")
                }
            }
            // 原始格式支持且开启存储功能：在程序执行和输出均无错误，且发送消息成功时才进行保存
            if (format in MiraiCompilerFramework.enableStorageFormats && storageMode == "true") {
                // 额外检测：执行后原项目消失（如被删除、存储被关闭），则不再保存存储
                if (PastebinData.pastebin[name]?.get("storage") == null) {
                    sendQuoteReply("【存储错误】拒绝访问：名称 $name 不存在或未开启存储，保存数据失败！")
                    return
                }
                val ret = JsonProcessor.savePastebinStorage(name, userID, outputGlobal, outputStorage, outputBucket)
                if (ret != null) sendQuoteReply("【存储错误】$ret")
            }
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply(
                "[指令运行错误](非代码问题)\n" +
                "报错类别：${e::class.simpleName}\n" +
                "报错信息：${trimToMaxLength(e.message.toString(), 300).first}"
            )
        } finally {
            THREAD--
            if (OutputLock.isLocked) OutputLock.unlock()
            if (StorageLock.isLocked) StorageLock.unlock()
        }
    }

    /**
     * ## 处理程序输出格式
     * @param name 项目名称
     * @param output 输出内容
     * @param outputFormat 输出格式
     * @param width 图片宽度
     * @param messageList 消息列表参数
     * @param title 转发消息的标题
     * @param updateStorage 更新存储数据函数
     */
    suspend fun CommandSender.handleOutputFormats(
        name: String,
        output: String,
        outputFormat: String,
        outputAt: Boolean,
        width: String?,
        messageList: List<JsonProcessor.JsonSingleMessage>,
        title: String?,
        updateStorage: (String?, String?, List<JsonProcessor.BucketData>?) -> Unit
    ): Any? {
        var outputGlobal: String? = null
        var outputStorage: String? = null
        var outputBucket: List<JsonProcessor.BucketData>? = null
        return when (outputFormat) {
            // text文本输出
            "text"-> {
                if ((output.length > MSG_TRANSFER_LENGTH || output.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                    ForwardMessageGenerator.stringToForwardMessage(StringBuilder(output), subject, title)
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
                JsonProcessor.generateMessageChain(name, messageList, outputAt, this).first.let { messageChain ->
                    if (messageChain.lineCount > 20 && PastebinConfig.enable_ForwardMessage)
                        ForwardMessageGenerator.anyMessageToForwardMessage(messageChain.removeFirstAt, subject, title)
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
                outputGlobal = forwardMessageData.global
                outputStorage = forwardMessageData.storage
                outputBucket = forwardMessageData.bucket
                forwardMessageData.forwardMessage     // 返回ForwardMessage
            }
            // TTS音频消息生成（JSON在内部进行解析）
            "Audio"-> {
                val audioData = AudioGenerator.generateAudio(output, subject)
                outputGlobal = audioData.global
                outputStorage = audioData.storage
                outputBucket = audioData.bucket
                if (audioData.success) {
                    audioData.audio
                } else {
                    sendQuoteReply(audioData.error)
                }
            }
            else -> {
                sendQuoteReply("代码执行完成但无法输出：无效的输出格式：$outputFormat，请联系创建者修改格式")
            }
        }
        updateStorage(outputGlobal, outputStorage, outputBucket)
    }

    /**
     * ## 处理并发送主动消息
     * @param name 项目名称
     * @param activeMessage 主动消息列表
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

        activeMessage.forEachIndexed { index, activeMessage ->
            if (index >= 10) {
                return "$result\n[上限] 执行中断：单次主动消息上限为10条"
            }
            val groupID = activeMessage.groupID
            val userID = activeMessage.userID
            val activeSingleMessage = activeMessage.message
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
                activeSingleMessage.messageList,
                "$name[主动消息]"
            ) { _, _, _ -> /* ignore */ }
            val msgToSend: Message = when (message) {
                is MessageChain, is ForwardMessage, is Image -> message
                is String -> {
                    isMultipleMessage = true
                    PlainText("主动消息MultipleMessage输出模式")
                }
                null -> PlainText("[处理消息失败] 意料之外的消息结果 null，请联系管理员")
                else -> PlainText("[处理消息失败] 主动消息不支持的输出消息类型或内容，请联系管理员：\n" +
                        trimToMaxLength(message.toString(), 300).first
                )
            }

            // 群聊主动消息
            if (groupID != null) {
                val group = bot?.getGroup(groupID)
                sendMessageToTarget(index, "群聊", groupID, group) { g ->
                    if (msgToSend is ForwardMessage && PastebinConfig.enable_ForwardMessage) {
                        g.sendMessage(msgToSend)
                    } else if (isMultipleMessage) {
                        val groupCommandSender = user?.id?.let { g[it]?.asCommandSender(false) }
                        val ret: String? = if (groupCommandSender != null) {
                            JsonProcessor.outputMultipleMessage(
                                name,
                                activeSingleMessage.messageList,
                                false,
                                groupCommandSender,
                                PlainText("【$name[多条主动消息]】\n")
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
            val friend = bot?.getFriend(userID)
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
                        activeSingleMessage.messageList,
                        false,
                        f.asCommandSender(),
                        PlainText("【$name[多条主动消息]】\n$preMessage")
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
        val postData = "formula=${java.net.URLEncoder.encode(latex, "GBK").replace("+", "%20")}&fsize=15px&fcolor=000000&bcolor=FFFFFF&mode=0&out=1"

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
                logger.info("获取结果图片成功")
                return "请求执行LaTeX转图片成功"
            } else {
                return "QuickLaTeX：HTTP Status ${connection.responseCode}: ${connection.responseMessage}"
            }
        } catch (e: Exception) {
            logger.warning(e)
            return "QuickLaTeX：${e::class.simpleName}(${e.message})"
        }
    }

    /**
     * 运行代码并返回输出字符串
     */
    fun runCodeToString(
        name: String,
        language: String,
        code: String,
        format: String,
        util: String?,
        input: String?,
        userInput: String
    ): Pair<String, Boolean> {
        try {
            logger.info("请求执行 PastebinData: $name 中的代码，input: $userInput")
            val result = if (language == "text")
                GlotAPI.RunResult(stdout = code)
            else
                GlotAPI.runCode(language, code, input, util)

            val builder = StringBuilder()
            if (result.message.isNotEmpty()) {
                if (language.lowercase() in DockerConfig.supportedLanguages) {
                    builder.append("[执行失败]\n来自docker容器的错误信息：\n")
                    builder.append("- error: ${result.error}\n")
                    builder.append("- message: ${trimToMaxLength(result.message, 300).first}")
                } else {
                    builder.append("[执行失败]\n收到来自glot接口的消息：${result.message}")
                }
                return Pair(builder.toString(), true)
            }
            var c = 0
            if (result.stdout.isNotEmpty()) c++
            if (result.stderr.isNotEmpty()) c++
            if (result.error.isNotEmpty()) c++
            val title = c >= 2

            if (c == 0) {
                if (format != "text") builder.append("[警告] 程序未输出任何内容，无法转换至预设输出格式")
                return Pair(builder.toString(), true)
            } else {
                if (result.error.isNotEmpty()) {
                    if (format != "text") builder.appendLine("程序发生错误，默认使用text输出")
                    builder.appendLine("error:")
                    builder.append(result.error)
                }
                if (result.stdout.isNotEmpty()) {
                    if (title) builder.appendLine("\nstdout:")
                    builder.append(result.stdout)
                }
                if (result.stderr.isNotEmpty()) {
                    if (title) builder.appendLine("\nstderr:")
                    builder.append(result.stderr)
                }
            }
            return Pair(builder.toString().replace("image://", Image_Path).replace("lgtbot://", SystemConfig.TEST_PATH),
                result.error.isNotEmpty() || result.stderr.isNotEmpty())
        } catch (e: Exception) {
            return Pair("[执行失败]\n原因：${e.message}", true)
        }
    }
}