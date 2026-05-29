package com.pca.assistant.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQLCipher passphrase wrapped by an Android Keystore-backed AES/GCM key.
 *
 * The random passphrase is generated once, encrypted with a non-extractable
 * Keystore key (StrongBox is requested when available), and persisted as
 * ciphertext+IV in SharedPreferences. Decryption requires the device unlocked
 * and the key still present in the secure element.
 */
@Singleton
class DbPassphrase @Inject constructor(
    private val context: Context,
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun obtain(): ByteArray {
        val ivB64 = prefs.getString(KEY_IV, null)
        val ctB64 = prefs.getString(KEY_CT, null)
        if (ivB64 != null && ctB64 != null) {
            return decrypt(
                android.util.Base64.decode(ivB64, android.util.Base64.NO_WRAP),
                android.util.Base64.decode(ctB64, android.util.Base64.NO_WRAP),
            )
        }
        // First run: generate 32-byte random passphrase
        val plain = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val (iv, ct) = encrypt(plain)
        prefs.edit {
            putString(KEY_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            putString(KEY_CT, android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP))
        }
        return plain
    }

    fun wipe() {
        prefs.edit { clear() }
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setIsStrongBoxBacked(hasStrongBox())
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    private fun hasStrongBox(): Boolean = context.packageManager.hasSystemFeature(
        android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE
    )

    private fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(plain)
        return cipher.iv to ct
    }

    private fun decrypt(iv: ByteArray, ct: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private companion object {
        const val PREFS = "pca_db_secrets"
        const val KEY_IV = "iv"
        const val KEY_CT = "ct"
        const val ALIAS = "pca_db_master_v1"
    }
}
