package site.tiedan.command.pastebin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.ERROR_MSG_MAX_LENGTH
import site.tiedan.MiraiCompilerFramework.getNickname
import site.tiedan.MiraiCompilerFramework.parseUserID
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.trimToMaxLength
import site.tiedan.command.pastebin.PbCollaborator.isCollaborator
import site.tiedan.command.pastebin.PbCollaborator.normalizeCollaborators
import site.tiedan.config.PastebinConfig
import site.tiedan.core.CodeCacheManager
import site.tiedan.core.StorageLockGuard.lockProject
import site.tiedan.core.StorageManager
import site.tiedan.data.PastebinData
import site.tiedan.module.ExecutionLock
import site.tiedan.module.Statistics
import site.tiedan.module.TagManager
import site.tiedan.utils.FuzzySearch
import site.tiedan.utils.PastebinUrlHelper
import site.tiedan.utils.PastebinUrlHelper.checkUrl
import site.tiedan.utils.PastebinUrlHelper.supportedUrls
import java.io.File

/*
 * # PB更新数据类指令
 *
 * @author tiedanGH
 */

/** 添加Pastebin项目 */
internal suspend fun CommandSender.pbAdd(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val isAdmin = ctx.isAdmin
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
    val code = preCheckUrl(url, null) ?: return
    PastebinData.pastebin[name] =
        mutableMapOf(
            "author" to author,
            "userID" to userID,
            "language" to language,
            "url" to url,
            "stdin" to stdin
        )
    // 能缓存直接落盘，省去首次执行的抓取
    val cached = PastebinUrlHelper.enableCache(url)
    if (cached) CodeCacheManager.put(name, code)
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
            "示例输入：${stdin}" +
            if (cached) "\n\n📦 代码已预读并保存至缓存" else ""
        )
    }
    PastebinData.save()
}

