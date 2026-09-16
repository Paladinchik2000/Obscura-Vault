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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obscura.R
import com.obscura.ui.common.asString
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
        state.isSetupMode && state.confirmPin != null -> stringResource(R.string.login_title_confirm_pin)
        state.isSetupMode -> stringResource(R.string.login_title_create_pin)
        else -> stringResource(R.string.login_title_unlock)
    }
    val subtitle = when {
        state.isSetupMode && state.confirmPin != null -> stringResource(R.string.login_subtitle_confirm_pin)
        state.isSetupMode -> stringResource(R.string.login_subtitle_create_pin, PIN_LENGTH)
        else -> stringResource(R.string.login_subtitle_unlock)
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
    val error = state.error
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
            stringResource(R.string.login_lockout, (remaining / 1000).coerceAtLeast(0)),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    } else if (error != null) {
        val message = error.asString()
        val attempts = state.attemptsRemaining
        val text = if (attempts == null) {
            message
        } else {
            val attemptsLeft = LocalContext.current.resources
                .getQuantityString(R.plurals.login_attempts_left, attempts, attempts)
            stringResource(R.string.login_error_with_attempts, message, attemptsLeft)
        }
        Text(
            text,
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
    // Digits are keypad labels, not language; the two special keys use resources.
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (showBiometric) "bio" else "", "0", "del")
    )
    val backspaceDescription = stringResource(R.string.keypad_backspace)

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
                            Text(
                                stringResource(R.string.keypad_backspace_symbol),
                                fontSize = 22.sp,
                                modifier = Modifier.semantics { contentDescription = backspaceDescription }
                            )
                        }
                        "bio" -> KeypadButton(enabled = enabled, onClick = onBiometric) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = stringResource(R.string.keypad_biometric))
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
