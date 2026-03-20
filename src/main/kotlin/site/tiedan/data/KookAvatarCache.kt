package site.tiedan.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object KookAvatarCache : AutoSavePluginData("KookAvatarCache") {

    @ValueDescription("Kook 平台头像 URL 缓存")
    var avatars: MutableMap<Long, MutableMap<String, String>> by value()

}