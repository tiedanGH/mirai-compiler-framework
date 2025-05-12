import CommandPastebin.sendQuoteReply
import JCompilerCollection.MSG_TRANSFER_LENGTH
import JCompilerCollection.THREAD
import JCompilerCollection.logger
import JCompilerCollection.runCode
import JCompilerCollection.save
import config.PastebinConfig
import data.CodeCache
import data.ExtraData
import data.PastebinData
import data.PastebinStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import utils.DownloadHelper.downloadImage
import utils.ForwardMessageGenerator.generateForwardMessage
import utils.ForwardMessageGenerator.stringToForwardMessage
import utils.ForwardMessageGenerator.trimToMaxLength
import utils.GlotAPI
import utils.JsonProcessor
import utils.JsonProcessor.blockSensitiveContent
import utils.JsonProcessor.generateMessageChain
import utils.JsonProcessor.outputMultipleMessage
import utils.JsonProcessor.processDecode
import utils.JsonProcessor.processEncode
import utils.JsonProcessor.savePastebinStorage
import utils.MarkdownImageProcessor.folder
import utils.MarkdownImageProcessor.processMarkdown
import utils.RequestLimiter.newRequest
import utils.Statistics
import utils.UbuntuPastebinHelper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.LocalTime

object CommandRun : RawCommand(
    owner = JCompilerCollection,
    primaryName = "run",
    secondaryNames = arrayOf("运行"),
    description = "运行pastebin中的代码",
    usage = "${commandPrefix}run <名称> [输入]"
){
    private val RunCodeLock = Mutex()
    private val OutputLock = Mutex()
    private val StorageLock = Mutex()
    @OptIn(ConsoleExperimentalApi::class)
    private val Image_Path = "file:////home/lighthouse/MiraiConsole/data/${JCompilerCollection.dataHolderName}/images/"
    private const val GAME_PATH = "file:////home/lighthouse/lgtbot-mirai/lgtbot/games/"

    /**
     * 从保存的pastebin链接中直接运行
     */
    override suspend fun CommandContext.onCommand(args: MessageChain) {

        val userID = sender.user?.id ?: 10000

        if (ExtraData.BlackList.contains(userID)) {
            logger.info("${userID}已被拉黑，请求被拒绝")
            return
        }
        val now = LocalTime.now()
        val start = LocalTime.MIDNIGHT
        val end = LocalTime.of(0, 11)
        if (!now.isBefore(start) && now.isBefore(end)) {
            sendQuoteReply(sender, originalMessage, "[维护中] 00:00~00:10为bot卡顿高发时段，暂不支持代码执行请求")
            return
        }

        val request = newRequest(userID)
        if (request.first.isNotEmpty()) {
            sendQuoteReply(sender, originalMessage, request.first)
            if (request.second) return
        }

        if (THREAD >= PastebinConfig.thread_limit) {
            sendQuoteReply(sender, originalMessage, "当前已经有 $THREAD 个进程正在执行，请等待几秒后再次尝试")
            return
        }
        val name = try {
            PastebinData.alias[args[0].content] ?: args[0].content
        } catch (e: Exception) {
            sendQuoteReply(sender, originalMessage, "[指令无效]\n${commandPrefix}run <名称> [输入]\n运行保存的pastebin代码")
            return
        }
        if (PastebinData.pastebin.containsKey(name).not()) {
            sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
            return
        }
        if (PastebinData.groupOnly.contains(name) && (sender.subject is Group).not() &&
            userID.toString() != PastebinData.pastebin[name]?.get("userID") && PastebinConfig.admins.contains(userID).not()) {
            sendQuoteReply(sender, originalMessage, "执行失败：此条代码链接被标记为仅限群聊中执行！")
            return
        }
        if (PastebinData.censorList.contains(name)) {
            sendQuoteReply(sender, originalMessage, "此条链接仍在审核中，暂时无法执行。管理员会定期对链接进行审核，您也可以主动联系进行催审")
            return
        }

        try {
            THREAD++

            val language = PastebinData.pastebin[name]?.get("language").toString()
            val url = PastebinData.pastebin[name]?.get("pastebinUrl").toString()
            val format = PastebinData.pastebin[name]?.get("format")
            var width = PastebinData.pastebin[name]?.get("width")
            val util = PastebinData.pastebin[name]?.get("util")
            val storageMode = PastebinData.pastebin[name]?.get("storage")
            val nickname = sender.name
            val userInput = args.drop(1).joinToString(separator = " ")
            var input = userInput

            var output = ""
            var outputFormat = format
            var outputAt = true
            var jsonDecodeResult = JsonProcessor.JsonMessage()
            var activeMessage: JsonProcessor.ActiveMessage? = null
            var outputGlobal: String? = null
            var outputStorage: String? = null

            // 数据统计
            Statistics.setName(name)
            Statistics.countRun()

            // 从url或缓存获取代码
            val code: String = if (url.startsWith("https://pastebin.ubuntu.com/p/")) {
                if (CodeCache.CodeCache.contains(name)) {
                    logger.info("从 CodeCache: $name 中获取代码")
                    CodeCache.CodeCache[name]!!
                } else {
                    logger.info("从 $url 中获取代码")
                    val cache = UbuntuPastebinHelper.get(url)
                    if (cache.isNotBlank()) {
                        CodeCache.CodeCache[name] = cache
                        CodeCache.save()
                        sender.sendMessage("【$name】已保存至缓存，下次执行时将从缓存中获取代码")
                        cache
                    } else {
                        sender.sendMessage("【$name】保存至缓存失败且无法执行：获取代码失败或代码为空，请联系代码创建者")
                        return
                    }
                }
            } else {
                logger.info("从 $url 中获取代码")
                UbuntuPastebinHelper.get(url)
            }

            if (code.isBlank()) {
                sendQuoteReply(sender, originalMessage, "[执行失败] 未获取到有效代码")
                return
            }

            // 输入存储的数据
            if (storageMode == "true") {
                if (StorageLock.isLocked) logger.info("(${userID})执行$name [存储]进程执行请求等待中...")
                StorageLock.lock()
                val global = PastebinStorage.Storage[name]?.get(0) ?: ""
                val storage = PastebinStorage.Storage[name]?.get(userID) ?: ""
                val from = if (sender.subject is Group) "${(sender.subject as Group).name}(${(sender.subject as Group).id})" else "private"
                val jsonInput = processEncode(global, storage, userID, nickname, from)
                input = "$jsonInput\n$userInput"
                logger.info("输入Storage数据: global{${global.length}} storage{${storage.length}} $nickname($userID) $from")
            }

//            logger.warning("[DEBUG]input: $input")

            // 特殊格式在这里执行代码，返回字符串输出
            if (listOf("markdown", "base64", "image", "LaTeX", "json", "ForwardMessage").contains(format)) {
                val pair = runCodeToString(language, code, util, input, name, userInput)      // 特殊格式限制一个执行
                output = pair.first
                if (pair.second) {
                    if (output.startsWith("执行失败")) {
                        sendQuoteReply(sender, originalMessage, output)
                        return
                    }
                    outputFormat = "raw-text"
                }
            }
            // 解析json
            if (outputFormat == "json") {
                jsonDecodeResult = processDecode(output)
                if (jsonDecodeResult.error.isNotEmpty()) {
                    if (PastebinConfig.enable_ForwardMessage) {
                        val forward = buildForwardMessage(sender.subject!!) {
                            displayStrategy = object : ForwardMessage.DisplayStrategy {
                                override fun generateTitle(forward: RawForwardMessage): String = "JSON解析错误"
                                override fun generatePreview(forward: RawForwardMessage): List<String> = listOf("[执行失败] 发生错误")
                            }
                            sender.subject!!.bot named "Error" says "[错误] ${jsonDecodeResult.error}"
                            val (resultString, tooLong) = trimToMaxLength(output, 10000)
                            if (tooLong) {
                                sender.subject!!.bot named "Error" says "原始输出过大，仅截取前10000个字符"
                            }
                            sender.subject!!.bot named "原始输出" says "程序原始输出：\n$resultString"
                        }
                        sender.sendMessage(forward)
                    } else {
                        sendQuoteReply(sender, originalMessage, "[错误] ${jsonDecodeResult.error}")
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
                    output = if (listOf("markdown", "base64").contains(outputFormat)) jsonDecodeResult.content
                            else blockSensitiveContent(jsonDecodeResult.content, outputAt, sender.subject is Group)
                }
                if (listOf("json", "ForwardMessage").contains(outputFormat)) {
                    sendQuoteReply(sender, originalMessage, "禁止套娃：不支持在JsonMessage或JsonForwardMessage内使用“$outputFormat”输出格式")
                    return
                }
                if (outputFormat == "text") outputFormat = "raw-text"
            }
            // 非text输出或包含主动消息需锁定输出进程
            if (listOf(null, "text", "raw-text").contains(outputFormat).not() || activeMessage != null) {
                if (OutputLock.isLocked) logger.info("(${userID})执行$name [输出]进程执行请求等待中...")
                OutputLock.lock()
            }
            // 输出内容生成
            val builder = when (outputFormat) {
                // 正常text在这里才执行代码（其他格式raw-text则直接输出）
                "text", null-> {
                    try {
                        logger.info("请求执行 PastebinData: $name 中的代码，input: $input")
                        runCode(sender.subject, sender.user, language, code, input, util)
                    } catch (e: Exception) {
                        sendQuoteReply(sender, originalMessage, "执行失败\n原因：${e.message}")
                        return
                    }
                }
                // markdown和base64转图片输出
                "markdown", "base64"-> {
                    if (outputFormat == "base64") output = "![base64image]($output)"
                    val processResult = if (width == null) {
                        processMarkdown(output)
                    } else {
                        processMarkdown(output, width)
                    }
                    if (processResult.first.startsWith("操作失败")) {
                        sendQuoteReply(sender, originalMessage, processResult.first)
                        return
                    }
                    val file = File("${folder}/markdown.png")
                    file.toExternalResource().use { resource ->     // 返回结果图片
                        sender.subject?.uploadImage(resource)
                    }
                }
                // 普通图片输出
                "image"-> {
                    val file = if (output.startsWith("file:///")) {
                        File(URI(output))
                    } else {
                        @OptIn(ConsoleExperimentalApi::class)
                        val outputFilePath = "./data/${JCompilerCollection.dataHolderName}/"
                        val pair = downloadImage(output, outputFilePath, "image.tmp", force = true)
                        if (pair.first.startsWith("[错误]")) {
                            sendQuoteReply(sender, originalMessage, pair.first)
                            return
                        }
                        logger.info("图片下载完成，用时${pair.second}秒")
                        File("${folder}/image.tmp")
                    }
                    if (!file.exists()) {
                        sendQuoteReply(sender, originalMessage, "[错误] 本地图片文件不存在，请检查路径")
                        return
                    }
                    file.toExternalResource().use { resource ->     // 返回结果图片
                        sender.subject?.uploadImage(resource)
                    }
                }
                // LaTeX转图片输出
                "LaTeX"-> {
                    val renderResult = renderLatexOnline(output)
                    if (renderResult.startsWith("QuickLaTeX")) {
                        sendQuoteReply(sender, originalMessage, "[错误] $renderResult")
                        return
                    }
                    val file = File("${folder}/latex.png")
                    file.toExternalResource().use { resource ->     // 返回结果图片
                        sender.subject?.uploadImage(resource)
                    }
                }
                // 不再次运行直接输出
                "raw-text"-> {
                    if ((output.length > MSG_TRANSFER_LENGTH || output.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                        stringToForwardMessage(StringBuilder(output), sender.subject)
                    } else {
                        val messageBuilder = MessageChainBuilder()
                        if (sender.subject is Group && outputAt) {
                            messageBuilder.add(At(sender.user!!))
                            messageBuilder.add("\n")
                        }
                        if (output.isEmpty()) {
                            messageBuilder.add("没有任何结果呢~")
                        } else {
                            messageBuilder.add(output)
                        }
                        messageBuilder
                    }
                }
                // json分支功能MessageChain
                "MessageChain"-> {
                    generateMessageChain(jsonDecodeResult, sender).first
                }
                // json分支功能MultipleMessage
                "MultipleMessage"-> {
                    "MultipleMessage"
                }
                // 转发消息生成（JSON在内部进行解析）
                "ForwardMessage"-> {
                    val triple = generateForwardMessage(output, sender)
                    outputGlobal = triple.second
                    outputStorage = triple.third
                    triple.first        // 返回ForwardMessage
                }
                else -> {
                    sendQuoteReply(sender, originalMessage, "代码执行完成但无法输出：无效的输出格式：$outputFormat，请联系创建者修改格式")
                    return
                }
            }
            when (builder) {
                is MessageChainBuilder -> sender.sendMessage(builder.build())
                is ForwardMessage -> sender.sendMessage(builder)
                is Image -> sender.sendMessage(builder)
                is String -> {
                    val ret = outputMultipleMessage(jsonDecodeResult, sender)
                    if (ret != null) {
                        sendQuoteReply(sender, originalMessage, "【输出多条消息时出错】$ret")
                    }
                }
                else -> sendQuoteReply(sender, originalMessage, "[处理消息失败] 不识别的输出消息类型或内容，请联系铁蛋：" + builder.toString())
            }
            // 原始格式为json、ForwardMessage且开启存档：在程序执行和输出均无错误，且发送消息成功时才进行保存
            if ((format == "json" || format == "ForwardMessage") && storageMode == "true") {
                savePastebinStorage(name, userID, outputGlobal, outputStorage)
            }
            // 主动消息相关
            if (activeMessage != null) {
                val ret = handleActiveMessage(activeMessage, sender, name)
                if (ret.isNotEmpty()) {
                    sendQuoteReply(sender, originalMessage, "【主动消息错误】$ret")
                }
            }
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply(sender, originalMessage, "[指令运行错误](非代码问题)\n报错类别：${e::class.simpleName}\n报错信息：${e.message?.take(100)}")
        } finally {
            THREAD--
            if (OutputLock.isLocked) OutputLock.unlock()
            if (StorageLock.isLocked) StorageLock.unlock()
        }
    }

    private suspend fun handleActiveMessage(
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
                    if ((now in allowTime.first..allowTime.second).not()) {
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
            delay(2000)
        }
        return result
    }

    fun renderLatexOnline(latex: String): String {
        val apiUrl = "https://quicklatex.com/latex3.f"
        val outputFilePath = "$folder/latex.png"
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

    private suspend fun runCodeToString(language: String, code: String, util: String?, input: String?, name: String, userInput: String): Pair<String, Boolean> {
        try {
            RunCodeLock.lock()
            logger.info("请求执行 PastebinData: $name 中的代码，input: $userInput")
            val result = if (language == "text")
                GlotAPI.RunResult(stdout = code)
            else
                GlotAPI.runCode(language, code, input, util)

            val builder = StringBuilder()
            if (result.message.isNotEmpty()) {
                builder.append("执行失败\n收到来自glot接口的消息：")
                builder.append(result.message)
                return Pair(builder.toString(), true)
            }
            var c = 0
            if (result.stdout.isNotEmpty()) c++
            if (result.stderr.isNotEmpty()) c++
            if (result.error.isNotEmpty()) c++
            val title = c >= 2

            if (c == 0) {
                builder.append("[警告] 程序未输出任何内容，无法转换至预设输出格式")
                return Pair(builder.toString(), true)
            } else {
                if (result.error.isNotEmpty()) {
                    builder.appendLine("程序发生错误，默认使用text输出")
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
            return Pair(builder.toString().replace("image://", Image_Path).replace("lgtbot://", GAME_PATH),
                result.error.isNotEmpty() || result.stderr.isNotEmpty())
        } catch (e: Exception) {
            return Pair("执行失败\n原因：${e.message}", true)
        } finally {
            RunCodeLock.unlock()
        }
    }
}
