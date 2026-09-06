package site.tiedan.data.dao

import java.sql.Connection

/**
 * # 代码缓存数据访问
 * - 原始值中存在 BOM、CRLF 与裸控制字符，全链路一律原样存取，**不做任何 trim / normalize**。
 *
 * @author tiedanGH
 */
object CodeCacheDao {

    /** 获取项目的缓存代码，未缓存时返回 null */
    fun get(conn: Connection, project: String): String? =
        conn.prepareStatement("SELECT code FROM code_cache WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    /** 项目是否已缓存代码 */
    fun contains(conn: Connection, project: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM code_cache WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeQuery().use { rs -> rs.next() }
        }

    /** 写入项目的缓存代码 */
    fun put(conn: Connection, project: String, code: String) {
        conn.prepareStatement(
            """
            INSERT INTO code_cache(project, code, code_len) VALUES(?, ?, ?)
            ON CONFLICT(project) DO UPDATE SET code = excluded.code, code_len = excluded.code_len
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, project)
            ps.setString(2, code)
            ps.setInt(3, code.length)
            ps.executeUpdate()
        }
    }

    /** 删除项目的缓存代码 */
    fun remove(conn: Connection, project: String) {
        conn.prepareStatement("DELETE FROM code_cache WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeUpdate()
        }
    }

    /** 项目改名时迁移缓存代码 */
    fun rename(conn: Connection, from: String, to: String) {
        if (from == to || !contains(conn, from)) return
        remove(conn, to)
        conn.prepareStatement("UPDATE code_cache SET project = ? WHERE project = ?").use { ps ->
            ps.setString(1, to)
            ps.setString(2, from)
            ps.executeUpdate()
        }
    }

    /** 全部已缓存的项目名 */
    fun listProjects(conn: Connection): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT project FROM code_cache").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    /** 已缓存的项目数量 */
    fun count(conn: Connection): Int =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM code_cache").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    /** 全部缓存代码的字符总数 */
    fun totalLength(conn: Connection): Long =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COALESCE(SUM(code_len), 0) FROM code_cache").use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }
}
