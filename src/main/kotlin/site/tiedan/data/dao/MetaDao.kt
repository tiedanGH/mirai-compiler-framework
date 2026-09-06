package site.tiedan.data.dao

import java.sql.Connection

/**
 * # 元数据表访问
 *
 * @author tiedanGH
 */
object MetaDao {

    /** 数据初始化标记，用于检测存储数据是否异常丢失 */
    const val KEY_INITIALIZED = "initialized"

    fun get(conn: Connection, key: String): String? =
        conn.prepareStatement("SELECT value FROM meta WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    fun set(conn: Connection, key: String, value: String) {
        conn.prepareStatement(
            "INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value"
        ).use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    fun isInitialized(conn: Connection): Boolean = get(conn, KEY_INITIALIZED) == "true"

    fun markInitialized(conn: Connection) = set(conn, KEY_INITIALIZED, "true")
}
