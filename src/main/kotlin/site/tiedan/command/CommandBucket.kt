package site.tiedan.command

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.MessageTooLargeException
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.RawForwardMessage
import net.mamoe.mirai.message.data.buildForwardMessage
import net.mamoe.mirai.message.data.content
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.CONSOLE_USER_ID
import site.tiedan.MiraiCompilerFramework.Command
import site.tiedan.MiraiCompilerFramework.getNickname
import site.tiedan.MiraiCompilerFramework.getPlatform
import site.tiedan.MiraiCompilerFramework.getUserPlatformID
import site.tiedan.MiraiCompilerFramework.isBotEnabled
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.parseUserID
import site.tiedan.MiraiCompilerFramework.pendingCommand
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.uploadTempImage
import site.tiedan.command.CommandPastebin.isCollaborator
import site.tiedan.config.MailConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.core.StorageLockGuard.lockBucket
import site.tiedan.core.StorageLockGuard.lockProjectAndBucket
import site.tiedan.core.StorageManager
import site.tiedan.data.PastebinData
import site.tiedan.format.MarkdownImageGenerator
import site.tiedan.module.MailService
import site.tiedan.utils.FuzzySearch
import site.tiedan.utils.Security
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * # 跨项目存储库操作指令
 *
 * @author tiedanGH
 */
