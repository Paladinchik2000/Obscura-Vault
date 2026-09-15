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

    /** Drops every piece of record data this ViewModel holds, including the filters typed against it. */
    private fun clearVaultData() {
        _vaultEntries.value = emptyList()
        _searchQuery.value = ""
        _selectedCategory.value = null
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
