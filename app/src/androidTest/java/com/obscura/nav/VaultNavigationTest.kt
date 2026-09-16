package com.obscura.nav

import android.content.ClipDescription
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.MainActivity
import com.obscura.R
import com.obscura.data.local.VaultEntity
import com.obscura.security.VaultSession
import com.obscura.ui.clipboard.SensitiveClipboard
import com.obscura.ui.dashboard.DashboardTags
import com.obscura.ui.detail.EntryTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real app from a fresh install: PIN setup, dashboard, editor, reminder, search, recreation and lock. */
@RunWith(AndroidJUnit4::class)
class VaultNavigationTest {

    private companion object {
        const val DATABASE_NAME = "obscura_encrypted_vault.db" // must match VaultDatabase
        const val PIN = "123456"
        const val TIMEOUT_MS = 20_000L
        const val TAG = "VaultNavigationTest"
        val PREFERENCE_FILES = listOf("obscura_auth", "obscura_ui")
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun freshInstall() = resetAppState()

    @After
    fun tearDown() = resetAppState()

    @Test
    fun createFindEditThenLockReturnsToLoginWithClearedBackStack() = withMainActivity { scenario ->
        createVault()

        // Empty vault: a clear call to action instead of an empty list.
        compose.onNodeWithText(string(R.string.dashboard_empty_vault_action)).performClick()

        waitForTag(EntryTags.TITLE)
        compose.onNodeWithTag(EntryTags.TITLE).performTextInput("GitHub test")
        compose.onNodeWithTag(EntryTags.USERNAME).performTextInput("octocat")
        compose.onNodeWithTag(EntryTags.SECRET).performTextInput("s3cret-Passw0rd!")
        compose.onNodeWithTag(EntryTags.SAVE).performScrollTo().performClick()

        // The first created entry brings up the one-time backup reminder on the dashboard.
        waitForText(string(R.string.backup_reminder_message))
        compose.onNodeWithText(string(R.string.action_later)).performClick()
        waitUntilGone(string(R.string.backup_reminder_message))

        // Search: a miss shows its own empty state, a hit shows the entry.
        compose.onNodeWithTag(DashboardTags.SEARCH).performTextInput("zzz")
        waitForText(string(R.string.dashboard_empty_search_title, "zzz"))
        compose.onNodeWithTag(DashboardTags.SEARCH).performTextClearance()
        compose.onNodeWithTag(DashboardTags.SEARCH).performTextInput("GitHub")
        waitForText("GitHub test")

        val beforeEdit = storedEntries().single()

        // Edit through the editor opened from the list.
        compose.onNodeWithText("GitHub test").performClick()
        waitForTag(EntryTags.TITLE)
        compose.onNodeWithTag(EntryTags.TITLE).performTextClearance()
        compose.onNodeWithTag(EntryTags.TITLE).performTextInput("GitHub edited")
        compose.onNodeWithTag(EntryTags.SAVE).performScrollTo().performClick()
        waitForText("GitHub edited")

        val afterEdit = storedEntries().single()
        assertEquals(beforeEdit.id, afterEdit.id)
        assertEquals(beforeEdit.createdAt, afterEdit.createdAt)
        assertTrue(
            "updatedAt must move forward: ${beforeEdit.updatedAt} -> ${afterEdit.updatedAt}",
            afterEdit.updatedAt > beforeEdit.updatedAt
        )

        // Lock: back to login, nothing from the vault left on screen.
        compose.onNodeWithTag(DashboardTags.LOCK).performClick()
        waitForText(string(R.string.login_subtitle_unlock))
        assertFalse(VaultSession.isUnlocked.value)
        assertTrue(compose.onAllNodesWithText("GitHub edited").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag(DashboardTags.SEARCH).fetchSemanticsNodes().isEmpty())

        // The vault screens are gone from the back stack: Back leaves the app instead of revealing them.
        Espresso.pressBackUnconditionally()
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (scenario.state != Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
        }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun halfFilledFormSurvivesRecreationButNotLock() = withMainActivity { scenario ->
        createVault()
        compose.onNodeWithText(string(R.string.dashboard_empty_vault_action)).performClick()

        waitForTag(EntryTags.TITLE)
        compose.onNodeWithTag(EntryTags.TITLE).performTextInput("Half-typed title")
        compose.onNodeWithTag(EntryTags.SECRET).performTextInput("half-typed-secret")
        compose.onNodeWithContentDescription(string(R.string.action_show_secret)).performClick()

        // The app is portrait-only, so a configuration change is simulated with recreate().
        recreate(scenario)

        waitForTag(EntryTags.TITLE)
        compose.onNodeWithTag(EntryTags.TITLE).assert(hasText("Half-typed title"))
        compose.onNodeWithTag(EntryTags.SECRET).performScrollTo().assert(hasText("half-typed-secret"))
        assertTrue(
            "secret visibility is part of the form state",
            compose.onAllNodesWithContentDescription(string(R.string.action_hide_secret)).fetchSemanticsNodes().isNotEmpty()
        )

        // A second recreation keeps it too.
        recreate(scenario)
        waitForTag(EntryTags.TITLE)
        compose.onNodeWithTag(EntryTags.TITLE).assert(hasText("Half-typed title"))

        // Lock from the editor: back to login; after unlocking, a new entry starts empty.
        VaultSession.requestLock()
        waitForText(string(R.string.login_subtitle_unlock))
        assertTrue(compose.onAllNodesWithText("Half-typed title").fetchSemanticsNodes().isEmpty())

        enterPin(PIN)
        waitForText(string(R.string.dashboard_empty_vault_title))
        compose.onNodeWithText(string(R.string.dashboard_empty_vault_action)).performClick()
        waitForTag(EntryTags.TITLE)
        compose.onNodeWithTag(EntryTags.TITLE).assert(hasText("Half-typed title").not())
        compose.onNodeWithTag(EntryTags.SECRET).assert(hasText("half-typed-secret").not())
    }

    @Test
    fun everyActivityIsPortraitOnly() {
        @Suppress("DEPRECATION")
        val activities = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            .activities
            .orEmpty()
            .filter { it.name.startsWith("com.obscura.") }

        assertTrue("no app activities found", activities.isNotEmpty())
        activities.forEach { activity ->
            assertEquals(
                "${activity.name} must be portrait-only",
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                activity.screenOrientation
            )
        }
    }

    @Test
    fun copiedSecretsAreMarkedSensitive() {
        val clip = SensitiveClipboard.sensitiveClip(string(R.string.clipboard_label), "s3cret")

        assertTrue(clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        assertEquals(30_000L, SensitiveClipboard.CLEAR_AFTER_MS)
    }

    /**
     * Runs [body] against a freshly launched MainActivity and always closes it afterwards.
     *
     * A failure in [body] is logged and kept before close() runs: closing has been seen to crash
     * the whole process inside Compose (see CLAUDE.md, "Известный плавающий сбой в androidTest"),
     * and that crash would otherwise be the only error in the report. The logcat for each test is
     * saved under build/outputs/androidTest-results; look for tag [TAG].
     */
    private fun withMainActivity(body: (ActivityScenario<MainActivity>) -> Unit) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var bodyFailure: Throwable? = null
        try {
            body(scenario)
        } catch (t: Throwable) {
            bodyFailure = t
            Log.e(TAG, "Test body failed; closing the activity next", t)
            throw t
        } finally {
            try {
                scenario.close()
            } catch (closeFailure: Throwable) {
                // The body's failure stays the reported one.
                if (bodyFailure != null) bodyFailure.addSuppressed(closeFailure) else throw closeFailure
            }
        }
    }

    /** Fresh install: create the PIN and wait for the empty dashboard. */
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

    /** Recreates the activity, as a configuration change would, and checks it really is a new instance. */
    private fun recreate(scenario: ActivityScenario<MainActivity>) {
        var before = 0
        scenario.onActivity { before = System.identityHashCode(it) }
        scenario.recreate()
        var after = before
        scenario.onActivity { after = System.identityHashCode(it) }
        assertNotEquals("activity was not recreated", before, after)
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun storedEntries(): List<VaultEntity> = runBlocking {
        VaultSession.runInSession { VaultSession.requireDatabase().vaultDao().getAllEntriesDirect() }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilGone(text: String) {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
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
