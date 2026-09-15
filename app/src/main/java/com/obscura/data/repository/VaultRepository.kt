package com.obscura.data.repository

import androidx.annotation.Keep
import com.obscura.data.local.VaultDao
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import com.obscura.security.VaultSession
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository interface for single source of truth (SSOT) data operations.
 */
@Keep
interface VaultRepository {
    fun getAllEntries(): Flow<List<VaultEntity>>
    fun getEntriesByCategory(category: VaultCategory): Flow<List<VaultEntity>>
    fun searchEntries(query: String): Flow<List<VaultEntity>>
    suspend fun getEntryById(id: String): VaultEntity?
    suspend fun saveEntry(entry: VaultEntity)
    suspend fun deleteEntry(id: String)
    suspend fun toggleFavorite(id: String, currentStatus: Boolean)
    fun getEntriesCount(): Flow<Int>
}

/**
 * Every DAO call runs inside the VaultSession scope, so locking the vault waits for
 * in-flight queries before the database is closed. Suspend calls fail with
 * VaultLockedException while locked; flows complete when the vault locks.
 *
 * Each write below is a single statement (and Room already wraps it in a transaction).
 * Any write that needs more than one statement must go through VaultSession.runInTransaction,
 * so a lock between steps rolls it back as a whole.
 */
@Keep
class VaultRepositoryImpl : VaultRepository {

    override fun getAllEntries(): Flow<List<VaultEntity>> =
        VaultSession.observe { dao().getAllEntries() }

    override fun getEntriesByCategory(category: VaultCategory): Flow<List<VaultEntity>> =
        VaultSession.observe { dao().getEntriesByCategory(category.id) }

    override fun searchEntries(query: String): Flow<List<VaultEntity>> =
        VaultSession.observe { dao().searchEntries(query) }

    override suspend fun getEntryById(id: String): VaultEntity? =
        VaultSession.runInSession { dao().getEntryById(id) }

    override suspend fun saveEntry(entry: VaultEntity) {
        VaultSession.runInSession {
            val entryToSave = if (entry.id.isBlank()) {
                entry.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
            } else {
                entry.copy(updatedAt = System.currentTimeMillis())
            }
            dao().insertEntry(entryToSave)
        }
    }

    override suspend fun deleteEntry(id: String) {
        VaultSession.runInSession { dao().deleteEntryById(id) }
    }

    override suspend fun toggleFavorite(id: String, currentStatus: Boolean) {
        VaultSession.runInSession { dao().updateFavoriteStatus(id, !currentStatus) }
    }

    override fun getEntriesCount(): Flow<Int> =
        VaultSession.observe { dao().getEntriesCount() }

    private suspend fun dao(): VaultDao = VaultSession.requireDatabase().vaultDao()
}
