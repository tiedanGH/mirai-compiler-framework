package site.tiedan.command

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.isBotEnabled
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.data.PastebinData
import site.tiedan.utils.FuzzySearch
import site.tiedan.core.PastebinCodeExecutor.executeMainProcess

/**
 * # 运行代码项目
 *
 * @author tiedanGH
 */
object CommandRun : RawCommand(
    owner = MiraiCompilerFramework,
    primaryName = "run",
    secondaryNames = arrayOf("运行"),
    description = "运行代码项目",
    usage = "${commandPrefix}run <名称> [输入]"
){
    val Image_Path = "file:///${MiraiCompilerFramework.dataFolderPath.toString().replace("\\", "/")}/images/"

    suspend fun MessageChain.queryImageUrls(): MutableList<String> =
        filterIsInstance<Image>().map { it.queryUrl() }.toMutableList()

    /** 输入与前面参数之间的分隔符 */
    private val SEPARATOR = Regex("\\s+")

    /**
     * 取第 [skip] 个参数之后的全部内容作为输入，保留其中的换行与连续空白
     * - 消息事件中从原始消息切分（原始消息比参数多出开头的指令名）
     */
    fun CommandSender.inputAfter(args: MessageChain, skip: Int): String {
        val joined = { args.drop(skip).joinToString(" ") { it.content } }
        val raw = (this as? CommandSenderOnMessage<*>)?.fromEvent?.message?.content?.trim()
            ?: return joined()
        return SEPARATOR.split(raw, limit = skip + 2).getOrNull(skip + 1) ?: joined()
    }

    /**
     * 从保存的pastebin链接中直接运行
     */
    override suspend fun CommandSender.onCommand(args: MessageChain) {

        if (!isBotEnabled(bot?.id)) return

        val name = try {
            PastebinData.alias[args[0].content] ?: args[0].content
        } catch (_: Exception) {
            sendQuoteReply("[指令无效]\n${commandPrefix}run <名称> [输入]\n运行保存的代码项目")
            return
        }
        if (PastebinData.pastebin.contains(name).not()) {
            val fuzzy = FuzzySearch.fuzzyFind(PastebinData.pastebin, name)
            sendQuoteReply(
                "未知的名称：$name\n" +
                if (fuzzy.isNotEmpty()) {
                    "🔍 模糊匹配结果->\n" + fuzzy.take(20).joinToString(separator = " ") +
                    "\n或使用「${commandPrefix}pb list」来查看完整列表"
                } else "请使用「${commandPrefix}pb list」来查看完整列表"
            )
            return
        }

        val userInput = inputAfter(args, 1)
        val imageUrls = args.drop(1).toMessageChain().queryImageUrls()
        if (this is CommandSenderOnMessage<*> && fromEvent.message[QuoteReply.Key] != null) {
            fromEvent.message.findIsInstance<QuoteReply>()
                ?.source?.originalMessage?.queryImageUrls()
                ?.let { imageUrls.addAll(0, it) }
        }

        // 执行代码并输出
        this.executeMainProcess(name, userInput, imageUrls)
    }
}
