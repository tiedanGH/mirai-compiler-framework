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

class StorageDaoTest {

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
    @DisplayName("global 与用户存储互不干扰")
    fun globalAndUserAreIndependent() {
        StorageDao.setGlobal(conn, "proj", "G")
        StorageDao.setUser(conn, "proj", "qq", 123L, "U123")

        assertEquals("G", StorageDao.getGlobal(conn, "proj"))
        assertEquals("U123", StorageDao.getUser(conn, "proj", "qq", 123L))
        assertNull(StorageDao.getUser(conn, "proj", "qq", 456L))
    }

    @Test
    @DisplayName("同用户号在不同平台之间隔离")
    fun platformsAreIsolated() {
        StorageDao.setUser(conn, "proj", "qq", 100L, "from-qq")
        StorageDao.setUser(conn, "proj", "kook", 100L, "from-kook")
        StorageDao.setUser(conn, "proj", "unknown", 100L, "from-unknown")

        assertEquals("from-qq", StorageDao.getUser(conn, "proj", "qq", 100L))
        assertEquals("from-kook", StorageDao.getUser(conn, "proj", "kook", 100L))
        assertEquals("from-unknown", StorageDao.getUser(conn, "proj", "unknown", 100L))
        assertEquals(3, StorageDao.userCount(conn, "proj"))
        assertEquals(1, StorageDao.userCount(conn, "proj", "qq"))
    }

    @Test
    @DisplayName("global 不计入用户数量与用户总大小")
    fun globalIsNotCountedAsUser() {
        StorageDao.setGlobal(conn, "proj", "1234567890")
        StorageDao.setUser(conn, "proj", "qq", 1L, "abc")
        StorageDao.setUser(conn, "proj", "qq", 2L, "de")

        assertEquals(2, StorageDao.userCount(conn, "proj"))
        assertEquals(5L, StorageDao.userLength(conn, "proj"))
        assertEquals(10L, StorageDao.totalGlobalLength(conn))
        assertEquals(5L, StorageDao.totalUserLength(conn))
        assertEquals(10, StorageDao.globalLength(conn, "proj"))
    }

    @Test
    @DisplayName("重复写入同一 key 覆盖而非新增行")
    fun writeIsUpsert() {
        StorageDao.setUser(conn, "proj", "qq", 1L, "old")
        StorageDao.setUser(conn, "proj", "qq", 1L, "new value")

        assertEquals("new value", StorageDao.getUser(conn, "proj", "qq", 1L))
        assertEquals(1, StorageDao.userCount(conn, "proj"))
        assertEquals(9L, StorageDao.userLength(conn, "proj"))
    }

    @Test
    @DisplayName("删除用户数据后项目仍保留 global")
    fun removeUserKeepsGlobal() {
        StorageDao.setGlobal(conn, "proj", "G")
        StorageDao.setUser(conn, "proj", "qq", 1L, "U")
        StorageDao.removeUser(conn, "proj", "qq", 1L)

        assertEquals("G", StorageDao.getGlobal(conn, "proj"))
        assertEquals(0, StorageDao.userCount(conn, "proj"))
        assertTrue(StorageDao.projectExists(conn, "proj"))
    }

    @Test
    @DisplayName("删除项目会清掉全部平台的数据")
    fun removeProjectClearsAllPlatforms() {
        StorageDao.setGlobal(conn, "proj", "G")
        StorageDao.setUser(conn, "proj", "qq", 1L, "Q")
        StorageDao.setUser(conn, "proj", "kook", 1L, "K")
        StorageDao.setUser(conn, "other", "qq", 1L, "keep")

        StorageDao.removeProject(conn, "proj")

        assertFalse(StorageDao.projectExists(conn, "proj"))
        assertEquals(listOf("other"), StorageDao.listProjects(conn))
        assertEquals("keep", StorageDao.getUser(conn, "other", "qq", 1L))
    }

