package com.obscura.ui.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.security.KeystoreCrypto
import com.obscura.security.VaultSession
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The auto-lock timeout is stored in plain preferences and drives VaultSession. */
@RunWith(AndroidJUnit4::class)
class AutoLockSettingsTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences(AutoLockSettings.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun clear() {
        runBlocking { VaultSession.lock() }
        prefs.edit().clear().commit()
        VaultSession.idleTimeoutMs = AutoLockOption.DEFAULT.millis
    }

    @After
    fun tearDown() {
        // Other tests in this process expect the app to count as being on screen.
        VaultSession.onAppForegrounded()
        runBlocking { VaultSession.lock() }
        prefs.edit().clear().commit()
        context.deleteDatabase("obscura_encrypted_vault.db")
        VaultSession.idleTimeoutMs = AutoLockOption.DEFAULT.millis
    }

    @Test
    fun defaultIsTwoMinutes() {
        assertEquals(AutoLockOption.MINUTES_2, AutoLockSettings(context).option)
        assertEquals(2 * 60 * 1000L, AutoLockOption.DEFAULT.millis)
    }

    @Test
    fun theChosenTimeoutIsStoredAndAppliedToTheSession() {
        AutoLockSettings(context).option = AutoLockOption.SECONDS_30

        assertEquals("applied right away", 30_000L, VaultSession.idleTimeoutMs)
        assertEquals("stored for the next process", 30_000L, prefs.getLong(AutoLockSettings.KEY_TIMEOUT, -1L))

        // A fresh instance — as after a restart — reads it back and pushes it into the session.
        VaultSession.idleTimeoutMs = AutoLockOption.DEFAULT.millis
        val reloaded = AutoLockSettings(context)
        assertEquals(AutoLockOption.SECONDS_30, reloaded.option)
        reloaded.applyToSession()
        assertEquals(30_000L, VaultSession.idleTimeoutMs)
    }

    @Test
    fun immediatelyLocksTheOpenSessionWhenTheAppGoesToTheBackground() = runBlocking {
        VaultSession.unlock(context, KeystoreCrypto.generateDek())
        assertTrue(VaultSession.isUnlocked.value)

        AutoLockSettings(context).option = AutoLockOption.MINUTES_15
        VaultSession.touch()
        VaultSession.onAppBackgrounded()
        assertTrue("a 15 minute timeout must not lock on its own", VaultSession.isUnlocked.value)

        AutoLockSettings(context).option = AutoLockOption.IMMEDIATELY
        VaultSession.onAppBackgrounded()

        withTimeout(10_000) { VaultSession.isUnlocked.first { !it } }
        assertFalse(VaultSession.isUnlocked.value)
    }

    /**
     * The vault must close on time even if the user never comes back: after an autofill unlock
     * there may be no activity of ours to return to at all.
     */
    @Test
    fun theTimeoutLocksWhileTheAppIsStillInTheBackground() = runBlocking {
        VaultSession.unlock(context, KeystoreCrypto.generateDek())
        VaultSession.idleTimeoutMs = 1_500L

        VaultSession.touch()
        VaultSession.onAppBackgrounded()
        assertTrue("must not lock before the timeout", VaultSession.isUnlocked.value)

        withTimeout(15_000) { VaultSession.isUnlocked.first { !it } }
        assertFalse("the timeout must fire without the app coming back", VaultSession.isUnlocked.value)
    }
}
