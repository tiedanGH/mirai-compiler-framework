package site.tiedan.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sqlite.SQLiteDataSource
import site.tiedan.data.dao.MetaDao
import site.tiedan.data.dao.Schema
import java.io.File
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * # SQLite 数据库连接管理
 * - 承载项目存储、存储库、代码缓存与项目统计数据
 *
 * @author tiedanGH
 */
object Database {

    const val FILE_NAME = "storage.db"

    private val lock = ReentrantLock()

    @Volatile
    private var connection: Connection? = null

    /** 当前线程是否已处在显式事务中，避免嵌套事务提前提交 */
    private var inTransaction = false

    /** 数据库文件，未初始化时为 null */
    @Volatile
    var file: File? = null
        private set

    val isInitialized: Boolean get() = connection != null

    /**
     * 初始哈数据库并建表
     * @return 底层 SQLite 版本号
     */
    fun initialize(dbFile: File): String = lock.withLock {
        check(connection == null) { "数据库已初始化：${file?.absolutePath}" }
        dbFile.parentFile?.mkdirs()
        val conn = open("jdbc:sqlite:${dbFile.absolutePath}")
        connection = conn
        file = dbFile
        Schema.initialize(conn)
        sqliteVersion(conn)
    }

    /**
     * 打开一个内存库并建表（仅测试使用）
     */
    fun initializeInMemory(): String = lock.withLock {
        check(connection == null) { "数据库已初始化" }
        val conn = open("jdbc:sqlite::memory:")
        connection = conn
        file = null
        Schema.initialize(conn)
        sqliteVersion(conn)
    }

    private fun open(url: String): Connection {
        val dataSource = SQLiteDataSource()
        dataSource.url = url
        val conn = dataSource.connection
        conn.autoCommit = true
        conn.createStatement().use { st ->
            // journal_mode 会返回一行结果，用 execute 兼容
            st.execute("PRAGMA journal_mode = WAL")
            st.execute("PRAGMA busy_timeout = 5000")
            st.execute("PRAGMA synchronous = NORMAL")
            st.execute("PRAGMA foreign_keys = ON")
        }
        return conn
    }

    private fun sqliteVersion(conn: Connection): String =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT sqlite_version()").use { rs -> if (rs.next()) rs.getString(1) else "unknown" }
        }

    private fun requireConnection(): Connection =
        connection ?: error("数据库尚未初始化，请检查插件启动流程")

    /**
     * 以自动提交方式执行一段数据库操作
     */
    fun <T> read(block: (Connection) -> T): T = lock.withLock { block(requireConnection()) }

    /**
     * 在一个数据库事务中执行一段操作，异常时整体回滚
     */
    fun <T> transaction(block: (Connection) -> T): T {
        return lock.withLock {
            val conn = requireConnection()
            if (inTransaction) {
                // 已在外层事务中，直接并入，由最外层统一提交
                block(conn)
            } else {
                conn.autoCommit = false
                inTransaction = true
                try {
                    val result = block(conn)
                    conn.commit()
                    result
                } catch (e: Throwable) {
                    runCatching { conn.rollback() }
                    throw e
                } finally {
                    inTransaction = false
                    runCatching { conn.autoCommit = true }
                }
            }
        }
    }

    /** [read] 的协程版本，把阻塞的 JDBC 调用切到 IO 线程池 */
    suspend fun <T> readAsync(block: (Connection) -> T): T = withContext(Dispatchers.IO) { read(block) }

    /** [transaction] 的协程版本，把阻塞的 JDBC 调用切到 IO 线程池 */
    suspend fun <T> transactionAsync(block: (Connection) -> T): T = withContext(Dispatchers.IO) { transaction(block) }

    /* ==================== 完整性与备份 ==================== */

    /** 数据初始化标记是否已置位 */
    fun isDataInitialized(): Boolean = read { MetaDao.isInitialized(it) }

    /** 置位数据初始化标记 */
    fun markDataInitialized() = transaction { MetaDao.markInitialized(it) }

    /** 库中是否已存在任何业务数据（用于判断数据异常丢失） */
    fun hasAnyData(): Boolean = read { conn ->
        conn.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT (SELECT COUNT(*) FROM project_storage)
                     + (SELECT COUNT(*) FROM bucket)
                     + (SELECT COUNT(*) FROM code_cache)
                     + (SELECT COUNT(*) FROM statistics)
                """.trimIndent()
            ).use { rs -> rs.next() && rs.getLong(1) > 0 }
        }
    }

    /** 快速完整性自检，返回 SQLite 的原始结论（正常时为 `ok`） */
    fun quickCheck(): String = read { conn ->
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA quick_check").use { rs -> if (rs.next()) rs.getString(1) else "unknown" }
        }
    }

    /**
     * 生成数据库的一致性快照
     * - 运行中的 db 不能直接复制文件（WAL 尚未回写），必须走 `VACUUM INTO`。
     */
    fun snapshotTo(target: File): File = lock.withLock {
        val conn = requireConnection()
        check(!inTransaction) { "VACUUM INTO 不能在事务内执行" }
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        conn.createStatement().use { st ->
            st.executeUpdate("VACUUM INTO '${target.absolutePath.replace("'", "''")}'")
        }
        target
    }

    /**
     * 回写 WAL 并关闭连接
     */
    fun close() {
        lock.withLock {
            val conn = connection
            if (conn != null) {
                runCatching {
                    conn.createStatement().use { st -> st.execute("PRAGMA wal_checkpoint(TRUNCATE)") }
                }
                runCatching { conn.close() }
            }
            connection = null
            file = null
        }
    }
}
