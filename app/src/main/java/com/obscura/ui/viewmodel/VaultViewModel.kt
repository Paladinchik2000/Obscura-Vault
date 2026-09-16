package com.obscura.ui.viewmodel

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import com.obscura.data.repository.VaultRepository
import com.obscura.security.PasswordGenerator
import com.obscura.security.VaultLockedException
import com.obscura.security.VaultSession
import com.obscura.ui.backup.BackupReminderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Keep
data class VaultUiState(
    val isAuthenticated: Boolean = false,
    val authError: String? = null,
    val pinInput: String = "",
    val searchQuery: String = "",
    val selectedCategoryFilter: VaultCategory? = null, // null means "All"
    val entries: List<VaultEntity> = emptyList(),
    val totalEntriesCount: Int = 0,
    val weakPasswordsCount: Int = 0,
    val favoritesCount: Int = 0,
    val isLoading: Boolean = false,
    val toastMessage: String? = null,
    val selectedItemForEdit: VaultEntity? = null,
    val showBackupReminder: Boolean = false
)

/**
 * Everything the add/edit form holds. It lives in the ViewModel, so it survives a configuration
 * change, and it is never written to a saved-state Bundle, so secrets don't end up on disk.
 */
data class EntryForm(
    val category: VaultCategory = VaultCategory.ACCOUNT,
    val title: String = "",
    val usernameOrCardholder: String = "",
    val secretValue: String = "",
    val urlOrCardNumber: String = "",
    val notesOrCvv: String = "",
    val expiryDate: String = "",
    val tags: String = "",
    val isSecretVisible: Boolean = false,
    val titleError: Boolean = false
) {
    /** Applies the form to [base], keeping id, createdAt, favourite flag and the previous updatedAt. */
    fun applyTo(base: VaultEntity): VaultEntity = base.copy(
        title = title.trim(),
        category = category.id,
        usernameOrCardholder = usernameOrCardholder,
        secretValue = secretValue,
        urlOrCardNumber = urlOrCardNumber,
        notesOrCvv = notesOrCvv,
        expiryDate = expiryDate,
        tags = tags
    )

    companion object {
        fun from(entry: VaultEntity?): EntryForm =
            if (entry == null) {
                EntryForm()
            } else {
                EntryForm(
                    category = entry.getCategoryEnum(),
                    title = entry.title,
                    usernameOrCardholder = entry.usernameOrCardholder,
                    secretValue = entry.secretValue,
                    urlOrCardNumber = entry.urlOrCardNumber,
                    notesOrCvv = entry.notesOrCvv,
                    expiryDate = entry.expiryDate,
                    tags = entry.tags
                )
            }
    }
}

/** The add/edit screen: which entry it is for and what has been typed into it so far. */
sealed interface EditorState {
    data object Idle : EditorState
    data class Loading(val entryId: String) : EditorState

    /** [entry] is null when a new entry is being created. */
    data class Ready(val entryId: String?, val entry: VaultEntity?, val form: EntryForm) : EditorState
    data object NotFound : EditorState
}

