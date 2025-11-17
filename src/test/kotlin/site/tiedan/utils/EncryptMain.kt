package site.tiedan.utils

fun main() {
    val key = Security.generateAesKey()
    println("AES Key: $key")

    val text = "my-content"

    val encrypted = Security.encrypt(text, key)
    println("Encrypted: $encrypted")

    val decrypted = Security.decrypt(encrypted, key)
    println("Decrypted: $decrypted")
}
