package site.tiedan.data.dao

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.sql.Connection

class StatisticsDaoTest {

    private lateinit var conn: Connection

    @BeforeEach
    fun setUp() {
        conn = TestDb.open()
    }

    @AfterEach
    fun tearDown() {
        conn.close()
    }

    private fun requireStat(project: String): StatisticsDao.StatisticsRow =
        StatisticsDao.get(conn, project) ?: throw AssertionError("项目 $project 应当有统计记录")

    @Test
    @DisplayName("只跑过文本输出的项目，markdown 与 download 必须保持为 null")
    fun neverCalledColumnsStayNull() {
        StatisticsDao.countRun(conn, "proj")
        val stat = requireStat("proj")

        assertEquals(1.0, stat.run)
        assertEquals(1.0, stat.score)
        assertNull(stat.markdown, "从未调用过 markdown，必须是 null 而不是 0")
        assertNull(stat.mdTime)
        assertNull(stat.download, "从未调用过 image 下载，必须是 null 而不是 0")
        assertNull(stat.dlTime)
    }

    @Test
    @DisplayName("运行计数与热度累加")
    fun countRunAccumulates() {
        repeat(3) { StatisticsDao.countRun(conn, "proj") }

        assertEquals(3.0, StatisticsDao.getRun(conn, "proj"))
        assertEquals(3.0, StatisticsDao.getScore(conn, "proj"))
    }

    @Test
    @DisplayName("markdown 计数从 null 起步，且不影响 run / score")
    fun countMarkdownStartsFromNull() {
        StatisticsDao.countRun(conn, "proj")
        StatisticsDao.countMarkdown(conn, "proj", 1.234)
        StatisticsDao.countMarkdown(conn, "proj", 2.0)

        val stat = requireStat("proj")
        assertEquals(2.0, stat.markdown)
        assertEquals(3.23, stat.mdTime, "累计用时按 roundTo2 取整")
        assertEquals(1.0, stat.run, "markdown 计数不得改动 run")
        assertEquals(1.0, stat.score, "markdown 计数不得改动 score")
        assertNull(stat.download)
    }

    @Test
    @DisplayName("首次调用 markdown 的项目此前可以没有任何统计记录")
    fun countMarkdownCreatesRow() {
        StatisticsDao.countMarkdown(conn, "fresh", 0.5)

        val stat = requireStat("fresh")
        assertEquals(0.0, stat.run)
        assertEquals(0.0, stat.score)
        assertEquals(1.0, stat.markdown)
        assertEquals(0.5, stat.mdTime)
    }

    @Test
    @DisplayName("下载计数与 markdown 计数互不干扰")
    fun countDownloadIsIndependent() {
        StatisticsDao.countMarkdown(conn, "proj", 1.0)
        StatisticsDao.countDownload(conn, "proj", 2.5)
        StatisticsDao.countDownload(conn, "proj", 2.5)

        val stat = requireStat("proj")
        assertEquals(1.0, stat.markdown)
        assertEquals(1.0, stat.mdTime)
        assertEquals(2.0, stat.download)
        assertEquals(5.0, stat.dlTime)
    }

    @Test
    @DisplayName("热度衰减按 BigDecimal HALF_UP 取整")
    fun decayRoundsHalfUp() {
        StatisticsDao.countRun(conn, "a")
        StatisticsDao.countRun(conn, "a")
        StatisticsDao.countRun(conn, "a")
        StatisticsDao.countRun(conn, "b")

        StatisticsDao.decayAllScores(conn, 0.875)

        assertEquals(2.63, StatisticsDao.getScore(conn, "a"))    // 3 * 0.875 = 2.625 -> 2.63
        assertEquals(0.88, StatisticsDao.getScore(conn, "b"))    // 1 * 0.875 = 0.875 -> 0.88
        assertEquals(3.0, StatisticsDao.getRun(conn, "a"), "衰减不得改动运行次数")
    }

    @Test
    @DisplayName("空库上的衰减不报错")
    fun decayOnEmptyDatabase() {
        StatisticsDao.decayAllScores(conn, 0.5)
        assertEquals(0, StatisticsDao.count(conn))
    }

    @Test
    @DisplayName("全局累计统计跟随每次计数增长")
    fun totalsAccumulate() {
        assertEquals(StatisticsDao.Totals(0L, 0L, 0.0, 0L, 0.0), StatisticsDao.totals(conn))

        StatisticsDao.countRun(conn, "a")
        StatisticsDao.countRun(conn, "a")
        StatisticsDao.countRun(conn, "b")           // b 从未调用 markdown / download
        StatisticsDao.countMarkdown(conn, "a", 1.5)
        StatisticsDao.countDownload(conn, "a", 2.25)

        val totals = StatisticsDao.totals(conn)
        assertEquals(3L, totals.run)
        assertEquals(1L, totals.markdown)
        assertEquals(1.5, totals.mdTime)
        assertEquals(1L, totals.download)
        assertEquals(2.25, totals.dlTime)
    }

    @Test
    @DisplayName("删除项目后全局累计统计不会变少")
    fun totalsSurviveProjectDeletion() {
        repeat(5) { StatisticsDao.countRun(conn, "会被删掉的项目") }
        StatisticsDao.countMarkdown(conn, "会被删掉的项目", 3.0)
        StatisticsDao.countDownload(conn, "会被删掉的项目", 1.25)
        StatisticsDao.countRun(conn, "留下的项目")

        val before = StatisticsDao.totals(conn)
        StatisticsDao.remove(conn, "会被删掉的项目")

        assertEquals(before, StatisticsDao.totals(conn), "删除项目不得让历史总数变少")
        assertEquals(6L, StatisticsDao.totals(conn).run)
        assertEquals(1L, StatisticsDao.totals(conn).markdown)
        assertEquals(3.0, StatisticsDao.totals(conn).mdTime)
        assertEquals(1L, StatisticsDao.totals(conn).download)

        assertEquals(1, sumOfProjectRuns())
    }

    /** 对 statistics 表求和，仅测试内用于反证「汇总实现」会丢数据 */
    private fun sumOfProjectRuns(): Int =
        TestDb.sqlLength(conn, "SELECT CAST(COALESCE(SUM(run), 0) AS INTEGER) FROM statistics")

    @Test
    @DisplayName("删除统计只影响目标项目")
    fun removeOnlyTargetProject() {
        StatisticsDao.countRun(conn, "a")
        StatisticsDao.countRun(conn, "b")

        StatisticsDao.remove(conn, "a")

        assertNull(StatisticsDao.get(conn, "a"))
        assertEquals(1.0, StatisticsDao.getRun(conn, "b"))
    }

    @Test
    @DisplayName("无统计记录的项目读数为 0")
    fun missingProjectReadsAsZero() {
        assertNull(StatisticsDao.get(conn, "missing"))
        assertEquals(0.0, StatisticsDao.getRun(conn, "missing"))
        assertEquals(0.0, StatisticsDao.getScore(conn, "missing"))
        assertTrue(StatisticsDao.allScores(conn).isEmpty())
    }

    @Test
    @DisplayName("数字形式的项目名按字符串处理")
    fun numericProjectNamesAreText() {
        for (name in listOf("1", "2", "2048", "01", "1.0")) {
            StatisticsDao.countRun(conn, name)
        }
        assertEquals(5, StatisticsDao.count(conn))
        assertEquals(1.0, StatisticsDao.getRun(conn, "01"))
        assertEquals(setOf("1", "2", "2048", "01", "1.0"), StatisticsDao.allScores(conn).keys)
    }
}
