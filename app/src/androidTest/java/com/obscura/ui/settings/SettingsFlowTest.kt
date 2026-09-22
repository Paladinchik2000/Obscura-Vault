package com.obscura.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.MainActivity
import com.obscura.R
import com.obscura.security.VaultSession
import com.obscura.ui.dashboard.DashboardTags
import com.obscura.ui.detail.EntryTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The settings screen in the real app: reachable from the dashboard, gone when the vault locks. */
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {

    private companion object {
        const val DATABASE_NAME = "obscura_encrypted_vault.db"
        const val PIN = "123456"
        const val NEW_PIN = "654321"
        const val TIMEOUT_MS = 20_000L
        const val TAG = "SettingsFlowTest"
        val PREFERENCE_FILES = listOf("obscura_auth", "obscura_ui", "obscura_settings")
        const val AUTOFILL_COMPONENT = "com.obscura/com.obscura.autofill.ObscuraAutofillService"
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private var previousAutofillService: String? = null

    @Before
    fun freshInstall() {
        previousAutofillService = shell("settings get secure autofill_service").trim()
        resetAppState()
    }

    @After
    fun tearDown() {
        // Put the device's own autofill service back, whatever it was.
        val previous = previousAutofillService
        if (previous.isNullOrEmpty() || previous == "null") {
            shell("settings delete secure autofill_service")
        } else {
            shell("settings put secure autofill_service $previous")
        }
        resetAppState()
        VaultSession.idleTimeoutMs = AutoLockOption.DEFAULT.millis
    }

    @Test
    fun settingsOpenFromDashboardAndCloseOnLock() = withMainActivity {
        createVault()

        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        waitForText(string(R.string.settings_title))
        waitForText(string(R.string.settings_offline))

        // Back returns to the dashboard.
        compose.onNodeWithTag(SettingsTags.BACK).performClick()
        waitForText(string(R.string.dashboard_empty_vault_title))

        // Locking from the settings screen throws the user back to login.
        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        waitForText(string(R.string.settings_title))
        VaultSession.requestLock()
        waitForText(string(R.string.login_subtitle_unlock))
        assertTrue(compose.onAllNodesWithText(string(R.string.settings_title)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun changingThePinKeepsEntriesAndRejectsTheOldPin() = withMainActivity {
        createVault()
        addEntry(title = "GitHub test", secret = "s3cret-Passw0rd!")

        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        waitForTag(SettingsTags.PIN_OPEN)
        compose.onNodeWithTag(SettingsTags.PIN_OPEN).performClick()

        waitForTag(SettingsTags.PIN_CURRENT)
        compose.onNodeWithTag(SettingsTags.PIN_CURRENT).performTextInput(PIN)
        compose.onNodeWithTag(SettingsTags.PIN_NEW).performTextInput(NEW_PIN)
        compose.onNodeWithTag(SettingsTags.PIN_CONFIRM).performTextInput(NEW_PIN)
        compose.onNodeWithTag(SettingsTags.PIN_SUBMIT).performScrollTo().performClick()
        waitForText(string(R.string.settings_pin_changed))

        // The vault stays open: changing the PIN re-wraps the DEK, it does not replace it.
        assertTrue(VaultSession.isUnlocked.value)

        VaultSession.requestLock()
        waitForText(string(R.string.login_subtitle_unlock))

        enterPin(PIN)
        // The login screen appends the remaining attempts to the message.
        waitForText(string(R.string.error_incorrect_pin), substring = true)

        enterPin(NEW_PIN)
        waitForText("GitHub test")
    }

    @Test
    fun choosingAnAutoLockTimeoutStoresItAndAppliesItToTheSession() = withMainActivity {
        createVault()
        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()

        val tag = SettingsTags.autoLock(AutoLockOption.SECONDS_30)
        waitForTag(tag)
        compose.onNodeWithTag(tag).performScrollTo().performClick()

        compose.waitUntil(TIMEOUT_MS) { VaultSession.idleTimeoutMs == AutoLockOption.SECONDS_30.millis }
        val prefs = context.getSharedPreferences(AutoLockSettings.PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(AutoLockOption.SECONDS_30.millis, prefs.getLong(AutoLockSettings.KEY_TIMEOUT, -1L))
    }

    @Test
    fun theAutofillSectionFollowsTheSystemSetting() = withMainActivity { scenario ->
        createVault()
        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        waitForTag(SettingsTags.AUTOFILL_STATE)
        waitForText(string(R.string.settings_autofill_disabled))

        // Turning Obscura on from outside is what the button leads to; the screen must notice.
        shell("settings put secure autofill_service $AUTOFILL_COMPONENT")
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        waitForText(string(R.string.settings_autofill_enabled))
    }

    // ------------------------------------------------------------------ helpers

    /** Runs a shell command as the shell user, which may write secure settings. */
    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { stream ->
                stream.readBytes().decodeToString()
            }
        }

    private fun addEntry(title: String, secret: String) {
        compose.onNodeWithText(string(R.string.dashboard_empty_vault_action)).performClick()
        waitForTag(EntryTags.TITLE)
        compose.onNodeWithTag(EntryTags.TITLE).performTextInput(title)
        compose.onNodeWithTag(EntryTags.SECRET).performTextInput(secret)
        compose.onNodeWithTag(EntryTags.SAVE).performScrollTo().performClick()

        // The first entry raises the one-time backup reminder.
        waitForText(string(R.string.backup_reminder_message))
        compose.onNodeWithText(string(R.string.action_later)).performClick()
        waitUntilGone(string(R.string.backup_reminder_message))
    }

    private fun waitUntilGone(text: String) {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
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
        context.deleteDatabase(DATABASE_NAME)
        PREFERENCE_FILES.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
