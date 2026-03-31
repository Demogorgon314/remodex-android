package com.emanueledipietro.remodex.feature.appshell

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.emanueledipietro.remodex.data.app.RemodexAppRepository
import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.platform.media.AndroidVoiceRecorder
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantResponseMetrics
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.RemodexAssistantRevertPresentation
import com.emanueledipietro.remodex.model.RemodexAssistantRevertRiskLevel
import com.emanueledipietro.remodex.model.RemodexAssistantRevertSheetState
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexApprovalRequest
import com.emanueledipietro.remodex.model.RemodexApprovalKind
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexBridgeUpdatePrompt
import com.emanueledipietro.remodex.model.RemodexBridgeProfilePresentation
import com.emanueledipietro.remodex.model.RemodexBridgeVersionStatus
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerAutocompletePanel
import com.emanueledipietro.remodex.model.RemodexComposerAutocompleteState
import com.emanueledipietro.remodex.model.RemodexComposerCommandLogic
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerMentionedFile
import com.emanueledipietro.remodex.model.RemodexComposerMentionedSkill
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexComposerReviewSelection
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexGptVoiceStatus
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitWorktreeChangeTransferMode
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexNotificationRegistrationState
import com.emanueledipietro.remodex.model.RemodexPermissionGrantScope
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexSlashCommand
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexTurnTerminalState
import com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation
import com.emanueledipietro.remodex.model.RemodexUsageStatus
import com.emanueledipietro.remodex.model.remodexGptVoiceStatus
import com.emanueledipietro.remodex.model.remodexApprovalRequestSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

data class ComposerUiState(
    val draftText: String = "",
    val sendLabel: String = "Send",
    val canSend: Boolean = false,
    val canStop: Boolean = false,
    val voice: ComposerVoiceUiState = ComposerVoiceUiState(),
    val mentionedFiles: List<RemodexComposerMentionedFile> = emptyList(),
    val mentionedSkills: List<RemodexComposerMentionedSkill> = emptyList(),
    val reviewSelection: RemodexComposerReviewSelection? = null,
    val isSubagentsSelectionArmed: Boolean = false,
    val queuedDrafts: List<RemodexQueuedDraft> = emptyList(),
    val attachments: List<RemodexComposerAttachment> = emptyList(),
    val attachmentLimitMessage: String? = null,
    val composerMessage: String? = null,
    val voiceRecoveryReason: ComposerVoiceRecoveryReason? = null,
    val voiceRecovery: ComposerVoiceRecoveryUiState? = null,
    val maxAttachments: Int = 4,
    val runtimeConfig: RemodexRuntimeConfig = RemodexRuntimeConfig(),
    val autocomplete: RemodexComposerAutocompleteState = RemodexComposerAutocompleteState(),
    val gitState: RemodexGitState = RemodexGitState(),
    val selectedGitBaseBranch: String = "",
)

enum class ComposerVoiceButtonMode {
    IDLE,
    PREFLIGHTING,
    RECORDING,
    TRANSCRIBING,
}

data class ComposerVoiceUiState(
    val buttonMode: ComposerVoiceButtonMode = ComposerVoiceButtonMode.IDLE,
    val isConnected: Boolean = false,
    val audioLevels: List<Float> = emptyList(),
    val durationSeconds: Double = 0.0,
) {
    val isRecording: Boolean
        get() = buttonMode == ComposerVoiceButtonMode.RECORDING
}

enum class ComposerVoiceRecoveryAction {
    RETRY_CONNECTION,
    OPEN_SETTINGS,
    RETRY_VOICE,
}

enum class ComposerVoiceRecoveryTone {
    WARNING,
    ERROR,
    PROGRESS,
}

data class ComposerVoiceRecoveryUiState(
    val title: String,
    val summary: String,
    val detail: String? = null,
    val actionLabel: String? = null,
    val action: ComposerVoiceRecoveryAction? = null,
    val tone: ComposerVoiceRecoveryTone = ComposerVoiceRecoveryTone.WARNING,
)

data class PlanComposerSessionUiState(
    val anchorMessageId: String? = null,
)

data class AppUiState(
    val onboardingCompleted: Boolean = false,
    val connectionStatus: RemodexConnectionStatus = RemodexConnectionStatus(),
    val connectionHeadline: String = "Waiting for your Mac",
    val connectionMessage: String = "Run remodex up on your Mac, then pair this Android device with the QR code.",
    val recoveryState: SecureConnectionSnapshot = SecureConnectionSnapshot(),
    val collapsedProjectGroupIds: Set<String> = emptySet(),
    val threads: List<RemodexThreadSummary> = emptyList(),
    val selectedThread: RemodexThreadSummary? = null,
    val notificationRegistration: RemodexNotificationRegistrationState = RemodexNotificationRegistrationState(),
    val runtimeDefaults: RemodexRuntimeDefaults = RemodexRuntimeDefaults(),
    val availableModels: List<RemodexModelOption> = emptyList(),
    val appearanceMode: RemodexAppearanceMode = RemodexAppearanceMode.SYSTEM,
    val appFontStyle: RemodexAppFontStyle = RemodexAppFontStyle.SYSTEM,
    val trustedMac: RemodexTrustedMacPresentation? = null,
    val bridgeProfiles: List<RemodexBridgeProfilePresentation> = emptyList(),
    val bridgeUpdatePrompt: RemodexBridgeUpdatePrompt? = null,
    val supportsThreadFork: Boolean = true,
    val pendingApprovalRequest: RemodexApprovalRequest? = null,
    val isCreatingThread: Boolean = false,
    val isRefreshingThreads: Boolean = false,
    val isRefreshingUsage: Boolean = false,
    val isSelectedThreadHydrating: Boolean = false,
    val gptAccountSnapshot: RemodexGptAccountSnapshot = RemodexGptAccountSnapshot(),
    val gptAccountErrorMessage: String? = null,
    val bridgeVersionStatus: RemodexBridgeVersionStatus = RemodexBridgeVersionStatus(),
    val usageStatus: RemodexUsageStatus = RemodexUsageStatus(),
    val composer: ComposerUiState = ComposerUiState(),
    val planComposerSession: PlanComposerSessionUiState? = null,
    val conversationBanner: String? = null,
    val transientBanner: String? = null,
    val approvalBanner: ApprovalBannerUiState? = null,
    val isHandingOffToMac: Boolean = false,
    val showDesktopHandoffConfirm: Boolean = false,
    val desktopHandoffErrorMessage: String? = null,
    val gitSyncAlert: RemodexGitSyncAlertUiState? = null,
    val threadCompletionBanner: ThreadCompletionBannerUiState? = null,
    val completionHapticSignal: Long = 0L,
    val composerSendDismissSignal: Long = 0L,
    val composerSendAnchorSignal: Long = 0L,
    val assistantRevertStatesByMessageId: Map<String, RemodexAssistantRevertPresentation> = emptyMap(),
    val assistantRevertSheet: RemodexAssistantRevertSheetState? = null,
    val commandExecutionDetailsByItemId: Map<String, RemodexCommandExecutionDetails> = emptyMap(),
    val assistantResponseMetrics: RemodexAssistantResponseMetrics? = null,
) {
    val isConnected: Boolean
        get() = connectionStatus.phase == RemodexConnectionPhase.CONNECTED

    val canCreateThread: Boolean
        get() = isConnected && !isCreatingThread
}

data class ThreadCompletionBannerUiState(
    val threadId: String,
    val title: String,
)

data class ApprovalBannerUiState(
    val threadId: String,
    val title: String,
    val message: String,
)

private data class BaseUiOverlayState(
    val gitUiState: GitUiTransientState,
    val sendSignalsByThread: Map<String, ComposerSendUiSignals>,
    val planSessionsByThread: Map<String, PlanComposerSessionUiState>,
    val voiceUiState: ComposerVoiceUiState,
    val isAppForeground: Boolean,
    val dismissedApprovalBannerRequestIds: Set<String>,
)

data class DesktopHandoffUiState(
    val isHandingOffToMac: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val errorMessage: String? = null,
)

data class RemodexGitSyncAlertUiState(
    val title: String = "Git Error",
    val message: String,
    val buttons: List<RemodexGitSyncAlertButtonUiState> = listOf(
        RemodexGitSyncAlertButtonUiState(
            title = "OK",
            role = RemodexGitSyncAlertButtonRole.CANCEL,
            action = RemodexGitSyncAlertAction.DISMISS_ONLY,
        ),
    ),
)

data class RemodexGitSyncAlertButtonUiState(
    val title: String,
    val role: RemodexGitSyncAlertButtonRole? = null,
    val action: RemodexGitSyncAlertAction,
)

enum class RemodexGitSyncAlertButtonRole {
    CANCEL,
    DESTRUCTIVE,
}

enum class RemodexGitSyncAlertAction {
    DISMISS_ONLY,
    DISCARD_RUNTIME_CHANGES,
    CONTINUE_GIT_BRANCH_OPERATION,
    COMMIT_AND_CONTINUE_GIT_BRANCH_OPERATION,
}

private sealed interface PendingGitBranchOperation {
    data class CreateBranch(val branchName: String) : PendingGitBranchOperation

    data class SwitchBranch(val branchName: String) : PendingGitBranchOperation

    data class CreateWorktree(
        val branchName: String,
        val baseBranch: String,
        val changeTransfer: RemodexGitWorktreeChangeTransferMode,
        val followUp: GitWorktreeFollowUp = GitWorktreeFollowUp.NONE,
    ) : PendingGitBranchOperation
}

private enum class GitWorktreeFollowUp {
    NONE,
    HANDOFF_CURRENT_THREAD,
}

private data class ComposerRenderStateA(
    val draftsByThread: Map<String, String> = emptyMap(),
    val attachmentsByThread: Map<String, List<RemodexComposerAttachment>> = emptyMap(),
    val mentionedFilesByThread: Map<String, List<RemodexComposerMentionedFile>> = emptyMap(),
    val mentionedSkillsByThread: Map<String, List<RemodexComposerMentionedSkill>> = emptyMap(),
    val reviewSelectionsByThread: Map<String, RemodexComposerReviewSelection?> = emptyMap(),
)

private data class ComposerRenderStateBLeft(
    val subagentsSelectionsByThread: Map<String, Boolean> = emptyMap(),
    val attachmentMessagesByThread: Map<String, String?> = emptyMap(),
    val composerMessagesByThread: Map<String, String?> = emptyMap(),
    val voiceRecoveryReasonsByThread: Map<String, ComposerVoiceRecoveryReason?> = emptyMap(),
)

private data class ComposerRenderStateB(
    val subagentsSelectionsByThread: Map<String, Boolean> = emptyMap(),
    val attachmentMessagesByThread: Map<String, String?> = emptyMap(),
    val composerMessagesByThread: Map<String, String?> = emptyMap(),
    val voiceRecoveryReasonsByThread: Map<String, ComposerVoiceRecoveryReason?> = emptyMap(),
    val gitStatesByThread: Map<String, RemodexGitState> = emptyMap(),
    val baseBranchesByThread: Map<String, String> = emptyMap(),
    val autocomplete: RemodexComposerAutocompleteState = RemodexComposerAutocompleteState(),
)

private data class SessionRenderState(
    val snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
    val commandExecutionDetails: Map<String, RemodexCommandExecutionDetails> = emptyMap(),
    val assistantResponseMetricsByThreadId: Map<String, RemodexAssistantResponseMetrics> = emptyMap(),
)

private data class SettingsRenderState(
    val gptAccountSnapshot: RemodexGptAccountSnapshot = RemodexGptAccountSnapshot(),
    val gptAccountErrorMessage: String? = null,
    val bridgeVersionStatus: RemodexBridgeVersionStatus = RemodexBridgeVersionStatus(),
    val usageStatus: RemodexUsageStatus = RemodexUsageStatus(),
)

private data class GitUiTransientState(
    val revertedMessageIds: Set<String> = emptySet(),
    val assistantRevertSheet: RemodexAssistantRevertSheetState? = null,
    val gitSyncAlertsByThread: Map<String, RemodexGitSyncAlertUiState> = emptyMap(),
)

private data class AutoReconnectUiState(
    val isActive: Boolean = false,
    val attempt: Int = 0,
    val message: String = "Reconnecting...",
)

private data class PendingComposerSendState(
    val draftText: String,
    val attachments: List<RemodexComposerAttachment>,
    val mentionedFiles: List<RemodexComposerMentionedFile>,
    val mentionedSkills: List<RemodexComposerMentionedSkill>,
    val reviewSelection: RemodexComposerReviewSelection?,
    val isSubagentsSelectionArmed: Boolean,
)

private data class ComposerSendUiSignals(
    val dismissSignal: Long = 0L,
    val anchorSignal: Long = 0L,
)

private data class ThreadChromeState(
    val threadCompletionBanner: ThreadCompletionBannerUiState? = null,
    val completionHapticSignal: Long = 0L,
    val transientBanner: String? = null,
    val desktopHandoff: DesktopHandoffUiState = DesktopHandoffUiState(),
)

private data class ThreadLoadingState(
    val isCreatingThread: Boolean = false,
    val isRefreshingThreads: Boolean = false,
    val isRefreshingUsage: Boolean = false,
)

sealed interface ComposerVoiceRecoveryReason {
    data object ReconnectRequired : ComposerVoiceRecoveryReason

    data object LoginRequired : ComposerVoiceRecoveryReason

    data object ReauthenticationRequired : ComposerVoiceRecoveryReason

    data object VoiceSyncInProgress : ComposerVoiceRecoveryReason

    data class MicrophonePermissionRequired(
        val requiresSettings: Boolean,
    ) : ComposerVoiceRecoveryReason

    data class Generic(
        val message: String,
    ) : ComposerVoiceRecoveryReason
}

private object NoopVoiceRecorder : AndroidVoiceRecorder {
    override val meteringState = MutableStateFlow(com.emanueledipietro.remodex.platform.media.AndroidVoiceMeteringSnapshot())

    override suspend fun startRecording() {
        throw UnsupportedOperationException("Voice recording is not configured.")
    }

    override suspend fun stopRecording(): com.emanueledipietro.remodex.platform.media.AndroidVoiceRecordingClip? {
        return null
    }

    override fun cancelRecording() = Unit
}

