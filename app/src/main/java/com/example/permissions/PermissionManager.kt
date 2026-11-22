package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.accessibility.AgentAccessibilityService
import com.example.settings.AgentSettings
import com.example.settings.AutonomyLevel
import java.net.URI

enum class ToolRiskLevel(val label: String) {
    SAFE("Safe • Read / Compute"),
    SENSITIVE("Sensitive • File / Network / System"),
    SYSTEM("System • UI Automation / Intent")
}

sealed interface PermissionDecision {
    data object Granted : PermissionDecision
    data class RequiresApproval(val reason: String) : PermissionDecision
    data class Denied(val reason: String) : PermissionDecision
}

class PermissionManager(private val context: Context) {

    fun evaluateToolExecution(
        toolName: String,
        riskLevel: ToolRiskLevel,
        settings: AgentSettings
    ): PermissionDecision {
        if (toolName in settings.disabledTools) {
            return PermissionDecision.Denied(
                "Tool '$toolName' is disabled in Settings > Tool Permissions."
            )
        }

        if (toolName == "accessibility_interact") {
            if (!settings.accessibilityUserConsent) {
                return PermissionDecision.Denied(
                    "Accessibility interaction requires explicit user consent in Settings."
                )
            }
            if (!isAccessibilityServiceEnabled()) {
                return PermissionDecision.Denied(
                    "Agent Studio Accessibility Service is not enabled in Android Settings."
                )
            }
        }

        return when (settings.autonomyLevel) {
            AutonomyLevel.AUTONOMOUS -> PermissionDecision.Granted
            AutonomyLevel.BALANCED -> {
                if (riskLevel == ToolRiskLevel.SAFE) {
                    PermissionDecision.Granted
                } else {
                    PermissionDecision.RequiresApproval(
                        "Balanced mode requests confirmation before running $toolName (${riskLevel.label})."
                    )
                }
            }
            AutonomyLevel.STRICT -> {
                PermissionDecision.RequiresApproval(
                    "Strict mode requires your approval for every tool call ($toolName)."
                )
            }
        }
    }

    fun isUrlPermitted(rawUrl: String, settings: AgentSettings): Result<URI> {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrElse {
            return Result.failure(IllegalArgumentException("Invalid URL syntax."))
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            return Result.failure(
                SecurityException("Only HTTPS URLs are permitted for network tools.")
            )
        }
        val host = uri.host?.lowercase()
            ?: return Result.failure(IllegalArgumentException("URL missing host."))

        // Block private/loopback IP ranges to prevent SSRF on local network
        if (host == "localhost" ||
            host == "127.0.0.1" ||
            host == "0.0.0.0" ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("169.254.")
        ) {
            return Result.failure(
                SecurityException("Access to local/loopback addresses is restricted.")
            )
        }

        if (settings.restrictNetworkDomains) {
            val allowed = settings.allowedDomainsCsv
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            val matches = allowed.any { domain -> host == domain || host.endsWith(".$domain") }
            if (!matches) {
                return Result.failure(
                    SecurityException("Domain '$host' is not in your Settings network allowlist.")
                )
            }
        }
        return Result.success(uri)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        if (AgentAccessibilityService.isBridgeConnected(context)) return true
        val expectedComponent = "${context.packageName}/${AgentAccessibilityService::class.java.name}"
        val enabledServices = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull() ?: return false
        return enabledServices.split(":").any {
            it.equals(expectedComponent, ignoreCase = true) ||
                it.endsWith("/${AgentAccessibilityService::class.java.name}", ignoreCase = true)
        }
    }
}
