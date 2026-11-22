package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Browser
import com.adamglin.phosphoricons.regular.Cpu
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Key
import com.adamglin.phosphoricons.regular.LockSimple
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.SignOut
import com.adamglin.phosphoricons.regular.Trash
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.settings.ProviderRegistryStore
import com.example.settings.SavedProviderProfile
import com.example.ui.MainViewModel
import com.example.ui.components.EmbeddedWebSessionView
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.ProviderEditorDialog
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissOutlinedButton
import com.example.ui.components.SwissPrimaryButton
import com.example.ui.components.SwissSectionHeader
import com.example.ui.components.SwissSurfaceCard
import com.example.ui.components.SwissTopBar
import com.example.ui.components.TokenUsageOverviewCard
import com.example.ui.components.TokenUsageSheet
import com.example.ui.theme.JetBrainsMonoFamily
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
    val savedProviders by viewModel.savedProviders.collectAsStateWithLifecycle()
    val activeProviderId by viewModel.activeProviderId.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()
    val tokenUsage by viewModel.tokenUsage.collectAsStateWithLifecycle()

    var showWebBrowserModal by rememberSaveable { mutableStateOf(false) }
    var webPortalUrl by rememberSaveable { mutableStateOf(viewModel.getSavedWebPortalUrl()) }
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var baseUrlInput by rememberSaveable { mutableStateOf(viewModel.getSavedApiBaseUrl()) }
    var modelIdInput by rememberSaveable { mutableStateOf(settings.modelId) }
    var hasLocalEdits by rememberSaveable { mutableStateOf(false) }

    var editingProvider by androidx.compose.runtime.remember { mutableStateOf<SavedProviderProfile?>(null) }
    var showAddProviderDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showModelSheet by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showTokenUsageSheet by androidx.compose.runtime.remember { mutableStateOf(false) }

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
            .imePadding()
    ) {
        SwissTopBar(
            title = "Providers & Endpoints",
            subtitle = "Connect APIs, model lists & web sessions",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = { showAddProviderDialog = true },
                    modifier = Modifier.size(SwissGeometry.minTouchTarget).testTag("add_provider_button")
                ) {
                    Icon(
                        PhosphorIcons.Regular.Plus,
                        contentDescription = "Add Provider",
                        tint = tokens.primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                            "Add a provider below or open an embedded web session."
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
                            text = "Active: ${settings.customBaseUrl.ifBlank { "Not set" }} · ${settings.modelId.ifBlank { "Not set" }}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = tokens.primaryAccent
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissOutlinedButton(
                            text = "Models List",
                            onClick = { showModelSheet = true },
                            icon = PhosphorIcons.Regular.Cpu,
                            modifier = Modifier.weight(1f).testTag("open_models_sheet_button")
                        )
                        SwissOutlinedButton(
                            text = "Reauth",
                            onClick = { viewModel.expireWebSessionForTestOrReauth() },
                            icon = PhosphorIcons.Regular.ArrowsClockwise,
                            modifier = Modifier.weight(1f).testTag("expire_session_button")
                        )
                    }

                    SwissOutlinedButton(
                        text = "Logout & Clear Keys",
                        onClick = {
                            showWebBrowserModal = false
                            viewModel.logoutAll()
                        },
                        icon = PhosphorIcons.Regular.SignOut,
                        tint = tokens.error,
                        modifier = Modifier.fillMaxWidth().testTag("logout_all_button")
                    )
                }
            }

            item {
                TokenUsageOverviewCard(
                    snapshot = tokenUsage,
                    onOpenDetails = { showTokenUsageSheet = true }
                )
            }

            // Saved Providers (OpenCode-style multi-provider list with Edit-After-Save & Model IDs)
            if (savedProviders.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SwissSectionHeader(
                            title = "Saved Providers",
                            trailingText = "${savedProviders.size} saved",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                items(savedProviders, key = { it.id }) { profile ->
                    val isActive = profile.id == activeProviderId && preferredMode != ProviderMode.WEB_SESSION
                    SavedProviderCard(
                        profile = profile,
                        isActive = isActive,
                        hasKey = viewModel.hasKeyForProvider(profile.id),
                        onActivate = { viewModel.activateSavedProvider(profile.id) },
                        onOpenModels = {
                            viewModel.activateSavedProvider(profile.id)
                            showModelSheet = true
                        },
                        onEdit = { editingProvider = profile },
                        onDelete = { viewModel.deleteSavedProvider(profile.id) }
                    )
                }
            }

            // Quick Add / Connect Any Compatible API Base URL + Model ID + Key
            item {
                SwissSectionHeader(
                    title = if (savedProviders.isEmpty()) "Compatible API Endpoint" else "Quick Connect / Add Endpoint",
                    trailingText = "Any HTTPS Base URL"
                )
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
                                text = "Connect any OpenAI-compatible endpoint. Fetches /models IDs automatically and supports editing after save.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProviderRegistryStore.builtInPresets.forEach { preset ->
                            FilterChip(
                                selected = baseUrlInput.trimEnd('/') == preset.baseUrl.trimEnd('/'),
                                onClick = {
                                    hasLocalEdits = true
                                    baseUrlInput = preset.baseUrl
                                    modelIdInput = preset.defaultModelId
                                },
                                label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = tokens.surface2,
                                    selectedContainerColor = tokens.primaryAccent.copy(alpha = 0.14f),
                                    selectedLabelColor = tokens.primaryAccent
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = baseUrlInput.trimEnd('/') == preset.baseUrl.trimEnd('/'),
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
                        "Update & Fetch Models"
                    } else {
                        "Save & Connect Endpoint"
                    }
                    val quickEnabled = (apiKeyInput.isNotBlank() || hasStoredKey) &&
                        baseUrlInput.isNotBlank() &&
                        modelIdInput.isNotBlank()

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
                        enabled = quickEnabled,
                        icon = PhosphorIcons.Regular.LockSimple,
                        modifier = Modifier.fillMaxWidth().testTag("save_api_fallback_button")
                    )

                    if (savedProviders.isNotEmpty()) {
                        SwissOutlinedButton(
                            text = "Save as New Provider Profile",
                            onClick = {
                                val cleanBase = baseUrlInput.trim()
                                val cleanModel = modelIdInput.trim()
                                hasLocalEdits = false
                                viewModel.saveQuickConnectEndpoint(
                                    apiKey = apiKeyInput,
                                    baseUrl = cleanBase,
                                    modelId = cleanModel,
                                    saveAsNew = true
                                )
                                apiKeyInput = ""
                            },
                            enabled = quickEnabled,
                            icon = PhosphorIcons.Regular.Plus,
                            modifier = Modifier.fillMaxWidth().testTag("save_as_new_provider_button")
                        )
                    }
                }
            }

            // Embedded Web Session Card
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

    if (showAddProviderDialog || editingProvider != null) {
        val target = editingProvider
        ProviderEditorDialog(
            initial = target,
            hasExistingKey = target?.let { viewModel.hasKeyForProvider(it.id) } ?: false,
            onDismiss = {
                showAddProviderDialog = false
                editingProvider = null
            },
            onSave = { existingId, name, url, key, modelId, modelIds, verify ->
                viewModel.saveProviderProfile(
                    existingId = existingId,
                    name = name,
                    baseUrl = url,
                    apiKey = key,
                    selectedModelId = modelId,
                    customModelIds = modelIds,
                    verifyConnection = verify,
                    setActive = true
                )
                hasLocalEdits = false
                showAddProviderDialog = false
                editingProvider = null
            }
        )
    }

    if (showModelSheet) {
        ModelSelectorSheet(
            providers = savedProviders,
            activeProviderId = activeProviderId,
            activeModelId = settings.modelId,
            isFetchingModels = isFetchingModels,
            onDismiss = { showModelSheet = false },
            onActivateProvider = { id -> viewModel.activateSavedProvider(id) },
            onFetchModels = { id -> viewModel.fetchModelsForProvider(id) },
            onSelectModel = { provId, mId -> viewModel.selectModelIdForProvider(provId, mId) },
            onRemoveModel = { provId, mId -> viewModel.removeModelIdFromProvider(provId, mId) },
            onOpenEndpoints = { showModelSheet = false }
        )
    }

    if (showTokenUsageSheet) {
        TokenUsageSheet(
            snapshot = tokenUsage,
            onDismiss = { showTokenUsageSheet = false },
            onReset = { viewModel.resetTokenUsage() }
        )
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

@Composable
private fun SavedProviderCard(
    profile: SavedProviderProfile,
    isActive: Boolean,
    hasKey: Boolean,
    onActivate: () -> Unit,
    onOpenModels: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = if (isActive) tokens.primaryAccent.copy(alpha = 0.06f) else tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(
            SwissGeometry.borderWidth,
            if (isActive) tokens.primaryAccent.copy(alpha = 0.65f) else tokens.border.copy(alpha = 0.75f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SwissGeometry.radiusCard))
            .clickable(onClick = onActivate)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profile.baseUrl,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isActive) {
                        SwissBadge(text = "Active", textColor = tokens.success)
                    }
                    SwissBadge(
                        text = if (hasKey) "Key Saved" else "No Key",
                        textColor = if (hasKey) tokens.primaryAccent else tokens.warning
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = tokens.surface2,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenModels)
                ) {
                    Text(
                        text = "${profile.selectedModelId.ifBlank { "Pick model" }} · ${profile.modelIds.size} IDs",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.PencilSimple,
                            contentDescription = "Edit Provider",
                            tint = tokens.primaryAccent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.Trash,
                            contentDescription = "Delete Provider",
                            tint = tokens.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
