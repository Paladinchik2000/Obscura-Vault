package com.obscura.autofill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obscura.R
import com.obscura.data.local.VaultEntity

object AutofillPickerTags {
    const val SEARCH = "autofill_picker_search"
    const val LIST = "autofill_picker_list"
    const val LINK_CONFIRM = "autofill_picker_link_confirm"
    const val LINK_DECLINE = "autofill_picker_link_decline"
}

/**
 * Manual choice for a request nothing matched: the user picks an entry themselves.
 *
 * Picking one for an app or site that was not matched offers to remember the choice, which is
 * how an app gets linked to an entry in the first place.
 */
@Composable
fun AutofillPickerScreen(
    entries: List<VaultEntity>,
    /** Where the request came from, e.g. an app label or a host; null hides the link offer. */
    targetLabel: String?,
    onPick: (entry: VaultEntity, link: Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<VaultEntity?>(null) }

    val visible = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { entry ->
            entry.title.contains(query, ignoreCase = true) ||
                entry.usernameOrCardholder.contains(query, ignoreCase = true) ||
                entry.tags.contains(query, ignoreCase = true)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.autofill_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.autofill_picker_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AutofillPickerTags.SEARCH)
            )

            if (visible.isEmpty()) {
                Text(
                    stringResource(R.string.autofill_picker_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            LazyColumn(modifier = Modifier.testTag(AutofillPickerTags.LIST)) {
                items(visible, key = { it.id }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (targetLabel == null) onPick(entry, false) else pending = entry
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Column {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                            if (entry.usernameOrCardholder.isNotBlank()) {
                                Text(
                                    entry.usernameOrCardholder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    val entry = pending
    if (entry != null && targetLabel != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.autofill_link_title)) },
            text = { Text(stringResource(R.string.autofill_link_message, entry.title, targetLabel)) },
            confirmButton = {
                TextButton(
                    onClick = { pending = null; onPick(entry, true) },
                    modifier = Modifier.testTag(AutofillPickerTags.LINK_CONFIRM)
                ) { Text(stringResource(R.string.autofill_link_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { pending = null; onPick(entry, false) },
                    modifier = Modifier.testTag(AutofillPickerTags.LINK_DECLINE)
                ) { Text(stringResource(R.string.autofill_link_decline)) }
            }
        )
    }
}
