package site.tiedan.data.dao

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

/**
 * 个人收藏：序号固定、收藏别名唯一、指令集增删，以及项目改名与删除联动
 */
class FavoriteDaoTest {

    private lateinit var conn: Connection

    @BeforeEach
    fun setUp() {
        conn = TestDb.open()
    }

    @AfterEach
    fun tearDown() {
        conn.close()
    }

    private fun slots(userID: String) = FavoriteDao.listFavorites(conn, userID).map { it.slot to it.project }

    /* ==================== 收藏 ==================== */

    @Test
    @DisplayName("收藏按序号排列，重复收藏同一项目不写入")
    fun favoritesOrderedBySlot() {
        assertTrue(FavoriteDao.addFavorite(conn, "1", 3, "c"))
        assertTrue(FavoriteDao.addFavorite(conn, "1", 1, "a"))
        assertTrue(FavoriteDao.addFavorite(conn, "1", 2, "b"))
        assertFalse(FavoriteDao.addFavorite(conn, "1", 4, "a"), "同一项目不能重复收藏")
        assertEquals(listOf(1 to "a", 2 to "b", 3 to "c"), slots("1"))
    }

    @Test
    @DisplayName("移除中间序号后其余序号不变，空位可以再次写入")
    fun removedSlotStaysEmpty() {
        FavoriteDao.addFavorite(conn, "1", 1, "a")
        FavoriteDao.addFavorite(conn, "1", 2, "b")
        FavoriteDao.addFavorite(conn, "1", 3, "c")

        assertTrue(FavoriteDao.removeFavorite(conn, "1", 2))
        assertEquals(listOf(1 to "a", 3 to "c"), slots("1"))

        assertFalse(FavoriteDao.addFavorite(conn, "1", 3, "d"), "已占用的序号不能写入")
        assertTrue(FavoriteDao.addFavorite(conn, "1", 2, "d"))
        assertEquals(listOf(1 to "a", 2 to "d", 3 to "c"), slots("1"))
    }

    @Test
    @DisplayName("不同用户的收藏互不影响")
    fun usersAreIsolated() {
        FavoriteDao.addFavorite(conn, "1", 1, "a")
        FavoriteDao.addFavorite(conn, "kook_1", 1, "a")
        FavoriteDao.addFavorite(conn, "kook_1", 2, "b")

        FavoriteDao.removeFavorite(conn, "1", 1)
        assertEquals(emptyList<Pair<Int, String>>(), slots("1"))
        assertEquals(listOf(1 to "a", 2 to "b"), slots("kook_1"))
    }

    @Test
    @DisplayName("收藏别名在同一用户内唯一，未设置别名互不冲突")
    fun aliasUniquePerUser() {
        FavoriteDao.addFavorite(conn, "1", 1, "a")
        FavoriteDao.addFavorite(conn, "1", 2, "b")
        FavoriteDao.addFavorite(conn, "1", 3, "c")
        FavoriteDao.addFavorite(conn, "2", 1, "a")

        assertTrue(FavoriteDao.setAlias(conn, "1", 1, "yy"))
        assertThrows(SQLException::class.java) { FavoriteDao.setAlias(conn, "1", 2, "yy") }
        assertTrue(FavoriteDao.setAlias(conn, "2", 1, "yy"), "不同用户可以使用相同的收藏别名")

        // 清除后可以再次使用
        assertTrue(FavoriteDao.setAlias(conn, "1", 1, null))
        assertTrue(FavoriteDao.setAlias(conn, "1", 2, "yy"))
        assertEquals(listOf(null, "yy", null), FavoriteDao.listFavorites(conn, "1").map { it.alias })
    }

    /* ==================== 指令集 ==================== */

    @Test
    @DisplayName("指令集按创建先后排列，同一项目可以重复出现")
    fun commandSetsKeepOrder() {
        FavoriteDao.addCommand(conn, "1", "早安", "天气", "北京")
        FavoriteDao.addCommand(conn, "1", "签到", "签到", "")
        FavoriteDao.addCommand(conn, "1", "早安", "天气", "上海")

        assertEquals(listOf("早安", "签到"), FavoriteDao.setNames(conn, "1"))
        val morning = FavoriteDao.listSet(conn, "1", "早安")
        assertEquals(listOf("天气" to "北京", "天气" to "上海"), morning.map { it.project to it.input })
        assertEquals(3, FavoriteDao.listCommands(conn, "1").size)
    }

    @Test
    @DisplayName("最后一条指令移除后指令集消失，删除别人的指令无效")
    fun removeCommandAndDeleteSet() {
        FavoriteDao.addCommand(conn, "1", "早安", "天气", "")
        FavoriteDao.addCommand(conn, "1", "签到", "签到", "")
        FavoriteDao.addCommand(conn, "1", "签到", "运势", "")
        val id = FavoriteDao.listSet(conn, "1", "早安").single().id

        assertFalse(FavoriteDao.removeCommand(conn, "2", id), "不能删除别人的指令")
        assertTrue(FavoriteDao.removeCommand(conn, "1", id))
        assertEquals(listOf("签到"), FavoriteDao.setNames(conn, "1"))

        assertEquals(0, FavoriteDao.deleteSet(conn, "1", "早安"))
        assertEquals(2, FavoriteDao.deleteSet(conn, "1", "签到"))
        assertEquals(emptyList<String>(), FavoriteDao.setNames(conn, "1"))
    }

