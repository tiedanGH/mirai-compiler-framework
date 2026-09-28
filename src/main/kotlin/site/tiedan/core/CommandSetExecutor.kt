package site.tiedan.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.commandPrefix
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.contact.Group
import site.tiedan.MiraiCompilerFramework.THREADS
import site.tiedan.MiraiCompilerFramework.ThreadInfo
import site.tiedan.MiraiCompilerFramework.getPlatform
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.MiraiCompilerFramework.newJobId
import site.tiedan.MiraiCompilerFramework.parseUserID
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.MiraiCompilerFramework.tryRegisterThreads
import site.tiedan.MiraiCompilerFramework.userThreadLimit
import site.tiedan.core.PastebinCodeExecutor.BlockReason
import site.tiedan.core.PastebinCodeExecutor.blockReason
import site.tiedan.core.PastebinCodeExecutor.executeMainProcess
import site.tiedan.data.ExtraData
import site.tiedan.data.PastebinData
import site.tiedan.data.dao.FavoriteDao
import site.tiedan.module.ExecutionLock
import site.tiedan.module.FavoriteManager
import site.tiedan.module.RequestLimiter

/**
 * # 指令集执行
 * - 整批预检：条数上限、逐条可执行性、进程余量、请求频率，任一不满足整批拒绝，不执行任何指令
 * - 预检通过后一次性登记全部进程，按指令集顺序启动，不同项目同时执行，每条执行完即退还自己的进程
 * - 同名项目、共享存储库的存储项目归入同一队列，队列内按指令集顺序依次执行，先后次序不会错乱
 *
 * @author tiedanGH
 */
object CommandSetExecutor {

    /** 执行指令集时附带了输入 */
    const val NO_EXTRA_INPUT = "执行失败：指令集不支持附加输入，每条指令使用保存时的输入"

    /**
     * 执行单个收藏的项目，项目已被删除时提示
     */
    suspend fun CommandSender.executeFavorite(favorite: FavoriteDao.Favorite, userInput: String, imageUrls: List<String>) {
        if (favorite.project !in PastebinData.pastebin) {
            sendQuoteReply("项目 ${favorite.project} 已被删除，请使用「${commandPrefix}f rm ${favorite.slot}」移除收藏")
            return
        }
        executeMainProcess(favorite.project, userInput, imageUrls)
    }

    /**
     * 执行整个指令集
     * @param commands 指令集中的全部指令
     */
    suspend fun CommandSender.executeCommandSet(userID: String, setName: String, commands: List<FavoriteDao.Command>) {
        if (ExtraData.BlackList.contains(userID)) {
            logger.warning("$userID 已被拉黑，请求被拒绝")
            return
        }
        parseUserID(userID) ?: return sendQuoteReply("[ID解析失败] 无法解析您的用户ID，请联系管理员")

        // 上限可能被调低，此前建好的指令集需要先精简
        val limit = userThreadLimit
        if (commands.size > limit) {
            sendQuoteReply("执行失败：指令集「$setName」共 ${commands.size} 条指令，超出单用户进程上限 $limit 条，请先移除部分指令")
            return
        }

        val problems = checkCommands(userID, commands, subject is Group)
        if (problems.isNotEmpty()) {
            sendQuoteReply("执行失败：指令集「$setName」中以下指令当前无法执行\n" + problems.joinToString("\n"))
            return
        }

        val nickname = this.name
        val from = if (subject is Group) "${(subject as Group).name}(${(subject as Group).id})" else "private"
        val platform = getPlatform()
        val slots = commands.map {
            ThreadInfo(newJobId(it.project, nickname, userID), it.project, nickname, userID, from, platform)
        }
        tryRegisterThreads(slots)?.let { quota ->
            sendQuoteReply(
                "执行失败：此指令集需预留 ${slots.size} 个进程，当前可用进程不足，请稍后再试\n" +
                "全局进程 ${quota.total}/${quota.totalLimit}，您的进程 ${quota.user}/${quota.userLimit}"
            )
            return
        }
        try {
            val admission = RequestLimiter.admitBatch(userID, slots.size)
            if (!admission.admitted) {
                sendQuoteReply(admission.message)
                return
            }
            val warning = admission.message.takeIf { it.isNotEmpty() }?.let { "\n\n$it" }.orEmpty()
            val projects = FavoriteManager.mergedProjectText(commands.map { it.project })
            sendQuoteReply("▶️ 执行指令集「$setName」（${slots.size} 条）：$projects$warning")
            // 同一队列内按指令集顺序依次执行，不同队列按顺序启动并同时执行
            val queues = queueGroups(commands.map { it.project }) { lockKeys(it) }
            coroutineScope {
                for (queue in queues) {
                    // 执行内部有阻塞的网络请求，放到 IO 线程池
                    launch(Dispatchers.IO) {
                        for (index in queue) {
                            val command = commands[index]
                            val slot = slots[index]
                            try {
                                executeMainProcess(command.project, command.input, emptyList(), slot)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                // 单条异常不影响其他指令
                                logger.warning("指令集「$setName」中的 ${command.project} 执行异常", e)
                            } finally {
                                THREADS.removeIf { it.id == slot.id }
                            }
                        }
                    }
                }
            }
        } finally {
            // 频率拒绝、发送消息失败等提前结束需退还进程，按 ID 删除
            val ids = slots.mapTo(HashSet()) { it.id }
            THREADS.removeIf { it.id in ids }
        }
    }

    /**
     * 按执行时争用的锁划分队列：锁有交集的指令归入同一队列（可传递）
     * @param lockKeys 项目执行时会占用的锁
     * @return 各队列包含的指令下标，队列按首条指令的先后排列，队列内保持指令集顺序
     */
    internal fun queueGroups(projects: List<String>, lockKeys: (String) -> Set<String>): List<List<Int>> {
        val keysOf = projects.distinct().associateWith(lockKeys)
        val parent = IntArray(projects.size) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x]
            return x
        }
        for (i in projects.indices) {
            for (j in 0 until i) {
                if (keysOf.getValue(projects[i]).any { it in keysOf.getValue(projects[j]) }) {
                    parent[root(i)] = root(j)
                }
            }
        }
        return projects.indices.groupBy { root(it) }.values.toList()
    }

    /**
     * 项目执行时会占用的锁：项目自身；开启存储的项目还会锁定关联的全部存储库
     */
    private fun lockKeys(name: String): Set<String> = buildSet {
        add("project:$name")
        if (PastebinData.pastebin[name]?.get("storage") == "true") {
            StorageManager.linkedBucketIds(name).forEach { add("bucket:$it") }
        }
    }

    /**
     * 逐条检查指令当前能否执行
     * @return 无法执行的指令及原因
     */
    private fun checkCommands(userID: String, commands: List<FavoriteDao.Command>, inGroup: Boolean): List<String> {
        val cached by lazy { FavoriteManager.cachedProjects() }
        return commands.mapIndexedNotNull { index, command ->
            val name = command.project
            val reason = if (name !in PastebinData.pastebin) {
                "项目已被删除"
            } else when (blockReason(name, userID, inGroup)) {
                BlockReason.LOCKED -> ExecutionLock.of(name)?.desc ?: "🔒 已锁定"
                BlockReason.CENSORED -> "审核中，暂时无法执行"
                null -> "源代码网站已停服且无代码缓存".takeIf { FavoriteManager.isDiscontinuedWithoutCache(name) { cached } }
            }
            reason?.let { "${index + 1}. $name：$it" }
        }
    }
}
