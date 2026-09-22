package com.obscura.ui.settings

import android.app.Application
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obscura.R
import com.obscura.security.AuthRepository
import com.obscura.security.BiometricAuthenticator
import com.obscura.security.BiometricAvailability
import com.obscura.security.BiometricOutcome
import com.obscura.security.ChangePinResult
import com.obscura.security.KeystoreCrypto
import com.obscura.security.UnlockResult
import com.obscura.security.VaultLockedException
import com.obscura.security.VaultReset
import com.obscura.security.VaultSession
import com.obscura.ui.auth.PIN_LENGTH
import com.obscura.ui.common.UiText
import com.obscura.ui.common.uiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How often a showing lockout is checked against AuthRepository. */
private const val LOCKOUT_POLL_MS = 200L

/**
 * State of the "change PIN" form. The typed PINs live here rather than in the composable, so a
 * configuration change does not put them into saved instance state.
 */
data class PinChangeState(
    val isOpen: Boolean = false,
    val currentPin: String = "",
    val newPin: String = "",
    val confirmPin: String = "",
    val isBusy: Boolean = false,
    val error: UiText? = null,
    val lockedUntil: Long? = null
) {
    val canSubmit: Boolean
        get() = !isBusy && currentPin.length == PIN_LENGTH && newPin.length == PIN_LENGTH &&
            confirmPin.length == PIN_LENGTH && lockedUntil == null
}

data class BiometricsState(
    val isEnabled: Boolean = false,
    val availability: BiometricAvailability = BiometricAvailability.TEMPORARILY_UNAVAILABLE,
    val confirmDisable: Boolean = false,
    val isBusy: Boolean = false
) {
    val canEnable: Boolean
        get() = !isEnabled && !isBusy && availability == BiometricAvailability.AVAILABLE
}

/** Two confirmations: a warning that points at the backup screen, then the PIN. */
enum class ResetStep { NONE, WARNING, PIN }

data class ResetState(
    val step: ResetStep = ResetStep.NONE,
    val pin: String = "",
    val isBusy: Boolean = false,
    val error: UiText? = null,
    val lockedUntil: Long? = null
) {
    val canConfirm: Boolean get() = !isBusy && pin.length == PIN_LENGTH && lockedUntil == null
}

/**
 * Whether this device has autofill at all (API 26+) and whether Obscura is the chosen service.
 */
data class AutofillState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false
)

