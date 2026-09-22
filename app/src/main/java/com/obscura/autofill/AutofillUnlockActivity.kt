package com.obscura.autofill

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.obscura.autofill.match.CallerIdentity
import com.obscura.security.VaultSession
import com.obscura.ui.auth.LoginScreen
import com.obscura.ui.theme.ObscuraTheme
import kotlinx.coroutines.launch

/**
 * The unlock step of an autofill request: the vault was locked, so the response carried this
 * screen instead of any data.
 *
 * It is the app's own login screen, with the app's own PIN and fingerprint. The device PIN is
 * deliberately not accepted: it would mean anyone who knows the phone's PIN can read every
 * password. Nothing sensitive travels in the intent — only which fields to fill and who asked —
 * and the datasets are built here, after the vault is open.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Keep
class AutofillUnlockActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CALLER_PACKAGE = "com.obscura.autofill.CALLER_PACKAGE"
        const val EXTRA_USERNAME_ID = "com.obscura.autofill.USERNAME_ID"
        const val EXTRA_PASSWORD_ID = "com.obscura.autofill.PASSWORD_ID"
        const val EXTRA_WEB_DOMAIN = "com.obscura.autofill.WEB_DOMAIN"
    }

    private lateinit var callerPackage: String
    private lateinit var form: ParsedForm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        callerPackage = intent.getStringExtra(EXTRA_CALLER_PACKAGE).orEmpty()
        form = ParsedForm(
            usernameId = autofillIdExtra(EXTRA_USERNAME_ID),
            passwordId = autofillIdExtra(EXTRA_PASSWORD_ID),
            webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        )

        if (callerPackage.isEmpty() || !form.isFillable) {
            finishWithoutData()
            return
        }

        // A parallel unlock in the app itself means there is nothing left to ask for.
        if (VaultSession.isUnlocked.value) {
            answerWithMatches()
            return
        }

        setContent {
            ObscuraTheme {
                LoginScreen(onUnlocked = { answerWithMatches() })
            }
        }
    }

    /** Builds the datasets the locked response could not carry and hands them back. */
    private fun answerWithMatches() {
        lifecycleScope.launch {
            val responses = AutofillResponses(this@AutofillUnlockActivity)
            val identity = CallerIdentity.of(this@AutofillUnlockActivity, callerPackage)
            val response = if (identity == null) {
                null
            } else {
                runCatching {
                    responses.datasetsResponse(
                        responses.matchingEntries(responses.targetFor(identity, form)),
                        form
                    )
                }.getOrNull()
            }

            if (response == null) {
                finishWithoutData()
            } else {
                setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response))
                finish()
            }
        }
    }

    /** Unlocked but nothing matches, or the request made no sense: end without filling anything. */
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
