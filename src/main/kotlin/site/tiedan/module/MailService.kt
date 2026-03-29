package site.tiedan.module

import jakarta.mail.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.console.command.CommandSender
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.config.MailConfig
import site.tiedan.utils.MailContentBuilder
import site.tiedan.utils.buildMailContent
import site.tiedan.utils.buildMailSession
import java.io.*
import kotlin.io.path.inputStream

/**
 * 项目相关的邮件工具类
 * 包含项目特定的邮件发送功能和HTML模板
 */
object MailService {

    /**
     * 发送存储数据查询邮件
     * @param sender 命令发送者
     * @param output 存储数据内容
     * @param userID 用户QQ号
     * @param name 查询名称
     */
    suspend fun sendStorageMail(
        sender: CommandSender,
        output: String,
        userID: String,
        name: String,
    ) {
        if (userID == "10000") {
            sender.sendQuoteReply("[错误] 控制台环境禁止使用此邮件发送功能")
            return
        }
        // TODO 平台限制检测，其他平台必须提供完整邮箱地址
        try {
            withContext(Dispatchers.IO) {
                FileOutputStream("${cacheFolder}storage.txt").use { outputStream ->
                    outputStream.write(output.toByteArray())
                }
            }
        } catch (e: IOException) {
            logger.warning(e)
            sender.sendQuoteReply("[请求使用邮件发送]\n但在尝试导出存储数据文件时发生错误：${e.message}")
            return
        }

        val session = buildMailSession {
            MailConfig.properties.inputStream().use {
                load(it)
            }
        }

        val mail = buildMailContent(session) {
            to = "${userID}@qq.com"
            title = "存储数据查询"

            htmlWithFooter {
                append("""
                <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 10px 10px 0 0; margin: -20px -20px 20px -20px;">
                    <h1 style="margin: 0; font-size: 24px;">📦 存储数据查询结果</h1>
                </div>
                
                <div style="background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin-bottom: 20px; border-radius: 4px;">
                    <h3 style="margin-top: 0; color: #856404;">⚠️ 重要提示</h3>
                    <p style="margin: 8px 0; color: #856404;"><b>使用本邮件服务即表示您已知晓并遵守以下注意事项：</b></p>
                    <ol style="margin: 10px 0; padding-left: 20px; color: #856404;">
                        <li style="margin: 5px 0;">不能在短时间内频繁使用此邮件发送服务</li>
                        <li style="margin: 5px 0;">不能在查询名称、查询ID、存储数据中添加任何违规内容</li>
                        <li style="margin: 5px 0;">此邮件为自动发送，请不要回复。如遇到问题请直接联系管理员</li>
                    </ol>
                </div>
                
                <div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                    <h3 style="margin-top: 0; color: #333;">📋 查询信息</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 10px; border-bottom: 1px solid #dee2e6; font-weight: bold; color: #495057; width: 120px;">查询名称</td>
                            <td style="padding: 10px; border-bottom: 1px solid #dee2e6; color: #212529;">$name</td>
                        </tr>
                        <tr>
                            <td style="padding: 10px; font-weight: bold; color: #495057;">数据文件</td>
                            <td style="padding: 10px; color: #212529;">📎 StorageData.txt（请查看附件）</td>
                        </tr>
                    </table>
                </div>
                
                <div style="background-color: #d1ecf1; border-left: 4px solid #17a2b8; padding: 15px; border-radius: 4px;">
                    <p style="margin: 0; color: #0c5460; font-size: 14px;">
                        💡 <strong>提示：</strong>查询结果已导出为文本文件，请下载附件查看完整数据。
                    </p>
                </div>
            """.trimIndent())
            }

            file("StorageData.txt") {
                File("${cacheFolder}storage.txt")
            }
        }

        val current = Thread.currentThread()
        val oc = current.contextClassLoader
        try {
            current.contextClassLoader = MailConfig::class.java.classLoader
            Transport.send(mail)
            sender.sendQuoteReply("[请求使用邮件发送]\n存储数据导出成功（文件总长度：${output.length}），并通过邮件发送，请您登录邮箱查看")
        } catch (e: MessagingException) {
            logger.warning(e)
            sender.sendQuoteReply("[请求使用邮件发送]\n存储数据导出成功（文件总长度：${output.length}），但邮件发送失败，原因: ${e.message}")
        } catch (e: Exception) {
            logger.warning(e)
            sender.sendQuoteReply("[请求使用邮件发送]\n存储数据导出成功（文件总长度：${output.length}），但发生其他未知错误: ${e.message}")
        } finally {
            current.contextClassLoader = oc
            File("${cacheFolder}storage.txt").delete()
        }
    }

    /**
     * 构建完整 HTML
     * @param bodyContent HTML 正文内容
     * @return 包含页脚的完整 HTML
     */
    fun buildHtmlWithFooter(bodyContent: String): String {
        val footerLinks = buildString {
            // GitHub 链接
            append("""
                <a href="https://github.com/tiedanGH/mirai-compiler-framework" target="_blank">
                    <span class="footer-icon">🔗</span>GitHub 仓库
                </a>
            """.trimIndent())
            // 相关网站链接
            if (MailConfig.relatedWebsite.isNotEmpty()) {
                append("""
                    <a href="https://${MailConfig.relatedWebsite}" target="_blank">
                        <span class="footer-icon">🌐</span>${MailConfig.relatedWebsite}
                    </a>
                """.trimIndent())
            }
            // 联系邮箱链接
            if (MailConfig.contactMail.isNotEmpty()) {
                append("""
                    <a href="mailto:${MailConfig.contactMail}">
                        <span class="footer-icon">✉️</span>${MailConfig.contactMail}
                    </a>
                """.trimIndent())
            }
        }

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .content {
                        margin-bottom: 30px;
                    }
                    .footer {
                        margin-top: 40px;
                        padding-top: 20px;
                        border-top: 2px solid #e0e0e0;
                        font-size: 14px;
                        color: #666;
                    }
                    .footer-title {
                        font-weight: bold;
                        color: #333;
                        margin-bottom: 10px;
                    }
                    .footer-links {
                        margin: 10px 0;
                        font-size: 12px;
                    }
                    .footer-links a {
                        color: #0066cc;
                        text-decoration: none;
                        margin-right: 10px;
                    }
                    .footer-links a:last-child {
                        margin-right: 0;
                    }
                    .footer-links a:hover {
                        text-decoration: underline;
                    }
                    .footer-icon {
                        display: inline-block;
                        margin-right: 3px;
                    }
                    .footer-desc {
                        margin-top: 10px;
                        font-size: 11px;
                        color: #999;
                    }
                </style>
            </head>
            <body>
                <div class="content">
                    $bodyContent
                </div>
                <div class="footer">
                    <div class="footer-title">Mirai Compiler Framework</div>
                    <div class="footer-links">
                        $footerLinks
                    </div>
                    <div class="footer-desc">
                        基于 Glot 接口的 Mirai Console 在线编译器框架
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * 为 MailContentBuilder 添加带项目页脚的 HTML 内容的扩展函数
     * @param builderAction HTML 内容构建器
     */
    fun MailContentBuilder.htmlWithFooter(builderAction: StringBuilder.() -> Unit) {
        val bodyContent = StringBuilder().apply(builderAction).toString()
        val fullHtml = buildHtmlWithFooter(bodyContent)
        html { append(fullHtml) }
    }
}
