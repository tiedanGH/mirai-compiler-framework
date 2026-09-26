package site.tiedan.command.pastebin

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.parseUserID
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.module.FavoriteManager

/*
 * # PB管理指令
 *
 * @author tiedanGH
 */

/** 处理添加和修改申请（审核功能） */
internal suspend fun CommandSender.pbHandle(ctx: PbContext) {
    val args = ctx.args
    val isAdmin = ctx.isAdmin
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
            bot?.getFriendOrFail(parseUserID(PastebinData.pastebin[name]!!["userID"]!!)!!)!!.sendMessage(noticeApply)   // 抄送结果至申请人
            "已将结果发送至申请人"
        } catch (e: Exception) {
            logger.warning(e)
            "发送消息至申请人时出现错误，可能因为机器人权限不足或未找到对象，简要错误信息：${e::class.simpleName}(${e.message})"
        }
    if (option == "拒绝") {
        PastebinData.pastebin.remove(name)
        FavoriteManager.removeProject(name)
    }
    sendQuoteReply(reply)   // 回复指令发出者
}

/** 添加/移除黑名单 */
internal suspend fun CommandSender.pbBlack(ctx: PbContext) {
    val args = ctx.args
    val isAdmin = ctx.isAdmin
    if (!isAdmin) throw PermissionDeniedException()
    try {
        val platformID = args[1].content
        if (ExtraData.BlackList.contains(platformID)) {
            ExtraData.BlackList.remove(platformID)
            sendQuoteReply("已将 $platformID 移出黑名单")
        } else {
            ExtraData.BlackList.add(platformID)
            sendQuoteReply("已将 $platformID 移入黑名单")
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

/** 重载配置和数据文件 */
internal suspend fun CommandSender.pbReload(ctx: PbContext) {
    val isAdmin = ctx.isAdmin
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
