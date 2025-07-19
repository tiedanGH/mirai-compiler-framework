package site.tiedan

import site.tiedan.config.MailConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.config.SystemConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.ExternalResource.Companion.DEFAULT_FORMAT_NAME
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.info
import site.tiedan.format.ForwardMessageGenerator.stringToForwardMessage
import site.tiedan.format.JsonProcessor.blockProhibitedContent
import site.tiedan.utils.PastebinUrlHelper
import site.tiedan.module.GlotAPI
import site.tiedan.module.Statistics
import site.tiedan.module.calculateNextClearDelay
import site.tiedan.module.executeClearBlackList
import site.tiedan.command.CommandGlot
import site.tiedan.command.CommandPastebin
import site.tiedan.command.CommandRun
import site.tiedan.config.DockerConfig
import site.tiedan.data.*
import site.tiedan.module.Events
import java.io.File

object MiraiCompilerFramework : KotlinPlugin(
    JvmPluginDescription(
        id = "site.tiedan.mirai-compiler-framework",
        name = "Mirai Compiler Framework",
        version = "1.1.0",
    ) {
        author("tiedan")
        info("""基于Glot接口的在线编译器框架""")
    }
) {
    const val CMD_PREFIX = "run"
    const val MSG_TRANSFER_LENGTH = 550
    const val MSG_MAX_LENGTH = 800
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

    var THREAD = 0

    data class Command(val usage: String, val usageCN: String, val desc: String, val type: Int)

    override fun onEnable() {
        CommandGlot.register()
        CommandPastebin.register()
        CommandRun.register()

        PastebinConfig.reload()
        MailConfig.reload()
        SystemConfig.reload()
        DockerConfig.reload()
        GlotCache.reload()
        PastebinData.reload()
        ExtraData.reload()
        PastebinStorage.reload()
        CodeCache.reload()

        startTimer()

        if (PastebinConfig.API_TOKEN.isEmpty())
            logger.error("Glot API token为空，请先在PastebinConfig中配置才能使用本框架，访问 https://glot.io/account/token 获取token")
        if (PastebinConfig.Hastebin_TOKEN.isEmpty())
            logger.warning("Hastebin token为空，将无法获取Hastebin上的代码，如需注册请访问 https://www.toptal.com/developers/hastebin/documentation")

        GlobalEventChannel.registerListenerHost(Events)

        logger.info { "Mirai Compiler Framework loaded" }
    }

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

    suspend fun Contact.uploadFileToImage(file: File): Image? {
        return file.toExternalResource().use { resource ->     // 返回结果图片
            if (resource.formatName == DEFAULT_FORMAT_NAME) {
                return null
            }
            this.uploadImage(resource)
        }
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

    override fun onDisable() {
        CommandGlot.unregister()
        CommandPastebin.unregister()
        CommandRun.unregister()
    }
}