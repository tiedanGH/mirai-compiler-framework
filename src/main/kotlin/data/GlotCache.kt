package data

import module.GlotAPI
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object GlotCache: AutoSavePluginData("GlotCache") {

    @ValueDescription("支持的语言")
    var languages: List<GlotAPI.Language> by value()

    @ValueDescription("模板文件")
    val templateFiles: MutableMap<String, GlotAPI.CodeFile> by value()
}