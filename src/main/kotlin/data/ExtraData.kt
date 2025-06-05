package data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object ExtraData : AutoSavePluginData("ExtraData") {

    @ValueDescription("黑名单用户")
    var BlackList: MutableList<Long> by value()

    @ValueDescription("接收主动私信时间段")
    var private_allowTime: MutableMap<Long, Pair<Int, Int>> by value()

    @ValueDescription("数据统计")
    var statistics: MutableMap<String, MutableMap<String, Double>> by value()

}