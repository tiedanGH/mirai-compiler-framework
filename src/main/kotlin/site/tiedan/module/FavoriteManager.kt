package site.tiedan.module

import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.data.Database
import site.tiedan.data.PastebinData
import site.tiedan.data.dao.CodeCacheDao
import site.tiedan.data.dao.FavoriteDao
import site.tiedan.utils.PastebinUrlHelper

/**
 * # 个人收藏
 * - 收藏：每人最多 [MAX_FAVORITES] 个，序号固定，可设置一个收藏别名
 * - 指令集：每人最多 [MAX_SETS] 个，单个指令集的条数上限为单用户进程上限
 *
 * @author tiedanGH
 */
object FavoriteManager {

    const val MAX_FAVORITES = 50
    const val MAX_SETS = 20
    const val MAX_NAME_LENGTH = 10
    const val MAX_INPUT_LENGTH = 500

    /* ==================== 名称校验 ==================== */

    /** 纯数字一律按序号解析：指令集名称、收藏别名的校验与执行分派共用 */
    fun isIndexToken(token: String): Boolean = token.isNotEmpty() && token.all { it.isDigit() }

    private fun checkNameFormat(name: String, what: String): String? = when {
        name.length > MAX_NAME_LENGTH -> "${what}不能超过 $MAX_NAME_LENGTH 个字"
        isIndexToken(name) -> "${what}不能是纯数字"
        else -> null
    }

    /**
     * 校验收藏别名，不通过时返回原因
     * @param projects 全部项目名称
     * @param otherAliases 自己其他收藏的别名
     * @param setNames 自己的指令集名称
     */
    fun checkAlias(alias: String, projects: Set<String>, otherAliases: Set<String>, setNames: Collection<String>): String? =
        checkNameFormat(alias, "收藏别名") ?: when (alias) {
            in projects -> "收藏别名不能与项目名称重复：$alias"
            in otherAliases -> "当前别名已被您的其他收藏使用"
            in setNames -> "收藏别名不能与您的指令集名称重复：$alias"
            else -> null
        }

    /**
     * 校验新建的指令集名称，不通过时返回原因
     * @param aliases 自己全部收藏的别名
     */
    fun checkSetName(name: String, aliases: Set<String>): String? =
        checkNameFormat(name, "指令集名称") ?: if (name in aliases) "指令集名称不能与您的收藏别名重复：$name" else null

    /**
     * 按序号、收藏别名、项目名称（含项目别名）查找收藏
     * @param projectAlias 项目别名到项目名称的映射
     */
    fun findFavorite(
        favorites: List<FavoriteDao.Favorite>,
        token: String,
        projectAlias: (String) -> String? = { PastebinData.alias[it] },
    ): FavoriteDao.Favorite? {
        if (isIndexToken(token)) {
            val slot = token.toIntOrNull() ?: return null
            return favorites.find { it.slot == slot }
        }
        return favorites.find { it.alias == token }
            ?: favorites.find { it.project == token }
            ?: projectAlias(token)?.let { real -> favorites.find { it.project == real } }
    }

    /* ==================== 收藏 ==================== */

    /** 用户的全部收藏，按序号排列 */
    fun favorites(userID: String): List<FavoriteDao.Favorite> = Database.read { FavoriteDao.listFavorites(it, userID) }

    /** 最小的空位序号，收藏已满时返回 null */
    fun firstFreeSlot(usedSlots: Set<Int>): Int? = (1..MAX_FAVORITES).firstOrNull { it !in usedSlots }

    /**
     * 批量收藏结果
     * @param overflow 收藏已满未能收藏的项目
     */
    data class AddResult(
        val added: List<FavoriteDao.Favorite>,
        val duplicated: List<String>,
        val unknown: List<String>,
        val overflow: List<String>,
    )

    /**
     * 批量收藏：项目别名解析为项目名称，依次填入最小的空位序号
     */
    fun addFavorites(userID: String, tokens: List<String>): AddResult = Database.transaction { conn ->
        val existing = FavoriteDao.listFavorites(conn, userID)
        val usedSlots = existing.mapTo(HashSet()) { it.slot }
        val favorited = existing.mapTo(HashSet()) { it.project }
        val added = mutableListOf<FavoriteDao.Favorite>()
        val duplicated = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        val overflow = mutableListOf<String>()
        for (token in tokens.distinct()) {
            val project = PastebinData.alias[token] ?: token
            when {
                project !in PastebinData.pastebin -> unknown += token
                project in favorited -> duplicated += project
                else -> {
                    val slot = firstFreeSlot(usedSlots)
                    if (slot == null) {
                        overflow += project
                    } else {
                        FavoriteDao.addFavorite(conn, userID, slot, project)
                        usedSlots += slot
                        favorited += project
                        added += FavoriteDao.Favorite(slot, project, null)
                    }
                }
            }
        }
        AddResult(added, duplicated, unknown, overflow)
    }

