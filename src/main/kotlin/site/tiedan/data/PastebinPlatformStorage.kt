package site.tiedan.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object PastebinPlatformStorage : AutoSavePluginData("PastebinPlatformStorage") {

    @ValueDescription("其他平台项目用户存储数据（仅storage）")
    var storage: MutableMap<String, MutableMap<String, MutableMap<Long, String>>> by value()

}