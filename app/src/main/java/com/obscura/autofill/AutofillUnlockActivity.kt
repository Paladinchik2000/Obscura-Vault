package com.obscura.autofill

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.obscura.autofill.match.CallerIdentity
import com.obscura.autofill.match.DomainMatcher
import com.obscura.autofill.match.PublicSuffixList
import com.obscura.data.local.EntryLink
import com.obscura.data.local.LinkType
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import com.obscura.security.VaultSession
import com.obscura.ui.auth.LoginScreen
import com.obscura.ui.theme.ObscuraTheme
import kotlinx.coroutines.launch

/**
 * The part of an autofill request that needs the user: unlocking the vault, choosing an entry by
 * hand, or both.
 *
 * Unlocking uses the app's own PIN or fingerprint. The device PIN is deliberately not accepted:
 * it would mean anyone who knows the phone's PIN can read every password. Nothing sensitive
 * travels in the intent — only which fields to fill and who asked — and datasets are built here,
 * after the vault is open.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Keep
class AutofillUnlockActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CALLER_PACKAGE = "com.obscura.autofill.CALLER_PACKAGE"
        const val EXTRA_USERNAME_ID = "com.obscura.autofill.USERNAME_ID"
        const val EXTRA_PASSWORD_ID = "com.obscura.autofill.PASSWORD_ID"
        const val EXTRA_WEB_DOMAIN = "com.obscura.autofill.WEB_DOMAIN"

        /** Whether the user is choosing an entry by hand rather than just unlocking. */
        const val EXTRA_PICK = "com.obscura.autofill.PICK"

        /** A response-level authentication answers with a FillResponse, a dataset with a Dataset. */
        const val EXTRA_RESULT_IS_DATASET = "com.obscura.autofill.RESULT_IS_DATASET"
    }

    private lateinit var callerPackage: String
    private lateinit var form: ParsedForm
    private val responses by lazy { AutofillResponses(this) }
    private var resultIsDataset = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        callerPackage = intent.getStringExtra(EXTRA_CALLER_PACKAGE).orEmpty()
        resultIsDataset = intent.getBooleanExtra(EXTRA_RESULT_IS_DATASET, false)
        form = ParsedForm(
            usernameId = autofillIdExtra(EXTRA_USERNAME_ID),
            passwordId = autofillIdExtra(EXTRA_PASSWORD_ID),
            webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        )

        if (callerPackage.isEmpty() || !form.isFillable) {
            finishWithoutData()
            return
        }

        if (VaultSession.isUnlocked.value) {
            continueUnlocked()
        } else {
            setContent { ObscuraTheme { LoginScreen(onUnlocked = { continueUnlocked() }) } }
        }
    }

    /** Vault open: either hand back what matches, or let the user choose. */
    private fun continueUnlocked() {
        if (intent.getBooleanExtra(EXTRA_PICK, false)) {
            showPicker()
            return
        }

        lifecycleScope.launch {
            val identity = CallerIdentity.of(this@AutofillUnlockActivity, callerPackage)
            val matches = if (identity == null) {
                emptyList()
            } else {
                runCatching { responses.matchingEntries(responses.targetFor(identity, form)) }
                    .getOrDefault(emptyList())
            }

            // Nothing matched: rather than an empty answer, offer the manual choice right away.
            if (matches.isEmpty()) showPicker() else answerWith(responses.datasetsResponse(matches, form))
        }
    }

    private fun showPicker() {
        lifecycleScope.launch {
            val caller = confirmedCaller()
            val entries = runCatching { loginEntries() }.getOrDefault(emptyList())
            // An app Obscura cannot see from the fill request can still have been linked
            // earlier: here, with the caller confirmed, that link counts — its entries come
            // first and are picked without asking to remember them again.
            val linked = caller
                ?.let { runCatching { responses.entryIdsLinkedTo(it) }.getOrDefault(emptySet()) }
                .orEmpty()
            val ordered = entries.sortedByDescending { it.id in linked }
            val label = caller?.let { targetLabel(it) }

            setContent {
                ObscuraTheme {
                    AutofillPickerScreen(
                        entries = ordered,
                        targetLabel = label,
                        linkedIds = linked,
                        onPick = { entry, link -> onEntryPicked(entry, link, caller) }
                    )
                }
            }
        }
    }

    /**
     * The app being filled, when it can be confirmed; null leaves the picker as a plain list with
     * no links applied and none offered.
     *
     * Two conditions. The package in our intent must be the app that actually started this screen
     * — the system reports it as the calling activity, since the app starts us for a result. And
     * its certificate must be readable: by now that normally works even for an app outside our
     * <queries>, because starting us for a result made it visible to us.
     */
    private fun confirmedCaller(): CallerIdentity? {
        if (callingActivity?.packageName != callerPackage) return null
        return CallerIdentity.of(this, callerPackage)
    }

    private fun onEntryPicked(entry: VaultEntity, link: Boolean, caller: CallerIdentity?) {
        lifecycleScope.launch {
            if (link && caller != null) runCatching { saveLink(entry, caller) }
            answerWith(responses.datasetsResponse(listOf(entry), form), entry)
        }
    }

    /** Remembers the choice, so next time the entry is offered without asking. */
    private suspend fun saveLink(entry: VaultEntity, identity: CallerIdentity) {
        val host = responses.targetFor(identity, form).webHost

        val link = if (host != null) {
            val domains = DomainMatcher(PublicSuffixList.fromResources(this))
            val storable = domains.storableHost(host) ?: return
            EntryLink(entryId = entry.id, type = LinkType.DOMAIN.id, value = storable)
        } else {
            EntryLink(
                entryId = entry.id,
                type = LinkType.APP.id,
                value = identity.packageName,
                certSha256 = identity.currentCertificateHash
            )
        }

        VaultSession.runInSession { VaultSession.requireDatabase().vaultDao().insertLink(link) }
    }

    private suspend fun loginEntries(): List<VaultEntity> =
        VaultSession.runInSession {
            VaultSession.requireDatabase().vaultDao().getAllEntriesDirect()
                .filter { it.getCategoryEnum() == VaultCategory.ACCOUNT }
        }

    /** What to call the request in the link question: the site, or the app's own name. */
    private fun targetLabel(identity: CallerIdentity): String {
        responses.targetFor(identity, form).webHost?.let { return it }
        return runCatching {
            val info = packageManager.getApplicationInfo(callerPackage, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(callerPackage)
    }

    private fun answerWith(response: android.service.autofill.FillResponse?, picked: VaultEntity? = null) {
        if (response == null) {
            finishWithoutData()
            return
        }

        val result = Intent()
        if (resultIsDataset) {
            // A dataset authentication must answer with a dataset, not with a whole response.
            val entry = picked
            if (entry == null) {
                finishWithoutData()
                return
            }
            result.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, responses.datasetFor(entry, form))
        } else {
            result.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    /** Nothing to fill, or the request made no sense: end without filling anything. */
    private fun finishWithoutData() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun autofillIdExtra(name: String): AutofillId? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(name)
        }
}
