package com.obscura.data.local

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.obscura.security.DatabaseKey
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Proves SQLCipher takes the `x'<hex>'` key as the raw AES key (no PBKDF2) and measures what
 * that means for open time.
 */
@RunWith(AndroidJUnit4::class)
class VaultDatabaseRawKeyTest {

    private companion object {
        const val TAG = "VaultDbRawKeyTest"
        const val DATABASE_NAME = "obscura_encrypted_vault.db" // must match VaultDatabase
        const val RAW_CONTRAST_DB = "raw-key-contrast.db"
        const val PASSPHRASE_CONTRAST_DB = "passphrase-contrast.db"
        const val PAGE_SIZE = 4096 // SQLCipher 4 default cipher_page_size
        const val RESERVE = 80 // per-page reserve: 16-byte IV + 64-byte HMAC-SHA512
    }

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val entry = VaultEntity(id = "entry-1", title = "GitHub", category = "account", secretValue = "hunter2")

    @Before
    fun setUp() = deleteAll()

    @After
    fun tearDown() = deleteAll()

    @Test
    fun firstPageDecryptsWithRawKeyWithoutKdf() = runBlocking {
        val dek = randomDek()
        VaultDatabase.open(context, dek).apply { vaultDao().insertEntry(entry) }.close()

        val page = ByteArray(PAGE_SIZE).also { page ->
            DataInputStream(context.getDatabasePath(DATABASE_NAME).inputStream()).use { it.readFully(page) }
        }
        val rawKeyLiteral = String(DatabaseKey.sqlCipherRawKey(dek), Charsets.US_ASCII)
        val key = fromHex(rawKeyLiteral.substring(2, 66))
        val iv = page.copyOfRange(PAGE_SIZE - RESERVE, PAGE_SIZE - RESERVE + 16)
        val encrypted = page.copyOfRange(16, PAGE_SIZE - RESERVE) // bytes 0..15 are the plaintext salt

        val plain = Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }.doFinal(encrypted)

        Log.i(
            TAG,
            "page1 key=${toHex(key)} iv=${toHex(iv)} ct32=${toHex(encrypted.copyOf(32))} pt8=${toHex(plain.copyOf(8))}"
        )
        // plain[0] is offset 16 of the SQLite header.
        assertEquals("page size (hi)", 0x10, plain[0].toInt() and 0xFF)
        assertEquals("page size (lo)", 0x00, plain[1].toInt() and 0xFF)
        assertEquals("reserved bytes per page", RESERVE, plain[4].toInt() and 0xFF)
        assertEquals("max embedded payload fraction", 64, plain[5].toInt() and 0xFF)
        assertEquals("min embedded payload fraction", 32, plain[6].toInt() and 0xFF)
        assertEquals("leaf payload fraction", 32, plain[7].toInt() and 0xFF)
    }

    @Test
    fun openTimeRawKeyVersusPassphrase() = runBlocking {
        val dek = randomDek()
        VaultDatabase.open(context, dek).apply { vaultDao().insertEntry(entry) }.close()
        val roomMs = timedRuns {
            val db = VaultDatabase.open(context, dek)
            db.vaultDao().getEntryById(entry.id) // Room opens lazily: this keys and opens the file
            db.close()
        }

        val rawFile = context.getDatabasePath(RAW_CONTRAST_DB)
        val passphraseFile = context.getDatabasePath(PASSPHRASE_CONTRAST_DB)
        rawFile.parentFile?.mkdirs()
        // Both files need real pages: SQLCipher derives the key lazily on the first page read,
        // so an empty file would never run the passphrase KDF and the comparison would mean nothing.
        for ((file, password) in listOf(
            rawFile to DatabaseKey.sqlCipherRawKey(dek),
            passphraseFile to "correct horse battery staple".toByteArray()
        )) {
            SQLiteDatabase.openOrCreateDatabase(file, password, null, null, null).apply {
                execSQL("CREATE TABLE t(x TEXT)")
                execSQL("INSERT INTO t VALUES ('row')")
                close()
            }
        }
        assertTrue("contrast files must not be empty", rawFile.length() > 0 && passphraseFile.length() > 0)
        val rawMs = timedRuns { openAndQuery(rawFile, DatabaseKey.sqlCipherRawKey(dek)) }
        val passphraseMs = timedRuns { openAndQuery(passphraseFile, "correct horse battery staple".toByteArray()) }

        Log.i(
            TAG,
            "open ms (5 runs after warm-up): room+rawKey=$roomMs median=${median(roomMs)}; " +
                "sqlcipher rawKey=$rawMs median=${median(rawMs)}; " +
                "sqlcipher passphrase=$passphraseMs median=${median(passphraseMs)}"
        )
        assertTrue(
            "raw key open (${median(rawMs)} ms) should be far faster than passphrase open (${median(passphraseMs)} ms)",
            median(rawMs) * 5 < median(passphraseMs)
        )
    }

    private fun openAndQuery(file: File, password: ByteArray) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, password, null, null, null)
        db.rawQuery("SELECT count(*) FROM sqlite_master", arrayOf<String>()).use { it.moveToFirst() }
        db.close()
    }

    /** One warm-up run, then five timed runs in milliseconds. */
    private inline fun timedRuns(block: () -> Unit): List<Double> {
        block()
        return List(5) {
            val start = SystemClock.elapsedRealtimeNanos()
            block()
            (SystemClock.elapsedRealtimeNanos() - start) / 10_000 / 100.0
        }
    }

    private fun median(values: List<Double>) = values.sorted()[values.size / 2]

    private fun deleteAll() {
        listOf(DATABASE_NAME, RAW_CONTRAST_DB, PASSPHRASE_CONTRAST_DB).forEach { context.deleteDatabase(it) }
    }

    private fun randomDek(): SecretKey = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")

    private fun fromHex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun toHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
