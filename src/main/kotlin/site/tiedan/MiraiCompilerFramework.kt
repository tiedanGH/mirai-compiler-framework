package site.tiedan

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.DEFAULT_FORMAT_NAME
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.info
import site.tiedan.command.*
import site.tiedan.config.*
import site.tiedan.data.*
import site.tiedan.module.*
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

object MiraiCompilerFramework : KotlinPlugin(
    JvmPluginDescription(
        id = "site.tiedan.mirai-compiler-framework",
        name = "Mirai Compiler Framework",
        version = "1.2.0",
    ) {
        author("tiedan")
        info("""基于Glot接口的在线编译器框架""")
    }
) {
    const val CMD_PREFIX = "run"
    const val MSG_TRANSFER_LENGTH = 550
    const val MSG_MAX_LENGTH = 800
    const val TIMEOUT = 60L
    val supportedFormats = listOf(
        "text",
        "markdown",
        "base64",
        "image",
        "LaTeX",
        "json",     // MessageChain, MultipleMessage
        "ForwardMessage",
        "Audio",
    )
    val enableStorageFormats = listOf(
        "json",     // MessageChain, MultipleMessage
        "ForwardMessage",
        "Audio",
    )
    @OptIn(ConsoleExperimentalApi::class)
    val cacheFolder = "./data/$dataHolderName/cache/"
    @OptIn(ConsoleExperimentalApi::class)
    val imageFolder = "./data/$dataHolderName/images/"

    data class ThreadInfo(
        val id: String,
        val name: String,
        val sender: String,
        val from: String,
        val startTime: Long = System.currentTimeMillis(),
    )
    val THREADS = ConcurrentLinkedQueue<ThreadInfo>()

    data class Command(val usage: String, val usageCN: String, val desc: String, val type: Int)

    override fun onEnable() {
        CommandGlot.register()
        CommandPastebin.register()
        CommandBucket.register()
        CommandRun.register()

        PastebinConfig.reload()
        MailConfig.reload()
        SystemConfig.reload()
        DockerConfig.reload()
        GlotCache.reload()
        PastebinData.reload()
        ExtraData.reload()
        PastebinStorage.reload()
        PastebinBucket.reload()
        CodeCache.reload()

        startTimer()

        if (PastebinConfig.API_TOKEN.isEmpty())
            logger.error("Glot API token为空，请先在PastebinConfig中配置才能使用本框架，访问 https://glot.io/account/token 获取token")
        if (PastebinConfig.Hastebin_TOKEN.isEmpty())
            logger.warning("Hastebin token为空，将无法获取Hastebin上的代码，如需注册请访问 https://www.toptal.com/developers/hastebin/documentation")

        GlobalEventChannel.registerListenerHost(Events)

        logger.info { "Mirai Compiler Framework loaded" }
    }

    override fun onDisable() {
        CommandGlot.unregister()
        CommandPastebin.unregister()
        CommandBucket.unregister()
        CommandRun.unregister()
    }

    val pendingCommand = mutableMapOf<Long, String>()
    suspend fun CommandSender.requestUserConfirmation(userID: Long, command: String, alert: String): Boolean? {
        if (!pendingCommand.contains(userID)) {
            pendingCommand.put(userID, command)
            sendQuoteReply(alert)
            return null
        }
        pendingCommand.remove(userID)
        return true
    }

    private fun startTimer() {
        launch {
            while (true) {
                val delayTime = calculateNextClearDelay()
                logger.info { "已重新加载协程，下次定时剩余时间 ${delayTime / 1000} 秒" }
                delay(delayTime)
                executeClearBlackList()
                Statistics.dailyDecayScore()
            }
        }
    }

    /**
     * 发送引用消息
     */
    suspend fun CommandSender.sendQuoteReply(msgToSend: String) {
        if (this is CommandSenderOnMessage<*>) {
            sendMessage(buildMessageChain {
                +QuoteReply(fromEvent.message)
                +PlainText(msgToSend)
            })
        } else {
            sendMessage(msgToSend)
        }
    }

    /**
     * 上传文件至在线图片
     */
    suspend fun Contact.uploadFileToImage(file: File): Image? {
        return file.toExternalResource().use { resource ->     // 返回结果图片
            if (resource.formatName == DEFAULT_FORMAT_NAME) {
                return null
            }
            this.uploadImage(resource)
        }
    }

    /**
     * 按最大长度截断字符串
     */
    fun trimToMaxLength(input: String, maxLength: Int = 30000): Pair<String, Boolean> {
        var currentCount = 0
        val sb = StringBuilder()
        for (ch in input) {
            val len = ch.chineseLength
            if (currentCount + len > maxLength) {
                return sb.toString() to true
            }
            sb.append(ch)
            currentCount += len
        }
        return sb.toString() to false
    }

    /**
     * 中文字符长度
     */
    private val Char.chineseLength: Int
        get() {
            return when (this) {
                in '\u0000'..'\u007F' -> 1
                in '\u0080'..'\u07FF' -> 2
                in '\u0800'..'\uFFFF' -> 3
                else -> 4
            }
        }

    /**
     * 获取用户昵称
     */
    fun getNickname(sender: CommandSender, qq: Long): String {
        val subject = sender.subject
        var nickname: String? = null
        if (subject is Group) {
            nickname = subject.getMember(qq)?.nameCardOrNick
        }
        if (nickname == null) {
            nickname = sender.bot?.getFriend(qq)?.nameCardOrNick
        }
        return nickname ?: "[未知]"
    }
}