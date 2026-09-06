package site.tiedan.data.dao

import java.sql.Connection
import java.sql.ResultSet

/**
 * # 存储库数据访问
 * - 关联项目规范化为 `bucket_project` 关联表，「关联了某项目的存储库」一次索引查询
 * - 删除存储库不删行，而是把 `empty` 置 1，保留槽位编号
 *
 * @author tiedanGH
 */
object BucketDao {

    /** 存储库一行数据 */
    data class BucketRow(
        val id: Long,
        val name: String,
        val password: String,
        val owner: String,
        val userID: String,
        val projects: List<String>,
        val description: String,
        val content: String,
        val contentLen: Int,
        val encrypt: Boolean,
    )

    /** 备份一行数据 */
    data class BackupRow(val name: String, val time: Long, val content: String)

    private fun readBucket(rs: ResultSet, projects: List<String>) = BucketRow(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        password = rs.getString("password"),
        owner = rs.getString("owner"),
        userID = rs.getString("user_id"),
        projects = projects,
        description = rs.getString("description"),
        content = rs.getString("content"),
        contentLen = rs.getInt("content_len"),
        encrypt = rs.getInt("encrypt") != 0,
    )

    /* ==================== 槽位 ==================== */

    /** 槽位是否存在（空置也算存在） */
    fun exists(conn: Connection, id: Long): Boolean =
        conn.prepareStatement("SELECT 1 FROM bucket WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> rs.next() }
        }

    /** 槽位是否为空置状态（不存在同样视为空置） */
    fun isEmpty(conn: Connection, id: Long): Boolean =
        conn.prepareStatement("SELECT empty FROM bucket WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) != 0 else true }
        }

