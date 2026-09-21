package com.obscura.ui.common

import androidx.annotation.StringRes
import com.obscura.R
import com.obscura.data.model.VaultCategory
import com.obscura.security.BiometricAvailability
import com.obscura.security.PasswordGenerator

@StringRes
fun VaultCategory.labelRes(): Int = when (this) {
    VaultCategory.ACCOUNT -> R.string.category_account
    VaultCategory.BANK_CARD -> R.string.category_bank_card
    VaultCategory.SECURE_NOTE -> R.string.category_secure_note
    VaultCategory.API_KEY -> R.string.category_api_key
}

@StringRes
fun BiometricAvailability.labelRes(): Int = when (this) {
    BiometricAvailability.AVAILABLE -> R.string.settings_biometrics_disabled
    BiometricAvailability.NOT_ENROLLED -> R.string.settings_biometrics_not_enrolled
    BiometricAvailability.NO_HARDWARE -> R.string.settings_biometrics_no_hardware
    BiometricAvailability.TEMPORARILY_UNAVAILABLE -> R.string.settings_biometrics_unavailable
}

fun PasswordGenerator.StrengthLevel.labelRes(): Int = when (this) {
    PasswordGenerator.StrengthLevel.EMPTY -> R.string.strength_empty
    PasswordGenerator.StrengthLevel.WEAK -> R.string.strength_weak
    PasswordGenerator.StrengthLevel.MEDIUM -> R.string.strength_medium
    PasswordGenerator.StrengthLevel.STRONG -> R.string.strength_strong
    PasswordGenerator.StrengthLevel.EXCELLENT -> R.string.strength_excellent
}
