package com.example.settings

import android.content.Context
import com.example.authentication.SecretVault
import com.example.model.OpenAiCompatibleModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class SavedProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val selectedModelId: String,
    val modelIds: List<String> = emptyList(),
    val lastVerifiedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModelId: String,
    val suggestedModels: List<String>
)

class ProviderRegistryStore(
    context: Context,
    private val vault: SecretVault
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _providers = MutableStateFlow<List<SavedProviderProfile>>(emptyList())
    val providers: StateFlow<List<SavedProviderProfile>> = _providers.asStateFlow()

    private val _activeProviderId = MutableStateFlow<String?>(null)
    val activeProviderId: StateFlow<String?> = _activeProviderId.asStateFlow()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString(KEY_PROVIDERS_JSON, null)
        val parsed = if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<SavedProviderProfile>>(raw) }.getOrDefault(emptyList())
        } else emptyList()
        _providers.value = parsed
        val savedActive = prefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
        _activeProviderId.value = savedActive?.takeIf { id -> parsed.any { it.id == id } }
            ?: parsed.firstOrNull()?.id
    }

    private fun persist(list: List<SavedProviderProfile>, activeId: String?) {
        _providers.value = list
        _activeProviderId.value = activeId
        prefs.edit()
            .putString(KEY_PROVIDERS_JSON, json.encodeToString(list))
            .putString(KEY_ACTIVE_PROVIDER_ID, activeId)
            .apply()
    }

    fun getProviderKey(providerId: String): String? = vault.getSecret(vaultKeyFor(providerId))

    fun hasProviderKey(providerId: String): Boolean = vault.hasSecret(vaultKeyFor(providerId))

    fun saveOrUpdateProvider(
        existingId: String?,
        name: String,
        baseUrl: String,
        apiKey: String?,
        selectedModelId: String,
        modelIds: List<String>,
        verifiedNow: Boolean = false,
        setActive: Boolean = true
    ): SavedProviderProfile {
        val cleanBase = OpenAiCompatibleModelProvider.normalizeBaseUrl(baseUrl)
        val cleanModel = selectedModelId.trim()
        val mergedModels = (listOf(cleanModel) + modelIds.map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()

        val id = existingId?.takeIf { it.isNotBlank() }
            ?: "prov_${UUID.randomUUID().toString().take(8)}"

        val existing = _providers.value.firstOrNull { it.id == id }
        val resolvedName = name.trim().ifBlank { deriveProviderName(cleanBase) }
        val now = System.currentTimeMillis()
        val profile = SavedProviderProfile(
            id = id,
            name = resolvedName,
            baseUrl = cleanBase,
            selectedModelId = cleanModel.ifBlank { mergedModels.firstOrNull().orEmpty() },
            modelIds = mergedModels,
            lastVerifiedAt = if (verifiedNow) now else existing?.lastVerifiedAt,
            updatedAt = now
        )

        val cleanKey = apiKey?.trim().orEmpty()
        if (cleanKey.isNotEmpty()) {
            vault.putSecret(vaultKeyFor(id), cleanKey)
        }

        val updatedList = _providers.value.toMutableList().apply {
            val idx = indexOfFirst { it.id == id }
            if (idx >= 0) set(idx, profile) else add(0, profile)
        }
        val nextActiveId = if (setActive) profile.id else _activeProviderId.value ?: profile.id
        persist(updatedList, nextActiveId)
        return profile
    }

    fun setActiveProvider(providerId: String): SavedProviderProfile? {
        val target = _providers.value.firstOrNull { it.id == providerId } ?: return null
        persist(_providers.value, target.id)
        return target
    }

    fun updateProviderModels(
        providerId: String,
        fetchedModels: List<String>,
        selectedModelId: String? = null
    ): SavedProviderProfile? {
        val existing = _providers.value.firstOrNull { it.id == providerId } ?: return null
        val nextSelected = selectedModelId?.trim()?.takeIf { it.isNotBlank() }
            ?: existing.selectedModelId.takeIf { it.isNotBlank() }
            ?: fetchedModels.firstOrNull().orEmpty()
        val merged = (fetchedModels.map { it.trim() } + listOf(nextSelected) + existing.modelIds)
            .filter { it.isNotBlank() }
            .distinct()
        val updated = existing.copy(
            selectedModelId = nextSelected,
            modelIds = merged,
            lastVerifiedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val updatedList = _providers.value.map { if (it.id == providerId) updated else it }
        persist(updatedList, _activeProviderId.value)
        return updated
    }

    fun addCustomModelId(providerId: String, modelId: String, selectImmediately: Boolean = true): SavedProviderProfile? {
        val clean = modelId.trim()
        if (clean.isEmpty()) return null
        val existing = _providers.value.firstOrNull { it.id == providerId } ?: return null
        val merged = (listOf(clean) + existing.modelIds).distinct()
        val updated = existing.copy(
            selectedModelId = if (selectImmediately) clean else existing.selectedModelId,
            modelIds = merged,
            updatedAt = System.currentTimeMillis()
        )
        val updatedList = _providers.value.map { if (it.id == providerId) updated else it }
        persist(updatedList, _activeProviderId.value)
        return updated
    }

    fun removeModelId(providerId: String, modelId: String): SavedProviderProfile? {
        val existing = _providers.value.firstOrNull { it.id == providerId } ?: return null
        val remaining = existing.modelIds.filterNot { it == modelId }
        val nextSelected = if (existing.selectedModelId == modelId) {
            remaining.firstOrNull().orEmpty()
        } else {
            existing.selectedModelId
        }
        val updated = existing.copy(
            selectedModelId = nextSelected,
            modelIds = remaining,
            updatedAt = System.currentTimeMillis()
        )
        val updatedList = _providers.value.map { if (it.id == providerId) updated else it }
        persist(updatedList, _activeProviderId.value)
        return updated
    }

    fun deleteProvider(providerId: String): SavedProviderProfile? {
        vault.removeSecret(vaultKeyFor(providerId))
        val remaining = _providers.value.filterNot { it.id == providerId }
        val nextActive = if (_activeProviderId.value == providerId) remaining.firstOrNull()?.id else _activeProviderId.value
        persist(remaining, nextActive)
        return remaining.firstOrNull { it.id == nextActive }
    }

    fun ensureMigratedFromLegacy(legacyBaseUrl: String, legacyModelId: String, legacyKey: String?) {
        if (_providers.value.isNotEmpty()) return
        val cleanBase = OpenAiCompatibleModelProvider.normalizeBaseUrl(legacyBaseUrl)
        if (cleanBase.isBlank() && legacyKey.isNullOrBlank()) return
        saveOrUpdateProvider(
            existingId = null,
            name = deriveProviderName(cleanBase),
            baseUrl = cleanBase.ifBlank { "https://api.fasfas.ai/v1" },
            apiKey = legacyKey,
            selectedModelId = legacyModelId.ifBlank { "glm-5.3-flash" },
            modelIds = listOfNotNull(legacyModelId.takeIf { it.isNotBlank() }),
            verifiedNow = !legacyKey.isNullOrBlank(),
            setActive = true
        )
    }

    fun clearAll() {
        _providers.value.forEach { vault.removeSecret(vaultKeyFor(it.id)) }
        prefs.edit().clear().apply()
        _providers.value = emptyList()
        _activeProviderId.value = null
    }

    companion object {
        private const val PREFS_NAME = "agent_studio_providers"
        private const val KEY_PROVIDERS_JSON = "providers_json"
        private const val KEY_ACTIVE_PROVIDER_ID = "active_provider_id"

        private fun vaultKeyFor(providerId: String): String = "provider_key_$providerId"

        fun deriveProviderName(baseUrl: String): String {
            return when {
                baseUrl.contains("fasfas.ai/rp", ignoreCase = true) -> "Fasfas RP"
                baseUrl.contains("fasfas.ai", ignoreCase = true) -> "Fasfas AI"
                baseUrl.contains("openrouter.ai", ignoreCase = true) -> "OpenRouter"
                baseUrl.contains("api.openai.com", ignoreCase = true) -> "OpenAI"
                baseUrl.contains("api.groq.com", ignoreCase = true) -> "Groq"
                baseUrl.contains("together.xyz", ignoreCase = true) -> "Together AI"
                baseUrl.contains("deepinfra.com", ignoreCase = true) -> "DeepInfra"
                baseUrl.contains("anthropic.com", ignoreCase = true) -> "Anthropic Compat"
                baseUrl.contains("generativelanguage.googleapis.com", ignoreCase = true) -> "Gemini OpenAI"
                else -> runCatching { java.net.URI(baseUrl).host ?: "Custom Endpoint" }.getOrDefault("Custom Endpoint")
            }
        }

        val builtInPresets = listOf(
            ProviderPreset(
                id = "fasfas",
                name = "Fasfas AI",
                baseUrl = "https://api.fasfas.ai/v1",
                defaultModelId = "glm-5.3-flash",
                suggestedModels = listOf("glm-5.3-flash", "mai-image-2.6-flash", "mai-image-2.6")
            ),
            ProviderPreset(
                id = "fasfas_rp",
                name = "Fasfas RP",
                baseUrl = "https://api.fasfas.ai/rp/v1",
                defaultModelId = "glm-5.3-flash",
                suggestedModels = listOf("glm-5.3-flash")
            ),
            ProviderPreset(
                id = "openrouter",
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                defaultModelId = "openai/gpt-4o-mini",
                suggestedModels = listOf("openai/gpt-4o-mini", "anthropic/claude-sonnet-4", "google/gemini-2.5-flash")
            ),
            ProviderPreset(
                id = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                defaultModelId = "gpt-4o-mini",
                suggestedModels = listOf("gpt-4o-mini", "gpt-4.1", "o4-mini")
            ),
            ProviderPreset(
                id = "groq",
                name = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                defaultModelId = "llama-3.3-70b-versatile",
                suggestedModels = listOf("llama-3.3-70b-versatile", "qwen-qwq-32b")
            ),
            ProviderPreset(
                id = "together",
                name = "Together AI",
                baseUrl = "https://api.together.xyz/v1",
                defaultModelId = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                suggestedModels = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-Coder-32B-Instruct")
            ),
            ProviderPreset(
                id = "gemini_compat",
                name = "Gemini Compat",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                defaultModelId = "gemini-2.5-flash",
                suggestedModels = listOf("gemini-2.5-flash", "gemini-2.5-pro")
            )
        )
    }
}
