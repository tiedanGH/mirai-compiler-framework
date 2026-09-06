package site.tiedan.data

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.tiedan.data.dao.CodeCacheDao
import site.tiedan.data.dao.Schema
import site.tiedan.data.dao.StorageDao
import java.io.File

class DatabaseTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dbFile: File

    @BeforeEach
    fun setUp() {
        Database.close()
        dbFile = File(tempDir, "sub/dir/${Database.FILE_NAME}")
        Database.initialize(dbFile)
    }

    @AfterEach
    fun tearDown() {
        Database.close()
    }

    @Test
    @DisplayName("初始化自动创建目录、库文件与全部表")
    fun initializeCreatesFileAndTables() {
        assertTrue(dbFile.exists(), "库文件应当被创建")
        assertTrue(Database.isInitialized)
        assertEquals(dbFile, Database.file)
        assertEquals(Schema.VERSION, Database.read { Schema.readVersion(it) })
        assertEquals("ok", Database.quickCheck())
    }

    @Test
    @DisplayName("重复初始化会被拒绝")
    fun doubleInitializeIsRejected() {
        assertThrows(IllegalStateException::class.java) { Database.initialize(dbFile) }
    }

    @Test
    @DisplayName("关闭后再访问会明确报错，而不是静默写丢数据")
    fun accessAfterCloseFails() {
        Database.close()
        assertFalse(Database.isInitialized)
        assertThrows(IllegalStateException::class.java) { Database.read { it } }
    }

    @Test
    @DisplayName("事务提交后数据可见")
    fun transactionCommits() {
        Database.transaction { CodeCacheDao.put(it, "proj", "code") }
        assertEquals("code", Database.read { CodeCacheDao.get(it, "proj") })
    }

    @Test
    @DisplayName("事务抛异常时整体回滚")
    fun transactionRollsBackOnFailure() {
        Database.transaction { CodeCacheDao.put(it, "kept", "before") }

        val error = assertThrows(IllegalStateException::class.java) {
            Database.transaction { conn ->
                CodeCacheDao.put(conn, "a", "1")
                CodeCacheDao.put(conn, "b", "2")
                error("模拟写入中途失败")
            }
        }

        assertEquals("模拟写入中途失败", error.message)
        assertEquals(1, Database.read { CodeCacheDao.count(it) })
        assertEquals("before", Database.read { CodeCacheDao.get(it, "kept") })
    }

    @Test
    @DisplayName("嵌套事务并入外层，由最外层统一提交")
    fun nestedTransactionJoinsOuter() {
        Database.transaction { outer ->
            CodeCacheDao.put(outer, "a", "1")
            Database.transaction { inner -> CodeCacheDao.put(inner, "b", "2") }
        }
        assertEquals(2, Database.read { CodeCacheDao.count(it) })
    }

    @Test
    @DisplayName("内层失败时外层事务整体回滚")
    fun nestedTransactionRollsBackAsOne() {
        assertThrows(IllegalStateException::class.java) {
            Database.transaction { outer ->
                CodeCacheDao.put(outer, "a", "1")
                Database.transaction<Unit> { error("内层失败") }
            }
        }
        assertEquals(0, Database.read { CodeCacheDao.count(it) })
    }

    @Test
    @DisplayName("事务结束后恢复自动提交")
    fun autoCommitRestoredAfterTransaction() {
        Database.transaction { CodeCacheDao.put(it, "a", "1") }
        assertTrue(Database.read { it.autoCommit })

        runCatching { Database.transaction<Unit> { error("失败也要恢复") } }
        assertTrue(Database.read { it.autoCommit })
    }

    @Test
    @DisplayName("VACUUM INTO 生成的快照包含完整数据")
    fun snapshotContainsAllData() {
        Database.transaction { conn ->
            StorageDao.setGlobal(conn, "proj", "global data")
            StorageDao.setUser(conn, "proj", "kook", 42L, "kook data")
            CodeCacheDao.put(conn, "proj", "print('hi')")
        }

        val snapshot = Database.snapshotTo(File(tempDir, "backup/2026-09-06/storage.db"))
        assertTrue(snapshot.exists())
        assertTrue(snapshot.length() > 0)

        // 快照是独立的库文件，可以脱离原连接单独打开
        Database.close()
        Database.initialize(snapshot)
        assertEquals("global data", Database.read { StorageDao.getGlobal(it, "proj") })
        assertEquals("kook data", Database.read { StorageDao.getUser(it, "proj", "kook", 42L) })
        assertEquals("print('hi')", Database.read { CodeCacheDao.get(it, "proj") })
    }

    @Test
    @DisplayName("快照覆盖已存在的目标文件")
    fun snapshotOverwritesExistingTarget() {
        val target = File(tempDir, "backup/storage.db")
        target.parentFile.mkdirs()
        target.writeText("占位内容，VACUUM INTO 遇到已存在的目标会直接失败")

        Database.transaction { CodeCacheDao.put(it, "proj", "code") }
        Database.snapshotTo(target)

        Database.close()
        Database.initialize(target)
        assertEquals("code", Database.read { CodeCacheDao.get(it, "proj") })
    }

    @Test
    @DisplayName("快照不能在事务内生成")
    fun snapshotRejectedInsideTransaction() {
        assertThrows(IllegalStateException::class.java) {
            Database.transaction { Database.snapshotTo(File(tempDir, "bad.db")) }
        }
    }

    @Test
    @DisplayName("初始化标记与业务数据判定")
    fun initializationFlags() {
        assertFalse(Database.isDataInitialized())
        assertFalse(Database.hasAnyData())

        Database.markDataInitialized()
        assertTrue(Database.isDataInitialized())
        assertFalse(Database.hasAnyData(), "只置标记不写数据时仍应判定为空库")

        Database.transaction { CodeCacheDao.put(it, "proj", "code") }
        assertTrue(Database.hasAnyData())
    }

    @Test
    @DisplayName("关闭后数据仍在磁盘上，重开可读")
    fun dataSurvivesReopen() {
        Database.transaction { StorageDao.setGlobal(it, "proj", "persisted") }
        Database.close()

        Database.initialize(dbFile)
        assertEquals("persisted", Database.read { StorageDao.getGlobal(it, "proj") })
    }
}
