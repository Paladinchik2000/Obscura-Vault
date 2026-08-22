package com.obscura.data.repository

import androidx.annotation.Keep
import com.obscura.data.local.VaultDao
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
 * Concrete implementation executing all Room operations safely on Dispatchers.IO.
 */
@Keep
class VaultRepositoryImpl(private val vaultDao: VaultDao) : VaultRepository {

    override fun getAllEntries(): Flow<List<VaultEntity>> {
        return vaultDao.getAllEntries().flowOn(Dispatchers.IO)
    }

    override fun getEntriesByCategory(category: VaultCategory): Flow<List<VaultEntity>> {
        return vaultDao.getEntriesByCategory(category.id).flowOn(Dispatchers.IO)
    }

    override fun searchEntries(query: String): Flow<List<VaultEntity>> {
        return vaultDao.searchEntries(query).flowOn(Dispatchers.IO)
    }

    override suspend fun getEntryById(id: String): VaultEntity? {
        return withContext(Dispatchers.IO) {
            vaultDao.getEntryById(id)
        }
    }

    override suspend fun saveEntry(entry: VaultEntity) {
        withContext(Dispatchers.IO) {
            val entryToSave = if (entry.id.isBlank()) {
                entry.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
            } else {
                entry.copy(updatedAt = System.currentTimeMillis())
            }
            vaultDao.insertEntry(entryToSave)
        }
    }

    override suspend fun deleteEntry(id: String) {
        withContext(Dispatchers.IO) {
            vaultDao.deleteEntryById(id)
        }
    }

    override suspend fun toggleFavorite(id: String, currentStatus: Boolean) {
        withContext(Dispatchers.IO) {
            vaultDao.updateFavoriteStatus(id, !currentStatus)
        }
    }

    override fun getEntriesCount(): Flow<Int> {
        return vaultDao.getEntriesCount().flowOn(Dispatchers.IO)
    }
}
