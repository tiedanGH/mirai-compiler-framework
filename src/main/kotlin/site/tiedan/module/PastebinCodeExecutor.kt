package site.tiedan.module

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.RawForwardMessage
import net.mamoe.mirai.message.data.ShortVideo
import net.mamoe.mirai.message.data.buildForwardMessage
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.MSG_TRANSFER_LENGTH
import site.tiedan.MiraiCompilerFramework.THREAD
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.command.CommandRun.Image_Path
import site.tiedan.config.DockerConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.config.SystemConfig
import site.tiedan.data.CodeCache
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.data.PastebinStorage
import site.tiedan.format.AudioGenerator.generateAudio
import site.tiedan.format.Base64Processor.encodeImagesToBase64
import site.tiedan.format.Base64Processor.fileToMessage
import site.tiedan.format.Base64Processor.processBase64
import site.tiedan.format.ForwardMessageGenerator.generateForwardMessage
import site.tiedan.format.ForwardMessageGenerator.stringToForwardMessage
import site.tiedan.format.ForwardMessageGenerator.trimToMaxLength
import site.tiedan.format.JsonProcessor
import site.tiedan.format.JsonProcessor.blockProhibitedContent
import site.tiedan.format.JsonProcessor.generateMessageChain
import site.tiedan.format.JsonProcessor.outputMultipleMessage
import site.tiedan.format.JsonProcessor.processDecode
import site.tiedan.format.JsonProcessor.processEncode
import site.tiedan.format.JsonProcessor.savePastebinStorage
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.format.MarkdownImageGenerator.processMarkdown
import site.tiedan.module.RequestLimiter.newRequest
import site.tiedan.utils.DownloadHelper.downloadImage
import site.tiedan.utils.PastebinUrlHelper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.LocalTime
import kotlin.collections.set
import kotlin.text.isNotBlank

object PastebinCodeExecutor {
    private val OutputLock = Mutex()
    private val StorageLock = Mutex()
    
