package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.database.MessageEntity
import com.example.execution.AgentPhase
import com.example.execution.ExecutionUiState
import com.example.ui.MainViewModel
import com.example.ui.components.MarkdownMessageContent
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissDivider
import com.example.ui.components.SwissOutlinedButton
import com.example.ui.components.SwissPrimaryButton
import com.example.ui.components.SwissStatusIndicator
import com.example.ui.components.SwissSurfaceCard
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import com.example.ui.theme.SwissMotion
import com.example.ui.theme.SwissOpacity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceScreen(
    viewModel: MainViewModel,
    onNavigateToAuth: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val preferredMode by viewModel.preferredMode.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task")
        }
        runCatching { speechLauncher.launch(intent) }.onFailure {
            viewModel.postMessage("Speech recognition unavailable on this device.")
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
            .background(tokens.background)
            .imePadding()
    ) {
        // Compact Swiss Header
        HomeHeader(
            activeTitle = conversations.firstOrNull { it.id == activeId }?.title ?: "Workspace",
            conversations = conversations,
            activeId = activeId,
            authState = authState,
            preferredMode = preferredMode,
            executionState = executionState,
            showMenu = showConversationsMenu,
            onToggleMenu = { showConversationsMenu = !showConversationsMenu },
            onDismissMenu = { showConversationsMenu = false },
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
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToActivity = onNavigateToActivity
        )

        // Human Approval Checkpoint (Balanced / Strict)
        AnimatedVisibility(
            visible = executionState.pendingApproval != null,
            enter = fadeIn(tween(SwissMotion.fadeDurationMs, easing = SwissMotion.standardEasing)) + expandVertically(tween(SwissMotion.expandDurationMs)),
            exit = fadeOut(tween(SwissMotion.fadeDurationMs)) + shrinkVertically(tween(SwissMotion.expandDurationMs))
        ) {
            executionState.pendingApproval?.let { pending ->
                ApprovalCheckpointPanel(
                    toolName = pending.toolCall.toolName,
                    argumentsJson = pending.toolCall.argumentsJson,
                    reason = pending.reason,
                    onApprove = { viewModel.resolveApproval(true) },
                    onDecline = { viewModel.resolveApproval(false) },
                    modifier = Modifier.padding(horizontal = SwissGeometry.screenPadding, vertical = 8.dp)
                )
            }
        }

        // Web-Bridge Orchestration Panel (Priority 1 Web Session)
        AnimatedVisibility(
            visible = executionState.pendingWebBridge != null,
            enter = fadeIn(tween(SwissMotion.fadeDurationMs, easing = SwissMotion.standardEasing)) + expandVertically(tween(SwissMotion.expandDurationMs)),
            exit = fadeOut(tween(SwissMotion.fadeDurationMs)) + shrinkVertically(tween(SwissMotion.expandDurationMs))
        ) {
            executionState.pendingWebBridge?.let { bridge ->
                WebBridgePanel(
                    preparedPrompt = bridge.preparedPrompt,
                    explanation = bridge.explanation,
                    hasSuggestedLocalTools = bridge.hasSuggestedLocalTools,
                    onCopyAndOpenAuth = {
                        viewModel.copyToClipboard("Agent Bridge Prompt", bridge.preparedPrompt)
                        onNavigateToAuth()
                    },
                    onRunLocalTools = { viewModel.runWebBridgeLocalTools() },
                    onSubmitWebReply = { reply -> viewModel.submitWebBridgeReply(reply) },
                    modifier = Modifier.padding(horizontal = SwissGeometry.screenPadding, vertical = 8.dp)
                )
            }
        }

        // Distraction-Free Conversation List
        val lastUserContent = remember(messages) {
            messages.lastOrNull { it.role == "USER" }?.content
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = SwissGeometry.screenPadding,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    HomeWelcomeSection(
                        authState = authState,
                        preferredMode = preferredMode,
                        onSelectQuickMission = { prompt -> inputText = prompt },
                        onOpenEndpoints = onNavigateToAuth,
                        onOpenActivity = onNavigateToActivity
                    )
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    when (msg.role) {
                        "USER" -> UserMessageBubble(
                            message = msg,
                            onEdit = { inputText = msg.content }
                        )
                        "TOOL" -> ToolExecutionRow(
                            message = msg,
                            onCopy = { viewModel.copyToClipboard("Tool Output", msg.content) },
                            onPreviewArtifact = { path -> viewModel.openHtmlPreviewFromPath(path) }
                        )
                        "SYSTEM" -> SystemNoticeRow(message = msg)
                        else -> AssistantEditorialMessage(
                            message = msg,
                            onCopy = { viewModel.copyToClipboard("Agent Output", msg.content) },
                            onCopyCode = { code -> viewModel.copyToClipboard("Code", code) },
                            onShare = { viewModel.shareText(msg.content) },
                            onRegenerate = lastUserContent?.let { prompt ->
                                { viewModel.sendPrompt(prompt) }
                            },
                            onPreviewArtifact = { path -> viewModel.openHtmlPreviewFromPath(path) }
                        )
                    }
                }
            }
        }

        // Attachment Indicator
        AnimatedVisibility(visible = pendingAttachment != null) {
            pendingAttachment?.let { att ->
                Surface(
                    color = tokens.surface2,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    border = BorderStroke(SwissGeometry.borderWidth, tokens.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SwissGeometry.screenPadding, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Attached: ${att.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearAttachment() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                tint = tokens.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Swiss Composer
        SwissComposerBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            isRunning = executionState.isRunning,
            hasAttachment = pendingAttachment != null,
            onAttach = {
                attachLauncher.launch(
                    arrayOf("application/pdf", "text/plain", "text/markdown", "application/json", "text/*")
                )
            },
            onVoice = {
                if (viewModel.hasRecordAudioPermission()) launchVoiceRecognizer()
                else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onSend = {
                if (inputText.isNotBlank() || pendingAttachment != null) {
                    val prompt = inputText
                    inputText = ""
                    viewModel.sendPrompt(prompt)
                }
            },
            onScheduleBackground = {
                if (inputText.isNotBlank()) {
                    val prompt = inputText
                    inputText = ""
                    viewModel.scheduleBackgroundMission(prompt)
                }
            },
            onStop = { viewModel.stopExecution() }
        )
    }
}

@Composable
private fun HomeHeader(
    activeTitle: String,
    conversations: List<com.example.database.ConversationEntity>,
    activeId: Long?,
    authState: AuthState,
    preferredMode: ProviderMode,
    executionState: ExecutionUiState,
    showMenu: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToActivity: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SwissGeometry.screenPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(SwissGeometry.radiusSmall))
                        .clickable(onClick = onToggleMenu)
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 4.dp)
                        .testTag("conversation_selector_button")
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Agent Harness",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.mutedText
                        )
                        Text(
                            text = activeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Switch Thread",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(
                        text = { Text("New Thread", fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = onNewConversation
                    )
                    SwissDivider()
                    conversations.forEach { conv ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    conv.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (conv.id == activeId) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (conversations.size > 1) {
                                    IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Thread",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            onClick = { onSelectConversation(conv.id) }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("new_mission_button")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Thread",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(SwissGeometry.iconSize)
                    )
                }
                IconButton(
                    onClick = onNavigateToAuth,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("profile_account_button")
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Account & Endpoints",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(SwissGeometry.iconSize)
                    )
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("header_settings_button")
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(SwissGeometry.iconSize)
                    )
                }
            }
        }

        // Compact Status Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SwissGeometry.screenPadding, end = SwissGeometry.screenPadding, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (endpointLabel, endpointColor) = when (authState) {
                is AuthState.Authenticated -> {
                    val label = if (authState.mode == ProviderMode.WEB_SESSION) "Web Session" else "API Endpoint"
                    label to tokens.success
                }
                is AuthState.Authenticating -> "Connecting" to tokens.primaryAccent
                is AuthState.SessionExpired -> "Expired" to tokens.warning
                is AuthState.AuthenticationFailed -> "Auth Error" to tokens.error
                AuthState.NotAuthenticated -> {
                    (if (preferredMode == ProviderMode.WEB_SESSION) "Web Setup" else "API Setup") to tokens.warning
                }
            }

            SwissStatusIndicator(
                label = endpointLabel,
                color = endpointColor,
                onClick = onNavigateToAuth,
                modifier = Modifier.testTag("auth_status_chip")
            )

            val phaseColor = when (executionState.phase) {
                AgentPhase.IDLE -> tokens.mutedText
                AgentPhase.COMPLETED -> tokens.success
                AgentPhase.ERROR, AgentPhase.CANCELLED -> tokens.error
                AgentPhase.AWAITING_APPROVAL, AgentPhase.AWAITING_WEB_BRIDGE -> tokens.warning
                else -> tokens.primaryAccent
            }

            val phaseLabel = buildString {
                append(executionState.phase.label)
                executionState.activeToolName?.let { append(" · $it") }
            }

            SwissStatusIndicator(
                label = phaseLabel,
                color = phaseColor,
                onClick = onNavigateToActivity,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        SwissDivider()
    }
}

