package com.obscura.ui.backup

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

const val BACKUP_REMINDER_TEXT =
    "Сделайте резервную копию. Ключи привязаны к этому устройству — при потере телефона " +
        "или удалении приложения данные не восстановить ничем."

/** Remembers whether the one-time "make a backup" reminder has been shown. */
interface BackupReminderStore {
    fun wasShown(): Boolean
    fun markShown()
}

/** Plain SharedPreferences: the flag isn't sensitive and has to be readable while the vault is locked. */
class PrefsBackupReminderStore(context: Context) : BackupReminderStore {

    private val prefs = context.applicationContext.getSharedPreferences("obscura_ui", Context.MODE_PRIVATE)

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
        title = { Text("Резервная копия") },
        text = { Text(BACKUP_REMINDER_TEXT) },
        confirmButton = { TextButton(onClick = onMakeBackup) { Text("Сделать копию") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Позже") } }
    )
}
