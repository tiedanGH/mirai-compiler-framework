package site.tiedan.utils

import at.favre.lib.crypto.bcrypt.BCrypt

object Security {

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
}
