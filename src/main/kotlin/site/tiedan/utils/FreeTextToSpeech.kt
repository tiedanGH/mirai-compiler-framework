/*
 * This file is based on source code from https://github.com/cssxsh/mirai-tts-plugin
 * The original project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 *
 * In compliance with the AGPL-3.0 license, any network service that uses this file
 * must also make its complete source code publicly available.
 *
 * Original copyright and license terms are retained.
 */
package site.tiedan.utils

import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import xyz.cssxsh.baidu.aip.tts.SpeechException
import xyz.cssxsh.baidu.aip.tts.SpeechOption
import xyz.cssxsh.baidu.api.BaiduApiClient
import xyz.cssxsh.baidu.oauth.BaiduAuthConfig

/**
 * 百度百科 TTS 接口
 * @param client 提供 http 访问
 * @author cssxsh
 */
class FreeTextToSpeech(private val client: BaiduApiClient<*>)  {
    companion object {
        internal const val FREE_TTS = "https://tts.baidu.com/text2audio"
    }

    suspend fun speech(text: String, block: SpeechOption.() -> Unit): ByteArray {
        return handle(text = text, option = SpeechOption().apply(block))
    }

    internal object TextToSpeechConfig : BaiduAuthConfig {
        override val appId: Long = 0
        override val appKey: String = ""
        override val appName: String = ""
        override val secretKey: String = ""
    }

    /**
     * 从百度百科 api 获取 tts，格式mp3
     */
    private suspend fun handle(text: String, option: SpeechOption): ByteArray {
        return client.useHttpClient { http ->
            http.prepareForm(FREE_TTS, Parameters.build {
                append("tex", text)
                append("pdt", "301")
                append("cuid", "bake")
                append("ctp", "1")
                append("lan", "zh")
                append("spd", option.speed.toString())
                append("pit", option.pitch.toString())
                append("vol", option.volume.toString())
                append("per", option.person.toString())
                append("aue", option.format.toString())
            }).execute { response ->
                val type = requireNotNull(response.contentType()) { "ContentType is null." }
                when {
                    type.match(ContentType.Audio.Any) -> response.body()
                    type.match(ContentType.Application.Json) -> throw SpeechException(response, response.body())
                    else -> throw IllegalStateException("ContentType: $type not is audio.")
                }
            }
        }
    }
}
