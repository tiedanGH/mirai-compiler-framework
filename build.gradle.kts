plugins {
    val kotlinVersion = "2.1.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.16.0"
}

group = "site.tiedan"
version = "1.1.2"

repositories {
    mavenCentral()
    maven("https://maven.aliyun.com/repository/public")
}

dependencies {
    implementation("org.jsoup:jsoup:1.21.1")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.16")
    api("jakarta.mail:jakarta.mail-api:2.1.3")
    implementation("org.eclipse.angus:angus-mail:2.0.4")
    implementation("org.apache.tika:tika-core:2.9.2")
    implementation("xyz.cssxsh.baidu:baidu-aip:3.3.2")
}

mirai {
    jvmTarget = JavaVersion.VERSION_17
}