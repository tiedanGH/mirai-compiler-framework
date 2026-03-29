package site.tiedan.core

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.ERROR_FORWARD_MAX_LENGTH
import site.tiedan.MiraiCompilerFramework.ERROR_MSG_MAX_LENGTH
import site.tiedan.MiraiCompilerFramework.THREADS
import site.tiedan.MiraiCompilerFramework.ThreadInfo
import site.tiedan.MiraiCompilerFramework.getPlatform
import site.tiedan.MiraiCompilerFramework.getUserPlatformID
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.parseUserID
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.trimToMaxLength
import site.tiedan.command.CommandRun.Image_Path
import site.tiedan.config.DockerConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.config.SystemConfig
import site.tiedan.core.OutputHandler.handleActiveMessage
import site.tiedan.core.OutputHandler.handleOutputFormats
import site.tiedan.data.CodeCache
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.format.Base64Processor
import site.tiedan.format.JsonProcessor
import site.tiedan.module.RequestLimiter
import site.tiedan.module.Statistics
import site.tiedan.utils.HttpUtil
import site.tiedan.utils.PastebinUrlHelper
import java.net.ConnectException
import kotlin.collections.set

/**
 * # PastebinCodeExecutor
 * - 代码执行主进程 [executeMainProcess]
 * - 运行代码并返回输出字符串 [runCodeToString]
 *
 * @author tiedanGH
 */
object PastebinCodeExecutor {