data class SettingsUiState(
    val autoLock: AutoLockOption = AutoLockOption.DEFAULT,
    val pinChange: PinChangeState = PinChangeState(),
    val biometrics: BiometricsState = BiometricsState(),
    val reset: ResetState = ResetState(),
    val autofill: AutofillState = AutofillState(),
    val message: UiText? = null
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository(app)
    private val autoLock = AutoLockSettings(app)

    private val _state = MutableStateFlow(
        SettingsUiState(
            autoLock = autoLock.option,
            biometrics = BiometricsState(isEnabled = repo.isBiometricEnrolled)
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        watchLockout()
    }

    /**
     * Same lockout as the login screen, so it has to clear itself here too: the countdown in the
     * dialog is only a label. AuthRepository stays the source of truth.
     */
    private fun watchLockout() = viewModelScope.launch {
        _state.map { it.pinChange.lockedUntil ?: it.reset.lockedUntil }
            .distinctUntilChanged()
            .collectLatest { until ->
                if (until == null) return@collectLatest
                while (isActive) {
                    if (repo.lockoutRemaining() == null) {
                        clearLockout()
                        break
                    }
                    delay(LOCKOUT_POLL_MS)
                }
            }
    }

    /** Re-reads the lockout from storage; call when the screen comes back to the foreground. */
    fun refreshLockout() {
        val until = repo.lockoutRemaining()
        if (until == null) {
            clearLockout()
        } else {
            _state.update {
                it.copy(
                    pinChange = if (it.pinChange.isOpen) it.pinChange.copy(lockedUntil = until) else it.pinChange,
                    reset = if (it.reset.step == ResetStep.PIN) it.reset.copy(lockedUntil = until) else it.reset
                )
            }
        }
    }

    private fun clearLockout() = _state.update {
        it.copy(
            pinChange = if (it.pinChange.lockedUntil == null) it.pinChange
            else it.pinChange.copy(lockedUntil = null, error = null),
            reset = if (it.reset.lockedUntil == null) it.reset
            else it.reset.copy(lockedUntil = null, error = null)
        )
    }

    // ----------------------------------------------------------------- change pin

    fun openPinChange() = updatePinChange { PinChangeState(isOpen = true, lockedUntil = repo.lockoutRemaining()) }

    fun cancelPinChange() = updatePinChange { PinChangeState() }

    fun onCurrentPinChanged(value: String) = updatePinChange { it.copy(currentPin = value.digits(), error = null) }

    fun onNewPinChanged(value: String) = updatePinChange { it.copy(newPin = value.digits(), error = null) }

    fun onConfirmPinChanged(value: String) = updatePinChange { it.copy(confirmPin = value.digits(), error = null) }

    fun submitPinChange() {
        val form = _state.value.pinChange
        if (!form.canSubmit) return
        if (form.newPin != form.confirmPin) {
            updatePinChange { it.copy(error = uiText(R.string.error_pins_do_not_match)) }
            return
        }

        updatePinChange { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val current = form.currentPin.toCharArray()
            val next = form.newPin.toCharArray()
            val result = try {
                repo.changePin(current, next)
            } finally {
                current.fill(Char(0))
                next.fill(Char(0))
            }

            when (result) {
                ChangePinResult.Success -> _state.update {
                    it.copy(pinChange = PinChangeState(), message = uiText(R.string.settings_pin_changed))
                }
                is ChangePinResult.WrongPin -> updatePinChange {
                    it.copy(
                        isBusy = false,
                        currentPin = "",
                        error = uiText(R.string.error_incorrect_pin),
                        lockedUntil = repo.lockoutRemaining()
                    )
                }
                is ChangePinResult.LockedOut -> updatePinChange {
                    it.copy(isBusy = false, currentPin = "", lockedUntil = result.until)
                }
                ChangePinResult.SameAsCurrent -> updatePinChange {
                    it.copy(isBusy = false, newPin = "", confirmPin = "", error = uiText(R.string.error_pin_same_as_current))
                }
                is ChangePinResult.Error -> updatePinChange {
                    it.copy(isBusy = false, error = uiText(R.string.error_pin_change_failed))
                }
            }
        }
    }

    // ------------------------------------------------------------------ autofill

    /**
     * Reads the state from AutofillManager; call when the screen starts and after coming back
     * from the system settings screen.
     */
    fun refreshAutofill() {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().getSystemService(AutofillManager::class.java)
        } else {
            null
        }
        val state = if (manager == null || !manager.isAutofillSupported) {
            AutofillState(isSupported = false, isEnabled = false)
        } else {
            AutofillState(isSupported = true, isEnabled = manager.hasEnabledAutofillServices())
        }
        _state.update { it.copy(autofill = state) }
    }

    // ------------------------------------------------------------------ auto-lock

    fun onAutoLockSelected(option: AutoLockOption) {
        autoLock.option = option
        _state.update { it.copy(autoLock = option) }
    }

    // ----------------------------------------------------------------- biometrics

    /** Availability needs an activity, so the screen reports it when it starts and after changes. */
    fun refreshBiometrics(activity: FragmentActivity) = updateBiometrics {
        it.copy(isEnabled = repo.isBiometricEnrolled, availability = BiometricAuthenticator.availability(activity))
    }

    fun enableBiometrics(activity: FragmentActivity) {
        if (!_state.value.biometrics.canEnable) return
        updateBiometrics { it.copy(isBusy = true) }

        viewModelScope.launch {
            // A previous key may be invalidated; the enrollment always starts from a fresh one.
            KeystoreCrypto.deleteBiometricKey()
            val outcome = BiometricAuthenticator.authenticate(
                activity = activity,
                title = string(R.string.biometric_enroll_title),
                subtitle = string(R.string.biometric_enroll_subtitle),
                negativeButton = string(R.string.action_cancel),
                cipherProvider = { KeystoreCrypto.bioEncryptCipher() }
            )

            when (outcome) {
                is BiometricOutcome.Success -> {
                    try {
                        repo.enableBiometrics(outcome.cipher, VaultSession.requireKey())
                        _state.update {
                            it.copy(
                                biometrics = it.biometrics.copy(isEnabled = true, isBusy = false),
                                message = uiText(R.string.settings_biometrics_enabled_message)
                            )
                        }
                    } catch (e: VaultLockedException) {
                        _state.update {
                            it.copy(
                                biometrics = it.biometrics.copy(isBusy = false),
                                message = uiText(R.string.error_vault_locked)
                            )
                        }
                    }
                }
                BiometricOutcome.UserCancelled -> updateBiometrics { it.copy(isBusy = false) }
                else -> _state.update {
                    it.copy(
                        biometrics = it.biometrics.copy(isBusy = false),
                        message = uiText(R.string.error_biometric_failed)
                    )
                }
            }
            refreshBiometrics(activity)
        }
    }

    fun requestDisableBiometrics() = updateBiometrics { it.copy(confirmDisable = true) }

    fun cancelDisableBiometrics() = updateBiometrics { it.copy(confirmDisable = false) }

    /** Drops both the wrapped copy of the DEK and the Keystore key that protects it. */
    fun confirmDisableBiometrics() {
        repo.disableBiometrics()
        _state.update {
            it.copy(
                biometrics = it.biometrics.copy(isEnabled = false, confirmDisable = false),
                message = uiText(R.string.settings_biometrics_disabled_message)
            )
        }
    }

    // --------------------------------------------------------------- full reset

    fun startReset() = updateReset { ResetState(step = ResetStep.WARNING) }

    fun proceedToResetPin() = updateReset {
        if (it.step == ResetStep.WARNING) ResetState(step = ResetStep.PIN, lockedUntil = repo.lockoutRemaining()) else it
    }

    fun cancelReset() = updateReset { ResetState() }

    fun onResetPinChanged(value: String) = updateReset { it.copy(pin = value.digits(), error = null) }

    /**
     * Verifies the PIN through the same counter as unlocking, then erases everything. The wipe
     * itself is NonCancellable: this ViewModel is cleared the moment the vault locks and the
     * navigation graph pops, and a half-erased vault would be worse than either end state.
     */
    fun confirmReset() {
        val form = _state.value.reset
        if (!form.canConfirm) return
        updateReset { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            val pin = form.pin.toCharArray()
            val verified = try {
                repo.unlockWithPin(pin)
            } finally {
                pin.fill(Char(0))
            }

            when (verified) {
                is UnlockResult.Success -> withContext(NonCancellable) {
                    VaultReset.wipe(getApplication())
                    AutoLockSettings(getApplication()).applyToSession()
                }
                is UnlockResult.WrongPin -> updateReset {
                    it.copy(
                        isBusy = false,
                        pin = "",
                        error = uiText(R.string.error_incorrect_pin),
                        lockedUntil = repo.lockoutRemaining()
                    )
                }
                is UnlockResult.LockedOut -> updateReset {
                    it.copy(isBusy = false, pin = "", lockedUntil = verified.until)
                }
                else -> updateReset { it.copy(isBusy = false, pin = "", error = uiText(R.string.error_unlock_failed)) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun updatePinChange(transform: (PinChangeState) -> PinChangeState) =
        _state.update { it.copy(pinChange = transform(it.pinChange)) }

    private fun updateBiometrics(transform: (BiometricsState) -> BiometricsState) =
        _state.update { it.copy(biometrics = transform(it.biometrics)) }

    private fun updateReset(transform: (ResetState) -> ResetState) =
        _state.update { it.copy(reset = transform(it.reset)) }

    private fun String.digits(): String = filter { it.isDigit() }.take(PIN_LENGTH)

    private fun string(id: Int): String = getApplication<Application>().getString(id)
}
