package com.obscura.security

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup key derivation on the device's own crypto provider, checked against keys computed
 * with OpenSSL (PBKDF2-HMAC-SHA256, 600000 iterations, salt 00..0f, 32 bytes).
 */
@RunWith(AndroidJUnit4::class)
class BackupKdfReferenceTest {

    private companion object {
        const val TAG = "BackupKdfTest"
        const val JSON = """{"formatVersion":1,"entries":[]}"""
        const val ASCII_PASSWORD = "correct horse battery staple"
        const val ASCII_KEY = "ef177144eec9420cbc1093d2a8b344a92bc506d0d4ec9c028dd19f8324d8c1e6"
        const val CYRILLIC_PASSWORD = "пароль-Обскура"
        const val CYRILLIC_KEY = "1899e75602a69e59f2d00d7c8a3f4b8fe0f89437cb44187b2530cb2b71f09b7f"
    }

    @Test
    fun deviceProviderMatchesOpenSslForAsciiPassword() {
        val file = fileEncryptedWithReferenceKey(ASCII_KEY)
        assertEquals(JSON, BackupCryptoUtils.decryptPayload(file, ASCII_PASSWORD.toCharArray()))
    }

    @Test
    fun deviceProviderMatchesOpenSslForCyrillicPassword() {
        val file = fileEncryptedWithReferenceKey(CYRILLIC_KEY)
        assertEquals(JSON, BackupCryptoUtils.decryptPayload(file, CYRILLIC_PASSWORD.toCharArray()))
    }

    /** Diagnostic: logs how long one backup key derivation takes on this device. */
    @Test
    fun measureKeyDerivationTime() {
        val provider = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").provider
        BackupCryptoUtils.encryptPayload(JSON, ASCII_PASSWORD.toCharArray()) // warm-up

        val runsMs = List(3) {
            val start = SystemClock.elapsedRealtimeNanos()
            val file = BackupCryptoUtils.encryptPayload(JSON, ASCII_PASSWORD.toCharArray())
            val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            assertEquals(6 + 16 + 12 + JSON.length + 16, file.size)
            elapsed
        }
        Log.i(
            TAG,
            "PBKDF2WithHmacSHA256 x${BackupCryptoUtils.PBKDF2_ITERATIONS} via ${provider.name}: " +
                "runs=${runsMs} ms, median=${runsMs.sorted()[1]} ms"
        )
    }

    private fun fileEncryptedWithReferenceKey(keyHex: String): ByteArray {
        val header = ByteBuffer.allocate(6).put("OBVB".toByteArray()).putShort(BackupCryptoUtils.FORMAT_VERSION.toShort()).array()
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { 7 }
        val key = ByteArray(keyHex.length / 2) { keyHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val cipherText = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(header)
        }.doFinal(JSON.toByteArray())
        return ByteBuffer.allocate(header.size + salt.size + iv.size + cipherText.size)
            .put(header).put(salt).put(iv).put(cipherText)
            .array()
    }
}
