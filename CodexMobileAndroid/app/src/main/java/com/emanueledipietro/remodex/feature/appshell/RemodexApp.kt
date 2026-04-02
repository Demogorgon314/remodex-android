package com.emanueledipietro.remodex.feature.appshell

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.feature.onboarding.OnboardingScreen
import com.emanueledipietro.remodex.feature.recovery.PairingScannerScreen
import com.emanueledipietro.remodex.feature.settings.AboutRemodexScreen
import com.emanueledipietro.remodex.feature.settings.ArchivedChatsScreen
import com.emanueledipietro.remodex.feature.settings.SettingsScreen
import com.emanueledipietro.remodex.feature.threads.ThreadsScreen
import com.emanueledipietro.remodex.feature.turn.ConversationScreen
import com.emanueledipietro.remodex.feature.turn.FileChangeDetailSheet
import com.emanueledipietro.remodex.feature.turn.FileChangeSheetPresentation
import com.emanueledipietro.remodex.feature.turn.buildRepositoryDiffSheetPresentation
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexApprovalKind
import com.emanueledipietro.remodex.model.RemodexApprovalRequest
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexBridgeUpdatePrompt
import com.emanueledipietro.remodex.model.RemodexGitDiffTotals
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.normalizeRemodexFilesystemProjectPath
import com.emanueledipietro.remodex.model.remodexApprovalRequestMessage
import com.emanueledipietro.remodex.platform.media.ComposerCameraCapture
import com.emanueledipietro.remodex.platform.media.canLaunchComposerCameraCapture
import com.emanueledipietro.remodex.platform.media.createComposerCameraCapture
import com.emanueledipietro.remodex.platform.media.resolveComposerAttachmentResolution
import com.emanueledipietro.remodex.platform.media.resolveComposerAttachments
import com.emanueledipietro.remodex.platform.notifications.AndroidRemodexNotificationManager
import com.emanueledipietro.remodex.platform.notifications.RemodexNotificationPermissionUiState
import com.emanueledipietro.remodex.platform.window.RemodexWindowLayout
import com.emanueledipietro.remodex.platform.window.remodexWindowLayout
import com.emanueledipietro.remodex.ui.RemodexBrandMark
import com.emanueledipietro.remodex.ui.theme.RemodexConversationShapes
import com.emanueledipietro.remodex.ui.theme.remodexConversationChrome
import kotlinx.coroutines.delay

internal enum class ShellRoute(val title: String) {
    CONTENT("Remodex"),
    SETTINGS("Settings"),
    ABOUT_REMODEX("About Remodex"),
    ARCHIVED_CHATS("Archived Chats"),
}

internal enum class ShellBackAction {
    DISMISS_SCANNER,
    CLOSE_SIDEBAR,
    NAVIGATE_TO_SETTINGS,
    NAVIGATE_TO_CONTENT,
}

internal enum class ShellTopBarTitleLayout {
    CENTERED,
    LEADING,
}

private data class RemodexSystemBarStyle(
    val statusBarColor: Color,
    val navigationBarColor: Color,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean = useDarkStatusBarIcons,
)

internal fun compactSidebarOffset(
    sidebarWidth: Dp,
    contentOffset: Dp,
): Dp {
    return contentOffset - sidebarWidth
}

internal fun resolveShellBackAction(
    isScannerPresented: Boolean,
    isCompactSidebarOpen: Boolean,
    shellRoute: ShellRoute,
): ShellBackAction? {
    return when {
        isScannerPresented -> ShellBackAction.DISMISS_SCANNER
        isCompactSidebarOpen -> ShellBackAction.CLOSE_SIDEBAR
        shellRoute == ShellRoute.ABOUT_REMODEX ||
            shellRoute == ShellRoute.ARCHIVED_CHATS ->
            ShellBackAction.NAVIGATE_TO_SETTINGS
        shellRoute == ShellRoute.SETTINGS -> ShellBackAction.NAVIGATE_TO_CONTENT
        else -> null
    }
}

internal fun resolveShellTopBarTitleLayout(
    shellRoute: ShellRoute,
    hasSelectedThread: Boolean,
): ShellTopBarTitleLayout {
    return if (shellRoute == ShellRoute.CONTENT && hasSelectedThread) {
        ShellTopBarTitleLayout.LEADING
    } else {
        ShellTopBarTitleLayout.CENTERED
    }
}

