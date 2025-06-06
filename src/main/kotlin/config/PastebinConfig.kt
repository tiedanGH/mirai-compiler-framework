package config

import net.mamoe.mirai.console.data.*

@PublishedApi
internal object PastebinConfig : AutoSavePluginConfig("PastebinConfig") {

    @ValueDescription("API_TOKEN")
    val API_TOKEN: String by value()

    @ValueDescription("Hastebin_TOKEN")
    val Hastebin_TOKEN: String by value()

    @ValueDescription("pastebin指令权限")
    val admins: List<Long> by value(listOf(10000L))

    @ValueDescription("最多进程数限制")
    val thread_limit: Int by value(3)

    @ValueDescription("是否启用转发消息（消息过长时收入转发消息，部分框架可能不支持）")
    val enable_ForwardMessage: Boolean by value(true)

    @ValueDescription("是否启用审核功能（add链接时加入审核列队）")
    val enable_censor: Boolean by value(false)

}