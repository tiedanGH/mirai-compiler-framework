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
                    OrphanGroup("存储", StorageDao.listProjects(conn).filterNot { it in known }),
                    OrphanGroup("缓存", CodeCacheDao.listProjects(conn).filterNot { it in known }),
                    OrphanGroup("统计", StatisticsDao.listProjects(conn).filterNot { it in known }),
                    OrphanGroup("存储库关联", BucketDao.listLinkedProjects(conn).filterNot { it in known }),
                )
            )
        }
    }

    /**
     * 渲染为 `#pb status` 中的自检段落
     */
    fun format(result: Result, maxNames: Int = 10): String = buildString {
        appendLine("🔍 数据自检")
        if (result.clean) {
            appendLine(" · 全部数据均对应现存项目，未发现孤儿数据")
            return@buildString
        }
        for (group in result.groups) {
            if (group.projects.isEmpty()) {
                appendLine(" · ${group.label}：✅")
                continue
            }
            val shown = group.projects.take(maxNames).joinToString("、")
            val more = if (group.projects.size > maxNames) "等 ${group.projects.size} 项" else ""
            appendLine(" · ${group.label}：⚠️ $shown$more")
        }
    }
}