    /**
     * 分配下一个可用槽位编号
     */
    fun nextFreeId(conn: Connection): Long =
        conn.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT COALESCE(MIN(n), 1) FROM (
                    SELECT 1 AS n
                    UNION ALL
                    SELECT id + 1 FROM bucket WHERE empty = 0
                ) candidates
                WHERE n NOT IN (SELECT id FROM bucket WHERE empty = 0)
                """.trimIndent()
            ).use { rs -> if (rs.next()) rs.getLong(1) else 1L }
        }

    /** 非空存储库数量 */
    fun count(conn: Connection): Int =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM bucket WHERE empty = 0").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }

    /* ==================== 读取 ==================== */

    /** 获取存储库，空置或不存在时返回 null */
    fun get(conn: Connection, id: Long): BucketRow? =
        conn.prepareStatement("SELECT * FROM bucket WHERE id = ? AND empty = 0").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) readBucket(rs, getProjects(conn, id)) else null
            }
        }

    /** 全部槽位（编号升序），空置槽位的值为 null */
    fun listSlots(conn: Connection): Map<Long, BucketRow?> {
        val projectsById = allProjects(conn)
        return conn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM bucket ORDER BY id").use { rs ->
                buildMap {
                    while (rs.next()) {
                        val id = rs.getLong("id")
                        val empty = rs.getInt("empty") != 0
                        put(id, if (empty) null else readBucket(rs, projectsById[id].orEmpty()))
                    }
                }
            }
        }
    }

    /** 按名称查找存储库 */
    fun findByName(conn: Connection, name: String): BucketRow? =
        conn.prepareStatement("SELECT * FROM bucket WHERE name = ? AND empty = 0 ORDER BY id LIMIT 1").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs ->
                if (rs.next()) readBucket(rs, getProjects(conn, rs.getLong("id"))) else null
            }
        }

    /** 存储库编号转名称，空置槽位返回 null */
    fun idToName(conn: Connection, id: Long): String? =
        conn.prepareStatement("SELECT name FROM bucket WHERE id = ? AND empty = 0").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    /** 获取存储库主存储数据（加密库为密文） */
    fun getRawContent(conn: Connection, id: Long): String? =
        conn.prepareStatement("SELECT content FROM bucket WHERE id = ? AND empty = 0").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    /** 全部存储库主存储数据的总长度 */
    fun totalContentLength(conn: Connection): Long =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COALESCE(SUM(content_len), 0) FROM bucket WHERE empty = 0").use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }

    /* ==================== 写入 ==================== */

    /**
     * 创建存储库
     */
    fun create(conn: Connection, id: Long, name: String, passwordHash: String, owner: String, userID: String) {
        conn.prepareStatement(
            """
            INSERT INTO bucket(id, name, password, owner, user_id, description, content, content_len, encrypt, empty)
            VALUES(?, ?, ?, ?, ?, '', '', 0, 0, 0)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name, password = excluded.password, owner = excluded.owner,
                user_id = excluded.user_id, description = '', content = '', content_len = 0,
                encrypt = 0, empty = 0
            """.trimIndent()
        ).use { ps ->
            ps.setLong(1, id)
            ps.setString(2, name)
            ps.setString(3, passwordHash)
            ps.setString(4, owner)
            ps.setString(5, userID)
            ps.executeUpdate()
        }
        clearProjects(conn, id)
        clearBackups(conn, id)
    }

    /**
     * 删除存储库，保留空置槽位
     */
    fun delete(conn: Connection, id: Long) {
        conn.prepareStatement(
            """
            UPDATE bucket SET name = '', password = '', owner = '', user_id = '', description = '',
                content = '', content_len = 0, encrypt = 0, empty = 1 WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
        clearProjects(conn, id)
        clearBackups(conn, id)
    }

    /** 直接写入存储库主存储数据 */
    fun setRawContent(conn: Connection, id: Long, raw: String) {
        conn.prepareStatement("UPDATE bucket SET content = ?, content_len = ? WHERE id = ?").use { ps ->
            ps.setString(1, raw)
            ps.setInt(2, raw.length)
            ps.setLong(3, id)
            ps.executeUpdate()
        }
    }

    // 逐字段拆分为独立函数，列名不能来自于用户输入
    fun setName(conn: Connection, id: Long, name: String) = setColumn(conn, id, "name", name)
    fun setPassword(conn: Connection, id: Long, passwordHash: String) = setColumn(conn, id, "password", passwordHash)
    fun setOwner(conn: Connection, id: Long, owner: String) = setColumn(conn, id, "owner", owner)
    fun setUserID(conn: Connection, id: Long, userID: String) = setColumn(conn, id, "user_id", userID)
    fun setDescription(conn: Connection, id: Long, desc: String) = setColumn(conn, id, "description", desc)

    private fun setColumn(conn: Connection, id: Long, column: String, value: String) {
        conn.prepareStatement("UPDATE bucket SET $column = ? WHERE id = ?").use { ps ->
            ps.setString(1, value)
            ps.setLong(2, id)
            ps.executeUpdate()
        }
    }

    /** 设置数据加密标记 */
    fun setEncrypt(conn: Connection, id: Long, encrypt: Boolean) {
        conn.prepareStatement("UPDATE bucket SET encrypt = ? WHERE id = ?").use { ps ->
            ps.setInt(1, if (encrypt) 1 else 0)
            ps.setLong(2, id)
            ps.executeUpdate()
        }
    }

    /* ==================== 关联项目 ==================== */

    /** 获取存储库关联的项目列表 */
    fun getProjects(conn: Connection, id: Long): List<String> =
        conn.prepareStatement("SELECT project FROM bucket_project WHERE bucket_id = ? ORDER BY rowid").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }

    /** 读取全部存储库的关联项目 */
    fun allProjects(conn: Connection): Map<Long, List<String>> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT bucket_id, project FROM bucket_project ORDER BY bucket_id, rowid").use { rs ->
                buildMap<Long, MutableList<String>> {
                    while (rs.next()) getOrPut(rs.getLong(1)) { mutableListOf() }.add(rs.getString(2))
                }
            }
        }

    /** 覆盖存储库关联的项目列表 */
    fun setProjects(conn: Connection, id: Long, projects: List<String>) {
        clearProjects(conn, id)
        if (projects.isEmpty()) return
        conn.prepareStatement("INSERT OR IGNORE INTO bucket_project(bucket_id, project) VALUES(?, ?)").use { ps ->
            for (project in projects) {
                if (project.isBlank()) continue
                ps.setLong(1, id)
                ps.setString(2, project)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun clearProjects(conn: Connection, id: Long) {
        conn.prepareStatement("DELETE FROM bucket_project WHERE bucket_id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    /** 查询关联了指定项目的全部存储库（编号升序） */
    fun linkedIds(conn: Connection, project: String): List<Long> =
        conn.prepareStatement(
            """
            SELECT bp.bucket_id FROM bucket_project bp
            JOIN bucket b ON b.id = bp.bucket_id AND b.empty = 0
            WHERE bp.project = ? ORDER BY bp.bucket_id
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, project)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong(1)) } }
        }

    /** 全部被存储库关联过的项目名 */
    fun listLinkedProjects(conn: Connection): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT DISTINCT project FROM bucket_project").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    /** 将项目从全部存储库的关联列表中移除 */
    fun removeProjectFromAll(conn: Connection, project: String) {
        conn.prepareStatement("DELETE FROM bucket_project WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeUpdate()
        }
    }

    /** 项目改名时迁移全部存储库的关联记录 */
    fun renameProjectInAll(conn: Connection, from: String, to: String) {
        if (from == to) return
        // 目标名可能已被同一存储库关联，先清掉以免撞主键
        conn.prepareStatement(
            "DELETE FROM bucket_project WHERE project = ? AND bucket_id IN (SELECT bucket_id FROM bucket_project WHERE project = ?)"
        ).use { ps ->
            ps.setString(1, to)
            ps.setString(2, from)
            ps.executeUpdate()
        }
        conn.prepareStatement("UPDATE bucket_project SET project = ? WHERE project = ?").use { ps ->
            ps.setString(1, to)
            ps.setString(2, from)
            ps.executeUpdate()
        }
    }

    /** 全部存储库的关联项目条目总数 */
    fun totalLinkedProjects(conn: Connection): Long =
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT COUNT(*) FROM bucket_project bp JOIN bucket b ON b.id = bp.bucket_id AND b.empty = 0"
            ).use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }

    /* ==================== 备份 ==================== */

    /** 获取全部备份槽位，长度固定为 [Schema.BACKUP_SLOTS]，空槽为 null */
    fun getBackups(conn: Connection, id: Long): List<BackupRow?> {
        val slots = arrayOfNulls<BackupRow>(Schema.BACKUP_SLOTS)
        conn.prepareStatement("SELECT slot, name, time, content FROM bucket_backup WHERE bucket_id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val slot = rs.getInt(1)
                    if (slot in slots.indices) {
                        slots[slot] = BackupRow(rs.getString(2), rs.getLong(3), rs.getString(4))
                    }
                }
            }
        }
        return slots.toList()
    }

    /** 获取指定槽位的备份 */
    fun getBackup(conn: Connection, id: Long, slot: Int): BackupRow? {
        if (slot !in 0 until Schema.BACKUP_SLOTS) return null
        return conn.prepareStatement(
            "SELECT name, time, content FROM bucket_backup WHERE bucket_id = ? AND slot = ?"
        ).use { ps ->
            ps.setLong(1, id)
            ps.setInt(2, slot)
            ps.executeQuery().use { rs ->
                if (rs.next()) BackupRow(rs.getString(1), rs.getLong(2), rs.getString(3)) else null
            }
        }
    }

    /** 写入指定槽位的备份，传入 null 清空该槽位 */
    fun setBackup(conn: Connection, id: Long, slot: Int, backup: BackupRow?) {
        if (slot !in 0 until Schema.BACKUP_SLOTS) return
        if (backup == null) {
            conn.prepareStatement("DELETE FROM bucket_backup WHERE bucket_id = ? AND slot = ?").use { ps ->
                ps.setLong(1, id)
                ps.setInt(2, slot)
                ps.executeUpdate()
            }
            return
        }
        conn.prepareStatement(
            """
            INSERT INTO bucket_backup(bucket_id, slot, name, time, content, content_len) VALUES(?, ?, ?, ?, ?, ?)
            ON CONFLICT(bucket_id, slot) DO UPDATE SET
                name = excluded.name, time = excluded.time,
                content = excluded.content, content_len = excluded.content_len
            """.trimIndent()
        ).use { ps ->
            ps.setLong(1, id)
            ps.setInt(2, slot)
            ps.setString(3, backup.name)
            ps.setLong(4, backup.time)
            ps.setString(5, backup.content)
            ps.setInt(6, backup.content.length)
            ps.executeUpdate()
        }
    }

    /** 清空指定存储库的全部备份 */
    fun clearBackups(conn: Connection, id: Long) {
        conn.prepareStatement("DELETE FROM bucket_backup WHERE bucket_id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    /** 全部存储库的备份数据总长度 */
    fun totalBackupLength(conn: Connection): Long =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COALESCE(SUM(content_len), 0) FROM bucket_backup").use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }
}
