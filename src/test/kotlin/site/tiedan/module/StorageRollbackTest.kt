package site.tiedan.module

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.tiedan.data.Database
import site.tiedan.data.dao.StorageDao
import java.io.File

/**
 * 备份快照由真实的 `VACUUM INTO` 生成，与线上备份走的是同一条路径。
 */
class StorageRollbackTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var snapshot: StorageRollback.Snapshot

    @BeforeEach
    fun setUp() {
        Database.close()

        // 「备份时」的数据
        Database.initialize(File(tempDir, "origin/storage.db"))
        Database.transaction { conn ->
            StorageDao.setGlobal(conn, PROJECT, "global-旧")
            StorageDao.setUser(conn, PROJECT, "qq", 111L, "qq111-旧")
            StorageDao.setUser(conn, PROJECT, "kook", 111L, "kook111-旧")
            StorageDao.setUser(conn, PROJECT, "qq", 222L, "qq222-仅备份有")
            StorageDao.setUser(conn, PROJECT, "qq", 999L, "qq999-两侧一致")
            StorageDao.setGlobal(conn, OTHER, "不该被动")
        }
        val snapshotFile = Database.snapshotTo(File(tempDir, "backup/2026-09-08/storage.db"))
        Database.close()
        snapshot = StorageRollback.Snapshot(1, "2026-09-08", StorageRollback.KIND_DAILY, 0L, snapshotFile)

        // 「当前」的数据
        Database.initialize(File(tempDir, "live/storage.db"))
        Database.transaction { conn ->
            StorageDao.setGlobal(conn, PROJECT, "global-新")
            StorageDao.setUser(conn, PROJECT, "qq", 111L, "qq111-新")
            StorageDao.setUser(conn, PROJECT, "kook", 111L, "kook111-新")
            StorageDao.setUser(conn, PROJECT, "qq", 333L, "qq333-备份后新增")
            StorageDao.setUser(conn, PROJECT, "qq", 999L, "qq999-两侧一致")
            StorageDao.setGlobal(conn, OTHER, "不该被动")
        }
    }

    @AfterEach
    fun tearDown() {
        Database.close()
    }

    private fun global(project: String = PROJECT): String? =
        Database.read { StorageDao.getGlobal(it, project) }

    private fun user(platform: String, userId: Long): String? =
        Database.read { StorageDao.getUser(it, PROJECT, platform, userId) }

    private fun planAll(project: String = PROJECT): StorageRollback.Plan =
        StorageRollback.load(snapshot, project).plan(StorageRollback.Target.ALL)

    @Test
    @DisplayName("逐条对照涵盖两侧全部条目，并给出正确的变化类型")
    fun diffClassifiesEveryEntry() {
        val byLabel = planAll().diffs.associateBy { it.label }

        assertEquals(setOf("global", "111", "222", "333", "999", "kook_111"), byLabel.keys)
        assertEquals("覆盖", byLabel.getValue("global").action)
        assertEquals("覆盖", byLabel.getValue("111").action)
        assertEquals("覆盖", byLabel.getValue("kook_111").action)
        assertEquals("恢复", byLabel.getValue("222").action, "仅备份中存在，回滚后应被恢复")
        assertEquals("删除", byLabel.getValue("333").action, "备份之后新增，回滚后应被删除")
        assertEquals("不变", byLabel.getValue("999").action)
        assertEquals(5, planAll().changed.size)
    }

    @Test
    @DisplayName("回滚全部存储后与备份完全一致，且不波及其他项目")
    fun applyAllRestoresSnapshotExactly() {
        assertEquals(5, StorageRollback.apply(planAll()))

        assertEquals("global-旧", global())
        assertEquals("qq111-旧", user("qq", 111L))
        assertEquals("kook111-旧", user("kook", 111L))
        assertEquals("qq222-仅备份有", user("qq", 222L))
        assertEquals("qq999-两侧一致", user("qq", 999L))
        assertNull(user("qq", 333L), "备份之后新增的用户数据应被删除")
        assertEquals("不该被动", global(OTHER))

        assertTrue(planAll().changed.isEmpty(), "回滚完成后不应再有差异")
    }

    @Test
    @DisplayName("只回滚 global 时不动任何用户数据")
    fun globalTargetTouchesOnlyGlobal() {
        val plan = StorageRollback.load(snapshot, PROJECT).plan(StorageRollback.Target.GLOBAL)

        assertEquals(1, plan.diffs.size)
        assertEquals(1, StorageRollback.apply(plan))
        assertEquals("global-旧", global())
        assertEquals("qq111-新", user("qq", 111L))
        assertEquals("kook111-新", user("kook", 111L))
        assertEquals("qq333-备份后新增", user("qq", 333L))
        assertNull(user("qq", 222L), "未指定的条目不应被恢复")
    }

    @Test
    @DisplayName("按用户回滚区分平台：同一用户号在不同平台互不影响")
    fun userTargetIsPlatformSpecific() {
        val loaded = StorageRollback.load(snapshot, PROJECT)
        val target = loaded.resolveTarget("kook_111")
        assertNotNull(target)

        assertEquals(1, StorageRollback.apply(loaded.plan(target!!)))
        assertEquals("kook111-旧", user("kook", 111L))
        assertEquals("qq111-新", user("qq", 111L), "QQ 同号用户不应被牵连")
        assertEquals("global-新", global())
    }

    @Test
    @DisplayName("仅当前存在的用户，回滚即删除该条数据")
    fun userTargetAbsentInBackupIsDeleted() {
        val loaded = StorageRollback.load(snapshot, PROJECT)
        val plan = loaded.plan(loaded.resolveTarget("333")!!)

        assertEquals("删除", plan.diffs.single().action)
        assertEquals(1, StorageRollback.apply(plan))
        assertNull(user("qq", 333L))
        assertEquals("global-新", global(), "单用户回滚不应影响 global")
    }

    @Test
    @DisplayName("目标关键字解析：中英文关键字均可，两侧都没有的ID返回 null")
    fun resolveTargetHandlesKeywordsAndUnknownID() {
        val loaded = StorageRollback.load(snapshot, PROJECT)

        assertEquals(StorageRollback.Target.ALL, loaded.resolveTarget("all"))
        assertEquals(StorageRollback.Target.ALL, loaded.resolveTarget("全部"))
        assertEquals(StorageRollback.Target.GLOBAL, loaded.resolveTarget("GLOBAL"))
        assertEquals(StorageRollback.Target.GLOBAL, loaded.resolveTarget("全局"))
        assertNotNull(loaded.resolveTarget("222"), "仅备份中存在的用户也是合法目标")
        assertNull(loaded.resolveTarget("55555"))
        assertNull(loaded.resolveTarget("kook_55555"))
    }

    @Test
    @DisplayName("二次确认期间数据被改写，方案指纹判定过期")
    fun fingerprintDetectsConcurrentWrite() {
        val plan = planAll()
        StorageRollback.remember(USER, plan)
        assertTrue(StorageRollback.matches(USER, plan))

        // 模拟排队中的执行进程拿到锁后写入了新数据
        Database.transaction { StorageDao.setUser(it, PROJECT, "qq", 111L, "执行进程写入") }

        assertFalse(StorageRollback.matches(USER, planAll()), "当前数据变化后方案必须失效")

        StorageRollback.forget(USER)
        assertFalse(StorageRollback.matches(USER, plan))
    }

    @Test
    @DisplayName("备份内容被换掉时方案同样失效，即使当前数据未变")
    fun fingerprintCoversBackupSide() {
        val plan = planAll()
        StorageRollback.remember(USER, plan)

        // 每日备份同日重跑会原地覆盖同名目录下的快照：标签不变，内容却换了
        Database.close()
        Database.initialize(File(tempDir, "origin2/storage.db"))
        Database.transaction { StorageDao.setGlobal(it, PROJECT, "global-另一份备份") }
        val replaced = Database.snapshotTo(File(tempDir, "backup2/storage.db"))
        Database.close()
        Database.initialize(File(tempDir, "live/storage.db"))

        val sameLabel = snapshot.copy(file = replaced)
        assertFalse(
            StorageRollback.matches(USER, StorageRollback.load(sameLabel, PROJECT).plan(StorageRollback.Target.ALL)),
            "备份侧内容变化必须让方案失效",
        )
        StorageRollback.forget(USER)
    }

    @Test
    @DisplayName("备份中没有该项目时判定为空，不产生任何改动")
    fun snapshotWithoutProjectIsEmpty() {
        val plan = planAll("从未备份过的项目")

        assertTrue(plan.snapshotEmpty)
        assertTrue(plan.diffs.isEmpty())
        assertEquals(0, StorageRollback.apply(plan))
    }

    @Test
    @DisplayName("回滚全程不改动备份文件，也不在备份目录留下 WAL")
    fun snapshotFileIsNeverModified() {
        val sizeBefore = snapshot.file.length()
        val hashBefore = snapshot.file.readBytes().contentHashCode()

        StorageRollback.apply(planAll())

        assertEquals(sizeBefore, snapshot.file.length())
        assertEquals(hashBefore, snapshot.file.readBytes().contentHashCode())
        assertFalse(File("${snapshot.file.absolutePath}-wal").exists(), "只读打开不应创建 WAL 文件")
    }

    private companion object {
        const val PROJECT = "测试项目"
        const val OTHER = "其他项目"
        const val USER = "rollback-test-user"
    }
}
