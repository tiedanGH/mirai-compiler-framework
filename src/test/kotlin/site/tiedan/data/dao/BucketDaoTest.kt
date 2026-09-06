package site.tiedan.data.dao

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.sql.Connection

class BucketDaoTest {

    private lateinit var conn: Connection

    @BeforeEach
    fun setUp() {
        conn = TestDb.open()
    }

    @AfterEach
    fun tearDown() {
        conn.close()
    }

    private fun requireBucket(id: Long): BucketDao.BucketRow =
        BucketDao.get(conn, id) ?: throw AssertionError("存储库 $id 不应为空")

    private fun create(id: Long, name: String) =
        BucketDao.create(conn, id, name, "hash-$id", "owner-$id", "10000$id")

    /* ==================== 槽位 ==================== */

    @Test
    @DisplayName("新建存储库写入全部字段")
    fun createWritesAllFields() {
        create(1L, "库A")
        val bucket = requireBucket(1L)

        assertEquals(1L, bucket.id)
        assertEquals("库A", bucket.name)
        assertEquals("hash-1", bucket.password)
        assertEquals("owner-1", bucket.owner)
        assertEquals("100001", bucket.userID)
        assertEquals("", bucket.description)
        assertEquals("", bucket.content)
        assertEquals(0, bucket.contentLen)
        assertFalse(bucket.encrypt)
        assertTrue(bucket.projects.isEmpty())
    }

    @Test
    @DisplayName("删除存储库保留空置槽位并清空全部字段")
    fun deleteKeepsEmptySlot() {
        create(1L, "库A")
        BucketDao.setRawContent(conn, 1L, "secret content")
        BucketDao.setProjects(conn, 1L, listOf("p1"))
        BucketDao.setBackup(conn, 1L, 0, BucketDao.BackupRow("bk", 123L, "data"))

        BucketDao.delete(conn, 1L)

        assertTrue(BucketDao.exists(conn, 1L), "槽位应当保留")
        assertTrue(BucketDao.isEmpty(conn, 1L))
        assertNull(BucketDao.get(conn, 1L))
        assertNull(BucketDao.getRawContent(conn, 1L))
        assertNull(BucketDao.idToName(conn, 1L))
        assertEquals(0, BucketDao.count(conn))
        // 密码哈希与正文不得残留在库文件里
        conn.createStatement().use { st ->
            st.executeQuery("SELECT password, content FROM bucket WHERE id = 1").use { rs ->
                assertTrue(rs.next())
                assertEquals("", rs.getString(1))
                assertEquals("", rs.getString(2))
            }
        }
        assertTrue(BucketDao.getProjects(conn, 1L).isEmpty())
        assertTrue(BucketDao.getBackups(conn, 1L).all { it == null })
    }

    @Test
    @DisplayName("分配槽位编号时优先填补空洞")
    fun nextFreeIdFillsGaps() {
        assertEquals(1L, BucketDao.nextFreeId(conn))

        create(1L, "A")
        create(2L, "B")
        create(3L, "C")
        assertEquals(4L, BucketDao.nextFreeId(conn))

        BucketDao.delete(conn, 2L)
        assertEquals(2L, BucketDao.nextFreeId(conn), "应当复用被删除的 2 号槽位")

        create(2L, "B2")
        assertEquals(4L, BucketDao.nextFreeId(conn))
    }

    @Test
    @DisplayName("全部槽位按编号升序返回，空置槽位为 null")
    fun listSlotsIsOrdered() {
        create(1L, "A")
        create(2L, "B")
        create(3L, "C")
        BucketDao.delete(conn, 2L)

        val slots = BucketDao.listSlots(conn)
        assertEquals(listOf(1L, 2L, 3L), slots.keys.toList())
        assertEquals("A", slots[1L]?.name)
        assertNull(slots[2L])
        assertEquals("C", slots[3L]?.name)
        assertEquals(2, BucketDao.count(conn))
    }

    /* ==================== 字段修改 ==================== */

    @Test
    @DisplayName("逐字段修改互不影响")
    fun typedSettersUpdateOnlyOneColumn() {
        create(1L, "旧名")
        BucketDao.setName(conn, 1L, "新名")
        BucketDao.setPassword(conn, 1L, "new-hash")
        BucketDao.setOwner(conn, 1L, "新所有者")
        BucketDao.setUserID(conn, 1L, "999")
        BucketDao.setDescription(conn, 1L, "一句简介")
        BucketDao.setEncrypt(conn, 1L, true)

        val bucket = requireBucket(1L)
        assertEquals("新名", bucket.name)
        assertEquals("new-hash", bucket.password)
        assertEquals("新所有者", bucket.owner)
        assertEquals("999", bucket.userID)
        assertEquals("一句简介", bucket.description)
        assertTrue(bucket.encrypt)
    }

    @Test
    @DisplayName("主存储数据无损存取，content_len 始终等于正文长度")
    fun rawContentRoundTrip() {
        create(1L, "A")
        for (value in TestDb.HOSTILE_VALUES) {
            BucketDao.setRawContent(conn, 1L, value)
            assertEquals(value, BucketDao.getRawContent(conn, 1L))
            assertEquals(value.length, BucketDao.get(conn, 1L)?.contentLen)
        }
    }

    /* ==================== 关联项目 ==================== */

