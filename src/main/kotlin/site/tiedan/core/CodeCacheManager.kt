package site.tiedan.core

import site.tiedan.data.Database
import site.tiedan.data.dao.CodeCacheDao

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
     * 删除项目的缓存代码
     */
    fun remove(name: String) {
        Database.transaction { CodeCacheDao.remove(it, name) }
    }

    /**
     * 项目改名时迁移缓存代码
     */
    fun rename(from: String, to: String) {
        Database.transaction { CodeCacheDao.rename(it, from, to) }
    }
}
