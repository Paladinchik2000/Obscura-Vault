package com.obscura.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations, never a destructive fallback: the vault database holds the only copy of the
 * user's data. The SQL below matches the exported schema in app/schemas byte for byte, which is
 * what VaultDatabaseMigrationTest checks.
 */
object VaultMigrations {

    /** Adds entry_links: ties between an entry and the apps or sites autofill may use it for. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `entry_links` (" +
                    "`id` TEXT NOT NULL, " +
                    "`entryId` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, " +
                    "`certSha256` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`entryId`) REFERENCES `vault_entries`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_links_entryId` ON `entry_links` (`entryId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_entry_links_type_value` ON `entry_links` (`type`, `value`)"
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