@Composable
fun RemodexApp(
    viewModel: AppViewModel,
    notificationManager: AndroidRemodexNotificationManager,
    pendingThreadDeepLinkId: String?,
    onThreadDeepLinkHandled: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var shellRoute by rememberSaveable { mutableStateOf(ShellRoute.CONTENT) }
    var isSidebarOpen by rememberSaveable { mutableStateOf(false) }
    var isSidebarSearchActive by rememberSaveable { mutableStateOf(false) }
    var isScannerPresented by rememberSaveable { mutableStateOf(false) }
    var didCopyBridgeUpdateCommand by rememberSaveable { mutableStateOf(false) }
    val conversationChrome = remodexConversationChrome()
    val defaultPageColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationPermissionUiState by remember(notificationManager) {
        mutableStateOf(notificationManager.permissionUiState())
    }
    val refreshNotificationUiState = remember(notificationManager) {
        {
            notificationPermissionUiState = notificationManager.permissionUiState()
            notificationManager.notifyPushRegistrationStateMayHaveChanged()
        }
    }
    var pendingCameraCapture by remember { mutableStateOf<ComposerCameraCapture?>(null) }
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MaxComposerImages),
    ) { uris ->
        if (uris.isNotEmpty()) {
            handleIncomingComposerAttachmentUris(
                context = context,
                viewModel = viewModel,
                uris = uris,
                emptyFailureMessage = "Could not load the selected image.",
                partialFailureMessage = "Some selected images could not be loaded.",
            )
        }
    }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { didCapture ->
        val capture = pendingCameraCapture
        pendingCameraCapture = null
        if (!didCapture || capture == null) {
            capture?.file?.delete()
            return@rememberLauncherForActivityResult
        }

        val attachments = resolveComposerAttachments(context, listOf(capture.uri))
        if (attachments.isEmpty()) {
            capture.file.delete()
            viewModel.presentComposerMessage("Could not load the captured photo.")
            return@rememberLauncherForActivityResult
        }
        viewModel.addAttachments(attachments)
    }
    val launchCameraCapture = {
        val capture = createComposerCameraCapture(context)
        if (capture == null) {
            viewModel.presentComposerMessage("Could not prepare the camera capture. Try again.")
        } else {
            pendingCameraCapture?.file?.delete()
            pendingCameraCapture = capture
            viewModel.presentComposerMessage(null)
            cameraCaptureLauncher.launch(capture.uri)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        refreshNotificationUiState()
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (!isGranted) {
            viewModel.presentComposerMessage(
                "Camera permission was denied. You can still use Photo library or enable camera access in Android Settings.",
            )
            return@rememberLauncherForActivityResult
        }
        launchCameraCapture()
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording()
            return@rememberLauncherForActivityResult
        }

        val activity = context.findActivity()
        val requiresSettings = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false
        viewModel.handleVoicePermissionDenied(requiresSettings = requiresSettings)
    }

    DisposableEffect(lifecycleOwner, notificationManager) {
        viewModel.onAppForegroundChanged(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        notificationManager.setAppForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.onAppForegroundChanged(true)
                    notificationManager.setAppForeground(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onAppForegroundChanged(false)
                    notificationManager.setAppForeground(false)
                }
                Lifecycle.Event.ON_RESUME -> {
                    refreshNotificationUiState()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.onboardingCompleted, pendingThreadDeepLinkId) {
        if (uiState.onboardingCompleted && !pendingThreadDeepLinkId.isNullOrBlank()) {
            viewModel.selectThread(pendingThreadDeepLinkId)
            shellRoute = ShellRoute.CONTENT
            onThreadDeepLinkHandled()
        }
    }

    LaunchedEffect(uiState.threadCompletionBanner?.threadId) {
        val bannerId = uiState.threadCompletionBanner?.threadId ?: return@LaunchedEffect
        delay(4_000)
        if (uiState.threadCompletionBanner?.threadId == bannerId) {
            viewModel.dismissThreadCompletionBanner()
        }
    }

    LaunchedEffect(uiState.transientBanner) {
        val banner = uiState.transientBanner ?: return@LaunchedEffect
        delay(3_500)
        if (uiState.transientBanner == banner) {
            viewModel.dismissTransientBanner()
        }
    }

    LaunchedEffect(
        uiState.bridgeUpdatePrompt?.title,
        uiState.bridgeUpdatePrompt?.message,
        uiState.bridgeUpdatePrompt?.command,
    ) {
        didCopyBridgeUpdateCommand = false
    }

    LaunchedEffect(uiState.completionHapticSignal) {
        if (uiState.completionHapticSignal <= 0L) {
            return@LaunchedEffect
        }
        performRunCompletionHaptic(
            context = context,
            view = view,
        )
    }

    val systemBarStyle = when {
        !uiState.onboardingCompleted -> {
            RemodexSystemBarStyle(
                statusBarColor = Color.Black,
                navigationBarColor = Color.Black,
                useDarkStatusBarIcons = false,
            )
        }

        isScannerPresented -> {
            RemodexSystemBarStyle(
                statusBarColor = Color.Transparent,
                navigationBarColor = Color.Transparent,
                useDarkStatusBarIcons = false,
            )
        }

        shellRoute == ShellRoute.CONTENT -> {
            val barColor = conversationChrome.canvas
            RemodexSystemBarStyle(
                statusBarColor = barColor,
                navigationBarColor = barColor,
                useDarkStatusBarIcons = barColor.luminance() > 0.5f,
            )
        }

        else -> {
            RemodexSystemBarStyle(
                statusBarColor = defaultPageColor,
                navigationBarColor = defaultPageColor,
                useDarkStatusBarIcons = defaultPageColor.luminance() > 0.5f,
            )
        }
    }
    RemodexSystemBars(style = systemBarStyle)

    if (!uiState.onboardingCompleted) {
        OnboardingScreen(
            onContinue = {
                viewModel.prepareForManualScan()
                isScannerPresented = true
                viewModel.completeOnboarding()
            },
        )
        return
    }

    val windowLayout = remodexWindowLayout(LocalConfiguration.current.screenWidthDp)
    val shellBackAction = resolveShellBackAction(
        isScannerPresented = isScannerPresented,
        isCompactSidebarOpen = windowLayout == RemodexWindowLayout.COMPACT && isSidebarOpen,
        shellRoute = shellRoute,
    )
    val handleShellBack = {
        when (
            resolveShellBackAction(
                isScannerPresented = isScannerPresented,
                isCompactSidebarOpen = windowLayout == RemodexWindowLayout.COMPACT && isSidebarOpen,
                shellRoute = shellRoute,
            )
        ) {
            ShellBackAction.DISMISS_SCANNER -> isScannerPresented = false
            ShellBackAction.CLOSE_SIDEBAR -> setSidebarOpen(
                currentOpen = isSidebarOpen,
                nextOpen = false,
                view = view,
            ) { isSidebarOpen = it }
            ShellBackAction.NAVIGATE_TO_SETTINGS -> shellRoute = ShellRoute.SETTINGS
            ShellBackAction.NAVIGATE_TO_CONTENT -> shellRoute = ShellRoute.CONTENT
            null -> Unit
        }
    }
    BackHandler(enabled = shellBackAction != null) {
        handleShellBack()
    }

    RemodexShell(
        viewModel = viewModel,
        uiState = uiState,
        windowLayout = windowLayout,
        shellRoute = shellRoute,
        onShellRouteChange = { shellRoute = it },
        onShellBack = handleShellBack,
        isSidebarOpen = isSidebarOpen,
        onSidebarOpenChange = { nextOpen ->
            setSidebarOpen(
                currentOpen = isSidebarOpen,
                nextOpen = nextOpen,
                view = view,
            ) { isSidebarOpen = it }
        },
        isSidebarSearchActive = isSidebarSearchActive,
        onSidebarSearchActiveChange = { isSidebarSearchActive = it },
        notificationPermissionUiState = notificationPermissionUiState,
        onNotificationAction = {
            when {
                notificationPermissionUiState.canRequestPermission &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                notificationPermissionUiState.requiresSystemSettings -> {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        },
                    )
                }
            }
        },
        onOpenAttachmentPicker = {
            viewModel.presentComposerMessage(null)
            attachmentPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onOpenCameraCapture = {
            if (!canLaunchComposerCameraCapture(context)) {
                viewModel.presentComposerMessage("Camera is not available on this device.")
                return@RemodexShell
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                launchCameraCapture()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onOpenScanner = {
            viewModel.prepareForManualScan()
            isScannerPresented = true
        },
        onRequestVoiceInput = {
            when (uiState.composer.voice.buttonMode) {
                ComposerVoiceButtonMode.RECORDING -> viewModel.stopVoiceRecording()
                ComposerVoiceButtonMode.PREFLIGHTING,
                ComposerVoiceButtonMode.TRANSCRIBING -> Unit
                ComposerVoiceButtonMode.IDLE -> {
                    if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.startVoiceRecording()
                    } else {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        },
        onCancelVoiceRecording = viewModel::cancelVoiceRecording,
    )

    uiState.bridgeUpdatePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBridgeUpdatePrompt,
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = viewModel::dismissBridgeUpdatePrompt) {
                        Text("Not Now")
                    }
                    TextButton(
                        onClick = {
                            viewModel.dismissBridgeUpdatePrompt()
                            viewModel.prepareForManualScan()
                            isScannerPresented = true
                        },
                    ) {
                        Text("Scan New QR Code")
                    }
                    Button(
                        onClick = viewModel::retryConnectionAfterBridgeUpdate,
                    ) {
                        Text("I Updated It")
                    }
                }
            },
            title = {
                DesktopHandoffDialogTitle(
                    title = prompt.title,
                    eyebrow = "Bridge update",
                )
            },
            text = {
                BridgeUpdateDialogBody(
                    prompt = prompt,
                    didCopyCommand = didCopyBridgeUpdateCommand,
                    onCopyCommand = { command ->
                        copyTextToClipboard(
                            context = context,
                            label = "Remodex bridge update command",
                            value = command,
                        )
                        didCopyBridgeUpdateCommand = true
                    },
                )
            },
        )
    }

    if (isScannerPresented) {
        PairingScannerScreen(
            onDismiss = {
                isScannerPresented = false
                viewModel.finishManualScan()
            },
            onPairWithQrPayload = { payload ->
                isScannerPresented = false
                viewModel.finishManualScan()
                viewModel.pairWithQrPayload(payload)
            },
        )
    }
}

