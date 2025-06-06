package commands

import MiraiCompilerFramework
import MiraiCompilerFramework.Command
import MiraiCompilerFramework.CMD_PREFIX
import MiraiCompilerFramework.logger
import MiraiCompilerFramework.sendQuoteReply
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.content
import utils.GlotAPI

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
        Command("pb help", "代码 帮助", "查看和添加pastebin代码", 1),
        Command("run <名称> [输入]", "运行 <名称> [输入]", "运行pastebin中的代码", 1),
    )

    override suspend fun CommandSender.onCommand(args: MessageChain) {
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
                    val file = GlotAPI.getTemplateFile(language)
                    sendMessage("$CMD_PREFIX $language\n" + file.content)
                }

                else -> {
                    sendQuoteReply("[参数不匹配]\n请使用「${commandPrefix}glot help」来查看指令帮助")
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            sendQuoteReply("[参数不足]\n请使用「${commandPrefix}glot help」来查看指令帮助")
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply("[指令执行未知错误]\n请联系管理员查看后台：${e::class.simpleName}(${e.message})")
        }
    }
}