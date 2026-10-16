package com.remotebot.core.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Sensitive data encryption manager based on Google Tink (AES256-GCM).
 * Bypasses Android Keystore EncryptedSharedPreferences to prevent deadlocks on custom ROMs (MIUI/HyperOS/OneUI).
 */
class SecureStorageManager(
    context: Context,
) {
    private val aead: Aead

    init {
        AeadConfig.register()
        val keysetHandle =
            AndroidKeysetManager
                .Builder()
                .withSharedPref(context, "remotebot_tink_keyset", "remotebot_tink_prefs")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://remotebot_master_key")
                .build()
                .keysetHandle

        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrBlank()) return plainText
        return try {
            val cipherBytes = aead.encrypt(plainText.toByteArray(Charsets.UTF_8), null)
            Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(cipherText: String?): String? {
        if (cipherText.isNullOrBlank()) return cipherText
        return try {
            val cipherBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            val decryptedBytes = aead.decrypt(cipherBytes, null)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            // If decryption fails (e.g. legacy unencrypted token), return as-is
            cipherText
        }
    }
}