private fun performRunCompletionHaptic(
    context: Context,
    view: View,
) {
    val didConfirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        false
    }
    if (didConfirm) {
        return
    }

    val didVibrate = runCatching {
        completionVibrator(context)?.let { vibrator ->
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    vibrator.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK),
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            24L,
                            VibrationEffect.DEFAULT_AMPLITUDE,
                        ),
                    )
                }

                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(24L)
                }
            }
            true
        } ?: false
    }.getOrDefault(false)
    if (didVibrate) {
        return
    }

    val didContextClick = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    if (!didContextClick) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

private fun performLightImpactHaptic(view: View) {
    val didTap = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    if (!didTap) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

private inline fun setSidebarOpen(
    currentOpen: Boolean,
    nextOpen: Boolean,
    view: View,
    updateState: (Boolean) -> Unit,
) {
    if (currentOpen == nextOpen) {
        return
    }

    performLightImpactHaptic(view)
    updateState(nextOpen)
}

private fun completionVibrator(context: Context): Vibrator? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }
}

@Composable
private fun RemodexSystemBars(
    style: RemodexSystemBarStyle,
) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return

    SideEffect {
        val window = activity.window
        window.statusBarColor = style.statusBarColor.toArgb()
        window.navigationBarColor = style.navigationBarColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = style.useDarkStatusBarIcons
            isAppearanceLightNavigationBars = style.useDarkNavigationBarIcons
        }
    }
}

