package site.tiedan.config

import net.mamoe.mirai.console.data.*

@PublishedApi
internal object PlatformConfig : AutoSavePluginConfig("PlatformConfig") {

    @ValueDescription("账号平台配置（不再列表内默认为 qq）")
    val platforms: MutableMap<Long, MutableMap<String, String>> by value(
        mutableMapOf(
            114514L to mutableMapOf(
                "platform" to "kook",
                "quick_prefix" to "//",
            )
        )
    )
}