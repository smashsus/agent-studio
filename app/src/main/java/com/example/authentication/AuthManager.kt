package com.example.authentication

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI

/**
 * Manages provider-neutral authentication and endpoint configuration:
 * - Embedded Web Session: user-chosen HTTPS web portal inside an isolated WebView.
 * - API Endpoint: any OpenAI-compatible HTTPS base URL + Bearer API key encrypted in KeystoreVault.
 *
 * Security & policy invariants:
 * - Never collects or stores user passwords.
 * - Never extracts session cookies from other apps or scrapes private web endpoints.
 * - Never exposes credentials to the model prompt, logs, or tool execution layer.
 */
class AuthManager(
    context: Context,
    private val vault: KeystoreVault
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("deepseek_auth_meta", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.NotAuthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _preferredMode = MutableStateFlow(loadPreferredMode())
    val preferredMode: StateFlow<ProviderMode> = _preferredMode.asStateFlow()

    init {
        refreshSessionState()
    }

    private fun loadPreferredMode(): ProviderMode {
        val raw = prefs.getString(KEY_PREFERRED_MODE, ProviderMode.COMPATIBLE_API.name)
        return runCatching { ProviderMode.valueOf(raw ?: ProviderMode.COMPATIBLE_API.name) }
            .getOrDefault(ProviderMode.COMPATIBLE_API)
    }

    fun selectProviderMode(mode: ProviderMode) {
        prefs.edit().putString(KEY_PREFERRED_MODE, mode.name).apply()
        _preferredMode.value = mode
        refreshSessionState()
    }

    fun beginWebAuthentication(portalUrl: String? = null) {
        val cleanUrl = portalUrl?.trim()?.takeIf { it.startsWith("https://") }
        if (cleanUrl != null) {
            prefs.edit().putString(KEY_WEB_PORTAL_URL, cleanUrl).apply()
        }
        val host = cleanUrl?.let { extractHost(it) } ?: "your web portal"
        _authState.value = AuthState.Authenticating(
            mode = ProviderMode.WEB_SESSION,
            message = "Sign in on $host inside the embedded browser."
        )
    }

    /**
     * Called when the embedded WebView finishes navigating to any HTTPS web portal.
     * Observes domain cookie presence via Android CookieManager without exposing cookie values.
     */
    fun updateWebSessionFromEmbeddedBrowser(currentUrl: String?, pageTitle: String?) {
        val url = currentUrl?.trim() ?: return
        if (!url.startsWith("https://")) return
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return
        val origin = "https://$host"

        val hasSessionCookies = runCatching {
            val cookieStr = CookieManager.getInstance().getCookie(origin)
            !cookieStr.isNullOrBlank() && cookieStr.contains("=")
        }.getOrDefault(false)

        val isLoginRoute = url.contains("/sign_in", ignoreCase = true) ||
            url.contains("/signin", ignoreCase = true) ||
            url.contains("/login", ignoreCase = true) ||
            url.contains("/auth", ignoreCase = true)

        if (!isLoginRoute && hasSessionCookies) {
            val now = System.currentTimeMillis()
            val expires = now + WEB_SESSION_WINDOW_MS
            val label = pageTitle?.takeIf {
                it.isNotBlank() &&
                    !it.contains("Sign in", true) &&
                    !it.contains("Log in", true)
            } ?: host
            prefs.edit()
                .putBoolean(KEY_WEB_CONNECTED, true)
                .putString(KEY_WEB_LABEL, label)
                .putString(KEY_WEB_PORTAL_URL, url)
                .putLong(KEY_WEB_CONNECTED_AT, now)
                .putLong(KEY_WEB_EXPIRES_AT, expires)
                .apply()
            if (_preferredMode.value == ProviderMode.WEB_SESSION) {
                _authState.value = AuthState.Authenticated(
                    mode = ProviderMode.WEB_SESSION,
                    accountLabel = "Web Session ($host)",
                    connectedAt = now,
                    expiresAt = expires,
                    grantsProgrammaticApi = false
                )
            }
        }
    }

    fun markWebSessionConfirmedByUser(
        accountHint: String? = null,
        portalUrl: String? = null
    ) {
        val cleanUrl = portalUrl?.trim()?.takeIf { it.startsWith("https://") }
        if (cleanUrl != null) {
            prefs.edit().putString(KEY_WEB_PORTAL_URL, cleanUrl).apply()
        }
        val host = (cleanUrl ?: getSavedWebPortalUrl()).takeIf { it.isNotBlank() }?.let { extractHost(it) }
        val resolvedLabel = accountHint?.takeIf { it.isNotBlank() }
            ?: host?.let { "Web Session ($it)" }
            ?: "Embedded Web Session"

        val now = System.currentTimeMillis()
        val expires = now + WEB_SESSION_WINDOW_MS
        prefs.edit()
            .putBoolean(KEY_WEB_CONNECTED, true)
            .putString(KEY_WEB_LABEL, resolvedLabel)
            .putLong(KEY_WEB_CONNECTED_AT, now)
            .putLong(KEY_WEB_EXPIRES_AT, expires)
            .putString(KEY_PREFERRED_MODE, ProviderMode.WEB_SESSION.name)
            .apply()
        _preferredMode.value = ProviderMode.WEB_SESSION
        _authState.value = AuthState.Authenticated(
            mode = ProviderMode.WEB_SESSION,
            accountLabel = resolvedLabel,
            connectedAt = now,
            expiresAt = expires,
            grantsProgrammaticApi = false
        )
    }

    fun expireSessionForReauth() {
        val label = prefs.getString(KEY_WEB_LABEL, "Web Session") ?: "Web Session"
        prefs.edit().putBoolean(KEY_WEB_CONNECTED, false).apply()
        _authState.value = AuthState.SessionExpired(
            lastAccountLabel = label,
            mode = _preferredMode.value
        )
    }

    suspend fun saveAndVerifyApiFallback(
        apiKey: String,
        baseUrl: String,
        @Suppress("UNUSED_PARAMETER") isCustomCompatible: Boolean = true
    ): Result<String> {
        return verifyAndFetchModels(apiKey, baseUrl).map { it.first }
    }

    suspend fun verifyAndFetchModels(
        apiKey: String,
        baseUrl: String
    ): Result<Pair<String, List<String>>> = withContext(Dispatchers.IO) {
        val mode = ProviderMode.COMPATIBLE_API
        val effectiveKey = apiKey.trim().ifBlank { vault.getSecret(VAULT_API_KEY).orEmpty() }
        val cleanBase = com.example.model.OpenAiCompatibleModelProvider.normalizeBaseUrl(baseUrl)
        if (effectiveKey.isBlank()) {
            _authState.value = AuthState.AuthenticationFailed("API key cannot be empty.", mode)
            return@withContext Result.failure(IllegalArgumentException("API key is empty"))
        }
        if (!cleanBase.startsWith("https://")) {
            _authState.value = AuthState.AuthenticationFailed(
                "Only HTTPS endpoints are permitted for credential transmission.",
                mode
            )
            return@withContext Result.failure(IllegalArgumentException("HTTPS required"))
        }

        _authState.value = AuthState.Authenticating(mode, "Verifying endpoint & fetching models...")
        val request = Request.Builder()
            .url("$cleanBase/models")
            .header("Authorization", "Bearer $effectiveKey")
            .header("Accept", "application/json")
            .header("User-Agent", com.example.model.OpenAiCompatibleModelProvider.USER_AGENT)
            .get()
            .build()

        val displayHost = extractHost(cleanBase) ?: cleanBase
        val responseResult = runCatching {
            com.example.model.OpenAiCompatibleModelProvider.executeRequestWithTlsFallback(request, 15L)
        }
        responseResult.fold(
            onSuccess = { resp ->
                resp.use {
                    val bodySnippet = runCatching { it.body?.string().orEmpty() }.getOrDefault("")
                    if (it.isSuccessful || it.code == 404 || it.code == 405) {
                        val discoveredModels = if (it.isSuccessful) {
                            com.example.model.OpenAiCompatibleModelProvider.parseModelsListBody(bodySnippet)
                        } else emptyList()
                        vault.putSecret(VAULT_API_KEY, effectiveKey)
                        val now = System.currentTimeMillis()
                        prefs.edit()
                            .putString(KEY_API_BASE, cleanBase)
                            .putLong(KEY_API_CONNECTED_AT, now)
                            .putString(KEY_PREFERRED_MODE, mode.name)
                            .apply()
                        _preferredMode.value = mode
                        _authState.value = AuthState.Authenticated(
                            mode = mode,
                            accountLabel = displayHost,
                            connectedAt = now,
                            expiresAt = null,
                            grantsProgrammaticApi = true
                        )
                        Result.success(cleanBase to discoveredModels)
                    } else if (it.code == 401 || it.code == 403) {
                        val detail = com.example.model.OpenAiCompatibleModelProvider.extractErrorMessage(bodySnippet)
                        _authState.value = AuthState.AuthenticationFailed(
                            "Endpoint rejected credentials (HTTP ${it.code}): $detail",
                            mode
                        )
                        Result.failure(SecurityException("HTTP ${it.code}: $detail"))
                    } else {
                        vault.putSecret(VAULT_API_KEY, effectiveKey)
                        val now = System.currentTimeMillis()
                        prefs.edit()
                            .putString(KEY_API_BASE, cleanBase)
                            .putLong(KEY_API_CONNECTED_AT, now)
                            .putString(KEY_PREFERRED_MODE, mode.name)
                            .apply()
                        _preferredMode.value = mode
                        _authState.value = AuthState.Authenticated(
                            mode = mode,
                            accountLabel = "$displayHost (Saved)",
                            connectedAt = now,
                            expiresAt = null,
                            grantsProgrammaticApi = true
                        )
                        Result.success(cleanBase to emptyList())
                    }
                }
            },
            onFailure = { err ->
                _authState.value = AuthState.AuthenticationFailed(
                    "Connection check failed: ${err.message ?: "Network error"}",
                    mode
                )
                Result.failure(err)
            }
        )
    }

    fun activateProviderCredential(apiKey: String?, baseUrl: String) {
        val cleanBase = com.example.model.OpenAiCompatibleModelProvider.normalizeBaseUrl(baseUrl)
        val cleanKey = apiKey?.trim().orEmpty()
        if (cleanKey.isNotEmpty()) {
            vault.putSecret(VAULT_API_KEY, cleanKey)
        }
        if (cleanBase.startsWith("https://")) {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString(KEY_API_BASE, cleanBase)
                .putLong(KEY_API_CONNECTED_AT, now)
                .putString(KEY_PREFERRED_MODE, ProviderMode.COMPATIBLE_API.name)
                .apply()
            _preferredMode.value = ProviderMode.COMPATIBLE_API
            refreshSessionState()
        }
    }

    fun syncApiBaseUrlIfConfigured(baseUrl: String) {
        val cleanBase = com.example.model.OpenAiCompatibleModelProvider.normalizeBaseUrl(baseUrl)
        if (cleanBase.startsWith("https://")) {
            prefs.edit().putString(KEY_API_BASE, cleanBase).apply()
            if (_preferredMode.value != ProviderMode.WEB_SESSION) {
                refreshSessionState()
            }
        }
    }

    /**
     * Internal-only accessor used exclusively by OpenAiCompatibleModelProvider on HTTP requests.
     * Never exposed to tools, logs, Room, or model prompts.
     */
    internal fun getApiKeyForModelTransport(): String? = vault.getSecret(VAULT_API_KEY)

    fun hasApiFallbackConfigured(): Boolean = vault.hasSecret(VAULT_API_KEY)

    fun getSavedApiBaseUrl(): String =
        prefs.getString(KEY_API_BASE, "").orEmpty()

    fun getSavedWebPortalUrl(): String =
        prefs.getString(KEY_WEB_PORTAL_URL, "").orEmpty()

    fun refreshSessionState() {
        val mode = _preferredMode.value
        val now = System.currentTimeMillis()
        when (mode) {
            ProviderMode.WEB_SESSION -> {
                val connected = prefs.getBoolean(KEY_WEB_CONNECTED, false)
                val label = prefs.getString(KEY_WEB_LABEL, "Embedded Web Session") ?: "Embedded Web Session"
                val connectedAt = prefs.getLong(KEY_WEB_CONNECTED_AT, 0L)
                val expiresAt = prefs.getLong(KEY_WEB_EXPIRES_AT, 0L)
                _authState.value = when {
                    !connected -> AuthState.NotAuthenticated
                    expiresAt > 0L && now > expiresAt -> AuthState.SessionExpired(label, ProviderMode.WEB_SESSION)
                    else -> AuthState.Authenticated(
                        mode = ProviderMode.WEB_SESSION,
                        accountLabel = label,
                        connectedAt = connectedAt,
                        expiresAt = expiresAt,
                        grantsProgrammaticApi = false
                    )
                }
            }
            ProviderMode.OFFICIAL_API, ProviderMode.COMPATIBLE_API -> {
                if (vault.hasSecret(VAULT_API_KEY)) {
                    val base = getSavedApiBaseUrl()
                    val displayHost = extractHost(base) ?: base.ifBlank { "Configured API" }
                    val connectedAt = prefs.getLong(KEY_API_CONNECTED_AT, now)
                    _authState.value = AuthState.Authenticated(
                        mode = ProviderMode.COMPATIBLE_API,
                        accountLabel = displayHost,
                        connectedAt = connectedAt,
                        expiresAt = null,
                        grantsProgrammaticApi = true
                    )
                } else {
                    _authState.value = AuthState.NotAuthenticated
                }
            }
        }
    }

    fun logoutAll() {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
        }
        vault.clearAll()
        prefs.edit().clear().apply()
        _preferredMode.value = ProviderMode.COMPATIBLE_API
        _authState.value = AuthState.NotAuthenticated
    }

    private fun extractHost(url: String): String? =
        runCatching { URI(url).host?.takeIf { it.isNotBlank() } }.getOrNull()

    companion object {
        private const val WEB_SESSION_WINDOW_MS = 24L * 60L * 60L * 1000L // 24h local session reminder

        private const val VAULT_API_KEY = "optional_api_key"
        private const val KEY_PREFERRED_MODE = "preferred_mode"
        private const val KEY_WEB_CONNECTED = "web_connected"
        private const val KEY_WEB_LABEL = "web_label"
        private const val KEY_WEB_PORTAL_URL = "web_portal_url"
        private const val KEY_WEB_CONNECTED_AT = "web_connected_at"
        private const val KEY_WEB_EXPIRES_AT = "web_expires_at"
        private const val KEY_API_BASE = "api_base_url"
        private const val KEY_API_CONNECTED_AT = "api_connected_at"
    }
}
