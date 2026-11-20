package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.ui.MainViewModel
import com.example.ui.components.DeepSeekOfficialWebView
import com.example.ui.theme.LocalAgentColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuthScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val preferredMode by viewModel.preferredMode.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val agentColors = LocalAgentColors.current

    var showEmbeddedWebView by rememberSaveable { mutableStateOf(false) }
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var customBaseUrl by rememberSaveable { mutableStateOf(viewModel.getSavedApiBaseUrl()) }
    var useCustomCompatible by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Authentication State Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("DeepSeek Account & Auth State", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    val (badgeText, badgeColor, detailText) = when (val s = authState) {
                        AuthState.NotAuthenticated -> Triple(
                            "Not Authenticated",
                            agentColors.warning,
                            "Connect your DeepSeek account below via Official Web Session (Priority 1) or optional API fallback (Priority 2)."
                        )
                        is AuthState.Authenticating -> Triple(
                            "Authenticating",
                            agentColors.telemetry,
                            s.message
                        )
                        is AuthState.Authenticated -> {
                            val expText = s.expiresAt?.let {
                                " • Expires: ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(it))}"
                            } ?: " • Persistent Keystore Credential"
                            Triple(
                                "Authenticated (${s.mode.displayName})",
                                agentColors.success,
                                "${s.accountLabel}$expText"
                            )
                        }
                        is AuthState.SessionExpired -> Triple(
                            "Session Expired",
                            agentColors.warning,
                            "Session for ${s.lastAccountLabel} expired. Reauthenticate cleanly below."
                        )
                        is AuthState.AuthenticationFailed -> Triple(
                            "Authentication Failed",
                            agentColors.danger,
                            s.reason
                        )
                    }

                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Text(detailText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.expireWebSessionForTestOrReauth() },
                            modifier = Modifier.testTag("expire_session_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reauth / Expire")
                        }
                        OutlinedButton(
                            onClick = {
                                showEmbeddedWebView = false
                                viewModel.logoutAll()
                            },
                            modifier = Modifier.testTag("logout_all_button")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Logout")
                        }
                    }
                }
            }
        }

        // Priority 1: Official DeepSeek Web Session
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (preferredMode == ProviderMode.WEB_SESSION)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(
                    1.dp,
                    if (preferredMode == ProviderMode.WEB_SESSION) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            RadioButton(
                                selected = preferredMode == ProviderMode.WEB_SESSION,
                                onClick = { viewModel.selectProviderMode(ProviderMode.WEB_SESSION) }
                            )
                            Column {
                                Text("Priority 1: Official DeepSeek Web Session", style = MaterialTheme.typography.titleSmall)
                                Text("Uses your existing DeepSeek account via official chat.deepseek.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = agentColors.telemetry, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Transparency Note: DeepSeek does not currently provide a third-party OAuth flow that turns a consumer web login into programmatic API access. Your embedded session runs inside an isolated official WebView without capturing passwords or reverse-engineering private endpoints.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.selectProviderMode(ProviderMode.WEB_SESSION)
                            if (!showEmbeddedWebView) viewModel.beginWebAuthentication()
                            showEmbeddedWebView = !showEmbeddedWebView
                        },
                        modifier = Modifier.fillMaxWidth().testTag("login_deepseek_web_button")
                    ) {
                        Icon(Icons.Default.Web, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (showEmbeddedWebView) "Hide DeepSeek Web Browser" else "Login with DeepSeek (Official Web)")
                    }
                }
            }
        }

        // Embedded Official DeepSeek WebView
        if (showEmbeddedWebView) {
            item {
                DeepSeekOfficialWebView(
                    pendingBridgePrompt = executionState.pendingWebBridge?.preparedPrompt,
                    onPageFinished = { url, title -> viewModel.onDeepSeekWebPageLoaded(url, title) },
                    onConfirmConnected = { viewModel.confirmWebSessionConnected() },
                    onCopyBridgePrompt = { prompt -> viewModel.copyToClipboard("DeepSeek Prompt", prompt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                )
            }
        }

        // Priority 2: Optional Official DeepSeek API / Compatible Endpoint
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (preferredMode != ProviderMode.WEB_SESSION)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(
                    1.dp,
                    if (preferredMode != ProviderMode.WEB_SESSION) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = preferredMode != ProviderMode.WEB_SESSION,
                            onClick = {
                                viewModel.selectProviderMode(
                                    if (useCustomCompatible) ProviderMode.COMPATIBLE_API else ProviderMode.OFFICIAL_API
                                )
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Priority 2: Official DeepSeek API (Optional Fallback)", style = MaterialTheme.typography.titleSmall)
                            Text("Enables fully automated multi-step tool loops when an API key is provided. Encrypted in Android Keystore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use Custom Compatible Endpoint", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = useCustomCompatible,
                            onCheckedChange = {
                                useCustomCompatible = it
                                if (!it) customBaseUrl = "https://api.deepseek.com"
                            }
                        )
                    }

                    if (useCustomCompatible) {
                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = { customBaseUrl = it },
                            label = { Text("HTTPS Base URL (OpenAI-compatible)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = {
                            Text(
                                if (viewModel.hasApiFallback()) "Key stored in Keystore (enter new key to replace)"
                                else "Optional API Key (never exposed to model/tools)"
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("optional_api_key_input")
                    )

                    FilledTonalButton(
                        onClick = {
                            val targetBase = if (useCustomCompatible) customBaseUrl else "https://api.deepseek.com"
                            viewModel.saveApiFallback(apiKeyInput, targetBase, useCustomCompatible)
                            apiKeyInput = ""
                        },
                        enabled = apiKeyInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("save_api_fallback_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Encrypt in Keystore & Verify")
                    }
                }
            }
        }
    }
}
