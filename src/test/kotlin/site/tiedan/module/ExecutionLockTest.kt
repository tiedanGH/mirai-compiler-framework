package site.tiedan.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import site.tiedan.module.ExecutionLock.Mode

/**
 * 执行锁定：各锁定范围的拦截与豁免、管理员锁定的输入解析
 */
class ExecutionLockTest {

    private val scenes = listOf(true, false)

    @Test
    @DisplayName("非管理员输入管理员锁定时与无法识别的输入一致")
    fun adminModeHiddenFromUsers() {
        assertEquals(Mode.ADMIN, ExecutionLock.parse("admin", isAdmin = true))
        assertEquals(Mode.ADMIN, ExecutionLock.parse(" 管理 ", isAdmin = true))
        assertNull(ExecutionLock.parse("admin", isAdmin = false))
        assertNull(ExecutionLock.parse("管理", isAdmin = false))
        assertNull(ExecutionLock.parse("unknown", isAdmin = true))
        assertEquals(Mode.ALL, ExecutionLock.parse("ALL", isAdmin = false))
    }

    @Test
    @DisplayName("管理员锁定：作者与其他用户在任何场景都被拦截，管理员不受限制")
    fun adminLockBlocksOwner() {
        for (inGroup in scenes) {
            assertTrue(Mode.ADMIN.blocks(inGroup, isOwner = false, isAdmin = false))
            assertTrue(Mode.ADMIN.blocks(inGroup, isOwner = true, isAdmin = false))
            assertFalse(Mode.ADMIN.blocks(inGroup, isOwner = false, isAdmin = true))
        }
    }

    @Test
    @DisplayName("作者设置的锁定只拦截其他用户的对应场景，作者与管理员不受限制")
    fun ownerLocksExemptOwnerAndAdmin() {
        assertTrue(Mode.GROUP.blocks(inGroup = true, isOwner = false, isAdmin = false))
        assertFalse(Mode.GROUP.blocks(inGroup = false, isOwner = false, isAdmin = false))
        assertTrue(Mode.PRIVATE.blocks(inGroup = false, isOwner = false, isAdmin = false))
        assertFalse(Mode.PRIVATE.blocks(inGroup = true, isOwner = false, isAdmin = false))
        for (mode in listOf(Mode.PRIVATE, Mode.GROUP, Mode.ALL)) {
            for (inGroup in scenes) {
                assertFalse(mode.blocks(inGroup, isOwner = true, isAdmin = false), "$mode 不应拦截作者")
                assertFalse(mode.blocks(inGroup, isOwner = false, isAdmin = true), "$mode 不应拦截管理员")
            }
        }
        for (inGroup in scenes) assertTrue(Mode.ALL.blocks(inGroup, isOwner = false, isAdmin = false))
    }
}
