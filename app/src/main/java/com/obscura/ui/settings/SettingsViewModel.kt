package com.obscura.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obscura.R
import com.obscura.security.AuthRepository
import com.obscura.security.ChangePinResult
import com.obscura.ui.auth.PIN_LENGTH
import com.obscura.ui.common.UiText
import com.obscura.ui.common.uiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

data class SettingsUiState(
    val pinChange: PinChangeState = PinChangeState(),
    val message: UiText? = null
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository(app)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

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
                ChangePinResult.Success -> {
                    _state.update {
                        it.copy(pinChange = PinChangeState(), message = uiText(R.string.settings_pin_changed))
                    }
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

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun updatePinChange(transform: (PinChangeState) -> PinChangeState) =
        _state.update { it.copy(pinChange = transform(it.pinChange)) }

    private fun String.digits(): String = filter { it.isDigit() }.take(PIN_LENGTH)
}
