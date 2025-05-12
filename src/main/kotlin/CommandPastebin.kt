import JCompilerCollection.logger
import JCompilerCollection.reload
import JCompilerCollection.save
import config.MailConfig
import config.PastebinConfig
import data.CodeCache
import data.ExtraData
import data.PastebinData
import data.PastebinStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.console.command.isConsole
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.MessageTooLargeException
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.containsFriend
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import utils.DownloadHelper.downloadImage
import utils.GlotAPI
import utils.MarkdownImageProcessor.folder
import utils.Statistics
import utils.UbuntuPastebinHelper.checkUrl
import utils.buildMailContent
import utils.buildMailSession
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.io.path.inputStream
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
    @OptIn(ConsoleExperimentalApi::class)
    override suspend fun CommandContext.onCommand(args: MessageChain) {

        val userID = sender.user?.id ?: 10000

        try {
            when (args[0].content) {

                "help"-> {   // 查看pastebin帮助（help）
                    var reply = "·pastebin查看运行帮助：\n" +
                                "${commandPrefix}pb profile [QQ]　查看个人信息\n" +
                                "${commandPrefix}pb private　允许私信主动消息\n" +
                                "${commandPrefix}pb stats [名称]　查看统计\n" +
                                "${commandPrefix}pb list [页码/作者]　查看完整列表\n" +
                                "${commandPrefix}pb info <名称>　查看信息&运行示例\n" +
                                "${commandPrefix}run <名称> [stdin]　运行代码\n" +
                                "\n" +
                                "·pastebin更新数据帮助：\n" +
                                "${commandPrefix}pb add <名称> <作者> <语言> <pastebinUrl> [示例输入(stdin)]　　添加pastebin数据\n" +
                                "${commandPrefix}pb set <名称> <参数名> <内容>　　修改程序属性\n" +
                                "${commandPrefix}pb delete <名称>　　删除一条数据\n" +
                                "·pastebin高级功能帮助：\n" +
                                "${commandPrefix}pb set <名称> format <输出格式> [宽度/存储]　　修改输出格式\n" +
                                "${commandPrefix}pb upload <图片名称(需要包含拓展名)> <【图片/URL】>　　上传图片至缓存\n" +
                                "${commandPrefix}pb storage <名称> [查询ID]　　查询存储数据"
                    if (args.getOrNull(1)?.content == "all" && PastebinConfig.admins.contains(userID)) {
                        reply += "\n\n" +
                                "·pastebin管理指令帮助：\n" +
                                "${commandPrefix}pb handle <名称> <同意/拒绝> [备注]　处理添加和修改申请\n" +
                                "${commandPrefix}pb black [qq]　黑名单处理\n" +
                                "${commandPrefix}pb reload　重载本地数据"
                    }
                    sendQuoteReply(sender, originalMessage, reply)
                }

                "帮助"-> {   // 查看pastebin帮助（帮助）
                    var reply = "·pastebin查看相关帮助：\n" +
                                "${commandPrefix}代码 简介 [QQ]　查看个人信息\n" +
                                "${commandPrefix}代码 私信时段　允许私信主动消息\n" +
                                "${commandPrefix}代码 统计 [名称]　查询数据统计\n" +
                                "${commandPrefix}代码 列表 [页码/作者]　查看完整列表\n" +
                                "${commandPrefix}代码 信息 <名称>　查看信息及运行示例\n" +
                                "${commandPrefix}代码 运行 <名称> [输入]　运行代码\n" +
                                "\n" +
                                "·pastebin更新数据帮助：\n" +
                                "${commandPrefix}代码 添加 <名称> <作者> <语言> <pastebinUrl> [示例输入(stdin)]　　添加pastebin数据\n" +
                                "${commandPrefix}代码 修改 <名称> <参数名> <内容>　　修改程序属性\n" +
                                "${commandPrefix}代码 删除 <名称>　　删除一条数据\n" +
                                "·pastebin高级功能帮助：\n" +
                                "${commandPrefix}代码 修改 <名称> 输出格式 <输出格式> [宽度/存储]　　修改输出格式\n" +
                                "${commandPrefix}代码 上传 <图片名称(需要包含拓展名)> <【图片/URL】>　　上传图片至缓存\n" +
                                "${commandPrefix}代码 存储 <名称> [查询ID]　　查询存储数据"
                    if (args.getOrNull(1)?.content == "all" && PastebinConfig.admins.contains(userID)) {
                        reply += "\n\n" +
                                "·pastebin管理指令帮助：\n" +
                                "${commandPrefix}代码 处理 <名称> <同意/拒绝> [备注]　处理添加和修改申请\n" +
                                "${commandPrefix}代码 黑名单 [QQ号]　黑名单处理\n" +
                                "${commandPrefix}代码 重载　重载本地数据"
                    }
                    sendQuoteReply(sender, originalMessage, reply)
                }

                "profile", "简介"-> {   // 查看个人信息
                    val id = args.getOrNull(1)?.content?.replace("@", "")?.toLongOrNull() ?: userID
                    val time = ExtraData.private_allowTime[id]
                    val reply = buildString {
                        append("　【个人信息")
                        if (id != userID) append(" - $id")
                        appendLine("】　")
                        append("接收主动私信：")
                        if (sender.bot?.containsFriend(userID) != true) {
                            appendLine("未添加好友")
                        } else if (time == null) {
                            appendLine("不允许")
                        } else if (time.second - time.first == 23) {
                            appendLine("始终允许")
                        } else {
                            appendLine("${time.first}:00 ~ ${time.second}:59")
                        }
                        append(summarizeStatistics(id))
                    }
                    sendQuoteReply(sender, originalMessage, reply)
                }

                "private", "私信时段"-> {
                    if (sender.bot?.containsFriend(userID) != true) {
                        sendQuoteReply(sender, originalMessage, "请先添加bot为好友才能使用此功能")
                        return
                    }
                    val help = """
                        |具体使用帮助详见下方：
                        |-> 关闭私信主动消息
                        |${commandPrefix}pb private off/disable/关闭/取消
                        |-> 配置可用时间段（24小时制）
                        |${commandPrefix}pb private <起始> <结束>
                        |*例如：5 6 代表 5:00am ~ 6:59am
                        |*始终允许请填写：0 23
                    """.trimMargin()
                    val notice = """
                        |【关于私信主动消息功能】
                        |请务必注意：在您启用此功能并配置可用时间段后，Bot 在该时段内将有权限向您发送私信消息。
                        |配置可用时间段即代表您已知晓：收到消息的内容和频率均为他人自定义，即可能存在不适宜的内容和频率。Bot 所有者不对因私信功能引发的任何纠纷或损失承担责任。
                        |
                        |·使用前请再次确认：
                        |  1. 您已充分了解并同意可能收到的消息内容与频率。
                        |  2. 您已设置合适的可用时间段，避免在不便时段中受到打扰。
                        |  3. 您可随时修改可用时间段，或关闭此功能权限防止不必要的麻烦。
                        |
                        |**请先完整阅读以上内容**
                        |
                        |$help
                        |
                        |·如有疑问或需要帮助，请联系铁蛋(2295824927)。
                    """.trimMargin()
                    val option = args.getOrNull(1)?.content
                    if (option == null) {
                        sendQuoteReply(sender, originalMessage, notice)
                        return
                    }
                    if (arrayListOf("off","disable","关闭","取消").contains(option)) {
                        if (!ExtraData.private_allowTime.contains(userID)) {
                            sendQuoteReply(sender, originalMessage, "您尚未启用此功能，无需关闭")
                            return
                        }
                        ExtraData.private_allowTime.remove(userID)
                        ExtraData.save()
                        sendQuoteReply(sender, originalMessage, "您已成功关闭主动消息权限，bot将停止给您发送任何私信主动消息")
                        return
                    }
                    var start = option.toIntOrNull()
                    var end = args.getOrNull(2)?.content?.toIntOrNull()
                    if (start == null || end == null) {
                        sendQuoteReply(sender, originalMessage, "[参数不匹配] $help")
                        return
                    }
                    if (start > end) {
                        val temp = start
                        start = end
                        end = temp
                    }
                    if (start < 0) start = 0
                    if (start > 23) start = 23
                    if (end < 0) end = 0
                    if (end > 23) end = 23
                    ExtraData.private_allowTime[userID] = start to end
                    ExtraData.save()
                    if (end - start == 23) {
                        sendQuoteReply(sender, originalMessage,
                            "[成功] 您已设置 始终允许 私信主动消息，bot向您发送主动消息将不受限制\n" +
                                    "如需关闭请使用：${commandPrefix}pb private off")
                    } else {
                        sendQuoteReply(sender, originalMessage,
                            "[成功] 您已设置在每日 ${start}:00 ~ ${end}:59 之间允许接收私信主动消息\n" +
                                    "如需关闭请使用：${commandPrefix}pb private off")
                    }
                }

                "stats", "statistics", "统计"-> {
                    val name = args.getOrNull(1)?.content
                    val statistics = if (name != null) {
                        if (PastebinData.pastebin.containsKey(name).not()) {
                            sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                            return
                        }
                        "　【数据统计 - ${name}】　\n" +
                        Statistics.getStatistic(name)
                    } else {
                        "　【pastebin数据统计】　\n" +
                        Statistics.getAllStatistics() +
                        summarizeStatistics(null)
                    }
                    sendQuoteReply(sender, originalMessage, statistics)
                }

                "list", "列表"-> {   // 查看完整列表
                    val pageLimit = ceil(PastebinData.pastebin.size.toDouble() / 20).toInt()
                    val addPara = args.getOrElse(1) { "default" }.toString()
                    var page = addPara.toIntOrNull() ?: 0
                    if (page < 0) page = 0
                    if (!PastebinConfig.enable_ForwardMessage && page == 0) { page = 1 }
                    if (page > pageLimit) {
                        sendQuoteReply(sender, originalMessage, "指定的页码 $page 超过了最大页数 $pageLimit")
                        return
                    }
                    val pastebinList: MutableList<String> = mutableListOf("")
                    val findAuthorMode = addPara != page.toString() && arrayOf("作者", "author", "全部", "all", "default", "0").contains(addPara).not()
                    if (findAuthorMode) {
                        var found = false
                        for ((key, value) in PastebinData.pastebin) {
                            val author = value["author"] ?: continue
                            if (addPara.contains(author, ignoreCase = true)) {
                                found = true
                                val language = value["language"] ?: "[X]"
                                val censorNote = if (PastebinData.censorList.contains(key)) "（审核中）" else ""
                                pastebinList[0] += "$key     $language $author$censorNote\n"
                            }
                        }
                        if (found) {
                            sendQuoteReply(sender, originalMessage, "·根据作者的查找结果：\n${pastebinList[0]}")
                        } else {
                            sendQuoteReply(sender, originalMessage, "在全部pastebin列表中未能找到此作者的记录：$addPara")
                        }
                        return
                    }
                    var pageIndex = 0
                    PastebinData.pastebin.entries.forEachIndexed { index, (key, value) ->
                        val language = value["language"] ?: "[X]"
                        val author = value["author"] ?: "[X]"
                        val isShowAuthor = addPara in listOf("作者", "author", "全部", "all") || page > 0
                        val censorNote = if (PastebinData.censorList.contains(key)) "（审核中）" else ""
                        pastebinList[pageIndex] += buildString {
                            append("$key     $language")
                            if (isShowAuthor) append(" $author")
                            append(censorNote)
                            appendLine()
                        }
                        val isLastItem = index == PastebinData.pastebin.size - 1
                        val isPageEnd = index % 20 == 19
                        if (isPageEnd || isLastItem) {
                            pastebinList[pageIndex] += "-----第 ${pageIndex + 1} 页 / 共 $pageLimit 页-----"
                            if (!isLastItem) {
                                pastebinList.add("")
                                pageIndex++
                            }
                        }
                    }
                    if (addPara in arrayOf("default", "0", "作者", "author", "全部", "all") && PastebinConfig.enable_ForwardMessage) {
                        try {
                            val forward: ForwardMessage = buildForwardMessage(sender.subject!!) {
                                displayStrategy = object : ForwardMessage.DisplayStrategy {
                                    override fun generateTitle(forward: RawForwardMessage): String = "pastebin列表"
                                    override fun generateBrief(forward: RawForwardMessage): String = "[pastebin列表]"
                                    override fun generatePreview(forward: RawForwardMessage): List<String> =
                                        mutableListOf("代码总数：${PastebinData.pastebin.size}",
                                            "缓存数量：${CodeCache.CodeCache.size}",
                                            "存储数量：${PastebinStorage.Storage.size}")
                                    override fun generateSummary(forward: RawForwardMessage): String = "总计 ${PastebinData.pastebin.size} 条代码链接"
                                }
                                for ((index, str) in pastebinList.withIndex()) {
                                    sender.subject!!.bot named "第${index + 1}页" says str
                                }
                            }
                            sender.sendMessage(forward)
                        } catch (e: Exception) {
                            logger.warning(e)
                            sendQuoteReply(sender, originalMessage, "[转发消息错误]\n处理列表或发送转发消息时发生错误，请联系铁蛋查看后台，简要错误信息：${e.message}")
                            return
                        }
                    } else {
                        sendQuoteReply(sender, originalMessage, "·pastebin列表：\n${pastebinList[page - 1]}")
                    }
                }

                "info", "信息", "示例"-> {   // 查看数据具体参数
                    val name = PastebinData.alias[args[1].content] ?: args[1].content
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                        return
                    }
                    val data = PastebinData.pastebin[name] ?: emptyMap()

                    val adminOption = args.getOrNull(2)?.content == "admin" && PastebinConfig.admins.contains(userID)
                    val alias = PastebinData.alias.entries.find { it.value == name }?.key
                    val info = buildString {
                        if (adminOption) appendLine("---[管理员预览]---")
                        append("名称：$name")
                        alias?.let { append("（$it）") }
                        appendLine()
                        appendLine("作者：${data["author"]}")
                        if (adminOption) appendLine("userID: ${data["userID"]}")
                        appendLine("语言：${data["language"]}")
                        append("pastebinUrl：")
                        appendLine(
                            when {
                                PastebinConfig.enable_censor ->
                                    "审核功能已开启，链接无法查看，如有需求请联系铁蛋"
                                !PastebinData.hiddenUrl.contains(name) || adminOption ->
                                    data["pastebinUrl"].orEmpty()
                                else ->
                                    "链接被隐藏"
                            }
                        )
                        data["util"]?.let { appendLine("辅助文件：$it") }
                        data["format"]?.let { fmt ->
                            appendLine("输出格式：$fmt")
                            data["width"]?.let { w -> appendLine("图片宽度：$w") }
                        }
                        if (data["storage"] == "true") appendLine("存储功能：已开启")
                        append(
                            if (!data["stdin"].isNullOrEmpty())
                                "示例输入：${data["stdin"]}"
                            else
                                "示例输入：无"
                        )
                    }
                    sendQuoteReply(sender, originalMessage, info)
                    if (PastebinData.censorList.contains(name)) {
                        sender.sendMessage("此条链接仍在审核中，暂时无法执行")
                    } else {
                        sender.sendMessage("#run ${alias ?: name} ${PastebinData.pastebin[name]?.get("stdin")}")
                    }
                }

                "add", "添加", "新增"-> {   // 添加pastebin数据
                    val name = args[1].content
                    if (PastebinData.pastebin.containsKey(name)) {
                        sendQuoteReply(sender, originalMessage, "添加失败：名称 $name 已存在")
                        return
                    }
                    if (PastebinData.alias.containsKey(name)) {
                        sendQuoteReply(sender, originalMessage, "添加失败：名称 $name 已存在于别名中")
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
                    var additionalOutput = ""
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                        return
                    }
                    if (userID.toString() != PastebinData.pastebin[name]?.get("userID") && PastebinConfig.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "此条记录并非由您创建，如需修改请联系创建者：${PastebinData.pastebin[name]?.get("userID")}")
                        return
                    }
                    val cnParaList = listOf("名称", "作者", "创建者ID", "语言", "链接", "辅助文件", "示例输入", "别名", "隐藏链接", "仅限群聊", "输出格式", "数据存储")
                    val enParaList = listOf("name", "author", "userID", "language", "pastebinUrl", "util", "stdin", "alias", "hide", "groupOnly", "format", "storage")
                    val cnIndex = cnParaList.indexOf(option)
                    if (cnIndex != -1) {
                        option = enParaList[cnIndex]
                    }
                    if (enParaList.contains(option).not()) {
                        sendQuoteReply(sender, originalMessage,
                                "未知的配置项：$option\n" +
                                "仅支持配置：\n" +
                                "name（名称）\n" +
                                "author（作者）\n" +
                                "language（语言）\n" +
                                "pastebinUrl（链接）\n" +
                                "util（辅助文件）\n" +
                                "stdin（示例输入）\n" +
                                "alias（别名）\n" +
                                "hide（隐藏链接）\n" +
                                "groupOnly（仅限群聊）\n" +
                                "format（输出格式）\n" +
                                "storage（数据存储）"
                        )
                        return
                    }
                    if (option == "format" && args.size > 5) {
                        sendQuoteReply(sender, originalMessage, "修改失败：format中仅能包含两个参数（输出格式，默认图片宽度）")
                        return
                    }
                    if (option != "stdin" && option != "format" && args.size > 4) {
                        sendQuoteReply(sender, originalMessage, "修改失败：$option 参数中不能包含空格！")
                        return
                    }
                    if (option != "stdin" && option != "util" && option != "alias" && content.isEmpty()) {
                        sendQuoteReply(sender, originalMessage, "修改失败：修改后的值为空！")
                        return
                    }
                    if (option == "name" || option == "alias") {
                        if (PastebinData.pastebin.containsKey(content)) {
                            sendQuoteReply(sender, originalMessage, "修改失败：名称 $content 已存在")
                            return
                        }
                        if (PastebinData.alias.containsKey(content)) {
                            sendQuoteReply(sender, originalMessage, "修改失败：名称 $content 已存在于别名中")
                            return
                        }
                    }
                    if (option == "pastebinUrl" && !checkUrl(content)) {
                        sendQuoteReply(sender, originalMessage, "修改失败：无效的链接 $content")
                        return
                    }
                    when (option) {
                        "name"-> {
                            val tempMap = linkedMapOf<String, MutableMap<String, String>>()
                            for ((key, value) in PastebinData.pastebin) {
                                if (key == name) {
                                    tempMap[content] = value
                                } else {
                                    tempMap[key] = value
                                }
                            }
                            PastebinData.pastebin.clear()
                            PastebinData.pastebin.putAll(tempMap)
                            // 转移别名
                            PastebinData.alias.entries.find { it.value == name }?.setValue(content)
                            // 转移标记
                            if (PastebinData.hiddenUrl.remove(name)) {
                                PastebinData.hiddenUrl.add(content)
                            }
                            if (PastebinData.groupOnly.remove(name)) {
                                PastebinData.groupOnly.add(content)
                            }
                            // 转移存储数据
                            if (PastebinStorage.Storage.contains(name)) {
                                PastebinStorage.Storage[content] = PastebinStorage.Storage[name]!!
                                PastebinStorage.Storage.remove(name)
                            }
                            // 转移缓存数据
                            if (CodeCache.CodeCache.contains(name)) {
                                CodeCache.CodeCache[content] = CodeCache.CodeCache[name]!!
                                CodeCache.CodeCache.remove(name)
                            }
                        }
                        "alias"-> {
                            PastebinData.alias.entries.removeIf { it.value == name }
                            if (content.isNotEmpty()) {
                                PastebinData.alias[content] = name
                            }
                        }
                        "format"-> {
                            val alias = mapOf("md" to "markdown", "html" to "markdown", "latex" to "LaTeX")
                            val paras = content.split(" ")
                            val format = if (paras[0] in alias.keys) {
                                alias[paras[0]]!!
                            } else {
                                paras[0]
                            }
                            content = format
                            if (listOf("text", "markdown", "base64", "image", "LaTeX", "json", "ForwardMessage").contains(format).not()) {
                                sendQuoteReply(sender, originalMessage,
                                        "无效的输出格式：$format\n" +
                                        "仅支持输出：\n" +
                                        "·text（纯文本）\n" +
                                        "·markdown/md（md/html转图片）\n" +
                                        "·base64（base64转图片）\n" +
                                        "·image（链接或路径直接发图）\n" +
                                        "·LaTeX（LaTeX转图片）\n" +
                                        "·json（自定义输出格式、图片宽度，MessageChain和MultipleMessage需使用此格式）\n" +
                                        "·ForwardMessage（使用json生成包含多条文字/图片消息的转发消息）"
                                )
                                return
                            }
                            if (format == "ForwardMessage" && !PastebinConfig.enable_ForwardMessage) {
                                sendQuoteReply(sender, originalMessage, "当前未开启转发消息，无法使用此功能！")
                                return
                            }
                            if (format == "text") {
                                PastebinData.pastebin[name]?.remove("format")
                            } else {
                                PastebinData.pastebin[name]?.set("format", format)
                            }
                            if (listOf("text", "json", "ForwardMessage").contains(format)) {
                                PastebinData.pastebin[name]?.remove("width")
                                val storage = paras.getOrNull(1)
                                when (storage) {
                                    in arrayListOf("enable","on","true","开启")-> {
                                        PastebinData.pastebin[name]?.set("storage", "true")
                                    }
                                    in arrayListOf("disable","off","false","关闭")-> {
                                        PastebinData.pastebin[name]?.remove("storage")
                                        PastebinStorage.Storage.remove(name)
                                    }
                                }
                            } else {
                                val width = paras.getOrNull(1)
                                if (width != null) {
                                    if (width.toIntOrNull() == null) {
                                        sendQuoteReply(sender, originalMessage, "修改失败：宽度只能是int型数字")
                                        return
                                    }
                                    PastebinData.pastebin[name]?.set("width", width)
                                }
                            }
                        }
                        "storage"-> {
                            when (content) {
                                in arrayListOf("enable","on","true","开启")-> {
                                    content = "开启"
                                    PastebinData.pastebin[name]?.set("storage", "true")
                                }
                                in arrayListOf("disable","off","false","关闭")-> {
                                    content = "关闭"
                                    PastebinData.pastebin[name]?.remove("storage")
                                    PastebinStorage.Storage.remove(name)
                                }
                                else-> {
                                    sendQuoteReply(sender, originalMessage, "无效的配置项：请设置 开启/关闭 存储功能")
                                    return
                                }
                            }
                        }
                        "hide"-> {
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
                        "groupOnly"-> {
                            when (content) {
                                in arrayListOf("enable","on","true","开启")-> {
                                    content = "仅限群聊执行"
                                    PastebinData.groupOnly.add(name)
                                }
                                in arrayListOf("disable","off","false","关闭")-> {
                                    content = "允许全局执行"
                                    PastebinData.groupOnly.remove(name)
                                }
                                else-> {
                                    sendQuoteReply(sender, originalMessage, "无效的配置项：请设置 开启/关闭 仅限群聊执行功能")
                                    return
                                }
                            }
                        }
                        "util"-> {
                            val files = File(GlotAPI.utilsFolder).listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
                            if (content.isEmpty()) {
                                PastebinData.pastebin[name]?.remove("util")
                            } else {
                                if (files.contains(content).not()) {
                                    sendQuoteReply(sender, originalMessage, "未找到文件，请检查文件名\n辅助文件列表：\n${files.joinToString("\n")}")
                                    return
                                }
                                PastebinData.pastebin[name]?.set("util", content)
                            }
                        }
                        else -> {
                            if (option == "pastebinUrl" && CodeCache.CodeCache.contains(name)) {
                                additionalOutput = "pastebinUrl被修改，代码缓存已清除，下次执行时需重新获取代码\n"
                                CodeCache.CodeCache.remove(name)
                            }
                            PastebinData.pastebin[name]?.set(option, content)
                        }
                    }
                    if (option == "hide") {
                        sendQuoteReply(sender, originalMessage, "${additionalOutput}成功将 $name 的pastebin链接标记为 $content")
                    } else if (option == "groupOnly") {
                        sendQuoteReply(sender, originalMessage, "${additionalOutput}成功将 $name 标记为 $content")
                    } else if (option == "userID") {
                        sendQuoteReply(sender, originalMessage, "${additionalOutput}成功将 $name 的所有权转移至 $content")
                    } else if (option == "pastebinUrl" && PastebinConfig.enable_censor) {
                        if (PastebinConfig.admins.contains(userID)) {
                            sendQuoteReply(sender, originalMessage, "${additionalOutput}$name 的pastebinUrl参数的修改已生效")
                        } else {
                            PastebinData.censorList.add(name)
                            sendQuoteReply(
                                sender, originalMessage,
                                "${additionalOutput}$name 的pastebinUrl参数已修改，已自动提交新审核，在审核期间本条链接暂时无法运行，望理解"
                            )
                        }
                    } else {
                        sendQuoteReply(sender, originalMessage, "${additionalOutput}成功将 $name 的 $option 参数修改为 $content")
                    }
                    PastebinData.save()
                    PastebinStorage.save()
                    CodeCache.save()
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
                    PastebinData.alias.entries.removeIf { it.value == name }
                    PastebinData.hiddenUrl.remove(name)
                    PastebinData.groupOnly.remove(name)
                    PastebinData.censorList.remove(name)
                    PastebinData.pastebin.remove(name)
                    PastebinData.save()
                    PastebinStorage.Storage.remove(name)
                    PastebinStorage.save()
                    CodeCache.CodeCache.remove(name)
                    CodeCache.save()
                    sendQuoteReply(sender, originalMessage, "删除 $name 成功！")
                }

                "upload", "上传"-> {   // 上传自定义图片
                    val imageName = args[1].content
                    var imageUrl: String
                    try{
                        imageUrl = (args[2] as Image).queryUrl()
                    } catch (e: ClassCastException) {
                        if (args[2] is UnsupportedMessage) {
                            sendQuoteReply(sender, originalMessage, "[不支持的消息] 无法解析新版客户端发送的图片消息：请尝试使用*电脑怀旧版客户端*重新发送图片上传，或将图片替换为URL")
                            return
                        } else if (args[2].content.startsWith("https://")) {
                            imageUrl = args[2].content
                        } else {
                            sendQuoteReply(sender, originalMessage, "转换图片失败，您发送的消息可能无法转换为图片，请尝试更换图片或联系铁蛋寻求帮助。如果使用URL上传，请以\"https://\"开头")
                            return
                        }
                    } catch (e: Exception) {
                        logger.warning("${e::class.simpleName}: ${e.message}")
                        sendQuoteReply(sender, originalMessage,
                                "获取图片参数失败，请检查指令格式是否正确\n" +
                                "${commandPrefix}pb upload <图片名称(需要包含拓展名)> <【图片/URL】>\n" +
                                "注意：图片名字后需要空格或换行分隔图片参数")
                        return
                    }
                    val outputFilePath = "./data/${JCompilerCollection.dataHolderName}/images/"
                    val pair = downloadImage(imageUrl, outputFilePath, imageName)
                    if (pair.first.startsWith("[错误]")) {
                        sendQuoteReply(sender, originalMessage, pair.first)
                        return
                    }
                    sendQuoteReply(sender, originalMessage, "上传图片成功！您已经可以通过目录“image://$imageName”调用此图片（用时：${pair.second}秒）")
                }

                "storage", "查询存储", "存储"-> {   // 查询存储数据
                    if (!PastebinConfig.enable_ForwardMessage) {
                        sendQuoteReply(sender, originalMessage, "当前未开启转发消息，无法使用此功能！")
                        return
                    }
                    val name = args[1].content
                    if (PastebinData.pastebin.containsKey(name).not()) {
                        sendQuoteReply(sender, originalMessage, "未知的名称：$name\n请使用「${commandPrefix}pb list」来查看完整列表")
                        return
                    }
                    val storage = PastebinStorage.Storage[name]
                    if (userID.toString() != PastebinData.pastebin[name]?.get("userID") && PastebinConfig.admins.contains(userID).not()) {
                        sendQuoteReply(sender, originalMessage, "【查询名称】$name\n【用户数量】${storage?.size?.minus(1)}\n此条记录并非由您创建，仅创建者可查看存储数据详细内容")
                        return
                    }
                    val mail = args.getOrNull(2)?.content == "邮件" || args.getOrNull(2)?.content == "mail"
                    if (MailConfig.enable && mail && storage != null) {
                        var output = "【查询名称】$name\n【用户数量】${storage.size - 1}\n\n"
                        for (id in storage.keys) {
                            output += if (id == 0L) {
                                "【全局存储[global]】\n${storage[id]}\n\n"
                            } else {
                                "【用户存储[$id]】\n${storage[id]}\n\n"
                            }
                        }
                        logger.info("请求使用邮件发送结果：$name")
                        sendStorageMail(sender, originalMessage, output, userID, name)
                        return
                    }
                    val id = try {
                        if (args[2].content == "global" || args[2].content == "全局") 0
                        else args[2].content.toLong()
                    } catch (e: Exception) {
                        -1
                    }
                    try {
                        val forward = buildForwardMessage(sender.subject!!) {
                            displayStrategy = object : ForwardMessage.DisplayStrategy {
                                override fun generateTitle(forward: RawForwardMessage): String = "存储数据查询"
                                override fun generateBrief(forward: RawForwardMessage): String = "[存储数据]"
                                override fun generatePreview(forward: RawForwardMessage): List<String> =
                                    if (id == -1L) listOf("查询名称：$name", "用户数量：${storage?.size?.minus(1)}")
                                    else listOf("查询名称：$name", "查询ID：$id")

                                override fun generateSummary(forward: RawForwardMessage): String =
                                    if (storage == null) "查询失败：名称不存在"
                                    else if (id != -1L && storage[id] == null) "查询失败：ID不存在"
                                    else "查询成功"
                            }
                            if (id == -1L) {
                                sender.subject!!.bot named "存储查询" says "【查询名称】$name\n【用户数量】${storage?.size?.minus(1)}"
                                if (storage != null) {
                                    for (qq in storage.keys) {
                                        val content = if (storage[qq]!!.length <= 10000) storage[qq]
                                        else "[内容过长] 数据长度：${storage[qq]?.length}，如需查看完整内容请使用指令\n\n${commandPrefix}pb storage $name mail\n\n将结果发送邮件至您的邮箱"
                                        if (qq == 0L)
                                            sender.subject!!.bot named "全局存储" says "【全局存储[global]】\n$content"
                                        else
                                            sender.subject!!.bot named "用户存储" says "【用户存储[$qq]】\n$content"
                                    }
                                } else {
                                    sender.subject!!.bot named "查询失败" says "[错误] 查询失败：存储数据中不存在此名称"
                                }
                            } else {
                                sender.subject!!.bot named "存储查询" says "【查询名称】$name\n【查询ID】$id"
                                val idStorage = storage?.get(id)
                                sender.subject!!.bot named "存储查询" says
                                        if (idStorage == null) "[错误] 查询失败：存储数据中不存在此名称或userID"
                                        else if (idStorage.isEmpty()) "[警告] 查询成功，但查询的存储数据为空"
                                        else idStorage
                            }
                        }
                        sender.sendMessage(forward)
                    } catch (e: MessageTooLargeException) {
                        val length = if (id == -1L) "汇总存储查询总长度超出限制，用户数量：${storage?.size?.minus(1)}，请尝试添加编号查询指定内容"
                                else "数据长度：${storage?.get(id)?.length}"
                        sendQuoteReply(sender, originalMessage, "[内容过长] $length。如需查看完整内容请使用指令\n" +
                                "${commandPrefix}pb storage $name mail\n将结果发送邮件至您的邮箱")
                    } catch (e: Exception) {
                        logger.warning(e)
                        sendQuoteReply(sender, originalMessage, "[转发消息错误]\n生成或发送转发消息时发生错误，请联系铁蛋查看后台，简要错误信息：${e.message}")
                    }
                }

                // admin指令
                "handle", "处理"-> {   // 处理添加和修改申请（审核功能）
                    if (PastebinConfig.admins.contains(userID).not()) throw PermissionDeniedException()
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
                        } catch (e: Exception) {
                            logger.warning(e)
                            "发送消息至申请人时出现错误，可能因为机器人权限不足或未找到对象，详细信息请查看后台"
                        }
                    if (option == "拒绝") {
                        PastebinData.pastebin.remove(name)
                    }
                    sendQuoteReply(sender, originalMessage, reply)   // 回复指令发出者
                }

                "black", "黑名单"-> {   // 添加/移除黑名单
                    if (PastebinConfig.admins.contains(userID).not()) throw PermissionDeniedException()
                    try {
                        val qq = args[1].content.toLong()
                        if (ExtraData.BlackList.contains(qq)) {
                            ExtraData.BlackList.remove(qq)
                            sendQuoteReply(sender, originalMessage, "已将 $qq 移出黑名单")
                        } else {
                            ExtraData.BlackList.add(qq)
                            sendQuoteReply(sender, originalMessage, "已将 $qq 移入黑名单")
                        }
                        ExtraData.save()
                    } catch (e: IndexOutOfBoundsException) {
                        var blackListInfo = "·代码执行黑名单："
                        for (black in ExtraData.BlackList) {
                            blackListInfo += "\n$black"
                        }
                        sendQuoteReply(sender, originalMessage, blackListInfo)
                    }
                }

                "reload", "重载"-> {   // 重载配置和数据文件
                    if (PastebinConfig.admins.contains(userID).not()) throw PermissionDeniedException()
                    try {
                        PastebinConfig.reload()
                        MailConfig.reload()
                        PastebinData.reload()
                        ExtraData.reload()
                        PastebinStorage.reload()
                        CodeCache.reload()
                        sendQuoteReply(sender, originalMessage, "数据重载成功")
                    } catch (e: Exception) {
                        logger.warning(e)
                        sendQuoteReply(sender, originalMessage, "出现错误：${e.message}")
                    }
                }

                else-> {
                    sendQuoteReply(sender, originalMessage, "[参数不匹配]\n请使用「${commandPrefix}pb help」来查看指令帮助")
                }
            }
        } catch (e: PermissionDeniedException) {
            sendQuoteReply(sender, originalMessage, "[参数不匹配]\n请使用「${commandPrefix}pb help」来查看指令帮助")
        } catch (e: IndexOutOfBoundsException) {
            sendQuoteReply(sender, originalMessage, "[参数不足]\n请使用「${commandPrefix}pb help」来查看指令帮助")
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply(sender, originalMessage, "[指令执行未知错误]\n可能由于bot发消息出错，请联系铁蛋查看后台：${e::class.simpleName}(${e.message})")
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

    private fun summarizeStatistics(userID: Long?): String {
        val filtered = if (userID == null) {
            PastebinData.pastebin
        } else {
            PastebinData.pastebin.filterValues { it["userID"] == userID.toString() }
        }
        val projectCount = filtered.size
        if (projectCount == 0) {
            return "·未上传过代码项目"
        }

        val lang = filtered.values.mapNotNull { it["language"]?.lowercase() }
        val langCounts: Map<String, Int> = lang.groupingBy { it }.eachCount()
        val langStats = langCounts.entries
            .sortedByDescending { it.value }
            .joinToString(separator = "\n") { (lang, cnt) ->
                val percent = cnt.toDouble() / projectCount * 100
                val formatted = String.format("%.2f", percent)
                " - $lang: ${formatted}%"
            }

        val languageMap: Map<String, String> = filtered
            .mapNotNull { (key, valueMap) ->
                valueMap["language"]?.lowercase()?.let { language ->
                    key to language
                }
            }.toMap()
        val top10Project = languageMap.entries
            .sortedByDescending { (key, _) ->
                ExtraData.statistics[key]?.get("run") ?: 0
            }
            .take(10)
            .joinToString(separator = "、") { (key, language) ->
                "$key${if (userID == null) "（$language）" else ""}"
            }

        return "·项目总数：$projectCount\n" +
                "$langStats\n\n" +
                "·热门项目：$top10Project"
    }

    private suspend fun sendStorageMail(
        sender: CommandSender,
        originalMessage: MessageChain,
        output: String,
        userID: Long,
        name: String
    ) {
        try {
            withContext(Dispatchers.IO) {
                FileOutputStream("$folder/storage.txt").use { outputStream ->
                    outputStream.write(output.toByteArray())
                }
            }
        } catch (e: IOException) {
            logger.warning(e)
            sendQuoteReply(sender, originalMessage, "[请求使用邮件发送]\n但在尝试导出存储数据文件时发生错误：${e.message}")
            return
        }
        val session = buildMailSession {
            MailConfig.properties.inputStream().use {
                load(it)
            }
        }
        val mail = buildMailContent(session) {
            to = "${userID}@qq.com"
            title = "存储数据查询"
            text {
                append("※※※使用此服务表示您知晓并遵守以下注意事项※※※\n")
                append("1、不能在短时间内频繁使用此邮件发送服务\n")
                append("2、不能在查询名称、查询ID、存储数据中添加任何违规内容\n")
                append("3、此邮件为自动发送，请不要回复。如遇到问题请直接联系铁蛋\n")
                append("\n\n")
                append("【查询名称】$name\n\n")
                append("·查询的结果数据请查看附件")
            }
            file("存储数据.txt") {
                File("$folder/storage.txt")
            }
        }
        val current = Thread.currentThread()
        val oc = current.contextClassLoader
        try {
            current.contextClassLoader = MailConfig::class.java.classLoader
            jakarta.mail.Transport.send(mail)
            sendQuoteReply(sender, originalMessage, "[请求使用邮件发送]\n存储数据导出成功（文件总长度：${output.length}），并通过邮件发送，请您登录邮箱查看")
        } catch (cause: jakarta.mail.MessagingException) {
            sendQuoteReply(sender, originalMessage, "[请求使用邮件发送]\n存储数据导出成功（文件总长度：${output.length}），但邮件发送失败，原因: ${cause.message}")
        } catch (e: Exception){
            logger.warning(e)
            sendQuoteReply(sender, originalMessage, "[请求使用邮件发送]\n存储数据导出成功（文件总长度：${output.length}），但发生其他未知错误: ${e.message}")
        } finally {
            current.contextClassLoader = oc
            File("$folder/storage.txt").delete()
        }
    }
}