    /** 批量移除结果 */
    data class RemoveResult(val removed: List<FavoriteDao.Favorite>, val notFound: List<String>)

    /**
     * 批量移除收藏：原序号空置，其余收藏的序号不变
     */
    fun removeFavorites(userID: String, tokens: List<String>): RemoveResult = Database.transaction { conn ->
        val favorites = FavoriteDao.listFavorites(conn, userID)
        val removed = mutableListOf<FavoriteDao.Favorite>()
        val notFound = mutableListOf<String>()
        for (token in tokens.distinct()) {
            val target = findFavorite(favorites, token)
            when {
                target == null -> notFound += token
                target in removed -> {}
                else -> {
                    FavoriteDao.removeFavorite(conn, userID, target.slot)
                    removed += target
                }
            }
        }
        RemoveResult(removed, notFound)
    }

    /** 设置收藏别名的结果 */
    sealed interface AliasResult {
        data class Updated(val favorite: FavoriteDao.Favorite, val alias: String?) : AliasResult
        data class Rejected(val reason: String) : AliasResult
        data object NotFound : AliasResult
    }

    /**
     * 设置收藏别名，传 null 清除
     */
    fun setAlias(userID: String, token: String, alias: String?): AliasResult = Database.transaction { conn ->
        val favorites = FavoriteDao.listFavorites(conn, userID)
        val target = findFavorite(favorites, token) ?: return@transaction AliasResult.NotFound
        if (alias != null) {
            val otherAliases = favorites.filter { it.slot != target.slot }.mapNotNullTo(HashSet()) { it.alias }
            checkAlias(alias, PastebinData.pastebin.keys, otherAliases, FavoriteDao.setNames(conn, userID))
                ?.let { return@transaction AliasResult.Rejected(it) }
        }
        FavoriteDao.setAlias(conn, userID, target.slot, alias)
        AliasResult.Updated(target, alias)
    }

    /* ==================== 指令集 ==================== */

    /** 用户的全部指令集名称，按创建先后排列 */
    fun setNames(userID: String): List<String> = Database.read { FavoriteDao.setNames(it, userID) }

    /** 指令集中的全部指令 */
    fun commandSet(userID: String, setName: String): List<FavoriteDao.Command> =
        Database.read { FavoriteDao.listSet(it, userID, setName) }

    /** 用户的全部指令集，按创建先后排列 */
    fun commandSets(userID: String): Map<String, List<FavoriteDao.Command>> =
        Database.read { FavoriteDao.listCommands(it, userID) }.groupBy { it.setName }

    /** 追加指令的结果 */
    sealed interface SetAddResult {
        /** @param created 本次是否新建了指令集 */
        data class Added(val size: Int, val created: Boolean) : SetAddResult
        data class Rejected(val reason: String) : SetAddResult
    }

    /**
     * 向指令集追加一条指令，指令集不存在时自动创建
     * @param perSetLimit 单个指令集的条数上限
     */
    fun addCommand(userID: String, setName: String, project: String, input: String, perSetLimit: Int): SetAddResult =
        Database.transaction { conn ->
            val names = FavoriteDao.setNames(conn, userID)
            val created = setName !in names
            if (created) {
                val aliases = FavoriteDao.listFavorites(conn, userID).mapNotNullTo(HashSet()) { it.alias }
                checkSetName(setName, aliases)?.let { return@transaction SetAddResult.Rejected(it) }
                if (names.size >= MAX_SETS) {
                    return@transaction SetAddResult.Rejected("指令集数量已达上限 $MAX_SETS 个，请先删除不用的指令集")
                }
            }
            val size = FavoriteDao.listSet(conn, userID, setName).size
            if (size >= perSetLimit) {
                return@transaction SetAddResult.Rejected(
                    "指令集「$setName」已有 $size 条指令，达到单用户进程上限 $perSetLimit 条"
                )
            }
            FavoriteDao.addCommand(conn, userID, setName, project, input)
            SetAddResult.Added(size + 1, created)
        }

    /**
     * 移除指令集中的第 [index] 条指令
     * - 从 1 开始，后面的指令依次前移
     * @return 被移除的指令，序号不存在时返回 null
     */
    fun removeCommand(userID: String, setName: String, index: Int): FavoriteDao.Command? =
        Database.transaction { conn ->
            val target = FavoriteDao.listSet(conn, userID, setName).getOrNull(index - 1)
            if (target != null) FavoriteDao.removeCommand(conn, userID, target.id)
            target
        }

    /** 删除整个指令集，返回删除的指令条数 */
    fun deleteSet(userID: String, setName: String): Int =
        Database.transaction { FavoriteDao.deleteSet(it, userID, setName) }

