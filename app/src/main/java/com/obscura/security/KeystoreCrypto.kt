package com.obscura.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * All low-level crypto for Obscura.
 *
 * Two Keystore keys are used:
 *  - PEPPER_ALIAS : HMAC-SHA256, hardware-backed, NOT auth-gated.
 *                   Makes offline brute-force of the PIN impossible without the device.
 *  - BIO_ALIAS    : AES-GCM, requires a STRONG biometric for every single use.
 *                   Wraps a second copy of the DEK for biometric unlock.
 *
 * The DEK (data encryption key) itself is a plain random AES-256 key that lives
 * only in memory while the vault is unlocked. It is never persisted unwrapped.
 */
object KeystoreCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    const val PEPPER_ALIAS = "obscura_pin_pepper_v1"
    const val BIO_ALIAS = "obscura_bio_dek_v1"

    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val AES_KEY_BITS = 256
    private const val SALT_BYTES = 32

    /** Tune with a benchmark on your min-spec device; target ~250-400 ms. */
    private const val PBKDF2_ITERATIONS = 210_000

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private val rng = SecureRandom()

    // ---------------------------------------------------------------- random

    fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }

    fun generateDek(): SecretKey {
        val raw = ByteArray(AES_KEY_BITS / 8).also { rng.nextBytes(it) }
        val key = SecretKeySpec(raw, "AES")
        raw.fill(0)
        return key
    }

    // ---------------------------------------------------------------- pepper

    /**
     * Hardware-bound HMAC key. Non-exportable: an attacker holding a copy of the
     * app's files cannot compute the KEK without the physical device.
     */
    private fun pepperKey(): SecretKey {
        (keyStore.getKey(PEPPER_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(PEPPER_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // StrongBox where available; caller falls back if unsupported.
                    setIsStrongBoxBacked(true)
                }
            }
            .build()

        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
                .apply { init(spec) }
                .generateKey()
        } catch (e: Exception) {
            // No StrongBox on this device -> retry without it.
            val fallback = KeyGenParameterSpec.Builder(PEPPER_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
                .apply { init(fallback) }
                .generateKey()
        }
    }

    /**
     * KEK = HMAC(keystore_pepper, PBKDF2(pin, salt)).
     * Run this off the main thread — PBKDF2 is deliberately slow.
     */
    fun deriveKekFromPin(pin: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        val stretched = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }

        val peppered = Mac.getInstance("HmacSHA256")
            .apply { init(pepperKey()) }
            .doFinal(stretched)

        stretched.fill(0)
        val kek = SecretKeySpec(peppered, "AES")
        peppered.fill(0)
        return kek
    }

    // ------------------------------------------------------------ bio wrapping

    /** Creates (or replaces) the biometric-gated AES key. */
    fun createBiometricKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            BIO_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .setUserAuthenticationRequired(true)
            // Re-enrolling a fingerprint/face permanently invalidates this key.
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0, // timeout 0 = auth required for *every* use, via CryptoObject
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(builder.build()) }
            .generateKey()
    }

    fun biometricKeyOrNull(): SecretKey? =
        runCatching { keyStore.getKey(BIO_ALIAS, null) as? SecretKey }.getOrNull()

    fun deleteBiometricKey() {
        runCatching { keyStore.deleteEntry(BIO_ALIAS) }
    }

    fun hasBiometricKey(): Boolean = runCatching { keyStore.containsAlias(BIO_ALIAS) }.getOrDefault(false)

    /**
     * Cipher handed to BiometricPrompt.CryptoObject.
     * May throw KeyPermanentlyInvalidatedException if biometrics were re-enrolled.
     */
    fun bioEncryptCipher(): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, biometricKeyOrNull() ?: createBiometricKey())
        }

    fun bioDecryptCipher(iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, biometricKeyOrNull(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }

    // --------------------------------------------------------------- AES-GCM

    fun encrypt(key: SecretKey, plaintext: ByteArray): EncryptedBlob {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return EncryptedBlob(cipher.iv, cipher.doFinal(plaintext))
    }

    fun decrypt(key: SecretKey, blob: EncryptedBlob): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.iv))
        }
        return cipher.doFinal(blob.ciphertext)
    }
}
