package com.obscura.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.data.local.VaultEntity
import com.obscura.data.repository.VaultRepositoryImpl
import com.obscura.security.BackupCryptoUtils
import com.obscura.security.UnsupportedBackupVersionException
import com.obscura.security.VaultSession
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/** Export and import against the real encrypted database. */
@RunWith(AndroidJUnit4::class)
class BackupFormatTest {

    private companion object {
        const val DATABASE_NAME = "obscura_encrypted_vault.db" // must match VaultDatabase
    }

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dek = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
    private val backupFile = File(context.cacheDir, "backup-format-test.obvb")
    private val uri = Uri.fromFile(backupFile)
    private val password = "correct horse battery staple"
    private val entry = VaultEntity(id = "entry-1", title = "GitHub", category = "account", secretValue = "hunter2")

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        backupFile.delete()
    }

    @After
    fun tearDown() = runBlocking {
        VaultSession.lock()
        context.deleteDatabase(DATABASE_NAME)
        backupFile.delete()
        Unit
    }

    @Test
    fun exportWritesVersionedFileThatImportsIntoEmptyVault() = runBlocking {
        VaultSession.unlock(context, dek)
        VaultRepositoryImpl().saveEntry(entry)

        assertEquals(1, BackupManager(context).exportVaultToFile(uri, password.toCharArray()).getOrThrow())

        val bytes = backupFile.readBytes()
        assertEquals("OBVB", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(BackupCryptoUtils.FORMAT_VERSION, BackupCryptoUtils.readFormatVersion(bytes))
        val json = JSONObject(BackupCryptoUtils.decryptPayload(bytes, password.toCharArray()))
        assertEquals("formatVersion", json.keys().next())
        assertEquals(BackupCryptoUtils.FORMAT_VERSION, json.getInt("formatVersion"))

        // Restore into a brand-new vault database.
        VaultSession.lock()
        context.deleteDatabase(DATABASE_NAME)
        VaultSession.unlock(context, dek)

        assertEquals(1, BackupManager(context).importVaultFromFile(uri, password.toCharArray()).getOrThrow())
        assertEquals("hunter2", VaultRepositoryImpl().getEntryById(entry.id)?.secretValue)
    }

    @Test
    fun importRejectsUnknownPayloadVersionWithClearError() = runBlocking {
        VaultSession.unlock(context, dek)
        backupFile.writeBytes(
            BackupCryptoUtils.encryptPayload("""{"formatVersion":99,"entries":[]}""", password.toCharArray())
        )

        val error = BackupManager(context).importVaultFromFile(uri, password.toCharArray()).exceptionOrNull()

        assertTrue("expected UnsupportedBackupVersionException, got $error", error is UnsupportedBackupVersionException)
        assertEquals(99, (error as UnsupportedBackupVersionException).found)
    }
}
