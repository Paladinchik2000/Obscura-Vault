package com.obscura.ui.auth

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obscura.R
import com.obscura.security.*
import com.obscura.security.AuthRepository
import com.obscura.ui.common.UiText
import com.obscura.ui.common.uiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PIN_LENGTH = 6

data class LoginUiState(
    val pin: String = "",
    val confirmPin: String? = null,      // non-null while confirming during setup
    val isSetupMode: Boolean = false,
    val isBusy: Boolean = false,
    val error: UiText? = null,
    val attemptsRemaining: Int? = null,
    val lockedUntil: Long? = null,
    val canUseBiometrics: Boolean = false,
    val unlocked: Boolean = false
)

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository(app)

    private val _state = MutableStateFlow(
        LoginUiState(
            isSetupMode = !repo.isVaultInitialized,
            canUseBiometrics = repo.isBiometricEnrolled,
            lockedUntil = repo.lockoutRemaining()
        )
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // ------------------------------------------------------------- keypad

    fun onDigit(d: Char) {
        val s = _state.value
        if (s.isBusy || s.lockedUntil != null) return

        if (s.isSetupMode && s.confirmPin != null) {
            if (s.confirmPin.length >= PIN_LENGTH) return
            val next = s.confirmPin + d
            _state.update { it.copy(confirmPin = next, error = null) }
            if (next.length == PIN_LENGTH) finishSetup()
        } else {
            if (s.pin.length >= PIN_LENGTH) return
            val next = s.pin + d
            _state.update { it.copy(pin = next, error = null) }
            if (next.length == PIN_LENGTH) {
                if (s.isSetupMode) _state.update { it.copy(confirmPin = "") } else submitPin()
            }
        }
    }

    fun onBackspace() {
        _state.update { s ->
            when {
                s.confirmPin != null && s.confirmPin.isNotEmpty() ->
                    s.copy(confirmPin = s.confirmPin.dropLast(1), error = null)
                s.confirmPin != null -> s.copy(confirmPin = null, error = null)
                else -> s.copy(pin = s.pin.dropLast(1), error = null)
            }
        }
    }

    // -------------------------------------------------------------- unlock

    private fun submitPin() = viewModelScope.launch {
        _state.update { it.copy(isBusy = true) }
        val pinChars = _state.value.pin.toCharArray()

        when (val result = repo.unlockWithPin(pinChars)) {
            is UnlockResult.Success -> {
                VaultSession.unlock(getApplication<Application>(), result.dek)
                _state.update { it.copy(isBusy = false, pin = "", unlocked = true) }
            }
            is UnlockResult.WrongPin -> _state.update {
                it.copy(
                    isBusy = false,
                    pin = "",
                    error = uiText(R.string.error_incorrect_pin),
                    attemptsRemaining = result.attemptsRemaining,
                    lockedUntil = repo.lockoutRemaining()
                )
            }
            is UnlockResult.LockedOut -> _state.update {
                it.copy(isBusy = false, pin = "", lockedUntil = result.until)
            }
            else -> _state.update {
                it.copy(isBusy = false, pin = "", error = uiText(R.string.error_unlock_failed))
            }
        }
        pinChars.fill(Char(0))
    }

    private fun finishSetup() = viewModelScope.launch {
        val s = _state.value
        if (s.pin != s.confirmPin) {
            _state.update {
                it.copy(pin = "", confirmPin = null, error = uiText(R.string.error_pins_do_not_match))
            }
            return@launch
        }
        _state.update { it.copy(isBusy = true) }
        val pinChars = s.pin.toCharArray()
        val dek = repo.createVault(pinChars)
        pinChars.fill(Char(0))
        VaultSession.unlock(getApplication<Application>(), dek)
        _state.update { it.copy(isBusy = false, pin = "", confirmPin = null, unlocked = true) }
    }

    // ----------------------------------------------------------- biometrics

    fun unlockWithBiometrics(activity: FragmentActivity) = viewModelScope.launch {
        val iv = repo.biometricIv() ?: return@launch
        val outcome = BiometricAuthenticator.authenticate(
            activity = activity,
            title = string(R.string.biometric_unlock_title),
            subtitle = string(R.string.biometric_unlock_subtitle),
            negativeButton = string(R.string.biometric_use_pin),
            cipherProvider = { KeystoreCrypto.bioDecryptCipher(iv) }
        )
        handleBiometricUnlock(outcome)
    }

    private suspend fun handleBiometricUnlock(outcome: BiometricOutcome) {
        when (outcome) {
            is BiometricOutcome.Success ->
                when (val r = repo.unlockWithBiometrics(outcome.cipher)) {
                    is UnlockResult.Success -> {
                        VaultSession.unlock(getApplication<Application>(), r.dek)
                        _state.update { it.copy(unlocked = true) }
                    }
                    else -> _state.update {
                        it.copy(canUseBiometrics = false, error = uiText(R.string.error_biometric_unavailable))
                    }
                }
            BiometricOutcome.KeyInvalidated -> {
                repo.disableBiometrics()
                _state.update {
                    it.copy(canUseBiometrics = false, error = uiText(R.string.error_biometrics_changed))
                }
            }
            BiometricOutcome.UserCancelled -> Unit
            is BiometricOutcome.Failed -> _state.update {
                // Codes from BiometricPrompt come with a message already localized by the system.
                val error = if (outcome.code < 0) uiText(R.string.error_biometric_failed) else UiText.Platform(outcome.message)
                it.copy(error = error)
            }
        }
    }

    fun consumeUnlock() = _state.update { it.copy(unlocked = false) }

    private fun string(id: Int): String = getApplication<Application>().getString(id)
}
