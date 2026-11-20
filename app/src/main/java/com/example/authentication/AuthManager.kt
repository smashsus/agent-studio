package com.example.authentication

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Manages DeepSeek user-account authentication:
 * Priority 1: Official DeepSeek Web Session (embedded chat.deepseek.com WebView).
 * Priority 2: Optional Official DeepSeek API / Compatible endpoint key encrypted in KeystoreVault.
 *
 * Security & policy invariants:
 * - Never collects or stores user passwords.
 * - Never extracts session cookies from other apps or reverse-engineers private web endpoints.
 * - Never pretends web login grants programmatic API access.
 * - Never exposes credentials to the model prompt or tool execution layer.
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
        val raw = prefs.getString(KEY_PREFERRED_MODE, ProviderMode.WEB_SESSION.name)
        return runCatching { ProviderMode.valueOf(raw ?: ProviderMode.WEB_SESSION.name) }
            .getOrDefault(ProviderMode.WEB_SESSION)
    }

    fun selectProviderMode(mode: ProviderMode) {
        prefs.edit().putString(KEY_PREFERRED_MODE, mode.name).apply()
        _preferredMode.value = mode
        refreshSessionState()
    }

    fun beginWebAuthentication() {
        _authState.value = AuthState.Authenticating(
            mode = ProviderMode.WEB_SESSION,
            message = "Sign in on official chat.deepseek.com inside the embedded browser."
        )
    }

    /**
     * Called when the embedded WebView finishes navigating on chat.deepseek.com.
     * Observes domain session presence via Android CookieManager without exposing cookie values.
     */
    fun updateWebSessionFromEmbeddedBrowser(currentUrl: String?, pageTitle: String?) {
        val url = currentUrl ?: return
        if (!url.startsWith(DEEPSEEK_WEB_ORIGIN)) return

        val hasSessionCookies = runCatching {
            val cookieStr = CookieManager.getInstance().getCookie(DEEPSEEK_WEB_ORIGIN)
            !cookieStr.isNullOrBlank() && cookieStr.contains("=")
        }.getOrDefault(false)

        val isLoginRoute = url.contains("/sign_in", ignoreCase = true) ||
            url.contains("/login", ignoreCase = true)

        if (!isLoginRoute && hasSessionCookies) {
            val now = System.currentTimeMillis()
            val expires = now + WEB_SESSION_WINDOW_MS
            val label = pageTitle?.takeIf { it.isNotBlank() && !it.contains("Sign", true) }
                ?: "DeepSeek Web Account"
            prefs.edit()
                .putBoolean(KEY_WEB_CONNECTED, true)
                .putString(KEY_WEB_LABEL, label)
                .putLong(KEY_WEB_CONNECTED_AT, now)
                .putLong(KEY_WEB_EXPIRES_AT, expires)
                .apply()
            if (_preferredMode.value == ProviderMode.WEB_SESSION) {
                _authState.value = AuthState.Authenticated(
                    mode = ProviderMode.WEB_SESSION,
                    accountLabel = "DeepSeek Web Session ($label)",
                    connectedAt = now,
                    expiresAt = expires,
                    grantsProgrammaticApi = false
                )
            }
        }
    }

    fun markWebSessionConfirmedByUser(accountHint: String = "DeepSeek Account") {
        val now = System.currentTimeMillis()
        val expires = now + WEB_SESSION_WINDOW_MS
        prefs.edit()
            .putBoolean(KEY_WEB_CONNECTED, true)
            .putString(KEY_WEB_LABEL, accountHint)
            .putLong(KEY_WEB_CONNECTED_AT, now)
            .putLong(KEY_WEB_EXPIRES_AT, expires)
            .apply()
        _preferredMode.value = ProviderMode.WEB_SESSION
        prefs.edit().putString(KEY_PREFERRED_MODE, ProviderMode.WEB_SESSION.name).apply()
        _authState.value = AuthState.Authenticated(
            mode = ProviderMode.WEB_SESSION,
            accountLabel = accountHint,
            connectedAt = now,
            expiresAt = expires,
            grantsProgrammaticApi = false
        )
    }

    fun expireSessionForReauth() {
        val label = prefs.getString(KEY_WEB_LABEL, "DeepSeek Account") ?: "DeepSeek Account"
        prefs.edit().putBoolean(KEY_WEB_CONNECTED, false).apply()
        _authState.value = AuthState.SessionExpired(
            lastAccountLabel = label,
            mode = _preferredMode.value
        )
    }

    suspend fun saveAndVerifyApiFallback(
        apiKey: String,
        baseUrl: String,
        isCustomCompatible: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val mode = if (isCustomCompatible) ProviderMode.COMPATIBLE_API else ProviderMode.OFFICIAL_API
        val trimmedKey = apiKey.trim()
        val cleanBase = baseUrl.trim().trimEnd('/').ifEmpty { "https://api.deepseek.com" }
        if (trimmedKey.isBlank()) {
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

        _authState.value = AuthState.Authenticating(mode, "Verifying endpoint credentials...")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("$cleanBase/models")
            .header("Authorization", "Bearer $trimmedKey")
            .get()
            .build()

        val responseResult = runCatching { client.newCall(request).execute() }
        responseResult.fold(
            onSuccess = { resp ->
                resp.use {
                    if (it.isSuccessful || it.code == 404) {
                        vault.putSecret(VAULT_API_KEY, trimmedKey)
                        val now = System.currentTimeMillis()
                        prefs.edit()
                            .putString(KEY_API_BASE, cleanBase)
                            .putLong(KEY_API_CONNECTED_AT, now)
                            .putString(KEY_PREFERRED_MODE, mode.name)
                            .apply()
                        _preferredMode.value = mode
                        _authState.value = AuthState.Authenticated(
                            mode = mode,
                            accountLabel = if (isCustomCompatible) cleanBase else "Official DeepSeek API",
                            connectedAt = now,
                            expiresAt = null,
                            grantsProgrammaticApi = true
                        )
                        Result.success(Unit)
                    } else if (it.code == 401 || it.code == 403) {
                        _authState.value = AuthState.AuthenticationFailed(
                            "Endpoint rejected credentials (HTTP ${it.code}). Check your key and try again.",
                            mode
                        )
                        Result.failure(SecurityException("HTTP ${it.code}"))
                    } else {
                        // Still allow saving if server returned non-401 (some compatible endpoints lack /models)
                        vault.putSecret(VAULT_API_KEY, trimmedKey)
                        val now = System.currentTimeMillis()
                        prefs.edit()
                            .putString(KEY_API_BASE, cleanBase)
                            .putLong(KEY_API_CONNECTED_AT, now)
                            .putString(KEY_PREFERRED_MODE, mode.name)
                            .apply()
                        _preferredMode.value = mode
                        _authState.value = AuthState.Authenticated(
                            mode = mode,
                            accountLabel = "$cleanBase (Saved)",
                            connectedAt = now,
                            expiresAt = null,
                            grantsProgrammaticApi = true
                        )
                        Result.success(Unit)
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

    /**
     * Internal-only accessor used exclusively by OpenAiCompatibleModelProvider on HTTP requests.
     * Never exposed to tools, logs, Room, or model prompts.
     */
    internal fun getApiKeyForModelTransport(): String? = vault.getSecret(VAULT_API_KEY)

    fun hasApiFallbackConfigured(): Boolean = vault.hasSecret(VAULT_API_KEY)

    fun getSavedApiBaseUrl(): String =
        prefs.getString(KEY_API_BASE, "https://api.deepseek.com") ?: "https://api.deepseek.com"

    fun refreshSessionState() {
        val mode = _preferredMode.value
        val now = System.currentTimeMillis()
        when (mode) {
            ProviderMode.WEB_SESSION -> {
                val connected = prefs.getBoolean(KEY_WEB_CONNECTED, false)
                val label = prefs.getString(KEY_WEB_LABEL, "DeepSeek Account") ?: "DeepSeek Account"
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
                    val connectedAt = prefs.getLong(KEY_API_CONNECTED_AT, now)
                    _authState.value = AuthState.Authenticated(
                        mode = mode,
                        accountLabel = if (mode == ProviderMode.OFFICIAL_API) "Official DeepSeek API" else base,
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
        _preferredMode.value = ProviderMode.WEB_SESSION
        _authState.value = AuthState.NotAuthenticated
    }

    companion object {
        const val DEEPSEEK_WEB_URL = "https://chat.deepseek.com/"
        private const val DEEPSEEK_WEB_ORIGIN = "https://chat.deepseek.com"
        private const val WEB_SESSION_WINDOW_MS = 24L * 60L * 60L * 1000L // 24h local session reminder

        private const val VAULT_API_KEY = "optional_api_key"
        private const val KEY_PREFERRED_MODE = "preferred_mode"
        private const val KEY_WEB_CONNECTED = "web_connected"
        private const val KEY_WEB_LABEL = "web_label"
        private const val KEY_WEB_CONNECTED_AT = "web_connected_at"
        private const val KEY_WEB_EXPIRES_AT = "web_expires_at"
        private const val KEY_API_BASE = "api_base_url"
        private const val KEY_API_CONNECTED_AT = "api_connected_at"
    }
}
