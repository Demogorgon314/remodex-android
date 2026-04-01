package com.emanueledipietro.remodex.data.app

import android.util.Log
import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.data.connection.BridgeProfilesSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.jsonObjectOrNull
import com.emanueledipietro.remodex.data.connection.statusLabel
import com.emanueledipietro.remodex.data.preferences.AppPreferences
import com.emanueledipietro.remodex.data.preferences.AppPreferencesRepository
import com.emanueledipietro.remodex.data.threads.CachedThreadRecord
import com.emanueledipietro.remodex.data.threads.ThreadActiveContextService
import com.emanueledipietro.remodex.data.threads.ThreadCommandService
import com.emanueledipietro.remodex.data.threads.ThreadCacheStore
import com.emanueledipietro.remodex.data.threads.ThreadHydrationService
import com.emanueledipietro.remodex.data.threads.ThreadLocalTimelineService
import com.emanueledipietro.remodex.data.threads.ThreadResumeService
import com.emanueledipietro.remodex.data.threads.ThreadSyncService
import com.emanueledipietro.remodex.data.threads.ThreadSyncSnapshot
import com.emanueledipietro.remodex.data.threads.TimelineMutation
import com.emanueledipietro.remodex.data.threads.shouldRetryAfterThreadMaterializationValue
import com.emanueledipietro.remodex.data.threads.shouldTreatAsThreadNotFoundValue
import com.emanueledipietro.remodex.data.voice.RemodexVoiceTranscriptionService
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexAssistantResponseMetrics
import com.emanueledipietro.remodex.model.RemodexBridgeVersionStatus
import com.emanueledipietro.remodex.model.RemodexBridgeProfilePresentation
import com.emanueledipietro.remodex.model.RemodexCodeReviewRequest
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexGitCommit
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitRemoteUrl
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitWorktreeChangeTransferMode
import com.emanueledipietro.remodex.model.RemodexGitWorktreeResult
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexPermissionGrantScope
import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexGptAccountStatus
import com.emanueledipietro.remodex.model.RemodexNotificationRegistrationState
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexRuntimeOverrides
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation
import com.emanueledipietro.remodex.model.RemodexUsageStatus
import com.emanueledipietro.remodex.model.androidUserMessageText
import com.emanueledipietro.remodex.model.remodexInitialGptAccountSnapshot
import com.emanueledipietro.remodex.model.toConversationAttachment
import com.emanueledipietro.remodex.feature.turn.TurnTimelineReducer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private object NoopVoiceTranscriptionService : RemodexVoiceTranscriptionService {
    override suspend fun transcribeVoiceAudioFile(
        file: File,
        durationSeconds: Double,
    ): String {
        throw UnsupportedOperationException("Voice transcription is not configured.")
    }
}

