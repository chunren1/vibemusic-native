package com.cyk666.vibemusic

import com.cyk666.vibemusic.security.AesGcmTokenCipher
import com.cyk666.vibemusic.security.TokenKeyProvider
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 的加密用例：AesGcmTokenCipher 只依赖 javax.crypto + java.util.Base64，
 * 用 JVM 自己生成的 AES 密钥就能跑真加解密（不必依赖 Robolectric 没有的
 * AndroidKeyStore）。Keystore 侧只剩“取密钥”一件事，见 [AuthStoreCipherTest] 的注入。
 */
class TokenCipherTest {

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun cipherWith(key: SecretKey?): AesGcmTokenCipher =
        AesGcmTokenCipher(TokenKeyProvider { key })

    @Test
    fun encryptThenDecrypt_roundTrips() {
        val cipher = cipherWith(newKey())
        val token = "eyJhbGciOiJIUzI1NiJ9.payload.signature"
        assertEquals(token, cipher.decrypt(cipher.encrypt(token)))
    }

    @Test
    fun encrypt_usesV1PrefixAndFreshIvEachTime() {
        val cipher = cipherWith(newKey())
        val first = cipher.encrypt("same-token")
        val second = cipher.encrypt("same-token")
        assertTrue("密文必须带 v1: 前缀，found=$first", first.startsWith(AesGcmTokenCipher.PREFIX))
        // 随机 IV：同一明文两次加密结果必须不同，否则等于 ECB。
        assertNotEquals(first, second)
        assertEquals("same-token", cipher.decrypt(first))
        assertEquals("same-token", cipher.decrypt(second))
    }

    @Test
    fun encrypt_blankStaysBlank() {
        val cipher = cipherWith(newKey())
        assertEquals("", cipher.encrypt(""))
        assertEquals("", cipher.decrypt(""))
    }

    /** 升级前落盘的是明文：没有前缀就必须原值返回（迁移兼容）。 */
    @Test
    fun decrypt_legacyPlaintextPassesThrough() {
        val cipher = cipherWith(newKey())
        assertEquals("legacy-token-123", cipher.decrypt("legacy-token-123"))
        assertEquals("legacy-refresh", cipher.decrypt("legacy-refresh"))
    }

    /** 换机恢复备份/密钥被清：GCM tag 不匹配 → 空串，绝不抛。 */
    @Test
    fun decrypt_withWrongKeyReturnsEmptyWithoutThrowing() {
        val stored = cipherWith(newKey()).encrypt("secret-token")
        assertEquals("", cipherWith(newKey()).decrypt(stored))
    }

    @Test
    fun decrypt_garbageOrTruncatedReturnsEmpty() {
        val cipher = cipherWith(newKey())
        assertEquals("", cipher.decrypt(AesGcmTokenCipher.PREFIX + "not-base64!!"))
        assertEquals("", cipher.decrypt(AesGcmTokenCipher.PREFIX + "AAAA"))
    }

    /** Keystore 不可用（单测/异常机器）：降级明文，登录不失败；下次有密钥时再加密。 */
    @Test
    fun encrypt_withoutKeyDegradesToPlaintext() {
        val cipher = cipherWith(null)
        assertEquals("tok", cipher.encrypt("tok"))
        // 明文读回原值（没有前缀即旧格式）。
        assertEquals("tok", cipher.decrypt("tok"))
    }
}