@Composable
private fun HomeWelcomeSection(
    authState: AuthState,
    preferredMode: ProviderMode,
    onSelectQuickMission: (String) -> Unit,
    onOpenEndpoints: () -> Unit,
    onOpenActivity: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SwissGeometry.sectionSpacing)
    ) {
        // Compact Editorial Welcome
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Autonomous Agent Workspace",
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.primaryText,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (authState is AuthState.Authenticated && preferredMode != ProviderMode.WEB_SESSION) {
                    "Connected to your configured endpoint with on-device research, files, JavaScript sandbox, and Android tools."
                } else {
                    "Connect any compatible API endpoint or web session, and run multi-step on-device tool workflows."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.secondaryText
            )
        }

        // Quick-access Workspace Shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SwissOutlinedButton(
                text = "Configure Endpoint",
                onClick = onOpenEndpoints,
                modifier = Modifier.weight(1f)
            )
            SwissOutlinedButton(
                text = "View Activity",
                onClick = onOpenActivity,
                modifier = Modifier.weight(1f)
            )
        }

        // Sample Tasks
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Workspace Prompts",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.primaryText
            )

            val samples = listOf(
                "Research" to "Research the latest developments in quantum computing and prepare a report",
                "JavaScript Sandbox" to "Run JavaScript to compute the first 12 Fibonacci numbers:\n```js\nlet a=0,b=1,r=[]; for(let i=0;i<12;i++){r.push(a);[a,b]=[b,a+b];} JSON.stringify(r);\n```",
                "Workspace & Reminders" to "List workspace files and summarize what is stored"
            )
            samples.forEach { (category, prompt) ->
                Surface(
                    color = tokens.surface1,
                    shape = RoundedCornerShape(SwissGeometry.radiusStandard),
                    border = BorderStroke(SwissGeometry.borderWidth, tokens.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectQuickMission(prompt) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.primaryAccent
                        )
                        Text(
                            text = prompt.replace("\n", " "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.primaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: MessageEntity,
    onEdit: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            color = tokens.surface2,
            shape = RoundedCornerShape(SwissGeometry.radiusStandard),
            border = BorderStroke(SwissGeometry.borderWidth, tokens.border),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!message.attachmentName.isNullOrBlank()) {
                    SwissBadge(text = "File: ${message.attachmentName}", textColor = tokens.primaryAccent)
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.primaryText
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.mutedText
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Prompt",
                            tint = tokens.secondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantEditorialMessage(
    message: MessageEntity,
    onCopy: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShare: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onPreviewArtifact: (String) -> Unit
) {
    val tokens = LocalSwissTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarkdownMessageContent(
            markdown = message.content,
            onCopyCode = onCopyCode
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatMessageTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText,
                modifier = Modifier.padding(end = 6.dp)
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy response",
                    tint = tokens.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share response",
                    tint = tokens.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (onRegenerate != null) {
                IconButton(onClick = onRegenerate, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Regenerate",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (!message.artifactPath.isNullOrBlank()) {
                SwissOutlinedButton(
                    text = "Preview",
                    onClick = { onPreviewArtifact(message.artifactPath) },
                    icon = Icons.Default.Visibility
                )
            }
        }
    }
}

@Composable
private fun ToolExecutionRow(
    message: MessageEntity,
    onCopy: () -> Unit,
    onPreviewArtifact: (String) -> Unit
) {
    val tokens = LocalSwissTokens.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isError = message.content.startsWith("Tool error", ignoreCase = true) ||
        message.content.startsWith("Permission denied", ignoreCase = true)

    Surface(
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = if (isError) tokens.error else tokens.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = message.toolName ?: "tool",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.primaryText
                    )
                    SwissBadge(
                        text = if (isError) "Issue" else "Result",
                        textColor = if (isError) tokens.error else tokens.success
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle tool output",
                    tint = tokens.secondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissDivider()
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.secondaryText
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissOutlinedButton(
                            text = "Copy Output",
                            onClick = onCopy,
                            icon = Icons.Default.ContentCopy
                        )
                        if (!message.artifactPath.isNullOrBlank()) {
                            SwissOutlinedButton(
                                text = "Preview File",
                                onClick = { onPreviewArtifact(message.artifactPath) },
                                icon = Icons.Default.Visibility
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemNoticeRow(message: MessageEntity) {
    val tokens = LocalSwissTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(18.dp)
                .background(tokens.warning)
        )
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText
        )
    }
}

@Composable
private fun ApprovalCheckpointPanel(
    toolName: String,
    argumentsJson: String,
    reason: String,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    SwissSurfaceCard(
        modifier = modifier,
        borderColor = tokens.warning
    ) {
        Text(
            text = "Approval Required · $toolName",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.warning
        )
        Text(reason, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
        Text(
            text = argumentsJson.take(260),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.primaryText
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwissPrimaryButton(
                text = "Approve",
                onClick = onApprove,
                modifier = Modifier.testTag("approve_tool_button")
            )
            SwissOutlinedButton(
                text = "Decline",
                onClick = onDecline,
                modifier = Modifier.testTag("decline_tool_button")
            )
        }
    }
}

@Composable
private fun WebBridgePanel(
    preparedPrompt: String,
    explanation: String,
    hasSuggestedLocalTools: Boolean,
    onCopyAndOpenAuth: () -> Unit,
    onRunLocalTools: () -> Unit,
    onSubmitWebReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    var pastedReply by rememberSaveable { mutableStateOf("") }

    SwissSurfaceCard(
        modifier = modifier,
        borderColor = tokens.primaryAccent
    ) {
        Text(
            text = "Web Session Bridge",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.primaryAccent
        )
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SwissOutlinedButton(
                text = "Open Web Session",
                onClick = onCopyAndOpenAuth,
                modifier = Modifier.weight(1f).testTag("bridge_open_deepseek_button")
            )
            if (hasSuggestedLocalTools) {
                SwissPrimaryButton(
                    text = "Run Local Tools",
                    onClick = onRunLocalTools,
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f).testTag("bridge_run_local_tools_button")
                )
            }
        }
        OutlinedTextField(
            value = pastedReply,
            onValueChange = { pastedReply = it },
            label = { Text("Paste response (text or tool JSON)") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("bridge_reply_input")
        )
        SwissPrimaryButton(
            text = "Continue Loop",
            onClick = {
                if (pastedReply.isNotBlank()) {
                    val reply = pastedReply
                    pastedReply = ""
                    onSubmitWebReply(reply)
                }
            },
            enabled = pastedReply.isNotBlank(),
            modifier = Modifier.align(Alignment.End).testTag("bridge_submit_reply_button")
        )
    }
}

@Composable
private fun SwissComposerBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isRunning: Boolean,
    hasAttachment: Boolean,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    onScheduleBackground: () -> Unit,
    onStop: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) tokens.primaryAccent else tokens.border,
        animationSpec = tween(150, easing = SwissMotion.standardEasing),
        label = "composer_focus"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.background)
            .padding(horizontal = SwissGeometry.screenPadding, vertical = 10.dp)
    ) {
        Surface(
            color = if (isFocused) tokens.surface2 else tokens.surface1,
            shape = RoundedCornerShape(SwissGeometry.radiusComposer),
            border = BorderStroke(SwissGeometry.borderWidth, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 140.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onAttach,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("attach_document_button")
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach Document",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onVoice,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("voice_input_button")
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Enter task or message...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.mutedText.copy(alpha = SwissOpacity.placeholder)
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        interactionSource = interactionSource,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.primaryText),
                        cursorBrush = SolidColor(tokens.primaryAccent),
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_input_field")
                    )
                }

                if (isRunning) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(SwissGeometry.minTouchTarget)
                            .clip(RoundedCornerShape(SwissGeometry.radiusStandard))
                            .background(tokens.surface3)
                            .testTag("stop_execution_button")
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop Execution",
                            tint = tokens.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = onScheduleBackground,
                            modifier = Modifier
                                .size(SwissGeometry.minTouchTarget)
                                .testTag("schedule_workmanager_button")
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Schedule Background Execution",
                                tint = tokens.secondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    val canSend = inputText.isNotBlank() || hasAttachment
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier
                            .size(SwissGeometry.minTouchTarget)
                            .clip(RoundedCornerShape(SwissGeometry.radiusStandard))
                            .background(if (canSend) tokens.primaryAccent else tokens.surface3)
                            .testTag("send_prompt_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) {
                                if (tokens.isDark) tokens.background else androidx.compose.ui.graphics.Color.White
                            } else tokens.mutedText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMessageTime(ts: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}
