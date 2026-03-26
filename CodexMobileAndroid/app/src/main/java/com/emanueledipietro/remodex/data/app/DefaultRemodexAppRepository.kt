package com.emanueledipietro.remodex.data.app

import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.statusLabel
import com.emanueledipietro.remodex.data.preferences.AppPreferences
import com.emanueledipietro.remodex.data.preferences.AppPreferencesRepository
import com.emanueledipietro.remodex.data.threads.CachedThreadRecord
import com.emanueledipietro.remodex.data.threads.ThreadCommandService
import com.emanueledipietro.remodex.data.threads.ThreadCacheStore
import com.emanueledipietro.remodex.data.threads.ThreadHydrationService
import com.emanueledipietro.remodex.data.threads.ThreadSyncService
import com.emanueledipietro.remodex.data.threads.ThreadSyncSnapshot
import com.emanueledipietro.remodex.data.threads.toCachedThreadRecord
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexNotificationRegistrationState
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeOverrides
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val initialBaseThreads = threadSyncService.threads.value
        .map(ThreadSyncSnapshot::toCachedThreadRecord)
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
        ),
    )

    override val session: StateFlow<RemodexSessionSnapshot> = sessionState
    override val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>> =
        threadSyncService.commandExecutionDetails

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            threadSyncService.threads.collectLatest { snapshots ->
                syncThreads(snapshots)
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            threadCacheStore.threads.collectLatest { cachedThreads ->
                if (threadSyncService.threads.value.isNotEmpty()) {
                    return@collectLatest
                }
                refreshThreadsLocally(cachedThreads.map(CachedThreadRecord::toBaseThreadSummary))
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                appPreferencesRepository.preferences,
                secureConnectionCoordinator.state,
                baseThreadsState,
                managedPushRegistrationState,
                threadSyncService.availableModels,
            ) { preferences, secureConnection, baseThreads, notificationRegistration, availableModels ->
                SessionInputs(
                    preferences = preferences,
                    secureConnection = secureConnection,
                    baseThreads = baseThreads,
                    notificationRegistration = notificationRegistration,
                    availableModels = availableModels,
                )
            }.collectLatest { inputs ->
                val preferences = inputs.preferences
                val secureConnection = inputs.secureConnection
                val baseThreads = inputs.baseThreads
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
                    preferredThreadId = preferences.selectedThreadId ?: sessionState.value.selectedThreadId,
                    threads = threads,
                )
                if (selectedThreadId != preferences.selectedThreadId) {
                    appPreferencesRepository.setSelectedThreadId(selectedThreadId)
                }
                sessionState.update { snapshot ->
                    snapshot.copy(
                        onboardingCompleted = preferences.onboardingCompleted,
                        connectionStatus = secureConnection.toConnectionStatus(),
                        secureConnection = secureConnection,
                        runtimeDefaults = preferences.runtimeDefaults,
                        availableModels = resolvedAvailableModels,
                        appearanceMode = preferences.appearanceMode,
                        trustedMac = secureConnection.toTrustedMacPresentation(),
                        threads = threads,
                        selectedThreadId = selectedThreadId,
                        notificationRegistration = inputs.notificationRegistration,
                    )
                }
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            secureConnectionCoordinator.state.collectLatest { secureConnection ->
                if (secureConnection.secureState == SecureConnectionState.ENCRYPTED) {
                    threadHydrationService?.refreshThreads()
                    sessionState.value.selectedThreadId?.let { selectedThreadId ->
                        threadHydrationService?.hydrateThread(selectedThreadId)
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
        val hydrationService = threadHydrationService ?: (threadSyncService as? ThreadHydrationService)
        hydrationService?.refreshThreads()
    }

    override suspend fun selectThread(threadId: String) {
        val threadExists = session.value.threads.any { it.id == threadId } ||
            threadSyncService.threads.value.any { it.id == threadId }
        if (threadExists) {
            appPreferencesRepository.setSelectedThreadId(threadId)
            sessionState.update { snapshot ->
                snapshot.copy(selectedThreadId = threadId)
            }
            threadHydrationService?.hydrateThread(threadId)
        }
    }

    override suspend fun createThread(preferredProjectPath: String?) {
        val createdThread = threadCommandService.createThread(
            preferredProjectPath = preferredProjectPath,
            runtimeDefaults = preferencesState.value.runtimeDefaults,
        ) ?: return
        refreshBaseThreadsFromSync()
        appPreferencesRepository.setSelectedThreadId(createdThread.id)
        sessionState.update { snapshot ->
            snapshot.copy(selectedThreadId = createdThread.id)
        }
        threadHydrationService?.hydrateThread(createdThread.id)
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
                    thread.copy(title = trimmedName)
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
            snapshot.copy(selectedThreadId = nextSelectedThreadId)
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
            snapshot.copy(selectedThreadId = nextSelectedThreadId)
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
        val thread = sessionState.value.threads.firstOrNull { it.id == threadId } ?: return
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

        threadCommandService.sendPrompt(
            threadId = threadId,
            prompt = trimmedPrompt,
            runtimeConfig = thread.runtimeConfig,
            attachments = attachments,
        )
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
        threadCommandService.startCodeReview(
            threadId = threadId,
            target = target,
            baseBranch = baseBranch,
        )
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
            snapshot.copy(selectedThreadId = forkedThread.id)
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
    }

    override suspend fun forgetTrustedMac() {
        secureConnectionCoordinator.forgetTrustedMac()
    }

    private suspend fun syncThreads(snapshots: List<ThreadSyncSnapshot>) {
        val cachedThreads = snapshots
            .map(ThreadSyncSnapshot::toCachedThreadRecord)
            .sortedByDescending(CachedThreadRecord::lastUpdatedEpochMs)
        threadCacheStore.replaceThreads(cachedThreads)
        refreshThreadsLocally(cachedThreads.map(CachedThreadRecord::toBaseThreadSummary))
    }

    private fun refreshBaseThreadsFromSync() {
        refreshThreadsLocally(
            threadSyncService.threads.value
            .map(ThreadSyncSnapshot::toCachedThreadRecord)
            .sortedByDescending(CachedThreadRecord::lastUpdatedEpochMs)
            .map(CachedThreadRecord::toBaseThreadSummary),
        )
    }

    private fun applyPreferencesLocally(preferences: AppPreferences) {
        preferencesState.value = preferences
        val resolvedAvailableModels = resolveAvailableModels(baseThreadsState.value)
        val threads = materializeThreads(
            baseThreads = baseThreadsState.value,
            preferences = preferences,
            availableModels = resolvedAvailableModels,
        )
        sessionState.update { snapshot ->
            snapshot.copy(
                onboardingCompleted = preferences.onboardingCompleted,
                runtimeDefaults = preferences.runtimeDefaults,
                availableModels = resolvedAvailableModels,
                appearanceMode = preferences.appearanceMode,
                trustedMac = snapshot.secureConnection.toTrustedMacPresentation(),
                threads = threads,
                selectedThreadId = resolveSelectedThreadId(
                    preferredThreadId = preferences.selectedThreadId ?: snapshot.selectedThreadId,
                    threads = threads,
                ),
            )
        }
    }

    private fun refreshThreadsLocally(baseThreads: List<RemodexThreadSummary>) {
        baseThreadsState.value = baseThreads
        val resolvedAvailableModels = resolveAvailableModels(baseThreads)
        val threads = materializeThreads(
            baseThreads = baseThreads,
            preferences = preferencesState.value,
            availableModels = resolvedAvailableModels,
        )
        sessionState.update { snapshot ->
            snapshot.copy(
                availableModels = resolvedAvailableModels,
                threads = threads,
                trustedMac = snapshot.secureConnection.toTrustedMacPresentation(),
                selectedThreadId = resolveSelectedThreadId(
                    preferredThreadId = preferencesState.value.selectedThreadId ?: snapshot.selectedThreadId,
                    threads = threads,
                ),
            )
        }
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
}

private data class SessionInputs(
    val preferences: AppPreferences,
    val secureConnection: SecureConnectionSnapshot,
    val baseThreads: List<RemodexThreadSummary>,
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

private fun materializeThreads(
    baseThreads: List<RemodexThreadSummary>,
    preferences: AppPreferences,
    availableModels: List<RemodexModelOption>,
): List<RemodexThreadSummary> {
    return baseThreads.map { thread ->
        val queuedDraftItems = preferences.queuedDraftsByThread[thread.id].orEmpty()
        val effectiveRuntime = thread.runtimeConfig
            .withAvailableModels(availableModels)
            .applyDefaults(preferences.runtimeDefaults)
            .applyThreadOverrides(preferences.runtimeOverridesByThread[thread.id])
        thread.copy(
            queuedDrafts = queuedDraftItems.size,
            queuedDraftItems = queuedDraftItems,
            runtimeConfig = effectiveRuntime,
            runtimeLabel = effectiveRuntime.runtimeLabel,
        )
    }
}

private fun CachedThreadRecord.toBaseThreadSummary(): RemodexThreadSummary {
    return RemodexThreadSummary(
        id = id,
        title = title,
        preview = preview,
        projectPath = projectPath,
        lastUpdatedLabel = lastUpdatedLabel,
        isRunning = isRunning,
        syncState = syncState,
        parentThreadId = parentThreadId,
        agentNickname = agentNickname,
        agentRole = agentRole,
        queuedDrafts = 0,
        queuedDraftItems = emptyList(),
        runtimeLabel = runtimeConfig.runtimeLabel,
        runtimeConfig = runtimeConfig,
        messages = timelineItems.sortedBy { item -> item.orderIndex },
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

private fun SecureConnectionSnapshot.toTrustedMacPresentation(): RemodexTrustedMacPresentation? {
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
    val detail = buildList {
        add(secureState.statusLabel)
        fingerprint?.let(::add)
    }.joinToString(separator = " · ")
    return RemodexTrustedMacPresentation(
        deviceId = macDeviceId,
        title = title,
        name = fallbackName,
        systemName = null,
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
