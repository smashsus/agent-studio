package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.database.ExecutionLogEntity
import com.example.database.MessageEntity
import com.example.database.SubtaskEntity
import com.example.execution.AgentPhase
import com.example.execution.ExecutionUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalAgentColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceScreen(
    viewModel: MainViewModel,
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val executionLogs by viewModel.executionLogs.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val preferredMode by viewModel.preferredMode.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var activityExpanded by rememberSaveable { mutableStateOf(true) }
    var showConversationsMenu by remember { mutableStateOf(false) }

    val attachLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.attachUri(uri)
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    val launchVoiceRecognizer = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your DeepSeek Agent task")
        }
        runCatching { speechLauncher.launch(intent) }.onFailure {
            viewModel.postMessage("Speech recognition service unavailable on this device.")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchVoiceRecognizer()
        else viewModel.postMessage("Microphone permission is required for voice input.")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Top Agent Status & Session Bar
        WorkspaceTopBar(
            activeTitle = conversations.firstOrNull { it.id == activeId }?.title ?: "Agent Mission",
            conversationsCount = conversations.size,
            authState = authState,
            preferredMode = preferredMode,
            executionState = executionState,
            showMenu = showConversationsMenu,
            onToggleMenu = { showConversationsMenu = !showConversationsMenu },
            onDismissMenu = { showConversationsMenu = false },
            conversations = conversations,
            activeId = activeId,
            onSelectConversation = {
                viewModel.selectConversation(it)
                showConversationsMenu = false
            },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onNewConversation = {
                viewModel.createNewConversation()
                showConversationsMenu = false
            },
            onNavigateToAuth = onNavigateToAuth,
            onStopExecution = { viewModel.stopExecution() }
        )

        // Expandable Agent Activity Panel
        AgentActivityPanel(
            executionState = executionState,
            subtasks = subtasks,
            logs = executionLogs,
            expanded = activityExpanded,
            onToggleExpanded = { activityExpanded = !activityExpanded }
        )

        // Human Approval Checkpoint Card
        executionState.pendingApproval?.let { pending ->
            ApprovalCheckpointCard(
                toolName = pending.toolCall.toolName,
                argumentsJson = pending.toolCall.argumentsJson,
                reason = pending.reason,
                onApprove = { viewModel.resolveApproval(true) },
                onDecline = { viewModel.resolveApproval(false) }
            )
        }

        // Priority 1 Web-Bridge Orchestration Card
        executionState.pendingWebBridge?.let { bridge ->
            WebBridgeCheckpointCard(
                preparedPrompt = bridge.preparedPrompt,
                explanation = bridge.explanation,
                hasSuggestedLocalTools = bridge.hasSuggestedLocalTools,
                onCopyAndOpenAuth = {
                    viewModel.copyToClipboard("DeepSeek Web-Bridge Prompt", bridge.preparedPrompt)
                    onNavigateToAuth()
                },
                onRunLocalTools = { viewModel.runWebBridgeLocalTools() },
                onSubmitWebReply = { reply -> viewModel.submitWebBridgeReply(reply) }
            )
        }

        // Conversation Timeline
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    WorkspaceEmptyState(
                        preferredMode = preferredMode,
                        onSelectQuickMission = { prompt -> inputText = prompt }
                    )
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    MessageCard(
                        message = msg,
                        onCopy = { viewModel.copyToClipboard("Agent Output", msg.content) },
                        onShare = { viewModel.shareText(msg.content) },
                        onPreviewArtifact = { path -> viewModel.openHtmlPreviewFromPath(path) }
                    )
                }
            }
        }

        // Pending Attachment Pill
        AnimatedVisibility(visible = pendingAttachment != null) {
            pendingAttachment?.let { att ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Attached: ${att.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearAttachment() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Attachment", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Bottom Composer
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        attachLauncher.launch(
                            arrayOf("application/pdf", "text/plain", "text/markdown", "application/json", "text/*")
                        )
                    },
                    modifier = Modifier.testTag("attach_document_button")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach Document")
                }

                IconButton(
                    onClick = {
                        if (viewModel.hasRecordAudioPermission()) launchVoiceRecognizer()
                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.testTag("voice_input_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice Input")
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Assign autonomous task or question...") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prompt_input_field")
                )

                if (executionState.isRunning) {
                    IconButton(
                        onClick = { viewModel.stopExecution() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(LocalAgentColors.current.dangerContainer)
                            .testTag("stop_execution_button")
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop Execution",
                            tint = LocalAgentColors.current.danger
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() || pendingAttachment != null) {
                                    val text = inputText
                                    inputText = ""
                                    viewModel.sendPrompt(text)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("send_prompt_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Run Agent",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val text = inputText
                                    inputText = ""
                                    viewModel.scheduleBackgroundMission(text)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("schedule_workmanager_button")
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Run in Background (WorkManager)",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTopBar(
    activeTitle: String,
    conversationsCount: Int,
    authState: AuthState,
    preferredMode: ProviderMode,
    executionState: ExecutionUiState,
    showMenu: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    conversations: List<com.example.database.ConversationEntity>,
    activeId: Long?,
    onSelectConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onStopExecution: () -> Unit
) {
    val agentColors = LocalAgentColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onToggleMenu)
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("conversation_selector_button")
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = activeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "($conversationsCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.Default.ExpandMore, contentDescription = "Switch Conversation")
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(
                            text = { Text("New Agent Mission", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = onNewConversation
                        )
                        HorizontalDivider()
                        conversations.forEach { conv ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        conv.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (conv.id == activeId) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = {
                                    if (conversations.size > 1) {
                                        IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                onClick = { onSelectConversation(conv.id) }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier.testTag("new_mission_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Mission")
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auth status chip
                val (authLabel, authColor) = when (authState) {
                    is AuthState.Authenticated -> {
                        val shortMode = if (authState.mode == ProviderMode.WEB_SESSION) "DeepSeek Web" else "DeepSeek API"
                        "$shortMode • Active" to agentColors.success
                    }
                    is AuthState.Authenticating -> "Signing in..." to agentColors.telemetry
                    is AuthState.SessionExpired -> "Session Expired" to agentColors.warning
                    is AuthState.AuthenticationFailed -> "Auth Failed" to agentColors.danger
                    AuthState.NotAuthenticated -> "${if (preferredMode == ProviderMode.WEB_SESSION) "Web" else "API"} • Connect" to agentColors.warning
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, authColor.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .clickable(onClick = onNavigateToAuth)
                        .testTag("auth_status_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(authColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(authLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Live Agent Phase Indicator
                val phaseColor = when (executionState.phase) {
                    AgentPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    AgentPhase.COMPLETED -> agentColors.success
                    AgentPhase.ERROR, AgentPhase.CANCELLED -> agentColors.danger
                    AgentPhase.AWAITING_APPROVAL, AgentPhase.AWAITING_WEB_BRIDGE -> agentColors.warning
                    else -> agentColors.telemetry
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (executionState.isRunning && executionState.phase != AgentPhase.AWAITING_APPROVAL && executionState.phase != AgentPhase.AWAITING_WEB_BRIDGE) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = phaseColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = buildString {
                                append(executionState.phase.label)
                                if (executionState.activeToolName != null) {
                                    append(" • ${executionState.activeToolName}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = phaseColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (executionState.isRunning) {
                    TextButton(
                        onClick = onStopExecution,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Stop", color = agentColors.danger, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentActivityPanel(
    executionState: ExecutionUiState,
    subtasks: List<SubtaskEntity>,
    logs: List<ExecutionLogEntity>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    if (subtasks.isEmpty() && logs.isEmpty() && !executionState.isRunning) return

    val agentColors = LocalAgentColors.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .testTag("agent_activity_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = agentColors.telemetry,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Agent Activity • ${executionState.statusDetail}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Activity Panel"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (subtasks.isNotEmpty()) {
                        subtasks.forEach { st ->
                            val iconTint = when (st.status) {
                                "COMPLETED" -> agentColors.success
                                "RUNNING" -> agentColors.telemetry
                                "FAILED", "CANCELLED" -> agentColors.danger
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = st.status,
                                    tint = iconTint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${st.orderIndex + 1}. ${st.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (st.status == "PENDING") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (logs.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "Execution Trace (${logs.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        logs.take(4).forEach { log ->
                            val statusColor = when (log.status) {
                                "SUCCESS" -> agentColors.success
                                "RUNNING" -> agentColors.telemetry
                                else -> agentColors.danger
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${log.toolName} (${log.durationMs}ms)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = log.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCheckpointCard(
    toolName: String,
    argumentsJson: String,
    reason: String,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val agentColors = LocalAgentColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = agentColors.warningContainer),
        border = BorderStroke(1.dp, agentColors.warning),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = agentColors.warning)
                Spacer(Modifier.width(8.dp))
                Text("Human Approval Checkpoint: $toolName", style = MaterialTheme.typography.titleSmall)
            }
            Text(reason, style = MaterialTheme.typography.bodySmall)
            Text(
                text = argumentsJson.take(280),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, modifier = Modifier.testTag("approve_tool_button")) {
                    Text("Approve")
                }
                OutlinedButton(onClick = onDecline, modifier = Modifier.testTag("decline_tool_button")) {
                    Text("Decline")
                }
            }
        }
    }
}

@Composable
private fun WebBridgeCheckpointCard(
    preparedPrompt: String,
    explanation: String,
    hasSuggestedLocalTools: Boolean,
    onCopyAndOpenAuth: () -> Unit,
    onRunLocalTools: () -> Unit,
    onSubmitWebReply: (String) -> Unit
) {
    var pastedReply by rememberSaveable { mutableStateOf("") }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "DeepSeek Web Session Bridge",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(explanation, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = onCopyAndOpenAuth,
                    modifier = Modifier.weight(1f).testTag("bridge_open_deepseek_button")
                ) {
                    Text("Send via DeepSeek Web", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (hasSuggestedLocalTools) {
                    Button(
                        onClick = onRunLocalTools,
                        modifier = Modifier.weight(1f).testTag("bridge_run_local_tools_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Run Local Tools", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            OutlinedTextField(
                value = pastedReply,
                onValueChange = { pastedReply = it },
                label = { Text("Paste DeepSeek Web response (text or tool JSON)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("bridge_reply_input")
            )
            Button(
                onClick = {
                    if (pastedReply.isNotBlank()) {
                        val reply = pastedReply
                        pastedReply = ""
                        onSubmitWebReply(reply)
                    }
                },
                enabled = pastedReply.isNotBlank(),
                modifier = Modifier.align(Alignment.End).testTag("bridge_submit_reply_button")
            ) {
                Text("Continue Agent Loop")
            }
        }
    }
}

@Composable
private fun WorkspaceEmptyState(
    preferredMode: ProviderMode,
    onSelectQuickMission: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DeepSeek Native Agent Harness",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = if (preferredMode == ProviderMode.WEB_SESSION) {
                        "Priority 1: Official DeepSeek Web Session active mode. Connect your DeepSeek account in the Account tab or run autonomous on-device research, PDF, JS sandbox, and Android tools below."
                    } else {
                        "Priority 2: Automated multi-step tool loop connected to your configured DeepSeek / compatible API endpoint."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Quick Missions",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        val quickPrompts = listOf(
            "Research the latest developments in quantum computing and prepare a report",
            "Run JavaScript to compute the first 12 Fibonacci numbers:\n```js\nlet a=0,b=1,r=[]; for(let i=0;i<12;i++){r.push(a);[a,b]=[b,a+b];} JSON.stringify(r);\n```",
            "List workspace files and summarize what is stored",
            "Create a task notification reminder in 10 seconds to review my research notes"
        )
        quickPrompts.forEach { sample ->
            AssistChip(
                onClick = { onSelectQuickMission(sample) },
                label = {
                    Text(
                        sample.replace("\n", " "),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MessageCard(
    message: MessageEntity,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onPreviewArtifact: (String) -> Unit
) {
    val isUser = message.role == "USER"
    val isTool = message.role == "TOOL"
    val isSystem = message.role == "SYSTEM"

    val containerColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isTool -> MaterialTheme.colorScheme.surfaceContainerHigh
        isSystem -> LocalAgentColors.current.warningContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            1.dp,
            if (isTool) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val roleLabel = when (message.role) {
                    "USER" -> "YOU"
                    "TOOL" -> "TOOL • ${message.toolName ?: "exec"}"
                    "SYSTEM" -> "SYSTEM"
                    else -> "DEEPSEEK AGENT"
                }
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isTool) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!message.attachmentName.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Attachment: ${message.attachmentName}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = message.content,
                style = if (isTool) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
            )

            if (!isUser) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                    }
                    if (!message.artifactPath.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onPreviewArtifact(message.artifactPath) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Preview Artifact", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}
