package com.obscura.ui.backup

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.data.backup.BackupManager
import com.obscura.data.backup.ImportMode
import com.obscura.data.backup.ImportPreview
import com.obscura.data.backup.ImportResult
import com.obscura.data.backup.MergeCounts
import com.obscura.data.local.VaultEntity
import com.obscura.security.UnsupportedBackupVersionException
import com.obscura.security.VaultLockedException
import com.obscura.security.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom
import java.util.Collections
import javax.crypto.spec.SecretKeySpec

/** Backup screen logic end to end: BackupViewModel + BackupManager + real SQLCipher database. */
@RunWith(AndroidJUnit4::class)
class BackupFlowTest {

    private companion object {
        const val TAG = "BackupFlowTest"
        const val DATABASE_NAME = "obscura_encrypted_vault.db" // must match VaultDatabase
        const val PASSWORD = "correct horse battery staple"
        const val TIMEOUT_MS = 60_000L
    }

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val backupFile = File(app.cacheDir, "backup-flow-test.obvb")
    private val uri = Uri.fromFile(backupFile)
    private val dek = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
    private val store = ViewModelStore()

    private val entries = listOf(
        VaultEntity(
            id = "a", title = "GitHub", category = "account", usernameOrCardholder = "octocat",
            secretValue = "hunter2-but-longer", urlOrCardNumber = "https://github.com", tags = "work",
            isFavorite = true, createdAt = 1_700_000_000_000, updatedAt = 1_700_000_100_000
        ),
        VaultEntity(
            id = "b", title = "Карта", category = "bank_card", usernameOrCardholder = "IVAN PETROV",
            secretValue = "4321", urlOrCardNumber = "4111111111111111", notesOrCvv = "123",
            expiryDate = "12/29", createdAt = 1_700_000_200_000, updatedAt = 1_700_000_300_000
        )
    )

    @Before
    fun setUp() {
        app.deleteDatabase(DATABASE_NAME)
        backupFile.delete()
    }

    @After
    fun tearDown() {
        store.clear()
        runBlocking { VaultSession.lock() }
        app.deleteDatabase(DATABASE_NAME)
        backupFile.delete()
    }

    @Test
    fun exportThenImportIntoEmptyVaultRestoresEveryEntry() = runBlocking {
        unlockWith(entries)
        val viewModel = newViewModel()

        viewModel.onExportPasswordChanged(PASSWORD)
        viewModel.onExportConfirmationChanged(PASSWORD)
        assertTrue(viewModel.exportState.value.canStart)
        viewModel.onExportDestinationChosen(uri)
        val exported = withTimeout(TIMEOUT_MS) {
            viewModel.exportState.first { it.phase is ExportPhase.Done || it.phase is ExportPhase.Failed }
        }
        assertEquals(ExportPhase.Done(2), exported.phase)
        assertEquals("password is cleared after export", "", exported.password)

        // A brand-new vault database on the same device.
        VaultSession.lock()
        app.deleteDatabase(DATABASE_NAME)
        VaultSession.unlock(app, dek)

        decryptThroughViewModel(viewModel, PASSWORD)
        val choose = awaitImport(viewModel) { it is ImportPhase.ChooseMode || it is ImportPhase.Failed }
        assertEquals(
            ImportPhase.ChooseMode(ImportPreview(2, 0, MergeCounts(added = 2, updated = 0, unchanged = 0, keptNewer = 0))),
            choose
        )

        viewModel.onImportModeChosen(ImportMode.REPLACE)
        val done = awaitImport(viewModel) { it is ImportPhase.Done || it is ImportPhase.Failed }
        assertEquals(ImportPhase.Done(ImportResult(ImportMode.REPLACE, added = 2, updated = 0, unchanged = 0, removed = 0)), done)

        // lastAccessedAt is not part of the backup format.
        assertEquals(entries.map { it.copy(lastAccessedAt = 0) }, vaultSnapshot().map { it.copy(lastAccessedAt = 0) })
    }

    @Test
    fun mergeKeepsNewerVaultEntriesAndShowsSummaryBeforeWriting() = runBlocking {
        val inFile = listOf(
            note("a", "A from file", updatedAt = 100), // the vault has a newer copy
            note("b", "B from file", updatedAt = 300), // the file has the newer copy
            note("d", "D from file", updatedAt = 100), // not in the vault
            note("e", "E", updatedAt = 100) // same updatedAt on both sides
        )
        val inVault = listOf(
            note("a", "A in vault", updatedAt = 200),
            note("b", "B in vault", updatedAt = 100),
            note("c", "C only in vault", updatedAt = 100),
            note("e", "E", updatedAt = 100)
        )
        unlockWith(inFile)
        BackupManager(app).exportVaultToFile(uri, PASSWORD.toCharArray()).getOrThrow()
        VaultSession.runInTransaction {
            val dao = VaultSession.requireDatabase().vaultDao()
            dao.clearAll()
            dao.insertAll(inVault)
        }

        val viewModel = newViewModel()
        decryptThroughViewModel(viewModel, PASSWORD)
        val choose = awaitImport(viewModel) { it is ImportPhase.ChooseMode || it is ImportPhase.Failed }
        assertEquals(
            ImportPhase.ChooseMode(ImportPreview(4, 4, MergeCounts(added = 1, updated = 1, unchanged = 2, keptNewer = 1))),
            choose
        )
        assertEquals("nothing is written before a mode is chosen", inVault, vaultSnapshot())

        viewModel.onImportModeChosen(ImportMode.MERGE)
        val done = awaitImport(viewModel) { it is ImportPhase.Done || it is ImportPhase.Failed }
        assertEquals(ImportPhase.Done(ImportResult(ImportMode.MERGE, added = 1, updated = 1, unchanged = 2, removed = 0)), done)

        assertEquals(
            mapOf("a" to "A in vault", "b" to "B from file", "c" to "C only in vault", "d" to "D from file", "e" to "E"),
            vaultSnapshot().associate { it.id to it.title }
        )
    }

