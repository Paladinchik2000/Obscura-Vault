package com.obscura.ui.auth

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@SuppressLint("ContextCastToActivity")
@Composable
fun LoginScreen(
    onUnlocked: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val activity = LocalContext.current as FragmentActivity

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) {
            viewModel.consumeUnlock()
            onUnlocked()
        }
    }

    // Offer biometrics immediately on a cold unlock.
    LaunchedEffect(Unit) {
        if (state.canUseBiometrics && !state.isSetupMode && state.lockedUntil == null) {
            viewModel.unlockWithBiometrics(activity)
        }
    }

    val entered = state.confirmPin ?: state.pin
    val title = when {
        state.isSetupMode && state.confirmPin != null -> "Confirm your PIN"
        state.isSetupMode -> "Create a PIN"
        else -> "Obscura"
    }
    val subtitle = when {
        state.isSetupMode && state.confirmPin != null -> "Enter it once more"
        state.isSetupMode -> "$PIN_LENGTH digits, used to protect your vault key"
        else -> "Enter your PIN to unlock"
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))
            PinDots(filled = entered.length, total = PIN_LENGTH, isError = state.error != null)

            Spacer(Modifier.height(20.dp))
            LockoutOrError(state)

            Spacer(Modifier.weight(1f))

            PinKeypad(
                enabled = !state.isBusy && state.lockedUntil == null,
                showBiometric = state.canUseBiometrics && !state.isSetupMode,
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onBiometric = { viewModel.unlockWithBiometrics(activity) }
            )
        }
    }
}

@Composable
private fun LockoutOrError(state: LoginUiState) {
    val lockedUntil = state.lockedUntil
    if (lockedUntil != null) {
        var remaining by remember(lockedUntil) {
            mutableLongStateOf(lockedUntil - System.currentTimeMillis())
        }
        LaunchedEffect(lockedUntil) {
            while (remaining > 0) {
                delay(1000)
                remaining = lockedUntil - System.currentTimeMillis()
            }
        }
        Text(
            "Too many attempts. Try again in ${(remaining / 1000).coerceAtLeast(0)}s",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    } else if (state.error != null) {
        Text(
            buildString {
                append(state.error)
                state.attemptsRemaining?.let { append(" · $it attempts left") }
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PinDots(filled: Int, total: Int, isError: Boolean) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            shake.animateTo(1f, tween(60))
            shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 800f))
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { i ->
            val isFilled = i < filled
            Box(
                Modifier
                    .size(14.dp)
                    .scale(if (isFilled) 1f + shake.value * 0.2f else 1f)
                    .clip(CircleShape)
                    .background(
                        when {
                            isError -> MaterialTheme.colorScheme.error
                            isFilled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(
    enabled: Boolean,
    showBiometric: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (showBiometric) "bio" else "", "0", "del")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(72.dp))
                        "del" -> KeypadButton(enabled = enabled, onClick = onBackspace) {
                            Text("⌫", fontSize = 22.sp)
                        }
                        "bio" -> KeypadButton(enabled = enabled, onClick = onBiometric) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = "Unlock with biometrics")
                        }
                        else -> KeypadButton(enabled = enabled, onClick = { onDigit(key[0]) }) {
                            Text(key, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(72.dp)
    ) { content() }
}
