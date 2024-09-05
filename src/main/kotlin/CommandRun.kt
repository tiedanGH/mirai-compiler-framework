import JCompilerCollection.THREAD
import JCompilerCollection.logger
import JCompilerCollection.runCode
import CommandPastebin.sendQuoteReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.content

object CommandRun : RawCommand(
    owner = JCompilerCollection,
    primaryName = "run",
    secondaryNames = arrayOf("运行"),
    description = "运行pastebin中的代码",
    usage = "${commandPrefix}run <名称> [输入]"
){
    /**
     * 从保存的pastebin链接中直接运行
     */
    override suspend fun CommandContext.onCommand(args: MessageChain) {

        if (THREAD >= 3) {
            sendQuoteReply(sender, originalMessage, "当前有 $THREAD 个进程正在执行或等待冷却，请等待几秒后再次尝试")
            return
        }
        try {
            val name = try {
                args[0].content
            } catch (ex: Exception) {
                sendQuoteReply(sender, originalMessage, "[指令无效]\n${commandPrefix}run <名称> [输入]\n运行保存的pastebin代码")
                return
            }
            if (PastebinData.pastebin.containsKey(name).not()) {
                sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                return
            }
            if (PastebinData.censorList.contains(name)) {
                sendQuoteReply(sender, originalMessage, "此条链接仍在审核中，暂时无法执行。管理员会定期对链接进行审核，您也可以主动联系进行催审")
                return
            }
            val language = PastebinData.pastebin[name]?.get("language").toString()
            val url = PastebinData.pastebin[name]?.get("pastebinUrl").toString()
            val input = args.drop(1).joinToString(separator = " ")

            logger.info("从 $url 中获取代码")
            val code: String = UbuntuPastebinHelper.get(url)
            if (code.isBlank()) {
                sender.sendMessage("未获取到有效代码")
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                THREAD++; delay(2000); THREAD--
            }
            logger.info("请求执行 pastebinData: $name 中的代码，input: $input")
            val builder = runCode(sender.subject, sender.user, language, code, input)
            CoroutineScope(Dispatchers.IO).launch {
                THREAD++; delay(5000); THREAD--
            }
            when (builder) {
                is MessageChainBuilder -> sender.sendMessage(builder.build())
                is ForwardMessage -> sender.sendMessage(builder)
                else -> sendQuoteReply(sender, originalMessage, "[处理消息失败] 不识别的消息类型")
            }
        } catch (e: Exception) {
            logger.warning(e)
            sender.sendMessage("执行失败\n原因：${e.message}")
        }
    }
}
