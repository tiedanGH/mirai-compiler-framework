package site.tiedan.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.config.SystemConfig
import site.tiedan.data.KookAvatarCache
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 获取 KOOK 用户头像 URL
 *
 * 逻辑：
 * 1. 若本地缓存未过期（24h），直接返回缓存
 * 2. 若缓存过期或不存在，请求 KOOK API
 * 3. 请求成功则更新缓存并返回
 * 4. 请求失败时，若存在旧缓存，则返回旧缓存；否则返回 KOOK 平台图标
 */
object KookAvatarService {

    private const val BASE_URL = "https://www.kookapp.cn/api/v3"
    private const val CACHE_EXPIRE_MILLIS = 24 * 60 * 60 * 1000L
    private const val DEFAULT_AVATAR_URL = "https://www.kookapp.cn/favicon.ico"

    private const val KEY_AVATAR_URL = "avatarUrl"
    private const val KEY_LAST_REQUEST_TIME = "lastRequestTime"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * 获取 KOOK 用户头像 URL
     *
     * @param userId KOOK 用户 ID
     * @param guildId 可选，服务器 ID
     */
    @Synchronized
    fun getAvatarUrl(
        userId: Long,
        guildId: Long? = null,
    ): String {
        if (isCacheValid(userId)) {
            return getCachedAvatarUrl(userId)!!
        }

        return try {
            val freshAvatarUrl = requestAvatarUrlFromKook(userId, SystemConfig.kookToken, guildId)
            if (!freshAvatarUrl.isNullOrBlank()) {
                updateCache(userId, freshAvatarUrl)
                freshAvatarUrl
            } else {
                getFallbackAvatarUrl(userId)
            }
        } catch (e: Exception) {
            logger.warning("获取 KOOK 用户头像失败，userId=$userId: ${e.message}")
            getFallbackAvatarUrl(userId)
        }
    }

    /**
     * 强制刷新某个用户头像，不走 24h 缓存判断
     */
    @Synchronized
    fun refreshAvatarUrl(
        userId: Long,
        botToken: String,
        guildId: Long? = null,
    ): String {
        return try {
            val freshAvatarUrl = requestAvatarUrlFromKook(userId, botToken, guildId)
            if (!freshAvatarUrl.isNullOrBlank()) {
                updateCache(userId, freshAvatarUrl)
                freshAvatarUrl
            } else {
                getFallbackAvatarUrl(userId)
            }
        } catch (e: Exception) {
            logger.warning("强制刷新 KOOK 用户头像失败，userId=$userId: ${e.message}")
            getFallbackAvatarUrl(userId)
        }
    }

    /**
     * 读取缓存中的头像（不校验是否过期）
     */
    fun getCachedAvatarUrl(userId: Long): String? {
        return KookAvatarCache.avatars[userId]?.get(KEY_AVATAR_URL)
    }

    /**
     * 判断缓存是否有效（未过期）
     */
    fun isCacheValid(userId: Long): Boolean {
        val cache = KookAvatarCache.avatars[userId] ?: return false
        val avatarUrl = cache[KEY_AVATAR_URL]
        val lastRequestTime = cache[KEY_LAST_REQUEST_TIME]?.toLongOrNull() ?: return false
        if (avatarUrl.isNullOrBlank()) return false
        return System.currentTimeMillis() - lastRequestTime < CACHE_EXPIRE_MILLIS
    }

    /**
     * 更新缓存
     */
    private fun updateCache(userId: Long, avatarUrl: String) {
        KookAvatarCache.avatars[userId] = mutableMapOf(
            KEY_AVATAR_URL to avatarUrl,
            KEY_LAST_REQUEST_TIME to System.currentTimeMillis().toString()
        )
        KookAvatarCache.save()
    }

    /**
     * 获取兜底头像：
     * 1. 优先返回旧缓存
     * 2. 若没有缓存，则返回默认 KOOK 图标，默认图标不会写入缓存
     */
    private fun getFallbackAvatarUrl(userId: Long): String {
        return getCachedAvatarUrl(userId) ?: DEFAULT_AVATAR_URL
    }

    /**
     * 请求 KOOK API
     */
    @Throws(IOException::class)
    private fun requestAvatarUrlFromKook(
        userId: Long,
        botToken: String,
        guildId: Long?
    ): String? {
        val url = buildString {
            append("$BASE_URL/user/view?user_id=")
            append(URLEncoder.encode(userId.toString(), StandardCharsets.UTF_8.name()))
            if (guildId != null) {
                append("&guild_id=")
                append(URLEncoder.encode(guildId.toString(), StandardCharsets.UTF_8.name()))
            }
        }

        val body = HttpUtil.get(
            url,
            mapOf(
                "Authorization" to "Bot $botToken",
                "User-Agent" to "Mozilla/5.0"
            )
        )

        val result = json.decodeFromString<KookUserViewResponse>(body)
        if (result.code != 0) {
            throw IOException("KOOK API error: code=${result.code}, message=${result.message}")
        }

        return result.data?.avatar
    }

    @Serializable
    private data class KookUserViewResponse(
        @SerialName("code")
        val code: Int,
        @SerialName("message")
        val message: String = "",
        @SerialName("data")
        val data: KookUserData? = null
    )

    @Serializable
    private data class KookUserData(
        @SerialName("id")
        val id: String? = null,
        @SerialName("username")
        val username: String? = null,
        @SerialName("avatar")
        val avatar: String? = null
    )
}
