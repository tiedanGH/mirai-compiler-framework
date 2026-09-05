package site.tiedan.core

import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.data.CodeCache

/**
 * # 代码缓存管理器
 *
 * @author tiedanGH
 */
object CodeCacheManager {

    /**
     * 获取项目的缓存代码
     * @return 未缓存时返回 null
     */
    fun get(name: String): String? = CodeCache.CodeCache[name]

    /**
     * 项目是否已缓存代码
     */
    fun contains(name: String): Boolean = CodeCache.CodeCache.contains(name)

    /**
     * 已缓存的项目数量
     */
    fun count(): Int = CodeCache.CodeCache.size

    /**
     * 全部缓存代码的字符总数
     */
    fun totalSize(): Long = CodeCache.CodeCache.values.sumOf { it.length.toLong() }

    /**
     * 写入项目的缓存代码
     */
    fun put(name: String, code: String) {
        CodeCache.CodeCache[name] = code
    }

    /**
     * 删除项目的缓存代码
     */
    fun remove(name: String) {
        CodeCache.CodeCache.remove(name)
    }

    /**
     * 项目改名时迁移缓存代码
     */
    fun rename(from: String, to: String) {
        CodeCache.CodeCache.remove(from)?.let { CodeCache.CodeCache[to] = it }
    }

    /**
     * 持久化缓存数据
     */
    fun save() {
        CodeCache.save()
    }
}
