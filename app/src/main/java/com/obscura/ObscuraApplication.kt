package com.obscura

import android.app.Application
import androidx.annotation.Keep
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.obscura.security.VaultSession
import com.obscura.ui.settings.AutoLockSettings

/**
 * ObscuraApplication
 * Entry point for the offline-first Obscura Fortress application.
 * Initializes SQLCipher native libraries for encrypted Room Database.
 */
@Keep
class ObscuraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // sqlcipher-android does not load its native library itself: load it before any database opens.
        System.loadLibrary("sqlcipher")
        AutoLockSettings(this).applyToSession()

        // Process-wide, not per activity: the vault can be unlocked from the autofill service
        // without MainActivity ever starting, and it still has to lock on time.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> VaultSession.onAppForegrounded()
                    Lifecycle.Event.ON_STOP -> VaultSession.onAppBackgrounded()
                    else -> Unit
                }
            }
        )
    }
}
