package site.tiedan.module

import java.security.SecureRandom

/**
 * # 指令集分享码
 * - 分享时保存指令集的快照，只存在内存中：[TTL_MILLIS] 后过期，机器人重启后失效
 * - 内容完全相同的快照共用同一个分享码（只比较指令内容），重复分享时重新计时并更新名称
 * - 过期后仍可导入最后一次，导入后删除；新建分享时会先清理全部过期分享
 * - 每人最多同时保留 [MAX_ACTIVE_PER_USER] 个分享码，超出时清除最久未重新分享的那个
 *
 * @author tiedanGH
 */
object FavoriteShare {

    /** 分享码有效期 */
    const val TTL_MILLIS = 24 * 60 * 60 * 1000L

    /** 单个用户同时有效的分享码上限 */
    const val MAX_ACTIVE_PER_USER = FavoriteManager.MAX_SETS

    private const val CODE_LENGTH = 6

    /** 分享码字符：去掉 0/O/1/I 这类容易看错的字符 */
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** 快照中的一条指令 */
    data class Item(val project: String, val input: String)

    /**
     * 指令集快照
     * @param setName 最近一次分享时的名称，作为导入时的默认名称
     * @param creatorID 首次分享的用户，用于限制单个用户的分享数量
     */
    data class Snapshot(
        val code: String,
        val setName: String,
        val creatorID: String,
        val items: List<Item>,
        val expiresAt: Long,
    ) {
        fun isExpired(now: Long): Boolean = now >= expiresAt
    }

    private val byCode = HashMap<String, Snapshot>()
    private val byContent = HashMap<List<Item>, String>()
    private val random = SecureRandom()

    /**
     * 分享指令集：
     * - 先清理全部过期分享，内容与现有分享一致时沿用其分享码，重新计时并更新名称
     * - 用户的分享码已达上限时，清除最久未重新分享的那个后再新建
     * @return 分享后的快照
     */
    @Synchronized
    fun share(userID: String, setName: String, items: List<Item>, now: Long = System.currentTimeMillis()): Snapshot {
        purgeExpired(now)
        byContent[items]?.let { code ->
            val renewed = byCode.getValue(code).copy(setName = setName, expiresAt = now + TTL_MILLIS)
            byCode[code] = renewed
            return renewed
        }
        val own = byCode.values.filter { it.creatorID == userID }
        if (own.size >= MAX_ACTIVE_PER_USER) {
            own.sortedBy { it.expiresAt }.take(own.size - MAX_ACTIVE_PER_USER + 1).forEach(::remove)
        }
        val code = generateSequence { newCode() }.first { it !in byCode }
        val snapshot = Snapshot(code, setName, userID, items, now + TTL_MILLIS)
        byCode[code] = snapshot
        byContent[items] = code
        return snapshot
    }

    /**
     * 按分享码查找快照，不区分大小写
     * - 已过期但尚未清理的仍会返回，允许最后一次导入
     */
    @Synchronized
    fun find(code: String): Snapshot? = byCode[code.uppercase()]

    /**
     * 导入成功后调用：已过期的分享在这次导入后删除
     */
    @Synchronized
    fun afterImport(code: String, now: Long = System.currentTimeMillis()) {
        val snapshot = byCode[code.uppercase()] ?: return
        if (snapshot.isExpired(now)) remove(snapshot)
    }

    /** 清空全部分享（仅测试使用） */
    @Synchronized
    internal fun clear() {
        byCode.clear()
        byContent.clear()
    }

    private fun purgeExpired(now: Long) {
        byCode.values.filter { it.isExpired(now) }.forEach(::remove)
    }

    private fun remove(snapshot: Snapshot) {
        byCode.remove(snapshot.code)
        byContent.remove(snapshot.items)
    }

    private fun newCode(): String =
        buildString(CODE_LENGTH) { repeat(CODE_LENGTH) { append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]) } }
}
