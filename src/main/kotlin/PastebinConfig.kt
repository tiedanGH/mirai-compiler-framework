import net.mamoe.mirai.console.data.*

@PublishedApi
internal object PastebinConfig : AutoSavePluginConfig("PastebinConfig") {

    @ValueDescription("pastebin指令权限")
    val admins: MutableList<Long> by value(mutableListOf(10000L, 2295824927))

    @ValueDescription("是否启用转发消息（消息过长时收入转发消息，部分框架可能不支持）")
    var enable_ForwardMessage: Boolean by value(true)

    @ValueDescription("是否启用审核功能（add链接时加入审核列队）")
    var enable_censor: Boolean by value(false)

}