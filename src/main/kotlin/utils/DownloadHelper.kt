package utils

import JCompilerCollection.logger
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object DownloadHelper {
    private const val CONNECT_TIMEOUT = 6000   // 连接超时时间
    private const val READ_TIMEOUT = 6000      // 读取超时时间

    fun downloadImage(
        imageUrl: String,
        outputFilePath: String,
        fileName: String,
        timeout: Long = 30,
        force: Boolean = false
    ): Pair<String, Long> {
        if (!isImageUrl(imageUrl)) {
            return Pair("[错误] 连接失败或未能在链接资源中检测到图片", 0)
        }
        return downloadFile(imageUrl, outputFilePath, fileName, timeout, force)
    }

    private fun downloadFile(
        fileUrl: String,
        outputFilePath: String,
        fileName: String,
        timeout: Long = 30,
        force: Boolean = false
    ): Pair<String, Long> {
        if (fileName.contains("/")) {
            return Pair("[错误] 文件名称中不能包含符号“/”", 0)
        }
        val outputFile = File(outputFilePath + fileName)
        if (outputFile.exists() && !force) {
            return Pair("[错误] 此文件名已存在：$fileName", 0)
        }

        var result = ""
        val startTime = System.currentTimeMillis()

        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit {
            var connection: HttpURLConnection? = null
            var inputStream: java.io.InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                logger.info("执行下载文件：$fileUrl")
                val url = URL(fileUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.inputStream
                    outputStream = FileOutputStream(outputFile)

                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    result = "下载并保存文件成功"
                } else {
                    result = "[错误] HTTP Status ${connection.responseCode}: ${connection.responseMessage}"
                }
            } catch (e: Exception) {
                result = "[错误] 下载时发生错误: ${e::class.simpleName}(${e.message})"
            } finally {
                inputStream?.close()
                outputStream?.close()
                connection?.disconnect()
            }
        }

        try {
            future.get(timeout, TimeUnit.SECONDS) // 限制下载时间
        } catch (e: Exception) {
            future.cancel(true) // 超时后取消任务
            result += "[错误] 下载执行超时：最大限制时间为${timeout}秒"
        } finally {
            executor.shutdown()
        }

        val elapsedTime = System.currentTimeMillis() - startTime
        return Pair(result, ceil(elapsedTime / 1000.0).toLong())
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