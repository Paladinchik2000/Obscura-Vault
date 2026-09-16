package com.obscura.ui.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.obscura.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies secrets to the clipboard marked as sensitive (Android 13+ hides the content from the
 * clipboard preview and keyboard suggestions; older releases ignore the flag) and clears the
 * clipboard again after [CLEAR_AFTER_MS]. The timer lives in the app process: if the process
 * dies first, the clipboard is not cleared.
 */
object SensitiveClipboard {

    const val CLEAR_AFTER_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingClear: Job? = null

    /** Call on the main thread. A new copy restarts the timer. */
    fun copy(context: Context, text: String) {
        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return
        val label = appContext.getString(R.string.clipboard_label)
        clipboard.setPrimaryClip(sensitiveClip(label, text))

        pendingClear?.cancel()
        pendingClear = scope.launch {
            delay(CLEAR_AFTER_MS)
            clearIfStillOurs(clipboard, label)
        }
    }

    fun sensitiveClip(label: String, text: String): ClipData =
        ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

    private fun clearIfStillOurs(clipboard: ClipboardManager, label: String) {
        // In the background (Android 10+) the description can't be read and comes back null.
        // Clear anyway then: leaving a copied password around is worse than dropping a newer copy.
        val current = runCatching { clipboard.primaryClipDescription }.getOrNull()
        if (current != null && current.label?.toString() != label) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