    suspend fun CommandSender.executeMainProcess(name: String, userInput: String, imageUrls: List<String>) {

        val userID = user?.id ?: 10000

        if (ExtraData.BlackList.contains(userID)) {
            logger.info("${userID}已被拉黑，请求被拒绝")
            return
        }

        val request = newRequest(userID)
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
            var jsonDecodeResult = JsonProcessor.JsonMessage()
            var activeMessage: JsonProcessor.ActiveMessage? = null
            var outputGlobal: String? = null
            var outputStorage: String? = null

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
                if (StorageLock.isLocked) logger.info("(${userID})执行$name [存储]进程执行请求等待中...")
                StorageLock.lock()
                val global = PastebinStorage.Storage[name]?.get(0) ?: ""
                val storage = PastebinStorage.Storage[name]?.get(userID) ?: ""
                val from = if (subject is Group) "${(subject as Group).name}(${(subject as Group).id})" else "private"
                val encodeBase64 = PastebinData.pastebin[name]?.get("base64") == "true"
                val imageData = encodeImagesToBase64(imageUrls, encodeBase64)

                val jsonInput = processEncode(global, storage, userID, nickname, from, imageData)
                input = "$jsonInput\n$userInput"
                logger.info("输入Storage数据: global{${global.length}} storage{${storage.length}} $nickname($userID) $from")
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
                jsonDecodeResult = processDecode(output)
                if (jsonDecodeResult.error.isNotEmpty()) {
                    if (PastebinConfig.enable_ForwardMessage) {
                        val forward = buildForwardMessage(subject!!) {
                            displayStrategy = object : ForwardMessage.DisplayStrategy {
                                override fun generateTitle(forward: RawForwardMessage): String = "输出解析错误"
                                override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("执行失败：JSON解析错误")
                            }
                            subject!!.bot named "Error" says "[错误] ${jsonDecodeResult.error}"
                            val (resultString, tooLong) = trimToMaxLength(output, 10000)
                            if (tooLong) {
                                subject!!.bot named "Error" says "原始输出过大，仅截取前10000个字符"
                            }
                            subject!!.bot named "原始输出" says "程序原始输出：\n$resultString"
                        }
                        sendMessage(forward)
                    } else {
                        sendQuoteReply("[错误] ${jsonDecodeResult.error}")
                    }
                    return
                }
                outputFormat = jsonDecodeResult.format
                outputAt = jsonDecodeResult.at
                activeMessage = jsonDecodeResult.active
                outputGlobal = jsonDecodeResult.global
                outputStorage = jsonDecodeResult.storage
                width = jsonDecodeResult.width.toString()
                if (outputFormat != "MessageChain") {
                    output = if (outputFormat in listOf("markdown", "base64")) jsonDecodeResult.content
                    else blockProhibitedContent(jsonDecodeResult.content, outputAt, subject is Group).first
                }
                if (outputFormat in listOf("json", "ForwardMessage")) {
                    sendQuoteReply("禁止套娃：不支持在JsonMessage或JsonForwardMessage内使用“$outputFormat”输出格式")
                    return
                }
            }
            // 非text输出需锁定输出进程
            if (outputFormat != "text") {
                if (OutputLock.isLocked) logger.info("(${userID})执行$name [输出]进程执行请求等待中...")
                OutputLock.lock()
            }
            // 输出内容生成
            val builder = when (outputFormat) {
                // text文本输出
                "text"-> {
                    if ((output.length > MSG_TRANSFER_LENGTH || output.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                        stringToForwardMessage(StringBuilder(output), subject)
                    } else {
                        val messageBuilder = MessageChainBuilder()
                        if (subject is Group && outputAt) {
                            messageBuilder.add(At(user!!))
                            messageBuilder.add("\n")
                        } else {
                            val ret = blockProhibitedContent(output, at = true, isGroup = false)
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
                    val markdownResult = processMarkdown(name, output, width ?: "600")
                    if (!markdownResult.success) {
                        sendQuoteReply(markdownResult.message)
                        return
                    }
                    val file = File("${cacheFolder}markdown.png")
                    subject?.uploadFileToImage(file)     // 返回结果图片
                        ?: return sendQuoteReply("[错误] 图片文件异常：ExternalResource上传失败，请尝试重新执行")
                }
                // base64自定义格式输出
                "base64"-> {
                    val base64Result = processBase64(output)
                    if (!base64Result.success) {
                        sendQuoteReply(base64Result.extension)
                        return
                    }
                    fileToMessage(base64Result.fileType, base64Result.extension, subject, true)
                        ?: return sendQuoteReply("[错误] Base64文件转换时出现未知错误，请联系管理员")
                }
                // 普通图片输出
                "image"-> {
                    val file = if (output.startsWith("file:///")) {
                        File(URI(output))
                    } else {
                        val downloadResult = downloadImage(name, output, cacheFolder, "image", force = true)
                        if (!downloadResult.success) {
                            sendQuoteReply(downloadResult.message)
                            return
                        }
                        File("${cacheFolder}image")
                    }
                    if (!file.exists()) {
                        sendQuoteReply("[错误] 本地图片文件不存在，请检查路径")
                        return
                    }
                    subject?.uploadFileToImage(file)     // 返回结果图片
                        ?: return sendQuoteReply("[错误] 图片文件异常：ExternalResource上传失败，请尝试重新执行")
                }
                // LaTeX转图片输出
                "LaTeX"-> {
                    val renderResult = renderLatexOnline(output)
                    if (renderResult.startsWith("QuickLaTeX")) {
                        sendQuoteReply("[错误] $renderResult")
                        return
                    }
                    val file = File("${cacheFolder}latex.png")
                    subject?.uploadFileToImage(file)     // 返回结果图片
                        ?: return sendQuoteReply("[错误] 图片文件异常：ExternalResource上传失败，请尝试重新执行")
                }
                // json分支功能MessageChain
                "MessageChain"-> {
                    generateMessageChain(name, jsonDecodeResult, this).first
                }
                // json分支功能MultipleMessage
                "MultipleMessage"-> {
                    "MultipleMessage"
                }
                // 转发消息生成（JSON在内部进行解析）
                "ForwardMessage"-> {
                    val forwardMessageData = generateForwardMessage(name, output, this)
                    outputGlobal = forwardMessageData.global
                    outputStorage = forwardMessageData.storage
                    forwardMessageData.forwardMessage     // 返回ForwardMessage
                }
                // TTS音频消息生成（JSON在内部进行解析）
                "Audio"-> {
                    val audioData = generateAudio(output, subject)
                    outputGlobal = audioData.global
                    outputStorage = audioData.storage
                    if (audioData.success) {
                        audioData.audio
                    } else {
                        sendQuoteReply(audioData.error)
                    }
                }
                else -> {
                    sendQuoteReply("代码执行完成但无法输出：无效的输出格式：$outputFormat，请联系创建者修改格式")
                    return
                }
            }
            // 根据消息类型进行回复
            when (builder) {
                is MessageChain -> sendMessage(builder)
                is ForwardMessage -> sendMessage(builder)
                is Image -> sendMessage(builder)
                is Audio -> sendMessage(builder)
                is ShortVideo -> sendMessage(builder)
                is String -> {
                    val ret = outputMultipleMessage(name, jsonDecodeResult, this)
                    if (ret != null) {
                        sendQuoteReply("【输出多条消息时出错】$ret")
                    }
                }
                is Unit -> { /* do nothing */ }
                null -> sendQuoteReply("[处理消息失败] 意料之外的消息结果 null，请联系管理员")
                else -> sendQuoteReply("[处理消息失败] 不识别的输出消息类型或内容，请联系管理员：\n" +
                        trimToMaxLength(builder.toString(), 300).first
                )
            }
            // 原始格式支持且开启存储功能：在程序执行和输出均无错误，且发送消息成功时才进行保存
            if (format in MiraiCompilerFramework.enableStorageFormats && storageMode == "true") {
                // 额外检测：执行后原项目消失（如被删除、存储被关闭），则不再保存存储
                if (PastebinData.pastebin[name]?.get("storage") == null) {
                    sendQuoteReply("[存储错误] 拒绝访问：名称 $name 不存在或未开启存储，保存数据失败！")
                    return
                }
                savePastebinStorage(name, userID, outputGlobal, outputStorage)
            }
            // 主动消息相关
            if (activeMessage != null) {
                val ret = handleActiveMessage(activeMessage, this, name)
                if (ret.isNotEmpty()) {
                    sendQuoteReply("【主动消息错误】$ret")
                }
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

    suspend fun handleActiveMessage(
        activeMessage: JsonProcessor.ActiveMessage,
        sender: CommandSender,
        name: String,
    ): String {
        var result = ""
        // 群聊主动消息
        if (activeMessage.group != null) {
            val group = sender.bot?.getGroup(activeMessage.group)
            if (group != null) {
                try {
                    val message = activeMessage.content
                    if ((message.length > MSG_TRANSFER_LENGTH || message.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                        group.sendMessage(stringToForwardMessage(StringBuilder(message), sender.subject, "$name[主动消息]"))
                    } else {
                        group.sendMessage("【$name[主动消息]】\n$message")
                    }
                } catch (e: Exception) {
                    result += "\n[群聊] 消息发送出错：${e.message}"
                }
            } else {
                result += "\n[群聊] 消息发送失败：bot未加入此群聊(${activeMessage.group})，请检查群号是否正确或联系bot所有者"
            }
        }
        // 私信主动消息
        val seenIds = mutableSetOf<Long>()
        activeMessage.private?.forEachIndexed { index, singlePrivateMessage ->
            if (index >= 10) {
                return "$result\n[私信] 执行中断：单次私信主动消息上限为10条"
            }
            val id = singlePrivateMessage.userID
            if (id == null) {
                result += "\n[私信] 消息发送失败：userID为空"
                return@forEachIndexed
            }
            if (!seenIds.add(id)) {
                result += "\n[私信] 消息发送失败：检测到重复userID($id)"
                return@forEachIndexed
            }
            val friend = sender.bot?.getFriend(id)
            if (friend != null) {
                val now = LocalTime.now().hour
                val allowTime = ExtraData.private_allowTime[id]
                if (allowTime != null) {
                    if (notInAllowTime(now, allowTime.first, allowTime.second)) {
                        result += "\n[私信] 权限不足：不在${id}设置的可用时间段内"
                        return@forEachIndexed
                    }
                } else {
                    result += "\n[私信] 权限不足：${id}不允许任何主动消息"
                    return@forEachIndexed
                }
                try {
                    val message = "来自：${sender.name}(${sender.user?.id ?: 10000})\n【消息内容】\n${singlePrivateMessage.content}"
                    if ((message.length > MSG_TRANSFER_LENGTH || message.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                        friend.sendMessage(stringToForwardMessage(StringBuilder(message), sender.subject, "$name[主动消息]"))
                    } else {
                        friend.sendMessage("【$name[主动消息]】\n$message")
                    }
                } catch (e: Exception) {
                    result += "\n[私信] 消息发送出错：${e.message}"
                    return@forEachIndexed
                }
            } else {
                result += "\n[私信] 消息发送失败：获取好友失败(${id})，请检查ID是否正确"
                return@forEachIndexed
            }
            delay(1000)
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