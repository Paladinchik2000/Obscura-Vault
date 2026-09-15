package com.obscura.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF (RFC 5869) over HMAC-SHA256.
 *
 * Used to split the vault DEK into purpose-bound subkeys, so a single key never
 * serves two roles (e.g. database encryption and backups).
 */
object Hkdf {

    private const val HMAC_SHA256 = "HmacSHA256"
    private const val HASH_LEN = 32

    fun sha256(ikm: ByteArray, info: ByteArray, length: Int, salt: ByteArray = ByteArray(0)): ByteArray {
        require(length in 1..255 * HASH_LEN) { "HKDF-SHA256 length must be in 1..${255 * HASH_LEN}" }

        // Extract. A missing salt is HashLen zero bytes (RFC 5869 §2.2).
        val prk = Mac.getInstance(HMAC_SHA256)
            .apply { init(SecretKeySpec(if (salt.isEmpty()) ByteArray(HASH_LEN) else salt, HMAC_SHA256)) }
            .doFinal(ikm)

        // Expand: T(i) = HMAC(PRK, T(i-1) || info || i). SecretKeySpec copies prk, so it can be wiped now.
        val mac = Mac.getInstance(HMAC_SHA256).apply { init(SecretKeySpec(prk, HMAC_SHA256)) }
        prk.fill(0)

        val out = ByteArray(length)
        var block = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            val next = mac.doFinal()
            block.fill(0)
            block = next

            val n = minOf(HASH_LEN, length - offset)
            System.arraycopy(block, 0, out, offset, n)
            offset += n
            counter++
        }
        block.fill(0)
        return out
    }
}
