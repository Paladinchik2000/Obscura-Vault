package com.obscura.data.backup

import com.obscura.data.local.VaultEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MergePlanTest {

    private fun entry(id: String, updatedAt: Long, title: String = id) =
        VaultEntity(id = id, title = title, category = "secure_note", createdAt = 1, updatedAt = updatedAt)

    @Test
    fun idMissingFromVaultIsAdded() {
        val plan = planMerge(emptyMap(), listOf(entry("a", 100)))

        assertEquals(listOf("a"), plan.toAdd.map { it.id })
        assertEquals(MergeCounts(added = 1, updated = 0, unchanged = 0, keptNewer = 0), plan.counts)
    }

    @Test
    fun newerCopyInFileUpdatesVaultEntry() {
        val plan = planMerge(mapOf("a" to 100L), listOf(entry("a", 200, title = "from file")))

        assertEquals(listOf("from file"), plan.toUpdate.map { it.title })
        assertEquals(MergeCounts(added = 0, updated = 1, unchanged = 0, keptNewer = 0), plan.counts)
    }

    @Test
    fun newerCopyInVaultIsKeptAndReported() {
        val plan = planMerge(mapOf("a" to 300L), listOf(entry("a", 200)))

        assertEquals(emptyList<VaultEntity>(), plan.toAdd + plan.toUpdate)
        assertEquals(MergeCounts(added = 0, updated = 0, unchanged = 1, keptNewer = 1), plan.counts)
    }

    @Test
    fun equalTimestampsKeepVaultCopyWithoutCallingItNewer() {
        val plan = planMerge(mapOf("a" to 200L), listOf(entry("a", 200, title = "same time, other content")))

        assertEquals(emptyList<VaultEntity>(), plan.toAdd + plan.toUpdate)
        assertEquals(MergeCounts(added = 0, updated = 0, unchanged = 1, keptNewer = 0), plan.counts)
    }

    @Test
    fun entriesOnlyInVaultAreNeitherWrittenNorCounted() {
        val plan = planMerge(mapOf("a" to 100L, "only-in-vault" to 100L), listOf(entry("a", 100)))

        assertEquals(MergeCounts(added = 0, updated = 0, unchanged = 1, keptNewer = 0), plan.counts)
    }

    @Test
    fun duplicateIdsInFileCollapseToNewestCopy() {
        val incoming = listOf(entry("a", 100, title = "old"), entry("a", 300, title = "newest"), entry("a", 200, title = "middle"))

        assertEquals(listOf("newest"), newestPerId(incoming).map { it.title })
        val plan = planMerge(mapOf("a" to 250L), incoming)
        assertEquals(listOf("newest"), plan.toUpdate.map { it.title })
        assertEquals(MergeCounts(added = 0, updated = 1, unchanged = 0, keptNewer = 0), plan.counts)
    }

    @Test
    fun mixedFileProducesSeparateCounts() {
        val vault = mapOf("a" to 200L, "b" to 100L, "c" to 100L, "e" to 100L)
        val file = listOf(entry("a", 100), entry("b", 300), entry("d", 100), entry("e", 100))

        val plan = planMerge(vault, file)

        assertEquals(listOf("d"), plan.toAdd.map { it.id })
        assertEquals(listOf("b"), plan.toUpdate.map { it.id })
        assertEquals(MergeCounts(added = 1, updated = 1, unchanged = 2, keptNewer = 1), plan.counts)
    }
}
