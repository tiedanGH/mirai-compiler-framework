package site.tiedan.module

import site.tiedan.data.Database
import site.tiedan.data.PastebinData
import site.tiedan.data.dao.BucketDao
import site.tiedan.data.dao.CodeCacheDao
import site.tiedan.data.dao.FavoriteDao
import site.tiedan.data.dao.StatisticsDao
import site.tiedan.data.dao.StorageDao

/**
 * # 数据自检
 * - 逐项核对数据库中的存储、代码缓存、项目统计、存储库关联、收藏、指令集，是否都还对应着 `PastebinData` 里真实存在的项目，对不上的称为**孤儿数据**。
 * - 另外统计**失效项目**：链接指向已停服网站、且本地无代码缓存，已无法执行的项目。
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
    private const val LABEL_FAVORITE = "收藏"
    private const val LABEL_COMMAND_SET = "指令集"

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
                    OrphanGroup(LABEL_FAVORITE, FavoriteDao.listFavoritedProjects(conn).filterNot { it in known }),
                    OrphanGroup(LABEL_COMMAND_SET, FavoriteDao.listCommandProjects(conn).filterNot { it in known }),
                )
            )
        }
    }

    /**
     * 失效项目：链接指向已停服网站、且本地无代码缓存，已无法执行
     */
    fun invalidProjects(): List<String> {
        val cached by lazy { FavoriteManager.cachedProjects() }
        return PastebinData.pastebin.keys.filter { FavoriteManager.isDiscontinuedWithoutCache(it) { cached } }
    }

    /**
     * 渲染为 `#pb status` 中的自检段落
     * @param invalidCount 失效项目数量，自检中只显示数量
     */
    fun format(result: Result, invalidCount: Int = 0, maxNames: Int = 10): String = buildString {
        if (result.clean && invalidCount == 0) {
            append("🔍 数据自检：✅")
            return@buildString
        }
        val summary = listOfNotNull(
            "${result.total} 项孤儿数据".takeUnless { result.clean },
            "$invalidCount 个失效项目".takeIf { invalidCount > 0 },
        )
        append("🔍 数据自检：⚠️ ${summary.joinToString("，")}")
        for (group in result.groups.filter { it.projects.isNotEmpty() }) {
            val shown = group.projects.take(maxNames).joinToString("、")
            val more = if (group.projects.size > maxNames) "…等 ${group.projects.size} 项" else ""
            append("\n · ${group.label}：$shown$more")
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
                    LABEL_FAVORITE -> FavoriteDao.removeFavoritesOf(conn, project)
                    LABEL_COMMAND_SET -> FavoriteDao.removeCommandsOf(conn, project)
                }
                removed++
            }
        }
        removed
    }
}
