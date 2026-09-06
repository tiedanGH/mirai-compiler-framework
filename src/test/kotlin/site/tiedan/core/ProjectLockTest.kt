package site.tiedan.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.tiedan.data.Database
import site.tiedan.data.dao.BucketDao
import java.io.File

/**
 * 项目级并发锁：不同项目并行，同项目或共享存储库时排队
 */
class ProjectLockTest {

    /** 被阻塞时的等待上限，超过即判定为「拿不到锁」 */
    private val blockedMs = 200L

    /** 应当能拿到锁时的等待上限，给足余量避免偶发抖动 */
    private val freeMs = 2000L

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        Database.close()
        Database.initialize(File(tempDir, Database.FILE_NAME))
    }

    @AfterEach
    fun tearDown() {
        Database.close()
    }

    private fun linkBucket(id: Long, vararg projects: String) {
        Database.transaction { conn ->
            BucketDao.create(conn, id, "库$id", "hash", "owner", "1")
            BucketDao.setProjects(conn, id, projects.toList())
        }
    }

    @Test
    @DisplayName("同一项目的两次执行必须排队")
    fun sameProjectSerializes() {
        runBlocking {
            val first = StorageManager.acquireProjectLock("同项目")
            assertTrue(StorageManager.isProjectLocked("同项目"))

            val blocked = withTimeoutOrNull(blockedMs) { StorageManager.acquireProjectLock("同项目") }
            assertNull(blocked, "同项目的第二次获取必须被阻塞")

            first.release()
            val second = withTimeoutOrNull(freeMs) { StorageManager.acquireProjectLock("同项目") }
            assertNotNull(second, "前一次释放后应当立刻可获取")
            second?.release()
        }
    }

    @Test
    @DisplayName("不同项目互不阻塞")
    fun differentProjectsRunInParallel() {
        runBlocking {
            val a = StorageManager.acquireProjectLock("独立A")
            val b = withTimeoutOrNull(freeMs) { StorageManager.acquireProjectLock("独立B") }

            assertNotNull(b, "不同项目之间不应排队——这正是项目级锁的意义")
            assertTrue(StorageManager.isProjectLocked("独立A"))
            assertTrue(StorageManager.isProjectLocked("独立B"))

            b?.release()
            a.release()
        }
    }

    @Test
    @DisplayName("共享同一存储库的不同项目必须排队")
    fun projectsSharingBucketSerialize() {
        runBlocking {
            linkBucket(1L, "共享甲", "共享乙")

            val first = StorageManager.acquireProjectLock("共享甲")
            val blocked = withTimeoutOrNull(blockedMs) { StorageManager.acquireProjectLock("共享乙") }
            assertNull(blocked, "两个项目写同一个存储库时必须串行，否则会丢更新")

            first.release()
            val second = withTimeoutOrNull(freeMs) { StorageManager.acquireProjectLock("共享乙") }
            assertNotNull(second, "前一次释放后应当立刻可获取")
            second?.release()
        }
    }

    @Test
    @DisplayName("等待存储库锁时被取消，必须退还已拿到的项目锁")
    fun cancelledAcquireReleasesPartialLocks() {
        runBlocking {
            linkBucket(2L, "部分甲", "部分乙")

            val first = StorageManager.acquireProjectLock("部分甲")
            // 「部分乙」会先拿到自己的项目锁，再卡在存储库锁上被超时取消
            assertNull(withTimeoutOrNull(blockedMs) { StorageManager.acquireProjectLock("部分乙") })

            assertFalse(
                StorageManager.isProjectLocked("部分乙"),
                "中途失败必须退还已拿到的锁，否则该项目会被永久锁死"
            )
            first.release()
        }
    }

    @Test
    @DisplayName("关联多个存储库时按编号升序加锁，不会死锁")
    fun multipleBucketsLockInOrder() {
        runBlocking {
            // 两个项目关联同一组存储库，但在各自存储库中的声明顺序相反
            Database.transaction { conn ->
                BucketDao.create(conn, 3L, "库3", "hash", "owner", "1")
                BucketDao.create(conn, 4L, "库4", "hash", "owner", "1")
                BucketDao.setProjects(conn, 3L, listOf("多库甲", "多库乙"))
                BucketDao.setProjects(conn, 4L, listOf("多库乙", "多库甲"))
            }

            val first = StorageManager.acquireProjectLock("多库甲")
            assertNull(withTimeoutOrNull(blockedMs) { StorageManager.acquireProjectLock("多库乙") })

            first.release()
            val second = withTimeoutOrNull(freeMs) { StorageManager.acquireProjectLock("多库乙") }
            assertNotNull(second, "释放后另一个项目应当能拿到全部存储库锁")
            second?.release()
        }
    }

    @Test
    @DisplayName("重复释放无副作用")
    fun releaseIsIdempotent() {
        runBlocking {
            val lock = StorageManager.acquireProjectLock("幂等")
            lock.release()
            lock.release()
            lock.release()

            val again = withTimeoutOrNull(freeMs) { StorageManager.acquireProjectLock("幂等") }
            assertNotNull(again, "重复释放不得把锁的状态搞乱")
            again?.release()
        }
    }

    @Test
    @DisplayName("项目执行期间，指令无法拿到它关联存储库的锁")
    fun bucketLockBlockedByRunningProject() {
        runBlocking {
            linkBucket(6L, "执行中项目")

            // 模拟执行进程持有项目锁（连带锁住存储库 6）
            val running = StorageManager.acquireProjectLock("执行中项目")
            assertTrue(StorageManager.isBucketLocked(6L), "项目锁应当连带锁住关联存储库")

            val command = withTimeoutOrNull(blockedMs) { StorageManager.acquireBucketLock(6L) }
            assertNull(command, "bk 指令必须等执行进程写完，否则会互相覆盖")

            running.release()
            val after = withTimeoutOrNull(freeMs) { StorageManager.acquireBucketLock(6L) }
            assertNotNull(after, "执行结束后指令应当立刻可操作")
            after?.release()
        }
    }

    @Test
    @DisplayName("指令持有存储库锁期间，项目无法开始执行")
    fun runningProjectBlockedByBucketLock() {
        runBlocking {
            linkBucket(7L, "待执行项目")

            // 模拟 bk 指令正在改写存储库
            val command = StorageManager.acquireBucketLock(7L)
            val running = withTimeoutOrNull(blockedMs) { StorageManager.acquireProjectLock("待执行项目") }
            assertNull(running, "存储库正被指令改写时，执行进程必须等待")

            command.release()
            val after = withTimeoutOrNull(freeMs) { StorageManager.acquireProjectLock("待执行项目") }
            assertNotNull(after, "指令结束后执行进程应当立刻可开始")
            after?.release()
        }
    }

    @Test
    @DisplayName("关联操作同时锁住项目与目标存储库")
    fun projectAndBucketLockCoversBoth() {
        runBlocking {
            // 目标存储库尚未与该项目关联，仍必须一并锁住
            Database.transaction { conn ->
                BucketDao.create(conn, 8L, "库8", "hash", "owner", "1")
            }

            val link = StorageManager.acquireProjectAndBucketLock("关联操作项目", 8L)
            assertTrue(StorageManager.isProjectLocked("关联操作项目"))
            assertTrue(StorageManager.isBucketLocked(8L), "尚未关联的目标存储库也要锁住")

            assertNull(
                withTimeoutOrNull(blockedMs) { StorageManager.acquireBucketLock(8L) },
                "关联操作进行中，其他指令不得改写目标存储库"
            )

            link.release()
            assertFalse(StorageManager.isProjectLocked("关联操作项目"))
            assertFalse(StorageManager.isBucketLocked(8L))
        }
    }

    @Test
    @DisplayName("未关联存储库的项目不牵连任何存储库")
    fun unlinkedProjectDoesNotTouchBuckets() {
        runBlocking {
            linkBucket(5L, "关联者")

            val other = StorageManager.acquireProjectLock("无关者")
            val linked = withTimeoutOrNull(freeMs) { StorageManager.acquireProjectLock("关联者") }

            assertNotNull(linked, "未共享存储库的项目之间不应互相阻塞")
            linked?.release()
            other.release()
        }
    }
}
