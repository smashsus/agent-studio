package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.ui.theme.LocalAgentColors

@Composable
fun MemoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val taskMemories by viewModel.taskMemories.collectAsStateWithLifecycle()
    val longTermMemories by viewModel.longTermMemories.collectAsStateWithLifecycle()

    var editingMemory by remember { mutableStateOf<MemoryEntryEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val tabs = listOf(
        "Short-Term (${messages.size})",
        "Task (${taskMemories.size + subtasks.size})",
        "Long-Term (${longTermMemories.size})"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (selectedTab != 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_memory_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory Entry")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("memory_tab_$idx")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        if (messages.isEmpty()) {
                            item {
                                MemoryEmptyCard(
                                    title = "Short-Term Conversation Memory",
                                    body = "Messages from the active mission appear here as the agent's rolling working context."
                                )
                            }
                        } else {
                            items(messages, key = { it.id }) { msg ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "${msg.role}${msg.toolName?.let { " • $it" } ?: ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(msg.content, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (subtasks.isNotEmpty()) {
                            item {
                                Text("Active Task Plan Subtasks", style = MaterialTheme.typography.titleSmall)
                            }
                            items(subtasks, key = { "sub_${it.id}" }) { st ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${st.orderIndex + 1}. ${st.title}", style = MaterialTheme.typography.titleSmall)
                                            Text(st.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            st.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (st.status == "COMPLETED") LocalAgentColors.current.success else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                        if (taskMemories.isEmpty() && subtasks.isEmpty()) {
                            item {
                                MemoryEmptyCard(
                                    title = "Task Memory",
                                    body = "Task context, intermediate research summaries, and active plan state are stored here."
                                )
                            }
                        } else {
                            if (taskMemories.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Task Memory Notes", style = MaterialTheme.typography.titleSmall)
                                        TextButton(onClick = { viewModel.clearMemoryLayer(MemoryLayer.TASK) }) {
                                            Text("Clear Task Memory")
                                        }
                                    }
                                }
                                items(taskMemories, key = { it.id }) { entry ->
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
                    2 -> {
                        if (longTermMemories.isEmpty()) {
                            item {
                                MemoryEmptyCard(
                                    title = "Long-Term User-Approved Memory",
                                    body = "Persistent preferences, domain facts, and project context approved for future missions."
                                )
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Stored Preferences & Facts", style = MaterialTheme.typography.titleSmall)
                                    TextButton(onClick = { viewModel.clearMemoryLayer(MemoryLayer.LONG_TERM) }) {
                                        Text("Clear All")
                                    }
                                }
                            }
                            items(longTermMemories, key = { it.id }) { entry ->
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
    }

    if (showAddDialog) {
        val layer = if (selectedTab == 2) MemoryLayer.LONG_TERM else MemoryLayer.TASK
        MemoryEditDialog(
            title = if (layer == MemoryLayer.LONG_TERM) "Add Long-Term Memory" else "Add Task Memory",
            initialKey = "",
            initialContent = "",
            initialCategory = "User Preference",
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
private fun MemoryEmptyCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemoryEntryCard(
    entry: MemoryEntryEntity,
    onEdit: () -> Unit,
    onToggleApproved: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text(entry.keyName, style = MaterialTheme.typography.titleSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Memory", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Memory", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(entry.content, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (entry.userApproved) LocalAgentColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (entry.userApproved) "Approved for Agent Context" else "Paused / Excluded",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Switch(checked = entry.userApproved, onCheckedChange = { onToggleApproved() })
            }
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
    var key by remember { mutableStateOf(initialKey) }
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Key / Topic") }, singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(key, content, category) },
                enabled = key.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
