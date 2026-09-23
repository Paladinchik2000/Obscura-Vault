package com.obscura.autofill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obscura.R
import com.obscura.data.local.VaultEntity

object AutofillSaveTags {
    const val TITLE = "autofill_save_title"
    const val CONFIRM = "autofill_save_confirm"
    const val CANCEL = "autofill_save_cancel"
    const val SAVE_AS_NEW = "autofill_save_as_new"
    const val UPDATE_PREFIX = "autofill_save_update_"
}

/**
 * What will be saved, before anything is written: the title (editable), the username, and
 * whether this app will get the login suggested automatically. The password is never shown.
 *
 * With the vault locked the button reads "Unlock and save": the user sees what they are
 * unlocking for before they do it.
 */
@Composable
fun AutofillSaveSummaryScreen(
    title: String,
    onTitleChange: (String) -> Unit,
    username: String,
    willBeLinked: Boolean,
    vaultLocked: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    SaveScaffold(heading = stringResource(R.string.autofill_save_title)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.autofill_save_entry_title)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AutofillSaveTags.TITLE)
        )
        Text(
            if (username.isBlank()) stringResource(R.string.autofill_save_no_username)
            else stringResource(R.string.autofill_save_username, username),
            style = MaterialTheme.typography.bodyMedium
        )
        if (!willBeLinked) {
            Text(
                stringResource(R.string.autofill_save_unconfirmed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onSave,
            enabled = title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AutofillSaveTags.CONFIRM)
        ) {
            Text(
                stringResource(
                    if (vaultLocked) R.string.autofill_save_unlock_and_save else R.string.autofill_save_confirm
                )
            )
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AutofillSaveTags.CANCEL)
        ) { Text(stringResource(R.string.autofill_save_cancel)) }
    }
}

/**
 * The username is already saved with another password. Each candidate can be updated, or the
 * login saved as a new entry — the choice is always the user's.
 */
@Composable
fun AutofillPasswordChangeScreen(
    entries: List<VaultEntity>,
    onUpdate: (VaultEntity) -> Unit,
    onSaveAsNew: () -> Unit,
    onCancel: () -> Unit
) {
    SaveScaffold(heading = stringResource(R.string.autofill_save_change_title)) {
        Text(stringResource(R.string.autofill_save_change_message), style = MaterialTheme.typography.bodyMedium)
        entries.forEach { entry ->
            OutlinedButton(
                onClick = { onUpdate(entry) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AutofillSaveTags.UPDATE_PREFIX + entry.id)
            ) { Text(stringResource(R.string.autofill_save_update, entry.title)) }
        }
        TextButton(
            onClick = onSaveAsNew,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AutofillSaveTags.SAVE_AS_NEW)
        ) { Text(stringResource(R.string.autofill_save_as_new)) }
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AutofillSaveTags.CANCEL)
        ) { Text(stringResource(R.string.autofill_save_cancel)) }
    }
}

@Composable
private fun SaveScaffold(heading: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
