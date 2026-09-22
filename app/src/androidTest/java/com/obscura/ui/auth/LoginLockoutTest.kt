package com.obscura.ui.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.MainActivity
import com.obscura.R
import com.obscura.data.Preferences
import com.obscura.data.local.VaultDatabase
import com.obscura.security.AuthRepository
import com.obscura.security.EncryptedBlob
import com.obscura.security.KeystoreCrypto
import com.obscura.security.VaultSession
import com.obscura.ui.dashboard.DashboardTags
import com.obscura.ui.settings.SettingsTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.KeyGenerator

/**
 * The PIN lockout must clear itself when it runs out. It used to be set once and never cleared:
 * the countdown on screen was only a label, so the keypad stayed dead until the process restarted.
 *
 * Time comes from AuthRepository.clock, which these tests move forward instead of waiting.
 */
@RunWith(AndroidJUnit4::class)
class LoginLockoutTest {

    private companion object {
        const val PIN = "123456"
        const val WRONG_PIN = "999999"
        const val NEW_PIN = "654321"
        const val TIMEOUT_MS = 30_000L
        const val TAG = "LoginLockoutTest"
        const val KEY_BIO_BLOB = "bio_wrapped_dek"
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Added to the real clock, so the app sees time pass without the test sleeping. */
    @Volatile
    private var clockOffsetMs = 0L

    @Before
    fun freshInstall() {
        resetAppState()
        AuthRepository.clock = { System.currentTimeMillis() + clockOffsetMs }
    }

    @After
    fun tearDown() {
        AuthRepository.clock = { System.currentTimeMillis() }
        clockOffsetMs = 0L
        resetAppState()
    }

    @Test
    fun theLockoutClearsItselfWithoutRecreatingTheScreen() = withMainActivity { scenario ->
        createVaultAndLock()
        lockOutTheKeypad()

        var activityBefore = 0
        scenario.onActivity { activityBefore = System.identityHashCode(it) }

        skipPastTheLockout()

        waitUntilKeypadIsUsable()
        assertTrue(
            "the error must go with the lockout",
            compose.onAllNodesWithText(string(R.string.error_incorrect_pin), substring = true)
                .fetchSemanticsNodes().isEmpty()
        )
        var activityAfter = 0
        scenario.onActivity { activityAfter = System.identityHashCode(it) }
        assertEquals("the screen must recover in place, not by being recreated", activityBefore, activityAfter)

        // And the keypad really works again.
        enterPin(PIN)
        waitForText(string(R.string.dashboard_empty_vault_title))
    }

    @Test
    fun aLockoutThatRunsOutInTheBackgroundIsGoneOnReturn() = withMainActivity { scenario ->
        createVaultAndLock()
        lockOutTheKeypad()

        scenario.moveToState(Lifecycle.State.CREATED)
        skipPastTheLockout()
        scenario.moveToState(Lifecycle.State.RESUMED)

        waitUntilKeypadIsUsable()
        enterPin(PIN)
        waitForText(string(R.string.dashboard_empty_vault_title))
    }

    @Test
    fun biometricsStayAvailableWhileThePinIsLockedOut() {
        // Seeded before the screen starts, so the keypad offers the fingerprint key at all.
        seedBiometricEnrollment()

        withMainActivity {
            createVaultAndLock()

            lockOutTheKeypad()

            compose.onNodeWithText("1").assertIsNotEnabled()
            compose.onNodeWithContentDescription(string(R.string.keypad_biometric)).assertIsEnabled()
        }
    }

    @Test
    fun theSamePinChangeLockoutInSettingsClearsItself() = withMainActivity {
        createVault()

        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        waitForTag(SettingsTags.PIN_OPEN)
        compose.onNodeWithTag(SettingsTags.PIN_OPEN).performClick()
        waitForTag(SettingsTags.PIN_CURRENT)
        compose.onNodeWithTag(SettingsTags.PIN_NEW).performTextInput(NEW_PIN)
        compose.onNodeWithTag(SettingsTags.PIN_CONFIRM).performTextInput(NEW_PIN)

        // Two wrong current PINs lock the shared counter, exactly as on the login screen.
        repeat(2) { attempt ->
            compose.onNodeWithTag(SettingsTags.PIN_CURRENT).performTextInput(WRONG_PIN)
            compose.onNodeWithTag(SettingsTags.PIN_SUBMIT).performClick()
            compose.waitUntil(TIMEOUT_MS) { failedAttempts() == attempt + 1 }
        }
        assertNotNull("the settings form must be locked out", AuthRepository(context).lockoutRemaining())
        compose.onNodeWithTag(SettingsTags.PIN_SUBMIT).assertIsNotEnabled()

        skipPastTheLockout()

        // The form recovers on its own: filling the current PIN in is enough to submit again.
        compose.waitUntil(TIMEOUT_MS) { AuthRepository(context).lockoutRemaining() == null }
        compose.onNodeWithTag(SettingsTags.PIN_CURRENT).performTextInput(PIN)
        compose.waitUntil(TIMEOUT_MS) { isEnabled(SettingsTags.PIN_SUBMIT) }
        compose.onNodeWithTag(SettingsTags.PIN_SUBMIT).performClick()
        waitForText(string(R.string.settings_pin_changed))
    }

    // ------------------------------------------------------------------ helpers

    /** Two wrong PINs are what the backoff table turns into a real lockout. */
    private fun lockOutTheKeypad() {
        repeat(2) { attempt -> enterWrongPin(expectedFailures = attempt + 1) }
        compose.waitUntil(TIMEOUT_MS) { AuthRepository(context).lockoutRemaining() != null }
        compose.waitUntil(TIMEOUT_MS) { !isEnabledByText("1") }
    }

    /**
     * Waits for the attempt to be counted, not just typed: checking the PIN runs PBKDF2, and
     * while the ViewModel is busy the keypad ignores digits, so the next attempt would be lost.
     */
    private fun enterWrongPin(expectedFailures: Int) {
        enterPin(WRONG_PIN)
        compose.waitUntil(TIMEOUT_MS) { failedAttempts() == expectedFailures }
    }

    private fun failedAttempts(): Int =
        context.getSharedPreferences(Preferences.AUTH, Context.MODE_PRIVATE).getInt("failed_attempts", 0)

    private fun skipPastTheLockout() {
        clockOffsetMs += 60_000L
    }

    private fun waitUntilKeypadIsUsable() {
        compose.waitUntil(TIMEOUT_MS) { isEnabledByText("1") }
    }

    private fun isEnabled(tag: String): Boolean =
        runCatching { compose.onNodeWithTag(tag).assertIsEnabled() }.isSuccess

    private fun isEnabledByText(text: String): Boolean =
        runCatching { compose.onNodeWithText(text).assertIsEnabled() }.isSuccess

    /** A wrapped DEK plus a key under the biometric alias: enough for the keypad to offer it. */
    private fun seedBiometricEnrollment() {
        context.getSharedPreferences(Preferences.AUTH, Context.MODE_PRIVATE).edit()
            .putString(
                KEY_BIO_BLOB,
                EncryptedBlob(ByteArray(12) { it.toByte() }, ByteArray(48) { (it * 5).toByte() }).serialize()
            )
            .commit()
        // The real key needs an enrolled biometric, which an emulator does not have; deletion and
        // presence are all this test needs, so a plain AES key under the same alias will do.
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

    /** Fresh install through the real screens; ends on the dashboard with the vault open. */
    private fun createVault() {
        waitForText(string(R.string.login_title_create_pin))
        enterPin(PIN)
        waitForText(string(R.string.login_title_confirm_pin))
        enterPin(PIN)
        waitForText(string(R.string.dashboard_empty_vault_title))
    }

    /** The login screen only exists once there is a vault to unlock. */
    private fun createVaultAndLock() {
        createVault()
        VaultSession.requestLock()
        waitForText(string(R.string.login_subtitle_unlock))
    }

    private fun enterPin(pin: String) {
        pin.forEach { digit -> compose.onNodeWithText(digit.toString()).performClick() }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
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
    }
}
