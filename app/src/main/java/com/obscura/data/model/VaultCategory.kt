package com.obscura.data.model

import androidx.annotation.Keep

@Keep
enum class VaultCategory(val id: String, val title: String) {
    ACCOUNT("account", "Account"),
    BANK_CARD("bank_card", "Bank Card"),
    SECURE_NOTE("secure_note", "Secure Note"),
    API_KEY("api_key", "API Key / Secret");

    companion object {
        fun fromString(value: String): VaultCategory {
            return entries.firstOrNull { it.id.equals(value, ignoreCase = true) } ?: ACCOUNT
        }
    }
}
