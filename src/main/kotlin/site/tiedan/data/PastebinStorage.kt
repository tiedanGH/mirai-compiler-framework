package site.tiedan.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object PastebinStorage : AutoSavePluginData("PastebinStorage") {

    @ValueDescription("项目独立存储数据")
    var storage: MutableMap<String, MutableMap<Long, String>> by value()

}