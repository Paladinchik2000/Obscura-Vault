package com.obscura.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

enum class BiometricAvailability { AVAILABLE, NOT_ENROLLED, NO_HARDWARE, TEMPORARILY_UNAVAILABLE }

sealed interface BiometricOutcome {
    data class Success(val cipher: Cipher) : BiometricOutcome
    data object UserCancelled : BiometricOutcome
    data object KeyInvalidated : BiometricOutcome
    data class Failed(val code: Int, val message: String) : BiometricOutcome
}

object BiometricAuthenticator {

    fun availability(activity: FragmentActivity): BiometricAvailability =
        when (BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.NO_HARDWARE
            else -> BiometricAvailability.TEMPORARILY_UNAVAILABLE
        }

    /**
     * @param cipherProvider throws KeyPermanentlyInvalidatedException if biometrics
     *        were re-enrolled since the key was created.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButton: String,
        cipherProvider: () -> Cipher
    ): BiometricOutcome = suspendCancellableCoroutine { cont ->

        val cipher = try {
            cipherProvider()
        } catch (e: KeyPermanentlyInvalidatedException) {
            cont.resume(BiometricOutcome.KeyInvalidated); return@suspendCancellableCoroutine
        } catch (e: Exception) {
            cont.resume(BiometricOutcome.KeyInvalidated); return@suspendCancellableCoroutine
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (cont.isActive) {
                    cont.resume(
                        if (c != null) BiometricOutcome.Success(c)
                        else BiometricOutcome.Failed(-1, "Missing CryptoObject")
                    )
                }
            }

            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (!cont.isActive) return
                val cancelled = code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    code == BiometricPrompt.ERROR_USER_CANCELED ||
                    code == BiometricPrompt.ERROR_CANCELED
                cont.resume(
                    if (cancelled) BiometricOutcome.UserCancelled
                    else BiometricOutcome.Failed(code, msg.toString())
                )
            }
            // onAuthenticationFailed = one bad read; prompt stays open, so ignore it.
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            callback
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButton)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
