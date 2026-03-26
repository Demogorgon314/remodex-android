package com.emanueledipietro.remodex.feature.appshell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.emanueledipietro.remodex.data.app.RemodexAppRepository
import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.RemodexAssistantRevertPresentation
import com.emanueledipietro.remodex.model.RemodexAssistantRevertRiskLevel
import com.emanueledipietro.remodex.model.RemodexAssistantRevertSheetState
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
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
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexNotificationRegistrationState
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexSlashCommand
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexTurnTerminalState
import com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation
import com.emanueledipietro.remodex.model.RemodexUsageStatus
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
import kotlin.math.max

data class ComposerUiState(
    val draftText: String = "",
    val sendLabel: String = "Send",
    val canSend: Boolean = false,
    val canStop: Boolean = false,
    val mentionedFiles: List<RemodexComposerMentionedFile> = emptyList(),
    val mentionedSkills: List<RemodexComposerMentionedSkill> = emptyList(),
    val reviewSelection: RemodexComposerReviewSelection? = null,
    val isSubagentsSelectionArmed: Boolean = false,
    val queuedDrafts: List<RemodexQueuedDraft> = emptyList(),
    val attachments: List<RemodexComposerAttachment> = emptyList(),
    val attachmentLimitMessage: String? = null,
    val composerMessage: String? = null,
    val maxAttachments: Int = 4,
    val runtimeConfig: RemodexRuntimeConfig = RemodexRuntimeConfig(),
    val autocomplete: RemodexComposerAutocompleteState = RemodexComposerAutocompleteState(),
    val gitState: RemodexGitState = RemodexGitState(),
    val selectedGitBaseBranch: String = "",
)

data class AppUiState(
    val onboardingCompleted: Boolean = false,
    val connectionStatus: RemodexConnectionStatus = RemodexConnectionStatus(),
    val connectionHeadline: String = "Waiting for your Mac",
    val connectionMessage: String = "Run remodex up on your Mac, then pair this Android device with the QR code.",
    val recoveryState: SecureConnectionSnapshot = SecureConnectionSnapshot(),
    val threads: List<RemodexThreadSummary> = emptyList(),
    val selectedThread: RemodexThreadSummary? = null,
    val notificationRegistration: RemodexNotificationRegistrationState = RemodexNotificationRegistrationState(),
    val runtimeDefaults: RemodexRuntimeDefaults = RemodexRuntimeDefaults(),
    val availableModels: List<RemodexModelOption> = emptyList(),
    val appearanceMode: RemodexAppearanceMode = RemodexAppearanceMode.SYSTEM,
    val appFontStyle: RemodexAppFontStyle = RemodexAppFontStyle.SYSTEM,
    val trustedMac: RemodexTrustedMacPresentation? = null,
    val isRefreshingThreads: Boolean = false,
    val isRefreshingUsage: Boolean = false,
    val gptAccountSnapshot: RemodexGptAccountSnapshot = RemodexGptAccountSnapshot(),
    val gptAccountErrorMessage: String? = null,
    val bridgeVersionStatus: RemodexBridgeVersionStatus = RemodexBridgeVersionStatus(),
    val usageStatus: RemodexUsageStatus = RemodexUsageStatus(),
    val composer: ComposerUiState = ComposerUiState(),
    val conversationBanner: String? = null,
    val threadCompletionBanner: ThreadCompletionBannerUiState? = null,
    val completionHapticSignal: Long = 0L,
    val assistantRevertStatesByMessageId: Map<String, RemodexAssistantRevertPresentation> = emptyMap(),
    val assistantRevertSheet: RemodexAssistantRevertSheetState? = null,
    val commandExecutionDetailsByItemId: Map<String, RemodexCommandExecutionDetails> = emptyMap(),
) {
    val isConnected: Boolean
        get() = connectionStatus.phase == RemodexConnectionPhase.CONNECTED
}

data class ThreadCompletionBannerUiState(
    val threadId: String,
    val title: String,
)