object CommandBucket : RawCommand(
    owner = MiraiCompilerFramework,
    primaryName = "bucket",
    secondaryNames = arrayOf("bk", "存储库"),
    description = "跨项目存储库操作指令",
    usage = "${commandPrefix}bk help"
) {
    private val commandList = arrayOf(
        Command("bk list [文字/备份]", "bk 列表 [文字/备份]", "查看存储库列表", 1),
        Command("bk info <ID/名称>", "bk 信息 <ID/名称>", "查看存储库信息", 1),
        Command("bk storage <ID/名称> [密码] [备份ID/mail] [邮件地址]", "bk 存储 <ID/名称> [密码] [备份ID/邮件] [邮件地址]", "查询存储库数据", 1),
        Command("bk create <名称> <密码>", "bk 创建 <名称> <密码>", "创建新存储库", 1),
        Command("bk set <ID/名称> <参数名> <内容>", "bk 修改 <ID/名称> <参数名> <内容>", "修改存储库属性", 1),

        Command("bk add <项目名称> <ID/名称> [密码]", "bk 添加 <项目名称> <ID/名称> [密码]", "将存储库添加至项目", 2),
        Command("bk rm <项目名称> <ID/名称>", "bk 移除 <项目名称> <ID/名称>", "将存储库从项目移除", 2),

        Command("bk backup <ID/名称> <编号> [密码]", "bk 备份 <ID/名称> <编号> [密码]", "备份存储库数据", 3),
        Command("bk backup <ID/名称> del <编号> [密码]", "bk 备份 <ID/名称> 删除 <编号> [密码]", "删除指定备份", 3),
        Command("bk rollback <ID/名称> <编号> [密码]", "bk 回滚 <ID/名称> <编号> [密码]", "从备份回滚数据", 3),
        Command("bk delete <ID/名称>", "bk 删除 <ID/名称>", "永久删除存储库", 3),
    )

    override suspend fun CommandSender.onCommand(args: MessageChain) {

        if (!isBotEnabled(bot?.id)) return

        val platform = getPlatform()
        val userID = getUserPlatformID(this.user?.id) ?: CONSOLE_USER_ID
        val userName = this.name
        val isAdmin = PastebinConfig.admins.contains(userID)

        if (pendingCommand[userID]?.let { it != args.content } == true) {
            pendingCommand.remove(userID)
            sendQuoteReply("指令不一致，操作已取消")
        }

        // 本次指令持有的存储锁，与执行进程共用同一套锁
        var storageLock: StorageManager.StorageLock? = null

        try {
            when (args[0].content) {

                "help"-> {   // 查看存储库帮助（help）
                    val reply = "🗄 跨项目存储库操作指令：\n" +
                            commandList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                            "🔗 关联存储库与项目：\n" +
                            commandList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                            "⚠️ 危险区：\n" +
                            commandList.filter { it.type == 3 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                    sendQuoteReply(reply)
                }

                "帮助"-> {   // 查看存储库帮助（帮助）
                    val reply = "🗄 跨项目存储库操作指令：\n" +
                            commandList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" } +
                            "🔗 关联存储库与项目：\n" +
                            commandList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" } +
                            "⚠️ 危险区：\n" +
                            commandList.filter { it.type == 3 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" }
                    sendQuoteReply(reply)
                }

                "list", "列表"-> {   // 查看存储库列表
                    val option = args.getOrNull(1)?.content ?: "图片"
                    if (option == "文字" || option == "text") {
                        val bucketList = StorageManager.listBucketSlots().entries.joinToString("\n") { (id, bucket) ->
                            if (bucket == null) "$id. [空槽位]" else "$id. ${bucket.name}"
                        }
                        sendQuoteReply(" ·bucket存储库列表：\n$bucketList")
                    } else {
                        val showBackups = option in arrayOf("全部", "all", "备份", "backup")
                        val markdownResult = MarkdownImageGenerator.processMarkdown(
                            name = null,
                            MarkdownImageGenerator.generateBucketListHtml(showBackups),
                            width = "750"
                        )
                        if (!markdownResult.success || markdownResult.file == null) {
                            sendQuoteReply(markdownResult.message)
                            return
                        }
                        val image = subject?.uploadTempImage(markdownResult.file)
                            ?: return sendQuoteReply("[错误] 图片文件异常：ExternalResource上传失败，请尝试重新执行")
                        sendMessage(image)
                    }
                }

                "info", "信息"-> {   // 查看存储库信息
                    val id = checkBucketNameOrID(args[1].content, "查看") ?: return
                    val bucket = StorageManager.getBucket(id)
                        ?: return sendQuoteReply("[错误] 存储库数据读取失败：槽位 $id 状态异常，请联系管理员")
                    val info = buildString {
                        appendLine("存储库ID：$id")
                        appendLine("名称：${bucket.name}")
                        appendLine("所有者：${bucket.owner}(${bucket.userID})")
                        appendLine("关联项目(${bucket.projects.size})：${bucket.projects.joinToString(" ")}")
                        val lock = if (bucket.encrypt) " 🔐" else ""
                        appendLine("存储大小：${bucket.content.length}$lock")
                        val backups = StorageManager.getBackups(id)
                        appendLine("备份信息：")
                        backups.forEach { backup ->
                            appendLine(
                                "- " + (backup?.let { "${formatTime(it.time)} ${it.name}" } ?: "空备份")
                            )
                        }
                        if (bucket.desc.isNotEmpty()) {
                            appendLine("------- [简介] -------\n${bucket.desc}")
                        }
                    }
                    sendQuoteReply(info)
                }

                "storage", "存储"-> {   // 查询存储库数据
                    val id = checkBucketNameOrID(args[1].content, "查询") ?: return

                    val password = args.getOrNull(2)?.content
                    checkPassword(id, password, userID, isAdmin) ?: return  // 验证密码

                    val bucket = StorageManager.getBucket(id)
                        ?: return sendQuoteReply("[错误] 存储库数据读取失败：槽位 $id 状态异常，请联系管理员")
                    if (bucket.encrypt) {
                        sendQuoteReply("🔐 此存储库启用了数据加密，为保证数据安全，查询功能被禁用")
                        return
                    }

                    val requestMail = args.getOrNull(3)?.content == "邮件" || args.getOrNull(3)?.content == "mail"
                    if (MailConfig.enable && requestMail) {
                        val mail = args.getOrNull(4)?.content
                        if (mail == null && platform != "qq") {
                            sendQuoteReply(
                                "⚠️ 当前平台 $platform 仅支持邮件查询：\n" +
                                "${commandPrefix}bk storage $id <密码> mail <邮箱地址>"
                            )
                            return
                        }
                        if (mail != null && !Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(mail)) {
                            sendQuoteReply("邮箱地址无效：请输入正确的邮箱地址")
                            return
                        }

                        val allBackupData = StorageManager.getBackups(id)
                            .mapIndexed { index, backup ->
                                "【备份${index + 1}数据】\n" + (
                                        backup?.let { "${formatTime(it.time)} ${it.name}\n【备份内容】\n${it.content}" }
                                            ?: "空备份"
                                        )
                            }
                            .joinToString("\n\n")
                        var output = "【查询存储库】$id\n" +
                                "【名称】${bucket.name}\n" +
                                "【存储库大小】${bucket.content.length}\n" +
                                "【关联项目】${bucket.projects.joinToString(" ")}\n" +
                                "\n" +
                                "【存储库内容】\n${bucket.content}\n" +
                                "\n\n" +
                                allBackupData
                        logger.info("请求使用邮件发送结果：${bucketInfo(id)}")
                        MailService.sendStorageMail(this, output, userID, bucketInfo(id), mail)
                        return
                    }

                    if (!PastebinConfig.enable_ForwardMessage || platform != "qq") {
                        sendQuoteReply(
                            "⚠️ 当前未启用转发消息，或该平台不支持此功能，仅可通过邮箱查询存储数据：\n" +
                            "${commandPrefix}bk storage $id <密码> mail <邮箱地址>"
                        )
                        return
                    }

                    val backupID = args.getOrNull(3)?.content?.toIntOrNull() ?: 0
                    val backup = (backupID - 1).takeIf { backupID > 0 }?.let { StorageManager.getBackup(id, it) }
                    try {
                        val forward = buildForwardMessage(subject!!) {
                            displayStrategy = object : ForwardMessage.DisplayStrategy {
                                override fun generateTitle(forward: RawForwardMessage): String = "存储库查询"
                                override fun generateBrief(forward: RawForwardMessage): String = "[存储数据]"
                                override fun generatePreview(forward: RawForwardMessage): List<String> =
                                    listOf("查询ID：$id", "查询名称：${bucket.name}") +
                                    if (backupID != 0) listOf("查询备份：$backupID") else emptyList()

                                override fun generateSummary(forward: RawForwardMessage): String =
                                    if (backupID != 0 && backup == null) "查询失败：备份ID不存在"
                                    else "查询成功"
                            }
                            subject!!.bot named "存储库查询" says
                                    "【查询ID】$id\n" +
                                    "【查询名称】${bucket.name}\n" +
                                    "【存储库大小】${bucket.content.length}\n" +
                                    "【关联项目】${bucket.projects.joinToString(" ")}"
                            if (backupID == 0) {
                                subject!!.bot named "存储库查询" says
                                        if (bucket.content.isEmpty()) "[警告] 查询成功，但查询的存储数据为空"
                                        else bucket.content
                            } else {
                                if (backup != null) {
                                    subject!!.bot named "存储库备份查询" says
                                            "【查询备份】$backupID\n" +
                                            "【备份名称】${backup.name}\n" +
                                            "【备份时间】${formatTime(backup.time)}\n" +
                                            "【备份大小】${backup.content.length}"
                                }
                                subject!!.bot named "存储库备份查询" says
                                        if (backup == null) "[错误] 查询失败：无效备份ID或当前槽位尚未添加备份"
                                        else if (backup.content.isEmpty()) "[警告] 查询成功，但查询的存储数据为空"
                                        else backup.content
                            }
                        }
                        sendMessage(forward)
                    } catch (_: MessageTooLargeException) {
                        val length = "数据长度：${bucket.content.length}"
                        sendQuoteReply("[内容过长] $length。如需查看完整内容请使用指令\n" +
                                "${commandPrefix}pb storage $name mail\n将结果发送邮件至您的邮箱")
                    } catch (e: Exception) {
                        logger.warning(e)
                        sendQuoteReply("[转发消息错误]\n生成或发送转发消息时发生错误，请联系管理员查看后台，简要错误信息：${e.message}")
                    }
                }

                "create", "创建"-> {   // 创建新存储库
                    if (PastebinData.pastebin.none { it.value["userID"] == userID }) {
                        sendQuoteReply("创建失败：请先创建一个项目，然后再创建存储库")
                        return
                    }
                    val name = args[1].content
                    val password = args[2].content
                    if (name.all { it.isDigit() }) {
                        sendQuoteReply("创建失败：存储库名称不能为纯数字")
                        return
                    }
                    if (bucketNameToId(name) != null) {
                        sendQuoteReply("创建失败：名称 $name 已存在")
                        return
                    }
                    val id = StorageManager.nextFreeBucketId()
                    StorageManager.createBucket(id, name, Security.hashPassword(password), userName, userID)
                    sendQuoteReply(
                        "🗄 创建新存储库成功！\n" +
                        "存储库ID：$id\n" +
                        "名称：$name\n" +
                        "密码：长度为 ${password.length}\n" +
                        "所有者：$userName($userID)" +
                        (if (subject is Group) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                    )
                }

                "set", "修改"-> {   // 修改存储库属性
                    val id = checkBucketNameOrID(args[1].content, "修改") ?: return
                    storageLock = lockBucket(id) ?: return
                    var option = args[2].content
                    var content = args.drop(3).joinToString(separator = " ")
                    var additionalOutput = ""
                    val ownerID = StorageManager.getBucket(id)?.userID
                    val isOwner = userID == ownerID
                    if (!isOwner && !isAdmin) {
                        sendQuoteReply("无权修改此存储库，如需修改请联系所有者：$ownerID")
                        return
                    }
                    val paraMap = mapOf(
                        "名称" to "name",
                        "密码" to "password",
                        "简介" to "desc",
                        "所有者" to "owner",
                        "所有者ID" to "userID",
                        "备份名" to "backup",
                        "加密" to "encrypt",
                    )
                    option = paraMap[option] ?: option
                    if (paraMap.values.contains(option).not()) {
                        sendQuoteReply(
                            "❓ 未知的配置项：$option\n" +
                            "name（名称）\n" +
                            "password（密码）\n" +
                            "desc（简介）\n" +
                            "owner（所有者）\n" +
                            "userID（所有者ID）\n" +
                            "backup（备份名）\n" +
                            "encrypt（加密）"
                        )
                        return
                    }
                    if (content.isEmpty()) {
                        sendQuoteReply("修改失败：修改后的值为空！")
                        return
                    }
                    when (option) {
                        "password"-> {
                            if (subject is Group) {
                               additionalOutput = "⚠️ 您正在群聊进行操作，新密码存在极高泄露风险，建议私信重新修改密码！\n\n"
                            }
                            val newPassword = content
                            content = "***"
                            StorageManager.setBucketPassword(id, Security.hashPassword(newPassword))
                        }
                        "userID"-> {
                            val targetID = parseUserID(content)
                            if (targetID == null) {
                                sendQuoteReply("转移失败：输入的 userID 格式不正确，应为纯数字或带平台前缀 kook_123")
                                return
                            }
                            val targetName = getNickname(targetID)
                            if (targetName == null) {
                                sendQuoteReply("转移失败：无法找到目标用户 $content，转移对象必须为机器人好友或本群成员")
                                return
                            }

                            requestUserConfirmation(userID, args.content,
                                " +++⚠️ 危险操作警告 ⚠️+++\n" +
                                "您正在转移存储库 ${bucketInfo(id)} 的所有权，转移前请确保您已知晓：\n" +
                                "- 转移后您将*完全失去*存储库管理权\n" +
                                "- 此操作*不可撤销*\n" +
                                "- 请务必确认目标用户ID准确且有效\n" +
                                "- 转移后关联的项目不受影响，您仍然可以通过密码查询或关联存储库\n" +
                                "\n" +
                                "如您确认无误，请再次执行转移指令以完成操作"
                            ) ?: return

                            StorageManager.setBucketOwner(id, targetName, content)
                        }
                        "backup"-> {
                            val paras = content.split(" ")
                            val num = paras.getOrNull(0)?.toIntOrNull()?.minus(1)
                                ?.takeIf { it in 0..2 }
                                ?: return sendQuoteReply("备份编号无效：备份编号仅支持 1-3")
                            val newName = paras.getOrNull(1)
                                ?: return sendQuoteReply("修改失败：请输入修改后的新备份名称")
                            val backup = StorageManager.getBackup(id, num)
                                ?: return sendQuoteReply("修改失败：备份编号 ${num + 1} 尚未初始化")

                            content = "$newName（备份ID ${num + 1}）"
                            StorageManager.setBackup(id, num, backup.copy(name = newName))
                        }
                        "encrypt"-> {
                            if (StorageManager.getBucket(id)?.encrypt == true) {
                                return sendQuoteReply("修改失败：加密功能开启后不支持关闭")
                            }
                            if (content !in arrayListOf("enable","on","true","开启")) {
                                return sendQuoteReply("修改失败：加密功能仅支持开启")
                            }

                            requestUserConfirmation(userID, args.content,
                                " +++⚠️ 不可逆操作警告 ⚠️+++\n" +
                                "您正在为存储库 ${bucketInfo(id)} 启用*数据加密*，请再次确认以下内容：\n" +
                                "- 数据将在本地文件加密保存，仅在程序调用时才能获得真实值\n" +
                                "- 查询功能将被永久禁用，任何人都无法查询数据\n" +
                                "- 此操作*不可撤销*，启用后无法恢复\n" +
                                "\n" +
                                "如您确认无误，请再次执行修改指令以完成操作"
                            ) ?: return

                            StorageManager.enableBucketEncryption(id)
                        }
                        "name" -> StorageManager.setBucketName(id, content)

                        "desc" -> StorageManager.setBucketDesc(id, content)

                        else -> {
                            sendQuoteReply("[错误] 未处理的配置项：$option，请联系管理员")
                            return
                        }
                    }
                    if (option == "userID") {
                        sendQuoteReply("${additionalOutput}成功将存储库 ${bucketInfo(id)} 的所有权转移至 $content")
                    } else {
                        sendQuoteReply("${additionalOutput}成功将存储库 ${bucketInfo(id)} 的 $option 参数修改为 $content")
                    }
                }

                "add", "添加"-> {   // 将存储库添加至项目
                    val ctx = prepareProjectContext(args, userID) ?: return
                    storageLock = lockProjectAndBucket(ctx.projectName, ctx.id) ?: return
                    ctx.refreshProjects()

                    val password = args.getOrNull(3)?.content
                    checkPassword(ctx.id, password, userID, isAdmin) ?: return  // 验证密码

                    if (PastebinData.pastebin[ctx.projectName]?.get("storage") != "true") {
                        sendQuoteReply("添加失败：项目 ${ctx.projectName} 未开启存储功能")
                        return
                    }
                    if (ctx.projectName in ctx.projectsList) {
                        sendQuoteReply("添加失败：存储库 ${ctx.id} 与项目 ${ctx.projectName} 已经处于关联状态")
                        return
                    }

                    ctx.projectsList.add(ctx.projectName)
                    StorageManager.setBucketProjects(ctx.id, ctx.projectsList)
                    sendQuoteReply(
                        "成功将存储库 ${bucketInfo(ctx.id)} 关联到项目 ${ctx.projectName}" +
                        (if (subject is Group && password != null) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                    )
                }

                "remove", "rm", "移除"-> {   // 将存储库从项目移除
                    val ctx = prepareProjectContext(args, userID) ?: return
                    storageLock = lockProjectAndBucket(ctx.projectName, ctx.id) ?: return
                    ctx.refreshProjects()
                    if (ctx.projectsList.remove(ctx.projectName).not()) {
                        sendQuoteReply("移除失败：存储库 ${ctx.id} 未关联此项目 ${ctx.projectName}")
                        return
                    }
                    StorageManager.setBucketProjects(ctx.id, ctx.projectsList)
                    sendQuoteReply("成功将存储库 ${bucketInfo(ctx.id)} 与项目 ${ctx.projectName} 解除关联")
                }

                "backup", "备份"-> {   // 备份存储库数据
                    val id = checkBucketNameOrID(args[1].content, "备份") ?: return
                    storageLock = lockBucket(id) ?: return

                    // 删除备份指令
                    if (args.getOrNull(2)?.content == "del") {
                        val num = args.getOrNull(3)?.content?.toIntOrNull()
                            ?.takeIf { it in 1..3 }
                            ?: return sendQuoteReply("编号无效：备份编号仅支持 1-3")

                        val password = args.getOrNull(4)?.content
                        checkPassword(id, password, userID, isAdmin) ?: return  // 验证密码

                        val backup = StorageManager.getBackup(id, num - 1)
                        if (backup == null) {
                            return sendQuoteReply("删除失败：槽位 $num 中没有备份")
                        }

                        requestUserConfirmation(userID, args.content,
                            " +++⚠️ 危险操作警告 ⚠️+++\n" +
                            "您正在删除存储库 ${bucketInfo(id)} 的备份槽位 $num，此操作执行后：\n" +
                            "- 此备份数据将*永久丢失*\n" +
                            "- 删除后数据*不可恢复*\n" +
                            "\n" +
                            "【当前备份信息】\n" +
                            "+ 备份ID：$num\n" +
                            "+ 备注名：${backup.name}\n" +
                            "+ 备份时间：${formatTime(backup.time)}\n" +
                            "+ 备份大小：${backup.content.length}\n" +
                            "\n" +
                            "如您确认备份不再需要，请再次执行删除指令以完成操作"
                        ) ?: return

                        StorageManager.setBackup(id, num - 1, null)

                        return sendQuoteReply("成功删除存储库 ${bucketInfo(id)} 的备份槽位 $num！")
                    }

                    // 常规备份指令
                    val password = args.getOrNull(3)?.content
                    checkPassword(id, password, userID, isAdmin) ?: return  // 验证密码

                    val num = args.getOrNull(2)?.content?.toIntOrNull()
                        ?.takeIf { it in 1..3 }
                        ?: return sendQuoteReply("编号无效：备份编号仅支持 1-3")

                    val bucketContent = StorageManager.getBucketRawContent(id)
                    if (bucketContent.isNullOrEmpty()) {
                        sendQuoteReply("备份失败：存储库 ${bucketInfo(id)} 当前数据为空")
                        return
                    }
                    var backup = StorageManager.getBackup(id, num - 1)
                    if (backup == null) {
                        val newBackup = StorageManager.Backup(
                            name = "备份$num",
                            time = System.currentTimeMillis(),
                            content = bucketContent,
                        )
                        StorageManager.setBackup(id, num - 1, newBackup)
                        sendQuoteReply(
                            "✅ 在槽位 $num 创建新备份成功！\n" +
                            "存储库ID：$id\n" +
                            "存储库名称：${bucketIdToName(id)}\n" +
                            "\n" +
                            "备份名称：${newBackup.name}\n" +
                            "备份时间：${formatTime(newBackup.time)}\n" +
                            "备份大小：${newBackup.content.length}\n" +
                            "\n" +
                            "注：备份的备注名可通过下方指令修改\n" +
                            "${commandPrefix}bk set <ID/名称> backup <备份编号> <新名称>" +
                            (if (subject is Group && password != null) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                        )
                    } else {
                        requestUserConfirmation(userID, args.content,
                            " +++ℹ️ 二次确认提示 ℹ️️+++\n" +
                            "您尝试备份存储库 ${bucketInfo(id)}，但槽位 $num 已存在其他备份数据，此操作执行后：\n" +
                            "- 旧备份数据将被新备份覆盖\n" +
                            "- 覆盖后旧数据*不可恢复*\n" +
                            "\n" +
                            "【旧备份信息】\n" +
                            "+ 备份ID：$num\n" +
                            "+ 备注名：${backup.name}\n" +
                            "+ 备份时间：${formatTime(backup.time)}\n" +
                            "+ 备份大小：${backup.content.length}\n" +
                            "\n" +
                            "如您确认旧备份不再需要，请再次执行备份指令以完成操作"
                        ) ?: return

                        backup = backup.copy(time = System.currentTimeMillis(), content = bucketContent)
                        StorageManager.setBackup(id, num - 1, backup)
                        sendQuoteReply(
                            "✅ 成功更新槽位 $num 的备份！\n" +
                            "存储库ID：$id\n" +
                            "存储库名称：${bucketIdToName(id)}\n" +
                            "\n" +
                            "备份名称：${backup.name}\n" +
                            "备份时间：${formatTime(backup.time)}\n" +
                            "备份大小：${backup.content.length}" +
                            (if (subject is Group && password != null) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                        )
                    }
                }

                "rollback", "回滚"-> {   // 从备份回滚数据
                    val id = checkBucketNameOrID(args[1].content, "回滚") ?: return
                    storageLock = lockBucket(id) ?: return

                    val password = args.getOrNull(3)?.content
                    checkPassword(id, password, userID, isAdmin) ?: return  // 验证密码

                    val num = args.getOrNull(2)?.content?.toIntOrNull()
                        ?.takeIf { it in 1..3 }
                        ?: return sendQuoteReply("编号无效：备份编号仅支持 1-3")

                    val backup = StorageManager.getBackup(id, num - 1)
                        ?: return sendQuoteReply("回滚失败：备份编号 $num 没有任何数据")

                    requestUserConfirmation(userID, args.content,
                        " +++⚠️ 危险操作警告 ⚠️+++\n" +
                        "您正在回滚存储库 ${bucketInfo(id)}，请再次确认以下信息：\n" +
                        "- 存储库的*主存储数据*将被指定备份覆盖\n" +
                        "- 覆盖后旧数据*不可恢复*\n" +
                        "- 建议先备份此版本后再执行回滚\n" +
                        "\n" +
                        "【待回滚备份信息】\n" +
                        "+ 备份ID：$num\n" +
                        "+ 备注名：${backup.name}\n" +
                        "+ 备份时间：${formatTime(backup.time)}\n" +
                        "+ 备份大小：${backup.content.length}\n" +
                        "\n" +
                        "如您确认无误，请再次执行回滚指令以完成操作"
                    ) ?: return

                    StorageManager.setBucketRawContent(id, backup.content)
                    sendQuoteReply(
                        "[ROLLBACK] 成功将存储库 ${bucketInfo(id)} 回滚至槽位 $num 的备份：${backup.name}（${formatTime(backup.time)}）！" +
                        (if (subject is Group && password != null) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                    )
                }

                "delete", "删除"-> {   // 永久删除存储库
                    val id = checkBucketNameOrID(args[1].content, "删除") ?: return
                    storageLock = lockBucket(id) ?: return
                    val ownerID = StorageManager.getBucket(id)?.userID
                    val isOwner = userID == ownerID
                    val forceDelete = args.getOrNull(2)?.content == "force"
                    if (!isOwner) {
                        if (!isAdmin) {
                            sendQuoteReply("无权删除此存储库，如需删除请联系所有者：$ownerID。如果您对此存储库存在疑问，请联系指令管理员")
                            return
                        }
                        if (!forceDelete) {
                            sendQuoteReply("操作保护：您并非该存储库所有者。若需要管理员强制删除，请在指令末尾添加 force 参数")
                            return
                        }
                    }
                    val projects = StorageManager.getBucket(id)?.projects?.joinToString(" ") ?: ""

                    requestUserConfirmation(userID, args.content,
                        " +++🛑 高危操作警告 🛑+++\n" +
                        "您正在删除存储库 ${bucketInfo(id)}，删除前请确保您已知晓：\n" +
                        "- 此存储库所有数据将*永久丢失*\n" +
                        "- 删除操作*不可恢复*\n" +
                        (if (projects.isNotEmpty()) "⚠️ 以下项目正在使用此存储库，存储库销毁后可能会影响项目运行：$projects\n" else "") +
                        "\n" +
                        "如您确认无误，请再次执行删除指令以完成操作"
                    ) ?: return

                    StorageManager.deleteBucket(id)
                    sendQuoteReply("删除存储库 $id 成功" + (if (projects.isNotEmpty()) "，项目已解除关联" else ""))
                }

                else-> {
                    sendQuoteReply("[参数不匹配]\n请使用「${commandPrefix}bk help」来查看指令帮助")
                }
            }
        } catch (_: IndexOutOfBoundsException) {
            sendQuoteReply("[参数不足]\n请使用「${commandPrefix}bk help」来查看指令帮助")
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply("[指令执行未知错误]\n请联系管理员查看后台：${e::class.simpleName}(${e.message})")
        } finally {
            storageLock?.release()
        }
    }

    suspend fun CommandSender.checkBucketNameOrID(content: String, optionName: String): Long? {
        val id = bucketNameToId(content) ?: content.toLongOrNull() ?: -1
        if (!StorageManager.bucketSlotExists(id)) {
            sendQuoteReply("名称或ID不存在：$content\n请使用「${commandPrefix}bk list」来查看存储库列表")
            return null
        }
        if (isBucketEmpty(id)) {
            sendQuoteReply("${optionName}失败：存储库 $id 处于空置状态")
            return null
        }
        return id
    }

    suspend fun CommandSender.checkPassword(id: Long, password: String?, userID: String, isAdmin: Boolean): Boolean? {
        val bucket = StorageManager.getBucket(id) ?: return null
        val storedHashed = bucket.password.takeIf { it.isNotEmpty() } ?: return null

        val isOwner = userID == bucket.userID
        val passwordCorrect = password != null && Security.verifyPassword(password, storedHashed)

        // 关闭了管理员的访问权限，必须要求密码
        if (!isOwner && !passwordCorrect) {
            sendQuoteReply("拒绝访问：存储库密码错误")
            return null
        }
        return true
    }

    data class ProjectContext(
        val id: Long,
        val projectName: String,
        val projectsList: MutableList<String>,
    ) {
        /** 重新读取关联项目列表 */
        fun refreshProjects() {
            projectsList.clear()
            projectsList.addAll(StorageManager.getBucket(id)?.projects ?: emptyList())
        }
    }
    suspend fun CommandSender.prepareProjectContext(
        args: MessageChain,
        userID: String
    ): ProjectContext? {
        val projectName = args[1].content
        if (!PastebinData.pastebin.contains(projectName)) {
            val fuzzy = FuzzySearch.fuzzyFind(PastebinData.pastebin, projectName)
            sendQuoteReply(
                "未知的名称：$projectName\n" +
                if (fuzzy.isNotEmpty()) {
                    "🔍 模糊匹配结果->\n" + fuzzy.take(20).joinToString(separator = " ") +
                    "\n或使用「${commandPrefix}pb list」来查看完整列表"
                } else "请使用「${commandPrefix}pb list」来查看完整列表"
            )
            return null
        }
        val projectOwnerID = PastebinData.pastebin[projectName]?.get("userID")
        val isOwner = userID == projectOwnerID
        val isAdmin = PastebinConfig.admins.contains(userID)
        val isCollaborator = isCollaborator(projectName, userID)
        if (!isOwner && !isAdmin && !isCollaborator) {
            sendQuoteReply("无权修改此项目，如需修改请联系所有者：$projectOwnerID")
            return null
        }
        val id = checkBucketNameOrID(args[2].content, "操作") ?: return null
        val projects = StorageManager.getBucket(id)?.projects?.joinToString(" ") ?: ""
        val projectsList = projects.split(" ").filter { it.isNotEmpty() }.toMutableList()
        return ProjectContext(id, projectName, projectsList)
    }


    fun linkedBucketId(projectName: String): List<Long> =
        StorageManager.linkedBucketIds(projectName)

    fun bucketIdsToNames(ids: List<Long>): String =
        ids.joinToString(" ") { bucketIdToName(it) ?: "【ID${it}名称错误】" }

    fun removeProjectFromBucket(name: String) =
        StorageManager.removeProjectFromBuckets(name)

    fun bucketInfo(id: Long): String =
        "${bucketIdToName(id)}($id)"

    fun projectsCount(id: Long): Int =
        StorageManager.getBucket(id)?.projects?.size ?: 0

    fun isBucketEmpty(id: Long): Boolean =
        StorageManager.isBucketEmpty(id)

    fun bucketIdToName(id: Long): String? =
        StorageManager.bucketIdToName(id)

    fun bucketNameToId(name: String): Long? =
        StorageManager.findBucketByName(name)?.id

    fun formatTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
