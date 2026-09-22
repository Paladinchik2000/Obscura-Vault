package com.obscura.nav

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.obscura.data.repository.VaultRepositoryImpl
import com.obscura.security.VaultSession
import com.obscura.ui.auth.LoginScreen
import com.obscura.ui.backup.BackupScreen
import com.obscura.ui.backup.PrefsBackupReminderStore
import com.obscura.ui.dashboard.DashboardScreen
import com.obscura.ui.detail.AddEditVaultScreen
import com.obscura.ui.settings.SettingsScreen
import com.obscura.ui.viewmodel.EditorState
import com.obscura.ui.viewmodel.VaultViewModel

object Routes {
    const val LOGIN = "login"

    /** Nested graph holding every screen that needs an unlocked vault. */
    const val VAULT_GRAPH = "vault"
    const val DASHBOARD = "dashboard"
    const val ENTRY_NEW = "entry/new"
    const val ENTRY_EDIT = "entry/edit/{id}"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"

    fun editEntry(id: String) = "entry/edit/${Uri.encode(id)}"
}

@Composable
fun ObscuraNavHost(navController: NavHostController = rememberNavController()) {

    // Auto-lock lives in ObscuraApplication on ProcessLifecycleOwner: it must not depend on
    // this screen being the one that opened the vault.

    val isUnlocked by VaultSession.isUnlocked.collectAsState()

    // Any transition to locked kicks the user back to login and clears the whole back stack.
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked && navController.currentDestination?.route != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onUnlocked = {
                    navController.navigate(Routes.VAULT_GRAPH) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        navigation(startDestination = Routes.DASHBOARD, route = Routes.VAULT_GRAPH) {

            composable(Routes.DASHBOARD) { backStackEntry ->
                WhenUnlocked {
                    val viewModel = vaultViewModel(navController, backStackEntry)
                    val state by viewModel.uiState.collectAsState()
                    DashboardScreen(
                        entries = state.entries,
                        totalEntriesCount = state.totalEntriesCount,
                        weakPasswordsCount = state.weakPasswordsCount,
                        searchQuery = state.searchQuery,
                        selectedCategory = state.selectedCategoryFilter,
                        toastMessage = state.toastMessage,
                        onSearchQueryChanged = viewModel::onSearchQueryChanged,
                        onCategorySelected = viewModel::onCategoryFilterSelected,
                        onItemClick = { navController.navigate(Routes.editEntry(it.id)) },
                        onAddNewClick = { navController.navigate(Routes.ENTRY_NEW) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onLockVault = { VaultSession.requestLock() },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) { launchSingleTop = true } },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onClearToast = viewModel::clearToast,
                        showBackupReminder = state.showBackupReminder,
                        onBackupReminderHandled = viewModel::onBackupReminderHandled
                    )
                }
            }

            composable(Routes.ENTRY_NEW) { backStackEntry ->
                WhenUnlocked {
                    EntryEditor(navController, vaultViewModel(navController, backStackEntry), entryId = null)
                }
            }

            composable(
                Routes.ENTRY_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                WhenUnlocked {
                    val id = backStackEntry.arguments?.getString("id")
                    if (id == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        EntryEditor(navController, vaultViewModel(navController, backStackEntry), entryId = id)
                    }
                }
            }

            composable(Routes.BACKUP) {
                WhenUnlocked {
                    BackupScreen(onBack = { navController.popBackStack() })
                }
            }

            composable(Routes.SETTINGS) {
                WhenUnlocked {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) { launchSingleTop = true } }
                    )
                }
            }
        }
    }
}

/**
 * Renders vault screens only while the vault is unlocked. The LaunchedEffect above navigates to
 * login on lock; this also covers the frames before that, e.g. a back stack restored after
 * process death while the vault is locked.
 */
@Composable
private fun WhenUnlocked(content: @Composable () -> Unit) {
    val unlocked by VaultSession.isUnlocked.collectAsState()
    if (unlocked) content()
}

/**
 * One VaultViewModel for the whole vault graph: the dashboard sees what the editor saved,
 * including the one-time backup reminder raised after the first entry is created.
 */
@Composable
private fun vaultViewModel(navController: NavHostController, backStackEntry: NavBackStackEntry): VaultViewModel {
    val graphEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.VAULT_GRAPH) }
    val appContext = LocalContext.current.applicationContext
    return viewModel(
        viewModelStoreOwner = graphEntry,
        factory = viewModelFactory {
            initializer { VaultViewModel(VaultRepositoryImpl(), PrefsBackupReminderStore(appContext)) }
        }
    )
}

@Composable
private fun EntryEditor(navController: NavHostController, viewModel: VaultViewModel, entryId: String?) {
    // Runs again when the activity is recreated; the ViewModel keeps the open form in that case.
    LaunchedEffect(entryId) { viewModel.startEditing(entryId) }
    val editor by viewModel.editor.collectAsState()

    val close: () -> Unit = {
        viewModel.finishEditing()
        navController.popBackStack()
    }
    BackHandler(onBack = close)

    when (val state = editor) {
        is EditorState.Ready -> if (state.entryId == entryId) {
            AddEditVaultScreen(
                isNewEntry = state.entry == null,
                form = state.form,
                onFormChange = viewModel::updateForm,
                onSaveClick = { if (viewModel.saveEditor()) close() },
                onDeleteClick = if (state.entry != null) {
                    {
                        viewModel.deleteEditedEntry()
                        close()
                    }
                } else {
                    null
                },
                onBackClick = close
            )
        }
        EditorState.NotFound -> LaunchedEffect(Unit) { close() }
        EditorState.Idle, is EditorState.Loading -> Unit
    }
}
