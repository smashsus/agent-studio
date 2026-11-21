package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.MemoryEntryEntity
import com.example.memory.MemoryLayer
import com.example.ui.MainViewModel
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissDivider
import com.example.ui.components.SwissEmptyState
import com.example.ui.components.SwissOutlinedButton
import com.example.ui.components.SwissPrimaryButton
import com.example.ui.components.SwissSearchField
import com.example.ui.components.SwissSectionHeader
import com.example.ui.components.SwissSurfaceCard
import com.example.ui.components.SwissTopBar
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val taskMemories by viewModel.taskMemories.collectAsStateWithLifecycle()
    val longTermMemories by viewModel.longTermMemories.collectAsStateWithLifecycle()

    var editingMemory by remember { mutableStateOf<MemoryEntryEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.content.contains(searchQuery, ignoreCase = true) || it.role.contains(searchQuery, ignoreCase = true) }
    }
    val filteredTaskMem = remember(taskMemories, searchQuery) {
        if (searchQuery.isBlank()) taskMemories
        else taskMemories.filter {
            it.keyName.contains(searchQuery, true) || it.content.contains(searchQuery, true) || it.category.contains(searchQuery, true)
        }
    }
    val filteredLongMem = remember(longTermMemories, searchQuery) {
        if (searchQuery.isBlank()) longTermMemories
        else longTermMemories.filter {
            it.keyName.contains(searchQuery, true) || it.content.contains(searchQuery, true) || it.category.contains(searchQuery, true)
        }
    }

    val tabs = listOf(
        "Short-Term (${messages.size})",
        "Task (${taskMemories.size + subtasks.size})",
        "Long-Term (${longTermMemories.size})"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        SwissTopBar(
            title = "Memory",
            subtitle = "${messages.size + taskMemories.size + longTermMemories.size} stored items",
            actions = {
                if (selectedTab != 0) {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .size(SwissGeometry.minTouchTarget)
                            .testTag("add_memory_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Memory Entry", tint = tokens.primaryText)
                    }
                }
            }
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = tokens.background,
            contentColor = tokens.primaryText,
            indicator = { positions ->
                if (selectedTab < positions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                        color = tokens.primaryAccent
                    )
                }
            }
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == idx) tokens.primaryText else tokens.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("memory_tab_$idx")
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(SwissGeometry.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SwissSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search memory entries..."
                )
            }

            when (selectedTab) {
                0 -> {
                    if (filteredMessages.isEmpty()) {
                        item {
                            SwissEmptyState(
                                icon = Icons.Default.Memory,
                                title = "Short-Term Memory Empty",
                                description = "Messages from the active thread appear here as rolling working context."
                            )
                        }
                    } else {
                        items(filteredMessages, key = { it.id }) { msg ->
                            SwissSurfaceCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SwissBadge(
                                        text = "${msg.role}${msg.toolName?.let { " · $it" } ?: ""}",
                                        textColor = tokens.primaryAccent
                                    )
                                    Text(
                                        text = formatMemoryTime(msg.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.mutedText
                                    )
                                }
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.primaryText,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (subtasks.isNotEmpty() && searchQuery.isBlank()) {
                        item {
                            SwissSectionHeader(title = "Task Subtasks", trailingText = "${subtasks.size} steps")
                        }
                        items(subtasks, key = { "sub_${it.id}" }) { st ->
                            SwissSurfaceCard(useSurface2 = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${st.orderIndex + 1}. ${st.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = tokens.primaryText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SwissBadge(
                                        text = st.status,
                                        textColor = if (st.status == "COMPLETED") tokens.success else tokens.primaryAccent
                                    )
                                }
                                Text(st.description, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                            }
                        }
                    }

                    if (filteredTaskMem.isEmpty() && subtasks.isEmpty()) {
                        item {
                            SwissEmptyState(
                                icon = Icons.Default.Memory,
                                title = "No Task Memory Entries",
                                description = "Intermediate task summaries and working notes are stored here."
                            )
                        }
                    } else if (filteredTaskMem.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Task Memory Notes", style = MaterialTheme.typography.titleMedium, color = tokens.primaryText)
                                TextButton(onClick = { viewModel.clearMemoryLayer(MemoryLayer.TASK) }) {
                                    Text("Clear Layer", color = tokens.error)
                                }
                            }
                        }
                        items(filteredTaskMem, key = { it.id }) { entry ->
                            MemoryEntryCard(
                                entry = entry,
                                onEdit = { editingMemory = entry },
                                onToggleApproved = { viewModel.updateMemory(entry.copy(userApproved = !entry.userApproved)) },
                                onDelete = { viewModel.deleteMemory(entry.id) }
                            )
                        }
                    }
                }
                2 -> {
                    if (filteredLongMem.isEmpty()) {
                        item {
                            SwissEmptyState(
                                icon = Icons.Default.Memory,
                                title = "No Long-Term Memory Entries",
                                description = "Approved user preferences, project facts, and persistent context appear here."
                            )
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Approved Facts & Preferences", style = MaterialTheme.typography.titleMedium, color = tokens.primaryText)
                                TextButton(onClick = { viewModel.clearMemoryLayer(MemoryLayer.LONG_TERM) }) {
                                    Text("Clear All", color = tokens.error)
                                }
                            }
                        }
                        items(filteredLongMem, key = { it.id }) { entry ->
                            MemoryEntryCard(
                                entry = entry,
                                onEdit = { editingMemory = entry },
                                onToggleApproved = { viewModel.updateMemory(entry.copy(userApproved = !entry.userApproved)) },
                                onDelete = { viewModel.deleteMemory(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val layer = if (selectedTab == 2) MemoryLayer.LONG_TERM else MemoryLayer.TASK
        MemoryEditDialog(
            title = if (layer == MemoryLayer.LONG_TERM) "Add Long-Term Memory" else "Add Task Memory",
            initialKey = "",
            initialContent = "",
            initialCategory = "Preference",
            onDismiss = { showAddDialog = false },
            onSave = { k, c, cat ->
                viewModel.addMemory(layer, k, c, cat)
                showAddDialog = false
            }
        )
    }

    editingMemory?.let { current ->
        MemoryEditDialog(
            title = "Edit Memory Entry",
            initialKey = current.keyName,
            initialContent = current.content,
            initialCategory = current.category,
            onDismiss = { editingMemory = null },
            onSave = { k, c, cat ->
                viewModel.updateMemory(current.copy(keyName = k, content = c, category = cat))
                editingMemory = null
            }
        )
    }
}

@Composable
private fun MemoryEntryCard(
    entry: MemoryEntryEntity,
    onEdit: () -> Unit,
    onToggleApproved: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    SwissSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.keyName,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.primaryText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SwissBadge(text = entry.category, textColor = tokens.secondaryAccent)
                    Text(
                        text = formatMemoryTime(entry.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.mutedText
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(SwissGeometry.minTouchTarget)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Memory", tint = tokens.secondaryText, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(SwissGeometry.minTouchTarget)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Memory", tint = tokens.secondaryText, modifier = Modifier.size(18.dp))
                }
            }
        }

        Text(
            text = entry.content,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.primaryText
        )

        SwissDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (entry.userApproved) "Active in agent context" else "Excluded from context",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.userApproved) tokens.success else tokens.mutedText
            )
            Switch(checked = entry.userApproved, onCheckedChange = { onToggleApproved() })
        }
    }
}

@Composable
private fun MemoryEditDialog(
    title: String,
    initialKey: String,
    initialContent: String,
    initialCategory: String,
    onDismiss: () -> Unit,
    onSave: (key: String, content: String, category: String) -> Unit
) {
    val tokens = LocalSwissTokens.current
    var key by remember { mutableStateOf(initialKey) }
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusDialog),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = tokens.primaryText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Title / Key") }, singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, minLines = 3)
            }
        },
        confirmButton = {
            SwissPrimaryButton(
                text = "Save",
                onClick = { onSave(key, content, category) },
                enabled = key.isNotBlank() && content.isNotBlank()
            )
        },
        dismissButton = {
            SwissOutlinedButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

private fun formatMemoryTime(ts: Long): String {
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts))
}
