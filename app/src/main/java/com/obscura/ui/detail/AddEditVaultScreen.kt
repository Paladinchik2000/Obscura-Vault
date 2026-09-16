package com.obscura.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obscura.data.model.VaultCategory
import com.obscura.security.PasswordGenerator
import com.obscura.ui.clipboard.SensitiveClipboard
import com.obscura.ui.theme.CanvasBlack
import com.obscura.ui.theme.CardBackground
import com.obscura.ui.theme.CardBorder
import com.obscura.ui.theme.CrimsonPrimary
import com.obscura.ui.theme.SecurityGreen
import com.obscura.ui.theme.SecurityRed
import com.obscura.ui.theme.SecurityYellow
import com.obscura.ui.theme.TextMuted
import com.obscura.ui.theme.TextPrimary
import com.obscura.ui.theme.TextSecondary
import com.obscura.ui.viewmodel.EntryForm
import kotlinx.coroutines.launch

object EntryTags {
    const val TITLE = "entry_title"
    const val USERNAME = "entry_username"
    const val SECRET = "entry_secret"
    const val SAVE = "entry_save"
}

/**
 * Stateless editor: the form itself lives in VaultViewModel, so typed values survive rotation
 * without going into saved instance state. Only non-secret dialog flags are saved here.
 */
