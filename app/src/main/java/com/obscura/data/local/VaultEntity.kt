package com.obscura.data.local

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.obscura.data.model.VaultCategory

/**
 * Room Entity representing encrypted password manager records stored in SQLCipher DB.
 */
@Keep
@Entity(tableName = "vault_entries")
data class VaultEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val category: String, // VaultCategory id
    val usernameOrCardholder: String = "",
    val secretValue: String = "", // Password, CVV, or Secret text
    val urlOrCardNumber: String = "",
    val notesOrCvv: String = "",
    val expiryDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val tags: String = "" // Comma-separated tags
) {
    fun getCategoryEnum(): VaultCategory = VaultCategory.fromString(category)

    fun getMaskedSecret(): String {
        return when (getCategoryEnum()) {
            VaultCategory.BANK_CARD -> {
                if (urlOrCardNumber.length >= 4) "**** **** **** ${urlOrCardNumber.takeLast(4)}"
                else "•••• •••• •••• ••••"
            }
            else -> "••••••••••••"
        }
    }
}
