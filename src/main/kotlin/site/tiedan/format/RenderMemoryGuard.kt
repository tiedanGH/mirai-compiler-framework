package site.tiedan.format

import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.config.SystemConfig
import site.tiedan.utils.ProcessMemory
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * # 渲染进程内存守护
 * - 统一监测整机内存，持续超出 [SystemConfig.memoryLimit] 时终止占用最高的渲染进程
 * - 每轮只终止一个，下一轮重新采样，仍超限则继续终止次高者，回到限制以内即停止
 * - 每个进程在超限状态下至少可运行 [GRACE_MILLIS]，超限期间新启动的进程同样享有完整宽限期
 * - 读取不到内存占用的进程排在最后，其中运行最久者优先
 *
 * @author tiedanGH
 */
object RenderMemoryGuard {

    /** 采样间隔 */
    private const val TICK_MILLIS = 1_000L

    /** 持续超限多久后开始终止 */
    internal const val GRACE_MILLIS = 5_000L

    private const val MB = 1024L * 1024

    // 操作系统相关信息（仅用于监测内存用量）
    private val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    /**
     * 受监测的渲染进程
     * @param name 项目名称，内部渲染（列表、帮助等）为 null
     */
    class Watched internal constructor(
        internal val handle: ProcessHandle,
        internal val name: String?,
        internal val startedAt: Long = System.currentTimeMillis(),
    ) {
        /** 是否已被内存守护终止 */
        @Volatile
        var killed = false
            private set

        /** 被终止时的内存占用（字节），读取失败为 null */
        var usageAtKill: Long? = null
            private set

        internal val label: String get() = "${name ?: "内部渲染"}(PID ${handle.pid()})"

        internal fun kill(usage: Long?) {
            usageAtKill = usage
            killed = true   // 先写标记再终止
            handle.destroyForcibly()
        }
    }

    private val watching = ConcurrentHashMap.newKeySet<Watched>()
    private var monitor: Job? = null

    /** 本轮持续超限的起始时刻，未超限时为 null（仅监测协程读写） */
    private var overLimitSince: Long? = null

    /**
     * 将渲染进程纳入监测，进程退出后自动移出
     */
    fun watch(process: Process, name: String?): Watched {
        val watched = Watched(process.toHandle(), name)
        synchronized(this) {
            watching.add(watched)
            // 监测协程意外结束时 monitor 不会被清空，按存活状态判断以便重新拉起
            if (monitor?.isActive != true) monitor = CoroutineScope(Dispatchers.IO).launch { monitorLoop() }
        }
        process.onExit().thenRun { watching.remove(watched) }
        return watched
    }

    private suspend fun monitorLoop() {
        while (true) {
            delay(TICK_MILLIS)
            synchronized(this) {
                if (watching.isEmpty()) {
                    monitor = null
                    overLimitSince = null
                    return
                }
            }
            // 内存紧张时可能出现 OutOfMemoryError，守护本身不能因此停止
            try {
                check()
            } catch (e: Throwable) {
                logger.warning("渲染进程内存监测异常", e)
            }
        }
    }

    private fun check() {
        val usage = systemUsage()
        if (usage <= SystemConfig.memoryLimit * MB) {
            overLimitSince = null
            return
        }
        val now = System.currentTimeMillis()
        val since = overLimitSince ?: now.also { overLimitSince = it }
        val candidates = eligible(watching, since, now)
        if (candidates.isEmpty()) return

        val usages = candidates.mapNotNull { w -> ProcessMemory.usageOf(w.handle.pid())?.let { w to it } }.toMap()
        val victim = pickVictim(candidates, usages) ?: return
        val victimUsage = usages[victim]
        victim.kill(victimUsage)

        logger.warning(buildString {
            append("系统总内存 ${usage / MB}MB 超出安全限制 ${SystemConfig.memoryLimit}MB 已达 ${(now - since) / 1000} 秒，")
            if (victimUsage != null) append("终止占用最高的渲染进程 ${victim.label}：${victimUsage / MB}MB")
            else append("无法读取进程内存，终止运行最久的渲染进程 ${victim.label}")
            val others = usages.filterKeys { it !== victim }
            if (others.isNotEmpty()) {
                append("；其余候选：")
                append(others.entries.joinToString("、") { (w, u) -> "${w.label} ${u / MB}MB" })
            }
        })
    }

    /** 整机内存用量：物理内存 + 交换区 */
    private fun systemUsage(): Long {
        val physicalUsage = osBean.totalMemorySize - osBean.freeMemorySize
        val swapUsage = osBean.totalSwapSpaceSize - osBean.freeSwapSpaceSize
        return physicalUsage + swapUsage
    }

    /**
     * 可终止的进程：尚未被终止、仍在运行，且在本轮超限中已度过宽限期
     */
    internal fun eligible(watched: Collection<Watched>, overLimitSince: Long, now: Long): List<Watched> =
        watched.filter { !it.killed && it.handle.isAlive && now - maxOf(overLimitSince, it.startedAt) >= GRACE_MILLIS }

    /**
     * 终止顺序：占用从高到低；读取不到占用的排在最后，其中运行最久者优先
     */
    internal fun pickVictim(candidates: Collection<Watched>, usages: Map<Watched, Long>): Watched? =
        candidates.maxWithOrNull(compareBy<Watched> { usages[it] ?: -1L }.thenByDescending { it.startedAt })
}
