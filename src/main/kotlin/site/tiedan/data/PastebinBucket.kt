package site.tiedan.data

import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

@PublishedApi
internal object PastebinBucket : AutoSavePluginData("PastebinBucket") {

    @ValueDescription("跨项目存储库")
    var bucket: MutableMap<Long, MutableMap<String, String>> by value()

    @ValueDescription("存储库备份数据")
    var backups: MutableMap<Long, MutableList<BackupInfo?>> by value()

    @Serializable
    data class BackupInfo(
        var name: String,
        var time: Long,
        var content: String,
    )
}