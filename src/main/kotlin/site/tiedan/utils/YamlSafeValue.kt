package site.tiedan.utils

/**
 * # YAML 安全值转义工具
 *
 * ## 转义规则（双射，不丢失原始数据）
 * | 原始值 | 落盘值 |
 * | --- | --- |
 * | `null` / `Null` / `NULL` / `~` | `\null` / `\Null` / `\NULL` / `\~` |
 * | `\null` | `\\null` |
 * | 其他任意值 | 原样不变 |
 *
 * @author tiedanGH
 */
object YamlSafeValue {

    /** 转义前缀：单个反斜杠，可安全 round-trip */
    private const val ESCAPE = "\\"

    /**
     * 判断字符串是否会被 YAML 解析为 null
     */
    fun isNullLiteral(value: String): Boolean =
        value == "~" || value.equals("null", ignoreCase = true)

    /** 是否属于风险族：null 字面量本身，或由若干个反斜杠加 null 字面量组成 */
    private fun isRisky(value: String): Boolean =
        isNullLiteral(value.trimStart('\\'))

    /**
     * 转义：写入 PluginData 前调用
     */
    fun escape(value: String): String =
        if (isRisky(value)) ESCAPE + value else value

    /**
     * 还原：从 PluginData 读出并交给用户程序或展示时调用
     */
    fun unescape(value: String): String =
        if (value.startsWith(ESCAPE) && isRisky(value)) value.drop(1) else value
}
