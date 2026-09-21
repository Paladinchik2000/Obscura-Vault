package com.obscura.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obscura.BuildConfig
import com.obscura.R
import com.obscura.ui.auth.PIN_LENGTH
import com.obscura.ui.common.asString
import com.obscura.ui.common.labelRes
import kotlinx.coroutines.delay

object SettingsTags {
    const val BACK = "settings_back"
    const val PIN_OPEN = "settings_pin_open"
    const val PIN_CURRENT = "settings_pin_current"
    const val PIN_NEW = "settings_pin_new"
    const val PIN_CONFIRM = "settings_pin_confirm"
    const val PIN_SUBMIT = "settings_pin_submit"
    const val BIO_STATE = "settings_bio_state"
    const val BIO_ACTION = "settings_bio_action"
    const val BIO_DISABLE_CONFIRM = "settings_bio_disable_confirm"

    const val RESET_OPEN = "settings_reset_open"
    const val RESET_BACKUP = "settings_reset_backup"
    const val RESET_CONTINUE = "settings_reset_continue"
    const val RESET_PIN = "settings_reset_pin"
    const val RESET_CONFIRM = "settings_reset_confirm"

    fun autoLock(option: AutoLockOption) = "settings_autolock_" + option.name
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as FragmentActivity

    LaunchedEffect(Unit) { viewModel.refreshBiometrics(activity) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it.asString(context))
            viewModel.clearMessage()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SettingsTags.BACK)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                PinSection(
                    state = state.pinChange,
                    onOpen = viewModel::openPinChange,
                    onCancel = viewModel::cancelPinChange,
                    onCurrentChange = viewModel::onCurrentPinChanged,
                    onNewChange = viewModel::onNewPinChanged,
                    onConfirmChange = viewModel::onConfirmPinChanged,
                    onSubmit = viewModel::submitPinChange
                )

                BiometricsSection(
                    state = state.biometrics,
                    onEnable = { viewModel.enableBiometrics(activity) },
                    onRequestDisable = viewModel::requestDisableBiometrics,
                    onCancelDisable = viewModel::cancelDisableBiometrics,
                    onConfirmDisable = viewModel::confirmDisableBiometrics
                )

                AutoLockSection(selected = state.autoLock, onSelect = viewModel::onAutoLockSelected)

                ResetSection(
                    state = state.reset,
                    onStart = viewModel::startReset,
                    onMakeBackup = {
                        viewModel.cancelReset()
                        onOpenBackup()
                    },
                    onContinue = viewModel::proceedToResetPin,
                    onPinChange = viewModel::onResetPinChanged,
                    onConfirm = viewModel::confirmReset,
                    onCancel = viewModel::cancelReset
                )

                AboutSection()
            }

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PinSection(
    state: PinChangeState,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_pin_title)) {
        if (!state.isOpen) {
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTags.PIN_OPEN)
            ) { Text(stringResource(R.string.settings_pin_change)) }
            return@SettingsSection
        }

        PinField(
            label = stringResource(R.string.settings_pin_current),
            value = state.currentPin,
            onValueChange = onCurrentChange,
            enabled = !state.isBusy,
            testTag = SettingsTags.PIN_CURRENT
        )
        PinField(
            label = stringResource(R.string.settings_pin_new),
            value = state.newPin,
            onValueChange = onNewChange,
            enabled = !state.isBusy,
            testTag = SettingsTags.PIN_NEW
        )
        PinField(
            label = stringResource(R.string.settings_pin_confirm),
            value = state.confirmPin,
            onValueChange = onConfirmChange,
            enabled = !state.isBusy,
            testTag = SettingsTags.PIN_CONFIRM
        )

        val lockedUntil = state.lockedUntil
        val error = state.error
        when {
            lockedUntil != null -> LockoutText(lockedUntil)
            error != null -> Text(
                error.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !state.isBusy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier
                    .weight(1f)
                    .testTag(SettingsTags.PIN_SUBMIT)
            ) { Text(stringResource(R.string.settings_pin_submit)) }
        }
    }
}

