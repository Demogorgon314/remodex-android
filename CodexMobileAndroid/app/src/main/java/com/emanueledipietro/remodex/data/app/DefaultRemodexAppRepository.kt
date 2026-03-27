package com.emanueledipietro.remodex.data.app

import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.jsonObjectOrNull
import com.emanueledipietro.remodex.data.connection.statusLabel
import com.emanueledipietro.remodex.data.preferences.AppPreferences
import com.emanueledipietro.remodex.data.preferences.AppPreferencesRepository
import com.emanueledipietro.remodex.data.threads.CachedThreadRecord
import com.emanueledipietro.remodex.data.threads.ThreadCommandService
import com.emanueledipietro.remodex.data.threads.ThreadCacheStore
import com.emanueledipietro.remodex.data.threads.ThreadHydrationService
import com.emanueledipietro.remodex.data.threads.ThreadLocalTimelineService
import com.emanueledipietro.remodex.data.threads.ThreadResumeService
import com.emanueledipietro.remodex.data.threads.ThreadSyncService
import com.emanueledipietro.remodex.data.threads.ThreadSyncSnapshot
import com.emanueledipietro.remodex.data.threads.shouldTreatAsThreadNotFoundValue
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexBridgeVersionStatus
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanningMode
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
import com.emanueledipietro.remodex.model.remodexInitialGptAccountSnapshot
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

