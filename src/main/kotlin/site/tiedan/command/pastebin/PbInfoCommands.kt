package site.tiedan.command.pastebin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.MessageTooLargeException
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.containsFriend
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework.CONSOLE_USER_ID
import site.tiedan.MiraiCompilerFramework.ERROR_MSG_MAX_LENGTH
import site.tiedan.MiraiCompilerFramework.THREADS
import site.tiedan.MiraiCompilerFramework.getUserPlatformID
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.trimToMaxLength
import site.tiedan.command.pastebin.PbCollaborator.isCollaborator
import site.tiedan.config.MailConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.core.CodeCacheManager
import site.tiedan.core.StorageManager
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.module.DataAudit
import site.tiedan.module.MailService
import site.tiedan.module.Statistics
import site.tiedan.module.StatusReport
import site.tiedan.module.TagManager
import site.tiedan.utils.FuzzySearch
import site.tiedan.utils.HttpUtil
import site.tiedan.utils.PastebinUrlHelper
import java.net.ConnectException

/*
 * # PB信息查询类指令
 *
 * @author tiedanGH
 */

/** 查看统计信息 */
internal suspend fun CommandSender.pbStats(ctx: PbContext) {
    val args = ctx.args
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
        "　【📊PB框架数据统计】　\n" +
        Statistics.getAllStatistics() + "\n" +
        Statistics.summarizeStatistics(null)
    }
    sendQuoteReply(statistics)
}

/** 查看个人信息 */
internal suspend fun CommandSender.pbProfile(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val content = args.getOrNull(1)?.content?.trim()

    val platformID = content?.let {
        if (it.startsWith("@")) {
            it.removePrefix("@").toLongOrNull()
                ?.let { id -> getUserPlatformID(id) ?: CONSOLE_USER_ID }
                ?: it
        } else {
            it
        }
    } ?: userID
    val numID = platformID.substringAfterLast("_").toLongOrNull()
    val time = ExtraData.private_allowTime[platformID]

    val reply = buildString {
        appendLine("　【个人信息】　")
        appendLine("🆔 $platformID")

        val allowStatus = when {
            numID == null || bot?.containsFriend(numID) != true -> "未添加好友"
            time == null -> "不允许"
            time.second - time.first == 23 || time.second == time.first - 1 -> "始终允许"
            else -> "${time.first}:00 ~ ${time.second}:59"
        }
        appendLine("💬 接收主动私信：$allowStatus")

        appendLine(Statistics.imageStatistics(platformID))
        appendLine(Statistics.summarizeStatistics(platformID))
    }

    sendQuoteReply(reply)
}

