package com.obscura.ui.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obscura.R
import com.obscura.data.backup.ImportMode
import com.obscura.data.backup.ImportPreview
import com.obscura.data.backup.ImportResult
import com.obscura.security.PasswordGenerator
import com.obscura.ui.common.asString
import com.obscura.ui.common.labelRes

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel()
) {
    val exportUi by viewModel.exportState.collectAsState()
    val importUi by viewModel.importState.collectAsState()
    val busy = exportUi.isBusy || importUi.isBusy

    // The picker creates an empty document; BackupManager deletes it again if the export fails.
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> viewModel.onExportDestinationChosen(uri) }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImportFileChosen(uri)
    }

    // Leaving mid-operation would lose the result; the operation itself keeps running.
    BackHandler(enabled = busy) {}

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.backup_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            ExportSection(
                state = exportUi,
                otherBusy = importUi.isBusy,
                onPasswordChange = viewModel::onExportPasswordChanged,
                onConfirmationChange = viewModel::onExportConfirmationChanged,
                onStart = { createDocument.launch(viewModel.suggestedFileName()) },
                onAcknowledge = viewModel::onExportResultAcknowledged
            )

            ImportSection(
                state = importUi,
                otherBusy = exportUi.isBusy,
                onPickFile = { openDocument.launch(arrayOf("*/*")) },
                onPasswordChange = viewModel::onImportPasswordChanged,
                onDecrypt = viewModel::onImportDecrypt,
                onModeChosen = viewModel::onImportModeChosen,
                onCancel = viewModel::onImportCancelled,
                onAcknowledge = viewModel::onImportResultAcknowledged
            )
        }
    }
}

@Composable
private fun ExportSection(
    state: ExportState,
    otherBusy: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onStart: () -> Unit,
    onAcknowledge: () -> Unit
) {
    SectionCard(title = stringResource(R.string.backup_export_title)) {
        val phase = state.phase
        if (phase is ExportPhase.Done) {
            Text(stringResource(R.string.backup_export_done, phase.entryCount))
            Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_done))
            }
            return@SectionCard
        }

        Text(
            stringResource(R.string.backup_export_warning),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        PasswordField(stringResource(R.string.backup_password_label), state.password, onPasswordChange, enabled = !state.isBusy)
        if (state.password.isNotEmpty()) StrengthIndicator(state.strength)
        PasswordField(
            stringResource(R.string.backup_password_confirm_label),
            state.confirmation,
            onConfirmationChange,
            enabled = !state.isBusy
        )

        val hint = when {
            state.password.isNotEmpty() && state.isTooShort ->
                stringResource(R.string.backup_password_too_short, MIN_BACKUP_PASSWORD_LENGTH)
            state.confirmationMismatch -> stringResource(R.string.backup_password_mismatch)
            else -> null
        }
        hint?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (phase is ExportPhase.Failed) Text(phase.message.asString(), color = MaterialTheme.colorScheme.error)
        if (state.isBusy) BusyRow(stringResource(R.string.backup_export_progress))

        Button(
            onClick = onStart,
            enabled = state.canStart && !otherBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.backup_export_action)) }
    }
}

@Composable
private fun ImportSection(
    state: ImportState,
    otherBusy: Boolean,
    onPickFile: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onDecrypt: () -> Unit,
    onModeChosen: (ImportMode) -> Unit,
    onCancel: () -> Unit,
    onAcknowledge: () -> Unit
) {
    SectionCard(title = stringResource(R.string.backup_import_title)) {
        when (val phase = state.phase) {
            ImportPhase.Idle -> {
                Text(stringResource(R.string.backup_import_intro), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onPickFile, enabled = !otherBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_import_choose_file))
                }
            }

            ImportPhase.ReadingHeader -> BusyRow(stringResource(R.string.backup_import_checking_file))

            is ImportPhase.PasswordRequired -> {
                PasswordField(stringResource(R.string.backup_password_label), state.password, onPasswordChange, enabled = true)
                phase.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = onDecrypt,
                        enabled = state.password.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.backup_import_decrypt)) }
                }
            }

            ImportPhase.Decrypting -> BusyRow(stringResource(R.string.backup_import_decrypting))

            is ImportPhase.ChooseMode -> ModeChoice(phase.preview, onModeChosen, onCancel)

            ImportPhase.Writing -> BusyRow(stringResource(R.string.backup_import_writing))

            is ImportPhase.Done -> {
                Text(importResultText(phase.result))
                Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_done))
                }
            }

            is ImportPhase.Failed -> {
                Text(phase.message.asString(), color = MaterialTheme.colorScheme.error)
                Button(onClick = onPickFile, enabled = !otherBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_import_choose_other_file))
                }
                TextButton(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}

/** The summary is shown before anything is written; each button states what it will do. */
@Composable
private fun ModeChoice(preview: ImportPreview, onModeChosen: (ImportMode) -> Unit, onCancel: () -> Unit) {
    val merge = preview.merge
    Text(stringResource(R.string.backup_import_counts, preview.backupEntryCount, preview.existingEntryCount))

    Text(stringResource(R.string.backup_merge_title), fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.backup_merge_counts, merge.added, merge.updated, merge.unchanged))
    if (merge.keptNewer > 0) {
        Text(
            LocalContext.current.resources.getQuantityString(R.plurals.backup_merge_kept_newer, merge.keptNewer, merge.keptNewer),
            style = MaterialTheme.typography.bodySmall
        )
    }
    Text(stringResource(R.string.backup_merge_rule), style = MaterialTheme.typography.bodySmall)
    Button(onClick = { onModeChosen(ImportMode.MERGE) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_merge_action))
    }

    Text(stringResource(R.string.backup_replace_title), fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.backup_replace_summary, preview.existingEntryCount, preview.backupEntryCount),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
    Button(
        onClick = { onModeChosen(ImportMode.REPLACE) },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.backup_replace_action)) }

    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_cancel))
    }
}

@Composable
private fun importResultText(result: ImportResult): String = when (result.mode) {
    ImportMode.REPLACE -> stringResource(R.string.backup_import_done_replace, result.removed, result.added)
    ImportMode.MERGE -> stringResource(R.string.backup_import_done_merge, result.added, result.updated, result.unchanged)
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StrengthIndicator(strength: PasswordGenerator.PasswordStrength) {
    val color = when (strength.level) {
        PasswordGenerator.StrengthLevel.EXCELLENT, PasswordGenerator.StrengthLevel.STRONG -> MaterialTheme.colorScheme.primary
        PasswordGenerator.StrengthLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
        PasswordGenerator.StrengthLevel.WEAK, PasswordGenerator.StrengthLevel.EMPTY -> MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { strength.score / 100f },
            color = color,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.backup_strength, stringResource(strength.level.labelRes())),
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
