package site.tiedan.command.pastebin

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.message.data.*
import site.tiedan.MiraiCompilerFramework.parseUserID
import site.tiedan.MiraiCompilerFramework.requestUserConfirmation
import site.tiedan.MiraiCompilerFramework.save
import site.tiedan.MiraiCompilerFramework.sendQuoteReply
import site.tiedan.core.StorageManager
import site.tiedan.data.PastebinData

/*
 * # 跨分类指令共用
 *
 * @author tiedanGH
 */

/**
 * pb 指令调用上下文
 * - [storageLock] 由需要持锁的指令写入，调度方在调用结束时统一释放
 */
class PbContext(
    val args: MessageChain,
    val platform: String,
    val userID: String,
    val numID: Long,
    val isAdmin: Boolean,
) {
    /** 本次指令持有的存储锁，与执行进程共用同一套锁 */
    var storageLock: StorageManager.StorageLock? = null
}

/**
 * 属性变更类操作一律要求项目真名
 * @return true 表示输入的是别名，中止本次操作
 */
internal suspend fun CommandSender.rejectAlias(name: String, action: String): Boolean {
    val real = PastebinData.alias[name] ?: return false
    sendQuoteReply("${action}不支持别名，请使用项目完整名称：$real")
    return true
}

/**
 * 协作者判定与批量编辑
 */
object PbCollaborator {

    fun isCollaborator(name: String, userID: String): Boolean {
        val raw = PastebinData.pastebin[name]?.get("collaborators") ?: return false
        return raw.containsCollaborator(userID)
    }

    fun String.containsCollaborator(userID: String): Boolean {
        return this.parseCollaborators().contains(userID)
    }

    private fun String.parseCollaborators(): List<String> {
        return this.split(",", "，", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    internal fun normalizeCollaborators(ownerID: String, collaborators: List<String>): String? {
        val normalized = collaborators
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { parseUserID(it) != null }
            .filter { it != ownerID }
            .distinct()
        return normalized.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    // 批量添加/移除协作者
    internal suspend fun CommandSender.handleBatchCollaboratorAction(
        action: String,
        targetPlatformID: String,
        userID: String,
        commandContent: String
    ) {
        if (parseUserID(targetPlatformID) == null) {
            sendQuoteReply("操作失败：协作者ID格式错误，应为纯数字或带平台前缀 kook_123")
            return
        }
        if (targetPlatformID == userID) {
            sendQuoteReply("操作失败：项目所有者无需将自己添加/移除协作者")
            return
        }

        val ownedProjects = PastebinData.pastebin.filterValues { it["userID"] == userID }
        if (ownedProjects.isEmpty()) {
            sendQuoteReply("操作失败：您没有创建过任何项目，无法执行批量协作者操作")
            return
        }

        val affectedProjects = ownedProjects
            .filter { (_, data) ->
                val collaborators = data["collaborators"]?.parseCollaborators().orEmpty()
                when (action) {
                    "add" -> targetPlatformID !in collaborators
                    "remove" -> targetPlatformID in collaborators
                    else -> false
                }
            }
            .keys
            .sorted()

        if (affectedProjects.isEmpty()) {
            val tip = when (action) {
                "add" -> "没有可新增的项目：该ID已经是您所有项目的协作者"
                "remove" -> "没有可移除的项目：该ID当前不是您任何项目的协作者"
                else -> "错误：不支持的操作"
            }
            sendQuoteReply(tip)
            return
        }

        val actionText = if (action == "add") "批量添加协作者" else "批量移除协作者"

        requestUserConfirmation(
            userID, commandContent,
            " +++⚠️ 批量修改确认 ⚠️+++\n" +
            "操作：$actionText\n" +
            "目标ID：$targetPlatformID\n" +
            "受影响项目（${affectedProjects.size}个）：\n" +
            affectedProjects.joinToString("、", postfix = "\n") +
            "\n" +
            "如确认无误，请再次执行相同指令以完成操作"
        ) ?: return

        affectedProjects.forEach { projectName ->
            val project = PastebinData.pastebin[projectName] ?: return@forEach
            val ownerID = project["userID"] ?: return@forEach
            val collaborators = project["collaborators"]?.parseCollaborators()?.toMutableList() ?: mutableListOf()

            when (action) {
                "add" -> collaborators.add(targetPlatformID)
                "remove" -> collaborators.removeAll { it == targetPlatformID }
            }

            val normalized = normalizeCollaborators(ownerID, collaborators)
            if (normalized == null) {
                project.remove("collaborators")
            } else {
                project["collaborators"] = normalized
            }
        }

        PastebinData.save()

        sendQuoteReply(
            buildString {
                appendLine("✅ 批量协作者操作完成")
                appendLine("操作：$actionText")
                appendLine("目标ID：$targetPlatformID")
                append("已修改项目：${affectedProjects.size} 个")
            }
        )
    }
}
