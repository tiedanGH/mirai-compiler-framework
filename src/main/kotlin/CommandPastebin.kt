import JCompilerCollection.logger
import JCompilerCollection.reload
import JCompilerCollection.save
import UbuntuPastebinHelper.checkUrl
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.warning
import kotlin.math.ceil

/**
 * 保存部分常用的pastebin链接
 */
object CommandPastebin : RawCommand(
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
                    var reply = "·pastebin查看运行帮助：\n" +
                                "-> 查看完整列表\n" +
                                "${commandPrefix}pb list [页码/作者]\n" +
                                "-> 查看具体参数及运行示例\n" +
                                "${commandPrefix}pb info <名称>\n" +
                                "-> 运行pastebin代码\n" +
                                "${commandPrefix}run <名称> [stdin]\n" +
                                "\n" +
                                "·pastebin更新数据帮助：\n" +
                                "-> 添加pastebin数据\n" +
                                "${commandPrefix}pb add <名称> <作者> <语言> <pastebinUrl> [示例输入(stdin)]\n" +
                                "-> 修改数据中某一项的参数\n" +
                                "${commandPrefix}pb set <名称> <参数名> <内容>\n" +
                                "-> 删除一项数据\n" +
                                "${commandPrefix}pb delete <名称>"
                    if (PastebinConfig.admins.contains(userID)) {
                        reply += "\n" +
                                "-> 修改数据中的userID参数\n" +
                                "${commandPrefix}pb set <名称> userID <qq>\n" +
                                "-> 处理添加和修改申请\n" +
                                "${commandPrefix}pb handle <名称> <同意/拒绝> [备注]\n" +
                                "-> 重载本地数据\n" +
                                "${commandPrefix}pb reload"
                    }
                    sendQuoteReply(sender, originalMessage, reply)
                }

                "帮助"-> {   // 查看pastebin帮助（帮助）
                    var reply = "·pastebin查看相关帮助：\n" +
                                "-> 查看完整列表\n" +
                                "${commandPrefix}代码 列表 [页码/作者]\n" +
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
                                "${commandPrefix}代码 删除 <名称>"
                    if (PastebinConfig.admins.contains(userID)) {
                        reply += "\n" +
                                "-> 修改数据中的userID参数\n" +
                                "${commandPrefix}代码 修改 <名称> 创建者ID <QQ号>\n" +
                                "-> 处理添加和修改申请\n" +
                                "${commandPrefix}代码 处理 <名称> <同意/拒绝> [备注]\n" +
                                "-> 重载本地数据\n" +
                                "${commandPrefix}代码 重载"
                    }
                    sendQuoteReply(sender, originalMessage, reply)
                }

                "list", "列表"-> {   // 查看完整列表
                    val pageLimit = ceil(PastebinData.pastebin.size.toDouble() / 20).toInt()
                    val showType = args.getOrElse(1) { "default" }.toString()
                    val page = try {
                        showType.toInt()
                    } catch (ex: Exception) {
                        if (PastebinConfig.enable_ForwardMessage) { 0 } else { 1 }
                    }
                    if (page > pageLimit) {
                        sendQuoteReply(sender, originalMessage, "指定的页码 $page 超过了最大页数 $pageLimit")
                        return
                    }
                    var pastebinList = "·pastebin列表：\n"
                    for ((index, key) in PastebinData.pastebin.keys.withIndex()) {
                        if (page > 0 && index in page * 20 - 20 until page * 20 || page == 0) {
                            pastebinList += "$key   ${PastebinData.pastebin[key]?.get("language")}"
                            if (showType == "author" || showType == "作者" || page != 0) {
                                pastebinList += " ${PastebinData.pastebin[key]?.get("author")}"
                            }
                            if (PastebinData.censorList.contains(key)) {
                                pastebinList += "（审核中）"
                            }
                            pastebinList += "\n"
                        }
                        if (page > 0 && (index == page * 20 - 1 || index == PastebinData.pastebin.size - 1)) {
                            pastebinList += "第 $page 页 / 共 $pageLimit 页"
                            break
                        }
                    }
                    if ((showType == "default" || showType == "author" || showType == "作者") && PastebinConfig.enable_ForwardMessage) {
                        try {
                            val forward: ForwardMessage = buildForwardMessage(sender.subject!!) {
                                displayStrategy = object : ForwardMessage.DisplayStrategy {
                                    override fun generateTitle(forward: RawForwardMessage): String = "pastebin列表"
                                    override fun generateBrief(forward: RawForwardMessage): String = "[pastebin列表]"
                                    override fun generatePreview(forward: RawForwardMessage): List<String> = mutableListOf("${pastebinList.substring(0, 50)}...")
                                    override fun generateSummary(forward: RawForwardMessage): String = "总计 ${PastebinData.pastebin.size} 条代码链接"
                                }
                                sender.subject!!.bot named "列表" says pastebinList
                            }
                            sender.sendMessage(forward)
                        } catch (e: Exception) {
                            logger.warning(e)
                            sendQuoteReply(sender, originalMessage, "[转发消息错误]\n处理列表或发送转发消息时发生错误，请联系机器人所有者查看后台，简要错误信息：${e.message}")
                            return
                        }
                    } else {
                        sendQuoteReply(sender, originalMessage, pastebinList)
                    }
                }

                "info", "信息", "示例"-> {   // 查看数据具体参数
                    val name = args[1].content
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                        return
                    }
                    var info = "名称：$name\n" +
                            "作者：${PastebinData.pastebin[name]?.get("author")}\n" +
                            "语言：${PastebinData.pastebin[name]?.get("language")}\n" +
                            "pastebinUrl：" +
                        if (PastebinConfig.enable_censor) {
                            "审核功能已开启，链接无法查看，如有需求请联系铁蛋\n"
                        } else if (PastebinData.hiddenUrl.contains(name).not()) {
                            "\n${PastebinData.pastebin[name]?.get("pastebinUrl")}\n"
                        } else {
                            "链接被隐藏\n"
                        }
                    info += if (PastebinData.pastebin[name]?.get("stdin")?.isNotEmpty() == true) {
                            "示例输入：${PastebinData.pastebin[name]?.get("stdin")}"
                        } else {
                            "示例输入：无"
                        }
                    sendQuoteReply(sender, originalMessage, info)
                    if (PastebinData.censorList.contains(name)) {
                        sender.sendMessage("此条链接仍在审核中，暂时无法执行")
                    } else {
                        sender.sendMessage("#run $name ${PastebinData.pastebin[name]?.get("stdin")}")
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
                        mutableMapOf(
                            "author" to author,
                            "userID" to userID.toString(),
                            "language" to language,
                            "pastebinUrl" to pastebinUrl,
                            "stdin" to stdin
                        )
                    if (PastebinConfig.enable_censor && PastebinConfig.admins.contains(userID).not()) {
                        PastebinData.censorList.add(name)
                        sendQuoteReply(
                            sender, originalMessage,
                            "您已成功提交审核，此提交并不会发送提醒，管理员会定期查看并审核，您也可以主动联系进行催审"
                        )
                    } else {
                        sendQuoteReply(
                            sender, originalMessage,
                            "添加pastebin成功！\n" +
                                    "名称：$name\n" +
                                    "作者：$author\n" +
                                    "创建者ID：$userID\n" +
                                    "语言：$language\n" +
                                    "pastebinUrl：\n" +
                                if (PastebinConfig.enable_censor) {
                                    "审核功能已开启，链接无法查看\n"
                                } else {
                                    "${pastebinUrl}\n"
                                } +
                                    "示例输入：${stdin}"
                        )
                    }
                    PastebinData.save()
                }

                "set", "修改", "设置"-> {   // 修改数据中某一项的参数
                    val name = args[1].content
                    var option = args[2].content
                    var content = args.drop(3).joinToString(separator = " ")
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                        return
                    }
                    if (userID.toString() != PastebinData.pastebin[name]?.get("userID") && PastebinConfig.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "此条记录并非由您创建，如需修改请联系创建者：${PastebinData.pastebin[name]?.get("userID")}")
                        return
                    }
                    val cnParaList = listOf("名称", "作者", "创建者ID", "语言", "链接", "示例输入", "隐藏链接")
                    val enParaList = listOf("name", "author", "userID", "language", "pastebinUrl", "stdin", "hide")
                    val cnIndex = cnParaList.indexOf(option)
                    if (cnIndex != -1) {
                        option = enParaList[cnIndex]
                    }
                    if (enParaList.contains(option).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的配置项：$option\n仅支持配置：\nname（名称）\nauthor（作者）\nlanguage（语言）\npastebinUrl（链接）\nstdin（示例输入）\nhide（隐藏链接）")
                        return
                    }
                    if (option != "stdin" && args.size > 4) {
                        sendQuoteReply(sender, originalMessage, "修改失败：$option 参数中不能包含空格！")
                        return
                    }
                    if (option != "stdin" && content.isEmpty()) {
                        sendQuoteReply(sender, originalMessage, "修改失败：修改后的值为空！")
                        return
                    }
                    if (option == "name" && PastebinData.pastebin.containsKey(content)) {
                        sendQuoteReply(sender, originalMessage, "修改失败：名称 $content 已存在")
                        return
                    }
                    if (option == "pastebinUrl" && !checkUrl(content)) {
                        sendQuoteReply(sender, originalMessage, "修改失败：无效的链接 $content")
                        return
                    }
                    if (option == "userID" && PastebinConfig.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "修改失败：无权修改创建者ID")
                        return
                    }
                    when (option) {
                        "name" -> {
                            PastebinData.pastebin[content] = PastebinData.pastebin[name]!!
                            PastebinData.pastebin.remove(name)
                            if (PastebinData.hiddenUrl.remove(name)) {
                                PastebinData.hiddenUrl.add(content)
                            }
                        }
                        "hide" -> {
                            when (content) {
                                in arrayListOf("enable","on","true","开启")-> {
                                    content = "隐藏"
                                    PastebinData.hiddenUrl.add(name)
                                }
                                in arrayListOf("disable","off","false","关闭")-> {
                                    content = "显示"
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
                    if (option == "hide") {
                        sendQuoteReply(sender, originalMessage, "成功将 $name 的pastebin链接设置为 $content")
                    } else {
                        if (option == "pastebinUrl" && PastebinConfig.enable_censor) {
                            if (PastebinConfig.admins.contains(userID)) {
                                sendQuoteReply(
                                    sender, originalMessage,
                                    "$name 的pastebinUrl参数的修改已生效"
                                )
                            } else {
                                PastebinData.censorList.add(name)
                                sendQuoteReply(
                                    sender, originalMessage,
                                    "$name 的pastebinUrl参数已修改，已自动提交新审核，在审核期间本条链接暂时无法运行，望理解"
                                )
                            }
                        } else {
                            sendQuoteReply(sender, originalMessage, "成功将 $name 的 $option 参数修改为 $content")
                        }
                    }
                    PastebinData.save()
                }

                "delete", "remove", "移除", "删除"-> {   // 删除pastebin数据
                    val name = args[1].content
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "删除失败：名称 $name 不存在")
                        return
                    }
                    if (userID.toString() != PastebinData.pastebin[name]?.get("userID") && PastebinConfig.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "此条记录并非由您创建，如需删除请联系创建者：${PastebinData.pastebin[name]?.get("userID")}。如果您认为此条记录存在不合适的内容或其他问题，请联系指令管理员")
                        return
                    }
                    PastebinData.hiddenUrl.remove(name)
                    PastebinData.censorList.remove(name)
                    PastebinData.pastebin.remove(name)
                    PastebinData.save()
                    sendQuoteReply(sender, originalMessage, "删除 $name 成功！")
                }

                // admin指令
                "handle", "处理"-> {
                    if (PastebinConfig.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "操作失败：无权使用此指令")
                        return
                    }
                    val name = args[1].content
                    var option = args[2].content
                    val remark = args.getOrElse(3) { "无" }.toString()
                    if (PastebinData.censorList.contains(name).not()) {
                        sendQuoteReply(sender, originalMessage, "操作失败：$name 不在审核列表中")
                        return
                    }
                    if (arrayListOf("accept","同意").contains(option)) {
                        option = "同意"
                        PastebinData.censorList.remove(name)
                    } else if (arrayListOf("refuse","拒绝").contains(option)) {
                        option = "拒绝"
                        PastebinData.censorList.remove(name)
                    } else {
                        sendQuoteReply(sender, originalMessage, "[操作无效] 指令参数错误")
                        return
                    }
                    val reply = "申请处理成功！\n操作：$option\n备注：$remark\n" +
                        try {
                            val noticeApply = "【申请处理通知】\n" +
                                            "申请内容：pastebin运行链接\n" +
                                            "结果：$option\n" +
                                            "备注：$remark"
                            sender.bot?.getFriendOrFail(PastebinData.pastebin[name]!!["userID"]!!.toLong())!!.sendMessage(noticeApply)   // 抄送结果至申请人
                            "已将结果发送至申请人"
                        } catch (ex: Exception) {
                            logger.warning(ex)
                            "发送消息至申请人时出现错误，可能因为机器人权限不足或未找到对象，详细信息请查看后台"
                        }
                    if (option == "拒绝") {
                        PastebinData.pastebin.remove(name)
                    }
                    sendQuoteReply(sender, originalMessage, reply)   // 回复指令发出者
                }

                "reload", "重载"-> {
                    if (PastebinConfig.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "操作失败：无权使用此指令")
                        return
                    }
                    try {
                        PastebinConfig.reload()
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

    suspend fun sendQuoteReply(sender: CommandSender, originalMessage: MessageChain, msgToSend: String) {
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
