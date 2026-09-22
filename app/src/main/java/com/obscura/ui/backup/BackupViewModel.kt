package com.obscura.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obscura.R
import com.obscura.data.backup.BackupContents
import com.obscura.data.backup.BackupManager
import com.obscura.data.backup.ImportMode
import com.obscura.data.backup.ImportPreview
import com.obscura.data.backup.ImportResult
import com.obscura.data.local.VaultEntity
import com.obscura.security.BackupFormatException
import com.obscura.security.PasswordGenerator
import com.obscura.security.UnsupportedBackupVersionException
import com.obscura.security.VaultLockedException
import com.obscura.security.VaultSession
import com.obscura.ui.common.UiText
import com.obscura.ui.common.uiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException

const val MIN_BACKUP_PASSWORD_LENGTH = 12

internal object BackupMessages {
    val WRONG_PASSWORD = uiText(R.string.error_wrong_password)
    val NOT_A_BACKUP = uiText(R.string.error_not_a_backup)
    val VAULT_LOCKED = uiText(R.string.error_backup_vault_locked)
    val READ_FAILED = uiText(R.string.error_backup_read_failed)
    val WRITE_FAILED = uiText(R.string.error_backup_write_failed)

    fun unsupportedVersion(found: Int, supported: Int) =
        uiText(R.string.error_unsupported_backup_version, found, supported)
}

sealed interface ExportPhase {
    data object Editing : ExportPhase
    data object Running : ExportPhase
    data class Done(val entryCount: Int) : ExportPhase
    data class Failed(val message: UiText) : ExportPhase
}

data class ExportState(
    val password: String = "",
    val confirmation: String = "",
    val phase: ExportPhase = ExportPhase.Editing
) {
    val strength: PasswordGenerator.PasswordStrength = PasswordGenerator.evaluateStrength(password)
    val isTooShort: Boolean get() = password.length < MIN_BACKUP_PASSWORD_LENGTH
    val confirmationMismatch: Boolean get() = confirmation.isNotEmpty() && confirmation != password
    val isBusy: Boolean get() = phase == ExportPhase.Running
    val canStart: Boolean get() = !isTooShort && confirmation == password && !isBusy
}

sealed interface ImportPhase {
    data object Idle : ImportPhase
    data object ReadingHeader : ImportPhase
    data class PasswordRequired(val error: UiText? = null) : ImportPhase
    data object Decrypting : ImportPhase

    /** Decrypted; nothing written yet. [preview] tells the user what each mode would do. */
    data class ChooseMode(val preview: ImportPreview) : ImportPhase
    data object Writing : ImportPhase
    data class Done(val result: ImportResult) : ImportPhase
    data class Failed(val message: UiText) : ImportPhase
}

data class ImportState(
    val password: String = "",
    val phase: ImportPhase = ImportPhase.Idle
) {
    val isBusy: Boolean
        get() = phase == ImportPhase.ReadingHeader || phase == ImportPhase.Decrypting || phase == ImportPhase.Writing
}

