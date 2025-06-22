package format

import MiraiCompilerFramework.logger
import MiraiCompilerFramework.uploadFileToImage
import format.ForwardMessageGenerator.trimToMaxLength
import format.MarkdownImageGenerator.cacheFolder
import net.mamoe.mirai.contact.AudioSupported
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File
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
            val mimePrefix = base64Str.substringBefore(",")
            val base64Content = base64Str.substringAfter(",")
            val mimeTypeRegex = Regex("data:(.*?);base64")
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

            val outputFile = File("${cacheFolder}base64.$extension")
            val decodedBytes = Base64.getDecoder().decode(base64Content)
            outputFile.writeBytes(decodedBytes)

            logger.info("Base64解码并写入文件成功：${outputFile.name}")
            return base64Data
        } catch (e: Exception) {
            logger.warning(e)
            return Base64Result(false, "[错误] Base64解析出错：${e.message}", FileType.Error)
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
            "video/webm" -> Base64Result(true, "webm", FileType.Video)
            "application/pdf" -> Base64Result(true, "pdf", FileType.Application)
            "text/plain" -> Base64Result(true, "txt", FileType.Text)
            else -> Base64Result(false, "[unknown]", FileType.Error)
        }
    }

    suspend fun fileToMessage(fileType: FileType, extension: String, subject: Contact?, supportAll: Boolean): Message? {
        val errorMessage = PlainText("[错误] base64在MessageChain输出格式下不兼容此文件格式，请更换其他输出格式")
        val file = File("${cacheFolder}base64.$extension")
        when (fileType) {
            FileType.Image-> {
                return subject?.uploadFileToImage(file)
            }

            FileType.Audio-> {
                if (!supportAll) return errorMessage
                val receiver = subject as? AudioSupported
                return file.toExternalResource().use { receiver?.uploadAudio(it) }
            }

            FileType.Video-> {
                if (!supportAll) return errorMessage
                val thumbnail = File("${cacheFolder}thumbnail.png")
                return file.toExternalResource().use { video ->
                    thumbnail.toExternalResource().use { thumbnail ->
                        subject?.uploadShortVideo(thumbnail, video)
                    }
                }
            }

            FileType.Text-> {
                return PlainText(trimToMaxLength(file.readText(), 10000).first)
            }

            FileType.Application-> return errorMessage

            else-> return errorMessage
        }
    }
}