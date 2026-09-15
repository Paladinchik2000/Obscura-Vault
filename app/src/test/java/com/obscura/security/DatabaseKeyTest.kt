package com.obscura.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class DatabaseKeyTest {

    private val dekBytes = ByteArray(32) { it.toByte() }
    private val dek = SecretKeySpec(dekBytes, "AES")

    @Test
    fun v1KeyIsPinned() {
        // Reference computed independently with OpenSSL:
        //   openssl kdf -keylen 32 -kdfopt digest:SHA2-256 -kdfopt hexkey:000102..1f \
        //     -kdfopt info:obscura-db-key-v1 HKDF
        // If this ever changes, every existing vault database becomes unreadable.
        assertEquals(
            "x'aece23fc494c2da78a60f6bd55386d74f6351021be824fc3081242ec9461bd91'",
            rawKey()
        )
    }

    @Test
    fun rawKeyIsSqlCipherBlobLiteral() {
        // Exactly 2 + 64 + 1 chars: SQLCipher only skips its PBKDF2 for this shape.
        assertTrue(Regex("x'[0-9a-f]{64}'").matches(rawKey()))
    }

    @Test
    fun databaseKeyIsNotTheDek() {
        val dekAsRawKey = "x'" + dekBytes.joinToString("") { "%02x".format(it) } + "'"
        assertNotEquals(dekAsRawKey, rawKey())
    }

    @Test
    fun derivationLeavesTheDekIntact() {
        rawKey()
        assertArrayEquals(ByteArray(32) { it.toByte() }, dek.encoded)
    }

    private fun rawKey(): String = String(DatabaseKey.sqlCipherRawKey(dek), Charsets.US_ASCII)
}
