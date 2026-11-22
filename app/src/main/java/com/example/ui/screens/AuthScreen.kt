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
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Browser
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Key
import com.adamglin.phosphoricons.regular.LockSimple
import com.adamglin.phosphoricons.regular.SignOut
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.ui.MainViewModel
import com.example.ui.components.EmbeddedWebSessionView
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
    var webPortalUrl by rememberSaveable { mutableStateOf(viewModel.getSavedWebPortalUrl()) }
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
                    Icon(
                        PhosphorIcons.Regular.GearSix,
                        contentDescription = "Settings",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
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
                            "Configure any OpenAI-compatible HTTPS endpoint and Model ID below, or open an embedded web session."
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
                    if (preferredMode != ProviderMode.WEB_SESSION && (settings.customBaseUrl.isNotBlank() || settings.modelId.isNotBlank())) {
                        Text(
                            text = "Active endpoint: ${settings.customBaseUrl.ifBlank { "Not set" }} · Model: ${settings.modelId.ifBlank { "Not set" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.primaryAccent
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissOutlinedButton(
                            text = "Expire / Reauth",
                            onClick = { viewModel.expireWebSessionForTestOrReauth() },
                            icon = PhosphorIcons.Regular.ArrowsClockwise,
                            modifier = Modifier.weight(1f).testTag("expire_session_button")
                        )
                        SwissOutlinedButton(
                            text = "Logout",
                            onClick = {
                                showWebBrowserModal = false
                                viewModel.logoutAll()
                            },
                            icon = PhosphorIcons.Regular.SignOut,
                            tint = tokens.error,
                            modifier = Modifier.weight(1f).testTag("logout_all_button")
                        )
                    }
                }
            }

            // Any Compatible API Base URL + Model ID + Key Configuration
            item {
                SwissSectionHeader(title = "Compatible API Endpoint", trailingText = "Any HTTPS Base URL")
                SwissSurfaceCard(
                    borderColor = if (preferredMode != ProviderMode.WEB_SESSION) tokens.primaryAccent.copy(alpha = 0.7f) else null
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
                                text = "Connect any OpenAI-compatible HTTPS endpoint and Model ID. Credentials are encrypted via Android Keystore.",
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
                            Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
                            Triple("Custom", baseUrlInput, modelIdInput)
                        ).forEach { (label, presetUrl, presetModel) ->
                            FilterChip(
                                selected = label != "Custom" &&
                                    presetUrl.isNotBlank() &&
                                    baseUrlInput.trimEnd('/') == presetUrl.trimEnd('/'),
                                onClick = {
                                    hasLocalEdits = true
                                    if (label == "Custom") {
                                        if (baseUrlInput.isBlank()) baseUrlInput = ""
                                    } else {
                                        baseUrlInput = presetUrl
                                        modelIdInput = presetModel
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = tokens.surface2,
                                    selectedContainerColor = tokens.primaryAccent.copy(alpha = 0.14f),
                                    selectedLabelColor = tokens.primaryAccent
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = label != "Custom" &&
                                        presetUrl.isNotBlank() &&
                                        baseUrlInput.trimEnd('/') == presetUrl.trimEnd('/'),
                                    borderColor = tokens.border.copy(alpha = 0.45f),
                                    selectedBorderColor = tokens.primaryAccent.copy(alpha = 0.5f)
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
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
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
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
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
                        leadingIcon = {
                            Icon(
                                PhosphorIcons.Regular.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
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
                            val cleanBase = baseUrlInput.trim()
                            val cleanModel = modelIdInput.trim()
                            hasLocalEdits = false
                            viewModel.saveApiFallback(
                                apiKey = apiKeyInput,
                                baseUrl = cleanBase,
                                modelId = cleanModel,
                                isCustom = true
                            )
                            apiKeyInput = ""
                        },
                        enabled = (apiKeyInput.isNotBlank() || hasStoredKey) &&
                            baseUrlInput.isNotBlank() &&
                            modelIdInput.isNotBlank(),
                        icon = PhosphorIcons.Regular.LockSimple,
                        modifier = Modifier.fillMaxWidth().testTag("save_api_fallback_button")
                    )
                }
            }

            // Embedded Web Session Card (Full-Screen Dialog for clean rendering)
            item {
                SwissSectionHeader(title = "Embedded Web Session", trailingText = "Any HTTPS Portal")
                SwissSurfaceCard(
                    borderColor = if (preferredMode == ProviderMode.WEB_SESSION) tokens.primaryAccent.copy(alpha = 0.7f) else null
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
                                text = "Open any HTTPS web portal inside a full-height isolated browser without storing passwords.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText
                            )
                        }
                    }

                    OutlinedTextField(
                        value = webPortalUrl,
                        onValueChange = { webPortalUrl = it },
                        label = { Text("Web Portal HTTPS URL (e.g. https://...)") },
                        singleLine = true,
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val cleanPortalUrl = webPortalUrl.trim()
                    val portalValid = cleanPortalUrl.startsWith("https://") && cleanPortalUrl.length > 10

                    SwissOutlinedButton(
                        text = "Open In-App Browser",
                        onClick = {
                            viewModel.selectProviderMode(ProviderMode.WEB_SESSION)
                            viewModel.beginWebAuthentication(cleanPortalUrl)
                            showWebBrowserModal = true
                        },
                        enabled = portalValid,
                        icon = PhosphorIcons.Regular.Browser,
                        modifier = Modifier.fillMaxWidth().testTag("login_web_session_button")
                    )
                }
            }
        }
    }

    if (showWebBrowserModal) {
        val validUrl = webPortalUrl.trim().takeIf { it.startsWith("https://") }
        if (validUrl != null) {
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
                    border = BorderStroke(SwissGeometry.borderWidth, tokens.border.copy(alpha = 0.6f))
                ) {
                    EmbeddedWebSessionView(
                        initialUrl = validUrl,
                        pendingBridgePrompt = executionState.pendingWebBridge?.preparedPrompt,
                        onPageFinished = { url, title -> viewModel.onWebPageLoaded(url, title) },
                        onConfirmConnected = {
                            viewModel.confirmWebSessionConnected(validUrl)
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
}
