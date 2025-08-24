package site.tiedan.format

import net.mamoe.mirai.contact.AudioSupported
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import site.tiedan.MiraiCompilerFramework.MSG_TRANSFER_LENGTH
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.uploadFileToImage
import site.tiedan.config.PastebinConfig
import site.tiedan.format.ForwardMessageGenerator.stringToForwardMessage
import site.tiedan.format.JsonProcessor.ImageData
import site.tiedan.MiraiCompilerFramework.TIMEOUT
import site.tiedan.utils.DownloadHelper.downloadFile
import java.io.File
import org.apache.tika.Tika
import java.util.*

object Base64Processor {
    enum class FileType {
        Text,
        Image,
        Audio,
        Video,
        Application,
        Error,
    }
    private val supportedFileTypes: Set<FileType> = setOf(
        FileType.Text,
        FileType.Image,
        FileType.Audio,
        FileType.Video,
    )

    data class Base64Result(val success: Boolean, val extension: String, val fileType: FileType)

    fun processBase64(base64Str: String): Base64Result {
        try {
            val input = base64Str.trim()
            val (mimePrefix, rawContent) = if (input.contains(",")) {
                input.substringBefore(",") to input.substringAfter(",")
            } else {
                "" to input
            }

            var base64Content = rawContent.trim()
            if ((base64Content.startsWith("\"") && base64Content.endsWith("\"")) ||
                (base64Content.startsWith("'") && base64Content.endsWith("'"))) {
                base64Content = base64Content.substring(1, base64Content.length - 1)
            }
            base64Content = base64Content.replace("\uFEFF", "")

            val mimeTypeRegex = Regex("data:(.*?);base64", RegexOption.IGNORE_CASE)
            val mimeType = mimeTypeRegex.find(mimePrefix)?.groupValues?.get(1) ?: "[unknown]"
            val base64Data = mimeTypeToExtension(mimeType)
            val extension = base64Data.extension
            val fileType = base64Data.fileType
            if (!supportedFileTypes.contains(fileType)) {
                return Base64Result(
                    false,
                    "[错误] 当前Base64不支持此文件格式：$extension，请确保字符串前包含正确的“data:xxx”用于检测格式",
                    FileType.Error
                )
            }

            val decodedBytes = try {
                Base64.getMimeDecoder().decode(base64Content)
            } catch (_: IllegalArgumentException) {
                try {
                    Base64.getUrlDecoder().decode(base64Content)
                } catch (eUrl: IllegalArgumentException) {
                    val cleaned = base64Content.replace(Regex("[^A-Za-z0-9+/=]"), "")
                    if (cleaned.isEmpty()) throw eUrl
                    Base64.getDecoder().decode(cleaned)
                }
            }

            val outputFile = File("${cacheFolder}base64.$extension")
            outputFile.writeBytes(decodedBytes)

            logger.info("Base64解码并写入文件成功：${outputFile.name}")
            return base64Data
        } catch (e: Exception) {
            val content = base64Str.substringAfter(",", base64Str)
            val m = Regex("[^A-Za-z0-9+/=\\-_\\r\\n]").find(content)
            val illegal = m?.value
            val msg = if (illegal != null) {
                "[错误] Base64解析出错：包含非法字符 '${illegal}'"
            } else {
                "[错误] Base64解析出错：${e.message}"
            }
            logger.warning(e)
            return Base64Result(false, msg, FileType.Error)
        }
    }

    private fun mimeTypeToExtension(mimeType: String): Base64Result {
        return when (mimeType.lowercase()) {
            "image/jpeg" -> Base64Result(true, "jpg", FileType.Image)
            "image/png" -> Base64Result(true, "png", FileType.Image)
            "image/gif" -> Base64Result(true, "gif", FileType.Image)
            "image/bmp" -> Base64Result(true, "bmp", FileType.Image)
            "image/webp" -> Base64Result(true, "webp", FileType.Image)
            "audio/mpeg" -> Base64Result(true, "mp3", FileType.Audio)
            "audio/wav" -> Base64Result(true, "wav", FileType.Audio)
            "audio/ogg" -> Base64Result(true, "ogg", FileType.Audio)
            "video/mp4" -> Base64Result(true, "mp4", FileType.Video)
            "video/avi" -> Base64Result(true, "avi", FileType.Video)
            "video/webm" -> Base64Result(true, "webm", FileType.Video)
            "application/pdf" -> Base64Result(true, "pdf", FileType.Application)
            "application/octet-stream"-> Base64Result(true, "octet-stream", FileType.Application)
            "text/plain" -> Base64Result(true, "txt", FileType.Text)
            else -> Base64Result(false, mimeType, FileType.Error)
        }
    }

    suspend fun fileToMessage(fileType: FileType, extension: String, subject: Contact?, supportAll: Boolean): Message? {
        val errorMessage = PlainText("[错误] base64在MessageChain输出格式下不兼容此文件格式，请更换其他输出格式")
        val file = File("${cacheFolder}base64.$extension")
        when (fileType) {
            FileType.Image -> {
                return subject?.uploadFileToImage(file)
            }

            FileType.Audio -> {
                if (!supportAll) return errorMessage
                val receiver = subject as? AudioSupported
                return file.toExternalResource().use { receiver?.uploadAudio(it) }
            }

            FileType.Video -> {
                if (!supportAll) return errorMessage
                val thumbnail = File("${cacheFolder}thumbnail.png")
                return file.toExternalResource().use { video ->
                    thumbnail.toExternalResource().use { thumbnail ->
                        subject?.uploadShortVideo(thumbnail, video)
                    }
                }
            }

            FileType.Text -> {
                val output = file.readText()
                return if ((output.length > MSG_TRANSFER_LENGTH || output.lines().size > 30) && PastebinConfig.enable_ForwardMessage) {
                    stringToForwardMessage(StringBuilder(output), subject)
                } else {
                    PlainText(output)
                }
            }

            FileType.Application -> return errorMessage

            else-> return errorMessage
        }
    }

    fun encodeImagesToBase64(imageUrls: List<String>, encode: Boolean): List<ImageData> {
        val imageData = mutableListOf<ImageData>()
        val errorMessage = "[错误] 图片转换base64时出错，请联系管理员："

        var timeUsed: Long = 0
        for (url in imageUrls) {
            if (!encode) {
                imageData.add(ImageData(url, null))
                continue
            }
            val downloadResult = downloadFile(null, url, cacheFolder, "base64_download", TIMEOUT - timeUsed, force = true)
            if (!downloadResult.success) {
                imageData.add(ImageData(url, null, downloadResult.message))
                continue
            }
            timeUsed += downloadResult.duration
            val result = fileToDataUri(File("${cacheFolder}base64_download"))
            if (result.first) {
                imageData.add(ImageData(url, result.second))
            } else {
                imageData.add(ImageData(url, null, "$errorMessage${result.second}"))
            }
        }
        return imageData
    }

    fun fileToDataUri(file: File): Pair<Boolean, String> {
        return try {
            val bytes = file.readBytes()
            val tika = Tika()
            val mimeType = tika.detect(bytes, file.name)
            val base64 = Base64.getEncoder().encodeToString(bytes)
            Pair(true, "data:$mimeType;base64,$base64")
        } catch (e: Exception) {
            Pair(false, e.message.toString())
        }
    }
}