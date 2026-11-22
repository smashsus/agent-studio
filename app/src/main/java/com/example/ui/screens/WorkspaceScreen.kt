package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.PushPin
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.ChatsCircle
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.UserCircle
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.database.ConversationSummary
import com.example.execution.AgentPhase
import com.example.execution.ExecutionUiState
import com.example.ui.MainViewModel
import com.example.ui.components.AgentManagerSheet
import com.example.ui.components.ChatHistorySheet
import com.example.ui.components.GeminiChatInputArea
import com.example.ui.components.HomeWelcomeSection
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissDivider
import com.example.ui.components.SwissStatusIndicator
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry

@Composable
fun WorkspaceScreen(
    viewModel: MainViewModel,
    onOpenChat: (prefillPrompt: String) -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onNavigateToFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val summaries by viewModel.conversationSummaries.collectAsStateWithLifecycle()
    val activeId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val preferredMode by viewModel.preferredMode.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val savedProviders by viewModel.savedProviders.collectAsStateWithLifecycle()
    val activeProviderId by viewModel.activeProviderId.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()
    val agentProfiles by viewModel.agentProfiles.collectAsStateWithLifecycle()
    val activeAgentId by viewModel.activeAgentId.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showAgentSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    val activeAgent = remember(agentProfiles, activeAgentId) {
        agentProfiles.firstOrNull { it.id == activeAgentId } ?: agentProfiles.firstOrNull()
    }
    val effectiveModelId = activeAgent?.preferredModelId?.takeIf { it.isNotBlank() } ?: settings.modelId

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

    val activeSummary = remember(summaries, activeId) {
        summaries.firstOrNull { it.id == activeId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
            .imePadding()
    ) {
        HomeHeader(
            activeSummary = activeSummary,
            threadCount = summaries.size,
            authState = authState,
            preferredMode = preferredMode,
            modelId = effectiveModelId,
            executionState = executionState,
            onOpenHistory = { showHistorySheet = true },
            onNewConversation = {
                viewModel.createNewConversation()
                onOpenChat("")
            },
            onNavigateToAuth = onNavigateToAuth,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToActivity = onNavigateToActivity
        )

        // Clean Home Launchpad & Overview Viewport
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = SwissGeometry.screenPadding,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "home_welcome") {
                HomeWelcomeSection(
                    authState = authState,
                    preferredMode = preferredMode,
                    modelId = effectiveModelId,
                    activeAgentHandle = activeAgent?.handle ?: "@build",
                    autonomyLevel = settings.autonomyLevel,
                    toolCount = viewModel.toolDefinitions.size,
                    recentSummaries = summaries,
                    activeConversationId = activeId,
                    onOpenChat = { onOpenChat("") },
                    onSelectQuickMission = { prompt -> onOpenChat(prompt) },
                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                        onOpenChat("")
                    },
                    onOpenHistory = { showHistorySheet = true },
                    onOpenAgents = { showAgentSheet = true },
                    onOpenModels = { showModelSheet = true },
                    onOpenEndpoints = onNavigateToAuth,
                    onOpenFiles = onNavigateToFiles,
                    onOpenActivity = onNavigateToActivity
                )
            }
        }

        // Quick Gemini-style Starter Bar that launches directly into the dedicated Chat Container
        GeminiChatInputArea(
            inputText = inputText,
            onInputChange = { inputText = it },
            placeholder = if (preferredMode == ProviderMode.WEB_SESSION) {
                "Start a Web Session prompt..."
            } else {
                "Ask ${effectiveModelId.ifBlank { activeAgent?.name ?: "Agent" }} to start chatting..."
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
                    onOpenChat("")
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
            inputTestTag = "prompt_input_field"
        )
    }

    if (showHistorySheet) {
        ChatHistorySheet(
            summaries = summaries,
            activeConversationId = activeId,
            onDismiss = { showHistorySheet = false },
            onSelectConversation = { id ->
                viewModel.selectConversation(id)
                onOpenChat("")
            },
            onNewConversation = {
                viewModel.createNewConversation()
                onOpenChat("")
            },
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
private fun HomeHeader(
    activeSummary: ConversationSummary?,
    threadCount: Int,
    authState: AuthState,
    preferredMode: ProviderMode,
    modelId: String,
    executionState: ExecutionUiState,
    onOpenHistory: () -> Unit,
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
                .padding(start = SwissGeometry.screenPadding, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(SwissGeometry.radiusSmall))
                    .clickable(onClick = onOpenHistory)
                    .defaultMinSize(minHeight = SwissGeometry.minTouchTarget)
                    .padding(horizontal = 4.dp)
                    .testTag("conversation_selector_button")
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val runtimeLabel = if (preferredMode == ProviderMode.WEB_SESSION) {
                            "Web Session"
                        } else {
                            modelId.ifBlank { "No Model Set" }
                        }
                        Text(
                            text = runtimeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (preferredMode != ProviderMode.WEB_SESSION && modelId.isBlank()) tokens.warning else tokens.primaryAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeSummary?.isPinned == true) {
                            Icon(
                                imageVector = PhosphorIcons.Fill.PushPin,
                                contentDescription = "Pinned thread",
                                tint = tokens.primaryAccent,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    Text(
                        text = activeSummary?.title ?: "Workspace",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    PhosphorIcons.Regular.CaretDown,
                    contentDescription = "Open Thread History",
                    tint = tokens.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("chat_history_button")
                ) {
                    Icon(
                        PhosphorIcons.Regular.ChatsCircle,
                        contentDescription = "Conversations ($threadCount)",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("new_mission_button")
                ) {
                    Icon(
                        PhosphorIcons.Regular.Plus,
                        contentDescription = "New Chat",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNavigateToAuth,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("profile_account_button")
                ) {
                    Icon(
                        PhosphorIcons.Regular.UserCircle,
                        contentDescription = "Account & Endpoints",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(SwissGeometry.minTouchTarget)
                        .testTag("header_settings_button")
                ) {
                    Icon(
                        PhosphorIcons.Regular.GearSix,
                        contentDescription = "Settings",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Compact Status Row (Endpoint · Model · Phase)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = SwissGeometry.screenPadding, end = SwissGeometry.screenPadding, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (endpointLabel, endpointColor) = when (authState) {
                is AuthState.Authenticated -> {
                    val label = if (authState.mode == ProviderMode.WEB_SESSION) "Web Session" else "API Ready"
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

            if (modelId.isNotBlank() && preferredMode != ProviderMode.WEB_SESSION) {
                SwissBadge(
                    text = modelId,
                    textColor = tokens.secondaryText
                )
            }

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
                onClick = onNavigateToActivity
            )
        }
        SwissDivider()
    }
}
