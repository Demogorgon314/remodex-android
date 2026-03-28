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
import androidx.compose.material3.IconButton
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
    onDisconnect: () -> Unit,
    onForgetTrustedMac: () -> Unit,
    onSetMacNickname: (String, String?) -> Unit,
    onOpenArchivedChats: () -> Unit,
    onOpenAboutRemodex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var isShowingGptInfo by rememberSaveable { mutableStateOf(false) }
    var isShowingMacNameDialog by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(uiState.isConnected, uiState.selectedThread?.id) {
        onRefreshSettingsStatus()
    }

    if (isShowingGptInfo) {
        SettingsGptMacInfoDialog(
            onDismiss = { isShowingGptInfo = false },
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

        SettingsCard(title = "ChatGPT") {
            SettingsStatusRow(
                icon = gptStatusIcon(uiState.gptAccountSnapshot),
                iconTint = gptStatusColor(uiState.gptAccountSnapshot),
                title = "Status",
                statusLabel = uiState.gptAccountSnapshot.statusLabel,
            )

            uiState.gptAccountSnapshot.detailText?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            gptHintText(
                snapshot = uiState.gptAccountSnapshot,
                isConnected = uiState.isConnected,
            )?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.gptAccountErrorMessage?.takeIf(String::isNotBlank)?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!uiState.gptAccountSnapshot.isAuthenticated) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { isShowingGptInfo = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "How ChatGPT voice sign-in works",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.gptAccountSnapshot.canLogout) {
                SettingsButton(
                    title = "Log out",
                    role = SettingsButtonRole.DESTRUCTIVE,
                    onClick = onLogoutGptAccount,
                )
            }
        }

        SettingsCard(title = "Bridge Version") {
            SettingsStatusRow(
                title = "Status",
                statusLabel = uiState.bridgeVersionStatus.statusLabel,
            )
            SettingsKeyValueRow(
                title = "Installed on Mac",
                value = uiState.bridgeVersionStatus.installedVersion ?: "Unknown",
                valueColor = if (uiState.bridgeVersionStatus.shouldHighlightInstalledVersion) {
                    Color(0xFFF29D38)
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
                    Color(0xFFF29D38)
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
                        title = "Disconnect",
                        role = SettingsButtonRole.DESTRUCTIVE,
                        onClick = onDisconnect,
                    )
                }

                trustedMac != null -> {
                    SettingsButton(
                        title = "Forget Pair",
                        role = SettingsButtonRole.DESTRUCTIVE,
                        onClick = onForgetTrustedMac,
                    )
                }
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
        shape = RoundedCornerShape(12.dp),
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
                .padding(horizontal = 16.dp, vertical = 11.dp),
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.92f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
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
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.95f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                        text = { Text(option.label) },
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
                    text = "ChatGPT voice is checked on your Mac. Remodex reads the ChatGPT session from your paired Mac bridge.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GptSetupStep(
                    number = "1",
                    title = "Open ChatGPT on your Mac",
                    detail = "Use the Mac that is paired with this Android device.",
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
                    text = "You do not need to start ChatGPT login from this Android phone.",
                    style = MaterialTheme.typography.bodySmall,
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
                    text = "This nickname stays on this Android phone and appears anywhere this Mac is shown.",
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

private fun gptHintText(
    snapshot: RemodexGptAccountSnapshot,
    isConnected: Boolean,
): String? {
    if (snapshot.needsReauth) {
        return "Voice on this bridge needs a fresh ChatGPT sign-in on your Mac."
    }
    if (snapshot.isAuthenticated && snapshot.isVoiceTokenReady) {
        return null
    }
    if (snapshot.isAuthenticated) {
        return "Waiting for voice sync..."
    }
    if (snapshot.hasActiveLogin && isConnected) {
        return "Finish the ChatGPT sign-in flow in the browser on your Mac."
    }
    if (snapshot.hasActiveLogin) {
        return "Reconnect to your bridge to finish sign-in on your Mac."
    }
    if (!isConnected) {
        return "Connect to your bridge first."
    }
    return "ChatGPT voice uses the account already signed in on your Mac."
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
