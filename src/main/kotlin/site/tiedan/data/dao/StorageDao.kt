package site.tiedan.data.dao

import java.sql.Connection

/**
 * # 项目存储数据访问
 * `project_storage` 一张表同时承载 QQ与 其他平台的项目存储
 * - global 数据：`platform = ''`、`user_id = 0`，全平台共用
 * - 用户数据：`platform = qq/kook/...`、`user_id = 用户号`
 *
 * @author tiedanGH
 */
object StorageDao {

    /** global 行的平台标识（全平台共用） */
    const val GLOBAL_PLATFORM = ""

    /** global 行的用户号 */
    const val GLOBAL_USER_ID = 0L

    /** 一条存储记录 */
    data class Entry(val platform: String, val userId: Long, val content: String)

    /* ==================== 读取 ==================== */

    /** 读取 global 数据，不存在时返回 null */
    fun getGlobal(conn: Connection, project: String): String? =
        getUser(conn, project, GLOBAL_PLATFORM, GLOBAL_USER_ID)

    /** 读取指定用户的存储数据，不存在时返回 null */
    fun getUser(conn: Connection, project: String, platform: String, userId: Long): String? =
        conn.prepareStatement(
            "SELECT content FROM project_storage WHERE project = ? AND platform = ? AND user_id = ?"
        ).use { ps ->
            ps.setString(1, project)
            ps.setString(2, platform)
            ps.setLong(3, userId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    /** 项目是否存在任何存储数据 */
    fun projectExists(conn: Connection, project: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM project_storage WHERE project = ? LIMIT 1").use { ps ->
            ps.setString(1, project)
            ps.executeQuery().use { rs -> rs.next() }
        }

    /** 存在存储数据的项目数量 */
    fun projectCount(conn: Connection): Int =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(DISTINCT project) FROM project_storage").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }

    /** 全部存在存储数据的项目名（字典序） */
    fun listProjects(conn: Connection): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT DISTINCT project FROM project_storage ORDER BY project").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    /** 读取指定项目的全部存储数据 */
    fun listProject(conn: Connection, project: String): List<Entry> =
        conn.prepareStatement(
            "SELECT platform, user_id, content FROM project_storage WHERE project = ? ORDER BY platform, user_id"
        ).use { ps ->
            ps.setString(1, project)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Entry(rs.getString(1), rs.getLong(2), rs.getString(3))) }
            }
        }

    /**
     * 读取指定项目在指定平台的用户存储数据（不含 global）
     * @return key 为用户号
     */
    fun listPlatformUsers(conn: Connection, project: String, platform: String): Map<Long, String> =
        conn.prepareStatement(
            "SELECT user_id, content FROM project_storage WHERE project = ? AND platform = ? ORDER BY user_id"
        ).use { ps ->
            ps.setString(1, project)
            ps.setString(2, platform)
            ps.executeQuery().use { rs ->
                buildMap { while (rs.next()) put(rs.getLong(1), rs.getString(2)) }
            }
        }

    /* ==================== 写入 ==================== */

    /** 写入 global 数据 */
    fun setGlobal(conn: Connection, project: String, content: String) =
        setUser(conn, project, GLOBAL_PLATFORM, GLOBAL_USER_ID, content)

    /** 写入指定用户的存储数据 */
    fun setUser(conn: Connection, project: String, platform: String, userId: Long, content: String) {
        conn.prepareStatement(
            """
            INSERT INTO project_storage(project, platform, user_id, content, content_len) VALUES(?, ?, ?, ?, ?)
            ON CONFLICT(project, platform, user_id)
            DO UPDATE SET content = excluded.content, content_len = excluded.content_len
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, project)
            ps.setString(2, platform)
            ps.setLong(3, userId)
            ps.setString(4, content)
            ps.setInt(5, content.length)
            ps.executeUpdate()
        }
    }

    /** 删除指定用户的存储数据 */
    fun removeUser(conn: Connection, project: String, platform: String, userId: Long) {
        conn.prepareStatement(
            "DELETE FROM project_storage WHERE project = ? AND platform = ? AND user_id = ?"
        ).use { ps ->
            ps.setString(1, project)
            ps.setString(2, platform)
            ps.setLong(3, userId)
            ps.executeUpdate()
        }
    }

    /** 项目改名时迁移存储数据 */
    fun renameProject(conn: Connection, from: String, to: String) {
        if (from == to) return
        removeProject(conn, to)
        conn.prepareStatement("UPDATE project_storage SET project = ? WHERE project = ?").use { ps ->
            ps.setString(1, to)
            ps.setString(2, from)
            ps.executeUpdate()
        }
    }

    /** 删除指定项目的全部存储数据（全部平台） */
    fun removeProject(conn: Connection, project: String) {
        conn.prepareStatement("DELETE FROM project_storage WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeUpdate()
        }
    }

    /* ==================== 统计 ==================== */

    /** 指定项目的 global 数据长度，不存在返回 null */
    fun globalLength(conn: Connection, project: String): Int? =
        conn.prepareStatement(
            "SELECT content_len FROM project_storage WHERE project = ? AND platform = ? AND user_id = ?"
        ).use { ps ->
            ps.setString(1, project)
            ps.setString(2, GLOBAL_PLATFORM)
            ps.setLong(3, GLOBAL_USER_ID)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }

    /** 全部项目的 global 数据总长度 */
    fun totalGlobalLength(conn: Connection): Long =
        conn.prepareStatement(
            "SELECT COALESCE(SUM(content_len), 0) FROM project_storage WHERE platform = ? AND user_id = ?"
        ).use { ps ->
            ps.setString(1, GLOBAL_PLATFORM)
            ps.setLong(2, GLOBAL_USER_ID)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }

    /**
     * 用户存储数据总长度
     * @param platform 为 null 时统计全部平台
     */
    fun totalUserLength(conn: Connection, platform: String? = null): Long {
        val sql = StringBuilder("SELECT COALESCE(SUM(content_len), 0) FROM project_storage WHERE platform != ?")
        if (platform != null) sql.append(" AND platform = ?")
        return conn.prepareStatement(sql.toString()).use { ps ->
            ps.setString(1, GLOBAL_PLATFORM)
            if (platform != null) ps.setString(2, platform)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    }

    /**
     * 指定项目的用户存储数据总长度
     * @param platform 为 null 时统计全部平台
     */
    fun userLength(conn: Connection, project: String, platform: String? = null): Long {
        val sql = StringBuilder(
            "SELECT COALESCE(SUM(content_len), 0) FROM project_storage WHERE project = ? AND platform != ?"
        )
        if (platform != null) sql.append(" AND platform = ?")
        return conn.prepareStatement(sql.toString()).use { ps ->
            ps.setString(1, project)
            ps.setString(2, GLOBAL_PLATFORM)
            if (platform != null) ps.setString(3, platform)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    }

    /**
     * 指定项目的用户存储数量
     * @param platform 为 null 时统计全部平台
     */
    fun userCount(conn: Connection, project: String, platform: String? = null): Int {
        val sql = StringBuilder("SELECT COUNT(*) FROM project_storage WHERE project = ? AND platform != ?")
        if (platform != null) sql.append(" AND platform = ?")
        return conn.prepareStatement(sql.toString()).use { ps ->
            ps.setString(1, project)
            ps.setString(2, GLOBAL_PLATFORM)
            if (platform != null) ps.setString(3, platform)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }
}
