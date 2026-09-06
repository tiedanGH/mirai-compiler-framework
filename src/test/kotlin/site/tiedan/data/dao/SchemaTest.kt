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

class SchemaTest {

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
    @DisplayName("建表幂等，重复执行不会清空数据")
    fun initializeIsIdempotent() {
        StorageDao.setGlobal(conn, "proj", "data")
        StatisticsDao.countRun(conn, "proj")

        Schema.initialize(conn)
        Schema.initialize(conn)

        assertEquals("data", StorageDao.getGlobal(conn, "proj"))
        assertEquals(Schema.VERSION, Schema.readVersion(conn))
        assertEquals(1L, StatisticsDao.totals(conn).run)
    }

    @Test
    @DisplayName("全局累计统计表建库时即有唯一一行")
    fun statisticsTotalIsSingleton() {
        assertEquals(StatisticsDao.Totals(0L, 0L, 0.0, 0L, 0.0), StatisticsDao.totals(conn))
        assertEquals(1, TestDb.sqlLength(conn, "SELECT COUNT(*) FROM statistics_total"))
    }

    @Test
    @DisplayName("表结构版本号写入 PRAGMA user_version")
    fun versionIsPersisted() {
        assertEquals(Schema.VERSION, Schema.readVersion(conn))
        assertTrue(Schema.tablesExist(conn))
    }

    @Test
    @DisplayName("全部业务表均已建立")
    fun allTablesCreated() {
        val expected = setOf(
            "meta", "project_storage", "bucket", "bucket_project", "bucket_backup",
            "code_cache", "statistics", "statistics_total"
        )
        val actual = conn.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rs ->
                buildSet { while (rs.next()) add(rs.getString(1)) }
            }
        }
        assertTrue(actual.containsAll(expected), "缺少表：${expected - actual}")
    }

    @Test
    @DisplayName("统计表的四个可空列在 DDL 层面确实可空")
    fun statisticsNullableColumns() {
        val nullable = conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(statistics)").use { rs ->
                buildMap { while (rs.next()) put(rs.getString("name"), rs.getInt("notnull") == 0) }
            }
        }
        for (column in listOf("markdown", "md_time", "download", "dl_time")) {
            assertTrue(nullable[column] == true, "$column 必须可空")
        }
        for (column in listOf("run", "score")) {
            assertFalse(nullable[column] == true, "$column 应当非空")
        }
    }

    @Test
    @DisplayName("meta 表的读写与初始化标记")
    fun metaReadWrite() {
        assertNull(MetaDao.get(conn, MetaDao.KEY_INITIALIZED))
        assertFalse(MetaDao.isInitialized(conn))

        MetaDao.markInitialized(conn)
        assertTrue(MetaDao.isInitialized(conn))

        MetaDao.set(conn, "custom", "v1")
        MetaDao.set(conn, "custom", "v2")
        assertEquals("v2", MetaDao.get(conn, "custom"))
    }
}
