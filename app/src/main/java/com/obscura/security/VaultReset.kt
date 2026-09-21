package com.obscura.security

import android.content.Context
import com.obscura.data.Preferences
import com.obscura.data.local.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Erases the vault completely: there is nothing left to unlock afterwards and the next start
 * lands on PIN creation.
 *
 * Deliberately not cancellable: a half-wiped vault would be worse than either end state. The
 * caller runs it under [NonCancellable] too, because the screen it was started from disappears
 * as soon as the session locks.
 */
object VaultReset {

    suspend fun wipe(context: Context) = withContext(Dispatchers.IO + NonCancellable) {
        val app = context.applicationContext

        // The database must be closed before its files go, so lock() runs first.
        VaultSession.lock()
        deleteDatabaseFiles(app)

        Preferences.ALL.forEach { name ->
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }

        // Without these the wrapped DEKs could not be opened anyway; leaving them behind would
        // only keep dead key material in the Keystore.
        KeystoreCrypto.deleteBiometricKey()
        KeystoreCrypto.deletePepperKey()
    }

    private fun deleteDatabaseFiles(context: Context) {
        val database = context.getDatabasePath(VaultDatabase.DATABASE_NAME)
        // WAL and SHM hold recently written pages; deleting only the main file would leave them.
        listOf(database, File("${database.path}-wal"), File("${database.path}-shm")).forEach { it.delete() }
        // Picks up the journal and anything else Android tracks for this database.
        context.deleteDatabase(VaultDatabase.DATABASE_NAME)
    }
}