    /** 指令集改名的结果 */
    sealed interface RenameResult {
        /** @param count 迁移的指令条数 */
        data class Renamed(val count: Int) : RenameResult
        data class Rejected(val reason: String) : RenameResult
        data object NotFound : RenameResult
    }

    /**
     * 指令集改名：新名称沿用新建指令集的校验，且不能与自己已有的指令集重名
     */
    fun renameSet(userID: String, from: String, to: String): RenameResult = Database.transaction { conn ->
        val names = FavoriteDao.setNames(conn, userID)
        if (from !in names) return@transaction RenameResult.NotFound
        if (to == from) return@transaction RenameResult.Rejected("新名称与原名称相同")
        if (to in names) return@transaction RenameResult.Rejected("指令集「$to」已存在")
        val aliases = FavoriteDao.listFavorites(conn, userID).mapNotNullTo(HashSet()) { it.alias }
        checkSetName(to, aliases)?.let { return@transaction RenameResult.Rejected(it) }
        RenameResult.Renamed(FavoriteDao.renameSet(conn, userID, from, to))
    }

    /* ==================== 快捷前缀 ==================== */

    /** 快捷前缀匹配到的收藏目标 */
    sealed interface QuickTarget {
        data class Favorite(val favorite: FavoriteDao.Favorite) : QuickTarget
        data class CommandSet(val name: String, val commands: List<FavoriteDao.Command>) : QuickTarget
    }

    /**
     * 按快捷前缀后的名称查找自己的收藏别名或指令集，收藏别名优先
     * - 收藏别名与指令集名称不超过 [MAX_NAME_LENGTH] 个字且不是纯数字，不符合的名称直接跳过，不查询数据库
     * @return 都不匹配时返回 null
     */
    fun resolveQuick(userID: String, token: String): QuickTarget? {
        if (token.length > MAX_NAME_LENGTH || isIndexToken(token)) return null
        return Database.read { conn ->
            FavoriteDao.findByAlias(conn, userID, token)?.let { return@read QuickTarget.Favorite(it) }
            FavoriteDao.listSet(conn, userID, token).takeIf { it.isNotEmpty() }?.let { QuickTarget.CommandSet(token, it) }
        }
    }

    /* ==================== 项目联动 ==================== */

    /**
     * 项目改名时迁移收藏与指令集
     */
    fun renameProject(from: String, to: String) {
        runCatching { Database.transaction { FavoriteDao.renameProject(it, from, to) } }
            .onFailure { logger.warning("迁移项目 $from 的收藏数据失败", it) }
    }

    /**
     * 项目删除时清除收藏与指令集中的该项目
     */
    fun removeProject(name: String) {
        runCatching { Database.transaction { FavoriteDao.removeProjectFromAll(it, name) } }
            .onFailure { logger.warning("清除项目 $name 的收藏数据失败", it) }
    }

    /* ==================== 状态与卡片 ==================== */

    /** 状态徽标类别 */
    enum class BadgeKind { DELETED, CENSOR, LOCK, LINK_DOWN, UNRUNNABLE }

    data class Badge(val label: String, val kind: BadgeKind)

    /** 锁定范围的简短标签 */
    fun lockLabel(mode: ExecutionLock.Mode): String = when (mode) {
        ExecutionLock.Mode.PRIVATE -> "🔒 仅群聊"
        ExecutionLock.Mode.GROUP -> "🔒 仅私信"
        ExecutionLock.Mode.ALL -> "🔒 禁止执行"
    }

    /**
     * 链接指向已停服网站且本地没有代码缓存，项目已无法执行
     * @param cached 已缓存代码的项目名单，只在链接已停服时读取
     */
    fun isDiscontinuedWithoutCache(name: String, cached: () -> Set<String>): Boolean {
        val url = PastebinData.pastebin[name]?.get("url") ?: return false
        return PastebinUrlHelper.isDiscontinued(url) && name !in cached()
    }

    /**
     * 项目当前的状态徽标，正常时为 null
     * - 只取优先级最高的一个：已删除 > 审核中 > 无法执行 > 锁定 > 链接停服
     */
    fun projectBadge(name: String, cached: () -> Set<String>): Badge? {
        val data = PastebinData.pastebin[name] ?: return Badge("已删除", BadgeKind.DELETED)
        if (name in PastebinData.censorList) return Badge("审核中", BadgeKind.CENSOR)
        val discontinued = data["url"]?.let { PastebinUrlHelper.isDiscontinued(it) } == true
        if (discontinued && name !in cached()) return Badge("⚠️ 无法执行", BadgeKind.UNRUNNABLE)
        ExecutionLock.of(name)?.let { return Badge(lockLabel(it), BadgeKind.LOCK) }
        return if (discontinued) Badge("⚠️ 链接停服", BadgeKind.LINK_DOWN) else null
    }

    /** 已缓存代码的项目名单 */
    fun cachedProjects(): Set<String> = Database.read { CodeCacheDao.listProjects(it) }.toSet()

