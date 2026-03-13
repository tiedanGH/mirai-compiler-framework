package site.tiedan.command

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.UnsupportedMessage
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.message.data.content
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.Command
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.getNickname
import site.tiedan.MiraiCompilerFramework.imageFolder
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.pendingCommand
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.command.CommandRun.Image_Path
import site.tiedan.config.PastebinConfig
import site.tiedan.data.ImageData
import site.tiedan.data.PastebinData
import site.tiedan.format.MarkdownImageGenerator
import site.tiedan.utils.FuzzySearch
import site.tiedan.utils.DownloadHelper
import site.tiedan.utils.DownloadHelper.downloadFile
import site.tiedan.utils.DownloadHelper.downloadImage
import java.io.File
import kotlin.collections.orEmpty
import kotlin.collections.set

/**
 * # 本地图片操作指令
 *
 * @author tiedanGH
 */
object CommandImage : RawCommand(
    owner = MiraiCompilerFramework,
    primaryName = "image",
    secondaryNames = arrayOf("img", "图片"),
    description = "本地图片操作指令",
    usage = "${commandPrefix}img help"
) {
    private val commandList = arrayOf(
        Command("img list [查询模式]", "图片 列表 [查询模式]", "查看图片列表", 1),
        Command("img info <名称>", "图片 信息 <名称>", "查看图片信息", 1),
        Command("img upload <图片名称> <【图片/URL】>", "图片 上传 <图片名称> <【图片/URL】>", "上传图片至服务器", 1),

        Command("img set <名称> <参数名> <内容>", "图片 修改 <名称> <参数名> <内容>", "修改图片属性", 2),
        Command("img delete <名称>", "图片 删除 <名称>", "删除图片", 2),
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

                "help"-> {   // 查看图片指令帮助（help）
                    val reply = "🖼️ 本地图片操作指令：\n" +
                            commandList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                            "✏️ 更新图片帮助：\n" +
                            commandList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                    sendQuoteReply(reply)
                }

                "帮助"-> {   // 查看图片指令帮助（帮助）
                    val reply = "🖼️ 本地图片操作指令：\n" +
                            commandList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" } +
                            "✏️ 更新图片帮助：\n" +
                            commandList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" }
                    sendQuoteReply(reply)
                }

                "list", "列表"-> {   // 查看图片列表
                    val commandPbList = arrayOf(
                        Command("img list [all]", "pb 列表 [全部]", "图片输出完整列表", 1),

                        Command("img list search <图片名> [所有者]", "pb 列表 搜索 <图片名> [所有者]", "根据条件搜索图片", 2),
                        Command("img list owner <所有者>", "pb 列表 所有者 <所有者>", "根据所有者关键词筛选", 2),
                    )
                    val mode = args.getOrElse(1) { PlainText("all") }.content
                    val params: Array<String?> = args.drop(2).map { it.content }.toTypedArray()
                    when (mode) {
                        "help"-> {
                            var reply = "📜 查看完整列表：\n" +
                                    commandPbList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                                    "🔍 列表搜索与筛选：\n" +
                                    commandPbList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                            sendQuoteReply(reply)
                        }

                        "帮助"-> {
                            var reply = "📜 查看完整列表：\n" +
                                    commandPbList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" } +
                                    "🔍 列表搜索与筛选：\n" +
                                    commandPbList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usageCN}　${it.desc}\n" }
                            sendQuoteReply(reply)
                        }

                        "all", "全部",
                        "owner", "所有者",
                        "search", "搜索"-> {
                            val filter = when (mode) {
                                in arrayOf("owner", "所有者") ->
                                    MarkdownImageGenerator.ImageListFilter(owner = params.getOrNull(0))
                                in arrayOf("search", "搜索") ->
                                    MarkdownImageGenerator.ImageListFilter(
                                        name = params.getOrNull(0),
                                        owner = params.getOrNull(1)
                                    )
                                else ->
                                    MarkdownImageGenerator.ImageListFilter()
                            }
                            val markdownResult = MarkdownImageGenerator.processMarkdown(
                                name = null,
                                MarkdownImageGenerator.generateImageListHtml(filter),
                                width = "1500"
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

                        else-> {
                            var reply = "📜 查看完整列表：\n" +
                                    commandPbList.filter { it.type == 1 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" } +
                                    "🔍 列表搜索与筛选：\n" +
                                    commandPbList.filter { it.type == 2 }.joinToString("") { "${commandPrefix}${it.usage}　${it.desc}\n" }
                            sendQuoteReply("[未知查询方法] $mode\n\n$reply")
                        }
                    }
                }

                "info", "信息"-> {   // 查看图片信息
                    val name = args[1].content
                    if (ImageData.images.contains(name).not()) {
                        val fuzzy = FuzzySearch.fuzzyFind(ImageData.images, name)
                        sendQuoteReply(
                            "未知图片：$name\n" +
                            if (fuzzy.isNotEmpty()) {
                                "🔍 模糊匹配结果->\n" + fuzzy.take(20).joinToString(separator = " ") +
                                "\n或使用「${commandPrefix}img list」来查看完整列表"
                            } else "请使用「${commandPrefix}img list」来查看完整列表"
                        )
                        return
                    }

                    val data = ImageData.images[name].orEmpty()
                    val info = buildString {
                        appendLine("图片名称：$name")
                        appendLine("所有者：${data["owner"]}(${data["userID"]})")
                        appendLine("图片路径：image://$name")
                    }
                    val image = generateImage(name, subject) ?: PlainText("[生成预览图失败]")
                    sendQuoteReply(buildMessageChain {
                        append(info)
                        append(image)
                    })
                }

                "upload", "上传"-> {   // 上传图片至服务器
                    if (PastebinData.pastebin.none { it.value["userID"] == userID.toString() && it.value["format"] != null }) {
                        sendQuoteReply("上传失败：请先创建一个非text输出格式的项目，然后再上传图片")
                        return
                    }

                    val imageName = args[1].content

                    val result = uploadImage(imageName, args[2], force = false)
                    if (!result.success) {
                        sendQuoteReply(result.message)
                        return
                    }

                    ImageData.images[imageName] = mutableMapOf(
                        "owner" to userName,
                        "userID" to userID.toString(),
                    )
                    ImageData.images = ImageData.images.toSortedMap()
                    ImageData.save()

                    sendQuoteReply(
                        "🖼️ 上传图片成功！（用时：${result.duration}秒）\n" +
                        "通过下方快捷路径调用此图片：\n" +
                        "image://$imageName"
                    )
                }

                "set", "修改"-> {   // 修改图片属性
                    val name = args[1].content
                    var option = args[2].content
                    var content = args.drop(3).joinToString(separator = " ")
                    var additionalOutput = ""
                    if (ImageData.images.contains(name).not()) {
                        val fuzzy = FuzzySearch.fuzzyFind(ImageData.images, name)
                        sendQuoteReply(
                            "未知图片：$name\n" +
                            if (fuzzy.isNotEmpty()) {
                                "🔍 模糊匹配结果->\n" + fuzzy.take(20).joinToString(separator = " ") +
                                "\n或使用「${commandPrefix}img list」来查看完整列表"
                            } else "请使用「${commandPrefix}img list」来查看完整列表"
                        )
                        return
                    }
                    val ownerID = ImageData.images[name]?.get("userID")
                    val isOwner = userID.toString() == ownerID
                    if (!isOwner && !isAdmin) {
                        sendQuoteReply("无权修改此图片，如需修改请联系所有者：$ownerID")
                        return
                    }
                    val paraMap = mapOf(
                        "名称" to "name",
                        "图片" to "image",
                        "所有者" to "owner",
                        "所有者ID" to "userID",
                    )
                    option = paraMap.getOrDefault(option, option)
                    if (paraMap.values.contains(option).not()) {
                        sendQuoteReply(
                            "❓ 未知的配置项：$option\n" +
                            "name（名称）\n" +
                            "image（图片）\n" +
                            "owner（所有者）\n" +
                            "userID（所有者ID）"
                        )
                        return
                    }
                    if (content.isEmpty()) {
                        sendQuoteReply("修改失败：修改后的值为空！")
                        return
                    }
                    if (option == "name" && ImageData.images.contains(content)) {
                        sendQuoteReply("修改失败：目标名称 $content 已存在")
                        return
                    }
                    when (option) {
                        "name" -> {
                            val (success, error) = renameImageFile(name, content)
                            if (!success) {
                                sendQuoteReply(error)
                                return
                            }
                            additionalOutput = "\n⚠️ 图片调用路径已变更为\nimage://$content"
                            ImageData.images[content] = ImageData.images.remove(name)!!
                            ImageData.images = ImageData.images.toSortedMap()
                        }
                        "image"-> {
                            val result = uploadImage(name, args[3], force = true)
                            if (!result.success) {
                                sendQuoteReply(result.message)
                                return
                            }
                            content = result.duration.toString()
                        }
                        "userID" -> {
                            if (content.toLongOrNull() == null) {
                                sendQuoteReply("转移失败：输入的 userID 不是整数")
                                return
                            }
                            val targetName = getNickname(this, content.toLong())
                            if (targetName == null) {
                                sendQuoteReply("转移失败：无法找到目标用户 $content，转移对象必须为机器人好友或本群成员")
                                return
                            }

                            if (!isAdmin) {
                                requestUserConfirmation(
                                    userID, args.content,
                                    " +++ℹ️ 二次确认提示 ℹ️️+++\n" +
                                    "您正在转移图片 $name 的所有权，转移前确认：\n" +
                                    "- 转移后您将*失去*该图片的操作权\n" +
                                    "- 您仍然可以在任意项目中使用此图片（除非此图片被删除）\n" +
                                    "\n" +
                                    "如您确认无误，请再次执行相同指令以完成转移"
                                ) ?: return
                            }

                            ImageData.images[name]?.set("owner", targetName)
                            ImageData.images[name]?.set("userID", content)
                        }
                        else -> {
                            ImageData.images[name]?.set(option, content)
                        }
                    }
                    ImageData.save()
                    if (option == "image") {
                        sendQuoteReply("更新图片 $name 成功！（用时：${content}秒）")
                    } else if (option == "userID") {
                        sendQuoteReply("成功将图片 $name 的所有权转移至 $content")
                    } else {
                        sendQuoteReply("成功将图片 $name 的 $option 参数修改为 $content$additionalOutput")
                    }
                }

                "delete", "删除"-> {   // 删除图片
                    val name = args[1].content
                    if (ImageData.images.contains(name).not()) {
                        sendQuoteReply("删除失败：图片名称 $name 不存在")
                        return
                    }
                    val ownerID = ImageData.images[name]?.get("userID")
                    val isOwner = userID.toString() == ownerID
                    if (!isOwner && !isAdmin) {
                        sendQuoteReply("无权删除此图片，如需删除请联系所有者：$ownerID。如果您对此图片存在疑问，请联系指令管理员")
                        return
                    }

                    val (success, error) = deleteImageFile(name)
                    if (!success) {
                        sendQuoteReply(error)
                        return
                    }
                    ImageData.images.remove(name)
                    ImageData.save()
                    sendQuoteReply("🗑️ 删除图片 $name 成功！")
                }

                else-> {
                    sendQuoteReply("[参数不匹配]\n请使用「${commandPrefix}img help」来查看指令帮助")
                }
            }
        } catch (_: IndexOutOfBoundsException) {
            sendQuoteReply("[参数不足]\n请使用「${commandPrefix}img help」来查看指令帮助")
        } catch (e: Exception) {
            logger.warning(e)
            sendQuoteReply("[指令执行未知错误]\n请联系管理员查看后台：${e::class.simpleName}(${e.message})")
        }
    }

    private suspend fun generateImage(name: String, subject: Contact?): Image? {
        val markdown = "<style>html, body, img, * { border: 0; padding: 0; margin: 0; } </style><img src='$Image_Path$name'>"
        val markdownResult = MarkdownImageGenerator.processMarkdown(null, markdown, "20")
        if (!markdownResult.success) return null
        val file = File("${cacheFolder}markdown.png")
        return subject?.uploadFileToImage(file)
    }

    private suspend fun uploadImage(imageName: String, image: Message, force: Boolean): DownloadHelper.DownloadResult {
        var imageUrl: String
        var isImage = true
        try{
            imageUrl = (image as Image).queryUrl()
        } catch (_: ClassCastException) {
            if (image is UnsupportedMessage) {
                return DownloadHelper.DownloadResult(false,
                    "[不支持的消息] 无法解析当前图片消息：请尝试使用其他客户端重新上传，或将图片替换为URL", 0.0)
            } else if (image.content.startsWith("https://")) {
                imageUrl = image.content
                isImage = false
            } else {
                return DownloadHelper.DownloadResult(false,
                    "[转换图片失败] 您发送的消息可能无法转换为图片，请尝试更换图片或联系管理员寻求帮助。如果使用URL上传，请以\"https://\"开头", 0.0)
            }
        } catch (e: Exception) {
            logger.warning("${e::class.simpleName}: ${e.message}")
            return DownloadHelper.DownloadResult(false,
                "获取图片参数失败，请检查指令格式是否正确\n" +
                "${commandPrefix}img upload <图片名称> <【图片/URL】>\n" +
                "注意：图片名字后需要空格或换行分隔图片参数", 0.0
            )
        }
        val downloadResult = if (isImage) downloadFile(null, imageUrl, imageFolder, imageName, force = force)
            else downloadImage(null, imageUrl, imageFolder, imageName, force = force)
        return downloadResult
    }

    private fun renameImageFile(oldName: String, newName: String): Pair<Boolean, String> {
        return try {
            val oldFile = File(imageFolder + oldName)
            val newFile = File(imageFolder + newName)
            if (oldFile.exists()) {
                val success = oldFile.renameTo(newFile)
                if (success) {
                    Pair(true, "重命名成功")
                } else {
                    Pair(false, "重命名失败：文件重命名失败，请联系管理员")
                }
            } else {
                Pair(false, "重命名失败：源文件不存在，请联系管理员")
            }
        } catch (e: Exception) {
            Pair(false, "重命名文件时出错: ${e::class.simpleName}(${e.message})")
        }
    }

    private fun deleteImageFile(imageName: String): Pair<Boolean, String> {
        return try {
            val imageFile = File(imageFolder + imageName)
            if (imageFile.exists()) {
                val success = imageFile.delete()
                if (success) {
                    Pair(true, "删除成功")
                } else {
                    Pair(false, "删除失败：文件删除失败，请联系管理员")
                }
            } else {
                Pair(false, "删除失败：源文件不存在，请联系管理员")
            }
        } catch (e: Exception) {
            Pair(false, "删除文件时出错: ${e::class.simpleName}(${e.message})")
        }
    }
}
