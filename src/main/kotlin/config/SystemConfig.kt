package config

import net.mamoe.mirai.console.data.*

@PublishedApi
internal object SystemConfig : AutoSavePluginConfig("SystemConfig") {

    @ValueDescription("调用markdown进程时系统的最大内存限制（单位：M）")
    val memoryLimit: Long by value(4096L)

    @ValueDescription("群聊词汇黑名单（防止多机器人指令冲突，支持正则）")
    val groupBlackList: List<String> by value(listOf())

    @ValueDescription("私信词汇黑名单（拦截私信中的禁用词汇，支持正则）")
    val privateBlackList: List<String> by value(listOf())

    @ValueDescription("测试目录（无需配置）")
    val TEST_PATH: String by value()

}