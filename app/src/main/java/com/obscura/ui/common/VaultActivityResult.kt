package com.obscura.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.obscura.security.VaultSession

/**
 * Launches a system screen for a result without the vault locking underneath it.
 *
 * Such a screen (a SAF picker, for example) is a separate activity, so ours is stopped while it
 * is open. With the "immediately" auto-lock that would lock the vault and the result would arrive
 * on the login screen. Marking the session keeps it open until the result comes back — including
 * a cancellation — and no longer than VaultSession's grace.
 *
 * Use this for every external activity we start ourselves.
 */
@Composable
fun <I, O> rememberVaultResultLauncher(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): (I) -> Unit {
    val launcher = rememberLauncherForActivityResult(contract) { result ->
        VaultSession.finishedOwnActivityResult()
        onResult(result)
    }
    return remember(launcher) {
        { input: I ->
            VaultSession.startedOwnActivityResult()
            launcher.launch(input)
        }
    }
}
