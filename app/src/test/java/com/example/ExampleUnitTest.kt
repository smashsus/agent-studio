package com.example

import com.example.model.WebBridgeProvider
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
        val calls = WebBridgeProvider.parseEmbeddedToolCalls(reply)
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

    @Test
    fun openAiCompatible_normalizesFasfasAndFullEndpointUrls() {
        assertEquals(
            "https://api.fasfas.ai/v1",
            com.example.model.OpenAiCompatibleModelProvider.normalizeBaseUrl("https://api.fasfas.ai/v1/")
        )
        assertEquals(
            "https://api.fasfas.ai/v1",
            com.example.model.OpenAiCompatibleModelProvider.normalizeBaseUrl("https://api.fasfas.ai")
        )
        assertEquals(
            "https://api.fasfas.ai/v1",
            com.example.model.OpenAiCompatibleModelProvider.normalizeBaseUrl("https://api.fasfas.ai/v1/chat/completions")
        )
        assertEquals(
            "https://api.fasfas.ai/rp/v1",
            com.example.model.OpenAiCompatibleModelProvider.normalizeBaseUrl("https://api.fasfas.ai/rp/v1/")
        )
    }

    @Test
    fun openAiCompatible_parsesNullToolCallsAndObjectArguments() {
        val textBody = """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": "Hello from glm-5.3-flash!",
                  "reasoning": "Checked context.",
                  "tool_calls": null
                }
              }]
            }
        """.trimIndent()
        val textResp = com.example.model.OpenAiCompatibleModelProvider.parseResponseBody(textBody)
        assertTrue(textResp is com.example.model.ModelResponse.TextResponse)
        val parsedText = textResp as com.example.model.ModelResponse.TextResponse
        assertEquals("Hello from glm-5.3-flash!", parsedText.content)
        assertEquals("Checked context.", parsedText.reasoningSummary)

        val toolBody = """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [{
                    "id": "call_1",
                    "type": "function",
                    "function": {
                      "name": "web_search",
                      "arguments": {"query": "fasfas api", "max_results": 3}
                    }
                  }]
                }
              }]
            }
        """.trimIndent()
        val toolResp = com.example.model.OpenAiCompatibleModelProvider.parseResponseBody(toolBody)
        assertTrue(toolResp is com.example.model.ModelResponse.ToolCallsResponse)
        val parsedTool = toolResp as com.example.model.ModelResponse.ToolCallsResponse
        assertEquals(1, parsedTool.calls.size)
        assertEquals("web_search", parsedTool.calls.first().toolName)
        assertTrue(parsedTool.calls.first().argumentsJson.contains("fasfas api"))
    }

    @Test
    fun openAiCompatible_parsesModelsListVariants() {
        val openAiJson = """
            {
              "object": "list",
              "data": [
                {"id": "glm-5.3-flash", "object": "model"},
                {"id": "mai-image-2.6", "object": "model"},
                {"id": "glm-5.3-flash", "object": "model"}
              ]
            }
        """.trimIndent()
        assertEquals(
            listOf("glm-5.3-flash", "mai-image-2.6"),
            com.example.model.OpenAiCompatibleModelProvider.parseModelsListBody(openAiJson)
        )

        val arrayJson = """["gpt-4o-mini", "o4-mini"]"""
        assertEquals(
            listOf("gpt-4o-mini", "o4-mini"),
            com.example.model.OpenAiCompatibleModelProvider.parseModelsListBody(arrayJson)
        )

        val modelsObjJson = """{"models": [{"name": "custom-coder"}]}"""
        assertEquals(
            listOf("custom-coder"),
            com.example.model.OpenAiCompatibleModelProvider.parseModelsListBody(modelsObjJson)
        )
    }
}
