package site.tiedan.command

import kotlinx.coroutines.CancellationException
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.data.findIsInstance
import net.mamoe.mirai.message.data.toMessageChain
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.CONSOLE_USER_ID
import site.tiedan.MiraiCompilerFramework.Command
import site.tiedan.MiraiCompilerFramework.getPlatform
import site.tiedan.MiraiCompilerFramework.getQuickPrefix
import site.tiedan.MiraiCompilerFramework.getUserPlatformID
import site.tiedan.MiraiCompilerFramework.isBotEnabled
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.pendingCommand
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.uploadTempImage
import site.tiedan.MiraiCompilerFramework.userThreadLimit
import site.tiedan.command.CommandRun.inputAfter
import site.tiedan.command.CommandRun.queryImageUrls
import site.tiedan.core.CommandSetExecutor.NO_EXTRA_INPUT
import site.tiedan.core.CommandSetExecutor.executeCommandSet
import site.tiedan.core.CommandSetExecutor.executeFavorite
import site.tiedan.data.PastebinData
import site.tiedan.data.dao.FavoriteDao
import site.tiedan.format.MarkdownImageGenerator
import site.tiedan.module.FavoriteManager
import site.tiedan.module.FavoriteShare
import site.tiedan.utils.FuzzySearch

/**
 * # 个人收藏指令
 *
 * @author tiedanGH
 */
