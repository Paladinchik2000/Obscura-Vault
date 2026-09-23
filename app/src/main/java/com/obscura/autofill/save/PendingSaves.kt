package com.obscura.autofill.save

import androidx.annotation.VisibleForTesting
import java.security.SecureRandom

/**
 * A login waiting for the user on the save screen: what was typed and where.
 *
 * The password is held as a char array and wiped as soon as it is written, dropped or expired.
 * The strings the framework handed it over in cannot be wiped — they were only copied here and
 * are no longer referenced.
 */
class PendingSave(
    val origin: SaveOrigin,
    val username: String,
    password: CharArray,
    /** Entries the form was filled from, if any: they are candidates for a password change. */
    val filledEntryIds: List<String>
) {
    private var secret: CharArray? = password

    /** The typed password, or null once wiped. Do not keep the array beyond the call. */
    fun password(): CharArray? = secret

    val isWiped: Boolean get() = secret == null

    fun wipe() {
        secret?.fill('\u0000')
        secret = null
    }
}

/**
 * The one pending save, in this process's memory only — never on disk, never in an intent or
 * saved instance state. The save screen is given a random token and looks the save up with it,
 * so its intent carries nothing worth reading.
 *
 * One at a time: a new save wipes the previous. A save nobody picks up is wiped after
 * [LIFETIME_MS]. If the process dies, the save is gone and the user types the login again.
 */
object PendingSaves {

    /** Long enough to unlock the vault and decide, short enough not to keep a password around. */
    const val LIFETIME_MS = 2 * 60 * 1000L

    @VisibleForTesting
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val random = SecureRandom()

    private var token: String? = null
    private var save: PendingSave? = null
    private var createdAt = 0L

    @Synchronized
    fun put(pending: PendingSave): String {
        wipeCurrent()
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        val newToken = bytes.joinToString("") { "%02x".format(it) }
        token = newToken
        save = pending
        createdAt = clock()
        return newToken
    }

    /** The save behind [requested], or null when unknown, replaced or expired (then it is wiped). */
    @Synchronized
    fun get(requested: String?): PendingSave? {
        expireStale()
        if (requested == null || requested != token) return null
        return save
    }

    /** Wipes the save behind [requested], if it is still the current one. */
    @Synchronized
    fun discard(requested: String?) {
        if (requested != null && requested == token) wipeCurrent()
    }

    @Synchronized
    fun expireStale() {
        if (save != null && clock() - createdAt >= LIFETIME_MS) wipeCurrent()
    }

    @VisibleForTesting
    internal val isEmpty: Boolean
        @Synchronized get() = save == null

    private fun wipeCurrent() {
        save?.wipe()
        save = null
        token = null
    }
}
