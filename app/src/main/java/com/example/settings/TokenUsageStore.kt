package com.example.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.roundToLong

@Serializable
data class TurnTokenUsage(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val inputInstructionsTokens: Long,
    val inputMessagesTokens: Long,
    val outputThinkingTokens: Long,
    val outputResponseTokens: Long,
    val isApiReported: Boolean = false
) {
    val totalInputTokens: Long get() = inputInstructionsTokens + inputMessagesTokens
    val totalOutputTokens: Long get() = outputThinkingTokens + outputResponseTokens
    val totalTokens: Long get() = totalInputTokens + totalOutputTokens
}

@Serializable
data class TokenBucket(
    val label: String = "Overall",
    val subtitle: String = "",
    val requestCount: Long = 0L,
    val exactReportedCount: Long = 0L,
    val inputInstructionsTokens: Long = 0L,
    val inputMessagesTokens: Long = 0L,
    val outputThinkingTokens: Long = 0L,
    val outputResponseTokens: Long = 0L
) {
    val totalInputTokens: Long get() = inputInstructionsTokens + inputMessagesTokens
    val totalOutputTokens: Long get() = outputThinkingTokens + outputResponseTokens
    val totalTokens: Long get() = totalInputTokens + totalOutputTokens

    fun add(turn: TurnTokenUsage): TokenBucket = copy(
        requestCount = requestCount + 1L,
        exactReportedCount = exactReportedCount + if (turn.isApiReported) 1L else 0L,
        inputInstructionsTokens = inputInstructionsTokens + turn.inputInstructionsTokens,
        inputMessagesTokens = inputMessagesTokens + turn.inputMessagesTokens,
        outputThinkingTokens = outputThinkingTokens + turn.outputThinkingTokens,
        outputResponseTokens = outputResponseTokens + turn.outputResponseTokens
    )
}

@Serializable
data class TokenUsageSnapshot(
    val overall: TokenBucket = TokenBucket(label = "Overall Profile", subtitle = "All providers & models"),
    val byProvider: Map<String, TokenBucket> = emptyMap(),
    val byModel: Map<String, TokenBucket> = emptyMap(),
    val updatedAt: Long = 0L
)

object TokenEstimator {
    /**
     * Lightweight, deterministic token estimator (~3.7 chars/token on mixed code + prose).
     * Reconciles cleanly with API-reported `usage` totals whenever the endpoint provides them.
     */
    fun estimateTextTokens(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val trimmed = text.trim()
        val charBased = ceil(trimmed.length / 3.7).toLong()
        val wordCount = trimmed.split(Regex("\\s+")).count { it.isNotEmpty() }
        val wordBased = ceil(wordCount * 1.32).toLong()
        return maxOf(1L, ((charBased + wordBased) / 2.0).roundToLong())
    }

    fun buildTurnUsage(
        providerId: String,
        providerName: String,
        modelId: String,
        systemPrompt: String,
        toolsSchemaJsonText: String,
        messagesTexts: List<String>,
        thinkingText: String?,
        responseText: String,
        reportedPromptTokens: Long? = null,
        reportedCompletionTokens: Long? = null,
        reportedReasoningTokens: Long? = null
    ): TurnTokenUsage {
        val estInstr = estimateTextTokens(systemPrompt) + estimateTextTokens(toolsSchemaJsonText)
        val estMsgs = messagesTexts.sumOf { estimateTextTokens(it) + 4L }
        val estInputSum = (estInstr + estMsgs).coerceAtLeast(1L)

        val (finalInstr, finalMsgs) = if (reportedPromptTokens != null && reportedPromptTokens > 0L) {
            val ratio = estInstr.toDouble() / estInputSum.toDouble()
            val apportionedInstr = (reportedPromptTokens * ratio).roundToLong().coerceIn(0L, reportedPromptTokens)
            val apportionedMsgs = (reportedPromptTokens - apportionedInstr).coerceAtLeast(0L)
            apportionedInstr to apportionedMsgs
        } else {
            estInstr to estMsgs
        }

        val estThinking = estimateTextTokens(thinkingText)
        val estReply = estimateTextTokens(responseText)
        val (finalThinking, finalReply) = when {
            reportedCompletionTokens != null && reportedCompletionTokens > 0L -> {
                val thinkingVal = when {
                    reportedReasoningTokens != null && reportedReasoningTokens >= 0L ->
                        reportedReasoningTokens.coerceAtMost(reportedCompletionTokens)
                    estThinking > 0L -> {
                        val outSum = (estThinking + estReply).coerceAtLeast(1L)
                        (reportedCompletionTokens * (estThinking.toDouble() / outSum.toDouble()))
                            .roundToLong()
                            .coerceIn(0L, reportedCompletionTokens)
                    }
                    else -> 0L
                }
                val replyVal = (reportedCompletionTokens - thinkingVal).coerceAtLeast(0L)
                thinkingVal to replyVal
            }
            else -> estThinking to estReply
        }

        return TurnTokenUsage(
            providerId = providerId.ifBlank { "default" },
            providerName = providerName.ifBlank { "Endpoint" },
            modelId = modelId.ifBlank { "unknown-model" },
            inputInstructionsTokens = finalInstr,
            inputMessagesTokens = finalMsgs,
            outputThinkingTokens = finalThinking,
            outputResponseTokens = finalReply,
            isApiReported = reportedPromptTokens != null || reportedCompletionTokens != null
        )
    }
}

class TokenUsageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<TokenUsageSnapshot> = _snapshot.asStateFlow()

    private fun load(): TokenUsageSnapshot {
        val raw = prefs.getString(KEY_SNAPSHOT_JSON, null) ?: return TokenUsageSnapshot()
        return runCatching { json.decodeFromString<TokenUsageSnapshot>(raw) }
            .getOrDefault(TokenUsageSnapshot())
    }

    fun recordTurn(turn: TurnTokenUsage) {
        synchronized(lock) {
            val current = _snapshot.value
            val provKey = turn.providerId.ifBlank { turn.providerName }
            val existingProv = current.byProvider[provKey]
                ?: TokenBucket(label = turn.providerName, subtitle = provKey)
            val modelKey = "${turn.providerName} :: ${turn.modelId}"
            val existingModel = current.byModel[modelKey]
                ?: TokenBucket(label = turn.modelId, subtitle = turn.providerName)

            val updated = current.copy(
                overall = current.overall.add(turn),
                byProvider = current.byProvider + (provKey to existingProv.add(turn)),
                byModel = current.byModel + (modelKey to existingModel.add(turn)),
                updatedAt = System.currentTimeMillis()
            )
            _snapshot.value = updated
            prefs.edit().putString(KEY_SNAPSHOT_JSON, json.encodeToString(updated)).apply()
        }
    }

    fun reset() {
        synchronized(lock) {
            val empty = TokenUsageSnapshot()
            _snapshot.value = empty
            prefs.edit().remove(KEY_SNAPSHOT_JSON).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "agent_studio_token_usage"
        private const val KEY_SNAPSHOT_JSON = "usage_snapshot_json"
    }
}
