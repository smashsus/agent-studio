package com.example.model

import com.example.authentication.AuthManager
import com.example.tools.ToolCallRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAI-compatible Chat Completions ModelProvider
 * (e.g. https://api.fasfas.ai/v1/chat/completions, https://api.openai.com/v1/chat/completions).
 * Credentials are fetched internally from AuthManager/KeystoreVault strictly for the Authorization header.
 */
class OpenAiCompatibleModelProvider(
    private val authManager: AuthManager
) : ModelProvider {

    override val providerName: String = "Compatible API Endpoint"
    private val activeCall = AtomicReference<Call?>(null)

    override suspend fun generateNextStep(request: ModelRequest): ModelResponse =
        withContext(Dispatchers.IO) {
            val apiKey = authManager.getApiKeyForModelTransport()
                ?: return@withContext ModelResponse.ErrorResponse(
                    "No API key configured in Android Keystore. Open Account & Endpoints to connect your API key.",
                    retryable = false
                )

            val rawBase = request.settings.customBaseUrl.ifBlank {
                authManager.getSavedApiBaseUrl()
            }
            val baseUrl = normalizeBaseUrl(rawBase)
            if (!baseUrl.startsWith("https://")) {
                return@withContext ModelResponse.ErrorResponse(
                    "Insecure base URL rejected; HTTPS is required.",
                    retryable = false
                )
            }

            val payload = buildJsonObject {
                put("model", request.settings.modelId.trim())
                put("messages", buildMessagesArray(request.systemPrompt, request.messages))
                if (request.toolsSchemaJson.isNotEmpty() && !request.settings.modelId.contains("reasoner", true)) {
                    put("tools", request.toolsSchemaJson)
                    put("tool_choice", "auto")
                }
                put("temperature", 0.3)
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(request.settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(request.settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(request.settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val call = client.newCall(httpRequest)
            activeCall.set(call)

            try {
                call.execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val retryable = response.code == 429 || response.code >= 500
                        return@withContext ModelResponse.ErrorResponse(
                            message = "Model endpoint returned HTTP ${response.code}: ${extractErrorMessage(bodyStr)}",
                            retryable = retryable
                        )
                    }
                    parseResponseBody(bodyStr)
                }
            } catch (e: Exception) {
                val cancelled = call.isCanceled()
                ModelResponse.ErrorResponse(
                    message = if (cancelled) "Request cancelled." else "Network error: ${e.message ?: "Unknown"}",
                    retryable = !cancelled
                )
            } finally {
                activeCall.compareAndSet(call, null)
            }
        }

    override fun cancelActiveRequest() {
        activeCall.getAndSet(null)?.cancel()
    }

    private fun buildMessagesArray(systemPrompt: String, messages: List<ModelMessage>): JsonArray {
        return buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            })
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role)
                    if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                        if (msg.content.isNotBlank()) {
                            put("content", msg.content)
                        } else {
                            put("content", JsonNull)
                        }
                    } else {
                        put("content", msg.content)
                    }
                    if (msg.role == "tool") {
                        msg.toolCallId?.let { put("tool_call_id", it) }
                        msg.toolName?.let { put("name", it) }
                    }
                    if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                        put("tool_calls", buildJsonArray {
                            msg.toolCalls.forEach { tc ->
                                add(buildJsonObject {
                                    put("id", tc.id)
                                    put("type", "function")
                                    put("function", buildJsonObject {
                                        put("name", tc.toolName)
                                        put("arguments", tc.argumentsJson)
                                    })
                                })
                            }
                        })
                    }
                })
            }
        }
    }

    companion object {
        const val USER_AGENT = "AgentHarness-Android/1.0"
        private val json = Json { ignoreUnknownKeys = true }

        fun normalizeBaseUrl(rawUrl: String): String {
            var url = rawUrl.trim().trimEnd('/')
            if (url.isEmpty()) return "https://api.deepseek.com"
            val suffixes = listOf("/chat/completions", "/responses", "/messages", "/models")
            for (suffix in suffixes) {
                if (url.endsWith(suffix, ignoreCase = true)) {
                    url = url.dropLast(suffix.length).trimEnd('/')
                }
            }
            if (url.equals("https://api.fasfas.ai", ignoreCase = true) ||
                url.equals("https://api.openai.com", ignoreCase = true)
            ) {
                url = "$url/v1"
            }
            return url
        }

        internal fun parseResponseBody(body: String): ModelResponse {
            return runCatching {
                val root = json.parseToJsonElement(body) as? JsonObject
                    ?: return ModelResponse.ErrorResponse("Model returned non-JSON response.")

                val errorElem = root["error"]
                if (errorElem != null && errorElem !is JsonNull) {
                    val errText = extractErrorMessage(body)
                    if (errText.isNotBlank()) {
                        return ModelResponse.ErrorResponse("Model endpoint error: $errText")
                    }
                }

                val choices = root["choices"] as? JsonArray
                val choice = choices?.firstOrNull() as? JsonObject
                    ?: return ModelResponse.ErrorResponse("Model returned no choices.")
                val message = (choice["message"] as? JsonObject)
                    ?: (choice["delta"] as? JsonObject)
                    ?: return ModelResponse.ErrorResponse("Model choice missing message.")

                val content = extractTextContent(message["content"])
                val reasoning = extractTextContent(message["reasoning_content"])
                    .ifBlank { extractTextContent(message["reasoning"]) }
                    .ifBlank { extractTextContent(message["thinking"]) }
                    .ifBlank { null }

                val rawToolCalls = message["tool_calls"] as? JsonArray
                if (!rawToolCalls.isNullOrEmpty()) {
                    val parsedCalls = rawToolCalls.mapIndexedNotNull { idx, elem ->
                        val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
                        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: "call_$idx"
                        val fn = obj["function"] as? JsonObject ?: return@mapIndexedNotNull null
                        val name = (fn["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
                        val args = when (val argsElem = fn["arguments"]) {
                            is JsonPrimitive -> argsElem.contentOrNull ?: "{}"
                            is JsonObject -> argsElem.toString()
                            else -> "{}"
                        }
                        ToolCallRequest(id = id, toolName = name, argumentsJson = args)
                    }
                    if (parsedCalls.isNotEmpty()) {
                        return ModelResponse.ToolCallsResponse(
                            calls = parsedCalls,
                            assistantThought = content.takeIf { it.isNotBlank() } ?: reasoning
                        )
                    }
                }

                // Fallback: check if model emitted JSON tool_call block inside content
                val embeddedCalls = DeepSeekWebBridgeProvider.parseEmbeddedToolCalls(content)
                if (embeddedCalls.isNotEmpty()) {
                    return ModelResponse.ToolCallsResponse(
                        calls = embeddedCalls,
                        assistantThought = reasoning
                    )
                }

                ModelResponse.TextResponse(
                    content = content.ifBlank { "(Model returned empty response.)" },
                    reasoningSummary = reasoning
                )
            }.getOrElse { err ->
                ModelResponse.ErrorResponse("Failed to parse model response: ${err.message}")
            }
        }

        private fun extractTextContent(elem: JsonElement?): String {
            return when (elem) {
                null, is JsonNull -> ""
                is JsonPrimitive -> elem.contentOrNull.orEmpty()
                is JsonArray -> elem.mapNotNull { item ->
                    when (item) {
                        is JsonPrimitive -> item.contentOrNull
                        is JsonObject -> (item["text"] as? JsonPrimitive)?.contentOrNull
                            ?: (item["content"] as? JsonPrimitive)?.contentOrNull
                        else -> null
                    }
                }.joinToString("\n")
                is JsonObject -> (elem["text"] as? JsonPrimitive)?.contentOrNull
                    ?: (elem["content"] as? JsonPrimitive)?.contentOrNull
                    ?: ""
            }
        }

        internal fun extractErrorMessage(body: String): String {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return "Empty response body"
            return runCatching {
                val obj = json.parseToJsonElement(trimmed) as? JsonObject ?: return trimmed.take(240)
                when (val err = obj["error"]) {
                    is JsonObject -> (err["message"] as? JsonPrimitive)?.contentOrNull
                        ?: (err["error"] as? JsonPrimitive)?.contentOrNull
                        ?: err.toString().take(240)
                    is JsonPrimitive -> err.contentOrNull ?: trimmed.take(240)
                    else -> (obj["message"] as? JsonPrimitive)?.contentOrNull
                        ?: (obj["detail"] as? JsonPrimitive)?.contentOrNull
                        ?: trimmed.take(240)
                }
            }.getOrDefault(trimmed.take(240))
        }
    }
}
