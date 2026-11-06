package com.cyk666.vibemusic

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * 注册表单口径与 `GET /api/auth/register-config` 的解析。
 *
 * 为什么单独一个纯函数文件：这些规则（用户名正则、密码长度、邀请码必填）是**与后端
 * 共用的契约**，抄两遍迟早漂移；写成纯函数才能用真值表逐条钉住，不必假装有能力
 * 在单测里跑服务端。校验文案也在这里，UI 只负责显示。
 */
object RegisterRules {

    /** 与后端 `@Pattern` 完全同一条：字母/数字/下划线，3-30 位。 */
    private val USERNAME = Regex("^[A-Za-z0-9_]{3,30}$")

    const val USERNAME_HINT = "用户名只能包含字母、数字、下划线，3-30 位"
    const val PASSWORD_HINT = "密码至少8位"
    const val INVITE_HINT = "请输入邀请码"

    /**
     * 提交前校验：返回第一条错误文案，null = 可以发请求。
     *
     * 密码不 trim（空格是合法密码字符）；用户名按 trim 后判，与提交时一致。
     */
    fun submitError(
        username: String,
        password: String,
        inviteCode: String,
        inviteCodeRequired: Boolean
    ): String? {
        val name = username.trim()
        if (name.isEmpty() || password.isEmpty()) return "请输入用户名和密码"
        if (!USERNAME.matches(name)) return USERNAME_HINT
        if (password.length < 8) return PASSWORD_HINT
        if (inviteCodeRequired && inviteCode.isBlank()) return INVITE_HINT
        return null
    }

    /** Pure：`GET /api/auth/register-config` 的 `data.inviteCodeRequired`，畸形/缺省回落 false。 */
    fun parseConfig(json: String): Boolean = try {
        JSONObject(json).optJSONObject("data")?.optBoolean("inviteCodeRequired", false) ?: false
    } catch (_: Exception) {
        SilentFail.note("RegisterRules.parseConfig")
        false
    }

    /**
     * 拉配置：任何网络/解析失败都回落 false（“不需要邀请码”），绝不把异常抛给 UI。
     * 服务端未启用邀请码时本来就是这个值；真的启用而这次拉失败，注册会被服务端
     * 拒绝并给出提示，比进注册页就崩掉强。
     */
    suspend fun fetchConfig(get: suspend () -> String): Boolean = try {
        parseConfig(get())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        SilentFail.note("RegisterRules.fetchConfig")
        false
    }
}
