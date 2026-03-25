package com.emanueledipietro.remodex.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanueledipietro.remodex.data.connection.statusLabel
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexRuntimeMetaMapper
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.platform.notifications.RemodexNotificationPermissionUiState

@Composable
fun SettingsScreen(
    uiState: AppUiState,
    notificationPermissionUiState: RemodexNotificationPermissionUiState,
    onNotificationAction: () -> Unit,
    onSelectAppearanceMode: (RemodexAppearanceMode) -> Unit,
    onSelectDefaultModelId: (String?) -> Unit,
    onSelectDefaultReasoningEffort: (String?) -> Unit,
    onSelectDefaultAccessMode: (RemodexAccessMode) -> Unit,
    onSelectDefaultServiceTier: (RemodexServiceTier?) -> Unit,
    onDisconnect: () -> Unit,
    onForgetTrustedMac: () -> Unit,
    onOpenArchivedChats: () -> Unit,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val archivedThreads = uiState.threads.filter { thread -> thread.syncState.name == "ARCHIVED_LOCAL" }
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
    val managedPushSummary = buildString {
        if (uiState.notificationRegistration.managedPushSupported) {
            append("Managed push is configured for Android")
            uiState.notificationRegistration.pushProvider?.let { provider ->
                append(" via ${provider.name}.")
            }
        } else {
            append("Managed push is unavailable until Firebase is configured for this Android build.")
        }
        uiState.notificationRegistration.lastErrorMessage?.takeIf(String::isNotBlank)?.let { error ->
            append("\n\n")
            append(error)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSection(title = "Archived Chats") {
            SettingsLinkRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                title = "Archived chats",
                subtitle = if (archivedThreads.isEmpty()) {
                    "No archived chats yet."
                } else {
                    "${archivedThreads.size} chats are stored locally on this Android device."
                },
                onClick = onOpenArchivedChats,
            )
        }

        SettingsSection(title = "Appearance") {
            SettingsButtonRow(
                options = RemodexAppearanceMode.entries.map { mode -> mode.name to mode.label },
                selectedKey = uiState.appearanceMode.name,
                onSelected = { key -> onSelectAppearanceMode(RemodexAppearanceMode.valueOf(key)) },
            )
        }

        SettingsSection(title = "Notifications") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = notificationPermissionUiState.headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = notificationPermissionUiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (notificationPermissionUiState.actionLabel != null) {
                OutlinedButton(onClick = onNotificationAction) {
                    Text(notificationPermissionUiState.actionLabel)
                }
            }
            Text(
                text = managedPushSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(title = "Runtime defaults") {
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
                currentLabel = uiState.runtimeDefaults.accessMode.label,
                options = RemodexAccessMode.entries.map { mode ->
                    SettingsOption(mode.name, mode.label)
                },
                onSelected = { key -> onSelectDefaultAccessMode(RemodexAccessMode.valueOf(key)) },
            )
        }

        SettingsSection(title = "About") {
            Text(
                text = "Remodex for Android follows the same local-first pairing flow as iOS and keeps chats grouped by local project path.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SettingsSection(title = "Usage") {
            UsageMetricRow(label = "Projects", value = uiState.threads.map { it.projectPath }.distinct().size.toString())
            UsageMetricRow(label = "Chats", value = uiState.threads.size.toString())
            UsageMetricRow(
                label = "Running",
                value = uiState.threads.count { thread -> thread.isRunning }.toString(),
            )
        }

        SettingsSection(title = "Connection") {
            if (uiState.trustedMac == null) {
                Text(
                    text = "No paired Mac",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pair this Android device with your local Remodex bridge to enable trusted reconnect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = uiState.trustedMac.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = uiState.trustedMac.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                uiState.trustedMac.detail?.takeIf(String::isNotBlank)?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = uiState.connectionMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Current state: ${uiState.recoveryState.secureState.statusLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(onClick = onOpenScanner) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                    )
                    Text(
                        text = "Scan QR Code",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (uiState.isConnected) {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                } else if (uiState.trustedMac != null) {
                    OutlinedButton(onClick = onForgetTrustedMac) {
                        Text("Forget Pair")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsLinkRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    )
                    .padding(8.dp),
            ) {
                icon()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsageMetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsButtonRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            val selected = selectedKey == key
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = { onSelected(key) },
                enabled = !selected,
            ) {
                Text(label)
            }
        }
    }
}

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
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(currentLabel)
            }
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

private data class SettingsOption(
    val key: String,
    val label: String,
)
