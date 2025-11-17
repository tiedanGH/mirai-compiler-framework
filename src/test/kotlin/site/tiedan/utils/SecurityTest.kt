package site.tiedan.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SecurityTest {

    /* ========================
     *  BCrypt Tests
     * ======================== */
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

    /* ========================
     *  AES-GCM Tests
     * ======================== */
    @Test
    fun `test aes encrypt and decrypt`() {
        val key = Security.generateAesKey()
        println("[Test] Generated AES key: $key")

        val content = "This is a secret content."
        println("[Test] Original content: $content")

        val encrypted = Security.encrypt(content, key)
        println("[Test] Encrypted content: $encrypted")

        val decrypted = Security.decrypt(encrypted, key)
        println("[Test] Decrypted content: $decrypted")

        assertEquals(content, decrypted, "Decrypted content must match the original")
        println("[PASS] AES encryption/decryption consistency test passed")
    }

    @Test
    fun `test aes encryption randomness`() {
        val key = Security.generateAesKey()
        val content = "Same content"

        val encrypted1 = Security.encrypt(content, key)
        val encrypted2 = Security.encrypt(content, key)

        println("[Test] Encrypted #1: $encrypted1")
        println("[Test] Encrypted #2: $encrypted2")

        assertNotEquals(encrypted1, encrypted2, "AES-GCM must produce different ciphertexts due to random IV")
        println("[PASS] AES IV randomness test passed")
    }

    @Test
    fun `test aes decrypt wrong key fails`() {
        val key1 = Security.generateAesKey()
        val key2 = Security.generateAesKey()

        val content = "Very sensitive data"
        val encrypted = Security.encrypt(content, key1)

        println("[Test] Content encrypted with key1: $encrypted")
        println("[Test] Trying to decrypt using key2")

        assertThrows(Exception::class.java) {
            Security.decrypt(encrypted, key2)
        }

        println("[PASS] AES wrong-key decryption correctly failed")
    }
}
