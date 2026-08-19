package site.tiedan.command

import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.Command
import site.tiedan.MiraiCompilerFramework.CMD_PREFIX
import site.tiedan.MiraiCompilerFramework.isBotEnabled
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.content
import site.tiedan.core.GlotAPI

/**
 * # 查看框架信息和帮助
 *
 * @author tiedanGH
 */
object CommandGlot : RawCommand(
    owner = MiraiCompilerFramework,
    primaryName = "glot",
    secondaryNames = arrayOf("Glot", "jcc"),
    description = "查看框架信息和帮助",
    usage = "${commandPrefix}glot help"
) {
    private val commandList = arrayOf(
        Command("glot help", "glot 帮助", "查看框架信息和帮助", 1),
        Command("glot list", "glot 列表", "列出所有支持的编程语言", 1),
        Command("glot template [语言]", "glot 模版 [语言]", "获取指定语言的模板", 1),
        Command("pb help", "代码 帮助", "pb代码项目操作指令", 1),
        Command("bucket help", "存储库 帮助", "跨项目存储库操作指令", 1),
        Command("image help", "图片 帮助", "本地图片操作指令", 1),
        Command("run <名称> [输入]", "运行 <名称> [输入]", "运行代码项目", 1),
    )

    override suspend fun CommandSender.onCommand(args: MessageChain) {
        if (!isBotEnabled(bot?.id)) return
        try {
            when (args[0].content) {

                "help"-> {   // 查看glot帮助（help）
                    sendQuoteReply(
                        " ·🚀 在线运行代码指令:\n" +
                        "$CMD_PREFIX <language> <code>\n" +
                        "$CMD_PREFIX <language> <源代码URL> [stdin]\n" +
                        "[引用消息] $CMD_PREFIX <language> [stdin]\n" +
                        "📦 仓库地址：\n" +
                        "https://github.com/tiedanGH/mirai-compiler-framework/\n" +
                        "📚 完整指令帮助：\n" +
                        commandList.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                    )
                }

                "帮助"-> {   // 查看glot帮助（帮助）
                    sendQuoteReply(
                        " ·🚀 在线运行代码指令:\n" +
                        "$CMD_PREFIX <语言> <代码>\n" +
                        "$CMD_PREFIX <语言> <源代码URL> [输入]\n" +
                        "[引用消息] $CMD_PREFIX <语言> [输入]\n" +
                        "📦 仓库地址：\n" +
                        "https://github.com/tiedanGH/mirai-compiler-framework/" +
                        "📚 完整指令帮助：\n" +
                        commandList.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" }
                    )
                }

                "list", "列表" -> {   // 列出所有支持的编程语言
                    try {
                        sendQuoteReply(
                            " ·所有支持的编程语言：\n" +
                            GlotAPI.listLanguages().joinToString { it.name }
                        )
                    } catch (e: Exception) {
                        logger.warning(e)
                        sendQuoteReply("执行失败\n${e.message}")
                    }
                }

                "template", "模版" -> {   // 获取指定语言的模板
                    val language = args[1].content
                    if (!GlotAPI.checkSupport(language)) {
                        sendQuoteReply("不支持该语言，请使用「${commandPrefix}glot list」列出所有支持的编程语言")
                        return
                    }
                    sendMessage("$CMD_PREFIX $language\n" + GlotAPI.getTemplateFile(language).content)
                }

                else -> {
                    sendQuoteReply("[参数不匹配]\n请使用「${commandPrefix}glot help」来查看指令帮助")
                }
            }
        } catch (_: IndexOutOfBoundsException) {
            sendQuoteReply("[参数不足]\n请使用「${commandPrefix}glot help」来查看指令帮助")
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply("[指令执行未知错误]\n请联系管理员查看后台：${e::class.simpleName}(${e.message})")
        }
    }
}