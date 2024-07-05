import JCompilerCollection.logger
import JCompilerCollection.reload
import JCompilerCollection.save
import UbuntuPastebinHelper.checkUrl
import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.console.command.isConsole
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.warning

/**
 * 保存部分常用的pastebin链接
 */
object PastebinCommand : RawCommand(
    owner = JCompilerCollection,
    primaryName = "pastebin",
    secondaryNames = arrayOf("pb", "代码"),
    description = "查看和添加pastebin代码",
    usage = "${commandPrefix}pb help"
){
    override suspend fun CommandContext.onCommand(args: MessageChain) {

        val userID = sender.user?.id ?: 10000

        try {
            when (args[0].content) {

                "help"-> {   // 查看pastebin帮助（help）
                    val reply = "·pastebin查看相关帮助：\n" +
                                "-> 查看完整列表\n" +
                                "${commandPrefix}pastebin list [author]\n" +
                                "-> 查看具体参数及运行示例\n" +
                                "${commandPrefix}pastebin info <名称>\n" +
                                "-> 运行pastebin代码\n" +
                                "${commandPrefix}run <名称> [stdin]\n" +
                                "\n" +
                                "·pastebin更新数据帮助：\n" +
                                "-> 添加pastebin数据\n" +
                                "${commandPrefix}pastebin add <名称> <作者> <语言> <pastebinUrl> [示例输入(stdin)]\n" +
                                "-> 修改数据中某一项的参数\n" +
                                "${commandPrefix}pastebin set <名称> <参数名> <内容>\n" +
                                "-> 删除一项数据\n" +
                                "${commandPrefix}pastebin delete <名称>\n" +
                                "(指令可以简写成「${commandPrefix}pb」)"
                    sendQuoteReply(sender, originalMessage, reply)
                }

                "帮助"-> {   // 查看pastebin帮助（帮助）
                    val reply = "·pastebin查看相关帮助：\n" +
                            "-> 查看完整列表\n" +
                            "${commandPrefix}代码 列表 [作者]\n" +
                            "-> 查看具体参数及运行示例\n" +
                            "${commandPrefix}代码 信息 <名称>\n" +
                            "-> 运行pastebin代码\n" +
                            "${commandPrefix}代码 运行 <名称> [输入]\n" +
                            "\n" +
                            "·pastebin更新数据帮助：\n" +
                            "-> 添加pastebin数据\n" +
                            "${commandPrefix}代码 添加 <名称> <作者> <语言> <pastebinUrl> [示例输入(stdin)]\n" +
                            "-> 修改数据中某一项的参数\n" +
                            "${commandPrefix}代码 修改 <名称> <参数名> <内容>\n" +
                            "-> 删除一项数据\n" +
                            "${commandPrefix}代码 删除 <名称>\n" +
                            "(指令可以简写成「${commandPrefix}pb」)"
                    sendQuoteReply(sender, originalMessage, reply)
                }

                "list", "列表"-> {   // 查看完整列表
                    val showAuthor = try {
                        args[1].content == "author" || args[1].content == "作者"
                    } catch (ex: Exception) {
                        false
                    }
                    var pastebinList = "·pastebin列表：\n"
                    for (key in PastebinData.pastebin.keys) {
                        pastebinList += "$key   ${PastebinData.pastebin[key]?.get("language")}"
                        if (showAuthor) {
                            pastebinList += " ${PastebinData.pastebin[key]?.get("author")}"
                        }
                        pastebinList += "\n"
                    }
                    sendQuoteReply(sender, originalMessage, pastebinList)
                }

                "info", "信息", "示例"-> {   // 查看数据具体参数
                    val name = args[1].content
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                        return
                    }
                    val info = "名称：$name\n" +
                            "作者：${PastebinData.pastebin[name]?.get("author")}\n" +
                            "语言：${PastebinData.pastebin[name]?.get("language")}\n" +
                            "pastebinUrl：" +
                        if (PastebinData.hiddenUrl.contains(name).not()) {
                            "\n${PastebinData.pastebin[name]?.get("pastebinUrl")}\n"
                        } else {
                            "链接被隐藏\n"
                        } +
                        if (PastebinData.pastebin[name]?.get("stdin")?.isNotEmpty() == true) {
                            "示例输入：${PastebinData.pastebin[name]?.get("stdin")}"
                        } else {
                            "示例输入：无"
                        }
                    var runMsg = "run ${PastebinData.pastebin[name]?.get("language")} " +
                                 "${PastebinData.pastebin[name]?.get("pastebinUrl")}"
                    if (PastebinData.pastebin[name]?.get("stdin")?.isNotEmpty() == true) {
                        runMsg += "\n${PastebinData.pastebin[name]?.get("stdin")}"
                    }
                    sendQuoteReply(sender, originalMessage, info)
                    if (PastebinData.hiddenUrl.contains(name).not()) {
                        sender.sendMessage(runMsg)
                    }
                }

                "add", "添加", "新增"-> {   // 添加pastebin数据
                    val name = args[1].content
                    if (PastebinData.pastebin.containsKey(name)) {
                        sendQuoteReply(sender, originalMessage, "添加失败：名称 $name 已存在")
                        return
                    }
                    val author = args[2].content
                    val language = args[3].content
                    val pastebinUrl = args[4].content
                    val stdin = args.drop(5).joinToString(separator = " ")
                    if (!checkUrl(pastebinUrl)) {
                        sendQuoteReply(sender, originalMessage, "添加失败：无效的链接 $pastebinUrl")
                        return
                    }
                    PastebinData.pastebin[name] =
                        mutableMapOf("author" to author, "userID" to userID.toString(), "language" to language, "pastebinUrl" to pastebinUrl, "stdin" to stdin)
                    PastebinData.save()
                    sendQuoteReply(sender, originalMessage,
                        "添加pastebin成功！\n" +
                                "名称：$name\n" +
                                "作者：$author\n" +
                                "创建者ID：$userID\n" +
                                "语言：$language\n" +
                                "pastebinUrl：\n" +
                                "${pastebinUrl}\n" +
                                "示例输入：${stdin}"
                    )
                }

                "set", "修改", "设置"-> {   // 修改数据中某一项的参数
                    val name = args[1].content
                    val option = args[2].content
                    val content = args.drop(3).joinToString(separator = " ")
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                        return
                    }
                    if (userID.toString() != PastebinData.pastebin[name]?.get("userID") && PastebinData.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "此条记录并非由您创建，如需修改请联系创建者：${PastebinData.pastebin[name]?.get("userID")}")
                        return
                    }
                    if (listOf("name", "author", "userID", "language", "pastebinUrl", "stdin", "hide").contains(option).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的配置项：$option\n仅支持配置：\nname（名称）\nauthor（作者）\nlanguage（语言）\npastebinUrl（链接）\nstdin（示例输入）\nhide（是否隐藏链接）")
                        return
                    }
                    if (option != "stdin" && args.size > 4) {
                        sendQuoteReply(sender, originalMessage, "无法修改：因为参数中不能包含空格！")
                        return
                    }
                    if (option != "stdin" && content.isEmpty()) {
                        sendQuoteReply(sender, originalMessage, "无法修改：因为输入的值为空！")
                        return
                    }
                    if (option == "pastebinUrl" && !checkUrl(content)) {
                        sendQuoteReply(sender, originalMessage, "无法修改：无效的链接 $content")
                        return
                    }
                    if (option == "userID" && PastebinData.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "无法修改：无权修改创建者ID")
                        return
                    }
                    when (option) {
                        "name" -> {
                            PastebinData.pastebin[content] = PastebinData.pastebin[name]!!
                            PastebinData.pastebin.remove(name)
                        }
                        "hide" -> {
                            when (content) {
                                in arrayListOf("enable","on","true","开启")-> {
                                    PastebinData.hiddenUrl.add(name)
                                }
                                in arrayListOf("disable","off","false","关闭")-> {
                                    PastebinData.hiddenUrl.remove(name)
                                }
                                else-> {
                                    sendQuoteReply(sender, originalMessage, "无效的配置项：请设置 开启/关闭 隐藏链接功能")
                                    return
                                }
                            }
                        }
                        else -> {
                            PastebinData.pastebin[name]?.set(option, content)
                        }
                    }
                    sendQuoteReply(sender, originalMessage, "成功将 $name 的 $option 参数修改为 $content")
                    PastebinData.save()
                }

                "delete", "remove", "移除", "删除"-> {   // 删除pastebin数据
                    val name = args[1].content
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "删除失败：名称 $name 不存在")
                        return
                    }
                    if (userID.toString() != PastebinData.pastebin[name]?.get("userID") && PastebinData.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "此条记录并非由您创建，如需删除请联系创建者：${PastebinData.pastebin[name]?.get("userID")}")
                        return
                    }
                    PastebinData.hiddenUrl.remove(name)
                    PastebinData.pastebin.remove(name)
                    PastebinData.save()
                    sendQuoteReply(sender, originalMessage, "删除 $name 成功！")
                }

                "reload", "重载"-> {
                    try {
                        PastebinData.reload()
                        sendQuoteReply(sender, originalMessage, "数据重载成功")
                    } catch (ex: Exception) {
                        logger.warning(ex)
                        sendQuoteReply(sender, originalMessage, "出现错误：${ex.message}")
                    }
                }

                else-> {
                    sendQuoteReply(sender, originalMessage, "[参数不匹配]\n请使用「${commandPrefix}pb help」来查看指令帮助")
                }
            }
        } catch (ex: Exception) {
            logger.warning {"error: ${ex.message}"}
            sendQuoteReply(sender, originalMessage, "[参数不足]\n请使用「${commandPrefix}pb help」来查看指令帮助")
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
}
