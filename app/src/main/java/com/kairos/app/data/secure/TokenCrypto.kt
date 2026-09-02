package com.kairos.app.data.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the device token with an AES-256-GCM key that is generated in, and
 * never leaves, the AndroidKeyStore. We do this ourselves rather than use
 * androidx.security:security-crypto, which Google deprecated in April 2025.
 *
 * Ciphertext is stored as base64 of [IV length byte][IV][GCM ciphertext+tag], so
 * a single opaque string round-trips through DataStore.
 */
object TokenCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "kairos_token_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val out = ByteArray(1 + iv.size + ct.size)
        out[0] = iv.size.toByte()
        System.arraycopy(iv, 0, out, 1, iv.size)
        System.arraycopy(ct, 0, out, 1 + iv.size, ct.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** Returns null if the blob can't be decrypted (e.g. the key was cleared by
     *  the OS after a lock-screen change) rather than crashing — the caller
     *  treats that as "no valid token" and re-enrolls. */
    fun decrypt(blob: String): String? = runCatching {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val ivLen = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val ct = bytes.copyOfRange(1 + ivLen, bytes.size)

        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Not tied to device-credential auth: the app must read the token on
            // cold start with no user prompt. Confidentiality comes from the key
            // being non-exportable and hardware-backed where available.
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
