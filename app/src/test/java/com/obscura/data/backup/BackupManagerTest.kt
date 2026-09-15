package com.obscura.data.backup

import com.obscura.data.local.FakeVaultDao
import com.obscura.data.local.FakeVaultDatabase
import com.obscura.security.VaultLockedException
import com.obscura.security.VaultSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.spec.SecretKeySpec

class BackupManagerTest {

    private val outputOpened = AtomicBoolean(false)
    private val openOutput = { outputOpened.set(true); ByteArrayOutputStream() }

    @After
    fun tearDown() = runBlocking { VaultSession.lock() }

    @Test
    fun exportIsRefusedWhileVaultIsLocked() = runBlocking {
        val result = BackupManager.exportVault("backup-password".toCharArray(), openOutput)

        assertTrue(result.exceptionOrNull() is VaultLockedException)
        assertFalse("backup file must not be opened", outputOpened.get())
    }

    @Test
    fun exportLockedMidwayWritesNothing() = runBlocking {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val readStarted = CompletableDeferred<Unit>()
        val dao = FakeVaultDao(onGetAllEntriesDirect = { readStarted.complete(Unit); awaitCancellation() })
        VaultSession.unlock(SecretKeySpec(ByteArray(32), "AES"), FakeVaultDatabase(dao, events))

        val export = async { BackupManager.exportVault("backup-password".toCharArray(), openOutput) }
        readStarted.await()
        VaultSession.lock()

        assertTrue(export.await().exceptionOrNull() is VaultLockedException)
        assertFalse("backup file must not be opened", outputOpened.get())
        assertEquals(listOf("db-closed"), events.toList())
    }
}
