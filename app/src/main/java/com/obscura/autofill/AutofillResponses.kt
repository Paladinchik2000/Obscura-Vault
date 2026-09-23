package com.obscura.autofill

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.obscura.R
import com.obscura.autofill.match.AutofillMatcher
import com.obscura.autofill.match.CallerIdentity
import com.obscura.autofill.match.DomainMatcher
import com.obscura.autofill.match.EntryWithLinks
import com.obscura.autofill.match.FillTarget
import com.obscura.autofill.match.PublicSuffixList
import com.obscura.autofill.save.SavePolicy
import com.obscura.data.local.VaultEntity
import com.obscura.security.VaultSession

/**
 * Turns a parsed form plus the vault into a FillResponse.
 *
 * Shared by the service and by the unlock screen, so both build the same answer from the same
 * matching rules — the screen just gets there after the vault has been opened.
 *
 * A suggestion shows the entry title and the username, never the password.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillResponses(private val context: Context) {

    private val matcher = AutofillMatcher(DomainMatcher(PublicSuffixList.fromResources(context)))

    /**
     * What the request is for. The domain in the layout is only believed when the caller is a
     * browser we recognise by package and signing certificate.
     */
    fun targetFor(identity: CallerIdentity, form: ParsedForm): FillTarget =
        FillTarget.of(identity.packageName, identity.certificateHashes, form.webDomain)

    /** Entries that may be offered here, newest first. Runs in the vault session. */
    suspend fun matchingEntries(target: FillTarget): List<VaultEntity> =
        VaultSession.runInSession {
            val dao = VaultSession.requireDatabase().vaultDao()
            val linksByEntry = dao.getAllLinksDirect().groupBy { it.entryId }
            val candidates = dao.getAllEntriesDirect().map { entry ->
                EntryWithLinks(entry, linksByEntry[entry.id].orEmpty())
            }
            matcher.candidates(target, candidates)
        }

    /**
     * Entries the user has already tied to this app: package plus a certificate the app is
     * signed with. The app rule alone — a domain does not count here. Runs in the vault session.
     */
    suspend fun entryIdsLinkedTo(identity: CallerIdentity): Set<String> =
        matchingEntries(FillTarget(identity.packageName, identity.certificateHashes))
            .mapTo(HashSet()) { it.id }

    /**
     * One dataset per entry, plus a way into the manual choice. [search] is null when the caller
     * already is the picker, and then an empty list means there is nothing to answer with.
     */
    fun datasetsResponse(
        entries: List<VaultEntity>,
        form: ParsedForm,
        callerPackage: String,
        search: PendingIntent? = null
    ): FillResponse? {
        if (entries.isEmpty() && search == null) return null
        val builder = FillResponse.Builder()
        entries.forEach { entry -> builder.addDataset(datasetFor(entry, form)) }
        search?.let { builder.addDataset(searchDataset(form, it)) }
        saveInfoFor(form, callerPackage)?.let { builder.setSaveInfo(it) }
        return builder.build()
    }

    /**
     * Asks the system to offer saving what the user types into this form. Only where the save
     * really happens (see [SavePolicy]); the system itself skips the offer when nothing was typed
     * or the values are exactly those of a dataset it was given.
     */
    private fun saveInfoFor(form: ParsedForm, callerPackage: String): SaveInfo? {
        val password = form.passwordId ?: return null
        if (!SavePolicy.offersSave(Build.VERSION.SDK_INT, callerPackage, context.packageName, hasPasswordField = true)) {
            return null
        }
        val builder = SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
            arrayOf(password)
        )
        form.usernameId?.let { builder.setOptionalIds(arrayOf(it)) }
        return builder.build()
    }

    /**
     * "Search Obscura": shown when nothing matched, and alongside matches so a different entry
     * can still be chosen. It carries no values — picking it opens our own screen.
     */
    private fun searchDataset(form: ParsedForm, pick: PendingIntent): Dataset {
        val view = presentation(
            context.getString(R.string.autofill_search_title),
            context.getString(R.string.autofill_search_subtitle)
        )
        val builder = Dataset.Builder()
        form.usernameId?.let { builder.setValue(it, null, view) }
        form.passwordId?.let { builder.setValue(it, null, view) }
        builder.setAuthentication(pick.intentSender)
        return builder.build()
    }

    /** The vault is locked: one authentication step that opens our unlock screen. */
    fun lockedResponse(form: ParsedForm, callerPackage: String, unlock: PendingIntent): FillResponse {
        val builder = FillResponse.Builder()
            .setAuthentication(
                form.autofillIds(),
                unlock.intentSender,
                presentation(context.getString(R.string.autofill_unlock_title), context.getString(R.string.autofill_unlock_subtitle))
            )
        // A login typed by hand into a form whose vault stayed locked can still be saved.
        saveInfoFor(form, callerPackage)?.let { builder.setSaveInfo(it) }
        return builder.build()
    }

    fun datasetFor(entry: VaultEntity, form: ParsedForm): Dataset {
        // The id comes back in SaveRequest.getDatasetIds(): the entry the form was filled from
        // is a candidate for a password change. An entry id is not a secret.
        val builder = Dataset.Builder().setId(entry.id)
        val view = presentation(entry.title, entry.usernameOrCardholder.ifBlank { null })

        form.usernameId?.let { builder.setValue(it, AutofillValue.forText(entry.usernameOrCardholder), view) }
        form.passwordId?.let { builder.setValue(it, AutofillValue.forText(entry.secretValue), view) }
        return builder.build()
    }

    fun presentation(title: String, subtitle: String?): RemoteViews =
        RemoteViews(context.packageName, R.layout.autofill_suggestion).apply {
            setTextViewText(R.id.autofill_title, title)
            if (subtitle.isNullOrBlank()) {
                setViewVisibility(R.id.autofill_subtitle, android.view.View.GONE)
            } else {
                setTextViewText(R.id.autofill_subtitle, subtitle)
            }
        }

    companion object {
        fun ParsedForm.autofillIds(): Array<AutofillId> =
            listOfNotNull(usernameId, passwordId).toTypedArray()
    }
}
