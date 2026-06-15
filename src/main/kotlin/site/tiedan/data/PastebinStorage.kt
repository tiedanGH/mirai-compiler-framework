package site.tiedan.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object PastebinStorage : AutoSavePluginData("PastebinStorage") {

    @ValueDescription("存储数据初始化标记（用于检测数据是否异常丢失，请勿手动修改）")
    var initialized: Boolean by value(false)

    @ValueDescription("项目独立存储数据")
    var storage: MutableMap<String, MutableMap<Long, String>> by value()

}