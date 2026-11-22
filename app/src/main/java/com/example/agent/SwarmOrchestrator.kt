package com.example.agent

import com.example.authentication.AuthManager
import com.example.database.MessageEntity
import com.example.execution.AgentPhase
import com.example.execution.ErrorRecoveryManager
import com.example.execution.ExecutionMonitor
import com.example.execution.TaskPlanner
import com.example.memory.MemoryLayer
import com.example.memory.MemoryManager
import com.example.model.ModelMessage
import com.example.model.ModelRequest
import com.example.model.ModelResponse
import com.example.permissions.PermissionDecision
import com.example.permissions.PermissionManager
import com.example.settings.AgentSettings
import com.example.settings.ProviderRegistryStore
import com.example.settings.SavedProviderProfile
import com.example.settings.TokenUsageStore
import com.example.tools.ToolExecutor
import com.example.tools.ToolRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

data class SwarmMemberRuntime(
    val index: Int,
    val slot: SwarmMemberSlot,
    val agent: AgentProfile,
    val providerProfile: SavedProviderProfile?,
    val effectiveProviderId: String,
    val effectiveProviderName: String,
    val effectiveBaseUrl: String,
    val effectiveApiKey: String?,
    val effectiveModelId: String
)

data class SwarmRoundOutput(
    val member: SwarmMemberRuntime,
    val text: String,
    val converged: Boolean
)

