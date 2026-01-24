package site.tiedan.utils

import kotlin.math.abs

object FuzzySearch {

    fun fuzzyFind(
        map: MutableMap<String, MutableMap<String, String>>,
        query: String
    ): List<String> {

        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        return map.keys.filter { key ->
            editDistanceAtMostOne(q, key) || isOneTransposition(q, key)
        }
    }

    /**
     * 判断两个字符串的编辑距离是否 <= 1
     */
    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        val lenA = a.length
        val lenB = b.length

        // 长度差超过 1，直接不可能
        if (abs(lenA - lenB) > 1) return false

        var i = 0
        var j = 0
        var diff = 0

        while (i < lenA && j < lenB) {
            if (a[i] == b[j]) {
                i++
                j++
            } else {
                diff++
                if (diff > 1) return false

                when {
                    lenA > lenB -> i++       // 删除 a[i]
                    lenA < lenB -> j++       // 插入 b[j]
                    else -> {                // 替换
                        i++
                        j++
                    }
                }
            }
        }

        // 处理末尾多出来的那个字符
        if (i < lenA || j < lenB) diff++

        return diff <= 1
    }

    /**
     * 判断两个字符串是否通过一次字符交换可以相等
     */
    private fun isOneTransposition(a: String, b: String): Boolean {
        if (a.length != b.length) return false

        var first = -1
        var second = -1

        for (i in a.indices) {
            if (a[i] != b[i]) {
                if (first == -1) first = i
                else if (second == -1) second = i
                else return false
            }
        }

        return second != -1 &&
                a[first] == b[second] &&
                a[second] == b[first]
    }
}
