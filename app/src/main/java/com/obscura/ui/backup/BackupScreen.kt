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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obscura.data.backup.ImportMode
import com.obscura.data.backup.ImportPreview
import com.obscura.data.backup.ImportResult
import com.obscura.security.PasswordGenerator

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text("Резервная копия", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
    SectionCard(title = "Экспорт") {
        val phase = state.phase
        if (phase is ExportPhase.Done) {
            Text("Резервная копия сохранена. Записей в файле: ${phase.entryCount}.")
            Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            return@SectionCard
        }

        Text(
            "Пароль резервной копии нигде не хранится и не восстанавливается. " +
                "Если вы его забудете, файл невозможно будет расшифровать.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        PasswordField("Пароль резервной копии", state.password, onPasswordChange, enabled = !state.isBusy)
        if (state.password.isNotEmpty()) StrengthIndicator(state.strength)
        PasswordField("Повторите пароль", state.confirmation, onConfirmationChange, enabled = !state.isBusy)

        val hint = when {
            state.password.isNotEmpty() && state.isTooShort -> "Минимум $MIN_BACKUP_PASSWORD_LENGTH символов"
            state.confirmationMismatch -> "Пароли не совпадают"
            else -> null
        }
        hint?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (phase is ExportPhase.Failed) Text(phase.message, color = MaterialTheme.colorScheme.error)
        if (state.isBusy) BusyRow("Шифрование… Вывод ключа из пароля занимает несколько секунд.")

        Button(
            onClick = onStart,
            enabled = state.canStart && !otherBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить резервную копию") }
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
    SectionCard(title = "Импорт") {
        when (val phase = state.phase) {
            ImportPhase.Idle -> {
                Text(
                    "Выберите файл резервной копии (.obvb). Версия формата проверяется до ввода пароля.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onPickFile, enabled = !otherBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать файл")
                }
            }

            ImportPhase.ReadingHeader -> BusyRow("Проверка файла…")

            is ImportPhase.PasswordRequired -> {
                PasswordField("Пароль резервной копии", state.password, onPasswordChange, enabled = true)
                phase.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = onDecrypt,
                        enabled = state.password.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Расшифровать") }
                }
            }

            ImportPhase.Decrypting -> BusyRow("Проверка пароля и расшифровка… Вывод ключа занимает несколько секунд.")

            is ImportPhase.ChooseMode -> ModeChoice(phase.preview, onModeChosen, onCancel)

            ImportPhase.Writing -> BusyRow("Запись в хранилище…")

            is ImportPhase.Done -> {
                Text(importResultText(phase.result))
                Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            }

            is ImportPhase.Failed -> {
                Text(phase.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onPickFile, enabled = !otherBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать другой файл")
                }
                TextButton(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            }
        }
    }
}

/** The summary is shown before anything is written; each button states what it will do. */
@Composable
private fun ModeChoice(preview: ImportPreview, onModeChosen: (ImportMode) -> Unit, onCancel: () -> Unit) {
    val merge = preview.merge
    Text("В файле записей: ${preview.backupEntryCount}. Сейчас в хранилище: ${preview.existingEntryCount}.")

    Text("Объединить", fontWeight = FontWeight.SemiBold)
    Text("Добавится: ${merge.added}. Обновится: ${merge.updated}. Не изменится: ${merge.unchanged}.")
    if (merge.keptNewer > 0) {
        Text(
            "Из неизменённых ${merge.keptNewer} — записи, которые в хранилище новее, чем в файле. Они останутся как есть.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    Text(
        "При совпадении остаётся запись, изменённая позже; при одинаковом времени изменения — запись из хранилища.",
        style = MaterialTheme.typography.bodySmall
    )
    Button(onClick = { onModeChosen(ImportMode.MERGE) }, modifier = Modifier.fillMaxWidth()) {
        Text("Объединить")
    }

    Text("Заменить", fontWeight = FontWeight.SemiBold)
    Text(
        "Будут удалены все текущие записи (${preview.existingEntryCount}) и записаны записи из файла (${preview.backupEntryCount}).",
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
    ) { Text("Заменить все записи") }

    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
}

private fun importResultText(result: ImportResult): String = when (result.mode) {
    ImportMode.REPLACE ->
        "Импорт с заменой завершён. Удалено записей: ${result.removed}, записано из файла: ${result.added}."
    ImportMode.MERGE ->
        "Импорт завершён. Добавлено: ${result.added}, обновлено: ${result.updated}, без изменений: ${result.unchanged}."
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
    val (label, color) = when {
        strength.score >= 80 -> "отличная" to MaterialTheme.colorScheme.primary
        strength.score >= 60 -> "высокая" to MaterialTheme.colorScheme.primary
        strength.score >= 40 -> "средняя" to MaterialTheme.colorScheme.tertiary
        else -> "слабая" to MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { strength.score / 100f },
            color = color,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Стойкость: $label", color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
