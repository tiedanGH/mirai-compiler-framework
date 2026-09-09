package site.tiedan.module

import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.THREADS
import site.tiedan.MiraiCompilerFramework.baseDataFolder
import site.tiedan.MiraiCompilerFramework.cacheFolder
import site.tiedan.MiraiCompilerFramework.enabledAt
import site.tiedan.MiraiCompilerFramework.imageFolder
import site.tiedan.config.PastebinConfig
import site.tiedan.core.OutputHandler
import site.tiedan.core.StorageManager
import site.tiedan.data.Database
import site.tiedan.utils.FileSizeUtil.folderSize
import site.tiedan.utils.FileSizeUtil.formatSize
import java.io.File

/**
 * # 框架运行状态
 * - 此报告任何人可见，因此刻意不输出任何绝对路径与异常原文。
 *
 * @author tiedanGH
 */
object StatusReport {

    fun generate(): String {
        val version = MiraiCompilerFramework.description.version
        val backup = BackupManager.status()

        val dbSize = Database.fileSize()
        val walSize = Database.walSize()
        val imageSize = folderSize(File(imageFolder))
        val cacheSize = folderSize(File(cacheFolder))
        val dataSize = folderSize(File(baseDataFolder))

        return buildString {
            appendLine(" · 框架运行状态　v$version")
            appendLine("⏱️ 已运行：${formatUptime(System.currentTimeMillis() - enabledAt)}")
            appendLine()

            appendLine("💾 数据库")
            if (Database.isInitialized) {
                appendLine(" · 连接：✅ 正常（SQLite ${Database.sqliteVersion()}）")
                val check = Database.cachedQuickCheck()
                appendLine(" · 完整性：${if (check == "ok") "✅ ok（表结构 v${Database.schemaVersion()}）" else "❌ $check"}")
                appendLine(" · 大小：${formatSize(dbSize)}（WAL ${formatSize(walSize)}）")
            } else {
                appendLine(" · 连接：❌ 未初始化，存储相关功能全部不可用")
            }
            appendLine(" · 累计错误：${formatErrors()}")
            appendLine(DataAudit.format(DataAudit.scan()))
            appendLine()

            appendLine("⚙️ 运行中")
            appendLine(" · 执行进程：${THREADS.size} / ${PastebinConfig.thread_limit}")
            appendLine(" · 输出进程：${OutputHandler.activeCount()} / ${OutputHandler.limit}")
            val lockedProjects = StorageManager.lockedProjectCount()
            val lockedBuckets = StorageManager.lockedBucketCount()
            appendLine(
                " · 存储锁：" +
                if (lockedProjects == 0 && lockedBuckets == 0) "空闲"
                else "${lockedProjects}项目 / ${lockedBuckets}存储库"
            )
            appendLine()

            val others = (dataSize - dbSize - walSize - imageSize - cacheSize - backup.totalSize).coerceAtLeast(0)
            appendLine("📁 数据目录：${formatSize(dataSize)}")
            appendLine(" · 数据库：${formatSize(dbSize + walSize)}")
            appendLine(" · 图片：${formatSize(imageSize)}")
            appendLine(" · 缓存：${formatSize(cacheSize)}")
            appendLine(" · 备份：${formatSize(backup.totalSize)}")
            appendLine(" · 其他：${formatSize(others)}")
            appendLine("🗂 备份记录")
            appendLine(" · 每日备份：${backup.lastDaily ?: "暂无"}（${backup.dailyCount}份）")
            append(" · 关机备份：${backup.lastShutdown ?: "暂无"}（${backup.shutdownCount}份）")
        }
    }

    /**
     * 只报异常类名与相对时间，不输出异常原文
     */
    private fun formatErrors(): String {
        val total = Database.totalErrors
        if (total == 0L) return "无"
        val last = Database.lastError ?: return "$total 次"
        val ago = formatUptime(System.currentTimeMillis() - last.time)
        return "$total 次（最近 ${last.type}，$ago 前）"
    }

    private fun formatUptime(millis: Long): String {
        if (millis < 0) return "未知"
        val totalMinutes = millis / 60_000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}天${hours}小时${minutes}分"
            hours > 0 -> "${hours}小时${minutes}分"
            else -> "${minutes}分"
        }
    }
}
