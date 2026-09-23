package com.obscura.autofill.save

import com.obscura.autofill.match.CallerIdentity
import com.obscura.autofill.match.DomainMatcher
import com.obscura.autofill.match.FillTarget
import com.obscura.autofill.match.PublicSuffixList
import com.obscura.data.local.EntryLink
import com.obscura.data.local.LinkType

/**
 * Where a login being saved was typed.
 *
 * Captured by the service in onSaveRequest and nowhere else: the package comes from the
 * AssistStructure the system hands over, the certificate from PackageManager — the same footing
 * filling stands on. The save screen cannot confirm its caller the way the fill screens do: the
 * system starts it with a plain startIntentSender, not for a result, so it has no calling
 * activity (measured in AutofillSaveTest). It therefore gets the origin only from
 * [PendingSaves], in memory, never from its intent.
 */
class SaveOrigin private constructor(
    val packageName: String,
    /** Null when the app cannot be seen: nothing can be matched to it and no app link is made. */
    val identity: CallerIdentity?,
    /** Set only when the request came from a trusted browser, which is believed about its site. */
    val webHost: String?,
    /** The app's name from PackageManager, when it can be read. */
    val appLabel: String?
) {

    /** What to match existing entries against; null for an app that cannot be seen. */
    fun target(): FillTarget? = identity?.let { FillTarget(packageName, it.certificateHashes, webHost) }

    /** The title a new entry starts with: the site's registrable domain, the app's name, or its package. */
    fun defaultTitle(psl: PublicSuffixList): String {
        webHost?.let { host -> return psl.registrableDomainOf(host) ?: host }
        return appLabel?.trim()?.takeIf { it.isNotEmpty() } ?: packageName
    }

    /** What goes into the entry's address field: the site for a browser, nothing for an app. */
    fun url(): String = webHost?.let { "https://$it" }.orEmpty()

    /**
     * The link a saved entry gets straight away: the site for a trusted browser, the app with
     * its current certificate otherwise, and none when the certificate could not be read — then
     * the link can be made later from the picker, where the caller is confirmed.
     */
    fun linkFor(entryId: String, domains: DomainMatcher): EntryLink? {
        webHost?.let { host ->
            val storable = domains.storableHost(host) ?: return null
            return EntryLink(entryId = entryId, type = LinkType.DOMAIN.id, value = storable)
        }
        val app = identity ?: return null
        return EntryLink(
            entryId = entryId,
            type = LinkType.APP.id,
            value = app.packageName,
            certSha256 = app.currentCertificateHash
        )
    }

    companion object {
        /** For onSaveRequest only; see the class comment for why nothing else may build one. */
        fun fromSaveRequest(
            packageName: String,
            identity: CallerIdentity?,
            webDomain: String?,
            appLabel: String?
        ): SaveOrigin {
            val usable = identity?.takeIf { it.packageName == packageName && it.certificateHashes.isNotEmpty() }
            val host = usable?.let { FillTarget.of(packageName, it.certificateHashes, webDomain).webHost }
            return SaveOrigin(packageName, usable, host, appLabel)
        }
    }
}
