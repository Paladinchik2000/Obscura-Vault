package com.obscura.data.local

import android.content.Context
import androidx.annotation.Keep
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.obscura.security.KeystoreManager
import net.sqlcipher.database.SupportFactory

/**
 * VaultDatabase
 * Single Source of Truth (SSOT) Room Database encrypted with SQLCipher AES-256.
 */
@Keep
@Database(entities = [VaultEntity::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): VaultDatabase {
            val keystoreManager = KeystoreManager(context)
            val passphrase = keystoreManager.getOrCreateDatabasePassphrase()
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context,
                VaultDatabase::class.java,
                "obscura_encrypted_vault.db"
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
