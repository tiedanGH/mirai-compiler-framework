package site.tiedan.utils

fun main() {
    val password = "MyPassword"

    val hashed = Security.hashPassword(password)
    println("Password: $password")
    println("Hashed:   $hashed")

    val correct = Security.verifyPassword(password, hashed)
    val wrong = Security.verifyPassword("WrongPassword", hashed)

    println("Correct password verify: $correct")
    println("Wrong password verify:   $wrong")
}
