package site.tiedan.module

import site.tiedan.data.Database
import site.tiedan.data.PastebinData
import site.tiedan.data.dao.BucketDao
import site.tiedan.data.dao.CodeCacheDao
import site.tiedan.data.dao.StatisticsDao
import site.tiedan.data.dao.StorageDao

/**
 * # 数据自检
 * - 逐项核对数据库中的存储、代码缓存、项目统计、存储库关联，是否都还对应着 `PastebinData` 里真实存在的项目，对不上的称为**孤儿数据**。
 *
 * 只汇报，不自动清除：这里只负责给出清单，是否清理交由管理员判断。
 *
 * @author tiedanGH
 */
object DataAudit {

    private const val LABEL_STORAGE = "存储"
    private const val LABEL_CACHE = "代码缓存"
    private const val LABEL_STATISTICS = "项目统计"
    private const val LABEL_BUCKET_LINK = "存储库关联"

    /** 单项孤儿数据 */
    data class OrphanGroup(val label: String, val projects: List<String>)

    data class Result(val groups: List<OrphanGroup>) {
        val total: Int get() = groups.sumOf { it.projects.size }
        val clean: Boolean get() = total == 0
    }

    /**
     * 扫描全部孤儿数据
     */
    fun scan(): Result {
        val known = PastebinData.pastebin.keys
        return Database.read { conn ->
            Result(
                listOf(
                    OrphanGroup(LABEL_STORAGE, StorageDao.listProjects(conn).filterNot { it in known }),
                    OrphanGroup(LABEL_CACHE, CodeCacheDao.listProjects(conn).filterNot { it in known }),
                    OrphanGroup(LABEL_STATISTICS, StatisticsDao.listProjects(conn).filterNot { it in known }),
                    OrphanGroup(LABEL_BUCKET_LINK, BucketDao.listLinkedProjects(conn).filterNot { it in known }),
                )
            )
        }
    }

    /**
     * 渲染为 `#pb status` 中的自检段落
     */
    fun format(result: Result, maxNames: Int = 10): String = buildString {
        if (result.clean) {
            append("🔍 数据自检：✅")
            return@buildString
        }
        appendLine("🔍 数据自检：⚠️ ${result.total} 项孤儿数据")
        val groups = result.groups.filter { it.projects.isNotEmpty() }
        for ((index, group) in groups.withIndex()) {
            val shown = group.projects.take(maxNames).joinToString("、")
            val more = if (group.projects.size > maxNames) "…等 ${group.projects.size} 项" else ""
            val line = " · ${group.label}：$shown$more"
            if (index < groups.lastIndex) appendLine(line) else append(line)
        }
    }

    /**
     * 清除全部孤儿数据
     * - 这是**不可逆**操作，且判据依赖 yml，必须由管理员显式确认后调用。
     *
     * @return 实际删除的条目数量
     */
    fun clean(result: Result): Int = Database.transaction { conn ->
        var removed = 0
        for (group in result.groups) {
            for (project in group.projects) {
                when (group.label) {
                    LABEL_STORAGE -> StorageDao.removeProject(conn, project)
                    LABEL_CACHE -> CodeCacheDao.remove(conn, project)
                    LABEL_STATISTICS -> StatisticsDao.remove(conn, project)
                    LABEL_BUCKET_LINK -> BucketDao.removeProjectFromAll(conn, project)
                }
                removed++
            }
        }
        removed
    }
}
