package site.tiedan.config

import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.info
import kotlin.io.path.Path
import kotlin.io.path.notExists
import kotlin.io.path.writeText

@PublishedApi
internal object MailConfig : ReadOnlyPluginConfig("MailConfig") {

    @ValueDescription("启用邮件发送功能")
    val enable: Boolean by value(true)

    var properties = Path("mail.properties")
        private set

    @OptIn(ConsoleExperimentalApi::class)
    override fun onInit(owner: PluginDataHolder, storage: PluginDataStorage) {
        if (owner is JvmPlugin) {
            properties = owner.resolveConfigPath("mail.properties")
            if (properties.notExists()) {
                properties.writeText(
                    """
                    mail.host=smtp.example.com
                    mail.auth=true
                    mail.user=xxx
                    mail.password=****
                    mail.from=xxx@example.com
                    mail.store.protocol=smtp
                    mail.transport.protocol=smtp
                    # smtp
                    mail.smtp.starttls.enable=true
                    mail.smtp.auth=true
                    mail.smtp.timeout=15000
                """.trimIndent()
                )
                owner.logger.info { "邮件配置文件已生成，请修改内容以生效 ${properties.toUri()}" }
            }
        }
    }
}