package site.tiedan.data.dao

import java.sql.Connection

/**
 * # 个人收藏数据访问
 * - 收藏按序号排列，序号固定：移除后空置，其余收藏的序号不变
 * - 指令集由同一用户同名的全部指令构成，按写入先后排列，最后一条移除后指令集随之消失
 *
 * @author tiedanGH
 */
object FavoriteDao {

    /**
     * 一条收藏
     * @param alias 收藏别名，未设置为 null
     */
    data class Favorite(val slot: Int, val project: String, val alias: String?)

    /** 指令集中的一条指令 */
    data class Command(val id: Long, val setName: String, val project: String, val input: String)

    /* ==================== 收藏 ==================== */

    /** 用户的全部收藏，按序号排列 */
    fun listFavorites(conn: Connection, userID: String): List<Favorite> =
        conn.prepareStatement("SELECT slot, project, alias FROM favorite_project WHERE user_id = ? ORDER BY slot").use { ps ->
            ps.setString(1, userID)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Favorite(rs.getInt(1), rs.getString(2), rs.getString(3))) }
            }
        }

    /** 在指定序号写入收藏，项目已收藏或序号已占用时不写入并返回 false */
    fun addFavorite(conn: Connection, userID: String, slot: Int, project: String): Boolean =
        conn.prepareStatement("INSERT OR IGNORE INTO favorite_project(user_id, slot, project) VALUES(?, ?, ?)").use { ps ->
            ps.setString(1, userID)
            ps.setInt(2, slot)
            ps.setString(3, project)
            ps.executeUpdate() > 0
        }

    /** 移除指定序号的收藏 */
    fun removeFavorite(conn: Connection, userID: String, slot: Int): Boolean =
        conn.prepareStatement("DELETE FROM favorite_project WHERE user_id = ? AND slot = ?").use { ps ->
            ps.setString(1, userID)
            ps.setInt(2, slot)
            ps.executeUpdate() > 0
        }

    /** 设置指定序号的收藏别名，传 null 清除 */
    fun setAlias(conn: Connection, userID: String, slot: Int, alias: String?): Boolean =
        conn.prepareStatement("UPDATE favorite_project SET alias = ? WHERE user_id = ? AND slot = ?").use { ps ->
            ps.setString(1, alias)
            ps.setString(2, userID)
            ps.setInt(3, slot)
            ps.executeUpdate() > 0
        }

    /* ==================== 指令集 ==================== */

    /** 用户的全部指令，按写入先后排列 */
    fun listCommands(conn: Connection, userID: String): List<Command> =
        conn.prepareStatement(
            "SELECT id, set_name, project, input FROM favorite_command WHERE user_id = ? ORDER BY id"
        ).use { ps ->
            ps.setString(1, userID)
            ps.executeQuery().use { rs -> readCommands(rs) }
        }

    /** 指令集中的全部指令，按写入先后排列 */
    fun listSet(conn: Connection, userID: String, setName: String): List<Command> =
        conn.prepareStatement(
            "SELECT id, set_name, project, input FROM favorite_command WHERE user_id = ? AND set_name = ? ORDER BY id"
        ).use { ps ->
            ps.setString(1, userID)
            ps.setString(2, setName)
            ps.executeQuery().use { rs -> readCommands(rs) }
        }

    /** 用户的全部指令集名称，按创建先后排列 */
    fun setNames(conn: Connection, userID: String): List<String> =
        conn.prepareStatement(
            "SELECT set_name FROM favorite_command WHERE user_id = ? GROUP BY set_name ORDER BY MIN(id)"
        ).use { ps ->
            ps.setString(1, userID)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }

    /** 向指令集追加一条指令 */
    fun addCommand(conn: Connection, userID: String, setName: String, project: String, input: String) {
        conn.prepareStatement(
            "INSERT INTO favorite_command(user_id, set_name, project, input) VALUES(?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, userID)
            ps.setString(2, setName)
            ps.setString(3, project)
            ps.setString(4, input)
            ps.executeUpdate()
        }
    }

    /** 删除一条指令，只能删除自己的 */
    fun removeCommand(conn: Connection, userID: String, id: Long): Boolean =
        conn.prepareStatement("DELETE FROM favorite_command WHERE user_id = ? AND id = ?").use { ps ->
            ps.setString(1, userID)
            ps.setLong(2, id)
            ps.executeUpdate() > 0
        }

    /** 删除整个指令集，返回删除的指令条数 */
    fun deleteSet(conn: Connection, userID: String, setName: String): Int =
        conn.prepareStatement("DELETE FROM favorite_command WHERE user_id = ? AND set_name = ?").use { ps ->
            ps.setString(1, userID)
            ps.setString(2, setName)
            ps.executeUpdate()
        }

    private fun readCommands(rs: java.sql.ResultSet): List<Command> =
        buildList { while (rs.next()) add(Command(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4))) }

    /* ==================== 项目联动 ==================== */

    /**
     * 项目改名时迁移全部用户的收藏与指令
     * - 先清除目标名下的残留行，收藏的序号与别名保持不变
     */
    fun renameProject(conn: Connection, from: String, to: String) {
        if (from == to) return
        for (table in listOf("favorite_project", "favorite_command")) {
            conn.prepareStatement("DELETE FROM $table WHERE project = ?").use { ps ->
                ps.setString(1, to)
                ps.executeUpdate()
            }
            conn.prepareStatement("UPDATE $table SET project = ? WHERE project = ?").use { ps ->
                ps.setString(1, to)
                ps.setString(2, from)
                ps.executeUpdate()
            }
        }
    }

    /** 删除全部用户对该项目的收藏 */
    fun removeFavoritesOf(conn: Connection, project: String) {
        conn.prepareStatement("DELETE FROM favorite_project WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeUpdate()
        }
    }

    /** 删除全部用户指令集中该项目的指令 */
    fun removeCommandsOf(conn: Connection, project: String) {
        conn.prepareStatement("DELETE FROM favorite_command WHERE project = ?").use { ps ->
            ps.setString(1, project)
            ps.executeUpdate()
        }
    }

    /** 项目删除时一并清除收藏与指令 */
    fun removeProjectFromAll(conn: Connection, project: String) {
        removeFavoritesOf(conn, project)
        removeCommandsOf(conn, project)
    }

    /** 被收藏过的全部项目名（去重） */
    fun listFavoritedProjects(conn: Connection): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT DISTINCT project FROM favorite_project").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    /** 指令集中出现过的全部项目名（去重） */
    fun listCommandProjects(conn: Connection): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT DISTINCT project FROM favorite_command").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
}
