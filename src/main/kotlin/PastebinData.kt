import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object PastebinData : AutoSavePluginData("PastebinData") {

    @ValueName("pastebin代码数据")
    var pastebin: MutableMap<String, MutableMap<String, String>> by value(mutableMapOf("example" to mutableMapOf("language" to "python", "pastebinUrl" to "https://paste.ubuntu.com", "stdin" to "1")))

}