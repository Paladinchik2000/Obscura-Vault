package com.obscura.security

import androidx.annotation.Keep
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The data is not an Obscura Vault backup, or its header is damaged. */
open class BackupFormatException(message: String) : IllegalArgumentException(message)

/** The backup was written in a format version this build cannot read. */
class UnsupportedBackupVersionException(val found: Int, val supported: Int) :
    BackupFormatException("Backup format version $found is not supported; this app reads version $supported")

/**
 * BackupCryptoUtils
 *
 * Backup file layout (integers are big-endian):
 *
 *   offset  size  field
 *   0       4     magic "OBVB"
 *   4       2     formatVersion (unsigned)
 *   6       16    PBKDF2-HMAC-SHA256 salt
 *   22      12    AES-GCM IV
 *   34      n+16  AES-256-GCM ciphertext of the UTF-8 JSON payload, 128-bit tag at the end
 *
 * The header (magic + formatVersion) stays in the clear, so a reader can tell "written by a
 * different app version" apart from "wrong password" before deriving any key. It is also fed
 * to GCM as additional authenticated data, so it can't be changed without failing decryption.
 * formatVersion covers the whole file: layout, KDF parameters and the JSON schema.
 */
@Keep
object BackupCryptoUtils {

    /** Bump on any change to the file layout, KDF parameters or JSON schema of backups. */
    const val FORMAT_VERSION = 1

    const val HEADER_SIZE_BYTES = 6
    const val PBKDF2_ITERATIONS = 120_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_SIZE_BYTES = 16
    const val IV_SIZE_BYTES = 12 // Standard 96-bit GCM IV
    const val TAG_LENGTH_BITS = 128

    private val MAGIC = "OBVB".toByteArray(Charsets.US_ASCII)

    /**
     * Encrypts a plaintext JSON string with a user-supplied password using AES-256-GCM.
     *
     * @return the complete backup file: header, salt, IV and ciphertext with tag.
     */
    @JvmStatic
    fun encryptPayload(plainText: String, password: CharArray): ByteArray {
        val header = header(FORMAT_VERSION)
        val salt = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            updateAAD(header)
        }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return ByteBuffer.allocate(header.size + salt.size + iv.size + cipherText.size)
            .put(header).put(salt).put(iv).put(cipherText)
            .array()
    }

    /**
     * Decrypts a backup file produced by [encryptPayload].
     *
     * @throws BackupFormatException if the data isn't an Obscura backup or is truncated
     * @throws UnsupportedBackupVersionException if the file uses another format version;
     *   thrown before the password is even looked at
     * @throws SecurityException if the password is incorrect or the file has been altered
     */
    @JvmStatic
    fun decryptPayload(encryptedData: ByteArray, password: CharArray): String {
        val version = readFormatVersion(encryptedData)
        if (version != FORMAT_VERSION) throw UnsupportedBackupVersionException(version, FORMAT_VERSION)

        val minLength = HEADER_SIZE_BYTES + SALT_SIZE_BYTES + IV_SIZE_BYTES + TAG_LENGTH_BITS / 8
        if (encryptedData.size < minLength) throw BackupFormatException("Backup file is truncated")

        val buffer = ByteBuffer.wrap(encryptedData)
        val header = ByteArray(HEADER_SIZE_BYTES).also { buffer.get(it) }
        val salt = ByteArray(SALT_SIZE_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_SIZE_BYTES).also { buffer.get(it) }
        val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_LENGTH_BITS, iv))
                updateAAD(header)
            }
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw SecurityException("Incorrect backup password or tampered backup file.", e)
        }
    }

    /**
     * Reads formatVersion from the clear-text header; needs no password.
     *
     * @throws BackupFormatException if [data] doesn't start with an Obscura backup header.
     */
    @JvmStatic
    fun readFormatVersion(data: ByteArray): Int {
        if (data.size < HEADER_SIZE_BYTES || MAGIC.indices.any { data[it] != MAGIC[it] }) {
            throw BackupFormatException("Not an Obscura Vault backup file")
        }
        return ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
    }

    private fun header(version: Int): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE_BYTES).put(MAGIC).putShort(version.toShort()).array()

    /**
     * Derives a 256-bit AES key using PBKDF2WithHmacSHA256 with 120,000 iterations.
     */
    @JvmStatic
    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
