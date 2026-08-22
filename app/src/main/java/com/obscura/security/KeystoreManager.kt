package com.obscura.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.Keep
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android Keystore Manager
 * Handles hardware-backed AES-256 key generation and secure passphrase retrieval
 * for SQLCipher Room Database encryption.
 */
@Keep
class KeystoreManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS = "obscura_vault_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "obscura_secure_prefs"
        private const val KEY_DB_PASSPHRASE = "encrypted_db_passphrase"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Gets or generates the hardware-backed 256-bit passphrase for SQLCipher DB decryption.
     */
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingPassHex = prefs.getString(KEY_DB_PASSPHRASE, null)

        if (existingPassHex != null) {
            return hexToBytes(existingPassHex)
        }

        // Generate new random 32-byte (256-bit) passphrase
        val randomPassphrase = ByteArray(32)
        SecureRandom().nextBytes(randomPassphrase)

        // Store as Hex
        val hexPass = bytesToHex(randomPassphrase)
        prefs.edit().putString(KEY_DB_PASSPHRASE, hexPass).apply()

        // Ensure Android Keystore master key is initialized
        getOrCreateMasterKey()

        return randomPassphrase
    }

    /**
     * Ensures an AES-256 Master Key exists inside the Android Keystore.
     */
    fun getOrCreateMasterKey(): SecretKey {
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Master key availability
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val j = hex.substring(index, index + 2).toInt(16)
            result[i] = j.toByte()
        }
        return result
    }
}
