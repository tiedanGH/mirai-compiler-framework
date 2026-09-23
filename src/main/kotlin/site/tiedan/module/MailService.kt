package site.tiedan.module

import jakarta.mail.MessagingException
import jakarta.mail.Transport
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.console.command.CommandSender
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.config.MailConfig
import site.tiedan.utils.MailTemplate
import site.tiedan.utils.buildMailContent
import site.tiedan.utils.buildMailSession
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.io.path.inputStream

/**
 * # 项目相关的邮件发送
 * 版式统一交给 [MailTemplate]，这里只负责组织内容与投递。
 *
 * @author tiedanGH
 */
object MailService {

    /** 邮箱地址格式 */
    private val MAIL_ADDRESS = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /** 校验邮箱地址 */
    fun isValidAddress(mail: String): Boolean = MAIL_ADDRESS.matches(mail)

    /**
     * 发送存储数据查询邮件
     * @param output 存储数据内容
     * @param name 查询名称
     * @param mail 收件邮箱地址，为 null 时按 QQ 邮箱推断
     */
    suspend fun sendStorageMail(
        sender: CommandSender,
        output: String,
        userID: String,
        name: String,
        mail: String?,
    ) {
        val body = MailTemplate.banner("💾 存储数据查询结果") +
            usageNotice("不能在查询名称、查询ID、存储数据中添加任何违规内容") +
            MailTemplate.infoTable(
                title = "📋 查询信息",
                rows = listOf(
                    "查询名称" to name,
                    "数据文件" to "📎 StorageData.txt（请查看附件）",
                ),
            ) +
            MailTemplate.tip("查询结果已导出为文本文件，请下载附件查看完整数据。")

        sendAttachmentMail(
            sender = sender,
            userID = userID,
            mail = mail,
            label = "存储数据",
            title = "存储数据查询",
            body = body,
            fileName = "StorageData.txt",
            content = output,
        )
    }

    /**
     * 发送项目代码导出邮件
     * @param code 项目的缓存代码
     * @param name 项目名称
     * @param mail 收件邮箱地址，为 null 时按 QQ 邮箱推断
     */
    suspend fun sendExportMail(
        sender: CommandSender,
        code: String,
        userID: String,
        name: String,
        language: String,
        mail: String?,
    ) {
        val body = MailTemplate.banner("📤 项目代码导出结果") +
            usageNotice("导出的代码仅供查阅与备份，请勿用于任何违规用途") +
            MailTemplate.infoTable(
                title = "📋 项目信息",
                rows = listOf(
                    "项目名称" to name,
                    "编程语言" to language,
                    "代码长度" to "${code.length}",
                    "代码文件" to "📎 SourceCode.txt（请查看附件）",
                ),
            ) +
            MailTemplate.tip("代码取自框架的本地缓存，与源链接的最新内容可能存在差异。")

        sendAttachmentMail(
            sender = sender,
            userID = userID,
            mail = mail,
            label = "项目代码",
            title = "项目代码导出",
            body = body,
            fileName = "SourceCode.txt",
            content = code,
        )
    }

    /** 各类邮件共用的使用须知，仅中间一条按用途替换 */
    private fun usageNotice(specific: String): String = MailTemplate.warning(
        title = "⚠️ 重要提示",
        intro = "使用本邮件服务即表示您已知晓并遵守以下注意事项：",
        items = listOf(
            "不能在短时间内频繁使用此邮件发送服务",
            specific,
            "此邮件为自动发送，请不要回复。如遇到问题请直接联系管理员",
        ),
    )

    /**
     * 把内容写成附件发出，并按结果回复指令发送者
     * @param label 内容的称呼，用于组织回复文案
     */
    private suspend fun sendAttachmentMail(
        sender: CommandSender,
        userID: String,
        mail: String?,
        label: String,
        title: String,
        body: String,
        fileName: String,
        content: String,
    ) {
        // 未提供地址时按 QQ 邮箱推断
        val address = mail ?: "${userID}@qq.com"

        // 每次请求独立命名，避免并发请求互相覆盖附件
        val tempFile = File("${cacheFolder}mail_${UUID.randomUUID()}.txt")
        try {
            withContext(Dispatchers.IO) {
                tempFile.parentFile?.mkdirs()
                tempFile.writeText(content)
            }
        } catch (e: IOException) {
            logger.warning(e)
            sender.sendQuoteReply("[请求使用邮件发送]\n但在尝试导出${label}文件时发生错误：${e.message}")
            return
        }

        val session = buildMailSession {
            MailConfig.properties.inputStream().use { load(it) }
        }
        val message = buildMailContent(session) {
            to = address
            this.title = title
            html { append(MailTemplate.page(body)) }
            file(fileName) { tempFile }
        }

        val prefix = "[请求使用邮件发送]\n${label}导出成功（文件总长度：${content.length}）"
        when (val error = deliver(message)) {
            null -> {
                sender.sendQuoteReply("$prefix，并通过邮件发送，请您登录邮箱查看")
                tempFile.delete()
            }
            // 发送失败时保留本地文件，便于排查
            is MessagingException ->
                sender.sendQuoteReply("$prefix，但邮件发送失败。本地文件已保留，请联系管理员。原因:\n${error.message}")
            else ->
                sender.sendQuoteReply("$prefix，但发生其他未知错误。本地文件已保留，请联系管理员。原因:\n${error.message}")
        }
    }

    /**
     * 投递邮件
     * - jakarta.mail 通过 ServiceLoader 查找实现，插件的隔离类加载器下必须临时换成配置类的加载器
     * @return 成功返回 null，失败返回捕获到的异常
     */
    private suspend fun deliver(message: MimeMessage): Exception? = withContext(Dispatchers.IO) {
        val current = Thread.currentThread()
        val origin = current.contextClassLoader
        try {
            current.contextClassLoader = MailConfig::class.java.classLoader
            Transport.send(message)
            null
        } catch (e: Exception) {
            logger.warning(e)
            e
        } finally {
            current.contextClassLoader = origin
        }
    }
}
