package site.tiedan.command

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.console.command.isNotConsole
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.MessageTooLargeException
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.containsFriend
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.Command
import site.tiedan.MiraiCompilerFramework.ERROR_MSG_MAX_LENGTH
import site.tiedan.MiraiCompilerFramework.THREADS
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.getNickname
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.pendingCommand
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.trimToMaxLength
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.command.CommandBucket.bucketIDsToNames
import site.tiedan.command.CommandBucket.linkedBucketID
import site.tiedan.command.CommandBucket.removeProjectFromBucket
import site.tiedan.config.MailConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.data.*
import site.tiedan.data.CodeCache
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.data.PastebinStorage
import site.tiedan.format.MarkdownImageGenerator
import site.tiedan.module.MailService
import site.tiedan.module.Statistics
import site.tiedan.utils.FuzzySearch
import site.tiedan.utils.HttpUtil
import site.tiedan.utils.PastebinUrlHelper
import site.tiedan.utils.PastebinUrlHelper.checkUrl
import site.tiedan.utils.PastebinUrlHelper.supportedUrls
import java.io.File
import java.net.ConnectException
import kotlin.math.ceil

/**
 * # pb代码项目操作指令
 *
 * @author tiedanGH
 */
object CommandPastebin : RawCommand(
    owner = MiraiCompilerFramework,
    primaryName = "pastebin",
    secondaryNames = arrayOf("pb", "代码"),
    description = "pb代码项目操作指令",
    usage = "${commandPrefix}pb help"
){
    private val commandList = arrayOf(
        Command("pb support", "pb 支持", "支持粘贴代码的网站", 1),
        Command("pb profile [QQ]", "pb 简介 [QQ]", "查看个人信息", 1),
        Command("pb private", "pb 私信时段", "允许私信主动消息", 1),
        Command("pb stats [名称]", "pb 统计 [名称]", "查看统计信息", 1),
        Command("pb list [查询模式]", "pb 列表 [查询模式]", "查看项目列表", 1),
        Command("pb info <名称>", "pb 信息 <名称>", "查看信息&运行示例", 1),
        Command("pb thread", "pb 进程", "查询运行和等待中的进程", 1),
        Command("run <名称> [stdin]", "pb 运行 <名称> [输入]", "运行代码项目", 1),

        Command("pb add <名称> <作者> <语言> <源代码URL> [示例输入(stdin)]", "pb 添加 <名称> <作者> <语言> <源代码URL> [示例输入(stdin)]", "添加Pastebin项目", 2),
        Command("pb set <名称> <参数名> <内容>", "pb 修改 <名称> <参数名> <内容>", "修改项目属性", 2),
        Command("pb delete <名称>", "pb 删除 <名称>", "永久删除项目", 2),

        Command("pb set <名称> format <输出格式> [宽度/存储]", "pb 修改 <名称> 输出格式 <输出格式> [宽度/存储]", "修改输出格式", 3),
        Command("pb storage <名称> [查询ID/mail]", "pb 存储 <名称> [查询ID/邮件]", "查询存储数据", 3),
        Command("pb export <名称>", "pb 导出 <名称>", "将项目代码缓存导出为临时链接（过期时使用）", 3),
        Command("bucket help", "存储库 帮助", "跨项目存储库操作指令", 3),
        Command("image help", "图片 帮助", "本地图片操作指令", 3),

        Command("pb handle <名称> <同意/拒绝> [备注]", "pb 处理 <名称> <同意/拒绝> [备注]", "处理添加和修改申请", 4),
        Command("pb black [qq]", "pb 黑名单 [QQ号]", "黑名单处理", 4),
        Command("pb reload", "pb 重载", "重载本地数据", 4),
    )

    override suspend fun CommandSender.onCommand(args: MessageChain) {

        val userID = this.user?.id ?: 10000
        val isAdmin = PastebinConfig.admins.contains(userID)

        if (pendingCommand[userID]?.let { it != args.content } == true) {
            pendingCommand.remove(userID)
            sendQuoteReply("指令不一致，操作已取消")
        }

        try {
            when (args[0].content) {

                "help"-> {   // 查看pastebin帮助（help）
                    var reply = "📋 pastebin查看运行帮助：\n" +
                            commandList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                            "✏️ pastebin更新数据帮助：\n" +
                            commandList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                            "⚙️ pastebin高级功能帮助：\n" +
                            commandList.filter { it.type == 3 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                    if (args.getOrNull(1)?.content == "all" && isAdmin) {
                        reply += "\n" +
                                "🛠️ pastebin管理指令帮助：\n" +
                                commandList.filter { it.type == 4 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                    }
                    sendQuoteReply(reply)
                }

                "帮助"-> {   // 查看pastebin帮助（帮助）
                    var reply = "📋 pastebin查看相关帮助：\n" +
                            commandList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" } +
                            "✏️ pastebin更新数据帮助：\n" +
                            commandList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" } +
                            "⚙️ pastebin高级功能帮助：\n" +
                            commandList.filter { it.type == 3 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" }
                    if (args.getOrNull(1)?.content == "all" && isAdmin) {
                        reply += "\n" +
                                "🛠️ pastebin管理指令帮助：\n" +
                                commandList.filter { it.type == 4 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" }
                    }
                    sendQuoteReply(reply)
                }

                "support", "支持"-> {   // 支持粘贴代码的网站
                    sendQuoteReply(
                        "🌐 目前pb支持粘贴代码的网站：\n" +
                        supportedUrls.joinToString(separator = "") { "${it.website}\n" } +
                        "💡 如有更多好用的网站欢迎推荐"
                    )
                }

                "profile", "简介"-> {   // 查看个人信息
                    val id = args.getOrNull(1)?.content?.replace("@", "")?.toLongOrNull() ?: userID
                    val time = ExtraData.private_allowTime[id]
                    val reply = buildString {
                        append("　【个人信息")
                        if (id != userID) append(" - $id")
                        appendLine("】　")
                        append("💬 接收主动私信：")
                        if (bot?.containsFriend(userID) != true) {
                            appendLine("未添加好友")
                        } else if (time == null) {
                            appendLine("不允许")
                        } else if (time.second - time.first == 23 || time.second == time.first - 1) {
                            appendLine("始终允许")
                        } else {
                            appendLine("${time.first}:00 ~ ${time.second}:59")
                        }
                        append(Statistics.imageStatistics(id))
                        append(Statistics.summarizeStatistics(id))
                    }
                    sendQuoteReply(reply)
                }

                "private", "私信时段"-> {   // 允许私信主动消息
                    if (bot?.containsFriend(userID) != true && isNotConsole()) {
                        sendQuoteReply("请先添加bot为好友才能使用此功能")
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
                        |·如有疑问或需要帮助，请联系管理员。
                    """.trimMargin()
                    val option = args.getOrNull(1)?.content
                    if (option == null) {
                        sendQuoteReply(notice)
                        return
                    }
                    if (arrayListOf("off","disable","关闭","取消").contains(option)) {
                        if (!ExtraData.private_allowTime.contains(userID)) {
                            sendQuoteReply("您尚未启用此功能，无需关闭")
                            return
                        }
                        ExtraData.private_allowTime.remove(userID)
                        ExtraData.save()
                        sendQuoteReply("您已成功关闭主动消息权限，bot将停止给您发送任何私信主动消息")
                        return
                    }
                    var start = option.toIntOrNull()
                    var end = args.getOrNull(2)?.content?.toIntOrNull()
                    if (start == null || end == null) {
                        sendQuoteReply("[参数不匹配] $help")
                        return
                    }
                    if (start < 0) start = 0
                    if (start > 23) start = 23
                    if (end < 0) end = 0
                    if (end > 23) end = 23
                    ExtraData.private_allowTime[userID] = start to end
                    ExtraData.save()
                    if (end - start == 23 || end == start - 1) {
                        sendQuoteReply(
                            "[成功] 您已设置 始终允许 私信主动消息，bot向您发送主动消息将不受限制\n" +
                                    "如需关闭请使用：${commandPrefix}pb private off")
                    } else {
                        sendQuoteReply(
                            "[成功] 您已设置在每日 ${start}:00 ~ ${end}:59 之间允许接收私信主动消息\n" +
                                    "如需关闭请使用：${commandPrefix}pb private off")
                    }
                }

                "stats", "statistics", "统计"-> {   // 查看统计信息
                    val name = args.getOrNull(1)?.content?.let { PastebinData.alias[it] ?: it }
                    val statistics = if (name != null) {
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
                        "　【📊数据统计 - ${name}】　\n" +
                        Statistics.getStatistic(name)
                    } else {
                        "　【📊pastebin数据统计】　\n" +
                        Statistics.getAllStatistics() + "\n" +
                        Statistics.summarizeStatistics(null)
                    }
                    sendQuoteReply(statistics)
                }

                "list", "列表"-> {   // 查看完整列表
                    val commandPbList = arrayOf(
                        Command("pb list [all]", "pb 列表 [全部]", "图片输出完整列表", 1),
                        Command("pb list forward [作者]", "pb 列表 转发 [作者]", "转发消息输出完整列表", 1),

                        Command("pb list run [作者名]", "pb 列表 次数 [作者名]", "根据总执行次数排序", 2),
                        Command("pb list heat [作者名]", "pb 列表 热度 [作者名]", "根据热度排序", 2),

                        Command("pb list search [项目名] [作者名] [语言] [输出格式]", "pb 列表 搜索 [项目名] [作者名] [语言] [输出格式]", "根据条件搜索项目（输入 null 来跳过某一项）", 3),
                        Command("pb list author <作者名>", "pb 列表 作者 <作者名>", "根据作者关键词筛选", 3),
                        Command("pb list lang <语言>", "pb 列表 语言 <语言>", "根据编程语言筛选", 3),
                        Command("pb list format <输出格式>", "pb 列表 格式 <输出格式>", "根据输出格式筛选", 3),
                        Command("pb list page <页数>", "pb 列表 页码 <页数>", "根据页码查询", 3),
                    )
                    fun String?.nullIfLiteral(): String? =
                        if (this == "null") null else this

                    val mode = args.getOrElse(1) { PlainText("all") }.content
                    val params: Array<String?> = args.drop(2)
                        .map { it.content.nullIfLiteral() }
                        .toTypedArray()
                    val totalPage = ceil(PastebinData.pastebin.size.toDouble() / 20).toInt()
                    when (mode) {
                        "help"-> {
                            var reply = "📜 查看完整列表：\n" +
                                    commandPbList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                                    "📊 列表统计与排序：\n" +
                                    commandPbList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                                    "🔍 列表搜索与筛选：\n" +
                                    commandPbList.filter { it.type == 3 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                            sendQuoteReply(reply)
                        }

                        "帮助"-> {
                            var reply = "📜 查看完整列表：\n" +
                                    commandPbList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" } +
                                    "📊 列表统计与排序：\n" +
                                    commandPbList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" } +
                                    "🔍 列表搜索与筛选：\n" +
                                    commandPbList.filter { it.type == 3 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" }
                            sendQuoteReply(reply)
                        }

                        "all", "全部",
                        "run", "次数",
                        "heat", "热度",
                        "lang", "language", "语言",
                        "format", "格式",
                        "author", "作者",
                        "search", "搜索",
                        "page", "页码"-> {
                            val sortMode = when (mode) {
                                in arrayOf("run", "次数")-> "run"
                                in arrayOf("heat", "热度")-> "score"
                                else-> "normal"
                            }
                            val filter = when (mode) {
                                in arrayOf("all", "全部", "run", "次数", "heat", "热度", "author", "作者") ->
                                    MarkdownImageGenerator.PastebinListFilter(author = params.getOrNull(0))
                                in arrayOf("search", "搜索") ->
                                    MarkdownImageGenerator.PastebinListFilter(
                                        project = params.getOrNull(0),
                                        author = params.getOrNull(1),
                                        language = params.getOrNull(2),
                                        format = params.getOrNull(3)
                                    )
                                in arrayOf("lang", "language", "语言") ->
                                    MarkdownImageGenerator.PastebinListFilter(language = params.getOrNull(0))
                                in arrayOf("format", "格式") ->
                                    MarkdownImageGenerator.PastebinListFilter(format = params.getOrNull(0))
                                in arrayOf("page", "页码") ->
                                    MarkdownImageGenerator.PastebinListFilter(page = params.getOrNull(0)?.toIntOrNull())
                                else ->
                                    MarkdownImageGenerator.PastebinListFilter()
                            }
                            val markdownResult = MarkdownImageGenerator.processMarkdown(
                                name = null,
                                MarkdownImageGenerator.generatePastebinListHtml(sortMode, filter),
                                width = if (filter.isFilterEnabled) "600" else "2000"
                            )
                            if (!markdownResult.success) {
                                sendQuoteReply(markdownResult.message)
                                return
                            }
                            val file = File("${cacheFolder}markdown.png")
                            val image = subject?.uploadFileToImage(file)
                                ?: return sendQuoteReply("[错误] 图片文件异常：ExternalResource上传失败，请尝试重新执行")
                            sendMessage(image)
                        }

                        "forward", "转发"-> {
                            if (!PastebinConfig.enable_ForwardMessage) {
                                sendQuoteReply("当前未开启转发消息，无法使用此方法查询列表！")
                                return
                            }
                            val pastebinList: MutableList<String> = mutableListOf("")
                            var pageIndex = 0
                            PastebinData.pastebin.entries.forEachIndexed { index, (key, value) ->
                                val language = value["language"] ?: "[数据异常]"
                                val author = value["author"] ?: "[数据异常]"
                                val isShowAuthor = params.getOrNull(0) in listOf("author", "作者") || mode in listOf("page", "页码")
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
                                    pastebinList[pageIndex] += "-----第 ${pageIndex + 1} 页 / 共 $totalPage 页-----"
                                    if (!isLastItem) {
                                        pastebinList.add("")
                                        pageIndex++
                                    }
                                }
                            }
                            try {
                                val forward: ForwardMessage = buildForwardMessage(subject!!) {
                                    displayStrategy = object : ForwardMessage.DisplayStrategy {
                                        override fun generateTitle(forward: RawForwardMessage): String =
                                            "Pastebin完整列表"

                                        override fun generateBrief(forward: RawForwardMessage): String =
                                            "[Pastebin列表]"

                                        override fun generatePreview(forward: RawForwardMessage): List<String> =
                                            mutableListOf(
                                                "项目总数：${PastebinData.pastebin.size}",
                                                "缓存数量：${CodeCache.CodeCache.size}",
                                                "存储数量：${PastebinStorage.storage.size}"
                                            )

                                        override fun generateSummary(forward: RawForwardMessage): String =
                                            "总计 ${PastebinData.pastebin.size} 条代码链接"
                                    }
                                    for ((index, str) in pastebinList.withIndex()) {
                                        subject!!.bot named "第${index + 1}页" says str
                                    }
                                }
                                sendMessage(forward)
                            } catch (e: Exception) {
                                logger.warning(e)
                                sendQuoteReply("[转发消息错误]\n处理列表或发送转发消息时发生错误，请联系管理员查看后台，简要错误信息：${e.message}")
                                return
                            }
                        }

                        else-> {
                            var reply = "📜 查看完整列表：\n" +
                                    commandPbList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                                    "📊 列表统计与排序：\n" +
                                    commandPbList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                                    "🔍 列表搜索与筛选：\n" +
                                    commandPbList.filter { it.type == 3 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                            sendQuoteReply("[未知查询方法] $mode\n\n$reply")
                        }
                    }
                }

                "info", "信息"-> {   // 查看数据具体参数
                    val name = PastebinData.alias[args[1].content] ?: args[1].content
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

                    val data = PastebinData.pastebin[name].orEmpty()
                    val ownerID = PastebinData.pastebin[name]?.get("userID")
                    val isOwner = userID.toString() == ownerID
                    val showAll = args.getOrNull(2)?.content == "show" && (isOwner || isAdmin)
                    val alias = PastebinData.alias.entries.find { it.value == name }?.key
                    val info = buildString {
                        if (showAll) appendLine("---[完整信息预览]---")
                        append("名称：$name")
                        alias?.let { append("（$it）") }
                        appendLine()
                        appendLine("作者：${data["author"]}")
                        if (showAll) appendLine("userID: ${data["userID"]}")
                        appendLine("语言：${data["language"]}")
                        append("源代码URL：")
                        appendLine(
                            when {
                                PastebinConfig.enable_censor ->
                                    "审核功能已开启，链接无法查看，如有需求请联系管理员"
                                !PastebinData.hiddenUrl.contains(name) || showAll ->
                                    "\n${data["url"].orEmpty()}"
                                else ->
                                    "链接被隐藏"
                            }
                        )
                        data["util"]?.let { appendLine("辅助文件：$it") }
                        data["format"]?.let { fmt ->
                            appendLine("输出格式：$fmt")
                            data["width"]?.let { w -> appendLine("图片宽度：$w") }
                        }
                        if (data["storage"] == "true") {
                            val linkedBuckets = bucketIDsToNames(linkedBucketID(name))
                            val storageInfo =
                                if (linkedBuckets.isEmpty()) "存储功能：已开启"
                                else "关联存储库：$linkedBuckets"
                            appendLine(storageInfo)
                        }
                        if (data["base64"] == "true") appendLine("输入图片base64：已开启")
                        appendLine(
                            if (data["stdin"].isNullOrEmpty()) "示例输入：无"
                            else "示例输入：${data["stdin"]}"
                        )
                        if (PastebinData.censorList.contains(name)) {
                            appendLine("[!] 此条链接仍在审核中，暂时无法执行")
                        }
                    }
                    sendQuoteReply(info)
                    if (PastebinData.censorList.contains(name).not()) {
                        if (subject is Group) {
                            sendMessage("${PastebinConfig.QUICK_PREFIX}${alias ?: name} ${PastebinData.pastebin[name]?.get("stdin")}")
                        } else {
                            sendMessage("#run ${alias ?: name} ${PastebinData.pastebin[name]?.get("stdin")}")
                        }
                    }
                }

                "thread", "进程"-> {   // 查询运行和等待中的进程
                    if (THREADS.isEmpty()) {
                        sendQuoteReply("当前没有正在运行或等待中的进程")
                        return
                    }
                    val threads = THREADS.withIndex().joinToString("\n") { (index, thread) ->
                        val elapsed = (System.currentTimeMillis() - thread.startTime) / 1000
                        "【#${index + 1}】 ${thread.name}\n" +
                        "${thread.sender}\n" +
                        "${thread.from} [${thread.platform}]\n" +
                        "⏱️ 等待时间：${elapsed} 秒"
                    }
                    sendQuoteReply(" ⏳ 当前正在运行或等待的进程：\n$threads")
                }

                "add", "添加", "新增"-> {   // 添加Pastebin项目
                    val name = args[1].content
                    if (PastebinData.pastebin.contains(name)) {
                        sendQuoteReply("添加失败：名称 $name 已存在")
                        return
                    }
                    if (PastebinData.alias.contains(name)) {
                        sendQuoteReply("添加失败：名称 $name 已存在于别名中")
                        return
                    }
                    val author = args[2].content
                    val language = args[3].content
                    val url = PastebinUrlHelper.extractUrl(args[4].content)
                    val stdin = args.drop(5).joinToString(separator = " ")
                    if (!checkUrl(url)) {
                        sendQuoteReply(
                            "添加失败：无效的链接 $url\n" +
                            "🔗 支持的URL格式如下方所示：\n" +
                            supportedUrls.joinToString(separator = "") { "${it.url}...\n" }
                        )
                        return
                    }
                    PastebinData.pastebin[name] =
                        mutableMapOf(
                            "author" to author,
                            "userID" to userID.toString(),
                            "language" to language,
                            "url" to url,
                            "stdin" to stdin
                        )
                    if (PastebinConfig.enable_censor && !isAdmin) {
                        PastebinData.censorList.add(name)
                        sendQuoteReply("您已成功提交审核，此提交并不会发送提醒，管理员会定期查看并审核，您也可以主动联系进行催审")
                    } else {
                        sendQuoteReply(
                            "📁 添加新项目成功！\n" +
                            "名称：$name\n" +
                            "作者：$author\n" +
                            "userID：$userID\n" +
                            "语言：$language\n" +
                            "源代码URL：\n" +
                            if (PastebinConfig.enable_censor) {
                                "审核功能已开启，链接无法查看\n"
                            } else {
                                "${url}\n"
                            } +
                            "示例输入：${stdin}"
                        )
                    }
                    PastebinData.save()
                }

                "set", "修改"-> {   // 修改项目属性
                    val name = args[1].content
                    var option = args[2].content
                    var content = args.drop(3).joinToString(separator = " ")
                    var additionalOutput = ""
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
                    val ownerID = PastebinData.pastebin[name]?.get("userID")
                    val isOwner = userID.toString() == ownerID
                    if (!isOwner && !isAdmin) {
                        sendQuoteReply("无权修改此项目，如需修改请联系所有者：$ownerID")
                        return
                    }
                    val paraMap = mapOf(
                        // 基础信息修改
                        "名称" to "name",
                        "别名" to "alias",
                        "作者" to "author",
                        "语言" to "language",
                        "链接" to "url",
                        "示例输入" to "stdin",
                        "所有者ID" to "userID",
                        // 启用拓展功能
                        "隐藏链接" to "hide",
                        "仅限群聊" to "groupOnly",
                        "辅助文件" to "util",
                        "输出格式" to "format",
                        "数据存储" to "storage",
                        "图片base64" to "base64",
                    )
                    option = paraMap.getOrDefault(option, option)
                    if (paraMap.values.contains(option).not()) {
                        sendQuoteReply(
                            "❓ 未知的配置项：$option\n" +
                            "---基础信息修改---\n" +
                            "name（名称）\n" +
                            "alias（别名）\n" +
                            "author（作者）\n" +
                            "language（语言）\n" +
                            "url（链接）\n" +
                            "stdin（示例输入）\n" +
                            "userID（所有者ID）\n" +
                            "---启用拓展功能---\n" +
                            "hide（隐藏链接）\n" +
                            "groupOnly（仅限群聊）\n" +
                            "util（辅助文件）\n" +
                            "format（输出格式）\n" +
                            "storage（数据存储）\n" +
                            "base64（图片base64）"
                        )
                        return
                    }
                    if (option == "format" && args.size > 5) {
                        sendQuoteReply("修改失败：format中仅能包含两个参数（输出格式，图片宽度/配置存储）")
                        return
                    }
                    if (option != "stdin" && option != "format" && args.size > 4) {
                        sendQuoteReply("修改失败：$option 参数中不能包含空格！")
                        return
                    }
                    if (option != "stdin" && option != "util" && option != "alias" && content.isEmpty()) {
                        sendQuoteReply("修改失败：修改后的值为空！")
                        return
                    }
                    if (option == "name" || option == "alias") {
                        if (PastebinData.pastebin.contains(content)) {
                            sendQuoteReply("修改失败：名称 $content 已存在")
                            return
                        }
                        if (PastebinData.alias.contains(content)) {
                            sendQuoteReply("修改失败：名称 $content 已存在于别名中")
                            return
                        }
                    }
                    if (option == "url") {
                        content = PastebinUrlHelper.extractUrl(content)
                        if (!checkUrl(content)) {
                            sendQuoteReply(
                                "修改失败：无效的链接 $content\n" +
                                "🔗 支持的URL格式如下方所示：\n" +
                                supportedUrls.joinToString(separator = "") { "${it.url}...\n" }
                            )
                            return
                        }
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
                            PastebinStorage.storage.remove(name)?.let {
                                PastebinStorage.storage[content] = it
                            }
                            // 转移缓存数据
                            CodeCache.CodeCache.remove(name)?.let {
                                CodeCache.CodeCache[content] = it
                            }
                            // 转移统计数据
                            ExtraData.statistics.remove(name)?.let {
                                ExtraData.statistics[content] = it
                            }
                        }
                        "alias"-> {
                            PastebinData.alias.entries.removeIf { it.value == name }
                            if (content.isNotEmpty()) {
                                PastebinData.alias[content] = name
                            }
                        }
                        "userID"-> {
                            if (content.toLongOrNull() == null) {
                                sendQuoteReply("转移失败：输入的 userID 不是整数")
                                return
                            }
                            if (getNickname(content.toLong()) == null) {
                                sendQuoteReply("转移失败：无法找到目标用户 $content，转移对象必须为机器人好友或本群成员")
                                return
                            }
                            if (!isAdmin) {
                                requestUserConfirmation(
                                    userID, args.content,
                                    " +++⚠️ 危险操作警告 ⚠️+++\n" +
                                    "您正在转移项目 $name 的所有权，转移前请确保您已知晓：\n" +
                                    "- 转移后您将*完全失去*项目管理权\n" +
                                    "- 此操作*不可撤销*\n" +
                                    "- 请务必确认目标用户ID准确且有效\n" +
                                    "\n" +
                                    "如您确认无误，请再次执行转移指令以完成操作"
                                ) ?: return
                            }

                            PastebinData.pastebin[name]?.set("userID", content)
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
                                    sendQuoteReply("无效的配置项：请设置 开启/关闭 隐藏链接功能")
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
                                    sendQuoteReply("无效的配置项：请设置 开启/关闭 仅限群聊执行功能")
                                    return
                                }
                            }
                        }
                        "util"-> {
                            val files = File(MiraiCompilerFramework.utilsFolder).listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
                            if (content.isEmpty()) {
                                PastebinData.pastebin[name]?.remove("util")
                            } else {
                                if (files.contains(content).not()) {
                                    sendQuoteReply("未找到此文件：请检查文件名\n辅助文件列表：\n${files.joinToString("\n")}")
                                    return
                                }
                                PastebinData.pastebin[name]?.set("util", content)
                            }
                        }
                        "format"-> {
                            val alias = mapOf(
                                "md" to "markdown",
                                "html" to "markdown",
                                "latex" to "LaTeX",
                                "JSON" to "json",
                                "audio" to "Audio",
                            )
                            val paras = content.split(" ")
                            val format = alias.getOrDefault(paras[0], paras[0])
                            content = format
                            if (MiraiCompilerFramework.supportedFormats.contains(format).not()) {
                                sendQuoteReply(
                                        "❌ 无效的输出格式：$format\n" +
                                        "仅支持输出：\n" +
                                        "·text（纯文本）\n" +
                                        "·markdown（md/html转图片）\n" +
                                        "·base64（base64自定义格式输出）\n" +
                                        "·image（链接或路径直接发图）\n" +
                                        "·LaTeX（LaTeX转图片）\n" +
                                        "·json（自定义输出格式、图片宽度，MessageChain和MultipleMessage需使用此格式）\n" +
                                        "·ForwardMessage（使用json生成包含多条文字/图片消息的转发消息）\n" +
                                        "·Audio（使用json生成文字转语音消息）"
                                )
                                return
                            }
                            if (format == "ForwardMessage" && !PastebinConfig.enable_ForwardMessage) {
                                sendQuoteReply("当前未开启转发消息，无法使用此功能！")
                                return
                            }
                            if (format == "text") {
                                PastebinData.pastebin[name]?.remove("format")
                            } else {
                                PastebinData.pastebin[name]?.set("format", format)
                            }
                            if (format == "markdown") {
                                val width = paras.getOrNull(1)
                                if (width != null) {
                                    if (width.toIntOrNull() == null) {
                                        sendQuoteReply("修改失败：宽度只能是int型数字")
                                        return
                                    }
                                    content = "$format（宽度：$width）"
                                    PastebinData.pastebin[name]?.set("width", width)
                                } else {
                                    PastebinData.pastebin[name]?.remove("width")
                                }
                            } else {
                                PastebinData.pastebin[name]?.remove("width")
                                when (paras.getOrNull(1)?.lowercase()) {
                                    in arrayListOf("enable","on","true","开启")-> {
                                        content = "$format（开启存储）"
                                        PastebinData.pastebin[name]?.set("storage", "true")
                                    }
                                    in arrayListOf("disable","off","false","关闭")-> {
                                        content = "$format（关闭存储）"
                                        PastebinData.pastebin[name]?.remove("storage")
                                        PastebinStorage.storage.remove(name)
                                    }
                                    in arrayListOf("clear","清空")-> {
                                        content = "$format（清空存储）"
                                        PastebinStorage.storage.remove(name)
                                    }
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
                                    if (PastebinData.pastebin[name]?.get("base64") != null) {
                                        content += "（关闭图片base64）"
                                        PastebinData.pastebin[name]?.remove("base64")
                                    }
                                    PastebinStorage.storage.remove(name)
                                }
                                in arrayListOf("clear","清空")-> {
                                    content = "清空"
                                    PastebinStorage.storage.remove(name)
                                }
                                else-> {
                                    sendQuoteReply("无效的配置项：请设置 开启/关闭/清空 存储功能")
                                    return
                                }
                            }
                        }
                        "base64"-> {
                            when (content) {
                                in arrayListOf("enable","on","true","开启")-> {
                                    if (PastebinData.pastebin[name]?.get("storage") != "true") {
                                        sendQuoteReply("启用失败：此项目未开启存储功能，无法使用输入图片base64功能")
                                        return
                                    }
                                    content = "开启"
                                    PastebinData.pastebin[name]?.set("base64", "true")
                                }
                                in arrayListOf("disable","off","false","关闭")-> {
                                    content = "关闭"
                                    PastebinData.pastebin[name]?.remove("base64")
                                    PastebinStorage.storage.remove(name)
                                }
                                else-> {
                                    sendQuoteReply("无效的配置项：请设置 开启/关闭 输入图片转base64")
                                    return
                                }
                            }
                        }
                        else -> {
                            if (option == "url" && CodeCache.CodeCache.contains(name)) {
                                additionalOutput = "🔗 源代码URL被修改，代码缓存已清除，下次执行时需重新获取代码\n"
                                CodeCache.CodeCache.remove(name)
                            }
                            PastebinData.pastebin[name]?.set(option, content)
                        }
                    }
                    if (option == "hide") {
                        sendQuoteReply("${additionalOutput}成功将 $name 的源代码标记为 $content")
                    } else if (option == "groupOnly") {
                        sendQuoteReply("${additionalOutput}成功将 $name 标记为 $content")
                    } else if (option == "userID") {
                        sendQuoteReply("${additionalOutput}成功将 $name 的所有权转移至 $content")
                    } else if (option == "url" && PastebinConfig.enable_censor) {
                        if (isAdmin) {
                            sendQuoteReply("${additionalOutput}$name 的 url 参数的修改已生效")
                        } else {
                            PastebinData.censorList.add(name)
                            sendQuoteReply("${additionalOutput}$name 的 url 参数已修改，已自动提交新审核，在审核期间本条链接暂时无法运行，望理解")
                        }
                    } else {
                        sendQuoteReply("${additionalOutput}成功将 $name 的 $option 参数修改为 $content")
                    }
                    PastebinData.save()
                    PastebinStorage.save()
                    CodeCache.save()
                    ExtraData.save()
                }

                "delete", "remove", "删除", "移除"-> {   // 永久删除项目
                    val name = args[1].content
                    if (PastebinData.pastebin.contains(name).not()) {
                        sendQuoteReply("删除失败：名称 $name 不存在")
                        return
                    }
                    val ownerID = PastebinData.pastebin[name]?.get("userID")
                    val isOwner = userID.toString() == ownerID
                    if (!isOwner && !isAdmin) {
                        sendQuoteReply("无权删除此项目，如需删除请联系所有者：$ownerID。如果您认为此条记录存在不合适的内容或其他问题，请联系指令管理员")
                        return
                    }
                    val storageMode = PastebinData.pastebin[name]?.get("storage") == "true"
                    val linkedBuckets = bucketIDsToNames(linkedBucketID(name))
                    requestUserConfirmation(userID, args.content,
                        " +++🛑 高危操作警告 🛑+++\n" +
                        "您正在删除项目 $name，删除前请确保您已知晓：\n" +
                        "- 项目所有有关数据都将*删除*\n" +
                        "- 删除操作*不可恢复*\n" +
                        "- 项目的统计数据将被*清空*\n" +
                        (if (storageMode) "⚠️ 此项目开启了存储功能，删除后所有存储数据将*永久丢失*\n" else "") +
                        (if (linkedBuckets.isNotEmpty()) "⚠️ 此项目关联了存储库，删除后以下存储库将自动*解除关联*：$linkedBuckets\n" else "") +
                        "\n" +
                        "如您确认无误，请再次执行删除指令以完成操作"
                    ) ?: return

                    PastebinData.alias.entries.removeIf { it.value == name }
                    PastebinData.hiddenUrl.remove(name)
                    PastebinData.groupOnly.remove(name)
                    PastebinData.censorList.remove(name)
                    PastebinData.pastebin.remove(name)
                    PastebinData.save()
                    PastebinStorage.storage.remove(name)
                    PastebinStorage.save()
                    CodeCache.CodeCache.remove(name)
                    CodeCache.save()
                    ExtraData.statistics.remove(name)
                    ExtraData.save()
                    removeProjectFromBucket(name)
                    PastebinBucket.save()
                    sendQuoteReply("删除项目 $name 成功！")
                }

                "upload", "上传"-> {   // 已迁移至 CommandImage
                    sendQuoteReply("[已废弃] 上传图片功能已迁移至独立指令，请使用「${commandPrefix}img upload <图片名称> <【图片/URL】>」来上传图片")
                }

                "storage", "存储"-> {   // 查询存储数据
                    val name = PastebinData.alias[args[1].content] ?: args[1].content
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
                    val storage = PastebinStorage.storage[name]
                    val ownerID = PastebinData.pastebin[name]?.get("userID")
                    val isOwner = userID.toString() == ownerID
                    if (!isOwner && !isAdmin) {
                        sendQuoteReply("【查询名称】$name\n【用户数量】${storage?.size?.minus(1)}\n无权查看数据内容，仅所有者可查看存储数据详细内容")
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
                        MailService.sendStorageMail(this, output, userID, name)
                        return
                    }

                    if (!PastebinConfig.enable_ForwardMessage) {
                        sendQuoteReply("当前未开启转发消息，仅能通过邮件查询存储数据")
                        return
                    }
                    val id = try {
                        if (args[2].content == "global" || args[2].content == "全局") 0
                        else args[2].content.toLong()
                    } catch (_: Exception) {
                        -1
                    }
                    try {
                        val forward = buildForwardMessage(subject!!) {
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
                                subject!!.bot named "存储查询" says "【查询名称】$name\n【用户数量】${storage?.size?.minus(1)}"
                                if (storage != null) {
                                    for (qq in storage.keys) {
                                        val content = if (storage[qq]!!.length <= 10000) storage[qq]
                                        else "[内容过长] 数据长度：${storage[qq]?.length}，如需查看完整内容请使用指令\n\n${commandPrefix}pb storage $name mail\n\n将结果发送邮件至您的邮箱"
                                        if (qq == 0L)
                                            subject!!.bot named "全局存储" says "【全局存储[global]】\n$content"
                                        else
                                            subject!!.bot named "用户存储" says "【用户存储[$qq]】\n$content"
                                    }
                                } else {
                                    subject!!.bot named "查询失败" says "[错误] 查询失败：存储数据中不存在此名称"
                                }
                            } else {
                                subject!!.bot named "存储查询" says "【查询名称】$name\n【查询ID】$id"
                                val idStorage = storage?.get(id)
                                subject!!.bot named "存储查询" says
                                        if (idStorage == null) "[错误] 查询失败：存储数据中不存在此名称或userID"
                                        else if (idStorage.isEmpty()) "[警告] 查询成功，但查询的存储数据为空"
                                        else idStorage
                            }
                        }
                        sendMessage(forward)
                    } catch (_: MessageTooLargeException) {
                        val length = if (id == -1L) "汇总存储查询总长度超出限制，用户数量：${storage?.size?.minus(1)}，请尝试添加编号查询指定内容"
                                else "数据长度：${storage?.get(id)?.length}"
                        sendQuoteReply("[内容过长] $length。如需查看完整内容请使用指令\n" +
                                "${commandPrefix}pb storage $name mail\n将结果发送邮件至您的邮箱")
                    } catch (e: Exception) {
                        logger.warning(e)
                        sendQuoteReply("[转发消息错误]\n生成或发送转发消息时发生错误，请联系管理员查看后台，简要错误信息：${e.message}")
                    }
                }

                "export", "导出"-> {   // 将项目代码缓存导出为临时链接
                    if (PastebinConfig.enable_censor) {
                        sendQuoteReply("审核功能已开启，导出功能被禁用，如有需求请联系管理员")
                        return
                    }
                    val name = PastebinData.alias[args[1].content] ?: args[1].content
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
                    if (PastebinData.hiddenUrl.contains(name)) {
                        sendQuoteReply("导出失败：$name 的源代码链接被标记为隐藏，无法使用导出功能，请联系项目作者")
                        return
                    }

                    val exportCode = CodeCache.CodeCache[name]
                    if (exportCode == null) {
                        sendQuoteReply("导出失败：$name 的代码缓存为空或项目链接类型不支持缓存功能")
                        return
                    }

                    val url =  try {
                        PastebinUrlHelper.pasteToHastebin(exportCode)
                    } catch (e: Exception) {
                        when (e) {
                            is ConnectException,
                            is HttpUtil.HttpException ->
                                sendQuoteReply("[API服务异常]\n原因：${e.message}")

                            else -> {
                                logger.warning(e)
                                sendQuoteReply(
                                    "[导出代码失败]\n" +
                                    "报错类别：${e::class.simpleName}\n" +
                                    "报错信息：${trimToMaxLength(e.message.toString(), ERROR_MSG_MAX_LENGTH).first}"
                                )
                            }
                        }
                        return
                    }
                    sendQuoteReply("已成功将 $name 的源代码从缓存导出至 Hastebin，链接如下（有效期 30 天）：\n$url")
                }

                // admin指令
                "handle", "处理"-> {   // 处理添加和修改申请（审核功能）
                    if (!isAdmin) throw PermissionDeniedException()
                    val name = args[1].content
                    var option = args[2].content
                    val remark = args.getOrElse(3) { "无" }.toString()
                    if (PastebinData.censorList.contains(name).not()) {
                        sendQuoteReply("操作失败：$name 不在审核列表中")
                        return
                    }
                    if (arrayListOf("accept","同意").contains(option)) {
                        option = "同意"
                        PastebinData.censorList.remove(name)
                    } else if (arrayListOf("refuse","拒绝").contains(option)) {
                        option = "拒绝"
                        PastebinData.censorList.remove(name)
                    } else {
                        sendQuoteReply("[操作无效] 指令参数错误")
                        return
                    }
                    val reply = "申请处理成功！\n操作：$option\n备注：$remark\n" +
                        try {
                            val noticeApply = "【申请处理通知】\n" +
                                            "申请内容：pastebin运行链接\n" +
                                            "结果：$option\n" +
                                            "备注：$remark"
                            bot?.getFriendOrFail(PastebinData.pastebin[name]!!["userID"]!!.toLong())!!.sendMessage(noticeApply)   // 抄送结果至申请人
                            "已将结果发送至申请人"
                        } catch (e: Exception) {
                            logger.warning(e)
                            "发送消息至申请人时出现错误，可能因为机器人权限不足或未找到对象，详细信息请查看后台"
                        }
                    if (option == "拒绝") {
                        PastebinData.pastebin.remove(name)
                    }
                    sendQuoteReply(reply)   // 回复指令发出者
                }

                "black", "黑名单"-> {   // 添加/移除黑名单
                    if (!isAdmin) throw PermissionDeniedException()
                    try {
                        val qq = args[1].content.toLong()
                        if (ExtraData.BlackList.contains(qq)) {
                            ExtraData.BlackList.remove(qq)
                            sendQuoteReply("已将 $qq 移出黑名单")
                        } else {
                            ExtraData.BlackList.add(qq)
                            sendQuoteReply("已将 $qq 移入黑名单")
                        }
                        ExtraData.save()
                    } catch (_: IndexOutOfBoundsException) {
                        var blackListInfo = "·代码执行黑名单："
                        for (black in ExtraData.BlackList) {
                            blackListInfo += "\n$black"
                        }
                        sendQuoteReply(blackListInfo)
                    }
                }

                "reload", "重载"-> {   // 重载配置和数据文件
                    if (!isAdmin) throw PermissionDeniedException()
                    try {
                        MiraiCompilerFramework.reloadConfig()
                        MiraiCompilerFramework.reloadData()
                        sendQuoteReply("配置及数据重载成功")
                    } catch (e: Exception) {
                        logger.warning(e)
                        sendQuoteReply("出现错误：${e.message}")
                    }
                }

                else-> {
                    sendQuoteReply("[参数不匹配]\n请使用「${commandPrefix}pb help」来查看指令帮助")
                }
            }
        } catch (_: PermissionDeniedException) {
            sendQuoteReply("[参数不匹配]\n请使用「${commandPrefix}pb help」来查看指令帮助")
        } catch (_: IndexOutOfBoundsException) {
            sendQuoteReply("[参数不足]\n请使用「${commandPrefix}pb help」来查看指令帮助")
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply("[指令执行未知错误]\n请联系管理员查看后台：${e::class.simpleName}(${e.message})")
        }
    }
}
