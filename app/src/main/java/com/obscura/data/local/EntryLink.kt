package com.obscura.data.local

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** What an [EntryLink] points at. Stored as [id] so the column survives renames of the enum. */
@Keep
enum class LinkType(val id: String) {
    /** An installed app, identified by package name *and* signing certificate. */
    APP("app"),

    /** A site, stored as the registrable domain; subdomains of it match too. */
    DOMAIN("domain");

    companion object {
        fun fromId(id: String): LinkType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Ties a vault entry to an app or a site, so autofill knows what may be offered where.
 *
 * For [LinkType.APP] the package name alone is not enough: any app outside a store can claim a
 * package name, so [certSha256] pins the signing certificate as well.
 */
@Keep
@Entity(
    tableName = "entry_links",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId"), Index(value = ["type", "value"])]
)
data class EntryLink(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    /** [LinkType.id] */
    val type: String,
    /** Package name, or registrable domain in lower case. */
    val value: String,
    /** Apps only: lower-case hex SHA-256 of a signing certificate, empty for domains. */
    val certSha256: String = ""
) {
    fun typeOrNull(): LinkType? = LinkType.fromId(type)
}
