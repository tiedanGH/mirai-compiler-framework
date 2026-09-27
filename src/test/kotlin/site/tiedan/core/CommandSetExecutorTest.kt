package site.tiedan.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 指令集执行队列：锁有交集的指令排队，其余同时执行
 */
class CommandSetExecutorTest {

    private fun groups(projects: List<String>, keys: Map<String, Set<String>>) =
        CommandSetExecutor.queueGroups(projects) { keys.getValue(it) }

    @Test
    @DisplayName("同名项目归入同一队列，队列内保持指令集顺序")
    fun sameNameQueues() {
        val keys = mapOf("echo" to setOf("project:echo"), "ls" to setOf("project:ls"), "cat" to setOf("project:cat"))
        assertEquals(listOf(listOf(0, 2, 4), listOf(1), listOf(3)), groups(listOf("echo", "ls", "echo", "cat", "echo"), keys))
    }

    @Test
    @DisplayName("不同项目各自成队，队列按首条指令的先后排列")
    fun distinctProjectsRunTogether() {
        val keys = mapOf("a" to setOf("project:a"), "b" to setOf("project:b"), "c" to setOf("project:c"))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), groups(listOf("a", "b", "c"), keys))
    }

    @Test
    @DisplayName("共享存储库的项目归入同一队列，关联可传递")
    fun sharedBucketsQueueTransitively() {
        val keys = mapOf(
            "A" to setOf("project:A", "bucket:1"),
            "B" to setOf("project:B", "bucket:1", "bucket:2"),
            "C" to setOf("project:C", "bucket:2"),
            "D" to setOf("project:D"),
        )
        assertEquals(listOf(listOf(0, 2, 3), listOf(1)), groups(listOf("A", "D", "C", "B"), keys))
    }
}
