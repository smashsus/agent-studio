package com.example.model

import com.example.tools.ToolCallRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Embedded Web Session Bridge Provider.
 *
 * Design honesty & security:
 * Logging into a web portal provides browser-based interaction inside the embedded WebView
 * and does NOT grant programmatic API access. This provider never scrapes private endpoints or
 * fakes API calls. Instead, it:
 * 1) Parses structured tool calls from a user-supplied web session reply, OR
 * 2) Suggests / executes deterministic local tool chains (shell, search, webpage extraction,
 *    PDF/file inspection, JS sandbox, Android Intents, notifications) and prepares the structured
 *    context prompt for the user's embedded web session.
 */
class WebBridgeProvider : ModelProvider {

    override val providerName: String = "Embedded Web Session (Bridge)"

    override suspend fun generateNextStep(request: ModelRequest): ModelResponse {
        val reply = request.webBridgeReply?.trim()
        if (!reply.isNullOrEmpty()) {
            val calls = parseEmbeddedToolCalls(reply)
            if (calls.isNotEmpty()) {
                return ModelResponse.ToolCallsResponse(
                    calls = calls,
                    assistantThought = "Parsed ${calls.size} structured tool call(s) from web session response."
                )
            }
            return ModelResponse.TextResponse(content = reply)
        }

        val lastMsg = request.messages.lastOrNull()
        val lastUserPrompt = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()

        if (lastMsg?.role == "tool" && request.runLocalToolchainDirect) {
            val toolOutputs = request.messages.takeLastWhile { it.role == "tool" }
            val summary = buildLocalCompletionSummary(lastUserPrompt, toolOutputs)
            return ModelResponse.TextResponse(content = summary)
        }

        val suggestedCalls = inferLocalToolCalls(lastUserPrompt, request.settings.modelId)

        if (request.runLocalToolchainDirect && suggestedCalls.isNotEmpty() && request.isFirstTurn) {
            return ModelResponse.ToolCallsResponse(
                calls = suggestedCalls,
                assistantThought = "Executing local autonomous tool chain in Web Session Bridge mode."
            )
        }

        val preparedPrompt = buildStructuredWebBridgePrompt(request)
        return ModelResponse.AwaitingWebBridge(
            preparedPrompt = preparedPrompt,
            explanation = "Embedded Web Session mode uses your in-app browser tab (no private endpoint scraping). Copy this structured prompt into your web session and paste the reply below, or tap 'Run Local Tools' to execute on-device tools directly.",
            suggestedLocalCalls = suggestedCalls
        )
    }

    override fun cancelActiveRequest() {
        // Coroutine job cancellation stops bridge wait cleanly.
    }

    private fun buildStructuredWebBridgePrompt(request: ModelRequest): String {
        val userGoal = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val recentTools = request.messages.filter { it.role == "tool" }.takeLast(4)
        val activeLabel = request.settings.modelId.ifBlank { "Agent Studio" }
        return buildString {
            appendLine("[$activeLabel • Structured Tool Request]")
            appendLine("Goal: $userGoal")
            if (request.systemPrompt.isNotBlank()) {
                appendLine()
                appendLine("Context & Memory:")
                appendLine(request.systemPrompt.take(1200))
            }
            if (recentTools.isNotEmpty()) {
                appendLine()
                appendLine("Tool Results Collected So Far:")
                recentTools.forEach { t ->
                    appendLine("- ${t.toolName ?: "tool"}: ${t.content.take(1500)}")
                }
            }
            appendLine()
            appendLine("Available Local Android Tools:")
            appendLine(request.toolCatalogText)
            appendLine()
            appendLine("If you need a tool executed, reply with JSON inside ```json ... ```:")
            appendLine("""{"tool":"web_search","arguments":{"query":"..."}}""")
            appendLine("Otherwise provide your final response directly.")
        }
    }

    private fun buildLocalCompletionSummary(
        userGoal: String,
        toolOutputs: List<ModelMessage>
    ): String = buildString {
        appendLine("### Local Tool Execution Summary")
        appendLine("**Goal:** $userGoal")
        appendLine()
        toolOutputs.forEach { msg ->
            appendLine("#### Tool: `${msg.toolName ?: "tool"}`")
            appendLine(msg.content)
            appendLine()
        }
        appendLine("---")
        appendLine("_Tip: Open **Account & Endpoints** to synthesize these findings inside your embedded web session, or connect any OpenAI-compatible API endpoint for fully automatic multi-step reasoning._")
    }