/** 修改项目属性 */
internal suspend fun CommandSender.pbSet(ctx: PbContext) {
    val args = ctx.args
    val userID = ctx.userID
    val isAdmin = ctx.isAdmin
    val name = args[1].content
    if (rejectAlias(name, "修改属性")) return
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
    val isOwner = userID == ownerID
    val isCollaborator = isCollaborator(name, userID)
    if (!isOwner && !isAdmin && !isCollaborator) {
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
        "标签" to "tag",
        "示例输入" to "stdin",
        "所有者ID" to "userID",
        "协作者" to "collaborators",
        "collab" to "collaborators",
        // 启用拓展功能
        "隐藏链接" to "hide",
        "锁定" to "lock",
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
            "tag（标签）\n" +
            "stdin（示例输入）\n" +
            "userID（所有者ID）\n" +
            "collab（协作者）\n" +
            "---启用拓展功能---\n" +
            "hide（隐藏链接）\n" +
            "lock（锁定）\n" +
            "util（辅助文件）\n" +
            "format（输出格式）\n" +
            "storage（数据存储）\n" +
            "base64（图片base64）"
        )
        return
    }
    if (isCollaborator && !isOwner && !isAdmin && (option == "name" || option == "userID" || option == "collaborators")) {
        sendQuoteReply("无权修改此配置项：协作者只能修改除 name（名称）、userID（所有者）和 collaborators（协作者）以外的项目配置")
        return
    }
    if (option == "format" && args.size > 5) {
        sendQuoteReply("修改失败：format中仅能包含两个参数（输出格式，图片宽度/配置存储）")
        return
    }
    if (option !in arrayOf("stdin", "format", "collaborators", "tag") && args.size > 4) {
        sendQuoteReply("修改失败：$option 参数中不能包含空格！")
        return
    }
    if (option !in arrayOf("stdin", "util", "alias", "lock") && content.isEmpty()) {
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
    var fetchedCode: String? = null
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
        fetchedCode = preCheckUrl(content, name) ?: return
    }
    // 改名会搬动存储数据、锁定状态后禁用执行，要等正在执行的进程结束
    if (option == "name" || option == "lock") {
        ctx.storageLock = lockProject(name) ?: return
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
            // 转移存储数据（含其他平台）
            StorageManager.renameProjectStorage(name, content)
            // 转移存储库关联
            StorageManager.renameProjectInBuckets(name, content)
            // 转移缓存数据
            CodeCacheManager.rename(name, content)
            // 转移统计数据
            Statistics.renameProject(name, content)
        }
        "alias"-> {
            PastebinData.alias.entries.removeIf { it.value == name }
            if (content.isNotEmpty()) {
                PastebinData.alias[content] = name
            }
        }
        "userID"-> {
            val id = parseUserID(content)
            if (id == null) {
                sendQuoteReply("转移失败：输入的 userID 格式不正确，应为纯数字或带平台前缀 kook_123")
                return
            }
            val targetName = getNickname(id)
            if (targetName == null) {
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
        "tag"-> {
            if (TagManager.isClearWord(content)) {
                TagManager.clearProjectTags(name)
                content = "无"
            } else {
                val tags = TagManager.parse(content)
                if (tags.isEmpty()) {
                    sendQuoteReply("[参数不足] 请提供至少一个标签，多个标签用空格分隔。清空标签请填「无」")
                    return
                }
                val unknown = TagManager.setProjectTags(name, tags)
                if (unknown.isNotEmpty()) {
                    sendQuoteReply(buildString {
                        append("·🏷️ 标签设置结果：")
                        append("\n❌ 无效标签：${unknown.joinToString(" ")}")
                        append(TagManager.fuzzyMatchLine(unknown))
                        append("\n请使用「${commandPrefix}pb tag」查看可用标签")
                    })
                    return
                }
                content = TagManager.projectTags(name).joinToString(" ")
            }
        }
        "collaborators"-> {
            when (content.lowercase()) {
                "clear", "none", "empty", "清空", "无" -> {
                    content = "无"
                    PastebinData.pastebin[name]?.remove("collaborators")
                }
                else -> {
                    val ids = content
                        .split(",", "，", " ")
                        .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }

                    if (ownerID in ids) {
                        sendQuoteReply("修改失败：项目所有者无需重复添加为协作者")
                        return
                    }
                    val normalized = normalizeCollaborators(ownerID ?: "", ids)
                    if (normalized == null) {
                        sendQuoteReply("修改失败：协作者列表为空或格式错误，请输入全部账号ID，多个ID可使用空格、逗号分隔。删除全部协作者请输入“无”")
                        return
                    }

                    PastebinData.pastebin[name]?.set("collaborators", normalized)
                    content = normalized
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
                    sendQuoteReply("无效的配置项：请设置 开启/关闭 隐藏链接功能")
                    return
                }
            }
        }
        "lock"-> {
            if (ExecutionLock.isClearWord(content)) {
                ExecutionLock.set(name, null)
                content = ExecutionLock.CLEARED_DESC
            } else {
                val mode = ExecutionLock.parse(content)
                if (mode == null) {
                    sendQuoteReply(
                        "无效的配置项：锁定范围仅支持\n" +
                        "private（私信）　禁止私信执行\n" +
                        "group（群聊）　禁止群聊执行\n" +
                        "all（全部）　禁止全部执行\n" +
                        "解除锁定请填「无」或留空"
                    )
                    return
                }
                ExecutionLock.set(name, mode)
                content = mode.desc
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
                        StorageManager.removeProjectStorage(name)
                    }
                    in arrayListOf("clear","清空")-> {
                        content = "$format（清空存储）"
                        StorageManager.removeProjectStorage(name)
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
                    StorageManager.removeProjectStorage(name)
                }
                in arrayListOf("clear","清空")-> {
                    content = "清空"
                    StorageManager.removeProjectStorage(name)
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
                    StorageManager.removeProjectStorage(name)
                }
                else-> {
                    sendQuoteReply("无效的配置项：请设置 开启/关闭 输入图片转base64")
                    return
                }
            }
        }
        else -> {
            if (option == "url") {
                // 已取到新代码，直接写入缓存，无需等待下次执行；新链接不支持缓存时旧缓存必须清除
                val newCode = fetchedCode?.takeIf { PastebinUrlHelper.enableCache(content) }
                val hadCache = CodeCacheManager.contains(name)
                val oldUrl = PastebinData.pastebin[name]?.get("url").orEmpty()
                // 替换前把旧缓存保留为上一版本，停服网站项目的唯一副本不会随之丢失
                val preservation = CodeCacheManager.replaceOnUrlChange(name, oldUrl, newCode)
                additionalOutput = buildString {
                    when {
                        newCode != null -> append("🔗 源代码URL已修改，新代码成功获取并保存至缓存\n")
                        hadCache -> append("🔗 源代码URL已修改，代码缓存已清除\n")
                    }
                    when (preservation) {
                        CodeCacheManager.Preservation.SAVED ->
                            append("🗂 旧代码已保留为上一版本，可使用「${commandPrefix}pb export $name prev」取回\n")
                        CodeCacheManager.Preservation.KEPT_DISCONTINUED ->
                            append("🗂 上一版本保留的是停服网站的旧代码，本次替换下的代码未覆盖它，可从原链接重新获取\n")
                        CodeCacheManager.Preservation.NONE -> {}
                    }
                }
            }
            PastebinData.pastebin[name]?.set(option, content)
        }
    }
    if (option == "hide") {
        sendQuoteReply("${additionalOutput}成功将 $name 的源代码标记为 $content")
    } else if (option == "lock") {
        sendQuoteReply("${additionalOutput}成功修改 $name 的锁定状态：$content")
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
}

/**
 * 项目链接预检
 * - 链接不得与其他项目重复
 * - 必须能获取到非空内容，避免无效链接
 *
 * @param project 正在修改的项目；新增项目时传 null
 * @return 预检通过返回抓取到的代码；未通过时返回 null
 */
private suspend fun CommandSender.preCheckUrl(url: String, project: String?): String? {
    val duplicated = PastebinData.pastebin.entries
        .firstOrNull { (name, data) -> name != project && data["url"] == url }
    if (duplicated != null) {
        sendQuoteReply(
            "[链接重复] 此链接已被项目 ${duplicated.key} 使用，请直接执行已有项目。\n" +
            "如需新建项目，请修改后重新上传代码"
        )
        return null
    }

    sendMessage("⏳ 正在获取代码，请稍候...")
    val code = try {
        withContext(Dispatchers.IO) { PastebinUrlHelper.get(url) }
    } catch (e: PastebinUrlHelper.ServiceDiscontinuedException) {
        sendQuoteReply("[链接无效]\n${e.message}")
        return null
    } catch (e: Exception) {
        sendQuoteReply(
            "[获取代码失败] 请确认此链接可正常访问或重新尝试\n" +
            "报错类别：${e::class.simpleName}\n" +
            "报错信息：${trimToMaxLength(e.message.toString(), ERROR_MSG_MAX_LENGTH).first}"
        )
        return null
    }
    if (code.isBlank()) {
        sendQuoteReply("[链接无效] 获取到的内容为空，请确认代码已正确上传")
        return null
    }
    return code
}
