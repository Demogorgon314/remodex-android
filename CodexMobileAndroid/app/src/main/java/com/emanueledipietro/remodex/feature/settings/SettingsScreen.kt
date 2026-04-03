package com.emanueledipietro.remodex.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexBridgeVersionStatus
import com.emanueledipietro.remodex.model.RemodexBridgeProfilePresentation
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexContextWindowUsage
import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexGptAccountStatus
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexRateLimitBucket
import com.emanueledipietro.remodex.model.RemodexRateLimitDisplayRow
import com.emanueledipietro.remodex.model.RemodexRateLimitWindow
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexRuntimeMetaMapper
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation
import com.emanueledipietro.remodex.model.remodexGptHintText
import com.emanueledipietro.remodex.platform.notifications.RemodexNotificationPermissionUiState
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private const val ChatSupportUrl = "https://x.com/emanueledpt"
private const val PrivacyPolicyUrl = "https://github.com/Emanuele-web04/remodex/blob/main/Legal/PRIVACY_POLICY.md"
private const val TermsOfUseUrl = "https://github.com/Emanuele-web04/remodex/blob/main/Legal/TERMS_OF_USE.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: AppUiState,
    notificationPermissionUiState: RemodexNotificationPermissionUiState,
    onNotificationAction: () -> Unit,
    onSelectAppFontStyle: (RemodexAppFontStyle) -> Unit,
    onSelectDefaultModelId: (String?) -> Unit,
    onSelectDefaultReasoningEffort: (String?) -> Unit,
    onSelectDefaultAccessMode: (RemodexAccessMode) -> Unit,
    onSelectDefaultServiceTier: (RemodexServiceTier?) -> Unit,
    onRefreshSettingsStatus: () -> Unit,
    onRefreshUsageStatus: () -> Unit,
    onLogoutGptAccount: () -> Unit,
    onOpenScanner: () -> Unit,
    onDisconnect: () -> Unit,
    onRetryConnection: () -> Unit,
    onForgetTrustedMac: () -> Unit,
    onActivateBridgeProfile: (String) -> Unit,
    onRemoveBridgeProfile: (String) -> Unit,
    onSetMacNickname: (String, String?) -> Unit,
    onOpenArchivedChats: () -> Unit,
    onOpenAboutRemodex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var isShowingGptInfo by rememberSaveable { mutableStateOf(false) }
    var isShowingGptLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var isShowingForgetPairConfirm by rememberSaveable { mutableStateOf(false) }
    var isShowingMacNameDialog by rememberSaveable { mutableStateOf(false) }
    var pendingBridgeSwitchProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBridgeDeleteProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    val archivedThreads = remember(uiState.threads) {
        uiState.threads.filter { thread -> thread.syncState.name == "ARCHIVED_LOCAL" }
    }
    val availableModels = remember(uiState.availableModels) {
        RemodexRuntimeMetaMapper.orderedModels(uiState.availableModels)
    }
    val effectiveRuntimeDefaults = remember(availableModels, uiState.runtimeDefaults) {
        RemodexRuntimeConfig(
            availableModels = availableModels,
            selectedModelId = uiState.runtimeDefaults.modelId,
            reasoningEffort = uiState.runtimeDefaults.reasoningEffort,
            accessMode = uiState.runtimeDefaults.accessMode,
            serviceTier = uiState.runtimeDefaults.serviceTier,
        ).normalizeSelections()
    }
    val resolvedModel = remember(effectiveRuntimeDefaults) {
        effectiveRuntimeDefaults.selectedModelOption()
    }
    val reasoningOptions = remember(effectiveRuntimeDefaults) {
        effectiveRuntimeDefaults.availableReasoningEfforts
    }
    val serviceTierOptions = remember(uiState.threads, uiState.selectedThread?.id) {
        uiState.selectedThread?.runtimeConfig?.availableServiceTiers
            ?.takeIf(List<RemodexServiceTier>::isNotEmpty)
            ?: uiState.threads.firstNotNullOfOrNull { thread ->
                thread.runtimeConfig.availableServiceTiers.takeIf(List<RemodexServiceTier>::isNotEmpty)
            }
            ?: RemodexServiceTier.entries
    }
    val trustedMac = uiState.trustedMac
    val bridgeProfiles = uiState.bridgeProfiles
    val pendingBridgeDeleteProfile = remember(pendingBridgeDeleteProfileId, bridgeProfiles) {
        pendingBridgeDeleteProfileId?.let { profileId ->
            bridgeProfiles.firstOrNull { profile -> profile.profileId == profileId }
        }
    }
    val hasRunningTurn = remember(uiState.threads) {
        uiState.threads.any { thread -> thread.isRunning }
    }

    LaunchedEffect(uiState.isConnected, uiState.selectedThread?.id) {
        onRefreshSettingsStatus()
    }

    if (isShowingGptInfo) {
        SettingsGptMacInfoDialog(
            onDismiss = { isShowingGptInfo = false },
        )
    }

    if (isShowingGptLogoutConfirm) {
        SettingsGptLogoutConfirmDialog(
            onDismiss = { isShowingGptLogoutConfirm = false },
            onConfirm = {
                isShowingGptLogoutConfirm = false
                onLogoutGptAccount()
            },
        )
    }

    if (isShowingForgetPairConfirm && trustedMac != null) {
        SettingsConnectionConfirmDialog(
            title = "Forget Pair?",
            message = "You'll need to scan a QR code again to reconnect.",
            confirmLabel = "Forget Pair",
            onDismiss = { isShowingForgetPairConfirm = false },
            onConfirm = {
                isShowingForgetPairConfirm = false
                onForgetTrustedMac()
            },
        )
    }

    if (isShowingMacNameDialog && trustedMac?.deviceId != null) {
        SettingsMacNameDialog(
            currentNickname = trustedMac.currentNickname,
            currentName = trustedMac.name,
            systemName = trustedMac.systemName ?: trustedMac.name,
            onDismiss = { isShowingMacNameDialog = false },
            onReset = {
                onSetMacNickname(trustedMac.deviceId, null)
                isShowingMacNameDialog = false
            },
            onSave = { nickname ->
                onSetMacNickname(trustedMac.deviceId, nickname)
                isShowingMacNameDialog = false
            },
        )
    }

    pendingBridgeDeleteProfile?.let { pendingProfile ->
        SettingsConnectionConfirmDialog(
            title = if (pendingProfile.isActive) {
                "Delete current bridge?"
            } else {
                "Delete \"${pendingProfile.name}\"?"
            },
            message = if (pendingProfile.isActive) {
                "This also forgets the current pairing."
            } else {
                "This removes the saved bridge from this phone."
            },
            confirmLabel = if (pendingProfile.isActive) "Delete Current" else "Delete",
            onDismiss = { pendingBridgeDeleteProfileId = null },
            onConfirm = {
                pendingBridgeDeleteProfileId = null
                if (pendingProfile.isActive) {
                    onForgetTrustedMac()
                } else {
                    onRemoveBridgeProfile(pendingProfile.profileId)
                }
            },
        )
    }

    pendingBridgeSwitchProfileId
        ?.let { pendingProfileId ->
            bridgeProfiles.firstOrNull { profile -> profile.profileId == pendingProfileId }
        }
        ?.let { pendingProfile ->
            AlertDialog(
                onDismissRequest = { pendingBridgeSwitchProfileId = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingBridgeSwitchProfileId = null
                            onActivateBridgeProfile(pendingProfile.profileId)
                        },
                    ) {
                        Text("Switch")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingBridgeSwitchProfileId = null }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Switch to ${pendingProfile.name}?") },
                text = {
                    Text(
                        if (hasRunningTurn) {
                            "This switches your phone to ${pendingProfile.name} and disconnects the current bridge. Any live run will stop syncing here until you switch back."
                        } else {
                            "This switches your phone to ${pendingProfile.name} and disconnects the current bridge."
                        },
                    )
                },
            )
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsCard(title = "Archived Chats") {
            SettingsNavigationRow(
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                title = "Archived Chats",
                trailingText = archivedThreads.size.takeIf { it > 0 }?.toString(),
                onClick = onOpenArchivedChats,
            )
        }

        SettingsCard(title = "Appearance") {
            SettingsSelectionRow(
                title = "Font",
                currentLabel = uiState.appFontStyle.title,
                options = RemodexAppFontStyle.entries.map { style ->
                    SettingsOption(style.name, style.title)
                },
                onSelected = { key -> onSelectAppFontStyle(RemodexAppFontStyle.valueOf(key)) },
            )
            Text(
                text = uiState.appFontStyle.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(title = "Notifications") {
            SettingsStatusRow(
                icon = Icons.Outlined.Notifications,
                title = "Status",
                statusLabel = notificationStatusLabel(notificationPermissionUiState),
            )
            Text(
                text = "Used for local alerts when a run finishes while the app is in background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notificationPermissionUiState.actionLabel?.let { label ->
                SettingsButton(
                    title = label,
                    onClick = onNotificationAction,
                )
            }
        }

        SettingsChatGptCard(
            snapshot = uiState.gptAccountSnapshot,
            errorMessage = uiState.gptAccountErrorMessage,
            isConnected = uiState.isConnected,
            onShowInfo = { isShowingGptInfo = true },
            onRefresh = onRefreshSettingsStatus,
            onRequestLogout = { isShowingGptLogoutConfirm = true },
        )

        SettingsCard(title = "Bridge Version") {
            SettingsStatusRow(
                title = "Status",
                statusLabel = uiState.bridgeVersionStatus.statusLabel,
            )
            SettingsKeyValueRow(
                title = "Installed on Mac",
                value = uiState.bridgeVersionStatus.installedVersion ?: "Unknown",
                valueColor = if (uiState.bridgeVersionStatus.shouldHighlightInstalledVersion) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                monospace = true,
            )
            SettingsKeyValueRow(
                title = "Latest available",
                value = uiState.bridgeVersionStatus.latestVersion ?: "Unknown",
                monospace = true,
            )
            Text(
                text = uiState.bridgeVersionStatus.guidanceText,
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.bridgeVersionStatus.shouldHighlightInstalledVersion) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        SettingsCard(title = "Runtime defaults") {
            SettingsSelectionRow(
                title = "Model",
                currentLabel = resolvedModel?.let(RemodexRuntimeMetaMapper::modelTitle)
                    ?: uiState.runtimeDefaults.modelId
                    ?: "Auto",
                options = buildList {
                    add(SettingsOption("__AUTO__", "Auto"))
                    addAll(availableModels.map { model ->
                        SettingsOption(model.id, RemodexRuntimeMetaMapper.modelTitle(model))
                    })
                },
                onSelected = { key -> onSelectDefaultModelId(key.takeUnless { it == "__AUTO__" }) },
            )
            SettingsSelectionRow(
                title = "Reasoning",
                currentLabel = effectiveRuntimeDefaults.reasoningEffort
                    ?.let(RemodexRuntimeMetaMapper::reasoningTitle)
                    ?: "Auto",
                options = buildList {
                    add(SettingsOption("__AUTO__", "Auto"))
                    addAll(reasoningOptions.map { effort ->
                        SettingsOption(effort.reasoningEffort, effort.label)
                    })
                },
                onSelected = { key ->
                    onSelectDefaultReasoningEffort(key.takeUnless { it == "__AUTO__" })
                },
            )
            SettingsSelectionRow(
                title = "Speed",
                currentLabel = uiState.runtimeDefaults.serviceTier?.label ?: "Normal",
                options = buildList {
                    add(SettingsOption("__NORMAL__", "Normal"))
                    addAll(serviceTierOptions.map { tier -> SettingsOption(tier.name, tier.label) })
                },
                onSelected = { key ->
                    onSelectDefaultServiceTier(
                        key.takeUnless { it == "__NORMAL__" }?.let(RemodexServiceTier::valueOf),
                    )
                },
            )
            SettingsSelectionRow(
                title = "Access",
                currentLabel = uiState.runtimeDefaults.accessMode.shortLabel,
                options = RemodexAccessMode.entries.map { mode ->
                    SettingsOption(mode.name, mode.shortLabel)
                },
                onSelected = { key -> onSelectDefaultAccessMode(RemodexAccessMode.valueOf(key)) },
            )
        }

        SettingsCard(title = "About") {
            Text(
                text = "Chats are End-to-end encrypted between your Android phone and Mac. The relay only sees ciphertext and connection metadata after the secure handshake completes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsNavigationRow(
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                title = "How Remodex Works",
                onClick = onOpenAboutRemodex,
            )
            SettingsNavigationRow(
                leading = {
                    Text(
                        text = "X",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                title = "Chat & Support",
                onClick = { uriHandler.openUri(ChatSupportUrl) },
            )
            SettingsNavigationRow(
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                title = "Privacy Policy",
                onClick = { uriHandler.openUri(PrivacyPolicyUrl) },
            )
            SettingsNavigationRow(
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Gavel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                title = "Terms of Use",
                onClick = { uriHandler.openUri(TermsOfUseUrl) },
            )
        }

        SettingsCard(title = "Usage") {
            SettingsRefreshHeader(
                title = "Refresh",
                isRefreshing = uiState.isRefreshingUsage,
                onClick = onRefreshUsageStatus,
            )
            UsageSummaryCardContent(
                contextWindowUsage = uiState.usageStatus.contextWindowUsage,
                rateLimitBuckets = uiState.usageStatus.rateLimitBuckets,
                rateLimitsErrorMessage = uiState.usageStatus.rateLimitsErrorMessage,
                isRefreshing = uiState.isRefreshingUsage,
            )
        }

        SettingsCard(title = "Connection") {
            if (trustedMac == null) {
                Text(
                    text = "No paired Mac",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pair this Android device with your local Remodex bridge to enable trusted reconnect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SettingsTrustedMacCard(
                    presentation = trustedMac,
                    connectionStatusLabel = connectionPhaseLabel(uiState.connectionStatus.phase),
                    onEditName = { isShowingMacNameDialog = true },
                )
            }

            if (uiState.connectionStatus.phase in setOf(
                    RemodexConnectionPhase.CONNECTING,
                    RemodexConnectionPhase.RETRYING,
                )
            ) {
                Text(
                    text = uiState.connectionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uiState.connectionStatus.phase != RemodexConnectionPhase.CONNECTED) {
                Text(
                    text = uiState.connectionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.recoveryState.secureState.name == "REPAIR_REQUIRED") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            when {
                uiState.isConnected -> {
                    SettingsButton(
                        title = "Add Bridge",
                        onClick = onOpenScanner,
                    )
                    SettingsButton(
                        title = "Disconnect",
                        role = SettingsButtonRole.DESTRUCTIVE,
                        onClick = onDisconnect,
                    )
                }

                trustedMac != null -> {
                    SettingsButton(
                        title = "Reconnect",
                        onClick = onRetryConnection,
                    )
                    SettingsButton(
                        title = "Add Bridge",
                        onClick = onOpenScanner,
                    )
                    SettingsButton(
                        title = "Forget Pair",
                        role = SettingsButtonRole.DESTRUCTIVE,
                        onClick = { isShowingForgetPairConfirm = true },
                    )
                }

                else -> {
                    SettingsButton(
                        title = "Scan QR Code",
                        onClick = onOpenScanner,
                    )
                }
            }

            if (bridgeProfiles.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
                SettingsSavedBridgeProfiles(
                    profiles = bridgeProfiles,
                    onActivate = { profile ->
                        if (profile.isActive) {
                            return@SettingsSavedBridgeProfiles
                        }
                        pendingBridgeSwitchProfileId = profile.profileId
                    },
                    onRemove = { profile ->
                        pendingBridgeDeleteProfileId = profile.profileId
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsChatGptCard(
    snapshot: RemodexGptAccountSnapshot,
    errorMessage: String?,
    isConnected: Boolean,
    onShowInfo: () -> Unit,
    onRefresh: () -> Unit,
    onRequestLogout: () -> Unit,
) {
    val hintText = remodexGptHintText(
        snapshot = snapshot,
        isConnected = isConnected,
    )
    val shouldShowRefreshAction = !snapshot.isAuthenticated ||
        !snapshot.isVoiceTokenReady ||
        !errorMessage.isNullOrBlank()
    val accountMetaText = gptAccountMetaText(snapshot)

    SettingsCard(title = "ChatGPT") {
        SettingsGptStatusRow(snapshot = snapshot)

        accountMetaText?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }

        hintText?.let { hint ->
            SettingsInlineMessageText(
                text = hint,
                tone = gptHintTone(snapshot = snapshot),
            )
        }

        errorMessage?.takeIf(String::isNotBlank)?.let { message ->
            SettingsInlineMessageCard(
                text = message,
                tone = SettingsInlineMessageTone.ERROR,
            )
        }

        snapshot.detailText
            ?.takeIf { detail -> detail != accountMetaText }
            ?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        if (!snapshot.isAuthenticated) {
            SettingsNavigationRow(
                title = "How ChatGPT sign-in works",
                onClick = onShowInfo,
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        if (shouldShowRefreshAction) {
            SettingsButton(
                title = "Refresh status",
                onClick = onRefresh,
            )
        }

        if (snapshot.canLogout) {
            SettingsButton(
                title = "Log out on Mac",
                role = SettingsButtonRole.DESTRUCTIVE,
                onClick = onRequestLogout,
            )
        }
    }
}

@Composable
private fun SettingsGptStatusRow(
    snapshot: RemodexGptAccountSnapshot,
) {
    val iconTint = gptStatusColor(snapshot)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = gptStatusIcon(snapshot),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Status",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        SettingsStatusPill(label = snapshot.statusLabel)
    }
}

private fun gptAccountMetaText(
    snapshot: RemodexGptAccountSnapshot,
): String? {
    return buildList {
        snapshot.email
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::add)
        snapshot.planType
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.replaceFirstChar(Char::titlecase)
            ?.let(::add)
    }.takeIf(List<String>::isNotEmpty)?.joinToString(separator = " • ")
}

private enum class SettingsInlineMessageTone {
    NEUTRAL,
    WARNING,
    ERROR,
}

@Composable
private fun SettingsInlineMessageCard(
    text: String,
    tone: SettingsInlineMessageTone,
) {
    val (containerColor, borderColor, textColor) = when (tone) {
        SettingsInlineMessageTone.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsInlineMessageTone.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        SettingsInlineMessageTone.ERROR -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.error,
        )
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SettingsInlineMessageText(
    text: String,
    tone: SettingsInlineMessageTone,
) {
    val textColor = when (tone) {
        SettingsInlineMessageTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        SettingsInlineMessageTone.WARNING -> MaterialTheme.colorScheme.onSurfaceVariant
        SettingsInlineMessageTone.ERROR -> MaterialTheme.colorScheme.error
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = textColor,
    )
}

private enum class SettingsButtonRole {
    NORMAL,
    DESTRUCTIVE,
}

@Composable
private fun SettingsButton(
    title: String,
    onClick: () -> Unit,
    role: SettingsButtonRole = SettingsButtonRole.NORMAL,
) {
    val containerColor = if (role == SettingsButtonRole.DESTRUCTIVE) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f)
    }
    val borderColor = if (role == SettingsButtonRole.DESTRUCTIVE) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.95f)
    }
    val contentColor = if (role == SettingsButtonRole.DESTRUCTIVE) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    leading: @Composable BoxScope.() -> Unit,
    trailingText: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.92f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center,
                content = leading,
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            trailingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SettingsStatusRow(
    title: String,
    statusLabel: String,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        SettingsStatusPill(label = statusLabel)
    }
}

@Composable
private fun SettingsKeyValueRow(
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsStatusPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.95f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private data class SettingsOption(
    val key: String,
    val label: String,
)

@Composable
private fun SettingsSelectionRow(
    title: String,
    currentLabel: String,
    options: List<SettingsOption>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box {
            Text(
                text = currentLabel,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(option.key)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRefreshHeader(
    title: String,
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onClick,
            enabled = !isRefreshing,
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (isRefreshing) "Refreshing..." else title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun UsageSummaryCardContent(
    contextWindowUsage: RemodexContextWindowUsage?,
    rateLimitBuckets: List<RemodexRateLimitBucket>,
    rateLimitsErrorMessage: String?,
    isRefreshing: Boolean,
) {
    val visibleRows = remember(rateLimitBuckets) {
        RemodexRateLimitBucket.visibleDisplayRows(rateLimitBuckets)
    }

    Text(
        text = "Rate limits",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )

    when {
        visibleRows.isNotEmpty() -> {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                visibleRows.forEach { row ->
                    RateLimitRow(row = row)
                }
            }
        }

        rateLimitsErrorMessage?.isNotBlank() == true -> {
            Text(
                text = rateLimitsErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        isRefreshing -> {
            Text(
                text = "Loading current limits...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> {
            Text(
                text = "Rate limits are unavailable for this account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

    Text(
        text = "Context window",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )

    if (contextWindowUsage != null) {
        MetricRow(
            label = "Context",
            value = "${contextWindowUsage.percentRemaining}% left",
            detail = "(${compactTokenCount(contextWindowUsage.tokensUsed)} used / ${compactTokenCount(contextWindowUsage.tokenLimit)})",
        )
        UsageProgressBar(progress = contextWindowUsage.fractionUsed.toFloat())
    } else {
        MetricRow(
            label = "Context",
            value = "Unavailable",
            detail = "Waiting for token usage",
        )
    }
}

@Composable
private fun RateLimitRow(
    row: RemodexRateLimitDisplayRow,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${row.window.remainingPercent}% left",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            resetLabel(row.window)?.let { label ->
                Text(
                    text = " ($label)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        UsageProgressBar(progress = row.window.clampedUsedPercent / 100f)
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = " $detail",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UsageProgressBar(
    progress: Float,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = CircleShape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .size(width = 0.dp, height = 10.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f), CircleShape),
        )
    }
}

@Composable
private fun SettingsTrustedMacCard(
    presentation: RemodexTrustedMacPresentation,
    connectionStatusLabel: String,
    onEditName: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.92f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = CircleShape,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Mac",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = presentation.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(onClick = onEditName),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = CircleShape,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Mac name",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsStatusPill(label = connectionStatusLabel)
                SettingsStatusPill(label = presentation.title)
            }

            presentation.systemName?.let { systemName ->
                SettingsKeyValueRow(title = "System", value = systemName)
            }
            presentation.detail?.let { detail ->
                SettingsKeyValueRow(
                    title = "Status",
                    value = detail,
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsSavedBridgeProfiles(
    profiles: List<RemodexBridgeProfilePresentation>,
    onActivate: (RemodexBridgeProfilePresentation) -> Unit,
    onRemove: (RemodexBridgeProfilePresentation) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Saved Bridges",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        profiles.forEach { profile ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            profile.systemName?.let { systemName ->
                                Text(
                                    text = systemName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (profile.isConnected) {
                            SettingsStatusPill(label = "Connected")
                        } else if (profile.isActive) {
                            SettingsStatusPill(label = "Active")
                        }
                    }

                    Text(
                        text = profile.detail.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!profile.isActive) {
                            SettingsButton(
                                title = "Switch",
                                onClick = { onActivate(profile) },
                            )
                        }
                        SettingsButton(
                            title = if (profile.isActive) "Delete Current" else "Delete",
                            role = SettingsButtonRole.DESTRUCTIVE,
                            onClick = { onRemove(profile) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGptMacInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text("Use ChatGPT on Mac")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Remodex uses the ChatGPT session that is already signed in on your paired Mac.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GptSetupStep(
                    number = "1",
                    title = "Open ChatGPT on your Mac",
                    detail = "Use the Mac paired with this phone.",
                )
                GptSetupStep(
                    number = "2",
                    title = "Sign in there",
                    detail = "Make sure the ChatGPT account you want for voice is already active on the Mac.",
                )
                GptSetupStep(
                    number = "3",
                    title = "Come back to Remodex",
                    detail = "Keep the bridge connected and reopen Settings if the status has not refreshed yet.",
                )
                Text(
                    text = "You do not need to start ChatGPT login from this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun SettingsGptLogoutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Log out",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        title = {
            Text("Log out of ChatGPT?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Remodex will stop using the ChatGPT session from your paired Mac.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Voice transcription will not work again until you sign in on your Mac.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun SettingsConnectionConfirmDialog(
    title: String,
    message: String? = null,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        title = { Text(title) },
        text = message?.let { resolvedMessage ->
            @Composable {
                Text(
                    text = resolvedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun GptSetupStep(
    number: String,
    title: String,
    detail: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsMacNameDialog(
    currentNickname: String,
    currentName: String,
    systemName: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var draftNickname by remember(currentNickname, systemName) {
        mutableStateOf(currentNickname)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(draftNickname.trim().takeIf(String::isNotEmpty)) },
                enabled = draftNickname != currentNickname,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onReset,
                enabled = currentNickname.isNotBlank(),
            ) {
                Text("Use Default")
            }
        },
        title = { Text("Edit Mac Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = currentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draftNickname,
                    onValueChange = { draftNickname = it },
                    singleLine = true,
                    label = { Text("Mac name") },
                    placeholder = { Text(systemName) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Text(
                    text = "This nickname stays on this phone and appears anywhere this Mac is shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private val RemodexTrustedMacPresentation.currentNickname: String
    get() = if (systemName != null) name else ""

private fun notificationStatusLabel(
    permissionUiState: RemodexNotificationPermissionUiState,
): String {
    return when {
        permissionUiState.isEnabled -> "Authorized"
        permissionUiState.canRequestPermission -> "Not requested"
        permissionUiState.requiresSystemSettings -> "Denied"
        else -> "Unknown"
    }
}

private fun gptHintTone(
    snapshot: RemodexGptAccountSnapshot,
): SettingsInlineMessageTone {
    return when {
        snapshot.needsReauth -> SettingsInlineMessageTone.WARNING
        snapshot.status == RemodexGptAccountStatus.EXPIRED -> SettingsInlineMessageTone.ERROR
        snapshot.isAuthenticated && !snapshot.isVoiceTokenReady -> SettingsInlineMessageTone.NEUTRAL
        snapshot.status == RemodexGptAccountStatus.LOGIN_PENDING -> SettingsInlineMessageTone.WARNING
        else -> SettingsInlineMessageTone.NEUTRAL
    }
}

private fun gptStatusIcon(
    snapshot: RemodexGptAccountSnapshot,
): ImageVector {
    return when (snapshot.status) {
        RemodexGptAccountStatus.AUTHENTICATED -> if (snapshot.needsReauth) {
            Icons.Outlined.ErrorOutline
        } else {
            Icons.Outlined.CheckCircle
        }
        RemodexGptAccountStatus.EXPIRED -> Icons.Outlined.ErrorOutline
        RemodexGptAccountStatus.LOGIN_PENDING -> Icons.AutoMirrored.Outlined.HelpOutline
        RemodexGptAccountStatus.NOT_LOGGED_IN,
        RemodexGptAccountStatus.UNKNOWN,
        RemodexGptAccountStatus.UNAVAILABLE,
        -> Icons.Outlined.AccountCircle
    }
}

private fun gptStatusColor(
    snapshot: RemodexGptAccountSnapshot,
): Color {
    return when (snapshot.status) {
        RemodexGptAccountStatus.AUTHENTICATED -> if (snapshot.needsReauth) {
            Color(0xFFF29D38)
        } else {
            Color(0xFF2AAE67)
        }
        RemodexGptAccountStatus.EXPIRED -> Color(0xFFE25555)
        RemodexGptAccountStatus.LOGIN_PENDING -> Color(0xFFF29D38)
        RemodexGptAccountStatus.NOT_LOGGED_IN,
        RemodexGptAccountStatus.UNKNOWN,
        RemodexGptAccountStatus.UNAVAILABLE,
        -> Color(0xFF7B7E87)
    }
}

private fun connectionPhaseLabel(
    phase: RemodexConnectionPhase,
): String {
    return when (phase) {
        RemodexConnectionPhase.DISCONNECTED -> "Offline"
        RemodexConnectionPhase.CONNECTING -> "Connecting"
        RemodexConnectionPhase.RETRYING -> "Retrying"
        RemodexConnectionPhase.CONNECTED -> "Connected"
    }
}

private fun compactTokenCount(
    count: Int,
): String {
    return when {
        count >= 1_000_000 -> {
            val value = count / 1_000_000.0
            if (value % 1.0 == 0.0) "${value.toInt()}M" else String.format(Locale.US, "%.1fM", value)
        }
        count >= 1_000 -> {
            val value = count / 1_000.0
            if (value % 1.0 == 0.0) "${value.toInt()}K" else String.format(Locale.US, "%.1fK", value)
        }
        else -> NumberFormat.getIntegerInstance().format(count)
    }
}

private fun resetLabel(
    window: RemodexRateLimitWindow,
): String? {
    val resetsAtEpochMs = window.resetsAtEpochMs ?: return null
    val date = Date(resetsAtEpochMs)
    val now = Date()
    val dayFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
    return if (android.text.format.DateUtils.isToday(resetsAtEpochMs)) {
        "resets ${dayFormatter.format(date)}"
    } else {
        val formatter = java.text.SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
        "resets ${formatter.format(date)}"
    }
}
