package data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object BlackListData : AutoSavePluginData("BlackListData") {

    @ValueName("黑名单用户")
    var BlackList: MutableList<Long> by value()

}