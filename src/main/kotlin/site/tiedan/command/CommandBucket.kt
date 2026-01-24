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
import site.tiedan.MiraiCompilerFramework.Command
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.getNickname
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.pendingCommand
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.command.CommandPastebin.sendStorageMail
import site.tiedan.config.MailConfig
import site.tiedan.config.PastebinConfig
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinBucket
import site.tiedan.data.PastebinData
import site.tiedan.format.JsonProcessor
import site.tiedan.format.MarkdownImageGenerator
import site.tiedan.utils.FuzzySearch
import site.tiedan.utils.Security
import java.io.File
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
        Command("bk storage <ID/名称> [密码] [备份编号]", "bk 存储 <ID/名称> [密码] [备份编号]", "查询存储库数据", 1),
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

        val userID = this.user?.id ?: 10000
        val userName = this.name
        val isAdmin = PastebinConfig.admins.contains(userID)

        if (pendingCommand[userID]?.let { it != args.content } == true) {
            pendingCommand.remove(userID)
            sendQuoteReply("指令不一致，操作已取消")
        }

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
                        val bucketList = PastebinBucket.bucket.entries.joinToString("\n") { (id, data) ->
                            if (isBucketEmpty(id)) "$id. [空槽位]" else "$id. ${data["name"]}"
                        }
                        sendQuoteReply(" ·bucket存储库列表：\n$bucketList")
                    } else {
                        val showBackups = option in arrayOf("全部", "all", "备份", "backup")
                        val markdownResult = MarkdownImageGenerator.processMarkdown(
                            name = null,
                            MarkdownImageGenerator.generateBucketListHtml(showBackups),
                            width = "750"
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
                }

                "info", "信息"-> {   // 查看存储库信息
                    val id = checkBucketNameOrID(args[1].content, "查看") ?: return
                    val data = PastebinBucket.bucket[id].orEmpty()
                    val info = buildString {
                        appendLine("存储库ID：$id")
                        appendLine("名称：${data["name"]}")
                        appendLine("所有者：${data["owner"]}(${data["userID"]})")
                        appendLine("关联项目(${projectsCount(id)})：${data["projects"]}")
                        val lock = if (data["encrypt"] == "true") " 🔐" else ""
                        appendLine("存储大小：${data["content"]?.length}$lock")
                        val backups = PastebinBucket.backups[id].orEmpty()
                        appendLine("备份信息：")
                        backups.forEach { backup ->
                            appendLine(
                                "- " + (backup?.let { "${formatTime(it.time)} ${it.name}" } ?: "空备份")
                            )
                        }
                        if(data["desc"]?.isNotEmpty() == true) {
                            appendLine("------- [简介] -------\n${data["desc"]}")
                        }
                    }
                    sendQuoteReply(info)
                }

                "storage", "存储"-> {   // 查询存储库数据
                    val id = checkBucketNameOrID(args[1].content, "查询") ?: return

                    val password = args.getOrNull(2)?.content
                    checkPassword(id, password, userID, isAdmin) ?: return  // 验证密码

                    val bucket = PastebinBucket.bucket[id].orEmpty()
                    if (bucket["encrypt"] == "true") {
                        sendQuoteReply("🔐 此存储库启用了数据加密，为保证数据安全，查询功能被禁用")
                        return
                    }

                    val mail = args.getOrNull(2)?.content == "邮件" || args.getOrNull(2)?.content == "mail"
                    if (MailConfig.enable && mail && bucket.isNotEmpty()) {
                        val allBackupData = PastebinBucket.backups[id]
                            ?.mapIndexed { index, backup ->
                                "【备份${index + 1}数据】\n" + (
                                        backup?.let { "${formatTime(it.time)} ${it.name}\n【备份内容】\n${it.content}" }
                                            ?: "空备份"
                                        )
                            }
                            ?.joinToString("\n\n")
                        var output = "【查询存储库】$id\n" +
                                "【名称】${bucket["name"]}\n" +
                                "【存储库大小】${bucket["content"]?.length}\n" +
                                "【关联项目】${bucket["projects"]}\n" +
                                "\n" +
                                "【存储库内容】\n${bucket["content"]}\n" +
                                "\n\n" +
                                allBackupData
                        logger.info("请求使用邮件发送结果：$id")
                        sendStorageMail(this, output, userID, id.toString())
                        return
                    }

                    if (!PastebinConfig.enable_ForwardMessage) {
                        sendQuoteReply("当前未开启转发消息，仅能通过邮件查询存储数据")
                        return
                    }
                    val backupID = args.getOrNull(3)?.content?.toIntOrNull() ?: 0
                    val backup = (backupID - 1).takeIf { backupID > 0 }?.let { PastebinBucket.backups[id]?.get(it) }
                    try {
                        val forward = buildForwardMessage(subject!!) {
                            displayStrategy = object : ForwardMessage.DisplayStrategy {
                                override fun generateTitle(forward: RawForwardMessage): String = "存储库查询"
                                override fun generateBrief(forward: RawForwardMessage): String = "[存储数据]"
                                override fun generatePreview(forward: RawForwardMessage): List<String> =
                                    listOf("查询ID：$id", "查询名称：${bucket["name"]}") +
                                    if (backupID != 0) listOf("查询备份：$backupID") else emptyList()

                                override fun generateSummary(forward: RawForwardMessage): String =
                                    if (bucket.isEmpty()) "查询失败：存储库不存在"
                                    else if (backupID != 0 && backup == null) "查询失败：备份ID不存在"
                                    else "查询成功"
                            }
                            subject!!.bot named "存储库查询" says
                                    "【查询ID】$id\n" +
                                    "【查询名称】${bucket["name"]}\n" +
                                    "【存储库大小】${bucket["content"]?.length}\n" +
                                    "【关联项目】${bucket["projects"]}"
                            if (backupID == 0) {
                                subject!!.bot named "存储库查询" says
                                        if (bucket["content"].isNullOrEmpty()) "[警告] 查询成功，但查询的存储数据为空"
                                        else bucket["content"] as String
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
                        val length = "数据长度：${bucket["content"]?.length}"
                        sendQuoteReply("[内容过长] $length。如需查看完整内容请使用指令\n" +
                                "${commandPrefix}pb storage $name mail\n将结果发送邮件至您的邮箱")
                    } catch (e: Exception) {
                        logger.warning(e)
                        sendQuoteReply("[转发消息错误]\n生成或发送转发消息时发生错误，请联系管理员查看后台，简要错误信息：${e.message}")
                    }
                }

                "create", "创建"-> {   // 创建新存储库
                    if (PastebinData.pastebin.none { it.value["userID"] == userID.toString() }) {
                        sendQuoteReply("创建失败：请先创建一个项目，然后再创建存储库")
                        return
                    }
                    val name = args[1].content
                    val password = args[2].content
                    if (name.all { it.isDigit() }) {
                        sendQuoteReply("创建失败：存储库名称不能为纯数字")
                        return
                    }
                    if (nameToID(name) != null) {
                        sendQuoteReply("创建失败：名称 $name 已存在")
                        return
                    }
                    val id = generateSequence(1L) { it + 1 }.first { isBucketEmpty(it) }
                    PastebinBucket.bucket[id] = mutableMapOf(
                        "name" to name,
                        "password" to Security.hashPassword(password),
                        "owner" to userName,
                        "userID" to userID.toString(),
                        "projects" to "",
                        "desc" to "",
                        "content" to "",
                    )
                    PastebinBucket.backups[id] = mutableListOf(null, null, null)
                    PastebinBucket.save()
                    sendQuoteReply(
                        "创建新存储库成功！\n" +
                        "存储库ID：$id\n" +
                        "名称：$name\n" +
                        "密码：长度为 ${password.length}\n" +
                        "所有者：$userName($userID)" +
                        (if (subject is Group) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                    )
                }

                "set", "修改"-> {   // 修改存储库属性
                    val id = checkBucketNameOrID(args[1].content, "修改") ?: return
                    var option = args[2].content
                    var content = args.drop(3).joinToString(separator = " ")
                    var additionalOutput = ""
                    val ownerID = PastebinBucket.bucket[id]?.get("userID")
                    val isOwner = userID.toString() == ownerID
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
                            "未知的配置项：$option\n" +
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
                            PastebinBucket.bucket[id]?.set("password", Security.hashPassword(newPassword))
                        }
                        "userID"-> {
                            if (content.toLongOrNull() == null) {
                                sendQuoteReply("转移失败：输入的 userID 不是整数")
                                return
                            }
                            val targetName = getNickname(this, content.toLong())
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

                            PastebinBucket.bucket[id]?.set("owner", targetName)
                            PastebinBucket.bucket[id]?.set("userID", content)
                        }
                        "backup"-> {
                            val paras = content.split(" ")
                            val num = paras.getOrNull(0)?.toIntOrNull()?.minus(1)
                                ?.takeIf { it in 0..2 }
                                ?: return sendQuoteReply("备份编号无效：备份编号仅支持 1-3")
                            val newName = paras.getOrNull(1)
                                ?: return sendQuoteReply("修改失败：请输入修改后的新备份名称")
                            val backup = PastebinBucket.backups[id]?.get(num)
                                ?: return sendQuoteReply("修改失败：备份编号 ${num + 1} 尚未初始化")

                            content = "$newName（备份ID ${num + 1}）"
                            backup.apply { name = newName }
                        }
                        "encrypt"-> {
                            if (PastebinBucket.bucket[id]?.get("encrypt") == "true") {
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

                            PastebinBucket.bucket[id]?.set("encrypt", "true")
                            val currentStorage = PastebinBucket.bucket[id]?.get("content") ?: ""
                            val encryptedStorage = Security.encrypt(currentStorage, ExtraData.key)
                            PastebinBucket.bucket[id]?.set("content", encryptedStorage)

                            PastebinBucket.backups[id]?.forEachIndexed { index, data->
                                if (data != null) {
                                    val currentStorage = data.content
                                    data.content = Security.encrypt(currentStorage, ExtraData.key)
                                }
                            }
                        }
                        else -> {
                            PastebinBucket.bucket[id]?.set(option, content)
                        }
                    }
                    PastebinBucket.save()
                    if (option == "userID") {
                        sendQuoteReply("${additionalOutput}成功将存储库 ${bucketInfo(id)} 的所有权转移至 $content")
                    } else {
                        sendQuoteReply("${additionalOutput}成功将存储库 ${bucketInfo(id)} 的 $option 参数修改为 $content")
                    }
                }

                "add", "添加"-> {   // 将存储库添加至项目
                    val ctx = prepareProjectContext(args, userID) ?: return

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
                    PastebinBucket.bucket[ctx.id]!!["projects"] = ctx.projectsList.joinToString(" ")
                    PastebinBucket.save()
                    sendQuoteReply(
                        "成功将存储库 ${bucketInfo(ctx.id)} 关联到项目 ${ctx.projectName}" +
                        (if (subject is Group && password != null) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                    )
                }

                "remove", "rm", "移除"-> {   // 将存储库从项目移除
                    val ctx = prepareProjectContext(args, userID) ?: return
                    if (ctx.projectsList.remove(ctx.projectName).not()) {
                        sendQuoteReply("移除失败：存储库 ${ctx.id} 未关联此项目 ${ctx.projectName}")
                        return
                    }
                    PastebinBucket.bucket[ctx.id]!!["projects"] = ctx.projectsList.joinToString(" ")
                    PastebinBucket.save()
                    sendQuoteReply("成功将存储库 ${bucketInfo(ctx.id)} 与项目 ${ctx.projectName} 解除关联")
                }

                "backup", "备份"-> {   // 备份存储库数据
                    val id = checkBucketNameOrID(args[1].content, "备份") ?: return

                    // 删除备份指令
                    if (args.getOrNull(2)?.content == "del") {
                        val num = args.getOrNull(3)?.content?.toIntOrNull()
                            ?.takeIf { it in 1..3 }
                            ?: return sendQuoteReply("编号无效：备份编号仅支持 1-3")

                        val password = args.getOrNull(4)?.content
                        checkPassword(id, password, userID, isAdmin) ?: return  // 验证密码

                        val backup = PastebinBucket.backups[id]?.get(num - 1)
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

                        PastebinBucket.backups[id]?.set(num - 1, null)
                        PastebinBucket.save()

                        return sendQuoteReply("成功删除存储库 ${bucketInfo(id)} 的备份槽位 $num！")
                    }

                    // 常规备份指令
                    val password = args.getOrNull(3)?.content
                    checkPassword(id, password, userID, isAdmin) ?: return  // 验证密码

                    val num = args.getOrNull(2)?.content?.toIntOrNull()
                        ?.takeIf { it in 1..3 }
                        ?: return sendQuoteReply("编号无效：备份编号仅支持 1-3")

                    val bucketContent = PastebinBucket.bucket[id]?.get("content")
                    if (bucketContent.isNullOrEmpty()) {
                        sendQuoteReply("备份失败：存储库 ${bucketInfo(id)} 当前数据为空")
                        return
                    }
                    var backup = PastebinBucket.backups[id]?.get(num - 1)
                    if (backup == null) {
                        val newBackup = PastebinBucket.BackupInfo(
                            name = "备份$num",
                            time = System.currentTimeMillis(),
                            content = bucketContent,
                        )
                        PastebinBucket.backups[id]?.set(num - 1, newBackup)
                        sendQuoteReply(
                            "在槽位 $num 创建新备份成功！\n" +
                            "存储库ID：$id\n" +
                            "存储库名称：${bucketIDToName(id)}\n" +
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

                        backup.time = System.currentTimeMillis()
                        backup.content = bucketContent
                        sendQuoteReply(
                            "成功更新槽位 $num 的备份！\n" +
                            "存储库ID：$id\n" +
                            "存储库名称：${bucketIDToName(id)}\n" +
                            "\n" +
                            "备份名称：${backup.name}\n" +
                            "备份时间：${formatTime(backup.time)}\n" +
                            "备份大小：${backup.content.length}" +
                            (if (subject is Group && password != null) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                        )
                    }
                    PastebinBucket.save()
                }

                "rollback", "回滚"-> {   // 从备份回滚数据
                    val id = checkBucketNameOrID(args[1].content, "回滚") ?: return

                    val password = args.getOrNull(3)?.content
                    checkPassword(id, password, userID, isAdmin) ?: return  // 验证密码

                    val num = args.getOrNull(2)?.content?.toIntOrNull()
                        ?.takeIf { it in 1..3 }
                        ?: return sendQuoteReply("编号无效：备份编号仅支持 1-3")

                    val backup = PastebinBucket.backups[id]?.get(num - 1)
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

                    PastebinBucket.bucket[id]?.set("content", backup.content)
                    PastebinBucket.save()
                    sendQuoteReply(
                        "[ROLLBACK] 成功将存储库 ${bucketInfo(id)} 回滚至槽位 $num 的备份：${backup.name}（${formatTime(backup.time)}）！" +
                        (if (subject is Group && password != null) "\n\n⚠️ 您正在群聊进行操作，密码存在极高泄露风险，建议尽快修改密码！" else "")
                    )
                }

                "delete", "删除"-> {   // 永久删除存储库
                    val id = checkBucketNameOrID(args[1].content, "删除") ?: return
                    val ownerID = PastebinBucket.bucket[id]?.get("userID")
                    val isOwner = userID.toString() == ownerID
                    if (!isOwner && !isAdmin) {
                        sendQuoteReply("无权删除此存储库，如需删除请联系所有者：$ownerID。如果您对此存储库存在疑问，请联系指令管理员")
                        return
                    }
                    val projects = PastebinBucket.bucket[id]?.get("projects") ?: ""

                    requestUserConfirmation(userID, args.content,
                        " +++🛑 高危操作警告 🛑+++\n" +
                        "您正在删除存储库 ${bucketInfo(id)}，删除前请确保您已知晓：\n" +
                        "- 此存储库所有数据将*永久丢失*\n" +
                        "- 删除操作*不可恢复*\n" +
                        (if (projects.isNotEmpty()) "⚠️ 以下项目正在使用此存储库，存储库销毁后可能会影响项目运行：$projects\n" else "") +
                        "\n" +
                        "如您确认无误，请再次执行删除指令以完成操作"
                    ) ?: return

                    PastebinBucket.bucket[id]?.clear()
                    PastebinBucket.backups[id]?.clear()
                    PastebinBucket.save()
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
        }
    }

    suspend fun CommandSender.checkBucketNameOrID(content: String, optionName: String): Long? {
        val id = nameToID(content) ?: content.toLongOrNull() ?: -1
        if (PastebinBucket.bucket.contains(id).not()) {
            sendQuoteReply("名称或ID不存在：$content\n请使用「${commandPrefix}bk list」来查看存储库列表")
            return null
        }
        if (isBucketEmpty(id)) {
            sendQuoteReply("${optionName}失败：存储库 $id 处于空置状态")
            return null
        }
        return id
    }

    suspend fun CommandSender.checkPassword(id: Long, password: String?, userID: Long, isAdmin: Boolean): Boolean? {
        val data = PastebinBucket.bucket[id] ?: return null
        val storedHashed = data["password"] ?: return null

        val isOwner = userID.toString() == data["userID"]
        val passwordCorrect = password != null && Security.verifyPassword(password, storedHashed)

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
    )
    suspend fun CommandSender.prepareProjectContext(
        args: MessageChain,
        userID: Long
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
        if (userID.toString() != projectOwnerID && !PastebinConfig.admins.contains(userID)) {
            sendQuoteReply("无权修改此项目，如需修改请联系所有者：$projectOwnerID")
            return null
        }
        val id = checkBucketNameOrID(args[2].content, "操作") ?: return null
        val projects = PastebinBucket.bucket[id]?.get("projects") ?: ""
        val projectsList = projects.split(" ").filter { it.isNotEmpty() }.toMutableList()
        return ProjectContext(id, projectName, projectsList)
    }


    fun linkedBucketID(projectName: String): List<Long> {
        return PastebinBucket.bucket
            .filter { (_, innerMap) ->
                val projects = innerMap["projects"] ?: return@filter false
                projects.split(" ").any { it == projectName }
            }
            .keys
            .toList()
    }

    fun bucketIdsToBucketData(ids: List<Long>): List<JsonProcessor.BucketData> {
        return ids.map { id ->
            val bk = PastebinBucket.bucket[id]
            val storage = bk?.get("content") ?: ""
            JsonProcessor.BucketData(
                id = id,
                name = bk?.get("name"),
                content = if (bk?.get("encrypt") == "true") {
                    Security.decrypt(storage, ExtraData.key)
                } else storage
            )
        }
    }

    fun bucketIDsToNames(ids: List<Long>): String {
        return ids.map { id ->
            bucketIDToName(id) ?: "【ID${id}名称错误】"
        }.joinToString(" ")
    }

    fun removeProjectFromBucket(name: String) {
        for ((_, innerMap) in PastebinBucket.bucket) {
            val projects = innerMap["projects"] ?: continue
            val projectList = projects.split(" ").toMutableList()
            projectList.removeAll { it == name }
            innerMap["projects"] = projectList.joinToString(" ")
        }
    }


    fun bucketInfo(id: Long): String =
        "${bucketIDToName(id)}($id)"

    fun projectsCount(id: Long): Int =
        PastebinBucket.bucket[id]?.get("projects")?.split(" ")?.filter { it.isNotBlank() }?.size ?: 0

    fun isBucketEmpty(id: Long): Boolean =
        PastebinBucket.bucket[id]?.isEmpty() != false

    fun bucketIDToName(id: Long): String? =
        PastebinBucket.bucket[id]?.get("name")

    fun nameToID(name: String): Long? =
        PastebinBucket.bucket.entries.find { it.value["name"] == name }?.key

    fun formatTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
