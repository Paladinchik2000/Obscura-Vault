package com.obscura.data.local

import androidx.room.InvalidationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Stand-in for the Room database: never opens a file, records when it is closed. */
class FakeVaultDatabase(
    private val dao: VaultDao,
    private val events: MutableList<String>
) : VaultDatabase() {

    override fun vaultDao(): VaultDao = dao

    override fun createInvalidationTracker(): InvalidationTracker =
        throw UnsupportedOperationException("not used by fakes")

    override fun clearAllTables() =
        throw UnsupportedOperationException("not used by fakes")

    override fun close() {
        events += "db-closed"
    }
}

class FakeVaultDao(
    private val onGetAllEntriesDirect: suspend () -> List<VaultEntity> = { emptyList() }
) : VaultDao {

    /** Backs every entry query, like a Room flow that never completes on its own. */
    val entriesFlow = MutableStateFlow<List<VaultEntity>>(emptyList())
    val countFlow = MutableStateFlow(0)

    override fun getAllEntries(): Flow<List<VaultEntity>> = entriesFlow
    override fun getEntriesByCategory(category: String): Flow<List<VaultEntity>> = entriesFlow
    override fun searchEntries(query: String): Flow<List<VaultEntity>> = entriesFlow
    override fun getEntriesCount(): Flow<Int> = countFlow

    override suspend fun getAllEntriesDirect(): List<VaultEntity> = onGetAllEntriesDirect()

    override suspend fun insertEntry(entry: VaultEntity) {
        entriesFlow.value = entriesFlow.value.filterNot { it.id == entry.id } + entry
    }

    override suspend fun getEntryVersions(): List<EntryVersion> = unused()
    override suspend fun getEntryById(id: String): VaultEntity? = unused()
    override suspend fun updateEntry(entry: VaultEntity) = unused()
    override suspend fun deleteEntry(entry: VaultEntity) = unused()
    override suspend fun deleteEntryById(id: String) = unused()
    override suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean, timestamp: Long) = unused()
    override suspend fun clearAll() = unused()
    override suspend fun insertAll(entries: List<VaultEntity>) = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("not used by fakes")
}
