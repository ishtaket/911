package com.searchaid.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the Room database encryption key using Android Keystore.
 * The actual DB passphrase is stored encrypted in a private file,
 * and the encryption key lives in hardware-backed Keystore.
 */
object DatabaseKeyManager {

    private const val KEYSTORE_ALIAS = "searchaid_db_key"
    private const val PASSPHRASE_FILE = "db_passphrase.enc"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128

    fun getPassphrase(context: Context): ByteArray {
        val file = context.getFileStreamPath(PASSPHRASE_FILE)
        return if (file.exists()) {
            decryptPassphrase(context)
        } else {
            val passphrase = generatePassphrase()
            encryptAndStore(context, passphrase)
            passphrase
        }
    }

    private fun generatePassphrase(): ByteArray {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun encryptAndStore(context: Context, passphrase: ByteArray) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val encrypted = cipher.doFinal(passphrase)

        // Store IV length (1 byte) + IV + encrypted passphrase
        context.openFileOutput(PASSPHRASE_FILE, Context.MODE_PRIVATE).use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(encrypted)
        }
    }

    private fun decryptPassphrase(context: Context): ByteArray {
        val key = getOrCreateKey()
        val data = context.openFileInput(PASSPHRASE_FILE).use { it.readBytes() }

        val ivLength = data[0].toInt()
        val iv = data.sliceArray(1..ivLength)
        val encrypted = data.sliceArray((ivLength + 1) until data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }
}