@Composable
private fun RemodexShell(
    viewModel: AppViewModel,
    uiState: AppUiState,
    windowLayout: RemodexWindowLayout,
    shellRoute: ShellRoute,
    onShellRouteChange: (ShellRoute) -> Unit,
    onShellBack: () -> Unit,
    isSidebarOpen: Boolean,
    onSidebarOpenChange: (Boolean) -> Unit,
    isSidebarSearchActive: Boolean,
    onSidebarSearchActiveChange: (Boolean) -> Unit,
    notificationPermissionUiState: RemodexNotificationPermissionUiState,
    onNotificationAction: () -> Unit,
    onOpenAttachmentPicker: () -> Unit,
    onOpenCameraCapture: () -> Unit,
    onOpenScanner: () -> Unit,
    onRequestVoiceInput: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
) {
    val shellBackground = if (shellRoute == ShellRoute.CONTENT) {
        remodexConversationChrome().canvas
    } else {
        MaterialTheme.colorScheme.background
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(shellBackground),
    ) {
        val compact = windowLayout == RemodexWindowLayout.COMPACT
        val sidebarWidth = if (compact && isSidebarSearchActive) {
            maxWidth
        } else {
            330.dp.coerceAtMost(maxWidth)
        }
        val contentOffset by animateDpAsState(
            targetValue = if (compact && isSidebarOpen) sidebarWidth else 0.dp,
            label = "shell_content_offset",
        )

        if (!compact) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 360.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    ThreadsScreen(
                        uiState = uiState,
                        onSelectThread = { threadId ->
                            viewModel.selectThread(threadId)
                            onShellRouteChange(ShellRoute.CONTENT)
                        },
                        onRefreshThreads = viewModel::refreshThreads,
                        onRetryConnection = viewModel::retryConnection,
                        onCreateThread = { preferredProjectPath ->
                            viewModel.createThread(preferredProjectPath) { createdThreadId ->
                                if (createdThreadId != null) {
                                    onShellRouteChange(ShellRoute.CONTENT)
                                }
                            }
                        },
                        onSetProjectGroupCollapsed = viewModel::setProjectGroupCollapsed,
                        onRenameThread = viewModel::renameThread,
                        onArchiveThread = viewModel::archiveThread,
                        onUnarchiveThread = viewModel::unarchiveThread,
                        onDeleteThread = viewModel::deleteThread,
                        onArchiveProject = viewModel::archiveProject,
                        onOpenSettings = { onShellRouteChange(ShellRoute.SETTINGS) },
                        onSearchActiveChange = onSidebarSearchActiveChange,
                    )
                }

                Spacer(modifier = Modifier.width(18.dp))

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    MainPane(
                        viewModel = viewModel,
                        uiState = uiState,
                        shellRoute = shellRoute,
                        compact = false,
                        onMenu = {},
                        onBack = onShellBack,
                        onOpenSettings = { onShellRouteChange(ShellRoute.SETTINGS) },
                        onOpenScanner = onOpenScanner,
                        onOpenArchivedChats = { onShellRouteChange(ShellRoute.ARCHIVED_CHATS) },
                        onOpenAboutRemodex = { onShellRouteChange(ShellRoute.ABOUT_REMODEX) },
                        onNotificationAction = onNotificationAction,
                        notificationPermissionUiState = notificationPermissionUiState,
                        onOpenAttachmentPicker = onOpenAttachmentPicker,
                        onOpenCameraCapture = onOpenCameraCapture,
                        onRequestVoiceInput = onRequestVoiceInput,
                        onCancelVoiceRecording = onCancelVoiceRecording,
                        onNavigateToThreadCompletion = { threadId ->
                            viewModel.selectThread(threadId)
                            onShellRouteChange(ShellRoute.CONTENT)
                            viewModel.dismissThreadCompletionBanner()
                        },
                        onDismissThreadCompletionBanner = viewModel::dismissThreadCompletionBanner,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = contentOffset),
                ) {
                    MainPane(
                        viewModel = viewModel,
                        uiState = uiState,
                        shellRoute = shellRoute,
                        compact = true,
                        onMenu = { onSidebarOpenChange(true) },
                        onBack = onShellBack,
                        onOpenSettings = { onShellRouteChange(ShellRoute.SETTINGS) },
                        onOpenScanner = onOpenScanner,
                        onOpenArchivedChats = { onShellRouteChange(ShellRoute.ARCHIVED_CHATS) },
                        onOpenAboutRemodex = { onShellRouteChange(ShellRoute.ABOUT_REMODEX) },
                        onNotificationAction = onNotificationAction,
                        notificationPermissionUiState = notificationPermissionUiState,
                        onOpenAttachmentPicker = onOpenAttachmentPicker,
                        onOpenCameraCapture = onOpenCameraCapture,
                        onRequestVoiceInput = onRequestVoiceInput,
                        onCancelVoiceRecording = onCancelVoiceRecording,
                        onNavigateToThreadCompletion = { threadId ->
                            viewModel.selectThread(threadId)
                            onShellRouteChange(ShellRoute.CONTENT)
                            viewModel.dismissThreadCompletionBanner()
                        },
                        onDismissThreadCompletionBanner = viewModel::dismissThreadCompletionBanner,
                    )

                    if (isSidebarOpen) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.08f))
                                .clickable { onSidebarOpenChange(false) },
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight()
                        .offset(
                            x = compactSidebarOffset(
                                sidebarWidth = sidebarWidth,
                                contentOffset = contentOffset,
                            ),
                        )
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    ThreadsScreen(
                        uiState = uiState,
                        onSelectThread = { threadId ->
                            viewModel.selectThread(threadId)
                            onShellRouteChange(ShellRoute.CONTENT)
                            onSidebarOpenChange(false)
                        },
                        onRefreshThreads = viewModel::refreshThreads,
                        onRetryConnection = viewModel::retryConnection,
                        onCreateThread = { preferredProjectPath ->
                            viewModel.createThread(preferredProjectPath) { createdThreadId ->
                                if (createdThreadId != null) {
                                    onShellRouteChange(ShellRoute.CONTENT)
                                    onSidebarOpenChange(false)
                                }
                            }
                        },
                        onSetProjectGroupCollapsed = viewModel::setProjectGroupCollapsed,
                        onRenameThread = viewModel::renameThread,
                        onArchiveThread = viewModel::archiveThread,
                        onUnarchiveThread = viewModel::unarchiveThread,
                        onDeleteThread = viewModel::deleteThread,
                        onArchiveProject = viewModel::archiveProject,
                        onOpenSettings = {
                            onShellRouteChange(ShellRoute.SETTINGS)
                            onSidebarOpenChange(false)
                        },
                        onSearchActiveChange = onSidebarSearchActiveChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainPane(
    viewModel: AppViewModel,
    uiState: AppUiState,
    shellRoute: ShellRoute,
    compact: Boolean,
    onMenu: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenArchivedChats: () -> Unit,
    onOpenAboutRemodex: () -> Unit,
    onNotificationAction: () -> Unit,
    notificationPermissionUiState: RemodexNotificationPermissionUiState,
    onOpenAttachmentPicker: () -> Unit,
    onOpenCameraCapture: () -> Unit,
    onRequestVoiceInput: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onNavigateToThreadCompletion: (String) -> Unit,
    onDismissThreadCompletionBanner: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val contentBackground = if (shellRoute == ShellRoute.CONTENT) {
        chrome.canvas
    } else {
        MaterialTheme.colorScheme.background
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    var repositoryDiffSheetPresentation by remember(uiState.selectedThread?.id) {
        mutableStateOf<FileChangeSheetPresentation?>(null)
    }
    val repoDiffTotals = if (shellRoute == ShellRoute.CONTENT) {
        uiState.composer.gitState.sync?.diffTotals
    } else {
        null
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(contentBackground),
    ) {
        ShellTopBar(
            shellRoute = shellRoute,
            selectedThreadTitle = uiState.selectedThread?.displayTitle,
            selectedThreadProjectPath = uiState.selectedThread?.projectPath,
            hasSelectedThread = uiState.selectedThread != null,
            compact = compact,
            repoDiffTotals = repoDiffTotals,
            isLoadingRepoDiff = shellRoute == ShellRoute.CONTENT && uiState.composer.gitState.isLoading,
            onOpenRepoDiff = if (shellRoute == ShellRoute.CONTENT && repoDiffTotals != null) {
                {
                    viewModel.loadRepositoryDiff { diff ->
                        repositoryDiffSheetPresentation = buildRepositoryDiffSheetPresentation(diff.patch)
                    }
                }
            } else {
                null
            },
            onMenu = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                viewModel.closeComposerAutocomplete()
                onMenu()
            },
            onBack = onBack,
        )

        uiState.threadCompletionBanner?.let { banner ->
            ThreadCompletionBanner(
                title = banner.title,
                message = "Answer ready in another chat",
                onOpen = { onNavigateToThreadCompletion(banner.threadId) },
                onDismiss = onDismissThreadCompletionBanner,
            )
        }

        uiState.approvalBanner?.let { banner ->
            ThreadCompletionBanner(
                title = banner.title,
                message = banner.message,
                onOpen = {
                    viewModel.selectThread(banner.threadId)
                    viewModel.dismissApprovalBanner()
                },
                onDismiss = viewModel::dismissApprovalBanner,
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            when (shellRoute) {
                ShellRoute.CONTENT -> {
                    if (uiState.selectedThread == null) {
                        HomeEmptyState(
                            uiState = uiState,
                            onPrimaryAction = {
                                when {
                                    uiState.isConnected -> viewModel.disconnect()
                                    uiState.trustedMac != null -> viewModel.retryConnection()
                                    else -> onOpenScanner()
                                }
                            },
                            onOpenScanner = onOpenScanner,
                            onForgetPair = viewModel::forgetTrustedMac,
                        )
                    } else {
                        val receiveAttachmentContext = LocalContext.current
                        ConversationScreen(
                            uiState = uiState,
                            onRetryConnection = viewModel::retryConnection,
                            onComposerInputChanged = viewModel::updateComposerInput,
                            onSendPrompt = viewModel::sendPrompt,
                            onSubmitStructuredUserInput = viewModel::respondToStructuredUserInput,
                            onSubmitPlanFollowUp = viewModel::sendPlanFollowUp,
                            onDismissPlanComposerSession = viewModel::dismissPlanComposerSession,
                            onStopTurn = viewModel::stopTurn,
                            onRestoreLatestQueuedDraft = viewModel::restoreLatestQueuedDraftToComposer,
                            onSelectModel = viewModel::selectModel,
                            onSelectPlanningMode = viewModel::selectPlanningMode,
                            onSelectReasoningEffort = viewModel::selectReasoningEffort,
                            onSelectAccessMode = viewModel::selectAccessMode,
                            onSelectServiceTier = viewModel::selectServiceTier,
                            onOpenAttachmentPicker = onOpenAttachmentPicker,
                            onOpenCameraCapture = onOpenCameraCapture,
                            onReceiveComposerAttachmentUris = { uris ->
                                handleIncomingComposerAttachmentUris(
                                    context = receiveAttachmentContext,
                                    viewModel = viewModel,
                                    uris = uris,
                                    emptyFailureMessage = "Could not load the pasted image.",
                                    partialFailureMessage = "Some pasted images could not be loaded.",
                                )
                            },
                            onTapVoiceButton = onRequestVoiceInput,
                            onCancelVoiceRecording = onCancelVoiceRecording,
                            onRemoveAttachment = viewModel::removeAttachment,
                            onSelectFileAutocomplete = viewModel::selectFileAutocomplete,
                            onRemoveMentionedFile = viewModel::removeMentionedFile,
                            onSelectSkillAutocomplete = viewModel::selectSkillAutocomplete,
                            onRemoveMentionedSkill = viewModel::removeMentionedSkill,
                            onSelectSlashCommand = viewModel::selectSlashCommand,
                            onSelectCodeReviewTarget = viewModel::selectCodeReviewTarget,
                            onSelectCodeReviewBranch = viewModel::selectCodeReviewBranch,
                            onSelectCodeReviewCommit = viewModel::selectCodeReviewCommit,
                            onClearReviewSelection = viewModel::clearReviewSelection,
                            onClearSubagentsSelection = viewModel::clearSubagentsSelection,
                            onCloseComposerAutocomplete = viewModel::closeComposerAutocomplete,
                            onPrepareForkDestinationSelection = viewModel::prepareForkDestinationSelection,
                            onSelectGitBaseBranch = viewModel::selectGitBaseBranch,
                            onRefreshGitState = viewModel::refreshGitState,
                            onRefreshUsageStatus = viewModel::refreshUsageStatus,
                            onRequestContinueOnMac = viewModel::requestContinueOnMac,
                            onCheckoutGitBranch = viewModel::checkoutGitBranch,
                            onCreateGitBranch = viewModel::createGitBranch,
                            onCreateGitWorktree = viewModel::createGitWorktree,
                            onCommitGitChanges = { viewModel.commitGitChanges() },
                            onCommitAndPushGitChanges = { viewModel.commitAndPushGitChanges() },
                            onPullGitChanges = viewModel::pullGitChanges,
                            onPushGitChanges = viewModel::pushGitChanges,
                            onCreatePullRequest = { viewModel.createPullRequest(uriHandler::openUri) },
                            onDiscardRuntimeChangesAndSync = viewModel::discardRuntimeChangesAndSync,
                            onHandoffThreadToWorktree = viewModel::handoffThreadToWorktree,
                            onForkThreadIntoNewWorktree = viewModel::forkThreadIntoNewWorktree,
                            onForkThread = viewModel::forkThread,
                            onOpenSubagentThread = viewModel::selectThread,
                            onHydrateSubagentThread = viewModel::hydrateThreadMetadata,
                            onStartAssistantRevertPreview = viewModel::startAssistantRevertPreview,
                            onConfirmAssistantRevert = viewModel::confirmAssistantRevert,
                            onDismissAssistantRevertSheet = viewModel::dismissAssistantRevertSheet,
                        )
                    }
                }

                ShellRoute.SETTINGS -> {
                    SettingsScreen(
                        uiState = uiState,
                        notificationPermissionUiState = notificationPermissionUiState,
                        onNotificationAction = onNotificationAction,
                        onSelectAppFontStyle = viewModel::setAppFontStyle,
                        onSelectDefaultModelId = viewModel::setDefaultModelId,
                        onSelectDefaultReasoningEffort = viewModel::setDefaultReasoningEffort,
                        onSelectDefaultAccessMode = viewModel::setDefaultAccessMode,
                        onSelectDefaultServiceTier = viewModel::setDefaultServiceTier,
                        onRefreshSettingsStatus = viewModel::refreshSettingsStatus,
                        onRefreshUsageStatus = viewModel::refreshUsageStatus,
                        onLogoutGptAccount = viewModel::logoutGptAccount,
                        onOpenScanner = onOpenScanner,
                        onDisconnect = viewModel::disconnect,
                        onRetryConnection = viewModel::retryConnection,
                        onForgetTrustedMac = viewModel::forgetTrustedMac,
                        onActivateBridgeProfile = viewModel::activateBridgeProfile,
                        onRemoveBridgeProfile = viewModel::removeBridgeProfile,
                        onSetMacNickname = viewModel::setMacNickname,
                        onOpenArchivedChats = onOpenArchivedChats,
                        onOpenAboutRemodex = onOpenAboutRemodex,
                    )
                }

                ShellRoute.ABOUT_REMODEX -> {
                    AboutRemodexScreen()
                }

                ShellRoute.ARCHIVED_CHATS -> {
                    ArchivedChatsScreen(
                        archivedThreads = uiState.threads.filter { thread ->
                            thread.syncState.name == "ARCHIVED_LOCAL"
                        },
                        onUnarchiveThread = viewModel::unarchiveThread,
                        onDeleteThread = viewModel::deleteThread,
                    )
                }
            }
        }

        repositoryDiffSheetPresentation?.let { presentation ->
            FileChangeDetailSheet(
                title = presentation.title,
                messageId = presentation.messageId,
                renderState = presentation.renderState,
                diffChunks = presentation.diffChunks,
                onDismiss = { repositoryDiffSheetPresentation = null },
            )
        }

        uiState.gitSyncAlert?.let { alert ->
            AlertDialog(
                onDismissRequest = { viewModel.performGitSyncAlertAction(RemodexGitSyncAlertAction.DISMISS_ONLY) },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        alert.buttons.forEach { button ->
                            TextButton(
                                onClick = { viewModel.performGitSyncAlertAction(button.action) },
                            ) {
                                Text(
                                    text = button.title,
                                    color = if (button.role == RemodexGitSyncAlertButtonRole.DESTRUCTIVE) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                        }
                    }
                },
                title = { Text(alert.title) },
                text = { Text(alert.message) },
            )
        }

        uiState.pendingApprovalRequest?.let { request ->
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {
                    ApprovalRequestActions(
                        request = request,
                        onDecline = viewModel::declinePendingApproval,
                        onAllowOnce = viewModel::approvePendingApproval,
                        onAllowForSession = viewModel::approvePendingApprovalForSession,
                        onCancel = viewModel::cancelPendingApproval,
                    )
                },
                title = {
                    Text(
                        if (request.kind == RemodexApprovalKind.PERMISSIONS) {
                            "Permission request"
                        } else {
                            "Approval request"
                        },
                    )
                },
                text = { Text(remodexApprovalRequestMessage(request)) },
            )
        }

        if (uiState.showDesktopHandoffConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDesktopHandoffDialogs,
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDesktopHandoffDialogs) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::confirmContinueOnMac) {
                        Text("Force Close & Continue")
                    }
                },
                title = { DesktopHandoffDialogTitle(title = "Hand off to Mac app") },
                text = { DesktopHandoffConfirmBody() },
            )
        }

        uiState.desktopHandoffErrorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissDesktopHandoffDialogs,
                confirmButton = {
                    Button(onClick = viewModel::dismissDesktopHandoffDialogs) {
                        Text("OK")
                    }
                },
                title = { DesktopHandoffDialogTitle(title = "Couldn't hand off to Mac app") },
                text = { DesktopHandoffErrorBody(message = message) },
            )
        }
    }
}

