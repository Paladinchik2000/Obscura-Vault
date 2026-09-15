package com.obscura.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.obscura.data.local.FakeVaultDao
import com.obscura.data.local.FakeVaultDatabase
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import com.obscura.data.repository.VaultRepositoryImpl
import com.obscura.security.VaultSession
import com.obscura.ui.backup.BackupReminderStore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class VaultViewModelTest {

    // A real single thread standing in for Android's main thread.
    private val mainThread = newSingleThreadContext("test-main")
    private val store = ViewModelStore()
    private val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val dek = SecretKeySpec(ByteArray(32), "AES")
    private val entry = VaultEntity(id = "1", title = "GitHub", category = "account", secretValue = "hunter2")

    private class FakeBackupReminderStore : BackupReminderStore {
        @Volatile var shown = false
        override fun wasShown() = shown
        override fun markShown() {
            shown = true
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThread)
    }

    @After
    fun tearDown() {
        store.clear()
        runBlocking { VaultSession.lock() }
        Dispatchers.resetMain()
        mainThread.close()
    }

    @Test
    fun lockWipesRecordDataWhileViewModelStaysAlive() = runBlocking {
        val dao = unlockWith(listOf(entry))
        val viewModel = newViewModel()
        viewModel.onSearchQueryChanged("git")
        viewModel.onCategoryFilterSelected(VaultCategory.ACCOUNT)
        withTimeout(5_000) { viewModel.uiState.first { it.totalEntriesCount == 1 } }

        VaultSession.lock()

        withTimeout(5_000) { viewModel.vaultEntries.first { it.isEmpty() } }
        drainMainThread()
        assertEquals(emptyList<VaultEntity>(), viewModel.vaultEntries.value)
        assertEquals(VaultUiState(), viewModel.uiState.value)
        assertEquals(listOf(entry), dao.entriesFlow.value) // only the copy held by the ViewModel is gone
    }

    @Test
    fun entriesEmittedAfterLockNeverReachViewModel() = runBlocking {
        val dao = unlockWith(listOf(entry))
        val viewModel = newViewModel()
        withTimeout(5_000) { viewModel.uiState.first { it.totalEntriesCount == 1 } }
        VaultSession.lock()
        withTimeout(5_000) { viewModel.vaultEntries.first { it.isEmpty() } }

        dao.entriesFlow.value = listOf(entry, entry.copy(id = "2"))
        drainMainThread()

        assertEquals(emptyList<VaultEntity>(), viewModel.vaultEntries.value)
        assertEquals(0, viewModel.uiState.value.totalEntriesCount)
    }

    @Test
    fun nextUnlockResumesObservation() = runBlocking {
        unlockWith(listOf(entry))
        val viewModel = newViewModel()
        withTimeout(5_000) { viewModel.uiState.first { it.totalEntriesCount == 1 } }
        VaultSession.lock()
        withTimeout(5_000) { viewModel.vaultEntries.first { it.isEmpty() } }

        unlockWith(listOf(entry, entry.copy(id = "2")))

        val resumed = withTimeout(5_000) { viewModel.uiState.first { it.totalEntriesCount == 2 } }
        assertEquals(listOf("1", "2"), resumed.entries.map { it.id })
    }

    @Test
    fun firstCreatedEntryShowsBackupReminderExactlyOnce() = runBlocking {
        unlockWith(emptyList())
        val reminder = FakeBackupReminderStore()
        val viewModel = newViewModel(reminder)

        viewModel.saveEntry(entry.copy(id = ""))
        withTimeout(5_000) { viewModel.uiState.first { it.showBackupReminder } }

        viewModel.onBackupReminderHandled()
        assertTrue(reminder.shown)
        assertFalse(viewModel.uiState.value.showBackupReminder)

        saveAndWait(viewModel, entry.copy(id = "", title = "Second"))
        assertFalse("reminder must not come back", viewModel.uiState.value.showBackupReminder)
    }

    @Test
    fun editingAnExistingEntryDoesNotShowBackupReminder() = runBlocking {
        unlockWith(listOf(entry))
        val viewModel = newViewModel(FakeBackupReminderStore())

        saveAndWait(viewModel, entry.copy(title = "GitHub (work)"))

        assertFalse(viewModel.uiState.value.showBackupReminder)
    }

    /** saveEntry sets the toast last, so seeing it means the reminder decision has been made. */
    private suspend fun saveAndWait(viewModel: VaultViewModel, toSave: VaultEntity) {
        viewModel.clearToast()
        viewModel.saveEntry(toSave)
        withTimeout(5_000) { viewModel.uiState.first { it.toastMessage != null } }
        drainMainThread()
    }

    private suspend fun unlockWith(entries: List<VaultEntity>): FakeVaultDao {
        val dao = FakeVaultDao().apply { entriesFlow.value = entries }
        VaultSession.unlock(dek, FakeVaultDatabase(dao, events))
        return dao
    }

    private fun newViewModel(reminder: BackupReminderStore = FakeBackupReminderStore()): VaultViewModel =
        ViewModelProvider(
            store,
            viewModelFactory { initializer { VaultViewModel(VaultRepositoryImpl(), reminder) } }
        )[VaultViewModel::class.java]

    /** Lets any work already queued on the main thread run before asserting. */
    private suspend fun drainMainThread() {
        repeat(3) { withContext(mainThread) { } }
    }
}
