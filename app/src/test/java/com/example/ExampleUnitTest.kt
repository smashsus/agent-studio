package com.example

import com.example.model.DeepSeekWebBridgeProvider
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import com.example.tools.AgentTool
import com.example.tools.ToolDefinition
import com.example.tools.ToolParameter
import com.example.tools.ToolRegistry
import com.example.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun parseEmbeddedToolCalls_extractsJsonCodeFence() {
        val reply = """
            Let me search for that.
            ```json
            {"tool": "web_search", "arguments": {"query": "quantum computing 2026", "max_results": 5}}
            ```
        """.trimIndent()
        val calls = DeepSeekWebBridgeProvider.parseEmbeddedToolCalls(reply)
        assertEquals(1, calls.size)
        assertEquals("web_search", calls.first().toolName)
        assertTrue(calls.first().argumentsJson.contains("quantum computing 2026"))
    }

    @Test
    fun toolRegistry_validatesRequiredArgumentsAndRejectsUnknownTools() {
        val dummyTool = object : AgentTool {
            override val definition = ToolDefinition(
                name = "sample_tool",
                category = "Test",
                description = "Sample",
                riskLevel = ToolRiskLevel.SAFE,
                parameters = listOf(ToolParameter("query", "string", "Search term", required = true))
            )
            override suspend fun execute(args: JsonObject, settings: AgentSettings) = ToolResult(true, "ok")
        }
        val registry = ToolRegistry(listOf(dummyTool))
        assertTrue(registry.validateArguments("unknown_tool", "{}").isFailure)
        assertTrue(registry.validateArguments("sample_tool", "{}").isFailure)
        assertTrue(registry.validateArguments("sample_tool", """{"query":"hello"}""").isSuccess)
    }
}