    @Test
    @DisplayName("覆盖关联列表会清掉旧记录")
    fun setProjectsReplaces() {
        create(1L, "A")
        BucketDao.setProjects(conn, 1L, listOf("p1", "p2", "p3"))
        BucketDao.setProjects(conn, 1L, listOf("p9"))

        assertEquals(listOf("p9"), BucketDao.getProjects(conn, 1L))
        assertTrue(BucketDao.linkedIds(conn, "p1").isEmpty())
    }

    @Test
    @DisplayName("按项目反查关联的存储库编号，空置槽位不参与")
    fun linkedIdsSkipEmptySlots() {
        create(1L, "A")
        create(2L, "B")
        create(3L, "C")
        BucketDao.setProjects(conn, 1L, listOf("shared"))
        BucketDao.setProjects(conn, 2L, listOf("shared", "only2"))
        BucketDao.setProjects(conn, 3L, listOf("shared"))

        assertEquals(listOf(1L, 2L, 3L), BucketDao.linkedIds(conn, "shared"))
        assertEquals(listOf(2L), BucketDao.linkedIds(conn, "only2"))
        assertEquals(4L, BucketDao.totalLinkedProjects(conn))

        BucketDao.delete(conn, 2L)
        assertEquals(listOf(1L, 3L), BucketDao.linkedIds(conn, "shared"))
        assertTrue(BucketDao.linkedIds(conn, "only2").isEmpty())
    }

    @Test
    @DisplayName("删除项目时从全部存储库解除关联")
    fun removeProjectFromAllBuckets() {
        create(1L, "A")
        create(2L, "B")
        BucketDao.setProjects(conn, 1L, listOf("gone", "keep"))
        BucketDao.setProjects(conn, 2L, listOf("gone"))

        BucketDao.removeProjectFromAll(conn, "gone")

        assertTrue(BucketDao.linkedIds(conn, "gone").isEmpty())
        assertEquals(listOf("keep"), BucketDao.getProjects(conn, 1L))
        assertTrue(BucketDao.getProjects(conn, 2L).isEmpty())
    }

    @Test
    @DisplayName("项目改名时同步全部存储库的关联记录")
    fun renameProjectInAllBuckets() {
        create(1L, "A")
        create(2L, "B")
        BucketDao.setProjects(conn, 1L, listOf("old", "other"))
        BucketDao.setProjects(conn, 2L, listOf("old"))

        BucketDao.renameProjectInAll(conn, "old", "new")

        assertEquals(listOf(1L, 2L), BucketDao.linkedIds(conn, "new"))
        assertTrue(BucketDao.linkedIds(conn, "old").isEmpty())
        assertEquals(listOf("new", "other"), BucketDao.getProjects(conn, 1L))
    }

    @Test
    @DisplayName("改名撞上同一存储库已有的目标名时不违反主键")
    fun renameProjectCollidingInSameBucket() {
        create(1L, "A")
        BucketDao.setProjects(conn, 1L, listOf("old", "new"))

        BucketDao.renameProjectInAll(conn, "old", "new")

        assertEquals(listOf("new"), BucketDao.getProjects(conn, 1L))
        assertEquals(listOf(1L), BucketDao.linkedIds(conn, "new"))
    }

    /* ==================== 备份 ==================== */

    @Test
    @DisplayName("备份槽位固定 3 个，空槽为 null")
    fun backupsHaveFixedSlots() {
        create(1L, "A")
        val backups = BucketDao.getBackups(conn, 1L)

        assertEquals(Schema.BACKUP_SLOTS, backups.size)
        assertTrue(backups.all { it == null })
    }

    @Test
    @DisplayName("备份的写入、覆盖与清空")
    fun backupWriteAndClear() {
        create(1L, "A")
        BucketDao.setBackup(conn, 1L, 0, BucketDao.BackupRow("首个备份", 1000L, "内容0"))
        BucketDao.setBackup(conn, 1L, 2, BucketDao.BackupRow("第三个", 3000L, "内容2"))

        assertEquals(BucketDao.BackupRow("首个备份", 1000L, "内容0"), BucketDao.getBackup(conn, 1L, 0))
        assertNull(BucketDao.getBackup(conn, 1L, 1))
        assertEquals("第三个", BucketDao.getBackups(conn, 1L)[2]?.name)

        BucketDao.setBackup(conn, 1L, 0, BucketDao.BackupRow("改名后", 1001L, "内容0"))
        assertEquals("改名后", BucketDao.getBackup(conn, 1L, 0)?.name)
        assertEquals(1001L, BucketDao.getBackup(conn, 1L, 0)?.time)

        BucketDao.setBackup(conn, 1L, 0, null)
        assertNull(BucketDao.getBackup(conn, 1L, 0))
        assertNotNull(BucketDao.getBackup(conn, 1L, 2))
    }

    @Test
    @DisplayName("越界槽位既不写入也不抛异常")
    fun outOfRangeSlotIsIgnored() {
        create(1L, "A")
        BucketDao.setBackup(conn, 1L, 3, BucketDao.BackupRow("越界", 1L, "x"))
        BucketDao.setBackup(conn, 1L, -1, BucketDao.BackupRow("越界", 1L, "x"))

        assertNull(BucketDao.getBackup(conn, 1L, 3))
        assertNull(BucketDao.getBackup(conn, 1L, -1))
        assertEquals(0L, BucketDao.totalBackupLength(conn))
    }
}
