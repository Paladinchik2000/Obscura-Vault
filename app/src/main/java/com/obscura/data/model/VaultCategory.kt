package com.obscura.data.model

import androidx.annotation.Keep

/** Display names live in string resources (see ui/common/Labels.kt). */
@Keep
enum class VaultCategory(val id: String) {
    ACCOUNT("account"),
    BANK_CARD("bank_card"),
    SECURE_NOTE("secure_note"),
    API_KEY("api_key");

    companion object {
        fun fromString(value: String): VaultCategory {
            return entries.firstOrNull { it.id.equals(value, ignoreCase = true) } ?: ACCOUNT
        }
    }
}