    /**
     * ## pb代码执行主进程
     * @param name 项目名称
     * @param userInput 用户输入
     * @param imageUrls 输入图片URL链接
     */
    suspend fun CommandSender.executeMainProcess(name: String, userInput: String, imageUrls: List<String>) {

        val userID = getUserPlatformID(this.user?.id) ?: "10000"
        val numID = parseUserID(userID)
            ?: return sendQuoteReply("[用户ID解析失败] 无法解析您的用户ID，请联系管理员")
        val nickname = this.name

        if (ExtraData.BlackList.contains(userID)) {
            logger.warning("$userID 已被拉黑，请求被拒绝")
            return
        }

        // 请求频率限制
        val request = RequestLimiter.newRequest(userID)
        if (request.first.isNotEmpty()) {
            sendQuoteReply(request.first)
            if (request.second) return
        }

        if (THREADS.size >= PastebinConfig.thread_limit) {
            sendQuoteReply("执行失败：当前已经有 ${THREADS.size} 个进程正在执行，请等待几秒后再次尝试")
            return
        }
        val ownerID = PastebinData.pastebin[name]?.get("userID")
        val isOwner = userID == ownerID
        val isAdmin = PastebinConfig.admins.contains(userID)
        if (PastebinData.groupOnly.contains(name) && (subject is Group).not() && !isOwner && !isAdmin) {
            sendQuoteReply("执行失败：此条代码链接被标记为仅限群聊中执行！")
            return
        }
        if (PastebinData.censorList.contains(name)) {
            sendQuoteReply("执行失败：此条链接仍在审核中，暂时无法执行。管理员会定期对链接进行审核，您也可以主动联系进行催审")
            return
        }

        val jobId = "${System.currentTimeMillis()}-${name}-$nickname($userID)"
        val from = if (subject is Group) "${(subject as Group).name}(${(subject as Group).id})" else "private"
        val platform = getPlatform()

        THREADS.add(ThreadInfo(jobId, name, "$nickname($userID)", from, platform))

        try {
            val language = PastebinData.pastebin[name]?.get("language").toString()
            val url = PastebinData.pastebin[name]?.get("url").toString()
            val format = PastebinData.pastebin[name]?.get("format") ?: "text"
            var width = PastebinData.pastebin[name]?.get("width")
            val util = PastebinData.pastebin[name]?.get("util")
            val storageMode = PastebinData.pastebin[name]?.get("storage")
            var input = userInput

            var output: String
            var outputFormat = format
            var outputAt = true
            var messageList: List<JsonProcessor.SingleChainMessage> = listOf(JsonProcessor.SingleChainMessage())
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
                    "报错信息：${trimToMaxLength(e.message.toString(), ERROR_MSG_MAX_LENGTH).first}"
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
                if (StorageManager.isLocked()) {
                    logger.debug("(${userID})执行$name [存储]进程执行请求等待中...")
                    if (THREADS.size > 3) sendQuoteReply("当前进程较多（${THREADS.size - 1} 个正在等待），等待时间可能较长")
                }
                StorageManager.lock()
                val global = StorageManager.getGlobalData(name)
                val storage = StorageManager.getStorageData(name, numID, platform)
                val bucket = StorageManager.getBucketData(name)
                val encodeBase64 = PastebinData.pastebin[name]?.get("base64") == "true"
                val imageData = Base64Processor.encodeImagesToBase64(imageUrls, encodeBase64)
                val avatar = MiraiCompilerFramework.getAvatarUrl(numID, platform)

                val jsonInput = JsonProcessor.processEncode(global, storage, bucket, numID, nickname, avatar, from, platform, imageData)
                input = "$jsonInput\n$userInput"

                val platformInfo = if (platform == "qq") "" else "($platform)"
                logger.info(
                    "输入存储数据: global{${global.length}} storage$platformInfo{${storage.length}} " +
                    "bucket{${bucket.joinToString(" ") { "[${it.id}](${it.content?.length})" }}}"
                )
                logger.debug("请求用户环境：$nickname($numID) $from $platform")
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
                            val (resultString, tooLong) = trimToMaxLength(output, ERROR_FORWARD_MAX_LENGTH)
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
                if (OutputHandler.isLocked()) logger.debug("(${userID})执行$name [输出]进程执行请求等待中...")
                OutputHandler.lock()
            }
            // 处理程序输出格式
            val message = this.handleOutputFormats(
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
                        trimToMaxLength(message.toString(), ERROR_MSG_MAX_LENGTH).first
                )
            }
            // 主动消息相关
            if (activeMessage != null) {
                val ret = this.handleActiveMessage(name, activeMessage)
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
                val ret = StorageManager.savePastebinStorage(name, numID, platform, outputGlobal, outputStorage, outputBucket)
                if (ret != null) sendQuoteReply("【存储错误】$ret")
            }
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply(
                "[指令运行错误](非代码问题)\n" +
                "报错类别：${e::class.simpleName}\n" +
                "报错信息：${trimToMaxLength(e.message.toString(), ERROR_MSG_MAX_LENGTH).first}"
            )
        } finally {
            THREADS.removeIf { it.id == jobId }
            if (OutputHandler.isLocked()) OutputHandler.unlock()
            if (StorageManager.isLocked()) StorageManager.unlock()
        }
    }

    /**
     * 运行代码并返回输出字符串
     */
    private fun runCodeToString(
        name: String,
        language: String,
        code: String,
        format: String,
        util: String?,
        input: String?,
        userInput: String
    ): Pair<String, Boolean> {
        try {
            logger.debug("请求执行 PastebinData: $name 中的代码，input: $userInput")
            val result = if (language == "text")
                GlotAPI.RunResult(stdout = code)
            else
                GlotAPI.runCode(language, code, input, util)

            val builder = StringBuilder()
            if (result.message.isNotEmpty()) {
                if (language.lowercase() in DockerConfig.supportedLanguages) {
                    builder.append("[执行失败]\n来自docker容器的错误信息：\n")
                    builder.append("- error: ${result.error}\n")
                    builder.append("- message: ${trimToMaxLength(result.message, ERROR_MSG_MAX_LENGTH).first}")
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
            return when (e) {
                is ConnectException,
                is HttpUtil.HttpException ->
                    Pair("[API服务异常]\n原因：${e.message}", true)

                else -> {
                    logger.warning("执行失败：${e::class.simpleName}(${e.message})")
                    Pair("[执行失败]\n原因：${e.message}", true)
                }
            }
        }
    }
}