object CommandFavorite : RawCommand(
    owner = MiraiCompilerFramework,
    primaryName = "favorite",
    secondaryNames = arrayOf("f", "fav", "收藏"),
    description = "个人收藏与指令集",
    usage = "${commandPrefix}f help"
) {
    private val commandList = arrayOf(
        Command("f [list]", "收藏 [列表]", "查看收藏列表", 1),
        Command("f add <项目1> [项目2]...", "收藏 添加 <项目1> [项目2]...", "收藏项目", 1),
        Command("f rm <序号/项目/收藏别名>...", "收藏 移除 <序号/项目/收藏别名>...", "取消收藏", 1),
        Command("f alias <序号/项目/收藏别名> [别名]", "收藏 别名 <序号/项目/收藏别名> [别名]", "设置收藏别名（留空清除）", 1),
        Command("f run <序号/收藏别名> [输入]", "收藏 执行 <序号/收藏别名> [输入]", "执行收藏的项目", 1),

        Command("f run <指令集>", "收藏 执行 <指令集>", "执行指令集全部项目", 2),
        Command("f set [指令集]", "收藏 指令集 [指令集]", "查看指令集", 2),
        Command("f set <指令集> add <项目> [输入]", "收藏 指令集 <指令集> 添加 <项目> [输入]", "追加指令（自动创建指令集）", 2),
        Command("f set <指令集> rm <序号>", "收藏 指令集 <指令集> 移除 <序号>", "移除一条指令", 2),
        Command("f set <指令集> edit <序号> <项目> [输入]", "收藏 指令集 <指令集> 修改 <序号> <项目> [输入]", "修改一条指令", 2),
        Command("f set <指令集> move <序号> <新序号>", "收藏 指令集 <指令集> 移动 <序号> <新序号>", "调整指令顺序", 2),
        Command("f set <指令集> rename <新名称>", "收藏 指令集 <指令集> 改名 <新名称>", "修改指令集名称", 2),
        Command("f set <指令集> delete", "收藏 指令集 <指令集> 删除", "删除整个指令集", 2),
        Command("f share <指令集>", "收藏 分享 <指令集>", "生成指令集分享码", 2),
        Command("f import <分享码> [新名称]", "收藏 导入 <分享码> [新名称]", "通过分享码导入指令集", 2),
    )

    override suspend fun CommandSender.onCommand(args: MessageChain) {

        if (!isBotEnabled(bot?.id)) return

        val userID = getUserPlatformID(this.user?.id) ?: CONSOLE_USER_ID

        if (pendingCommand[userID]?.let { it != args.content } == true) {
            pendingCommand.remove(userID)
            sendQuoteReply("指令不一致，操作已取消")
        }

        try {
            when (args.getOrNull(0)?.content ?: "list") {
                "help", "帮助"-> sendQuoteReply(buildHelp(cn = args[0].content == "帮助", firstQuickPrefix()))
                "list", "列表"-> showCard(userID)
                "add", "添加"-> addFavorites(userID, args)
                "rm", "remove", "移除"-> removeFavorites(userID, args)
                "alias", "别名"-> setAlias(userID, args)
                "run", "执行"-> run(userID, args)
                "set", "指令集"-> commandSet(userID, args)
                "share", "分享"-> shareSet(userID, args[1].content)
                "import", "导入"-> importSet(userID, args)
                else-> sendQuoteReply("[参数不匹配]\n请使用「${commandPrefix}f help」来查看指令帮助")
            }
        } catch (_: IndexOutOfBoundsException) {
            sendQuoteReply("[参数不足]\n请使用「${commandPrefix}f help」来查看指令帮助")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply("[指令执行未知错误]\n请联系管理员查看后台：${e::class.simpleName}(${e.message})")
        }
    }

    /**
     * @param quickPrefix 把快捷执行放在帮助最前面
     */
    private fun buildHelp(cn: Boolean, quickPrefix: String?): String {
        fun group(title: String, type: Int) = title + "\n" +
            commandList.filter { it.type == type }
                .joinToString("\n") { "$commandPrefix${if (cn) it.usageCN else it.usage}　${it.desc}" }
        val quick = quickPrefix?.let { "$it<收藏别名/指令集>　执行收藏项目或指令集\n" }.orEmpty()
        return quick + group("⭐ 个人收藏：", 1) + "\n" + group("📦 指令集：", 2)
    }

    /* ==================== 收藏 ==================== */

    private suspend fun CommandSender.showCard(userID: String) {
        val card = FavoriteManager.buildCard(userID, userThreadLimit)
        if (card.isEmpty) {
            sendQuoteReply(
                "您还没有任何收藏\n" +
                "⭐ 收藏项目：${commandPrefix}f add <项目1> [项目2]...\n" +
                "📦 创建指令集：${commandPrefix}f set <名称> add <项目> [输入]"
            )
            return
        }
        val footer = firstQuickPrefix()
            ?.let { "▶️ 执行：$it<收藏别名/指令集> 或 ${commandPrefix}f run <序号>" }
            ?: "▶️ 执行：${commandPrefix}f run <序号/收藏别名/指令集>"
        replyRendered(MarkdownImageGenerator.generateFavoriteCardHtml(card, footer), width = "760") {
            FavoriteManager.formatCardText(card) + "\n$footer"
        }
    }

    private suspend fun CommandSender.addFavorites(userID: String, args: MessageChain) {
        val tokens = args.drop(1).map { it.content }.filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            sendQuoteReply("[参数不足]\n${commandPrefix}f add <项目1> [项目2]...")
            return
        }
        val result = FavoriteManager.addFavorites(userID, tokens)
        sendQuoteReply(buildString {
            if (result.added.isNotEmpty()) {
                appendLine("⭐ 已收藏：" + result.added.joinToString("、") { "${it.project}（#${it.slot}）" })
            }
            if (result.duplicated.isNotEmpty()) {
                appendLine("已在收藏中：" + result.duplicated.joinToString("、"))
            }
            if (result.overflow.isNotEmpty()) {
                appendLine("收藏已达上限 ${FavoriteManager.MAX_FAVORITES} 个，未能收藏：" + result.overflow.joinToString("、"))
            }
            if (result.unknown.isNotEmpty()) {
                appendLine("未知的项目：" + result.unknown.joinToString("、"))
                // 只有一个未知项目时给出模糊匹配
                result.unknown.singleOrNull()?.let { name ->
                    val fuzzy = FuzzySearch.fuzzyFind(PastebinData.pastebin, name)
                    if (fuzzy.isNotEmpty()) appendLine("🔍 模糊匹配结果->\n" + fuzzy.take(20).joinToString(" "))
                }
            }
        }.trimEnd())
    }

    private suspend fun CommandSender.removeFavorites(userID: String, args: MessageChain) {
        val tokens = args.drop(1).map { it.content }.filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            sendQuoteReply("[参数不足]\n${commandPrefix}f rm <序号/项目/收藏别名>...")
            return
        }
        val result = FavoriteManager.removeFavorites(userID, tokens)
        sendQuoteReply(buildString {
            if (result.removed.isNotEmpty()) {
                appendLine("已取消收藏：" + result.removed.joinToString("、") { "#${it.slot} ${it.project}" })
            }
            if (result.notFound.isNotEmpty()) {
                appendLine("未找到收藏：" + result.notFound.joinToString("、"))
                appendLine("请使用「${commandPrefix}收藏」查看收藏序号")
            }
        }.trimEnd())
    }

    private suspend fun CommandSender.setAlias(userID: String, args: MessageChain) {
        val token = args[1].content
        if (args.size > 3) {
            sendQuoteReply("收藏别名中不能包含空格")
            return
        }
        // 只有留空才表示清除，「无」之类的文字都按新别名处理
        val alias = args.getOrNull(2)?.content?.takeIf { it.isNotEmpty() }
        when (val result = FavoriteManager.setAlias(userID, token, alias)) {
            is FavoriteManager.AliasResult.Rejected -> sendQuoteReply("设置失败：${result.reason}")
            is FavoriteManager.AliasResult.NotFound -> sendQuoteReply(
                (if (alias == null) "未找到收藏：$token" else "未找到项目：$token") +
                "\n请使用「${commandPrefix}收藏」查看收藏序号"
            )
            is FavoriteManager.AliasResult.Updated -> {
                val target = "#${result.favorite.slot} ${result.favorite.project}"
                sendQuoteReply(
                    when {
                        result.alias == null -> "已清除 $target 的收藏别名"
                        result.added -> "⭐ 已收藏 ${result.favorite.project}（#${result.favorite.slot}）\n" +
                            "已将收藏别名设为「${result.alias}」\n▶️ 执行：${runHint(result.alias)} [输入]"
                        else -> "已将 $target 的收藏别名设为「${result.alias}」\n▶️ 执行：${runHint(result.alias)} [输入]"
                    }
                )
            }
        }
    }

    /**
     * 执行：纯数字按收藏序号，然后先匹配收藏别名，再匹配指令集
     */
    private suspend fun CommandSender.run(userID: String, args: MessageChain) {
        val target = args[1].content
        val favorites = FavoriteManager.favorites(userID)
        val favorite = if (FavoriteManager.isIndexToken(target)) {
            val slot = target.toIntOrNull()
            favorites.find { it.slot == slot }
                ?: return sendQuoteReply("收藏序号 $target 没有对应的收藏\n请使用「${commandPrefix}收藏」查看收藏序号")
        } else {
            favorites.find { it.alias == target }
        }
        if (favorite != null) {
            runFavorite(favorite, args)
            return
        }

        val commands = FavoriteManager.commandSet(userID, target)
        if (commands.isEmpty()) {
            val aliases = favorites.mapNotNull { it.alias }
            val sets = FavoriteManager.setNames(userID)
            sendQuoteReply(buildString {
                appendLine("未找到收藏别名或指令集：$target")
                if (aliases.isNotEmpty()) appendLine("⭐ 收藏别名：${aliases.joinToString("、")}")
                if (sets.isNotEmpty()) appendLine("📦 指令集：${sets.joinToString("、")}")
                append("也可以按收藏序号执行，请使用「${commandPrefix}收藏」查看")
            })
            return
        }
        if (args.size > 2) {
            sendQuoteReply(NO_EXTRA_INPUT)
            return
        }
        executeCommandSet(userID, target, commands)
    }

    /** 执行单个收藏：输入与图片的处理和 run 指令一致 */
    private suspend fun CommandSender.runFavorite(favorite: FavoriteDao.Favorite, args: MessageChain) {
        val userInput = inputAfter(args, 2)
        val imageUrls = args.drop(2).toMessageChain().queryImageUrls()
        if (this is CommandSenderOnMessage<*> && fromEvent.message[QuoteReply.Key] != null) {
            fromEvent.message.findIsInstance<QuoteReply>()
                ?.source?.originalMessage?.queryImageUrls()
                ?.let { imageUrls.addAll(0, it) }
        }
        executeFavorite(favorite, userInput, imageUrls)
    }

    /* ==================== 指令集 ==================== */

    private suspend fun CommandSender.commandSet(userID: String, args: MessageChain) {
        val setName = args.getOrNull(1)?.content
        if (setName == null) {
            listSets(userID)
            return
        }
        when (args.getOrNull(2)?.content) {
            null-> showSet(userID, setName)
            "add", "添加"-> addCommand(userID, setName, args)
            "rm", "remove", "移除"-> removeCommand(userID, setName, args)
            "edit", "修改"-> editCommand(userID, setName, args)
            "move", "移动"-> moveCommand(userID, setName, args)
            "rename", "改名"-> renameSet(userID, setName, args)
            "delete", "删除"-> deleteSet(userID, setName)
            else-> sendQuoteReply("[参数不匹配]\n请使用「${commandPrefix}f help」来查看指令帮助")
        }
    }

    private suspend fun CommandSender.listSets(userID: String) {
        val sets = FavoriteManager.commandSets(userID)
        if (sets.isEmpty()) {
            sendQuoteReply("您还没有指令集\n📦 创建指令集：${commandPrefix}f set <名称> add <项目> [输入]")
            return
        }
        sendQuoteReply(
            "📦 我的指令集（${sets.size}/${FavoriteManager.MAX_SETS}）\n" +
            sets.entries.joinToString("\n") { (name, commands) -> "· $name（${commands.size}/$userThreadLimit 条）" } +
            "\n🔍 查看详情：${commandPrefix}f set <名称>"
        )
    }

    /**
     * 以图片展示指令集详情
     * @param caption 附在图片前的说明
     */
    private suspend fun CommandSender.showSet(userID: String, setName: String, caption: String? = null) {
        val commands = FavoriteManager.commandSet(userID, setName)
        if (commands.isEmpty()) {
            sendQuoteReply(unknownSetMessage(userID, setName))
            return
        }
        val detail = FavoriteManager.buildSetDetail(setName, commands, userThreadLimit)
        val footer = "▶️ 执行：${runHint(setName)}"
        replyRendered(MarkdownImageGenerator.generateCommandSetHtml(detail, footer), width = "640", caption) {
            FavoriteManager.formatSetDetailText(detail) + "\n$footer"
        }
    }

    /**
     * 解析一条指令：第 [projectAt] 个参数为项目，其后的全部内容为输入
     * @return 项目真名与输入；解析失败时已回复原因并返回 null
     */
    private suspend fun CommandSender.parseCommand(args: MessageChain, projectAt: Int): Pair<String, String>? {
        val token = args[projectAt].content
        val project = PastebinData.alias[token] ?: token
        if (project !in PastebinData.pastebin) {
            val fuzzy = FuzzySearch.fuzzyFind(PastebinData.pastebin, project)
            val suggestion = if (fuzzy.isNotEmpty()) "\n🔍 模糊匹配结果->\n" + fuzzy.take(20).joinToString(" ") else ""
            sendQuoteReply("未知的项目：$token$suggestion")
            return null
        }
        if (args.drop(projectAt + 1).any { it is Image }) {
            sendQuoteReply("指令集中的输入不支持包含图片")
            return null
        }
        val input = inputAfter(args, projectAt + 1)
        if (input.length > FavoriteManager.MAX_INPUT_LENGTH) {
            sendQuoteReply("输入过长：单条指令的输入不能超过 ${FavoriteManager.MAX_INPUT_LENGTH} 字")
            return null
        }
        return project to input
    }

    private suspend fun CommandSender.addCommand(userID: String, setName: String, args: MessageChain) {
        val (project, input) = parseCommand(args, projectAt = 3) ?: return
        when (val result = FavoriteManager.addCommand(userID, setName, project, input, userThreadLimit)) {
            is FavoriteManager.SetAddResult.Rejected -> sendQuoteReply("添加失败：${result.reason}")
            is FavoriteManager.SetAddResult.Added -> sendQuoteReply(buildString {
                if (result.created) appendLine("📦 已创建指令集「$setName」")
                append("已添加第 ${result.size} 条指令：$project")
                if (input.isNotEmpty()) append("：${FavoriteManager.inputPreview(input)}")
                append("\n当前 ${result.size}/$userThreadLimit 条，执行：${runHint(setName)}")
            })
        }
    }

    private suspend fun CommandSender.removeCommand(userID: String, setName: String, args: MessageChain) {
        val index = args[3].content.toIntOrNull()
            ?: return sendQuoteReply("请填写要移除的指令序号\n🔍 查看序号：${commandPrefix}f set $setName")
        val removed = FavoriteManager.removeCommand(userID, setName, index)
        if (removed == null) {
            val exists = FavoriteManager.commandSet(userID, setName).isNotEmpty()
            sendQuoteReply(
                if (exists) "指令集「$setName」中没有第 $index 条指令\n🔍 查看序号：${commandPrefix}f set $setName"
                else unknownSetMessage(userID, setName)
            )
            return
        }
        val remaining = FavoriteManager.commandSet(userID, setName).size
        sendQuoteReply(buildString {
            append("已从指令集「$setName」移除第 $index 条指令：${removed.project}")
            if (remaining == 0) append("\n指令集已没有指令，随之删除")
        })
    }

    private suspend fun CommandSender.editCommand(userID: String, setName: String, args: MessageChain) {
        val index = args[3].content.toIntOrNull()
            ?: return sendQuoteReply("[参数不足] 请输入修改的指令序号\n🔍 查看序号：${commandPrefix}f set $setName")
        val (project, input) = parseCommand(args, projectAt = 4) ?: return
        when (val result = FavoriteManager.editCommand(userID, setName, index, project, input)) {
            is FavoriteManager.SetEditResult.NotFound -> sendQuoteReply(unknownSetMessage(userID, setName))
            is FavoriteManager.SetEditResult.OutOfRange -> sendQuoteReply(outOfRangeMessage(setName, result))
            is FavoriteManager.SetEditResult.Edited -> {
                val preview = if (input.isNotEmpty()) "：${FavoriteManager.inputPreview(input)}" else ""
                showSet(userID, setName, caption = "✏️ 已修改第 $index 条指令：$project$preview")
            }
        }
    }

    private suspend fun CommandSender.moveCommand(userID: String, setName: String, args: MessageChain) {
        val from = args[3].content.toIntOrNull()
        val to = args[4].content.toIntOrNull()
        if (from == null || to == null) {
            sendQuoteReply("[参数不足] 请输入原序号与新序号\n🔍 查看序号：${commandPrefix}f set $setName")
            return
        }
        if (from == to) {
            sendQuoteReply("移动失败：原序号与新序号相同，指令位置没有变化")
            return
        }
        when (val result = FavoriteManager.moveCommand(userID, setName, from, to)) {
            is FavoriteManager.SetEditResult.NotFound -> sendQuoteReply(unknownSetMessage(userID, setName))
            is FavoriteManager.SetEditResult.OutOfRange -> sendQuoteReply(outOfRangeMessage(setName, result))
            is FavoriteManager.SetEditResult.Edited -> showSet(
                userID, setName, caption = "↕️ 已将第 $from 条指令 ${result.before.project} 移动到第 $to 条"
            )
        }
    }

    private fun outOfRangeMessage(setName: String, result: FavoriteManager.SetEditResult.OutOfRange): String =
        "指令集「$setName」中没有第 ${result.index} 条指令（共 ${result.size} 条）\n" +
        "🔍 查看序号：${commandPrefix}f set $setName"

    private suspend fun CommandSender.renameSet(userID: String, setName: String, args: MessageChain) {
        val newName = args[3].content
        if (args.size > 4) {
            sendQuoteReply("指令集名称中不能包含空格")
            return
        }
        when (val result = FavoriteManager.renameSet(userID, setName, newName)) {
            is FavoriteManager.RenameResult.NotFound -> sendQuoteReply(unknownSetMessage(userID, setName))
            is FavoriteManager.RenameResult.Rejected -> sendQuoteReply("改名失败：${result.reason}")
            is FavoriteManager.RenameResult.Renamed -> sendQuoteReply(
                "已将指令集「$setName」改名为「$newName」（共 ${result.count} 条指令）\n" +
                "▶️ 执行：${runHint(newName)}"
            )
        }
    }

    private suspend fun CommandSender.deleteSet(userID: String, setName: String) {
        val count = FavoriteManager.deleteSet(userID, setName)
        sendQuoteReply(
            if (count == 0) unknownSetMessage(userID, setName)
            else "已删除指令集「$setName」（共 $count 条指令）"
        )
    }

    /* ==================== 分享 ==================== */

    private suspend fun CommandSender.shareSet(userID: String, setName: String) {
        val commands = FavoriteManager.commandSet(userID, setName)
        if (commands.isEmpty()) {
            sendQuoteReply(unknownSetMessage(userID, setName))
            return
        }
        val snapshot = FavoriteShare.share(userID, setName, commands.map { FavoriteShare.Item(it.project, it.input) })
        sendQuoteReply(
            "📤 分享成功：${snapshot.code}\n" +
            "24 小时内有效，且重启后失效\n" +
            "📥 一键导入「$setName」：\n" +
            "${commandPrefix}f import ${snapshot.code}"
        )
    }

    /**
     * 通过分享码导入：先预览指令内容，二次确认后导入
     */
    private suspend fun CommandSender.importSet(userID: String, args: MessageChain) {
        val code = args[1].content.uppercase()
        if (args.size > 3) {
            sendQuoteReply("指令集名称中不能包含空格")
            return
        }
        val snapshot = FavoriteShare.find(code)
        if (snapshot == null) {
            sendQuoteReply("$code 不存在或已失效\n分享码 24 小时内有效，机器人重启后失效，请联系分享人重新分享")
            return
        }
        val setName = args.getOrNull(2)?.content ?: snapshot.setName
        val (valid, missing) = snapshot.items.partition { it.project in PastebinData.pastebin }
        FavoriteManager.checkImport(userID, setName, valid.size, userThreadLimit)?.let {
            val hint = if (it.nameIssue) "\n📥 ${commandPrefix}f import $code <新名称>" else ""
            sendQuoteReply("导入失败：${it.reason}$hint")
            return
        }

        requestUserConfirmation(userID, args.content, buildString {
            appendLine("📥 即将导入「$setName」（${valid.size} 条）：")
            valid.forEachIndexed { index, item ->
                append("${index + 1}. ${item.project}")
                if (item.input.isNotEmpty()) append("：${FavoriteManager.inputPreview(item.input)}")
                appendLine()
            }
            if (missing.isNotEmpty()) {
                appendLine("跳过不存在项目：${missing.map { it.project }.distinct().joinToString("、")}")
            }
            appendLine("⚠️ 导入后指令集将以您的身份执行，会读写自己的存档")
            appendLine()
            append("请再次发送本指令完成导入")
        }) ?: return

        when (val result = FavoriteManager.importSet(userID, setName, snapshot.items, userThreadLimit)) {
            is FavoriteManager.ImportResult.Rejected -> sendQuoteReply("导入失败：${result.problem.reason}")
            is FavoriteManager.ImportResult.Imported -> {
                FavoriteShare.afterImport(code)
                sendQuoteReply(buildString {
                    append("📥 已导入指令集「$setName」（${result.size} 条）")
                    if (result.skipped.isNotEmpty()) append("\n跳过不存在项目：${result.skipped.joinToString("、")}")
                    append("\n▶️ 执行：${runHint(setName)}")
                })
            }
        }
    }

    private fun unknownSetMessage(userID: String, setName: String): String {
        val sets = FavoriteManager.setNames(userID)
        val hint = if (sets.isNotEmpty()) "📦 您的指令集：${sets.joinToString("、")}"
        else "📦 创建指令集：${commandPrefix}f set <名称> add <项目> [输入]"
        return "指令集「$setName」不存在\n$hint"
    }

    /* ==================== 工具 ==================== */

    /** 第一个快捷前缀，未设置时返回 null */
    private fun CommandSender.firstQuickPrefix(): String? =
        getQuickPrefix(getPlatform()).firstOrNull { it.isNotEmpty() }

    /**
     * 执行提示：优先使用第一个快捷前缀，未设置快捷前缀时使用收藏执行指令
     */
    private fun CommandSender.runHint(target: String): String =
        firstQuickPrefix()?.let { "$it$target" } ?: "${commandPrefix}f run $target"

    /**
     * 渲染为图片回复，渲染或上传失败时退回文字版
     * @param caption 与图片同一条消息发送的说明，放在图片前
     */
    private suspend fun CommandSender.replyRendered(html: String, width: String, caption: String? = null, fallback: () -> String) {
        val markdownResult = MarkdownImageGenerator.processMarkdown(name = null, html, width = width)
        val image = markdownResult.file?.takeIf { markdownResult.success }
            ?.let { subject?.uploadTempImage(it) }
        if (image == null) {
            markdownResult.file?.delete()
            sendQuoteReply(listOfNotNull(caption, fallback()).joinToString("\n"))
            return
        }
        sendQuoteReply(if (caption == null) image else PlainText("$caption\n") + image)
    }
}
