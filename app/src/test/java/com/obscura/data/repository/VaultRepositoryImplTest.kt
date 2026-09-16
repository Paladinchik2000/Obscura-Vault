package com.obscura.data.repository

import com.obscura.data.local.FakeVaultDao
import com.obscura.data.local.FakeVaultDatabase
import com.obscura.data.local.VaultEntity
import com.obscura.security.VaultSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class VaultRepositoryImplTest {

    private val dao = FakeVaultDao()

    @Before
    fun setUp() = runBlocking {
        VaultSession.unlock(SecretKeySpec(ByteArray(32), "AES"), FakeVaultDatabase(dao, Collections.synchronizedList(mutableListOf())))
    }

    @After
    fun tearDown() = runBlocking { VaultSession.lock() }

    @Test
    fun newEntryGetsRandomUuidAndMatchingTimestamps() = runBlocking {
        val before = System.currentTimeMillis()

        VaultRepositoryImpl().saveEntry(VaultEntity(id = "", title = "New", category = "account", createdAt = 1, updatedAt = 1))

        val saved = dao.entriesFlow.value.single()
        UUID.fromString(saved.id)
        assertEquals(saved.createdAt, saved.updatedAt)
        assertTrue(saved.createdAt >= before)
    }

    @Test
    fun everySaveOfAnExistingEntryMovesUpdatedAtForwardEvenIfTheClockIsBehind() = runBlocking {
        val repository = VaultRepositoryImpl()
        val inTheFuture = System.currentTimeMillis() + 60_000

        repository.saveEntry(VaultEntity(id = "a", title = "A", category = "account", createdAt = 5, updatedAt = inTheFuture))
        val first = dao.entriesFlow.value.single()
        assertEquals(inTheFuture + 1, first.updatedAt)
        assertEquals(5L, first.createdAt)

        repository.saveEntry(first.copy(title = "A2"))
        assertEquals(inTheFuture + 2, dao.entriesFlow.value.single().updatedAt)
    }

    @Test
    fun savingAnOldEntryUsesTheCurrentTime() = runBlocking {
        val before = System.currentTimeMillis()

        VaultRepositoryImpl().saveEntry(VaultEntity(id = "a", title = "A", category = "account", createdAt = 5, updatedAt = 1_000))

        assertTrue(dao.entriesFlow.value.single().updatedAt >= before)
    }
}
