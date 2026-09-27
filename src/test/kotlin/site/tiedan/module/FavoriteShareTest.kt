package site.tiedan.module

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import site.tiedan.module.FavoriteShare.Item
import site.tiedan.module.FavoriteShare.TTL_MILLIS

/**
 * 指令集分享码：内容去重、过期后的最后一次导入、新建时清理
 */
class FavoriteShareTest {

    private val start = 1_000_000_000_000L
    private val items = listOf(Item("天气", "北京"), Item("签到", ""))

    @BeforeEach
    fun setUp() = FavoriteShare.clear()

    @AfterEach
    fun tearDown() = FavoriteShare.clear()

    @Test
    @DisplayName("分享码为 6 位且不含易混淆字符，查找不区分大小写")
    fun codeFormat() {
        val snapshot = FavoriteShare.share("1", "早安", items, start)
        assertTrue(Regex("[A-HJ-NP-Z2-9]{6}").matches(snapshot.code), snapshot.code)
        assertEquals(snapshot, FavoriteShare.find(snapshot.code.lowercase()))
    }

    @Test
    @DisplayName("内容相同时共用分享码，不看名称与分享人，并重新计时")
    fun sameContentSharesCode() {
        val first = FavoriteShare.share("1", "早安", items, start)
        val second = FavoriteShare.share("2", "晨间", items.toList(), start + 1_000)
        assertEquals(first.code, second.code)
        assertEquals("晨间", second.setName, "名称更新为这次分享的名称")
        assertEquals("晨间", FavoriteShare.find(first.code)?.setName, "导入时的默认名称跟着更新")
        assertEquals(start + 1_000 + TTL_MILLIS, second.expiresAt)

        val changed = FavoriteShare.share("1", "早安", items + Item("运势", ""), start)
        assertNotEquals(first.code, changed.code, "内容不同时生成新的分享码")
    }

    @Test
    @DisplayName("过期但尚未清理的分享仍可导入最后一次，导入后删除")
    fun lastImportAfterExpiry() {
        val code = FavoriteShare.share("1", "早安", items, start).code
        val expiry = start + TTL_MILLIS

        FavoriteShare.afterImport(code, expiry - 1)
        assertNotNull(FavoriteShare.find(code), "未过期时导入后仍然保留")

        assertNotNull(FavoriteShare.find(code), "过期后尚未清理时仍能找到")
        FavoriteShare.afterImport(code, expiry)
        assertNull(FavoriteShare.find(code), "过期后的这次导入完成即删除")
    }

    @Test
    @DisplayName("新建分享时清理全部过期分享，被清理的不能再导入")
    fun purgeExpiredOnShare() {
        val old = FavoriteShare.share("1", "早安", items, start).code
        FavoriteShare.share("2", "别的", listOf(Item("运势", "")), start + TTL_MILLIS)
        assertNull(FavoriteShare.find(old))
    }

    @Test
    @DisplayName("超出单人上限时清除最久未重新分享的那个，内容重复的分享不占名额")
    fun perUserLimitEvictsOldest() {
        val codes = (0 until FavoriteShare.MAX_ACTIVE_PER_USER).map { i ->
            FavoriteShare.share("1", "集$i", listOf(Item("p$i", "")), start + i).code
        }
        // 重新分享第一个，它不再是最久未分享的
        assertEquals(codes[0], FavoriteShare.share("1", "集0", listOf(Item("p0", "")), start + 100).code)

        FavoriteShare.share("1", "新增", listOf(Item("x", "")), start + 200)
        assertNull(FavoriteShare.find(codes[1]), "清除最久未重新分享的那个")
        assertNotNull(FavoriteShare.find(codes[0]))
        assertNotNull(FavoriteShare.find(codes[2]))

        FavoriteShare.share("2", "别人", listOf(Item("y", "")), start + 300)
        assertNotNull(FavoriteShare.find(codes[2]), "其他用户的分享不影响本人")
    }
}
