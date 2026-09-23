package com.obscura.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
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
import com.obscura.autofill.save.PendingSave
import com.obscura.autofill.save.PendingSaves
import com.obscura.autofill.save.SaveOrigin
import com.obscura.autofill.save.SavePolicy
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
            callback.onSuccess(responses.datasetsResponse(emptyList(), form, callerPackage, pickPendingIntent(form, callerPackage)))
            return
        }

        if (!VaultSession.isUnlocked.value) {
            callback.onSuccess(responses.lockedResponse(form, callerPackage, unlockPendingIntent(form, callerPackage)))
            return
        }

        val target = responses.targetFor(identity, form)
        val job = scope.launch {
            val response = try {
                responses.datasetsResponse(
                    entries = responses.matchingEntries(target),
                    form = form,
                    callerPackage = callerPackage,
                    search = pickPendingIntent(form, callerPackage)
                )
            } catch (e: VaultLockedException) {
                // Locked between the check and the query: ask for the unlock instead.
                responses.lockedResponse(form, callerPackage, unlockPendingIntent(form, callerPackage))
            }
            if (!cancellationSignal.isCanceled) callback.onSuccess(response)
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    /**
     * The user said yes to the system's "Save to Obscura?". Nothing is written here: the login is
     * put aside in memory and our own screen opens, showing what will be saved and whether it
     * replaces a password — the system dialog shows neither.
     *
     * Where the login came from is read here, from the structure the system hands over, because
     * the save screen cannot tell: it is not started for a result, so it has no calling activity.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val callerPackage = structure?.activityComponent?.packageName
        // Build.VERSION_CODES.P, same as SavePolicy.MIN_SDK, spelled out for lint: the calls below
        // (getDatasetIds, onSuccess(IntentSender)) are API 28.
        if (structure == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !AuthRepository(this).isVaultInitialized) {
            callback.onSuccess()
            return
        }

        val form = AutofillFormParser.parse(structure)
        val typed = AutofillFormParser.typedValues(structure, form)
        val password = typed.password
        if (callerPackage == null || password == null ||
            !SavePolicy.acceptsSave(Build.VERSION.SDK_INT, callerPackage, packageName, password)
        ) {
            password?.fill('\u0000')
            callback.onSuccess()
            return
        }

        val origin = SaveOrigin.fromSaveRequest(
            packageName = callerPackage,
            identity = CallerIdentity.of(this, callerPackage),
            webDomain = form.webDomain,
            appLabel = appLabel(callerPackage)
        )
        val token = PendingSaves.put(
            PendingSave(origin, typed.username.orEmpty(), password, request.datasetIds.orEmpty())
        )
        // A save nobody opens must not keep the password around.
        Handler(Looper.getMainLooper()).postDelayed({ PendingSaves.expireStale() }, PendingSaves.LIFETIME_MS + 1_000L)

        callback.onSuccess(savePendingIntent(token).intentSender)
    }

    /** The app's own name, when it can be read; an app we cannot see has none for us. */
    private fun appLabel(packageName: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    /** Only the token travels: the login itself stays in this process. */
    private fun savePendingIntent(token: String): PendingIntent =
        PendingIntent.getActivity(
            this,
            token.hashCode(),
            Intent(this, AutofillSaveActivity::class.java).putExtra(AutofillSaveActivity.EXTRA_TOKEN, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
