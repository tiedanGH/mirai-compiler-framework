package site.tiedan.config

import net.mamoe.mirai.console.data.*

@PublishedApi
internal object DockerConfig : AutoSavePluginConfig("DockerConfig") {

    @ValueDescription("docker已经部署的语言（不再使用Glot官网API，如未使用docker-run请忽略此配置文件）")
    val supportedLanguages: List<String> by value(listOf())

    @ValueDescription("docker请求地址")
    val requestUrl: String by value("http://localhost:8088/run")

    @ValueDescription("docker请求token")
    val token: String by value()

}