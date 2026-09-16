package com.obscura.nav

import android.content.ClipDescription
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import com.obscura.data.local.VaultEntity
import com.obscura.security.VaultSession
import com.obscura.ui.backup.BACKUP_REMINDER_TEXT
import com.obscura.ui.clipboard.SensitiveClipboard
import com.obscura.ui.dashboard.DashboardTags
import com.obscura.ui.detail.EntryTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real app from a fresh install: PIN setup, dashboard, editor, reminder, search and lock. */
@RunWith(AndroidJUnit4::class)
class VaultNavigationTest {

    private companion object {
        const val DATABASE_NAME = "obscura_encrypted_vault.db" // must match VaultDatabase
        const val TIMEOUT_MS = 20_000L
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
    fun createFindEditThenLockReturnsToLoginWithClearedBackStack() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Create a PIN")
            enterPin("123456")
            waitForText("Confirm your PIN")
            enterPin("123456")

            // Empty vault: a clear call to action instead of an empty list.
            waitForText("Your vault is empty")
            compose.onNodeWithText("Add your first entry").performClick()

            waitForTag(EntryTags.TITLE)
            compose.onNodeWithTag(EntryTags.TITLE).performTextInput("GitHub test")
            compose.onNodeWithTag(EntryTags.USERNAME).performTextInput("octocat")
            compose.onNodeWithTag(EntryTags.SECRET).performTextInput("s3cret-Passw0rd!")
            compose.onNodeWithTag(EntryTags.SAVE).performScrollTo().performClick()

            // The first created entry brings up the one-time backup reminder on the dashboard.
            waitForText(BACKUP_REMINDER_TEXT)
            compose.onNodeWithText("Позже").performClick()
            waitUntilGone(BACKUP_REMINDER_TEXT)

            // Search: a miss shows its own empty state, a hit shows the entry.
            compose.onNodeWithTag(DashboardTags.SEARCH).performTextInput("zzz")
            waitForText("No entries match", substring = true)
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
            waitForText("Enter your PIN to unlock")
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
        } finally {
            scenario.close()
        }
    }

    @Test
    fun copiedSecretsAreMarkedSensitive() {
        val clip = SensitiveClipboard.sensitiveClip("s3cret")

        assertTrue(clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        assertEquals(30_000L, SensitiveClipboard.CLEAR_AFTER_MS)
    }

    private fun enterPin(pin: String) {
        pin.forEach { digit -> compose.onNodeWithText(digit.toString()).performClick() }
    }

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
