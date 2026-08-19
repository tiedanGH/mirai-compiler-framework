package site.tiedan.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class YamlSafeValueTest {

    /** YAML 会解析为 null 的字面量，必须全部被转义 */
    private val nullLiterals = listOf("null", "Null", "NULL", "nUlL", "~")

    /** 不应受影响的普通值（含框架已在生产中依赖的 true / 数字 / 空串） */
    private val plainValues = listOf(
        "", " ", "true", "false", "100", "1.5", "-3", "0x1F", ".inf",
        "{}", "[]", "{\"a\":1}", "nullable", "null x", "x null", "a~b", "~x",
        "\\", "\\\\", "\\abc", "line1\nline2", "很长的中文存储数据",
    )

    @Test
    fun `null literals are escaped`() {
        for (v in nullLiterals) {
            val escaped = YamlSafeValue.escape(v)
            assertEquals("\\$v", escaped, "「$v」应被加上反斜杠前缀")
            assertFalse(YamlSafeValue.isNullLiteral(escaped), "转义后不应再是 null 字面量")
        }
    }

    @Test
    fun `plain values are untouched`() {
        for (v in plainValues) {
            assertEquals(v, YamlSafeValue.escape(v), "普通值「$v」不应被转义")
        }
    }

    @Test
    fun `escape then unescape restores original`() {
        for (v in nullLiterals + plainValues + listOf("\\null", "\\\\null", "\\~", "\\NULL")) {
            assertEquals(v, YamlSafeValue.unescape(YamlSafeValue.escape(v)), "「$v」round-trip 必须无损")
        }
    }

    @Test
    fun `already escaped values get another layer`() {
        // 反斜杠 + null 字面量属于风险族，必须再加一层才能与真实 null 字面量区分
        assertEquals("\\\\null", YamlSafeValue.escape("\\null"))
        assertEquals("\\null", YamlSafeValue.unescape("\\\\null"))
        assertEquals("\\\\~", YamlSafeValue.escape("\\~"))
    }

    @Test
    fun `unescape leaves non risky values alone`() {
        // 非风险族的反斜杠前缀值不应被误剥离
        assertEquals("\\abc", YamlSafeValue.unescape("\\abc"))
        assertEquals("\\", YamlSafeValue.unescape("\\"))
        assertEquals("abc", YamlSafeValue.unescape("abc"))
    }

    @Test
    fun `escape is idempotent in effect for storage round trip`() {
        // 模拟「反复保存-读取」：每轮都是 escape 落盘、unescape 取出，值必须稳定
        var stored = YamlSafeValue.escape("null")
        repeat(5) {
            val loaded = YamlSafeValue.unescape(stored)
            assertEquals("null", loaded, "多轮读写后原始值必须保持不变")
            stored = YamlSafeValue.escape(loaded)
        }
        assertEquals("\\null", stored)
    }

    @Test
    fun `is null literal detects only yaml nulls`() {
        for (v in nullLiterals) assertTrue(YamlSafeValue.isNullLiteral(v), "「$v」应被识别")
        for (v in plainValues) assertFalse(YamlSafeValue.isNullLiteral(v), "「$v」不应被识别")
    }
}