    /**
     * 卡片中的一条收藏
     * @param author 项目作者，项目已删除时为 null
     * @param language 项目已删除时为 null
     */
    data class FavoriteRow(
        val slot: Int,
        val project: String,
        val author: String?,
        val alias: String?,
        val projectAlias: String?,
        val language: String?,
        val deleted: Boolean,
        val badge: Badge?,
    )

    /**
     * 卡片中的一个指令集
     * @param missing 已不存在的项目
     */
    data class SetRow(val name: String, val projects: List<String>, val missing: Set<String>)

    /**
     * 收藏卡片
     * @param setLimit 单个指令集的条数上限
     */
    data class Card(val favorites: List<FavoriteRow>, val sets: List<SetRow>, val setLimit: Int) {
        val isEmpty: Boolean get() = favorites.isEmpty() && sets.isEmpty()
    }

    /**
     * 汇总收藏卡片所需的数据
     */
    fun buildCard(userID: String, setLimit: Int): Card {
        val projectAliases = PastebinData.alias.entries.associate { (alias, project) -> project to alias }
        val cached by lazy { cachedProjects() }
        val rows = favorites(userID).map { fav ->
            val data = PastebinData.pastebin[fav.project]
            FavoriteRow(
                slot = fav.slot,
                project = fav.project,
                author = data?.get("author"),
                alias = fav.alias,
                projectAlias = projectAliases[fav.project],
                language = data?.get("language"),
                deleted = data == null,
                badge = projectBadge(fav.project) { cached },
            )
        }
        val sets = commandSets(userID).map { (name, commands) ->
            val projects = commands.map { it.project }
            SetRow(name, projects, projects.filterTo(HashSet()) { it !in PastebinData.pastebin })
        }
        return Card(rows, sets, setLimit)
    }

    /** 合并重复项目，保持首次出现的顺序 */
    fun mergeProjects(projects: List<String>): List<Pair<String, Int>> =
        projects.groupingBy { it }.eachCount().toList()

    /** 合并重复项目后的文字，重复的项目写作「项目×次数」 */
    fun mergedProjectText(projects: List<String>): String =
        mergeProjects(projects).joinToString("、") { (name, count) -> if (count > 1) "$name×$count" else name }

    /** 输入预览：换行显示为 ⏎，过长截断 */
    fun inputPreview(input: String): String {
        val flat = input.replace("\r\n", "\n").replace("\n", " ⏎ ")
        return if (flat.length > 40) flat.take(40) + "…" else flat
    }

    /**
     * 指令集详情中的一条指令
     * @param index 在指令集中的序号（从 1 开始）
     */
    data class CommandRow(
        val index: Int,
        val project: String,
        val input: String,
        val deleted: Boolean,
        val badge: Badge?,
    )

    /**
     * 指令集详情
     * @param setLimit 单个指令集的条数上限
     */
    data class SetDetail(val name: String, val commands: List<CommandRow>, val setLimit: Int)

    /**
     * 汇总指令集详情所需的数据
     */
    fun buildSetDetail(setName: String, commands: List<FavoriteDao.Command>, setLimit: Int): SetDetail {
        val cached by lazy { cachedProjects() }
        val rows = commands.mapIndexed { index, command ->
            CommandRow(
                index = index + 1,
                project = command.project,
                input = command.input,
                deleted = command.project !in PastebinData.pastebin,
                badge = projectBadge(command.project) { cached },
            )
        }
        return SetDetail(setName, rows, setLimit)
    }

    /**
     * 指令集详情的文字版：图片渲染或上传失败时使用
     */
    fun formatSetDetailText(detail: SetDetail): String = buildString {
        appendLine("📦 指令集「${detail.name}」（${detail.commands.size}/${detail.setLimit} 条）")
        append(detail.commands.joinToString("\n") { row ->
            buildString {
                append("${row.index}. ${row.project}")
                if (row.input.isNotEmpty()) append("：${inputPreview(row.input)}")
            }
        })
    }

    /**
     * 卡片的文字版：图片渲染或上传失败时使用
     */
    fun formatCardText(card: Card): String = buildString {
        appendLine("⭐ 收藏（${card.favorites.size}/$MAX_FAVORITES）")
        if (card.favorites.isEmpty()) appendLine("暂无收藏")
        for (row in card.favorites) {
            append("${row.slot}. ${row.project}")
            row.alias?.let { append("（$it）") }
            appendLine()
        }
        appendLine("📦 指令集（${card.sets.size}/$MAX_SETS）")
        if (card.sets.isEmpty()) append("暂无指令集")
        append(card.sets.joinToString("\n") { set ->
            "· ${set.name}（${set.projects.size}/${card.setLimit} 条）：${mergedProjectText(set.projects)}"
        })
    }
}
