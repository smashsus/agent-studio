package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.duotone.Sparkle
import com.adamglin.phosphoricons.fill.PushPin
import com.adamglin.phosphoricons.regular.ArrowDown
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.ChatsCircle
import com.adamglin.phosphoricons.regular.Plus
import com.example.authentication.ProviderMode
import com.example.database.ConversationSummary
import com.example.ui.MainViewModel
import com.example.ui.components.AgentManagerSheet
import com.example.ui.components.ApprovalCheckpointPanel
import com.example.ui.components.AssistantEditorialMessage
import com.example.ui.components.ChatHistorySheet
import com.example.ui.components.GeminiChatInputArea
import com.example.ui.components.LiveExecutionProgressCard
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.SwissDivider
import com.example.ui.components.SystemNoticeRow
import com.example.ui.components.ToolExecutionRow
import com.example.ui.components.UserMessageBubble
import com.example.ui.components.WebBridgePanel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import com.example.ui.theme.SwissMotion
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    initialDraft: String,
    onBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val summaries by viewModel.conversationSummaries.collectAsStateWithLifecycle()
    val activeId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val preferredMode by viewModel.preferredMode.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val savedProviders by viewModel.savedProviders.collectAsStateWithLifecycle()
    val activeProviderId by viewModel.activeProviderId.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()
    val agentProfiles by viewModel.agentProfiles.collectAsStateWithLifecycle()
    val activeAgentId by viewModel.activeAgentId.collectAsStateWithLifecycle()

    var inputText by rememberSaveable(initialDraft) { mutableStateOf(initialDraft) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showAgentSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val showScrollToBottom by remember {
        derivedStateOf {
            messages.isNotEmpty() && listState.canScrollForward
        }
    }

    LaunchedEffect(activeId, messages.size, executionState.isRunning) {
        if (messages.isNotEmpty()) {
            val targetIdx = if (executionState.isRunning) messages.size else messages.lastIndex
            listState.animateScrollToItem(targetIdx.coerceAtLeast(0))
        }
    }

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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your prompt")
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

    val activeSummary = remember(summaries, activeId) {
        summaries.firstOrNull { it.id == activeId }
    }

    val activeAgent = remember(agentProfiles, activeAgentId) {
        agentProfiles.firstOrNull { it.id == activeAgentId } ?: agentProfiles.firstOrNull()
    }
    val effectiveModelId = activeAgent?.preferredModelId?.takeIf { it.isNotBlank() } ?: settings.modelId

    val runtimeLabel = if (preferredMode == ProviderMode.WEB_SESSION) {
        "Web Session"
    } else {
        effectiveModelId.ifBlank { "Pick Model" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
            .imePadding()
    ) {
        // Minimal, Distraction-Free Chat Top Bar
        ChatTopBar(
            activeSummary = activeSummary,
            agentHandle = activeAgent?.handle ?: "@build",
            runtimeLabel = runtimeLabel,
            modelMissing = preferredMode != ProviderMode.WEB_SESSION && effectiveModelId.isBlank(),
            onBack = onBack,
            onOpenHistory = { showHistorySheet = true },
            onOpenAgents = { showAgentSheet = true },
            onOpenModels = { showModelSheet = true },
            onNewThread = { viewModel.createNewConversation() }
        )

        // Human Approval Checkpoint (only appears when needed)
        AnimatedVisibility(
            visible = executionState.pendingApproval != null,
            enter = fadeIn(tween(SwissMotion.fadeDurationMs, easing = SwissMotion.standardEasing)) +
                expandVertically(tween(SwissMotion.expandDurationMs)),
            exit = fadeOut(tween(SwissMotion.fadeDurationMs)) +
                shrinkVertically(tween(SwissMotion.expandDurationMs))
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

        // Web-Bridge Orchestration Panel (only appears when needed)
        AnimatedVisibility(
            visible = executionState.pendingWebBridge != null,
            enter = fadeIn(tween(SwissMotion.fadeDurationMs, easing = SwissMotion.standardEasing)) +
                expandVertically(tween(SwissMotion.expandDurationMs)),
            exit = fadeOut(tween(SwissMotion.fadeDurationMs)) +
                shrinkVertically(tween(SwissMotion.expandDurationMs))
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

        val lastUserContent = remember(messages) {
            messages.lastOrNull { it.role == "USER" }?.content
        }

        // Dedicated Scrollable Message Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !executionState.isRunning) {
                EmptyChatConversationState(
                    runtimeLabel = runtimeLabel,
                    onSelectSuggestion = { suggestion -> inputText = suggestion },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = SwissGeometry.screenPadding)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_message_list"),
                    contentPadding = PaddingValues(
                        horizontal = SwissGeometry.screenPadding,
                        vertical = 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        when (msg.role) {
                            "USER" -> UserMessageBubble(
                                message = msg,
                                onCopy = { viewModel.copyToClipboard("Prompt", msg.content) },
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
                                modelLabel = settings.modelId,
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

                    if (executionState.isRunning) {
                        item(key = "live_progress") {
                            LiveExecutionProgressCard(executionState = executionState)
                        }
                    }
                }
            }

            // Floating Scroll-to-Bottom Pill Button
            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToBottom,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.lastIndex)
                            }
                        }
                    },
                    shape = CircleShape,
                    containerColor = tokens.surface2,
                    contentColor = tokens.primaryAccent,
                    modifier = Modifier.testTag("chat_scroll_to_bottom_button")
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.ArrowDown,
                        contentDescription = "Scroll to latest message",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Gemini Agent Style Material 3 Input Area
        GeminiChatInputArea(
            inputText = inputText,
            onInputChange = { inputText = it },
            placeholder = if (preferredMode == ProviderMode.WEB_SESSION) {
                "Ask Web Session..."
            } else {
                "Ask ${effectiveModelId.ifBlank { activeAgent?.name ?: "Agent" }}..."
            },
            isRunning = executionState.isRunning,
            pendingAttachment = pendingAttachment,
            onClearAttachment = { viewModel.clearAttachment() },
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
            onStop = { viewModel.stopExecution() },
            applyNavigationBarsPadding = false,
            inputTestTag = "chat_prompt_input"
        )
    }

    if (showHistorySheet) {
        ChatHistorySheet(
            summaries = summaries,
            activeConversationId = activeId,
            onDismiss = { showHistorySheet = false },
            onSelectConversation = { id -> viewModel.selectConversation(id) },
            onNewConversation = { viewModel.createNewConversation() },
            onRenameConversation = { id, title -> viewModel.renameConversation(id, title) },
            onTogglePinConversation = { id, pinned -> viewModel.togglePinConversation(id, pinned) },
            onClearConversation = { id -> viewModel.clearConversationMessages(id) },
            onExportConversation = { id -> viewModel.exportConversationMarkdown(id) },
            onDeleteConversation = { id -> viewModel.deleteConversation(id) }
        )
    }

    if (showAgentSheet) {
        AgentManagerSheet(
            agents = agentProfiles,
            activeAgentId = activeAgentId,
            allTools = viewModel.toolDefinitions,
            onDismiss = { showAgentSheet = false },
            onSelectAgent = { id -> viewModel.selectAgent(id) },
            onInstallCatalogAgent = { id -> viewModel.installCatalogAgent(id, activate = true) },
            onSaveAgent = { existingId, name, handle, desc, prompt, modelId, tools, disableAll ->
                viewModel.saveOrUpdateAgent(
                    existingId = existingId,
                    name = name,
                    handle = handle,
                    description = desc,
                    systemPrompt = prompt,
                    preferredModelId = modelId,
                    allowedTools = tools,
                    disableAllTools = disableAll,
                    activate = true
                )
            },
            onInstallFromSpec = { raw -> viewModel.installAgentFromSpec(raw) },
            onUninstallAgent = { id -> viewModel.uninstallAgent(id) }
        )
    }

    if (showModelSheet) {
        ModelSelectorSheet(
            providers = savedProviders,
            activeProviderId = activeProviderId,
            activeModelId = effectiveModelId,
            isFetchingModels = isFetchingModels,
            onDismiss = { showModelSheet = false },
            onActivateProvider = { id -> viewModel.activateSavedProvider(id) },
            onFetchModels = { id -> viewModel.fetchModelsForProvider(id) },
            onSelectModel = { provId, mId -> viewModel.selectModelIdForProvider(provId, mId) },
            onRemoveModel = { provId, mId -> viewModel.removeModelIdFromProvider(provId, mId) },
            onOpenEndpoints = onNavigateToAuth
        )
    }
}

