package com.obscura.data

/**
 * Every SharedPreferences file the app owns. Listed in one place so a full vault reset cannot
 * miss one: [ALL] is what com.obscura.security.VaultReset clears.
 */
object Preferences {

    /** Salt, wrapped DEK copies, failed attempts and lockout. */
    const val AUTH = "obscura_auth"

    /** Non-sensitive UI flags, e.g. whether the backup reminder was shown. */
    const val UI = "obscura_ui"

    /** User settings, e.g. the auto-lock timeout. */
    const val SETTINGS = "obscura_settings"

    val ALL = listOf(AUTH, UI, SETTINGS)
}