/** 查看标签库 / 管理员批量编辑标签库 */
internal suspend fun CommandSender.pbTag(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val isAdmin = ctx.isAdmin
    val action = args.getOrNull(1)?.content
    val rawTags = args.drop(2).joinToString(" ") { it.content }
    when (action) {
        "add", "添加", "del", "remove", "删除"-> {
            if (!isAdmin) throw PermissionDeniedException()
            val tags = TagManager.parse(rawTags)
            if (tags.isEmpty()) {
                sendQuoteReply("[参数不足] 请提供至少一个标签，多个标签用空格分隔")
                return
            }
            val invalid = TagManager.invalidTags(tags)
            if (invalid.isNotEmpty()) {
                sendQuoteReply("[参数错误] 标签长度不能超过 ${TagManager.MAX_LENGTH} 字：${invalid.joinToString(" ")}")
                return
            }
            if (action in listOf("add", "添加")) {
                val (added, existing) = TagManager.addToLibrary(tags)
                sendQuoteReply(buildString {
                    append("·🗄 PB标签库添加结果：")
                    if (added.isNotEmpty()) append("\n✅ 添加成功：${added.joinToString(" ")}")
                    if (existing.isNotEmpty()) append("\n⚠️ 已存在：${existing.joinToString(" ")}")
                })
            } else {
                val (removed, missing, affected) = TagManager.removeFromLibrary(tags)
                sendQuoteReply(buildString {
                    append("·🗄 PB标签库删除结果：")
                    if (removed.isNotEmpty()) {
                        append("\n✅ 删除成功：${removed.joinToString(" ")}")
                        if (affected > 0) append("\n♻ 已从 $affected 个项目上清理")
                    }
                    if (missing.isNotEmpty()) append("\n⚠️ 不存在：${missing.joinToString(" ")}")
                })
            }
        }

        "mark", "标记"-> {
            val rawTag = args.getOrNull(2)?.content
            val names = args.drop(3).map { it.content }.filter { it.isNotEmpty() }
            if (rawTag == null || names.isEmpty()) {
                sendQuoteReply(
                    "[参数不足] 请参考以下指令：\n" +
                    "${commandPrefix}pb tag mark <标签> <项目1> [项目2]...\n" +
                    "${commandPrefix}pb 标签 标记 <标签> <项目1> [项目2]..."
                )
                return
            }
            val tag = TagManager.normalize(rawTag)
            if (tag == null) {
                sendQuoteReply(buildString {
                    append("[无效标签] $rawTag")
                    append(TagManager.fuzzyMatchLine(listOf(rawTag)))
                    append("\n请使用「${commandPrefix}pb tag」查看可用标签")
                })
                return
            }

            val authorized = mutableListOf<String>()
            val denied = mutableListOf<String>()
            val unknown = mutableListOf<String>()
            for (name in names.map { PastebinData.alias[it] ?: it }.distinct()) {
                val owner = PastebinData.pastebin[name]?.get("userID")
                when {
                    PastebinData.pastebin.contains(name).not() -> unknown += name
                    isAdmin || userID == owner || isCollaborator(name, userID) -> authorized += name
                    else -> denied += name
                }
            }
            val (added, existing) = TagManager.addTagToProjects(tag, authorized)
            sendQuoteReply(buildString {
                append("·🏷️ 标签 $tag 标记结果：")
                if (added.isNotEmpty()) append("\n✅ 标记成功：${added.joinToString(" ")}")
                if (existing.isNotEmpty()) append("\n⚠️ 已有标签：${existing.joinToString(" ")}")
                if (denied.isNotEmpty()) append("\n🚫 无权操作：${denied.joinToString(" ")}")
                if (unknown.isNotEmpty()) append("\n❓ 未知项目：${unknown.joinToString(" ")}")
            })
        }

        null-> {
            if (TagManager.libraryCount() == 0) {
                sendQuoteReply("ℹ 标签库中暂无标签，如需添加请联系管理员")
                return
            }
            sendQuoteReply(buildString {
                appendLine("·🏷️ 可用标签（${TagManager.libraryCount()}个）：")
                appendLine(TagManager.formatTagLines())
                appendLine()
                appendLine("🔍 筛选项目：${commandPrefix}pb list tag <标签>")
                append("📌 批量标记：${commandPrefix}pb tag mark <标签> <项目1> [项目2]...")
            })
        }

        else-> {
            sendQuoteReply("[参数不匹配] 查看标签库请使用「${commandPrefix}pb tag」")
        }
    }
}

/** 查看框架运行状态 */
internal suspend fun CommandSender.pbStatus(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val isAdmin = ctx.isAdmin
    val action = args.getOrNull(1)?.content
    if (action in listOf("invalid", "失效")) {
        sendQuoteReply(invalidProjectsReport())
        return
    }
    if (action != "clean") {
        sendQuoteReply(StatusReport.generate())
        return
    }
    // 清除孤儿数据：不可逆，判据依赖 yml
    if (!isAdmin) throw PermissionDeniedException()
    val result = DataAudit.scan()
    if (result.clean) {
        sendQuoteReply("✅ 未发现孤儿数据，无需清理")
        return
    }
    requestUserConfirmation(userID, args.content,
        " +++⚠️ 不可逆操作警告 ⚠️+++\n" +
        "您正在清除下列孤儿数据，共 ${result.total} 项：\n" +
        DataAudit.format(result) + "\n" +
        "\n" +
        "- 这些数据是项目被误删或误改名后*唯一残留*的线索，清理可能会*误删正常数据*\n" +
        "- 请先确认上方名称确实为废弃项目，必要时先进行备份\n" +
        "\n" +
        "如您确认无误，请再次执行本指令以完成清理"
    ) ?: return

    val removed = DataAudit.clean(result)
    logger.warning("管理员 $userID 清除了 $removed 项孤儿数据")
    sendQuoteReply("✅ 已清除 $removed 项孤儿数据")
}

/**
 * 失效项目清单：任何人可查看，按作者分组列出项目名称
 * - 失效项目多的作者排在前面，数量相同时按项目添加的先后
 */
