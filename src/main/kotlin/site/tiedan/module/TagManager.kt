package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.data.PastebinData

/**
 * # 项目标签
 *
 * @author tiedanGH
 */
object TagManager {

    /** 标签数据字段名 */
    const val FIELD = "tags"

    /** 标签的最大长度 */
    const val MAX_LENGTH = 6

    /** 清空项目标签的输入 */
    private val CLEAR_WORDS = setOf("clear", "none", "empty", "清空", "无")

    /** 解析用户输入的标签串 */
    fun parse(raw: String): List<String> =
        raw.split(" ", ",", "，", "、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun isClearWord(raw: String): Boolean = raw.trim().lowercase() in CLEAR_WORDS

    /** 获取标签库（字典序） */
    fun library(): List<String> = PastebinData.tagLibrary.sorted()

    /** 标签库数量 */
    fun libraryCount(): Int = PastebinData.tagLibrary.size

    /** 获取项目当前标签 */
    fun projectTags(name: String): List<String> =
        PastebinData.pastebin[name]?.get(FIELD)?.let { parse(it) } ?: emptyList()

    /**
     * 批量加入标签库
     * @return 新增的标签 to 已存在的标签
     */
    fun addToLibrary(tags: List<String>): Pair<List<String>, List<String>> {
        val existing = tags.filter { it in PastebinData.tagLibrary }
        val added = tags.filterNot { it in PastebinData.tagLibrary }
        if (added.isNotEmpty()) {
            PastebinData.tagLibrary.addAll(added)
            PastebinData.save()
        }
        return added to existing
    }

    /**
     * 批量移出标签库，并从全部项目上一并摘除
     * @return 已移除的标签、不存在的标签、受影响的项目数
     */
    fun removeFromLibrary(tags: List<String>): Triple<List<String>, List<String>, Int> {
        val removed = tags.filter { it in PastebinData.tagLibrary }
        val missing = tags.filterNot { it in PastebinData.tagLibrary }
        if (removed.isEmpty()) return Triple(removed, missing, 0)

        PastebinData.tagLibrary.removeAll(removed.toSet())
        var affected = 0
        for ((_, data) in PastebinData.pastebin) {
            val current = data[FIELD]?.let { parse(it) } ?: continue
            val kept = current.filterNot { tag -> removed.any { it.equals(tag, ignoreCase = true) } }
            if (kept.size == current.size) continue
            affected++
            if (kept.isEmpty()) data.remove(FIELD) else data[FIELD] = kept.joinToString(" ")
        }
        PastebinData.save()
        return Triple(removed, missing, affected)
    }

    /**
     * 设置项目标签
     * @return 不在标签库中的标签；返回空列表表示设置成功
     */
    fun setProjectTags(name: String, tags: List<String>): List<String> {
        val unknown = tags.filterNot { tag -> PastebinData.tagLibrary.any { it.equals(tag, ignoreCase = true) } }
        if (unknown.isNotEmpty()) return unknown
        val normalized = tags.map { tag -> PastebinData.tagLibrary.first { it.equals(tag, ignoreCase = true) } }
        PastebinData.pastebin[name]?.set(FIELD, normalized.joinToString(" "))
        PastebinData.save()
        return emptyList()
    }

    /** 清空项目标签 */
    fun clearProjectTags(name: String) {
        PastebinData.pastebin[name]?.remove(FIELD)
        PastebinData.save()
    }

    /** 标签名是否合法 */
    fun invalidTags(tags: List<String>): List<String> = tags.filter { it.length > MAX_LENGTH }

    /** 每个标签被多少个项目使用 */
    fun usageByTag(): Map<String, Int> {
        val counts = PastebinData.tagLibrary.associateWith { 0 }.toMutableMap()
        for ((_, data) in PastebinData.pastebin) {
            for (tag in data[FIELD]?.let { parse(it) } ?: continue) {
                counts.computeIfPresent(tag) { _, n -> n + 1 }
            }
        }
        return counts
    }

    /** 全部项目使用标签的总次数（同一标签被多个项目使用会重复计入） */
    fun totalUsage(): Int =
        PastebinData.pastebin.values.sumOf { data -> data[FIELD]?.let { parse(it).size } ?: 0 }

    /**
     * 标签列表输出：按使用项目数降序，附项目数
     */
    fun formatTagLines(): String =
        usageByTag().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .joinToString(" ") { "${it.key}(${it.value})" }

    /**
     * 标签库中模糊搜索关键词，无匹配时返回空字符串
     */
    fun fuzzyMatchLine(keywords: List<String>): String {
        val matched = keywords
            .flatMap { keyword -> PastebinData.tagLibrary.filter { it.contains(keyword, ignoreCase = true) } }
            .distinct()
        return if (matched.isEmpty()) "" else "\n🔍 模糊匹配结果：${matched.joinToString(" ")}"
    }

    /** 项目是否带有指定标签 */
    fun hasTag(rawTags: String?, keyword: String): Boolean =
        rawTags?.let { parse(it).any { tag -> tag.equals(keyword, ignoreCase = true) } } == true
}
