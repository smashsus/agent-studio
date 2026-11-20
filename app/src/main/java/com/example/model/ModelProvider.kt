package com.example.model

import com.example.settings.AgentSettings
import com.example.tools.ToolCallRequest
import kotlinx.serialization.json.JsonArray

data class ModelMessage(
    val role: String, // system, user, assistant, tool
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<ToolCallRequest>? = null
)

data class ModelRequest(
    val systemPrompt: String,
    val messages: List<ModelMessage>,
    val toolsSchemaJson: JsonArray,
    val toolCatalogText: String,
    val settings: AgentSettings,
    val stepIndex: Int,
    val isFirstTurn: Boolean,
    val webBridgeReply: String? = null,
    val runLocalToolchainDirect: Boolean = false
)

sealed interface ModelResponse {
    data class TextResponse(
        val content: String,
        val reasoningSummary: String? = null
    ) : ModelResponse

    data class ToolCallsResponse(
        val calls: List<ToolCallRequest>,
        val assistantThought: String? = null
    ) : ModelResponse

    data class AwaitingWebBridge(
        val preparedPrompt: String,
        val explanation: String,
        val suggestedLocalCalls: List<ToolCallRequest> = emptyList()
    ) : ModelResponse

    data class ErrorResponse(
        val message: String,
        val retryable: Boolean = false
    ) : ModelResponse
}

interface ModelProvider {
    val providerName: String
    suspend fun generateNextStep(request: ModelRequest): ModelResponse
    fun cancelActiveRequest()
}