@Composable
private fun BridgeUpdateDialogBody(
    prompt: RemodexBridgeUpdatePrompt,
    didCopyCommand: Boolean,
    onCopyCommand: (String) -> Unit,
) {
    val chrome = remodexConversationChrome()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = prompt.message,
            style = MaterialTheme.typography.bodyMedium,
            color = chrome.bodyText,
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, chrome.subtleBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Run this on your Mac",
                    style = MaterialTheme.typography.labelMedium,
                    color = chrome.secondaryText,
                )
                Text(
                    text = prompt.command,
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.bodyText,
                )
                TextButton(
                    onClick = { onCopyCommand(prompt.command) },
                ) {
                    Text(if (didCopyCommand) "Copied" else "Copy")
                }
            }
        }
        Text(
            text = "After the package updates, restart the bridge on your Mac and come back here.",
            style = MaterialTheme.typography.bodySmall,
            color = chrome.secondaryText,
        )
    }
}

@Composable
private fun DesktopHandoffDialogTitle(
    title: String,
    eyebrow: String = "Mac handoff",
) {
    val chrome = remodexConversationChrome()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = chrome.sendButton.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, chrome.subtleBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Computer,
                    contentDescription = null,
                    tint = chrome.sendButton,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = chrome.secondaryText,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = chrome.titleText,
            )
        }
    }
}

