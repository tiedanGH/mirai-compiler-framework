package site.tiedan.command.pastebin

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.pendingCommand
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.uploadTempImage
import site.tiedan.command.CommandBucket
import site.tiedan.command.CommandBucket.removeProjectFromBucket
import site.tiedan.command.pastebin.PbCollaborator.handleBatchCollaboratorAction
import site.tiedan.core.CodeCacheManager
import site.tiedan.core.StorageLockGuard.lockProject
import site.tiedan.core.StorageManager
import site.tiedan.data.PastebinData
import site.tiedan.format.MarkdownImageGenerator
import site.tiedan.module.FavoriteManager
import site.tiedan.module.Statistics
import site.tiedan.module.StorageRollback
import site.tiedan.utils.FuzzySearch

/*
 * # PB危险操作类指令
 *
 * @author tiedanGH
 */

/** 批量编辑自己全部项目的协作者 */
internal suspend fun CommandSender.pbCollab(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val subAction = args.getOrNull(1)?.content
    val targetPlatformID = args.getOrNull(2)?.content

    if (subAction == null || targetPlatformID == null) {
        sendQuoteReply(
            "[参数不足] 请参考以下指令批量编辑协作者：\n" +
            "${commandPrefix}pb collab add <ID>\n" +
            "${commandPrefix}pb collab remove <ID>\n" +
            "${commandPrefix}pb 协作 添加 <平台ID>\n" +
            "${commandPrefix}pb 协作 移除 <平台ID>"
        )
        return
    }

    when (subAction.lowercase()) {
        "add", "添加", "新增" -> {
            handleBatchCollaboratorAction("add", targetPlatformID, userID, args.content)
        }
        "remove", "rm", "移除", "删除" -> {
            handleBatchCollaboratorAction("remove", targetPlatformID, userID, args.content)
        }
        else -> {
            sendQuoteReply(
                "未知的操作：$subAction\n" +
                "仅支持 add/remove（添加/移除）"
            )
        }
    }
}

/** 将项目存储数据回滚至备份 */
internal suspend fun CommandSender.pbRollback(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val isAdmin = ctx.isAdmin
    val name = args[1].content
    if (rejectAlias(name, "回滚")) return
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

    // 协作者不具备回滚存储权限
    val ownerID = PastebinData.pastebin[name]?.get("userID")
    if (userID != ownerID && !isAdmin) {
        sendQuoteReply("无权操作此项目：仅所有者可回滚存储数据，如需回滚请联系所有者：$ownerID")
        return
    }

    val snapshots = StorageRollback.snapshots()
    if (snapshots.isEmpty()) {
        sendQuoteReply("[回滚失败] 本地暂无包含存储数据库的备份，无法回滚")
        return
    }

    val subAction = args.getOrNull(2)?.content
    // 可回滚备份列表：只读，无需上锁
    if (subAction == null || subAction in listOf("list", "列表")) {
        val infos = StorageRollback.listFor(name)
        val usage =
            "\n🔍 数据对比：${commandPrefix}pb rollback $name diff <编号> [目标]\n" +
            "↩️ 执行回滚：${commandPrefix}pb rollback $name <编号> <目标>\n" +
            "🎯 目标：all（全部存储）／global（全局）／<用户ID>"

        val markdownResult = MarkdownImageGenerator.processMarkdown(
            name = null,
            MarkdownImageGenerator.generateRollbackListHtml(infos),
            width = "620"
        )
        val image = markdownResult.file?.takeIf { markdownResult.success }
            ?.let { subject?.uploadTempImage(it) }
        // 渲染或上传失败时退回纯文字列表
        if (image == null) {
            sendQuoteReply(StorageRollback.formatSnapshotList(name, infos) + usage)
            return
        }
        sendQuoteReply(buildMessageChain {
            +PlainText("·🕰 可回滚备份：$name\n")
            +image
            +PlainText(usage)
        })
        return
    }

    val isDiff = subAction in listOf("diff", "对比")
    val index = (if (isDiff) args.getOrNull(3)?.content else subAction)
        ?.toIntOrNull()?.takeIf { it in 1..snapshots.size }
    if (index == null) {
        sendQuoteReply(
            "[编号无效] 备份编号仅支持 1-${snapshots.size}\n" +
            "请使用「${commandPrefix}pb rollback $name list」查看可回滚备份"
        )
        return
    }
    val snapshot = snapshots[index - 1]

    // 只读对比：不上锁，展示的是此刻的数据
    if (isDiff) {
        val loaded = StorageRollback.load(snapshot, name)
        val rawTarget = args.getOrNull(4)?.content ?: "all"
        val target = loaded.resolveTarget(rawTarget)
            ?: return sendQuoteReply("[目标无效] 用户ID $rawTarget 在备份与当前数据中均不存在")
        sendQuoteReply(StorageRollback.formatDiff(loaded.plan(target)))
        return
    }

    // 回滚目标必须显式指定，避免误将整个项目回滚
    val rawTarget = args.getOrNull(3)?.content
    if (rawTarget == null) {
        sendQuoteReply(
            "[参数不足] 请指定回滚目标：\n" +
            "${commandPrefix}pb rollback $name $index all　回滚项目全部存储\n" +
            "${commandPrefix}pb rollback $name $index global　仅回滚全局数据\n" +
            "${commandPrefix}pb rollback $name $index <用户ID>　仅回滚该用户数据\n" +
            "🔍 可先使用「${commandPrefix}pb rollback $name diff $index」查看完整对比"
        )
        return
    }

    // 与执行进程共用同一把项目锁：回滚期间该项目无法被执行
    ctx.storageLock = lockProject(name) ?: return

    // 排队期间项目可能已被改名、删除或转移所有权。
    if (PastebinData.pastebin.contains(name).not()) {
        clearPendingRollback(userID)
        sendQuoteReply("[回滚取消] 项目 $name 在排队期间已被改名或删除，请重新执行指令")
        return
    }
    if (userID != PastebinData.pastebin[name]?.get("userID") && !isAdmin) {
        clearPendingRollback(userID)
        sendQuoteReply("[回滚取消] 项目 $name 在排队期间被转移所有权，本次操作已取消")
        return
    }

    val loaded = StorageRollback.load(snapshot, name)
    val target = loaded.resolveTarget(rawTarget)
        ?: return sendQuoteReply("[目标无效] 用户ID $rawTarget 在备份与当前数据中均不存在")
    val plan = loaded.plan(target)
    if (plan.snapshotEmpty && target.isAll) {
        clearPendingRollback(userID)
        sendQuoteReply(
            "[回滚失败] 备份 ${snapshot.label} 中不存在项目 $name 的任何存储数据\n" +
            "可能原因：此备份早于项目开启存储，或项目在此备份之后被改名（数据仍留在旧名称下），请联系管理员"
        )
        return
    }
    if (plan.changed.isEmpty()) {
        clearPendingRollback(userID)
        sendQuoteReply("ℹ 无需回滚：${target.label}与备份 ${snapshot.label} 完全一致")
        return
    }

    val confirmed = requestUserConfirmation(userID, args.content,
        " +++⚠️ 危险操作警告 ⚠️+++\n" +
        "您正在回滚项目 $name 的存储数据，请再次确认以下信息：\n" +
        "- 目标范围内*当前数据将被备份覆盖*\n" +
        "- 覆盖后当前数据*不可恢复*\n" +
        "- 备份中不存在的条目会被*删除*\n" +
        "- 回滚不影响存储库、代码缓存与统计数据\n" +
        "\n" +
        StorageRollback.formatDiff(plan) + "\n" +
        "\n" +
        "如您确认无误，请再次执行回滚指令以完成操作"
    )
    if (confirmed == null) {
        StorageRollback.remember(userID, plan)
        return
    }
    // 二次确认期间锁已交还给执行进程，数据可能已被改写
    if (!StorageRollback.matches(userID, plan)) {
        clearPendingRollback(userID)
        sendQuoteReply(
            "[回滚取消] 二次确认期间待回滚数据发生了变化，本次回滚已取消\n" +
            "💡 可用「${commandPrefix}pb set $name lock all」锁定项目，回滚完成后再解除\n" +
            "最新对照如下，如仍需回滚请重新执行指令\n\n" +
            StorageRollback.formatDiff(plan)
        )
        return
    }
    StorageRollback.forget(userID)
    val changed = StorageRollback.apply(plan)
    logger.warning("$userID 将项目 $name 的${target.label}回滚至备份 ${snapshot.label}（$changed 项变化）")
    sendQuoteReply(
        "[ROLLBACK] 成功将项目 $name 的${target.label}回滚至备份 ${snapshot.label}（${snapshot.kind}）！\n" +
        "共 $changed 项数据发生变化"
    )
}

