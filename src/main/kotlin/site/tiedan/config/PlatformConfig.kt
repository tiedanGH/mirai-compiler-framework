package site.tiedan.config

import net.mamoe.mirai.console.data.*

@PublishedApi
internal object PlatformConfig : AutoSavePluginConfig("PlatformConfig") {

    @ValueDescription("账号平台配置（不再列表内默认为 qq；enable控制bot全局启用状态）")
    val platforms: MutableMap<Long, MutableMap<String, String>> by value(
        mutableMapOf(
            114514L to mutableMapOf(
                "enable" to "true",
                "platform" to "kook",
                "quick_prefix" to "//",
            )
        )
    )
}