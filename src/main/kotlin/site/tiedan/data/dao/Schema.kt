package site.tiedan.data.dao

import java.sql.Connection

/**
 * # 数据库表结构
 * - 全部 DDL 均为幂等（`IF NOT EXISTS`），首次启动自动建库建表。
 * - 含大文本的表都额外保存一列 `content_len`，值等于 Kotlin `String.length`（UTF-16 码元数）。
 *
 * @author tiedanGH
 */
object Schema {

    /** 当前表结构版本，写入 `PRAGMA user_version` */
    const val VERSION = 1

    /** 每个存储库固定的备份槽位数量 */
    const val BACKUP_SLOTS = 3

    private val DDL = listOf(
        // 元数据：initialized 哨兵等
        """
        CREATE TABLE IF NOT EXISTS meta (
            key   TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """,

        // 项目存储：统一 QQ 与其他平台
        """
        CREATE TABLE IF NOT EXISTS project_storage (
            project     TEXT    NOT NULL,
            platform    TEXT    NOT NULL,
            user_id     INTEGER NOT NULL,
            content     TEXT    NOT NULL,
            content_len INTEGER NOT NULL,
            PRIMARY KEY (project, platform, user_id)
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_storage_project ON project_storage(project)",

        // bucket存储库
        """
        CREATE TABLE IF NOT EXISTS bucket (
            id          INTEGER PRIMARY KEY,
            name        TEXT    NOT NULL DEFAULT '',
            password    TEXT    NOT NULL DEFAULT '',
            owner       TEXT    NOT NULL DEFAULT '',
            user_id     TEXT    NOT NULL DEFAULT '',
            description TEXT    NOT NULL DEFAULT '',
            content     TEXT    NOT NULL DEFAULT '',
            content_len INTEGER NOT NULL DEFAULT 0,
            encrypt     INTEGER NOT NULL DEFAULT 0,
            empty       INTEGER NOT NULL DEFAULT 0
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_bucket_name ON bucket(name)",

        // 存储库↔项目关联：projects 空格分隔串规范化
        """
        CREATE TABLE IF NOT EXISTS bucket_project (
            bucket_id INTEGER NOT NULL,
            project   TEXT    NOT NULL,
            PRIMARY KEY (bucket_id, project)
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_bp_project ON bucket_project(project)",

        // 存储库备份：每库固定 3 槽，空槽表现为该行不存在
        """
        CREATE TABLE IF NOT EXISTS bucket_backup (
            bucket_id   INTEGER NOT NULL,
            slot        INTEGER NOT NULL,
            name        TEXT    NOT NULL,
            time        INTEGER NOT NULL,
            content     TEXT    NOT NULL,
            content_len INTEGER NOT NULL,
            PRIMARY KEY (bucket_id, slot)
        )
        """,

        // 代码缓存：项目名→代码文本
        """
        CREATE TABLE IF NOT EXISTS code_cache (
            project  TEXT PRIMARY KEY,
            code     TEXT NOT NULL,
            code_len INTEGER NOT NULL
        )
        """,

        // 项目数据统计：markdown/md_time/download/dl_time 必须可空
        """
        CREATE TABLE IF NOT EXISTS statistics (
            project  TEXT PRIMARY KEY,
            run      REAL NOT NULL DEFAULT 0,
            score    REAL NOT NULL DEFAULT 0,
            markdown REAL,
            md_time  REAL,
            download REAL,
            dl_time  REAL
        )
        """,

        // 全局累计统计：独立计数，项目被删除历史执行次数也不会变少
        """
        CREATE TABLE IF NOT EXISTS statistics_total (
            id       INTEGER PRIMARY KEY CHECK (id = 1),
            run      REAL NOT NULL DEFAULT 0,
            markdown REAL NOT NULL DEFAULT 0,
            md_time  REAL NOT NULL DEFAULT 0,
            download REAL NOT NULL DEFAULT 0,
            dl_time  REAL NOT NULL DEFAULT 0
        )
        """,
        "INSERT OR IGNORE INTO statistics_total(id) VALUES(1)",
    )

    /**
     * 建表并写入表结构版本号（幂等，可重复调用）
     */
    fun initialize(conn: Connection) {
        conn.createStatement().use { st ->
            for (sql in DDL) st.executeUpdate(sql.trimIndent())
            st.executeUpdate("PRAGMA user_version = $VERSION")
        }
    }

    /**
     * 读取表结构版本号，空库返回 0
     */
    fun readVersion(conn: Connection): Int =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA user_version").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }

    /**
     * 库中是否已存在表（用于判断数据异常丢失）
     */
    fun tablesExist(conn: Connection): Boolean =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='meta'").use { rs ->
                rs.next() && rs.getInt(1) > 0
            }
        }
}
