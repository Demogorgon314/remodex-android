package com.emanueledipietro.remodex.feature.mymacs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.feature.appshell.AppViewModel
import com.emanueledipietro.remodex.model.RemodexBridgeProfilePresentation
import java.util.Locale

@Composable
fun MyMacsScreen(
    uiState: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles = uiState.bridgeProfiles
    val isSwitching = uiState.isSwitchingMac
    val currentProfile = resolveCurrentMacProfile(profiles)
    val hasRunningTurn = remember(uiState.threads) {
        uiState.threads.any { it.isRunning }
    }

    var pendingForgetDeviceId by remember { mutableStateOf<String?>(null) }
    var pendingSwitchDeviceId by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Switch notice banner (like iOS switchNoticeBanner)
            uiState.macSwitchNotice?.takeIf { it.isNotEmpty() && !isSwitching }?.let { notice ->
                MyMacCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Current Mac section (like iOS currentMacCard)
            if (currentProfile != null) {
                SectionTitle("Current Mac")
                MyMacCard {
                    CurrentMacRow(
                        profile = currentProfile,
                        isSwitching = isSwitching,
                        switchingMacDeviceId = uiState.switchingMacDeviceId,
                    )
                }
            }

            // Paired Macs section (like iOS pairedMacsCard — shows ALL macs)
            SectionTitle("Paired Macs")
            MyMacCard {
                if (profiles.isEmpty()) {
                    Text(
                        text = "No paired Macs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    Column {
                        profiles.forEachIndexed { index, profile ->
                            PairedMacRow(
                                profile = profile,
                                isCurrent = profile.profileId == currentProfile?.profileId,
                                isSwitchingTarget = profile.macDeviceId == uiState.switchingMacDeviceId,
                                isSwitchingAny = isSwitching,
                                onSwitch = { deviceId ->
                                    pendingSwitchDeviceId = deviceId
                                },
                                onForget = { deviceId -> pendingForgetDeviceId = deviceId },
                            )
                            if (index < profiles.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 54.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                )
                            }
                        }
                    }
                }
            }

            // Scan QR Code button (like iOS scanButton)
            ScanQrButton(
                onClick = onNavigateToQrScanner,
                enabled = !isSwitching,
            )
        }

        // Switching overlay (like iOS switchingOverlay)
        if (isSwitching) {
            SwitchingOverlay(
                switchingMacDeviceId = uiState.switchingMacDeviceId,
                connectionPhaseLabel = uiState.connectionHeadline.takeIf { it != "Offline" },
                profiles = profiles,
                onCancel = viewModel::requestMacSwitchCancellation,
            )
        }
    }

    // Switch confirmation dialog (like iOS confirmationDialog)
    pendingSwitchDeviceId?.let { deviceId ->
        val targetProfile = profiles.firstOrNull { it.macDeviceId == deviceId }
        val targetName = targetProfile?.name ?: "this Mac"
        AlertDialog(
            onDismissRequest = { pendingSwitchDeviceId = null },
            title = {
                Text(
                    "Switch Mac?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    "Switching Macs will disconnect the current session, stop any in-progress runs, and may discard unfinished output.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSwitchDeviceId = null
                        viewModel.switchToTrustedMac(deviceId)
                    },
                ) {
                    Text("Switch Mac", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitchDeviceId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Forget confirmation dialog (like iOS alert)
    pendingForgetDeviceId?.let { deviceId ->
        AlertDialog(
            onDismissRequest = { pendingForgetDeviceId = null },
            title = {
                Text(
                    "Forget this Mac?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    "The paired Mac will be removed from this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingForgetDeviceId = null
                        viewModel.forgetMac(deviceId)
                    },
                ) {
                    Text("Forget", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingForgetDeviceId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Section title (like iOS sectionTitle)
// ---------------------------------------------------------------------------
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// Reusable card wrapper (like iOS MyMacCard)
// ---------------------------------------------------------------------------
@Composable
private fun MyMacCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Mac avatar (like iOS macAvatar)
// ---------------------------------------------------------------------------
@Composable
private fun MacAvatar() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Computer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Current Mac row (like iOS currentMacCard content)
// ---------------------------------------------------------------------------
@Composable
private fun CurrentMacRow(
    profile: RemodexBridgeProfilePresentation,
    isSwitching: Boolean,
    switchingMacDeviceId: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacAvatar()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleSmall,
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

            Text(
                text = buildString {
                    append(currentMacConnectionLabel(profile))
                    profile.detail?.let { detail ->
                        append(" · ")
                        append(detail)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isSwitching && switchingMacDeviceId == profile.macDeviceId) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun resolveCurrentMacProfile(
    profiles: List<RemodexBridgeProfilePresentation>,
): RemodexBridgeProfilePresentation? {
    return profiles.firstOrNull(RemodexBridgeProfilePresentation::isActive)
        ?: profiles.firstOrNull(RemodexBridgeProfilePresentation::isConnected)
}

internal fun currentMacConnectionLabel(profile: RemodexBridgeProfilePresentation): String {
    return if (profile.isConnected) "Connected" else "Selected"
}

// ---------------------------------------------------------------------------
// Paired Mac row (like iOS pairedMacRow)
// ---------------------------------------------------------------------------
@Composable
private fun PairedMacRow(
    profile: RemodexBridgeProfilePresentation,
    isCurrent: Boolean,
    isSwitchingTarget: Boolean,
    isSwitchingAny: Boolean,
    onSwitch: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (!isCurrent && !isSwitchingAny && profile.macDeviceId != null) {
                    mod.clickable { onSwitch(profile.macDeviceId) }
                } else {
                    mod
                }
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacAvatar()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isCurrent) {
                    Text(
                        text = "Current",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            profile.systemName?.let { systemName ->
                Text(
                    text = systemName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = buildString {
                    append(
                        when {
                            isSwitchingTarget -> "Switching"
                            isCurrent && profile.isConnected -> "Connected"
                            isCurrent -> "Selected"
                            else -> "Saved"
                        },
                    )
                    if (isSwitchingTarget) {
                        append(" · Reloading chats…")
                    } else {
                        profile.detail?.let { detail ->
                            append(" · ")
                            append(detail)
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Switching indicator or chevron
        if (isSwitchingTarget) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!isCurrent) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(12.dp),
            )
        }

        // Ellipsis menu button (like iOS ellipsis.circle)
        Box {
            IconButton(
                onClick = { showMenu = true },
                enabled = !isSwitchingAny,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Forget this Mac",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        showMenu = false
                        profile.macDeviceId?.let(onForget)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scan QR Code button (like iOS scanButton)
// ---------------------------------------------------------------------------
@Composable
private fun ScanQrButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (enabled) mod.clickable(onClick = onClick) else mod
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.88f else 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.5f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Scan QR Code",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Switching overlay (Material 3 style)
// ---------------------------------------------------------------------------
@Composable
private fun SwitchingOverlay(
    switchingMacDeviceId: String?,
    connectionPhaseLabel: String?,
    profiles: List<RemodexBridgeProfilePresentation>,
    onCancel: () -> Unit,
) {
    val targetProfile = switchingMacDeviceId?.let { deviceId ->
        profiles.firstOrNull { it.macDeviceId == deviceId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .widthIn(max = 320.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Text(
                    text = "Switching Mac",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                connectionPhaseLabel?.let { phaseLabel ->
                    Text(
                        text = phaseLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                targetProfile?.let { profile ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Computer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = profile.systemName ?: "Preparing secure reconnect",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                FilledTonalButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