private fun copyTextToClipboard(
    context: Context,
    label: String,
    value: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun handleIncomingComposerAttachmentUris(
    context: Context,
    viewModel: AppViewModel,
    uris: List<Uri>,
    emptyFailureMessage: String,
    partialFailureMessage: String,
) {
    if (uris.isEmpty()) {
        return
    }

    val resolution = resolveComposerAttachmentResolution(context, uris)
    if (resolution.attachments.isNotEmpty()) {
        viewModel.addAttachments(resolution.attachments)
        if (resolution.failedCount > 0) {
            viewModel.presentComposerMessage(partialFailureMessage)
        }
        return
    }

    if (resolution.failedCount > 0) {
        viewModel.presentComposerMessage(emptyFailureMessage)
    }
}

@Composable
private fun DesktopHandoffConfirmBody() {
    val chrome = remodexConversationChrome()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Remodex will reopen Codex.app on your Mac and jump straight back into this chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = chrome.bodyText,
        )
        DesktopHandoffFactCard(
            title = "Desktop run",
            body = "Any run already in progress on your Mac will be stopped first so the handoff feels like a real device switch.",
        )
        DesktopHandoffFactCard(
            title = "Draft safety",
            body = "Unsaved draft text on Mac may be lost before the thread is reopened there.",
        )
    }
}