class DefaultRemodexAppRepository(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val secureConnectionCoordinator: SecureConnectionCoordinator,
    private val threadCacheStore: ThreadCacheStore,
    private val threadSyncService: ThreadSyncService,
    private val threadCommandService: ThreadCommandService,
    private val threadHydrationService: ThreadHydrationService? = null,
    private val managedPushRegistrationState: Flow<RemodexNotificationRegistrationState> = flowOf(
        RemodexNotificationRegistrationState(),
    ),
    scope: CoroutineScope,
) : RemodexAppRepository {
    private val repositoryScope = scope
    private val timelineProjectionCacheLock = Any()
    private val timelineProjectionCacheByThread = mutableMapOf<String, ThreadTimelineProjectionCache>()
    private var threadCacheWriteJob: Job? = null
    private var threadListPublishJob: Job? = null
    private val initialBaseThreads = threadSyncService.threads.value
        .map { snapshot -> snapshot.toCachedThreadRecord(projectThreadTimelineItems(snapshot)) }
        .sortedByDescending(CachedThreadRecord::lastUpdatedEpochMs)
        .map(CachedThreadRecord::toBaseThreadSummary)
    private val baseThreadsState = MutableStateFlow(initialBaseThreads)
    private val preferencesState = MutableStateFlow(AppPreferences())

    private val sessionState = MutableStateFlow(
        RemodexSessionSnapshot(
            onboardingCompleted = false,
            connectionStatus = secureConnectionCoordinator.state.value.toConnectionStatus(),
            secureConnection = secureConnectionCoordinator.state.value,
            trustedMac = secureConnectionCoordinator.state.value.toTrustedMacPresentation(),
            availableModels = threadSyncService.availableModels.value,
            threads = initialBaseThreads,
            selectedThreadId = initialBaseThreads.firstOrNull()?.id,
            selectedThreadSnapshot = initialBaseThreads.firstOrNull(),
        ),
    )
    private val gptAccountSnapshotState = MutableStateFlow(remodexInitialGptAccountSnapshot())
    private val gptAccountErrorMessageState = MutableStateFlow<String?>(null)
    private val bridgeVersionStatusState = MutableStateFlow(RemodexBridgeVersionStatus())
    private val usageStatusState = MutableStateFlow(RemodexUsageStatus())

    override val session: StateFlow<RemodexSessionSnapshot> = sessionState
    override val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>> =
        threadSyncService.commandExecutionDetails
    override val gptAccountSnapshot: StateFlow<RemodexGptAccountSnapshot> = gptAccountSnapshotState
    override val gptAccountErrorMessage: StateFlow<String?> = gptAccountErrorMessageState
    override val bridgeVersionStatus: StateFlow<RemodexBridgeVersionStatus> = bridgeVersionStatusState
    override val usageStatus: StateFlow<RemodexUsageStatus> = usageStatusState

    init {
        repositoryScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            // Streaming assistant deltas should update the visible bubble continuously.
            // Using collectLatest here lets newer thread snapshots cancel in-flight work,
            // which makes Android feel chunkier than iOS under heavy streaming.
            threadSyncService.threads.collect { snapshots ->
                syncThreads(snapshots)
            }
        }

        repositoryScope.launch(start = CoroutineStart.UNDISPATCHED) {
            threadCacheStore.threads.collectLatest { cachedThreads ->
                if (threadSyncService.threads.value.isNotEmpty()) {
                    return@collectLatest
                }
                refreshThreadsLocally(cachedThreads.map(CachedThreadRecord::toBaseThreadSummary))
            }
        }

        repositoryScope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                appPreferencesRepository.preferences,
                secureConnectionCoordinator.state,
                managedPushRegistrationState,
                threadSyncService.availableModels,
            ) { preferences, secureConnection, notificationRegistration, availableModels ->
                SessionInputs(
                    preferences = preferences,
                    secureConnection = secureConnection,
                    notificationRegistration = notificationRegistration,
                    availableModels = availableModels,
                )
            }.collectLatest { inputs ->
                val preferences = inputs.preferences
                val secureConnection = inputs.secureConnection
                val baseThreads = baseThreadsState.value
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
                val selectedThreadId = resolveSelectedThreadId(
                    preferredThreadId = preferredSelectedThreadId(
                        persistedSelectedThreadId = preferences.selectedThreadId,
                        sessionSelectedThreadId = sessionState.value.selectedThreadId,
                    ),
                    threads = threads,
                )
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
                        threads = threads,
                        selectedThreadId = selectedThreadId,
                        selectedThreadSnapshot = threads.firstOrNull { thread -> thread.id == selectedThreadId }
                            ?: threads.firstOrNull(),
                        notificationRegistration = inputs.notificationRegistration,
                    )
                }
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            secureConnectionCoordinator.state.collectLatest { secureConnection ->
                if (secureConnection.secureState == SecureConnectionState.ENCRYPTED) {
                    val selectedThreadIdBeforeRefresh = sessionState.value.selectedThreadId
                    val runningSelectedThreadBeforeRefresh = selectedThreadIdBeforeRefresh
                        ?.let { selectedThreadId ->
                            sessionState.value.selectedThread
                                ?.takeIf { thread -> thread.id == selectedThreadId && thread.isRunning }
                        }
                    runHydrationSafely {
                        hydrationService()?.refreshThreads()
                    }
                    selectedThreadIdBeforeRefresh
                        ?.let { selectedThreadId ->
                            runHydrationSafely {
                                hydrationService()?.hydrateThread(selectedThreadId)
                            }
                            (runningSelectedThreadBeforeRefresh
                                ?: sessionState.value.selectedThread
                                    ?.takeIf { thread -> thread.id == selectedThreadId && thread.isRunning })
                                ?.let { selectedThread ->
                                    runHydrationSafely {
                                        resumeService()?.resumeThread(
                                            threadId = selectedThread.id,
                                            preferredProjectPath = selectedThread.projectPath.ifBlank { null },
                                            modelIdentifier = selectedThread.runtimeConfig.selectedModelId,
                                        )
                                    }
                                }
                        }
                }
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

    override suspend fun completeOnboarding() {
        appPreferencesRepository.setOnboardingCompleted(true)
        applyPreferencesLocally(
            preferencesState.value.copy(onboardingCompleted = true),
        )
    }

    override suspend fun refreshThreads() {
        if (sessionState.value.connectionStatus.phase != RemodexConnectionPhase.CONNECTED) {
            return
        }
        runHydrationSafely {
            hydrationService()?.refreshThreads()
        }
    }

    override suspend fun hydrateThread(threadId: String) {
        runHydrationSafely {
            hydrationService()?.hydrateThread(threadId)
        }
    }

    override suspend fun selectThread(threadId: String) {
        val threadExists = session.value.threads.any { it.id == threadId } ||
            threadSyncService.threads.value.any { it.id == threadId }
        if (threadExists) {
            appPreferencesRepository.setSelectedThreadId(threadId)
            sessionState.update { snapshot ->
                snapshot.copy(
                    selectedThreadId = threadId,
                    selectedThreadSnapshot = snapshot.threads.firstOrNull { thread -> thread.id == threadId }
                        ?: snapshot.selectedThreadSnapshot,
                )
            }
            runHydrationSafely {
                hydrationService()?.hydrateThread(threadId)
            }
        }
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
        refreshBaseThreadsFromSync()
        inheritRuntimeFromThreadId?.let { sourceThreadId ->
            inheritRuntimeOverride(
                sourceThreadId = sourceThreadId,
                destinationThreadId = createdThread.id,
            )
        }
        appPreferencesRepository.setSelectedThreadId(createdThread.id)
        val selectedThread = selectedThreadSnapshotForThreadId(createdThread.id)
        sessionState.update { snapshot ->
            snapshot.copy(
                selectedThreadId = createdThread.id,
                selectedThreadSnapshot = selectedThread ?: snapshot.selectedThreadSnapshot,
            )
        }
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
        appPreferencesRepository.setSelectedThreadId(nextSelectedThreadId)
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
        val subtreeIds = collectSubtreeThreadIds(baseThreadsState.value, threadId)
        val updatedThreads = baseThreadsState.value.filterNot { thread -> thread.id in subtreeIds }
        refreshThreadsLocally(updatedThreads)
        val nextSelectedThreadId = if (sessionState.value.selectedThreadId in subtreeIds) {
            updatedThreads.firstOrNull { it.syncState == RemodexThreadSyncState.LIVE }?.id
                ?: updatedThreads.firstOrNull()?.id
        } else {
            sessionState.value.selectedThreadId
        }
        appPreferencesRepository.setSelectedThreadId(nextSelectedThreadId)
        sessionState.update { snapshot ->
            snapshot.copy(
                selectedThreadId = nextSelectedThreadId,
                selectedThreadSnapshot = snapshot.threads.firstOrNull { thread -> thread.id == nextSelectedThreadId }
                    ?: snapshot.threads.firstOrNull(),
            )
        }
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
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty() && attachments.isEmpty()) {
            return
        }
        var thread = sessionState.value.threads.firstOrNull { it.id == threadId } ?: return
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
                threadCommandService.sendPrompt(
                    threadId = continuationThreadId,
                    prompt = trimmedPrompt,
                    runtimeConfig = resumedContinuationThread.runtimeConfig,
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
            threadCommandService.sendPrompt(
                threadId = threadId,
                prompt = trimmedPrompt,
                runtimeConfig = thread.runtimeConfig,
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
            threadCommandService.sendPrompt(
                threadId = continuationThreadId,
                prompt = trimmedPrompt,
                runtimeConfig = resumedContinuationThread.runtimeConfig,
                attachments = attachments,
            )
        }
        refreshBaseThreadsFromSync()
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
            runtimeConfig = thread.runtimeConfig,
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
        if (sessionState.value.connectionStatus.phase != RemodexConnectionPhase.CONNECTED) {
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
        if (sessionState.value.connectionStatus.phase != RemodexConnectionPhase.CONNECTED) {
            gptAccountSnapshotState.value = loggedOutGptAccountSnapshot(gptAccountSnapshotState.value)
            gptAccountErrorMessageState.value = null
            return
        }
        secureConnectionCoordinator.sendRequest(method = "account/logout", params = null)
        gptAccountSnapshotState.value = loggedOutGptAccountSnapshot(gptAccountSnapshotState.value)
        gptAccountErrorMessageState.value = null
        refreshGptAccountState()
    }

    override suspend fun refreshUsageStatus(threadId: String?) {
        if (sessionState.value.connectionStatus.phase != RemodexConnectionPhase.CONNECTED) {
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
        target: RemodexComposerReviewTarget,
        baseBranch: String?,
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
                target = target,
                baseBranch = baseBranch,
            )
            refreshBaseThreadsFromSync()
            return
        }
        try {
            threadCommandService.startCodeReview(
                threadId = threadId,
                target = target,
                baseBranch = baseBranch,
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
                target = target,
                baseBranch = baseBranch,
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
        refreshBaseThreadsFromSync()
        appPreferencesRepository.setSelectedThreadId(forkedThread.id)
        sessionState.update { snapshot ->
            snapshot.copy(
                selectedThreadId = forkedThread.id,
                selectedThreadSnapshot = snapshot.threads.firstOrNull { thread -> thread.id == forkedThread.id }
                    ?: snapshot.selectedThreadSnapshot,
            )
        }
        return forkedThread.id
    }

    override suspend fun loadGitState(threadId: String): RemodexGitState {
        return threadCommandService.loadGitState(threadId)
    }

    override suspend fun loadGitDiff(threadId: String): RemodexGitRepoDiff {
        return threadCommandService.loadGitDiff(threadId)
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
    ): RemodexGitState {
        return threadCommandService.createGitWorktree(
            threadId = threadId,
            name = name,
            baseBranch = baseBranch,
        )
    }

    override suspend fun commitGitChanges(
        threadId: String,
        message: String?,
    ): RemodexGitState {
        return threadCommandService.commitGitChanges(threadId, message)
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

    override suspend fun retryConnection() {
        secureConnectionCoordinator.retryConnection()
    }

    override suspend fun disconnect() {
        secureConnectionCoordinator.disconnect()
        gptAccountSnapshotState.value = disconnectedGptAccountSnapshot(gptAccountSnapshotState.value)
        gptAccountErrorMessageState.value = null
        bridgeVersionStatusState.value = RemodexBridgeVersionStatus()
        usageStatusState.value = RemodexUsageStatus()
    }

    override suspend fun forgetTrustedMac() {
        secureConnectionCoordinator.forgetTrustedMac()
        gptAccountSnapshotState.value = disconnectedGptAccountSnapshot(gptAccountSnapshotState.value)
        gptAccountErrorMessageState.value = null
        bridgeVersionStatusState.value = RemodexBridgeVersionStatus()
        usageStatusState.value = RemodexUsageStatus()
    }

    private suspend fun syncThreads(snapshots: List<ThreadSyncSnapshot>) {
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

    private fun projectThreadTimelineItems(snapshot: ThreadSyncSnapshot): List<com.emanueledipietro.remodex.model.RemodexConversationItem> {
        return synchronized(timelineProjectionCacheLock) {
            val previous = timelineProjectionCacheByThread[snapshot.id]
            val lastMutation = snapshot.timelineMutations.lastOrNull()
            val projectedItems = if (
                previous != null &&
                snapshot.timelineMutations.size == previous.mutationCount + 1 &&
                lastMutation != null
            ) {
                TurnTimelineReducer.applyProjectedFastPath(previous.projectedItems, lastMutation)
                    ?: TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
            } else {
                TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
            }
            timelineProjectionCacheByThread[snapshot.id] = ThreadTimelineProjectionCache(
                mutationCount = snapshot.timelineMutations.size,
                projectedItems = projectedItems,
            )
            projectedItems
        }
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
        if (sessionState.value.connectionStatus.phase != RemodexConnectionPhase.CONNECTED) {
            return thread
        }
        val resumeService = resumeService() ?: return thread
        if (resumeService.isThreadResumedLocally(thread.id)) {
            return sessionState.value.threads.firstOrNull { candidate -> candidate.id == thread.id } ?: thread
        }
        resumeService.resumeThread(
            threadId = thread.id,
            preferredProjectPath = thread.projectPath.ifBlank { null },
            modelIdentifier = thread.runtimeConfig.selectedModelId,
        )
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
        refreshBaseThreadsFromSync()
        appPreferencesRepository.setSelectedThreadId(createdThread.id)
        val selectedThread = selectedThreadSnapshotForThreadId(createdThread.id)
        sessionState.update { snapshot ->
            snapshot.copy(
                selectedThreadId = createdThread.id,
                selectedThreadSnapshot = selectedThread ?: snapshot.selectedThreadSnapshot,
            )
        }
        refreshBaseThreadsFromSync()
        return createdThread.id
    }

    private suspend fun refreshBaseThreadsFromSync() {
        val mergedBaseThreads = mergeBaseThreadsFromSync(
            threadSyncService.threads.value
                .map { snapshot -> snapshot.toCachedThreadRecord(projectThreadTimelineItems(snapshot)) }
                .sortedByDescending(CachedThreadRecord::lastUpdatedEpochMs)
                .map(CachedThreadRecord::toBaseThreadSummary),
        )
        refreshThreadsLocally(mergedBaseThreads)
    }

    private fun selectedThreadSnapshotForThreadId(threadId: String): RemodexThreadSummary? {
        val selectedBaseThread = baseThreadsState.value.firstOrNull { thread -> thread.id == threadId } ?: return null
        return selectedBaseThread.materialize(
            preferences = preferencesState.value,
            availableModels = resolveAvailableModels(baseThreadsState.value),
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
    ) {
        baseThreadsState.value = baseThreads
        val resolvedAvailableModels = resolveAvailableModels(baseThreads)
        val selectedThreadId = resolveSelectedThreadId(
            preferredThreadId = preferredSelectedThreadId(
                persistedSelectedThreadId = preferencesState.value.selectedThreadId,
                sessionSelectedThreadId = sessionState.value.selectedThreadId,
            ),
            threads = baseThreads,
        )
        if (selectedThreadId != preferencesState.value.selectedThreadId) {
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
        val selectedBaseThread = baseThreads.firstOrNull { thread -> thread.id == selectedThreadId }
            ?: baseThreads.firstOrNull()
        val selectedThread = selectedBaseThread?.materialize(
            preferences = preferences,
            availableModels = availableModels,
        )
        sessionState.update { snapshot ->
            snapshot.copy(
                availableModels = availableModels,
                selectedThreadId = selectedThreadId,
                selectedThreadSnapshot = selectedThread,
                trustedMac = snapshot.secureConnection.toTrustedMacPresentation(preferences.macNicknamesByDeviceId),
            )
        }
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
        val selectedThreadId = resolveSelectedThreadId(
            preferredThreadId = preferredSelectedThreadId(
                persistedSelectedThreadId = preferences.selectedThreadId,
                sessionSelectedThreadId = sessionState.value.selectedThreadId,
            ),
            threads = threads,
        )
        sessionState.update { snapshot ->
            snapshot.copy(
                onboardingCompleted = preferences.onboardingCompleted,
                connectionStatus = secureConnection.toConnectionStatus(),
                secureConnection = secureConnection,
                runtimeDefaults = preferences.runtimeDefaults,
                availableModels = availableModels,
                appearanceMode = preferences.appearanceMode,
                appFontStyle = preferences.appFontStyle,
                trustedMac = secureConnection.toTrustedMacPresentation(preferences.macNicknamesByDeviceId),
                threads = threads,
                selectedThreadId = selectedThreadId,
                selectedThreadSnapshot = threads.firstOrNull { thread -> thread.id == selectedThreadId }
                    ?: threads.firstOrNull(),
                notificationRegistration = notificationRegistration,
            )
        }
    }

    private fun scheduleThreadListPublish(
        baseThreads: List<RemodexThreadSummary>,
        preferences: AppPreferences,
        availableModels: List<RemodexModelOption>,
        secureConnection: SecureConnectionSnapshot,
        notificationRegistration: RemodexNotificationRegistrationState,
    ) {
        threadListPublishJob?.cancel()
        threadListPublishJob = repositoryScope.launch {
            delay(SelectedThreadStreamingThreadListDebounceMs)
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
        val localById = baseThreadsState.value.associateBy(RemodexThreadSummary::id)
        val mergedById = linkedMapOf<String, RemodexThreadSummary>()
        syncedThreads.forEach { syncedThread ->
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
            if (mergedById[localThread.id] == null) {
                mergedById[localThread.id] = localThread
            }
        }
        return mergedById.values.toList()
    }
}

private data class ThreadTimelineProjectionCache(
    val mutationCount: Int,
    val projectedItems: List<com.emanueledipietro.remodex.model.RemodexConversationItem>,
)

private data class SyncedThreadProjection(
    val cachedThreads: List<CachedThreadRecord>,
    val mergedBaseThreads: List<RemodexThreadSummary>,
)

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
    val notificationRegistration: RemodexNotificationRegistrationState,
    val availableModels: List<RemodexModelOption>,
)

private fun resolveSelectedThreadId(
    preferredThreadId: String?,
    threads: List<RemodexThreadSummary>,
): String? {
    if (preferredThreadId != null && threads.any { it.id == preferredThreadId }) {
        return preferredThreadId
    }
    return threads.firstOrNull()?.id
}

private fun preferredSelectedThreadId(
    persistedSelectedThreadId: String?,
    sessionSelectedThreadId: String?,
): String? {
    return sessionSelectedThreadId ?: persistedSelectedThreadId
}

private fun materializeThreads(
    baseThreads: List<RemodexThreadSummary>,
    preferences: AppPreferences,
    availableModels: List<RemodexModelOption>,
): List<RemodexThreadSummary> {
    return baseThreads.map { thread ->
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