private data class ComposerRenderStateA(
    val draftsByThread: Map<String, String> = emptyMap(),
    val attachmentsByThread: Map<String, List<RemodexComposerAttachment>> = emptyMap(),
    val mentionedFilesByThread: Map<String, List<RemodexComposerMentionedFile>> = emptyMap(),
    val mentionedSkillsByThread: Map<String, List<RemodexComposerMentionedSkill>> = emptyMap(),
    val reviewSelectionsByThread: Map<String, RemodexComposerReviewSelection?> = emptyMap(),
)

private data class ComposerRenderStateB(
    val subagentsSelectionsByThread: Map<String, Boolean> = emptyMap(),
    val attachmentMessagesByThread: Map<String, String?> = emptyMap(),
    val composerMessagesByThread: Map<String, String?> = emptyMap(),
    val gitStatesByThread: Map<String, RemodexGitState> = emptyMap(),
    val baseBranchesByThread: Map<String, String> = emptyMap(),
    val autocomplete: RemodexComposerAutocompleteState = RemodexComposerAutocompleteState(),
)

private data class SessionRenderState(
    val snapshot: com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot,
    val commandExecutionDetails: Map<String, RemodexCommandExecutionDetails> = emptyMap(),
)

private data class SettingsRenderState(
    val gptAccountSnapshot: RemodexGptAccountSnapshot = RemodexGptAccountSnapshot(),
    val gptAccountErrorMessage: String? = null,
    val bridgeVersionStatus: RemodexBridgeVersionStatus = RemodexBridgeVersionStatus(),
    val usageStatus: RemodexUsageStatus = RemodexUsageStatus(),
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

class AppViewModel(
    private val repository: RemodexAppRepository,
) : ViewModel() {
    private val composerDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val composerAttachments = MutableStateFlow<Map<String, List<RemodexComposerAttachment>>>(emptyMap())
    private val composerMentionedFiles = MutableStateFlow<Map<String, List<RemodexComposerMentionedFile>>>(emptyMap())
    private val composerMentionedSkills = MutableStateFlow<Map<String, List<RemodexComposerMentionedSkill>>>(emptyMap())
    private val composerReviewSelections = MutableStateFlow<Map<String, RemodexComposerReviewSelection?>>(emptyMap())
    private val composerSubagentsSelections = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val attachmentMessages = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val composerMessages = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val gitStates = MutableStateFlow<Map<String, RemodexGitState>>(emptyMap())
    private val selectedGitBaseBranchByThread = MutableStateFlow<Map<String, String>>(emptyMap())
    private val autocompleteState = MutableStateFlow(RemodexComposerAutocompleteState())
    private val revertedAssistantMessageIds = MutableStateFlow<Set<String>>(emptySet())
    private val assistantRevertSheetState = MutableStateFlow<RemodexAssistantRevertSheetState?>(null)
    private val threadCompletionBannerState = MutableStateFlow<ThreadCompletionBannerUiState?>(null)
    private val completionHapticSignalState = MutableStateFlow(0L)
    private val isRefreshingThreadsState = MutableStateFlow(false)
    private val isRefreshingUsageState = MutableStateFlow(false)
    private val autoReconnectState = MutableStateFlow(AutoReconnectUiState())
    private var fileAutocompleteJob: Job? = null
    private var skillAutocompleteJob: Job? = null
    private var autoReconnectJob: Job? = null
    private var lastObservedThreadId: String? = null
    private var lastHydratedSelectedThreadId: String? = null
    private var lastHydrationConnected = false
    private var previousThreadsById: Map<String, RemodexThreadSummary> = emptyMap()
    private var isAppForeground = false
    private var isManualScannerActive = false
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
        ) { subagentsSelectionsByThread, attachmentMessagesByThread, composerMessagesByThread ->
            Triple(
                subagentsSelectionsByThread,
                attachmentMessagesByThread,
                composerMessagesByThread,
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
                subagentsSelectionsByThread = left.first,
                attachmentMessagesByThread = left.second,
                composerMessagesByThread = left.third,
                gitStatesByThread = right.first,
                baseBranchesByThread = right.second,
                autocomplete = right.third,
            )
        }

    private val sessionRenderState =
        combine(
            repository.session,
            repository.commandExecutionDetails,
        ) { snapshot, commandExecutionDetails ->
            SessionRenderState(
                snapshot = snapshot,
                commandExecutionDetails = commandExecutionDetails,
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

    private val baseUiState =
        combine(
            sessionRenderState,
            composerRenderStateA,
            composerRenderStateB,
            revertedAssistantMessageIds,
            assistantRevertSheetState,
        ) { sessionRenderState, renderStateA, renderStateB, revertedMessageIds, revertSheetState ->
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
            val gitState = selectedThread?.id?.let(renderStateB.gitStatesByThread::get) ?: RemodexGitState()
            val selectedGitBaseBranch = selectedThread?.id?.let(renderStateB.baseBranchesByThread::get)
                ?: gitState.branches.defaultBranch.orEmpty()
            AppUiState(
                onboardingCompleted = snapshot.onboardingCompleted,
                connectionStatus = snapshot.connectionStatus,
                connectionHeadline = headline,
                connectionMessage = message,
                recoveryState = snapshot.secureConnection,
                threads = snapshot.threads,
                selectedThread = selectedThread,
                notificationRegistration = snapshot.notificationRegistration,
                runtimeDefaults = snapshot.runtimeDefaults,
                availableModels = snapshot.availableModels,
                appearanceMode = snapshot.appearanceMode,
                appFontStyle = snapshot.appFontStyle,
                trustedMac = snapshot.trustedMac,
                composer = composerState(
                    draftText = draftText,
                    attachments = attachments,
                    mentionedFiles = mentionedFiles,
                    mentionedSkills = mentionedSkills,
                    reviewSelection = reviewSelection,
                    isSubagentsSelectionArmed = isSubagentsSelectionArmed,
                    attachmentLimitMessage = attachmentMessage,
                    composerMessage = composerMessage,
                    autocomplete = renderStateB.autocomplete.enriched(
                        selectedThread = selectedThread,
                        gitState = gitState,
                        selectedGitBaseBranch = selectedGitBaseBranch,
                        draftText = draftText,
                        mentionedFiles = mentionedFiles,
                        mentionedSkills = mentionedSkills,
                        attachments = attachments,
                        reviewSelection = reviewSelection,
                        isSubagentsSelectionArmed = isSubagentsSelectionArmed,
                    ),
                    gitState = gitState,
                    selectedGitBaseBranch = selectedGitBaseBranch,
                    thread = selectedThread,
                ),
                conversationBanner = conversationBanner(
                    selectedThread = selectedThread,
                    secureConnection = snapshot.secureConnection,
                    gitState = gitState,
                ),
                assistantRevertStatesByMessageId = assistantRevertPresentations(
                    threads = snapshot.threads,
                    selectedThread = selectedThread,
                    secureConnection = snapshot.secureConnection,
                    revertedMessageIds = revertedMessageIds,
                ),
                assistantRevertSheet = revertSheetState,
                commandExecutionDetailsByItemId = sessionRenderState.commandExecutionDetails,
            )
        }

    private val threadChromeState =
        combine(
            threadCompletionBannerState,
            completionHapticSignalState,
        ) { threadCompletionBanner: ThreadCompletionBannerUiState?, completionHapticSignal: Long ->
            threadCompletionBanner to completionHapticSignal
        }

    private val decoratedUiState =
        combine(
            baseUiState,
            threadChromeState,
            isRefreshingThreadsState,
            isRefreshingUsageState,
            settingsRenderState,
        ) {
            baseState: AppUiState,
            threadChrome: Pair<ThreadCompletionBannerUiState?, Long>,
            isRefreshingThreads: Boolean,
            isRefreshingUsage: Boolean,
            settingsState: SettingsRenderState,
            ->
            baseState.copy(
                threadCompletionBanner = threadChrome.first,
                completionHapticSignal = threadChrome.second,
                isRefreshingThreads = isRefreshingThreads,
                isRefreshingUsage = isRefreshingUsage,
                gptAccountSnapshot = settingsState.gptAccountSnapshot,
                gptAccountErrorMessage = settingsState.gptAccountErrorMessage,
                bridgeVersionStatus = settingsState.bridgeVersionStatus,
                usageStatus = settingsState.usageStatus,
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
                        viewModelScope.launch {
                            repository.hydrateThread(selectedThreadId)
                        }
                    }
                }
                lastHydrationConnected = isConnected
                detectThreadCompletionBanner(snapshot)
                handleAutoReconnectSnapshot(snapshot)
            }
        }
    }

    fun dismissThreadCompletionBanner() {
        threadCompletionBannerState.value = null
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.completeOnboarding()
        }
    }

    fun onAppForegroundChanged(isForeground: Boolean) {
        isAppForeground = isForeground
        if (!isForeground) {
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
        if (!uiState.value.isConnected || isRefreshingThreadsState.value) {
            return
        }
        viewModelScope.launch {
            isRefreshingThreadsState.value = true
            try {
                repository.refreshThreads()
            } finally {
                isRefreshingThreadsState.value = false
            }
        }
    }

    fun selectThread(threadId: String) {
        viewModelScope.launch {
            repository.selectThread(threadId)
            refreshGitState(threadId)
            clearComposerAutocomplete()
            dismissAssistantRevertSheet()
        }
    }

    fun hydrateThreadMetadata(threadId: String) {
        if (threadId.isBlank()) {
            return
        }
        viewModelScope.launch {
            repository.hydrateThread(threadId)
        }
    }

    fun createThread(preferredProjectPath: String? = null) {
        viewModelScope.launch {
            repository.createThread(preferredProjectPath)
            repository.session.value.selectedThread?.id?.let(::refreshGitState)
            clearComposerAutocomplete()
            dismissAssistantRevertSheet()
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
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            val composer = uiState.value.composer
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
                clearComposer(threadId)
                try {
                    repository.startCodeReview(
                        threadId = threadId,
                        target = reviewSelection.target,
                        baseBranch = composer.selectedGitBaseBranch.ifBlank { null },
                    )
                    refreshGitState(threadId)
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

            val payload = buildPromptPayload(
                draftText = composer.draftText,
                mentionedFiles = composer.mentionedFiles,
                isSubagentsSelectionArmed = composer.isSubagentsSelectionArmed,
            )
            clearComposer(threadId)
            try {
                repository.sendPrompt(threadId, payload, composer.attachments)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
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

    fun sendQueuedDraft(draftId: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            repository.sendQueuedDraft(threadId, draftId)
        }
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

            RemodexSlashCommand.CODE_REVIEW -> {
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
                autocompleteState.value = autocompleteState.value.copy(
                    panel = RemodexComposerAutocompletePanel.FORK_DESTINATIONS,
                    forkDestinations = availableForkDestinations(uiState.value.composer.gitState),
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
            updateGitState(threadId) { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }
            runCatching { repository.loadGitDiff(threadId) }
                .onSuccess { diff ->
                    updateGitState(threadId) { currentState ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = if (diff.patch.isBlank()) {
                                "There are no repository changes to show."
                            } else {
                                null
                            },
                        )
                    }
                    if (diff.patch.isNotBlank()) {
                        onLoaded(diff)
                    }
                }
                .onFailure { error ->
                    updateGitState(threadId) { currentState ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load repository changes.",
                        )
                    }
                }
        }
    }

    fun checkoutGitBranch(branch: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            val nextState = repository.checkoutGitBranch(threadId, branch)
            updateGitState(threadId) { nextState }
        }
    }

    fun createGitBranch(branch: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            val nextState = repository.createGitBranch(threadId, branch)
            updateGitState(threadId) { nextState }
        }
    }

    fun createGitWorktree(name: String) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            val nextState = repository.createGitWorktree(
                threadId = threadId,
                name = name,
                baseBranch = uiState.value.composer.selectedGitBaseBranch.ifBlank { null },
            )
            updateGitState(threadId) { nextState }
        }
    }

    fun commitGitChanges(message: String? = null) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            val nextState = repository.commitGitChanges(threadId, message)
            updateGitState(threadId) { nextState }
        }
    }

    fun pullGitChanges() {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            val nextState = repository.pullGitChanges(threadId)
            updateGitState(threadId) { nextState }
        }
    }

    fun pushGitChanges() {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            val nextState = repository.pushGitChanges(threadId)
            updateGitState(threadId) { nextState }
        }
    }

    fun discardRuntimeChangesAndSync() {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            updateGitState(threadId) { currentState -> currentState.copy(isLoading = true, errorMessage = null) }
            val nextState = repository.discardRuntimeChangesAndSync(threadId)
            updateGitState(threadId) { nextState }
        }
    }

    fun forkThread(destination: RemodexComposerForkDestination) {
        val threadId = uiState.value.selectedThread?.id ?: return
        viewModelScope.launch {
            val nextThreadId = repository.forkThread(
                threadId = threadId,
                destination = destination,
                baseBranch = uiState.value.composer.selectedGitBaseBranch.ifBlank { null },
            )
            clearComposerAutocomplete()
            dismissAssistantRevertSheet()
            nextThreadId?.let(::refreshGitState)
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
        val selectedThreadId = uiState.value.selectedThread?.id
        if (isRefreshingUsageState.value) {
            return
        }
        viewModelScope.launch {
            isRefreshingUsageState.value = true
            try {
                repository.refreshUsageStatus(selectedThreadId)
            } finally {
                isRefreshingUsageState.value = false
            }
        }
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

    fun retryConnection() {
        viewModelScope.launch {
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
        if (!shouldAttemptAutoReconnect(snapshot) || autoReconnectJob?.isActive == true) {
            return
        }
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
        if (!snapshot.onboardingCompleted || !isAppForeground || isManualScannerActive) {
            return false
        }
        if (suppressAutoReconnectUntilManualConnect || snapshot.trustedMac == null) {
            return false
        }
        return snapshot.secureConnection.secureState in setOf(
            SecureConnectionState.TRUSTED_MAC,
            SecureConnectionState.LIVE_SESSION_UNRESOLVED,
        )
    }

    private suspend fun runAutoReconnectLoop() {
        var attempt = 0
        val maxAttempts = autoReconnectAttemptLimitOverride ?: 50

        try {
            while (shouldAttemptAutoReconnect(repository.session.value) && attempt < maxAttempts) {
                val snapshot = repository.session.value
                if (snapshot.connectionStatus.phase == RemodexConnectionPhase.CONNECTED ||
                    snapshot.secureConnection.secureState == SecureConnectionState.ENCRYPTED
                ) {
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
                repository.retryConnection()
                if (waitForReconnectAttemptOutcome(attempt)) {
                    return
                }

                val backoffSteps = autoReconnectBackoffMillisOverride ?: listOf(1_000L, 3_000L)
                val backoff = backoffSteps.getOrElse((attempt - 1).coerceAtMost(backoffSteps.lastIndex)) {
                    backoffSteps.last()
                }
                if (!sleepForReconnectBackoff(backoff)) {
                    return
                }
            }
        } finally {
            if (repository.session.value.connectionStatus.phase != RemodexConnectionPhase.CONNECTED) {
                clearAutoReconnectState()
            }
        }
    }

    private suspend fun waitForReconnectAttemptOutcome(
        attempt: Int,
    ): Boolean {
        var settleRemainingMs = 500L
        while (shouldAttemptAutoReconnect(repository.session.value)) {
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
            if (!shouldAttemptAutoReconnect(repository.session.value) &&
                !repository.session.value.isConnectedForRecovery()
            ) {
                return false
            }
            val chunk = minOf(reconnectSleepChunkMillis(), remaining)
            delay(chunk)
            remaining -= chunk
        }
        return shouldAttemptAutoReconnect(repository.session.value) || repository.session.value.isConnectedForRecovery()
    }

    private fun reconnectSleepChunkMillis(): Long {
        return reconnectSleepChunkMillisOverride ?: 100L
    }

    private fun clearAutoReconnectState() {
        autoReconnectState.value = AutoReconnectUiState()
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
        previousThreadsById = snapshot.threads.associateBy(RemodexThreadSummary::id)
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
                            errorMessage = error.message ?: "Could not load local git state.",
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
        clearComposerAutocomplete()
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
        ) && availableForkDestinations(composer.gitState).isNotEmpty()
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
        gitState: RemodexGitState,
    ): String? {
        if (selectedThread == null) {
            return null
        }
        return when {
            secureConnection.secureState != SecureConnectionState.ENCRYPTED && selectedThread.isRunning -> {
                "This turn will resume when the trusted connection comes back."
            }

            gitState.errorMessage != null -> gitState.errorMessage
            else -> null
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

    private fun composerState(
        draftText: String,
        attachments: List<RemodexComposerAttachment>,
        mentionedFiles: List<RemodexComposerMentionedFile>,
        mentionedSkills: List<RemodexComposerMentionedSkill>,
        reviewSelection: RemodexComposerReviewSelection?,
        isSubagentsSelectionArmed: Boolean,
        attachmentLimitMessage: String?,
        composerMessage: String?,
        autocomplete: RemodexComposerAutocompleteState,
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
            mentionedFiles = mentionedFiles,
            mentionedSkills = mentionedSkills,
            reviewSelection = reviewSelection,
            isSubagentsSelectionArmed = isSubagentsSelectionArmed,
            queuedDrafts = thread.queuedDraftItems,
            attachments = attachments,
            attachmentLimitMessage = attachmentLimitMessage,
            composerMessage = composerMessage,
            maxAttachments = MaxComposerImages,
            runtimeConfig = thread.runtimeConfig,
            autocomplete = autocomplete,
            gitState = gitState,
            selectedGitBaseBranch = selectedGitBaseBranch,
        )
    }

    private companion object {
        const val MaxComposerImages = 4
        const val MaxAutocompleteItems = 6
        const val MinAutocompleteQueryLength = 2
        const val AutocompleteDebounceMs = 180L
    }
}

private fun com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot.isConnectedForRecovery(): Boolean {
    return connectionStatus.phase == RemodexConnectionPhase.CONNECTED ||
        secureConnection.secureState == SecureConnectionState.ENCRYPTED
}

class AppViewModelFactory(
    private val repository: RemodexAppRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return AppViewModel(repository) as T
    }
}

private fun RemodexComposerAutocompleteState.enriched(
    selectedThread: RemodexThreadSummary?,
    gitState: RemodexGitState,
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
    return copy(
        availableCommands = availableCommands.filter { command ->
            when (command) {
                RemodexSlashCommand.FORK -> availableForkDestinations(gitState).isNotEmpty()
                else -> true
            }
        },
        slashCommands = slashCommands.filter { command ->
            when (command) {
                RemodexSlashCommand.FORK -> availableForkDestinations(gitState).isNotEmpty()
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
        forkDestinations = availableForkDestinations(gitState),
        hasComposerContentConflictingWithReview = hasReviewConflict,
        isThreadRunning = selectedThread.isRunning,
        selectedGitBaseBranch = selectedGitBaseBranch,
        gitDefaultBranch = gitState.branches.defaultBranch.orEmpty(),
    )
}

private fun availableForkDestinations(gitState: RemodexGitState): List<RemodexComposerForkDestination> {
    return buildList {
        if (gitState.hasContext) {
            add(RemodexComposerForkDestination.NEW_WORKTREE)
        }
        add(RemodexComposerForkDestination.LOCAL)
    }
}