/** Counts down the shared login lockout, so settings shows the same wait as the login screen. */
@Composable
private fun LockoutText(lockedUntil: Long) {
    var remaining by remember(lockedUntil) { mutableLongStateOf(lockedUntil - System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (remaining > 0) {
            delay(1000)
            remaining = lockedUntil - System.currentTimeMillis()
        }
    }
    Text(
        stringResource(R.string.login_lockout, (remaining / 1000).coerceAtLeast(0)),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.settings_pin_hint, PIN_LENGTH)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
private fun BiometricsSection(
    state: BiometricsState,
    onEnable: () -> Unit,
    onRequestDisable: () -> Unit,
    onCancelDisable: () -> Unit,
    onConfirmDisable: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_biometrics_title)) {
        Text(
            text = stringResource(
                if (state.isEnabled) R.string.settings_biometrics_enabled
                else state.availability.labelRes()
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(SettingsTags.BIO_STATE)
        )

        if (state.isEnabled) {
            OutlinedButton(
                onClick = onRequestDisable,
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTags.BIO_ACTION)
            ) { Text(stringResource(R.string.settings_biometrics_disable)) }
        } else {
            Button(
                onClick = onEnable,
                enabled = state.canEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTags.BIO_ACTION)
            ) { Text(stringResource(R.string.settings_biometrics_enable)) }
        }
    }

    if (state.confirmDisable) {
        AlertDialog(
            onDismissRequest = onCancelDisable,
            title = { Text(stringResource(R.string.settings_biometrics_disable_title)) },
            text = { Text(stringResource(R.string.settings_biometrics_disable_message)) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDisable,
                    modifier = Modifier.testTag(SettingsTags.BIO_DISABLE_CONFIRM)
                ) { Text(stringResource(R.string.settings_biometrics_disable)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelDisable) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun AutoLockSection(selected: AutoLockOption, onSelect: (AutoLockOption) -> Unit) {
    SettingsSection(title = stringResource(R.string.settings_autolock_title)) {
        AutoLockOption.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) }
                    )
                    .testTag(SettingsTags.autoLock(option))
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(
                    stringResource(option.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ResetSection(
    state: ResetState,
    onStart: () -> Unit,
    onMakeBackup: () -> Unit,
    onContinue: () -> Unit,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_reset_title)) {
        Text(stringResource(R.string.settings_reset_body), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SettingsTags.RESET_OPEN)
        ) { Text(stringResource(R.string.settings_reset_action)) }
    }

    when (state.step) {
        ResetStep.NONE -> Unit

        ResetStep.WARNING -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.settings_reset_warning_title)) },
            text = { Text(stringResource(R.string.settings_reset_warning_message)) },
            confirmButton = {
                TextButton(onClick = onMakeBackup, modifier = Modifier.testTag(SettingsTags.RESET_BACKUP)) {
                    Text(stringResource(R.string.settings_reset_make_backup))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = onContinue, modifier = Modifier.testTag(SettingsTags.RESET_CONTINUE)) {
                        Text(
                            stringResource(R.string.settings_reset_continue),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        ResetStep.PIN -> AlertDialog(
            onDismissRequest = { if (!state.isBusy) onCancel() },
            title = { Text(stringResource(R.string.settings_reset_pin_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_reset_pin_message))
                    PinField(
                        label = stringResource(R.string.settings_pin_current),
                        value = state.pin,
                        onValueChange = onPinChange,
                        enabled = !state.isBusy,
                        testTag = SettingsTags.RESET_PIN
                    )
                    val lockedUntil = state.lockedUntil
                    val error = state.error
                    when {
                        lockedUntil != null -> LockoutText(lockedUntil)
                        error != null -> Text(
                            error.asString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    enabled = state.canConfirm,
                    modifier = Modifier.testTag(SettingsTags.RESET_CONFIRM)
                ) {
                    Text(
                        stringResource(R.string.settings_reset_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel, enabled = !state.isBusy) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AboutSection() {
    SettingsSection(title = stringResource(R.string.settings_about_title)) {
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
        Text(
            stringResource(R.string.settings_offline),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
