package com.obscura.nav

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.obscura.security.VaultSession
import com.obscura.ui.auth.LoginScreen

object Routes {
    const val LOGIN = "login"
    const val VAULT = "vault"
}

@Composable
fun ObscuraNavHost(navController: NavHostController = rememberNavController()) {

    // Auto-lock: re-lock when the app leaves the foreground past the idle window.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> VaultSession.touch()
                Lifecycle.Event.ON_START -> VaultSession.lockIfIdle()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isUnlocked by VaultSession.isUnlocked.collectAsState()

    // Any transition to locked kicks the user back to login, wherever they were.
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
                    navController.navigate(Routes.VAULT) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.VAULT) {
            // TODO: replace with the real vault list screen.
            // Access entries via VaultRepositoryImpl: its queries run in the VaultSession scope,
            // which lock() cancels and waits for before closing the database.
            VaultPlaceholderScreen(onLock = { VaultSession.requestLock() })
        }
    }
}
