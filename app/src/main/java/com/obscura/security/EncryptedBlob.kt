package com.obscura.security

import android.util.Base64

/**
 * IV + ciphertext pair. Safe to persist in plain SharedPreferences: without the
 * wrapping key this is opaque.
 */
data class EncryptedBlob(val iv: ByteArray, val ciphertext: ByteArray) {

    fun serialize(): String =
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)

    override fun equals(other: Any?): Boolean =
        other is EncryptedBlob && iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()

    companion object {
        fun deserialize(value: String): EncryptedBlob? {
            val parts = value.split(":")
            if (parts.size != 2) return null
            return runCatching {
                EncryptedBlob(
                    Base64.decode(parts[0], Base64.NO_WRAP),
                    Base64.decode(parts[1], Base64.NO_WRAP)
                )
            }.getOrNull()
        }
    }
}