/** 永久删除项目 */
internal suspend fun CommandSender.pbDelete(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val isAdmin = ctx.isAdmin
    val name = args[1].content
    if (rejectAlias(name, "删除项目")) return
    if (PastebinData.pastebin.contains(name).not()) {
        sendQuoteReply("删除失败：名称 $name 不存在")
        return
    }

    val ownerID = PastebinData.pastebin[name]?.get("userID")
    val isOwner = userID == ownerID
    val forceDelete = args.getOrNull(2)?.content == "force"
    val skipConfirm = isAdmin && forceDelete && args.getOrNull(3)?.content == "confirm"
    if (!isOwner) {
        if (!isAdmin) {
            sendQuoteReply("无权删除此项目，如需删除请联系所有者：$ownerID。如果您认为此条记录存在不合适的内容或其他问题，请联系指令管理员")
            return
        }
        if (!forceDelete) {
            sendQuoteReply("操作保护：您并非该项目所有者。若需要管理员强制删除，请在指令末尾添加 force 参数")
            return
        }
    }

    if (!skipConfirm) {
        val storageMode = PastebinData.pastebin[name]?.get("storage") == "true"
        val linkedBuckets = CommandBucket.bucketIdsToNames(CommandBucket.linkedBucketId(name))
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
    }

    ctx.storageLock = lockProject(name) ?: return
    PastebinData.alias.entries.removeIf { it.value == name }
    PastebinData.hiddenUrl.remove(name)
    PastebinData.censorList.remove(name)
    PastebinData.pastebin.remove(name)
    PastebinData.save()
    CodeCacheManager.remove(name)
    Statistics.removeProject(name)
    StorageManager.removeProjectStorage(name)
    removeProjectFromBucket(name)
    FavoriteManager.removeProject(name)
    if (skipConfirm) logger.warning("管理员 $userID 跳过二次确认删除了项目 $name")
    sendQuoteReply(
        if (skipConfirm) "[管理员操作] 删除项目 $name 成功！"
        else "删除项目 $name 成功！"
    )
}

private fun clearPendingRollback(userID: String) {
    pendingCommand.remove(userID)
    StorageRollback.forget(userID)
}
