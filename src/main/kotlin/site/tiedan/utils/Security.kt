package site.tiedan.utils

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Security {
    /* =========================
     *  BCrypt 密码哈希
     * ========================= */
    private const val COST = 8

    /**
     * 生成哈希
     */
    fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray())
    }

    /**
     * 验证密码
     */
    fun verifyPassword(password: String, hashed: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hashed)
        return result.verified
    }

    /* =========================
     *  AES-GCM 对称加密
     * ========================= */
    private const val AES_KEY_SIZE = 256
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    /**
     * 生成 AES Key
     */
    fun generateAesKey(): String {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(AES_KEY_SIZE)
        val key = kg.generateKey()
        return Base64.getEncoder().encodeToString(key.encoded)
    }

    /**
     * AES-GCM 加密
     */
    fun encrypt(content: String, base64Key: String): String {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        val keySpec = SecretKeySpec(keyBytes, "AES")

        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val encrypted = cipher.doFinal(content.toByteArray())

        // 拼接 IV + ciphertext
        val result = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)

        return Base64.getEncoder().encodeToString(result)
    }

    /**
     * AES-GCM 解密
     */
    fun decrypt(base64CipherText: String, base64Key: String): String {
        val cipherBytes = Base64.getDecoder().decode(base64CipherText)
        val keyBytes = Base64.getDecoder().decode(base64Key)
        val keySpec = SecretKeySpec(keyBytes, "AES")

        val iv = cipherBytes.copyOfRange(0, IV_LENGTH)
        val encrypted = cipherBytes.copyOfRange(IV_LENGTH, cipherBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val decoded = cipher.doFinal(encrypted)
        return String(decoded)
    }
}
