package com.obscura.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.security.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 * The vault database holds the only copy of the data, so schema changes migrate it instead of
 * dropping it. These open a real database at an old version, migrate it and check the rows
 * survived.
 */
@RunWith(AndroidJUnit4::class)
class VaultDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    private val passphrase =
        DatabaseKey.sqlCipherRawKey(SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES"))

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        SupportOpenHelperFactory(passphrase)
    )

    @Test
    fun migrate1To2KeepsEntriesAndAddsLinks() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO vault_entries (" +
                    "id, title, category, usernameOrCardholder, secretValue, urlOrCardNumber, " +
                    "notesOrCvv, expiryDate, createdAt, updatedAt, lastAccessedAt, isFavorite, tags" +
                    ") VALUES ('e1', 'GitHub', 'account', 'octocat', 's3cret', 'https://github.com', " +
                    "'', '', 1000, 2000, 1500, 1, 'work')"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, VaultMigrations.MIGRATION_1_2)

        db.query("SELECT id, title, secretValue, updatedAt FROM vault_entries").use { cursor ->
            assertTrue("the entry must survive the migration", cursor.moveToFirst())
            assertEquals("e1", cursor.getString(0))
            assertEquals("GitHub", cursor.getString(1))
            assertEquals("s3cret", cursor.getString(2))
            assertEquals(2000L, cursor.getLong(3))
            assertEquals(1, cursor.count)
        }

        // The new table is usable and its foreign key points at a real entry.
        db.execSQL(
            "INSERT INTO entry_links (id, entryId, type, value, certSha256) " +
                "VALUES ('l1', 'e1', 'app', 'com.github.android', 'aa11')"
        )
        db.query("SELECT entryId, type, value, certSha256 FROM entry_links").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("e1", cursor.getString(0))
            assertEquals("app", cursor.getString(1))
            assertEquals("com.github.android", cursor.getString(2))
            assertEquals("aa11", cursor.getString(3))
        }
        db.close()
    }

    @Test
    fun deletingAnEntryTakesItsLinksWithIt() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, VaultMigrations.MIGRATION_1_2)

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO vault_entries (" +
                "id, title, category, usernameOrCardholder, secretValue, urlOrCardNumber, " +
                "notesOrCvv, expiryDate, createdAt, updatedAt, lastAccessedAt, isFavorite, tags" +
                ") VALUES ('e2', 'Bank', 'account', 'ivan', 'p', 'https://bank.com', '', '', 1, 1, 1, 0, '')"
        )
        db.execSQL(
            "INSERT INTO entry_links (id, entryId, type, value, certSha256) " +
                "VALUES ('l2', 'e2', 'domain', 'bank.com', '')"
        )

        db.execSQL("DELETE FROM vault_entries WHERE id = 'e2'")

        db.query("SELECT COUNT(*) FROM entry_links").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("a link must not outlive its entry", 0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate2To3CollapsesDuplicateLinksAndKeepsTheNewest() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            insertEntry(db, "e1")
            insertEntry(db, "e2")
            // What repeated "Remember this choice?" left behind before version 3: the same entry
            // and app three times, the last one with the certificate seen most recently.
            insertLink(db, "l1", "e1", "app", "com.example.app", "old1")
            insertLink(db, "l2", "e1", "app", "com.example.app", "old2")
            insertLink(db, "l3", "e1", "app", "com.example.app", "newest")
            // Not duplicates: another entry for the same app, and a domain for the same entry.
            insertLink(db, "l4", "e2", "app", "com.example.app", "cert")
            insertLink(db, "l5", "e1", "domain", "example.com", "")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, VaultMigrations.MIGRATION_2_3)

        assertEquals(
            listOf(
                "e1|app|com.example.app|newest",
                "e1|domain|example.com|",
                "e2|app|com.example.app|cert"
            ),
            links(db)
        )
        db.close()
    }

    @Test
    fun afterMigrationLinkingAgainReplacesInsteadOfAdding() {
        helper.createDatabase(TEST_DB, 2).use { db -> insertEntry(db, "e1") }
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, VaultMigrations.MIGRATION_2_3)

        // The DAO inserts links with REPLACE; a fresh id each time, as the picker creates one.
        db.execSQL(
            "INSERT OR REPLACE INTO entry_links (id, entryId, type, value, certSha256) " +
                "VALUES ('a', 'e1', 'app', 'com.example.app', 'first')"
        )
        db.execSQL(
            "INSERT OR REPLACE INTO entry_links (id, entryId, type, value, certSha256) " +
                "VALUES ('b', 'e1', 'app', 'com.example.app', 'second')"
        )

        assertEquals(listOf("e1|app|com.example.app|second"), links(db))
        db.close()
    }

    @Test
    fun migrate1To3InOneGo() {
        helper.createDatabase(TEST_DB, 1).use { db -> insertEntry(db, "e1") }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, *VaultMigrations.ALL)

        db.query("SELECT COUNT(*) FROM vault_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    private fun insertEntry(db: SupportSQLiteDatabase, id: String) {
        db.execSQL(
            "INSERT INTO vault_entries (" +
                "id, title, category, usernameOrCardholder, secretValue, urlOrCardNumber, " +
                "notesOrCvv, expiryDate, createdAt, updatedAt, lastAccessedAt, isFavorite, tags" +
                ") VALUES ('$id', 'Title', 'account', 'user', 'p', '', '', '', 1, 1, 1, 0, '')"
        )
    }

    private fun insertLink(
        db: SupportSQLiteDatabase,
        id: String,
        entryId: String,
        type: String,
        value: String,
        cert: String
    ) {
        db.execSQL(
            "INSERT INTO entry_links (id, entryId, type, value, certSha256) " +
                "VALUES ('$id', '$entryId', '$type', '$value', '$cert')"
        )
    }

    /** Every link as "entryId|type|value|cert", sorted. */
    private fun links(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT entryId, type, value, certSha256 FROM entry_links").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0..3).joinToString("|") { cursor.getString(it) })
                }
            }.sorted()
        }
}
