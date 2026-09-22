package com.obscura.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.Keep
import com.obscura.data.local.EntryLink
import com.obscura.data.local.LinkType
import com.obscura.data.local.VaultEntity
import com.obscura.security.BackupCryptoUtils
import com.obscura.security.BackupFormatException
import com.obscura.security.UnsupportedBackupVersionException
import com.obscura.security.VaultSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
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
 * Everything a backup file carries: the entries and the links that say which apps and sites they
 * belong to. A version 1 file has no links, so [links] is simply empty for those.
 */
@Keep
data class BackupContents(
    val entries: List<VaultEntity>,
    val links: List<EntryLink> = emptyList()
)

/** How imported entries are combined with what is already in the vault. */
enum class ImportMode {
    /** Delete every existing entry, then insert the entries from the backup. */
    REPLACE,

    /**
     * Add ids the vault doesn't have; for an id it has, keep whichever copy has the later
     * updatedAt (the vault's copy on a tie). See [planMerge].
     */
    MERGE
}

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
     * Exports all Vault entries into an encrypted file at [uri]. [uri] must be a new document
     * (as created by ACTION_CREATE_DOCUMENT): if anything fails — including the vault locking
     * midway — the document is deleted so no empty or partial backup is left behind.
     */
    suspend fun exportVaultToFile(uri: Uri, password: CharArray): Result<Int> =
        exportVault(password) { context.contentResolver.openOutputStream(uri, "wt") }
            .onFailure { deleteQuietly(uri) }

    /**
     * Reads formatVersion from the clear-text header only; no password is involved.
     * Fails with UnsupportedBackupVersionException for another version and with
     * BackupFormatException if the file is not an Obscura backup.
     */
    suspend fun readFormatVersion(uri: Uri): Result<Int> = resultOf {
        withContext(Dispatchers.IO) {
            val header = ByteArray(BackupCryptoUtils.HEADER_SIZE_BYTES)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open input stream for SAF URI: $uri")
            input.use {
                try {
                    DataInputStream(it).readFully(header)
                } catch (e: EOFException) {
                    throw BackupFormatException("Not an Obscura Vault backup file")
                }
            }
            val version = BackupCryptoUtils.readFormatVersion(header)
            if (version != BackupCryptoUtils.FORMAT_VERSION) {
                throw UnsupportedBackupVersionException(version, BackupCryptoUtils.FORMAT_VERSION)
            }
            version
        }
    }

    /**
     * Decrypts and parses a backup without writing anything. Runs in the vault session, so
     * plaintext entries are only produced while the vault is unlocked.
     */
    suspend fun decryptBackup(uri: Uri, password: CharArray): Result<BackupContents> = resultOf {
        VaultSession.runInSession {
            val encryptedBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not open input stream for SAF URI: $uri")
            deserializeBackupJson(BackupCryptoUtils.decryptPayload(encryptedBytes, password))
        }
    }

    /** What each import mode would do with [entries] against the vault as it is now; writes nothing. */
    suspend fun previewImport(contents: BackupContents): Result<ImportPreview> = resultOf {
        VaultSession.runInSession {
            val existing = VaultSession.requireDatabase().vaultDao().getEntryVersions()
                .associate { it.id to it.updatedAt }
            ImportPreview(
                backupEntryCount = newestPerId(contents.entries).size,
                existingEntryCount = existing.size,
                merge = planMerge(existing, contents.entries).counts
            )
        }
    }

    /**
     * Writes [entries] in one transaction: a failure or a lock midway rolls the whole import back.
     * MERGE plans again inside the transaction, so an entry edited after [previewImport] is still
     * never overwritten by an older copy from the file.
     */
    suspend fun restore(contents: BackupContents, mode: ImportMode): Result<ImportResult> = resultOf {
        VaultSession.runInTransaction {
            val dao = VaultSession.requireDatabase().vaultDao()
            when (mode) {
                ImportMode.REPLACE -> {
                    val removed = dao.getEntryVersions().size
                    val incoming = newestPerId(contents.entries)
                    dao.clearAllLinks()
                    dao.clearAll()
                    dao.insertAll(incoming)
                    dao.insertAllLinks(contents.linksOf(incoming))
                    ImportResult(mode, added = incoming.size, updated = 0, unchanged = 0, removed = removed)
                }
                ImportMode.MERGE -> {
                    val plan = planMerge(
                        dao.getEntryVersions().associate { it.id to it.updatedAt },
                        contents.entries
                    )
                    val written = plan.toAdd + plan.toUpdate
                    dao.insertAll(written)
                    // Links follow their entry: only the entries taken from the file get theirs
                    // replaced, entries the vault kept keep the links they already had.
                    dao.deleteLinksOfEntries(written.map { it.id })
                    dao.insertAllLinks(contents.linksOf(written))
                    ImportResult(
                        mode,
                        added = plan.toAdd.size,
                        updated = plan.toUpdate.size,
                        unchanged = plan.unchangedCount,
                        removed = 0
                    )
                }
            }
        }
    }

    /** Links belonging to [entries]; a link whose entry is not written would break the foreign key. */
    private fun BackupContents.linksOf(entries: List<VaultEntity>): List<EntryLink> {
        val ids = entries.mapTo(HashSet()) { it.id }
        return links.filter { it.entryId in ids }
    }

    /**
     * Decrypts and restores in one call.
     * Fails with UnsupportedBackupVersionException for backups of another format version.
     */
    suspend fun importVaultFromFile(
        uri: Uri,
        password: CharArray,
        mode: ImportMode = ImportMode.MERGE
    ): Result<ImportResult> =
        decryptBackup(uri, password).fold(
            onSuccess = { contents -> restore(contents, mode) },
            onFailure = { Result.failure(it) }
        )

    private fun deleteQuietly(uri: Uri) {
        runCatching {
            when {
                uri.scheme == ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).delete() }
                DocumentsContract.isDocumentUri(context, uri) ->
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                else -> Unit
            }
        }
    }

    internal companion object {

        /** Export pipeline, separated from SAF so it can be tested without a Context. */
        internal suspend fun exportVault(password: CharArray, openOutput: () -> OutputStream?): Result<Int> =
            resultOf {
                VaultSession.runInSession {
                    val dao = VaultSession.requireDatabase().vaultDao()
                    val contents = BackupContents(dao.getAllEntriesDirect(), dao.getAllLinksDirect())
                    val entries = contents.entries
                    val encryptedBytes = BackupCryptoUtils.encryptPayload(serializeBackupJson(contents), password)

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

private fun serializeBackupJson(contents: BackupContents): String {
    val entries = contents.entries
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

        val linkArray = JSONArray()
        contents.links.forEach { link ->
            linkArray.put(
                JSONObject().apply {
                    put("id", link.id)
                    put("entryId", link.entryId)
                    put("type", link.type)
                    put("value", link.value)
                    put("certSha256", link.certSha256)
                }
            )
        }
        put("links", linkArray)
    }
    return root.toString(2)
}

private fun deserializeBackupJson(jsonString: String): BackupContents {
    val root = JSONObject(jsonString)

    // A missing field reads as -1 and is rejected like any other unknown version.
    val formatVersion = root.optInt("formatVersion", -1)
    if (formatVersion !in BackupCryptoUtils.SUPPORTED_FORMAT_VERSIONS) {
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

    // Version 1 files have no links at all; anything pointing at an entry the file does not
    // carry is dropped, because it could not be written without breaking the foreign key.
    val entryIds = result.mapTo(HashSet()) { it.id }
    val links = ArrayList<EntryLink>()
    val linkArray = root.optJSONArray("links")
    for (i in 0 until (linkArray?.length() ?: 0)) {
        val obj = linkArray!!.getJSONObject(i)
        val entryId = obj.optString("entryId", "")
        val type = obj.optString("type", "")
        val value = obj.optString("value", "")
        if (entryId !in entryIds || LinkType.fromId(type) == null || value.isEmpty()) continue
        links.add(
            EntryLink(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                entryId = entryId,
                type = type,
                value = value,
                certSha256 = obj.optString("certSha256", "")
            )
        )
    }

    return BackupContents(result, links)
}
