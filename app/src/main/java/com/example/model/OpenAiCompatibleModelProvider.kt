package com.example.model

import com.example.authentication.AuthManager
import com.example.tools.ToolCallRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Priority 2 / Custom Compatible ModelProvider using official OpenAI-compatible Chat Completions
 * (e.g. https://api.deepseek.com/chat/completions).
 * Credentials are fetched internally from AuthManager/KeystoreVault strictly for the Authorization header.
 */
class OpenAiCompatibleModelProvider(
    private val authManager: AuthManager
) : ModelProvider {

    override val providerName: String = "Official DeepSeek / Compatible API"
    private val activeCall = AtomicReference<Call?>(null)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateNextStep(request: ModelRequest): ModelResponse =
        withContext(Dispatchers.IO) {
            val apiKey = authManager.getApiKeyForModelTransport()
                ?: return@withContext ModelResponse.ErrorResponse(
                    "No API key configured in Android Keystore. Connect Priority 2 API fallback in Account tab or use DeepSeek Web Session.",
                    retryable = false
                )

            val baseUrl = request.settings.customBaseUrl.trim().trimEnd('/').ifEmpty {
                authManager.getSavedApiBaseUrl()
            }
            if (!baseUrl.startsWith("https://")) {
                return@withContext ModelResponse.ErrorResponse(
                    "Insecure base URL rejected; HTTPS is required.",
                    retryable = false
                )
            }

            val payload = buildJsonObject {
                put("model", request.settings.modelId)
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
                    parseChatCompletionResponse(bodyStr)
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
                    put("content", msg.content)
                    if (msg.role == "tool" && msg.toolCallId != null) {
                        put("tool_call_id", msg.toolCallId)
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

    private fun parseChatCompletionResponse(body: String): ModelResponse {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return ModelResponse.ErrorResponse("Model returned no choices.")
            val message = choice["message"]?.jsonObject
                ?: return ModelResponse.ErrorResponse("Model choice missing message.")

            val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val reasoning = message["reasoning_content"]?.jsonPrimitive?.contentOrNull
            val rawToolCalls = message["tool_calls"]?.jsonArray

            if (rawToolCalls != null && rawToolCalls.isNotEmpty()) {
                val parsedCalls = rawToolCalls.mapIndexedNotNull { idx, elem ->
                    val obj = elem.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "call_$idx"
                    val fn = obj["function"]?.jsonObject ?: return@mapIndexedNotNull null
                    val name = fn["name"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
                    val args = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                    ToolCallRequest(id = id, toolName = name, argumentsJson = args)
                }
                if (parsedCalls.isNotEmpty()) {
                    return ModelResponse.ToolCallsResponse(
                        calls = parsedCalls,
                        assistantThought = content.takeIf { it.isNotBlank() } ?: reasoning
                    )
                }
            }

            // Fallback: check if reasoning model emitted JSON tool_call block inside content
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

    private fun extractErrorMessage(body: String): String {
        return runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: body.take(240)
        }.getOrDefault(body.take(240))
    }
}
