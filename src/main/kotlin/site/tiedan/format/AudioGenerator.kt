package site.tiedan.format

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import net.mamoe.mirai.contact.AudioSupported
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.format.JsonProcessor.json
import site.tiedan.module.FreeTextToSpeech
import xyz.cssxsh.baidu.aip.BaiduAipClient
import java.io.IOException

object AudioGenerator {
    @Serializable
    data class AudioMessage(
        val format: String = "Audio",
        val person: Int = 0,
        val speed: Int = 5,
        val pitch: Int = 5,
        val volume: Int = 10,
        val content: String = "",
        val storage: String? = null,
        val global: String? = null,
    )

    data class AudioData(
        val audio: Audio? = null,
        val success: Boolean,
        val global: String? = null,
        val storage: String? = null,
        val error: String = "",
    )

    suspend fun generateAudio(audioOutput: String, subject: Contact?): AudioData {
        val result = try {
            json.decodeFromString<AudioMessage>(audioOutput)
        } catch (e: Exception) {
            return AudioData(success = false, error = "[错误] JSON解析错误：\n${e.message}")
        }
        if (result.content.isBlank()) {
            return AudioData(success = false, error = "生成语音失败：content内容为空")
        }
        if (result.format == "text") {
            return AudioData(null, false, result.global, result.storage, result.content)
        }
        val receiver = subject as? AudioSupported
            ?: return AudioData(success = false, error = "生成语音失败：当前执行环境不支持接收语音消息")
        try {
            val audio = textToSpeech(
                result.person,
                result.speed,
                result.pitch,
                result.volume,
                result.content,
                receiver
            )
            return AudioData(audio, true, result.global, result.storage)
        } catch (e: IOException) {
            return AudioData(null, false, result.global, result.storage, "上传语音失败：${e.message ?: e.toString()}")
        } catch (e: IllegalStateException) {
            val message = e.message ?: e.toString()
            if (message.contains("parameter")) {
                return AudioData(null, false, result.global, result.storage, "生成语音失败（请求参数错误，请查看person支持）：$message")
            } else if (message.contains("rate")) {
                return AudioData(null, false, result.global, result.storage, "生成语音失败（调用频率过快）：$message")
            }
            return AudioData(null, false, result.global, result.storage, "生成语音失败：$message")
        } catch (e: Exception) {
            logger.warning(e)
            return AudioData(null, false, result.global, result.storage, "[错误] 生成语音未知错误：${e.message}")
        }
    }

    /**
     * TTS文本转语音
     * @param person 音库
     * @param speed 语速
     * @param pitch 语调
     * @param volume 音量
     */
    private suspend fun textToSpeech(
        person: Int,
        speed: Int,
        pitch: Int,
        volume: Int,
        content: String,
        receiver: AudioSupported
    ): Audio {
        return FreeTextToSpeech(
            client = BaiduAipClient(FreeTextToSpeech.TextToSpeechConfig)
        ).speech(text = content) {
            this.person = person
            this.speed = speed
            this.pitch = pitch
            this.volume = volume
        }.toExternalResource().use { receiver.uploadAudio(it) }
    }
}