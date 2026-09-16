package com.obscura.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text produced outside Compose (in ViewModels) without holding a Context. It is resolved
 * against string resources only when shown, so it follows the current locale.
 */
sealed interface UiText {

    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Text that already arrives localized from the platform, e.g. a BiometricPrompt error. */
    data class Platform(val text: CharSequence) : UiText

    fun asString(context: Context): String = when (this) {
        is Resource -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
        is Platform -> text.toString()
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Resource(id, args.toList())

@Composable
fun UiText.asString(): String = asString(LocalContext.current)
