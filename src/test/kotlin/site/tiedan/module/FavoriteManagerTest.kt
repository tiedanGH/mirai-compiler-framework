package site.tiedan.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import site.tiedan.data.dao.FavoriteDao.Favorite

/**
 * 个人收藏：名称校验、收藏查找、空位序号
 */
class FavoriteManagerTest {

    @Test
    @DisplayName("纯数字（含全角数字）一律按序号解析")
    fun indexToken() {
        assertTrue(FavoriteManager.isIndexToken("12"))
        assertTrue(FavoriteManager.isIndexToken("１２"))
        assertFalse(FavoriteManager.isIndexToken("a1"))
        assertFalse(FavoriteManager.isIndexToken("-1"))
        assertFalse(FavoriteManager.isIndexToken(""))
    }

    @Test
    @DisplayName("收藏别名：格式、项目原名、自己的其他别名与指令集名称均不能冲突")
    fun aliasRules() {
        val projects = setOf("今日运势", "猜数字")
        val others = setOf("cs")
        val sets = listOf("早安")

        assertNull(FavoriteManager.checkAlias("ys", projects, others, sets))
        assertNull(FavoriteManager.checkAlias("无", projects, others, sets), "「无」按普通别名处理")
        assertNotNull(FavoriteManager.checkAlias("12345678901", projects, others, sets), "超过 10 个字")
        assertNotNull(FavoriteManager.checkAlias("12", projects, others, sets), "纯数字")
        assertNotNull(FavoriteManager.checkAlias("猜数字", projects, others, sets), "与项目原名重复")
        assertNotNull(FavoriteManager.checkAlias("cs", projects, others, sets), "与自己的其他收藏别名重复")
        assertNotNull(FavoriteManager.checkAlias("早安", projects, others, sets), "与自己的指令集名称重复")
    }

    @Test
    @DisplayName("新建指令集：名称格式与自己的收藏别名均不能冲突")
    fun setNameRules() {
        assertNull(FavoriteManager.checkSetName("早安", setOf("ys")))
        assertNotNull(FavoriteManager.checkSetName("ys", setOf("ys")))
        assertNotNull(FavoriteManager.checkSetName("007", emptySet()))
        assertNotNull(FavoriteManager.checkSetName("一二三四五六七八九十一", emptySet()))
    }

    @Test
    @DisplayName("按序号、收藏别名、项目名称、项目别名查找收藏")
    fun findFavorite() {
        val favorites = listOf(Favorite(1, "今日运势", "ys"), Favorite(3, "猜数字", null), Favorite(4, "已删除项目", null))
        val projectAlias = mapOf("csz" to "猜数字")

        fun find(token: String) = FavoriteManager.findFavorite(favorites, token) { projectAlias[it] }?.slot

        assertEquals(3, find("3"))
        assertNull(find("2"), "空位序号没有对应的收藏")
        assertEquals(1, find("ys"))
        assertEquals(3, find("猜数字"))
        assertEquals(3, find("csz"))
        assertEquals(4, find("已删除项目"), "项目已删除时仍可按原名找到")
        assertNull(find("不存在"))
    }

    @Test
    @DisplayName("新收藏填入最小的空位，收藏已满时没有空位")
    fun firstFreeSlot() {
        assertEquals(1, FavoriteManager.firstFreeSlot(emptySet()))
        assertEquals(2, FavoriteManager.firstFreeSlot(setOf(1, 3, 4)))
        assertEquals(4, FavoriteManager.firstFreeSlot(setOf(1, 2, 3)))
        assertNull(FavoriteManager.firstFreeSlot((1..FavoriteManager.MAX_FAVORITES).toSet()))
    }

    @Test
    @DisplayName("重复项目合并计数，保持首次出现的顺序")
    fun mergeProjects() {
        assertEquals(
            listOf("天气" to 2, "签到" to 1, "运势" to 1),
            FavoriteManager.mergeProjects(listOf("天气", "签到", "天气", "运势"))
        )
        assertEquals("echo×5", FavoriteManager.mergedProjectText(List(5) { "echo" }))
        assertEquals("天气×2、签到", FavoriteManager.mergedProjectText(listOf("天气", "签到", "天气")))
    }
}
