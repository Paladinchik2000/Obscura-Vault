package com.obscura.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Known-answer tests from RFC 5869, Appendix A (SHA-256 cases). */
class HkdfTest {

    @Test
    fun rfc5869Case1_basic() {
        val okm = Hkdf.sha256(
            ikm = ByteArray(22) { 0x0b },
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
            salt = hex("000102030405060708090a0b0c")
        )
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            okm
        )
    }

    @Test
    fun rfc5869Case2_longInputsSpanningThreeBlocks() {
        val okm = Hkdf.sha256(
            ikm = range(0x00, 0x4f),
            info = range(0xb0, 0xff),
            length = 82,
            salt = range(0x60, 0xaf)
        )
        assertArrayEquals(
            hex(
                "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                    "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                    "cc30c58179ec3e87c14c01d5c1f3434f1d87"
            ),
            okm
        )
    }

    @Test
    fun rfc5869Case3_emptySaltAndInfo() {
        val okm = Hkdf.sha256(ikm = ByteArray(22) { 0x0b }, info = ByteArray(0), length = 42)
        assertArrayEquals(
            hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
            okm
        )
    }

    @Test
    fun differentInfoLabelsGiveIndependentKeys() {
        val ikm = ByteArray(32) { it.toByte() }
        val db = Hkdf.sha256(ikm, "obscura-db-key-v1".toByteArray(), 32)
        val backup = Hkdf.sha256(ikm, "obscura-backup-key-v1".toByteArray(), 32)
        assertFalse(db.contentEquals(backup))
    }

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun range(from: Int, toInclusive: Int): ByteArray =
        ByteArray(toInclusive - from + 1) { (from + it).toByte() }
}
