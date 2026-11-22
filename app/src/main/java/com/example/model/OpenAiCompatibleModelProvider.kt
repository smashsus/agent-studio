package com.example.model

import com.example.authentication.AuthManager
import com.example.settings.ProviderRegistryStore
import com.example.settings.TokenEstimator
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
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException

/**
 * OpenAI-compatible Chat Completions ModelProvider
 * (e.g. https://api.fasfas.ai/v1/chat/completions, https://api.openai.com/v1/chat/completions).
 * Credentials are fetched internally from AuthManager/KeystoreVault strictly for the Authorization header.
 */
class OpenAiCompatibleModelProvider(
    private val authManager: AuthManager
) : ModelProvider {

    override val providerName: String = "Compatible API Endpoint"
    private val inFlightCalls = ConcurrentHashMap.newKeySet<Call>()

    override suspend fun generateNextStep(request: ModelRequest): ModelResponse =
        withContext(Dispatchers.IO) {
            val apiKey = request.providerApiKeyOverride?.takeIf { it.isNotBlank() }
                ?: authManager.getApiKeyForModelTransport()
                ?: return@withContext ModelResponse.ErrorResponse(
                    "No API key configured in Android Keystore. Open Providers to connect your API key.",
                    retryable = false
                )

            val rawBase = request.providerBaseUrlOverride?.takeIf { it.isNotBlank() }
                ?: request.settings.customBaseUrl.ifBlank { authManager.getSavedApiBaseUrl() }
            val baseUrl = normalizeBaseUrl(rawBase)
            if (baseUrl.isBlank()) {
                return@withContext ModelResponse.ErrorResponse(
                    "Endpoint Base URL is not configured. Open Providers to set an HTTPS Base URL.",
                    retryable = false
                )
            }
            if (!baseUrl.startsWith("https://")) {
                return@withContext ModelResponse.ErrorResponse(
                    "Insecure base URL rejected; HTTPS is required.",
                    retryable = false
                )
            }

            val modelId = request.settings.modelId.trim()
            if (modelId.isEmpty()) {
                return@withContext ModelResponse.ErrorResponse(
                    "Model ID is not configured. Enter a Model ID in Providers or Settings.",
                    retryable = false
                )
            }

            val attachTools = request.toolsSchemaJson.isNotEmpty() && !modelId.contains("reasoner", true)
            val payload = buildJsonObject {
                put("model", modelId)
                put("messages", buildMessagesArray(request.systemPrompt, request.messages))
                if (attachTools) {
                    put("tools", request.toolsSchemaJson)
                    put("tool_choice", "auto")
                }
                put("temperature", 0.3)
            }

            val toolsSchemaText = if (attachTools) request.toolsSchemaJson.toString() else ""
            val resolvedProviderId = request.providerId?.takeIf { it.isNotBlank() } ?: baseUrl
            val resolvedProviderName = request.providerName?.takeIf { it.isNotBlank() }
                ?: ProviderRegistryStore.deriveProviderName(baseUrl)
            val inputMessageTexts = request.messages.map { m ->
                buildString {
                    append(m.content)
                    m.toolCalls?.forEach { tc ->
                        append(' ').append(tc.toolName).append(' ').append(tc.argumentsJson)
                    }
                }
            }

            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val timeoutSec = request.settings.timeoutSeconds.toLong().coerceIn(10L, 120L)
            val callHolder = AtomicReference<Call?>(null)
            try {
                executeRequestWithTlsFallback(httpRequest, timeoutSec, callHolder, inFlightCalls).use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val retryable = response.code == 429 || response.code >= 500
                        return@withContext ModelResponse.ErrorResponse(
                            message = "Model endpoint returned HTTP ${response.code}: ${extractErrorMessage(bodyStr)}",
                            retryable = retryable
                        )
                    }
                    parseResponseBody(
                        body = bodyStr,
                        providerId = resolvedProviderId,
                        providerName = resolvedProviderName,
                        modelId = modelId,
                        systemPrompt = request.systemPrompt,
                        toolsSchemaText = toolsSchemaText,
                        inputMessageTexts = inputMessageTexts
                    )
                }
            } catch (e: Exception) {
                val cancelled = callHolder.get()?.isCanceled() == true
                ModelResponse.ErrorResponse(
                    message = if (cancelled) "Request cancelled." else "Network error: ${e.message ?: "Unknown"}",
                    retryable = !cancelled
                )
            } finally {
                callHolder.getAndSet(null)?.let { inFlightCalls.remove(it) }
            }
        }

    override fun cancelActiveRequest() {
        val snapshot = inFlightCalls.toList()
        inFlightCalls.clear()
        snapshot.forEach { runCatching { it.cancel() } }
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
        const val USER_AGENT = "AgentStudio-Android/1.0"
        private val json = Json { ignoreUnknownKeys = true }

        internal fun executeRequestWithTlsFallback(
            request: Request,
            timeoutSeconds: Long,
            activeCall: AtomicReference<Call?>? = null,
            callRegistry: MutableSet<Call>? = null
        ): okhttp3.Response {
            val primaryClient = OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val primaryCall = primaryClient.newCall(request)
            activeCall?.set(primaryCall)
            callRegistry?.add(primaryCall)
            return try {
                primaryCall.execute()
            } catch (ssl: SSLException) {
                callRegistry?.remove(primaryCall)
                if (primaryCall.isCanceled()) throw ssl
                val http1Client = OkHttpClient.Builder()
                    .protocols(listOf(Protocol.HTTP_1_1))
                    .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                    .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
                val retryCall = http1Client.newCall(request)
                activeCall?.set(retryCall)
                callRegistry?.add(retryCall)
                retryCall.execute()
            }
        }

        suspend fun fetchAvailableModels(
            baseUrl: String,
            apiKey: String,
            timeoutSeconds: Long = 20L
        ): Result<List<String>> = withContext(Dispatchers.IO) {
            val cleanBase = normalizeBaseUrl(baseUrl)
            if (!cleanBase.startsWith("https://")) {
                return@withContext Result.failure(IllegalArgumentException("HTTPS base URL is required."))
            }
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("API key is required to fetch /models."))
            }
            val req = Request.Builder()
                .url("$cleanBase/models")
                .header("Authorization", "Bearer $cleanKey")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            runCatching {
                executeRequestWithTlsFallback(req, timeoutSeconds).use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}: ${extractErrorMessage(bodyStr)}")
                    }
                    parseModelsListBody(bodyStr)
                }
            }
        }

        internal fun parseModelsListBody(body: String): List<String> {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return emptyList()
            return runCatching {
                val elem = json.parseToJsonElement(trimmed)
                val items: JsonArray? = when (elem) {
                    is JsonArray -> elem
                    is JsonObject -> (elem["data"] as? JsonArray)
                        ?: (elem["models"] as? JsonArray)
                        ?: (elem["items"] as? JsonArray)
                    else -> null
                }
                items?.mapNotNull { entry ->
                    when (entry) {
                        is JsonPrimitive -> entry.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                        is JsonObject -> (entry["id"] as? JsonPrimitive)?.contentOrNull?.trim()
                            ?: (entry["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                            ?: (entry["model"] as? JsonPrimitive)?.contentOrNull?.trim()
                        else -> null
                    }
                }?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
            }.getOrDefault(emptyList())
        }

        fun normalizeBaseUrl(rawUrl: String): String {
            var url = rawUrl.trim().trimEnd('/')
            if (url.isEmpty()) return ""
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

        internal fun parseResponseBody(
            body: String,
            providerId: String = "endpoint",
            providerName: String = "Endpoint",
            modelId: String = "model",
            systemPrompt: String = "",
            toolsSchemaText: String = "",
            inputMessageTexts: List<String> = emptyList()
        ): ModelResponse {
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

                val usageObj = root["usage"] as? JsonObject
                val reportedPrompt = (usageObj?.get("prompt_tokens") as? JsonPrimitive)?.longOrNull
                    ?: (usageObj?.get("input_tokens") as? JsonPrimitive)?.longOrNull
                val reportedCompletion = (usageObj?.get("completion_tokens") as? JsonPrimitive)?.longOrNull
                    ?: (usageObj?.get("output_tokens") as? JsonPrimitive)?.longOrNull
                val compDetails = usageObj?.get("completion_tokens_details") as? JsonObject
                val reportedReasoning = (compDetails?.get("reasoning_tokens") as? JsonPrimitive)?.longOrNull

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
                        val toolOutText = buildString {
                            append(content)
                            parsedCalls.forEach { tc ->
                                append(' ').append(tc.toolName).append(' ').append(tc.argumentsJson)
                            }
                        }
                        val tokenUsage = TokenEstimator.buildTurnUsage(
                            providerId = providerId,
                            providerName = providerName,
                            modelId = modelId,
                            systemPrompt = systemPrompt,
                            toolsSchemaJsonText = toolsSchemaText,
                            messagesTexts = inputMessageTexts,
                            thinkingText = reasoning,
                            responseText = toolOutText,
                            reportedPromptTokens = reportedPrompt,
                            reportedCompletionTokens = reportedCompletion,
                            reportedReasoningTokens = reportedReasoning
                        )
                        return ModelResponse.ToolCallsResponse(
                            calls = parsedCalls,
                            assistantThought = content.takeIf { it.isNotBlank() } ?: reasoning,
                            tokenUsage = tokenUsage
                        )
                    }
                }

                // Fallback: check if model emitted JSON tool_call block inside content
                val embeddedCalls = WebBridgeProvider.parseEmbeddedToolCalls(content)
                if (embeddedCalls.isNotEmpty()) {
                    val tokenUsage = TokenEstimator.buildTurnUsage(
                        providerId = providerId,
                        providerName = providerName,
                        modelId = modelId,
                        systemPrompt = systemPrompt,
                        toolsSchemaJsonText = toolsSchemaText,
                        messagesTexts = inputMessageTexts,
                        thinkingText = reasoning,
                        responseText = content,
                        reportedPromptTokens = reportedPrompt,
                        reportedCompletionTokens = reportedCompletion,
                        reportedReasoningTokens = reportedReasoning
                    )
                    return ModelResponse.ToolCallsResponse(
                        calls = embeddedCalls,
                        assistantThought = reasoning,
                        tokenUsage = tokenUsage
                    )
                }

                val finalContent = content.ifBlank { "(Model returned empty response.)" }
                val tokenUsage = TokenEstimator.buildTurnUsage(
                    providerId = providerId,
                    providerName = providerName,
                    modelId = modelId,
                    systemPrompt = systemPrompt,
                    toolsSchemaJsonText = toolsSchemaText,
                    messagesTexts = inputMessageTexts,
                    thinkingText = reasoning,
                    responseText = finalContent,
                    reportedPromptTokens = reportedPrompt,
                    reportedCompletionTokens = reportedCompletion,
                    reportedReasoningTokens = reportedReasoning
                )
                ModelResponse.TextResponse(
                    content = finalContent,
                    reasoningSummary = reasoning,
                    tokenUsage = tokenUsage
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
