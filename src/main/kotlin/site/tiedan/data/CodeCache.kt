package site.tiedan.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object CodeCache : AutoSavePluginData("CodeCache") {

    @ValueDescription("代码缓存")
    var CodeCache: MutableMap<String, String> by value()

}