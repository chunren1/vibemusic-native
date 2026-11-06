package com.cyk666.vibemusic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 注册表单真值表：用户名白名单 / 密码长度 / 邀请码必填 / register-config 解析。
 * 口径必须与后端逐条一致（见任务说明里的服务端契约），漂移了这里先红。
 */
@RunWith(RobolectricTestRunner::class)
class RegisterRulesTest {

    @Test
    fun submitError_acceptsValidForm() {
        assertNull(RegisterRules.submitError("bob_1", "password123", "", false))
        assertNull(RegisterRules.submitError("bob_1", "password123", "INVITE", true))
        // 用户名前后空格按 trim 后判（提交时也 trim）。
        assertNull(RegisterRules.submitError("  bob_1  ", "password123", "", false))
    }

    @Test
    fun submitError_rejectsUsernameOutsideBackendPattern() {
        val bad = listOf("ab", "a".repeat(31), "bob-1", "bob.1", "中文名字", "bob 1")
        for (name in bad) {
            assertEquals("username=$name", RegisterRules.USERNAME_HINT, RegisterRules.submitError(name, "password123", "", false))
        }
    }

    @Test
    fun submitError_rejectsShortPassword() {
        assertEquals(RegisterRules.PASSWORD_HINT, RegisterRules.submitError("bob", "1234567", "", false))
        // 7 个字符 + 空格 = 8，空格也是合法密码字符（不 trim 密码）。
        assertNull(RegisterRules.submitError("bob", "1234567 ", "", false))
    }

    @Test
    fun submitError_requiresInviteCodeOnlyWhenServerSaysSo() {
        // 服务端未启用：空邀请码照样可提交。
        assertNull(RegisterRules.submitError("bob", "password123", "", false))
        // 服务端启用：空白（含纯空格）一律拦下。
        assertEquals(RegisterRules.INVITE_HINT, RegisterRules.submitError("bob", "password123", "", true))
        assertEquals(RegisterRules.INVITE_HINT, RegisterRules.submitError("bob", "password123", "   ", true))
    }

    @Test
    fun submitError_reportsBlankFieldsFirst() {
        assertEquals("请输入用户名和密码", RegisterRules.submitError("", "password123", "", false))
        assertEquals("请输入用户名和密码", RegisterRules.submitError("bob", "", "", true))
    }

    @Test
    fun parseConfig_readsInviteFlag() {
        assertTrue(RegisterRules.parseConfig("""{"code":200,"data":{"inviteCodeRequired":true}}"""))
        assertFalse(RegisterRules.parseConfig("""{"code":200,"data":{"inviteCodeRequired":false}}"""))
        // 缺字段/缺 data/非 JSON 都回落 false，绝不抛。
        assertFalse(RegisterRules.parseConfig("""{"code":200,"data":{}}"""))
        assertFalse(RegisterRules.parseConfig("""{"code":200,"data":null}"""))
        assertFalse(RegisterRules.parseConfig("not json"))
    }

    @Test
    fun fetchConfig_networkFailureFallsBackToFalse() = runBlocking {
        assertFalse(RegisterRules.fetchConfig { throw RuntimeException("boom") })
    }

    @Test
    fun fetchConfig_returnsServerValue() = runBlocking {
        assertTrue(RegisterRules.fetchConfig { """{"code":200,"data":{"inviteCodeRequired":true}}""" })
    }
}
