package com.obscura.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import com.obscura.BuildConfig
import com.obscura.autofill.AutofillResponses.Companion.autofillIds
import com.obscura.autofill.match.CallerIdentity
import com.obscura.security.AuthRepository
import com.obscura.security.VaultLockedException
import com.obscura.security.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Fills login forms in other apps.
 *
 * Nothing is offered unless the entry was linked to this app on purpose, or its own site matches
 * the site a browser we trust says it is showing. A locked vault answers with an authentication
 * step instead of data: no secret leaves the app before the user has unlocked it.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Keep
class ObscuraAutofillService : AutofillService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val callerPackage = structure.activityComponent?.packageName
        // Never offer Obscura's own secrets to Obscura: the vault screens are not a fill target.
        if (callerPackage == null || callerPackage == BuildConfig.APPLICATION_ID) {
            callback.onSuccess(null)
            return
        }

        // Without a vault there is nothing to unlock; the first PIN is created in the app itself.
        if (!AuthRepository(this).isVaultInitialized) {
            callback.onSuccess(null)
            return
        }

        val form = AutofillFormParser.parse(structure)
        if (!form.isFillable) {
            callback.onSuccess(null)
            return
        }

        val responses = AutofillResponses(this)

        // The caller's package is not visible to us (an app without a launcher icon falls outside
        // our <queries>), so its certificate cannot be read and nothing may be matched to it —
        // neither by link nor by domain. The manual choice still works. Answering null instead
        // would look exactly like "no matching entries" and hide the failure.
        val identity = CallerIdentity.of(this, callerPackage)
        if (identity == null) {
            callback.onSuccess(responses.datasetsResponse(emptyList(), form, pickPendingIntent(form, callerPackage)))
            return
        }

        if (!VaultSession.isUnlocked.value) {
            callback.onSuccess(responses.lockedResponse(form, unlockPendingIntent(form, callerPackage)))
            return
        }

        val target = responses.targetFor(identity, form)
        val job = scope.launch {
            val response = try {
                responses.datasetsResponse(
                    entries = responses.matchingEntries(target),
                    form = form,
                    search = pickPendingIntent(form, callerPackage)
                )
            } catch (e: VaultLockedException) {
                // Locked between the check and the query: ask for the unlock instead.
                responses.lockedResponse(form, unlockPendingIntent(form, callerPackage))
            }
            if (!cancellationSignal.isCanceled) callback.onSuccess(response)
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    /** Saving new logins is a separate task; answering keeps the framework from waiting. */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** The vault is locked: unlock first, then answer with whatever matches. */
    private fun unlockPendingIntent(form: ParsedForm, callerPackage: String): PendingIntent =
        activityPendingIntent(form, callerPackage, pick = false, resultIsDataset = false)

    /** "Search Obscura": the user chooses an entry by hand, so the answer is one dataset. */
    private fun pickPendingIntent(form: ParsedForm, callerPackage: String): PendingIntent =
        activityPendingIntent(form, callerPackage, pick = true, resultIsDataset = true)

    private fun activityPendingIntent(
        form: ParsedForm,
        callerPackage: String,
        pick: Boolean,
        resultIsDataset: Boolean
    ): PendingIntent {
        val intent = Intent(this, AutofillUnlockActivity::class.java).apply {
            putExtra(AutofillUnlockActivity.EXTRA_CALLER_PACKAGE, callerPackage)
            putExtra(AutofillUnlockActivity.EXTRA_USERNAME_ID, form.usernameId)
            putExtra(AutofillUnlockActivity.EXTRA_PASSWORD_ID, form.passwordId)
            putExtra(AutofillUnlockActivity.EXTRA_WEB_DOMAIN, form.webDomain)
            putExtra(AutofillUnlockActivity.EXTRA_PICK, pick)
            putExtra(AutofillUnlockActivity.EXTRA_RESULT_IS_DATASET, resultIsDataset)
        }
        return PendingIntent.getActivity(
            this,
            // Distinct request codes, or the two intents would overwrite each other.
            form.autofillIds().contentHashCode() * 2 + if (pick) 1 else 0,
            intent,
            // Immutable: the screen is given everything it needs here, and nothing else may
            // rewrite this intent on its way through the system.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