    @Test
    fun wrongPasswordIsSecurityExceptionAndVaultIsUnchanged() = runBlocking {
        unlockWith(entries)
        BackupManager(app).exportVaultToFile(uri, PASSWORD.toCharArray()).getOrThrow()
        // Make the vault differ from the backup, so any write would be visible.
        VaultSession.runInSession {
            VaultSession.requireDatabase().vaultDao().insertEntry(VaultEntity(id = "c", title = "Only in vault", category = "secure_note"))
        }
        val before = vaultSnapshot()

        val direct = BackupManager(app).importVaultFromFile(uri, "definitely wrong".toCharArray(), ImportMode.REPLACE)
        assertTrue("expected SecurityException, got ${direct.exceptionOrNull()}", direct.exceptionOrNull() is SecurityException)

        val viewModel = newViewModel()
        viewModel.onImportFileChosen(uri)
        awaitImport(viewModel) { it is ImportPhase.PasswordRequired || it is ImportPhase.Failed }
        viewModel.onImportPasswordChanged("definitely wrong")
        viewModel.onImportDecrypt()
        val phase = awaitImport(viewModel) {
            (it is ImportPhase.PasswordRequired && it.error != null) || it is ImportPhase.Failed || it is ImportPhase.ChooseMode
        }
        assertEquals(ImportPhase.PasswordRequired(error = BackupMessages.WRONG_PASSWORD), phase)

        assertEquals(before, vaultSnapshot())
    }

    @Test
    fun unknownFormatVersionIsRejectedBeforeAskingForPassword() = runBlocking {
        unlockWith(entries)
        backupFile.writeBytes(byteArrayOf('O'.code.toByte(), 'B'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 0x00, 99) + ByteArray(64))

        val header = BackupManager(app).readFormatVersion(uri).exceptionOrNull()
        assertTrue("expected UnsupportedBackupVersionException, got $header", header is UnsupportedBackupVersionException)
        assertEquals(99, (header as UnsupportedBackupVersionException).found)

        val viewModel = newViewModel()
        val seen = Collections.synchronizedList(mutableListOf<ImportPhase>())
        val recorder = launch(Dispatchers.Default) { viewModel.importState.collect { seen += it.phase } }
        viewModel.onImportFileChosen(uri)
        val phase = awaitImport(viewModel) { it is ImportPhase.Failed || it is ImportPhase.PasswordRequired }
        recorder.cancel()

        assertEquals(ImportPhase.Failed(BackupMessages.unsupportedVersion(99, 1)), phase)
        assertTrue("password must never be requested, saw $seen", seen.none { it is ImportPhase.PasswordRequired })
    }

    @Test
    fun lockingDuringExportLeavesNoFile() = runBlocking {
        unlockWith(entries)
        // What the SAF picker does before handing us the Uri: an empty document already exists.
        assertTrue(backupFile.createNewFile())

        val started = System.nanoTime()
        val export = async(Dispatchers.Default) { BackupManager(app).exportVaultToFile(uri, PASSWORD.toCharArray()) }
        delay(300) // key derivation takes ~1.6 s here, so the lock lands in the middle of it
        VaultSession.lock()
        val result = export.await()
        Log.i(TAG, "export ended after ${(System.nanoTime() - started) / 1_000_000} ms with ${result.exceptionOrNull()}")

        assertTrue("expected VaultLockedException, got ${result.exceptionOrNull()}", result.exceptionOrNull() is VaultLockedException)
        assertFalse("backup file must not exist", backupFile.exists())
    }

    private fun note(id: String, title: String, updatedAt: Long) =
        VaultEntity(
            id = id, title = title, category = "secure_note", secretValue = "secret-$id",
            createdAt = 1, updatedAt = updatedAt, lastAccessedAt = 1
        )

    private suspend fun decryptThroughViewModel(viewModel: BackupViewModel, password: String) {
        viewModel.onImportFileChosen(uri)
        assertEquals(
            ImportPhase.PasswordRequired(),
            awaitImport(viewModel) { it is ImportPhase.PasswordRequired || it is ImportPhase.Failed }
        )
        viewModel.onImportPasswordChanged(password)
        viewModel.onImportDecrypt()
    }

    private suspend fun unlockWith(initial: List<VaultEntity>) {
        VaultSession.unlock(app, dek)
        VaultSession.runInSession { VaultSession.requireDatabase().vaultDao().insertAll(initial) }
    }

    private suspend fun vaultSnapshot(): List<VaultEntity> =
        VaultSession.runInSession { VaultSession.requireDatabase().vaultDao().getAllEntriesDirect() }.sortedBy { it.id }

    private fun newViewModel(): BackupViewModel =
        ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[BackupViewModel::class.java]

    private suspend fun awaitImport(viewModel: BackupViewModel, predicate: (ImportPhase) -> Boolean): ImportPhase =
        withTimeout(TIMEOUT_MS) { viewModel.importState.first { predicate(it.phase) } }.phase
}
