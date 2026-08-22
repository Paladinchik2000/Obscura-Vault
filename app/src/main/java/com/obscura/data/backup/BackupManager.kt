package com.obscura.data.backup

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.obscura.data.local.VaultDatabase
import com.obscura.data.local.VaultEntity
import com.obscura.security.BackupCryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val version: Int = 2,
    val timestamp: Long = System.currentTimeMillis(),
    val app: String = "Obscura Vault",
    val entriesCount: Int,
    val entries: List<VaultEntity>
)

/**
 * BackupManager
 *
 * Manages full database export and import using Storage Access Framework (SAF)
 * and AES-256-GCM encryption with PBKDF2 password derivation.
 */
@Keep
class BackupManager(
    private val context: Context,
    private val database: VaultDatabase
) {

    /**
     * Exports all Vault entries into an encrypted JSON file using a user-supplied password.
     */
    suspend fun exportVaultToFile(uri: Uri, password: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = database.vaultDao().getAllEntriesDirect()
            val jsonPayload = serializeEntriesToJson(entries)
            val encryptedBytes = BackupCryptoUtils.encryptPayload(jsonPayload, password)

            context.contentResolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                outputStream.write(encryptedBytes)
                outputStream.flush()
            } ?: throw IllegalStateException("Could not open output stream for SAF URI: $uri")

            entries.size
        }
    }

    /**
     * Imports entries from an encrypted JSON backup file and saves them into the Room DB.
     */
    suspend fun importVaultFromFile(uri: Uri, password: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val encryptedBytes = context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                inputStream.readBytes()
            } ?: throw IllegalStateException("Could not open input stream for SAF URI: $uri")

            val decryptedJson = BackupCryptoUtils.decryptPayload(encryptedBytes, password)
            val importedEntries = deserializeJsonToEntries(decryptedJson)

            // Save to Room DB (Single Source of Truth)
            database.vaultDao().insertAll(importedEntries)
            importedEntries.size
        }
    }

    private fun serializeEntriesToJson(entries: List<VaultEntity>): String {
        val root = JSONObject().apply {
            put("version", 2)
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
}
