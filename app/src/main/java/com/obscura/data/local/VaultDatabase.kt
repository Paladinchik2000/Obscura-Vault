package com.obscura.data.local

import android.content.Context
import androidx.annotation.Keep
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.obscura.security.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.crypto.SecretKey

/**
 * VaultDatabase
 * Room database encrypted with SQLCipher AES-256. The key is derived from the vault
 * DEK, so the database can only be opened while the vault is unlocked.
 *
 * Instances are owned by [com.obscura.security.VaultSession]; don't cache them elsewhere.
 */
@Keep
@Database(entities = [VaultEntity::class, EntryLink::class], version = 2, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao

    /**
     * The SQLCipher key this instance was opened with. sqlcipher-android neither copies nor
     * wipes it: it keeps this exact array and reads it again for every new connection, so it
     * has to stay intact while the database is open. Wiped in [close].
     */
    private var passphrase: ByteArray? = null

    override fun close() {
        try {
            // Synchronous: Room waits for in-flight operations and closes every connection here.
            super.close()
        } finally {
            passphrase?.fill(0)
            passphrase = null
        }
    }

    companion object {
        const val DATABASE_NAME = "obscura_encrypted_vault.db"

        /**
         * Builds a new instance keyed from [dek]. The caller must close it when the vault locks.
         * Don't enable Room's auto-close: reopening after [close] would find the key wiped.
         */
        fun open(context: Context, dek: SecretKey): VaultDatabase {
            val passphrase = DatabaseKey.sqlCipherRawKey(dek)
            try {
                val database = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    DATABASE_NAME
                )
                    // The factory keeps a reference to this array; no copy is made on either side.
                    .openHelperFactory(SupportOpenHelperFactory(passphrase))
                    // No destructive fallback: this database holds the only copy of the data.
                    .addMigrations(*VaultMigrations.ALL)
                    .build()
                database.passphrase = passphrase
                return database
            } catch (e: Throwable) {
                passphrase.fill(0)
                throw e
            }
        }
    }
}
