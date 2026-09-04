package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets before they are written to local storage (currently
 * the per-TV Android TV server certificate fingerprint). The key is generated
 * inside Android Keystore and is non-exportable and encrypted at rest.
 *
 * Values written by older builds or produced when Android Keystore is
 * unavailable (for example under Robolectric) are passed through unchanged so
 * existing pairings remain usable. New device records created on physical
 * Android devices are stored as `enc:<iv>:<ciphertext>`.
 */
object SecureValueStore {

    private const val TAG = "SecureValueStore"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "tvgrip_secret_value"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val PREFIX = "enc:"

    @Volatile
    private var cachedKey: SecretKey? = null

    fun encrypt(plain: String): String? {
        if (plain.isBlank()) return plain
        val key = getOrCreateKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            PREFIX +
                Base64.encodeToString(iv, Base64.NO_WRAP) +
                ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encrypt value; storing legacy plaintext fallback.", e)
            null
        }
    }

    /** Returns the decrypted value, or the original string for legacy values. */
    fun decrypt(stored: String?): String? {
        if (stored.isNullOrBlank() || !stored.startsWith(PREFIX)) return stored
        val parts = stored.removePrefix(PREFIX).split(":", limit = 2)
        if (parts.size != 2) return stored
        val key = getOrCreateKey() ?: return stored
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt value; returning stored value unchanged.", e)
            stored
        }
    }

    private fun getOrCreateKey(): SecretKey? {
        cachedKey?.let { return it }
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                cachedKey = existing
                return existing
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            val created = generator.generateKey()
            cachedKey = created
            created
        } catch (e: Exception) {
            Log.d(TAG, "Android Keystore unavailable for value encryption; using legacy plaintext.", e)
            null
        }
    }
}
