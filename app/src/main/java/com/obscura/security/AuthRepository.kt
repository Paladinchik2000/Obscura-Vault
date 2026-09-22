package com.obscura.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.annotation.VisibleForTesting
import com.obscura.data.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

sealed interface ChangePinResult {
    data object Success : ChangePinResult
    data class WrongPin(val attemptsRemaining: Int) : ChangePinResult
    data class LockedOut(val until: Long) : ChangePinResult
    data object SameAsCurrent : ChangePinResult
    data class Error(val cause: Throwable) : ChangePinResult
}

sealed interface UnlockResult {
    data class Success(val dek: SecretKey) : UnlockResult
    data class WrongPin(val attemptsRemaining: Int) : UnlockResult
    data class LockedOut(val until: Long) : UnlockResult
    data object BiometricKeyInvalidated : UnlockResult
    data class Error(val cause: Throwable) : UnlockResult
}

class AuthRepository internal constructor(
    context: Context,
    /** Replaceable so tests do not have to wait a real lockout out. */
    private val now: () -> Long
) {

    // Reads the companion clock on every call, so replacing it also affects repositories
    // that were built earlier.
    constructor(context: Context) : this(context, { clock() })

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(Preferences.AUTH, Context.MODE_PRIVATE)

    internal companion object {
        /** Time source of every AuthRepository built the normal way. Tests may replace it. */
        @VisibleForTesting
        internal var clock: () -> Long = { System.currentTimeMillis() }

        private const val KEY_SALT = "pin_salt"
        private const val KEY_PIN_BLOB = "pin_wrapped_dek"
        private const val KEY_BIO_BLOB = "bio_wrapped_dek"
        private const val KEY_FAILED = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"

        private const val MAX_ATTEMPTS = 5
        private val BACKOFF_MS = longArrayOf(0, 0, 15_000, 60_000, 300_000, 900_000)
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

    // ------------------------------------------------------------ change pin

    /**
     * Re-wraps the existing DEK with a KEK derived from [newPin] and a fresh salt. The DEK itself,
     * the database and the biometric blob are untouched, so an open session stays valid.
     *
     * The current PIN is verified through the same failure counter and lockout as [unlockWithPin]:
     * otherwise this screen would be a way around the brute-force protection.
     */
    suspend fun changePin(currentPin: CharArray, newPin: CharArray): ChangePinResult =
        withContext(Dispatchers.Default) {
            lockoutRemaining()?.let { return@withContext ChangePinResult.LockedOut(it) }

            val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
                ?: return@withContext ChangePinResult.Error(IllegalStateException("Vault not initialized"))
            val blob = prefs.getString(KEY_PIN_BLOB, null)?.let { EncryptedBlob.deserialize(it) }
                ?: return@withContext ChangePinResult.Error(IllegalStateException("Corrupt vault header"))

            val dekBytes = try {
                KeystoreCrypto.decrypt(KeystoreCrypto.deriveKekFromPin(currentPin, salt), blob)
            } catch (e: AEADBadTagException) {
                // Wrong current PIN counts as a failed attempt, exactly like a failed unlock.
                return@withContext ChangePinResult.WrongPin(registerFailure())
            } catch (e: Exception) {
                return@withContext ChangePinResult.Error(e)
            }

            try {
                if (currentPin.contentEquals(newPin)) return@withContext ChangePinResult.SameAsCurrent

                val newSalt = KeystoreCrypto.randomSalt()
                val newKek = KeystoreCrypto.deriveKekFromPin(newPin, newSalt)
                val newBlob = KeystoreCrypto.encrypt(newKek, dekBytes)

                // One synchronous commit(): salt and blob must land together. If the process dies
                // here, the file holds either both old values or both new ones, never a mix.
                val written = prefs.edit()
                    .putString(KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
                    .putString(KEY_PIN_BLOB, newBlob.serialize())
                    .putInt(KEY_FAILED, 0)
                    .putLong(KEY_LOCKED_UNTIL, 0L)
                    .commit()

                if (written) ChangePinResult.Success
                else ChangePinResult.Error(IllegalStateException("Could not write the new PIN"))
            } catch (e: Exception) {
                ChangePinResult.Error(e)
            } finally {
                dekBytes.fill(0)
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

    /**
     * Timestamp the lockout expires, or null when there is none. The single source of truth for
     * every screen: UI state must be refreshed from here, never counted down in memory alone.
     */
    fun lockoutRemaining(): Long? {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return if (now() < until) until else null
    }

    private fun registerFailure(): Int {
        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        val delay = BACKOFF_MS[min(failed, BACKOFF_MS.lastIndex)]
        prefs.edit()
            .putInt(KEY_FAILED, failed)
            .putLong(KEY_LOCKED_UNTIL, if (delay > 0) now() + delay else 0L)
            .apply()
        return (MAX_ATTEMPTS - failed).coerceAtLeast(0)
    }

    private fun resetAttempts() {
        prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
    }
}
