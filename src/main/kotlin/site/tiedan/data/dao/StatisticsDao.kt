package site.tiedan.data.dao

import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.ResultSet

/**
 * # 项目统计数据访问
 * - `markdown` / `md_time` / `download` / `dl_time` 四列可空，保留未调用状态。
 *
 * @author tiedanGH
 */
object StatisticsDao {

    /** 统计一行数据 */
    data class StatisticsRow(
        val project: String,
        val run: Double,
        val score: Double,
        val markdown: Double?,
        val mdTime: Double?,
        val download: Double?,
        val dlTime: Double?,
    )

    /** 全部项目的统计汇总 */
    data class Totals(
        val run: Long,
        val markdown: Long,
        val mdTime: Double,
        val download: Long,
        val dlTime: Double,
    )

    private fun Double.roundTo2(): Double = BigDecimal(this).setScale(2, RoundingMode.HALF_UP).toDouble()

    private fun ResultSet.getDoubleOrNull(column: String): Double? {
        val value = getDouble(column)
        return if (wasNull()) null else value
    }

    /* ==================== 读取 ==================== */

    /** 获取项目的统计数据，无记录时返回 null */
    fun get(conn: Connection, project: String): StatisticsRow? {
        return conn.prepareStatement("SELECT * FROM statistics WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    StatisticsRow(
                        project = rs.getString("project"),
                        run = rs.getDouble("run"),
                        score = rs.getDouble("score"),
                        markdown = rs.getDoubleOrNull("markdown"),
                        mdTime = rs.getDoubleOrNull("md_time"),
                        download = rs.getDoubleOrNull("download"),
                        dlTime = rs.getDoubleOrNull("dl_time"),
                    )
                } else {
                    null
                }
            }
        }
    }

    /** 获取项目的热度指数，无记录时返回 0 */
    fun getScore(conn: Connection, project: String): Double = readColumn(conn, project, "score")

    /** 获取项目的运行次数，无记录时返回 0 */
    fun getRun(conn: Connection, project: String): Double = readColumn(conn, project, "run")

    // score / run 两列由代码固定传入，不接受外部输入
    private fun readColumn(conn: Connection, project: String, column: String): Double =
        conn.prepareStatement("SELECT $column FROM statistics WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getDouble(1) else 0.0 }
        }

    /** 一次性读取全部项目的热度指数，用于排序与渲染 */
    fun allScores(conn: Connection): Map<String, Double> = allOf(conn, "score")

    /** 一次性读取全部项目的运行次数，用于排序 */
    fun allRuns(conn: Connection): Map<String, Double> = allOf(conn, "run")

    // 列名由代码固定传入，不接受外部输入
    private fun allOf(conn: Connection, column: String): Map<String, Double> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT project, $column FROM statistics").use { rs ->
                buildMap { while (rs.next()) put(rs.getString(1), rs.getDouble(2)) }
            }
        }

    /**
     * 全局累计统计
     * - 独立计数，项目被删除历史执行次数也不会变少
     */
    fun totals(conn: Connection): Totals =
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT run, markdown, md_time, download, dl_time FROM statistics_total WHERE id = 1"
            ).use { rs ->
                if (rs.next()) {
                    Totals(
                        run = rs.getDouble(1).toLong(),
                        markdown = rs.getDouble(2).toLong(),
                        mdTime = rs.getDouble(3),
                        download = rs.getDouble(4).toLong(),
                        dlTime = rs.getDouble(5),
                    )
                } else {
                    Totals(0L, 0L, 0.0, 0L, 0.0)
                }
            }
        }

    /* ==================== 计数 ==================== */

    /** 统计运行次数与热度（+1） */
    fun countRun(conn: Connection, project: String) {
        val current = get(conn, project)
        upsert(
            conn, project,
            run = (current?.run ?: 0.0) + 1,
            score = ((current?.score ?: 0.0) + 1).roundTo2(),
        )
        addToTotals(conn, run = 1.0)
    }

    /** 统计调用 markdown 的次数与累计用时 */
    fun countMarkdown(conn: Connection, project: String, mdTime: Double) {
        val current = get(conn, project)
        conn.prepareStatement(
            """
            INSERT INTO statistics(project, run, score, markdown, md_time) VALUES(?, ?, ?, ?, ?)
            ON CONFLICT(project) DO UPDATE SET markdown = excluded.markdown, md_time = excluded.md_time
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, project)
            ps.setDouble(2, current?.run ?: 0.0)
            ps.setDouble(3, current?.score ?: 0.0)
            ps.setDouble(4, (current?.markdown ?: 0.0) + 1)
            ps.setDouble(5, ((current?.mdTime ?: 0.0) + mdTime).roundTo2())
            ps.executeUpdate()
        }
        addToTotals(conn, markdown = 1.0, mdTime = mdTime)
    }

    /** 统计 image 下载的次数与累计用时 */
    fun countDownload(conn: Connection, project: String, dlTime: Double) {
        val current = get(conn, project)
        conn.prepareStatement(
            """
            INSERT INTO statistics(project, run, score, download, dl_time) VALUES(?, ?, ?, ?, ?)
            ON CONFLICT(project) DO UPDATE SET download = excluded.download, dl_time = excluded.dl_time
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, project)
            ps.setDouble(2, current?.run ?: 0.0)
            ps.setDouble(3, current?.score ?: 0.0)
            ps.setDouble(4, (current?.download ?: 0.0) + 1)
            ps.setDouble(5, ((current?.dlTime ?: 0.0) + dlTime).roundTo2())
            ps.executeUpdate()
        }
        addToTotals(conn, download = 1.0, dlTime = dlTime)
    }

    /**
     * 累加全局统计
     * - 只增不减：项目被删掉之后历史执行次数依然留在总数里。
     */
    private fun addToTotals(
        conn: Connection,
        run: Double = 0.0,
        markdown: Double = 0.0,
        mdTime: Double = 0.0,
        download: Double = 0.0,
        dlTime: Double = 0.0,
    ) {
        val current = conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT run, markdown, md_time, download, dl_time FROM statistics_total WHERE id = 1"
            ).use { rs ->
                if (rs.next()) {
                    doubleArrayOf(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5))
                } else {
                    doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0)
                }
            }
        }
        conn.prepareStatement(
            """
            INSERT INTO statistics_total(id, run, markdown, md_time, download, dl_time) VALUES(1, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                run = excluded.run, markdown = excluded.markdown, md_time = excluded.md_time,
                download = excluded.download, dl_time = excluded.dl_time
            """.trimIndent()
        ).use { ps ->
            ps.setDouble(1, current[0] + run)
            ps.setDouble(2, current[1] + markdown)
            ps.setDouble(3, (current[2] + mdTime).roundTo2())
            ps.setDouble(4, current[3] + download)
            ps.setDouble(5, (current[4] + dlTime).roundTo2())
            ps.executeUpdate()
        }
    }

    private fun upsert(conn: Connection, project: String, run: Double, score: Double) {
        conn.prepareStatement(
            """
            INSERT INTO statistics(project, run, score) VALUES(?, ?, ?)
            ON CONFLICT(project) DO UPDATE SET run = excluded.run, score = excluded.score
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, project)
            ps.setDouble(2, run)
            ps.setDouble(3, score)
            ps.executeUpdate()
        }
    }

    /* ==================== 更新 ==================== */

    /** 项目改名时迁移统计数据 */
    fun rename(conn: Connection, from: String, to: String) {
        if (from == to || get(conn, from) == null) return
        remove(conn, to)
        conn.prepareStatement("UPDATE statistics SET project = ? WHERE project = ?").use { ps ->
            ps.setString(1, to)
            ps.setString(2, from)
            ps.executeUpdate()
        }
    }

    /** 删除项目的统计数据，全局统计不受影响 */
    fun remove(conn: Connection, project: String) {
        conn.prepareStatement("DELETE FROM statistics WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeUpdate()
        }
    }

    /** 按比例衰减全部项目的热度指数 */
    fun decayAllScores(conn: Connection, factor: Double) {
        val scores = allScores(conn)
        if (scores.isEmpty()) return
        conn.prepareStatement("UPDATE statistics SET score = ? WHERE project = ?").use { ps ->
            for ((project, score) in scores) {
                ps.setDouble(1, (score * factor).roundTo2())
                ps.setString(2, project)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    /** 有统计记录的项目数量 */
    fun count(conn: Connection): Int =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM statistics").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
}
