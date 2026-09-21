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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.MainActivity
import com.obscura.R
import com.obscura.data.Preferences
import com.obscura.data.local.VaultDatabase
import com.obscura.security.KeystoreCrypto
import com.obscura.security.VaultSession
import com.obscura.ui.dashboard.DashboardTags
import com.obscura.ui.detail.EntryTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Erasing the vault leaves nothing behind and the app starts over at PIN creation. */
@RunWith(AndroidJUnit4::class)
class SettingsResetTest {

    private companion object {
        const val PIN = "123456"
        const val NEW_PIN = "654321"
        const val TIMEOUT_MS = 20_000L
        const val TAG = "SettingsResetTest"
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun freshInstall() = resetAppState()

    @After
    fun tearDown() = resetAppState()

    @Test
    fun erasingTheVaultRemovesEverythingAndANewVaultCanBeCreated() = withMainActivity {
        createVault(PIN)
        addFirstEntry(title = "GitHub test", secret = "s3cret-Passw0rd!")
        assertTrue("the database file is expected before the reset", databaseFile().exists())

        eraseVault(PIN)

        // Back at first-run setup with a cleared back stack.
        waitForText(string(R.string.login_title_create_pin))
        assertFalse(VaultSession.isUnlocked.value)
        assertTrue(compose.onAllNodesWithText("GitHub test").fetchSemanticsNodes().isEmpty())

        assertFalse("database", databaseFile().exists())
        assertFalse("wal", File(databaseFile().path + "-wal").exists())
        assertFalse("shm", File(databaseFile().path + "-shm").exists())
        Preferences.ALL.forEach { name ->
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            assertTrue("preferences $name must be empty, found $all", all.isEmpty())
        }
        assertFalse("bio key", KeystoreCrypto.hasBiometricKey())
        assertFalse("pepper key", KeystoreCrypto.hasPepperKey())

        // A fresh vault works, with a different PIN and a new pepper key.
        createVault(NEW_PIN)
        addFirstEntry(title = "After reset", secret = "another-Passw0rd!")
        waitForText("After reset")
        assertTrue(KeystoreCrypto.hasPepperKey())
    }

    @Test
    fun theWarningLeadsToTheBackupScreen() = withMainActivity {
        createVault(PIN)

        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        waitForTag(SettingsTags.RESET_OPEN)
        compose.onNodeWithTag(SettingsTags.RESET_OPEN).performScrollTo().performClick()

        waitForText(string(R.string.settings_reset_warning_message))
        compose.onNodeWithTag(SettingsTags.RESET_BACKUP).performClick()

        waitForText(string(R.string.backup_export_title))
        assertTrue("nothing is erased on the way to the backup screen", databaseFile().exists())
    }

    @Test
    fun aWrongPinDoesNotEraseAnything() = withMainActivity {
        createVault(PIN)
        addFirstEntry(title = "GitHub test", secret = "s3cret-Passw0rd!")

        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        waitForTag(SettingsTags.RESET_OPEN)
        compose.onNodeWithTag(SettingsTags.RESET_OPEN).performScrollTo().performClick()
        waitForText(string(R.string.settings_reset_warning_message))
        compose.onNodeWithTag(SettingsTags.RESET_CONTINUE).performClick()

        waitForTag(SettingsTags.RESET_PIN)
        compose.onNodeWithTag(SettingsTags.RESET_PIN).performTextInput("999999")
        compose.onNodeWithTag(SettingsTags.RESET_CONFIRM).performClick()

        waitForText(string(R.string.error_incorrect_pin))
        assertTrue(databaseFile().exists())
        assertTrue(VaultSession.isUnlocked.value)
    }

    // ------------------------------------------------------------------ helpers

    private fun eraseVault(pin: String) {
        compose.onNodeWithTag(DashboardTags.SETTINGS).performClick()
        waitForTag(SettingsTags.RESET_OPEN)
        compose.onNodeWithTag(SettingsTags.RESET_OPEN).performScrollTo().performClick()

        waitForText(string(R.string.settings_reset_warning_message))
        compose.onNodeWithTag(SettingsTags.RESET_CONTINUE).performClick()

        waitForTag(SettingsTags.RESET_PIN)
        compose.onNodeWithTag(SettingsTags.RESET_PIN).performTextInput(pin)
        compose.onNodeWithTag(SettingsTags.RESET_CONFIRM).performClick()
    }

    private fun databaseFile(): File = context.getDatabasePath(VaultDatabase.DATABASE_NAME)

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

    private fun createVault(pin: String) {
        waitForText(string(R.string.login_title_create_pin))
        enterPin(pin)
        waitForText(string(R.string.login_title_confirm_pin))
        enterPin(pin)
        waitForText(string(R.string.dashboard_empty_vault_title))
    }

    private fun addFirstEntry(title: String, secret: String) {
        compose.onNodeWithText(string(R.string.dashboard_empty_vault_action)).performClick()
        waitForTag(EntryTags.TITLE)
        compose.onNodeWithTag(EntryTags.TITLE).performTextInput(title)
        compose.onNodeWithTag(EntryTags.SECRET).performTextInput(secret)
        compose.onNodeWithTag(EntryTags.SAVE).performScrollTo().performClick()

        waitForText(string(R.string.backup_reminder_message))
        compose.onNodeWithText(string(R.string.action_later)).performClick()
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(string(R.string.backup_reminder_message)).fetchSemanticsNodes().isEmpty()
        }
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
        KeystoreCrypto.deletePepperKey()
        VaultSession.idleTimeoutMs = AutoLockOption.DEFAULT.millis
    }
}
