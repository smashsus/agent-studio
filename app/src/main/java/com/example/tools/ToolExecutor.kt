package com.example.tools

import com.example.settings.AgentSettings
import kotlinx.coroutines.withTimeoutOrNull

class ToolExecutor(private val registry: ToolRegistry) {
    suspend fun execute(
        request: ToolCallRequest,
        settings: AgentSettings
    ): ToolResult {
        val tool = registry.getTool(request.toolName)
            ?: return ToolResult(false, "", error = "Tool '${request.toolName}' is not registered.")

        val validated = registry.validateArguments(request.toolName, request.argumentsJson)
        if (validated.isFailure) {
            return ToolResult(
                false,
                "",
                error = validated.exceptionOrNull()?.message ?: "Argument validation failed."
            )
        }

        val timeoutMs = settings.timeoutSeconds.coerceIn(10, 120) * 1000L
        return withTimeoutOrNull(timeoutMs) {
            runCatching {
                tool.execute(validated.getOrThrow(), settings)
            }.getOrElse { err ->
                ToolResult(false, "", error = err.message ?: "Unexpected tool error.")
            }
        } ?: ToolResult(
            false,
            "",
            error = "Tool '${request.toolName}' timed out after ${settings.timeoutSeconds}s."
        )
    }
}