    @Test
    @DisplayName("原位改写指令时 id 与指令集位置不变，改写别人的指令无效")
    fun updateCommandInPlace() {
        FavoriteDao.addCommand(conn, "1", "早安", "天气", "北京")
        FavoriteDao.addCommand(conn, "1", "签到", "签到", "")
        FavoriteDao.addCommand(conn, "1", "早安", "运势", "")
        val before = FavoriteDao.listSet(conn, "1", "早安")

        assertFalse(FavoriteDao.updateCommand(conn, "2", before[0].id, "x", ""), "不能改写别人的指令")
        assertTrue(FavoriteDao.updateCommand(conn, "1", before[0].id, "天气", "第一行\n第二行"))

        val after = FavoriteDao.listSet(conn, "1", "早安")
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(listOf("天气" to "第一行\n第二行", "运势" to ""), after.map { it.project to it.input })
        assertEquals(listOf("早安", "签到"), FavoriteDao.setNames(conn, "1"))
    }

    @Test
    @DisplayName("指令集改名迁移全部指令且顺序不变，不影响其他指令集与其他用户")
    fun renameSetKeepsOrder() {
        FavoriteDao.addCommand(conn, "1", "早安", "天气", "北京")
        FavoriteDao.addCommand(conn, "1", "签到", "签到", "")
        FavoriteDao.addCommand(conn, "1", "早安", "运势", "")
        FavoriteDao.addCommand(conn, "2", "早安", "天气", "上海")

        assertEquals(2, FavoriteDao.renameSet(conn, "1", "早安", "晨间"))
        assertEquals(listOf("晨间", "签到"), FavoriteDao.setNames(conn, "1"), "改名后指令集的先后位置不变")
        assertEquals(listOf("天气", "运势"), FavoriteDao.listSet(conn, "1", "晨间").map { it.project })
        assertEquals(listOf("早安"), FavoriteDao.setNames(conn, "2"))
        assertEquals(0, FavoriteDao.renameSet(conn, "1", "不存在", "x"))
    }

    @Test
    @DisplayName("按收藏别名查找只匹配自己的收藏")
    fun findByAlias() {
        FavoriteDao.addFavorite(conn, "1", 3, "今日运势")
        FavoriteDao.setAlias(conn, "1", 3, "ys")
        FavoriteDao.addFavorite(conn, "2", 1, "猜数字")

        assertEquals(FavoriteDao.Favorite(3, "今日运势", "ys"), FavoriteDao.findByAlias(conn, "1", "ys"))
        assertNull(FavoriteDao.findByAlias(conn, "2", "ys"), "别人的收藏别名不能匹配")
        assertNull(FavoriteDao.findByAlias(conn, "1", "今日运势"), "项目名称不是收藏别名")
    }

    @Test
    @DisplayName("保存的输入原样存取")
    fun inputRoundTrip() {
        for ((index, value) in TestDb.HOSTILE_VALUES.withIndex()) {
            FavoriteDao.addCommand(conn, "1", "set$index", "proj", value)
            assertEquals(value, FavoriteDao.listSet(conn, "1", "set$index").single().input, "输入被改写：$value")
        }
    }

    /* ==================== 项目联动 ==================== */

    @Test
    @DisplayName("改名迁移全部用户的收藏与指令，序号与别名保持不变")
    fun renameKeepsSlotAndAlias() {
        FavoriteDao.addFavorite(conn, "1", 5, "old")
        FavoriteDao.setAlias(conn, "1", 5, "o")
        FavoriteDao.addFavorite(conn, "2", 1, "old")
        FavoriteDao.addFavorite(conn, "2", 2, "other")
        FavoriteDao.addCommand(conn, "1", "早安", "old", "x")

        FavoriteDao.renameProject(conn, "old", "new")

        assertEquals(listOf(FavoriteDao.Favorite(5, "new", "o")), FavoriteDao.listFavorites(conn, "1"))
        assertEquals(listOf(1 to "new", 2 to "other"), slots("2"))
        assertEquals("new", FavoriteDao.listSet(conn, "1", "早安").single().project)
    }

    @Test
    @DisplayName("改名时清除目标名下的残留行，不会主键冲突")
    fun renameClearsOrphansUnderTarget() {
        // 已删除项目 new 留下的残留收藏与指令
        FavoriteDao.addFavorite(conn, "1", 1, "new")
        FavoriteDao.addFavorite(conn, "1", 2, "old")
        FavoriteDao.addCommand(conn, "3", "残留", "new", "")

        FavoriteDao.renameProject(conn, "old", "new")

        assertEquals(listOf(2 to "new"), slots("1"))
        assertEquals(emptyList<String>(), FavoriteDao.setNames(conn, "3"))
    }

    @Test
    @DisplayName("删除项目时收藏与指令一并清除，其他项目不受影响")
    fun removeProjectFromAll() {
        FavoriteDao.addFavorite(conn, "1", 1, "gone")
        FavoriteDao.addFavorite(conn, "1", 2, "keep")
        FavoriteDao.addCommand(conn, "1", "早安", "gone", "")
        FavoriteDao.addCommand(conn, "1", "早安", "keep", "")

        FavoriteDao.removeProjectFromAll(conn, "gone")

        assertEquals(listOf(2 to "keep"), slots("1"))
        assertEquals(listOf("keep"), FavoriteDao.listSet(conn, "1", "早安").map { it.project })
    }

    @Test
    @DisplayName("数据自检用的项目清单已去重")
    fun listedProjectsAreDistinct() {
        FavoriteDao.addFavorite(conn, "1", 1, "a")
        FavoriteDao.addFavorite(conn, "2", 1, "a")
        FavoriteDao.addCommand(conn, "1", "x", "b", "")
        FavoriteDao.addCommand(conn, "2", "y", "b", "")

        assertEquals(listOf("a"), FavoriteDao.listFavoritedProjects(conn))
        assertEquals(listOf("b"), FavoriteDao.listCommandProjects(conn))
    }
}
