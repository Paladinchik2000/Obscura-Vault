package com.obscura.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.MainActivity
import com.obscura.R
import com.obscura.security.VaultSession
import com.obscura.ui.dashboard.DashboardTags
import kotlinx.coroutines.runBlocking
import org.junit.After
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
        const val TIMEOUT_MS = 20_000L
        const val TAG = "SettingsFlowTest"
        val PREFERENCE_FILES = listOf("obscura_auth", "obscura_ui", "obscura_settings")
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun freshInstall() = resetAppState()

    @After
    fun tearDown() = resetAppState()

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

    // ------------------------------------------------------------------ helpers

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
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
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
