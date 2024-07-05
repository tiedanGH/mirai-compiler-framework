import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object PastebinData : AutoSavePluginData("PastebinData") {

    @ValueName("pastebin指令权限")
    val admins : MutableList<Long> by value(mutableListOf(10000L))

    @ValueName("隐藏Url的名称")
    val hiddenUrl : MutableSet<String> by value(mutableSetOf())

    @ValueName("pastebin代码数据")
    var pastebin: MutableMap<String, MutableMap<String, String>> by value(mutableMapOf("example" to mutableMapOf("language" to "python", "pastebinUrl" to "https://paste.ubuntu.com", "stdin" to "1")))

}