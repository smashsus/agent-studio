package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.PushPin
import com.adamglin.phosphoricons.regular.Broom
import com.adamglin.phosphoricons.regular.ChatsCircle
import com.adamglin.phosphoricons.regular.DotsThreeVertical
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.PushPin
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.Trash
import com.example.database.ConversationSummary
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistorySheet(
    summaries: List<ConversationSummary>,
    activeConversationId: Long?,
    onDismiss: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (Long, String) -> Unit,
    onTogglePinConversation: (Long, Boolean) -> Unit,
    onClearConversation: (Long) -> Unit,
    onExportConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit
) {
    val tokens = LocalSwissTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ConversationSummary?>(null) }

    val filtered = remember(summaries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) summaries
        else summaries.filter {
            it.title.contains(q, ignoreCase = true) ||
                (it.lastMessagePreview?.contains(q, ignoreCase = true) == true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surface1,
        contentColor = tokens.primaryText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SwissGeometry.screenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Conversations",
                        style = MaterialTheme.typography.headlineSmall,
                        color = tokens.primaryText
                    )
                    Text(
                        text = "${summaries.size} saved thread(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText
                    )
                }
                SwissPrimaryButton(
                    text = "New Thread",
                    icon = PhosphorIcons.Regular.Plus,
                    onClick = {
                        onNewConversation()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("history_new_thread_button")
                )
            }

            SwissSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search threads or messages..."
            )

            if (filtered.isEmpty()) {
                SwissEmptyState(
                    icon = PhosphorIcons.Regular.ChatsCircle,
                    title = "No matching threads",
                    description = "Try another keyword or start a new agent thread."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { summary ->
                        ConversationHistoryCard(
                            summary = summary,
                            isActive = summary.id == activeConversationId,
                            canDelete = summaries.size > 1,
                            onSelect = {
                                onSelectConversation(summary.id)
                                onDismiss()
                            },
                            onTogglePin = { onTogglePinConversation(summary.id, summary.isPinned) },
                            onRequestRename = {
                                renameTarget = summary
                                renameInput = summary.title
                            },
                            onClear = { onClearConversation(summary.id) },
                            onExport = { onExportConversation(summary.id) },
                            onRequestDelete = { deleteTarget = summary }
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = tokens.surface1,
            shape = RoundedCornerShape(SwissGeometry.radiusDialog),
            title = {
                Text("Rename Thread", style = MaterialTheme.typography.titleLarge, color = tokens.primaryText)
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Thread Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                SwissPrimaryButton(
                    text = "Save",
                    enabled = renameInput.isNotBlank(),
                    onClick = {
                        onRenameConversation(target.id, renameInput)
                        renameTarget = null
                    }
                )
            },
            dismissButton = {
                SwissOutlinedButton(text = "Cancel", onClick = { renameTarget = null })
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = tokens.surface1,
            shape = RoundedCornerShape(SwissGeometry.radiusDialog),
            title = {
                Text("Delete Thread?", style = MaterialTheme.typography.titleLarge, color = tokens.primaryText)
            },
            text = {
                Text(
                    "Remove \"${target.title}\" and all its messages and execution logs?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText
                )
            },
            confirmButton = {
                SwissPrimaryButton(
                    text = "Delete",
                    onClick = {
                        onDeleteConversation(target.id)
                        deleteTarget = null
                    }
                )
            },
            dismissButton = {
                SwissOutlinedButton(text = "Cancel", onClick = { deleteTarget = null })
            }
        )
    }
}

@Composable
private fun ConversationHistoryCard(
    summary: ConversationSummary,
    isActive: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onRequestRename: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = if (isActive) tokens.primaryAccent.copy(alpha = 0.09f) else tokens.surface2.copy(alpha = 0.65f),
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(
            width = SwissGeometry.borderWidth,
            color = if (isActive) tokens.primaryAccent.copy(alpha = 0.65f) else tokens.border.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SwissGeometry.radiusCard))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (summary.isPinned) {
                        Icon(
                            imageVector = PhosphorIcons.Fill.PushPin,
                            contentDescription = "Pinned",
                            tint = tokens.primaryAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        SwissBadge(text = "Active", textColor = tokens.primaryAccent)
                    }
                }

                val preview = summary.lastMessagePreview
                    ?.replace("\n", " ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "Empty workspace thread"

                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatRelativeTime(summary.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.mutedText
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.mutedText
                    )
                    Text(
                        text = "${summary.messageCount} msg${if (summary.messageCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.mutedText
                    )
                }
            }

            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (summary.isPinned) PhosphorIcons.Fill.PushPin else PhosphorIcons.Regular.PushPin,
                    contentDescription = if (summary.isPinned) "Unpin" else "Pin",
                    tint = if (summary.isPinned) tokens.primaryAccent else tokens.mutedText,
                    modifier = Modifier.size(17.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.DotsThreeVertical,
                        contentDescription = "Thread Options",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(PhosphorIcons.Regular.PencilSimple, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRequestRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (summary.isPinned) "Unpin" else "Pin to Top") },
                        leadingIcon = { Icon(PhosphorIcons.Regular.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Markdown") },
                        leadingIcon = { Icon(PhosphorIcons.Regular.ShareNetwork, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Messages") },
                        leadingIcon = { Icon(PhosphorIcons.Regular.Broom, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onClear()
                        }
                    )
                    if (canDelete) {
                        SwissDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Thread", color = tokens.error) },
                            leadingIcon = {
                                Icon(PhosphorIcons.Regular.Trash, contentDescription = null, tint = tokens.error, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onRequestDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

fun formatRelativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}
