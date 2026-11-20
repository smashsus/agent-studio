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
import com.example.app.DeepSeekAgentApp
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.database.ConversationEntity
import com.example.database.ExecutionLogEntity
import com.example.database.MemoryEntryEntity
import com.example.database.MessageEntity
import com.example.database.SubtaskEntity
import com.example.database.TaskPlanEntity
import com.example.execution.BackgroundAgentWorker
import com.example.execution.ExecutionUiState
import com.example.memory.MemoryLayer
import com.example.settings.AgentSettings
import com.example.settings.AutonomyLevel
import com.example.tools.ToolDefinition
import com.example.tools.ToolResult
import com.example.tools.WorkspaceFileInfo
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val app = application as DeepSeekAgentApp
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

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val toolDefinitions: List<ToolDefinition> = container.toolRegistry.allDefinitions()

    init {
        viewModelScope.launch {
            val existing = container.conversationManager.conversations.first()
            if (existing.isEmpty()) {
                _activeConversationId.value = container.conversationManager.ensureDefaultConversation()
            } else {
                _activeConversationId.value = existing.first().id
            }
            refreshWorkspaceFiles()
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
            _activeConversationId.value = remaining.firstOrNull()?.id
                ?: container.conversationManager.createConversation()
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

    fun beginWebAuthentication() {
        container.authManager.beginWebAuthentication()
    }

    fun onDeepSeekWebPageLoaded(url: String?, title: String?) {
        container.authManager.updateWebSessionFromEmbeddedBrowser(url, title)
    }

    fun confirmWebSessionConnected() {
        container.authManager.markWebSessionConfirmedByUser("DeepSeek Account (Web Session)")
        _snackbarMessage.value = "DeepSeek Web Session marked active."
    }

    fun expireWebSessionForTestOrReauth() {
        container.authManager.expireSessionForReauth()
    }

    fun saveApiFallback(apiKey: String, baseUrl: String, isCustom: Boolean) {
        viewModelScope.launch {
            val res = container.authManager.saveAndVerifyApiFallback(apiKey, baseUrl, isCustom)
            if (res.isSuccess) {
                container.settingsStore.setModelConfig(
                    modelId = settings.value.modelId,
                    customBaseUrl = baseUrl.ifBlank { "https://api.deepseek.com" }
                )
                _snackbarMessage.value = "Encrypted API fallback connected in Android Keystore."
            } else {
                _snackbarMessage.value = res.exceptionOrNull()?.message ?: "Authentication failed."
            }
        }
    }

    fun getSavedApiBaseUrl(): String = container.authManager.getSavedApiBaseUrl()
    fun hasApiFallback(): Boolean = container.authManager.hasApiFallbackConfigured()

    fun logoutAll() {
        container.authManager.logoutAll()
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
        val chooser = Intent.createChooser(intent, "Share via DeepSeek Agent").apply {
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
            container.settingsStore.setModelConfig(modelId, baseUrl)
            _snackbarMessage.value = "Model configuration updated."
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
