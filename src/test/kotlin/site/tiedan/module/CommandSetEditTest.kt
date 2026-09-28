package site.tiedan.module

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import site.tiedan.data.Database
import site.tiedan.module.FavoriteManager.SetEditResult

/**
 * 指令集编辑：原位修改与移动指令，id 与指令集在列表中的位置均不变
 */
class CommandSetEditTest {

    private val user = "1"

    @BeforeEach
    fun setUp() {
        Database.close()
        Database.initializeInMemory()
    }

    @AfterEach
    fun tearDown() {
        Database.close()
    }

    private fun add(setName: String, vararg projects: String) =
        projects.forEach { FavoriteManager.addCommand(user, setName, it, "输入$it", perSetLimit = 8) }

    private fun contents(setName: String) = FavoriteManager.commandSet(user, setName).map { it.project to it.input }

    private fun ids(setName: String) = FavoriteManager.commandSet(user, setName).map { it.id }

    @Test
    @DisplayName("修改：原位覆盖项目与输入，其余指令与先后顺序不变")
    fun editInPlace() {
        add("早安", "a", "b", "c")
        val ids = ids("早安")

        val result = FavoriteManager.editCommand(user, "早安", 2, "x", "第一行\n第二行")
        assertEquals("b", (result as SetEditResult.Edited).before.project)
        assertEquals(listOf("a" to "输入a", "x" to "第一行\n第二行", "c" to "输入c"), contents("早安"))
        assertEquals(ids, ids("早安"))
    }

    @Test
    @DisplayName("移动：其间的指令依次顺延，输入随指令移动，id 与指令集位置不变")
    fun moveKeepsIdsAndSetOrder() {
        add("早安", "a", "b", "c", "d")
        add("晚安", "x")
        val ids = ids("早安")

        val result = FavoriteManager.moveCommand(user, "早安", 4, 1)
        assertEquals("d", (result as SetEditResult.Edited).before.project)
        assertEquals(listOf("d", "a", "b", "c"), contents("早安").map { it.first })

        FavoriteManager.moveCommand(user, "早安", 1, 3)
        assertEquals(listOf("a" to "输入a", "b" to "输入b", "d" to "输入d", "c" to "输入c"), contents("早安"))

        FavoriteManager.moveCommand(user, "早安", 2, 2)
        assertEquals(listOf("a", "b", "d", "c"), contents("早安").map { it.first }, "原位移动不改变顺序")

        assertEquals(ids, ids("早安"))
        assertEquals(listOf("早安", "晚安"), FavoriteManager.setNames(user))
    }

    @Test
    @DisplayName("序号超出范围、指令集不存在或不属于自己时不做任何改动")
    fun rejectsInvalidTargets() {
        add("早安", "a", "b")

        assertEquals(SetEditResult.OutOfRange(3, 2), FavoriteManager.moveCommand(user, "早安", 1, 3))
        assertEquals(SetEditResult.OutOfRange(0, 2), FavoriteManager.moveCommand(user, "早安", 0, 1))
        assertEquals(SetEditResult.OutOfRange(5, 2), FavoriteManager.editCommand(user, "早安", 5, "x", ""))
        assertEquals(SetEditResult.NotFound, FavoriteManager.moveCommand(user, "不存在", 1, 2))
        assertEquals(SetEditResult.NotFound, FavoriteManager.editCommand("2", "早安", 1, "x", ""), "不能修改别人的指令集")

        assertEquals(listOf("a" to "输入a", "b" to "输入b"), contents("早安"))
    }
}
