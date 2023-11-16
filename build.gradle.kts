plugins {
    val kotlinVersion = "1.6.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.12.0"
}

group = "top.jie65535"
version = "1.1.0-pastebin"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies{
    implementation("org.jsoup:jsoup:1.15.3")
}