    private fun inferLocalToolCalls(prompt: String, modelId: String): List<ToolCallRequest> {
        val lower = prompt.lowercase()
        val calls = mutableListOf<ToolCallRequest>()

        val uriMatch = Regex("""\[Attachment URI:\s*(content://[^\]]+)\]""").find(prompt)
        if (uriMatch != null) {
            val uri = uriMatch.groupValues[1].trim()
            val toolName = if (lower.contains("pdf") || uri.contains("pdf", true)) "analyze_pdf" else "read_document"
            calls.add(
                ToolCallRequest(
                    id = "local_attach_1",
                    toolName = toolName,
                    argumentsJson = buildJsonObject { put("uri_or_path", uri) }.toString()
                )
            )
        }

        val urlMatch = Regex("""https://[^\s\]]+""").find(prompt)
        if (urlMatch != null && (lower.contains("fetch") || lower.contains("read") || lower.contains("summarize") || lower.contains("extract"))) {
            calls.add(
                ToolCallRequest(
                    id = "local_fetch_1",
                    toolName = "fetch_webpage",
                    argumentsJson = buildJsonObject { put("url", urlMatch.value) }.toString()
                )
            )
        } else if (lower.contains("open ") && urlMatch != null) {
            calls.add(
                ToolCallRequest(
                    id = "local_open_url",
                    toolName = "open_url",
                    argumentsJson = buildJsonObject { put("url", urlMatch.value) }.toString()
                )
            )
        }

        if (calls.isEmpty() && (lower.contains("research") || lower.contains("search") || lower.contains("latest") || lower.contains("find"))) {
            val cleanQuery = prompt
                .replace(Regex("^(research|search for|search|find|look up)\\s+", RegexOption.IGNORE_CASE), "")
                .take(140)
            calls.add(
                ToolCallRequest(
                    id = "local_search_1",
                    toolName = "web_search",
                    argumentsJson = buildJsonObject {
                        put("query", cleanQuery)
                        put("max_results", 5)
                    }.toString()
                )
            )
        }

        val jsBlock = Regex("""```(?:js|javascript)\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE).find(prompt)
        if (jsBlock != null) {
            calls.add(
                ToolCallRequest(
                    id = "local_js_1",
                    toolName = "run_javascript",
                    argumentsJson = buildJsonObject { put("code", jsBlock.groupValues[1].trim()) }.toString()
                )
            )
        }

        val shBlock = Regex("""```(?:sh|bash|shell)\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE).find(prompt)
        val explicitShell = Regex("""^(?:run\s+shell|shell)\s*:\s*(.+)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(prompt.trim())
        val shellCmd = shBlock?.groupValues?.get(1)?.trim() ?: explicitShell?.groupValues?.get(1)?.trim()
        if (!shellCmd.isNullOrBlank()) {
            calls.add(
                ToolCallRequest(
                    id = "local_shell_1",
                    toolName = "run_shell",
                    argumentsJson = buildJsonObject { put("command", shellCmd) }.toString()
                )
            )
        }

        if (lower.contains("remind") || lower.contains("notify") || lower.contains("notification")) {
            val reminderTitle = modelId.ifBlank { "Agent" }
            calls.add(
                ToolCallRequest(
                    id = "local_notif_1",
                    toolName = "create_notification",
                    argumentsJson = buildJsonObject {
                        put("title", "$reminderTitle Reminder")
                        put("message", prompt.take(120))
                        if (lower.contains("in ")) put("delay_seconds", 10)
                    }.toString()
                )
            )
        }

        if (lower.contains("read screen") || lower.contains("screen content") || lower.contains("what is on my screen")) {
            calls.add(
                ToolCallRequest(
                    id = "local_a11y_1",
                    toolName = "accessibility_interact",
                    argumentsJson = buildJsonObject { put("action", "read_screen") }.toString()
                )
            )
        }

        if (lower.contains("list files") || lower.contains("workspace files")) {
            calls.add(
                ToolCallRequest(
                    id = "local_files_1",
                    toolName = "list_workspace_files",
                    argumentsJson = "{}"
                )
            )
        }

        val openAppMatch = Regex("""(?:open|launch)\s+app\s+([a-zA-Z0-9._ -]{2,30})""", RegexOption.IGNORE_CASE).find(prompt)
        if (openAppMatch != null) {
            calls.add(
                ToolCallRequest(
                    id = "local_app_1",
                    toolName = "open_app",
                    argumentsJson = buildJsonObject { put("app_query", openAppMatch.groupValues[1].trim()) }.toString()
                )
            )
        }

        return calls
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseEmbeddedToolCalls(text: String): List<ToolCallRequest> {
            val candidates = mutableListOf<String>()
            Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
                .findAll(text)
                .forEach { candidates.add(it.groupValues[1].trim()) }
            if (candidates.isEmpty() && text.trim().startsWith("{")) {
                candidates.add(text.trim())
            }

            val results = mutableListOf<ToolCallRequest>()
            candidates.forEachIndexed { idx, raw ->
                runCatching {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    val array = obj["tool_calls"]?.jsonArray
                    if (array != null) {
                        array.forEachIndexed { subIdx, el ->
                            parseSingleToolObject(el.jsonObject, "bridge_${idx}_$subIdx")?.let { results.add(it) }
                        }
                    } else {
                        parseSingleToolObject(obj, "bridge_$idx")?.let { results.add(it) }
                    }
                }
            }
            return results
        }

        private fun parseSingleToolObject(obj: JsonObject, fallbackId: String): ToolCallRequest? {
            val name = obj["tool"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull
                ?: return null
            val argsObj = obj["arguments"] ?: obj["args"] ?: buildJsonObject { }
            return ToolCallRequest(
                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: fallbackId,
                toolName = name,
                argumentsJson = argsObj.toString()
            )
        }
    }
}
