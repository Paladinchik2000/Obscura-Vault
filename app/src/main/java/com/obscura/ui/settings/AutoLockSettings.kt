package com.obscura.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import com.obscura.R
import com.obscura.security.VaultSession

/** How long the vault may stay unlocked in the background. There is deliberately no "never". */
enum class AutoLockOption(val millis: Long, @StringRes val labelRes: Int) {
    IMMEDIATELY(0L, R.string.settings_autolock_immediately),
    SECONDS_30(30_000L, R.string.settings_autolock_30s),
    MINUTES_1(60_000L, R.string.settings_autolock_1m),
    MINUTES_2(120_000L, R.string.settings_autolock_2m),
    MINUTES_5(300_000L, R.string.settings_autolock_5m),
    MINUTES_15(900_000L, R.string.settings_autolock_15m);

    companion object {
        val DEFAULT = MINUTES_2

        fun fromMillis(millis: Long): AutoLockOption = entries.firstOrNull { it.millis == millis } ?: DEFAULT
    }
}

/**
 * The auto-lock timeout. Plain SharedPreferences: it is not sensitive and has to be readable
 * while the vault is locked, before any key exists.
 */
class AutoLockSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var option: AutoLockOption
        get() = AutoLockOption.fromMillis(prefs.getLong(KEY_TIMEOUT, AutoLockOption.DEFAULT.millis))
        set(value) {
            prefs.edit().putLong(KEY_TIMEOUT, value.millis).apply()
            VaultSession.idleTimeoutMs = value.millis
        }

    /** Pushes the stored value into the session; call once at process start. */
    fun applyToSession() {
        VaultSession.idleTimeoutMs = option.millis
    }

    companion object {
        const val PREFS_NAME = "obscura_settings"
        const val KEY_TIMEOUT = "auto_lock_ms"
    }
}