    @Test
    @DisplayName("listProject 返回 global 行与全部平台的用户行")
    fun listProjectReturnsEverything() {
        StorageDao.setGlobal(conn, "proj", "G")
        StorageDao.setUser(conn, "proj", "qq", 1L, "Q")
        StorageDao.setUser(conn, "proj", "kook", 2L, "K")

        val entries = StorageDao.listProject(conn, "proj")
        assertEquals(3, entries.size)
        assertTrue(entries.contains(StorageDao.Entry(StorageDao.GLOBAL_PLATFORM, 0L, "G")))
        assertTrue(entries.contains(StorageDao.Entry("qq", 1L, "Q")))
        assertTrue(entries.contains(StorageDao.Entry("kook", 2L, "K")))

        assertEquals(mapOf(1L to "Q"), StorageDao.listPlatformUsers(conn, "proj", "qq"))
    }

    @Test
    @DisplayName("各种历史脏数据原样存取，不做任何 normalize")
    fun hostileValuesRoundTrip() {
        for ((index, value) in TestDb.HOSTILE_VALUES.withIndex()) {
            StorageDao.setUser(conn, "proj", "qq", index.toLong(), value)
        }
        for ((index, value) in TestDb.HOSTILE_VALUES.withIndex()) {
            assertEquals(value, StorageDao.getUser(conn, "proj", "qq", index.toLong()), "index=$index")
        }
    }

    @Test
    @DisplayName("超长文本无损存取")
    fun longValueRoundTrip() {
        val huge = buildString { repeat(950_000) { append('x') } }
        StorageDao.setGlobal(conn, "proj", huge)

        assertEquals(huge, StorageDao.getGlobal(conn, "proj"))
        assertEquals(950_000, StorageDao.globalLength(conn, "proj"))
    }

    @Test
    @DisplayName("content_len 记录 UTF-16 码元数，与 SQL 的 length() 刻意不同")
    fun contentLenIsUtf16Length() {
        val value = "a😀b"                       // 码点 3 个，UTF-16 码元 4 个
        StorageDao.setGlobal(conn, "proj", value)

        assertEquals(4, value.length)
        assertEquals(4, StorageDao.globalLength(conn, "proj"), "content_len 必须等于 Kotlin String.length")
        assertEquals(
            3,
            TestDb.sqlLength(conn, "SELECT length(content) FROM project_storage WHERE project = 'proj'"),
            "SQL length() 返回码点数——正是不能用它代替 content_len 的原因"
        )
    }

    @Test
    @DisplayName("content_len 在每一条记录上都与正文长度一致")
    fun contentLenInvariantHolds() {
        for ((index, value) in TestDb.HOSTILE_VALUES.withIndex()) {
            StorageDao.setUser(conn, "proj", "qq", index.toLong(), value)
        }
        StorageDao.setUser(conn, "proj", "qq", 0L, "overwritten later")
        StorageDao.setUser(conn, "proj", "qq", 0L, "final")

        conn.createStatement().use { st ->
            st.executeQuery("SELECT user_id, content, content_len FROM project_storage").use { rs ->
                var rows = 0
                while (rs.next()) {
                    rows++
                    assertEquals(
                        rs.getString("content").length,
                        rs.getInt("content_len"),
                        "user_id=${rs.getLong("user_id")}"
                    )
                }
                assertEquals(TestDb.HOSTILE_VALUES.size, rows)
            }
        }
    }

    @Test
    @DisplayName("空库的统计查询返回 0 而非报错")
    fun emptyDatabaseAggregates() {
        assertEquals(0, StorageDao.projectCount(conn))
        assertEquals(0L, StorageDao.totalGlobalLength(conn))
        assertEquals(0L, StorageDao.totalUserLength(conn))
        assertEquals(0L, StorageDao.userLength(conn, "missing"))
        assertEquals(0, StorageDao.userCount(conn, "missing"))
        assertNull(StorageDao.globalLength(conn, "missing"))
        assertNull(StorageDao.getGlobal(conn, "missing"))
        assertTrue(StorageDao.listProject(conn, "missing").isEmpty())
    }
}