private fun invalidProjectsReport(): String {
    val projects = DataAudit.invalidProjects()
    if (projects.isEmpty()) return "✅ 未发现失效项目"
    val byAuthor = projects
        .groupBy { PastebinData.pastebin[it]?.get("author")?.takeIf { author -> author.isNotBlank() } ?: "未知作者" }
        .entries.sortedByDescending { it.value.size }
    return buildString {
        appendLine("⚠️ 失效项目（${projects.size} 个）")
        appendLine("项目链接指向已停服网站，且本地无代码缓存，已无法执行：")
        for ((author, names) in byAuthor) {
            appendLine("· $author（${names.size}）：${names.joinToString("、")}")
        }
        append("💡 作者将代码迁移至其他网站后，使用「${commandPrefix}pb set <名称> url <新链接>」重新设置链接即可恢复")
    }
}

/** 查询运行和等待中的进程 */
internal suspend fun CommandSender.pbThread(ctx: PbContext) {
    if (THREADS.isEmpty()) {
        sendQuoteReply("当前没有正在运行或等待中的进程")
        return
    }
    val threads = THREADS.withIndex().joinToString("\n") { (index, thread) ->
        val elapsed = (System.currentTimeMillis() - thread.startTime) / 1000
        "【#${index + 1}】 ${thread.name}\n" +
        "${thread.nickname}(${thread.userID})\n" +
        "${thread.from} [${thread.platform}]\n" +
        "⏱️ 等待时间：${elapsed} 秒"
    }
    sendQuoteReply(" ⏳ 当前正在运行或等待的进程：\n$threads")
}

/** 导出项目代码缓存（临时链接或邮件） */
internal suspend fun CommandSender.pbExport(ctx: PbContext) {
    val args = ctx.args
    val platform = ctx.platform
    val userID = ctx.userID
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

    val exportCode = CodeCacheManager.get(name)
    if (exportCode == null) {
        sendQuoteReply("导出失败：$name 的代码缓存为空或项目链接类型不支持缓存功能")
        return
    }

    val language = PastebinData.pastebin[name]?.get("language") ?: "未知"
    val requestMail = args.getOrNull(2)?.content in listOf("mail", "邮件")
    if (requestMail) {
        if (!MailConfig.enable) {
            sendQuoteReply("[错误] 邮件功能未启用，无法通过邮件发送项目代码")
            return
        }
        val mail = args.getOrNull(3)?.content
        if (mail == null && platform != "qq") {
            sendQuoteReply(
                "⚠️ 当前平台 $platform 需要指定邮箱地址：\n" +
                "${commandPrefix}pb export $name mail <邮箱地址>"
            )
            return
        }
        if (mail != null && !MailService.isValidAddress(mail)) {
            sendQuoteReply("邮箱地址无效：请输入正确的邮箱地址")
            return
        }
        logger.info("请求使用邮件发送代码导出：$name")
        MailService.sendExportMail(this, exportCode, userID, name, language, mail)
        return
    }

    val url =  try {
        PastebinUrlHelper.pasteToHastebin(exportCode)
    } catch (e: Exception) {
        val mailHint = if (MailConfig.enable) {
            " 请用邮件导出\n📧 ${commandPrefix}pb export $name mail [邮件地址]\n"
        } else "\n"
        when (e) {
            is ConnectException,
            is HttpUtil.HttpException -> {
                logger.warning(e)
                sendQuoteReply(
                    "[API服务异常]" + mailHint +
                    "原因：" + trimToMaxLength(e.message.toString(), ERROR_MSG_MAX_LENGTH).first
                )
            }

            else -> {
                logger.warning(e)
                sendQuoteReply(
                    "[导出代码失败]" + mailHint +
                    "报错类别：${e::class.simpleName}\n" +
                    "报错信息：${trimToMaxLength(e.message.toString(), ERROR_MSG_MAX_LENGTH).first}"
                )
            }
        }
        return
    }
    sendQuoteReply("已成功将 $name 的源代码从缓存导出至 Hastebin，链接如下（有效期 30 天）：\n$url")
}

