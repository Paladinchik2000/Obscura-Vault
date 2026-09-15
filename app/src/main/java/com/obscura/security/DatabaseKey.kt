package com.obscura.security

import javax.crypto.SecretKey

/**
 * SQLCipher key for the vault database, derived from the DEK.
 *
 * The DEK is never given to SQLCipher directly: HKDF with a dedicated info label
 * yields a database-only key that is independent of any other DEK subkey.
 *
 * Changing [HKDF_INFO] or the derivation makes existing databases unreadable —
 * bump the label version and migrate instead.
 */
object DatabaseKey {

    const val HKDF_INFO = "obscura-db-key-v1"

    private const val KEY_BYTES = 32
    private val HEX = "0123456789abcdef".toByteArray(Charsets.US_ASCII)

    /**
     * Returns the passphrase bytes `x'<64 hex chars>'`.
     *
     * SQLCipher treats this blob literal as a raw 256-bit key and skips its own
     * PBKDF2 — the input is already a full-entropy key, so another KDF pass would
     * only slow down every open. Built as bytes rather than a String so it can be wiped
     * once the database is closed (VaultDatabase.close does that).
     */
    fun sqlCipherRawKey(dek: SecretKey): ByteArray {
        val ikm = dek.encoded
        val key = try {
            Hkdf.sha256(ikm, HKDF_INFO.toByteArray(Charsets.US_ASCII), KEY_BYTES)
        } finally {
            ikm.fill(0)
        }

        val out = ByteArray(2 + KEY_BYTES * 2 + 1)
        out[0] = 'x'.code.toByte()
        out[1] = '\''.code.toByte()
        for (i in key.indices) {
            val b = key[i].toInt()
            out[2 + i * 2] = HEX[(b ushr 4) and 0x0F]
            out[3 + i * 2] = HEX[b and 0x0F]
        }
        out[out.lastIndex] = '\''.code.toByte()

        key.fill(0)
        return out
    }
}
