package com.obscura.autofill

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.obscura.R
import com.obscura.autofill.match.DomainMatcher
import com.obscura.autofill.match.PublicSuffixList
import com.obscura.autofill.save.PendingSave
import com.obscura.autofill.save.PendingSaves
import com.obscura.autofill.save.SavePlan
import com.obscura.autofill.save.SavePlanner
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import com.obscura.security.VaultSession
import com.obscura.ui.auth.LoginScreen
import com.obscura.ui.theme.ObscuraTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Saves a login the user typed into another app, after showing what will be written.
 *
 * Started by the system from onSaveRequest's IntentSender with nothing but a token; the login and
 * where it came from are in [PendingSaves], in memory. Nothing is written without a tap here, and
 * a password change is never applied on its own.
 *
 * With the vault locked the summary comes first, then the app's own PIN or fingerprint (never the
 * device PIN). After unlocking, whatever needs no further choice is done at once — a new login is
 * saved, an exact duplicate just reported — so nobody unlocks only to be asked again.
 */
@RequiresApi(Build.VERSION_CODES.P)
@Keep
class AutofillSaveActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TOKEN = "com.obscura.autofill.SAVE_TOKEN"
    }

    private var token: String? = null
    private lateinit var pending: PendingSave
    private val psl by lazy { PublicSuffixList.fromResources(this) }
    private val responses by lazy { AutofillResponses(this) }

    private var title by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        token = intent.getStringExtra(EXTRA_TOKEN)
        // Unknown, expired, replaced, or gone with a restarted process: there is nothing to save.
        val save = PendingSaves.get(token)
        if (save == null || save.isWiped) {
            finishWith(R.string.autofill_save_gone)
            return
        }
        pending = save
        title = save.origin.defaultTitle(psl)

        if (VaultSession.isUnlocked.value) decide(afterUnlock = false) else showSummary(vaultLocked = true)
    }

    override fun onDestroy() {
        // Whatever way the screen goes — saved, cancelled, dismissed — the password goes with it.
        if (isFinishing) PendingSaves.discard(token)
        super.onDestroy()
    }

    private fun showSummary(vaultLocked: Boolean) {
        setContent {
            ObscuraTheme {
                AutofillSaveSummaryScreen(
                    title = title,
                    onTitleChange = { title = it },
                    username = pending.username,
                    willBeLinked = pending.origin.linkFor("preview", DomainMatcher(psl)) != null,
                    vaultLocked = vaultLocked,
                    onSave = { if (vaultLocked) showUnlock() else saveNew() },
                    onCancel = { finishWith(null) }
                )
            }
        }
    }

    private fun showUnlock() {
        setContent { ObscuraTheme { LoginScreen(onUnlocked = { decide(afterUnlock = true) }) } }
    }

    /**
     * Looks at the vault and acts. [afterUnlock] means the user has already said "save" on the
     * summary: a new login is written straight away instead of being confirmed a second time.
     */
    private fun decide(afterUnlock: Boolean) {
        lifecycleScope.launch {
            val password = pending.password() ?: return@launch finishWith(R.string.autofill_save_gone)
            val plan = runCatching { SavePlanner.plan(pending.username, password, candidates()) }
                .getOrElse { return@launch finishWith(R.string.autofill_save_failed) }

            when (plan) {
                is SavePlan.AlreadySaved -> finishWith(R.string.autofill_save_already)
                is SavePlan.PasswordChange -> showPasswordChange(plan.entries)
                SavePlan.NewEntry -> if (afterUnlock) saveNew() else showSummary(vaultLocked = false)
            }
        }
    }

    private fun showPasswordChange(entries: List<VaultEntity>) {
        setContent {
            ObscuraTheme {
                AutofillPasswordChangeScreen(
                    entries = entries,
                    onUpdate = { updatePassword(it) },
                    onSaveAsNew = { saveNew() },
                    onCancel = { finishWith(null) }
                )
            }
        }
    }

    /**
     * Entries this login may already be in: those matched to the app or site by the filling
     * rules — which need a readable certificate — and those the form was filled from.
     */
    private suspend fun candidates(): List<VaultEntity> {
        val matched = pending.origin.target()?.let { responses.matchingEntries(it) }.orEmpty()
        val filled = VaultSession.runInSession {
            val dao = VaultSession.requireDatabase().vaultDao()
            pending.filledEntryIds.mapNotNull { dao.getEntryById(it) }
        }
        return (matched + filled).filter { it.getCategoryEnum() == VaultCategory.ACCOUNT }
    }

    private fun saveNew() {
        write(R.string.autofill_save_done) { password ->
            val now = System.currentTimeMillis()
            val entry = VaultEntity(
                id = UUID.randomUUID().toString(),
                title = title.trim().ifEmpty { pending.origin.defaultTitle(psl) },
                // A login: only these are candidates when the app asks to be filled next time.
                category = VaultCategory.ACCOUNT.id,
                usernameOrCardholder = pending.username,
                secretValue = String(password),
                urlOrCardNumber = pending.origin.url(),
                createdAt = now,
                updatedAt = now,
                lastAccessedAt = now
            )
            val dao = VaultSession.requireDatabase().vaultDao()
            dao.insertEntry(entry)
            pending.origin.linkFor(entry.id, DomainMatcher(psl))?.let { dao.insertLink(it) }
        }
    }

    private fun updatePassword(entry: VaultEntity) {
        write(R.string.autofill_save_updated) { password ->
            val dao = VaultSession.requireDatabase().vaultDao()
            dao.updateEntry(entry.copy(secretValue = String(password), updatedAt = System.currentTimeMillis()))
            // The unique index turns an existing link into a replacement, not a second row.
            pending.origin.linkFor(entry.id, DomainMatcher(psl))?.let { dao.insertLink(it) }
        }
    }

    private fun write(@StringRes done: Int, block: suspend (CharArray) -> Unit) {
        lifecycleScope.launch {
            val password = pending.password() ?: return@launch finishWith(R.string.autofill_save_gone)
            val result = runCatching { VaultSession.runInTransaction { block(password) } }
            finishWith(if (result.isSuccess) done else R.string.autofill_save_failed)
        }
    }

    /** Ends the screen; the password is wiped in [onDestroy]. */
    private fun finishWith(@StringRes message: Int?) {
        PendingSaves.discard(token)
        message?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show() }
        finish()
    }
}
