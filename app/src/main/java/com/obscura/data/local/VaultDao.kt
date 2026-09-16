package com.obscura.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Vault Entries.
 * Provides reactive Flow streams for offline-first architecture.
 */
@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_entries ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllEntries(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_entries WHERE category = :category ORDER BY isFavorite DESC, updatedAt DESC")
    fun getEntriesByCategory(category: String): Flow<List<VaultEntity>>

    @Query("""
        SELECT * FROM vault_entries 
        WHERE title LIKE '%' || :query || '%' 
           OR usernameOrCardholder LIKE '%' || :query || '%' 
           OR tags LIKE '%' || :query || '%'
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    fun searchEntries(query: String): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): VaultEntity?

    @Query("SELECT COUNT(*) FROM vault_entries")
    fun getEntriesCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntity)

    @Update
    suspend fun updateEntry(entry: VaultEntity)

    @Delete
    suspend fun deleteEntry(entry: VaultEntity)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)

    @Query("UPDATE vault_entries SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM vault_entries")
    suspend fun clearAll()

    // =========================================================
    // ДОБАВЛЕННЫЕ МЕТОДЫ ДЛЯ МЕНЕДЖЕРА БЭКАПОВ (BackupManager)
    // =========================================================

    @Query("SELECT * FROM vault_entries")
    suspend fun getAllEntriesDirect(): List<VaultEntity>

    /** Ids and update times only: enough to plan a merge without loading any secrets. */
    @Query("SELECT id, updatedAt FROM vault_entries")
    suspend fun getEntryVersions(): List<EntryVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<VaultEntity>)
}