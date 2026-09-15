package com.obscura

import android.app.Application
import androidx.annotation.Keep

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
    }
}
