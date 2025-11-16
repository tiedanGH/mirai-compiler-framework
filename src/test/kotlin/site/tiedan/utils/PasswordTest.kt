package site.tiedan.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PasswordTest {

    @Test
    fun `test hash and verify correct password`() {
        val password = "MySecret123"
        val hashed = Security.hashPassword(password)
        println("[Test] Generated hash: $hashed")

        val result = Security.verifyPassword(password, hashed)
        assertTrue(result, "Password should verify correctly")
        println("[PASS] Correct password verification succeeded")
    }

    @Test
    fun `test verify wrong password`() {
        val password = "MySecret123"
        val wrong = "WrongPassword"
        val hashed = Security.hashPassword(password)
        println("[Test] Hash generated for correct password: $hashed")

        val result = Security.verifyPassword(wrong, hashed)
        assertFalse(result, "Wrong password must not verify")
        println("[PASS] Wrong password verification test passed")
    }

    @Test
    fun `test hash randomness`() {
        val password = "MySecret123"

        val hash1 = Security.hashPassword(password)
        val hash2 = Security.hashPassword(password)

        println("[Test] Hash1: $hash1")
        println("[Test] Hash2: $hash2")

        assertNotEquals(hash1, hash2, "Two hashes for the same password should be different (random salt)")
        println("[PASS] Hash randomness test passed")
    }
}