/** 查询存储数据 */
internal suspend fun CommandSender.pbStorage(ctx: PbContext) {
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
    val storage = StorageManager.getProjectStorage(name)

    val ownerID = PastebinData.pastebin[name]?.get("userID")
    val isOwner = userID == ownerID
    val isCollaborator = isCollaborator(name, userID)
    if (!isOwner && !isAdmin && !isCollaborator) {
        sendQuoteReply("【查询名称】$name\n【用户数量】${storage?.users?.size}\n无权查看数据内容，仅所有者可查看存储数据详细内容")
        return
    }

    val requestMail = args.getOrNull(2)?.content == "邮件" || args.getOrNull(2)?.content == "mail"
    if (MailConfig.enable && requestMail && storage != null) {
        val mail = args.getOrNull(3)?.content
        if (mail == null && platform != "qq") {
            sendQuoteReply(
                "⚠️ 当前平台 $platform 仅支持邮件查询：\n" +
                "${commandPrefix}pb storage $name mail <邮箱地址>"
            )
            return
        }
        if (mail != null && !MailService.isValidAddress(mail)) {
            sendQuoteReply("邮箱地址无效：请输入正确的邮箱地址")
            return
        }

        var output = "【查询名称】$name\n【用户数量】${storage.users.size}\n\n"
        output += "【全局存储[global]】\n${storage.global}\n\n"
        for (user in storage.users) {
            output += "【用户存储[${user.platformID}]】\n${user.content}\n\n"
        }
        logger.info("请求使用邮件发送结果：$name")
        MailService.sendStorageMail(this, output, userID, name, mail)
        return
    }
    if (requestMail && !MailConfig.enable) {
        sendQuoteReply("[错误] 邮件功能未启用，无法通过邮件发送存储数据")
        return
    }

    if (!PastebinConfig.enable_ForwardMessage || platform != "qq") {
        sendQuoteReply(
            "⚠️ 当前未启用转发消息，或该平台不支持此功能，仅可通过邮箱查询存储数据：\n" +
            "${commandPrefix}pb storage $name mail <邮箱地址>"
        )
        return
    }

    val queryID = args.getOrNull(2)?.content
    val queryContent: String? = when (queryID) {
        null -> null
        "global", "全局" -> storage?.global
        else -> storage?.users?.firstOrNull { it.platformID == queryID }?.content
    }
    try {
        val forward = buildForwardMessage(subject!!) {
            displayStrategy = object : ForwardMessage.DisplayStrategy {
                override fun generateTitle(forward: RawForwardMessage): String = "存储数据查询"
                override fun generateBrief(forward: RawForwardMessage): String = "[存储数据]"
                override fun generatePreview(forward: RawForwardMessage): List<String> =
                    if (queryID == null) listOf("查询名称：$name", "用户数量：${storage?.users?.size}")
                    else listOf("查询名称：$name", "查询ID：$queryID")

                override fun generateSummary(forward: RawForwardMessage): String =
                    if (storage == null) "查询失败：名称不存在"
                    else if (queryID != null && queryContent == null) "查询失败：ID不存在"
                    else "查询成功"
            }
            if (queryID == null) {
                subject!!.bot named "存储查询" says "【查询名称】$name\n【用户数量】${storage?.users?.size}"
                if (storage != null) {
                    subject!!.bot named "全局存储" says
                            "【全局存储[global]】\n${truncateStorage(storage.global, name)}"
                    for (user in storage.users) {
                        subject!!.bot named "用户存储" says
                                "【用户存储[${user.platformID}]】\n${truncateStorage(user.content, name)}"
                    }
                } else {
                    subject!!.bot named "查询失败" says "[错误] 查询失败：存储数据中不存在此名称"
                }
            } else {
                subject!!.bot named "存储查询" says "【查询名称】$name\n【查询ID】$queryID"
                subject!!.bot named "存储查询" says
                        if (queryContent == null) "[错误] 查询失败：存储数据中不存在此名称或userID"
                        else if (queryContent.isEmpty()) "[警告] 查询成功，但查询的存储数据为空"
                        else queryContent
            }
        }
        sendMessage(forward)
    } catch (_: MessageTooLargeException) {
        val length = if (queryID == null) "汇总存储查询总长度超出限制，用户数量：${storage?.users?.size}，请尝试添加编号查询指定内容"
                else "数据长度：${queryContent?.length}"
        sendQuoteReply("[内容过长] $length。如需查看完整内容请使用指令\n" +
                "${commandPrefix}pb storage $name mail\n将结果发送邮件至您的邮箱")
    } catch (e: Exception) {
        logger.warning(e)
        sendQuoteReply("[转发消息错误]\n生成或发送转发消息时发生错误，请联系管理员查看后台，简要错误信息：${e.message}")
    }
}

/**
 * 转发消息中单条存储数据过长时替换为提示，引导改用邮件查询
 */
private fun truncateStorage(content: String, name: String): String =
    if (content.length <= 10000) content
    else "[内容过长] 数据长度：${content.length}，如需查看完整内容请使用指令\n\n" +
        "${commandPrefix}pb storage $name mail\n\n将结果发送邮件至您的邮箱"
