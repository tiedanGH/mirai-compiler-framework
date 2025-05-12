package data

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object PastebinData : AutoSavePluginData("PastebinData") {

    @ValueName("隐藏Url的名称")
    var hiddenUrl: MutableSet<String> by value(mutableSetOf())

    @ValueName("仅限群聊运行的名称")
    var groupOnly: MutableSet<String> by value(mutableSetOf())

    @ValueName("待审核列表")
    var censorList: MutableSet<String> by value(mutableSetOf())

    @ValueName("别名")
    var alias: MutableMap<String, String> by value(mutableMapOf())

    @ValueName("pastebin代码数据")
    var pastebin: MutableMap<String, MutableMap<String, String>> by value(mutableMapOf("example" to mutableMapOf("language" to "python", "pastebinUrl" to "https://pastebin.ubuntu.com/", "stdin" to "1")))

}