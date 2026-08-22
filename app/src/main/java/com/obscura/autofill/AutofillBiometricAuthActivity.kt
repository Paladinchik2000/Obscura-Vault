package com.obscura.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * AutofillBiometricAuthActivity
 *
 * A transparent Activity called by the Android Autofill Subsystem (AutofillService) via PendingIntent
 * whenever a protected Dataset or FillResponse requires Biometric Authorization (User Present + User Verified).
 *
 * Flow:
 * 1. Android Autofill Manager launches this Activity with the pending authentication parameters.
 * 2. Activity displays a sleek, translucent Jetpack Compose bottom dialog matching Obscura theme.
 * 3. Immediately triggers BiometricPrompt (Fingerprint / FaceID / Device PIN fallback).
 * 4. On Biometric verification success:
 *    - Resolves the pending Dataset / FillResponse or builds an authenticated Dataset containing the decrypted secret value.
 *    - Packages it into AutofillManager.EXTRA_AUTHENTICATION_RESULT.
 *    - Returns Activity.RESULT_OK to the caller Autofill Framework.
 * 5. On failure or dismissal, returns Activity.RESULT_CANCELED without exposing secret data.
 */
@Keep
class AutofillBiometricAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "com.obscura.autofill.EXTRA_ENTRY_ID"
        const val EXTRA_ENTRY_TITLE = "com.obscura.autofill.EXTRA_ENTRY_TITLE"
        const val EXTRA_USERNAME = "com.obscura.autofill.EXTRA_USERNAME"
        const val EXTRA_SECRET_VALUE = "com.obscura.autofill.EXTRA_SECRET_VALUE"
        const val EXTRA_USERNAME_AUTOFILL_ID = "com.obscura.autofill.EXTRA_USERNAME_AUTOFILL_ID"
        const val EXTRA_PASSWORD_AUTOFILL_ID = "com.obscura.autofill.EXTRA_PASSWORD_AUTOFILL_ID"
        const val EXTRA_PENDING_DATASET = "com.obscura.autofill.EXTRA_PENDING_DATASET"
        const val EXTRA_PENDING_RESPONSE = "com.obscura.autofill.EXTRA_PENDING_RESPONSE"
        const val EXTRA_AUTOPROMPT = "com.obscura.autofill.EXTRA_AUTOPROMPT"

        /**
         * Helper to create an Intent for launching this biometric prompt from an AutofillService
         */
        fun createIntent(
            context: Context,
            entryId: String,
            entryTitle: String,
            username: String,
            secretValue: String,
            usernameAutofillId: AutofillId? = null,
            passwordAutofillId: AutofillId? = null,
            autoPrompt: Boolean = true
        ): Intent {
            return Intent(context, AutofillBiometricAuthActivity::class.java).apply {
                putExtra(EXTRA_ENTRY_ID, entryId)
                putExtra(EXTRA_ENTRY_TITLE, entryTitle)
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_SECRET_VALUE, secretValue)
                putExtra(EXTRA_USERNAME_AUTOFILL_ID, usernameAutofillId)
                putExtra(EXTRA_PASSWORD_AUTOFILL_ID, passwordAutofillId)
                putExtra(EXTRA_AUTOPROMPT, autoPrompt)
            }
        }
    }

    private var entryTitle: String = ""
    private var username: String = ""
    private var secretValue: String = ""
    private var usernameAutofillId: AutofillId? = null
    private var passwordAutofillId: AutofillId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse extras delivered by the Autofill service
        entryTitle = intent.getStringExtra(EXTRA_ENTRY_TITLE) ?: "Obscura Vault Item"
        username = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        secretValue = intent.getStringExtra(EXTRA_SECRET_VALUE) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            usernameAutofillId = intent.getParcelableExtra(EXTRA_USERNAME_AUTOFILL_ID, AutofillId::class.java)
            passwordAutofillId = intent.getParcelableExtra(EXTRA_PASSWORD_AUTOFILL_ID, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            usernameAutofillId = intent.getParcelableExtra(EXTRA_USERNAME_AUTOFILL_ID)
            @Suppress("DEPRECATION")
            passwordAutofillId = intent.getParcelableExtra(EXTRA_PASSWORD_AUTOFILL_ID)
        }

        val autoPrompt = intent.getBooleanExtra(EXTRA_AUTOPROMPT, true)

        setContent {
            AutofillBiometricAuthScreen(
                entryTitle = entryTitle,
                username = username,
                autoPrompt = autoPrompt,
                onTriggerBiometrics = { startBiometricVerification() },
                onCancel = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            )
        }
    }

    /**
     * Executes native AndroidX BiometricPrompt flow
     */
    private fun startBiometricVerification() {
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                resolveAutofillSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Transient biometric failure (wrong finger, etc.)
                // System UI remains visible for retry
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Obscura Vault Autofill")
            .setSubtitle("Authenticate to autofill $entryTitle")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(this, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Resolves the pending Autofill request and returns the decrypted credentials to the framework
     */
    private fun resolveAutofillSuccess() {
        val replyIntent = Intent()

        // 1. If a pre-packaged Dataset was passed, forward it
        if (intent.hasExtra(EXTRA_PENDING_DATASET)) {
            val pendingDataset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_PENDING_DATASET, Dataset::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_PENDING_DATASET)
            }
            replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, pendingDataset)
        }
        // 2. If a pre-packaged FillResponse was passed, forward it
        else if (intent.hasExtra(EXTRA_PENDING_RESPONSE)) {
            val pendingResponse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_PENDING_RESPONSE, FillResponse::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_PENDING_RESPONSE)
            }
            replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, pendingResponse)
        }
        // 3. Otherwise construct the authenticated Dataset from the AutofillIds & secret payload
        else {
            val dataset = buildAuthenticatedDataset()
            if (dataset != null) {
                replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            }
        }

        // Return RESULT_OK with the resolved payload to the Android Autofill framework
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    /**
     * Helper to construct a Dataset when individual AutofillIds and decrypted secrets are provided
     */
    private fun buildAuthenticatedDataset(): Dataset? {
        val datasetBuilder = Dataset.Builder()
        var hasValues = false

        if (usernameAutofillId != null && username.isNotEmpty()) {
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, username)
            }
            datasetBuilder.setValue(
                usernameAutofillId!!,
                AutofillValue.forText(username),
                presentation
            )
            hasValues = true
        }

        if (passwordAutofillId != null && secretValue.isNotEmpty()) {
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "••••••••")
            }
            datasetBuilder.setValue(
                passwordAutofillId!!,
                AutofillValue.forText(secretValue),
                presentation
            )
            hasValues = true
        }

        return if (hasValues) datasetBuilder.build() else null
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }
}

/**
 * Transparent Jetpack Compose Bottom Sheet UI adhering strictly to Obscura True Black / Crimson Theme
 */
@Composable
fun AutofillBiometricAuthScreen(
    entryTitle: String,
    username: String,
    autoPrompt: Boolean,
    onTriggerBiometrics: () -> Unit,
    onCancel: () -> Unit
) {
    var hasPrompted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (autoPrompt && !hasPrompted) {
            hasPrompted = true
            onTriggerBiometrics()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            color = Color(0xFF0D0D0D)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333333))
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Vault Icon Shield
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161616))
                        .border(1.dp, Color(0xFFE50914), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title & Subtitle
                Text(
                    text = "AUTOFILL VERIFICATION",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Verify biometric identity to release credentials for",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = entryTitle,
                    color = Color(0xFFE50914),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Account / Username Badge
                if (username.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF141414))
                            .border(1.dp, Color(0xFF262626), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF222222)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "TARGET ACCOUNT",
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = username,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Primary Biometric Trigger Button
                Button(
                    onClick = onTriggerBiometrics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE50914),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Fingerprint",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUTHENTICATE & AUTOFILL",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cancel Button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2B2B2B))
                    )
                ) {
                    Text(
                        text = "CANCEL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
