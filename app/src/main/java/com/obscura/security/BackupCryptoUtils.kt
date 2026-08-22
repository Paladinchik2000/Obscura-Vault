package com.obscura.security

import androidx.annotation.Keep
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * BackupCryptoUtils
 *
 * Standalone AES-256-GCM Encryption with PBKDF2 (HMAC-SHA256) Key Derivation.
 * Prepends Salt (16B) and IV (12B) to ciphertext for zero-dependency cross-device portability.
 */
@Keep
object BackupCryptoUtils {

    const val PBKDF2_ITERATIONS = 120_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_SIZE_BYTES = 16
    const val IV_SIZE_BYTES = 12 // Standard 96-bit GCM IV
    const val TAG_LENGTH_BITS = 128

    /**
     * Encrypts a plaintext JSON string with a user-supplied password using AES-256-GCM.
     *
     * @param plainText Raw JSON string containing vault entries
     * @param password Char array containing user's backup encryption password
     * @return Byte array formatted as: [Salt 16B] + [IV 12B] + [AES-GCM Ciphertext + 16B Auth Tag]
     */
    @JvmStatic
    fun encryptPayload(plainText: String, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Prepend Salt (16B) + IV (12B) + CipherText
        val result = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(cipherText, 0, result, salt.size + iv.size, cipherText.size)
        return result
    }

    /**
     * Decrypts an encrypted backup byte array using the supplied password.
     *
     * @param encryptedData Combined byte array [Salt 16B] + [IV 12B] + [Ciphertext]
     * @param password Char array containing user's backup encryption password
     * @return Decrypted plaintext JSON string
     * @throws SecurityException If password is incorrect or ciphertext has been altered
     */
    @JvmStatic
    fun decryptPayload(encryptedData: ByteArray, password: CharArray): String {
        val minLength = SALT_SIZE_BYTES + IV_SIZE_BYTES + (TAG_LENGTH_BITS / 8)
        if (encryptedData.size < minLength) {
            throw IllegalArgumentException("Invalid or corrupted backup payload format (minimum length $minLength bytes required).")
        }

        val salt = ByteArray(SALT_SIZE_BYTES)
        val iv = ByteArray(IV_SIZE_BYTES)
        val cipherTextLength = encryptedData.size - (SALT_SIZE_BYTES + IV_SIZE_BYTES)
        val cipherText = ByteArray(cipherTextLength)

        System.arraycopy(encryptedData, 0, salt, 0, SALT_SIZE_BYTES)
        System.arraycopy(encryptedData, SALT_SIZE_BYTES, iv, 0, IV_SIZE_BYTES)
        System.arraycopy(encryptedData, SALT_SIZE_BYTES + IV_SIZE_BYTES, cipherText, 0, cipherTextLength)

        val secretKey = deriveKey(password, salt)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw SecurityException("Incorrect backup password or tampered backup file.", e)
        }
    }

    /**
     * Derives a 256-bit AES key using PBKDF2WithHmacSHA256 with 120,000 iterations.
     */
    @JvmStatic
    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val derivedKey = factory.generateSecret(spec).encoded
        return SecretKeySpec(derivedKey, "AES")
    }
}