@Composable
fun AddEditVaultScreen(
    isNewEntry: Boolean,
    form: EntryForm,
    onFormChange: ((EntryForm) -> EntryForm) -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
    onBackClick: () -> Unit
) {
    var showGeneratorDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    val strength = remember(form.secretValue) { PasswordGenerator.evaluateStrength(form.secretValue) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isNewEntry) "New Secret Record" else "Edit Secret Record",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (onDeleteClick != null) {
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = SecurityRed
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Selector
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaultCategory.entries.forEach { cat ->
                    val isSelected = form.category == cat
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CrimsonPrimary else CardBackground)
                            .border(
                                1.dp,
                                if (isSelected) CrimsonPrimary else CardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onFormChange { it.copy(category = cat) } }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CanvasBlack else TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Form Fields
            CustomInputField(
                label = "Title / Service Name",
                value = form.title,
                onValueChange = { value ->
                    onFormChange { it.copy(title = value, titleError = it.titleError && value.isBlank()) }
                },
                placeholder = "e.g. GitHub, Chase Bank, Server Key",
                isError = form.titleError,
                errorText = "A title is required",
                testTag = EntryTags.TITLE
            )

            when (form.category) {
                VaultCategory.ACCOUNT -> {
                    CustomInputField(
                        label = "Website / App URL",
                        value = form.urlOrCardNumber,
                        onValueChange = { value -> onFormChange { it.copy(urlOrCardNumber = value) } },
                        placeholder = "e.g. https://github.com"
                    )

                    CustomInputField(
                        label = "Username / Email",
                        value = form.usernameOrCardholder,
                        onValueChange = { value -> onFormChange { it.copy(usernameOrCardholder = value) } },
                        placeholder = "user@example.com",
                        testTag = EntryTags.USERNAME
                    )
                }

                VaultCategory.BANK_CARD -> {
                    CustomInputField(
                        label = "Cardholder Name",
                        value = form.usernameOrCardholder,
                        onValueChange = { value -> onFormChange { it.copy(usernameOrCardholder = value) } },
                        placeholder = "JOHN DOE",
                        testTag = EntryTags.USERNAME
                    )

                    CustomInputField(
                        label = "Card Number",
                        value = form.urlOrCardNumber,
                        onValueChange = { value -> onFormChange { it.copy(urlOrCardNumber = value) } },
                        placeholder = "4532 •••• •••• 8892"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CustomInputField(
                                label = "Expiry Date",
                                value = form.expiryDate,
                                onValueChange = { value -> onFormChange { it.copy(expiryDate = value) } },
                                placeholder = "MM/YY"
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CustomInputField(
                                label = "CVV / CVC",
                                value = form.notesOrCvv,
                                onValueChange = { value -> onFormChange { it.copy(notesOrCvv = value) } },
                                placeholder = "•••"
                            )
                        }
                    }
                }

                VaultCategory.SECURE_NOTE, VaultCategory.API_KEY -> {
                    CustomInputField(
                        label = "Tags / Context",
                        value = form.tags,
                        onValueChange = { value -> onFormChange { it.copy(tags = value) } },
                        placeholder = "e.g. Work, Production, Personal"
                    )
                }
            }

            // Secret Value Input Field with Mask & Generator
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (form.category) {
                            VaultCategory.ACCOUNT -> "Password"
                            VaultCategory.BANK_CARD -> "PIN / Security Code"
                            VaultCategory.SECURE_NOTE -> "Secret Text"
                            VaultCategory.API_KEY -> "API Key / Token"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )

                    Row {
                        TextButton(onClick = { showGeneratorDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Generate",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate", color = CrimsonPrimary)
                        }

                        if (form.secretValue.isNotEmpty()) {
                            IconButton(onClick = {
                                SensitiveClipboard.copy(context, form.secretValue)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied. The clipboard is cleared in 30 seconds.")
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = form.secretValue,
                    onValueChange = { value -> onFormChange { it.copy(secretValue = value) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(EntryTags.SECRET),
                    visualTransformation = if (form.isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { onFormChange { it.copy(isSecretVisible = !it.isSecretVisible) } }) {
                            Icon(
                                imageVector = if (form.isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (form.isSecretVisible) "Hide secret" else "Show secret",
                                tint = TextSecondary
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Password Strength Bar
                if (form.secretValue.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val scoreColor = when {
                        strength.score >= 80 -> SecurityGreen
                        strength.score >= 50 -> SecurityYellow
                        else -> SecurityRed
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { strength.score / 100f },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = scoreColor,
                            trackColor = CardBorder
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "${strength.label} (${strength.entropyBits.toInt()} bits)",
                            style = MaterialTheme.typography.bodySmall,
                            color = scoreColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (form.category == VaultCategory.SECURE_NOTE) {
                CustomInputField(
                    label = "Secure Notes",
                    value = form.notesOrCvv,
                    onValueChange = { value -> onFormChange { it.copy(notesOrCvv = value) } },
                    placeholder = "Additional encrypted notes...",
                    singleLine = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Action Button
            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag(EntryTags.SAVE),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = CanvasBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Encrypted Record",
                    color = CanvasBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }

    // Password Generator Dialog
    if (showGeneratorDialog) {
        PasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onPasswordGenerated = { generated ->
                onFormChange { it.copy(secretValue = generated) }
                showGeneratorDialog = false
            }
        )
    }

    if (showDeleteConfirmation && onDeleteClick != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = CardBackground,
            title = { Text("Delete this entry?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "“${form.title}” will be removed from the vault. This cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecurityRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun CustomInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    testTag: String? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            placeholder = { Text(placeholder, color = TextMuted) },
            singleLine = singleLine,
            isError = isError,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                focusedBorderColor = CrimsonPrimary,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = SecurityRed,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun PasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onPasswordGenerated: (String) -> Unit
) {
    var length by rememberSaveable { mutableStateOf(16f) }
    var useUpper by rememberSaveable { mutableStateOf(true) }
    var useLower by rememberSaveable { mutableStateOf(true) }
    var useDigits by rememberSaveable { mutableStateOf(true) }
    var useSymbols by rememberSaveable { mutableStateOf(true) }

    val previewPassword by remember(length, useUpper, useLower, useDigits, useSymbols) {
        mutableStateOf(
            PasswordGenerator.generatePassword(
                PasswordGenerator.GeneratorConfig(
                    length = length.toInt(),
                    includeUppercase = useUpper,
                    includeLowercase = useLower,
                    includeDigits = useDigits,
                    includeSymbols = useSymbols
                )
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Text("Generator Settings", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Generated Password Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CanvasBlack)
                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = previewPassword.ifEmpty { "Pick at least one character set" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = if (previewPassword.isEmpty()) TextMuted else CrimsonPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Length: ${length.toInt()} characters",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Slider(
                    value = length,
                    onValueChange = { length = it },
                    valueRange = 8f..32f,
                    colors = SliderDefaults.colors(
                        thumbColor = CrimsonPrimary,
                        activeTrackColor = CrimsonPrimary,
                        inactiveTrackColor = CardBorder
                    )
                )

                GeneratorOption("Uppercase (A-Z)", useUpper) { useUpper = it }
                GeneratorOption("Lowercase (a-z)", useLower) { useLower = it }
                GeneratorOption("Digits (0-9)", useDigits) { useDigits = it }
                GeneratorOption("Symbols (!@#$)", useSymbols) { useSymbols = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPasswordGenerated(previewPassword) },
                enabled = previewPassword.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Text("Use Password", color = CanvasBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun GeneratorOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
        )
        Text(label, color = TextPrimary)
    }
}
