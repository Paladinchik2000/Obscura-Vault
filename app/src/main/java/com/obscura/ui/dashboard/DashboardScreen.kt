package com.obscura.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obscura.R
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import com.obscura.ui.backup.BackupReminderDialog
import com.obscura.ui.clipboard.SensitiveClipboard
import com.obscura.ui.common.UiText
import com.obscura.ui.common.labelRes
import com.obscura.ui.theme.CanvasBlack
import com.obscura.ui.theme.CardBackground
import com.obscura.ui.theme.CardBorder
import com.obscura.ui.theme.CrimsonPrimary
import com.obscura.ui.theme.SecurityGreen
import com.obscura.ui.theme.SecurityRed
import com.obscura.ui.theme.TextMuted
import com.obscura.ui.theme.TextPrimary
import com.obscura.ui.theme.TextSecondary
import kotlinx.coroutines.launch

object DashboardTags {
    const val SEARCH = "dashboard_search"
    const val ADD = "dashboard_add"
    const val LOCK = "dashboard_lock"
    const val BACKUP = "dashboard_backup"
    const val SETTINGS = "dashboard_settings"
}

@Composable
fun DashboardScreen(
    entries: List<VaultEntity>,
    totalEntriesCount: Int,
    weakPasswordsCount: Int,
    searchQuery: String,
    selectedCategory: VaultCategory?,
    toastMessage: UiText?,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (VaultCategory?) -> Unit,
    onItemClick: (VaultEntity) -> Unit,
    onAddNewClick: () -> Unit,
    onToggleFavorite: (VaultEntity) -> Unit,
    onLockVault: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearToast: () -> Unit,
    showBackupReminder: Boolean = false,
    onBackupReminderHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it.asString(context))
            onClearToast()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddNewClick,
                containerColor = CrimsonPrimary,
                contentColor = CanvasBlack,
                shape = CircleShape,
                modifier = Modifier.testTag(DashboardTags.ADD)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.dashboard_add_entry),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.5.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SecurityGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_status_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = SecurityGreen,
                            fontSize = 11.sp
                        )
                    }
                }

                Row {
                    // Settings Button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CardBackground)
                            .border(1.dp, CardBorder, CircleShape)
                            .testTag(DashboardTags.SETTINGS)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_open),
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Backup Button
                    IconButton(
                        onClick = onOpenBackup,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CardBackground)
                            .border(1.dp, CardBorder, CircleShape)
                            .testTag(DashboardTags.BACKUP)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = stringResource(R.string.dashboard_open_backup),
                            tint = CrimsonPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Lock Button
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CardBackground)
                            .border(1.dp, CardBorder, CircleShape)
                            .testTag(DashboardTags.LOCK)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.dashboard_lock_vault),
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Metrics Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricTile(
                    title = stringResource(R.string.dashboard_metric_total),
                    value = totalEntriesCount.toString(),
                    icon = Icons.Default.Shield,
                    iconColor = CrimsonPrimary
                )

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .width(1.dp)
                        .background(CardBorder)
                )

                MetricTile(
                    title = stringResource(R.string.dashboard_metric_weak),
                    value = weakPasswordsCount.toString(),
                    icon = Icons.Default.Warning,
                    iconColor = if (weakPasswordsCount > 0) SecurityRed else SecurityGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DashboardTags.SEARCH),
                placeholder = { Text(stringResource(R.string.dashboard_search_placeholder), color = TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.dashboard_search),
                        tint = CrimsonPrimary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.dashboard_search_clear),
                                tint = TextMuted
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    CategoryChip(
                        title = stringResource(R.string.dashboard_filter_all),
                        isSelected = selectedCategory == null,
                        onClick = { onCategorySelected(null) }
                    )
                }

                items(VaultCategory.entries.toTypedArray()) { category ->
                    CategoryChip(
                        title = stringResource(category.labelRes()),
                        isSelected = selectedCategory == category,
                        onClick = { onCategorySelected(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vault Items List
            if (entries.isEmpty()) {
                EmptyVaultState(
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    onAddNewClick = onAddNewClick,
                    onClearSearch = { onSearchQueryChanged("") },
                    onShowAll = { onCategorySelected(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(entries, key = { it.id }) { item ->
                        VaultItemCard(
                            item = item,
                            onItemClick = { onItemClick(item) },
                            onCopySecret = { secret ->
                                SensitiveClipboard.copy(context, secret)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.clipboard_copied_notice,
                                            (SensitiveClipboard.CLEAR_AFTER_MS / 1000).toInt()
                                        )
                                    )
                                }
                            },
                            onToggleFavorite = { onToggleFavorite(item) }
                        )
                    }
                }
            }
        }
    }

    if (showBackupReminder) {
        BackupReminderDialog(
            onMakeBackup = { onBackupReminderHandled(); onOpenBackup() },
            onDismiss = onBackupReminderHandled
        )
    }
}

/** What to show instead of the list: an empty vault, an empty category, or a search with no hits. */
@Composable
private fun EmptyVaultState(
    searchQuery: String,
    selectedCategory: VaultCategory?,
    onAddNewClick: () -> Unit,
    onClearSearch: () -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when {
        searchQuery.isNotBlank() -> stringResource(R.string.dashboard_empty_search_title, searchQuery)
        selectedCategory != null ->
            stringResource(R.string.dashboard_empty_category_title, stringResource(selectedCategory.labelRes()))
        else -> stringResource(R.string.dashboard_empty_vault_title)
    }
    val body = when {
        searchQuery.isNotBlank() -> stringResource(R.string.dashboard_empty_search_body)
        selectedCategory != null -> stringResource(R.string.dashboard_empty_category_body)
        else -> stringResource(R.string.dashboard_empty_vault_body)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (searchQuery.isNotBlank()) {
                TextButton(onClick = onClearSearch) {
                    Text(stringResource(R.string.dashboard_empty_search_action), color = CrimsonPrimary)
                }
            } else {
                Button(
                    onClick = onAddNewClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary, contentColor = CanvasBlack)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (selectedCategory == null) R.string.dashboard_empty_vault_action
                            else R.string.dashboard_empty_category_add
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (selectedCategory != null) {
                    TextButton(onClick = onShowAll) {
                        Text(stringResource(R.string.dashboard_empty_category_show_all), color = CrimsonPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun CategoryChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) CrimsonPrimary else CardBackground)
            .border(
                1.dp,
                if (isSelected) CrimsonPrimary else CardBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) CanvasBlack else TextPrimary
        )
    }
}