class DefaultRemodexAppRepository(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val secureConnectionCoordinator: SecureConnectionCoordinator,
    private val threadCacheStore: ThreadCacheStore,
    private val threadSyncService: ThreadSyncService,
    private val threadCommandService: ThreadCommandService,
    private val threadHydrationService: ThreadHydrationService? = null,
    private val voiceTranscriptionService: RemodexVoiceTranscriptionService = NoopVoiceTranscriptionService,
    private val managedPushRegistrationState: Flow<RemodexNotificationRegistrationState> = flowOf(
        RemodexNotificationRegistrationState(),
    ),
    scope: CoroutineScope,
) : RemodexAppRepository {
    private data class LocalOptimisticUserMessage(
        val threadId: String,
        val messageId: String,
    )

    private data class RecoveredThreadContext(
        val threadId: String,
        val isRunning: Boolean,
        val isWaitingOnApproval: Boolean,
        val projectPath: String?,
        val modelIdentifier: String?,
    ) {
        val requiresLiveResume: Boolean
            get() = isRunning || isWaitingOnApproval
    }

    private val repositoryScope = scope
    private val timelineProjectionCacheLock = Any()
    private val timelineProjectionCacheByThread = mutableMapOf<String, ThreadTimelineProjectionCache>()
    private var threadCacheWriteJob: Job? = null
    private var threadListPublishJob: Job? = null
    private var recoveredEncryptedAttempt: Int? = null
    private var activeBridgeProfileId: String? = secureConnectionCoordinator.bridgeProfiles.value.activeProfileId
    private var suppressBridgeScopedThreadsUntilNextSync = false
    private val reconnectCatchupJobByThread = mutableMapOf<String, Job>()
    private val initialBaseThreads = threadSyncService.threads.value
        .map { snapshot -> snapshot.toCachedThreadRecord(projectThreadTimelineItems(snapshot)) }
        .sortedByDescending(CachedThreadRecord::lastUpdatedEpochMs)
        .map(CachedThreadRecord::toBaseThreadSummary)
    private val threadListPublishGeneration = AtomicLong(0L)
    private val preferredSelectedThreadId = AtomicReference<String?>(initialBaseThreads.firstOrNull()?.id)
    private val baseThreadsState = MutableStateFlow(initialBaseThreads)
    private val preferencesState = MutableStateFlow(AppPreferences())

    private val sessionState = MutableStateFlow(
        RemodexSessionSnapshot(
            onboardingCompleted = false,
            connectionStatus = secureConnectionCoordinator.state.value.toConnectionStatus(),
            secureConnection = secureConnectionCoordinator.state.value,
            trustedMac = secureConnectionCoordinator.state.value.toTrustedMacPresentation(),
            bridgeProfiles = secureConnectionCoordinator.bridgeProfiles.value.toBridgeProfilePresentations(),
            bridgeUpdatePrompt = threadSyncService.bridgeUpdatePrompt.value,
            supportsThreadFork = threadSyncService.supportsThreadFork.value,
            availableModels = threadSyncService.availableModels.value,
            pendingApprovalRequest = threadSyncService.pendingApprovalRequest.value,
            threads = initialBaseThreads,
            selectedThreadId = initialBaseThreads.firstOrNull()?.id,
            selectedThreadSnapshot = initialBaseThreads.firstOrNull(),
        ),
    )

    suspend fun sendPrompt(
        threadId: String,
        prompt: String,
        attachments: List<RemodexComposerAttachment>,
    ) {
        sendPrompt(
            threadId = threadId,
            prompt = prompt,
            attachments = attachments,
            planningModeOverride = null,
        )
    }
    private val gptAccountSnapshotState = MutableStateFlow(remodexInitialGptAccountSnapshot())
    private val gptAccountErrorMessageState = MutableStateFlow<String?>(null)
    private val bridgeVersionStatusState = MutableStateFlow(RemodexBridgeVersionStatus())
    private val usageStatusState = MutableStateFlow(RemodexUsageStatus())

    override val session: StateFlow<RemodexSessionSnapshot> = sessionState
    override val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>> =
        threadSyncService.commandExecutionDetails
    override val assistantResponseMetricsByThreadId: StateFlow<Map<String, RemodexAssistantResponseMetrics>> =
        threadSyncService.assistantResponseMetricsByThreadId
    override val gptAccountSnapshot: StateFlow<RemodexGptAccountSnapshot> = gptAccountSnapshotState
    override val gptAccountErrorMessage: StateFlow<String?> = gptAccountErrorMessageState
    override val bridgeVersionStatus: StateFlow<RemodexBridgeVersionStatus> = bridgeVersionStatusState
    override val usageStatus: StateFlow<RemodexUsageStatus> = usageStatusState

    init {
        appPreferencesRepository.setActiveBridgeProfileId(activeBridgeProfileId)
        threadCacheStore.setActiveProfileId(activeBridgeProfileId)
        syncActiveThreadHint(sessionState.value.selectedThreadId)

        repositoryScope.launch(start = CoroutineStart.UNDISPATCHED) {
            secureConnectionCoordinator.bridgeProfiles.collectLatest { bridgeProfiles ->
                val nextActiveProfileId = bridgeProfiles.activeProfileId
                val previousActiveProfileId = activeBridgeProfileId
                val didChangeActiveProfile =
                    previousActiveProfileId != null && nextActiveProfileId != previousActiveProfileId
                activeBridgeProfileId = nextActiveProfileId
                appPreferencesRepository.setActiveBridgeProfileId(nextActiveProfileId)
                threadCacheStore.setActiveProfileId(nextActiveProfileId)
                if (didChangeActiveProfile) {
                    suppressBridgeScopedThreadsUntilNextSync = true
                    reconnectCatchupJobByThread.values.forEach(Job::cancel)
                    reconnectCatchupJobByThread.clear()
                    recoveredEncryptedAttempt = null
                    clearThreadsForExplicitDisconnect()
                    resetBridgeScopedStatus()
                }
            }
        }

        repositoryScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            // Streaming assistant deltas should update the visible bubble continuously.
            // Using collectLatest here lets newer thread snapshots cancel in-flight work,
            // which makes Android feel chunkier than iOS under heavy streaming.
            threadSyncService.threads.collect { snapshots ->
                if (suppressBridgeScopedThreadsUntilNextSync) {
                    if (snapshots.isEmpty()) {
                        return@collect
                    }
                    suppressBridgeScopedThreadsUntilNextSync = false
                }
                syncThreads(snapshots)
            }
        }

        repositoryScope.launch(start = CoroutineStart.UNDISPATCHED) {
            threadCacheStore.threads.collectLatest { cachedThreads ->
                if (suppressBridgeScopedThreadsUntilNextSync) {
                    return@collectLatest
                }
                if (threadSyncService.threads.value.isNotEmpty()) {
                    return@collectLatest
                }
                refreshThreadsLocally(cachedThreads.map(CachedThreadRecord::toBaseThreadSummary))
            }
        }

        repositoryScope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                combine(
                    appPreferencesRepository.preferences,
                    secureConnectionCoordinator.state,
                    secureConnectionCoordinator.bridgeProfiles,
                    managedPushRegistrationState,
                    threadSyncService.availableModels,
                ) { preferences, secureConnection, bridgeProfiles, notificationRegistration, availableModels ->
                    SessionInputs(
                        preferences = preferences,
                        secureConnection = secureConnection,
                        bridgeProfiles = bridgeProfiles,
                        notificationRegistration = notificationRegistration,
                        availableModels = availableModels,
                        bridgeUpdatePrompt = null,
                        supportsThreadFork = true,
                        pendingApprovalRequest = null,
                    )
                },
                threadSyncService.bridgeUpdatePrompt,
                threadSyncService.supportsThreadFork,
                threadSyncService.pendingApprovalRequest,
            ) { baseInputs, bridgeUpdatePrompt, supportsThreadFork, pendingApprovalRequest ->
                SessionInputs(
                    preferences = baseInputs.preferences,
                    secureConnection = baseInputs.secureConnection,
                    bridgeProfiles = baseInputs.bridgeProfiles,
                    notificationRegistration = baseInputs.notificationRegistration,
                    availableModels = baseInputs.availableModels,
                    bridgeUpdatePrompt = bridgeUpdatePrompt,
                    supportsThreadFork = supportsThreadFork,
                    pendingApprovalRequest = pendingApprovalRequest,
                )
            }.collectLatest { inputs ->
                val preferences = inputs.preferences
                val secureConnection = inputs.secureConnection
                val baseThreads = bridgeScopedBaseThreads(baseThreadsState.value)
                val resolvedAvailableModels = inputs.availableModels.takeIf(List<RemodexModelOption>::isNotEmpty)
                    ?: baseThreads.firstNotNullOfOrNull { thread ->
                        thread.runtimeConfig.availableModels.takeIf(List<RemodexModelOption>::isNotEmpty)
                    }
                    ?: emptyList()
                preferencesState.value = preferences
                val threads = materializeThreads(
                    baseThreads = baseThreads,
                    preferences = preferences,
                    availableModels = resolvedAvailableModels,
                )
                val currentSessionSnapshot = sessionState.value
                val inMemorySelectedThreadId = preferredSelectedThreadId.get()
                val selectedThreadId = if (suppressBridgeScopedThreadsUntilNextSync) {
                    null
                } else {
                    resolveSelectedThreadId(
                        preferredThreadId = preferredSelectedThreadId(
                            inMemorySelectedThreadId = inMemorySelectedThreadId,
                            persistedSelectedThreadId = preferences.selectedThreadId,
                            sessionSelectedThreadId = currentSessionSnapshot.selectedThreadId,
                        ),
                        stickyThreadId = currentSessionSnapshot.selectedThreadId,
                        stickyThreadSnapshot = currentSessionSnapshot.selectedThreadSnapshot,
                        threads = threads,
                    )
                }
                preferredSelectedThreadId.set(selectedThreadId)
                if (selectedThreadId != preferences.selectedThreadId) {
                    appPreferencesRepository.setSelectedThreadId(selectedThreadId)
                }
                cancelPendingThreadListPublish()
                sessionState.update { snapshot ->
                    snapshot.copy(
                        onboardingCompleted = preferences.onboardingCompleted,
                        connectionStatus = secureConnection.toConnectionStatus(),
                        secureConnection = secureConnection,
                        runtimeDefaults = preferences.runtimeDefaults,
                        availableModels = resolvedAvailableModels,
                        appearanceMode = preferences.appearanceMode,
                        appFontStyle = preferences.appFontStyle,
                        trustedMac = secureConnection.toTrustedMacPresentation(preferences.macNicknamesByDeviceId),
                        bridgeProfiles = inputs.bridgeProfiles.toBridgeProfilePresentations(
                            nicknamesByDeviceId = preferences.macNicknamesByDeviceId,
                            connectedProfileId = secureConnection.activeProfileId.takeIf {
                                secureConnection.secureState == SecureConnectionState.ENCRYPTED
                            },
                        ),
                        bridgeUpdatePrompt = inputs.bridgeUpdatePrompt,
                        supportsThreadFork = inputs.supportsThreadFork,
                        pendingApprovalRequest = inputs.pendingApprovalRequest,
                        threads = threads,
                        selectedThreadId = selectedThreadId,
                        selectedThreadSnapshot = resolveSelectedThreadSnapshot(
                            selectedThreadId = selectedThreadId,
                            threads = threads,
                            currentSelectedThreadSnapshot = currentSessionSnapshot.selectedThreadSnapshot,
                        ),
                        notificationRegistration = inputs.notificationRegistration,
                    )
                }
                syncActiveThreadHint(selectedThreadId)
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            secureConnectionCoordinator.state.collectLatest { secureConnection ->
                if (secureConnection.secureState != SecureConnectionState.ENCRYPTED) {
                    recoveredEncryptedAttempt = null
                    reconnectCatchupJobByThread.values.forEach(Job::cancel)
                    reconnectCatchupJobByThread.clear()
                    return@collectLatest
                }

                val currentAttempt = secureConnection.attempt
                if (recoveredEncryptedAttempt == currentAttempt) {
                    return@collectLatest
                }
                recoveredEncryptedAttempt = currentAttempt

                val selectedThreadBeforeRecovery = sessionState.value.selectedThread
                val selectedThreadIdBeforeRecovery =
                    selectedThreadBeforeRecovery?.id ?: sessionState.value.selectedThreadId

                if (selectedThreadIdBeforeRecovery != null) {
                    hydrateThreadAndRecoverLiveSessionIfNeeded(
                        threadId = selectedThreadIdBeforeRecovery,
                        wasRunningBeforeHydrate = selectedThreadBeforeRecovery?.isRunning == true,
                        preferResumeBeforeHydrate = true,
                        forceResumeBeforeHydrate = true,
                    )
                    scheduleReconnectCatchup(threadId = selectedThreadIdBeforeRecovery)
                    return@collectLatest
                }

                runHydrationSafely {
                    hydrationService()?.refreshThreads()
                }
                val recoveredThreadId = recoverReconnectCandidateThreadId() ?: return@collectLatest
                selectRecoveredThread(threadId = recoveredThreadId)
                scheduleReconnectCatchup(threadId = recoveredThreadId)
            }
        }

        if (secureConnectionCoordinator.state.value.secureState == SecureConnectionState.TRUSTED_MAC) {
            scope.launch {
                secureConnectionCoordinator.retryConnection()
            }
        }
    }

    private suspend fun runHydrationSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            if (!shouldTreatAsThreadNotFoundValue(error)) {
                return
            }
        }
    }

    private suspend fun hydrateThreadAndRecoverLiveSessionIfNeeded(
        threadId: String,
        wasRunningBeforeHydrate: Boolean = false,
        preferResumeBeforeHydrate: Boolean = false,
        forceResumeBeforeHydrate: Boolean = false,
    ): RecoveredThreadContext? {
        val threadBeforeHydrate = resolveRecoveredThreadContext(threadId)
        logRecovery(
            "event=recoverThread stage=preHydrate threadId=$threadId before=${recoveredThreadSummary(threadBeforeHydrate)} " +
                "wasRunningBeforeHydrate=$wasRunningBeforeHydrate preferResumeBeforeHydrate=$preferResumeBeforeHydrate " +
                "forceResumeBeforeHydrate=$forceResumeBeforeHydrate hasStreamingTimeline=${threadHasStreamingTimeline(threadId)}",
        )
        if (preferResumeBeforeHydrate && shouldPreemptivelyResumeThread(threadId, threadBeforeHydrate, wasRunningBeforeHydrate)) {
            resumeThreadIfNeeded(
                threadId = threadId,
                preferredProjectPath = threadBeforeHydrate?.projectPath,
                modelIdentifier = threadBeforeHydrate?.modelIdentifier,
                skipIfPendingApprovalPromptVisible = threadBeforeHydrate?.isWaitingOnApproval == true,
                force = forceResumeBeforeHydrate,
            )
        }
        runHydrationSafely {
            hydrationService()?.hydrateThread(threadId)
        }
        val recoveredThread = resolveRecoveredThreadContext(threadId) ?: threadBeforeHydrate ?: return null
        val hasStreamingTimeline = threadHasStreamingTimeline(threadId)
        logRecovery(
            "event=recoverThread stage=postHydrate threadId=$threadId recovered=${recoveredThreadSummary(recoveredThread)} " +
                "wasRunningBeforeHydrate=$wasRunningBeforeHydrate hasStreamingTimeline=$hasStreamingTimeline",
        )
        if (wasRunningBeforeHydrate || recoveredThread.requiresLiveResume || hasStreamingTimeline) {
            resumeRecoveredThreadIfNeeded(
                recoveredThread = recoveredThread,
                force = preferResumeBeforeHydrate && recoveredThread.isRunning,
            )
        }
        return recoveredThread
    }

    private suspend fun recoverReconnectCandidateThreadId(): String? {
        val candidateThreadIds = collectReconnectRecoveryCandidateThreadIds()
        for (threadId in candidateThreadIds) {
            val recoveredThread = hydrateThreadAndRecoverLiveSessionIfNeeded(
                threadId = threadId,
                preferResumeBeforeHydrate = true,
            ) ?: continue
            if (recoveredThread.requiresLiveResume || threadHasStreamingTimeline(threadId)) {
                return threadId
            }
        }
        return null
    }

    private fun collectReconnectRecoveryCandidateThreadIds(limit: Int = 3): List<String> {
        val prioritizedThreadIds = linkedSetOf<String>()
        listOf(
            preferredSelectedThreadId.get(),
            preferencesState.value.selectedThreadId,
            sessionState.value.selectedThreadId,
            sessionState.value.selectedThreadSnapshot?.id,
        ).forEach { threadId ->
            val normalizedThreadId = threadId?.trim().orEmpty()
            if (normalizedThreadId.isNotEmpty()) {
                prioritizedThreadIds += normalizedThreadId
            }
        }

        val fallbackThreadIds = linkedSetOf<String>()
        sessionState.value.threads
            .asSequence()
            .filter { thread -> thread.syncState == RemodexThreadSyncState.LIVE }
            .map(RemodexThreadSummary::id)
            .forEach(fallbackThreadIds::add)

        baseThreadsState.value
            .asSequence()
            .filter { thread -> thread.syncState == RemodexThreadSyncState.LIVE }
            .map(RemodexThreadSummary::id)
            .forEach(fallbackThreadIds::add)

        threadSyncService.threads.value
            .asSequence()
            .filter { snapshot -> snapshot.syncState == RemodexThreadSyncState.LIVE }
            .sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
            .map(ThreadSyncSnapshot::id)
            .forEach(fallbackThreadIds::add)

        return buildList {
            addAll(prioritizedThreadIds)
            addAll(
                fallbackThreadIds
                    .asSequence()
                    .filterNot(prioritizedThreadIds::contains)
                    .take(limit)
                    .toList(),
            )
        }
            .distinct()
            .toList()
    }

    private fun resolveRecoveredThreadContext(threadId: String): RecoveredThreadContext? {
        val syncThread = threadSyncService.threads.value.firstOrNull { snapshot -> snapshot.id == threadId }
        val baseThread = baseThreadsState.value.firstOrNull { thread -> thread.id == threadId }
        val sessionThread = sessionState.value.threads.firstOrNull { thread -> thread.id == threadId }
        if (syncThread == null && baseThread == null && sessionThread == null) {
            return null
        }
        return RecoveredThreadContext(
            threadId = threadId,
            isRunning = syncThread?.isRunning ?: sessionThread?.isRunning ?: baseThread?.isRunning ?: false,
            isWaitingOnApproval = syncThread?.isWaitingOnApproval
                ?: sessionThread?.isWaitingOnApproval
                ?: baseThread?.isWaitingOnApproval
                ?: false,
            projectPath = syncThread?.projectPath?.ifBlank { null }
                ?: sessionThread?.projectPath?.ifBlank { null }
                ?: baseThread?.projectPath?.ifBlank { null },
            modelIdentifier = syncThread?.runtimeConfig?.selectedModelId
                ?: sessionThread?.runtimeConfig?.selectedModelId
                ?: baseThread?.runtimeConfig?.selectedModelId,
        )
    }

    private suspend fun resumeRecoveredThreadIfNeeded(
        recoveredThread: RecoveredThreadContext,
        force: Boolean = false,
    ) {
        resumeThreadIfNeeded(
            threadId = recoveredThread.threadId,
            preferredProjectPath = recoveredThread.projectPath,
            modelIdentifier = recoveredThread.modelIdentifier,
            skipIfPendingApprovalPromptVisible = recoveredThread.isWaitingOnApproval,
            force = force,
        )
    }

    private suspend fun selectRecoveredThread(threadId: String) {
        persistSelectedThreadIdLocally(threadId)
        val availableModels = resolveAvailableModels(baseThreadsState.value)
        val recoveredSelectedThread = sessionState.value.threads.firstOrNull { thread -> thread.id == threadId }
            ?: baseThreadsState.value.firstOrNull { thread -> thread.id == threadId }?.materialize(
                preferences = preferencesState.value,
                availableModels = availableModels,
            )
            ?: threadSyncService.threads.value.firstOrNull { snapshot -> snapshot.id == threadId }?.let { snapshot ->
                snapshot.toCachedThreadRecord(projectThreadTimelineItems(snapshot))
            }
                ?.toBaseThreadSummary()
                ?.materialize(
                    preferences = preferencesState.value,
                    availableModels = availableModels,
                )
        sessionState.update { snapshot ->
            snapshot.copy(
                selectedThreadId = threadId,
                selectedThreadSnapshot = recoveredSelectedThread ?: snapshot.selectedThreadSnapshot,
            )
        }
    }

    private suspend fun resumeThreadIfNeeded(
        threadId: String,
        preferredProjectPath: String?,
        modelIdentifier: String?,
        skipIfPendingApprovalPromptVisible: Boolean,
        force: Boolean = false,
    ) {
        if (!hasActiveSecureTransport()) {
            logRecovery("event=resumeThread skipped=noSecureTransport threadId=$threadId force=$force")
            return
        }
        val resumeService = resumeService() ?: run {
            logRecovery("event=resumeThread skipped=noResumeService threadId=$threadId force=$force")
            return
        }
        if (!force && resumeService.isThreadResumedLocally(threadId)) {
            logRecovery("event=resumeThread skipped=alreadyResumed threadId=$threadId force=$force")
            return
        }
        if (
            skipIfPendingApprovalPromptVisible &&
            threadSyncService.pendingApprovalRequest.value?.threadId == threadId
        ) {
            logRecovery("event=resumeThread skipped=pendingApprovalVisible threadId=$threadId force=$force")
            return
        }
        var didResume = false
        logRecovery(
            "event=resumeThread start threadId=$threadId force=$force preferredProjectPath=${preferredProjectPath.orEmpty()} " +
                "modelIdentifier=${modelIdentifier.orEmpty()}",
        )
        runHydrationSafely {
            resumeService.resumeThread(
                threadId = threadId,
                preferredProjectPath = preferredProjectPath,
                modelIdentifier = modelIdentifier,
            )
            didResume = true
        }
        if (didResume) {
            refreshBaseThreadsFromSync()
            logRecovery(
                "event=resumeThread success threadId=$threadId snapshot=${threadSummary(threadSyncService.threads.value.firstOrNull { snapshot -> snapshot.id == threadId })}",
            )
        } else {
            logRecovery("event=resumeThread completedWithoutSnapshot threadId=$threadId force=$force")
        }
    }

    private fun shouldPreemptivelyResumeThread(
        threadId: String,
        recoveredThread: RecoveredThreadContext?,
        wasRunningBeforeHydrate: Boolean,
    ): Boolean {
        if (wasRunningBeforeHydrate || recoveredThread?.requiresLiveResume == true) {
            return true
        }
        if (!hasRecoverableHistoryOrMaterializedState(threadId)) {
            return false
        }
        return !isLocalOnlyEmptyThread(threadId)
    }

    private fun hasRecoverableHistoryOrMaterializedState(threadId: String): Boolean {
        val syncThread = threadSyncService.threads.value.firstOrNull { snapshot -> snapshot.id == threadId }
        if (syncThread != null) {
            return true
        }
        val baseThread = baseThreadsState.value.firstOrNull { thread -> thread.id == threadId }
        if (baseThread?.messages?.isNotEmpty() == true) {
            return true
        }
        val sessionThread = sessionState.value.threads.firstOrNull { thread -> thread.id == threadId }
        return sessionThread?.messages?.isNotEmpty() == true
    }

    private fun isLocalOnlyEmptyThread(threadId: String): Boolean {
        if (threadSyncService.threads.value.any { snapshot -> snapshot.id == threadId }) {
            return false
        }
        val baseThread = baseThreadsState.value.firstOrNull { thread -> thread.id == threadId }
        val sessionThread = sessionState.value.threads.firstOrNull { thread -> thread.id == threadId }
        return (baseThread?.messages?.isEmpty() != false) && (sessionThread?.messages?.isEmpty() != false)
    }

    private fun threadHasStreamingTimeline(threadId: String): Boolean {
        val syncSnapshot = threadSyncService.threads.value.firstOrNull { snapshot -> snapshot.id == threadId }
        if (syncSnapshot != null && projectThreadTimelineItems(syncSnapshot).any(RemodexConversationItem::isStreaming)) {
            return true
        }
        val baseThread = baseThreadsState.value.firstOrNull { thread -> thread.id == threadId }
        if (baseThread?.messages?.any(RemodexConversationItem::isStreaming) == true) {
            return true
        }
        val sessionThread = sessionState.value.threads.firstOrNull { thread -> thread.id == threadId }
        return sessionThread?.messages?.any(RemodexConversationItem::isStreaming) == true
    }

    private suspend fun scheduleReconnectCatchup(threadId: String) {
        reconnectCatchupJobByThread.remove(threadId)?.cancel()
        if (performReconnectCatchupPass(threadId)) {
            return
        }
        reconnectCatchupJobByThread[threadId] = repositoryScope.launch {
            try {
                val recoveryDelaysMs = listOf(1_000L, 2_000L)
                for (delayMs in recoveryDelaysMs) {
                    delay(delayMs)
                    if (performReconnectCatchupPass(threadId)) {
                        break
                    }
                }
            } finally {
                reconnectCatchupJobByThread.remove(threadId)
            }
        }
    }

    private suspend fun performReconnectCatchupPass(threadId: String): Boolean {
        if (!hasActiveSecureTransport()) {
            return secureConnectionCoordinator.state.value.secureState != SecureConnectionState.ENCRYPTED
        }

        runHydrationSafely {
            hydrationService()?.refreshThreads()
        }
        runHydrationSafely {
            hydrationService()?.hydrateThread(threadId)
        }

        val refreshedSnapshot = threadSyncService.threads.value.firstOrNull { snapshot -> snapshot.id == threadId }
            ?: return true
        if (
            refreshedSnapshot.isRunning ||
            refreshedSnapshot.isWaitingOnApproval ||
            projectThreadTimelineItems(refreshedSnapshot).any(RemodexConversationItem::isStreaming)
        ) {
            val resumeService = resumeService()
            if (resumeService?.isThreadResumedLocally(threadId) != true) {
                runHydrationSafely {
                    resumeService?.resumeThread(
                        threadId = threadId,
                        preferredProjectPath = refreshedSnapshot.projectPath.ifBlank { null },
                        modelIdentifier = refreshedSnapshot.runtimeConfig.selectedModelId,
                    )
                }
            }
        }

        val settledSnapshot = threadSyncService.threads.value.firstOrNull { snapshot -> snapshot.id == threadId }
            ?: return true
        val hasPendingApprovalForThread = threadSyncService.pendingApprovalRequest.value?.threadId == threadId
        return hasPendingApprovalForThread || (
            !settledSnapshot.isRunning &&
                !settledSnapshot.isWaitingOnApproval &&
                projectThreadTimelineItems(settledSnapshot).none(RemodexConversationItem::isStreaming)
            )
    }

    private suspend fun fetchBridgeManagedAccountState() =
        runCatching {
            secureConnectionCoordinator.sendRequest(method = "account/status/read", params = null)
        }.getOrElse {
            secureConnectionCoordinator.sendRequest(method = "getAuthStatus", params = null)
        }

    private suspend fun fetchRateLimitsWithCompatRetry() =
        runCatching {
            secureConnectionCoordinator.sendRequest(method = "account/rateLimits/read", params = null)
        }.getOrElse {
            secureConnectionCoordinator.sendRequest(
                method = "account/rateLimits/read",
                params = buildJsonObject {},
            )
        }

    private suspend fun readContextWindowUsage(threadId: String) =
        secureConnectionCoordinator.sendRequest(
            method = "thread/contextWindow/read",
            params = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
            },
        ).result?.jsonObjectOrNull?.let(::decodeContextWindowUsage)

    private fun hydrationService(): ThreadHydrationService? {
        return threadHydrationService ?: (threadSyncService as? ThreadHydrationService)
    }

    private fun localTimelineService(): ThreadLocalTimelineService? {
        return threadSyncService as? ThreadLocalTimelineService
    }

    private fun resumeService(): ThreadResumeService? {
        return threadSyncService as? ThreadResumeService
    }

    private fun activeThreadContextService(): ThreadActiveContextService? {
        return threadSyncService as? ThreadActiveContextService
    }

    private fun syncActiveThreadHint(threadId: String?) {
        activeThreadContextService()?.setActiveThreadHint(threadId)
    }

    private fun hasActiveSecureTransport(): Boolean {
        return secureConnectionCoordinator.isEncryptedSessionReady()
    }

    private suspend fun inheritRuntimeOverride(
        sourceThreadId: String,
        destinationThreadId: String,
    ) {
        val sourceOverride = preferencesState.value.runtimeOverridesByThread[sourceThreadId] ?: return
        appPreferencesRepository.setRuntimeOverrides(
            threadId = destinationThreadId,
            overrides = sourceOverride,
        )
        applyPreferencesLocally(
            preferencesState.value.copy(
                runtimeOverridesByThread = preferencesState.value.runtimeOverridesByThread
                    .toMutableMap()
                    .apply {
                        this[destinationThreadId] = sourceOverride
                    }
            ),
        )
    }

    private suspend fun persistSelectedThreadIdLocally(threadId: String?) {
        preferredSelectedThreadId.set(threadId)
        appPreferencesRepository.setSelectedThreadId(threadId)
        preferencesState.value = preferencesState.value.copy(selectedThreadId = threadId)
        sessionState.update { snapshot ->
            snapshot.copy(
                selectedThreadId = threadId,
                selectedThreadSnapshot = snapshot.selectedThreadSnapshot?.takeIf { selected ->
                    selected.id == threadId
                },
            )
        }
        syncActiveThreadHint(threadId)
    }

    private suspend fun setThreadDeletedLocally(
        threadId: String,
        deleted: Boolean,
    ) {
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isEmpty()) {
            return
        }
        appPreferencesRepository.setThreadDeleted(
            threadId = normalizedThreadId,
            deleted = deleted,
        )
        val updatedDeletedThreadIds = preferencesState.value.deletedThreadIds.toMutableSet().apply {
            if (deleted) {
                add(normalizedThreadId)
            } else {
                remove(normalizedThreadId)
            }
        }
        applyPreferencesLocally(
            preferencesState.value.copy(
                deletedThreadIds = updatedDeletedThreadIds,
            ),
        )
    }

    override suspend fun completeOnboarding() {
        appPreferencesRepository.setOnboardingCompleted(true)
        applyPreferencesLocally(
            preferencesState.value.copy(onboardingCompleted = true),
        )
    }

    override suspend fun refreshThreads() {
        if (!hasActiveSecureTransport()) {
            return
        }
        runHydrationSafely {
            hydrationService()?.refreshThreads()
        }
    }

    override suspend fun hydrateThread(threadId: String) {
        hydrateThreadAndRecoverLiveSession(threadId)
    }

    override suspend fun syncActiveThread(threadId: String) {
        if (!hasActiveSecureTransport()) {
            logRecovery("event=syncActiveThread skipped=noSecureTransport threadId=$threadId")
            return
        }
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isEmpty() || isLocalOnlyEmptyThread(normalizedThreadId)) {
            logRecovery("event=syncActiveThread skipped=emptyOrLocalOnly threadId=$threadId")
            return
        }

        val threadBeforeSync = resolveRecoveredThreadContext(normalizedThreadId)
        val hadStreamingTimeline = threadHasStreamingTimeline(normalizedThreadId)
        logRecovery(
            "event=syncActiveThread start threadId=$normalizedThreadId before=${recoveredThreadSummary(threadBeforeSync)} " +
                "hadStreamingTimeline=$hadStreamingTimeline",
        )
        runHydrationSafely {
            hydrationService()?.hydrateThread(normalizedThreadId)
        }
        val recoveredThread = resolveRecoveredThreadContext(normalizedThreadId) ?: threadBeforeSync ?: return
        logRecovery(
            "event=syncActiveThread postHydrate threadId=$normalizedThreadId recovered=${recoveredThreadSummary(recoveredThread)} " +
                "hasStreamingTimeline=${threadHasStreamingTimeline(normalizedThreadId)}",
        )
        if (recoveredThread.requiresLiveResume || hadStreamingTimeline || threadHasStreamingTimeline(normalizedThreadId)) {
            resumeRecoveredThreadIfNeeded(
                recoveredThread = recoveredThread,
                force = true,
            )
        }
    }

    override suspend fun selectThread(threadId: String) {
        val threadExists = session.value.threads.any { it.id == threadId } ||
            threadSyncService.threads.value.any { it.id == threadId }
        logRecovery(
            "event=selectThread requestedThreadId=$threadId exists=$threadExists sessionSelected=${session.value.selectedThreadId.orEmpty()} " +
                "syncSnapshot=${threadSummary(threadSyncService.threads.value.firstOrNull { snapshot -> snapshot.id == threadId })}",
        )
        if (threadExists) {
            persistSelectedThreadIdLocally(threadId)
            val previousSelectedThread = sessionState.value.selectedThreadSnapshot
            sessionState.update { snapshot ->
                snapshot.copy(
                    selectedThreadId = threadId,
                    selectedThreadSnapshot = snapshot.threads.firstOrNull { thread -> thread.id == threadId }
                        ?: snapshot.selectedThreadSnapshot,
                )
            }
            logSelectedThreadState(
                source = "selectThread",
                previous = previousSelectedThread,
                next = sessionState.value.selectedThreadSnapshot,
            )
            hydrateThreadAndRecoverLiveSession(threadId)
        }
    }

    private suspend fun hydrateThreadAndRecoverLiveSession(threadId: String) {
        hydrateThreadAndRecoverLiveSessionIfNeeded(
            threadId = threadId,
            preferResumeBeforeHydrate = true,
            forceResumeBeforeHydrate = true,
        )
    }

    override suspend fun setProjectGroupCollapsed(
        groupId: String,
        collapsed: Boolean,
    ) {
        val normalizedGroupId = groupId.trim()
        if (normalizedGroupId.isEmpty()) {
            return
        }
        appPreferencesRepository.setProjectGroupCollapsed(
            groupId = normalizedGroupId,
            collapsed = collapsed,
        )
        val updatedCollapsedGroupIds = preferencesState.value.collapsedProjectGroupIds
            .toMutableSet()
            .apply {
                if (collapsed) {
                    add(normalizedGroupId)
                } else {
                    remove(normalizedGroupId)
                }
            }
        applyPreferencesLocally(
            preferencesState.value.copy(
                collapsedProjectGroupIds = updatedCollapsedGroupIds,
            ),
        )
    }

    override suspend fun createThread(
        preferredProjectPath: String?,
        inheritRuntimeFromThreadId: String?,
    ) {
        val runtimeDefaults = effectiveRuntimeDefaultsForNewThread(inheritRuntimeFromThreadId)
        val createdThread = threadCommandService.createThread(
            preferredProjectPath = preferredProjectPath,
            runtimeDefaults = runtimeDefaults,
        ) ?: return
        inheritRuntimeFromThreadId?.let { sourceThreadId ->
            inheritRuntimeOverride(
                sourceThreadId = sourceThreadId,
                destinationThreadId = createdThread.id,
            )
        }
        refreshBaseThreadsLocallyFromSnapshots(
            snapshots = listOf(createdThread),
            preferredSelectedThreadIdOverride = createdThread.id,
        )
    }

    override suspend fun renameThread(
        threadId: String,
        name: String,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return
        }
        refreshThreadsLocally(
            baseThreadsState.value.map { thread ->
                if (thread.id == threadId) {
                    thread.copy(title = trimmedName, name = trimmedName)
                } else {
                    thread
                }
            },
        )
        threadCommandService.renameThread(threadId, trimmedName)
    }

    override suspend fun archiveThread(threadId: String) {
        val subtreeIds = collectSubtreeThreadIds(baseThreadsState.value, threadId)
        val updatedThreads = baseThreadsState.value.map { thread ->
            if (thread.id in subtreeIds) {
                thread.copy(syncState = RemodexThreadSyncState.ARCHIVED_LOCAL)
            } else {
                thread
            }
        }
        refreshThreadsLocally(updatedThreads)
        val nextSelectedThreadId = if (sessionState.value.selectedThreadId in subtreeIds) {
            updatedThreads.firstOrNull { it.syncState == RemodexThreadSyncState.LIVE }?.id
                ?: updatedThreads.firstOrNull()?.id
        } else {
            sessionState.value.selectedThreadId
        }
        persistSelectedThreadIdLocally(nextSelectedThreadId)
        sessionState.update { snapshot ->
            snapshot.copy(
                selectedThreadId = nextSelectedThreadId,
                selectedThreadSnapshot = snapshot.threads.firstOrNull { thread -> thread.id == nextSelectedThreadId }
                    ?: snapshot.threads.firstOrNull(),
            )
        }
        subtreeIds.forEach { subtreeThreadId ->
            threadCommandService.archiveThread(subtreeThreadId, unarchive = false)
        }
    }

    override suspend fun unarchiveThread(threadId: String) {
        val subtreeIds = collectSubtreeThreadIds(baseThreadsState.value, threadId)
        refreshThreadsLocally(
            baseThreadsState.value.map { thread ->
                if (thread.id in subtreeIds) {
                    thread.copy(syncState = RemodexThreadSyncState.LIVE)
                } else {
                    thread
                }
            },
        )
        subtreeIds.forEach { subtreeThreadId ->
            threadCommandService.archiveThread(subtreeThreadId, unarchive = true)
        }
    }

    override suspend fun deleteThread(threadId: String) {
        val descendantIds = collectDescendantThreadIds(baseThreadsState.value, threadId)
        val updatedThreads = baseThreadsState.value.mapNotNull { thread ->
            when {
                thread.id == threadId -> null
                thread.id in descendantIds -> {
                    thread.copy(
                        syncState = RemodexThreadSyncState.ARCHIVED_LOCAL,
                        isRunning = false,
                        activeTurnId = null,
                    )
                }
                else -> thread
            }
        }
        val nextSelectedThreadId = if (sessionState.value.selectedThreadId == threadId) {
            updatedThreads.firstOrNull { it.syncState == RemodexThreadSyncState.LIVE }?.id
                ?: updatedThreads.firstOrNull()?.id
        } else {
            sessionState.value.selectedThreadId
        }
        baseThreadsState.value = updatedThreads
        val resolvedAvailableModels = resolveAvailableModels(updatedThreads)
        persistSelectedThreadIdLocally(nextSelectedThreadId)
        publishSelectedThreadSnapshot(
            baseThreads = updatedThreads,
            preferences = preferencesState.value,
            availableModels = resolvedAvailableModels,
            selectedThreadId = nextSelectedThreadId,
        )
        cancelPendingThreadListPublish()
        publishMaterializedThreads(
            baseThreads = updatedThreads,
            preferences = preferencesState.value,
            availableModels = resolvedAvailableModels,
            secureConnection = sessionState.value.secureConnection,
            notificationRegistration = sessionState.value.notificationRegistration,
        )
        setThreadDeletedLocally(threadId = threadId, deleted = true)
        threadCommandService.deleteThread(threadId)
    }

    override suspend fun archiveProject(projectPath: String) {
        val rootThreadIds = baseThreadsState.value
            .filter { thread ->
                thread.projectPath == projectPath &&
                    thread.parentThreadId == null &&
                    thread.syncState != RemodexThreadSyncState.ARCHIVED_LOCAL
            }
            .map(RemodexThreadSummary::id)
        for (rootThreadId in rootThreadIds) {
            archiveThread(rootThreadId)
        }
    }

    override suspend fun sendPrompt(
        threadId: String,
        prompt: String,
        attachments: List<RemodexComposerAttachment>,
        planningModeOverride: RemodexPlanningMode?,
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty() && attachments.isEmpty()) {
            return
        }
        var thread = sessionState.value.threads.firstOrNull { it.id == threadId } ?: return
        val activePlanningMode = planningModeOverride ?: thread.runtimeConfig.planningMode
        if (!thread.isRunning) {
            try {
                thread = resumeThreadBeforeSend(thread = thread)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                if (!shouldTreatAsThreadNotFoundValue(error)) {
                    throw error
                }
                val continuationThreadId = continueMissingThread(thread)
                val continuationThread = sessionState.value.threads.firstOrNull { candidate ->
                    candidate.id == continuationThreadId
                } ?: thread
                val resumedContinuationThread = resumeThreadBeforeSend(thread = continuationThread)
                sendPromptWithLocalOptimistic(
                    threadId = continuationThreadId,
                    prompt = trimmedPrompt,
                    runtimeConfig = applyPlanningModeOverride(
                        resumedContinuationThread.runtimeConfig,
                        planningModeOverride,
                    ),
                    attachments = attachments,
                )
                refreshBaseThreadsFromSync()
                return
            }
        }
        if (thread.isRunning) {
            val nextDrafts = thread.queuedDraftItems + RemodexQueuedDraft(
                id = "draft-${UUID.randomUUID()}",
                text = trimmedPrompt,
                createdAtEpochMs = System.currentTimeMillis(),
                attachments = attachments,
                planningMode = planningModeOverride ?: activePlanningMode.takeIf {
                    it == RemodexPlanningMode.PLAN
                },
            )
            appPreferencesRepository.setQueuedDrafts(threadId, nextDrafts)
            applyPreferencesLocally(
                preferencesState.value.copy(
                    queuedDraftsByThread = preferencesState.value.queuedDraftsByThread
                        .toMutableMap()
                        .apply {
                            this[threadId] = nextDrafts
                    },
                ),
            )
            return
        }

        try {
            sendPromptWithLocalOptimistic(
                threadId = threadId,
                prompt = trimmedPrompt,
                runtimeConfig = applyPlanningModeOverride(
                    thread.runtimeConfig,
                    planningModeOverride,
                ),
                attachments = attachments,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            if (!shouldTreatAsThreadNotFoundValue(error)) {
                throw error
            }
            val continuationThreadId = continueMissingThread(thread)
            val continuationThread = sessionState.value.threads.firstOrNull { candidate ->
                candidate.id == continuationThreadId
            } ?: thread
            val resumedContinuationThread = resumeThreadBeforeSend(thread = continuationThread)
            sendPromptWithLocalOptimistic(
                threadId = continuationThreadId,
                prompt = trimmedPrompt,
                attachments = attachments,
                runtimeConfig = applyPlanningModeOverride(
                    resumedContinuationThread.runtimeConfig,
                    planningModeOverride,
                ),
            )
        }
        refreshBaseThreadsFromSync()
    }

    override suspend fun compactThread(threadId: String) {
        val thread = sessionState.value.threads.firstOrNull { it.id == threadId } ?: return
        val resumedThread = if (!thread.isRunning) {
            try {
                resumeThreadBeforeSend(thread = thread)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                if (!shouldTreatAsThreadNotFoundValue(error)) {
                    throw error
                }
                val continuationThreadId = continueMissingThread(thread)
                val continuationThread = sessionState.value.threads.firstOrNull { candidate ->
                    candidate.id == continuationThreadId
                } ?: thread
                resumeThreadBeforeSend(thread = continuationThread)
            }
        } else {
            thread
        }
        threadCommandService.compactThread(resumedThread.id)
        refreshBaseThreadsFromSync()
    }

    override suspend fun respondToStructuredUserInput(
        requestId: JsonElement,
        answersByQuestionId: Map<String, List<String>>,
    ) {
        threadCommandService.respondToStructuredUserInput(requestId, answersByQuestionId)
    }

    override suspend fun approvePendingApproval(forSession: Boolean) {
        threadCommandService.approvePendingApproval(forSession)
    }

    override suspend fun declinePendingApproval() {
        threadCommandService.declinePendingApproval()
    }

    override suspend fun cancelPendingApproval() {
        threadCommandService.cancelPendingApproval()
    }

    override suspend fun grantPendingPermissionsApproval(scope: RemodexPermissionGrantScope) {
        threadCommandService.grantPendingPermissionsApproval(scope)
    }

    override suspend fun continueOnMac(threadId: String) {
        threadCommandService.continueOnMac(threadId)
    }

    override suspend fun stopTurn(threadId: String) {
        threadCommandService.stopTurn(threadId)
        refreshBaseThreadsFromSync()
    }

    override suspend fun sendQueuedDraft(
        threadId: String,
        draftId: String,
    ) {
        val thread = sessionState.value.threads.firstOrNull { it.id == threadId } ?: return
        if (thread.isRunning) {
            return
        }
        val draft = thread.queuedDraftItems.firstOrNull { it.id == draftId } ?: return
        val remainingDrafts = thread.queuedDraftItems.filterNot { it.id == draftId }
        appPreferencesRepository.setQueuedDrafts(threadId, remainingDrafts)
        applyPreferencesLocally(
            preferencesState.value.copy(
                queuedDraftsByThread = preferencesState.value.queuedDraftsByThread
                    .toMutableMap()
                    .apply {
                        if (remainingDrafts.isEmpty()) {
                            remove(threadId)
                        } else {
                            this[threadId] = remainingDrafts
                        }
                    },
            ),
        )
        threadCommandService.sendPrompt(
            threadId = threadId,
            prompt = draft.text,
            runtimeConfig = thread.runtimeConfig.copy(
                planningMode = draft.planningMode ?: thread.runtimeConfig.planningMode,
            ),
            attachments = draft.attachments,
        )
        refreshBaseThreadsFromSync()
    }

    override suspend fun setPlanningMode(
        threadId: String,
        planningMode: RemodexPlanningMode,
    ) {
        val currentOverride = preferencesState.value.runtimeOverridesByThread[threadId]
        val nextOverride = (currentOverride ?: RemodexRuntimeOverrides()).copy(planningMode = planningMode)
        appPreferencesRepository.setRuntimeOverrides(
            threadId = threadId,
            overrides = nextOverride,
        )
        applyPreferencesLocally(
            preferencesState.value.copy(
                runtimeOverridesByThread = preferencesState.value.runtimeOverridesByThread
                    .toMutableMap()
                    .apply {
                        this[threadId] = nextOverride
                    },
            ),
        )
    }

    override suspend fun setSelectedModelId(
        modelId: String?,
    ) {
        mutateRuntimeDefaults { defaults ->
            defaults.copy(modelId = modelId?.trim()?.takeIf(String::isNotEmpty))
        }
    }

    override suspend fun setReasoningEffort(
        threadId: String,
        reasoningEffort: String,
    ) {
        val currentOverride = preferencesState.value.runtimeOverridesByThread[threadId]
        val nextOverride = (currentOverride ?: RemodexRuntimeOverrides()).copy(
            reasoningEffort = reasoningEffort.trim().takeIf(String::isNotEmpty),
        )
        appPreferencesRepository.setRuntimeOverrides(
            threadId = threadId,
            overrides = nextOverride,
        )
        applyPreferencesLocally(
            preferencesState.value.copy(
                runtimeOverridesByThread = preferencesState.value.runtimeOverridesByThread
                    .toMutableMap()
                    .apply {
                        this[threadId] = nextOverride
                    },
            ),
        )
    }

    override suspend fun setServiceTier(
        threadId: String,
        serviceTier: RemodexServiceTier?,
    ) {
        val currentOverride = preferencesState.value.runtimeOverridesByThread[threadId]
        val nextOverride = (currentOverride ?: RemodexRuntimeOverrides()).copy(
            serviceTier = serviceTier,
            hasServiceTierOverride = true,
        )
        appPreferencesRepository.setRuntimeOverrides(
            threadId = threadId,
            overrides = nextOverride,
        )
        applyPreferencesLocally(
            preferencesState.value.copy(
                runtimeOverridesByThread = preferencesState.value.runtimeOverridesByThread
                    .toMutableMap()
                    .apply {
                        this[threadId] = nextOverride
                    },
            ),
        )
    }

    override suspend fun setAccessMode(
        threadId: String,
        accessMode: RemodexAccessMode,
    ) {
        val currentOverride = preferencesState.value.runtimeOverridesByThread[threadId]
        val nextOverride = (currentOverride ?: RemodexRuntimeOverrides()).copy(accessMode = accessMode)
        appPreferencesRepository.setRuntimeOverrides(
            threadId = threadId,
            overrides = nextOverride,
        )
        applyPreferencesLocally(
            preferencesState.value.copy(
                runtimeOverridesByThread = preferencesState.value.runtimeOverridesByThread
                    .toMutableMap()
                    .apply {
                        this[threadId] = nextOverride
                    },
            ),
        )
    }

    override suspend fun setDefaultModelId(modelId: String?) {
        setSelectedModelId(modelId)
    }

    override suspend fun setDefaultReasoningEffort(reasoningEffort: String?) {
        mutateRuntimeDefaults { defaults ->
            defaults.copy(reasoningEffort = reasoningEffort?.trim()?.takeIf(String::isNotEmpty))
        }
    }

    override suspend fun setDefaultAccessMode(accessMode: RemodexAccessMode) {
        mutateRuntimeDefaults { defaults ->
            defaults.copy(accessMode = accessMode)
        }
    }

    override suspend fun setDefaultServiceTier(serviceTier: RemodexServiceTier?) {
        mutateRuntimeDefaults { defaults ->
            defaults.copy(
                serviceTier = serviceTier,
                hasServiceTierPreference = true,
            )
        }
    }

    override suspend fun setAppearanceMode(mode: RemodexAppearanceMode) {
        val updatedPreferences = preferencesState.value.copy(appearanceMode = mode)
        appPreferencesRepository.setAppearanceMode(mode)
        applyPreferencesLocally(updatedPreferences)
    }

    override suspend fun setAppFontStyle(style: RemodexAppFontStyle) {
        val updatedPreferences = preferencesState.value.copy(appFontStyle = style)
        appPreferencesRepository.setAppFontStyle(style)
        applyPreferencesLocally(updatedPreferences)
    }

    override suspend fun setMacNickname(
        deviceId: String,
        nickname: String?,
    ) {
        val normalizedDeviceId = deviceId.trim()
        if (normalizedDeviceId.isEmpty()) {
            return
        }
        val updatedNicknames = preferencesState.value.macNicknamesByDeviceId.toMutableMap().apply {
            val trimmedNickname = nickname?.trim().orEmpty()
            if (trimmedNickname.isEmpty()) {
                remove(normalizedDeviceId)
            } else {
                this[normalizedDeviceId] = trimmedNickname
            }
        }
        val updatedPreferences = preferencesState.value.copy(macNicknamesByDeviceId = updatedNicknames)
        appPreferencesRepository.setMacNickname(normalizedDeviceId, nickname)
        applyPreferencesLocally(updatedPreferences)
    }

    override suspend fun refreshGptAccountState() {
        if (!hasActiveSecureTransport()) {
            gptAccountSnapshotState.value = disconnectedGptAccountSnapshot(gptAccountSnapshotState.value)
            gptAccountErrorMessageState.value = null
            bridgeVersionStatusState.value = RemodexBridgeVersionStatus()
            return
        }

        try {
            val response = fetchBridgeManagedAccountState()
            val payloadObject = response.result?.jsonObjectOrNull
                ?: throw IllegalStateException("Bridge account status response missing payload.")
            val nextSnapshot = decodeBridgeGptAccountSnapshot(
                payloadObject = payloadObject,
                previousSnapshot = gptAccountSnapshotState.value,
            )
            gptAccountSnapshotState.value = nextSnapshot
            bridgeVersionStatusState.value = decodeBridgeVersionStatus(payloadObject)
            if (nextSnapshot.isAuthenticated || nextSnapshot.status == RemodexGptAccountStatus.NOT_LOGGED_IN) {
                gptAccountErrorMessageState.value = null
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            if (gptAccountSnapshotState.value.status == RemodexGptAccountStatus.UNKNOWN) {
                gptAccountSnapshotState.value = disconnectedGptAccountSnapshot(gptAccountSnapshotState.value)
            }
            gptAccountErrorMessageState.value = error.message?.trim().takeUnless(String?::isNullOrEmpty)
                ?: "Unable to load ChatGPT account status."
            bridgeVersionStatusState.value = RemodexBridgeVersionStatus()
        }
    }

    override suspend fun logoutGptAccount() {
        if (!hasActiveSecureTransport()) {
            gptAccountSnapshotState.value = loggedOutGptAccountSnapshot(gptAccountSnapshotState.value)
            gptAccountErrorMessageState.value = null
            return
        }
        secureConnectionCoordinator.sendRequest(method = "account/logout", params = null)
        gptAccountSnapshotState.value = loggedOutGptAccountSnapshot(gptAccountSnapshotState.value)
        gptAccountErrorMessageState.value = null
        refreshGptAccountState()
    }

    override suspend fun transcribeVoiceAudioFile(
        file: File,
        durationSeconds: Double,
    ): String {
        return try {
            voiceTranscriptionService.transcribeVoiceAudioFile(
                file = file,
                durationSeconds = durationSeconds,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            runCatching { refreshGptAccountState() }
            throw error
        }
    }

    override suspend fun refreshUsageStatus(threadId: String?) {
        if (!hasActiveSecureTransport()) {
            usageStatusState.value = usageStatusState.value.copy(
                rateLimitBuckets = emptyList(),
                rateLimitsErrorMessage = "Connect to a Mac bridge to load usage.",
            )
            return
        }

        val normalizedThreadId = threadId?.trim().takeUnless { it.isNullOrEmpty() }
        val contextWindowUsage = normalizedThreadId?.let { resolvedThreadId ->
            runCatching { readContextWindowUsage(resolvedThreadId) }
                .getOrNull()
        }
        val rateLimitResponse = runCatching { fetchRateLimitsWithCompatRetry() }
        usageStatusState.value = if (rateLimitResponse.isSuccess) {
            val rateLimitsObject = rateLimitResponse.getOrThrow().result?.jsonObjectOrNull
                ?: JsonObject(emptyMap())
            RemodexUsageStatus(
                contextWindowUsage = contextWindowUsage,
                rateLimitBuckets = decodeRateLimitBuckets(rateLimitsObject),
                rateLimitsErrorMessage = null,
            )
        } else {
            RemodexUsageStatus(
                contextWindowUsage = contextWindowUsage,
                rateLimitBuckets = emptyList(),
                rateLimitsErrorMessage = rateLimitResponse.exceptionOrNull()?.message?.trim()
                    ?.takeUnless(String::isEmpty)
                    ?: "Unable to load rate limits",
            )
        }
    }

    override suspend fun fuzzyFileSearch(
        threadId: String,
        query: String,
    ): List<RemodexFuzzyFileMatch> {
        return threadCommandService.fuzzyFileSearch(threadId, query)
    }

    override suspend fun listSkills(
        threadId: String,
        forceReload: Boolean,
    ): List<RemodexSkillMetadata> {
        return threadCommandService.listSkills(threadId, forceReload)
    }

    override suspend fun startCodeReview(
        threadId: String,
        request: RemodexCodeReviewRequest,
    ) {
        var thread = sessionState.value.threads.firstOrNull { it.id == threadId } ?: return
        try {
            thread = resumeThreadBeforeSend(thread = thread)
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            if (!shouldTreatAsThreadNotFoundValue(error)) {
                throw error
            }
            val continuationThreadId = continueMissingThread(thread)
            val continuationThread = sessionState.value.threads.firstOrNull { candidate ->
                candidate.id == continuationThreadId
            } ?: thread
            resumeThreadBeforeSend(thread = continuationThread)
            threadCommandService.startCodeReview(
                threadId = continuationThreadId,
                request = request,
            )
            refreshBaseThreadsFromSync()
            return
        }
        try {
            threadCommandService.startCodeReview(
                threadId = threadId,
                request = request,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            if (!shouldTreatAsThreadNotFoundValue(error)) {
                throw error
            }
            val continuationThreadId = continueMissingThread(thread)
            val continuationThread = sessionState.value.threads.firstOrNull { candidate ->
                candidate.id == continuationThreadId
            } ?: thread
            resumeThreadBeforeSend(thread = continuationThread)
            threadCommandService.startCodeReview(
                threadId = continuationThreadId,
                request = request,
            )
        }
        refreshBaseThreadsFromSync()
    }

    override suspend fun forkThread(
        threadId: String,
        destination: RemodexComposerForkDestination,
        baseBranch: String?,
    ): String? {
        val forkedThread = threadCommandService.forkThread(
            threadId = threadId,
            destination = destination,
            baseBranch = baseBranch,
        ) ?: return null
        return selectForkedThread(forkedThread)
    }

    override suspend fun forkThreadIntoProjectPath(
        threadId: String,
        projectPath: String,
    ): String? {
        val sourceThread = sessionState.value.threads.firstOrNull { existing -> existing.id == threadId }
            ?: sessionState.value.selectedThread?.takeIf { selected -> selected.id == threadId }
        val resumeService = resumeService()
        if (sourceThread?.messages.isNullOrEmpty() && resumeService?.isThreadResumedLocally(threadId) == true) {
            val runtimeDefaults = effectiveRuntimeDefaultsForNewThread(threadId)
            val createdThread = threadCommandService.createThread(
                preferredProjectPath = projectPath,
                runtimeDefaults = runtimeDefaults,
            ) ?: return null
            inheritRuntimeOverride(sourceThreadId = threadId, destinationThreadId = createdThread.id)
            refreshBaseThreadsLocallyFromSnapshots(
                snapshots = listOf(createdThread),
                preferredSelectedThreadIdOverride = createdThread.id,
            )
            return createdThread.id
        }
        val forkedThread = threadCommandService.forkThreadIntoProjectPath(
            threadId = threadId,
            projectPath = projectPath,
        ) ?: return null
        return selectForkedThread(forkedThread)
    }

    private suspend fun selectForkedThread(
        forkedThread: ThreadSyncSnapshot,
    ): String {
        refreshBaseThreadsLocallyFromSnapshots(
            snapshots = listOf(forkedThread),
            preferredSelectedThreadIdOverride = forkedThread.id,
        )
        return forkedThread.id
    }

    override suspend fun loadGitState(threadId: String): RemodexGitState {
        return threadCommandService.loadGitState(threadId)
    }

    override suspend fun loadGitDiff(threadId: String): RemodexGitRepoDiff {
        return threadCommandService.loadGitDiff(threadId)
    }

    override suspend fun loadGitCommits(threadId: String): List<RemodexGitCommit> {
        return threadCommandService.loadGitCommits(threadId)
    }

    override suspend fun checkoutGitBranch(
        threadId: String,
        branch: String,
    ): RemodexGitState {
        return threadCommandService.checkoutGitBranch(threadId, branch)
    }

    override suspend fun createGitBranch(
        threadId: String,
        branch: String,
    ): RemodexGitState {
        return threadCommandService.createGitBranch(threadId, branch)
    }

    override suspend fun createGitWorktree(
        threadId: String,
        name: String,
        baseBranch: String?,
        changeTransfer: RemodexGitWorktreeChangeTransferMode,
    ): RemodexGitState {
        return threadCommandService.createGitWorktree(
            threadId = threadId,
            name = name,
            baseBranch = baseBranch,
            changeTransfer = changeTransfer,
        )
    }

    override suspend fun createGitWorktreeResult(
        threadId: String,
        name: String,
        baseBranch: String?,
        changeTransfer: RemodexGitWorktreeChangeTransferMode,
    ): RemodexGitWorktreeResult {
        return threadCommandService.createGitWorktreeResult(
            threadId = threadId,
            name = name,
            baseBranch = baseBranch,
            changeTransfer = changeTransfer,
        )
    }

    override suspend fun commitGitChanges(
        threadId: String,
        message: String?,
    ): RemodexGitState {
        return threadCommandService.commitGitChanges(threadId, message)
    }

    override suspend fun commitAndPushGitChanges(
        threadId: String,
        message: String?,
    ): RemodexGitState {
        return threadCommandService.commitAndPushGitChanges(threadId, message)
    }

    override suspend fun pullGitChanges(threadId: String): RemodexGitState {
        return threadCommandService.pullGitChanges(threadId)
    }

    override suspend fun pushGitChanges(threadId: String): RemodexGitState {
        return threadCommandService.pushGitChanges(threadId)
    }

    override suspend fun discardRuntimeChangesAndSync(threadId: String): RemodexGitState {
        return threadCommandService.discardRuntimeChangesAndSync(threadId)
    }

    override suspend fun moveThreadToProjectPath(
        threadId: String,
        projectPath: String,
    ) {
        val thread = sessionState.value.threads.firstOrNull { existing -> existing.id == threadId } ?: return
        val resumeService = resumeService() ?: return
        val normalizedProjectPath = projectPath.trim().takeIf(String::isNotEmpty) ?: return
        val previousProjectPath = thread.projectPath
        val locallyUpdatedThread = resumeService.updateThreadProjectPathLocally(
            threadId = threadId,
            projectPath = normalizedProjectPath,
        )
        if (locallyUpdatedThread != null) {
            refreshBaseThreadsLocallyFromSnapshots(listOf(locallyUpdatedThread))
        } else {
            refreshBaseThreadsFromSync()
        }

        // Brand-new local threads already carry the desired cwd for the first turn, but
        // the bridge cannot always resume them until a rollout exists.
        if (thread.messages.isEmpty() && resumeService.isThreadResumedLocally(threadId)) {
            return
        }

        try {
            resumeService.moveThreadToProjectPath(
                threadId = threadId,
                projectPath = normalizedProjectPath,
                modelIdentifier = thread.runtimeConfig.selectedModelId,
            )
        } catch (error: Throwable) {
            if (shouldRetryAfterThreadMaterializationValue(error)) {
                return
            }
            resumeService.updateThreadProjectPathLocally(
                threadId = threadId,
                projectPath = previousProjectPath,
            )
            refreshBaseThreadsFromSync()
            throw error
        }
        refreshBaseThreadsFromSync()
    }

    override suspend fun loadGitRemoteUrl(threadId: String): RemodexGitRemoteUrl {
        return threadCommandService.loadGitRemoteUrl(threadId)
    }

    override suspend fun previewAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertPreviewResult {
        return threadCommandService.previewAssistantRevert(
            threadId = threadId,
            forwardPatch = forwardPatch,
        )
    }

    override suspend fun applyAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertApplyResult {
        return threadCommandService.applyAssistantRevert(
            threadId = threadId,
            forwardPatch = forwardPatch,
        )
    }

    override suspend fun pairWithQrPayload(payload: PairingQrPayload) {
        secureConnectionCoordinator.rememberRelayPairing(payload)
        secureConnectionCoordinator.retryConnection()
    }

    override suspend fun dismissBridgeUpdatePrompt() {
        threadSyncService.dismissBridgeUpdatePrompt()
    }

    override suspend fun retryConnection() {
        secureConnectionCoordinator.retryConnection()
    }

    override suspend fun activateBridgeProfile(profileId: String): Boolean {
        suppressBridgeScopedThreadsUntilNextSync = true
        val didActivate = secureConnectionCoordinator.activateBridgeProfile(profileId)
        if (!didActivate) {
            suppressBridgeScopedThreadsUntilNextSync = false
            return false
        }
        clearThreadsForExplicitDisconnect()
        secureConnectionCoordinator.retryConnection()
        return true
    }

    override suspend fun removeBridgeProfile(profileId: String): String? {
        val nextProfileId = secureConnectionCoordinator.removeBridgeProfile(profileId)
        if (nextProfileId != null) {
            secureConnectionCoordinator.retryConnection()
        }
        return nextProfileId
    }

    override suspend fun disconnect() {
        clearThreadsForExplicitDisconnect()
        secureConnectionCoordinator.disconnect()
        resetBridgeScopedStatus()
    }

    override suspend fun forgetTrustedMac() {
        clearThreadsForExplicitDisconnect()
        secureConnectionCoordinator.forgetTrustedMac()
        resetBridgeScopedStatus()
    }

    private fun resetBridgeScopedStatus() {
        gptAccountSnapshotState.value = disconnectedGptAccountSnapshot(gptAccountSnapshotState.value)
        gptAccountErrorMessageState.value = null
        bridgeVersionStatusState.value = RemodexBridgeVersionStatus()
        usageStatusState.value = RemodexUsageStatus()
    }

    private suspend fun syncThreads(snapshots: List<ThreadSyncSnapshot>) {
        if (shouldPreserveThreadsDuringConnectionRecovery(snapshots)) {
            return
        }
        val projected = withContext(Dispatchers.Default) {
            synchronized(timelineProjectionCacheLock) {
                timelineProjectionCacheByThread.keys.retainAll(snapshots.map(ThreadSyncSnapshot::id).toSet())
            }
            val cachedThreads = snapshots
                .map { snapshot -> snapshot.toCachedThreadRecord(projectThreadTimelineItems(snapshot)) }
                .sortedByDescending(CachedThreadRecord::lastUpdatedEpochMs)
            SyncedThreadProjection(
                cachedThreads = cachedThreads,
                mergedBaseThreads = mergeBaseThreadsFromSync(cachedThreads.map(CachedThreadRecord::toBaseThreadSummary)),
            )
        }
        val shouldDeferThreadListUpdate = snapshots.any(ThreadSyncSnapshot::isRunning)
        refreshThreadsLocally(
            baseThreads = projected.mergedBaseThreads,
            deferThreadListUpdate = shouldDeferThreadListUpdate,
        )
        scheduleThreadCacheWrite(
            cachedThreads = projected.cachedThreads,
            debounce = snapshots.any(ThreadSyncSnapshot::isRunning),
        )
    }

    private suspend fun clearThreadsForExplicitDisconnect() {
        baseThreadsState.value = emptyList()
        preferredSelectedThreadId.set(null)
        cancelPendingThreadListPublish()
        preferencesState.value = preferencesState.value.copy(selectedThreadId = null)
        appPreferencesRepository.setSelectedThreadId(null)
        sessionState.update { snapshot ->
            snapshot.copy(
                availableModels = resolveAvailableModels(emptyList()),
                threads = emptyList(),
                selectedThreadId = null,
                selectedThreadSnapshot = null,
            )
        }
        syncActiveThreadHint(null)
    }

    private fun bridgeScopedBaseThreads(
        threads: List<RemodexThreadSummary>,
    ): List<RemodexThreadSummary> {
        return if (suppressBridgeScopedThreadsUntilNextSync) {
            emptyList()
        } else {
            threads
        }
    }

    private fun shouldPreserveThreadsDuringConnectionRecovery(
        snapshots: List<ThreadSyncSnapshot>,
    ): Boolean {
        if (snapshots.isNotEmpty() || baseThreadsState.value.isEmpty()) {
            return false
        }
        return secureConnectionCoordinator.state.value.secureState in setOf(
            SecureConnectionState.TRUSTED_MAC,
            SecureConnectionState.LIVE_SESSION_UNRESOLVED,
            SecureConnectionState.RECONNECTING,
            SecureConnectionState.HANDSHAKING,
        )
    }

    private fun projectThreadTimelineItems(snapshot: ThreadSyncSnapshot): List<com.emanueledipietro.remodex.model.RemodexConversationItem> {
        return synchronized(timelineProjectionCacheLock) {
            val cachedProjection = timelineProjectionCacheByThread[snapshot.id]
            if (cachedProjection != null) {
                if (snapshot.timelineMutations == cachedProjection.timelineMutations) {
                    return@synchronized cachedProjection.projectedItems
                }
                projectedTimelineItemsFastPath(
                    snapshot = snapshot,
                    cachedProjection = cachedProjection,
                )?.let { projectedItems ->
                    timelineProjectionCacheByThread[snapshot.id] = ThreadTimelineProjectionCache(
                        timelineMutations = snapshot.timelineMutations,
                        projectedItems = projectedItems,
                    )
                    return@synchronized projectedItems
                }
            }

            val projectedItems = TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
            timelineProjectionCacheByThread[snapshot.id] = ThreadTimelineProjectionCache(
                timelineMutations = snapshot.timelineMutations,
                projectedItems = projectedItems,
            )
            projectedItems
        }
    }

    private fun projectedTimelineItemsFastPath(
        snapshot: ThreadSyncSnapshot,
        cachedProjection: ThreadTimelineProjectionCache,
    ): List<com.emanueledipietro.remodex.model.RemodexConversationItem>? {
        if (snapshot.timelineMutations.size != cachedProjection.timelineMutations.size + 1) {
            return null
        }

        val prefixMutations = snapshot.timelineMutations.subList(0, cachedProjection.timelineMutations.size)
        if (prefixMutations != cachedProjection.timelineMutations) {
            return null
        }

        return TurnTimelineReducer.applyProjectedFastPath(
            items = cachedProjection.projectedItems,
            mutation = snapshot.timelineMutations.last(),
        )
    }

    private fun scheduleThreadCacheWrite(
        cachedThreads: List<CachedThreadRecord>,
        debounce: Boolean,
    ) {
        threadCacheWriteJob?.cancel()
        threadCacheWriteJob = repositoryScope.launch(Dispatchers.IO) {
            if (debounce) {
                delay(ThreadCacheStreamingWriteDebounceMs)
            }
            threadCacheStore.replaceThreads(cachedThreads)
        }
    }

    private suspend fun resumeThreadBeforeSend(
        thread: RemodexThreadSummary,
    ): RemodexThreadSummary {
        if (!hasActiveSecureTransport()) {
            return thread
        }
        val resumeService = resumeService() ?: return thread
        if (resumeService.isThreadResumedLocally(thread.id)) {
            return sessionState.value.threads.firstOrNull { candidate -> candidate.id == thread.id } ?: thread
        }
        try {
            resumeService.resumeThread(
                threadId = thread.id,
                preferredProjectPath = thread.projectPath.ifBlank { null },
                modelIdentifier = thread.runtimeConfig.selectedModelId,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            // Brand-new local threads can survive a reconnect before their first rollout
            // exists. Keep the send path moving so turn/start can materialize the thread.
            if (shouldRetryAfterThreadMaterializationValue(error)) {
                return sessionState.value.threads.firstOrNull { candidate -> candidate.id == thread.id } ?: thread
            }
            throw error
        }
        refreshBaseThreadsFromSync()
        return sessionState.value.threads.firstOrNull { candidate -> candidate.id == thread.id } ?: thread
    }

    private suspend fun continueMissingThread(thread: RemodexThreadSummary): String {
        val archivedThreads = baseThreadsState.value.map { existing ->
            if (existing.id == thread.id) {
                existing.copy(
                    syncState = RemodexThreadSyncState.ARCHIVED_LOCAL,
                    isRunning = false,
                )
            } else {
                existing
            }
        }
        refreshThreadsLocally(archivedThreads)

        val runtimeDefaults = effectiveRuntimeDefaultsForNewThread(thread.id)
        val createdThread = threadCommandService.createThread(
            preferredProjectPath = thread.projectPath.ifBlank { null },
            runtimeDefaults = runtimeDefaults,
        ) ?: throw IllegalStateException("Could not create a continuation thread.")
        inheritRuntimeOverride(sourceThreadId = thread.id, destinationThreadId = createdThread.id)
        localTimelineService()?.appendLocalSystemMessage(
            threadId = createdThread.id,
            text = "Continued from archived thread `${thread.id}`",
        )
        val continuationSnapshot = threadSyncService.threads.value.firstOrNull { snapshot ->
            snapshot.id == createdThread.id
        } ?: createdThread
        refreshBaseThreadsLocallyFromSnapshots(
            snapshots = listOf(continuationSnapshot),
            preferredSelectedThreadIdOverride = createdThread.id,
        )
        return createdThread.id
    }

    private suspend fun refreshBaseThreadsFromSync() {
        val mergedBaseThreads = mergeBaseThreadsFromSyncSnapshots(threadSyncService.threads.value)
        refreshThreadsLocally(mergedBaseThreads)
    }

    private fun refreshBaseThreadsLocallyFromSnapshots(
        snapshots: List<ThreadSyncSnapshot>,
        preferredSelectedThreadIdOverride: String? = null,
    ) {
        val mergedBaseThreads = mergeBaseThreadsFromSyncSnapshots(snapshots)
        refreshThreadsLocally(
            baseThreads = mergedBaseThreads,
            preferredSelectedThreadIdOverride = preferredSelectedThreadIdOverride,
        )
    }

    private fun mergeBaseThreadsFromSyncSnapshots(
        snapshots: List<ThreadSyncSnapshot>,
    ): List<RemodexThreadSummary> {
        return mergeBaseThreadsFromSync(
            snapshots
                .map { snapshot -> snapshot.toCachedThreadRecord(projectThreadTimelineItems(snapshot)) }
                .sortedByDescending(CachedThreadRecord::lastUpdatedEpochMs)
                .map(CachedThreadRecord::toBaseThreadSummary),
        )
    }

    private fun effectiveRuntimeDefaultsForNewThread(
        inheritRuntimeFromThreadId: String? = null,
    ): RemodexRuntimeDefaults {
        val storedDefaults = preferencesState.value.runtimeDefaults
        val availableModels = resolveAvailableModels(baseThreadsState.value)
        val inheritedRuntime = inheritRuntimeFromThreadId
            ?.let { sourceThreadId ->
                sessionState.value.threads.firstOrNull { thread -> thread.id == sourceThreadId }?.runtimeConfig
            }
        val effectiveRuntime = inheritedRuntime
            ?.withAvailableModels(availableModels)
            ?.normalizeSelections()
            ?: RemodexRuntimeConfig(
                availableModels = availableModels,
            ).applyDefaults(storedDefaults)
        return storedDefaults.copy(
            modelId = effectiveRuntime.selectedModelId,
            reasoningEffort = effectiveRuntime.reasoningEffort,
            accessMode = effectiveRuntime.accessMode,
            serviceTier = if (inheritedRuntime != null || storedDefaults.hasServiceTierPreference) {
                effectiveRuntime.serviceTier
            } else {
                null
            },
            hasServiceTierPreference = inheritedRuntime != null || storedDefaults.hasServiceTierPreference,
        )
    }

    private fun publishOptimisticPendingUserMessage(
        threadId: String,
        prompt: String,
        attachments: List<RemodexComposerAttachment>,
        runtimeConfig: RemodexRuntimeConfig,
    ): LocalOptimisticUserMessage? {
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isEmpty()) {
            return null
        }
        val messageText = optimisticPendingUserMessageText(
            prompt = prompt,
            attachments = attachments,
        )
        if (messageText.isEmpty()) {
            return null
        }
        val now = System.currentTimeMillis()
        val messageId = "user-local-${UUID.randomUUID()}"
        var didUpdateThread = false
        val updatedThreads = baseThreadsState.value.map { currentThread ->
            if (currentThread.id != normalizedThreadId) {
                currentThread
            } else {
                didUpdateThread = true
                currentThread.copy(
                    preview = messageText,
                    lastUpdatedLabel = "Updated just now",
                    isRunning = true,
                    runtimeConfig = runtimeConfig,
                    messages = (currentThread.messages + RemodexConversationItem(
                        id = messageId,
                        speaker = com.emanueledipietro.remodex.model.ConversationSpeaker.USER,
                        text = messageText,
                        deliveryState = RemodexMessageDeliveryState.PENDING,
                        createdAtEpochMs = now,
                        attachments = attachments.map { attachment -> attachment.toConversationAttachment() },
                        orderIndex = nextLocalOrderIndex(currentThread),
                    )).sortedBy(RemodexConversationItem::orderIndex),
                )
            }
        }
        if (!didUpdateThread) {
            return null
        }
        refreshThreadsLocally(
            baseThreads = updatedThreads,
        )
        return LocalOptimisticUserMessage(
            threadId = normalizedThreadId,
            messageId = messageId,
        )
    }

    private suspend fun sendPromptWithLocalOptimistic(
        threadId: String,
        prompt: String,
        runtimeConfig: RemodexRuntimeConfig,
        attachments: List<RemodexComposerAttachment>,
    ) {
        val optimisticMessage = publishOptimisticPendingUserMessage(
            threadId = threadId,
            prompt = prompt,
            attachments = attachments,
            runtimeConfig = runtimeConfig,
        )
        try {
            threadCommandService.sendPrompt(
                threadId = threadId,
                prompt = prompt,
                runtimeConfig = runtimeConfig,
                attachments = attachments,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            if (shouldTreatAsThreadNotFoundValue(error)) {
                optimisticMessage?.let { removeLocalOptimisticUserMessage(it) }
            } else {
                optimisticMessage?.let { markLocalOptimisticUserMessageFailed(it) }
            }
            throw error
        }
    }

    private fun applyPlanningModeOverride(
        runtimeConfig: RemodexRuntimeConfig,
        planningModeOverride: RemodexPlanningMode?,
    ): RemodexRuntimeConfig {
        return planningModeOverride?.let { runtimeConfig.copy(planningMode = it) } ?: runtimeConfig
    }

    private fun markLocalOptimisticUserMessageFailed(
        optimisticMessage: LocalOptimisticUserMessage,
    ) {
        updateLocalOptimisticUserMessage(
            optimisticMessage = optimisticMessage,
        ) { currentMessage ->
            currentMessage.copy(deliveryState = RemodexMessageDeliveryState.FAILED)
        }
    }

    private fun removeLocalOptimisticUserMessage(
        optimisticMessage: LocalOptimisticUserMessage,
    ) {
        val updatedThreads = baseThreadsState.value.map { currentThread ->
            if (currentThread.id != optimisticMessage.threadId) {
                currentThread
            } else {
                currentThread.copy(
                    messages = currentThread.messages.filterNot { message ->
                        message.id == optimisticMessage.messageId
                    },
                )
            }
        }
        refreshThreadsLocally(
            baseThreads = updatedThreads,
        )
    }

    private fun updateLocalOptimisticUserMessage(
        optimisticMessage: LocalOptimisticUserMessage,
        transform: (RemodexConversationItem) -> RemodexConversationItem,
    ) {
        val updatedThreads = baseThreadsState.value.map { currentThread ->
            if (currentThread.id != optimisticMessage.threadId) {
                currentThread
            } else {
                currentThread.copy(
                    messages = currentThread.messages.map { currentMessage ->
                        if (currentMessage.id == optimisticMessage.messageId) {
                            transform(currentMessage)
                        } else {
                            currentMessage
                        }
                    },
                )
            }
        }
        refreshThreadsLocally(
            baseThreads = updatedThreads,
        )
    }

    private fun applyPreferencesLocally(preferences: AppPreferences) {
        preferencesState.value = preferences
        val resolvedAvailableModels = resolveAvailableModels(baseThreadsState.value)
        cancelPendingThreadListPublish()
        publishMaterializedThreads(
            baseThreads = baseThreadsState.value,
            preferences = preferences,
            availableModels = resolvedAvailableModels,
            secureConnection = sessionState.value.secureConnection,
            notificationRegistration = sessionState.value.notificationRegistration,
        )
    }

    private fun refreshThreadsLocally(
        baseThreads: List<RemodexThreadSummary>,
        deferThreadListUpdate: Boolean = false,
        preferredSelectedThreadIdOverride: String? = null,
    ) {
        val publishGeneration = nextThreadListPublishGeneration()
        baseThreadsState.value = baseThreads
        val resolvedAvailableModels = resolveAvailableModels(baseThreads)
        val currentSessionSnapshot = sessionState.value
        val previousSessionSelectedThreadId = currentSessionSnapshot.selectedThreadId
        val inMemorySelectedThreadId = preferredSelectedThreadId.get()
        val selectedThreadId = if (suppressBridgeScopedThreadsUntilNextSync) {
            null
        } else {
            resolveSelectedThreadId(
                preferredThreadId = preferredSelectedThreadIdOverride ?: preferredSelectedThreadId(
                    inMemorySelectedThreadId = inMemorySelectedThreadId,
                    persistedSelectedThreadId = preferencesState.value.selectedThreadId,
                    sessionSelectedThreadId = currentSessionSnapshot.selectedThreadId,
                ),
                stickyThreadId = currentSessionSnapshot.selectedThreadId,
                stickyThreadSnapshot = currentSessionSnapshot.selectedThreadSnapshot,
                threads = baseThreads,
            )
        }
        preferredSelectedThreadId.set(selectedThreadId)
        if (selectedThreadId != preferencesState.value.selectedThreadId) {
            preferencesState.value = preferencesState.value.copy(selectedThreadId = selectedThreadId)
            repositoryScope.launch {
                appPreferencesRepository.setSelectedThreadId(selectedThreadId)
            }
        }
        publishSelectedThreadSnapshot(
            baseThreads = baseThreads,
            preferences = preferencesState.value,
            availableModels = resolvedAvailableModels,
            selectedThreadId = selectedThreadId,
        )
        if (deferThreadListUpdate) {
            scheduleThreadListPublish(
                baseThreads = baseThreads,
                preferences = preferencesState.value,
                availableModels = resolvedAvailableModels,
                secureConnection = sessionState.value.secureConnection,
                notificationRegistration = sessionState.value.notificationRegistration,
                publishGeneration = publishGeneration,
            )
        } else {
            cancelPendingThreadListPublish()
            publishMaterializedThreads(
                baseThreads = baseThreads,
                preferences = preferencesState.value,
                availableModels = resolvedAvailableModels,
                secureConnection = sessionState.value.secureConnection,
                notificationRegistration = sessionState.value.notificationRegistration,
            )
        }
    }

    private fun publishSelectedThreadSnapshot(
        baseThreads: List<RemodexThreadSummary>,
        preferences: AppPreferences,
        availableModels: List<RemodexModelOption>,
        selectedThreadId: String?,
    ) {
        val currentSelectedThreadSnapshot = sessionState.value.selectedThreadSnapshot
        val selectedThread = baseThreads
            .firstOrNull { thread -> thread.id == selectedThreadId }
            ?.materialize(
                preferences = preferences,
                availableModels = availableModels,
            )
            ?: currentSelectedThreadSnapshot?.takeIf { snapshot ->
                snapshot.id == selectedThreadId
            }
            ?: baseThreads.firstOrNull()?.materialize(
                preferences = preferences,
                availableModels = availableModels,
            )
        sessionState.update { snapshot ->
            snapshot.copy(
                availableModels = availableModels,
                selectedThreadId = selectedThreadId,
                selectedThreadSnapshot = selectedThread,
                trustedMac = snapshot.secureConnection.toTrustedMacPresentation(preferences.macNicknamesByDeviceId),
                bridgeProfiles = secureConnectionCoordinator.bridgeProfiles.value.toBridgeProfilePresentations(
                    nicknamesByDeviceId = preferences.macNicknamesByDeviceId,
                    connectedProfileId = snapshot.secureConnection.activeProfileId.takeIf {
                        snapshot.secureConnection.secureState == SecureConnectionState.ENCRYPTED
                    },
                ),
            )
        }
        logSelectedThreadState(
            source = "publishSelectedThreadSnapshot",
            previous = currentSelectedThreadSnapshot,
            next = selectedThread,
        )
        syncActiveThreadHint(selectedThreadId)
    }

    private fun publishMaterializedThreads(
        baseThreads: List<RemodexThreadSummary>,
        preferences: AppPreferences,
        availableModels: List<RemodexModelOption>,
        secureConnection: SecureConnectionSnapshot,
        notificationRegistration: RemodexNotificationRegistrationState,
    ) {
        val threads = materializeThreads(
            baseThreads = baseThreads,
            preferences = preferences,
            availableModels = availableModels,
        )
        val currentSessionSnapshot = sessionState.value
        val previousSessionSelectedThreadId = currentSessionSnapshot.selectedThreadId
        val previousSelectedThreadSnapshot = currentSessionSnapshot.selectedThreadSnapshot
        val inMemorySelectedThreadId = preferredSelectedThreadId.get()
        val selectedThreadId = if (suppressBridgeScopedThreadsUntilNextSync) {
            null
        } else {
            resolveSelectedThreadId(
                preferredThreadId = preferredSelectedThreadId(
                    inMemorySelectedThreadId = inMemorySelectedThreadId,
                    persistedSelectedThreadId = preferences.selectedThreadId,
                    sessionSelectedThreadId = currentSessionSnapshot.selectedThreadId,
                ),
                stickyThreadId = currentSessionSnapshot.selectedThreadId,
                stickyThreadSnapshot = currentSessionSnapshot.selectedThreadSnapshot,
                threads = threads,
            )
        }
        preferredSelectedThreadId.set(selectedThreadId)
        val nextSelectedThreadSnapshot = resolveSelectedThreadSnapshot(
            selectedThreadId = selectedThreadId,
            threads = threads,
            currentSelectedThreadSnapshot = currentSessionSnapshot.selectedThreadSnapshot,
        )
        sessionState.update { snapshot ->
            snapshot.copy(
                onboardingCompleted = preferences.onboardingCompleted,
                connectionStatus = secureConnection.toConnectionStatus(),
                secureConnection = secureConnection,
                collapsedProjectGroupIds = preferences.collapsedProjectGroupIds,
                runtimeDefaults = preferences.runtimeDefaults,
                availableModels = availableModels,
                appearanceMode = preferences.appearanceMode,
                appFontStyle = preferences.appFontStyle,
                trustedMac = secureConnection.toTrustedMacPresentation(preferences.macNicknamesByDeviceId),
                bridgeProfiles = secureConnectionCoordinator.bridgeProfiles.value.toBridgeProfilePresentations(
                    nicknamesByDeviceId = preferences.macNicknamesByDeviceId,
                    connectedProfileId = secureConnection.activeProfileId.takeIf {
                        secureConnection.secureState == SecureConnectionState.ENCRYPTED
                    },
                ),
                threads = threads,
                selectedThreadId = selectedThreadId,
                selectedThreadSnapshot = nextSelectedThreadSnapshot,
                notificationRegistration = notificationRegistration,
            )
        }
        logSelectedThreadState(
            source = "publishMaterializedThreads",
            previous = previousSelectedThreadSnapshot,
            next = nextSelectedThreadSnapshot,
        )
        syncActiveThreadHint(selectedThreadId)
    }

    private fun scheduleThreadListPublish(
        baseThreads: List<RemodexThreadSummary>,
        preferences: AppPreferences,
        availableModels: List<RemodexModelOption>,
        secureConnection: SecureConnectionSnapshot,
        notificationRegistration: RemodexNotificationRegistrationState,
        publishGeneration: Long,
    ) {
        threadListPublishJob?.cancel()
        threadListPublishJob = repositoryScope.launch {
            delay(SelectedThreadStreamingThreadListDebounceMs)
            val currentGeneration = threadListPublishGeneration.get()
            if (publishGeneration != currentGeneration) {
                return@launch
            }
            publishMaterializedThreads(
                baseThreads = baseThreads,
                preferences = preferences,
                availableModels = availableModels,
                secureConnection = secureConnection,
                notificationRegistration = notificationRegistration,
            )
        }
    }

    private fun cancelPendingThreadListPublish() {
        threadListPublishJob?.cancel()
        threadListPublishJob = null
    }

    private fun nextThreadListPublishGeneration(): Long {
        return threadListPublishGeneration.incrementAndGet()
    }

    private suspend fun mutateRuntimeDefaults(
        transform: (RemodexRuntimeDefaults) -> RemodexRuntimeDefaults,
    ) {
        val updatedDefaults = transform(preferencesState.value.runtimeDefaults)
        val updatedPreferences = preferencesState.value.copy(runtimeDefaults = updatedDefaults)
        appPreferencesRepository.setRuntimeDefaults(updatedDefaults)
        applyPreferencesLocally(updatedPreferences)
    }

    private fun resolveAvailableModels(baseThreads: List<RemodexThreadSummary>): List<RemodexModelOption> {
        return threadSyncService.availableModels.value.takeIf(List<RemodexModelOption>::isNotEmpty)
            ?: baseThreads.firstNotNullOfOrNull { thread ->
                thread.runtimeConfig.availableModels.takeIf(List<RemodexModelOption>::isNotEmpty)
            }
            ?: sessionState.value.availableModels
    }

    private fun mergeBaseThreadsFromSync(
        syncedThreads: List<RemodexThreadSummary>,
    ): List<RemodexThreadSummary> {
        val deletedThreadIds = preferencesState.value.deletedThreadIds
        val localById = baseThreadsState.value.associateBy(RemodexThreadSummary::id)
        val mergedById = linkedMapOf<String, RemodexThreadSummary>()
        syncedThreads.forEach { syncedThread ->
            if (syncedThread.id in deletedThreadIds) {
                return@forEach
            }
            val localThread = localById[syncedThread.id]
            mergedById[syncedThread.id] = if (localThread != null) {
                syncedThread.copy(
                    syncState = if (localThread.syncState == RemodexThreadSyncState.ARCHIVED_LOCAL) {
                        RemodexThreadSyncState.ARCHIVED_LOCAL
                    } else {
                        syncedThread.syncState
                    },
                )
            } else {
                syncedThread
            }
        }
        baseThreadsState.value.forEach { localThread ->
            if (localThread.id !in deletedThreadIds && mergedById[localThread.id] == null) {
                mergedById[localThread.id] = localThread
            }
        }
        return mergedById.values.toList()
    }

    private fun logRecovery(message: String) {
        runCatching { Log.d(logTag, message) }
    }

    private fun logSelectedThreadState(
        source: String,
        previous: RemodexThreadSummary?,
        next: RemodexThreadSummary?,
    ) {
        if (
            previous?.id == next?.id &&
            previous?.isRunning == next?.isRunning &&
            previous?.isWaitingOnApproval == next?.isWaitingOnApproval &&
            previous?.activeTurnId == next?.activeTurnId &&
            previous?.latestTurnTerminalState == next?.latestTurnTerminalState &&
            previous?.messages?.count(RemodexConversationItem::isStreaming) ==
                next?.messages?.count(RemodexConversationItem::isStreaming)
        ) {
            return
        }
        logRecovery(
            "event=selectedThreadState source=$source previous=${threadSummary(previous)} next=${threadSummary(next)}",
        )
    }

    private fun threadSummary(thread: RemodexThreadSummary?): String {
        return if (thread == null) {
            "null"
        } else {
            "id=${thread.id} isRunning=${thread.isRunning} waiting=${thread.isWaitingOnApproval} " +
                "activeTurnId=${thread.activeTurnId.orEmpty()} terminal=${thread.latestTurnTerminalState?.name ?: "null"} " +
                "messages=${thread.messages.size} streaming=${thread.messages.count(RemodexConversationItem::isStreaming)}"
        }
    }

    private fun threadSummary(snapshot: ThreadSyncSnapshot?): String {
        return if (snapshot == null) {
            "null"
        } else {
            "id=${snapshot.id} isRunning=${snapshot.isRunning} waiting=${snapshot.isWaitingOnApproval} " +
                "activeTurnId=${snapshot.activeTurnId.orEmpty()} terminal=${snapshot.latestTurnTerminalState?.name ?: "null"} " +
                "timelineMutations=${snapshot.timelineMutations.size}"
        }
    }

    private fun recoveredThreadSummary(context: RecoveredThreadContext?): String {
        return if (context == null) {
            "null"
        } else {
            "threadId=${context.threadId} isRunning=${context.isRunning} waiting=${context.isWaitingOnApproval} " +
                "projectPath=${context.projectPath.orEmpty()} modelIdentifier=${context.modelIdentifier.orEmpty()}"
        }
    }

    companion object {
        private const val logTag = "RemodexAppRepo"
    }
}

private data class ThreadTimelineProjectionCache(
    val timelineMutations: List<TimelineMutation>,
    val projectedItems: List<com.emanueledipietro.remodex.model.RemodexConversationItem>,
)

private data class SyncedThreadProjection(
    val cachedThreads: List<CachedThreadRecord>,
    val mergedBaseThreads: List<RemodexThreadSummary>,
)

private fun optimisticPendingUserMessageText(
    prompt: String,
    attachments: List<RemodexComposerAttachment>,
): String {
    return androidUserMessageText(
        prompt = prompt,
        attachmentCount = attachments.size,
    )
}

private fun nextLocalOrderIndex(thread: RemodexThreadSummary): Long {
    return thread.messages.maxOfOrNull(RemodexConversationItem::orderIndex)?.plus(1L) ?: 0L
}

private const val ThreadCacheStreamingWriteDebounceMs = 120L
private const val SelectedThreadStreamingThreadListDebounceMs = 180L

private fun ThreadSyncSnapshot.toCachedThreadRecord(
    timelineItems: List<com.emanueledipietro.remodex.model.RemodexConversationItem>,
): CachedThreadRecord {
    return CachedThreadRecord(
        id = id,
        title = title,
        name = name,
        preview = preview,
        projectPath = projectPath,
        lastUpdatedLabel = lastUpdatedLabel,
        lastUpdatedEpochMs = lastUpdatedEpochMs,
        isRunning = isRunning,
        isWaitingOnApproval = isWaitingOnApproval,
        syncState = syncState,
        parentThreadId = parentThreadId,
        agentNickname = agentNickname,
        agentRole = agentRole,
        activeTurnId = activeTurnId,
        latestTurnTerminalState = latestTurnTerminalState,
        stoppedTurnIds = stoppedTurnIds,
        runtimeConfig = runtimeConfig,
        timelineItems = timelineItems,
    )
}

private data class SessionInputs(
    val preferences: AppPreferences,
    val secureConnection: SecureConnectionSnapshot,
    val bridgeProfiles: BridgeProfilesSnapshot,
    val notificationRegistration: RemodexNotificationRegistrationState,
    val availableModels: List<RemodexModelOption>,
    val bridgeUpdatePrompt: com.emanueledipietro.remodex.model.RemodexBridgeUpdatePrompt?,
    val supportsThreadFork: Boolean,
    val pendingApprovalRequest: com.emanueledipietro.remodex.model.RemodexApprovalRequest?,
)

private fun resolveSelectedThreadId(
    preferredThreadId: String?,
    stickyThreadId: String?,
    stickyThreadSnapshot: RemodexThreadSummary?,
    threads: List<RemodexThreadSummary>,
): String? {
    if (preferredThreadId != null && threads.any { it.id == preferredThreadId }) {
        return preferredThreadId
    }
    if (preferredThreadId != null &&
        (preferredThreadId == stickyThreadId || stickyThreadSnapshot?.id == preferredThreadId)
    ) {
        return preferredThreadId
    }
    return threads.firstOrNull()?.id
}

private fun resolveSelectedThreadSnapshot(
    selectedThreadId: String?,
    threads: List<RemodexThreadSummary>,
    currentSelectedThreadSnapshot: RemodexThreadSummary?,
): RemodexThreadSummary? {
    if (selectedThreadId != null) {
        threads.firstOrNull { thread -> thread.id == selectedThreadId }?.let { return it }
        currentSelectedThreadSnapshot?.takeIf { snapshot ->
            snapshot.id == selectedThreadId
        }?.let { return it }
    }
    return threads.firstOrNull()
}

private fun preferredSelectedThreadId(
    inMemorySelectedThreadId: String?,
    persistedSelectedThreadId: String?,
    sessionSelectedThreadId: String?,
): String? {
    return inMemorySelectedThreadId ?: sessionSelectedThreadId ?: persistedSelectedThreadId
}

private fun materializeThreads(
    baseThreads: List<RemodexThreadSummary>,
    preferences: AppPreferences,
    availableModels: List<RemodexModelOption>,
): List<RemodexThreadSummary> {
    return baseThreads
        .filterNot { thread -> thread.id in preferences.deletedThreadIds }
        .map { thread ->
            thread.materialize(
                preferences = preferences,
                availableModels = availableModels,
            )
        }
}

private fun RemodexThreadSummary.materialize(
    preferences: AppPreferences,
    availableModels: List<RemodexModelOption>,
): RemodexThreadSummary {
    val queuedDraftItems = preferences.queuedDraftsByThread[id].orEmpty()
    val effectiveRuntime = runtimeConfig
        .withAvailableModels(availableModels)
        .applyDefaults(preferences.runtimeDefaults)
        .applyThreadOverrides(preferences.runtimeOverridesByThread[id])
    return copy(
        queuedDrafts = queuedDraftItems.size,
        queuedDraftItems = queuedDraftItems,
        runtimeConfig = effectiveRuntime,
        runtimeLabel = effectiveRuntime.runtimeLabel,
    )
}

internal fun CachedThreadRecord.toBaseThreadSummary(): RemodexThreadSummary {
    return RemodexThreadSummary(
        id = id,
        title = title,
        name = name,
        preview = preview,
        projectPath = projectPath,
        lastUpdatedLabel = lastUpdatedLabel,
        isRunning = isRunning,
        isWaitingOnApproval = isWaitingOnApproval,
        syncState = syncState,
        parentThreadId = parentThreadId,
        agentNickname = agentNickname,
        agentRole = agentRole,
        activeTurnId = activeTurnId,
        latestTurnTerminalState = latestTurnTerminalState,
        stoppedTurnIds = stoppedTurnIds,
        queuedDrafts = 0,
        queuedDraftItems = emptyList(),
        runtimeLabel = runtimeConfig.runtimeLabel,
        runtimeConfig = runtimeConfig,
        messages = timelineItems,
    )
}

private fun SecureConnectionSnapshot.toConnectionStatus(): RemodexConnectionStatus {
    val phase = when (secureState) {
        SecureConnectionState.ENCRYPTED -> RemodexConnectionPhase.CONNECTED
        SecureConnectionState.RECONNECTING -> RemodexConnectionPhase.RETRYING
        SecureConnectionState.HANDSHAKING -> {
            if (attempt > 0) {
                RemodexConnectionPhase.CONNECTING
            } else {
                RemodexConnectionPhase.DISCONNECTED
            }
        }

        else -> RemodexConnectionPhase.DISCONNECTED
    }

    return RemodexConnectionStatus(
        phase = phase,
        attempt = attempt,
    )
}

private fun SecureConnectionSnapshot.toTrustedMacPresentation(
    nicknamesByDeviceId: Map<String, String> = emptyMap(),
): RemodexTrustedMacPresentation? {
    val fingerprint = macFingerprint?.trim().takeUnless { it.isNullOrEmpty() }
    val systemName = macDisplayName?.trim().takeUnless { it.isNullOrEmpty() }
    val title = when (secureState) {
        SecureConnectionState.ENCRYPTED -> "Connected Pair"
        SecureConnectionState.HANDSHAKING -> "Pairing Mac"
        SecureConnectionState.LIVE_SESSION_UNRESOLVED,
        SecureConnectionState.RECONNECTING,
        SecureConnectionState.TRUSTED_MAC -> "Saved Pair"
        SecureConnectionState.REPAIR_REQUIRED -> "Previous Pair"
        SecureConnectionState.UPDATE_REQUIRED,
        SecureConnectionState.NOT_PAIRED -> "Trusted Pair"
    }
    val fallbackName = listOfNotNull(systemName, fingerprint?.let { "Mac $it" })
        .firstOrNull()
        ?: return null
    val nickname = macDeviceId
        ?.let(nicknamesByDeviceId::get)
        ?.trim()
        .takeUnless { it.isNullOrEmpty() }
    val displayName = nickname ?: fallbackName
    val detail = buildList {
        add(secureState.statusLabel)
        fingerprint?.let(::add)
    }.joinToString(separator = " · ")
    return RemodexTrustedMacPresentation(
        deviceId = macDeviceId,
        title = title,
        name = displayName,
        systemName = if (nickname != null) fallbackName else null,
        detail = detail.ifBlank { null },
    )
}

private fun BridgeProfilesSnapshot.toBridgeProfilePresentations(
    nicknamesByDeviceId: Map<String, String> = emptyMap(),
    connectedProfileId: String? = null,
): List<RemodexBridgeProfilePresentation> {
    return profiles.map { profile ->
        val fingerprint = profile.macFingerprint.trim().ifEmpty { "Unknown" }
        val fallbackName = profile.macDisplayName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "Mac $fingerprint"
        val nickname = nicknamesByDeviceId[profile.macDeviceId]
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
        val title = when {
            profile.profileId == connectedProfileId -> "Connected Pair"
            profile.needsQrBootstrap -> "Pairing Mac"
            profile.isActive -> "Saved Pair"
            profile.isTrusted -> "Saved Pair"
            else -> "Previous Pair"
        }
        val detail = buildList {
            add(
                when {
                    profile.profileId == connectedProfileId -> "End-to-end encrypted"
                    profile.needsQrBootstrap -> "Secure handshake pending"
                    profile.isTrusted -> "Trusted Mac"
                    else -> "Saved locally"
                },
            )
            add(fingerprint)
        }.joinToString(separator = " · ")
        RemodexBridgeProfilePresentation(
            profileId = profile.profileId,
            title = title,
            name = nickname ?: fallbackName,
            systemName = if (nickname != null) fallbackName else null,
            detail = detail,
            isActive = profile.isActive,
            isConnected = profile.profileId == connectedProfileId,
        )
    }
}

private fun collectSubtreeThreadIds(
    threads: List<RemodexThreadSummary>,
    rootThreadId: String,
): Set<String> {
    val childrenByParentId = threads
        .filter { thread -> !thread.parentThreadId.isNullOrBlank() }
        .groupBy { thread -> thread.parentThreadId.orEmpty() }
    val collectedIds = linkedSetOf<String>()
    fun collect(threadId: String) {
        if (!collectedIds.add(threadId)) {
            return
        }
        childrenByParentId[threadId].orEmpty().forEach { child ->
            collect(child.id)
        }
    }
    collect(rootThreadId)
    return collectedIds
}

private fun collectDescendantThreadIds(
    threads: List<RemodexThreadSummary>,
    rootThreadId: String,
): Set<String> = collectSubtreeThreadIds(threads, rootThreadId) - rootThreadId
