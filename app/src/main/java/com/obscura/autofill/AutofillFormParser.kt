package com.obscura.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi

/**
 * The fields autofill can work with in one screen.
 *
 * [webDomain] is whatever the app put in its layout — a claim, not a fact. Only the service
 * decides whether to believe it, and only for browsers it recognises.
 */
data class ParsedForm(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val webDomain: String?
) {
    val isFillable: Boolean get() = passwordId != null || usernameId != null
}

/** A login as typed into a form; [password] is wiped by whoever takes it. */
class TypedLogin(val username: String?, val password: CharArray?)

/** Finds the username and password fields in an assist structure. */
@RequiresApi(Build.VERSION_CODES.O)
object AutofillFormParser {

    private val USERNAME_WORDS = listOf("username", "user_name", "user", "email", "e-mail", "login", "identifier")
    private val PASSWORD_WORDS = listOf("password", "passwd", "pwd", "pass")

    fun parse(structure: AssistStructure): ParsedForm {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var webDomain: String? = null

        fun visit(node: AssistStructure.ViewNode) {
            if (webDomain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }

            val id = node.autofillId
            if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
                when {
                    passwordId == null && looksLikePassword(node) -> passwordId = id
                    usernameId == null && looksLikeUsername(node) -> usernameId = id
                }
            }

            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)

        return ParsedForm(usernameId = usernameId, passwordId = passwordId, webDomain = webDomain)
    }

    /**
     * What the user left in the form's fields, as the save request reports it. The password comes
     * out as a char array so the copy that is kept can be wiped; the framework's own string of it
     * cannot be.
     */
    fun typedValues(structure: AssistStructure, form: ParsedForm): TypedLogin {
        var username: String? = null
        var password: CharArray? = null

        fun visit(node: AssistStructure.ViewNode) {
            val id = node.autofillId
            val value = node.autofillValue?.takeIf { it.isText }?.textValue
            if (id != null && value != null) {
                if (id == form.usernameId) username = value.toString()
                if (id == form.passwordId) password = CharArray(value.length) { value[it] }
            }
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
        return TypedLogin(username, password)
    }

    private fun looksLikePassword(node: AssistStructure.ViewNode): Boolean {
        node.autofillHints?.forEach { hint ->
            when (hint.lowercase()) {
                View.AUTOFILL_HINT_PASSWORD, "passwordauto" -> return true
                View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS -> return false
            }
        }

        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val isTextClass = (node.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        if (isTextClass && variation in PASSWORD_VARIATIONS) return true

        return matchesAnyWord(node, PASSWORD_WORDS)
    }

    private fun looksLikeUsername(node: AssistStructure.ViewNode): Boolean {
        node.autofillHints?.forEach { hint ->
            when (hint.lowercase()) {
                View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS -> return true
                View.AUTOFILL_HINT_PASSWORD -> return false
            }
        }

        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val isTextClass = (node.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        if (isTextClass && variation in USERNAME_VARIATIONS) return true

        return matchesAnyWord(node, USERNAME_WORDS)
    }

    /** Falls back to the names an app gives its own fields when there are no hints. */
    private fun matchesAnyWord(node: AssistStructure.ViewNode, words: List<String>): Boolean {
        val haystack = buildString {
            node.idEntry?.let { append(it).append(' ') }
            node.hint?.let { append(it).append(' ') }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // android.util.Pair, so no destructuring here.
                node.htmlInfo?.attributes?.forEach { attribute ->
                    val name = attribute.first
                    if (name == "name" || name == "id" || name == "type") {
                        attribute.second?.let { append(it).append(' ') }
                    }
                }
            }
        }.lowercase()

        return words.any { haystack.contains(it) }
    }

    private val PASSWORD_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    )

    private val USERNAME_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    )
}
