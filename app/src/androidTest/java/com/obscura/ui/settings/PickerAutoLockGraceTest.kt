package com.obscura.ui.settings

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.MainActivity
import com.obscura.R
import com.obscura.data.Preferences
import com.obscura.data.local.VaultDatabase
import com.obscura.security.BiometricAuthenticator
import com.obscura.security.KeystoreCrypto
import com.obscura.security.VaultSession
import com.obscura.ui.dashboard.DashboardTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * With "immediately" auto-lock, a system screen we opened ourselves (a SAF picker) must not lock
 * the vault: our activity is stopped while the picker is up and the result would land on the
 * login screen. Every other way of leaving still locks at once.
 */
@RunWith(AndroidJUnit4::class)
class PickerAutoLockGraceTest {

    private companion object {
        const val PIN = "123456"
        const val TIMEOUT_MS = 20_000L
        const val TAG = "PickerAutoLockGraceTest"
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Before
    fun freshInstall() = resetAppState()

    @After
    fun tearDown() {
        VaultSession.ownResultGraceMs = 2 * 60 * 1000L
        resetAppState()
    }

    @Test
    fun theRealPickerKeepsTheVaultOpenAndCancellingItRestoresImmediateLocking() = withMainActivity { scenario ->
        createVault()
        chooseImmediateAutoLock()

        compose.onNodeWithTag(DashboardTags.BACKUP).performClick()
        waitForText(string(R.string.backup_import_intro))
        compose.onNodeWithText(string(R.string.backup_import_choose_file)).performClick()

        // The picker is a separate activity: ours is stopped, which is exactly what used to lock.
        waitForState(scenario, Lifecycle.State.CREATED)
        assertTrue("the vault must stay open while our own picker is up", VaultSession.isUnlocked.value)

        // Cancel the picker the way a user does.
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        waitForState(scenario, Lifecycle.State.RESUMED)
        waitForText(string(R.string.backup_import_intro))
        assertTrue("a cancelled picker must not lock either", VaultSession.isUnlocked.value)

        // The grace is over: the next trip to the background locks at once again.
        scenario.moveToState(Lifecycle.State.CREATED)
        waitUntilLocked()
    }

    @Test
    fun aPickerLeftOpenLongerThanTheGraceLocks() = withMainActivity { scenario ->
        createVault()
        chooseImmediateAutoLock()
        // Long enough that reaching the stopped state cannot race the expiry, short enough for a test.
        VaultSession.ownResultGraceMs = 5_000L

        VaultSession.startedOwnActivityResult()
        val stoppedAt = SystemClock.uptimeMillis()
        scenario.moveToState(Lifecycle.State.CREATED)

        // Still open right away, locked once the grace runs out, without coming back to the app.
        assertTrue(VaultSession.isUnlocked.value)
        waitUntilLocked()
        assertTrue(
            "the lock must wait for the grace, not fire straight away",
            SystemClock.uptimeMillis() - stoppedAt >= 4_000L
        )
    }

    @Test
    fun goingHomeLocksImmediately() = withMainActivity { scenario ->
        createVault()
        chooseImmediateAutoLock()

        scenario.moveToState(Lifecycle.State.CREATED)
        waitUntilLocked()
    }

    /**
     * BiometricPrompt is a system window, not an activity, so it must not stop ours — otherwise it
     * would need the same grace as the pickers. This drives the real authenticate() path and
     * records the lifecycle. On an emulator with nothing enrolled the prompt ends in an error
     * instead of showing; the stronger evidence is that the APK contains no activity from
     * androidx.biometric at all.
     */
    @Test
    fun theBiometricPromptDoesNotStopTheActivity() = withMainActivity { scenario ->
        createVault()

        val events = Collections.synchronizedList(mutableListOf<Lifecycle.Event>())
        lateinit var activity: MainActivity
        scenario.onActivity {
            activity = it
            it.lifecycle.addObserver(LifecycleEventObserver { _, event -> events += event })
        }

        val outcome = runBlocking {
            withTimeoutOrNull(15_000) {
                withContext(Dispatchers.Main) {
                    BiometricAuthenticator.authenticate(
                        activity = activity,
                        title = "Prompt lifecycle check",
                        subtitle = "instrumented test",
                        negativeButton = "Cancel",
                        cipherProvider = { KeystoreCrypto.bioEncryptCipher() }
                    )
                }
            }
        }
        android.util.Log.i(TAG, "biometric outcome on this device: $outcome")

        SystemClock.sleep(1_000)
        assertFalse(
            "BiometricPrompt must not stop our activity, otherwise it needs the picker grace too",
            events.contains(Lifecycle.Event.ON_STOP)
        )
        assertTrue(VaultSession.isUnlocked.value)
    }

    // ------------------------------------------------------------------ helpers

    private fun chooseImmediateAutoLock() {
        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        val tag = SettingsTags.autoLock(AutoLockOption.IMMEDIATELY)
        waitForTag(tag)
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(TIMEOUT_MS) { VaultSession.idleTimeoutMs == 0L }
        compose.onNodeWithTag(SettingsTags.BACK).performClick()
        waitForText(string(R.string.dashboard_empty_vault_title))
    }

    private fun waitUntilLocked() {
        compose.waitUntil(TIMEOUT_MS) { !VaultSession.isUnlocked.value }
        assertFalse(VaultSession.isUnlocked.value)
    }

    private fun waitForState(scenario: ActivityScenario<MainActivity>, state: Lifecycle.State) {
        compose.waitUntil(TIMEOUT_MS) { scenario.state == state }
    }

    /** See VaultNavigationTest: a body failure is logged before close(), which can crash the process. */
    private fun withMainActivity(body: (ActivityScenario<MainActivity>) -> Unit) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var bodyFailure: Throwable? = null
        try {
            body(scenario)
        } catch (t: Throwable) {
            bodyFailure = t
            android.util.Log.e(TAG, "Test body failed; closing the activity next", t)
            throw t
        } finally {
            try {
                scenario.close()
            } catch (closeFailure: Throwable) {
                if (bodyFailure != null) bodyFailure.addSuppressed(closeFailure) else throw closeFailure
            }
        }
    }

    private fun createVault() {
        waitForText(string(R.string.login_title_create_pin))
        enterPin(PIN)
        waitForText(string(R.string.login_title_confirm_pin))
        enterPin(PIN)
        waitForText(string(R.string.dashboard_empty_vault_title))
    }

    private fun enterPin(pin: String) {
        pin.forEach { digit -> compose.onNodeWithText(digit.toString()).performClick() }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun resetAppState() {
        runBlocking { VaultSession.lock() }
        context.deleteDatabase(VaultDatabase.DATABASE_NAME)
        Preferences.ALL.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        KeystoreCrypto.deleteBiometricKey()
        VaultSession.idleTimeoutMs = AutoLockOption.DEFAULT.millis
    }
}
