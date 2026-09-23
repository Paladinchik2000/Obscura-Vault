package com.obscura.autofill.save

import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import java.util.Locale

/** What saving a login typed into a form means for the vault. */
sealed interface SavePlan {

    /** An entry with this username and this very password exists: nothing to write. */
    data class AlreadySaved(val entry: VaultEntity) : SavePlan

    /**
     * Same username, another password: most likely a password change for one of [entries]. The
     * user decides which one to update, or saves a new entry — never a silent overwrite.
     */
    data class PasswordChange(val entries: List<VaultEntity>) : SavePlan

    /** Nothing here has this username: a new entry. */
    data object NewEntry : SavePlan
}

/**
 * Decides between a new entry, a password change and "already saved".
 *
 * [candidates] are the entries that belong to where the login was typed: matched to the app or
 * site by the same rules as filling, plus the entries the form was filled from. Only login
 * entries count.
 */
object SavePlanner {

    /**
     * Usernames are compared without surrounding spaces and without case — e-mail addresses are
     * case-insensitive in practice. Locale.ROOT, not the device's: in a Turkish locale "I" would
     * become a dotless "ı" and "ADMIN" would stop matching "admin".
     */
    fun normalizedUsername(username: String): String = username.trim().lowercase(Locale.ROOT)

    fun plan(username: String, password: CharArray, candidates: List<VaultEntity>): SavePlan {
        val wanted = normalizedUsername(username)
        val sameUser = candidates
            .filter { it.getCategoryEnum() == VaultCategory.ACCOUNT }
            .distinctBy { it.id }
            .filter { normalizedUsername(it.usernameOrCardholder) == wanted }

        sameUser.firstOrNull { samePassword(it.secretValue, password) }?.let { return SavePlan.AlreadySaved(it) }
        if (sameUser.isNotEmpty()) return SavePlan.PasswordChange(sameUser)
        return SavePlan.NewEntry
    }

    /** Compares without turning [typed] into a String that could not be wiped afterwards. */
    private fun samePassword(saved: String, typed: CharArray): Boolean {
        if (saved.length != typed.size) return false
        var same = true
        for (i in typed.indices) if (saved[i] != typed[i]) same = false
        return same
    }
}
