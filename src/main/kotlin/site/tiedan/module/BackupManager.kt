package site.tiedan.module

import net.mamoe.mirai.utils.info
import site.tiedan.MiraiCompilerFramework.baseDataFolder
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.data.Database
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * # 数据备份与完整性管理
 * - 每日定时备份 [dailyBackup]
 * - 关闭时额外备份 [backupOnShutdown]
 * - 存储数据完整性检查 [checkStorageIntegrity]
 *
 * @author tiedanGH
 */
object BackupManager {

    /** 每日定时备份保留的最大天数 */
    private const val DAILY_BACKUP_KEEP = 7

    /** 关闭时备份保留的最大数量 */
    private const val SHUTDOWN_BACKUP_KEEP = 5

    /** 存储数据完整性标记文件名（用于区分首次启动与存储数据丢失） */
    private const val STORAGE_MARKER_FILE = ".storage_initialized"

    /**
     * 每日定时备份全部 yml 数据文件，保留最近 [DAILY_BACKUP_KEEP] 天
     */
    fun dailyBackup() {
        val baseDir = File(baseDataFolder)
        if (!baseDir.exists() || !baseDir.isDirectory) return

        val bakDir = File(baseDir, "backup")
        if (!bakDir.exists()) bakDir.mkdirs()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        copyYmlFilesTo(File(bakDir, today))

        // 仅清理按日期命名的备份目录，保留最近 DAILY_BACKUP_KEEP 天（排除 shutdown 目录）
        val dateDirs = bakDir.listFiles { file -> file.isDirectory && file.name != "shutdown" }?.toList() ?: return
        if (dateDirs.size > DAILY_BACKUP_KEEP) {
            dateDirs.sortedBy { it.name }
                .take(dateDirs.size - DAILY_BACKUP_KEEP)
                .forEach { dir -> dir.deleteRecursively() }
        }
        logger.info { "YML 数据文件自动备份完成" }
    }

    /**
     * 关闭插件时额外备份一次数据，保留最近 [SHUTDOWN_BACKUP_KEEP] 次
     */
    fun backupOnShutdown() {
        try {
            val baseDir = File(baseDataFolder)
            if (!baseDir.exists() || !baseDir.isDirectory) return

            val shutdownDir = File(baseDir, "backup/shutdown")
            if (!shutdownDir.exists()) shutdownDir.mkdirs()

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"))
            copyYmlFilesTo(File(shutdownDir, timestamp))

            // 保留最近若干次关闭备份
            val dirs = shutdownDir.listFiles { file -> file.isDirectory }?.toList() ?: return
            if (dirs.size > SHUTDOWN_BACKUP_KEEP) {
                dirs.sortedBy { it.name }
                    .take(dirs.size - SHUTDOWN_BACKUP_KEEP)
                    .forEach { dir -> dir.deleteRecursively() }
            }
            logger.info { "关闭时数据备份完成（$timestamp）" }
        } catch (e: Exception) {
            logger.warning("关闭时数据备份失败", e)
        }
    }

    /**
     * 备份 baseDataFolder 下的全部数据到目标目录
     */
    private fun copyYmlFilesTo(targetDir: File) {
        val baseDir = File(baseDataFolder)
        if (!baseDir.exists() || !baseDir.isDirectory) return
        if (!targetDir.exists()) targetDir.mkdirs()
        baseDir.listFiles { file ->
            file.isFile && file.extension.equals("yml", ignoreCase = true)
        }?.forEach { ymlFile ->
            ymlFile.copyTo(File(targetDir, ymlFile.name), overwrite = true)
        }
        snapshotDatabase(targetDir)
    }

    /**
     * 生成存储数据库快照
     */
    private fun snapshotDatabase(targetDir: File) {
        if (!Database.isInitialized) {
            logger.warning("存储数据库未初始化，本次备份不含存储数据")
            return
        }
        runCatching {
            Database.snapshotTo(File(targetDir, Database.FILE_NAME))
        }.onFailure {
            logger.error("存储数据库快照失败，本次备份不含存储数据", it)
        }
    }

    /**
     * 存储数据完整性检查
     * - 标记文件（不随数据库文件丢失）用于证明插件曾成功初始化过存储数据
     * - 数据库内的 `meta.initialized` 在数据库被重建为空库时会重新变回 false
     *
     * @return true 存储数据正常；false 检测到存储数据异常丢失
     */
    fun checkStorageIntegrity(): Boolean {
        val markerFile = File("$baseDataFolder/$STORAGE_MARKER_FILE")
        val markerExists = markerFile.exists()

        // 标记正常：数据库已初始化过
        if (Database.isDataInitialized()) {
            if (!markerExists) writeStorageMarkerFile(markerFile)   // 标记文件缺失则补建
            return true
        }

        // meta.initialized == false
        if (!markerExists) {
            // 首次启动：置位标记并创建标记文件
            Database.markDataInitialized()
            writeStorageMarkerFile(markerFile)
            logger.info { "首次启动：已创建存储数据完整性标记" }
            return true
        }

        // 标记文件存在但数据库未初始化 -> 存储数据异常丢失
        return false
    }

    private fun writeStorageMarkerFile(markerFile: File) {
        runCatching {
            markerFile.parentFile?.mkdirs()
            markerFile.writeText(
                "此文件用于检测存储数据库 ${Database.FILE_NAME} 是否异常丢失，请勿删除或修改。\n" +
                "如需手动清空存储数据，请连同此文件一起删除后再启动。\n" +
                "创建时间：${LocalDateTime.now()}\n"
            )
        }.onFailure { logger.warning("创建存储数据完整性标记文件失败", it) }
    }
}
