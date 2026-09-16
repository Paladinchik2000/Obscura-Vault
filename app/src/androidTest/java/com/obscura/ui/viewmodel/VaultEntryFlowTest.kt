package com.obscura.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.data.local.VaultEntity
import com.obscura.data.repository.VaultRepositoryImpl
import com.obscura.security.VaultSession
import com.obscura.ui.backup.BackupReminderStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/** Create → search → edit → lock on the real SQLCipher database, through the vault ViewModel. */
@RunWith(AndroidJUnit4::class)
class VaultEntryFlowTest {

    private companion object {
        const val DATABASE_NAME = "obscura_encrypted_vault.db" // must match VaultDatabase
        const val TIMEOUT_MS = 15_000L
    }

    private class RecordingReminderStore : BackupReminderStore {
        @Volatile var shown = false
        override fun wasShown() = shown
        override fun markShown() {
            shown = true
        }
    }

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val dek = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        app.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        store.clear()
        runBlocking { VaultSession.lock() }
        app.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun createFindEditThenLockClearsList() = runBlocking {
        VaultSession.unlock(app, dek)
        val viewModel = ViewModelProvider(
            store,
            viewModelFactory { initializer { VaultViewModel(VaultRepositoryImpl(), RecordingReminderStore()) } }
        )[VaultViewModel::class.java]

        // Create
        viewModel.saveEntry(
            VaultEntity(id = "", title = "GitHub", category = "account", usernameOrCardholder = "octocat", secretValue = "s3cret")
        )
        val created = withTimeout(TIMEOUT_MS) { viewModel.vaultEntries.first { it.size == 1 } }.single()
        assertTrue(created.id.isNotBlank())
        assertEquals(created.createdAt, created.updatedAt)
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.showBackupReminder } }

        // Find by search: a miss, then a hit
        viewModel.onSearchQueryChanged("nothing-like-this")
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.searchQuery == "nothing-like-this" && it.entries.isEmpty() } }
        viewModel.onSearchQueryChanged("git")
        val found = withTimeout(TIMEOUT_MS) { viewModel.vaultEntries.first { list -> list.map { it.title } == listOf("GitHub") } }
        assertEquals(created.id, found.single().id)

        // Edit
        viewModel.startEditing(created.id)
        val editing = withTimeout(TIMEOUT_MS) { viewModel.editor.first { it is EditorState.Ready } } as EditorState.Ready
        assertEquals(created, editing.entry)
        viewModel.saveEntry(editing.entry!!.copy(title = "GitHub (work)"))
        viewModel.finishEditing()

        val edited = withTimeout(TIMEOUT_MS) {
            viewModel.vaultEntries.first { it.singleOrNull()?.title == "GitHub (work)" }
        }.single()
        assertEquals(created.id, edited.id)
        assertEquals(created.createdAt, edited.createdAt)
        assertTrue("updatedAt must move forward: ${created.updatedAt} -> ${edited.updatedAt}", edited.updatedAt > created.updatedAt)

        // Lock: the list and every other piece of record data in the ViewModel is gone
        VaultSession.lock()
        withTimeout(TIMEOUT_MS) { viewModel.vaultEntries.first { it.isEmpty() } }
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it == VaultUiState() } }
        assertEquals(EditorState.Idle, viewModel.editor.value)
    }
}
