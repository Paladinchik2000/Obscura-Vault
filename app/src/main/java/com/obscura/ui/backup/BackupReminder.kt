package com.obscura.ui.backup

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.obscura.data.Preferences
import com.obscura.R

/** Remembers whether the one-time "make a backup" reminder has been shown. */
interface BackupReminderStore {
    fun wasShown(): Boolean
    fun markShown()
}

/** Plain SharedPreferences: the flag isn't sensitive and has to be readable while the vault is locked. */
class PrefsBackupReminderStore(context: Context) : BackupReminderStore {

    private val prefs = context.applicationContext.getSharedPreferences(Preferences.UI, Context.MODE_PRIVATE)

    override fun wasShown(): Boolean = prefs.getBoolean(KEY_SHOWN, false)

    override fun markShown() {
        prefs.edit().putBoolean(KEY_SHOWN, true).apply()
    }

    private companion object {
        const val KEY_SHOWN = "backup_reminder_shown"
    }
}

@Composable
fun BackupReminderDialog(onMakeBackup: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_reminder_title)) },
        text = { Text(stringResource(R.string.backup_reminder_message)) },
        confirmButton = { TextButton(onClick = onMakeBackup) { Text(stringResource(R.string.backup_reminder_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) } }
    )
}
