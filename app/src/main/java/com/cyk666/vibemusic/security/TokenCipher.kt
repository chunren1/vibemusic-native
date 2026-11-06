package com.cyk666.vibemusic.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.cyk666.vibemusic.SilentFail
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 落盘 token 的加解密口子。AuthStore 只认这个接口，生产实现走 AndroidKeyStore，
 * 单测可以塞纯 JVM 密钥的实现（Robolectric 没有 AndroidKeyStore provider）。
 */
interface TokenCipher {
    /** 明文 → 落盘串。空串原样返回（不产生 "v1:" + 空密文）。 */
    fun encrypt(plain: String): String

    /** 落盘串 → 明文。历史明文（无前缀）按原值返回；解不开返回空串，绝不抛。 */
    fun decrypt(stored: String): String
}

/** 提供 AES-256 密钥；返回 null = 当前环境没有可用密钥（Keystore 不可用）。 */
fun interface TokenKeyProvider {
    fun key(): SecretKey?
}

/**
 * AES/GCM/NoPadding 实现：IV 由 provider 随机生成（生产 = AndroidKeyStore，12 字节），
 * 落盘 `v1:` + Base64(IV || ciphertext)。
 *
 * 为什么自带一套而不是加 androidx.security-crypto：仓库技术栈冻结，不加新依赖
 * （见 AGENTS.md）。这里只用到平台 API（Keystore + javax.crypto），代价是几十行。
 *
 * 为什么 `v1:` 前缀：升级前落盘的是明文 token，必须能继续读；前缀让"旧明文"
 * 与"新密文"在同一份 DataStore 里共存一个版本，下次写入自动迁移成密文。
 *
 * 为什么解不开只返回空串：密钥被系统清掉 / 备份恢复到新机 / Keystore 坏了，
 * 密文必然解不开。此时最多 401 一次（游客态可用），崩溃或清盘都更糟。
 */
class AesGcmTokenCipher(private val keys: TokenKeyProvider) : TokenCipher {

    override fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        // Keystore 不可用（模拟器异常/Robolectric）时降级明文：宁可这台机器没有加密，
        // 也不能让登录直接失败。真相由 SilentFail 记一笔，别让它悄无声息。
        val key = keys.key() ?: return plain.also { SilentFail.note("TokenCipher.encrypt.noKey") }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // IV 必须交给 provider 生成：AndroidKeyStore 的 AES/GCM 默认
            // setRandomizedEncryptionRequired(true)，调用方自带 IV 会抛
            // InvalidAlgorithmParameterException —— 被下面的 catch 吞成明文降级，
            // 等于线上悄悄不加密。JVM（单测）同样会随机生成 12 字节 IV。
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            if (iv == null || iv.size != IV_BYTES) {
                // 约定固定 12 字节（GCM 默认，也是 decrypt 侧的偏移量）；格式不符就降级别写坏数据。
                return plain.also { SilentFail.note("TokenCipher.encrypt.badIv") }
            }
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val blob = ByteArray(iv.size + body.size)
            System.arraycopy(iv, 0, blob, 0, iv.size)
            System.arraycopy(body, 0, blob, iv.size, body.size)
            PREFIX + Base64.getEncoder().encodeToString(blob)
        } catch (e: Exception) {
            SilentFail.note("TokenCipher.encrypt")
            plain
        }
    }

    override fun decrypt(stored: String): String {
        // 升级前的明文：原值返回，等下次 save 自然转成密文（迁移兼容）。
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val blob = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            if (blob.size <= IV_BYTES) return failed("TokenCipher.decrypt.shortBlob")
            val key = keys.key() ?: return failed("TokenCipher.decrypt.noKey")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
            String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            // GCM tag 不匹配（换机恢复备份/密钥被清）走这里：本次按未登录处理。
            failed("TokenCipher.decrypt")
        }
    }

    private fun failed(code: String): String {
        SilentFail.note(code)
        return ""
    }

    companion object {
        /** 落盘前缀标记：带它才是密文。 */
        const val PREFIX = "v1:"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/**
 * AndroidKeyStore 里的 AES-256 密钥（alias 固定，GCM/NoPadding，不要求解锁认证）。
 *
 * 为什么 `setUserAuthenticationRequired(false)`：静默续期与冷启动恢复都在后台跑，
 * 要是一次性指纹/锁屏门禁，息屏后第一次请求就会 401。
 *
 * 首次使用生成一次，成功/失败都缓存在进程内：Keystore 坏的机器不必每次登录都
 * 重试建密钥（失败就按"无密钥"降级，见 [AesGcmTokenCipher.encrypt]）。
 */
object KeystoreTokenKeyProvider : TokenKeyProvider {

    const val KEY_ALIAS = "vibemusic.auth.token.v1"

    private const val PROVIDER = "AndroidKeyStore"

    private val cached: SecretKey? by lazy {
        try {
            loadOrCreate()
        } catch (e: Exception) {
            SilentFail.note("TokenCipher.keystoreUnavailable")
            null
        }
    }

    override fun key(): SecretKey? = cached

    private fun loadOrCreate(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }
}

/** 进程级单例入口；单测用 [installForTest] 换成 JVM 密钥实现。 */
object TokenCipherRegistry {

    private val keystoreBacked: TokenCipher by lazy {
        AesGcmTokenCipher(KeystoreTokenKeyProvider)
    }

    @Volatile
    private var installed: TokenCipher? = null

    val current: TokenCipher get() = installed ?: keystoreBacked

    /** 只给单测用：传 null 恢复生产实现。 */
    internal fun installForTest(cipher: TokenCipher?) {
        installed = cipher
    }
}
