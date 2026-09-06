package site.tiedan.core

import net.mamoe.mirai.console.command.CommandSender
import site.tiedan.MiraiCompilerFramework.sendQuoteReply

/**
 * # 指令侧的存储锁守卫
 * - 指令与执行进程共用同一套存储锁，避免「指令改数据」与「项目写入数据」互相覆盖。
 * - 指令执行被占用时**必须先提示操作者**本次操作要排队。
 *
 * @author tiedanGH
 */
object StorageLockGuard {

    private val timeoutSeconds = StorageManager.LOCK_WAIT_TIMEOUT_MS / 1000

    /** 锁定项目及其关联的全部存储库 */
    suspend fun CommandSender.lockProject(name: String): StorageManager.StorageLock? =
        guard("项目 $name", StorageManager.isProjectLocked(name)) {
            StorageManager.awaitProjectLock(name)
        }

    /** 锁定单个存储库 */
    suspend fun CommandSender.lockBucket(id: Long): StorageManager.StorageLock? =
        guard("存储库 $id", StorageManager.isBucketLocked(id)) {
            StorageManager.awaitBucketLock(id)
        }

    /** 锁定项目与指定存储库，用于改变两者关联关系的操作 */
    suspend fun CommandSender.lockProjectAndBucket(name: String, bucketId: Long): StorageManager.StorageLock? =
        guard(
            "项目 $name 或存储库 $bucketId",
            StorageManager.isProjectLocked(name) || StorageManager.isBucketLocked(bucketId),
        ) {
            StorageManager.awaitProjectAndBucketLock(name, bucketId)
        }

    private suspend fun CommandSender.guard(
        target: String,
        busy: Boolean,
        acquire: suspend () -> StorageManager.StorageLock?,
    ): StorageManager.StorageLock? {
        if (busy) {
            sendQuoteReply("⏳ $target 正在执行中，本次操作将在执行完成后继续，请稍候...")
        }
        val lock = acquire()
        if (lock == null) {
            sendQuoteReply("[操作超时] $target 已被占用超过 $timeoutSeconds 秒，本次操作已取消，请稍后重试")
        }
        return lock
    }
}
