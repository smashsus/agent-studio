package com.example.authentication

enum class ProviderMode(val displayName: String, val priorityLabel: String) {
    WEB_SESSION(
        displayName = "Official DeepSeek Web Session",
        priorityLabel = "Priority 1 • Account Session"
    ),
    OFFICIAL_API(
        displayName = "Official DeepSeek API",
        priorityLabel = "Priority 2 • Optional Fallback"
    ),
    COMPATIBLE_API(
        displayName = "Compatible Model Endpoint",
        priorityLabel = "Custom Provider"
    )
}

sealed interface AuthState {
    data object NotAuthenticated : AuthState
    data class Authenticating(val mode: ProviderMode, val message: String) : AuthState
    data class Authenticated(
        val mode: ProviderMode,
        val accountLabel: String,
        val connectedAt: Long,
        val expiresAt: Long?,
        val grantsProgrammaticApi: Boolean
    ) : AuthState
    data class SessionExpired(val lastAccountLabel: String, val mode: ProviderMode) : AuthState
    data class AuthenticationFailed(val reason: String, val mode: ProviderMode) : AuthState
}
