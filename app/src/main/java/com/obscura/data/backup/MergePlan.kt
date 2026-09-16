package com.obscura.data.backup

import com.obscura.data.local.VaultEntity

/** Counts shown before the user picks how to import; nothing has been written yet. */
data class ImportPreview(
    val backupEntryCount: Int,
    val existingEntryCount: Int,
    val merge: MergeCounts
)

data class MergeCounts(
    val added: Int,
    val updated: Int,
    val unchanged: Int,
    /** Part of [unchanged]: entries whose copy in the vault is newer than the one in the file. */
    val keptNewer: Int
)

/** What an import actually did. */
data class ImportResult(
    val mode: ImportMode,
    val added: Int,
    val updated: Int,
    val unchanged: Int,
    val removed: Int
)

/** Per-entry decisions for [ImportMode.MERGE]. */
data class MergePlan(
    val toAdd: List<VaultEntity>,
    val toUpdate: List<VaultEntity>,
    val unchangedCount: Int,
    val keptNewerCount: Int
) {
    val counts: MergeCounts
        get() = MergeCounts(toAdd.size, toUpdate.size, unchangedCount, keptNewerCount)
}

/**
 * Merge rule: an id the vault doesn't have is added; for an id it has, the copy with the later
 * updatedAt wins. On a tie the vault's copy stays, so a merge never replaces an entry with one
 * that isn't strictly newer. Entries that exist only in the vault are not touched or counted.
 *
 * @param existingUpdatedAt updatedAt of every entry currently in the vault, by id
 */
fun planMerge(existingUpdatedAt: Map<String, Long>, incoming: List<VaultEntity>): MergePlan {
    val toAdd = mutableListOf<VaultEntity>()
    val toUpdate = mutableListOf<VaultEntity>()
    var unchanged = 0
    var keptNewer = 0

    for (entry in newestPerId(incoming)) {
        val existing = existingUpdatedAt[entry.id]
        when {
            existing == null -> toAdd += entry
            entry.updatedAt > existing -> toUpdate += entry
            else -> {
                unchanged++
                if (existing > entry.updatedAt) keptNewer++
            }
        }
    }
    return MergePlan(toAdd, toUpdate, unchanged, keptNewer)
}

/** Collapses duplicate ids within a backup to their most recently updated copy, keeping file order. */
fun newestPerId(entries: List<VaultEntity>): List<VaultEntity> =
    entries.groupBy { it.id }.values.map { copies -> copies.maxBy { it.updatedAt } }
