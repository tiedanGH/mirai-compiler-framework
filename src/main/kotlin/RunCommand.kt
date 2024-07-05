import JCompilerCollection.logger
import JCompilerCollection.runCode
import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.console.command.isConsole
import net.mamoe.mirai.message.data.*

object RunCommand : RawCommand(
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

        try {
            val name = try {
                args[0].content
            } catch (ex: Exception) {
                sendQuoteReply(sender, originalMessage, "[指令无效]\n${commandPrefix}run <名称> [输入]\n运行保存的pastebin代码")
                return
            }
            if (PastebinData.pastebin.containsKey(name).not()) {
                sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pastebin list」来查看完整列表")
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
            logger.info("请求执行 pastebinData: $name 中的代码，input: $input")
            val builder = runCode(sender.subject, sender.user, language, code, input)
            sender.sendMessage(builder.build())
        } catch (e: Exception) {
            logger.warning(e)
            sender.sendMessage("执行失败\n原因：${e.message}")
            return
        }
    }
}
private suspend fun sendQuoteReply(sender: CommandSender, originalMessage: MessageChain, msgToSend: String) {
    if (sender.isConsole()) {
        sender.sendMessage(msgToSend)
    } else {
        sender.sendMessage(buildMessageChain {
            +QuoteReply(originalMessage)
            +PlainText(msgToSend)
        })
    } 
}
