package com.obscura.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Changing the PIN re-wraps the same DEK and goes through the login brute-force protection. */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryPinChangeTest {

    private companion object {
        const val OLD_PIN = "111111"
        const val NEW_PIN = "222222"
        const val KEY_SALT = "pin_salt"
        const val KEY_PIN_BLOB = "pin_wrapped_dek"
        const val KEY_BIO_BLOB = "bio_wrapped_dek"
    }

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("obscura_auth", Context.MODE_PRIVATE)
    private val repo = AuthRepository(context)

    @Before
    fun clearAuthState() = prefs.edit().clear().commit().let { }

    @After
    fun tearDown() = prefs.edit().clear().commit().let { }

    @Test
    fun changePinRewrapsTheSameDekAndLeavesTheBiometricBlobUntouched() = runBlocking {
        val dek = repo.createVault(OLD_PIN.toCharArray())
        val dekBytes = dek.encoded

        // A biometric blob from an earlier enrollment must survive a PIN change byte for byte.
        val bioBlob = EncryptedBlob(ByteArray(12) { it.toByte() }, ByteArray(48) { (it * 7).toByte() }).serialize()
        prefs.edit().putString(KEY_BIO_BLOB, bioBlob).commit()

        val saltBefore = prefs.getString(KEY_SALT, null)
        val pinBlobBefore = prefs.getString(KEY_PIN_BLOB, null)

        assertEquals(ChangePinResult.Success, repo.changePin(OLD_PIN.toCharArray(), NEW_PIN.toCharArray()))

        // Same DEK, new wrapping: the database key is derived from the DEK and must keep working.
        val unlocked = repo.unlockWithPin(NEW_PIN.toCharArray())
        assertTrue("new PIN must unlock: $unlocked", unlocked is UnlockResult.Success)
        assertArrayEquals(dekBytes, (unlocked as UnlockResult.Success).dek.encoded)

        assertTrue(repo.unlockWithPin(OLD_PIN.toCharArray()) is UnlockResult.WrongPin)

        assertNotEquals(saltBefore, prefs.getString(KEY_SALT, null))
        assertNotEquals(pinBlobBefore, prefs.getString(KEY_PIN_BLOB, null))
        assertEquals(bioBlob, prefs.getString(KEY_BIO_BLOB, null))
    }

    @Test
    fun theNewPinMustDifferFromTheCurrentOne() = runBlocking {
        repo.createVault(OLD_PIN.toCharArray())
        val blobBefore = prefs.getString(KEY_PIN_BLOB, null)

        assertEquals(ChangePinResult.SameAsCurrent, repo.changePin(OLD_PIN.toCharArray(), OLD_PIN.toCharArray()))
        assertEquals(blobBefore, prefs.getString(KEY_PIN_BLOB, null))
    }

    @Test
    fun wrongCurrentPinCountsTowardsTheSharedLockout() = runBlocking {
        repo.createVault(OLD_PIN.toCharArray())

        val first = repo.changePin("999999".toCharArray(), NEW_PIN.toCharArray())
        assertTrue("first failure: $first", first is ChangePinResult.WrongPin)
        assertNull("one failure does not lock yet", repo.lockoutRemaining())

        val second = repo.changePin("999999".toCharArray(), NEW_PIN.toCharArray())
        assertTrue("second failure: $second", second is ChangePinResult.WrongPin)
        assertEquals(
            "attempts must count down through the same counter as login",
            (first as ChangePinResult.WrongPin).attemptsRemaining - 1,
            (second as ChangePinResult.WrongPin).attemptsRemaining
        )
        assertNotNull("the second failure locks, exactly as on the login screen", repo.lockoutRemaining())

        // The lockout applies to the login screen too, and blocks a further PIN change.
        assertTrue(repo.unlockWithPin(OLD_PIN.toCharArray()) is UnlockResult.LockedOut)
        assertTrue(repo.changePin(OLD_PIN.toCharArray(), NEW_PIN.toCharArray()) is ChangePinResult.LockedOut)
    }

    @Test
    fun aSuccessfulChangeClearsTheFailureCounter() = runBlocking {
        repo.createVault(OLD_PIN.toCharArray())
        repo.changePin("999999".toCharArray(), NEW_PIN.toCharArray())

        assertEquals(ChangePinResult.Success, repo.changePin(OLD_PIN.toCharArray(), NEW_PIN.toCharArray()))

        assertNull(repo.lockoutRemaining())
        assertEquals(0, prefs.getInt("failed_attempts", -1))
    }
}
