package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.data.PastebinData

/**
 * # 项目执行锁定
 * 作者可以按场景禁止项目被执行；管理员锁定后仅管理员可执行。
 *
 * @author tiedanGH
 */
object ExecutionLock {

    /** 锁定字段名 */
    const val FIELD = "lock"

    /**
     * 锁定范围
     * @param desc 锁定后具体禁止内容
     */
    enum class Mode(val value: String, val desc: String) {
        PRIVATE("private", "🔒 禁止私信执行（仅限群聊）"),
        GROUP("group", "🔒 禁止群聊执行（仅限私信）"),
        ALL("all", "🔒 禁止全部执行（全局）"),
        ADMIN("admin", "🔒 管理员锁定（仅限管理员）");   // 仅管理员可设置与解除

        /**
         * 此锁定范围是否拦截本次执行
         * - 管理员不受任何锁定限制，作者不受自己设置的锁定限制
         * @param inGroup 本次执行是否发生在群聊
         */
        fun blocks(inGroup: Boolean, isOwner: Boolean, isAdmin: Boolean): Boolean {
            if (isAdmin) return false
            return when (this) {
                ADMIN -> true
                ALL -> !isOwner
                GROUP -> inGroup && !isOwner
                PRIVATE -> !inGroup && !isOwner
            }
        }
    }

    /** 解除锁定的输入 */
    private val CLEAR_WORDS = setOf("none", "无", "关闭", "off", "disable")

    private val ALIASES = mapOf(
        "private" to Mode.PRIVATE, "私信" to Mode.PRIVATE,
        "group" to Mode.GROUP, "群聊" to Mode.GROUP,
        "all" to Mode.ALL, "全部" to Mode.ALL,
        "admin" to Mode.ADMIN, "管理" to Mode.ADMIN,
    )

    /** 解除锁定的提示语 */
    const val CLEARED_DESC = "🔓 已解除锁定，任何场景均可执行"

    /** 是否为解除锁定的输入，空输入同样视为解除 */
    fun isClearWord(raw: String): Boolean = raw.isEmpty() || raw.trim().lowercase() in CLEAR_WORDS

    /**
     * 解析锁定范围，无法识别时返回 null
     * @param isAdmin 非管理员输入管理员锁定时同样返回 null
     */
    fun parse(raw: String, isAdmin: Boolean): Mode? =
        ALIASES[raw.trim().lowercase()]?.takeIf { it != Mode.ADMIN || isAdmin }

    /** 项目当前的锁定范围，未锁定时返回 null */
    fun of(name: String): Mode? =
        PastebinData.pastebin[name]?.get(FIELD)?.let { value -> Mode.entries.firstOrNull { it.value == value } }

    /** 设置锁定范围，传 null 解除锁定 */
    fun set(name: String, mode: Mode?) {
        val data = PastebinData.pastebin[name] ?: return
        if (mode == null) data.remove(FIELD) else data[FIELD] = mode.value
        PastebinData.save()
    }

    /**
     * 本次执行是否被锁定拦截
     * @param inGroup 本次执行是否发生在群聊
     */
    fun isBlocked(name: String, inGroup: Boolean, isOwner: Boolean, isAdmin: Boolean): Boolean =
        of(name)?.blocks(inGroup, isOwner, isAdmin) == true

    /** 被锁定时给执行者的提示 */
    fun blockedMessage(name: String): String = when (of(name)) {
        Mode.ADMIN -> "🔒 执行失败：项目 $name 已被管理员锁定，仅管理员可执行"
        Mode.GROUP -> "🔒 执行失败：项目 $name 已锁定群聊执行，请通过私信使用"
        Mode.PRIVATE -> "🔒 执行失败：项目 $name 已锁定私信执行，请在群聊中使用"
        else -> "🔒 执行失败：项目 $name 已被锁定，当前禁止执行"
    }
}