class AppViewModel(
    private val repository: RemodexAppRepository,
    private val voiceRecorder: AndroidVoiceRecorder = NoopVoiceRecorder,
) : ViewModel() {
    private val composerDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val composerAttachments = MutableStateFlow<Map<String, List<RemodexComposerAttachment>>>(emptyMap())
    private val composerMentionedFiles = MutableStateFlow<Map<String, List<RemodexComposerMentionedFile>>>(emptyMap())
    private val composerMentionedSkills = MutableStateFlow<Map<String, List<RemodexComposerMentionedSkill>>>(emptyMap())
    private val composerReviewSelections = MutableStateFlow<Map<String, RemodexComposerReviewSelection?>>(emptyMap())
    private val composerSubagentsSelections = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val attachmentMessages = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val composerMessages = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val composerVoiceRecoveryReasons = MutableStateFlow<Map<String, ComposerVoiceRecoveryReason?>>(emptyMap())
    private val gitStates = MutableStateFlow<Map<String, RemodexGitState>>(emptyMap())
    private val selectedGitBaseBranchByThread = MutableStateFlow<Map<String, String>>(emptyMap())
    private val autocompleteState = MutableStateFlow(RemodexComposerAutocompleteState())
    private val revertedAssistantMessageIds = MutableStateFlow<Set<String>>(emptySet())
    private val assistantRevertSheetState = MutableStateFlow<RemodexAssistantRevertSheetState?>(null)
    private val gitSyncAlerts = MutableStateFlow<Map<String, RemodexGitSyncAlertUiState>>(emptyMap())
    private val desktopHandoffState = MutableStateFlow(DesktopHandoffUiState())
    private val transientBannerState = MutableStateFlow<String?>(null)
    private val threadCompletionBannerState = MutableStateFlow<ThreadCompletionBannerUiState?>(null)
    private val dismissedApprovalBannerRequestIdsState = MutableStateFlow<Set<String>>(emptySet())
    private val isAppForegroundState = MutableStateFlow(false)
    private val completionHapticSignalState = MutableStateFlow(0L)
    private val composerSendUiSignals = MutableStateFlow<Map<String, ComposerSendUiSignals>>(emptyMap())
    private val planComposerSessions = MutableStateFlow<Map<String, PlanComposerSessionUiState>>(emptyMap())
    private val voiceButtonModeState = MutableStateFlow(ComposerVoiceButtonMode.IDLE)
    private val isCreatingThreadState = MutableStateFlow(false)
    private val isRefreshingThreadsState = MutableStateFlow(false)
    private val isRefreshingUsageState = MutableStateFlow(false)
    private val hydratingThreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val autoReconnectState = MutableStateFlow(AutoReconnectUiState())
    private var fileAutocompleteJob: Job? = null
    private var skillAutocompleteJob: Job? = null
    private var autoReconnectJob: Job? = null
    private var lastObservedThreadId: String? = null
    private var lastHydratedSelectedThreadId: String? = null
    private var lastHydrationConnected = false
    private var voiceOperationGeneration = 0
    private var previousThreadsById: Map<String, RemodexThreadSummary> = emptyMap()
    private var isAppForeground = false
    private var isManualScannerActive = false
    private var pendingGitBranchOperation: PendingGitBranchOperation? = null
    private var pendingDesktopHandoffThreadId: String? = null
    private var suppressAutoReconnectUntilManualConnect = false
    internal var autoReconnectAttemptLimitOverride: Int? = null
    internal var autoReconnectBackoffMillisOverride: List<Long>? = null
    internal var reconnectSleepChunkMillisOverride: Long? = null

    private val composerRenderStateA =
        combine(
            composerDrafts,
            composerAttachments,
            composerMentionedFiles,
            composerMentionedSkills,
            composerReviewSelections,
        ) { draftsByThread, attachmentsByThread, mentionedFilesByThread, mentionedSkillsByThread, reviewSelectionsByThread ->
            ComposerRenderStateA(
                draftsByThread = draftsByThread,
                attachmentsByThread = attachmentsByThread,
                mentionedFilesByThread = mentionedFilesByThread,
                mentionedSkillsByThread = mentionedSkillsByThread,
                reviewSelectionsByThread = reviewSelectionsByThread,
            )
        }

    private val composerRenderStateBLeft =
        combine(
            composerSubagentsSelections,
            attachmentMessages,
            composerMessages,
            composerVoiceRecoveryReasons,
        ) { subagentsSelectionsByThread,
            attachmentMessagesByThread,
            composerMessagesByThread,
            voiceRecoveryReasonsByThread,
            ->
            ComposerRenderStateBLeft(
                subagentsSelectionsByThread = subagentsSelectionsByThread,
                attachmentMessagesByThread = attachmentMessagesByThread,
                composerMessagesByThread = composerMessagesByThread,
                voiceRecoveryReasonsByThread = voiceRecoveryReasonsByThread,
            )
        }

    private val composerRenderStateBRight =
        combine(
            gitStates,
            selectedGitBaseBranchByThread,
            autocompleteState,
        ) { gitStatesByThread, baseBranchesByThread, autocomplete ->
            Triple(
                gitStatesByThread,
                baseBranchesByThread,
                autocomplete,
            )
        }

    private val composerRenderStateB =
        combine(
            composerRenderStateBLeft,
            composerRenderStateBRight,
        ) { left, right ->
            ComposerRenderStateB(
                subagentsSelectionsByThread = left.subagentsSelectionsByThread,
                attachmentMessagesByThread = left.attachmentMessagesByThread,
                composerMessagesByThread = left.composerMessagesByThread,
                voiceRecoveryReasonsByThread = left.voiceRecoveryReasonsByThread,
                gitStatesByThread = right.first,
                baseBranchesByThread = right.second,
                autocomplete = right.third,
            )
        }

    private val sessionRenderState =
        combine(
            repository.session,
            repository.commandExecutionDetails,
            repository.assistantResponseMetricsByThreadId,
        ) { snapshot, commandExecutionDetails, assistantResponseMetricsByThreadId ->
            SessionRenderState(
                snapshot = snapshot,
                commandExecutionDetails = commandExecutionDetails,
                assistantResponseMetricsByThreadId = assistantResponseMetricsByThreadId,
            )
        }

    private val settingsRenderState =
        combine(
            repository.gptAccountSnapshot,
            repository.gptAccountErrorMessage,
            repository.bridgeVersionStatus,
            repository.usageStatus,
        ) { gptAccountSnapshot, gptAccountErrorMessage, bridgeVersionStatus, usageStatus ->
            SettingsRenderState(
                gptAccountSnapshot = gptAccountSnapshot,
                gptAccountErrorMessage = gptAccountErrorMessage,
                bridgeVersionStatus = bridgeVersionStatus,
                usageStatus = usageStatus,
            )
        }

    private val gitUiTransientState =
        combine(
            revertedAssistantMessageIds,
            assistantRevertSheetState,
            gitSyncAlerts,
        ) { revertedMessageIds, revertSheetState, gitSyncAlertsByThread ->
            GitUiTransientState(
                revertedMessageIds = revertedMessageIds,
                assistantRevertSheet = revertSheetState,
                gitSyncAlertsByThread = gitSyncAlertsByThread,
            )
        }

    private val baseUiTransientState =
        combine(
            gitUiTransientState,
            composerSendUiSignals,
            planComposerSessions,
        ) { gitUiState, sendSignalsByThread, planSessionsByThread ->
            Triple(gitUiState, sendSignalsByThread, planSessionsByThread)
        }

    private val voiceUiRenderState =
        combine(
            voiceButtonModeState,
            voiceRecorder.meteringState,
        ) { buttonMode, metering ->
            ComposerVoiceUiState(
                buttonMode = buttonMode,
                audioLevels = metering.audioLevels,
                durationSeconds = metering.durationSeconds,
            )
        }

    private val baseUiOverlayState =
        combine(
            baseUiTransientState,
            voiceUiRenderState,
            isAppForegroundState,
            dismissedApprovalBannerRequestIdsState,
        ) { transientState, voiceUiState, isAppForeground, dismissedApprovalBannerRequestIds ->
            val (gitUiState, sendSignalsByThread, planSessionsByThread) = transientState
            BaseUiOverlayState(
                gitUiState = gitUiState,
                sendSignalsByThread = sendSignalsByThread,
                planSessionsByThread = planSessionsByThread,
                voiceUiState = voiceUiState,
                isAppForeground = isAppForeground,
                dismissedApprovalBannerRequestIds = dismissedApprovalBannerRequestIds,
            )
        }

    private val baseUiState =
        combine(
            sessionRenderState,
            composerRenderStateA,
            composerRenderStateB,
            baseUiOverlayState,
        ) { sessionRenderState, renderStateA, renderStateB, overlayState ->
            val snapshot = sessionRenderState.snapshot
            val (headline, message) = connectionCopy(snapshot.secureConnection)
            val selectedThread = snapshot.selectedThread
            val draftText = selectedThread?.id?.let(renderStateA.draftsByThread::get).orEmpty()
            val attachments = selectedThread?.id?.let(renderStateA.attachmentsByThread::get).orEmpty()
            val mentionedFiles = selectedThread?.id?.let(renderStateA.mentionedFilesByThread::get).orEmpty()
            val mentionedSkills = selectedThread?.id?.let(renderStateA.mentionedSkillsByThread::get).orEmpty()
            val reviewSelection = selectedThread?.id?.let(renderStateA.reviewSelectionsByThread::get)
            val isSubagentsSelectionArmed = selectedThread?.id?.let(renderStateB.subagentsSelectionsByThread::get) == true
            val attachmentMessage = selectedThread?.id?.let(renderStateB.attachmentMessagesByThread::get)
            val composerMessage = selectedThread?.id?.let(renderStateB.composerMessagesByThread::get)
            val voiceRecoveryReason = selectedThread?.id?.let(renderStateB.voiceRecoveryReasonsByThread::get)
            val gitState = selectedThread?.id?.let(renderStateB.gitStatesByThread::get) ?: RemodexGitState()
            val selectedGitBaseBranch = selectedThread?.id?.let(renderStateB.baseBranchesByThread::get)
                ?: gitState.branches.defaultBranch.orEmpty()
            val selectedThreadSendSignals =
                selectedThread?.id?.let(overlayState.sendSignalsByThread::get) ?: ComposerSendUiSignals()
            AppUiState(
                onboardingCompleted = snapshot.onboardingCompleted,
                connectionStatus = snapshot.connectionStatus,
                connectionHeadline = headline,
                connectionMessage = message,
                recoveryState = snapshot.secureConnection,
                collapsedProjectGroupIds = snapshot.collapsedProjectGroupIds,
                threads = snapshot.threads,
                selectedThread = selectedThread,
                notificationRegistration = snapshot.notificationRegistration,
                runtimeDefaults = snapshot.runtimeDefaults,
                availableModels = snapshot.availableModels,
                appearanceMode = snapshot.appearanceMode,
                appFontStyle = snapshot.appFontStyle,
                trustedMac = snapshot.trustedMac,
                bridgeProfiles = snapshot.bridgeProfiles,
                bridgeUpdatePrompt = snapshot.bridgeUpdatePrompt,
                supportsThreadFork = snapshot.supportsThreadFork,
                pendingApprovalRequest = selectedThread?.let { thread ->
                    snapshot.pendingApprovalRequest?.takeIf { request ->
                        request.threadId.isNullOrBlank() || request.threadId == thread.id
                    }
                } ?: snapshot.pendingApprovalRequest?.takeIf { request ->
                    request.threadId.isNullOrBlank()
                },
                composer = composerState(
                    draftText = draftText,
                    attachments = attachments,
                    mentionedFiles = mentionedFiles,
                    mentionedSkills = mentionedSkills,
                    reviewSelection = reviewSelection,
                    isSubagentsSelectionArmed = isSubagentsSelectionArmed,
                    attachmentLimitMessage = attachmentMessage,
                    composerMessage = composerMessage,
                    voiceRecoveryReason = voiceRecoveryReason,
                    gptAccountSnapshot = RemodexGptAccountSnapshot(),
                    autocomplete = renderStateB.autocomplete.enriched(
                        selectedThread = selectedThread,
                        gitState = gitState,
                        supportsThreadFork = snapshot.supportsThreadFork,
                        selectedGitBaseBranch = selectedGitBaseBranch,
                        draftText = draftText,
                        mentionedFiles = mentionedFiles,
                        mentionedSkills = mentionedSkills,
                        attachments = attachments,
                        reviewSelection = reviewSelection,
                        isSubagentsSelectionArmed = isSubagentsSelectionArmed,
                    ),
                    voiceUiState = overlayState.voiceUiState,
                    isConnected = snapshot.connectionStatus.phase == RemodexConnectionPhase.CONNECTED,
                    gitState = gitState,
                    selectedGitBaseBranch = selectedGitBaseBranch,
                    thread = selectedThread,
                ),
                planComposerSession = selectedThread?.id?.let(overlayState.planSessionsByThread::get),
                conversationBanner = conversationBanner(
                    selectedThread = selectedThread,
                    secureConnection = snapshot.secureConnection,
                ),
                approvalBanner = approvalBanner(
                    snapshot = snapshot,
                    isAppForeground = overlayState.isAppForeground,
                    dismissedRequestIds = overlayState.dismissedApprovalBannerRequestIds,
                ),
                gitSyncAlert = selectedThread?.id?.let(overlayState.gitUiState.gitSyncAlertsByThread::get),
                assistantRevertStatesByMessageId = assistantRevertPresentations(
                    threads = snapshot.threads,
                    selectedThread = selectedThread,
                    secureConnection = snapshot.secureConnection,
                    revertedMessageIds = overlayState.gitUiState.revertedMessageIds,
                ),
                composerSendDismissSignal = selectedThreadSendSignals.dismissSignal,
                composerSendAnchorSignal = selectedThreadSendSignals.anchorSignal,
                assistantRevertSheet = overlayState.gitUiState.assistantRevertSheet,
                commandExecutionDetailsByItemId = sessionRenderState.commandExecutionDetails,
                assistantResponseMetrics = selectedThread?.id?.let(
                    sessionRenderState.assistantResponseMetricsByThreadId::get,
                ),
            )
        }

    private val threadChromeState =
        combine(
            threadCompletionBannerState,
            completionHapticSignalState,
            transientBannerState,
            desktopHandoffState,
        ) {
            threadCompletionBanner: ThreadCompletionBannerUiState?,
            completionHapticSignal: Long,
            transientBanner: String?,
            desktopHandoff: DesktopHandoffUiState,
            ->
            ThreadChromeState(
                threadCompletionBanner = threadCompletionBanner,
                completionHapticSignal = completionHapticSignal,
                transientBanner = transientBanner,
                desktopHandoff = desktopHandoff,
            )
        }

    private val threadLoadingState =
        combine(
            isCreatingThreadState,
            isRefreshingThreadsState,
            isRefreshingUsageState,
        ) { isCreatingThread, isRefreshingThreads, isRefreshingUsage ->
            ThreadLoadingState(
                isCreatingThread = isCreatingThread,
                isRefreshingThreads = isRefreshingThreads,
                isRefreshingUsage = isRefreshingUsage,
            )
        }

    private val chromeDecoratedUiState =
        combine(
            baseUiState,
            hydratingThreadCounts,
            threadChromeState,
            threadLoadingState,
        ) {
            baseState: AppUiState,
            activeHydrations: Map<String, Int>,
            threadChrome: ThreadChromeState,
            loadingState: ThreadLoadingState,
            ->
            val selectedThreadId = baseState.selectedThread?.id
            baseState.copy(
                isSelectedThreadHydrating = selectedThreadId?.let { threadId ->
                    activeHydrations[threadId]?.let { count -> count > 0 } == true
                } ?: false,
                transientBanner = threadChrome.transientBanner,
                isHandingOffToMac = threadChrome.desktopHandoff.isHandingOffToMac,
                showDesktopHandoffConfirm = threadChrome.desktopHandoff.showConfirmDialog,
                desktopHandoffErrorMessage = threadChrome.desktopHandoff.errorMessage,
                threadCompletionBanner = threadChrome.threadCompletionBanner,
                completionHapticSignal = threadChrome.completionHapticSignal,
                isCreatingThread = loadingState.isCreatingThread,
                isRefreshingThreads = loadingState.isRefreshingThreads,
                isRefreshingUsage = loadingState.isRefreshingUsage,
            )
        }

    private val decoratedUiState =
        combine(
            chromeDecoratedUiState,
            settingsRenderState,
        ) { baseState: AppUiState, settingsState: SettingsRenderState ->
            baseState.copy(
                gptAccountSnapshot = settingsState.gptAccountSnapshot,
                gptAccountErrorMessage = settingsState.gptAccountErrorMessage,
                bridgeVersionStatus = settingsState.bridgeVersionStatus,
                usageStatus = settingsState.usageStatus,
                composer = baseState.composer.copy(
                    voiceRecovery = resolveComposerVoiceRecoveryUiState(
                        reason = baseState.composer.voiceRecoveryReason,
                        isConnected = baseState.isConnected,
                        gptAccountSnapshot = settingsState.gptAccountSnapshot,
                    ),
                ),
            )
        }

    val uiState: StateFlow<AppUiState> =
        combine(
            decoratedUiState,
            autoReconnectState,
        ) { baseState, reconnectState ->
            val shouldShowReconnectState =
                reconnectState.isActive &&
                    baseState.connectionStatus.phase != RemodexConnectionPhase.CONNECTED &&
                    baseState.recoveryState.secureState !in setOf(
                        SecureConnectionState.REPAIR_REQUIRED,
                        SecureConnectionState.UPDATE_REQUIRED,
                        SecureConnectionState.NOT_PAIRED,
                    )
            baseState.copy(
                connectionStatus = if (shouldShowReconnectState) {
                    RemodexConnectionStatus(
                        phase = RemodexConnectionPhase.RETRYING,
                        attempt = max(baseState.connectionStatus.attempt, reconnectState.attempt),
                    )
                } else {
                    baseState.connectionStatus
                },
                connectionHeadline = if (shouldShowReconnectState) {
                    "Retrying connection"
                } else {
                    baseState.connectionHeadline
                },
                connectionMessage = if (shouldShowReconnectState) {
                    reconnectState.message
                } else {
                    baseState.connectionMessage
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppUiState(),
        )

    init {
        viewModelScope.launch {
            repository.session.collect { snapshot ->
                val selectedThreadId = snapshot.selectedThread?.id
                val isConnected = snapshot.connectionStatus.phase == RemodexConnectionPhase.CONNECTED
                if ((selectedThreadId == null || selectedThreadId != lastObservedThreadId) &&
                    voiceButtonModeState.value != ComposerVoiceButtonMode.IDLE
                ) {
                    invalidateVoiceCapture()
                }
                if (!isConnected && voiceButtonModeState.value != ComposerVoiceButtonMode.IDLE) {
                    invalidateVoiceCapture()
                }
                if (selectedThreadId != null && isConnected && !lastHydrationConnected) {
                    val currentRecovery = composerVoiceRecoveryReasons.value[selectedThreadId]
                    if (currentRecovery == ComposerVoiceRecoveryReason.ReconnectRequired) {
                        clearComposerVoiceRecovery(selectedThreadId)
                    }
                }
                if (selectedThreadId != null && selectedThreadId != lastObservedThreadId) {
                    lastObservedThreadId = selectedThreadId
                    refreshGitState(selectedThreadId)
                    clearComposerAutocomplete()
                }
                if (selectedThreadId == null) {
                    lastHydratedSelectedThreadId = null
                } else {
                    val shouldHydrateSelectedThread =
                        selectedThreadId != lastHydratedSelectedThreadId ||
                            (isConnected && !lastHydrationConnected)
                    if (shouldHydrateSelectedThread) {
                        lastHydratedSelectedThreadId = selectedThreadId
                        launchThreadHydration(selectedThreadId)
                    }
                }
                lastHydrationConnected = isConnected
                detectThreadCompletionBanner(snapshot)
                detectContextCompactionCompletion(snapshot)
                handleAutoReconnectSnapshot(snapshot)
                previousThreadsById = snapshot.threads.associateBy(RemodexThreadSummary::id)
            }
        }
    }

    fun dismissThreadCompletionBanner() {
        threadCompletionBannerState.value = null
    }

    fun dismissTransientBanner() {
        transientBannerState.value = null
    }

    fun dismissApprovalBanner() {
        val requestId = repository.session.value.pendingApprovalRequest?.id ?: return
        dismissedApprovalBannerRequestIdsState.update { dismissed -> dismissed + requestId }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.completeOnboarding()
        }
    }

    fun onAppForegroundChanged(isForeground: Boolean) {
        isAppForeground = isForeground
        isAppForegroundState.value = isForeground
        logAutoReconnect(
            event = "foregroundChanged",
            extra = "isForeground=$isForeground",
        )
        if (!isForeground) {
            invalidateVoiceCapture()
            return
        }
        maybeStartAutoReconnect(repository.session.value)
    }

    fun prepareForManualScan() {
        viewModelScope.launch {
            stopAutoReconnectForManualScan()
        }
    }

    fun finishManualScan() {
        isManualScannerActive = false
    }

    fun refreshThreads() {
        val currentState = uiState.value
        if (isRefreshingThreadsState.value) {
            return
        }
        viewModelScope.launch {
            isRefreshingThreadsState.value = true
            try {
                when {
                    currentState.isConnected -> repository.refreshThreads()
                    currentState.connectionStatus.phase in setOf(
                        RemodexConnectionPhase.CONNECTING,
                        RemodexConnectionPhase.RETRYING,
                    ) -> Unit
                    currentState.recoveryState.secureState in setOf(
                        SecureConnectionState.TRUSTED_MAC,
                        SecureConnectionState.LIVE_SESSION_UNRESOLVED,
                    ) -> repository.retryConnection()
                    else -> Unit
                }
            } finally {
                isRefreshingThreadsState.value = false
            }
        }
    }

    fun selectThread(threadId: String) {
        viewModelScope.launch {
            withTrackedThreadHydration(threadId) {
                repository.selectThread(threadId)
            }
            refreshGitState(threadId)
            clearComposerAutocomplete()
            dismissAssistantRevertSheet()
        }
    }

    fun setProjectGroupCollapsed(
        groupId: String,
        collapsed: Boolean,
    ) {
        viewModelScope.launch {
            repository.setProjectGroupCollapsed(
                groupId = groupId,
                collapsed = collapsed,
            )
        }
    }

    fun hydrateThreadMetadata(threadId: String) {
        if (threadId.isBlank()) {
            return
        }
        launchThreadHydration(threadId)
    }

    fun createThread(
        preferredProjectPath: String? = null,
        onCreated: ((String?) -> Unit)? = null,
    ) {
        if (isCreatingThreadState.value || !uiState.value.canCreateThread) {
            return
        }
        viewModelScope.launch {
            isCreatingThreadState.value = true
            try {
                runCatching {
                    val previousThreadIds = repository.session.value.threads
                        .mapTo(mutableSetOf(), RemodexThreadSummary::id)
                    repository.createThread(preferredProjectPath)
                    val createdThreadId = resolveCreatedThreadId(previousThreadIds)
                    if (createdThreadId != null && repository.session.value.selectedThread?.id != createdThreadId) {
                        repository.selectThread(createdThreadId)
                    }
                    (createdThreadId ?: repository.session.value.selectedThread?.id)?.let(::refreshGitState)
                    clearComposerAutocomplete()
                    dismissAssistantRevertSheet()
                    createdThreadId
                }.onSuccess { createdThreadId ->
                    onCreated?.invoke(createdThreadId)
                }.onFailure { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    onCreated?.invoke(null)
                }
            } finally {
                isCreatingThreadState.value = false
            }
        }
    }

    private fun launchThreadHydration(threadId: String) {
        if (threadId.isBlank()) {
            return
        }
        viewModelScope.launch {
            withTrackedThreadHydration(threadId) {
                repository.hydrateThread(threadId)
            }
        }
    }

    private fun resolveCreatedThreadId(previousThreadIds: Set<String>): String? {
        return repository.session.value.selectedThread?.id
            ?.takeIf { selectedThreadId -> selectedThreadId !in previousThreadIds }
            ?: repository.session.value.threads.firstOrNull { thread ->
                thread.id !in previousThreadIds
            }?.id
    }

    private suspend fun withTrackedThreadHydration(
        threadId: String,
        block: suspend () -> Unit,
    ) {
        if (threadId.isBlank()) {
            block()
            return
        }
        markThreadHydrationStarted(threadId)
        try {
            block()
        } finally {
            markThreadHydrationFinished(threadId)
        }
    }

    private fun markThreadHydrationStarted(threadId: String) {
        hydratingThreadCounts.update { current ->
            val nextCount = (current[threadId] ?: 0) + 1
            current + (threadId to nextCount)
        }
    }

    private fun markThreadHydrationFinished(threadId: String) {
        hydratingThreadCounts.update { current ->
            val nextCount = (current[threadId] ?: 0) - 1
            if (nextCount > 0) {
                current + (threadId to nextCount)
            } else {
                current - threadId
            }
        }
    }

    fun renameThread(
        threadId: String,
        name: String,
    ) {
        viewModelScope.launch {
            repository.renameThread(threadId, name)
        }
    }

    fun archiveThread(threadId: String) {
        viewModelScope.launch {
            repository.archiveThread(threadId)
        }
    }

    fun unarchiveThread(threadId: String) {
        viewModelScope.launch {
            repository.unarchiveThread(threadId)
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            repository.deleteThread(threadId)
        }
    }

    fun archiveProject(projectPath: String) {
        viewModelScope.launch {
            repository.archiveProject(projectPath)
        }
    }

    fun updateComposerInput(value: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        composerDrafts.update { draftsByThread ->
            draftsByThread.toMutableMap().apply {
                this[threadId] = value
            }
        }
        if (value.trim().isNotEmpty() && composerReviewSelections.value[threadId]?.target != null) {
            clearReviewSelection()
        }
        updateComposerAutocomplete(threadId = threadId, input = value)
    }

    fun sendPrompt() {
        val selectedThread = uiState.value.selectedThread ?: return
        val threadId = selectedThread.id
        viewModelScope.launch {
            val composer = uiState.value.composer
            val startsPlanComposerSession = composer.runtimeConfig.planningMode == RemodexPlanningMode.PLAN
            val pendingComposerState = PendingComposerSendState(
                draftText = composer.draftText,
                attachments = composer.attachments,
                mentionedFiles = composer.mentionedFiles,
                mentionedSkills = composer.mentionedSkills,
                reviewSelection = composer.reviewSelection,
                isSubagentsSelectionArmed = composer.isSubagentsSelectionArmed,
            )
            val reviewSelection = composer.reviewSelection
            if (reviewSelection?.target != null) {
                if (composer.autocomplete.hasComposerContentConflictingWithReview) {
                    setComposerMessage(
                        threadId = threadId,
                        message = "Clear text, files, skills, and images before starting a code review.",
                    )
                    return@launch
                }
                bumpComposerSendDismissSignal(threadId)
                clearComposer(threadId)
                try {
                    val previousThreadIds = repository.session.value.threads
                        .mapTo(mutableSetOf(), RemodexThreadSummary::id)
                    repository.createThread(
                        preferredProjectPath = selectedThread.projectPath.ifBlank { null },
                        inheritRuntimeFromThreadId = threadId,
                    )
                    val reviewThreadId = resolveCreatedThreadId(previousThreadIds)
                        ?: error("Could not create a review thread.")
                    if (repository.session.value.selectedThread?.id != reviewThreadId) {
                        repository.selectThread(reviewThreadId)
                    }
                    repository.startCodeReview(
                        threadId = reviewThreadId,
                        target = reviewSelection.target,
                        baseBranch = composer.selectedGitBaseBranch.ifBlank { null },
                    )
                    bumpComposerSendAnchorSignal(reviewThreadId)
                    refreshGitState(reviewThreadId)
                } catch (error: Throwable) {
                    if (error is CancellationException) {
                        throw error
                    }
                    restoreComposer(threadId, pendingComposerState)
                    setComposerMessage(
                        threadId = threadId,
                        message = error.message ?: "Could not start this code review.",
                    )
                }
                return@launch
            }

            val isCompactCommandRequest =
                composer.attachments.isEmpty() &&
                    composer.mentionedFiles.isEmpty() &&
                    composer.mentionedSkills.isEmpty() &&
                    !composer.isSubagentsSelectionArmed &&
                    RemodexComposerCommandLogic.isStandaloneSlashCommand(
                        text = composer.draftText,
                        commandToken = RemodexSlashCommand.COMPACT.token,
                    )
            if (isCompactCommandRequest) {
                if (selectedThread.isRunning) {
                    setComposerMessage(
                        threadId = threadId,
                        message = "Wait for the current response to finish first.",
                    )
                    return@launch
                }
                bumpComposerSendDismissSignal(threadId)
                bumpComposerSendAnchorSignal(threadId)
                clearComposer(threadId)
                try {
                    repository.compactThread(threadId)
                } catch (error: Throwable) {
                    if (error is CancellationException) {
                        throw error
                    }
                    restoreComposer(threadId, pendingComposerState)
                    setComposerMessage(
                        threadId = threadId,
                        message = error.message ?: "Could not compact this thread.",
                    )
                }
                return@launch
            }

            val payload = buildPromptPayload(
                draftText = composer.draftText,
                mentionedFiles = composer.mentionedFiles,
                isSubagentsSelectionArmed = composer.isSubagentsSelectionArmed,
            )
            if (startsPlanComposerSession) {
                startPlanComposerSession(
                    threadId = threadId,
                    anchorMessageId = selectedThread.messages.lastOrNull()?.id,
                )
            }
            bumpComposerSendDismissSignal(threadId)
            bumpComposerSendAnchorSignal(threadId)
            clearComposer(threadId)
            try {
                repository.sendPrompt(threadId, payload, composer.attachments)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                if (startsPlanComposerSession) {
                    clearPlanComposerSession(threadId)
                }
                restoreComposer(threadId, pendingComposerState)
                setComposerMessage(
                    threadId = threadId,
                    message = error.message ?: "Could not send this message.",
                )
            }
        }
    }

    fun stopTurn() {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            repository.stopTurn(threadId)
        }
    }

    fun requestContinueOnMac() {
        val threadId = uiState.value.selectedThread?.id ?: return
        if (!uiState.value.isConnected || desktopHandoffState.value.isHandingOffToMac) {
            return
        }
        pendingDesktopHandoffThreadId = threadId
        desktopHandoffState.value = DesktopHandoffUiState(showConfirmDialog = true)
    }

    fun dismissDesktopHandoffDialogs() {
        pendingDesktopHandoffThreadId = null
        desktopHandoffState.update { current ->
            current.copy(
                showConfirmDialog = false,
                errorMessage = null,
            )
        }
    }

    fun confirmContinueOnMac() {
        val threadId = pendingDesktopHandoffThreadId ?: uiState.value.selectedThread?.id ?: return
        if (desktopHandoffState.value.isHandingOffToMac) {
            return
        }
        viewModelScope.launch {
            desktopHandoffState.value = DesktopHandoffUiState(
                isHandingOffToMac = true,
            )
            runCatching {
                repository.continueOnMac(threadId)
            }.onSuccess {
                pendingDesktopHandoffThreadId = null
                desktopHandoffState.value = DesktopHandoffUiState()
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                pendingDesktopHandoffThreadId = null
                desktopHandoffState.value = DesktopHandoffUiState(
                    errorMessage = error.message ?: "Could not continue this chat on your Mac.",
                )
            }
        }
    }

    fun sendQueuedDraft(draftId: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            repository.sendQueuedDraft(threadId, draftId)
        }
    }

    suspend fun respondToStructuredUserInput(
        requestId: JsonElement,
        answersByQuestionId: Map<String, List<String>>,
    ) {
        repository.respondToStructuredUserInput(requestId, answersByQuestionId)
    }

    fun approvePendingApproval() {
        viewModelScope.launch {
            runCatching {
                when (uiState.value.pendingApprovalRequest?.kind) {
                    RemodexApprovalKind.PERMISSIONS -> {
                        repository.grantPendingPermissionsApproval(RemodexPermissionGrantScope.TURN)
                    }
                    else -> repository.approvePendingApproval()
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                presentTransientBanner(error.message ?: "Could not approve this request.")
            }
        }
    }

    fun declinePendingApproval() {
        viewModelScope.launch {
            runCatching {
                repository.declinePendingApproval()
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                presentTransientBanner(error.message ?: "Could not decline this request.")
            }
        }
    }

    fun approvePendingApprovalForSession() {
        viewModelScope.launch {
            runCatching {
                when (uiState.value.pendingApprovalRequest?.kind) {
                    RemodexApprovalKind.PERMISSIONS -> {
                        repository.grantPendingPermissionsApproval(RemodexPermissionGrantScope.SESSION)
                    }
                    else -> repository.approvePendingApproval(forSession = true)
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                presentTransientBanner(error.message ?: "Could not approve this request for the session.")
            }
        }
    }

    fun cancelPendingApproval() {
        viewModelScope.launch {
            runCatching {
                repository.cancelPendingApproval()
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                presentTransientBanner(error.message ?: "Could not stop this turn.")
            }
        }
    }

    suspend fun sendPlanFollowUp(
        prompt: String,
        shouldExitPlanMode: Boolean,
    ) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty()) {
            return
        }
        if (shouldExitPlanMode) {
            repository.setPlanningMode(threadId, RemodexPlanningMode.AUTO)
        }
        bumpComposerSendDismissSignal(threadId)
        bumpComposerSendAnchorSignal(threadId)
        repository.sendPrompt(
            threadId = threadId,
            prompt = trimmedPrompt,
            attachments = emptyList(),
            planningModeOverride = if (shouldExitPlanMode) {
                RemodexPlanningMode.AUTO
            } else {
                null
            },
        )
        clearPlanComposerSession(threadId)
    }

    fun dismissPlanComposerSession() {
        val threadId = uiState.value.selectedThread?.id ?: return
        clearPlanComposerSession(threadId)
    }

    fun addAttachments(attachments: List<RemodexComposerAttachment>) {
        val threadId = uiState.value.selectedThread?.id ?: return
        composerAttachments.update { attachmentsByThread ->
            val existingAttachments = attachmentsByThread[threadId].orEmpty()
            val mergedAttachments = (existingAttachments + attachments)
                .distinctBy(RemodexComposerAttachment::uriString)
            val limitedAttachments = mergedAttachments.take(MaxComposerImages)
            attachmentMessages.update { messagesByThread ->
                messagesByThread.toMutableMap().apply {
                    this[threadId] = when {
                        mergedAttachments.size > MaxComposerImages -> "Only $MaxComposerImages images are allowed per message."
                        else -> null
                    }
                }
            }
            attachmentsByThread.toMutableMap().apply {
                this[threadId] = limitedAttachments
            }
        }
        clearComposerMessage(threadId)
        clearReviewSelectionIfConfirmed(threadId)
    }

    fun removeAttachment(attachmentId: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        composerAttachments.update { attachmentsByThread ->
            val remainingAttachments = attachmentsByThread[threadId].orEmpty()
                .filterNot { attachment -> attachment.id == attachmentId }
            attachmentsByThread.toMutableMap().apply {
                this[threadId] = remainingAttachments
            }
        }
        attachmentMessages.update { messagesByThread ->
            messagesByThread.toMutableMap().apply {
                remove(threadId)
            }
        }
    }

    fun selectFileAutocomplete(item: RemodexFuzzyFileMatch) {
        val threadId = uiState.value.selectedThread?.id ?: return
        clearReviewSelectionIfConfirmed(threadId)
        val fullPath = item.path.trim().ifEmpty { item.fileName }
        composerDrafts.update { draftsByThread ->
            val currentDraft = draftsByThread[threadId].orEmpty()
            draftsByThread.toMutableMap().apply {
                this[threadId] = RemodexComposerCommandLogic.replaceTrailingFileToken(
                    text = currentDraft,
                    selectedPath = item.fileName,
                ) ?: currentDraft
            }
        }
        composerMentionedFiles.update { mentionsByThread ->
            val existing = mentionsByThread[threadId].orEmpty()
            val nextItems = existing.filterNot { mention -> mention.path == fullPath } + RemodexComposerMentionedFile(
                id = fullPath,
                fileName = item.fileName,
                path = fullPath,
            )
            mentionsByThread.toMutableMap().apply {
                this[threadId] = nextItems
            }
        }
        clearComposerAutocomplete()
    }

    fun removeMentionedFile(mentionId: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val mention = composerMentionedFiles.value[threadId].orEmpty().firstOrNull { it.id == mentionId } ?: return
        composerDrafts.update { draftsByThread ->
            val currentDraft = draftsByThread[threadId].orEmpty()
            draftsByThread.toMutableMap().apply {
                this[threadId] = RemodexComposerCommandLogic.removeFileMentionAliases(
                    text = currentDraft,
                    mention = mention,
                )
            }
        }
        composerMentionedFiles.update { mentionsByThread ->
            mentionsByThread.toMutableMap().apply {
                this[threadId] = mentionsByThread[threadId].orEmpty()
                    .filterNot { item -> item.id == mentionId }
            }
        }
    }

    fun selectSkillAutocomplete(skill: RemodexSkillMetadata) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val normalizedName = skill.name.trim()
        if (normalizedName.isEmpty()) {
            clearComposerAutocomplete()
            return
        }
        clearReviewSelectionIfConfirmed(threadId)
        composerDrafts.update { draftsByThread ->
            val currentDraft = draftsByThread[threadId].orEmpty()
            draftsByThread.toMutableMap().apply {
                this[threadId] = RemodexComposerCommandLogic.replaceTrailingSkillToken(
                    text = currentDraft,
                    selectedSkill = normalizedName,
                ) ?: currentDraft
            }
        }
        composerMentionedSkills.update { mentionsByThread ->
            val existing = mentionsByThread[threadId].orEmpty()
            if (existing.any { mention -> mention.name.equals(normalizedName, ignoreCase = true) }) {
                mentionsByThread
            } else {
                mentionsByThread.toMutableMap().apply {
                    this[threadId] = existing + RemodexComposerMentionedSkill(
                        id = normalizedName.lowercase(),
                        name = normalizedName,
                        path = skill.path,
                        description = skill.description,
                    )
                }
            }
        }
        clearComposerAutocomplete()
    }

    fun removeMentionedSkill(mentionId: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val mention = composerMentionedSkills.value[threadId].orEmpty().firstOrNull { it.id == mentionId } ?: return
        composerDrafts.update { draftsByThread ->
            val currentDraft = draftsByThread[threadId].orEmpty()
            draftsByThread.toMutableMap().apply {
                this[threadId] = currentDraft
                    .replace(
                        Regex("\\$${Regex.escape(mention.name)}(?=[\\s,.;:!?)\\]}>]|$)", RegexOption.IGNORE_CASE),
                        "",
                    )
                    .replace(Regex("\\s{2,}"), " ")
                    .trim()
            }
        }
        composerMentionedSkills.update { mentionsByThread ->
            mentionsByThread.toMutableMap().apply {
                this[threadId] = mentionsByThread[threadId].orEmpty()
                    .filterNot { item -> item.id == mentionId }
            }
        }
    }

    fun selectSlashCommand(command: RemodexSlashCommand) {
        val threadId = uiState.value.selectedThread?.id ?: return
        when (command) {
            RemodexSlashCommand.STATUS -> {
                composerDrafts.update { draftsByThread ->
                    val currentDraft = draftsByThread[threadId].orEmpty()
                    draftsByThread.toMutableMap().apply {
                        this[threadId] = RemodexComposerCommandLogic.removeTrailingSlashCommandToken(currentDraft)
                            ?: currentDraft
                    }
                }
                clearComposerAutocomplete()
            }

            RemodexSlashCommand.SUBAGENTS -> {
                composerDrafts.update { draftsByThread ->
                    val currentDraft = draftsByThread[threadId].orEmpty()
                    draftsByThread.toMutableMap().apply {
                        this[threadId] = RemodexComposerCommandLogic.removeTrailingSlashCommandToken(currentDraft)
                            ?: currentDraft
                    }
                }
                composerSubagentsSelections.update { selectionsByThread ->
                    selectionsByThread.toMutableMap().apply {
                        this[threadId] = true
                    }
                }
                clearReviewSelectionIfConfirmed(threadId)
                clearComposerAutocomplete()
            }

            RemodexSlashCommand.COMPACT -> {
                composerDrafts.update { draftsByThread ->
                    val currentDraft = draftsByThread[threadId].orEmpty()
                    draftsByThread.toMutableMap().apply {
                        this[threadId] = RemodexComposerCommandLogic.replaceTrailingSlashCommandToken(
                            text = currentDraft,
                            commandToken = RemodexSlashCommand.COMPACT.token,
                        ) ?: RemodexSlashCommand.COMPACT.token
                    }
                }
                clearReviewSelectionIfConfirmed(threadId)
                clearComposerAutocomplete()
            }

            RemodexSlashCommand.CODE_REVIEW -> {
                composerDrafts.update { draftsByThread ->
                    val currentDraft = draftsByThread[threadId].orEmpty()
                    draftsByThread.toMutableMap().apply {
                        this[threadId] = RemodexComposerCommandLogic.removeTrailingSlashCommandToken(currentDraft)
                            ?: currentDraft
                    }
                }
                if (uiState.value.composer.autocomplete.hasComposerContentConflictingWithReview) {
                    composerReviewSelections.update { selectionsByThread ->
                        selectionsByThread.toMutableMap().apply {
                            remove(threadId)
                        }
                    }
                    clearComposerAutocomplete()
                    return
                }
                composerReviewSelections.update { selectionsByThread ->
                    selectionsByThread.toMutableMap().apply {
                        this[threadId] = RemodexComposerReviewSelection(target = null)
                    }
                }
                autocompleteState.value = autocompleteState.value.copy(
                    panel = RemodexComposerAutocompletePanel.REVIEW_TARGETS,
                )
            }

            RemodexSlashCommand.FORK -> {
                if (!uiState.value.supportsThreadFork) {
                    clearComposerAutocomplete()
                    return
                }
                autocompleteState.value = autocompleteState.value.copy(
                    panel = RemodexComposerAutocompletePanel.FORK_DESTINATIONS,
                    forkDestinations = availableForkDestinations(
                        gitState = uiState.value.composer.gitState,
                        supportsThreadFork = uiState.value.supportsThreadFork,
                    ),
                )
            }
        }
    }

    fun selectCodeReviewTarget(target: RemodexComposerReviewTarget) {
        val threadId = uiState.value.selectedThread?.id ?: return
        composerDrafts.update { draftsByThread ->
            val currentDraft = draftsByThread[threadId].orEmpty()
            draftsByThread.toMutableMap().apply {
                this[threadId] = RemodexComposerCommandLogic.removeTrailingSlashCommandToken(currentDraft)
                    ?: currentDraft
            }
        }
        composerReviewSelections.update { selectionsByThread ->
            selectionsByThread.toMutableMap().apply {
                this[threadId] = RemodexComposerReviewSelection(target = target)
            }
        }
        clearComposerAutocomplete()
    }

    fun prepareForkDestinationSelection() {
        val threadId = uiState.value.selectedThread?.id ?: return
        val draftBefore = composerDrafts.value[threadId].orEmpty()
        val draftAfter = RemodexComposerCommandLogic.removeTrailingSlashCommandToken(draftBefore)
            ?: draftBefore
        composerDrafts.update { draftsByThread ->
            draftsByThread.toMutableMap().apply {
                this[threadId] = draftAfter
            }
        }
        clearComposerAutocomplete()
    }

    fun clearReviewSelection() {
        val threadId = uiState.value.selectedThread?.id ?: return
        composerReviewSelections.update { selectionsByThread ->
            selectionsByThread.toMutableMap().apply {
                remove(threadId)
            }
        }
        clearComposerAutocomplete()
    }

    fun clearSubagentsSelection() {
        val threadId = uiState.value.selectedThread?.id ?: return
        composerSubagentsSelections.update { selectionsByThread ->
            selectionsByThread.toMutableMap().apply {
                this[threadId] = false
            }
        }
        clearComposerAutocomplete()
    }

    fun closeComposerAutocomplete() {
        clearComposerAutocomplete()
    }

    fun presentComposerMessage(message: String?) {
        val threadId = uiState.value.selectedThread?.id ?: return
        setComposerMessage(threadId, message)
    }

    fun startVoiceRecording() {
        val threadId = uiState.value.selectedThread?.id ?: return
        if (voiceButtonModeState.value != ComposerVoiceButtonMode.IDLE) {
            return
        }
        if (!uiState.value.isConnected) {
            setComposerVoiceRecovery(threadId, ComposerVoiceRecoveryReason.ReconnectRequired)
            clearComposerMessage(threadId)
            return
        }
        resolveAuthSensitiveVoiceRecoveryReason(
            isConnected = true,
            gptAccountSnapshot = uiState.value.gptAccountSnapshot,
        )?.let { reason ->
            setComposerVoiceRecovery(threadId, reason)
            clearComposerMessage(threadId)
            return
        }

        clearComposerAutocomplete()
        clearComposerMessage(threadId)
        clearComposerVoiceRecovery(threadId)
        val generation = nextVoiceOperationGeneration()
        voiceButtonModeState.value = ComposerVoiceButtonMode.PREFLIGHTING
        viewModelScope.launch {
            try {
                voiceRecorder.startRecording()
                val isStillCurrent = isVoiceOperationCurrent(generation) &&
                    uiState.value.isConnected &&
                    uiState.value.selectedThread?.id == threadId
                if (!isStillCurrent) {
                    voiceRecorder.cancelRecording()
                    return@launch
                }
                voiceButtonModeState.value = ComposerVoiceButtonMode.RECORDING
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                if (isVoiceOperationCurrent(generation)) {
                    voiceButtonModeState.value = ComposerVoiceButtonMode.IDLE
                    setComposerVoiceRecovery(
                        threadId = threadId,
                        reason = classifyComposerVoiceRecoveryReason(
                            message = error.message,
                            isConnected = uiState.value.isConnected,
                            gptAccountSnapshot = uiState.value.gptAccountSnapshot,
                        ),
                    )
                    clearComposerMessage(threadId)
                }
            }
        }
    }

    fun stopVoiceRecording() {
        val threadId = uiState.value.selectedThread?.id ?: return
        if (voiceButtonModeState.value != ComposerVoiceButtonMode.RECORDING) {
            return
        }
        val generation = voiceOperationGeneration
        voiceButtonModeState.value = ComposerVoiceButtonMode.TRANSCRIBING
        viewModelScope.launch {
            var didResetState = false
            val clip = try {
                voiceRecorder.stopRecording()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                if (isVoiceOperationCurrent(generation)) {
                    voiceButtonModeState.value = ComposerVoiceButtonMode.IDLE
                    didResetState = true
                    setComposerVoiceRecovery(
                        threadId = threadId,
                        reason = classifyComposerVoiceRecoveryReason(
                            message = error.message ?: "Could not finish voice recording.",
                            isConnected = uiState.value.isConnected,
                            gptAccountSnapshot = uiState.value.gptAccountSnapshot,
                        ),
                    )
                    clearComposerMessage(threadId)
                }
                null
            }

            if (clip == null) {
                if (isVoiceOperationCurrent(generation) && !didResetState) {
                    voiceButtonModeState.value = ComposerVoiceButtonMode.IDLE
                }
                return@launch
            }

            try {
                val transcript = repository.transcribeVoiceAudioFile(
                    file = clip.file,
                    durationSeconds = clip.durationSeconds,
                )
                val isStillCurrent = isVoiceOperationCurrent(generation) &&
                    uiState.value.isConnected &&
                    uiState.value.selectedThread?.id == threadId
                if (isStillCurrent) {
                    clearComposerVoiceRecovery(threadId)
                    appendVoiceTranscript(threadId, transcript)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                if (isVoiceOperationCurrent(generation)) {
                    setComposerVoiceRecovery(
                        threadId = threadId,
                        reason = classifyComposerVoiceRecoveryReason(
                            message = error.message ?: "Could not transcribe this voice note.",
                            isConnected = uiState.value.isConnected,
                            gptAccountSnapshot = uiState.value.gptAccountSnapshot,
                        ),
                    )
                    clearComposerMessage(threadId)
                }
            } finally {
                clip.file.delete()
                if (isVoiceOperationCurrent(generation)) {
                    voiceButtonModeState.value = ComposerVoiceButtonMode.IDLE
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        invalidateVoiceCapture()
    }

    fun handleVoicePermissionDenied(requiresSettings: Boolean) {
        val threadId = uiState.value.selectedThread?.id ?: return
        setComposerVoiceRecovery(
            threadId = threadId,
            reason = ComposerVoiceRecoveryReason.MicrophonePermissionRequired(
                requiresSettings = requiresSettings,
            ),
        )
        clearComposerMessage(threadId)
    }

    fun selectPlanningMode(planningMode: RemodexPlanningMode) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            repository.setPlanningMode(threadId, planningMode)
        }
    }

    fun selectModel(modelId: String?) {
        viewModelScope.launch {
            repository.setSelectedModelId(modelId)
        }
    }

    fun selectReasoningEffort(reasoningEffort: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            repository.setReasoningEffort(threadId, reasoningEffort)
        }
    }

    fun selectAccessMode(accessMode: RemodexAccessMode) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            repository.setAccessMode(threadId, accessMode)
        }
    }

    fun selectServiceTier(serviceTier: RemodexServiceTier?) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            repository.setServiceTier(threadId, serviceTier)
        }
    }

    fun selectGitBaseBranch(branch: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        selectedGitBaseBranchByThread.update { branchesByThread ->
            branchesByThread.toMutableMap().apply {
                this[threadId] = branch
            }
        }
    }

    fun refreshGitState() {
        uiState.value.selectedThread?.id?.let(::refreshGitState)
    }

    fun loadRepositoryDiff(onLoaded: (RemodexGitRepoDiff) -> Unit) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            clearGitSyncAlert(threadId)
            updateGitState(threadId) { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }
            runCatching { repository.loadGitDiff(threadId) }
                .onSuccess { diff ->
                    updateGitState(threadId) { currentState ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    if (diff.patch.isNotBlank()) {
                        onLoaded(diff)
                    } else {
                        showGitSyncAlert(
                            threadId = threadId,
                            message = "There are no repository changes to show.",
                        )
                    }
                }
                .onFailure { error ->
                    updateGitState(threadId) { currentState ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    showGitSyncAlert(
                        threadId = threadId,
                        message = error.message ?: "Could not load repository changes.",
                    )
                }
        }
    }

    fun checkoutGitBranch(branch: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val selectedThread = uiState.value.selectedThread ?: return
        val gitState = uiState.value.composer.gitState
        val trimmedBranch = branch.trim()
        if (trimmedBranch.isEmpty()) {
            return
        }
        if (gitState.branches.branchesCheckedOutElsewhere.contains(trimmedBranch)) {
            val targetPath = gitState.branches.worktreePathByBranch[trimmedBranch]
            val normalizedCurrentPath = normalizeComparableProjectPath(selectedThread.projectPath)
            val normalizedTargetPath = normalizeComparableProjectPath(targetPath)
            if (normalizedTargetPath != null && normalizedTargetPath == normalizedCurrentPath) {
                return
            }
            val matchingThread = normalizedTargetPath?.let { worktreePath ->
                uiState.value.threads.firstOrNull { thread ->
                    normalizeComparableProjectPath(thread.projectPath) == worktreePath
                }
            }
            if (matchingThread != null) {
                selectThread(matchingThread.id)
                return
            }
            showGitSyncAlert(
                threadId = threadId,
                title = "Branch already open elsewhere",
                message = if (trimmedBranch.isBlank()) {
                    "This branch is already checked out in another worktree."
                } else {
                    "'$trimmedBranch' is already checked out in another worktree. Open that chat from the sidebar to continue there."
                },
            )
            return
        }
        maybeRunGitBranchOperation(
            threadId = threadId,
            operation = PendingGitBranchOperation.SwitchBranch(trimmedBranch),
        )
    }

    fun createGitBranch(branch: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val trimmedBranch = branch.trim()
        if (trimmedBranch.isEmpty()) {
            return
        }
        maybeRunGitBranchOperation(
            threadId = threadId,
            operation = PendingGitBranchOperation.CreateBranch(trimmedBranch),
        )
    }

    fun createGitWorktree(name: String) {
        val baseBranch = uiState.value.composer.selectedGitBaseBranch.ifBlank { null }
        createGitWorktree(
            name = name,
            baseBranch = baseBranch,
            changeTransfer = RemodexGitWorktreeChangeTransferMode.COPY,
        )
    }

    private fun createGitWorktree(
        name: String,
        baseBranch: String?,
        changeTransfer: RemodexGitWorktreeChangeTransferMode,
        followUp: GitWorktreeFollowUp = GitWorktreeFollowUp.NONE,
    ) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val trimmedName = name.trim()
        val trimmedBaseBranch = baseBranch?.trim().orEmpty()
        if (trimmedName.isEmpty() || trimmedBaseBranch.isEmpty()) {
            return
        }
        maybeRunGitBranchOperation(
            threadId = threadId,
            operation = PendingGitBranchOperation.CreateWorktree(
                branchName = trimmedName,
                baseBranch = trimmedBaseBranch,
                changeTransfer = changeTransfer,
                followUp = followUp,
            ),
        )
    }

    fun handoffThreadToWorktree(
        name: String,
        baseBranch: String?,
    ) {
        createGitWorktree(
            name = name,
            baseBranch = baseBranch,
            changeTransfer = RemodexGitWorktreeChangeTransferMode.MOVE,
            followUp = GitWorktreeFollowUp.HANDOFF_CURRENT_THREAD,
        )
    }

    fun commitGitChanges(message: String? = null) {
        val threadId = uiState.value.selectedThread?.id ?: return
        launchGitAction(
            threadId = threadId,
            title = "Git Error",
            fallbackMessage = "Operation failed.",
        ) {
            repository.commitGitChanges(threadId, message)
        }
    }

    fun commitAndPushGitChanges(message: String? = null) {
        val threadId = uiState.value.selectedThread?.id ?: return
        launchGitAction(
            threadId = threadId,
            title = "Git Error",
            fallbackMessage = "Operation failed.",
        ) {
            repository.commitAndPushGitChanges(threadId, message)
        }
    }

    fun pullGitChanges() {
        val threadId = uiState.value.selectedThread?.id ?: return
        launchGitAction(
            threadId = threadId,
            title = "Pull Failed",
            fallbackMessage = "Operation failed.",
        ) {
            repository.pullGitChanges(threadId)
        }
    }

    fun pushGitChanges() {
        val threadId = uiState.value.selectedThread?.id ?: return
        launchGitAction(
            threadId = threadId,
            title = "Push Failed",
            fallbackMessage = "Operation failed.",
        ) {
            repository.pushGitChanges(threadId)
        }
    }

    fun discardRuntimeChangesAndSync() {
        val threadId = uiState.value.selectedThread?.id ?: return
        val repoSync = uiState.value.composer.gitState.sync
        val unpushedCommitWarning = when (val aheadCount = repoSync?.aheadCount ?: 0) {
            0 -> ""
            1 -> " This also deletes 1 local commit that has not been pushed."
            else -> " This also deletes $aheadCount local commits that have not been pushed."
        }
        showGitSyncAlert(
            threadId = threadId,
            title = "Discard local changes?",
            message = "This resets the current branch to match the remote and removes local uncommitted changes.$unpushedCommitWarning This cannot be undone from the app.",
            buttons = listOf(
                RemodexGitSyncAlertButtonUiState(
                    title = "Cancel",
                    role = RemodexGitSyncAlertButtonRole.CANCEL,
                    action = RemodexGitSyncAlertAction.DISMISS_ONLY,
                ),
                RemodexGitSyncAlertButtonUiState(
                    title = "Discard Changes",
                    role = RemodexGitSyncAlertButtonRole.DESTRUCTIVE,
                    action = RemodexGitSyncAlertAction.DISCARD_RUNTIME_CHANGES,
                ),
            ),
        )
    }

    fun createPullRequest(onOpenUrl: (String) -> Unit) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val gitState = uiState.value.composer.gitState
        val validationMessage = createPullRequestValidationMessage(gitState)
        if (validationMessage != null) {
            showGitSyncAlert(
                threadId = threadId,
                title = "Git Error",
                message = validationMessage,
            )
            return
        }
        viewModelScope.launch {
            clearGitSyncAlert(threadId)
            runCatching { repository.loadGitRemoteUrl(threadId) }
                .onSuccess { remote ->
                    val ownerRepo = remote.ownerRepo?.trim().orEmpty()
                    if (ownerRepo.isEmpty()) {
                        showGitSyncAlert(
                            threadId = threadId,
                            title = "Git Error",
                            message = "Could not determine repository from remote URL.",
                        )
                        return@onSuccess
                    }
                    val sync = gitState.sync
                    val branch = sync?.currentBranch?.trim().orEmpty()
                    val base = gitState.branches.defaultBranch?.trim().orEmpty()
                    if (branch.isEmpty() || base.isEmpty()) {
                        showGitSyncAlert(
                            threadId = threadId,
                            title = "Git Error",
                            message = "Could not determine the repository branch metadata.",
                        )
                        return@onSuccess
                    }
                    onOpenUrl(buildPullRequestUrl(ownerRepo = ownerRepo, branch = branch, base = base))
                }
                .onFailure { error ->
                    showGitSyncAlert(
                        threadId = threadId,
                        title = "Git Error",
                        message = error.message ?: "Could not determine repository from remote URL.",
                    )
                }
        }
    }

    fun forkThread(destination: RemodexComposerForkDestination) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            clearComposerAutocomplete()
            dismissAssistantRevertSheet()
            runCatching {
                repository.forkThread(
                    threadId = threadId,
                    destination = destination,
                    baseBranch = uiState.value.composer.selectedGitBaseBranch.ifBlank { null },
                )
            }.onSuccess { nextThreadId ->
                nextThreadId?.let(::refreshGitState)
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                if (shouldSuppressForkFailureAfterBridgeUpdate()) {
                    return@onFailure
                }
                showGitSyncAlert(
                    threadId = threadId,
                    title = "Fork Failed",
                    message = error.message ?: "Could not fork the thread.",
                )
            }
        }
    }

    fun forkThreadIntoNewWorktree(
        name: String,
        baseBranch: String?,
    ) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return
        }
        val gitState = gitStates.value[threadId]
        val resolvedBaseBranch = baseBranch
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: gitState?.branches?.currentBranch?.trim()?.takeIf(String::isNotEmpty)
            ?: gitState?.sync?.currentBranch?.trim()?.takeIf(String::isNotEmpty)
            ?: gitState?.branches?.defaultBranch?.trim()?.takeIf(String::isNotEmpty)
        if (resolvedBaseBranch == null) {
            showGitSyncAlert(
                threadId = threadId,
                title = "Worktree Fork Failed",
                message = "Could not determine a base branch for the new worktree.",
            )
            return
        }

        viewModelScope.launch {
            clearComposerAutocomplete()
            dismissAssistantRevertSheet()
            clearGitSyncAlert(threadId)
            dismissTransientBanner()
            updateGitState(threadId) { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }
            runCatching {
                val worktreeResult = repository.createGitWorktreeResult(
                    threadId = threadId,
                    name = trimmedName,
                    baseBranch = resolvedBaseBranch,
                    changeTransfer = RemodexGitWorktreeChangeTransferMode.COPY,
                )
                if (worktreeResult.alreadyExisted) {
                    throw IllegalStateException(
                        "A managed worktree for '${worktreeResult.branch}' already exists. Choose a different branch name to create a fresh forked workspace."
                    )
                }
                repository.forkThreadIntoProjectPath(
                    threadId = threadId,
                    projectPath = worktreeResult.worktreePath,
                )
                worktreeResult
            }.onSuccess { worktreeResult ->
                updateGitState(threadId) { currentState ->
                    currentState.copy(isLoading = false, errorMessage = null)
                }
                presentTransientBanner("Now in worktree ${worktreeResult.branch}.")
                repository.session.value.selectedThread?.id?.let(::refreshGitState)
            }.onFailure { error ->
                updateGitState(threadId) { currentState ->
                    currentState.copy(isLoading = false, errorMessage = null)
                }
                showGitSyncAlert(
                    threadId = threadId,
                    title = "Worktree Fork Failed",
                    message = error.message ?: "Could not fork the thread into a new worktree.",
                )
            }
        }
    }

    fun startAssistantRevertPreview(messageId: String) {
        val thread = uiState.value.selectedThread ?: return
        val message = thread.messages.firstOrNull { item -> item.id == messageId } ?: return
        val changeSet = message.assistantChangeSet ?: return
        val presentation = uiState.value.assistantRevertStatesByMessageId[messageId] ?: return
        if (!presentation.isEnabled) {
            return
        }

        assistantRevertSheetState.value = RemodexAssistantRevertSheetState(
            messageId = messageId,
            threadId = thread.id,
            changeSet = changeSet,
            presentation = presentation,
            isLoadingPreview = true,
        )

        viewModelScope.launch {
            runCatching {
                repository.previewAssistantRevert(
                    threadId = thread.id,
                    forwardPatch = changeSet.forwardUnifiedPatch,
                )
            }.onSuccess { preview ->
                val currentSheet = assistantRevertSheetState.value
                if (currentSheet?.messageId == messageId) {
                    assistantRevertSheetState.value = currentSheet.copy(
                        preview = preview,
                        isLoadingPreview = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                val currentSheet = assistantRevertSheetState.value
                if (currentSheet?.messageId == messageId) {
                    assistantRevertSheetState.value = currentSheet.copy(
                        isLoadingPreview = false,
                        errorMessage = error.message ?: "Could not preview this undo action.",
                    )
                }
            }
        }
    }

    fun confirmAssistantRevert() {
        val sheetState = assistantRevertSheetState.value ?: return
        val preview = sheetState.preview ?: return
        if (!preview.canRevert) {
            return
        }

        assistantRevertSheetState.value = sheetState.copy(
            isApplying = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            runCatching {
                repository.applyAssistantRevert(
                    threadId = sheetState.threadId,
                    forwardPatch = sheetState.changeSet.forwardUnifiedPatch,
                )
            }.onSuccess { applyResult ->
                val currentSheet = assistantRevertSheetState.value ?: return@onSuccess
                if (currentSheet.messageId != sheetState.messageId) {
                    return@onSuccess
                }
                if (applyResult.success) {
                    revertedAssistantMessageIds.update { revertedMessageIds ->
                        revertedMessageIds + sheetState.messageId
                    }
                    applyResult.status?.let { syncStatus ->
                        updateGitState(sheetState.threadId) { currentState ->
                            currentState.copy(
                                sync = syncStatus,
                                isLoading = false,
                                lastActionMessage = "Reverted changes from this response.",
                                errorMessage = null,
                            )
                        }
                    } ?: refreshGitState(sheetState.threadId)
                    assistantRevertSheetState.value = null
                    return@onSuccess
                }

                assistantRevertSheetState.value = currentSheet.copy(
                    isApplying = false,
                    preview = preview.copy(
                        canRevert = false,
                        conflicts = applyResult.conflicts,
                        unsupportedReasons = applyResult.unsupportedReasons,
                        stagedFiles = applyResult.stagedFiles,
                    ),
                    errorMessage = applyResult.conflicts.firstOrNull()?.message
                        ?: applyResult.unsupportedReasons.firstOrNull()
                        ?: "Patch revert failed.",
                )
            }.onFailure { error ->
                val currentSheet = assistantRevertSheetState.value ?: return@onFailure
                if (currentSheet.messageId == sheetState.messageId) {
                    assistantRevertSheetState.value = currentSheet.copy(
                        isApplying = false,
                        errorMessage = error.message ?: "Patch revert failed.",
                    )
                }
            }
        }
    }

    fun dismissAssistantRevertSheet() {
        assistantRevertSheetState.value = null
    }

    fun dismissGitSyncAlert() {
        val threadId = uiState.value.selectedThread?.id ?: return
        clearGitSyncAlert(threadId)
        pendingGitBranchOperation = null
    }

    fun confirmGitSyncAlert() {
        val action = uiState.value.gitSyncAlert?.buttons
            ?.firstOrNull { button -> button.action != RemodexGitSyncAlertAction.DISMISS_ONLY }
            ?.action
            ?: RemodexGitSyncAlertAction.DISMISS_ONLY
        performGitSyncAlertAction(action)
    }

    fun performGitSyncAlertAction(action: RemodexGitSyncAlertAction) {
        val threadId = uiState.value.selectedThread?.id ?: return
        val pendingOperation = pendingGitBranchOperation
        clearGitSyncAlert(threadId)
        pendingGitBranchOperation = null
        when (action) {
            RemodexGitSyncAlertAction.DISMISS_ONLY -> Unit
            RemodexGitSyncAlertAction.DISCARD_RUNTIME_CHANGES -> {
                launchGitAction(
                    threadId = threadId,
                    title = "Discard Failed",
                    fallbackMessage = "Operation failed.",
                ) {
                    repository.discardRuntimeChangesAndSync(threadId)
                }
            }
            RemodexGitSyncAlertAction.CONTINUE_GIT_BRANCH_OPERATION -> {
                pendingOperation?.let { operation ->
                    performPendingGitBranchOperation(threadId, operation)
                }
            }
            RemodexGitSyncAlertAction.COMMIT_AND_CONTINUE_GIT_BRANCH_OPERATION -> {
                if (pendingOperation == null) {
                    return
                }
                viewModelScope.launch {
                    clearGitSyncAlert(threadId)
                    updateGitState(threadId) { currentState ->
                        currentState.copy(isLoading = true, errorMessage = null)
                    }
                    runCatching {
                        repository.commitGitChanges(threadId, "WIP before switching branches")
                    }.onSuccess { nextState ->
                        updateGitState(threadId) {
                            nextState.copy(errorMessage = null)
                        }
                        performPendingGitBranchOperation(threadId, pendingOperation)
                    }.onFailure { error ->
                        updateGitState(threadId) { currentState ->
                            currentState.copy(
                                isLoading = false,
                                errorMessage = null,
                            )
                        }
                        showGitSyncAlert(
                            threadId = threadId,
                            title = "Commit Failed",
                            message = error.message ?: "Could not commit the current branch.",
                        )
                    }
                }
            }
        }
    }

    fun setDefaultModelId(modelId: String?) {
        viewModelScope.launch {
            repository.setDefaultModelId(modelId)
        }
    }

    fun setDefaultReasoningEffort(reasoningEffort: String?) {
        viewModelScope.launch {
            repository.setDefaultReasoningEffort(reasoningEffort)
        }
    }

    fun setDefaultAccessMode(accessMode: RemodexAccessMode) {
        viewModelScope.launch {
            repository.setDefaultAccessMode(accessMode)
        }
    }

    fun setDefaultServiceTier(serviceTier: RemodexServiceTier?) {
        viewModelScope.launch {
            repository.setDefaultServiceTier(serviceTier)
        }
    }

    fun setAppearanceMode(mode: RemodexAppearanceMode) {
        viewModelScope.launch {
            repository.setAppearanceMode(mode)
        }
    }

    fun setAppFontStyle(style: RemodexAppFontStyle) {
        viewModelScope.launch {
            repository.setAppFontStyle(style)
        }
    }

    fun setMacNickname(
        deviceId: String,
        nickname: String?,
    ) {
        viewModelScope.launch {
            repository.setMacNickname(deviceId, nickname)
        }
    }

    fun activateBridgeProfile(profileId: String) {
        viewModelScope.launch {
            stopAutoReconnectForBridgeSwitch()
            suppressAutoReconnectUntilManualConnect = false
            repository.activateBridgeProfile(profileId)
            clearAutoReconnectState()
        }
    }

    fun removeBridgeProfile(profileId: String) {
        viewModelScope.launch {
            if (uiState.value.recoveryState.activeProfileId == profileId) {
                suppressAutoReconnectUntilManualConnect = false
                isManualScannerActive = false
                stopAutoReconnectLoop()
            }
            repository.removeBridgeProfile(profileId)
        }
    }

    fun refreshSettingsStatus() {
        refreshGptAccountState()
        refreshUsageStatus()
    }

    fun refreshGptAccountState() {
        viewModelScope.launch {
            repository.refreshGptAccountState()
        }
    }

    fun refreshUsageStatus() {
        refreshUsageStatus(threadId = uiState.value.selectedThread?.id)
    }

    fun logoutGptAccount() {
        viewModelScope.launch {
            repository.logoutGptAccount()
        }
    }

    fun pairWithQrPayload(payload: PairingQrPayload) {
        viewModelScope.launch {
            stopAutoReconnectForManualScan()
            isManualScannerActive = false
            suppressAutoReconnectUntilManualConnect = false
            repository.pairWithQrPayload(payload)
        }
    }

    fun dismissBridgeUpdatePrompt() {
        viewModelScope.launch {
            repository.dismissBridgeUpdatePrompt()
        }
    }

    fun retryConnection() {
        viewModelScope.launch {
            stopAutoReconnectForManualRetry()
            suppressAutoReconnectUntilManualConnect = false
            repository.retryConnection()
        }
    }

    fun retryConnectionAfterBridgeUpdate() {
        viewModelScope.launch {
            repository.dismissBridgeUpdatePrompt()
            stopAutoReconnectForManualRetry()
            suppressAutoReconnectUntilManualConnect = false
            repository.retryConnection()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            suppressAutoReconnectUntilManualConnect = true
            isManualScannerActive = false
            stopAutoReconnectLoop()
            repository.disconnect()
        }
    }

    fun forgetTrustedMac() {
        viewModelScope.launch {
            suppressAutoReconnectUntilManualConnect = true
            isManualScannerActive = false
            stopAutoReconnectLoop()
            repository.forgetTrustedMac()
        }
    }

    private fun handleAutoReconnectSnapshot(
        snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
    ) {
        when (snapshot.secureConnection.secureState) {
            SecureConnectionState.ENCRYPTED -> {
                suppressAutoReconnectUntilManualConnect = false
                clearAutoReconnectState()
                return
            }

            SecureConnectionState.REPAIR_REQUIRED,
            SecureConnectionState.UPDATE_REQUIRED,
            SecureConnectionState.NOT_PAIRED -> {
                stopAutoReconnectLoop()
                return
            }

            else -> Unit
        }

        if (shouldAttemptAutoReconnect(snapshot)) {
            maybeStartAutoReconnect(snapshot)
        } else if (autoReconnectJob?.isActive != true) {
            clearAutoReconnectState()
        }
    }

    private fun maybeStartAutoReconnect(
        snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
    ) {
        val blockReason = autoReconnectBlockReason(snapshot)
        if (blockReason != null || autoReconnectJob?.isActive == true) {
            logAutoReconnect(
                event = "maybeStartAutoReconnectSkipped",
                snapshot = snapshot,
                extra = when {
                    autoReconnectJob?.isActive == true -> "reason=jobActive"
                    else -> "reason=$blockReason"
                },
            )
            return
        }
        logAutoReconnect(event = "autoReconnectLoopStarting", snapshot = snapshot)
        autoReconnectJob = viewModelScope.launch {
            runAutoReconnectLoop()
        }.also { job ->
            job.invokeOnCompletion {
                if (autoReconnectJob === job) {
                    autoReconnectJob = null
                }
            }
        }
    }

    private fun shouldAttemptAutoReconnect(
        snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
    ): Boolean {
        return autoReconnectBlockReason(snapshot) == null
    }

    private fun autoReconnectBlockReason(
        snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
    ): String? {
        if (!snapshot.onboardingCompleted) {
            return "onboardingIncomplete"
        }
        if (!isAppForeground) {
            return "background"
        }
        if (isManualScannerActive) {
            return "manualScannerActive"
        }
        if (suppressAutoReconnectUntilManualConnect) {
            return "suppressedUntilManualConnect"
        }
        if (snapshot.trustedMac == null) {
            return "missingTrustedMac"
        }
        if (!snapshot.secureConnection.autoReconnectAllowed) {
            return "secureStateDisallowsAutoReconnect"
        }
        val allowedState = snapshot.secureConnection.secureState in setOf(
            SecureConnectionState.TRUSTED_MAC,
            SecureConnectionState.LIVE_SESSION_UNRESOLVED,
        )
        return if (allowedState) null else "secureState=${snapshot.secureConnection.secureState}"
    }

    private suspend fun runAutoReconnectLoop() {
        var attempt = 0
        val maxAttempts = autoReconnectAttemptLimitOverride ?: defaultAutoReconnectAttemptLimit
        logAutoReconnect(
            event = "autoReconnectLoopEntered",
            extra = "maxAttempts=$maxAttempts",
        )

        try {
            while (shouldAttemptAutoReconnect(repository.session.value) && attempt < maxAttempts) {
                val snapshot = repository.session.value
                logAutoReconnect(
                    event = "autoReconnectLoopTick",
                    snapshot = snapshot,
                    extra = "attempt=$attempt",
                )
                if (snapshot.connectionStatus.phase == RemodexConnectionPhase.CONNECTED ||
                    snapshot.secureConnection.secureState == SecureConnectionState.ENCRYPTED
                ) {
                    logAutoReconnect(event = "autoReconnectLoopStopped", snapshot = snapshot, extra = "reason=connected")
                    clearAutoReconnectState()
                    return
                }

                if (snapshot.connectionStatus.phase in setOf(
                        RemodexConnectionPhase.CONNECTING,
                        RemodexConnectionPhase.RETRYING,
                    ) || snapshot.secureConnection.secureState in setOf(
                        SecureConnectionState.HANDSHAKING,
                        SecureConnectionState.RECONNECTING,
                    )
                ) {
                    autoReconnectState.value = AutoReconnectUiState(
                        isActive = true,
                        attempt = max(1, attempt),
                    )
                    if (!sleepForReconnectBackoff(300L)) {
                        return
                    }
                    continue
                }

                attempt += 1
                autoReconnectState.value = AutoReconnectUiState(
                    isActive = true,
                    attempt = attempt,
                )
                logAutoReconnect(
                    event = "autoReconnectAttempt",
                    snapshot = snapshot,
                    extra = "attempt=$attempt",
                )
                repository.retryConnection()
                if (waitForReconnectAttemptOutcome(attempt)) {
                    return
                }

                val backoffSteps = autoReconnectBackoffMillisOverride ?: listOf(1_000L, 3_000L)
                val backoff = backoffSteps.getOrElse((attempt - 1).coerceAtMost(backoffSteps.lastIndex)) {
                    backoffSteps.last()
                }
                if (!sleepForReconnectBackoff(backoff)) {
                    logAutoReconnect(event = "autoReconnectLoopStopped", extra = "reason=backoffCancelled attempt=$attempt")
                    return
                }
            }
        } finally {
            logAutoReconnect(
                event = "autoReconnectLoopExited",
                extra = "finalAttempt=$attempt connectionPhase=${repository.session.value.connectionStatus.phase}",
            )
            if (repository.session.value.connectionStatus.phase != RemodexConnectionPhase.CONNECTED) {
                clearAutoReconnectState()
            }
        }

        if (attempt >= maxAttempts) {
            suppressAutoReconnectUntilManualConnect = true
            logAutoReconnect(
                event = "autoReconnectExhausted",
                extra = "attempts=$attempt",
            )
        }
    }

    private suspend fun waitForReconnectAttemptOutcome(
        attempt: Int,
    ): Boolean {
        var settleRemainingMs = 500L
        while (shouldContinueAutoReconnectWait(repository.session.value)) {
            val snapshot = repository.session.value
            when (snapshot.secureConnection.secureState) {
                SecureConnectionState.ENCRYPTED -> {
                    suppressAutoReconnectUntilManualConnect = false
                    clearAutoReconnectState()
                    return true
                }

                SecureConnectionState.REPAIR_REQUIRED,
                SecureConnectionState.UPDATE_REQUIRED,
                SecureConnectionState.NOT_PAIRED -> {
                    clearAutoReconnectState()
                    return true
                }

                SecureConnectionState.HANDSHAKING,
                SecureConnectionState.RECONNECTING -> {
                    autoReconnectState.value = AutoReconnectUiState(
                        isActive = true,
                        attempt = attempt,
                    )
                    if (!sleepForReconnectBackoff(reconnectSleepChunkMillis())) {
                        return true
                    }
                }

                SecureConnectionState.TRUSTED_MAC,
                SecureConnectionState.LIVE_SESSION_UNRESOLVED -> {
                    if (settleRemainingMs <= 0L) {
                        return false
                    }
                    val chunk = minOf(reconnectSleepChunkMillis(), settleRemainingMs)
                    settleRemainingMs -= chunk
                    if (!sleepForReconnectBackoff(chunk)) {
                        return true
                    }
                }
            }
        }
        return true
    }

    private fun shouldContinueAutoReconnectWait(
        snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
    ): Boolean {
        if (shouldAttemptAutoReconnect(snapshot) || snapshot.isConnectedForRecovery()) {
            return true
        }
        if (snapshot.connectionStatus.phase in setOf(
                RemodexConnectionPhase.CONNECTING,
                RemodexConnectionPhase.RETRYING,
            )
        ) {
            return true
        }
        return snapshot.secureConnection.secureState in setOf(
            SecureConnectionState.HANDSHAKING,
            SecureConnectionState.RECONNECTING,
        )
    }

    private suspend fun stopAutoReconnectForManualRetry() {
        isManualScannerActive = false
        if (autoReconnectJob?.isActive != true &&
            uiState.value.connectionStatus.phase !in setOf(
                RemodexConnectionPhase.CONNECTING,
                RemodexConnectionPhase.RETRYING,
            ) &&
            !uiState.value.isConnected
        ) {
            clearAutoReconnectState()
            return
        }

        autoReconnectState.value = AutoReconnectUiState(
            isActive = true,
            message = "Preparing reconnect...",
        )
        stopAutoReconnectLoop()
        if (uiState.value.connectionStatus.phase in setOf(
                RemodexConnectionPhase.CONNECTING,
                RemodexConnectionPhase.RETRYING,
            ) || uiState.value.isConnected
        ) {
            repository.disconnect()
        }
        waitForConnectionRecoveryToStop()
        clearAutoReconnectState()
    }

    private fun stopAutoReconnectForBridgeSwitch() {
        isManualScannerActive = false
        stopAutoReconnectLoop()
    }

    private suspend fun stopAutoReconnectForManualScan() {
        isManualScannerActive = true
        stopAutoReconnectLoop()
        if (uiState.value.connectionStatus.phase in setOf(
                RemodexConnectionPhase.CONNECTING,
                RemodexConnectionPhase.RETRYING,
            ) || uiState.value.isConnected
        ) {
            repository.disconnect()
        }
        waitForConnectionRecoveryToStop()
        clearAutoReconnectState()
    }

    private fun stopAutoReconnectLoop() {
        autoReconnectJob?.cancel()
        autoReconnectJob = null
    }

    private suspend fun waitForConnectionRecoveryToStop() {
        while (autoReconnectJob?.isActive == true ||
            uiState.value.connectionStatus.phase in setOf(
                RemodexConnectionPhase.CONNECTING,
                RemodexConnectionPhase.RETRYING,
            )
        ) {
            delay(reconnectSleepChunkMillis())
        }
    }

    private suspend fun sleepForReconnectBackoff(totalMillis: Long): Boolean {
        var remaining = totalMillis
        while (remaining > 0L) {
            if (!shouldContinueAutoReconnectWait(repository.session.value)
            ) {
                return false
            }
            val chunk = minOf(reconnectSleepChunkMillis(), remaining)
            delay(chunk)
            remaining -= chunk
        }
        return shouldContinueAutoReconnectWait(repository.session.value)
    }

    private fun reconnectSleepChunkMillis(): Long {
        return reconnectSleepChunkMillisOverride ?: 100L
    }

    private fun clearAutoReconnectState() {
        autoReconnectState.value = AutoReconnectUiState()
    }

    private fun logAutoReconnect(
        event: String,
        snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot = repository.session.value,
        extra: String = "",
    ) {
        val suffix = extra.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        runCatching {
            Log.d(
                logTag,
                "event=$event isForeground=$isAppForeground manualScanner=$isManualScannerActive suppressed=$suppressAutoReconnectUntilManualConnect " +
                    "secureState=${snapshot.secureConnection.secureState} autoReconnectAllowed=${snapshot.secureConnection.autoReconnectAllowed} " +
                    "connectionPhase=${snapshot.connectionStatus.phase} trustedMac=${snapshot.trustedMac != null}$suffix",
            )
        }
    }

    private fun detectThreadCompletionBanner(snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot) {
        val completedThreads = snapshot.threads.filter { currentThread ->
            val previousThread = previousThreadsById[currentThread.id] ?: return@filter false
            previousThread.isRunning &&
                !currentThread.isRunning &&
                currentThread.latestTurnTerminalState == RemodexTurnTerminalState.COMPLETED
        }
        if (isAppForeground && completedThreads.isNotEmpty()) {
            completionHapticSignalState.update { value -> value + 1L }
        }

        val selectedThreadId = snapshot.selectedThread?.id
        val completedThread = completedThreads.firstOrNull { currentThread ->
            currentThread.id != selectedThreadId
        }
        if (completedThread != null) {
            threadCompletionBannerState.value = ThreadCompletionBannerUiState(
                threadId = completedThread.id,
                title = completedThread.displayTitle,
            )
        }
    }

    private fun detectContextCompactionCompletion(
        snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
    ) {
        val currentThread = snapshot.selectedThread ?: return
        val previousThread = previousThreadsById[currentThread.id] ?: return
        if (!didCompleteContextCompaction(previousThread, currentThread)) {
            return
        }
        refreshUsageStatus(threadId = currentThread.id)
    }

    private fun didCompleteContextCompaction(
        previousThread: RemodexThreadSummary,
        currentThread: RemodexThreadSummary,
    ): Boolean {
        val previousCompaction = previousThread.messages.lastOrNull { message ->
            message.kind == ConversationItemKind.CONTEXT_COMPACTION
        }
        val currentCompaction = currentThread.messages.lastOrNull { message ->
            message.kind == ConversationItemKind.CONTEXT_COMPACTION
        } ?: return false

        return if (previousCompaction?.id == currentCompaction.id) {
            previousCompaction.isStreaming && !currentCompaction.isStreaming
        } else {
            !currentCompaction.isStreaming
        }
    }

    private fun refreshUsageStatus(threadId: String?) {
        if (isRefreshingUsageState.value) {
            return
        }
        viewModelScope.launch {
            isRefreshingUsageState.value = true
            try {
                repository.refreshUsageStatus(threadId)
            } finally {
                isRefreshingUsageState.value = false
            }
        }
    }

    private fun connectionCopy(secureConnection: SecureConnectionSnapshot): Pair<String, String> {
        val headline = when (secureConnection.secureState) {
            SecureConnectionState.NOT_PAIRED -> "Waiting for your Mac"
            SecureConnectionState.TRUSTED_MAC -> "Saved pairing ready"
            SecureConnectionState.LIVE_SESSION_UNRESOLVED -> "Trusted Mac offline"
            SecureConnectionState.HANDSHAKING -> {
                if (secureConnection.attempt > 0) {
                    "Pairing Mac"
                } else {
                    "Pairing QR saved"
                }
            }

            SecureConnectionState.ENCRYPTED -> "Connected to Remodex"
            SecureConnectionState.RECONNECTING -> "Retrying trusted reconnect"
            SecureConnectionState.REPAIR_REQUIRED -> "Re-pair required"
            SecureConnectionState.UPDATE_REQUIRED -> "Update bridge on your Mac"
        }
        return headline to secureConnection.phaseMessage
    }

    private fun updateComposerAutocomplete(
        threadId: String,
        input: String,
    ) {
        val composer = uiState.value.composer
        val slashToken = RemodexComposerCommandLogic.trailingSlashCommandToken(input)
        val fileToken = RemodexComposerCommandLogic.trailingFileToken(input)
        val skillToken = RemodexComposerCommandLogic.trailingSkillToken(input)

        when {
            fileToken != null -> {
                if (RemodexComposerCommandLogic.hasClosedConfirmedFileMentionPrefix(
                        text = input,
                        confirmedMentions = composer.mentionedFiles,
                    )
                ) {
                    clearComposerAutocomplete()
                    return
                }
                fileAutocompleteJob?.cancel()
                skillAutocompleteJob?.cancel()
                if (fileToken.query.length < MinAutocompleteQueryLength) {
                    clearComposerAutocomplete()
                    return
                }
                autocompleteState.value = RemodexComposerAutocompleteState(
                    panel = RemodexComposerAutocompletePanel.FILES,
                    fileQuery = fileToken.query,
                    isFileLoading = true,
                )
                val expectedQuery = fileToken.query
                fileAutocompleteJob = viewModelScope.launch {
                    delay(AutocompleteDebounceMs)
                    runCatching {
                        repository.fuzzyFileSearch(threadId, expectedQuery)
                    }.onSuccess { matches ->
                        val currentToken = RemodexComposerCommandLogic.trailingFileToken(
                            composerDrafts.value[threadId].orEmpty(),
                        )
                        if (currentToken?.query != expectedQuery) {
                            return@onSuccess
                        }
                        autocompleteState.value = autocompleteState.value.copy(
                            panel = RemodexComposerAutocompletePanel.FILES,
                            fileQuery = expectedQuery,
                            fileItems = matches.take(MaxAutocompleteItems),
                            isFileLoading = false,
                        )
                    }.onFailure {
                        val currentToken = RemodexComposerCommandLogic.trailingFileToken(
                            composerDrafts.value[threadId].orEmpty(),
                        )
                        if (currentToken?.query != expectedQuery) {
                            return@onFailure
                        }
                        clearComposerAutocomplete()
                    }
                }
            }

            skillToken != null -> {
                skillAutocompleteJob?.cancel()
                fileAutocompleteJob?.cancel()
                if (skillToken.query.length < MinAutocompleteQueryLength) {
                    clearComposerAutocomplete()
                    return
                }
                autocompleteState.value = RemodexComposerAutocompleteState(
                    panel = RemodexComposerAutocompletePanel.SKILLS,
                    skillQuery = skillToken.query,
                    isSkillLoading = true,
                )
                val expectedQuery = skillToken.query
                skillAutocompleteJob = viewModelScope.launch {
                    delay(AutocompleteDebounceMs)
                    runCatching {
                        repository.listSkills(threadId = threadId, forceReload = false)
                            .filter { skill ->
                                skill.enabled && skillSearchBlob(skill).contains(expectedQuery.lowercase())
                            }
                            .take(MaxAutocompleteItems)
                    }.onSuccess { matches ->
                        val currentToken = RemodexComposerCommandLogic.trailingSkillToken(
                            composerDrafts.value[threadId].orEmpty(),
                        )
                        if (currentToken?.query != expectedQuery) {
                            return@onSuccess
                        }
                        autocompleteState.value = autocompleteState.value.copy(
                            panel = RemodexComposerAutocompletePanel.SKILLS,
                            skillQuery = expectedQuery,
                            skillItems = matches,
                            isSkillLoading = false,
                        )
                    }.onFailure {
                        val currentToken = RemodexComposerCommandLogic.trailingSkillToken(
                            composerDrafts.value[threadId].orEmpty(),
                        )
                        if (currentToken?.query != expectedQuery) {
                            return@onFailure
                        }
                        clearComposerAutocomplete()
                    }
                }
            }

            slashToken != null -> {
                fileAutocompleteJob?.cancel()
                skillAutocompleteJob?.cancel()
                val availableCommands = availableSlashCommands(
                    composer = composer,
                    draftText = input,
                )
                autocompleteState.value = autocompleteState.value.copy(
                    panel = RemodexComposerAutocompletePanel.COMMANDS,
                    slashQuery = slashToken.query,
                    availableCommands = availableCommands,
                    slashCommands = RemodexSlashCommand.filtered(slashToken.query)
                        .filter { command -> availableCommands.contains(command) },
                )
            }

            else -> clearComposerAutocomplete()
        }
    }

    private fun clearComposerAutocomplete() {
        fileAutocompleteJob?.cancel()
        skillAutocompleteJob?.cancel()
        autocompleteState.value = RemodexComposerAutocompleteState()
    }

    private fun refreshGitState(threadId: String) {
        viewModelScope.launch {
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.loadGitState(threadId) }
                .onSuccess { nextState ->
                    selectedGitBaseBranchByThread.update { branchesByThread ->
                        val currentBaseBranch = branchesByThread[threadId]
                        if (!currentBaseBranch.isNullOrBlank()) {
                            branchesByThread
                        } else {
                            branchesByThread.toMutableMap().apply {
                                this[threadId] = nextState.branches.defaultBranch.orEmpty()
                            }
                        }
                    }
                    updateGitState(threadId) { nextState }
                }
                .onFailure { error ->
                    updateGitState(threadId) { currentState ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    private fun updateGitState(
        threadId: String,
        transform: (RemodexGitState) -> RemodexGitState,
    ) {
        gitStates.update { statesByThread ->
            val current = statesByThread[threadId] ?: RemodexGitState()
            statesByThread.toMutableMap().apply {
                this[threadId] = transform(current)
            }
        }
    }

    private fun buildPromptPayload(
        draftText: String,
        mentionedFiles: List<RemodexComposerMentionedFile>,
        isSubagentsSelectionArmed: Boolean,
    ): String {
        val withMentions = mentionedFiles.fold(draftText) { currentText, mention ->
            RemodexComposerCommandLogic.replaceFileMentionAliases(
                text = currentText,
                mention = mention,
            )
        }
        return RemodexComposerCommandLogic.applySubagentsSelection(
            text = withMentions,
            isSelected = isSubagentsSelectionArmed,
        )
    }

    private fun clearComposer(threadId: String) {
        composerDrafts.update { draftsByThread ->
            draftsByThread.toMutableMap().apply { this[threadId] = "" }
        }
        composerAttachments.update { attachmentsByThread ->
            attachmentsByThread.toMutableMap().apply { this[threadId] = emptyList() }
        }
        composerMentionedFiles.update { mentionsByThread ->
            mentionsByThread.toMutableMap().apply { remove(threadId) }
        }
        composerMentionedSkills.update { mentionsByThread ->
            mentionsByThread.toMutableMap().apply { remove(threadId) }
        }
        composerReviewSelections.update { selectionsByThread ->
            selectionsByThread.toMutableMap().apply { remove(threadId) }
        }
        composerSubagentsSelections.update { selectionsByThread ->
            selectionsByThread.toMutableMap().apply { this[threadId] = false }
        }
        attachmentMessages.update { messagesByThread ->
            messagesByThread.toMutableMap().apply { remove(threadId) }
        }
        composerMessages.update { messagesByThread ->
            messagesByThread.toMutableMap().apply { remove(threadId) }
        }
        composerVoiceRecoveryReasons.update { reasonsByThread ->
            reasonsByThread.toMutableMap().apply { remove(threadId) }
        }
        clearComposerAutocomplete()
    }

    private fun bumpComposerSendDismissSignal(threadId: String) {
        composerSendUiSignals.update { signalsByThread ->
            val current = signalsByThread[threadId] ?: ComposerSendUiSignals()
            signalsByThread.toMutableMap().apply {
                this[threadId] = current.copy(
                    dismissSignal = current.dismissSignal + 1,
                )
            }
        }
    }

    private fun bumpComposerSendAnchorSignal(threadId: String) {
        composerSendUiSignals.update { signalsByThread ->
            val current = signalsByThread[threadId] ?: ComposerSendUiSignals()
            signalsByThread.toMutableMap().apply {
                this[threadId] = current.copy(
                    anchorSignal = current.anchorSignal + 1,
                )
            }
        }
    }

    private fun startPlanComposerSession(
        threadId: String,
        anchorMessageId: String?,
    ) {
        planComposerSessions.update { sessionsByThread ->
            sessionsByThread.toMutableMap().apply {
                this[threadId] = PlanComposerSessionUiState(anchorMessageId = anchorMessageId)
            }
        }
    }

    private fun clearPlanComposerSession(threadId: String) {
        planComposerSessions.update { sessionsByThread ->
            sessionsByThread.toMutableMap().apply { remove(threadId) }
        }
    }

    private fun restoreComposer(
        threadId: String,
        pendingComposerState: PendingComposerSendState,
    ) {
        composerDrafts.update { draftsByThread ->
            draftsByThread.toMutableMap().apply {
                this[threadId] = pendingComposerState.draftText
            }
        }
        composerAttachments.update { attachmentsByThread ->
            attachmentsByThread.toMutableMap().apply {
                this[threadId] = pendingComposerState.attachments
            }
        }
        composerMentionedFiles.update { mentionsByThread ->
            mentionsByThread.toMutableMap().apply {
                this[threadId] = pendingComposerState.mentionedFiles
            }
        }
        composerMentionedSkills.update { mentionsByThread ->
            mentionsByThread.toMutableMap().apply {
                this[threadId] = pendingComposerState.mentionedSkills
            }
        }
        composerReviewSelections.update { selectionsByThread ->
            selectionsByThread.toMutableMap().apply {
                if (pendingComposerState.reviewSelection == null) {
                    remove(threadId)
                } else {
                    this[threadId] = pendingComposerState.reviewSelection
                }
            }
        }
        composerSubagentsSelections.update { selectionsByThread ->
            selectionsByThread.toMutableMap().apply {
                this[threadId] = pendingComposerState.isSubagentsSelectionArmed
            }
        }
    }

    private fun setComposerMessage(
        threadId: String,
        message: String?,
    ) {
        composerMessages.update { messagesByThread ->
            messagesByThread.toMutableMap().apply {
                if (message.isNullOrBlank()) {
                    remove(threadId)
                } else {
                    this[threadId] = message
                }
            }
        }
    }

    private fun clearComposerMessage(threadId: String) {
        setComposerMessage(threadId, null)
    }

    private fun setComposerVoiceRecovery(
        threadId: String,
        reason: ComposerVoiceRecoveryReason?,
    ) {
        composerVoiceRecoveryReasons.update { reasonsByThread ->
            reasonsByThread.toMutableMap().apply {
                if (reason == null) {
                    remove(threadId)
                } else {
                    this[threadId] = reason
                }
            }
        }
    }

    private fun clearComposerVoiceRecovery(threadId: String) {
        setComposerVoiceRecovery(threadId, null)
    }

    private fun appendVoiceTranscript(
        threadId: String,
        transcript: String,
    ) {
        composerDrafts.update { draftsByThread ->
            draftsByThread.toMutableMap().apply {
                val currentDraft = this[threadId].orEmpty()
                this[threadId] = appendVoiceTranscriptToDraft(currentDraft, transcript)
            }
        }
    }

    private fun invalidateVoiceCapture() {
        nextVoiceOperationGeneration()
        voiceButtonModeState.value = ComposerVoiceButtonMode.IDLE
        voiceRecorder.cancelRecording()
    }

    private fun nextVoiceOperationGeneration(): Int {
        voiceOperationGeneration += 1
        return voiceOperationGeneration
    }

    private fun isVoiceOperationCurrent(generation: Int): Boolean {
        return generation == voiceOperationGeneration
    }

    private fun clearReviewSelectionIfConfirmed(threadId: String) {
        if (composerReviewSelections.value[threadId]?.target != null) {
            composerReviewSelections.update { selectionsByThread ->
                selectionsByThread.toMutableMap().apply { remove(threadId) }
            }
        }
    }

    private fun availableSlashCommands(
        composer: ComposerUiState,
        draftText: String = composer.draftText,
    ): List<RemodexSlashCommand> {
        val allowsFork = RemodexComposerCommandLogic.canOfferForkSlashCommand(
            text = draftText,
            mentionedFileCount = composer.mentionedFiles.size,
            mentionedSkillCount = composer.mentionedSkills.size,
            attachmentCount = composer.attachments.size,
            hasReviewSelection = composer.reviewSelection != null,
            hasSubagentsSelection = composer.isSubagentsSelectionArmed,
            isPlanModeArmed = composer.runtimeConfig.planningMode == RemodexPlanningMode.PLAN,
        ) && availableForkDestinations(
            gitState = composer.gitState,
            supportsThreadFork = uiState.value.supportsThreadFork,
        ).isNotEmpty()
        return RemodexSlashCommand.allCommands.filter { command ->
            command != RemodexSlashCommand.FORK || allowsFork
        }
    }

    private fun skillSearchBlob(skill: RemodexSkillMetadata): String {
        return buildString {
            append(skill.name)
            append(' ')
            append(skill.description.orEmpty())
            append(' ')
            append(skill.path.orEmpty())
        }.lowercase()
    }

    private fun conversationBanner(
        selectedThread: RemodexThreadSummary?,
        secureConnection: SecureConnectionSnapshot,
    ): String? {
        if (selectedThread == null) {
            return null
        }
        return when {
            secureConnection.secureState != SecureConnectionState.ENCRYPTED && selectedThread.isRunning -> {
                "Trying to reconnect to your Mac."
            }
            else -> null
        }
    }

    private fun approvalBanner(
        snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
        isAppForeground: Boolean,
        dismissedRequestIds: Set<String>,
    ): ApprovalBannerUiState? {
        if (!isAppForeground) {
            return null
        }
        val request = snapshot.pendingApprovalRequest ?: return null
        if (request.id in dismissedRequestIds) {
            return null
        }
        val requestThreadId = request.threadId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (snapshot.selectedThread?.id == requestThreadId) {
            return null
        }
        val thread = snapshot.threads.firstOrNull { candidate -> candidate.id == requestThreadId } ?: return null
        return ApprovalBannerUiState(
            threadId = requestThreadId,
            title = thread.displayTitle,
            message = remodexApprovalRequestSummary(request),
        )
    }

    private fun presentTransientBanner(message: String) {
        transientBannerState.value = message
    }

    private fun maybeRunGitBranchOperation(
        threadId: String,
        operation: PendingGitBranchOperation,
    ) {
        val alert = gitBranchAlert(operation, uiState.value.composer.gitState)
        if (alert != null) {
            pendingGitBranchOperation = operation
            gitSyncAlerts.update { alertsByThread ->
                alertsByThread.toMutableMap().apply {
                    this[threadId] = alert
                }
            }
            return
        }
        pendingGitBranchOperation = null
        performPendingGitBranchOperation(threadId, operation)
    }

    private fun performPendingGitBranchOperation(
        threadId: String,
        operation: PendingGitBranchOperation,
    ) {
        when (operation) {
            is PendingGitBranchOperation.CreateBranch -> {
                launchGitAction(
                    threadId = threadId,
                    title = "Branch Creation Failed",
                    fallbackMessage = "Could not create branch.",
                ) {
                    repository.createGitBranch(threadId, operation.branchName)
                }
            }
            is PendingGitBranchOperation.SwitchBranch -> {
                launchGitAction(
                    threadId = threadId,
                    title = "Branch Switch Failed",
                    fallbackMessage = "Could not switch branch.",
                ) {
                    repository.checkoutGitBranch(threadId, operation.branchName)
                }
            }
            is PendingGitBranchOperation.CreateWorktree -> {
                if (operation.followUp == GitWorktreeFollowUp.HANDOFF_CURRENT_THREAD) {
                    viewModelScope.launch {
                        clearGitSyncAlert(threadId)
                        updateGitState(threadId) { currentState ->
                            currentState.copy(isLoading = true, errorMessage = null)
                        }
                        runCatching {
                            val result = repository.createGitWorktreeResult(
                                threadId = threadId,
                                name = operation.branchName,
                                baseBranch = operation.baseBranch,
                                changeTransfer = operation.changeTransfer,
                            )
                            repository.moveThreadToProjectPath(
                                threadId = threadId,
                                projectPath = result.worktreePath,
                            )
                            result
                        }.onSuccess { result ->
                            refreshGitState(threadId)
                            updateGitState(threadId) { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    lastActionMessage = "Moved this thread to worktree ${result.branch}.",
                                    errorMessage = null,
                                )
                            }
                            presentTransientBanner("Now in worktree ${result.branch}.")
                        }.onFailure { error ->
                            updateGitState(threadId) { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    errorMessage = null,
                                )
                            }
                            showGitSyncAlert(
                                threadId = threadId,
                                title = "Worktree Creation Failed",
                                message = error.message ?: "Could not create worktree.",
                            )
                        }
                    }
                } else {
                    launchGitAction(
                        threadId = threadId,
                        title = "Worktree Creation Failed",
                        fallbackMessage = "Could not create worktree.",
                    ) {
                        repository.createGitWorktree(
                            threadId = threadId,
                            name = operation.branchName,
                            baseBranch = operation.baseBranch,
                            changeTransfer = operation.changeTransfer,
                        )
                    }
                }
            }
        }
    }

    private fun showGitSyncAlert(
        threadId: String,
        message: String,
        title: String = "Git Error",
        buttons: List<RemodexGitSyncAlertButtonUiState> = defaultGitSyncAlertButtons(),
    ) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) {
            return
        }
        gitSyncAlerts.update { alertsByThread ->
            alertsByThread.toMutableMap().apply {
                this[threadId] = RemodexGitSyncAlertUiState(
                    title = title,
                    message = trimmedMessage,
                    buttons = buttons,
                )
            }
        }
    }

    private fun clearGitSyncAlert(threadId: String) {
        gitSyncAlerts.update { alertsByThread ->
            if (!alertsByThread.containsKey(threadId)) {
                alertsByThread
            } else {
                alertsByThread.toMutableMap().apply {
                    remove(threadId)
                }
            }
        }
    }

    private suspend fun shouldSuppressForkFailureAfterBridgeUpdate(): Boolean {
        yield()
        val snapshot = repository.session.value
        return snapshot.bridgeUpdatePrompt != null || !snapshot.supportsThreadFork
    }

    private fun gitBranchAlert(
        operation: PendingGitBranchOperation,
        gitState: RemodexGitState,
    ): RemodexGitSyncAlertUiState? {
        val currentBranch = gitState.sync?.currentBranch?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: gitState.branches.currentBranch?.trim().orEmpty()
        val defaultBranch = gitState.branches.defaultBranch?.trim().orEmpty()
        val sync = gitState.sync
        val isDirty = sync?.isDirty == true
        val localOnlyCommitCount = sync?.localOnlyCommitCount ?: 0
        val onDefaultBranch = currentBranch.isNotEmpty() && currentBranch == defaultBranch

        return when (operation) {
            is PendingGitBranchOperation.CreateBranch -> {
                when {
                    isDirty -> RemodexGitSyncAlertUiState(
                        title = "Bring local changes to '${operation.branchName}'?",
                        message = newBranchDirtyAlertMessage(
                            gitState = gitState,
                            branchName = operation.branchName,
                            currentBranch = currentBranch,
                            defaultBranch = defaultBranch,
                            localOnlyCommitCount = localOnlyCommitCount,
                        ),
                        buttons = listOf(
                            RemodexGitSyncAlertButtonUiState(
                                title = "Cancel",
                                role = RemodexGitSyncAlertButtonRole.CANCEL,
                                action = RemodexGitSyncAlertAction.DISMISS_ONLY,
                            ),
                            RemodexGitSyncAlertButtonUiState(
                                title = "Carry to New Branch",
                                action = RemodexGitSyncAlertAction.CONTINUE_GIT_BRANCH_OPERATION,
                            ),
                            RemodexGitSyncAlertButtonUiState(
                                title = "Commit, Create & Switch",
                                action = RemodexGitSyncAlertAction.COMMIT_AND_CONTINUE_GIT_BRANCH_OPERATION,
                            ),
                        ),
                    )
                    onDefaultBranch && localOnlyCommitCount > 0 -> {
                        val commitLabel = if (localOnlyCommitCount == 1) {
                            "1 local commit"
                        } else {
                            "$localOnlyCommitCount local commits"
                        }
                        val dirtySuffix = if (isDirty) {
                            " Uncommitted changes also stay in this working copy and will follow onto the new branch after checkout."
                        } else {
                            ""
                        }
                        RemodexGitSyncAlertUiState(
                            title = "Local commits stay on $defaultBranch",
                            message = "$defaultBranch already has $commitLabel that are not on the remote. Creating '${operation.branchName}' now starts the new branch from the current HEAD, but those commits stay in $defaultBranch's history.$dirtySuffix",
                            buttons = listOf(
                                RemodexGitSyncAlertButtonUiState(
                                    title = "Cancel",
                                    role = RemodexGitSyncAlertButtonRole.CANCEL,
                                    action = RemodexGitSyncAlertAction.DISMISS_ONLY,
                                ),
                                RemodexGitSyncAlertButtonUiState(
                                    title = "Create Anyway",
                                    action = RemodexGitSyncAlertAction.CONTINUE_GIT_BRANCH_OPERATION,
                                ),
                            ),
                        )
                    }
                    else -> null
                }
            }
            is PendingGitBranchOperation.CreateWorktree -> {
                when {
                    isDirty && currentBranch != operation.baseBranch -> {
                        val transferVerb = if (operation.changeTransfer == RemodexGitWorktreeChangeTransferMode.MOVE) {
                            "move"
                        } else {
                            "copy"
                        }
                        RemodexGitSyncAlertUiState(
                            title = "${transferVerb.replaceFirstChar(Char::uppercase)} local changes from the current branch",
                            message = "Creating '${operation.branchName}' can $transferVerb tracked local changes only from the current branch. Switch the base branch to '$currentBranch' or clean up local changes before creating the worktree.",
                        )
                    }
                    onDefaultBranch && currentBranch == operation.baseBranch && localOnlyCommitCount > 0 -> {
                        val commitLabel = if (localOnlyCommitCount == 1) {
                            "1 local commit"
                        } else {
                            "$localOnlyCommitCount local commits"
                        }
                        val dirtySuffix = if (isDirty) {
                            if (operation.changeTransfer == RemodexGitWorktreeChangeTransferMode.MOVE) {
                                " Tracked local changes will move into the new worktree; ignored files stay here."
                            } else {
                                " Tracked local changes will also be copied into the new worktree; ignored files stay here."
                            }
                        } else {
                            ""
                        }
                        RemodexGitSyncAlertUiState(
                            title = "Local commits stay on $defaultBranch",
                            message = "$defaultBranch already has $commitLabel that are not on the remote. Creating the new worktree branch '${operation.branchName}' from ${operation.baseBranch} starts from the current HEAD, but those commits stay in $defaultBranch's history too.$dirtySuffix",
                            buttons = listOf(
                                RemodexGitSyncAlertButtonUiState(
                                    title = "Cancel",
                                    role = RemodexGitSyncAlertButtonRole.CANCEL,
                                    action = RemodexGitSyncAlertAction.DISMISS_ONLY,
                                ),
                                RemodexGitSyncAlertButtonUiState(
                                    title = "Create Anyway",
                                    action = RemodexGitSyncAlertAction.CONTINUE_GIT_BRANCH_OPERATION,
                                ),
                            ),
                        )
                    }
                    else -> null
                }
            }
            is PendingGitBranchOperation.SwitchBranch -> {
                if (!isDirty) {
                    null
                } else {
                    RemodexGitSyncAlertUiState(
                        title = "Commit changes before switching branch?",
                        message = dirtyBranchAlertMessage(
                            gitState = gitState,
                            intro = "These local changes can block checkout or be hard to reason about after the switch. Commit them on ${currentBranch.ifEmpty { "the current branch" }} first, then switch to '${operation.branchName}'.",
                        ),
                        buttons = listOf(
                            RemodexGitSyncAlertButtonUiState(
                                title = "Cancel",
                                role = RemodexGitSyncAlertButtonRole.CANCEL,
                                action = RemodexGitSyncAlertAction.DISMISS_ONLY,
                            ),
                            RemodexGitSyncAlertButtonUiState(
                                title = "Commit & Switch",
                                action = RemodexGitSyncAlertAction.COMMIT_AND_CONTINUE_GIT_BRANCH_OPERATION,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun defaultGitSyncAlertButtons(): List<RemodexGitSyncAlertButtonUiState> {
        return listOf(
            RemodexGitSyncAlertButtonUiState(
                title = "OK",
                role = RemodexGitSyncAlertButtonRole.CANCEL,
                action = RemodexGitSyncAlertAction.DISMISS_ONLY,
            ),
        )
    }

    private fun launchGitAction(
        threadId: String,
        title: String,
        fallbackMessage: String,
        action: suspend () -> RemodexGitState,
    ) {
        viewModelScope.launch {
            clearGitSyncAlert(threadId)
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            runCatching { action() }
                .onSuccess { nextState ->
                    updateGitState(threadId) {
                        nextState.copy(errorMessage = null)
                    }
                }
                .onFailure { error ->
                    updateGitState(threadId) { currentState ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    showGitSyncAlert(
                        threadId = threadId,
                        title = title,
                        message = error.message ?: fallbackMessage,
                    )
                }
        }
    }

    private fun assistantRevertPresentations(
        threads: List<RemodexThreadSummary>,
        selectedThread: RemodexThreadSummary?,
        secureConnection: SecureConnectionSnapshot,
        revertedMessageIds: Set<String>,
    ): Map<String, RemodexAssistantRevertPresentation> {
        if (selectedThread == null) {
            return emptyMap()
        }

        val activeRepoRoots = threads.asSequence()
            .filter(RemodexThreadSummary::isRunning)
            .mapNotNull { thread -> normalizeRepoRoot(thread.projectPath) }
            .toSet()
        val sameRepoReadyChangeSets = threads.asSequence()
            .filter { thread ->
                normalizeRepoRoot(thread.projectPath) == normalizeRepoRoot(selectedThread.projectPath)
            }
            .flatMap { thread ->
                thread.messages.asSequence().mapNotNull { item ->
                    item.assistantChangeSet?.takeIf { changeSet ->
                        changeSet.status == RemodexAssistantChangeSetStatus.READY ||
                            changeSet.status == RemodexAssistantChangeSetStatus.COLLECTING
                    }
                }
            }.toList()

        return selectedThread.messages.mapNotNull { message ->
            val changeSet = message.assistantChangeSet ?: return@mapNotNull null
            message.id to assistantRevertPresentation(
                changeSet = if (message.id in revertedMessageIds) {
                    changeSet.copy(status = RemodexAssistantChangeSetStatus.REVERTED)
                } else {
                    changeSet
                },
                workingDirectory = selectedThread.projectPath,
                repoBusy = normalizeRepoRoot(selectedThread.projectPath) in activeRepoRoots,
                isConnected = secureConnection.secureState == SecureConnectionState.ENCRYPTED,
                siblingChangeSets = sameRepoReadyChangeSets,
            )
        }.toMap()
    }

    private fun assistantRevertPresentation(
        changeSet: RemodexAssistantChangeSet,
        workingDirectory: String,
        repoBusy: Boolean,
        isConnected: Boolean,
        siblingChangeSets: List<RemodexAssistantChangeSet>,
    ): RemodexAssistantRevertPresentation {
        val hasWorkingDirectory = normalizeRepoRoot(workingDirectory) != null
        val overlappingFiles = siblingChangeSets.asSequence()
            .filter { candidate -> candidate.id != changeSet.id }
            .flatMap { candidate ->
                candidate.fileChanges.asSequence().map { fileChange -> fileChange.path }
            }
            .toSet()
            .intersect(changeSet.fileChanges.map { fileChange -> fileChange.path }.toSet())
            .sorted()

        return when (changeSet.status) {
            RemodexAssistantChangeSetStatus.READY -> {
                when {
                    !hasWorkingDirectory -> RemodexAssistantRevertPresentation(
                        title = "Cannot undo",
                        isEnabled = false,
                        helperText = "The selected local folder is not available on this Mac.",
                        riskLevel = RemodexAssistantRevertRiskLevel.BLOCKED,
                    )

                    !isConnected -> RemodexAssistantRevertPresentation(
                        title = "Cannot undo",
                        isEnabled = false,
                        helperText = "Reconnect to your trusted Mac before undoing this response.",
                        riskLevel = RemodexAssistantRevertRiskLevel.BLOCKED,
                    )

                    repoBusy -> RemodexAssistantRevertPresentation(
                        title = "Cannot undo",
                        isEnabled = false,
                        helperText = "Finish the active run in this repo before undoing this response.",
                        riskLevel = RemodexAssistantRevertRiskLevel.BLOCKED,
                    )

                    overlappingFiles.isNotEmpty() -> RemodexAssistantRevertPresentation(
                        title = "Undo changes",
                        isEnabled = true,
                        helperText = "Review overlapping files before undoing this response.",
                        riskLevel = RemodexAssistantRevertRiskLevel.WARNING,
                        warningText = "Other chats also changed ${overlappingFiles.size} of these files.",
                        overlappingFiles = overlappingFiles,
                    )

                    else -> RemodexAssistantRevertPresentation(
                        title = "Undo changes",
                        isEnabled = true,
                        helperText = "Only changes from this response will be reverted unless later edits overlap.",
                        riskLevel = RemodexAssistantRevertRiskLevel.SAFE,
                    )
                }
            }

            RemodexAssistantChangeSetStatus.COLLECTING -> RemodexAssistantRevertPresentation(
                title = "Undo changes",
                isEnabled = false,
                helperText = "This response is still collecting its final patch.",
                riskLevel = RemodexAssistantRevertRiskLevel.BLOCKED,
            )

            RemodexAssistantChangeSetStatus.REVERTED -> RemodexAssistantRevertPresentation(
                title = "Already undone",
                isEnabled = false,
                riskLevel = RemodexAssistantRevertRiskLevel.BLOCKED,
            )

            RemodexAssistantChangeSetStatus.FAILED,
            RemodexAssistantChangeSetStatus.NOT_REVERTABLE -> RemodexAssistantRevertPresentation(
                title = "Cannot undo",
                isEnabled = false,
                helperText = changeSet.unsupportedReasons.firstOrNull(),
                riskLevel = RemodexAssistantRevertRiskLevel.BLOCKED,
            )
        }
    }

    private fun normalizeRepoRoot(path: String?): String? {
        return path?.trim()?.ifEmpty { null }
    }

    private fun normalizeComparableProjectPath(path: String?): String? {
        return path
            ?.trim()
            ?.removeSuffix("/")
            ?.ifEmpty { null }
    }

    private fun createPullRequestValidationMessage(gitState: RemodexGitState): String? {
        val sync = gitState.sync
        if (sync == null) {
            return "Git status is still loading. Wait a moment and retry."
        }
        val branch = sync.currentBranch?.trim().orEmpty()
        if (branch.isEmpty()) {
            return "No current branch found."
        }
        val defaultBranch = gitState.branches.defaultBranch?.trim().orEmpty()
        if (defaultBranch.isEmpty()) {
            return "Could not determine the repository default branch."
        }
        if (branch == defaultBranch) {
            return "Switch to a feature branch before creating a PR."
        }
        if (!sync.isPublishedToRemote || sync.trackingBranch.isNullOrBlank()) {
            return "Push this branch before creating a PR."
        }
        if (sync.aheadCount > 0) {
            return "Push this branch before creating a PR."
        }
        return null
    }

    private fun buildPullRequestUrl(ownerRepo: String, branch: String, base: String): String {
        val encodedBase = encodeGitHubCompareRef(base)
        val encodedBranch = encodeGitHubCompareRef(branch)
        return "https://github.com/$ownerRepo/compare/$encodedBase...$encodedBranch?expand=1"
    }

    private fun encodeGitHubCompareRef(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
            .replace("%2F", "/")
    }

    private fun dirtyBranchAlertMessage(
        gitState: RemodexGitState,
        intro: String,
    ): String {
        val files = gitState.sync?.files.orEmpty()
        if (files.isEmpty()) {
            return intro
        }
        val previewFiles = files.take(3).map { file -> file.path }
        val fileLines = previewFiles.joinToString(separator = "\n") { path -> "- $path" }
        val remainingCount = files.size - previewFiles.size
        val overflowLine = if (remainingCount > 0) {
            "\n- +$remainingCount more files"
        } else {
            ""
        }
        return "$intro\n\nFiles with local changes:\n$fileLines$overflowLine"
    }

    private fun newBranchDirtyAlertMessage(
        gitState: RemodexGitState,
        branchName: String,
        currentBranch: String,
        defaultBranch: String,
        localOnlyCommitCount: Int,
    ): String {
        val sourceBranch = currentBranch.ifEmpty { "the current branch" }
        var intro = "You're creating '$branchName' from $sourceBranch. Carry your tracked changes onto the new branch, or commit first and then create + switch."
        if (defaultBranch.isNotEmpty() &&
            sourceBranch == defaultBranch &&
            localOnlyCommitCount > 0
        ) {
            val commitLabel = if (localOnlyCommitCount == 1) {
                "1 local commit"
            } else {
                "$localOnlyCommitCount local commits"
            }
            intro = "$defaultBranch already has $commitLabel that are not on the remote. Those commits stay on $defaultBranch's history. $intro"
        }
        return dirtyBranchAlertMessage(gitState = gitState, intro = intro)
    }

    private fun composerState(
        draftText: String,
        attachments: List<RemodexComposerAttachment>,
        mentionedFiles: List<RemodexComposerMentionedFile>,
        mentionedSkills: List<RemodexComposerMentionedSkill>,
        reviewSelection: RemodexComposerReviewSelection?,
        isSubagentsSelectionArmed: Boolean,
        attachmentLimitMessage: String?,
        composerMessage: String?,
        voiceRecoveryReason: ComposerVoiceRecoveryReason?,
        gptAccountSnapshot: RemodexGptAccountSnapshot,
        autocomplete: RemodexComposerAutocompleteState,
        voiceUiState: ComposerVoiceUiState,
        isConnected: Boolean,
        gitState: RemodexGitState,
        selectedGitBaseBranch: String,
        thread: RemodexThreadSummary?,
    ): ComposerUiState {
        if (thread == null) {
            return ComposerUiState()
        }
        val showsRunningUi = thread.isRunning && thread.latestTurnTerminalState == null
        val hasConfirmedReviewSelection = reviewSelection?.target != null
        val hasPendingReviewSelection = reviewSelection != null && reviewSelection.target == null
        val canSend = !hasPendingReviewSelection && (
            draftText.isNotBlank() ||
                attachments.isNotEmpty() ||
                hasConfirmedReviewSelection ||
                isSubagentsSelectionArmed
            )
        return ComposerUiState(
            draftText = draftText,
            sendLabel = when {
                hasConfirmedReviewSelection -> "Start review"
                showsRunningUi -> "Queue follow-up"
                else -> "Send"
            },
            canSend = canSend,
            canStop = showsRunningUi,
            voice = voiceUiState.copy(isConnected = isConnected),
            mentionedFiles = mentionedFiles,
            mentionedSkills = mentionedSkills,
            reviewSelection = reviewSelection,
            isSubagentsSelectionArmed = isSubagentsSelectionArmed,
            queuedDrafts = thread.queuedDraftItems,
            attachments = attachments,
            attachmentLimitMessage = attachmentLimitMessage,
            composerMessage = composerMessage,
            voiceRecoveryReason = voiceRecoveryReason,
            voiceRecovery = resolveComposerVoiceRecoveryUiState(
                reason = voiceRecoveryReason,
                isConnected = isConnected,
                gptAccountSnapshot = gptAccountSnapshot,
            ),
            maxAttachments = MaxComposerImages,
            runtimeConfig = thread.runtimeConfig,
            autocomplete = autocomplete,
            gitState = gitState,
            selectedGitBaseBranch = selectedGitBaseBranch,
        )
    }

    companion object {
        private const val logTag = "RemodexAutoReconnect"
        private const val defaultAutoReconnectAttemptLimit = 3
        const val MaxComposerImages = 4
        const val MaxAutocompleteItems = 6
        const val MinAutocompleteQueryLength = 2
        const val AutocompleteDebounceMs = 180L
    }
}

internal fun appendVoiceTranscriptToDraft(
    currentDraft: String,
    transcript: String,
): String {
    val normalizedTranscript = transcript.trim()
    if (normalizedTranscript.isEmpty()) {
        return currentDraft
    }
    if (currentDraft.isEmpty()) {
        return normalizedTranscript
    }
    return if (currentDraft.last().isWhitespace()) {
        currentDraft + normalizedTranscript
    } else {
        "$currentDraft $normalizedTranscript"
    }
}

internal fun classifyComposerVoiceRecoveryReason(
    message: String?,
    isConnected: Boolean,
    gptAccountSnapshot: RemodexGptAccountSnapshot,
): ComposerVoiceRecoveryReason {
    resolveAuthSensitiveVoiceRecoveryReason(
        isConnected = isConnected,
        gptAccountSnapshot = gptAccountSnapshot,
    )?.let { return it }

    val normalizedMessage = message?.trim().orEmpty()
    return if (normalizedMessage.isEmpty()) {
        ComposerVoiceRecoveryReason.Generic("Voice transcription failed.")
    } else {
        ComposerVoiceRecoveryReason.Generic(normalizedMessage)
    }
}

internal fun resolveAuthSensitiveVoiceRecoveryReason(
    isConnected: Boolean,
    gptAccountSnapshot: RemodexGptAccountSnapshot,
): ComposerVoiceRecoveryReason? {
    return when (remodexGptVoiceStatus(snapshot = gptAccountSnapshot, isConnected = isConnected)) {
        RemodexGptVoiceStatus.READY -> null
        RemodexGptVoiceStatus.DISCONNECTED -> ComposerVoiceRecoveryReason.ReconnectRequired
        RemodexGptVoiceStatus.LOGIN_REQUIRED,
        RemodexGptVoiceStatus.LOGIN_PENDING,
        -> ComposerVoiceRecoveryReason.LoginRequired
        RemodexGptVoiceStatus.REAUTH_REQUIRED -> ComposerVoiceRecoveryReason.ReauthenticationRequired
        RemodexGptVoiceStatus.VOICE_SYNC_IN_PROGRESS -> ComposerVoiceRecoveryReason.VoiceSyncInProgress
    }
}

internal fun resolveComposerVoiceRecoveryUiState(
    reason: ComposerVoiceRecoveryReason?,
    isConnected: Boolean,
    gptAccountSnapshot: RemodexGptAccountSnapshot,
): ComposerVoiceRecoveryUiState? {
    val resolvedReason = when (reason) {
        null -> null
        ComposerVoiceRecoveryReason.LoginRequired,
        ComposerVoiceRecoveryReason.ReauthenticationRequired,
        ComposerVoiceRecoveryReason.VoiceSyncInProgress,
        -> resolveAuthSensitiveVoiceRecoveryReason(
            isConnected = isConnected,
            gptAccountSnapshot = gptAccountSnapshot,
        )
        else -> reason
    } ?: return null

    return when (resolvedReason) {
        ComposerVoiceRecoveryReason.ReconnectRequired -> ComposerVoiceRecoveryUiState(
            title = "Reconnect to your Mac",
            summary = "Voice mode needs your paired Mac bridge.",
            detail = "Reconnect to the bridge, then try voice transcription again.",
            actionLabel = "Retry connection",
            action = ComposerVoiceRecoveryAction.RETRY_CONNECTION,
            tone = ComposerVoiceRecoveryTone.WARNING,
        )

        ComposerVoiceRecoveryReason.LoginRequired -> ComposerVoiceRecoveryUiState(
            title = "Use ChatGPT on your Mac",
            summary = "Voice mode reads the ChatGPT session from your paired Mac.",
            detail = "Open Settings to check whether ChatGPT needs sign-in or is still finishing browser login.",
            actionLabel = "Open Settings",
            action = ComposerVoiceRecoveryAction.OPEN_SETTINGS,
            tone = ComposerVoiceRecoveryTone.WARNING,
        )

        ComposerVoiceRecoveryReason.ReauthenticationRequired -> ComposerVoiceRecoveryUiState(
            title = "Refresh ChatGPT sign-in",
            summary = "This bridge needs a fresh ChatGPT sign-in on your Mac before voice can continue.",
            detail = "Open Settings for the current voice status, then refresh the ChatGPT session on your paired Mac.",
            actionLabel = "Open Settings",
            action = ComposerVoiceRecoveryAction.OPEN_SETTINGS,
            tone = ComposerVoiceRecoveryTone.WARNING,
        )

        ComposerVoiceRecoveryReason.VoiceSyncInProgress -> ComposerVoiceRecoveryUiState(
            title = "Voice sync in progress",
            summary = "Your ChatGPT account is signed in on the Mac, but voice access is still syncing.",
            detail = "Wait a moment, then try voice mode again or open Settings to verify the latest status.",
            actionLabel = "Open Settings",
            action = ComposerVoiceRecoveryAction.OPEN_SETTINGS,
            tone = ComposerVoiceRecoveryTone.PROGRESS,
        )

        is ComposerVoiceRecoveryReason.MicrophonePermissionRequired -> ComposerVoiceRecoveryUiState(
            title = "Microphone access needed",
            summary = if (resolvedReason.requiresSettings) {
                "Enable microphone access in Android Settings before using voice transcription."
            } else {
                "Tap the mic again to allow microphone access for voice transcription."
            },
            detail = if (resolvedReason.requiresSettings) {
                "Android blocked microphone access for this app."
            } else {
                "Grant access when Android asks for microphone permission."
            },
            actionLabel = if (resolvedReason.requiresSettings) {
                null
            } else {
                "Try again"
            },
            action = if (resolvedReason.requiresSettings) {
                null
            } else {
                ComposerVoiceRecoveryAction.RETRY_VOICE
            },
            tone = ComposerVoiceRecoveryTone.WARNING,
        )

        is ComposerVoiceRecoveryReason.Generic -> ComposerVoiceRecoveryUiState(
            title = "Voice mode unavailable",
            summary = resolvedReason.message,
            detail = "Try again in a moment. If it keeps happening, reconnect to your Mac or check ChatGPT voice status in Settings.",
            actionLabel = null,
            action = null,
            tone = ComposerVoiceRecoveryTone.ERROR,
        )
    }
}

private fun com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot.isConnectedForRecovery(): Boolean {
    return connectionStatus.phase == RemodexConnectionPhase.CONNECTED ||
        secureConnection.secureState == SecureConnectionState.ENCRYPTED
}

class AppViewModelFactory(
    private val repository: RemodexAppRepository,
    private val voiceRecorder: AndroidVoiceRecorder,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return AppViewModel(
            repository = repository,
            voiceRecorder = voiceRecorder,
        ) as T
    }
}

private fun RemodexComposerAutocompleteState.enriched(
    selectedThread: RemodexThreadSummary?,
    gitState: RemodexGitState,
    supportsThreadFork: Boolean,
    selectedGitBaseBranch: String,
    draftText: String,
    mentionedFiles: List<RemodexComposerMentionedFile>,
    mentionedSkills: List<RemodexComposerMentionedSkill>,
    attachments: List<RemodexComposerAttachment>,
    reviewSelection: RemodexComposerReviewSelection?,
    isSubagentsSelectionArmed: Boolean,
): RemodexComposerAutocompleteState {
    if (selectedThread == null) {
        return RemodexComposerAutocompleteState()
    }
    val hasReviewConflict = RemodexComposerCommandLogic.hasContentConflictingWithReview(
        trimmedInput = draftText.trim(),
        mentionedFileCount = mentionedFiles.size,
        mentionedSkillCount = mentionedSkills.size,
        attachmentCount = attachments.size,
        hasSubagentsSelection = isSubagentsSelectionArmed,
    )
    val availableForkDestinations = availableForkDestinations(
        gitState = gitState,
        supportsThreadFork = supportsThreadFork,
    )
    return copy(
        availableCommands = availableCommands.filter { command ->
            when (command) {
                RemodexSlashCommand.FORK -> availableForkDestinations.isNotEmpty()
                else -> true
            }
        },
        slashCommands = slashCommands.filter { command ->
            when (command) {
                RemodexSlashCommand.FORK -> availableForkDestinations.isNotEmpty()
                else -> true
            }
        },
        reviewTargets = if (gitState.hasContext) {
            listOf(
                RemodexComposerReviewTarget.UNCOMMITTED_CHANGES,
                RemodexComposerReviewTarget.BASE_BRANCH,
            )
        } else {
            listOf(RemodexComposerReviewTarget.UNCOMMITTED_CHANGES)
        },
        forkDestinations = availableForkDestinations,
        hasComposerContentConflictingWithReview = hasReviewConflict,
        isThreadRunning = selectedThread.isRunning,
        selectedGitBaseBranch = selectedGitBaseBranch,
        gitDefaultBranch = gitState.branches.defaultBranch.orEmpty(),
    )
}

private fun availableForkDestinations(
    gitState: RemodexGitState,
    supportsThreadFork: Boolean,
): List<RemodexComposerForkDestination> {
    if (!supportsThreadFork) {
        return emptyList()
    }
    return buildList {
        if (gitState.hasContext) {
            add(RemodexComposerForkDestination.NEW_WORKTREE)
        }
        add(RemodexComposerForkDestination.LOCAL)
    }
}
