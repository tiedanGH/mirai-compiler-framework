package site.tiedan.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.tiedan.core.CodeCacheManager.Preservation.KEPT_DISCONTINUED
import site.tiedan.core.CodeCacheManager.Preservation.NONE
import site.tiedan.core.CodeCacheManager.Preservation.SAVED
import site.tiedan.data.Database
import site.tiedan.data.dao.CodeCacheDao
import java.io.File

class CodeCacheManagerTest {

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        Database.close()
        Database.initialize(File(tempDir, Database.FILE_NAME))
    }

    @AfterEach
    fun tearDown() {
        Database.close()
    }

    private fun setPrevious(code: String, url: String) {
        Database.transaction { CodeCacheDao.putPrevious(it, PROJECT, CodeCacheDao.Previous(code, url, 1L)) }
    }

    @Test
    @DisplayName("改链接并写入新代码时，旧缓存保留为上一版本")
    fun oldCacheIsPreservedOnReplace() {
        CodeCacheManager.put(PROJECT, "old")

        assertEquals(SAVED, CodeCacheManager.replaceOnUrlChange(PROJECT, LIVE_A, "new"))

        assertEquals("new", CodeCacheManager.get(PROJECT))
        assertEquals("old", CodeCacheManager.getPrevious(PROJECT)?.code)
        assertEquals(LIVE_A, CodeCacheManager.getPrevious(PROJECT)?.url)
    }

    @Test
    @DisplayName("新链接不支持缓存时，旧缓存清除但保留为上一版本")
    fun oldCacheIsPreservedOnClear() {
        CodeCacheManager.put(PROJECT, "old")

        assertEquals(SAVED, CodeCacheManager.replaceOnUrlChange(PROJECT, LIVE_A, null))

        assertNull(CodeCacheManager.get(PROJECT))
        assertEquals("old", CodeCacheManager.getPrevious(PROJECT)?.code)
    }

    @Test
    @DisplayName("原本没有缓存时不产生上一版本")
    fun nothingToPreserveWithoutCache() {
        assertEquals(NONE, CodeCacheManager.replaceOnUrlChange(PROJECT, LIVE_A, "new"))

        assertEquals("new", CodeCacheManager.get(PROJECT))
        assertNull(CodeCacheManager.getPrevious(PROJECT))
    }

    @Test
    @DisplayName("新旧代码相同时不改动上一版本")
    fun identicalCodeKeepsPrevious() {
        setPrevious("earlier", LIVE_A)
        CodeCacheManager.put(PROJECT, "same")

        assertEquals(NONE, CodeCacheManager.replaceOnUrlChange(PROJECT, LIVE_B, "same"))

        assertEquals("earlier", CodeCacheManager.getPrevious(PROJECT)?.code)
    }

    @Test
    @DisplayName("停服网站的上一版本不会被来源仍在的代码挤掉")
    fun discontinuedPreviousSurvivesLiveOrigin() {
        setPrevious("irreplaceable", DEAD_A)
        CodeCacheManager.put(PROJECT, "from-live")

        assertEquals(KEPT_DISCONTINUED, CodeCacheManager.replaceOnUrlChange(PROJECT, LIVE_B, "newest"))

        assertEquals("irreplaceable", CodeCacheManager.getPrevious(PROJECT)?.code)
        assertEquals("newest", CodeCacheManager.get(PROJECT))
    }

    @Test
    @DisplayName("两份都来自停服网站时，保留较新的一份")
    fun newerDiscontinuedReplacesOlderDiscontinued() {
        setPrevious("older-dead", DEAD_A)
        CodeCacheManager.put(PROJECT, "newer-dead")

        assertEquals(SAVED, CodeCacheManager.replaceOnUrlChange(PROJECT, DEAD_B, "newest"))

        assertEquals("newer-dead", CodeCacheManager.getPrevious(PROJECT)?.code)
    }

    @Test
    @DisplayName("来源仍在的上一版本照常被覆盖")
    fun livePreviousIsOverwritten() {
        setPrevious("live-old", LIVE_A)
        CodeCacheManager.put(PROJECT, "current")

        assertEquals(SAVED, CodeCacheManager.replaceOnUrlChange(PROJECT, LIVE_B, "newest"))

        assertEquals("current", CodeCacheManager.getPrevious(PROJECT)?.code)
    }

    @Test
    @DisplayName("改名与删除连同上一版本一起处理")
    fun renameAndRemoveCoverPrevious() {
        CodeCacheManager.put(PROJECT, "code")
        setPrevious("prev", LIVE_A)

        CodeCacheManager.rename(PROJECT, RENAMED)
        assertNull(CodeCacheManager.getPrevious(PROJECT))
        assertEquals("prev", CodeCacheManager.getPrevious(RENAMED)?.code)
        assertEquals("code", CodeCacheManager.get(RENAMED))

        CodeCacheManager.remove(RENAMED)
        assertNull(CodeCacheManager.get(RENAMED))
        assertNull(CodeCacheManager.getPrevious(RENAMED))
    }

    private companion object {
        const val PROJECT = "proj"
        const val RENAMED = "proj2"
        const val LIVE_A = "https://pastes.dev/aaaa"
        const val LIVE_B = "https://pastes.dev/bbbb"
        const val DEAD_A = "https://pastebin.ubuntu.com/p/aaaa/"
        const val DEAD_B = "https://bytebin.lucko.me/bbbb"
    }
}
