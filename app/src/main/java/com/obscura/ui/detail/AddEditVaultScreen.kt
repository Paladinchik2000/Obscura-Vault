package com.obscura.ui.detail

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import com.obscura.security.PasswordGenerator
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

@Composable
fun AddEditVaultScreen(
    initialItem: VaultEntity?,
    onSaveClick: (VaultEntity) -> Unit,
    onDeleteClick: ((String) -> Unit)?,
    onBackClick: () -> Unit
) {
    var category by remember { mutableStateOf(initialItem?.getCategoryEnum() ?: VaultCategory.ACCOUNT) }
    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var usernameOrCardholder by remember { mutableStateOf(initialItem?.usernameOrCardholder ?: "") }
    var secretValue by remember { mutableStateOf(initialItem?.secretValue ?: "") }
    var urlOrCardNumber by remember { mutableStateOf(initialItem?.urlOrCardNumber ?: "") }
    var notesOrCvv by remember { mutableStateOf(initialItem?.notesOrCvv ?: "") }
    var tags by remember { mutableStateOf(initialItem?.tags ?: "") }

    var isSecretVisible by remember { mutableStateOf(false) }
    var showGeneratorDialog by remember { mutableStateOf(false) }

    val strength = remember(secretValue) { PasswordGenerator.evaluateStrength(secretValue) }
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
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
                        text = if (initialItem == null) "New Secret Record" else "Edit Secret Record",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (initialItem != null && onDeleteClick != null) {
                    IconButton(onClick = { onDeleteClick(initialItem.id) }) {
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
                    val isSelected = category == cat
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
                            .clickable { category = cat }
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
                value = title,
                onValueChange = { title = it },
                placeholder = "e.g. GitHub, Chase Bank, Server Key"
            )

            when (category) {
                VaultCategory.ACCOUNT -> {
                    CustomInputField(
                        label = "Website / App URL",
                        value = urlOrCardNumber,
                        onValueChange = { urlOrCardNumber = it },
                        placeholder = "e.g. https://github.com"
                    )

                    CustomInputField(
                        label = "Username / Email",
                        value = usernameOrCardholder,
                        onValueChange = { usernameOrCardholder = it },
                        placeholder = "user@example.com"
                    )
                }

                VaultCategory.BANK_CARD -> {
                    CustomInputField(
                        label = "Cardholder Name",
                        value = usernameOrCardholder,
                        onValueChange = { usernameOrCardholder = it },
                        placeholder = "JOHN DOE"
                    )

                    CustomInputField(
                        label = "Card Number",
                        value = urlOrCardNumber,
                        onValueChange = { urlOrCardNumber = it },
                        placeholder = "4532 •••• •••• 8892"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CustomInputField(
                                label = "Expiry Date",
                                value = tags, // Reusing tags for expiry
                                onValueChange = { tags = it },
                                placeholder = "MM/YY"
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CustomInputField(
                                label = "CVV / CVC",
                                value = notesOrCvv,
                                onValueChange = { notesOrCvv = it },
                                placeholder = "•••"
                            )
                        }
                    }
                }

                VaultCategory.SECURE_NOTE, VaultCategory.API_KEY -> {
                    CustomInputField(
                        label = "Tags / Context",
                        value = tags,
                        onValueChange = { tags = it },
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
                        text = when (category) {
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

                        if (secretValue.isNotEmpty()) {
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(secretValue)) }) {
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
                    value = secretValue,
                    onValueChange = { secretValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                            Icon(
                                imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Secret Visibility",
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
                if (secretValue.isNotEmpty()) {
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

            if (category == VaultCategory.SECURE_NOTE) {
                CustomInputField(
                    label = "Secure Notes",
                    value = notesOrCvv,
                    onValueChange = { notesOrCvv = it },
                    placeholder = "Additional encrypted notes...",
                    singleLine = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Action Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val entity = VaultEntity(
                            id = initialItem?.id ?: "",
                            title = title,
                            category = category.id,
                            usernameOrCardholder = usernameOrCardholder,
                            secretValue = secretValue,
                            urlOrCardNumber = urlOrCardNumber,
                            notesOrCvv = notesOrCvv,
                            tags = tags,
                            isFavorite = initialItem?.isFavorite ?: false
                        )
                        onSaveClick(entity)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
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
                secretValue = generated
                showGeneratorDialog = false
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
    singleLine: Boolean = true
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
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextMuted) },
            singleLine = singleLine,
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
    }
}

@Composable
fun PasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onPasswordGenerated: (String) -> Unit
) {
    var length by remember { mutableFloatStateOf(16f) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }

    var previewPassword by remember(length, useUpper, useLower, useDigits, useSymbols) {
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
                        text = previewPassword,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = CrimsonPrimary,
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useUpper,
                        onCheckedChange = { useUpper = it },
                        colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                    )
                    Text("Uppercase (A-Z)", color = TextPrimary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useDigits,
                        onCheckedChange = { useDigits = it },
                        colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                    )
                    Text("Digits (0-9)", color = TextPrimary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useSymbols,
                        onCheckedChange = { useSymbols = it },
                        colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                    )
                    Text("Symbols (!@#$)", color = TextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPasswordGenerated(previewPassword) },
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
