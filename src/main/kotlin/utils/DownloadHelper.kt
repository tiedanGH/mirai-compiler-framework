package utils

import MiraiCompilerFramework.logger
import module.Statistics
import format.MarkdownImageProcessor.TIMEOUT
import module.Statistics.roundTo2
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object DownloadHelper {
    private const val CONNECT_TIMEOUT = 6000   // 连接超时时间
    private const val READ_TIMEOUT = 6000      // 读取超时时间

    data class DownloadResult(val success: Boolean, val message: String, val duration: Long)

    fun downloadImage(
        name: String?,
        imageUrl: String,
        outputFilePath: String,
        fileName: String,
        timeout: Long = 30,
        force: Boolean = false
    ): DownloadResult {
        if (!isImageUrl(imageUrl)) {
            return DownloadResult(false, "[错误] 连接失败或未能在链接资源中检测到图片", 0)
        }
        return downloadFile(name, imageUrl, outputFilePath, fileName, timeout, force)
    }

    fun downloadFile(
        name: String?,
        fileUrl: String,
        outputDir: String,
        fileName: String,
        timeout: Long = 30,
        force: Boolean = false
    ): DownloadResult {
        if (timeout <= 0L) {
            return DownloadResult(false, "[错误] 执行时间已达总上限${TIMEOUT}秒", 0)
        }
        if (fileName.contains("/")) {
            return DownloadResult(false, "[错误] 文件名称中不能包含符号“/”", 0)
        }
        val outputFile = File(outputDir, fileName)
        if (outputFile.exists() && !force) {
            return DownloadResult(false, "[错误] 此文件名已存在：$fileName", 0)
        }

        var resultMsg = ""
        val startTime = Instant.now()

        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit {
            var connection: HttpURLConnection? = null

            try {
                logger.info("执行下载文件：$fileUrl")
                connection = URL(fileUrl).openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        FileOutputStream(outputFile).use { outputStream ->
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                } else {
                    resultMsg = "[错误] HTTP Status ${connection.responseCode}: ${connection.responseMessage}"
                }
            } catch (e: Exception) {
                resultMsg = "[错误] 下载时发生错误: ${e::class.simpleName}(${e.message})"
            } finally {
                connection?.disconnect()
            }
        }

        try {
            future.get(timeout, TimeUnit.SECONDS) // 限制下载时间
        } catch (e: Exception) {
            future.cancel(true) // 超时后取消任务
            resultMsg += "[错误] 下载超时：超出最大时间限制${timeout}秒"
        } finally {
            executor.shutdown()
        }

        val endTime = Instant.now()     // 记录结束时间
        val duration = (Duration.between(startTime, endTime).toMillis() / 1000.0).roundTo2()
        val success = resultMsg.startsWith("[错误]").not()
        name?.let { Statistics.countDownload(it, duration) }
        logger.info("下载成功，用时${duration}秒")
        return DownloadResult(success, resultMsg, ceil(duration).toLong())
    }

    private fun isImageUrl(url: String): Boolean {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.connect()

            val contentType = connection.contentType
            connection.disconnect()

            contentType?.startsWith("image/") == true
        } catch (e: Exception) {
            logger.warning("检测图片链接发生错误：${e::class.simpleName}(${e.message})")
            true    // 连接超时跳过预检测
        }
    }

}