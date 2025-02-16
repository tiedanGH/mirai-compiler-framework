plugins {
    val kotlinVersion = "1.8.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.16.0"
}

group = "top.jie65535"
version = "1.1.0-pastebin"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies{
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.3")
    api("jakarta.mail:jakarta.mail-api:2.1.2")
    implementation("org.eclipse.angus:angus-mail:2.0.2")
}