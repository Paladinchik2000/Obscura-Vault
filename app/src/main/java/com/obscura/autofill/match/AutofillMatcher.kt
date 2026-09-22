package com.obscura.autofill.match

import com.obscura.data.local.EntryLink
import com.obscura.data.local.LinkType
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory

/** An entry together with the links saved for it. */
data class EntryWithLinks(val entry: VaultEntity, val links: List<EntryLink> = emptyList())

/**
 * What autofill is being asked to fill.
 *
 * [webHost] is set only when the request came from a browser we recognise by package *and*
 * signing certificate — see [TrustedBrowsers]. For every other app the domain in the layout is
 * ignored, because any app can put one there.
 */
data class FillTarget(
    val callerPackage: String,
    val callerCertificateHashes: Set<String>,
    val webHost: String? = null
) {
    companion object {
        /**
         * Builds the target for a request. The domain the caller put in its layout is kept only
         * when the caller is a trusted browser; for anyone else it is dropped here, once, so that
         * the rule cannot drift apart between the service and the tests.
         */
        fun of(packageName: String, certificateHashes: Set<String>, webDomain: String?): FillTarget {
            val trusted = TrustedBrowsers.isTrusted(packageName, certificateHashes)
            return FillTarget(
                callerPackage = packageName,
                callerCertificateHashes = certificateHashes,
                webHost = if (trusted) webDomain?.let { DomainMatcher.hostOf(it) } else null
            )
        }
    }
}

/**
 * Decides which entries may be offered for a request. Nothing matches unless it was linked on
 * purpose or the entry's own site matches, and only login entries are ever offered: a card or a
 * note has nothing to type into a password field.
 */
class AutofillMatcher(private val domains: DomainMatcher) {

    fun candidates(target: FillTarget, entries: List<EntryWithLinks>): List<VaultEntity> =
        entries.filter { matches(target, it) }.map { it.entry }

    fun matches(target: FillTarget, candidate: EntryWithLinks): Boolean {
        if (candidate.entry.getCategoryEnum() != VaultCategory.ACCOUNT) return false

        val host = target.webHost
        return if (host == null) linkedToApp(target, candidate.links) else matchesSite(host, candidate)
    }

    /** An app is matched by an explicit link only: package name plus the certificate it is signed with. */
    private fun linkedToApp(target: FillTarget, links: List<EntryLink>): Boolean =
        links.any { link ->
            link.typeOrNull() == LinkType.APP &&
                link.value == target.callerPackage &&
                link.certSha256.isNotEmpty() &&
                link.certSha256.lowercase() in target.callerCertificateHashes
        }

    private fun matchesSite(host: String, candidate: EntryWithLinks): Boolean {
        val linked = candidate.links.any { link ->
            link.typeOrNull() == LinkType.DOMAIN && domains.matches(link.value, host)
        }
        if (linked) return true

        // The entry's own address counts as well, but only when it really is one: the same column
        // holds card numbers for other categories and free text for anything the user typed.
        val storedHost = DomainMatcher.hostOf(candidate.entry.urlOrCardNumber) ?: return false
        return domains.matches(storedHost, host)
    }
}
