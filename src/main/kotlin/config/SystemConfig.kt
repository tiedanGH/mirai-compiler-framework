package config

import net.mamoe.mirai.console.data.*

@PublishedApi
internal object SystemConfig : AutoSavePluginConfig("SystemConfig") {

    @ValueDescription("调用markdown进程时系统的最大内存限制（单位：M）")
    val memoryLimit: Long by value(4096L)

    @ValueDescription("群聊词汇黑名单（防止多机器人指令冲突）")
    val groupBlackList: List<String> by value(listOf())

    @ValueDescription("私信词汇黑名单（其他用途）")
    val privateBlackList: List<String> by value(listOf())

}