@Composable
private fun DesktopHandoffErrorBody(message: String) {
    val chrome = remodexConversationChrome()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "We couldn't reopen the conversation on your Mac just yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = chrome.bodyText,
        )
        Surface(
            color = chrome.panelSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, chrome.subtleBorder),
            shape = RemodexConversationShapes.card,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
            )
        }
    }
}

@Composable
private fun DesktopHandoffFactCard(
    title: String,
    body: String,
) {
    val chrome = remodexConversationChrome()
    Surface(
        color = chrome.panelSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shape = RemodexConversationShapes.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = chrome.sendButton,
            ) {}
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = chrome.titleText,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun ShellTopBar(
    shellRoute: ShellRoute,
    selectedThreadTitle: String?,
    selectedThreadProjectPath: String?,
    hasSelectedThread: Boolean,
    compact: Boolean,
    repoDiffTotals: RemodexGitDiffTotals?,
    isLoadingRepoDiff: Boolean,
    onOpenRepoDiff: (() -> Unit)?,
    onMenu: () -> Unit,
    onBack: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val title = when (shellRoute) {
        ShellRoute.CONTENT -> selectedThreadTitle ?: "Remodex"
        ShellRoute.SETTINGS -> shellRoute.title
        ShellRoute.ABOUT_REMODEX -> shellRoute.title
        ShellRoute.ARCHIVED_CHATS -> shellRoute.title
    }
    val subtitleProjectPath = when (shellRoute) {
        ShellRoute.CONTENT -> {
            if (hasSelectedThread) {
                selectedThreadProjectPath
            } else {
                null
            }
        }
        ShellRoute.SETTINGS,
        ShellRoute.ABOUT_REMODEX,
        ShellRoute.ARCHIVED_CHATS,
        -> null
    }
    val titleLayout = resolveShellTopBarTitleLayout(
        shellRoute = shellRoute,
        hasSelectedThread = hasSelectedThread,
    )
    val titleAlignment = when (titleLayout) {
        ShellTopBarTitleLayout.CENTERED -> Alignment.CenterHorizontally
        ShellTopBarTitleLayout.LEADING -> Alignment.Start
    }
    val titleTextAlign = when (titleLayout) {
        ShellTopBarTitleLayout.CENTERED -> TextAlign.Center
        ShellTopBarTitleLayout.LEADING -> TextAlign.Start
    }
    val rowVerticalAlignment = when (titleLayout) {
        ShellTopBarTitleLayout.CENTERED -> Alignment.CenterVertically
        ShellTopBarTitleLayout.LEADING -> Alignment.Top
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = rowVerticalAlignment,
    ) {
        when {
            shellRoute != ShellRoute.CONTENT -> {
                ShellTopBarButton(
                    onClick = onBack,
                    contentDescription = "Back",
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = chrome.titleText,
                    )
                }
            }

            compact -> {
                ShellTopBarButton(
                    onClick = onMenu,
                    contentDescription = "Menu",
                ) {
                    ShellTopBarMenuGlyph(color = chrome.titleText)
                }
            }

            else -> {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp),
        ) {
            val subtitleStyle = MaterialTheme.typography.labelSmall
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val subtitle = remember(
                subtitleProjectPath,
                maxWidth,
                subtitleStyle,
                density,
            ) {
                val horizontalPadding = 20.dp
                val availableWidthPx = with(density) {
                    (maxWidth - horizontalPadding).coerceAtLeast(0.dp).toPx()
                }
                fitProjectPathForWidth(
                    projectPath = subtitleProjectPath,
                    maxWidthPx = availableWidthPx,
                ) { candidate ->
                    textMeasurer.measure(
                        text = AnnotatedString(candidate),
                        style = subtitleStyle,
                        maxLines = 1,
                    ).size.width.toFloat()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalAlignment = titleAlignment,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = chrome.titleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = titleTextAlign,
                )
                subtitle?.let { path ->
                    Text(
                        text = path,
                        modifier = Modifier.fillMaxWidth(),
                        style = subtitleStyle,
                        color = chrome.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = titleTextAlign,
                    )
                }
            }
        }

        if (shellRoute == ShellRoute.CONTENT && repoDiffTotals != null) {
            ShellTopBarDiffTotalsButton(
                totals = repoDiffTotals,
                isLoading = isLoadingRepoDiff,
                onClick = onOpenRepoDiff,
            )
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun ShellTopBarButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val chrome = remodexConversationChrome()
    Surface(
        modifier = Modifier
            .size(40.dp)
            .semantics {
                this.role = Role.Button
                this.contentDescription = contentDescription
            }
            .clickable(onClick = onClick),
        color = chrome.mutedSurface,
        shape = CircleShape,
        border = BorderStroke(1.dp, chrome.subtleBorder),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun ShellTopBarMenuGlyph(
    color: Color,
) {
    Box(
        modifier = Modifier.size(width = 20.dp, height = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 1.5.dp)
                    .background(
                        color = color,
                        shape = RoundedCornerShape(1.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 1.5.dp)
                    .background(
                        color = color,
                        shape = RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

@Composable
private fun ShellTopBarDiffTotalsButton(
    totals: RemodexGitDiffTotals,
    isLoading: Boolean,
    onClick: (() -> Unit)?,
) {
    val chrome = remodexConversationChrome()
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.6.dp,
                    color = chrome.secondaryText,
                )
            }
            Text(
                text = "+${totals.additions}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF22A653),
            )
            Text(
                text = "-${totals.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE04646),
            )
            if (totals.binaryFiles > 0) {
                Text(
                    text = "B${totals.binaryFiles}",
                    style = MaterialTheme.typography.labelSmall,
                    color = chrome.secondaryText,
                )
            }
        }
    }

    Surface(
        modifier = Modifier
            .clickable(enabled = onClick != null && !isLoading) { onClick?.invoke() },
        color = chrome.mutedSurface,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, chrome.subtleBorder),
    ) {
        content()
    }
}

internal fun fitProjectPathForWidth(
    projectPath: String?,
    maxWidthPx: Float,
    measureTextWidth: (String) -> Float,
): String? {
    val candidates = projectPathDisplayCandidates(projectPath)
    if (candidates.isEmpty()) {
        return null
    }
    if (maxWidthPx <= 0f) {
        return candidates.last()
    }
    return candidates.firstOrNull { candidate ->
        measureTextWidth(candidate) <= maxWidthPx
    } ?: candidates.last()
}

internal fun projectPathDisplayCandidates(projectPath: String?): List<String> {
    val normalized = normalizeRemodexFilesystemProjectPath(projectPath)
        ?.replace('\\', '/')
        ?: return emptyList()
    val segments = normalized
        .trim('/')
        .split('/')
        .filter(String::isNotBlank)
    if (segments.size <= 4) {
        return listOf(normalized)
    }

    val prefix = projectPathPrefix(normalized)
    val candidateHeads = listOf(2, 1, 0)
    val candidateTails = listOf(2, 1)
    val candidates = linkedSetOf(normalized)

    candidateTails.forEach { tailCount ->
        candidateHeads.forEach { headCount ->
            if (headCount + tailCount >= segments.size) {
                return@forEach
            }
            candidates += buildCondensedProjectPathCandidate(
                prefix = prefix,
                head = segments.take(headCount),
                tail = segments.takeLast(tailCount),
            )
        }
    }

    return candidates.toList()
}

private fun projectPathPrefix(path: String): String {
    return when {
        path.startsWith("~/") -> "~/"
        path.startsWith("//") -> "//"
        path.startsWith("/") -> "/"
        path.length >= 3 &&
            path[0].isLetter() &&
            path[1] == ':' &&
            path[2] == '/' -> path.take(3)
        else -> ""
    }
}

private fun buildCondensedProjectPathCandidate(
    prefix: String,
    head: List<String>,
    tail: List<String>,
): String {
    val body = buildList {
        addAll(head)
        add("...")
        addAll(tail)
    }.joinToString("/")
    return prefix + body
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ThreadCompletionBanner(
    title: String,
    message: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(1.dp, chrome.subtleBorder, RemodexConversationShapes.card)
            .clickable(onClick = onOpen),
        color = chrome.panelSurface,
        shape = RemodexConversationShapes.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ) {}
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = chrome.bodyText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun HomeEmptyState(
    uiState: AppUiState,
    onPrimaryAction: () -> Unit,
    onOpenScanner: () -> Unit,
    onForgetPair: () -> Unit,
) {
    val statusLabel = when {
        uiState.isConnected -> "Connected"
        uiState.trustedMac != null -> "Saved pairing"
        else -> "Offline"
    }
    val primaryTitle = when {
        uiState.isConnected -> "Disconnect"
        uiState.trustedMac != null -> "Reconnect"
        else -> "Scan QR Code"
    }
    val statusColor = when {
        uiState.isConnected -> MaterialTheme.colorScheme.primary
        uiState.trustedMac != null -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RemodexBrandMark(
            modifier = Modifier.size(88.dp),
            cornerRadius = 22.dp,
        )

        Spacer(modifier = Modifier.size(20.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color = statusColor,
                ) {}
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.size(18.dp))

        uiState.trustedMac?.let { trustedMac ->
            Text(
                text = trustedMac.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            trustedMac.detail?.takeIf(String::isNotBlank)?.let { detail ->
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = uiState.connectionMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.size(20.dp))

        Button(onClick = onPrimaryAction) {
            Text(primaryTitle)
        }

        if (uiState.trustedMac != null && !uiState.isConnected) {
            Spacer(modifier = Modifier.size(8.dp))
            TextButton(onClick = onOpenScanner) {
                Text("Scan New QR Code")
            }
            TextButton(onClick = onForgetPair) {
                Text("Forget Pair")
            }
        }
    }
}

@Composable
private fun ApprovalRequestActions(
    request: RemodexApprovalRequest,
    onDecline: () -> Unit,
    onAllowOnce: () -> Unit,
    onAllowForSession: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onDecline) {
                Text("Decline")
            }
            if (request.kind != RemodexApprovalKind.PERMISSIONS) {
                TextButton(onClick = onCancel) {
                    Text("Stop turn")
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onAllowForSession) {
                Text("Allow this session")
            }
            Button(onClick = onAllowOnce) {
                Text("Allow once")
            }
        }
    }
}

private const val MaxComposerImages = 4
