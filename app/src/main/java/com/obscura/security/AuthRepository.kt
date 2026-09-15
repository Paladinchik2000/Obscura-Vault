package com.obscura.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

sealed interface UnlockResult {
    data class Success(val dek: SecretKey) : UnlockResult
    data class WrongPin(val attemptsRemaining: Int) : UnlockResult
    data class LockedOut(val until: Long) : UnlockResult
    data object BiometricKeyInvalidated : UnlockResult
    data class Error(val cause: Throwable) : UnlockResult
}

class AuthRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("obscura_auth", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_PIN_BLOB = "pin_wrapped_dek"
        const val KEY_BIO_BLOB = "bio_wrapped_dek"
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_until"

        const val MAX_ATTEMPTS = 5
        val BACKOFF_MS = longArrayOf(0, 0, 15_000, 60_000, 300_000, 900_000)
    }

    val isVaultInitialized: Boolean get() = prefs.contains(KEY_PIN_BLOB)

    val isBiometricEnrolled: Boolean
        get() = prefs.contains(KEY_BIO_BLOB) && KeystoreCrypto.hasBiometricKey()

    // ------------------------------------------------------------- first run

    /** Creates the vault DEK and wraps it with the PIN. Returns the live DEK. */
    suspend fun createVault(pin: CharArray): SecretKey = withContext(Dispatchers.Default) {
        val salt = KeystoreCrypto.randomSalt()
        val dek = KeystoreCrypto.generateDek()
        val kek = KeystoreCrypto.deriveKekFromPin(pin, salt)

        val blob = KeystoreCrypto.encrypt(kek, dek.encoded)

        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_BLOB, blob.serialize())
            .putInt(KEY_FAILED, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .apply()

        dek
    }

    // ---------------------------------------------------------- pin unlock

    suspend fun unlockWithPin(pin: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        lockoutRemaining()?.let { return@withContext UnlockResult.LockedOut(it) }

        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return@withContext UnlockResult.Error(IllegalStateException("Vault not initialized"))
        val blob = prefs.getString(KEY_PIN_BLOB, null)?.let { EncryptedBlob.deserialize(it) }
            ?: return@withContext UnlockResult.Error(IllegalStateException("Corrupt vault header"))

        try {
            val kek = KeystoreCrypto.deriveKekFromPin(pin, salt)
            val raw = KeystoreCrypto.decrypt(kek, blob)
            val dek = SecretKeySpec(raw, "AES")
            raw.fill(0)
            resetAttempts()
            UnlockResult.Success(dek)
        } catch (e: AEADBadTagException) {
            // GCM tag mismatch == wrong PIN. No separate verifier hash needed.
            UnlockResult.WrongPin(registerFailure())
        } catch (e: Exception) {
            UnlockResult.Error(e)
        }
    }

    // ---------------------------------------------------- biometric enroll

    /** Call after the user authenticated with the ENCRYPT cipher from BiometricPrompt. */
    fun enableBiometrics(cipher: Cipher, dek: SecretKey) {
        val ciphertext = cipher.doFinal(dek.encoded)
        prefs.edit()
            .putString(KEY_BIO_BLOB, EncryptedBlob(cipher.iv, ciphertext).serialize())
            .apply()
    }

    fun disableBiometrics() {
        prefs.edit().remove(KEY_BIO_BLOB).apply()
        KeystoreCrypto.deleteBiometricKey()
    }

    /** IV needed to build the DECRYPT cipher before prompting. */
    fun biometricIv(): ByteArray? =
        prefs.getString(KEY_BIO_BLOB, null)?.let { EncryptedBlob.deserialize(it)?.iv }

    /** Call after the user authenticated with the DECRYPT cipher. */
    fun unlockWithBiometrics(cipher: Cipher): UnlockResult {
        val blob = prefs.getString(KEY_BIO_BLOB, null)?.let { EncryptedBlob.deserialize(it) }
            ?: return UnlockResult.BiometricKeyInvalidated
        return try {
            val raw = cipher.doFinal(blob.ciphertext)
            val dek = SecretKeySpec(raw, "AES")
            raw.fill(0)
            resetAttempts()
            UnlockResult.Success(dek)
        } catch (e: Exception) {
            disableBiometrics()
            UnlockResult.BiometricKeyInvalidated
        }
    }

    // -------------------------------------------------------------- lockout

    /** Returns the timestamp the lockout expires, or null if not locked. */
    fun lockoutRemaining(): Long? {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return if (System.currentTimeMillis() < until) until else null
    }

    private fun registerFailure(): Int {
        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        val delay = BACKOFF_MS[min(failed, BACKOFF_MS.lastIndex)]
        prefs.edit()
            .putInt(KEY_FAILED, failed)
            .putLong(KEY_LOCKED_UNTIL, if (delay > 0) System.currentTimeMillis() + delay else 0L)
            .apply()
        return (MAX_ATTEMPTS - failed).coerceAtLeast(0)
    }

    private fun resetAttempts() {
        prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
    }
}
