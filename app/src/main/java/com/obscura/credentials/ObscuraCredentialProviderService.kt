package com.obscura.credentials

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.service.credentials.BeginCreateCredentialRequest
import android.service.credentials.BeginCreateCredentialResponse
import android.service.credentials.BeginGetCredentialRequest
import android.service.credentials.BeginGetCredentialResponse
import android.service.credentials.ClearCredentialStateRequest
import android.service.credentials.CredentialProviderService
import android.credentials.CreateCredentialException
import android.credentials.GetCredentialException
import android.credentials.ClearCredentialStateException
import androidx.annotation.Keep
import androidx.annotation.RequiresApi

/**
 * ObscuraCredentialProviderService
 * Android 14+ (API 34+) Credential Provider Service handling WebAuthn/FIDO2 Passkeys
 * and encrypted password credentials for the Obscura Password Manager.
 */
@RequiresApi(value = 34)
@Keep
class ObscuraCredentialProviderService : CredentialProviderService() {

    override fun onBeginCreateCredential(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        // Handle BeginCreateCredential for Passkeys (Public Key Credentials)
    }

    override fun onBeginGetCredential(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        // Handle BeginGetCredential
    }

    override fun onClearCredentialState(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void, ClearCredentialStateException>
    ) {
        // Handle ClearCredentialState
    }
}
