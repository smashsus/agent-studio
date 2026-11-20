package com.example.tools

import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.serialization.json.JsonObject

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

data class ToolDefinition(
    val name: String,
    val category: String,
    val description: String,
    val riskLevel: ToolRiskLevel,
    val parameters: List<ToolParameter>
)

data class ToolCallRequest(
    val id: String,
    val toolName: String,
    val argumentsJson: String
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val artifactPath: String? = null,
    val artifactMimeType: String? = null,
    val htmlPreviewContent: String? = null,
    val error: String? = null
)

interface AgentTool {
    val definition: ToolDefinition
    suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult
}