@Composable
private fun ChatTopBar(
    activeSummary: ConversationSummary?,
    agentHandle: String,
    runtimeLabel: String,
    modelMissing: Boolean,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenModels: () -> Unit,
    onNewThread: () -> Unit
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
                .defaultMinSize(minHeight = 58.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = tokens.surface2,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(40.dp)
                        .defaultMinSize(minWidth = SwissGeometry.minTouchTarget, minHeight = SwissGeometry.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("chat_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.ArrowLeft,
                            contentDescription = "Back to Home",
                            tint = tokens.primaryText,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenHistory)
                            .padding(vertical = 2.dp)
                            .testTag("chat_thread_title_button")
                    ) {
                        Text(
                            text = activeSummary?.title ?: "New Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (activeSummary?.isPinned == true) {
                            Icon(
                                imageVector = PhosphorIcons.Fill.PushPin,
                                contentDescription = "Pinned",
                                tint = tokens.primaryAccent,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Icon(
                            imageVector = PhosphorIcons.Regular.CaretDown,
                            contentDescription = "Switch Thread",
                            tint = tokens.secondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // OpenCode-style interactive Agent (@handle) · Model ID selector strip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Surface(
                            color = tokens.secondaryAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenAgents)
                                .testTag("chat_agent_picker_button")
                        ) {
                            Text(
                                text = agentHandle,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.secondaryAccent,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = if (modelMissing) tokens.warning.copy(alpha = 0.14f) else tokens.primaryAccent.copy(alpha = 0.11f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenModels)
                                .testTag("chat_model_picker_button")
                        ) {
                            Text(
                                text = runtimeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = if (modelMissing) tokens.warning else tokens.primaryAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("chat_threads_button")
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.ChatsCircle,
                        contentDescription = "Threads",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNewThread,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("chat_new_thread_button")
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.Plus,
                        contentDescription = "New Chat",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        SwissDivider()
    }
}

@Composable
private fun EmptyChatConversationState(
    runtimeLabel: String,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val suggestions = listOf(
        "Summarize workspace files",
        "Inspect shell & system tools",
        "Draft a Markdown plan",
        "Build a quick HTML tool"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = tokens.primaryAccent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PhosphorIcons.Duotone.Sparkle,
                    contentDescription = null,
                    tint = tokens.primaryAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "How can I help?",
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.primaryText,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Running on $runtimeLabel · Clean conversation space",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp)
        )

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    color = tokens.surface1,
                    shape = CircleShape,
                    border = BorderStroke(0.75.dp, tokens.border.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onSelectSuggestion(suggestion) }
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.primaryText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}
