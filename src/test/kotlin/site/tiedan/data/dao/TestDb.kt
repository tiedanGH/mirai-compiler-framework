package site.tiedan.data.dao

import org.sqlite.SQLiteDataSource
import java.sql.Connection

/**
 * DAO 测试公用工具：每个测试独占一个内存库，互不干扰
 */
internal object TestDb {

    fun open(): Connection {
        val dataSource = SQLiteDataSource()
        dataSource.url = "jdbc:sqlite::memory:"
        val conn = dataSource.connection
        conn.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        Schema.initialize(conn)
        return conn
    }

    /** SQLite 侧的 `length()`，返回 Unicode 码点数（刻意与 Kotlin 的 String.length 区分） */
    fun sqlLength(conn: Connection, sql: String): Int =
        conn.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> if (rs.next()) rs.getInt(1) else -1 }
        }

    /**
     * 线上数据里真实出现过的各种「恶意」字符串，全链路必须原样存取
     */
    val HOSTILE_VALUES = listOf(
        "",
        " ",
        "null",
        "NULL",
        "~",
        "\\null",
        "\\\\null",
        "\uFEFFprint('BOM leading')",
        "line1\r\nline2\r\n",
        "ctrl\u0001and\u0005here",
        "emoji: 😀🎉",
        "混合中文 with 'quotes' and \"double\" and ; -- DROP TABLE bucket;",
        "tab\tand ?",
    )
}