/**
 * State for the backup screen. Lives in the ViewModel, so an export or import in progress
 * survives rotation; the work itself runs in the vault session via [BackupManager].
 * Passwords and decrypted entries are kept in memory only and wiped when the vault locks.
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val backupManager = BackupManager(application)

    private val _exportState = MutableStateFlow(ExportState())
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private var importUri: Uri? = null
    private var pendingContents: BackupContents? = null

    init {
        viewModelScope.launch {
            VaultSession.isUnlocked.collect { unlocked -> if (!unlocked) clearAll() }
        }
    }

    /** A file name, not display text: the same in every language. */
    fun suggestedFileName(): String =
        "obscura-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.obvb"

    // ------------------------------------------------------------------ export

    fun onExportPasswordChanged(value: String) = _exportState.update {
        if (it.isBusy) it else it.copy(password = value, phase = ExportPhase.Editing)
    }

    fun onExportConfirmationChanged(value: String) = _exportState.update {
        if (it.isBusy) it else it.copy(confirmation = value, phase = ExportPhase.Editing)
    }

    /** Called with the document chosen in CreateDocument; null when the picker was cancelled. */
    fun onExportDestinationChosen(uri: Uri?) {
        val state = _exportState.value
        if (uri == null || !state.canStart) return
        _exportState.value = state.copy(phase = ExportPhase.Running)

        viewModelScope.launch {
            val password = state.password.toCharArray()
            val result = try {
                backupManager.exportVaultToFile(uri, password)
            } finally {
                password.fill(Char(0))
            }
            _exportState.value = result.fold(
                onSuccess = { count -> ExportState(phase = ExportPhase.Done(count)) },
                onFailure = { e -> state.copy(phase = ExportPhase.Failed(exportErrorMessage(e))) }
            )
        }
    }

    fun onExportResultAcknowledged() {
        if (!_exportState.value.isBusy) _exportState.value = ExportState()
    }

    // ------------------------------------------------------------------ import

    /** Called with the document chosen in OpenDocument; null when the picker was cancelled. */
    fun onImportFileChosen(uri: Uri?) {
        if (uri == null || _importState.value.isBusy) return
        resetImport()
        importUri = uri
        _importState.value = ImportState(phase = ImportPhase.ReadingHeader)

        viewModelScope.launch {
            // The header is checked before any password is requested.
            _importState.value = backupManager.readFormatVersion(uri).fold(
                onSuccess = { ImportState(phase = ImportPhase.PasswordRequired()) },
                onFailure = { e -> ImportState(phase = ImportPhase.Failed(importErrorMessage(e))) }
            )
        }
    }

    fun onImportPasswordChanged(value: String) = _importState.update {
        if (it.phase is ImportPhase.PasswordRequired) it.copy(password = value) else it
    }

    fun onImportDecrypt() {
        val uri = importUri ?: return
        val state = _importState.value
        if (state.phase !is ImportPhase.PasswordRequired || state.password.isEmpty()) return
        _importState.value = state.copy(phase = ImportPhase.Decrypting)

        viewModelScope.launch {
            val password = state.password.toCharArray()
            val decrypted = try {
                backupManager.decryptBackup(uri, password)
            } finally {
                password.fill(Char(0))
            }
            decrypted.fold(
                onSuccess = { contents ->
                    val preview = backupManager.previewImport(contents).getOrElse { e ->
                        _importState.value = ImportState(phase = ImportPhase.Failed(importErrorMessage(e)))
                        return@launch
                    }
                    pendingContents = contents
                    _importState.value = ImportState(phase = ImportPhase.ChooseMode(preview))
                },
                onFailure = { e ->
                    _importState.value = if (e.isWrongPassword()) {
                        ImportState(phase = ImportPhase.PasswordRequired(error = BackupMessages.WRONG_PASSWORD))
                    } else {
                        ImportState(phase = ImportPhase.Failed(importErrorMessage(e)))
                    }
                }
            )
        }
    }

    fun onImportModeChosen(mode: ImportMode) {
        val contents = pendingContents ?: return
        if (_importState.value.phase !is ImportPhase.ChooseMode) return
        _importState.value = ImportState(phase = ImportPhase.Writing)

        viewModelScope.launch {
            val result = backupManager.restore(contents, mode)
            pendingContents = null
            _importState.value = ImportState(
                phase = result.fold(
                    onSuccess = { done -> ImportPhase.Done(done) },
                    onFailure = { e -> ImportPhase.Failed(importErrorMessage(e)) }
                )
            )
        }
    }

    fun onImportCancelled() {
        if (!_importState.value.isBusy) resetImport()
    }

    fun onImportResultAcknowledged() = onImportCancelled()

    override fun onCleared() {
        pendingContents = null
        importUri = null
    }

    private fun resetImport() {
        importUri = null
        pendingContents = null
        _importState.value = ImportState()
    }

    private fun clearAll() {
        _exportState.value = ExportState()
        resetImport()
    }

    private fun Throwable.isWrongPassword() = this is SecurityException && cause is AEADBadTagException

    private fun importErrorMessage(e: Throwable): UiText = when {
        e is UnsupportedBackupVersionException -> BackupMessages.unsupportedVersion(e.found, e.supported)
        e is BackupFormatException -> BackupMessages.NOT_A_BACKUP
        e.isWrongPassword() -> BackupMessages.WRONG_PASSWORD
        e is VaultLockedException -> BackupMessages.VAULT_LOCKED
        // Authenticated but not parseable: not something this app wrote.
        e is JSONException -> BackupMessages.NOT_A_BACKUP
        else -> BackupMessages.READ_FAILED
    }

    private fun exportErrorMessage(e: Throwable): UiText = when (e) {
        is VaultLockedException -> BackupMessages.VAULT_LOCKED
        else -> BackupMessages.WRITE_FAILED
    }
}
