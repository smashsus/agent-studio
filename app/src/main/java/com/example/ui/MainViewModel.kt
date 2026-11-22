package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentProfile
import com.example.app.AgentStudioApp
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.database.ConversationEntity
import com.example.database.ConversationSummary
import com.example.database.ExecutionLogEntity
import com.example.database.MemoryEntryEntity
import com.example.database.MessageEntity
import com.example.database.SubtaskEntity
import com.example.database.TaskPlanEntity
import com.example.execution.AgentPhase
import com.example.execution.BackgroundAgentWorker
import com.example.execution.ExecutionUiState
import com.example.memory.MemoryLayer
import com.example.model.OpenAiCompatibleModelProvider
import com.example.settings.AgentSettings
import com.example.settings.AutonomyLevel
import com.example.settings.SavedProviderProfile
import com.example.tools.ToolDefinition
import com.example.tools.ToolResult
import com.example.tools.WorkspaceFileInfo
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

data class PendingAttachment(
    val uri: String,
    val name: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AgentStudioApp
    private val container = app.container

    val authState: StateFlow<AuthState> = container.authManager.authState
    val preferredMode: StateFlow<ProviderMode> = container.authManager.preferredMode
    val executionState: StateFlow<ExecutionUiState> = container.executionMonitor.state

    val settings: StateFlow<AgentSettings> = container.settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AgentSettings()
    )

    val conversations: StateFlow<List<ConversationEntity>> = container.conversationManager.conversations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val conversationSummaries: StateFlow<List<ConversationSummary>> =
        container.conversationManager.conversationSummaries.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = _activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else container.conversationManager.observeMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestPlan: StateFlow<TaskPlanEntity?> = _activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else container.memoryManager.observeLatestPlan(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val subtasks: StateFlow<List<SubtaskEntity>> = latestPlan.flatMapLatest { plan ->
        if (plan == null) flowOf(emptyList())
        else container.memoryManager.observeSubtasks(plan.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val executionLogs: StateFlow<List<ExecutionLogEntity>> = _activeConversationId.flatMapLatest { id ->
        if (id == null) container.database.executionLogDao().observeRecentLogs()
        else container.database.executionLogDao().observeLogsForConversation(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskMemories: StateFlow<List<MemoryEntryEntity>> = container.memoryManager.taskMemories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val longTermMemories: StateFlow<List<MemoryEntryEntity>> = container.memoryManager.longTermMemories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _workspaceFiles = MutableStateFlow<List<WorkspaceFileInfo>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceFileInfo>> = _workspaceFiles.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _htmlPreviewContent = MutableStateFlow<Pair<String, String>?>(null)
    val htmlPreviewContent: StateFlow<Pair<String, String>?> = _htmlPreviewContent.asStateFlow()

    private val _filePreviewContent = MutableStateFlow<Pair<String, String>?>(null)
    val filePreviewContent: StateFlow<Pair<String, String>?> = _filePreviewContent.asStateFlow()

    private val _jsSandboxOutput = MutableStateFlow<String?>(null)
    val jsSandboxOutput: StateFlow<String?> = _jsSandboxOutput.asStateFlow()

    private val _shellSandboxOutput = MutableStateFlow<String?>(null)
    val shellSandboxOutput: StateFlow<String?> = _shellSandboxOutput.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _openChatEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openChatEvents: SharedFlow<Unit> = _openChatEvents.asSharedFlow()

    val savedProviders: StateFlow<List<SavedProviderProfile>> = container.providerRegistryStore.providers
    val activeProviderId: StateFlow<String?> = container.providerRegistryStore.activeProviderId
    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    val agentProfiles: StateFlow<List<AgentProfile>> = container.agentProfileStore.agents
    val activeAgentId: StateFlow<String> = container.agentProfileStore.activeAgentId

    val toolDefinitions: List<ToolDefinition> = container.toolRegistry.allDefinitions()

    init {
        viewModelScope.launch {
            val s = container.settingsStore.settings.first()
            container.providerRegistryStore.ensureMigratedFromLegacy(
                legacyBaseUrl = container.authManager.getSavedApiBaseUrl().ifBlank { s.customBaseUrl },
                legacyModelId = s.modelId,
                legacyKey = container.authManager.getApiKeyForModelTransport()
            )
            val existing = container.conversationManager.conversations.first()
            if (existing.isEmpty()) {
                _activeConversationId.value = container.conversationManager.ensureDefaultConversation()
            } else {
                _activeConversationId.value = existing.first().id
            }
            refreshWorkspaceFiles()
        }
        viewModelScope.launch {
            executionState.collect { st ->
                if (st.phase == AgentPhase.COMPLETED || st.phase == AgentPhase.EXECUTING_TOOL) {
                    refreshWorkspaceFiles()
                }
            }
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun postMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val id = container.conversationManager.createConversation()
            _activeConversationId.value = id
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            container.conversationManager.deleteConversation(id)
            val remaining = container.conversationManager.conversations.first()
            if (_activeConversationId.value == id || _activeConversationId.value == null) {
                _activeConversationId.value = remaining.firstOrNull()?.id
                    ?: container.conversationManager.createConversation()
            }
            _snackbarMessage.value = "Thread deleted."
        }
    }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch {
            container.conversationManager.renameConversation(id, newTitle)
            _snackbarMessage.value = "Thread renamed."
        }
    }

    fun togglePinConversation(id: Long, currentlyPinned: Boolean) {
        viewModelScope.launch {
            container.conversationManager.setPinned(id, !currentlyPinned)
        }
    }

    fun clearConversationMessages(id: Long) {
        viewModelScope.launch {
            container.conversationManager.clearMessages(id)
            _snackbarMessage.value = "Cleared thread messages."
        }
    }

    fun exportConversationMarkdown(id: Long) {
        viewModelScope.launch {
            val conv = conversations.value.firstOrNull { it.id == id }
            val msgs = container.conversationManager.getMessagesSnapshot(id)
            if (msgs.isEmpty()) {
                _snackbarMessage.value = "No messages in thread to export."
                return@launch
            }
            val md = buildString {
                appendLine("# ${conv?.title ?: "Agent Thread"}")
                appendLine()
                msgs.forEach { m ->
                    when (m.role) {
                        "USER" -> appendLine("## User\n${m.content}\n")
                        "TOOL" -> appendLine("### Tool: ${m.toolName ?: "tool"}\n```\n${m.content.take(2000)}\n```\n")
                        "SYSTEM" -> appendLine("> System: ${m.content}\n")
                        else -> appendLine("## Assistant\n${m.content}\n")
                    }
                }
            }
            shareText(md)
        }
    }

    fun sendPrompt(prompt: String) {
        viewModelScope.launch {
            val convId = _activeConversationId.value
                ?: container.conversationManager.createConversation().also { _activeConversationId.value = it }
            val attachment = _pendingAttachment.value
            _pendingAttachment.value = null
            container.agentController.startMission(
                conversationId = convId,
                userPrompt = prompt,
                attachmentUri = attachment?.uri,
                attachmentName = attachment?.name
            )
        }
    }

    fun scheduleBackgroundMission(prompt: String) {
        viewModelScope.launch {
            if (prompt.isBlank()) return@launch
            val convId = _activeConversationId.value
                ?: container.conversationManager.createConversation().also { _activeConversationId.value = it }
            BackgroundAgentWorker.enqueue(app, convId, prompt.trim())
            _snackbarMessage.value = "Mission scheduled via WorkManager background execution."
        }
    }

    fun stopExecution() {
        container.agentController.stopExecution()
    }

    fun resolveApproval(approved: Boolean) {
        container.agentController.resolveApproval(approved)
    }

    fun submitWebBridgeReply(reply: String) {
        if (reply.isBlank()) return
        container.agentController.submitWebBridgeReply(reply)
    }

    fun runWebBridgeLocalTools() {
        container.agentController.runWebBridgeLocalToolchain()
    }

    fun attachUri(uri: Uri) {
        val name = resolveDisplayName(uri)
        _pendingAttachment.value = PendingAttachment(uri.toString(), name)
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun handleIncomingShare(sharedText: String?, sharedUri: Uri?) {
        if (sharedUri != null) {
            attachUri(sharedUri)
        }
        if (!sharedText.isNullOrBlank()) {
            sendPrompt("Analyze and act on this shared content:\n$sharedText")
        }
        _openChatEvents.tryEmit(Unit)
    }

    private fun resolveDisplayName(uri: Uri): String {
        val cursor = runCatching {
            app.contentResolver.query(uri, null, null, null, null)
        }.getOrNull()
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return uri.lastPathSegment ?: "document"
    }

    // --- Authentication ---
    fun selectProviderMode(mode: ProviderMode) {
        container.authManager.selectProviderMode(mode)
    }

    fun beginWebAuthentication(portalUrl: String? = null) {
        container.authManager.beginWebAuthentication(portalUrl)
    }

    fun onWebPageLoaded(url: String?, title: String?) {
        container.authManager.updateWebSessionFromEmbeddedBrowser(url, title)
    }

    fun confirmWebSessionConnected(portalUrl: String? = null) {
        container.authManager.markWebSessionConfirmedByUser(portalUrl = portalUrl)
        _snackbarMessage.value = "Embedded Web Session marked active."
    }

    fun expireWebSessionForTestOrReauth() {
        container.authManager.expireSessionForReauth()
    }

    fun saveApiFallback(apiKey: String, baseUrl: String, modelId: String, isCustom: Boolean) {
        saveProviderProfile(
            existingId = container.providerRegistryStore.activeProviderId.value,
            name = "",
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModelId = modelId,
            customModelIds = emptyList(),
            verifyConnection = true,
            setActive = true
        )
    }

    fun saveProviderProfile(
        existingId: String?,
        name: String,
        baseUrl: String,
        apiKey: String,
        selectedModelId: String,
        customModelIds: List<String>,
        verifyConnection: Boolean = true,
        setActive: Boolean = true
    ) {
        viewModelScope.launch {
            val cleanBase = OpenAiCompatibleModelProvider.normalizeBaseUrl(baseUrl)
            val cleanModel = selectedModelId.trim().ifEmpty { settings.value.modelId.trim() }
            if (!cleanBase.startsWith("https://")) {
                _snackbarMessage.value = "Only HTTPS Base URLs are permitted."
                return@launch
            }
            if (cleanModel.isEmpty() && customModelIds.isEmpty()) {
                _snackbarMessage.value = "Please enter at least one Model ID."
                return@launch
            }

            val existingKey = existingId?.let { container.providerRegistryStore.getProviderKey(it) }
                ?: container.authManager.getApiKeyForModelTransport()
            val effectiveKey = apiKey.trim().ifBlank { existingKey.orEmpty() }

            if (effectiveKey.isBlank()) {
                _snackbarMessage.value = "API key cannot be empty."
                return@launch
            }

            if (verifyConnection) {
                val res = container.authManager.verifyAndFetchModels(effectiveKey, cleanBase)
                if (res.isSuccess) {
                    val (savedBase, discovered) = res.getOrThrow()
                    val mergedModels = (customModelIds + discovered).distinct()
                    val resolvedModel = cleanModel.ifBlank { mergedModels.firstOrNull().orEmpty() }
                    val saved = container.providerRegistryStore.saveOrUpdateProvider(
                        existingId = existingId,
                        name = name,
                        baseUrl = savedBase,
                        apiKey = effectiveKey,
                        selectedModelId = resolvedModel,
                        modelIds = mergedModels,
                        verifiedNow = true,
                        setActive = setActive
                    )
                    if (setActive) {
                        container.settingsStore.setModelConfig(
                            modelId = saved.selectedModelId,
                            customBaseUrl = saved.baseUrl
                        )
                    }
                    val countNote = if (discovered.isNotEmpty()) " · ${discovered.size} models discovered" else ""
                    _snackbarMessage.value = "Saved & connected ${saved.name} (${saved.selectedModelId})$countNote."
                } else {
                    _snackbarMessage.value = res.exceptionOrNull()?.message ?: "Authentication failed."
                }
            } else {
                val saved = container.providerRegistryStore.saveOrUpdateProvider(
                    existingId = existingId,
                    name = name,
                    baseUrl = cleanBase,
                    apiKey = effectiveKey,
                    selectedModelId = cleanModel,
                    modelIds = customModelIds,
                    verifiedNow = false,
                    setActive = setActive
                )
                if (setActive) {
                    container.authManager.activateProviderCredential(effectiveKey, saved.baseUrl)
                    container.settingsStore.setModelConfig(
                        modelId = saved.selectedModelId,
                        customBaseUrl = saved.baseUrl
                    )
                }
                _snackbarMessage.value = "Updated ${saved.name}."
            }
        }
    }

    fun activateSavedProvider(providerId: String) {
        viewModelScope.launch {
            val profile = container.providerRegistryStore.setActiveProvider(providerId) ?: return@launch
            val key = container.providerRegistryStore.getProviderKey(providerId)
            container.authManager.activateProviderCredential(key, profile.baseUrl)
            container.settingsStore.setModelConfig(
                modelId = profile.selectedModelId.ifBlank { settings.value.modelId },
                customBaseUrl = profile.baseUrl
            )
            _snackbarMessage.value = "Switched to ${profile.name} (${profile.selectedModelId})."
        }
    }

    fun deleteSavedProvider(providerId: String) {
        viewModelScope.launch {
            val wasActive = container.providerRegistryStore.activeProviderId.value == providerId
            val nextActive = container.providerRegistryStore.deleteProvider(providerId)
            if (wasActive && nextActive != null) {
                val key = container.providerRegistryStore.getProviderKey(nextActive.id)
                container.authManager.activateProviderCredential(key, nextActive.baseUrl)
                container.settingsStore.setModelConfig(
                    modelId = nextActive.selectedModelId,
                    customBaseUrl = nextActive.baseUrl
                )
            } else if (nextActive == null) {
                container.authManager.logoutAll()
            }
            _snackbarMessage.value = "Removed provider."
        }
    }

    fun fetchModelsForProvider(providerId: String? = container.providerRegistryStore.activeProviderId.value) {
        viewModelScope.launch {
            if (_isFetchingModels.value) return@launch
            val profile = providerId?.let { id -> savedProviders.value.firstOrNull { it.id == id } }
            val baseUrl = profile?.baseUrl ?: settings.value.customBaseUrl.ifBlank { getSavedApiBaseUrl() }
            val apiKey = profile?.id?.let { container.providerRegistryStore.getProviderKey(it) }
                ?: container.authManager.getApiKeyForModelTransport().orEmpty()

            if (baseUrl.isBlank() || apiKey.isBlank()) {
                _snackbarMessage.value = "Configure an HTTPS Base URL and API Key first."
                return@launch
            }
            _isFetchingModels.value = true
            val res = OpenAiCompatibleModelProvider.fetchAvailableModels(
                baseUrl = baseUrl,
                apiKey = apiKey,
                timeoutSeconds = settings.value.timeoutSeconds.toLong()
            )
            _isFetchingModels.value = false
            res.onSuccess { models ->
                if (models.isEmpty()) {
                    _snackbarMessage.value = "Endpoint returned an empty /models list; you can add custom Model IDs manually."
                } else {
                    if (profile != null) {
                        container.providerRegistryStore.updateProviderModels(profile.id, models)
                    }
                    _snackbarMessage.value = "Loaded ${models.size} model IDs."
                }
            }.onFailure { err ->
                _snackbarMessage.value = "Could not fetch /models: ${err.message}"
            }
        }
    }

    fun selectModelIdForProvider(providerId: String?, modelId: String, alsoClearAgentOverride: Boolean = true) {
        viewModelScope.launch {
            val clean = modelId.trim()
            if (clean.isEmpty()) return@launch
            val targetId = providerId ?: container.providerRegistryStore.activeProviderId.value
            val updated = targetId?.let {
                container.providerRegistryStore.addCustomModelId(it, clean, selectImmediately = true)
            }
            val isActive = updated == null || updated.id == container.providerRegistryStore.activeProviderId.value
            if (isActive) {
                val baseUrl = updated?.baseUrl ?: settings.value.customBaseUrl.ifBlank { getSavedApiBaseUrl() }
                container.settingsStore.setModelConfig(clean, baseUrl)
            }
            if (alsoClearAgentOverride) {
                val currentAgent = container.agentProfileStore.getActiveAgent()
                if (currentAgent.preferredModelId.isNotBlank()) {
                    container.agentProfileStore.saveOrUpdateAgent(
                        existingId = currentAgent.id,
                        name = currentAgent.name,
                        handle = currentAgent.handle,
                        description = currentAgent.description,
                        systemPrompt = currentAgent.systemPrompt,
                        preferredModelId = "",
                        allowedTools = currentAgent.allowedTools,
                        disableAllTools = currentAgent.disableAllTools,
                        activate = true
                    )
                }
            }
            _snackbarMessage.value = "Active model set to $clean."
        }
    }

    fun removeModelIdFromProvider(providerId: String, modelId: String) {
        viewModelScope.launch {
            val updated = container.providerRegistryStore.removeModelId(providerId, modelId)
            if (updated != null && updated.id == container.providerRegistryStore.activeProviderId.value && updated.selectedModelId.isNotBlank()) {
                container.settingsStore.setModelConfig(updated.selectedModelId, updated.baseUrl)
            }
        }
    }

    fun hasKeyForProvider(providerId: String): Boolean = container.providerRegistryStore.hasProviderKey(providerId)

    // --- OpenCode-Style Agents ---
    fun selectAgent(agentId: String) {
        val selected = container.agentProfileStore.selectAgent(agentId) ?: return
        _snackbarMessage.value = "Active agent: ${selected.name} (${selected.handle})"
    }

    fun installCatalogAgent(agentId: String, activate: Boolean = true) {
        val installed = container.agentProfileStore.installCatalogAgent(agentId, activate) ?: return
        _snackbarMessage.value = "Installed ${installed.name} (${installed.handle})."
    }

    fun saveOrUpdateAgent(
        existingId: String?,
        name: String,
        handle: String,
        description: String,
        systemPrompt: String,
        preferredModelId: String,
        allowedTools: List<String>,
        disableAllTools: Boolean,
        activate: Boolean = true
    ) {
        val saved = container.agentProfileStore.saveOrUpdateAgent(
            existingId = existingId,
            name = name,
            handle = handle,
            description = description,
            systemPrompt = systemPrompt,
            preferredModelId = preferredModelId,
            allowedTools = allowedTools,
            disableAllTools = disableAllTools,
            activate = activate
        )
        _snackbarMessage.value = "Saved agent ${saved.name} (${saved.handle})."
    }

    fun installAgentFromSpec(rawSpec: String): Boolean {
        val res = container.agentProfileStore.installFromSpecText(rawSpec)
        return res.fold(
            onSuccess = { profile ->
                _snackbarMessage.value = "Installed agent ${profile.name} (${profile.handle})."
                true
            },
            onFailure = { err ->
                _snackbarMessage.value = "Agent import failed: ${err.message}"
                false
            }
        )
    }

    fun uninstallAgent(agentId: String) {
        container.agentProfileStore.uninstallOrDeleteAgent(agentId)
        _snackbarMessage.value = "Removed agent."
    }

    fun getSavedApiBaseUrl(): String = container.authManager.getSavedApiBaseUrl()
    fun getSavedWebPortalUrl(): String = container.authManager.getSavedWebPortalUrl()
    fun hasApiFallback(): Boolean = container.authManager.hasApiFallbackConfigured()

    fun logoutAll() {
        container.authManager.logoutAll()
        container.providerRegistryStore.clearAll()
        _snackbarMessage.value = "Logged out and cleared WebView cookies & Keystore credentials."
    }

    // --- Memory ---
    fun addMemory(layer: String, key: String, content: String, category: String) {
        viewModelScope.launch {
            if (key.isBlank() || content.isBlank()) return@launch
            container.memoryManager.saveMemory(layer, key, content, category.ifBlank { "User Note" }, true)
            _snackbarMessage.value = "Saved to ${if (layer == MemoryLayer.LONG_TERM) "Long-Term" else "Task"} Memory."
        }
    }

    fun updateMemory(entry: MemoryEntryEntity) {
        viewModelScope.launch {
            container.memoryManager.updateMemory(entry)
            _snackbarMessage.value = "Memory updated."
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            container.memoryManager.deleteMemory(id)
        }
    }

    fun clearMemoryLayer(layer: String) {
        viewModelScope.launch {
            container.memoryManager.clearLayer(layer)
        }
    }

    // --- Files & Sandbox ---
    fun refreshWorkspaceFiles() {
        _workspaceFiles.value = container.fileManager.listAllFiles()
    }

    fun openWorkspaceFilePreview(fileInfo: WorkspaceFileInfo) {
        viewModelScope.launch {
            val file = File(fileInfo.absolutePath)
            if (!file.exists()) return@launch
            if (fileInfo.extension == "html") {
                _htmlPreviewContent.value = fileInfo.name to file.readText(Charsets.UTF_8)
            } else if (fileInfo.extension == "pdf") {
                val summary = runCatching { container.fileManager.analyzePdfFile(file) }
                    .getOrElse { "PDF preview error: ${it.message}" }
                _filePreviewContent.value = fileInfo.name to summary
            } else {
                _filePreviewContent.value = fileInfo.name to file.readText(Charsets.UTF_8).take(15_000)
            }
        }
    }

    fun openHtmlPreviewFromPath(path: String) {
        viewModelScope.launch {
            val file = File(path)
            if (file.exists() && file.extension.equals("html", true)) {
                _htmlPreviewContent.value = file.name to file.readText(Charsets.UTF_8)
            } else if (file.exists()) {
                val content = if (file.extension.equals("pdf", true)) {
                    container.fileManager.analyzePdfFile(file)
                } else {
                    file.readText(Charsets.UTF_8).take(15_000)
                }
                _filePreviewContent.value = file.name to content
            }
        }
    }

    fun closeHtmlPreview() {
        _htmlPreviewContent.value = null
    }

    fun closeFilePreview() {
        _filePreviewContent.value = null
    }

    fun exportFileToSaf(sourcePath: String, destUri: Uri) {
        viewModelScope.launch {
            runCatching {
                container.fileManager.exportWorkspaceFileToUri(sourcePath, destUri)
            }.onSuccess {
                _snackbarMessage.value = "Exported file to selected location."
            }.onFailure {
                _snackbarMessage.value = "Export failed: ${it.message}"
            }
        }
    }

    fun deleteWorkspaceFile(path: String) {
        if (container.fileManager.deleteWorkspaceFile(path)) {
            refreshWorkspaceFiles()
            _snackbarMessage.value = "Deleted file."
        }
    }

    fun shareFile(fileInfo: WorkspaceFileInfo) {
        runCatching {
            val file = File(fileInfo.absolutePath)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = when (fileInfo.extension) {
                    "pdf" -> "application/pdf"
                    "html" -> "text/html"
                    else -> "text/plain"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share ${fileInfo.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(chooser)
        }.onFailure {
            _snackbarMessage.value = "Share failed: ${it.message}"
        }
    }

    fun runJsSnippetInSandbox(code: String) {
        viewModelScope.launch {
            _jsSandboxOutput.value = "Executing in isolated WebView sandbox..."
            val tool = container.toolRegistry.getTool("run_javascript")
            val result: ToolResult = tool?.execute(
                buildJsonObject { put("code", code) },
                settings.value
            ) ?: ToolResult(false, "", error = "JS tool missing.")
            _jsSandboxOutput.value = if (result.success) result.output else "Error: ${result.error}"
        }
    }

    fun runShellInSandbox(command: String) {
        viewModelScope.launch {
            _shellSandboxOutput.value = "Executing via /system/bin/sh..."
            val tool = container.toolRegistry.getTool("run_shell")
            val result: ToolResult = tool?.execute(
                buildJsonObject { put("command", command) },
                settings.value
            ) ?: ToolResult(false, "", error = "Shell tool missing.")
            _shellSandboxOutput.value = if (result.success) result.output else (result.error ?: "Shell command failed.")
            refreshWorkspaceFiles()
        }
    }

    // --- Clipboard & Share ---
    fun copyToClipboard(label: String, text: String) {
        val cb = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, text))
        _snackbarMessage.value = "Copied to clipboard."
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "Share via Agent Studio").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(chooser)
    }

    // --- Settings & Permissions ---
    fun setAutonomyLevel(level: AutonomyLevel) {
        viewModelScope.launch { container.settingsStore.setAutonomyLevel(level) }
    }

    fun setModelConfig(modelId: String, baseUrl: String) {
        viewModelScope.launch {
            val cleanBase = com.example.model.OpenAiCompatibleModelProvider.normalizeBaseUrl(baseUrl)
            val cleanModel = modelId.trim()
            if (cleanModel.isEmpty()) {
                _snackbarMessage.value = "Model ID cannot be empty."
                return@launch
            }
            container.settingsStore.setModelConfig(cleanModel, cleanBase)
            container.authManager.syncApiBaseUrlIfConfigured(cleanBase)
            _snackbarMessage.value = "Model configuration updated ($cleanModel)."
        }
    }

    fun setExecutionLimits(retries: Int, timeoutSeconds: Int, maxSteps: Int) {
        viewModelScope.launch {
            container.settingsStore.setExecutionLimits(retries, timeoutSeconds, maxSteps)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsStore.setThemeMode(mode) }
    }

    fun setNetworkPolicy(restrict: Boolean, domainsCsv: String) {
        viewModelScope.launch { container.settingsStore.setNetworkPolicy(restrict, domainsCsv) }
    }

    fun setMemoryPreferences(longTerm: Boolean, autoSummarize: Boolean) {
        viewModelScope.launch { container.settingsStore.setMemoryPreferences(longTerm, autoSummarize) }
    }

    fun setAccessibilityConsent(consented: Boolean) {
        viewModelScope.launch { container.settingsStore.setAccessibilityConsent(consented) }
    }

    fun setToolEnabled(toolName: String, enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setToolEnabled(toolName, enabled) }
    }

    fun hasNotificationPermission(): Boolean = container.permissionManager.hasNotificationPermission()
    fun hasRecordAudioPermission(): Boolean = container.permissionManager.hasRecordAudioPermission()
    fun isAccessibilityServiceConnected(): Boolean = container.permissionManager.isAccessibilityServiceEnabled()

    fun clearAllLocalData() {
        viewModelScope.launch {
            container.conversationManager.clearAllHistory()
            container.memoryManager.clearAllMemories()
            _activeConversationId.value = container.conversationManager.createConversation()
            _snackbarMessage.value = "Cleared conversation history, plans, logs, and memory."
        }
    }
}
