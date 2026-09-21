package com.aboni.n2kRouter

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "n2k.passkey.key"
private const val PREFS_NAME = "n2k.passkeys"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128

/**
 * Persistent per-device passkey cache. Passkeys are encrypted with an AES key held in the Android Keystore
 * and stored in private shared preferences, keyed by device address.
 * A value that cannot be decrypted (e.g. restored from a backup onto another device) is treated as missing.
 */
class PasskeyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun get(address: String): String? {
        val stored = prefs.getString(address, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, raw, 0, IV_LEN))
            String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
        } catch (e: Exception) {
            appendLog("WARN: unable to decrypt passkey for $address (${e.javaClass.simpleName}), discarding")
            remove(address)
            null
        }
    }

    @Synchronized
    fun put(address: String, passkey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(passkey.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(address, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    @Synchronized
    fun remove(address: String) {
        prefs.edit().remove(address).apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val IV_LEN = 12
    }
}
