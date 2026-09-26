package site.tiedan.command

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.CONSOLE_USER_ID
import site.tiedan.MiraiCompilerFramework.Command
import site.tiedan.MiraiCompilerFramework.getPlatform
import site.tiedan.MiraiCompilerFramework.getUserPlatformID
import site.tiedan.MiraiCompilerFramework.isBotEnabled
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.parseUserID
import site.tiedan.MiraiCompilerFramework.pendingCommand
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.command.pastebin.PbContext
import site.tiedan.command.pastebin.pbAdd
import site.tiedan.command.pastebin.pbBlack
import site.tiedan.command.pastebin.pbCollab
import site.tiedan.command.pastebin.pbDelete
import site.tiedan.command.pastebin.pbExport
import site.tiedan.command.pastebin.pbHandle
import site.tiedan.command.pastebin.pbInfo
import site.tiedan.command.pastebin.pbList
import site.tiedan.command.pastebin.pbPrivate
import site.tiedan.command.pastebin.pbProfile
import site.tiedan.command.pastebin.pbReload
import site.tiedan.command.pastebin.pbRollback
import site.tiedan.command.pastebin.pbSet
import site.tiedan.command.pastebin.pbStats
import site.tiedan.command.pastebin.pbStatus
import site.tiedan.command.pastebin.pbStorage
import site.tiedan.command.pastebin.pbSupport
import site.tiedan.command.pastebin.pbTag
import site.tiedan.command.pastebin.pbThread
import site.tiedan.config.PastebinConfig

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

    private const val TYPE_VIEW = 1
    private const val TYPE_UPDATE = 2
    private const val TYPE_ADVANCED = 3
    private const val TYPE_INFO = 4     /* 默认折叠分类 */
    private const val TYPE_DANGER = 5
    private const val TYPE_RELATED = 6
    private const val TYPE_ADMIN = 7
    /** 展开全部分组 */
    private val HELP_ALL = listOf("all", "全部")

    private val commandList = arrayOf(
        Command("pb list [查询模式]", "pb 列表 [查询模式]", "查看项目列表", TYPE_VIEW),
        Command("pb info <名称>", "pb 信息 <名称>", "查看信息&运行示例", TYPE_VIEW),
        Command("pb support", "pb 支持", "支持粘贴代码的网站", TYPE_VIEW),
        Command("pb private", "pb 私信时段", "允许私信主动消息", TYPE_VIEW),
        Command("run <名称> [stdin]", "pb 运行 <名称> [输入]", "运行代码项目", TYPE_VIEW),

        Command("pb add <名称> <作者> <语言> <源代码URL> [示例输入(stdin)]", "pb 添加 <名称> <作者> <语言> <源代码URL> [示例输入(stdin)]", "添加Pastebin项目", TYPE_UPDATE),
        Command("pb set <名称> <参数名> <内容>", "pb 修改 <名称> <参数名> <内容>", "修改项目属性", TYPE_UPDATE),
        Command("pb tag mark <标签> <项目1> [项目2]...", "pb 标签 标记 <标签> <项目1> [项目2]...", "批量为项目添加标签", TYPE_UPDATE),

        Command("pb set <名称> format <输出格式> [宽度/存储]", "pb 修改 <名称> 输出格式 <输出格式> [宽度/存储]", "修改输出格式", TYPE_ADVANCED),
        Command("pb set <名称> lock <锁定范围>", "pb 修改 <名称> 锁定 <锁定范围>", "锁定项目执行（私信/群聊/全部）", TYPE_ADVANCED),
        Command("bucket help", "存储库 帮助", "跨项目存储库操作指令", TYPE_ADVANCED),

        Command("pb storage <名称> [查询ID/mail] [邮件地址]", "pb 存储 <名称> [查询ID/邮件] [邮件地址]", "查询存储数据", TYPE_ADVANCED),
        Command("pb stats [名称]", "pb 统计 [名称]", "查看统计信息", TYPE_INFO),
        Command("pb profile [ID]", "pb 简介 [平台ID]", "查看个人信息", TYPE_INFO),
        Command("pb tag", "pb 标签", "查看标签库与用法", TYPE_INFO),
        Command("pb status", "pb 状态", "查看框架运行状态", TYPE_INFO),
        Command("pb thread", "pb 进程", "查询运行和等待中的进程", TYPE_INFO),
        Command("pb export <名称> [prev] [mail] [邮件地址]", "pb 导出 <名称> [上一版] [邮件] [邮件地址]", "导出项目代码缓存（临时链接或邮件）", TYPE_INFO),

        Command("pb collab add/remove <ID>", "pb 协作 添加/移除 <平台ID>", "批量编辑自己全部项目的协作者", TYPE_DANGER),
        Command("pb rollback <名称> list", "pb 回滚 <名称> 列表", "查看可回滚的数据备份", TYPE_DANGER),
        Command("pb rollback <名称> diff <编号> [目标]", "pb 回滚 <名称> 对比 <编号> [目标]", "对比备份与当前存储数据", TYPE_DANGER),
        Command("pb rollback <名称> <编号> <目标>", "pb 回滚 <名称> <编号> <目标>", "将项目存储回滚至指定备份", TYPE_DANGER),
        Command("pb delete <名称>", "pb 删除 <名称>", "永久删除项目", TYPE_DANGER),

        Command("glot help", "glot 帮助", "查看框架信息", TYPE_RELATED),
        Command("image help", "图片 帮助", "本地图片操作指令", TYPE_RELATED),

        Command("pb handle <名称> <同意/拒绝> [备注]", "pb 处理 <名称> <同意/拒绝> [备注]", "处理添加和修改申请", TYPE_ADMIN),
        Command("pb black [ID]", "pb 黑名单 [平台ID]", "黑名单处理", TYPE_ADMIN),
        Command("pb reload", "pb 重载", "重载本地数据", TYPE_ADMIN),
        Command("pb status clean", "pb 状态 clean", "清除孤儿数据", TYPE_ADMIN),
        Command("pb tag add/del <标签>", "pb 标签 添加/移除 <标签>", "批量编辑标签库", TYPE_ADMIN),
    )

    /** 拼装 pb 指令帮助 */
    private fun buildHelp(cn: Boolean, showAll: Boolean, isAdmin: Boolean): String {
        fun group(title: String, type: Int) = title + "\n" +
            commandList.filter { it.type == type }
                .joinToString("") { "$commandPrefix${if (cn) it.usageCN else it.usage}　${it.desc}\n" }

        return buildString {
            append(group("📋 PB查看运行帮助：", TYPE_VIEW))
            append(group("✏️ PB更新数据帮助：", TYPE_UPDATE))
            append(group("⚙️ PB高级功能帮助：", TYPE_ADVANCED))
            if (!showAll) {
                val usage = if (cn) "pb 帮助 全部" else "pb help all"
                append("💡 完整指令帮助「$commandPrefix$usage」")
                return@buildString
            }
            append(group("📊 PB信息查询帮助：", TYPE_INFO))
            append(group("⚠️ PB危险操作帮助：", TYPE_DANGER))
            append(group("🔗 PB相关指令帮助：", TYPE_RELATED))
            if (isAdmin) {
                append("\n")
                append(group("🛠️ 管理指令帮助：", TYPE_ADMIN))
            }
        }
    }

    override suspend fun CommandSender.onCommand(args: MessageChain) {

        if (!isBotEnabled(bot?.id)) return

        val platform = getPlatform()
        val userID = getUserPlatformID(this.user?.id) ?: CONSOLE_USER_ID
        val numID = parseUserID(userID)
            ?: return sendQuoteReply("[用户ID解析失败] 无法解析您的用户ID，请联系管理员")
        val isAdmin = PastebinConfig.admins.contains(userID)

        if (pendingCommand[userID]?.let { it != args.content } == true) {
            pendingCommand.remove(userID)
            sendQuoteReply("指令不一致，操作已取消")
        }

        val ctx = PbContext(args, platform, userID, numID, isAdmin)

        try {
            when (args[0].content) {

                "help", "帮助"-> {   // 查看pastebin帮助
                    val showAll = (args.getOrNull(1)?.content ?: "") in HELP_ALL
                    sendQuoteReply(buildHelp(cn = args[0].content == "帮助", showAll = showAll, isAdmin = isAdmin))
                }

                "list", "列表"-> pbList(ctx)   // 查看完整列表
                "info", "信息"-> pbInfo(ctx)   // 查看数据具体参数
                "support", "支持"-> pbSupport()   // 支持粘贴代码的网站
                "private", "私信时段"-> pbPrivate(ctx)   // 允许私信主动消息

                "add", "添加", "新增"-> pbAdd(ctx)   // 添加Pastebin项目
                "set", "修改"-> pbSet(ctx)   // 修改项目属性

                "stats", "statistics", "统计"-> pbStats(ctx)   // 查看统计信息
                "profile", "简介"-> pbProfile(ctx)   // 查看个人信息
                "tag", "标签"-> pbTag(ctx)   // 查看标签库 / 管理员批量编辑标签库
                "status", "状态"-> pbStatus(ctx)   // 查看框架运行状态
                "thread", "进程"-> pbThread(ctx)   // 查询运行和等待中的进程
                "export", "导出"-> pbExport(ctx)   // 导出项目代码缓存（临时链接或邮件）
                "storage", "存储"-> pbStorage(ctx)   // 查询存储数据

                "collab", "collaborators", "协作", "协作者"-> pbCollab(ctx)   // 批量编辑自己全部项目的协作者
                "rollback", "回滚"-> pbRollback(ctx)   // 将项目存储数据回滚至备份
                "delete", "remove", "删除", "移除"-> pbDelete(ctx)   // 永久删除项目

                "handle", "处理"-> pbHandle(ctx)   // 处理添加和修改申请（审核功能）
                "black", "黑名单"-> pbBlack(ctx)   // 添加/移除黑名单
                "reload", "重载"-> pbReload(ctx)   // 重载配置和数据文件

                "upload", "上传"-> {   // 已迁移至 CommandImage
                    sendQuoteReply("[已废弃] 上传图片功能已迁移至独立指令，请使用「${commandPrefix}img upload <图片名称> <【图片/URL】>」来上传图片")
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
        } finally {
            ctx.storageLock?.release()
        }
    }
}
