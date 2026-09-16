package com.obscura.data.local

/** Id and last update time of a vault entry, as returned by [VaultDao.getEntryVersions]. */
data class EntryVersion(val id: String, val updatedAt: Long)
