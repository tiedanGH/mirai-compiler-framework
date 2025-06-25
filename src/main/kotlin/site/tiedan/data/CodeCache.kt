package site.tiedan.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object CodeCache : AutoSavePluginData("CodeCache") {

    @ValueName("代码缓存")
    var CodeCache: MutableMap<String, String> by value()

}