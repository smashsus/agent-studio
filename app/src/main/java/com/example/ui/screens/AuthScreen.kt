package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.authentication.AuthManager
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.ui.MainViewModel
import com.example.ui.components.DeepSeekOfficialWebView
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissOutlinedButton
import com.example.ui.components.SwissPrimaryButton
import com.example.ui.components.SwissSectionHeader
import com.example.ui.components.SwissSurfaceCard
import com.example.ui.components.SwissTopBar
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuthScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val preferredMode by viewModel.preferredMode.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showWebBrowserModal by rememberSaveable { mutableStateOf(false) }
    var webPortalUrl by rememberSaveable { mutableStateOf(AuthManager.DEEPSEEK_WEB_URL) }
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var baseUrlInput by rememberSaveable { mutableStateOf(viewModel.getSavedApiBaseUrl()) }
    var modelIdInput by rememberSaveable { mutableStateOf(settings.modelId) }
    var hasLocalEdits by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.customBaseUrl, settings.modelId) {
        if (!hasLocalEdits) {
            if (settings.customBaseUrl.isNotBlank()) baseUrlInput = settings.customBaseUrl
            if (settings.modelId.isNotBlank()) modelIdInput = settings.modelId
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        SwissTopBar(
            title = "Account & Endpoints",
            subtitle = "Provider-neutral connection hub",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(SwissGeometry.minTouchTarget)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = tokens.primaryText)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(SwissGeometry.screenPadding),
            verticalArrangement = Arrangement.spacedBy(SwissGeometry.sectionSpacing)
        ) {
            // Current Connection State
            item {
                SwissSectionHeader(title = "Connection Status")
                SwissSurfaceCard {
                    val (badgeText, badgeColor, detailText) = when (val s = authState) {
                        AuthState.NotAuthenticated -> Triple(
                            "Not Connected",
                            tokens.warning,
                            "Configure any OpenAI-compatible API endpoint with base URL below, or open an embedded web session."
                        )
                        is AuthState.Authenticating -> Triple(
                            "Connecting",
                            tokens.primaryAccent,
                            s.message
                        )
                        is AuthState.Authenticated -> {
                            val expText = s.expiresAt?.let {
                                " · Expires ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(it))}"
                            } ?: " · Keystore encrypted"
                            Triple(
                                "Connected",
                                tokens.success,
                                "${s.accountLabel}$expText"
                            )
                        }
                        is AuthState.SessionExpired -> Triple(
                            "Session Expired",
                            tokens.warning,
                            "Session for ${s.lastAccountLabel} expired. Reauthenticate below."
                        )
                        is AuthState.AuthenticationFailed -> Triple(
                            "Connection Failed",
                            tokens.error,
                            s.reason
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (preferredMode == ProviderMode.WEB_SESSION) "Web Session Mode" else "API Endpoint Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.primaryText
                        )
                        SwissBadge(text = badgeText, textColor = badgeColor)
                    }

                    Text(detailText, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                    if (preferredMode != ProviderMode.WEB_SESSION) {
                        Text(
                            text = "Active endpoint: ${settings.customBaseUrl} · Model: ${settings.modelId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.primaryAccent
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissOutlinedButton(
                            text = "Expire / Reauth",
                            onClick = { viewModel.expireWebSessionForTestOrReauth() },
                            icon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f).testTag("expire_session_button")
                        )
                        SwissOutlinedButton(
                            text = "Logout",
                            onClick = {
                                showWebBrowserModal = false
                                viewModel.logoutAll()
                            },
                            icon = Icons.AutoMirrored.Filled.Logout,
                            tint = tokens.error,
                            modifier = Modifier.weight(1f).testTag("logout_all_button")
                        )
                    }
                }
            }

            // Any Compatible API Base URL + Key Configuration
            item {
                SwissSectionHeader(title = "Compatible API Endpoint", trailingText = "Any HTTPS Base URL")
                SwissSurfaceCard(
                    borderColor = if (preferredMode != ProviderMode.WEB_SESSION) tokens.primaryAccent else tokens.border
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = preferredMode != ProviderMode.WEB_SESSION,
                            onClick = { viewModel.selectProviderMode(ProviderMode.COMPATIBLE_API) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use API Endpoint for Autonomous Tools",
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.primaryText
                            )
                            Text(
                                text = "Connect any OpenAI-compatible HTTPS endpoint and model ID. Credentials are encrypted via Android Keystore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText
                            )
                        }
                    }

                    // Provider-neutral quick-fill base URL presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("Fasfas AI", "https://api.fasfas.ai/v1", "glm-5.3-flash"),
                            Triple("Fasfas RP", "https://api.fasfas.ai/rp/v1", "glm-5.3-flash"),
                            Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
                            Triple("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
                            Triple("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
                            Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
                            Triple("Custom", baseUrlInput, modelIdInput)
                        ).forEach { (label, presetUrl, presetModel) ->
                            FilterChip(
                                selected = label != "Custom" &&
                                    baseUrlInput.trimEnd('/') == presetUrl.trimEnd('/'),
                                onClick = {
                                    hasLocalEdits = true
                                    baseUrlInput = presetUrl
                                    modelIdInput = presetModel
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tokens.surface2,
                                    selectedLabelColor = tokens.primaryAccent
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = {
                            hasLocalEdits = true
                            baseUrlInput = it
                        },
                        label = { Text("HTTPS Base URL (e.g. https://api.fasfas.ai/v1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modelIdInput,
                        onValueChange = {
                            hasLocalEdits = true
                            modelIdInput = it
                        },
                        label = { Text("Model ID (e.g. glm-5.3-flash)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = {
                            Text(
                                if (viewModel.hasApiFallback()) "API Key saved in Keystore (leave blank to keep, or enter new)"
                                else "API Key (Bearer token, encrypted locally)"
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("optional_api_key_input")
                    )

                    val hasStoredKey = viewModel.hasApiFallback()
                    val buttonText = if (apiKeyInput.isBlank() && hasStoredKey) {
                        "Update Endpoint & Model"
                    } else {
                        "Save & Connect Endpoint"
                    }

                    SwissPrimaryButton(
                        text = buttonText,
                        onClick = {
                            val cleanBase = baseUrlInput.trim().ifEmpty { "https://api.fasfas.ai/v1" }
                            val cleanModel = modelIdInput.trim().ifEmpty { "glm-5.3-flash" }
                            hasLocalEdits = false
                            viewModel.saveApiFallback(
                                apiKey = apiKeyInput,
                                baseUrl = cleanBase,
                                modelId = cleanModel,
                                isCustom = !cleanBase.contains("api.deepseek.com", ignoreCase = true)
                            )
                            apiKeyInput = ""
                        },
                        enabled = (apiKeyInput.isNotBlank() || hasStoredKey) && baseUrlInput.isNotBlank(),
                        icon = Icons.Default.Lock,
                        modifier = Modifier.fillMaxWidth().testTag("save_api_fallback_button")
                    )
                }
            }

            // Embedded Web Session Card (Full-Screen Dialog for clean rendering)
            item {
                SwissSectionHeader(title = "Embedded Web Session", trailingText = "Isolated WebView")
                SwissSurfaceCard(
                    borderColor = if (preferredMode == ProviderMode.WEB_SESSION) tokens.primaryAccent else tokens.border
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = preferredMode == ProviderMode.WEB_SESSION,
                            onClick = { viewModel.selectProviderMode(ProviderMode.WEB_SESSION) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Embedded Web Session",
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.primaryText
                            )
                            Text(
                                text = "Sign into a provider's web interface inside a full-height embedded browser without storing passwords.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText
                            )
                        }
                    }

                    OutlinedTextField(
                        value = webPortalUrl,
                        onValueChange = { webPortalUrl = it },
                        label = { Text("Web Portal HTTPS URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SwissOutlinedButton(
                        text = "Open In-App Browser",
                        onClick = {
                            viewModel.selectProviderMode(ProviderMode.WEB_SESSION)
                            viewModel.beginWebAuthentication()
                            showWebBrowserModal = true
                        },
                        icon = Icons.Default.Web,
                        modifier = Modifier.fillMaxWidth().testTag("login_deepseek_web_button")
                    )
                }
            }
        }
    }

    if (showWebBrowserModal) {
        Dialog(
            onDismissRequest = { showWebBrowserModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                shape = RoundedCornerShape(SwissGeometry.radiusDialog),
                color = tokens.surface1,
                border = BorderStroke(SwissGeometry.borderWidth, tokens.border)
            ) {
                DeepSeekOfficialWebView(
                    pendingBridgePrompt = executionState.pendingWebBridge?.preparedPrompt,
                    initialUrl = webPortalUrl.trim().takeIf { it.startsWith("https://") }
                        ?: AuthManager.DEEPSEEK_WEB_URL,
                    onPageFinished = { url, title -> viewModel.onDeepSeekWebPageLoaded(url, title) },
                    onConfirmConnected = {
                        viewModel.confirmWebSessionConnected()
                        showWebBrowserModal = false
                    },
                    onCopyBridgePrompt = { prompt -> viewModel.copyToClipboard("Agent Prompt", prompt) },
                    onClose = { showWebBrowserModal = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
