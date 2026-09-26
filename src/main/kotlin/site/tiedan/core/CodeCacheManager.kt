package site.tiedan.core

import site.tiedan.data.Database
import site.tiedan.data.dao.CodeCacheDao
import site.tiedan.utils.PastebinUrlHelper
import java.sql.Connection

/**
 * # 代码缓存管理器
 * 数据存放在 SQLite（[Database]），每次写入即刻落盘。
 *
 * @author tiedanGH
 */
object CodeCacheManager {

    /**
     * 获取项目的缓存代码
     * @return 未缓存时返回 null
     */
    fun get(name: String): String? = Database.read { CodeCacheDao.get(it, name) }

    /**
     * 项目是否已缓存代码
     */
    fun contains(name: String): Boolean = Database.read { CodeCacheDao.contains(it, name) }

    /**
     * 已缓存的项目数量
     */
    fun count(): Int = Database.read { CodeCacheDao.count(it) }

    /**
     * 全部缓存代码的字符总数
     */
    fun totalSize(): Long = Database.read { CodeCacheDao.totalLength(it) }

    /**
     * 写入项目的缓存代码
     */
    fun put(name: String, code: String) {
        Database.transaction { CodeCacheDao.put(it, name, code) }
    }

    /**
     * 删除项目的缓存代码，连同上一版本一并删除
     */
    fun remove(name: String) {
        Database.transaction {
            CodeCacheDao.remove(it, name)
            CodeCacheDao.removePrevious(it, name)
        }
    }

    /**
     * 项目改名时迁移缓存代码，连同上一版本一并迁移
     */
    fun rename(from: String, to: String) {
        Database.transaction {
            CodeCacheDao.rename(it, from, to)
            CodeCacheDao.renamePrevious(it, from, to)
        }
    }

    /**
     * 获取项目的上一版本代码
     * @return 未保留时返回 null
     */
    fun getPrevious(name: String): CodeCacheDao.Previous? = Database.read { CodeCacheDao.getPrevious(it, name) }

    /** 修改链接时旧缓存的去向 */
    enum class Preservation {
        /** 旧缓存已保留为上一版本 */
        SAVED,
        /** 已保留的上一版本来自停服网站，为保护它，本次替换下的代码未存入 */
        KEPT_DISCONTINUED,
        /** 无需保留：原本没有缓存，或新旧代码相同 */
        NONE,
    }

    /**
     * 修改链接时替换缓存，替换前把旧缓存保留为上一版本
     * - 新旧代码完全相同时不改动上一版本，避免挤掉更早的有效版本
     *
     * @param oldUrl 旧缓存对应的源链接
     * @param newCode 新链接取到的代码；为 null 表示新链接不支持缓存，只清除旧缓存
     */
    fun replaceOnUrlChange(name: String, oldUrl: String, newCode: String?): Preservation =
        Database.transaction { conn ->
            val old = CodeCacheDao.get(conn, name)
            val result = when {
                old == null || old == newCode -> Preservation.NONE
                keepsExistingPrevious(conn, name, oldUrl) -> Preservation.KEPT_DISCONTINUED
                else -> {
                    CodeCacheDao.putPrevious(conn, name, CodeCacheDao.Previous(old, oldUrl, System.currentTimeMillis()))
                    Preservation.SAVED
                }
            }
            if (newCode != null) CodeCacheDao.put(conn, name, newCode) else CodeCacheDao.remove(conn, name)
            result
        }

    private fun keepsExistingPrevious(conn: Connection, name: String, oldUrl: String): Boolean {
        val existing = CodeCacheDao.getPrevious(conn, name) ?: return false
        return PastebinUrlHelper.isDiscontinued(existing.url) && !PastebinUrlHelper.isDiscontinued(oldUrl)
    }
}