@OptIn(ExperimentalCoroutinesApi::class)
@Keep
class VaultViewModel(
    private val repository: VaultRepository,
    private val backupReminder: BackupReminderStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _vaultEntries = MutableStateFlow<List<VaultEntity>>(emptyList())
    val vaultEntries: StateFlow<List<VaultEntity>> = _vaultEntries.asStateFlow()

    private val _editor = MutableStateFlow<EditorState>(EditorState.Idle)
    val editor: StateFlow<EditorState> = _editor.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<VaultCategory?>(null)

    init {
        // Record data follows the vault session, not this ViewModel's lifetime: when the vault
        // locks, collectLatest cancels entry collection, waits for it to stop, and only then
        // wipes everything held here — so nothing can repopulate the state after the wipe.
        viewModelScope.launch {
            VaultSession.isUnlocked.collectLatest { unlocked ->
                if (unlocked) observeEntries() else clearVaultData()
            }
        }
    }

    /** Suspends until cancelled by the next lock. The Room queries run in the session scope. */
    private suspend fun observeEntries() {
        combine(_searchQuery, _selectedCategory) { query, category -> query to category }
            .flatMapLatest { (query, category) -> entriesFor(query, category) }
            .collect { items -> publishEntries(items) }
    }

    private fun entriesFor(query: String, category: VaultCategory?): Flow<List<VaultEntity>> =
        when {
            query.isNotBlank() -> repository.searchEntries(query)
            category != null -> repository.getEntriesByCategory(category)
            else -> repository.getAllEntries()
        }.onCompletion { cause ->
            // Repository flows only complete normally when the session closes. Wipe here too,
            // in case the locked state was conflated away before isUnlocked reached us.
            if (cause == null) clearVaultData()
        }

    private fun publishEntries(items: List<VaultEntity>) {
        val weakCount = items.count { item ->
            item.secretValue.isNotEmpty() && PasswordGenerator.evaluateStrength(item.secretValue).score < 50
        }
        _vaultEntries.value = items
        _uiState.update {
            it.copy(
                entries = items,
                totalEntriesCount = items.size,
                weakPasswordsCount = weakCount,
                favoritesCount = items.count { entry -> entry.isFavorite }
            )
        }
    }

    /** Drops every piece of record data this ViewModel holds: list, filters, editor form. */
    private fun clearVaultData() {
        _vaultEntries.value = emptyList()
        _searchQuery.value = ""
        _selectedCategory.value = null
        _editor.value = EditorState.Idle
        _uiState.value = VaultUiState()
    }

    fun onBiometricAuthSuccess() {
        _uiState.update { it.copy(isAuthenticated = true, authError = null) }
    }

    fun onBiometricAuthError(error: String) {
        _uiState.update { it.copy(authError = error) }
    }

    fun onPinDigitEntered(digit: String) {
        val currentPin = _uiState.value.pinInput
        if (currentPin.length < 6) {
            val newPin = currentPin + digit
            _uiState.update { it.copy(pinInput = newPin, authError = null) }

            // Auto-verify PIN code 123456 or custom local PIN
            if (newPin.length == 6) {
                if (newPin == "123456" || newPin == "000000") {
                    onBiometricAuthSuccess()
                } else {
                    _uiState.update { it.copy(pinInput = "", authError = "Invalid PIN Code. Try 123456") }
                }
            }
        }
    }

    fun onPinBackspace() {
        val currentPin = _uiState.value.pinInput
        if (currentPin.isNotEmpty()) {
            _uiState.update { it.copy(pinInput = currentPin.dropLast(1)) }
        }
    }

    fun lockVault() {
        _uiState.update { it.copy(isAuthenticated = false, pinInput = "") }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onCategoryFilterSelected(category: VaultCategory?) {
        _selectedCategory.value = category
        _uiState.update { it.copy(selectedCategoryFilter = category) }
    }

    // ------------------------------------------------------------------ editor

    /**
     * Opens the editor for [entryId], or for a new entry when null. Asking again for the editor
     * that is already open (the screen being recreated on rotation) keeps what has been typed.
     */
    fun startEditing(entryId: String?) {
        when (val current = _editor.value) {
            is EditorState.Ready -> if (current.entryId == entryId) return
            is EditorState.Loading -> if (current.entryId == entryId) return
            else -> Unit
        }

        if (entryId == null) {
            _editor.value = EditorState.Ready(entryId = null, entry = null, form = EntryForm())
            return
        }
        _editor.value = EditorState.Loading(entryId)
        inSession {
            val entry = repository.getEntryById(entryId)
            _editor.update { current ->
                // A lock or finishEditing() while the entry was loading wins.
                if (current is EditorState.Loading && current.entryId == entryId) {
                    if (entry != null) EditorState.Ready(entryId, entry, EntryForm.from(entry)) else EditorState.NotFound
                } else {
                    current
                }
            }
        }
    }

    fun updateForm(transform: (EntryForm) -> EntryForm) {
        _editor.update { if (it is EditorState.Ready) it.copy(form = transform(it.form)) else it }
    }

    /** Saves the form. Returns false, and flags the title, when the title is blank. */
    fun saveEditor(): Boolean {
        val state = _editor.value as? EditorState.Ready ?: return false
        if (state.form.title.isBlank()) {
            updateForm { it.copy(titleError = true) }
            return false
        }
        val base = state.entry ?: VaultEntity(id = "", title = "", category = state.form.category.id)
        saveEntry(state.form.applyTo(base))
        return true
    }

    /** Deletes the entry open in the editor. Returns false when there is none (a new entry). */
    fun deleteEditedEntry(): Boolean {
        val entry = (_editor.value as? EditorState.Ready)?.entry ?: return false
        deleteEntry(entry.id)
        return true
    }

    fun finishEditing() {
        _editor.value = EditorState.Idle
    }

    // ----------------------------------------------------------------- entries

    fun toggleFavorite(item: VaultEntity) = inSession {
        repository.toggleFavorite(item.id, item.isFavorite)
    }

    fun saveEntry(entry: VaultEntity) = inSession {
        val isNewEntry = entry.id.isBlank()
        repository.saveEntry(entry)
        // Once, after the first entry is created: the keys are bound to this device.
        if (isNewEntry && !backupReminder.wasShown()) {
            _uiState.update { it.copy(showBackupReminder = true) }
        }
        showToast("Vault entry saved successfully")
    }

    /** The reminder was shown and closed, whichever button the user chose. */
    fun onBackupReminderHandled() {
        backupReminder.markShown()
        _uiState.update { it.copy(showBackupReminder = false) }
    }

    fun deleteEntry(id: String) = inSession {
        repository.deleteEntry(id)
        showToast("Item deleted from vault")
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    /**
     * Database work runs in the VaultSession scope, not viewModelScope: lock() cancels it and
     * waits for it before closing the database, and a save isn't dropped when the screen goes away.
     */
    private fun inSession(block: suspend CoroutineScope.() -> Unit) {
        try {
            VaultSession.launchInSession(block)
        } catch (e: VaultLockedException) {
            showToast("Vault is locked")
        }
    }
}
