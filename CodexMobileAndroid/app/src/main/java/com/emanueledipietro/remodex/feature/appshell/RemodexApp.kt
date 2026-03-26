package com.emanueledipietro.remodex.feature.appshell

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexGitDiffTotals
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexServiceTier
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

private data class RemodexSystemBarStyle(
    val statusBarColor: Color,
    val navigationBarColor: Color,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean = useDarkStatusBarIcons,
)

internal fun resolveShellBackAction(
    isScannerPresented: Boolean,
    isCompactSidebarOpen: Boolean,
    shellRoute: ShellRoute,
): ShellBackAction? {
    return when {
        isScannerPresented -> ShellBackAction.DISMISS_SCANNER
        isCompactSidebarOpen -> ShellBackAction.CLOSE_SIDEBAR
        shellRoute == ShellRoute.ABOUT_REMODEX || shellRoute == ShellRoute.ARCHIVED_CHATS ->
            ShellBackAction.NAVIGATE_TO_SETTINGS
        shellRoute == ShellRoute.SETTINGS -> ShellBackAction.NAVIGATE_TO_CONTENT
        else -> null
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
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MaxComposerImages),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addAttachments(resolveComposerAttachments(context, uris))
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        refreshNotificationUiState()
    }

    DisposableEffect(lifecycleOwner, notificationManager) {
        viewModel.onAppForegroundChanged(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegroundChanged(true)
                Lifecycle.Event.ON_STOP -> viewModel.onAppForegroundChanged(false)
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
            ShellBackAction.CLOSE_SIDEBAR -> isSidebarOpen = false
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
        onSidebarOpenChange = { isSidebarOpen = it },
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
            attachmentPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onOpenScanner = {
            viewModel.prepareForManualScan()
            isScannerPresented = true
        },
    )

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
    val didVibrate = runCatching {
        completionVibrator(context)?.let { vibrator ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 28L, 44L, 38L),
                        -1,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0L, 28L, 44L, 38L), -1)
            }
            true
        } ?: false
    }.getOrDefault(false)
    if (didVibrate) {
        return
    }
    val didConfirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        false
    }
    if (!didConfirm) {
        val didContextClick = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (!didContextClick) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
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
    onOpenScanner: () -> Unit,
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
                            viewModel.createThread(preferredProjectPath)
                            onShellRouteChange(ShellRoute.CONTENT)
                        },
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
                        onOpenScanner = onOpenScanner,
                        onOpenArchivedChats = { onShellRouteChange(ShellRoute.ARCHIVED_CHATS) },
                        onOpenAboutRemodex = { onShellRouteChange(ShellRoute.ABOUT_REMODEX) },
                        onNotificationAction = onNotificationAction,
                        notificationPermissionUiState = notificationPermissionUiState,
                        onOpenAttachmentPicker = onOpenAttachmentPicker,
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
                Surface(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight()
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
                            viewModel.createThread(preferredProjectPath)
                            onShellRouteChange(ShellRoute.CONTENT)
                            onSidebarOpenChange(false)
                        },
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
                        onOpenScanner = onOpenScanner,
                        onOpenArchivedChats = { onShellRouteChange(ShellRoute.ARCHIVED_CHATS) },
                        onOpenAboutRemodex = { onShellRouteChange(ShellRoute.ABOUT_REMODEX) },
                        onNotificationAction = onNotificationAction,
                        notificationPermissionUiState = notificationPermissionUiState,
                        onOpenAttachmentPicker = onOpenAttachmentPicker,
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
    onOpenScanner: () -> Unit,
    onOpenArchivedChats: () -> Unit,
    onOpenAboutRemodex: () -> Unit,
    onNotificationAction: () -> Unit,
    notificationPermissionUiState: RemodexNotificationPermissionUiState,
    onOpenAttachmentPicker: () -> Unit,
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
                onOpen = { onNavigateToThreadCompletion(banner.threadId) },
                onDismiss = onDismissThreadCompletionBanner,
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
                        ConversationScreen(
                            uiState = uiState,
                            onRetryConnection = viewModel::retryConnection,
                            onComposerInputChanged = viewModel::updateComposerInput,
                            onSendPrompt = viewModel::sendPrompt,
                            onStopTurn = viewModel::stopTurn,
                            onSendQueuedDraft = viewModel::sendQueuedDraft,
                            onSelectModel = viewModel::selectModel,
                            onSelectPlanningMode = viewModel::selectPlanningMode,
                            onSelectReasoningEffort = viewModel::selectReasoningEffort,
                            onSelectAccessMode = viewModel::selectAccessMode,
                            onSelectServiceTier = viewModel::selectServiceTier,
                            onOpenAttachmentPicker = onOpenAttachmentPicker,
                            onRemoveAttachment = viewModel::removeAttachment,
                            onSelectFileAutocomplete = viewModel::selectFileAutocomplete,
                            onRemoveMentionedFile = viewModel::removeMentionedFile,
                            onSelectSkillAutocomplete = viewModel::selectSkillAutocomplete,
                            onRemoveMentionedSkill = viewModel::removeMentionedSkill,
                            onSelectSlashCommand = viewModel::selectSlashCommand,
                            onSelectCodeReviewTarget = viewModel::selectCodeReviewTarget,
                            onClearReviewSelection = viewModel::clearReviewSelection,
                            onClearSubagentsSelection = viewModel::clearSubagentsSelection,
                            onCloseComposerAutocomplete = viewModel::closeComposerAutocomplete,
                            onSelectGitBaseBranch = viewModel::selectGitBaseBranch,
                            onRefreshGitState = viewModel::refreshGitState,
                            onRefreshUsageStatus = viewModel::refreshUsageStatus,
                            onCheckoutGitBranch = viewModel::checkoutGitBranch,
                            onCreateGitBranch = viewModel::createGitBranch,
                            onCreateGitWorktree = viewModel::createGitWorktree,
                            onCommitGitChanges = { viewModel.commitGitChanges() },
                            onPullGitChanges = viewModel::pullGitChanges,
                            onPushGitChanges = viewModel::pushGitChanges,
                            onDiscardRuntimeChangesAndSync = viewModel::discardRuntimeChangesAndSync,
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
                        onDisconnect = viewModel::disconnect,
                        onForgetTrustedMac = viewModel::forgetTrustedMac,
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
    }
}

@Composable
private fun ShellTopBar(
    shellRoute: ShellRoute,
    selectedThreadTitle: String?,
    selectedThreadProjectPath: String?,
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
    val subtitle = when (shellRoute) {
        ShellRoute.CONTENT -> condensedProjectPath(selectedThreadProjectPath)
        ShellRoute.SETTINGS,
        ShellRoute.ABOUT_REMODEX,
        ShellRoute.ARCHIVED_CHATS,
        -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
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
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = null,
                        tint = chrome.titleText,
                    )
                }
            }

            else -> {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = chrome.titleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = chrome.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

private fun condensedProjectPath(projectPath: String?): String? {
    val normalized = projectPath
        ?.trim()
        ?.replace('\\', '/')
        ?.takeIf(String::isNotBlank)
        ?: return null
    val segments = normalized.split('/').filter(String::isNotBlank)
    if (segments.size <= 4) {
        return normalized
    }
    val head = segments.take(2).joinToString("/")
    val tail = segments.takeLast(2).joinToString("/")
    return "/$head/.../$tail"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ThreadCompletionBanner(
    title: String,
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
                    text = "Answer ready in another chat",
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

private const val MaxComposerImages = 4
