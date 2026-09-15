package com.obscura.data.backup

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.obscura.data.local.VaultEntity
import com.obscura.security.BackupCryptoUtils
import com.obscura.security.UnsupportedBackupVersionException
import com.obscura.security.VaultSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * BackupPayload
 *
 * Container data structure for encrypted Obscura Vault backups.
 */
@Keep
data class BackupPayload(
    val formatVersion: Int = BackupCryptoUtils.FORMAT_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val app: String = "Obscura Vault",
    val entriesCount: Int,
    val entries: List<VaultEntity>
)

/**
 * BackupManager
 *
 * Manages full database export and import using Storage Access Framework (SAF)
 * and AES-256-GCM encryption with PBKDF2 password derivation. See [BackupCryptoUtils]
 * for the file layout; the JSON inside starts with the same formatVersion.
 *
 * The database comes from [VaultSession.requireDatabase] and all work — including the
 * file write — runs inside the session scope: it is refused while the vault is locked
 * (Result.failure with VaultLockedException) and cancelled if the vault locks midway.
 */
@Keep
class BackupManager(private val context: Context) {

    /**
     * Exports all Vault entries into an encrypted JSON file using a user-supplied password.
     */
    suspend fun exportVaultToFile(uri: Uri, password: CharArray): Result<Int> =
        exportVault(password) { context.contentResolver.openOutputStream(uri) }

    /**
     * Imports entries from an encrypted JSON backup file and saves them into the Room DB.
     * Fails with UnsupportedBackupVersionException for backups of another format version.
     */
    suspend fun importVaultFromFile(uri: Uri, password: CharArray): Result<Int> = resultOf {
        VaultSession.runInSession {
            val encryptedBytes = context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                inputStream.readBytes()
            } ?: throw IllegalStateException("Could not open input stream for SAF URI: $uri")

            val decryptedJson = BackupCryptoUtils.decryptPayload(encryptedBytes, password)
            val importedEntries = deserializeJsonToEntries(decryptedJson)

            // One transaction for all rows: a lock or failure midway rolls the whole import back.
            VaultSession.runInTransaction {
                VaultSession.requireDatabase().vaultDao().insertAll(importedEntries)
            }
            importedEntries.size
        }
    }

    internal companion object {

        /** Export pipeline, separated from SAF so it can be tested without a Context. */
        internal suspend fun exportVault(password: CharArray, openOutput: () -> OutputStream?): Result<Int> =
            resultOf {
                VaultSession.runInSession {
                    val entries = VaultSession.requireDatabase().vaultDao().getAllEntriesDirect()
                    val encryptedBytes = BackupCryptoUtils.encryptPayload(serializeEntriesToJson(entries), password)

                    // Don't start writing if the vault locked while the payload was being built.
                    ensureActive()
                    openOutput()?.use { outputStream: OutputStream ->
                        outputStream.write(encryptedBytes)
                        outputStream.flush()
                    } ?: throw IllegalStateException("Could not open output stream for the backup file")

                    entries.size
                }
            }
    }
}

/** Like runCatching, but never swallows coroutine cancellation. */
private inline fun <T> resultOf(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

private fun serializeEntriesToJson(entries: List<VaultEntity>): String {
    val root = JSONObject().apply {
        // Must stay the first key: readers check it before touching anything else.
        put("formatVersion", BackupCryptoUtils.FORMAT_VERSION)
        put("timestamp", System.currentTimeMillis())
        put("app", "Obscura Vault")
        put("entriesCount", entries.size)

        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("title", entry.title)
                put("category", entry.category)
                put("usernameOrCardholder", entry.usernameOrCardholder)
                put("secretValue", entry.secretValue)
                put("urlOrCardNumber", entry.urlOrCardNumber)
                put("notesOrCvv", entry.notesOrCvv)
                put("expiryDate", entry.expiryDate)
                put("tags", entry.tags)
                put("isFavorite", entry.isFavorite)
                put("createdAt", entry.createdAt)
                put("updatedAt", entry.updatedAt)
            }
            array.put(obj)
        }
        put("entries", array)
    }
    return root.toString(2)
}

private fun deserializeJsonToEntries(jsonString: String): List<VaultEntity> {
    val root = JSONObject(jsonString)

    // A missing field reads as -1 and is rejected like any other unknown version.
    val formatVersion = root.optInt("formatVersion", -1)
    if (formatVersion != BackupCryptoUtils.FORMAT_VERSION) {
        throw UnsupportedBackupVersionException(formatVersion, BackupCryptoUtils.FORMAT_VERSION)
    }

    val array = root.getJSONArray("entries")
    val result = ArrayList<VaultEntity>(array.length())

    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        result.add(
            VaultEntity(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                title = obj.getString("title"),
                category = obj.optString("category", "LOGIN"),
                usernameOrCardholder = obj.optString("usernameOrCardholder", ""),
                secretValue = obj.optString("secretValue", ""),
                urlOrCardNumber = obj.optString("urlOrCardNumber", ""),
                notesOrCvv = obj.optString("notesOrCvv", ""),
                expiryDate = obj.optString("expiryDate", ""),
                tags = obj.optString("tags", ""),
                isFavorite = obj.optBoolean("isFavorite", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        )
    }
    return result
}
