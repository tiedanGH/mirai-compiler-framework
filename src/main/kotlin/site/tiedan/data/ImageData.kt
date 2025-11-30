package site.tiedan.data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object ImageData : AutoSavePluginData("ImageData") {

    @ValueDescription("本地图片数据")
    var images: MutableMap<String, MutableMap<String, String>> by value()

}