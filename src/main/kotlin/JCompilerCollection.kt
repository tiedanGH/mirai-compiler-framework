import config.MailConfig
import config.PastebinConfig
import data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.info
import utils.ForwardMessageGenerator.stringToForwardMessage
import utils.GlotAPI
import utils.JsonProcessor.blockSensitiveContent
import utils.UbuntuPastebinHelper
import utils.calculateNextClearDelay
import utils.executeClearBlackList

object JCompilerCollection : KotlinPlugin(
    JvmPluginDescription(
        id = "top.jie65535.mirai-console-jcc-plugin",
        name = "J Compiler Collection",
        version = "1.1.0-pastebin",
    ) {
        author("jie65535")
        info("""在线编译器集合""")
    }
) {
    const val CMD_PREFIX = "run"
    const val MSG_TRANSFER_LENGTH = 550
    private const val MSG_MAX_LENGTH = 800

    var THREAD = 0

    override fun onEnable() {
        logger.info { "Plugin loaded" }
        JccCommand.register()
        JccPluginData.reload()

        CommandPastebin.register()
        CommandRun.register()
        PastebinConfig.reload()
        MailConfig.reload()
        PastebinData.reload()
        ExtraData.reload()
        PastebinStorage.reload()
        CodeCache.reload()

        startTimer()

        globalEventChannel()
            .parentScope(this)
            .subscribeMessages {
                content {
                    message.firstIsInstanceOrNull<PlainText>()?.content?.trimStart()?.startsWith(CMD_PREFIX) == true
                } reply {
                    if (ExtraData.BlackList.contains(sender.id)) {
                        return@reply "${sender.id}已被拉黑，请求被拒绝"
                    }

                    val msg = message.firstIsInstance<PlainText>().content.trimStart().removePrefix(CMD_PREFIX).trim()
                    if (msg.isBlank()) {
                        return@reply "请输入正确的命令！例如：\n$CMD_PREFIX python print(\"Hello world\")"
                    }

                    val index = msg.indexOfFirst(Char::isWhitespace)
                    val language = if (index >= 0) msg.substring(0, index) else msg
                    if (!GlotAPI.checkSupport(language))
                        return@reply "不支持这种编程语言\n${commandPrefix}jcc list 列出所有支持的编程语言\n" +
                                "如果要执行保存好的pastebin代码，请在指令前添加 $commandPrefix"
                    if (THREAD >= PastebinConfig.thread_limit) {
                        val builder = MessageChainBuilder()
                        if (subject is Group) {
                            builder.add(At(sender))
                            builder.add("\n")
                        }
                        builder.append("当前有 $THREAD 个进程正在执行或等待冷却，请等待几秒后再次尝试")
                        return@reply builder.build()
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
                            return@reply "$CMD_PREFIX $language\n" + GlotAPI.getTemplateFile(language).content
                        }

                        // 如果是引用消息，则不再从原消息中分析。否则，还要从消息中判断是否存在输入参数
                        val si = if (quote != null) 0 else code.indexOfFirst(Char::isWhitespace)
                        // 尝试得到url
                        val url = if (si > 0) {
                            code.substring(0, si)
                        } else {
                            code
                        }
                        // 如果参数是一个ubuntu pastebin的链接，则去获取具体代码
                        if (UbuntuPastebinHelper.checkUrl(url)) {
                            if (si > 0) {
                                // 如果确实是一个链接，则链接后面跟的内容就是输入内容
                                input = code.substring(si+1)
                            }
                            logger.info("从 $url 中获取代码")
                            code = UbuntuPastebinHelper.get(url)
                            if (code.isBlank()) {
                                return@reply "未获取到有效代码"
                            }
                        }

                        logger.info("请求执行代码\n$code")

                        when (val builder = runCode(subject, sender, language, code, input)) {
                            is MessageChainBuilder -> return@reply builder.build()
                            is ForwardMessage-> return@reply builder
                            else -> return@reply "[处理消息失败] 不识别的消息类型"
                        }
                    } catch (e: Exception) {
                        logger.warning("执行失败：${e::class.simpleName}(${e.message})")
                        return@reply "执行失败\n原因：${e.message}"
                    } finally {
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(6000); THREAD--
                        }
                    }
                }
        }
    }

    fun runCode(subject: Contact?, sender: User?, language: String, code: String, input: String?, util: String? = null): Any {
        val result = if (language == "text")
            GlotAPI.RunResult(stdout = code)
        else
            GlotAPI.runCode(language, code, input, util)

        val builder = MessageChainBuilder()
        if (result.message.isNotEmpty()) {
            builder.append("执行失败\n收到来自glot接口的消息：")
            builder.append(result.message)
            return builder
        }
        var c = 0
        if (result.stdout.isNotEmpty()) c++
        if (result.stderr.isNotEmpty()) c++
        if (result.error.isNotEmpty()) c++
        val title = c >= 2
        if (subject is Group) {
            builder.add(At(sender!!))
            builder.add("\n")
        } else {
            val ret = blockSensitiveContent(result.stdout, at = true, isGroup = false)
            if (ret.startsWith("[警告]")) builder.add("$ret\n")
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
                return stringToForwardMessage(sb, subject)
            }
            // 非转发消息截断
            if (sb.length > MSG_MAX_LENGTH) {
                sb.deleteRange(MSG_MAX_LENGTH, sb.length)
                sb.append("\n消息内容过长，已截断")
            }
            builder.append(sb.toString())
        }
        return builder
    }

    private fun startTimer() {
        launch {
            while (true) {
                val delayTime = calculateNextClearDelay()
                logger.info { "已重新加载协程，下次清除剩余时间 ${delayTime / 1000} 秒" }
                delay(delayTime)
                executeClearBlackList()
            }
        }
    }

    override fun onDisable() {
        JccCommand.unregister()
        CommandPastebin.unregister()
        CommandRun.unregister()
    }
}