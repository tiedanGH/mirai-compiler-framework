package site.tiedan.module

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import site.tiedan.core.CodeCacheManager
import site.tiedan.data.Database
import site.tiedan.data.PastebinData
import java.util.UUID

/**
 * 数据自检：失效项目的判定
 */
class DataAuditTest {

    /** 项目数据是全局单例，每个用例使用独立的名称前缀 */
    private val prefix = "test_${UUID.randomUUID()}_"

    @BeforeEach
    fun setUp() {
        Database.close()
        Database.initializeInMemory()
    }

    @AfterEach
    fun tearDown() {
        PastebinData.pastebin.keys.filter { it.startsWith(prefix) }.forEach { PastebinData.pastebin.remove(it) }
        Database.close()
    }

    private fun project(name: String, url: String) {
        PastebinData.pastebin[prefix + name] = mutableMapOf("author" to "作者", "url" to url)
    }

    @Test
    @DisplayName("失效项目：链接指向已停服网站且没有代码缓存")
    fun invalidProjects() {
        project("ubuntu", "https://pastebin.ubuntu.com/p/abc/")
        project("cached", "https://pastebin.ubuntu.com/p/def/")
        project("live", "https://pastes.dev/xyz")
        project("bytebin", "https://bytebin.lucko.me/xyz")
        CodeCacheManager.put(prefix + "cached", "print(1)")

        val invalid = DataAudit.invalidProjects().filter { it.startsWith(prefix) }
        assertEquals(listOf(prefix + "ubuntu", prefix + "bytebin"), invalid)
    }
}
