package com.obscura

import android.app.Application
import androidx.annotation.Keep
import net.sqlcipher.database.SQLiteDatabase

/**
 * ObscuraApplication
 * Entry point for the offline-first Obscura Fortress application.
 * Initializes SQLCipher native libraries for encrypted Room Database.
 */
@Keep
class ObscuraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize SQLCipher native libraries
        SQLiteDatabase.loadLibs(this)
    }
}
