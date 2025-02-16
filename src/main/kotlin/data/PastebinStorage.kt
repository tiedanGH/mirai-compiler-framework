package data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object PastebinStorage : AutoSavePluginData("PastebinStorage") {

    @ValueName("pastebin数据存储")
    var Storage: MutableMap<String, MutableMap<Long, String>> by value()

}