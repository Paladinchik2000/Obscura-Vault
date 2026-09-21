package com.obscura.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.KeyGenerator

/** Turning biometric unlock off must leave neither the wrapped DEK nor the Keystore key behind. */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryBiometricsTest {

    private companion object {
        const val KEY_BIO_BLOB = "bio_wrapped_dek"
    }

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("obscura_auth", Context.MODE_PRIVATE)
    private val repo = AuthRepository(context)

    @Before
    fun clearAuthState() {
        prefs.edit().clear().commit()
        KeystoreCrypto.deleteBiometricKey()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        KeystoreCrypto.deleteBiometricKey()
    }

    @Test
    fun disablingBiometricsDeletesTheBlobAndTheKeystoreKey() {
        seedEnrolledBiometrics()
        assertTrue("both halves must be present before the test", repo.isBiometricEnrolled)

        repo.disableBiometrics()

        assertNull("the wrapped DEK must be gone", prefs.getString(KEY_BIO_BLOB, null))
        assertFalse("the Keystore key must be gone", KeystoreCrypto.hasBiometricKey())
        assertFalse(repo.isBiometricEnrolled)
    }

    @Test
    fun aLeftoverKeystoreKeyAloneDoesNotCountAsEnrolled() {
        createKeyUnderBioAlias()

        assertFalse("without the wrapped DEK there is nothing to unlock with", repo.isBiometricEnrolled)

        repo.disableBiometrics()
        assertFalse(KeystoreCrypto.hasBiometricKey())
    }

    private fun seedEnrolledBiometrics() {
        prefs.edit()
            .putString(
                KEY_BIO_BLOB,
                EncryptedBlob(ByteArray(12) { it.toByte() }, ByteArray(48) { (it * 3).toByte() }).serialize()
            )
            .commit()
        createKeyUnderBioAlias()
    }

    /**
     * The real key requires a STRONG biometric, which cannot be generated on an emulator with
     * nothing enrolled (KeyGenerator rejects the spec). Deletion is what this test is about, so
     * it falls back to a plain AES key under the same alias.
     */
    private fun createKeyUnderBioAlias() {
        runCatching { KeystoreCrypto.createBiometricKey() }.getOrElse {
            val spec = KeyGenParameterSpec.Builder(
                KeystoreCrypto.BIO_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }
                .generateKey()
        }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue("the alias must exist before disabling", keyStore.containsAlias(KeystoreCrypto.BIO_ALIAS))
    }
}