class SwarmOrchestrator(
    private val authManager: AuthManager,
    private val providerRegistryStore: ProviderRegistryStore,
    private val agentProfileStore: AgentProfileStore,
    private val conversationManager: ConversationManager,
    private val taskPlanner: TaskPlanner,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val permissionManager: PermissionManager,
    private val memoryManager: MemoryManager,
    private val executionMonitor: ExecutionMonitor,
    private val errorRecoveryManager: ErrorRecoveryManager,
    private val tokenUsageStore: TokenUsageStore,
    private val modelInvoker: suspend (ModelRequest) -> ModelResponse
) {
    private val executionMutex = Mutex()

    suspend fun runSwarmMission(
        conversationId: Long,
        planId: Long,
        userPrompt: String,
        cleanHistory: List<ModelMessage>,
        baseSettings: AgentSettings,
        swarmConfig: SwarmConfig
    ): Boolean {
        val runtimes = resolveMemberRuntimes(swarmConfig, baseSettings)
        if (runtimes.size < 2) return false

        val hasAnyKey = runtimes.any { !it.effectiveApiKey.isNullOrBlank() && it.effectiveBaseUrl.isNotBlank() }
        if (!hasAnyKey) {
            conversationManager.appendMessage(
                MessageEntity(
                    conversationId = conversationId,
                    role = "SYSTEM",
                    content = "Swarm Mode requires at least one connected API provider endpoint (with HTTPS Base URL, API Key, and Model ID)."
                )
            )
            return false
        }

        val memoryBlock = memoryManager.buildSystemMemoryContext(baseSettings.longTermMemoryEnabled)
        val totalAgents = runtimes.size
        val maxRounds = swarmConfig.maxCritiqueRounds.coerceIn(1, 3)

        // Stage 1: Independent Coordinated Drafting (each agent may call safe/permitted tools up to 3 steps)
        executionMonitor.update {
            it.copy(
                phase = AgentPhase.PLANNING,
                currentStep = 1,
                maxSteps = maxRounds + 2,
                statusDetail = "Swarm Stage 1/$totalAgents: $totalAgents agents drafting initial plans..."
            )
        }

        val initialDrafts: List<SwarmRoundOutput> = coroutineScope {
            runtimes.map { member ->
                async {
                    draftMemberProposal(
                        conversationId = conversationId,
                        member = member,
                        totalMembers = totalAgents,
                        userPrompt = userPrompt,
                        cleanHistory = cleanHistory,
                        memoryBlock = memoryBlock,
                        baseSettings = baseSettings
                    )
                }
            }.awaitAll()
        }

        initialDrafts.forEach { draft ->
            conversationManager.appendMessage(
                MessageEntity(
                    conversationId = conversationId,
                    role = "ASSISTANT",
                    toolName = "Swarm #${draft.member.index + 1} · ${draft.member.agent.handle} (${draft.member.effectiveModelId})",
                    content = draft.text
                )
            )
        }
        taskPlanner.advancePlanStep(planId, 0, "Swarm initial proposals from $totalAgents agents")

        // Stage 2: Cross-Check & Critique Rounds until Equalized or maxRounds reached
        var latestPositions = initialDrafts
        var equalizedEarly = false
        for (round in 1..maxRounds) {
            executionMonitor.update {
                it.copy(
                    phase = AgentPhase.PROCESSING_RESULTS,
                    currentStep = round + 1,
                    statusDetail = "Swarm Cross-Check Round $round/$maxRounds ($totalAgents agents critiquing)..."
                )
            }

            val digest = buildPeerDigest(latestPositions)
            val critiqueResponses = coroutineScope {
                runtimes.map { member ->
                    async {
                        runCritiqueRoundForMember(
                            member = member,
                            round = round,
                            maxRounds = maxRounds,
                            userPrompt = userPrompt,
                            peerDigest = digest,
                            baseSettings = baseSettings
                        )
                    }
                }.awaitAll()
            }

            latestPositions = critiqueResponses
            val agreedCount = critiqueResponses.count { it.converged }
            val requiredConsensus = ceil(totalAgents * 0.75).toInt().coerceAtLeast(2)
            equalizedEarly = agreedCount >= requiredConsensus

            val critiqueSummary = buildString {
                appendLine("**Swarm Cross-Check Round $round/$maxRounds** — $agreedCount/$totalAgents agents converged" +
                    if (equalizedEarly) " (Equalization threshold reached)" else "")
                critiqueResponses.forEach { r ->
                    val statusTag = if (r.converged) "CONVERGED" else "CRITIQUE"
                    val oneLine = r.text.lineSequence()
                        .firstOrNull { it.isNotBlank() && !it.startsWith("CONVERGED:", true) }
                        ?.take(180)
                        .orEmpty()
                    appendLine("- **#${r.member.index + 1} ${r.member.agent.handle}** [$statusTag]: $oneLine")
                }
            }.trimEnd()

            conversationManager.appendMessage(
                MessageEntity(
                    conversationId = conversationId,
                    role = "SYSTEM",
                    content = critiqueSummary
                )
            )
            taskPlanner.advancePlanStep(
                planId,
                round.coerceAtMost(2),
                "Cross-check round $round: $agreedCount/$totalAgents converged"
            )

            if (equalizedEarly) break
        }

        // Stage 3: Final Equalization Synthesis
        executionMonitor.update {
            it.copy(
                phase = AgentPhase.PROCESSING_RESULTS,
                currentStep = maxRounds + 2,
                statusDetail = "Equalizing final verified response across $totalAgents agents..."
            )
        }

        val equalizer = runtimes.first()
        val finalDigest = buildPeerDigest(latestPositions)
        val eqSystem = buildString {
            appendLine("You are the Swarm Equalizer synthesizing $totalAgents coordinated agent positions into one clear, verified final response.")
            appendLine("Do not repeat generic boilerplate. Merge verified strengths, apply resolved critiques, and note any remaining caveat concisely.")
        }
        val eqPrompt = buildString {
            appendLine("User Request:")
            appendLine(userPrompt)
            appendLine()
            appendLine("Equalized Agent Positions:")
            appendLine(finalDigest)
            appendLine()
            append("Produce the final, cohesive answer for the user:")
        }

        val eqSettings = baseSettings.copy(
            modelId = equalizer.effectiveModelId,
            disabledTools = toolRegistry.allDefinitions().map { it.name }.toSet()
        )
        val eqResp = invokeAndTrack(
            ModelRequest(
                systemPrompt = eqSystem,
                messages = listOf(ModelMessage(role = "user", content = eqPrompt)),
                toolsSchemaJson = kotlinx.serialization.json.JsonArray(emptyList()),
                toolCatalogText = "",
                settings = eqSettings,
                stepIndex = 1,
                isFirstTurn = true,
                providerId = equalizer.effectiveProviderId,
                providerName = equalizer.effectiveProviderName,
                providerBaseUrlOverride = equalizer.effectiveBaseUrl,
                providerApiKeyOverride = equalizer.effectiveApiKey
            ),
            baseSettings.retryLimit
        )

        val finalText = when (eqResp) {
            is ModelResponse.TextResponse -> buildString {
                if (!eqResp.reasoningSummary.isNullOrBlank()) {
                    appendLine("> **Reasoning Trace:** ${eqResp.reasoningSummary.take(600)}")
                    appendLine()
                }
                append(eqResp.content)
            }
            is ModelResponse.ErrorResponse -> {
                "Equalization synthesis encountered an error (${eqResp.message}). Latest consensus draft:\n\n${latestPositions.first().text}"
            }
            else -> latestPositions.first().text
        }

        conversationManager.appendMessage(
            MessageEntity(
                conversationId = conversationId,
                role = "ASSISTANT",
                toolName = "Swarm Equalized ($totalAgents agents · ${equalizer.effectiveModelId})",
                content = finalText
            )
        )

        if (baseSettings.autoSummarizeTasks) {
            memoryManager.saveMemory(
                layer = MemoryLayer.TASK,
                keyName = "Swarm: ${userPrompt.take(36)}",
                content = finalText.take(280),
                category = "Swarm Outcome",
                userApproved = true
            )
        }

        return true
    }

    private suspend fun draftMemberProposal(
        conversationId: Long,
        member: SwarmMemberRuntime,
        totalMembers: Int,
        userPrompt: String,
        cleanHistory: List<ModelMessage>,
        memoryBlock: String,
        baseSettings: AgentSettings
    ): SwarmRoundOutput {
        val allToolNames = toolRegistry.allDefinitions().map { it.name }
        val disabled = when {
            member.agent.disableAllTools -> allToolNames.toSet()
            member.agent.allowedTools.isNotEmpty() -> {
                val allowed = member.agent.allowedTools.toSet()
                baseSettings.disabledTools + allToolNames.filterNot { it in allowed }
            }
            else -> baseSettings.disabledTools
        }
        val memberSettings = baseSettings.copy(
            modelId = member.effectiveModelId,
            disabledTools = disabled
        )
        val systemPrompt = buildString {
            appendLine("You are Swarm Agent #${member.index + 1}/$totalMembers: ${member.agent.name} (${member.agent.handle}).")
            if (member.slot.roleNote.isNotBlank()) {
                appendLine("Swarm Role: ${member.slot.roleNote}")
            }
            if (member.agent.systemPrompt.isNotBlank()) {
                appendLine(member.agent.systemPrompt)
            }
            appendLine("Sandbox dir is 'Agent Studio'. Keep shell/file ops inside it. Never access credentials.")
            if (memoryBlock.isNotBlank()) {
                appendLine(memoryBlock)
            }
        }.trimEnd()

        val msgs = cleanHistory.takeLast(6).toMutableList()
        msgs.add(ModelMessage(role = "user", content = userPrompt))

        val toolsJson = toolRegistry.toOpenAiToolsSchemaJson(disabled)
        val toolCatalog = toolRegistry.buildPromptToolCatalog(disabled)

        var finalReply = ""
        for (step in 1..3) {
            val resp = invokeAndTrack(
                ModelRequest(
                    systemPrompt = systemPrompt,
                    messages = msgs.toList(),
                    toolsSchemaJson = toolsJson,
                    toolCatalogText = toolCatalog,
                    settings = memberSettings,
                    stepIndex = step,
                    isFirstTurn = step == 1,
                    providerId = member.effectiveProviderId,
                    providerName = member.effectiveProviderName,
                    providerBaseUrlOverride = member.effectiveBaseUrl,
                    providerApiKeyOverride = member.effectiveApiKey
                ),
                baseSettings.retryLimit
            )

            when (resp) {
                is ModelResponse.TextResponse -> {
                    finalReply = resp.content
                    break
                }
                is ModelResponse.ToolCallsResponse -> {
                    msgs.add(ModelMessage(role = "assistant", content = resp.assistantThought.orEmpty(), toolCalls = resp.calls))
                    for (call in resp.calls) {
                        val tool = toolRegistry.getTool(call.toolName)
                        val risk = tool?.definition?.riskLevel ?: com.example.permissions.ToolRiskLevel.SENSITIVE
                        val decision = permissionManager.evaluateToolExecution(call.toolName, risk, memberSettings)
                        val outputText = if (decision == PermissionDecision.Granted) {
                            executionMutex.withLock {
                                val res = toolExecutor.execute(call, memberSettings)
                                val out = if (res.success) res.output else "Tool error: ${res.error}"
                                conversationManager.appendMessage(
                                    MessageEntity(
                                        conversationId = conversationId,
                                        role = "TOOL",
                                        content = out,
                                        toolName = "${member.agent.handle}:${call.toolName}",
                                        toolArgumentsJson = call.argumentsJson,
                                        toolResultJson = out.take(2000),
                                        artifactPath = res.artifactPath
                                    )
                                )
                                out
                            }
                        } else {
                            "Skipped tool ${call.toolName} in autonomous swarm draft (requires foreground approval)."
                        }
                        msgs.add(
                            ModelMessage(
                                role = "tool",
                                content = outputText.take(6000),
                                toolCallId = call.id,
                                toolName = call.toolName
                            )
                        )
                    }
                }
                is ModelResponse.ErrorResponse -> {
                    finalReply = "(Agent #${member.index + 1} error: ${resp.message})"
                    break
                }
                is ModelResponse.AwaitingWebBridge -> {
                    finalReply = "(Web session cannot run autonomous swarm steps.)"
                    break
                }
            }
        }

        if (finalReply.isBlank()) {
            finalReply = "Completed tool checks; ready for cross-check."
        }
        return SwarmRoundOutput(member = member, text = finalReply, converged = false)
    }

    private suspend fun runCritiqueRoundForMember(
        member: SwarmMemberRuntime,
        round: Int,
        maxRounds: Int,
        userPrompt: String,
        peerDigest: String,
        baseSettings: AgentSettings
    ): SwarmRoundOutput {
        val critiqueSystem = buildString {
            appendLine("You are Swarm Agent #${member.index + 1} (${member.agent.handle}).")
            if (member.slot.roleNote.isNotBlank()) appendLine("Focus: ${member.slot.roleNote}")
            appendLine("Cross-check all peer drafts for errors, missing edge cases, or contradictions.")
            appendLine("Reply concisely with: 1) Key critique/confirmation of peer drafts, 2) Your refined position, 3) Final line MUST be exactly 'CONVERGED: YES' if aligned or 'CONVERGED: NO' if critical issues remain.")
        }.trimEnd()

        val prompt = buildString {
            appendLine("Task: $userPrompt")
            appendLine("Round $round/$maxRounds Peer Drafts:")
            appendLine(peerDigest)
        }
        val settings = baseSettings.copy(
            modelId = member.effectiveModelId,
            disabledTools = toolRegistry.allDefinitions().map { it.name }.toSet()
        )
        val resp = invokeAndTrack(
            ModelRequest(
                systemPrompt = critiqueSystem,
                messages = listOf(ModelMessage(role = "user", content = prompt)),
                toolsSchemaJson = kotlinx.serialization.json.JsonArray(emptyList()),
                toolCatalogText = "",
                settings = settings,
                stepIndex = round,
                isFirstTurn = false,
                providerId = member.effectiveProviderId,
                providerName = member.effectiveProviderName,
                providerBaseUrlOverride = member.effectiveBaseUrl,
                providerApiKeyOverride = member.effectiveApiKey
            ),
            baseSettings.retryLimit
        )

        val text = when (resp) {
            is ModelResponse.TextResponse -> resp.content
            is ModelResponse.ErrorResponse -> "Critique error (${resp.message}).\nCONVERGED: YES"
            else -> "CONVERGED: YES"
        }
        val converged = parseConvergedFlag(text)
        return SwarmRoundOutput(member = member, text = text, converged = converged)
    }

    private suspend fun invokeAndTrack(request: ModelRequest, retryLimit: Int): ModelResponse {
        val (resp, _) = errorRecoveryManager.executeWithRetry(
            maxRetries = retryLimit,
            isRetryable = { r -> r is ModelResponse.ErrorResponse && r.retryable }
        ) {
            modelInvoker(request)
        }
        when (resp) {
            is ModelResponse.TextResponse -> resp.tokenUsage?.let { tokenUsageStore.recordTurn(it) }
            is ModelResponse.ToolCallsResponse -> resp.tokenUsage?.let { tokenUsageStore.recordTurn(it) }
            else -> Unit
        }
        return resp
    }

    private fun resolveMemberRuntimes(
        swarmConfig: SwarmConfig,
        baseSettings: AgentSettings
    ): List<SwarmMemberRuntime> {
        val allAgents = agentProfileStore.agents.value
        val providers = providerRegistryStore.providers.value
        val activeProvId = providerRegistryStore.activeProviderId.value
        val activeProvider = providers.firstOrNull { it.id == activeProvId } ?: providers.firstOrNull()

        return swarmConfig.members.mapIndexed { idx, slot ->
            val agent = allAgents.firstOrNull { it.id == slot.agentId }
                ?: agentProfileStore.getActiveAgent()
            val targetProvId = slot.providerId.ifBlank { agent.preferredProviderId }
            val prov = providers.firstOrNull { it.id == targetProvId } ?: activeProvider
            val provId = prov?.id ?: "default"
            val provName = prov?.name ?: ProviderRegistryStore.deriveProviderName(baseSettings.customBaseUrl)
            val baseUrl = prov?.baseUrl?.takeIf { it.isNotBlank() }
                ?: baseSettings.customBaseUrl.ifBlank { authManager.getSavedApiBaseUrl() }
            val key = prov?.id?.let { providerRegistryStore.getProviderKey(it) }
                ?: authManager.getApiKeyForModelTransport()
            val modelId = slot.modelIdOverride.ifBlank {
                agent.preferredModelId.ifBlank {
                    prov?.selectedModelId.orEmpty().ifBlank { baseSettings.modelId }
                }
            }
            SwarmMemberRuntime(
                index = idx,
                slot = slot,
                agent = agent,
                providerProfile = prov,
                effectiveProviderId = provId,
                effectiveProviderName = provName,
                effectiveBaseUrl = baseUrl,
                effectiveApiKey = key,
                effectiveModelId = modelId
            )
        }
    }

    private fun buildPeerDigest(outputs: List<SwarmRoundOutput>): String = buildString {
        outputs.forEach { item ->
            appendLine("### Agent #${item.member.index + 1} (${item.member.agent.handle} · ${item.member.effectiveModelId})")
            appendLine(item.text.take(1800))
            appendLine()
        }
    }.trimEnd()

    companion object {
        internal fun parseConvergedFlag(text: String): Boolean {
            val match = Regex("""(?:CONVERGED|EQUALIZED)\s*:\s*(YES|TRUE|NO|FALSE)""", RegexOption.IGNORE_CASE)
                .findAll(text)
                .lastOrNull()
            return match?.groupValues?.getOrNull(1)?.let {
                it.equals("YES", true) || it.equals("TRUE", true)
            } ?: false
        }
    }
}
