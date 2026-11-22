package com.example.agent

import com.example.authentication.AuthManager
import com.example.authentication.ProviderMode
import com.example.database.AppDatabase
import com.example.database.ExecutionLogEntity
import com.example.database.MessageEntity
import com.example.execution.AgentPhase
import com.example.execution.ErrorRecoveryManager
import com.example.execution.ExecutionMonitor
import com.example.execution.PendingApproval
import com.example.execution.PendingWebBridge
import com.example.execution.TaskPlanner
import com.example.memory.MemoryLayer
import com.example.memory.MemoryManager
import com.example.model.WebBridgeProvider
import com.example.model.ModelMessage
import com.example.model.ModelProvider
import com.example.model.ModelRequest
import com.example.model.ModelResponse
import com.example.model.OpenAiCompatibleModelProvider
import com.example.permissions.PermissionDecision
import com.example.permissions.PermissionManager
import com.example.settings.AgentSettings
import com.example.settings.SettingsStore
import com.example.tools.ToolCallRequest
import com.example.tools.ToolExecutor
import com.example.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface WebBridgeDecision {
    data class SubmittedReply(val replyText: String) : WebBridgeDecision
    data object RunLocalToolchain : WebBridgeDecision
}

class AgentController(
    private val db: AppDatabase,
    private val authManager: AuthManager,
    private val conversationManager: ConversationManager,
    private val taskPlanner: TaskPlanner,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val memoryManager: MemoryManager,
    private val permissionManager: PermissionManager,
    private val executionMonitor: ExecutionMonitor,
    private val errorRecoveryManager: ErrorRecoveryManager,
    private val settingsStore: SettingsStore,
    private val webBridgeProvider: WebBridgeProvider,
    private val apiProvider: OpenAiCompatibleModelProvider,
    private val agentProfileStore: AgentProfileStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null
    private var approvalDeferred: CompletableDeferred<Boolean>? = null
    private var webBridgeDeferred: CompletableDeferred<WebBridgeDecision>? = null

    fun startMission(
        conversationId: Long,
        userPrompt: String,
        attachmentUri: String? = null,
        attachmentName: String? = null
    ) {
        stopExecution()
        activeJob = scope.launch {
            runMissionInCurrentScope(conversationId, userPrompt, attachmentUri, attachmentName)
        }
    }

    suspend fun runMissionInCurrentScope(
        conversationId: Long,
        userPrompt: String,
        attachmentUri: String?,
        attachmentName: String?
    ) {
        val baseSettings = settingsStore.settings.first()
        val activeAgent = agentProfileStore.getActiveAgent()
        val allToolNames = toolRegistry.allDefinitions().map { it.name }
        val agentDisabledTools = when {
            activeAgent.disableAllTools -> allToolNames.toSet()
            activeAgent.allowedTools.isNotEmpty() -> {
                val allowed = activeAgent.allowedTools.toSet()
                baseSettings.disabledTools + allToolNames.filterNot { it in allowed }
            }
            else -> baseSettings.disabledTools
        }
        val settings = baseSettings.copy(
            modelId = activeAgent.preferredModelId.ifBlank { baseSettings.modelId },
            disabledTools = agentDisabledTools
        )
        val trimmedPrompt = userPrompt.trim()
        if (trimmedPrompt.isEmpty() && attachmentUri == null) return

        val promptWithAttachment = buildString {
            append(trimmedPrompt.ifEmpty { "Analyze the attached document." })
            if (!attachmentUri.isNullOrBlank()) {
                append("\n[Attachment Name: ${attachmentName ?: "document"}]")
                append("\n[Attachment URI: $attachmentUri]")
            }
        }

        conversationManager.updateTitleIfDefault(conversationId, trimmedPrompt.ifEmpty { attachmentName ?: "Document" })
        conversationManager.appendMessage(
            MessageEntity(
                conversationId = conversationId,
                role = "USER",
                content = trimmedPrompt.ifEmpty { "Analyze attachment: ${attachmentName ?: "file"}" },
                attachmentUri = attachmentUri,
                attachmentName = attachmentName
            )
        )

        executionMonitor.update {
            it.copy(
                isRunning = true,
                phase = AgentPhase.PLANNING,
                activeToolName = null,
                currentStep = 0,
                maxSteps = settings.maxSteps,
                statusDetail = "Running ${activeAgent.handle} · Loading memory...",
                pendingApproval = null,
                pendingWebBridge = null
            )
        }

        val (planId, _) = taskPlanner.createPlanForRequest(conversationId, trimmedPrompt)
        val provider = resolveActiveProvider()

        var planStepCursor = 0
        var webBridgeReply: String? = null
        var runLocalDirect = false
        var finalCompleted = false

        try {
            val systemPrompt = buildSystemPrompt(settings, activeAgent)
            val modelMessages = buildInitialModelMessages(conversationId, promptWithAttachment)

            for (step in 1..settings.maxSteps) {
                executionMonitor.update {
                    it.copy(
                        currentStep = step,
                        phase = AgentPhase.PROCESSING_RESULTS,
                        statusDetail = "Querying ${provider.providerName} (Step $step/${settings.maxSteps})..."
                    )
                }

                val request = ModelRequest(
                    systemPrompt = systemPrompt,
                    messages = modelMessages.toList(),
                    toolsSchemaJson = toolRegistry.toOpenAiToolsSchemaJson(settings.disabledTools),
                    toolCatalogText = toolRegistry.buildPromptToolCatalog(settings.disabledTools),
                    settings = settings,
                    stepIndex = step,
                    isFirstTurn = step == 1,
                    webBridgeReply = webBridgeReply,
                    runLocalToolchainDirect = runLocalDirect
                )
                webBridgeReply = null

                val (response, _) = errorRecoveryManager.executeWithRetry(
                    maxRetries = settings.retryLimit,
                    isRetryable = { resp -> resp is ModelResponse.ErrorResponse && resp.retryable },
                    onRetry = { attempt ->
                        executionMonitor.update {
                            it.copy(statusDetail = "Retrying model request (attempt $attempt/${settings.retryLimit})...")
                        }
                    }
                ) {
                    provider.generateNextStep(request)
                }

                when (response) {
                    is ModelResponse.AwaitingWebBridge -> {
                        executionMonitor.update {
                            it.copy(
                                phase = AgentPhase.AWAITING_WEB_BRIDGE,
                                statusDetail = "Waiting for Web Session reply or local tool trigger...",
                                pendingWebBridge = PendingWebBridge(
                                    preparedPrompt = response.preparedPrompt,
                                    explanation = response.explanation,
                                    hasSuggestedLocalTools = response.suggestedLocalCalls.isNotEmpty()
                                )
                            )
                        }
                        val deferred = CompletableDeferred<WebBridgeDecision>()
                        webBridgeDeferred = deferred
                        val decision = deferred.await()
                        executionMonitor.update { it.copy(pendingWebBridge = null) }

                        when (decision) {
                            is WebBridgeDecision.SubmittedReply -> {
                                webBridgeReply = decision.replyText
                            }
                            WebBridgeDecision.RunLocalToolchain -> {
                                runLocalDirect = true
                                if (response.suggestedLocalCalls.isNotEmpty()) {
                                    executeToolBatch(
                                        conversationId = conversationId,
                                        planId = planId,
                                        step = step,
                                        calls = response.suggestedLocalCalls,
                                        thought = "Executing local Android tool chain.",
                                        settings = settings,
                                        modelMessages = modelMessages
                                    )
                                    taskPlanner.advancePlanStep(planId, planStepCursor++, "Executed local tools")
                                } else {
                                    conversationManager.appendMessage(
                                        MessageEntity(
                                            conversationId = conversationId,
                                            role = "ASSISTANT",
                                            toolName = "Web Session",
                                            content = "No automatic local tool matched this prompt. Open Account & Endpoints to use Embedded Web Session or connect an OpenAI-compatible API endpoint."
                                        )
                                    )
                                    finalCompleted = true
                                    break
                                }
                            }
                        }
                    }

                    is ModelResponse.ToolCallsResponse -> {
                        executeToolBatch(
                            conversationId = conversationId,
                            planId = planId,
                            step = step,
                            calls = response.calls,
                            thought = response.assistantThought,
                            settings = settings,
                            modelMessages = modelMessages
                        )
                        taskPlanner.advancePlanStep(planId, planStepCursor++, "Executed ${response.calls.joinToString { it.toolName }}")
                    }

                    is ModelResponse.TextResponse -> {
                        val content = buildString {
                            if (!response.reasoningSummary.isNullOrBlank()) {
                                appendLine("> **Reasoning Trace:** ${response.reasoningSummary.take(600)}")
                                appendLine()
                            }
                            append(response.content)
                        }
                        val modelTag = if (provider is WebBridgeProvider) {
                            "Web Session"
                        } else {
                            settings.modelId.trim().ifBlank { "Model" }
                        }
                        conversationManager.appendMessage(
                            MessageEntity(
                                conversationId = conversationId,
                                role = "ASSISTANT",
                                toolName = modelTag,
                                content = content
                            )
                        )
                        if (settings.autoSummarizeTasks) {
                            memoryManager.saveMemory(
                                layer = MemoryLayer.TASK,
                                keyName = "Outcome: ${trimmedPrompt.take(36)}",
                                content = response.content.take(280),
                                category = "Task Summary",
                                userApproved = true
                            )
                        }
                        finalCompleted = true
                        break
                    }

                    is ModelResponse.ErrorResponse -> {
                        conversationManager.appendMessage(
                            MessageEntity(
                                conversationId = conversationId,
                                role = "SYSTEM",
                                content = "Execution error: ${response.message}"
                            )
                        )
                        taskPlanner.completePlan(planId, "FAILED")
                        executionMonitor.update {
                            it.copy(
                                isRunning = false,
                                phase = AgentPhase.ERROR,
                                activeToolName = null,
                                statusDetail = response.message
                            )
                        }
                        return
                    }
                }
            }

            if (!finalCompleted) {
                conversationManager.appendMessage(
                    MessageEntity(
                        conversationId = conversationId,
                        role = "SYSTEM",
                        content = "Reached configured max step limit (${settings.maxSteps}). Review Execution Log or increase limit in Settings."
                    )
                )
            }
            taskPlanner.completePlan(planId, "COMPLETED")
            executionMonitor.update {
                it.copy(
                    isRunning = false,
                    phase = AgentPhase.COMPLETED,
                    activeToolName = null,
                    statusDetail = "Task completed"
                )
            }
        } catch (e: CancellationException) {
            taskPlanner.completePlan(planId, "CANCELLED")
            conversationManager.appendMessage(
                MessageEntity(
                    conversationId = conversationId,
                    role = "SYSTEM",
                    content = "Agent execution cancelled by user."
                )
            )
            executionMonitor.update {
                it.copy(
                    isRunning = false,
                    phase = AgentPhase.CANCELLED,
                    activeToolName = null,
                    pendingApproval = null,
                    pendingWebBridge = null,
                    statusDetail = "Cancelled by user"
                )
            }
            throw e
        } catch (t: Throwable) {
            taskPlanner.completePlan(planId, "FAILED")
            val msg = t.message ?: t::class.java.simpleName
            conversationManager.appendMessage(
                MessageEntity(
                    conversationId = conversationId,
                    role = "SYSTEM",
                    content = "Unexpected task error: $msg"
                )
            )
            executionMonitor.update {
                it.copy(
                    isRunning = false,
                    phase = AgentPhase.ERROR,
                    activeToolName = null,
                    pendingApproval = null,
                    pendingWebBridge = null,
                    statusDetail = msg
                )
            }
        }
    }

    private suspend fun executeToolBatch(
        conversationId: Long,
        planId: Long,
        step: Int,
        calls: List<ToolCallRequest>,
        thought: String?,
        settings: AgentSettings,
        modelMessages: MutableList<ModelMessage>
    ) {
        modelMessages.add(
            ModelMessage(
                role = "assistant",
                content = thought.orEmpty(),
                toolCalls = calls
            )
        )

        for (call in calls) {
            val tool = toolRegistry.getTool(call.toolName)
            val risk = tool?.definition?.riskLevel ?: com.example.permissions.ToolRiskLevel.SENSITIVE

            val phase = when (call.toolName) {
                "web_search", "fetch_webpage" -> AgentPhase.RESEARCHING
                "read_document", "analyze_pdf", "list_workspace_files" -> AgentPhase.READING_FILES
                else -> AgentPhase.EXECUTING_TOOL
            }

            val logId = db.executionLogDao().insert(
                ExecutionLogEntity(
                    conversationId = conversationId,
                    stepIndex = step,
                    toolName = call.toolName,
                    argumentsJson = call.argumentsJson,
                    status = "RUNNING",
                    resultSummary = "Running..."
                )
            )

            val decision = permissionManager.evaluateToolExecution(call.toolName, risk, settings)
            val permitted = when (decision) {
                PermissionDecision.Granted -> true
                is PermissionDecision.Denied -> {
                    db.executionLogDao().updateLog(logId, "DENIED", decision.reason, 0L, 0)
                    appendToolMessage(conversationId, call, "Permission denied: ${decision.reason}", null, modelMessages)
                    false
                }
                is PermissionDecision.RequiresApproval -> {
                    executionMonitor.update {
                        it.copy(
                            phase = AgentPhase.AWAITING_APPROVAL,
                            activeToolName = call.toolName,
                            statusDetail = decision.reason,
                            pendingApproval = PendingApproval(call, decision.reason)
                        )
                    }
                    val deferred = CompletableDeferred<Boolean>()
                    approvalDeferred = deferred
                    val approved = deferred.await()
                    executionMonitor.update { it.copy(pendingApproval = null) }
                    if (!approved) {
                        db.executionLogDao().updateLog(logId, "DENIED", "User declined tool execution.", 0L, 0)
                        appendToolMessage(conversationId, call, "Tool execution declined by user.", null, modelMessages)
                    }
                    approved
                }
            }

            if (!permitted) continue

            executionMonitor.update {
                it.copy(
                    phase = phase,
                    activeToolName = call.toolName,
                    statusDetail = "Executing ${call.toolName}..."
                )
            }

            val started = System.currentTimeMillis()
            val (result, retries) = errorRecoveryManager.executeWithRetry(
                maxRetries = settings.retryLimit,
                isRetryable = { r -> !r.success && (r.error?.contains("timeout", true) == true || r.error?.contains("network", true) == true) }
            ) {
                toolExecutor.execute(call, settings)
            }
            val duration = System.currentTimeMillis() - started

            if (result.success) {
                db.executionLogDao().updateLog(logId, "SUCCESS", result.output.take(240), duration, retries)
                if (result.artifactPath != null || result.htmlPreviewContent != null) {
                    executionMonitor.update {
                        it.copy(
                            lastArtifactPath = result.artifactPath ?: it.lastArtifactPath,
                            lastHtmlPreview = result.htmlPreviewContent ?: it.lastHtmlPreview
                        )
                    }
                }
                appendToolMessage(
                    conversationId = conversationId,
                    call = call,
                    content = result.output,
                    artifactPath = result.artifactPath,
                    modelMessages = modelMessages
                )
            } else {
                val errMsg = result.error ?: "Tool failed"
                db.executionLogDao().updateLog(logId, "FAILED", errMsg.take(240), duration, retries)
                appendToolMessage(
                    conversationId = conversationId,
                    call = call,
                    content = "Tool error: $errMsg",
                    artifactPath = null,
                    modelMessages = modelMessages
                )
            }
        }
    }

    private suspend fun appendToolMessage(
        conversationId: Long,
        call: ToolCallRequest,
        content: String,
        artifactPath: String?,
        modelMessages: MutableList<ModelMessage>
    ) {
        conversationManager.appendMessage(
            MessageEntity(
                conversationId = conversationId,
                role = "TOOL",
                content = content,
                toolName = call.toolName,
                toolArgumentsJson = call.argumentsJson,
                toolResultJson = content.take(4000),
                artifactPath = artifactPath
            )
        )
        modelMessages.add(
            ModelMessage(
                role = "tool",
                content = content.take(8000),
                toolCallId = call.id,
                toolName = call.toolName
            )
        )
    }

    private fun resolveActiveProvider(): ModelProvider {
        val mode = authManager.preferredMode.value
        return when (mode) {
            ProviderMode.OFFICIAL_API, ProviderMode.COMPATIBLE_API -> {
                if (authManager.hasApiFallbackConfigured()) apiProvider else webBridgeProvider
            }
            ProviderMode.WEB_SESSION -> webBridgeProvider
        }
    }

    private suspend fun buildSystemPrompt(settings: AgentSettings, agent: AgentProfile): String {
        val memoryBlock = memoryManager.buildSystemMemoryContext(settings.longTermMemoryEnabled)
        val activeModel = settings.modelId.trim().ifBlank { "autonomous Android agent" }
        return buildString {
            appendLine("You are ${agent.name} (${agent.handle}) powered by $activeModel inside Agent Studio.")
            if (agent.systemPrompt.isNotBlank()) {
                appendLine(agent.systemPrompt)
            }
            if (!agent.disableAllTools) {
                appendLine("You have local sandboxed Android shell access via the `run_shell` tool (/system/bin/sh inside AgentWorkspace), alongside file, web research, JavaScript, and Android system tools.")
                appendLine("Android shell tip: SELinux restricts directory listing of /system/bin; use `toybox` to list available utilities or `command -v <name>` to verify binaries.")
            }
            appendLine("Security rule: Never request, read, or emit user credentials, API keys, or Keystore/shared_prefs data.")
            if (memoryBlock.isNotBlank()) {
                appendLine()
                appendLine(memoryBlock)
            }
        }
    }

    private suspend fun buildInitialModelMessages(
        conversationId: Long,
        latestPrompt: String
    ): MutableList<ModelMessage> {
        val history = memoryManager.getShortTermContext(conversationId, limit = 14)
        val mapped = history.dropLast(1).mapNotNull { msg ->
            when (msg.role) {
                "USER" -> ModelMessage(role = "user", content = msg.content)
                "ASSISTANT" -> ModelMessage(role = "assistant", content = msg.content)
                else -> null
            }
        }.toMutableList()
        mapped.add(ModelMessage(role = "user", content = latestPrompt))
        return mapped
    }

    fun resolveApproval(approved: Boolean) {
        approvalDeferred?.let {
            if (!it.isCompleted) it.complete(approved)
        }
        approvalDeferred = null
    }

    fun submitWebBridgeReply(replyText: String) {
        webBridgeDeferred?.let {
            if (!it.isCompleted) it.complete(WebBridgeDecision.SubmittedReply(replyText))
        }
        webBridgeDeferred = null
    }

    fun runWebBridgeLocalToolchain() {
        webBridgeDeferred?.let {
            if (!it.isCompleted) it.complete(WebBridgeDecision.RunLocalToolchain)
        }
        webBridgeDeferred = null
    }

    fun stopExecution() {
        approvalDeferred?.cancel()
        approvalDeferred = null
        webBridgeDeferred?.cancel()
        webBridgeDeferred = null
        apiProvider.cancelActiveRequest()
        webBridgeProvider.cancelActiveRequest()
        activeJob?.cancel()
        activeJob = null
        if (executionMonitor.state.value.isRunning) {
            executionMonitor.update {
                it.copy(
                    isRunning = false,
                    phase = AgentPhase.CANCELLED,
                    activeToolName = null,
                    pendingApproval = null,
                    pendingWebBridge = null,
                    statusDetail = "Stopped by user"
                )
            }
        }
    }
}
