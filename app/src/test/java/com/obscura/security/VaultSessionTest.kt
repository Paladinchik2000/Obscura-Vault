package com.obscura.security

import com.obscura.data.local.FakeVaultDao
import com.obscura.data.local.FakeVaultDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import javax.crypto.spec.SecretKeySpec

class VaultSessionTest {

    private val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val dao = FakeVaultDao()
    private val dek = SecretKeySpec(ByteArray(32), "AES")

    @After
    fun tearDown() = runBlocking { VaultSession.lock() }

    @Test
    fun lockWaitsForRunningQueryBeforeClosingDatabase() = runBlocking {
        VaultSession.unlock(dek, FakeVaultDatabase(dao, events))
        val started = CompletableDeferred<Unit>()

        // Same shape as Room's performSuspending: a blocking query inside withContext,
        // which cancellation cannot interrupt.
        VaultSession.launchInSession {
            withContext(Dispatchers.IO) {
                started.complete(Unit)
                Thread.sleep(300)
                events += "query-finished"
            }
        }
        started.await()

        VaultSession.lock()

        assertEquals(listOf("query-finished", "db-closed"), events.toList())
        assertFalse(VaultSession.isUnlocked.value)
    }

    @Test
    fun workIsRefusedWhileLocked() = runBlocking {
        VaultSession.unlock(dek, FakeVaultDatabase(dao, events))
        VaultSession.lock()

        assertTrue(failure { VaultSession.runInSession { } } is VaultLockedException)
        assertTrue(failure { VaultSession.launchInSession { } } is VaultLockedException)
        assertTrue(failure { VaultSession.requireKey() } is VaultLockedException)
    }

    @Test
    fun runInSessionFailsWithVaultLockedWhenLockedMidway() = runBlocking {
        VaultSession.unlock(dek, FakeVaultDatabase(dao, events))
        val started = CompletableDeferred<Unit>()

        val work = async {
            failure { VaultSession.runInSession { started.complete(Unit); awaitCancellation() } }
        }
        started.await()
        VaultSession.lock()

        assertTrue(work.await() is VaultLockedException)
    }

    @Test
    fun databaseIsOnlyReachableFromSessionScope() = runBlocking {
        val database = FakeVaultDatabase(dao, events)
        VaultSession.unlock(dek, database)

        val outside = failure { VaultSession.requireDatabase() }
        assertTrue(outside is IllegalStateException && outside !is VaultLockedException)

        assertTrue(VaultSession.runInSession { VaultSession.requireDatabase() } === database)
    }

    @Test
    fun lockFromInsideSessionScopeIsRejected() = runBlocking {
        VaultSession.unlock(dek, FakeVaultDatabase(dao, events))

        val error = failure { VaultSession.runInSession { VaultSession.lock() } }

        assertTrue(error is IllegalStateException && error !is VaultLockedException)
        assertTrue(VaultSession.isUnlocked.value)
    }

    @Test
    fun observeCompletesWhenVaultLocks() = runBlocking {
        VaultSession.unlock(dek, FakeVaultDatabase(dao, events))
        val collected: MutableList<Int> = Collections.synchronizedList(mutableListOf())
        val firstValue = CompletableDeferred<Unit>()

        val collector = async {
            VaultSession.observe { VaultSession.requireDatabase().vaultDao().getEntriesCount() }
                .collect { collected += it; firstValue.complete(Unit) }
        }
        withTimeout(5_000) { firstValue.await() }

        VaultSession.lock()

        withTimeout(5_000) { collector.await() }
        assertEquals(listOf(0), collected.toList())
        assertEquals(listOf("db-closed"), events.toList())
    }

    @Test
    fun observeWhileLockedCompletesWithoutEmitting() = runBlocking {
        assertEquals(emptyList<Int>(), VaultSession.observe { flowOf(1) }.toList())
    }

    private suspend fun failure(block: suspend () -> Any?): Throwable? =
        try {
            block()
            null
        } catch (e: IllegalStateException) {
            e
        }
}
