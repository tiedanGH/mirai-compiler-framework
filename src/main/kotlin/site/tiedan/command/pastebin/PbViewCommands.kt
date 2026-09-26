package site.tiedan.command.pastebin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.isNotConsole
import net.mamoe.mirai.containsFriend
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework.Command
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.uploadTempImage
import site.tiedan.command.CommandBucket
import site.tiedan.config.PastebinConfig
import site.tiedan.config.PlatformConfig
import site.tiedan.core.CodeCacheManager
import site.tiedan.core.StorageManager
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.format.MarkdownImageGenerator
import site.tiedan.module.ExecutionLock
import site.tiedan.module.TagManager
import site.tiedan.utils.FuzzySearch
import site.tiedan.utils.PastebinUrlHelper
import site.tiedan.utils.PastebinUrlHelper.discontinuedUrls
import site.tiedan.utils.PastebinUrlHelper.supportedUrls
import kotlin.math.ceil

/*
 * # PB查看运行类指令
 *
 * @author tiedanGH
 */

/** 查看完整列表 */
internal suspend fun CommandSender.pbList(ctx: PbContext) {
    val args = ctx.args
    val commandPbList = arrayOf(
        Command("pb list [all]", "pb 列表 [全部]", "图片输出完整列表", 1),
        Command("pb list forward [作者]", "pb 列表 转发 [作者]", "转发消息输出完整列表", 1),

        Command("pb list run [作者名]", "pb 列表 次数 [作者名]", "根据总执行次数排序", 2),
        Command("pb list heat [作者名]", "pb 列表 热度 [作者名]", "根据热度排序", 2),

        Command("pb list search [项目名] [作者名] [语言] [输出格式]", "pb 列表 搜索 [项目名] [作者名] [语言] [输出格式]", "根据条件搜索项目（输入 null 来跳过某一项）", 3),
        Command("pb list author <作者名>", "pb 列表 作者 <作者名>", "根据作者关键词筛选", 3),
        Command("pb list tag <标签>", "pb 列表 标签 <标签>", "根据标签筛选", 3),
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
        "tag", "标签",
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
                in arrayOf("tag", "标签") ->
                    MarkdownImageGenerator.PastebinListFilter(tag = params.getOrNull(0))
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
            if (!markdownResult.success || markdownResult.file == null) {
                sendQuoteReply(markdownResult.message)
                return
            }
            val image = subject?.uploadTempImage(markdownResult.file)
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
                                "缓存数量：${CodeCacheManager.count()}",
                                "存储数量：${StorageManager.projectCount()}"
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

/** 查看数据具体参数 */
internal suspend fun CommandSender.pbInfo(ctx: PbContext) {
    val args = ctx.args
    val platform = ctx.platform
    val userID = ctx.userID
    val isAdmin = ctx.isAdmin
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
    val isOwner = userID == ownerID
    val showAll = args.getOrNull(2)?.content == "show" && (isOwner || isAdmin)
    val alias = PastebinData.alias.entries.find { it.value == name }?.key
    val info = buildString {
        if (showAll) appendLine("---[完整信息预览]---")
        append("名称：$name")
        alias?.let { append("（$it）") }
        appendLine()
        TagManager.projectTags(name).takeIf { it.isNotEmpty() }
            ?.let { appendLine("🏷️ 标签：${it.joinToString(" ")}") }
        ExecutionLock.of(name)?.let { appendLine(it.desc) }
        appendLine("作者：${data["author"]}")
        if (showAll) {
            appendLine("userID: ${data["userID"]}")
            val collaborators = data["collaborators"]
            if (collaborators.isNullOrEmpty().not())
                appendLine("协作者: $collaborators")
        }
        appendLine("语言：${data["language"]}")
        val url = data["url"].orEmpty()
        append("源代码URL：")
        appendLine(
            when {
                PastebinConfig.enable_censor ->
                    "审核功能已开启，链接无法查看，如有需求请联系管理员"
                PastebinData.hiddenUrl.contains(name) && !showAll ->
                    "链接被隐藏"
                // 停服网站链接无法访问，引导使用export导出缓存
                PastebinUrlHelper.isDiscontinued(url) ->
                    if (CodeCacheManager.contains(name)) {
                        "\n原网站已停服，请导出缓存\n" +
                        "🔗 ${commandPrefix}pb export $name"
                    } else {
                        "\n原网站已停止服务，且本地无代码缓存，此项目已无法执行"
                    }
                else ->
                    "\n$url"
            }
        )
        data["util"]?.let { appendLine("辅助文件：$it") }
        data["format"]?.let { fmt ->
            appendLine("输出格式：$fmt")
            data["width"]?.let { w -> appendLine("图片宽度：$w") }
        }
        if (data["storage"] == "true") {
            val linkedBuckets = CommandBucket.bucketIdsToNames(CommandBucket.linkedBucketId(name))
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
        // 根据不同的平台输出不同的快捷前缀
        val platformPrefix = PlatformConfig.platforms.values
            .firstOrNull { it["platform"] == platform }
            ?.get("quick_prefix")
            ?.takeIf { it.isNotEmpty() }
        if (platformPrefix.isNullOrEmpty().not()) {
            sendMessage("${platformPrefix}${alias ?: name} ${PastebinData.pastebin[name]?.get("stdin")}")
        } else if (PastebinConfig.QUICK_PREFIX.isNotEmpty()) {
            sendMessage("${PastebinConfig.QUICK_PREFIX.first()}${alias ?: name} ${PastebinData.pastebin[name]?.get("stdin")}")
        } else {
            sendMessage("#run ${alias ?: name} ${PastebinData.pastebin[name]?.get("stdin")}")
        }
    }
}

/** 支持粘贴代码的网站 */
internal suspend fun CommandSender.pbSupport() {
    sendQuoteReply(
        "🌐 目前pb支持粘贴代码的网站：\n" +
        supportedUrls.joinToString(separator = "\n", postfix = "\n") { it.website } +
        "💡 如有更多好用的网站欢迎推荐\n\n" +
        "⛔ 已停止服务：\n" +
        discontinuedUrls.joinToString(separator = "\n") { it.website }
    )
}

/** 允许私信主动消息 */
internal suspend fun CommandSender.pbPrivate(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val numID = ctx.numID
    if (bot?.containsFriend(numID) != true && isNotConsole()) {
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
            "如需关闭请使用：${commandPrefix}pb private off"
        )
    } else {
        sendQuoteReply(
            "[成功] 您已设置在每日 ${start}:00 ~ ${end}:59 之间允许接收私信主动消息\n" +
            "如需关闭请使用：${commandPrefix}pb private off"
        )
    }
}
