package com.obscura.data.local

import androidx.room.testing.MigrationTestHelper
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
 * dropping it. This opens a real version 1 database, migrates it and checks the rows survived.
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
}
