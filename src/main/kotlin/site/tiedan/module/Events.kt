package site.tiedan.module

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.EventHandler
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.warning
import site.tiedan.MiraiCompilerFramework.CMD_PREFIX
import site.tiedan.MiraiCompilerFramework.MSG_MAX_LENGTH
import site.tiedan.MiraiCompilerFramework.MSG_TRANSFER_LENGTH
import site.tiedan.MiraiCompilerFramework.THREAD
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.config.DockerConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.format.ForwardMessageGenerator.stringToForwardMessage
import site.tiedan.format.ForwardMessageGenerator.trimToMaxLength
import site.tiedan.format.JsonProcessor.blockProhibitedContent
import site.tiedan.module.PastebinCodeExecutor.executeMainProcess
import site.tiedan.utils.PastebinUrlHelper
import kotlin.coroutines.CoroutineContext

object Events : SimpleListenerHost() {

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        logger.warning({ "Mirai Compiler Framework with ${exception.event}" }, exception.cause)
    }

    @Suppress("unused")
    @EventHandler(priority = EventPriority.NORMAL)
    internal suspend fun MessageEvent.process() {
        // 快捷前缀执行pastebin中的代码
        if (message.content.startsWith(PastebinConfig.QUICK_PREFIX)) {
            return toCommandSender().commandRunOnEvent(message)
        }
        // 直接run任意代码
        if (message.content.startsWith(CMD_PREFIX)) {
            return toCommandSender().codeRunOnEvent(message)
        }
    }

    suspend fun CommandSender.commandRunOnEvent(message: MessageChain) {
        val msg = message.content.removePrefix(PastebinConfig.QUICK_PREFIX).trim()
        val args = msg.split(" ")
        if (args.isEmpty()) return

        val name = PastebinData.alias[args[0]] ?: args[0]
        val userInput = args.drop(1).joinToString(separator = " ")

        if (PastebinData.pastebin.containsKey(name).not()) return

        // 执行代码并输出
        this.executeMainProcess(name, userInput)
    }

    private suspend fun CommandSender.codeRunOnEvent(message: MessageChain) {
        if (ExtraData.BlackList.contains(user?.id)) {
            return logger.info("${user?.id}已被拉黑，请求被拒绝")
        }

        val msg = message.content.removePrefix(CMD_PREFIX).trim()
        if (msg.isBlank()) {
            sendMessage("请输入正确的命令！例如：\n$CMD_PREFIX python print(\"Hello world\")")
            return
        }
        val index = msg.indexOfFirst(Char::isWhitespace)
        val language = if (index >= 0) msg.substring(0, index) else msg
        if (!GlotAPI.checkSupport(language)) {
            sendMessage(
                "不支持这种编程语言\n" +
                "${commandPrefix}glot list　列出所有支持的编程语言\n" +
                "如果要执行保存好的pastebin代码，请在指令前添加“$commandPrefix”"
            )
            return
        }
        if (THREAD >= PastebinConfig.thread_limit) {
            sendQuoteReply("当前有 $THREAD 个进程正在执行或等待冷却，请等待几秒后再次尝试")
            return
        }

        try {
            THREAD++

            // 检查命令的引用
            val quote = message[QuoteReply]
            var input: String? = null
            // 支持运行引用的消息的代码
            var code = if (quote != null) {
                // run c [input]
                if (index >= 0) {
                    input = msg.substring(index).trim()
                }
                quote.source.originalMessage.content
            } else if (index >= 0) {
                msg.substring(index).trim()
            } else {
                sendMessage("$CMD_PREFIX $language\n" + GlotAPI.getTemplateFile(language).content)
                return
            }

            // 如果是引用消息，则不再从原消息中分析。否则，还要从消息中判断是否存在输入参数
            val si = if (quote != null) 0 else code.indexOfFirst(Char::isWhitespace)
            // 尝试得到url
            val url = if (si > 0) {
                code.substring(0, si)
            } else {
                code
            }
            // 如果参数是一个pastebin的链接，则去获取具体代码
            if (PastebinUrlHelper.checkUrl(url)) {
                if (si > 0) {
                    // 如果确实是一个链接，则链接后面跟的内容就是输入内容
                    input = code.substring(si+1)
                }
                logger.info("从 $url 中获取代码")
                code = PastebinUrlHelper.get(url)
                if (code.isBlank()) {
                    sendQuoteReply("未获取到有效代码")
                    return
                }
                logger.info("请求执行代码\n$code")
            } else {
                logger.info("请求执行代码")
            }

            when (val message = runCode(this, language, code, input)) {
                is MessageChain -> sendMessage(message)
                is ForwardMessage -> sendMessage(message)
                is PlainText -> sendQuoteReply(message.content)
                else -> sendQuoteReply("[处理消息失败] 不识别的输出消息类型或内容，请联系管理员：\n" +
                        trimToMaxLength(message.toString(), 300).first
                )
            }
        } catch (e: Exception) {
            logger.warning("执行失败：${e::class.simpleName}(${e.message})")
            sendQuoteReply("[执行失败]\n原因：${e.message}")
        } finally {
            CoroutineScope(Dispatchers.IO).launch {
                delay(6000); THREAD--
            }
        }
    }

    private fun runCode(sender: CommandSender, language: String, code: String, input: String?, util: String? = null): Message {
        val result = if (language == "text")
            GlotAPI.RunResult(stdout = code)
        else
            GlotAPI.runCode(language, code, input, util)

        val builder = MessageChainBuilder()
        if (result.message.isNotEmpty()) {
            return if (language.lowercase() in DockerConfig.supportedLanguages) {
                PlainText(
                    "[执行失败]\n来自docker容器的错误信息：\n" +
                    "- error: ${result.error}\n" +
                    "- message: ${trimToMaxLength(result.message, 300).first}"
                )
            } else {
                PlainText("[执行失败]\n收到来自glot接口的消息：${result.message}")
            }
        }
        var c = 0
        if (result.stdout.isNotEmpty()) c++
        if (result.stderr.isNotEmpty()) c++
        if (result.error.isNotEmpty()) c++
        val title = c >= 2
        if (sender.subject is Group) {
            builder.add(At(sender.user!!))
            builder.add("\n")
        } else {
            val ret = blockProhibitedContent(result.stdout, at = true, isGroup = false)
            if (ret.second) builder.add("${ret.first}\n")
        }

        if (c == 0) {
            builder.add("没有任何结果呢~")
        } else {
            val sb = StringBuilder()
            if (result.error.isNotEmpty()) {
                sb.appendLine("error:")
                sb.append(result.error)
            }
            if (result.stdout.isNotEmpty()) {
                if (title) sb.appendLine("\nstdout:")
                sb.append(result.stdout)
            }
            if (result.stderr.isNotEmpty()) {
                if (title) sb.appendLine("\nstderr:")
                sb.append(result.stderr)
            }
            // 输出内容过长，改为转发消息
            if ((sb.length > MSG_TRANSFER_LENGTH || sb.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                return stringToForwardMessage(sb, sender.subject)
            }
            // 非转发消息截断
            if (sb.length > MSG_MAX_LENGTH) {
                sb.deleteRange(MSG_MAX_LENGTH, sb.length)
                sb.append("\n消息内容过长，已截断")
            }
            builder.append(sb.toString())
        }
        return builder.build()
    }
}