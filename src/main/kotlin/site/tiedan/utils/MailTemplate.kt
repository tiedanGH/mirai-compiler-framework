package site.tiedan.utils

import site.tiedan.config.MailConfig

/**
 * # 邮件样式模板
 * 框架发出的全部邮件共用这里的版式：外壳、标题横幅、提示块、信息表格。
 *
 * @author tiedanGH
 */
object MailTemplate {

    /** 邮件配色 */
    private object Palette {
        const val BANNER_FROM = "#667eea"
        const val BANNER_TO = "#764ba2"

        const val WARN_BG = "#fff3cd"
        const val WARN_LINE = "#ffc107"
        const val WARN_TEXT = "#856404"

        const val TIP_BG = "#d1ecf1"
        const val TIP_LINE = "#17a2b8"
        const val TIP_TEXT = "#0c5460"

        const val PANEL_BG = "#f8f9fa"
        const val PANEL_LINE = "#dee2e6"
        const val LABEL_TEXT = "#495057"
        const val VALUE_TEXT = "#212529"
        const val HEADING_TEXT = "#333"
    }

    /**
     * 标题横幅
     */
    fun banner(title: String): String = """
        <div style="background: linear-gradient(135deg, ${Palette.BANNER_FROM} 0%, ${Palette.BANNER_TO} 100%); color: white; padding: 30px; border-radius: 10px 10px 0 0; margin: -20px -20px 20px -20px;">
            <h1 style="margin: 0; font-size: 24px;">$title</h1>
        </div>
    """.trimIndent()

    /**
     * 警示块：用于使用须知一类必须读到的内容
     * @param items 逐条列出的注意事项
     */
    fun warning(title: String, intro: String, items: List<String>): String = """
        <div style="background-color: ${Palette.WARN_BG}; border-left: 4px solid ${Palette.WARN_LINE}; padding: 15px; margin-bottom: 20px; border-radius: 4px;">
            <h3 style="margin-top: 0; color: ${Palette.WARN_TEXT};">$title</h3>
            <p style="margin: 8px 0; color: ${Palette.WARN_TEXT};"><b>$intro</b></p>
            <ol style="margin: 10px 0; padding-left: 20px; color: ${Palette.WARN_TEXT};">
                ${items.joinToString("\n") { """<li style="margin: 5px 0;">$it</li>""" }}
            </ol>
        </div>
    """.trimIndent()

    /**
     * 信息表格
     * @param rows 表格的每一行，first 为字段名，second 为字段值
     */
    fun infoTable(title: String, rows: List<Pair<String, String>>): String {
        val body = rows.mapIndexed { index, (label, value) ->
            // 最后一行不画分隔线，避免与面板下边缘重叠
            val border = if (index == rows.lastIndex) "" else "border-bottom: 1px solid ${Palette.PANEL_LINE}; "
            """
            <tr>
                <td style="padding: 10px; ${border}font-weight: bold; color: ${Palette.LABEL_TEXT}; width: 120px;">$label</td>
                <td style="padding: 10px; ${border}color: ${Palette.VALUE_TEXT};">$value</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        return """
            <div style="background-color: ${Palette.PANEL_BG}; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                <h3 style="margin-top: 0; color: ${Palette.HEADING_TEXT};">$title</h3>
                <table style="width: 100%; border-collapse: collapse;">
                    $body
                </table>
            </div>
        """.trimIndent()
    }

    /**
     * 提示块：用于补充说明，不影响阅读主体
     */
    fun tip(text: String): String = """
        <div style="background-color: ${Palette.TIP_BG}; border-left: 4px solid ${Palette.TIP_LINE}; padding: 15px; border-radius: 4px;">
            <p style="margin: 0; color: ${Palette.TIP_TEXT}; font-size: 14px;">💡 <strong>提示：</strong>$text</p>
        </div>
    """.trimIndent()

    /**
     * 邮件外壳：套上统一的排版样式与页脚
     * @param body 由上面几个构件拼出的正文
     */
    fun page(body: String): String = """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                    line-height: 1.6;
                    color: ${Palette.HEADING_TEXT};
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
                    color: ${Palette.HEADING_TEXT};
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
                $body
            </div>
            <div class="footer">
                <div class="footer-title">Mirai Compiler Framework</div>
                <div class="footer-links">
                    ${footerLinks()}
                </div>
                <div class="footer-desc">
                    基于 Glot 接口的 Mirai Console 在线编译器框架
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()

    private fun footerLinks(): String = buildString {
        append(
            """
            <a href="https://github.com/tiedanGH/mirai-compiler-framework" target="_blank">
                <span class="footer-icon">🔗</span>GitHub 仓库
            </a>
            """.trimIndent()
        )
        if (MailConfig.relatedWebsite.isNotEmpty()) {
            append(
                """
                <a href="https://${MailConfig.relatedWebsite}" target="_blank">
                    <span class="footer-icon">🌐</span>${MailConfig.relatedWebsite}
                </a>
                """.trimIndent()
            )
        }
        if (MailConfig.contactMail.isNotEmpty()) {
            append(
                """
                <a href="mailto:${MailConfig.contactMail}">
                    <span class="footer-icon">✉️</span>${MailConfig.contactMail}
                </a>
                """.trimIndent()
            )
        }
    }
}
