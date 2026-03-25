package com.emanueledipietro.remodex.feature.recovery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.emanueledipietro.remodex.data.connection.PairingBridgeUpdatePrompt
import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.data.connection.PairingQrValidationResult
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.statusLabel
import com.emanueledipietro.remodex.data.connection.validatePairingQrCode

@Composable
fun RecoveryScreen(
    recoveryState: SecureConnectionSnapshot,
    connectionHeadline: String,
    connectionMessage: String,
    onPairWithQrPayload: (PairingQrPayload) -> Unit,
    onRetryConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var cameraDenied by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var bridgeUpdatePrompt by remember { mutableStateOf<PairingBridgeUpdatePrompt?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraDenied = !granted
        showScanner = granted
    }

    fun requestScanner() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            showScanner = true
            cameraDenied = false
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(30.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "QR pairing stays in the loop",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Android keeps recovery explicit so a stale trusted session never leaves you stranded.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        RecoveryChecklistCard(
            headline = connectionHeadline,
            message = connectionMessage,
            onOpenScanner = {
                scanError = null
                bridgeUpdatePrompt = null
                requestScanner()
            },
            onRetryConnection = onRetryConnection,
        )

        RecoveryConnectionCard(
            recoveryState = recoveryState,
            onRetryConnection = onRetryConnection,
            onOpenScanner = { requestScanner() },
        )

        if (cameraDenied) {
            CameraPermissionCard(
                onRetry = { requestScanner() },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
            )
        }

        if (showScanner) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Scan pairing QR",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Point the camera at the QR printed by remodex up. The Android client validates the payload before it tries any secure bootstrap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PairingScannerView(
                        onScan = { code ->
                            when (val result = validatePairingQrCode(code)) {
                                is PairingQrValidationResult.Success -> {
                                    scanError = null
                                    bridgeUpdatePrompt = null
                                    showScanner = false
                                    onPairWithQrPayload(result.payload)
                                }

                                is PairingQrValidationResult.ScanError -> {
                                    scanError = result.message
                                    bridgeUpdatePrompt = null
                                    showScanner = false
                                }

                                is PairingQrValidationResult.BridgeUpdateRequired -> {
                                    bridgeUpdatePrompt = result.prompt
                                    scanError = null
                                    showScanner = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                    )
                }
            }
        }

        scanError?.let { message ->
            RecoveryStatusCard(
                title = "Pairing QR rejected",
                body = message,
                actionLabel = "Scan a different QR",
                onAction = {
                    scanError = null
                    requestScanner()
                },
            )
        }

        bridgeUpdatePrompt?.let { prompt ->
            RecoveryStatusCard(
                title = prompt.title,
                body = "${prompt.message}\n\nRun: ${prompt.command}",
                actionLabel = "Scan again after updating",
                onAction = {
                    bridgeUpdatePrompt = null
                    requestScanner()
                },
            )
        }
    }
}

@Composable
private fun RecoveryConnectionCard(
    recoveryState: SecureConnectionSnapshot,
    onRetryConnection: () -> Unit,
    onOpenScanner: () -> Unit,
) {
    val body = buildString {
        append(recoveryState.phaseMessage)
        recoveryState.macDeviceId?.let { macDeviceId ->
            append("\n\nMac: ")
            append(macDeviceId)
        }
        recoveryState.macFingerprint?.let { fingerprint ->
            append("\nFingerprint: ")
            append(fingerprint)
        }
        recoveryState.bridgeUpdateCommand?.let { command ->
            append("\n\nUpdate command: ")
            append(command)
        }
    }
    val action = when (recoveryState.secureState) {
        SecureConnectionState.TRUSTED_MAC,
        SecureConnectionState.LIVE_SESSION_UNRESOLVED,
        SecureConnectionState.RECONNECTING,
        SecureConnectionState.HANDSHAKING -> "Retry trusted reconnect" to onRetryConnection

        SecureConnectionState.NOT_PAIRED,
        SecureConnectionState.REPAIR_REQUIRED,
        SecureConnectionState.UPDATE_REQUIRED -> "Open QR scanner" to onOpenScanner

        SecureConnectionState.ENCRYPTED -> null
    }

    RecoveryStatusCard(
        title = recoveryState.secureState.statusLabel,
        body = body,
        actionLabel = action?.first,
        onAction = action?.second,
    )
}

@Composable
private fun RecoveryChecklistCard(
    headline: String,
    message: String,
    onOpenScanner: () -> Unit,
    onRetryConnection: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "1. Run remodex up on your Mac.\n2. Keep the pairing QR visible.\n3. Retry trusted reconnect here or rescan when the secure session has rotated.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRetryConnection) {
                Text("Retry trusted reconnect")
            }
            Button(onClick = onOpenScanner) {
                Text("Open QR scanner")
            }
        }
    }
}

@Composable
private fun CameraPermissionCard(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Camera access is off",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Android only asks for camera permission when you open the scanner. Grant access to scan a fresh pairing QR, or open system settings if permission was denied earlier.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("Try camera permission again")
            }
            Button(onClick = onOpenSettings) {
                Text("Open app settings")
            }
        }
    }
}

@Composable
private fun RecoveryStatusCard(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
