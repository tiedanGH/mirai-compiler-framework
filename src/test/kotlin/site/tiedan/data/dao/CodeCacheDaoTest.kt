package site.tiedan.data.dao

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.sql.Connection

class CodeCacheDaoTest {

    private lateinit var conn: Connection

    @BeforeEach
    fun setUp() {
        conn = TestDb.open()
    }

    @AfterEach
    fun tearDown() {
        conn.close()
    }

    @Test
    @DisplayName("写入、读取与覆盖")
    fun putGetAndOverwrite() {
        assertFalse(CodeCacheDao.contains(conn, "proj"))
        assertNull(CodeCacheDao.get(conn, "proj"))

        CodeCacheDao.put(conn, "proj", "print(1)")
        assertTrue(CodeCacheDao.contains(conn, "proj"))
        assertEquals("print(1)", CodeCacheDao.get(conn, "proj"))

        CodeCacheDao.put(conn, "proj", "print(2)")
        assertEquals("print(2)", CodeCacheDao.get(conn, "proj"))
        assertEquals(1, CodeCacheDao.count(conn))
    }

    @Test
    @DisplayName("删除只影响目标项目")
    fun removeOnlyTargetProject() {
        CodeCacheDao.put(conn, "a", "aaa")
        CodeCacheDao.put(conn, "b", "bb")

        CodeCacheDao.remove(conn, "a")

        assertNull(CodeCacheDao.get(conn, "a"))
        assertEquals("bb", CodeCacheDao.get(conn, "b"))
        assertEquals(1, CodeCacheDao.count(conn))
        assertEquals(2L, CodeCacheDao.totalLength(conn))
    }

    @Test
    @DisplayName("BOM / CRLF / 控制字符原样保留")
    fun hostileValuesAreNotNormalized() {
        for ((index, value) in TestDb.HOSTILE_VALUES.withIndex()) {
            CodeCacheDao.put(conn, "proj$index", value)
        }
        for ((index, value) in TestDb.HOSTILE_VALUES.withIndex()) {
            assertEquals(value, CodeCacheDao.get(conn, "proj$index"), "index=$index")
        }
        assertEquals(TestDb.HOSTILE_VALUES.size, CodeCacheDao.count(conn))
        assertEquals(TestDb.HOSTILE_VALUES.sumOf { it.length.toLong() }, CodeCacheDao.totalLength(conn))
    }

    @Test
    @DisplayName("code_len 记录 UTF-16 码元数，与 SQL 的 length() 刻意不同")
    fun codeLenIsUtf16Length() {
        val code = "# 🎉 celebrate\nprint('ok')"
        CodeCacheDao.put(conn, "proj", code)

        assertEquals(code.length.toLong(), CodeCacheDao.totalLength(conn))
        assertEquals(
            code.codePointCount(0, code.length),
            TestDb.sqlLength(conn, "SELECT length(code) FROM code_cache WHERE project = 'proj'")
        )
        assertTrue(
            code.length > code.codePointCount(0, code.length),
            "含非 BMP 字符时两种长度必须不同，否则这条测试失去意义"
        )
    }

    @Test
    @DisplayName("空库的统计查询返回 0")
    fun emptyDatabaseAggregates() {
        assertEquals(0, CodeCacheDao.count(conn))
        assertEquals(0L, CodeCacheDao.totalLength(conn))
    }
}
