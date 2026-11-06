package com.cyk666.vibemusic

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cyk666.vibemusic.security.AesGcmTokenCipher
import com.cyk666.vibemusic.security.TokenCipherRegistry
import com.cyk666.vibemusic.security.TokenKeyProvider
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * AuthStore × TokenCipher 的集成用例。
 *
 * 用 JVM 生成的 AES 密钥注入 [AesGcmTokenCipher]：加解密是真的 AES/GCM，只有
 * “密钥从哪来”被换掉（Robolectric 没有 AndroidKeyStore provider）。生产路径
 * 仍是 KeystoreTokenKeyProvider，见 TokenCipher.kt。
 */
@RunWith(RobolectricTestRunner::class)
class AuthStoreCipherTest {

    private val key: SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun cipherWith(k: SecretKey?): AesGcmTokenCipher =
        AesGcmTokenCipher(TokenKeyProvider { k })

    @Before
    fun setUp() {
        SilentFail.resetForTest()
        AuthToken.sessionEnded = false
        TokenCipherRegistry.installForTest(cipherWith(key))
    }

    @After
    fun tearDown() {
        TokenCipherRegistry.installForTest(null)
    }

    private fun context(): Context = RuntimeEnvironment.getApplication()

    /**
     * 直接读 DataStore 原始值（不解密），用来断言“盘上到底存了什么”。
     * 与 QueueStoreTest 同一套反射办法拿 file-private 的 DataStore 实例。
     */
    @Suppress("UNCHECKED_CAST")
    private fun raw(context: Context): Preferences = runBlocking {
        val getter = Class.forName("com.cyk666.vibemusic.AuthStoreKt")
            .getDeclaredMethod("getAuthDataStore", Context::class.java)
        getter.isAccessible = true
        val store = getter.invoke(null, context) as DataStore<Preferences>
        store.data.first()
    }

    private val tokenKey = stringPreferencesKey("token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val usernameKey = stringPreferencesKey("username")

    @Test
    fun save_writesV1Ciphertext_andLoadReturnsPlaintext(): Unit = runBlocking {
        AuthStore.save(context(), "tok-123", "alice", "Alice", "ref-123")

        val rawToken = raw(context())[tokenKey].orEmpty()
        val rawRefresh = raw(context())[refreshKey].orEmpty()
        assertTrue("token 必须落盘为 v1: 密文，found=$rawToken", rawToken.startsWith("v1:"))
        assertTrue("refresh_token 必须落盘为 v1: 密文，found=$rawRefresh", rawRefresh.startsWith("v1:"))
        // 明文绝不应出现在盘上。
        assertFalse(rawToken.contains("tok-123"))
        assertFalse(rawRefresh.contains("ref-123"))
        // username 保持明文（只有 token 类字段加密）。
        assertEquals("alice", raw(context())[usernameKey])

        val snap = AuthStore.load(context())
        assertEquals("tok-123", snap.token)
        assertEquals("ref-123", snap.refreshToken)
        assertEquals("tok-123", AuthToken.token)
        assertEquals("ref-123", AuthToken.refreshToken)
    }

    @Test
    fun legacyPlaintext_isReadable_andMigratesOnNextWrite(): Unit = runBlocking {
        // 模拟升级前的落盘：直接写明文。
        rawStore(context()).edit { p ->
            p[tokenKey] = "legacy-token"
            p[refreshKey] = "legacy-refresh"
        }

        assertEquals("legacy-token", AuthStore.load(context()).token)
        assertEquals("legacy-refresh", AuthStore.load(context()).refreshToken)

        AuthStore.saveTokens(context(), "legacy-token", "legacy-refresh")

        assertTrue(raw(context())[tokenKey].orEmpty().startsWith("v1:"))
        assertEquals("legacy-token", AuthStore.load(context()).token)
    }

    @Test
    fun applyRefreshedTokens_writesCiphertext(): Unit = runBlocking {
        AuthStore.save(context(), "acc1", "alice", "Alice", "ref1")

        AuthStore.saveTokens(context(), "acc2", "ref2")

        assertTrue(raw(context())[tokenKey].orEmpty().startsWith("v1:"))
        assertEquals("acc2", AuthStore.load(context()).token)
        assertEquals("ref2", AuthStore.load(context()).refreshToken)
    }

    /** 密钥被清 / 换机恢复备份 → 解不开只能空串 + SilentFail，绝不崩。 */
    @Test
    fun undecryptableCiphertext_returnsEmptyWithoutThrowing(): Unit = runBlocking {
        AuthStore.save(context(), "tok-123", "alice", "Alice", "ref-123")

        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        TokenCipherRegistry.installForTest(cipherWith(otherKey))

        val snap = AuthStore.load(context())
        assertEquals("", snap.token)
        assertEquals("", snap.refreshToken)
        assertEquals("", AuthToken.token)
        assertTrue(
            "解密失败必须留诊断点，found=${SilentFail.summary()}",
            SilentFail.summary().contains("TokenCipher.decrypt")
        )
        // 账号资料是明文，仍然可用（至少能显示“请重新登录”的正确身份）。
        assertEquals("alice", snap.username)
    }

    @Test
    fun clear_removesCiphertext(): Unit = runBlocking {
        AuthStore.save(context(), "tok-123", "alice", "Alice", "ref-123")

        AuthStore.clear(context())

        val p = raw(context())
        assertFalse(p.contains(tokenKey))
        assertFalse(p.contains(refreshKey))
        assertEquals("", AuthStore.load(context()).token)
    }

    @Suppress("UNCHECKED_CAST")
    private fun rawStore(context: Context): DataStore<Preferences> {
        val getter = Class.forName("com.cyk666.vibemusic.AuthStoreKt")
            .getDeclaredMethod("getAuthDataStore", Context::class.java)
        getter.isAccessible = true
        return getter.invoke(null, context) as DataStore<Preferences>
    }
}
