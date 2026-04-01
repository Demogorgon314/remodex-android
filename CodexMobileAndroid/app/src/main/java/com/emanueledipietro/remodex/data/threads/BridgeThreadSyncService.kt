package com.emanueledipietro.remodex.data.threads

import android.util.Log
import com.emanueledipietro.remodex.data.connection.RpcError
import com.emanueledipietro.remodex.data.connection.RpcMessage
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureTransportException
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.firstArray
import com.emanueledipietro.remodex.data.connection.firstBoolean
import com.emanueledipietro.remodex.data.connection.firstDouble
import com.emanueledipietro.remodex.data.connection.firstInt
import com.emanueledipietro.remodex.data.connection.firstObject
import com.emanueledipietro.remodex.data.connection.firstRawString
import com.emanueledipietro.remodex.data.connection.firstString
import com.emanueledipietro.remodex.data.connection.firstValue
import com.emanueledipietro.remodex.data.connection.jsonArrayOrNull
import com.emanueledipietro.remodex.data.connection.jsonObjectOrNull
import com.emanueledipietro.remodex.data.connection.rpcIdKey
import com.emanueledipietro.remodex.data.connection.stringOrNull
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.ConversationSystemTurnOrderingHint
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetSource
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.RemodexAssistantResponseMetrics
import com.emanueledipietro.remodex.model.RemodexApprovalKind
import com.emanueledipietro.remodex.model.RemodexApprovalRequest
import com.emanueledipietro.remodex.model.RemodexCodeReviewRequest
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitBranches
import com.emanueledipietro.remodex.model.RemodexGitChangedFile
import com.emanueledipietro.remodex.model.RemodexGitCommit
import com.emanueledipietro.remodex.model.RemodexGitDiffTotals
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitRemoteUrl
import com.emanueledipietro.remodex.model.RemodexGitRepoSync
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitWorktreeChangeTransferMode
import com.emanueledipietro.remodex.model.RemodexGitWorktreeResult
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanState
import com.emanueledipietro.remodex.model.RemodexPlanStep
import com.emanueledipietro.remodex.model.RemodexPlanStepStatus
import com.emanueledipietro.remodex.model.RemodexPermissionGrantScope
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexReasoningEffortOption
import com.emanueledipietro.remodex.model.RemodexRequestedPermissions
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertConflict
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexBridgeUpdatePrompt
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputOption
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputQuestion
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputRequest
import com.emanueledipietro.remodex.model.RemodexSubagentAction
import com.emanueledipietro.remodex.model.RemodexSubagentRef
import com.emanueledipietro.remodex.model.RemodexSubagentState
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexTurnTerminalState
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexUnifiedPatchParser
import com.emanueledipietro.remodex.model.androidUserMessageText
import com.emanueledipietro.remodex.model.fallbackConversationImageDisplayName
import com.emanueledipietro.remodex.model.isInlineImageDataUrl
import com.emanueledipietro.remodex.model.remodexBridgeUpdateCommand
import com.emanueledipietro.remodex.model.toConversationAttachment
import com.emanueledipietro.remodex.feature.turn.ThinkingDisclosureParser
import com.emanueledipietro.remodex.feature.turn.TurnTimelineReducer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

internal fun shouldIgnoreStreamingTextDelta(delta: String): Boolean = delta.isEmpty()

internal fun assistantLifecycleStartedText(existingText: String?): String = existingText.orEmpty()

internal fun shouldMergeThreadReadHistory(
    threadIsRunning: Boolean,
    existingHasTimeline: Boolean,
    allowWhileRunning: Boolean,
): Boolean = allowWhileRunning || !threadIsRunning || !existingHasTimeline

internal fun findReusableAssistantCompletionItemValue(
    items: List<com.emanueledipietro.remodex.model.RemodexConversationItem>,
    turnId: String?,
    itemId: String?,
    text: String,
): com.emanueledipietro.remodex.model.RemodexConversationItem? {
    val normalizedText = normalizeAssistantCompletionTextValue(text)
    if (normalizedText.isEmpty()) {
        return null
    }
    val normalizedTurnId = normalizeAssistantCompletionIdentifierValue(turnId)
    val normalizedItemId = normalizeAssistantCompletionIdentifierValue(itemId)
    return items.lastOrNull { candidate ->
        candidate.speaker == ConversationSpeaker.ASSISTANT &&
            candidate.kind == ConversationItemKind.CHAT &&
            normalizeAssistantCompletionTextValue(candidate.text) == normalizedText &&
            (
                candidate.isStreaming ||
                    (normalizedTurnId != null &&
                        normalizeAssistantCompletionIdentifierValue(candidate.turnId) == normalizedTurnId) ||
                    (normalizedItemId != null &&
                        normalizeAssistantCompletionIdentifierValue(candidate.itemId) == normalizedItemId)
                )
    }
}

internal fun shouldSuppressAnonymousAssistantCompletionValue(
    turnId: String?,
    itemId: String?,
    text: String,
    previousText: String?,
    elapsedMs: Long,
    maxAgeMs: Long = 45_000L,
): Boolean {
    if (normalizeAssistantCompletionIdentifierValue(turnId) != null) {
        return false
    }
    if (normalizeAssistantCompletionIdentifierValue(itemId) != null) {
        return false
    }
    val normalizedText = normalizeAssistantCompletionTextValue(text)
    val normalizedPreviousText = normalizeAssistantCompletionTextValue(previousText.orEmpty())
    if (normalizedText.isEmpty() || normalizedPreviousText.isEmpty()) {
        return false
    }
    return normalizedText == normalizedPreviousText && elapsedMs <= maxAgeMs
}

private fun normalizeAssistantCompletionTextValue(text: String): String = text.trim()

private fun normalizeAssistantCompletionIdentifierValue(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    return trimmed.ifEmpty { null }
}

internal fun shouldReplaceOptimisticReviewPromptValue(
    localText: String,
    incomingText: String,
): Boolean {
    val normalizedLocal = localText.trim()
    val normalizedIncoming = incomingText.trim()
    if (normalizedLocal.isEmpty() || normalizedIncoming.isEmpty()) {
        return false
    }
    if (normalizedLocal == normalizedIncoming) {
        return false
    }
    return normalizedLocal.startsWith("Review ", ignoreCase = true) &&
        normalizedIncoming.startsWith("Review ", ignoreCase = true)
}

internal fun shouldDropCompletedReasoningPlaceholderValue(
    existingText: String?,
    completedBody: String,
): Boolean {
    return ThinkingDisclosureParser.normalizedThinkingContent(existingText.orEmpty()).isEmpty() &&
        ThinkingDisclosureParser.normalizedThinkingContent(completedBody).isEmpty()
}

class BridgeThreadSyncService(
    private val secureConnectionCoordinator: SecureConnectionCoordinator,
    private val scope: CoroutineScope,
    private val appVersionName: String = "1.0",
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : ThreadSyncService, ThreadCommandService, ThreadHydrationService, ThreadResumeService, ThreadActiveContextService, ThreadLocalTimelineService {
    private data class DecodedHistoryItem(
        val timestampMs: Long,
        val sequence: Long,
        val item: com.emanueledipietro.remodex.model.RemodexConversationItem,
    )

    private data class AssistantCompletionFingerprint(
        val text: String,
        val timestampMs: Long,
    )

    private data class DecodedFileChangeInlineTotals(
        val additions: Int,
        val deletions: Int,
    )

    private data class DecodedFileChangeEntry(
        val path: String,
        val kind: String,
        val diff: String,
        val inlineTotals: DecodedFileChangeInlineTotals?,
    )

    private data class AssistantStreamingTrace(
        val deltaCount: Int = 0,
        val totalChars: Int = 0,
        val lastDeltaLength: Int = 0,
        val lastTurnId: String? = null,
        val lastItemId: String? = null,
        val finalAnswerCompleted: Boolean = false,
        val idleObserved: Boolean = false,
        val turnCompletedObserved: Boolean = false,
    )

    private data class AssistantResponseTrace(
        val requestStartedAtMs: Long,
        val turnId: String? = null,
        val itemId: String? = null,
        val messageId: String? = null,
        val firstOutputAtMs: Long? = null,
        val lastOutputAtMs: Long? = null,
        val outputTokens: Int? = null,
        val baselineTotalOutputTokens: Int? = null,
        val outputObservationCount: Int = 0,
        val turnCompleted: Boolean = false,
    )

    private val postTurnCatchupDelaysMs = listOf(1_000L, 2_000L)
    private val threadMaterializationRetryDelaysMs = listOf(250L, 500L, 1_000L)
    private val backingAvailableModels = MutableStateFlow<List<RemodexModelOption>>(emptyList())
    private val backingThreads = MutableStateFlow<List<ThreadSyncSnapshot>>(emptyList())
    private val backingCommandExecutionDetails =
        MutableStateFlow<Map<String, RemodexCommandExecutionDetails>>(emptyMap())
    private val backingAssistantResponseMetricsByThreadId =
        MutableStateFlow<Map<String, RemodexAssistantResponseMetrics>>(emptyMap())
    private val backingPendingApprovalRequest = MutableStateFlow<RemodexApprovalRequest?>(null)
    private val backingBridgeUpdatePrompt = MutableStateFlow<RemodexBridgeUpdatePrompt?>(null)
    private val backingSupportsThreadFork = MutableStateFlow(true)
    private val activeTurnIdByThread = mutableMapOf<String, String>()
    private val threadIdByTurnId = mutableMapOf<String, String>()
    private val reviewDebugTurnIds = mutableSetOf<String>()
    private val reviewDebugThreadIds = mutableSetOf<String>()
    private val streamingTraceByThread = mutableMapOf<String, AssistantStreamingTrace>()
    private val assistantResponseTraceByThread = mutableMapOf<String, AssistantResponseTrace>()
    private val latestAssistantTotalOutputTokensByThread = mutableMapOf<String, Int>()
    private val runningThreadFallbackIds = mutableSetOf<String>()
    private val assistantCompletionFingerprintByThread = mutableMapOf<String, AssistantCompletionFingerprint>()
    private val latestTurnTerminalStateByThread = mutableMapOf<String, RemodexTurnTerminalState>()
    private val stoppedTurnIdsByThread = mutableMapOf<String, Set<String>>()
    private val resumedThreadIds = mutableSetOf<String>()
    private val lifecycleCatchupJobByThread = mutableMapOf<String, Job>()
    @Volatile
    private var activeThreadIdHint: String? = null
    private var initializedAttempt: Int? = null
    private var supportsServiceTier = true
    private var hasPresentedServiceTierBridgeUpdatePrompt = false
    private var supportsThreadForkCapability = true
    private var hasPresentedThreadForkBridgeUpdatePrompt = false
    private var supportsTurnCollaborationMode = true

    override val availableModels: StateFlow<List<RemodexModelOption>> = backingAvailableModels
    override val threads: StateFlow<List<ThreadSyncSnapshot>> = backingThreads
    override val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>> =
        backingCommandExecutionDetails
    override val assistantResponseMetricsByThreadId: StateFlow<Map<String, RemodexAssistantResponseMetrics>> =
        backingAssistantResponseMetricsByThreadId
    override val pendingApprovalRequest: StateFlow<RemodexApprovalRequest?> = backingPendingApprovalRequest
    override val bridgeUpdatePrompt: StateFlow<RemodexBridgeUpdatePrompt?> = backingBridgeUpdatePrompt
    override val supportsThreadFork: StateFlow<Boolean> = backingSupportsThreadFork

    override fun dismissBridgeUpdatePrompt() {
        backingBridgeUpdatePrompt.value = null
    }

    override fun setActiveThreadHint(threadId: String?) {
        activeThreadIdHint = threadId?.trim()?.takeIf(String::isNotEmpty)
    }

    init {
        scope.launch {
            secureConnectionCoordinator.state.collectLatest { snapshot ->
                if (snapshot.secureState == SecureConnectionState.ENCRYPTED) {
                    if (initializedAttempt != snapshot.attempt) {
                        initializedAttempt = snapshot.attempt
                        supportsServiceTier = true
                        hasPresentedServiceTierBridgeUpdatePrompt = false
                        supportsThreadForkCapability = true
                        hasPresentedThreadForkBridgeUpdatePrompt = false
                        supportsTurnCollaborationMode = true
                        backingBridgeUpdatePrompt.value = null
                        backingSupportsThreadFork.value = true
                        if (!initializeSession()) {
                            return@collectLatest
                        }
                        refreshAvailableModels()
                        refreshThreads()
                    }
                } else {
                    initializedAttempt = null
                    supportsServiceTier = true
                    hasPresentedServiceTierBridgeUpdatePrompt = false
                    supportsThreadForkCapability = true
                    hasPresentedThreadForkBridgeUpdatePrompt = false
                    supportsTurnCollaborationMode = true
                    backingBridgeUpdatePrompt.value = null
                    backingSupportsThreadFork.value = true
                    lifecycleCatchupJobByThread.values.forEach(Job::cancel)
                    lifecycleCatchupJobByThread.clear()
                    activeTurnIdByThread.clear()
                    threadIdByTurnId.clear()
                    assistantResponseTraceByThread.clear()
                    latestAssistantTotalOutputTokensByThread.clear()
                    runningThreadFallbackIds.clear()
                    assistantCompletionFingerprintByThread.clear()
                    latestTurnTerminalStateByThread.clear()
                    stoppedTurnIdsByThread.clear()
                    resumedThreadIds.clear()
                    backingThreads.value = emptyList()
                    backingCommandExecutionDetails.value = emptyMap()
                    backingAssistantResponseMetricsByThreadId.value = emptyMap()
                    backingPendingApprovalRequest.value = null
                }
            }
        }

        scope.launch {
            secureConnectionCoordinator.notifications.collect(::handleNotification)
        }

        scope.launch {
            secureConnectionCoordinator.requests.collect(::handleRequest)
        }
    }

    override suspend fun refreshThreads() {
        if (!isConnected()) {
            return
        }

        val serverThreads = runBackgroundSyncOperation("refreshThreads") {
            listThreads(archived = false) + listThreads(archived = true)
        } ?: return
        val existingById = backingThreads.value.associateBy(ThreadSyncSnapshot::id)
        val serverThreadIds = serverThreads.map(ThreadSyncSnapshot::id).toSet()
        val merged = serverThreads
            .map { incoming ->
                val existing = existingById[incoming.id]
                incoming.copy(
                    isRunning = threadHasKnownRunningState(incoming.id),
                    timelineMutations = existing?.timelineMutations ?: incoming.timelineMutations,
                    runtimeConfig = mergeRuntimeConfig(
                        existing = existing?.runtimeConfig,
                        incoming = incoming.runtimeConfig,
                    ),
                ).withResolvedLiveThreadState(
                    threadId = incoming.id,
                    isRunning = threadHasKnownRunningState(incoming.id),
                )
            }
            .plus(
                existingById.values.mapNotNull { existing ->
                    if (
                        existing.id in serverThreadIds ||
                        (!threadHasKnownRunningState(existing.id) && existing.id !in resumedThreadIds)
                    ) {
                        null
                    } else {
                        existing.withResolvedLiveThreadState(
                            threadId = existing.id,
                            isRunning = threadHasKnownRunningState(existing.id),
                        )
                    }
                },
            )
            .sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
        backingThreads.value = merged
    }

    override suspend fun hydrateThread(threadId: String) {
        if (!isConnected() || threadId.isBlank()) {
            return
        }

        val existingSnapshot = currentThreadSnapshot(threadId)
        logThreadRecovery(
            "event=hydrateThread start threadId=$threadId existing=${threadSnapshotSummary(existingSnapshot)} resumedLocally=${threadId in resumedThreadIds}",
        )
        val response = runCatching {
            retryAfterThreadMaterialization {
                requestThreadRead(threadId)
            }
        }.getOrNull()
        if (response == null) {
            logThreadRecovery("event=hydrateThread skipped=noResponse threadId=$threadId")
            return
        }
        mergeThreadSnapshotResponse(
            threadId = threadId,
            response = response,
            existingSnapshot = existingSnapshot,
            syncState = currentSyncState(threadId),
            allowHistoryMergeWhileRunning = true,
        )
        logThreadRecovery(
            "event=hydrateThread success threadId=$threadId snapshot=${threadSnapshotSummary(currentThreadSnapshot(threadId))}",
        )
    }

    override suspend fun resumeThread(
        threadId: String,
        preferredProjectPath: String?,
        modelIdentifier: String?,
    ): ThreadSyncSnapshot? {
        if (!isConnected() || threadId.isBlank()) {
            return null
        }

        val existingSnapshot = currentThreadSnapshot(threadId)
        logThreadRecovery(
            "event=resumeThread start threadId=$threadId preferredProjectPath=${preferredProjectPath.orEmpty()} " +
                "modelIdentifier=${modelIdentifier.orEmpty()} existing=${threadSnapshotSummary(existingSnapshot)} resumedLocally=${threadId in resumedThreadIds}",
        )
        val response = runThreadResume(
            threadId = threadId,
            preferredProjectPath = preferredProjectPath,
            modelIdentifier = modelIdentifier,
        )
        if (response == null) {
            logThreadRecovery(
                "event=resumeThread completedWithoutResponse threadId=$threadId existing=${threadSnapshotSummary(existingSnapshot)}",
            )
            return existingSnapshot
        }
        val refreshedSnapshot = mergeThreadSnapshotResponse(
            threadId = threadId,
            response = response,
            existingSnapshot = existingSnapshot,
            syncState = RemodexThreadSyncState.LIVE,
            allowHistoryMergeWhileRunning = true,
        )?.let { snapshot ->
            val normalizedPreferredProjectPath = preferredProjectPath?.trim()?.takeIf(String::isNotEmpty)
            if (normalizedPreferredProjectPath != null && snapshot.projectPath != normalizedPreferredProjectPath) {
                snapshot.copy(projectPath = normalizedPreferredProjectPath)
            } else {
                snapshot
            }
        } ?: currentThreadSnapshot(threadId)
        resumedThreadIds.add(threadId)
        logThreadRecovery(
            "event=resumeThread success threadId=$threadId snapshot=${threadSnapshotSummary(refreshedSnapshot)} resumedLocally=${threadId in resumedThreadIds}",
        )
        return refreshedSnapshot
    }

    override suspend fun updateThreadProjectPathLocally(
        threadId: String,
        projectPath: String,
    ): ThreadSyncSnapshot? {
        val normalizedProjectPath = projectPath.trim().takeIf(String::isNotEmpty) ?: return null
        var updatedSnapshot: ThreadSyncSnapshot? = null
        updateThread(threadId) { snapshot ->
            snapshot.copy(projectPath = normalizedProjectPath).also { updatedSnapshot = it }
        }
        return updatedSnapshot
    }

    override fun isThreadResumedLocally(threadId: String): Boolean {
        return threadId in resumedThreadIds
    }

    private fun currentThreadSnapshot(threadId: String): ThreadSyncSnapshot? {
        return backingThreads.value.firstOrNull { snapshot -> snapshot.id == threadId }
    }

    private fun upsertThreadSnapshot(refreshedSnapshot: ThreadSyncSnapshot) {
        val normalizedSnapshot = refreshedSnapshot.withResolvedLiveThreadState(
            threadId = refreshedSnapshot.id,
            isRunning = refreshedSnapshot.isRunning,
        )
        val currentThreads = backingThreads.value
        val updatedThreads = if (currentThreads.any { snapshot -> snapshot.id == normalizedSnapshot.id }) {
            currentThreads.map { snapshot ->
                if (snapshot.id == normalizedSnapshot.id) {
                    normalizedSnapshot
                } else {
                    snapshot
                }
            }
        } else {
            currentThreads + normalizedSnapshot
        }
        backingThreads.value = updatedThreads.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private fun mergeThreadSnapshotResponse(
        threadId: String,
        response: RpcMessage,
        existingSnapshot: ThreadSyncSnapshot?,
        syncState: RemodexThreadSyncState,
        allowHistoryMergeWhileRunning: Boolean = false,
    ): ThreadSyncSnapshot? {
        val resultObject = response.result?.jsonObjectOrNull ?: return null
        val threadObject = resultObject.firstObject("thread") ?: return null
        val runningBeforeMerge = threadHasKnownRunningState(threadId)
        indexTurnIds(threadId = threadId, threadObject = threadObject)
        val turnReadState = resolveTurnReadState(threadObject)
        applyTurnReadState(threadId = threadId, turnReadState = turnReadState)
        val activityState = decodeThreadActivityState(
            threadObject.firstObject("status"),
        )
        if (activityState != null && !threadHasKnownRunningState(threadId)) {
            markThreadAsRunningFallback(threadId)
        }
        val runningAfterStateResolution = threadHasKnownRunningState(threadId)
        logThreadRecovery(
            "event=mergeThreadSnapshotResponse stage=stateApplied threadId=$threadId syncState=${syncState.name} " +
                "existing=${threadSnapshotSummary(existingSnapshot)} runningBefore=$runningBeforeMerge " +
                "runningAfter=$runningAfterStateResolution turnRead=${turnReadStateSummary(turnReadState)} " +
                "activity=${threadActivitySummary(activityState)} status=${threadStatusSummary(threadObject.firstObject("status"))} " +
                "allowHistoryMergeWhileRunning=$allowHistoryMergeWhileRunning",
        )
        val decodedHistoryItems by lazy(LazyThreadSafetyMode.NONE) {
            TurnTimelineReducer.reduce(
                decodeHistoryItems(
                    threadId = threadId,
                    threadObject = threadObject,
                ),
            )
        }

        fun mergedSnapshot(baseSnapshot: ThreadSyncSnapshot?): ThreadSyncSnapshot? {
            val threadIsRunning = threadHasKnownRunningState(threadId)
            val existingItems = baseSnapshot?.timelineMutations?.let(TurnTimelineReducer::reduce).orEmpty()
            val mergedHistoryItems = if (
                shouldMergeThreadReadHistory(
                    threadIsRunning = threadIsRunning,
                    existingHasTimeline = baseSnapshot?.timelineMutations?.isNotEmpty() == true,
                    allowWhileRunning = allowHistoryMergeWhileRunning,
                )
            ) {
                ThreadHistoryReconciler.mergeHistoryItems(
                    existing = existingItems,
                    history = decodedHistoryItems,
                    threadIsActive = threadIsRunning,
                    threadIsRunning = threadIsRunning,
                )
            } else {
                existingItems
            }
            return parseThreadSnapshot(
                threadObject = threadObject,
                syncState = syncState,
                existing = baseSnapshot,
            )?.copy(
                timelineMutations = mergedHistoryItems.map(TimelineMutation::Upsert),
                isRunning = threadIsRunning,
            )
        }

        var refreshedSnapshot: ThreadSyncSnapshot? = null
        updateThread(threadId) { latestSnapshot ->
            val merged = mergedSnapshot(latestSnapshot) ?: latestSnapshot
            refreshedSnapshot = merged
            merged
        }
        if (refreshedSnapshot != null) {
            logThreadRecovery(
                "event=mergeThreadSnapshotResponse stage=updated threadId=$threadId snapshot=${threadSnapshotSummary(refreshedSnapshot)}",
            )
            return refreshedSnapshot
        }

        val createdSnapshot = mergedSnapshot(currentThreadSnapshot(threadId) ?: existingSnapshot) ?: return null
        upsertThreadSnapshot(createdSnapshot)
        logThreadRecovery(
            "event=mergeThreadSnapshotResponse stage=created threadId=$threadId snapshot=${threadSnapshotSummary(createdSnapshot)}",
        )
        return createdSnapshot
    }

    override suspend fun appendLocalSystemMessage(
        threadId: String,
        text: String,
    ) {
        val normalizedThreadId = threadId.trim()
        val normalizedText = text.trim()
        val snapshot = backingThreads.value.firstOrNull { existing -> existing.id == normalizedThreadId } ?: return
        if (normalizedThreadId.isEmpty() || normalizedText.isEmpty()) {
            return
        }
        appendTimelineMutation(
            threadId = normalizedThreadId,
            mutation = TimelineMutation.Upsert(
                timelineItem(
                    id = "system-${UUID.randomUUID()}",
                    speaker = ConversationSpeaker.SYSTEM,
                    text = normalizedText,
                    orderIndex = nextOrderIndex(snapshot),
                ),
            ),
        )
    }

    override suspend fun createThread(
        preferredProjectPath: String?,
        runtimeDefaults: RemodexRuntimeDefaults,
    ): ThreadSyncSnapshot? {
        if (!isConnected()) {
            return null
        }
        val response = sendRequestWithServiceTierFallback(
            method = "thread/start",
            accessMode = runtimeDefaults.accessMode,
            includeServiceTier = shouldIncludeServiceTier(runtimeDefaults.serviceTier),
            buildBaseParams = { includeServiceTier ->
                buildJsonObject {
                    preferredProjectPath?.trim()?.takeIf(String::isNotEmpty)?.let { put("cwd", JsonPrimitive(it)) }
                    runtimeDefaults.modelId?.takeIf(String::isNotBlank)?.let { put("model", JsonPrimitive(it)) }
                    if (includeServiceTier) {
                        runtimeDefaults.serviceTier?.let { put("serviceTier", JsonPrimitive(it.wireValue)) }
                    }
                }
            },
        )
        val threadObject = response.result
            ?.jsonObjectOrNull
            ?.firstObject("thread")
            ?: return null
        val snapshot = parseThreadSnapshot(
            threadObject = threadObject,
            syncState = RemodexThreadSyncState.LIVE,
            existing = null,
        )?.copy(
            runtimeConfig = mergeRuntimeConfig(
                existing = null,
                incoming = RemodexRuntimeConfig(
                    availableModels = availableModels.value,
                    selectedModelId = runtimeDefaults.modelId,
                    reasoningEffort = runtimeDefaults.reasoningEffort,
                    serviceTier = runtimeDefaults.serviceTier,
                ),
            ),
        ) ?: return null
        backingThreads.value = (backingThreads.value + snapshot)
            .distinctBy(ThreadSyncSnapshot::id)
            .sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
        resumedThreadIds.add(snapshot.id)
        return backingThreads.value.firstOrNull { it.id == snapshot.id } ?: snapshot
    }

    override suspend fun renameThread(
        threadId: String,
        name: String,
    ) {
        if (!isConnected()) {
            return
        }
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return
        }
        updateThread(threadId) { snapshot ->
            snapshot.copy(
                title = trimmedName,
                name = trimmedName,
            )
        }
        runCatching {
            secureConnectionCoordinator.sendRequest(
                method = "thread/name/set",
                params = buildJsonObject {
                    put("thread_id", JsonPrimitive(threadId))
                    put("name", JsonPrimitive(trimmedName))
                },
            )
        }
    }

    override suspend fun archiveThread(
        threadId: String,
        unarchive: Boolean,
    ) {
        if (!isConnected()) {
            return
        }
        val method = if (unarchive) "thread/unarchive" else "thread/archive"
        runCatching {
            secureConnectionCoordinator.sendRequest(
                method = method,
                params = buildJsonObject {
                    put("threadId", JsonPrimitive(threadId))
                },
            )
        }.onFailure { error ->
            runCatching {
                Log.w("RemodexThreadSync", "$method failed (non-fatal) for $threadId", error)
            }
        }
        updateThread(threadId) { snapshot ->
            snapshot.copy(
                syncState = if (unarchive) {
                    RemodexThreadSyncState.LIVE
                } else {
                    RemodexThreadSyncState.ARCHIVED_LOCAL
                },
            )
        }
        if (!unarchive) {
            resumedThreadIds.remove(threadId)
        }
    }

    override suspend fun deleteThread(threadId: String) {
        if (!isConnected()) {
            return
        }
        runCatching {
            secureConnectionCoordinator.sendRequest(
                method = "thread/archive",
                params = buildJsonObject {
                    put("threadId", JsonPrimitive(threadId))
                },
            )
        }.onFailure { error ->
            runCatching {
                Log.w("RemodexThreadSync", "thread/archive failed (non-fatal) for $threadId", error)
            }
        }
        activeTurnIdByThread.remove(threadId)
        resumedThreadIds.remove(threadId)
        backingThreads.value = backingThreads.value.filterNot { snapshot -> snapshot.id == threadId }
    }

    override suspend fun sendPrompt(
        threadId: String,
        prompt: String,
        runtimeConfig: RemodexRuntimeConfig,
        attachments: List<RemodexComposerAttachment>,
    ) {
        if (!isConnected()) {
            return
        }

        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty() && attachments.isEmpty()) {
            return
        }
        val hadRunningState = threadHasKnownRunningState(threadId)

        optimisticAppendUserMessage(
            threadId = threadId,
            prompt = trimmedPrompt,
            attachments = attachments,
            runtimeConfig = runtimeConfig,
        )
        if (!hadRunningState) {
            markThreadAsRunningFallback(threadId)
        }

        var imageUrlKey = "url"
        var includeCollaborationMode = shouldIncludeCollaborationMode(runtimeConfig.planningMode)
        var turnStartResponse: RpcMessage? = null
        val response = try {
            retryAfterThreadMaterialization {
                while (turnStartResponse == null) {
                    try {
                        turnStartResponse = sendRequestWithServiceTierFallback(
                            method = "turn/start",
                            accessMode = runtimeConfig.accessMode,
                            includeServiceTier = shouldIncludeServiceTier(runtimeConfig.serviceTier),
                            buildBaseParams = { includeServiceTier ->
                                buildTurnStartParams(
                                    threadId = threadId,
                                    prompt = trimmedPrompt,
                                    attachments = attachments,
                                    runtimeConfig = runtimeConfig,
                                    includeServiceTier = includeServiceTier,
                                    imageUrlKey = imageUrlKey,
                                    includeCollaborationMode = includeCollaborationMode,
                                )
                            },
                        )
                    } catch (error: Throwable) {
                        if (imageUrlKey == "url" &&
                            attachments.isNotEmpty() &&
                            shouldRetryWithImageUrlFieldFallback(error)
                        ) {
                            imageUrlKey = "image_url"
                            continue
                        }
                        if (consumeUnsupportedCollaborationMode(error, includeCollaborationMode)) {
                            includeCollaborationMode = false
                            continue
                        }
                        throw error
                    }
                }
                turnStartResponse ?: error("turn/start retry loop exited without a response")
            }
        } catch (error: Throwable) {
            if (!hadRunningState) {
                clearThreadRunningState(threadId)
                touchThread(threadId = threadId, isRunning = false)
            }
            if (shouldTreatAsThreadNotFound(error)) {
                removeLatestPendingUserMessage(
                    threadId = threadId,
                    matchingText = trimmedPrompt,
                    matchingAttachments = attachments,
                )
            }
            throw error
        }
        val turnId = extractTurnId(response.result)
        if (turnId != null) {
            setActiveTurnId(threadId = threadId, turnId = turnId)
            confirmLatestPendingUserMessage(threadId = threadId, turnId = turnId)
            beginAssistantMessage(threadId = threadId, turnId = turnId)
        } else {
            markThreadAsRunningFallback(threadId)
        }
        refreshThreads()
        hydrateThread(threadId)
    }

    override suspend fun compactThread(threadId: String) {
        if (!isConnected()) {
            return
        }

        val hadRunningState = threadHasKnownRunningState(threadId)
        try {
            retryAfterThreadMaterialization {
                secureConnectionCoordinator.sendRequest(
                    method = "thread/compact/start",
                    params = buildJsonObject {
                        put("threadId", JsonPrimitive(threadId))
                    },
                )
            }
            if (!hadRunningState) {
                markThreadAsRunningFallback(threadId)
            }
            refreshThreads()
            hydrateThread(threadId)
        } catch (error: Throwable) {
            if (!hadRunningState) {
                clearThreadRunningState(threadId)
                touchThread(threadId = threadId, isRunning = false)
            }
            throw error
        }
    }

    override suspend fun respondToStructuredUserInput(
        requestId: JsonElement,
        answersByQuestionId: Map<String, List<String>>,
    ) {
        if (!isConnected()) {
            throw IllegalStateException("Remodex is not connected. Reconnect and try sending your answer again.")
        }

        secureConnectionCoordinator.sendResponse(
            id = requestId,
            result = buildStructuredUserInputResponse(answersByQuestionId),
        )
    }

    override suspend fun approvePendingApproval(forSession: Boolean) {
        val request = backingPendingApprovalRequest.value
            ?: throw IllegalStateException("No pending approval request.")
        if (request.kind == RemodexApprovalKind.PERMISSIONS) {
            throw IllegalStateException("This request needs a permission grant response.")
        }
        val decision = if (
            forSession && (
                request.kind == RemodexApprovalKind.COMMAND ||
                    request.kind == RemodexApprovalKind.FILE_CHANGE
                )
        ) {
            "acceptForSession"
        } else {
            "accept"
        }
        secureConnectionCoordinator.sendResponse(
            id = request.requestId,
            result = buildApprovalDecisionResponse(decision),
        )
        clearPendingApprovalRequest(request)
    }

    override suspend fun declinePendingApproval() {
        val request = backingPendingApprovalRequest.value
            ?: throw IllegalStateException("No pending approval request.")
        val result = if (request.kind == RemodexApprovalKind.PERMISSIONS) {
            buildPermissionsApprovalResponse(
                permissions = buildJsonObject { },
                scope = RemodexPermissionGrantScope.TURN,
            )
        } else {
            buildApprovalDecisionResponse("decline")
        }
        secureConnectionCoordinator.sendResponse(
            id = request.requestId,
            result = result,
        )
        clearPendingApprovalRequest(request)
    }

    override suspend fun cancelPendingApproval() {
        val request = backingPendingApprovalRequest.value
            ?: throw IllegalStateException("No pending approval request.")
        if (request.kind == RemodexApprovalKind.PERMISSIONS) {
            throw IllegalStateException("Permission requests cannot stop the turn directly.")
        }
        secureConnectionCoordinator.sendResponse(
            id = request.requestId,
            result = buildApprovalDecisionResponse("cancel"),
        )
        clearPendingApprovalRequest(request)
    }

    override suspend fun grantPendingPermissionsApproval(scope: RemodexPermissionGrantScope) {
        val request = backingPendingApprovalRequest.value
            ?: throw IllegalStateException("No pending approval request.")
        if (request.kind != RemodexApprovalKind.PERMISSIONS) {
            throw IllegalStateException("This approval request does not accept permission grants.")
        }
        val permissions = requestedPermissionsPayload(request)
            ?: buildGrantedPermissionsProfile(request.requestedPermissions)
        secureConnectionCoordinator.sendResponse(
            id = request.requestId,
            result = buildPermissionsApprovalResponse(
                permissions = permissions,
                scope = scope,
            ),
        )
        clearPendingApprovalRequest(request)
    }

    override suspend fun continueOnMac(threadId: String) {
        if (!isConnected()) {
            throw IllegalStateException("Not connected to your Mac.")
        }

        val trimmedThreadId = threadId.trim()
        if (trimmedThreadId.isEmpty()) {
            throw IllegalStateException("This chat does not have a valid thread id yet.")
        }

        try {
            val response = secureConnectionCoordinator.sendRequest(
                method = "desktop/continueOnMac",
                params = buildJsonObject {
                    put("threadId", JsonPrimitive(trimmedThreadId))
                },
            )
            val succeeded = response.result
                ?.jsonObjectOrNull
                ?.firstBoolean("success") == true
            if (!succeeded) {
                throw IllegalStateException("The Mac app did not return a valid response.")
            }
        } catch (error: Throwable) {
            throw mappedDesktopHandoffError(error)
        }
    }

    private fun reviewPromptText(request: RemodexCodeReviewRequest): String {
        return when (request.target) {
            RemodexComposerReviewTarget.UNCOMMITTED_CHANGES -> "Review current changes"
            RemodexComposerReviewTarget.BASE_BRANCH -> {
                val trimmedBaseBranch = request.baseBranch?.trim().orEmpty()
                if (trimmedBaseBranch.isEmpty()) {
                    "Review against base branch"
                } else {
                    "Review against base branch $trimmedBaseBranch"
                }
            }
            RemodexComposerReviewTarget.COMMIT -> {
                val trimmedSha = request.commitSha?.trim().orEmpty()
                val trimmedTitle = request.commitTitle?.trim().orEmpty()
                when {
                    trimmedSha.isEmpty() -> "Review commit"
                    trimmedTitle.isEmpty() -> "Review commit $trimmedSha"
                    else -> "Review commit $trimmedSha: $trimmedTitle"
                }
            }
            RemodexComposerReviewTarget.CUSTOM_INSTRUCTIONS -> {
                request.customInstructions?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "Custom review instructions"
            }
        }
    }

    override suspend fun startCodeReview(
        threadId: String,
        request: RemodexCodeReviewRequest,
    ) {
        if (!isConnected()) {
            return
        }

        val prompt = reviewPromptText(request)
        val runtimeConfig = backingThreads.value.firstOrNull { it.id == threadId }?.runtimeConfig
            ?: RemodexRuntimeConfig()
        val hadRunningState = threadHasKnownRunningState(threadId)
        optimisticAppendUserMessage(
            threadId = threadId,
            prompt = prompt,
            attachments = emptyList(),
            runtimeConfig = runtimeConfig,
        )
        if (!hadRunningState) {
            markThreadAsRunningFallback(threadId)
        }
        val targetObject = when (request.target) {
            RemodexComposerReviewTarget.UNCOMMITTED_CHANGES -> buildJsonObject {
                put("type", JsonPrimitive("uncommittedChanges"))
            }

            RemodexComposerReviewTarget.BASE_BRANCH -> buildJsonObject {
                put("type", JsonPrimitive("baseBranch"))
                request.baseBranch?.trim()?.takeIf(String::isNotEmpty)?.let { put("branch", JsonPrimitive(it)) }
            }

            RemodexComposerReviewTarget.COMMIT -> buildJsonObject {
                put("type", JsonPrimitive("commit"))
                request.commitSha?.trim()?.takeIf(String::isNotEmpty)?.let { put("sha", JsonPrimitive(it)) }
                request.commitTitle?.trim()?.takeIf(String::isNotEmpty)?.let { put("title", JsonPrimitive(it)) }
            }

            RemodexComposerReviewTarget.CUSTOM_INSTRUCTIONS -> buildJsonObject {
                put("type", JsonPrimitive("custom"))
                request.customInstructions?.trim()?.takeIf(String::isNotEmpty)?.let {
                    put("instructions", JsonPrimitive(it))
                }
            }
        }
        emitReviewDebugLog(
            "stage=reviewStartRequest threadId=$threadId target=${request.target.name} baseBranch=${request.baseBranch.orEmpty()} commitSha=${request.commitSha.orEmpty()} payload=${
                sanitizeReviewDebugJson(
                    buildJsonObject {
                        put("threadId", JsonPrimitive(threadId))
                        put("delivery", JsonPrimitive("inline"))
                        put("target", targetObject)
                    }.toString(),
                )
            }",
        )
        val response = try {
            retryAfterThreadMaterialization {
                sendRequestWithApprovalPolicyFallback(
                    method = "review/start",
                    baseParams = buildJsonObject {
                        put("threadId", JsonPrimitive(threadId))
                        put("delivery", JsonPrimitive("inline"))
                        put("target", targetObject)
                    },
                    accessMode = runtimeConfig.accessMode,
                )
            }
        } catch (error: Throwable) {
            if (!hadRunningState) {
                clearThreadRunningState(threadId)
                touchThread(threadId = threadId, isRunning = false)
            }
            if (shouldTreatAsThreadNotFound(error)) {
                removeLatestPendingUserMessage(
                    threadId = threadId,
                    matchingText = prompt,
                    matchingAttachments = emptyList(),
                )
            }
            throw error
        }
        emitReviewDebugLog(
            "stage=reviewStartResponse threadId=$threadId response=${sanitizeReviewDebugJson(response.result.toString())}",
        )
        extractTurnId(response.result)?.let { turnId ->
            reviewDebugTurnIds += turnId
            reviewDebugThreadIds += threadId
            setActiveTurnId(threadId = threadId, turnId = turnId)
            confirmLatestPendingUserMessage(threadId = threadId, turnId = turnId)
        } ?: markThreadAsRunningFallback(threadId)
    }

    override suspend fun forkThread(
        threadId: String,
        destination: RemodexComposerForkDestination,
        baseBranch: String?,
    ): ThreadSyncSnapshot? {
        if (!isConnected()) {
            return null
        }
        val sourceThread = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        val runtimeConfig = sourceThread.runtimeConfig
        val targetProjectPath = when (destination) {
            RemodexComposerForkDestination.LOCAL -> {
                val localCheckoutPath = loadGitState(threadId).branches.localCheckoutPath?.trim().orEmpty()
                if (sourceThread.projectPath.isManagedWorktreePath() && localCheckoutPath.isNotEmpty()) {
                    localCheckoutPath
                } else {
                    sourceThread.projectPath
                }
            }
            RemodexComposerForkDestination.NEW_WORKTREE -> {
                val defaultBranch = loadGitBranches(threadId).defaultBranch
                val threadLabel = sourceThread.name?.trim()?.takeIf(String::isNotEmpty) ?: sourceThread.title
                val branchName = defaultForkBranchName(threadLabel)
                val worktreeResult = createWorktree(
                    threadId = threadId,
                    name = branchName,
                    baseBranch = baseBranch?.trim()?.takeIf(String::isNotEmpty) ?: defaultBranch,
                    changeTransfer = RemodexGitWorktreeChangeTransferMode.COPY,
                )
                if (worktreeResult.alreadyExisted) {
                    throw IllegalStateException(
                        "A managed worktree for '${worktreeResult.branch}' already exists. Choose a different branch name to create a fresh forked workspace."
                    )
                }
                worktreeResult.worktreePath
            }
        }
        val forkResponse = runThreadFork(
            threadId = threadId,
            projectPath = targetProjectPath,
            runtimeConfig = runtimeConfig,
        ) ?: return null
        refreshThreads()
        hydrateThread(forkResponse.id)
        return backingThreads.value.firstOrNull { snapshot -> snapshot.id == forkResponse.id } ?: forkResponse
    }

    override suspend fun forkThreadIntoProjectPath(
        threadId: String,
        projectPath: String,
    ): ThreadSyncSnapshot? {
        if (!isConnected()) {
            return null
        }
        val sourceThread = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        val forkResponse = runThreadFork(
            threadId = threadId,
            projectPath = projectPath.trim().takeIf(String::isNotEmpty),
            runtimeConfig = sourceThread.runtimeConfig,
        ) ?: return null
        refreshThreads()
        hydrateThread(forkResponse.id)
        return backingThreads.value.firstOrNull { snapshot -> snapshot.id == forkResponse.id } ?: forkResponse
    }

    override suspend fun fuzzyFileSearch(
        threadId: String,
        query: String,
    ): List<RemodexFuzzyFileMatch> {
        val root = backingThreads.value.firstOrNull { it.id == threadId }?.projectPath?.trim().orEmpty()
        if (!isConnected() || query.isBlank() || root.isEmpty()) {
            return emptyList()
        }
        val response = secureConnectionCoordinator.sendRequest(
            method = "fuzzyFileSearch",
            params = buildJsonObject {
                put("query", JsonPrimitive(query.trim()))
                put("roots", buildJsonArray { add(JsonPrimitive(root)) })
                put("cancellationToken", JsonNull)
            },
        )
        val files = response.result?.jsonObjectOrNull?.firstArray("files").orEmpty()
        return files.mapNotNull { value ->
            val fileObject = value.jsonObjectOrNull ?: return@mapNotNull null
            val path = fileObject.firstString("path").orEmpty()
            val resolvedRoot = fileObject.firstString("root") ?: root
            val fileName = fileObject.firstString("fileName", "file_name")
                ?: path.substringAfterLast('/').substringAfterLast('\\')
            if (path.isBlank() || fileName.isBlank()) {
                return@mapNotNull null
            }
            RemodexFuzzyFileMatch(
                root = resolvedRoot,
                path = normalizeFuzzyFilePath(path = path, root = resolvedRoot),
                fileName = fileName,
                score = fileObject.firstDouble("score") ?: 0.0,
                indices = fileObject.firstArray("indices").orEmpty().mapNotNull { indexValue ->
                    (indexValue as? JsonPrimitive)?.content?.toIntOrNull()
                },
            )
        }
    }

    override suspend fun listSkills(
        threadId: String,
        forceReload: Boolean,
    ): List<RemodexSkillMetadata> {
        val root = backingThreads.value.firstOrNull { it.id == threadId }?.projectPath?.trim().orEmpty()
        if (!isConnected() || root.isEmpty()) {
            return emptyList()
        }
        val response = runCatching {
            secureConnectionCoordinator.sendRequest(
                method = "skills/list",
                params = buildJsonObject {
                    put("cwds", buildJsonArray { add(JsonPrimitive(root)) })
                    if (forceReload) {
                        put("forceReload", JsonPrimitive(true))
                    }
                },
            )
        }.recoverCatching {
            secureConnectionCoordinator.sendRequest(
                method = "skills/list",
                params = buildJsonObject {
                    put("cwd", JsonPrimitive(root))
                    if (forceReload) {
                        put("forceReload", JsonPrimitive(true))
                    }
                },
            )
        }.getOrThrow()
        return decodeSkillMetadata(response.result)
            .distinctBy { skill -> skill.id }
            .sortedBy { skill -> skill.name.lowercase(Locale.ROOT) }
    }

    override suspend fun loadGitState(threadId: String): RemodexGitState {
        if (!isConnected()) {
            return RemodexGitState()
        }
        val combinedResult = runCatching {
            runGitRequest(threadId = threadId, method = "git/branchesWithStatus")
                .result
                ?.jsonObjectOrNull
        }.getOrNull()
        if (combinedResult != null) {
            return RemodexGitState(
                sync = combinedResult.firstObject("status")?.let(::decodeGitRepoSync),
                branches = decodeGitBranches(combinedResult),
            )
        }

        return RemodexGitState(sync = loadGitSync(threadId), branches = loadGitBranches(threadId))
    }

    override suspend fun loadGitDiff(threadId: String): RemodexGitRepoDiff {
        if (!isConnected()) {
            return RemodexGitRepoDiff()
        }
        val resultObject = runGitRequest(threadId = threadId, method = "git/diff")
            .result
            ?.jsonObjectOrNull
            ?: return RemodexGitRepoDiff()
        return RemodexGitRepoDiff(
            patch = resultObject.firstString("patch").orEmpty(),
        )
    }

    override suspend fun loadGitCommits(threadId: String): List<RemodexGitCommit> {
        if (!isConnected()) {
            return emptyList()
        }
        val commits = runGitRequest(threadId = threadId, method = "git/log")
            .result
            ?.jsonObjectOrNull
            ?.firstArray("commits")
            .orEmpty()
        return commits.mapNotNull { value ->
            val commitObject = value.jsonObjectOrNull ?: return@mapNotNull null
            val sha = commitObject.firstString("hash", "sha").orEmpty().trim()
            if (sha.isEmpty()) {
                return@mapNotNull null
            }
            RemodexGitCommit(
                sha = sha,
                message = commitObject.firstString("message", "subject").orEmpty(),
                author = commitObject.firstString("author").orEmpty(),
                date = commitObject.firstString("date").orEmpty(),
            )
        }
    }

    override suspend fun checkoutGitBranch(
        threadId: String,
        branch: String,
    ): RemodexGitState {
        runGitRequest(
            threadId = threadId,
            method = "git/checkout",
            params = buildJsonObject {
                put("branch", JsonPrimitive(branch))
            },
        )
        return loadGitState(threadId).copy(lastActionMessage = "Switched to $branch.")
    }

    override suspend fun createGitBranch(
        threadId: String,
        branch: String,
    ): RemodexGitState {
        runGitRequest(
            threadId = threadId,
            method = "git/createBranch",
            params = buildJsonObject {
                put("name", JsonPrimitive(branch))
            },
        )
        return loadGitState(threadId).copy(lastActionMessage = "Created branch $branch.")
    }

    override suspend fun createGitWorktree(
        threadId: String,
        name: String,
        baseBranch: String?,
        changeTransfer: RemodexGitWorktreeChangeTransferMode,
    ): RemodexGitState {
        val worktreeResult = createWorktree(
            threadId = threadId,
            name = name,
            baseBranch = baseBranch,
            changeTransfer = changeTransfer,
        )
        return loadGitState(threadId).copy(
            lastActionMessage = if (worktreeResult.alreadyExisted) {
                "Opened existing worktree for ${worktreeResult.branch}."
            } else {
                "Created worktree ${worktreeResult.branch}."
            },
        )
    }

    override suspend fun createGitWorktreeResult(
        threadId: String,
        name: String,
        baseBranch: String?,
        changeTransfer: RemodexGitWorktreeChangeTransferMode,
    ): RemodexGitWorktreeResult {
        return createWorktree(
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
        val result = runGitRequest(
            threadId = threadId,
            method = "git/commit",
            params = buildJsonObject {
                message?.trim()?.takeIf(String::isNotEmpty)?.let { put("message", JsonPrimitive(it)) }
            },
        )
        val summary = result.result?.jsonObjectOrNull?.firstString("summary")
        return loadGitState(threadId).copy(
            lastActionMessage = summary ?: "Committed the current local changes.",
        )
    }

    override suspend fun commitAndPushGitChanges(
        threadId: String,
        message: String?,
    ): RemodexGitState {
        val commitState = commitGitChanges(threadId = threadId, message = message)
        val pushedState = pushGitChanges(threadId)
        return pushedState.copy(
            lastActionMessage = when {
                !pushedState.lastActionMessage.isNullOrBlank() -> "Committed and pushed the current branch."
                !commitState.lastActionMessage.isNullOrBlank() -> "Committed and pushed the current branch."
                else -> "Committed and pushed the current branch."
            },
        )
    }

    override suspend fun pullGitChanges(threadId: String): RemodexGitState {
        runGitRequest(threadId = threadId, method = "git/pull")
        return loadGitState(threadId).copy(lastActionMessage = "Pulled the latest remote changes.")
    }

    override suspend fun pushGitChanges(threadId: String): RemodexGitState {
        runGitRequest(threadId = threadId, method = "git/push")
        return loadGitState(threadId).copy(lastActionMessage = "Pushed the current branch.")
    }

    override suspend fun discardRuntimeChangesAndSync(threadId: String): RemodexGitState {
        runGitRequest(
            threadId = threadId,
            method = "git/resetToRemote",
            params = buildJsonObject {
                put("confirm", JsonPrimitive("discard_runtime_changes"))
            },
        )
        return loadGitState(threadId).copy(lastActionMessage = "Discarded local runtime changes and synced from remote.")
    }

    override suspend fun loadGitRemoteUrl(threadId: String): RemodexGitRemoteUrl {
        val resultObject = runGitRequest(threadId = threadId, method = "git/remoteUrl")
            .result
            ?.jsonObjectOrNull
            ?: return RemodexGitRemoteUrl()
        return RemodexGitRemoteUrl(
            url = resultObject.firstString("url").orEmpty(),
            ownerRepo = resultObject.firstString("ownerRepo"),
        )
    }

    override suspend fun previewAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertPreviewResult {
        val projectPath = backingThreads.value.firstOrNull { it.id == threadId }?.projectPath?.trim().orEmpty()
        require(projectPath.isNotEmpty()) {
            "The selected local folder is not available on this Mac."
        }
        val normalizedPatch = RemodexUnifiedPatchParser.normalize(forwardPatch)
            ?: throw IllegalStateException("This response cannot be auto-reverted because no exact patch was captured.")
        val response = secureConnectionCoordinator.sendRequest(
            method = "workspace/revertPatchPreview",
            params = buildJsonObject {
                put("cwd", JsonPrimitive(projectPath))
                put("forwardPatch", JsonPrimitive(normalizedPatch))
            },
        )
        return decodeRevertPreview(response.result?.jsonObjectOrNull)
    }

    override suspend fun applyAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertApplyResult {
        val projectPath = backingThreads.value.firstOrNull { it.id == threadId }?.projectPath?.trim().orEmpty()
        require(projectPath.isNotEmpty()) {
            "The selected local folder is not available on this Mac."
        }
        val normalizedPatch = RemodexUnifiedPatchParser.normalize(forwardPatch)
            ?: throw IllegalStateException("This response cannot be auto-reverted because no exact patch was captured.")
        val response = secureConnectionCoordinator.sendRequest(
            method = "workspace/revertPatchApply",
            params = buildJsonObject {
                put("cwd", JsonPrimitive(projectPath))
                put("forwardPatch", JsonPrimitive(normalizedPatch))
            },
        )
        val applyResult = decodeRevertApply(response.result?.jsonObjectOrNull)
        if (applyResult.success) {
            val now = nowEpochMs()
            updateThread(threadId) { snapshot ->
                snapshot.copy(
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                    timelineMutations = snapshot.timelineMutations + TimelineMutation.Upsert(
                        timelineItem(
                            id = "assistant-revert-$now",
                            speaker = ConversationSpeaker.SYSTEM,
                            text = "Reverted changes from this response.",
                            orderIndex = nextOrderIndex(snapshot),
                        ),
                    ),
                )
            }
        }
        return applyResult
    }

    override suspend fun stopTurn(threadId: String) {
        if (!isConnected()) {
            return
        }

        val interruptTurnId = activeTurnIdByThread[threadId] ?: runThreadRead(threadId)
            ?.result
            ?.jsonObjectOrNull
            ?.firstObject("thread")
            ?.let(::resolveInterruptibleTurnId)

        val params = buildJsonObject {
            put("threadId", JsonPrimitive(threadId))
            if (!interruptTurnId.isNullOrBlank()) {
                put("turnId", JsonPrimitive(interruptTurnId))
            }
        }
        secureConnectionCoordinator.sendRequest(
            method = "turn/interrupt",
            params = params,
        )
        clearThreadRunningState(threadId)
        backingThreads.value = backingThreads.value.map { snapshot ->
            if (snapshot.id == threadId) {
                snapshot.withResolvedLiveThreadState(
                    threadId = threadId,
                    isRunning = false,
                )
            } else {
                snapshot
            }
        }
        refreshThreads()
        hydrateThread(threadId)
    }

    private suspend fun initializeSession(): Boolean {
        val clientInfo = buildJsonObject {
            put("name", JsonPrimitive("codexmobile_android"))
            put("title", JsonPrimitive("CodexMobile Android"))
            put("version", JsonPrimitive(appVersionName))
        }
        val modernParams = buildJsonObject {
            put("clientInfo", clientInfo)
            put(
                "capabilities",
                buildJsonObject {
                    put("experimentalApi", JsonPrimitive(true))
                },
            )
        }

        val initialized = runCatching {
            secureConnectionCoordinator.sendRequest(
                method = "initialize",
                params = modernParams,
            )
        }.recoverCatching {
            secureConnectionCoordinator.sendRequest(
                method = "initialize",
                params = buildJsonObject {
                    put("clientInfo", clientInfo)
                },
            )
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                if (error is CancellationException || error is SecureTransportException) {
                    Log.d(logTag, "initializeSession skipped: ${error.message.orEmpty()}")
                    false
                } else {
                    throw error
                }
            },
        )

        if (!initialized) {
            return false
        }

        return runCatching {
            secureConnectionCoordinator.sendNotification(
                method = "initialized",
                params = null,
            )
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                if (error is CancellationException || error is SecureTransportException) {
                    Log.d(logTag, "initialized notification skipped: ${error.message.orEmpty()}")
                    false
                } else {
                    throw error
                }
            },
        )
    }

    private suspend fun refreshAvailableModels() {
        if (!isConnected()) {
            return
        }

        val response = runCatching {
            secureConnectionCoordinator.sendRequest(
                method = "model/list",
                params = buildJsonObject {
                    put("cursor", JsonNull)
                    put("limit", JsonPrimitive(50))
                    put("includeHidden", JsonPrimitive(false))
                },
            )
        }.getOrNull() ?: return

        val resultObject = response.result?.jsonObjectOrNull ?: return
        val items = resultObject.firstArray("items", "data", "models").orEmpty()
        val parsedModels = items.mapNotNull(::parseModelOption)
        if (parsedModels.isEmpty()) {
            return
        }

        backingAvailableModels.value = parsedModels
    }

    private suspend fun listThreads(archived: Boolean): List<ThreadSyncSnapshot> {
        val sourceKinds = JsonArray(
            listOf("cli", "vscode", "appServer", "exec", "unknown").map(::JsonPrimitive),
        )
        val params = buildJsonObject {
            put("cursor", JsonNull)
            put("limit", JsonPrimitive(100))
            put("sourceKinds", sourceKinds)
            if (archived) {
                put("archived", JsonPrimitive(true))
            }
        }
        val response = secureConnectionCoordinator.sendRequest(
            method = "thread/list",
            params = params,
        )
        val resultObject = response.result?.jsonObjectOrNull ?: return emptyList()
        val threadValues = resultObject.firstArray("data", "items", "threads").orEmpty()
        return threadValues.mapNotNull { value ->
            parseThreadSnapshot(
                threadObject = value.jsonObjectOrNull ?: return@mapNotNull null,
                syncState = if (archived) RemodexThreadSyncState.ARCHIVED_LOCAL else RemodexThreadSyncState.LIVE,
                existing = backingThreads.value.firstOrNull { snapshot ->
                    snapshot.id == value.jsonObjectOrNull?.firstString("id")
                },
            )
        }
    }

    private suspend fun runThreadRead(threadId: String): RpcMessage? {
        return runCatching { requestThreadRead(threadId) }.getOrNull()
    }

    private suspend fun requestThreadRead(threadId: String): RpcMessage {
        val camelParams = buildJsonObject {
            put("threadId", JsonPrimitive(threadId))
            put("includeTurns", JsonPrimitive(true))
        }
        return runCatching {
            secureConnectionCoordinator.sendRequest(
                method = "thread/read",
                params = camelParams,
            )
        }.recoverCatching {
            secureConnectionCoordinator.sendRequest(
                method = "thread/read",
                params = buildJsonObject {
                    put("thread_id", JsonPrimitive(threadId))
                    put("include_turns", JsonPrimitive(true))
                },
            )
        }.getOrThrow()
    }

    private suspend fun runThreadResume(
        threadId: String,
        preferredProjectPath: String?,
        modelIdentifier: String?,
    ): RpcMessage? {
        val params = buildJsonObject {
            put("threadId", JsonPrimitive(threadId))
            preferredProjectPath?.trim()?.takeIf(String::isNotEmpty)?.let { put("cwd", JsonPrimitive(it)) }
            modelIdentifier?.trim()?.takeIf(String::isNotEmpty)?.let { put("model", JsonPrimitive(it)) }
        }
        return secureConnectionCoordinator.sendRequest(
            method = "thread/resume",
            params = params,
        )
    }

    private suspend fun loadGitSync(threadId: String): RemodexGitRepoSync? {
        val resultObject = runGitRequest(threadId = threadId, method = "git/status")
            .result
            ?.jsonObjectOrNull
            ?: return null
        return decodeGitRepoSync(resultObject)
    }

    private fun decodeGitRepoSync(resultObject: JsonObject): RemodexGitRepoSync {
        return RemodexGitRepoSync(
            repoRoot = resultObject.firstString("repoRoot"),
            currentBranch = resultObject.firstString("branch"),
            trackingBranch = resultObject.firstString("tracking"),
            isDirty = resultObject.firstBoolean("dirty") ?: false,
            aheadCount = resultObject.firstInt("ahead") ?: 0,
            behindCount = resultObject.firstInt("behind") ?: 0,
            localOnlyCommitCount = resultObject.firstInt("localOnlyCommitCount") ?: 0,
            state = resultObject.firstString("state") ?: "up_to_date",
            canPush = resultObject.firstBoolean("canPush") ?: false,
            isPublishedToRemote = resultObject.firstBoolean("publishedToRemote") ?: false,
            files = resultObject.firstArray("files").orEmpty().mapNotNull { value ->
                val fileObject = value.jsonObjectOrNull ?: return@mapNotNull null
                val path = fileObject.firstString("path").orEmpty()
                if (path.isBlank()) {
                    return@mapNotNull null
                }
                RemodexGitChangedFile(
                    path = path,
                    status = fileObject.firstString("status").orEmpty(),
                )
            },
            diffTotals = resultObject.firstObject("diff")?.let { diffObject ->
                RemodexGitDiffTotals(
                    additions = diffObject.firstInt("additions") ?: 0,
                    deletions = diffObject.firstInt("deletions") ?: 0,
                    binaryFiles = diffObject.firstInt("binaryFiles") ?: 0,
                ).takeIf(RemodexGitDiffTotals::hasChanges)
            },
        )
    }

    private suspend fun loadGitBranches(threadId: String): RemodexGitBranches {
        val resultObject = runGitRequest(threadId = threadId, method = "git/branches")
            .result
            ?.jsonObjectOrNull
            ?: return RemodexGitBranches()
        return decodeGitBranches(resultObject)
    }

    private fun decodeGitBranches(resultObject: JsonObject): RemodexGitBranches {
        return RemodexGitBranches(
            branches = resultObject.firstArray("branches").orEmpty().mapNotNull { value ->
                (value as? JsonPrimitive)?.content?.trim().takeIf { !it.isNullOrEmpty() }
            },
            branchesCheckedOutElsewhere = resultObject.firstArray("branchesCheckedOutElsewhere").orEmpty()
                .mapNotNull { value -> (value as? JsonPrimitive)?.content?.trim().takeIf { !it.isNullOrEmpty() } }
                .toSet(),
            worktreePathByBranch = resultObject.firstObject("worktreePathByBranch")
                ?.entries
                ?.mapNotNull { (key, value) ->
                    value.stringOrNull?.takeIf(String::isNotEmpty)?.let { path -> key to path }
                }
                ?.toMap()
                .orEmpty(),
            localCheckoutPath = resultObject.firstString("localCheckoutPath"),
            currentBranch = resultObject.firstString("current"),
            defaultBranch = resultObject.firstString("default"),
        )
    }

    private suspend fun createWorktree(
        threadId: String,
        name: String,
        baseBranch: String?,
        changeTransfer: RemodexGitWorktreeChangeTransferMode,
    ): RemodexGitWorktreeResult {
        val resultObject = runGitRequest(
            threadId = threadId,
            method = "git/createWorktree",
            params = buildJsonObject {
                put("name", JsonPrimitive(name))
                baseBranch?.trim()?.takeIf(String::isNotEmpty)?.let { put("baseBranch", JsonPrimitive(it)) }
                put("changeTransfer", JsonPrimitive(changeTransfer.name.lowercase(Locale.ROOT)))
            },
        ).result?.jsonObjectOrNull ?: throw IllegalStateException("git/createWorktree response missing result.")
        return RemodexGitWorktreeResult(
            branch = resultObject.firstString("branch").orEmpty(),
            worktreePath = resultObject.firstString("worktreePath").orEmpty(),
            alreadyExisted = resultObject.firstBoolean("alreadyExisted") ?: false,
        )
    }

    private suspend fun runThreadFork(
        threadId: String,
        projectPath: String?,
        runtimeConfig: RemodexRuntimeConfig,
    ): ThreadSyncSnapshot? {
        if (!shouldIncludeThreadFork()) {
            markThreadForkUnsupportedForCurrentBridge()
            throw IllegalStateException(threadForkBridgeUpdatePrompt().message)
        }
        val normalizedProjectPath = projectPath?.trim()?.takeIf(String::isNotEmpty)
        var includeServiceTier = shouldIncludeServiceTier(runtimeConfig.serviceTier)
        var includeSandbox = true
        var useMinimalParams = false
        while (true) {
            val baseParams = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
                if (!useMinimalParams) {
                    normalizedProjectPath?.let { put("cwd", JsonPrimitive(it)) }
                    runtimeConfig.selectedModelId?.takeIf(String::isNotBlank)?.let { put("model", JsonPrimitive(it)) }
                    if (includeServiceTier) {
                        runtimeConfig.serviceTier?.let { put("serviceTier", JsonPrimitive(it.wireValue)) }
                    }
                    if (includeSandbox) {
                        put("sandbox", JsonPrimitive(runtimeConfig.accessMode.sandboxLegacyValue))
                    }
                }
            }
            try {
                val response = sendRequestWithApprovalPolicyFallback(
                    method = "thread/fork",
                    baseParams = baseParams,
                    accessMode = runtimeConfig.accessMode,
                )
                return handleThreadForkResponse(
                    response = response,
                    sourceRuntimeConfig = runtimeConfig,
                    fallbackProjectPath = normalizedProjectPath,
                    usesPostForkResumeOverrides = useMinimalParams,
                )
            } catch (error: Throwable) {
                val message = error.message.orEmpty().lowercase(Locale.ROOT)
                when {
                    consumeUnsupportedServiceTier(error, includeServiceTier) -> {
                        includeServiceTier = false
                    }

                    consumeUnsupportedThreadFork(error) -> {
                        throw error
                    }

                    includeSandbox && (message.contains("sandbox") || message.contains("approvalpolicy")) -> {
                        includeSandbox = false
                    }

                    consumeUnsupportedThreadForkOverrides(error, useMinimalParams) -> {
                        useMinimalParams = true
                    }

                    else -> throw error
                }
            }
        }
    }

    private fun handleThreadForkResponse(
        response: RpcMessage,
        sourceRuntimeConfig: RemodexRuntimeConfig,
        fallbackProjectPath: String?,
        usesPostForkResumeOverrides: Boolean,
    ): ThreadSyncSnapshot? {
        val threadObject = response.result?.jsonObjectOrNull?.firstObject("thread") ?: return null
        val parsedSnapshot = parseThreadSnapshot(
            threadObject = threadObject,
            syncState = RemodexThreadSyncState.LIVE,
            existing = null,
        ) ?: return null
        val resolvedProjectPath = when {
            usesPostForkResumeOverrides && fallbackProjectPath != null -> fallbackProjectPath
            parsedSnapshot.projectPath.isBlank() && fallbackProjectPath != null -> fallbackProjectPath
            else -> parsedSnapshot.projectPath
        }
        val mergedSnapshot = parsedSnapshot.copy(
            projectPath = resolvedProjectPath,
            runtimeConfig = sourceRuntimeConfig.copy(
                availableModels = if (parsedSnapshot.runtimeConfig.availableModels.isNotEmpty()) {
                    parsedSnapshot.runtimeConfig.availableModels
                } else {
                    sourceRuntimeConfig.availableModels
                },
                availableReasoningEfforts = if (parsedSnapshot.runtimeConfig.availableReasoningEfforts.isNotEmpty()) {
                    parsedSnapshot.runtimeConfig.availableReasoningEfforts
                } else {
                    sourceRuntimeConfig.availableReasoningEfforts
                },
                availableServiceTiers = if (parsedSnapshot.runtimeConfig.availableServiceTiers.isNotEmpty()) {
                    parsedSnapshot.runtimeConfig.availableServiceTiers
                } else {
                    sourceRuntimeConfig.availableServiceTiers
                },
                selectedModelId = parsedSnapshot.runtimeConfig.selectedModelId ?: sourceRuntimeConfig.selectedModelId,
                reasoningEffort = parsedSnapshot.runtimeConfig.reasoningEffort ?: sourceRuntimeConfig.reasoningEffort,
                serviceTier = parsedSnapshot.runtimeConfig.serviceTier ?: sourceRuntimeConfig.serviceTier,
            ).normalizeSelections(),
        )
        resumedThreadIds.add(mergedSnapshot.id)
        upsertThreadSnapshot(mergedSnapshot)
        return backingThreads.value.firstOrNull { snapshot -> snapshot.id == mergedSnapshot.id } ?: mergedSnapshot
    }

    private suspend fun runGitRequest(
        threadId: String,
        method: String,
        params: JsonObject = buildJsonObject {},
    ): RpcMessage {
        val projectPath = backingThreads.value.firstOrNull { it.id == threadId }?.projectPath?.trim().orEmpty()
        require(projectPath.isNotEmpty()) { "Missing local working directory for $method." }
        return try {
            secureConnectionCoordinator.sendRequest(
                method = method,
                params = JsonObject(params + ("cwd" to JsonPrimitive(projectPath))),
            )
        } catch (error: Throwable) {
            throw mappedGitRequestError(error)
        }
    }

    private fun mappedGitRequestError(error: Throwable): Throwable {
        val rpcError = error as? RpcError ?: return error
        val errorCode = rpcError.data?.jsonObjectOrNull?.firstString("errorCode")
        val message = gitUserFacingMessage(errorCode, rpcError.message)
        return IllegalStateException(message, rpcError)
    }

    private fun mappedDesktopHandoffError(error: Throwable): Throwable {
        val rpcError = error as? RpcError ?: return error
        val errorCode = rpcError.data?.jsonObjectOrNull?.firstString("errorCode")
        val message = desktopHandoffUserFacingMessage(errorCode, rpcError.message)
        return IllegalStateException(message, rpcError)
    }

    private fun gitUserFacingMessage(errorCode: String?, fallback: String): String {
        return when (errorCode) {
            "nothing_to_commit" -> "Nothing to commit."
            "nothing_to_push" -> "Nothing to push."
            "push_rejected" -> "Push rejected. Pull changes first."
            "branch_is_main" -> "Cannot operate on the main branch."
            "protected_branch" -> "This branch is protected."
            "branch_behind_remote" -> "Branch is behind remote. Pull first."
            "dirty_and_behind" -> "Uncommitted changes and branch is behind remote."
            "checkout_conflict_dirty_tree" -> "Cannot switch branches: you have uncommitted changes."
            "checkout_branch_in_other_worktree" -> "Cannot switch branches: this branch is already open in another worktree."
            "pull_conflict" -> "Pull failed due to conflicts."
            "branch_exists" -> fallback.ifBlank { "Branch already exists." }
            "invalid_branch_name" -> fallback.ifBlank { "Branch name is not valid for Git." }
            "missing_branch", "missing_branch_name" -> "Branch name is required."
            "missing_base_branch" -> fallback.ifBlank { "Base branch is required." }
            "branch_already_open_here" -> fallback.ifBlank { "This branch is already open in the current project." }
            "branch_in_other_worktree" -> fallback.ifBlank { "This branch is already open in another worktree." }
            "confirmation_required" -> "Confirmation is required for this action."
            "stash_pop_conflict" -> "Stash pop failed due to conflicts."
            "missing_working_directory" -> fallback.ifBlank { "The selected local folder is not available on this Mac." }
            "cannot_remove_local_checkout" -> fallback.ifBlank { "Cannot remove the main local checkout." }
            "unmanaged_worktree" -> fallback.ifBlank { "Only managed worktrees can be cleaned up automatically." }
            "worktree_cleanup_failed" -> fallback.ifBlank { "We could not clean up the temporary worktree automatically." }
            else -> fallback
        }
    }

    private fun desktopHandoffUserFacingMessage(errorCode: String?, fallback: String): String {
        return when (errorCode) {
            "missing_thread_id" -> "This chat does not have a valid thread id yet."
            "unsupported_platform" -> "Mac handoff works only when the bridge is running on macOS."
            "handoff_failed" -> fallback.ifBlank { "Could not relaunch Codex.app on your Mac." }
            else -> fallback.ifBlank { "Could not continue this chat on your Mac." }
        }
    }

    private fun String.isManagedWorktreePath(): Boolean {
        val normalized = trim().replace('\\', '/')
        return normalized.contains("/.codex/worktrees/") || normalized.contains("/.codex/worktrees")
    }

    private fun normalizeFuzzyFilePath(
        path: String,
        root: String,
    ): String {
        if (path.isBlank()) {
            return path
        }
        val normalizedRoot = root.trimEnd('/', '\\')
        val normalizedPath = path.trim()
        return when {
            normalizedRoot.isEmpty() -> normalizedPath
            normalizedPath.startsWith(normalizedRoot) -> normalizedPath
            normalizedPath.startsWith("/") -> normalizedPath
            else -> "$normalizedRoot/$normalizedPath"
        }
    }

    private fun decodeSkillMetadata(result: JsonElement?): List<RemodexSkillMetadata> {
        val resultObject = result?.jsonObjectOrNull ?: return emptyList()
        val collected = mutableListOf<RemodexSkillMetadata>()
        val dataItems = resultObject.firstArray("data").orEmpty()
        dataItems.forEach { value ->
            val itemObject = value.jsonObjectOrNull
            val skills = itemObject?.firstArray("skills")
            if (skills != null) {
                collected += decodeSkillArray(skills)
            }
        }
        if (collected.isEmpty() && dataItems.isNotEmpty()) {
            collected += decodeSkillArray(JsonArray(dataItems))
        }
        if (collected.isEmpty()) {
            resultObject.firstArray("skills")?.let { skills ->
                collected += decodeSkillArray(skills)
            }
        }
        return collected
    }

    private fun decodeSkillArray(values: JsonArray): List<RemodexSkillMetadata> {
        return values.mapNotNull { value ->
            val objectValue = value.jsonObjectOrNull ?: return@mapNotNull null
            val name = objectValue.firstString("name", "id").orEmpty().trim()
            if (name.isEmpty()) {
                return@mapNotNull null
            }
            RemodexSkillMetadata(
                name = name,
                description = objectValue.firstString("description"),
                path = objectValue.firstString("path"),
                scope = objectValue.firstString("scope"),
                enabled = objectValue.firstBoolean("enabled") ?: true,
            )
        }
    }

    private fun defaultForkBranchName(title: String): String {
        val slug = title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "conversation" }
        return "remodex/$slug-${nowEpochMs().toString().takeLast(6)}"
    }

    private suspend fun sendRequestWithApprovalPolicyFallback(
        method: String,
        baseParams: JsonObject,
        accessMode: RemodexAccessMode,
    ): RpcMessage {
        var lastError: Throwable? = null
        val policies = accessMode.approvalPolicyCandidates
        for ((index, policy) in policies.withIndex()) {
            val params = JsonObject(baseParams + ("approvalPolicy" to JsonPrimitive(policy)))
            try {
                return secureConnectionCoordinator.sendRequest(method = method, params = params)
            } catch (error: Throwable) {
                lastError = error
                val hasMorePolicies = index < (policies.lastIndex)
                if (hasMorePolicies && shouldRetryWithApprovalPolicyFallback(error)) {
                    continue
                }
                throw error
            }
        }
        throw lastError ?: IllegalStateException("$method failed without a concrete transport error.")
    }

    private suspend fun sendRequestWithServiceTierFallback(
        method: String,
        accessMode: RemodexAccessMode,
        includeServiceTier: Boolean,
        buildBaseParams: (Boolean) -> JsonObject,
    ): RpcMessage {
        var shouldIncludeServiceTier = includeServiceTier
        while (true) {
            try {
                return sendRequestWithApprovalPolicyFallback(
                    method = method,
                    baseParams = buildBaseParams(shouldIncludeServiceTier),
                    accessMode = accessMode,
                )
            } catch (error: Throwable) {
                if (consumeUnsupportedServiceTier(error, shouldIncludeServiceTier)) {
                    shouldIncludeServiceTier = false
                    continue
                }
                throw error
            }
        }
    }

    private fun shouldIncludeServiceTier(serviceTier: RemodexServiceTier?): Boolean {
        return supportsServiceTier && serviceTier != null
    }

    private fun shouldIncludeCollaborationMode(planningMode: RemodexPlanningMode): Boolean {
        return supportsTurnCollaborationMode && (
            planningMode == RemodexPlanningMode.PLAN ||
                planningMode == RemodexPlanningMode.AUTO
            )
    }

    private fun consumeUnsupportedServiceTier(
        error: Throwable,
        includeServiceTier: Boolean,
    ): Boolean {
        if (!includeServiceTier || !shouldRetryWithoutServiceTier(error)) {
            return false
        }
        supportsServiceTier = false
        markServiceTierUnsupportedForCurrentBridge()
        return true
    }

    private fun shouldRetryWithoutServiceTier(error: Throwable): Boolean {
        val rpcError = error as? RpcError ?: return false
        if (rpcError.code != -32600 && rpcError.code != -32602) {
            return false
        }
        val message = rpcError.message.lowercase(Locale.ROOT)
        return message.contains("servicetier")
            || message.contains("service tier")
            || message.contains("unknown field")
            || message.contains("unexpected field")
            || message.contains("unrecognized field")
            || message.contains("invalid param")
            || message.contains("invalid params")
    }

    private fun shouldIncludeThreadFork(): Boolean {
        return supportsThreadForkCapability
    }

    private fun consumeUnsupportedThreadForkOverrides(
        error: Throwable,
        useMinimalParams: Boolean,
    ): Boolean {
        if (useMinimalParams || !shouldRetryThreadForkWithoutOverrides(error)) {
            return false
        }
        return true
    }

    private fun shouldRetryThreadForkWithoutOverrides(error: Throwable): Boolean {
        val rpcError = error as? RpcError ?: return false
        if (rpcError.code != -32600 && rpcError.code != -32602 && rpcError.code != -32000) {
            return false
        }
        val message = rpcError.message.lowercase(Locale.ROOT)
        val mentionsUnknownField = message.contains("unknown field")
            || message.contains("unexpected field")
            || message.contains("unrecognized field")
        val mentionsInvalidNamedField = (message.contains("invalid param") || message.contains("invalid params")) &&
            (message.contains("field") || message.contains("parameter") || message.contains("param"))
        val mentionsForkOverride = message.contains("cwd")
            || message.contains("modelprovider")
            || message.contains("model provider")
            || message.contains("model")
            || message.contains("sandbox")
            || message.contains("servicetier")
            || message.contains("service tier")
        return (mentionsUnknownField || mentionsInvalidNamedField) && mentionsForkOverride
    }

    private fun consumeUnsupportedThreadFork(error: Throwable): Boolean {
        if (!shouldTreatAsUnsupportedThreadFork(error)) {
            return false
        }
        markThreadForkUnsupportedForCurrentBridge()
        return true
    }

    private fun shouldTreatAsUnsupportedThreadFork(error: Throwable): Boolean {
        val rpcError = error as? RpcError ?: return false
        if (rpcError.code == -32601) {
            return true
        }
        val message = rpcError.message.lowercase(Locale.ROOT)
        val mentionsUnsupportedMethod = message.contains("method not found")
            || message.contains("unknown method")
            || message.contains("not implemented")
            || message.contains("does not support")
        val mentionsForkSpecificUnsupported = (message.contains("thread/fork") || message.contains("thread fork")) &&
            (message.contains("unsupported") || message.contains("not supported"))
        if (rpcError.code != -32600 && rpcError.code != -32602 && rpcError.code != -32000) {
            return mentionsUnsupportedMethod || mentionsForkSpecificUnsupported
        }
        return mentionsUnsupportedMethod || mentionsForkSpecificUnsupported
    }

    private fun markServiceTierUnsupportedForCurrentBridge() {
        supportsServiceTier = false
        if (hasPresentedServiceTierBridgeUpdatePrompt) {
            return
        }
        hasPresentedServiceTierBridgeUpdatePrompt = true
        backingBridgeUpdatePrompt.value = serviceTierBridgeUpdatePrompt()
    }

    private fun markThreadForkUnsupportedForCurrentBridge() {
        supportsThreadForkCapability = false
        backingSupportsThreadFork.value = false
        if (hasPresentedThreadForkBridgeUpdatePrompt) {
            return
        }
        hasPresentedThreadForkBridgeUpdatePrompt = true
        backingBridgeUpdatePrompt.value = threadForkBridgeUpdatePrompt()
    }

    private fun serviceTierBridgeUpdatePrompt(): RemodexBridgeUpdatePrompt {
        return RemodexBridgeUpdatePrompt(
            title = "Update Remodex on your Mac to use Speed controls",
            message = "This Mac bridge does not support the selected speed setting yet. Update the Remodex npm package to use Fast Mode and other speed controls.",
            command = remodexBridgeUpdateCommand,
        )
    }

    private fun threadForkBridgeUpdatePrompt(): RemodexBridgeUpdatePrompt {
        return RemodexBridgeUpdatePrompt(
            title = "Update Remodex on your Mac to use /fork",
            message = "This Mac bridge does not support native conversation forks yet. Update the Remodex npm package to use /fork and worktree fork flows.",
            command = remodexBridgeUpdateCommand,
        )
    }

    private fun shouldRetryWithApprovalPolicyFallback(error: Throwable): Boolean {
        return shouldRetryWithApprovalPolicyFallbackValue(error)
    }

    private fun shouldRetryWithImageUrlFieldFallback(error: Throwable): Boolean {
        return shouldRetryWithImageUrlFieldFallbackValue(error)
    }

    private fun consumeUnsupportedCollaborationMode(
        error: Throwable,
        includeCollaborationMode: Boolean,
    ): Boolean {
        if (!includeCollaborationMode || !shouldRetryWithoutCollaborationMode(error)) {
            return false
        }
        supportsTurnCollaborationMode = false
        return true
    }

    private suspend fun <T> retryAfterThreadMaterialization(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                val retryDelayMs = threadMaterializationRetryDelaysMs.getOrNull(attempt)
                if (retryDelayMs == null || !shouldRetryAfterThreadMaterializationValue(error)) {
                    throw error
                }
                attempt += 1
                delay(retryDelayMs)
            }
        }
    }

    private fun shouldTreatAsThreadNotFound(error: Throwable): Boolean {
        return shouldTreatAsThreadNotFoundValue(error)
    }

    private suspend fun handleNotification(message: RpcMessage) {
        val method = message.method?.trim().orEmpty()
        val paramsObject = message.params?.jsonObjectOrNull
        if (shouldLogReviewDebug(method, paramsObject)) {
            logReviewDebug(
                stage = "notify",
                method = method,
                paramsObject = paramsObject,
            )
        }
        when (method) {
            "thread/name/updated" -> paramsObject?.let(::handleThreadNameUpdatedNotification)
            "turn/started" -> paramsObject?.let(::handleTurnStartedNotification)
            "turn/completed" -> paramsObject?.let(::handleTurnCompletedNotification)
            "turn/plan/updated" -> paramsObject?.let(::handleTurnPlanUpdatedNotification)
            "thread/status/changed" -> paramsObject?.let(::handleThreadStatusChangedNotification)
            "thread/tokenUsage/updated" -> paramsObject?.let(::handleThreadTokenUsageUpdatedNotification)
            "serverRequest/resolved" -> paramsObject?.let(::handleServerRequestResolvedNotification)
            "codex/event" -> if (paramsObject != null && handleLegacyCodexEnvelopeEvent(paramsObject)) return
            "codex/event/token_count" -> if (paramsObject != null && handleLegacyCodexNamedEvent(method = method, paramsObject = paramsObject)) return
            "token_count" -> if (paramsObject != null && handleLegacyTokenCountEvent(payload = paramsObject, paramsObject = paramsObject)) return

            "item/agentMessage/delta",
            "codex/event/agent_message_content_delta",
            "codex/event/agent_message_delta" -> paramsObject?.let(::appendAssistantDelta)

            "item/plan/delta" -> paramsObject?.let(::appendPlanDelta)

            "item/reasoning/summaryTextDelta",
            "item/reasoning/summaryPartAdded",
            "item/reasoning/textDelta" -> paramsObject?.let(::appendReasoningDelta)

            "item/fileChange/outputDelta" -> paramsObject?.let(::appendFileChangeDelta)

            "item/toolCall/outputDelta",
            "item/toolCall/output_delta",
            "item/tool_call/outputDelta",
            "item/tool_call/output_delta" -> paramsObject?.let(::appendToolCallDelta)

            "item/commandExecution/outputDelta",
            "item/command_execution/outputDelta" -> paramsObject?.let(::appendCommandExecutionDelta)

            "item/commandExecution/terminalInteraction",
            "item/command_execution/terminalInteraction" -> paramsObject?.let(::handleCommandExecutionTerminalInteraction)

            "item/mcpToolCall/progress" -> paramsObject?.let(::handleMcpToolCallProgressNotification)

            "item/completed",
            "codex/event/item_completed",
            "codex/event/agent_message" -> paramsObject?.let { handleItemLifecycle(it, isCompleted = true) }

            "item/started",
            "codex/event/item_started" -> paramsObject?.let { handleItemLifecycle(it, isCompleted = false) }

            else -> {
                if (paramsObject == null) {
                    return
                }
                if (handleToolCallNotificationFallback(method, paramsObject)) {
                    return
                }
                if (handleFileChangeNotificationFallback(method, paramsObject)) {
                    return
                }
                if (handleDiffNotificationFallback(method, paramsObject)) {
                    return
                }
                if (handleLegacyCodexNamedEvent(method = method, paramsObject = paramsObject)) {
                    return
                }
            }
        }
    }

    private fun handleLegacyCodexEnvelopeEvent(paramsObject: JsonObject): Boolean {
        val payload = paramsObject.firstObject("msg") ?: return false
        val eventType = payload.firstString("type")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)
            ?: return false
        return handleLegacyCodexEventType(
            eventType = eventType,
            payload = payload,
            paramsObject = paramsObject,
        )
    }

    private fun handleLegacyCodexNamedEvent(
        method: String,
        paramsObject: JsonObject,
    ): Boolean {
        if (!method.startsWith("codex/event/")) {
            return false
        }
        val eventType = method
            .removePrefix("codex/event/")
            .trim()
            .lowercase(Locale.ROOT)
            .takeIf(String::isNotBlank)
            ?: return false
        val payload = paramsObject.firstObject("msg")
            ?: paramsObject.firstObject("event")
            ?: paramsObject
        return handleLegacyCodexEventType(
            eventType = eventType,
            payload = payload,
            paramsObject = paramsObject,
        )
    }

    private fun handleLegacyCodexEventType(
        eventType: String,
        payload: JsonObject,
        paramsObject: JsonObject,
    ): Boolean {
        return when (eventType) {
            "token_count" -> handleLegacyTokenCountEvent(
                payload = payload,
                paramsObject = paramsObject,
            )
            else -> false
        }
    }

    private fun handleLegacyTokenCountEvent(
        payload: JsonObject,
        paramsObject: JsonObject,
    ): Boolean {
        val outputTokenUsage = extractLegacyCodexOutputTokens(payload) ?: return false
        val turnId = payload.firstString("turnId", "turn_id", "id")
            ?: extractAssistantTurnId(paramsObject = paramsObject, eventObject = payload)
            ?: extractTurnId(paramsObject)
        val threadId = payload.firstString("threadId", "thread_id", "conversationId", "conversation_id")
            ?: resolveThreadId(paramsObject, turnIdHint = turnId)
            ?: return false
        recordAssistantOutputTokens(
            threadId = threadId,
            turnId = turnId,
            totalOutputTokens = outputTokenUsage.totalOutputTokens,
            lastOutputTokens = outputTokenUsage.lastOutputTokens,
        )
        return true
    }

    private suspend fun handleRequest(message: RpcMessage) {
        val method = message.method?.trim().orEmpty()
        val requestId = message.id
        val paramsObject = message.params?.jsonObjectOrNull
        when (method) {
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval" -> {
                if (requestId != null) {
                    handleApprovalRequest(
                        requestId = requestId,
                        method = method,
                        paramsObject = paramsObject,
                        params = message.params,
                    )
                    return
                }
            }
            "item/tool/requestUserInput" -> {
                if (requestId != null && paramsObject != null) {
                    handleStructuredUserInputRequest(
                        requestId = requestId,
                        paramsObject = paramsObject,
                    )
                    return
                }
            }
        }
        if (method.endsWith("requestApproval") && requestId != null) {
            handleApprovalRequest(
                requestId = requestId,
                method = method,
                paramsObject = paramsObject,
                params = message.params,
            )
            return
        }
        secureConnectionCoordinator.sendErrorResponse(
            id = requestId,
            code = -32601,
            message = "Unsupported request method: $method",
        )
    }

    private fun handleThreadNameUpdatedNotification(paramsObject: JsonObject) {
        val threadId = resolveThreadId(paramsObject) ?: return
        val eventObject = envelopeEventObject(paramsObject)
        val renameKeys = arrayOf("threadName", "thread_name", "name", "title")
        val hasExplicitRenameField = paramsObject.firstValue(*renameKeys) != null ||
            eventObject?.firstValue(*renameKeys) != null
        val normalizedThreadName = paramsObject.firstString(*renameKeys)
            ?: eventObject?.firstString(*renameKeys)

        when {
            !normalizedThreadName.isNullOrBlank() -> {
                updateThread(threadId) { snapshot ->
                    snapshot.copy(
                        title = normalizedThreadName,
                        name = normalizedThreadName,
                    )
                }
            }

            hasExplicitRenameField -> {
                updateThread(threadId) { snapshot ->
                    snapshot.copy(
                        title = RemodexThreadSummary.defaultDisplayTitle,
                        name = null,
                    )
                }
            }

            else -> return
        }

        scope.launch {
            hydrateThread(threadId)
        }
    }

    private fun handleTurnStartedNotification(paramsObject: JsonObject) {
        val threadId = resolveThreadId(paramsObject)
        val turnId = extractTurnIdForTurnLifecycleEvent(paramsObject)
        if (threadId != null && turnId != null) {
            setActiveTurnId(threadId = threadId, turnId = turnId)
            confirmLatestPendingUserMessage(threadId = threadId, turnId = turnId)
        } else if (threadId != null) {
            markThreadAsRunningFallback(threadId)
        }
        threadId?.let { id ->
            touchThread(threadId = id, isRunning = true)
        }
    }

    private fun handleTurnCompletedNotification(paramsObject: JsonObject) {
        val turnId = extractTurnIdForTurnLifecycleEvent(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        markAssistantTurnCompleted(threadId = threadId, turnId = turnId)
        logAssistantStreamingTraceTerminal(
            threadId = threadId,
            event = "turnCompleted",
            turnId = turnId,
        )
        logReviewDebug(
            stage = "turnCompleted",
            method = "turn/completed",
            paramsObject = paramsObject,
            extra = "resolvedThreadId=$threadId resolvedTurnId=${turnId.orEmpty()} activeTurnId=${activeTurnIdByThread[threadId].orEmpty()}",
        )
        if (turnId != null) {
            threadIdByTurnId[turnId] = threadId
        }
        setLatestTurnTerminalState(
            threadId = threadId,
            state = RemodexTurnTerminalState.COMPLETED,
            turnId = turnId,
        )
        turnId?.let(reviewDebugTurnIds::remove)
        reviewDebugThreadIds.remove(threadId)
        clearThreadRunningState(threadId)
        completeStreamingItemsForThread(threadId = threadId, turnId = turnId)
        touchThread(threadId = threadId, isRunning = false)
        scope.launch {
            refreshThreads()
            hydrateThread(threadId)
            if (shouldContinueTurnCompletionCatchup(threadId = threadId, turnId = turnId)) {
                postTurnCompletionCatchup(threadId = threadId, turnId = turnId)
            }
        }
    }

    private fun handleThreadTokenUsageUpdatedNotification(paramsObject: JsonObject) {
        val threadId = resolveThreadId(paramsObject) ?: return
        val turnId = extractTurnId(paramsObject)
        val outputTokenUsage = extractThreadTokenUsageUpdatedOutputTokens(paramsObject) ?: return
        recordAssistantOutputTokens(
            threadId = threadId,
            turnId = turnId,
            totalOutputTokens = outputTokenUsage.totalOutputTokens,
            lastOutputTokens = outputTokenUsage.lastOutputTokens,
        )
    }

    private fun handleThreadStatusChangedNotification(paramsObject: JsonObject) {
        val threadId = resolveThreadId(paramsObject) ?: return
        val eventObject = envelopeEventObject(paramsObject)
        val statusObject = paramsObject.firstObject("status")
            ?: eventObject?.firstObject("status")
            ?: paramsObject.firstObject("event")?.firstObject("status")
        val activityState = decodeThreadActivityState(statusObject)
        val normalizedStatus = normalizeStatus(
            statusObject?.firstString("type", "statusType", "status_type")
                ?: paramsObject.firstString("status")
                ?: eventObject?.firstString("status")
                ?: "",
        )
        emitReviewDebugLog(
            "stage=threadStatusChanged threadId=$threadId status=$normalizedStatus activeTurnId=${activeTurnIdByThread[threadId].orEmpty()} payload=${sanitizeReviewDebugJson((statusObject ?: eventObject ?: paramsObject).toString())}",
        )
        when {
            normalizedStatus == "running"
                || normalizedStatus == "active"
                || normalizedStatus == "processing"
                || normalizedStatus == "inprogress"
                || normalizedStatus == "started"
                || normalizedStatus == "pending" -> {
                latestTurnTerminalStateByThread.remove(threadId)
                if (activeTurnIdByThread[threadId] == null) {
                    markThreadAsRunningFallback(threadId)
                }
                touchThread(threadId = threadId, isRunning = true)
            }

            normalizedStatus == "idle"
                || normalizedStatus == "notloaded"
                || normalizedStatus == "completed"
                || normalizedStatus == "done"
                || normalizedStatus == "finished"
                || normalizedStatus == "stopped"
                || normalizedStatus == "systemerror" -> {
                logAssistantStreamingTraceThreadStatus(
                    threadId = threadId,
                    normalizedStatus = normalizedStatus,
                )
                val activeTurnIdForThread = activeTurnIdByThread[threadId]
                val shouldCatchUpLifecycle = (
                    activeTurnIdForThread != null ||
                    runningThreadFallbackIds.contains(threadId) ||
                    hasStreamingMessage(threadId)
                )
                terminalStateForNormalizedStatus(normalizedStatus)?.let { state ->
                    setLatestTurnTerminalState(
                        threadId = threadId,
                        state = state,
                        turnId = activeTurnIdForThread,
                    )
                }
                completeStreamingItemsForThread(
                    threadId = threadId,
                    turnId = activeTurnIdForThread,
                )
                clearThreadRunningState(threadId)
                touchThread(threadId = threadId, isRunning = false)
                if (shouldCatchUpLifecycle) {
                    scheduleLifecycleCatchup(
                        threadId = threadId,
                        turnId = activeTurnIdForThread,
                        forceDelayedRetries = true,
                    )
                }
            }
        }
        val waitingOnApproval = when {
            activityState != null -> activityState.isWaitingOnApproval
            normalizedStatus == "running"
                || normalizedStatus == "processing"
                || normalizedStatus == "inprogress"
                || normalizedStatus == "started"
                || normalizedStatus == "pending"
                || normalizedStatus == "idle"
                || normalizedStatus == "notloaded"
                || normalizedStatus == "completed"
                || normalizedStatus == "done"
                || normalizedStatus == "finished"
                || normalizedStatus == "stopped"
                || normalizedStatus == "systemerror" -> false

            else -> null
        }
        if (waitingOnApproval != null) {
            updateThreadWaitingOnApproval(
                threadId = threadId,
                isWaitingOnApproval = waitingOnApproval,
            )
        }
    }

    private fun handleTurnPlanUpdatedNotification(paramsObject: JsonObject) {
        val turnId = extractTurnId(paramsObject)?.takeIf(String::isNotBlank) ?: return
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        threadIdByTurnId[turnId] = threadId
        upsertPlanUpdateMessage(
            threadId = threadId,
            turnId = turnId,
            planState = decodeHistoryPlanState(paramsObject),
            isStreaming = true,
        )
    }

    private fun restoreRunningStateFromStreamingEvent(
        threadId: String,
        resolvedTurnId: String?,
    ) {
        val normalizedTurnId = resolvedTurnId?.trim()?.takeIf(String::isNotEmpty)
        val hasKnownRunningState = threadHasKnownRunningState(threadId)
        if (normalizedTurnId != null) {
            if (activeTurnIdByThread[threadId] == normalizedTurnId) {
                return
            }
            if (activeTurnIdByThread[threadId] == null || !hasKnownRunningState || latestTurnTerminalStateByThread[threadId] != null) {
                setActiveTurnId(threadId = threadId, turnId = normalizedTurnId)
            }
            return
        }
        if (!hasKnownRunningState || latestTurnTerminalStateByThread[threadId] != null) {
            markThreadAsRunningFallback(threadId)
        }
    }

    private fun appendAssistantDelta(paramsObject: JsonObject) {
        val delta = extractTextDelta(paramsObject)
        if (shouldIgnoreStreamingTextDelta(delta)) {
            return
        }
        val eventObject = envelopeEventObject(paramsObject)
        val turnId = extractAssistantTurnId(paramsObject, eventObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        if (resolvedTurnId.isNullOrBlank()) {
            return
        }
        restoreRunningStateFromStreamingEvent(
            threadId = threadId,
            resolvedTurnId = resolvedTurnId,
        )
        val itemId = extractItemId(
            paramsObject = paramsObject,
            eventObject = eventObject,
            itemObject = extractIncomingItemObject(paramsObject, eventObject),
        )
        logAssistantStreamingTraceDelta(
            threadId = threadId,
            turnId = resolvedTurnId,
            itemId = itemId,
            deltaLength = delta.length,
        )
        threadIdByTurnId[resolvedTurnId] = threadId
        val baseMessageId = assistantBaseMessageId(
            turnId = resolvedTurnId,
            itemId = itemId,
        )
        val existingItem = reusableAssistantStreamingItem(
            threadId = threadId,
            turnId = resolvedTurnId,
            itemId = itemId,
            preferredMessageId = baseMessageId,
        )
        val effectiveMessageId = existingItem?.id ?: nextAssistantSegmentMessageId(
            threadId = threadId,
            turnId = resolvedTurnId,
            itemId = itemId,
        )
        val effectiveItemId = itemId ?: existingItem?.itemId
        val updatedItem = existingItem?.copy(
            text = appendStreamingTextDelta(existingItem.text, delta),
            turnId = resolvedTurnId,
            itemId = effectiveItemId,
            isStreaming = true,
        ) ?: timelineItem(
            id = effectiveMessageId,
            speaker = ConversationSpeaker.ASSISTANT,
            text = delta,
            kind = ConversationItemKind.CHAT,
            turnId = resolvedTurnId,
            itemId = effectiveItemId,
            isStreaming = true,
            orderIndex = resolveAssistantOrderIndex(
                threadId = threadId,
                existingItem = existingItem,
            ),
        )
        recordAssistantOutputObserved(
            threadId = threadId,
            turnId = resolvedTurnId,
            itemId = effectiveItemId,
            messageId = effectiveMessageId,
        )
        upsertAssistantTimelineItem(
            threadId = threadId,
            item = updatedItem,
            isRunning = true,
        )
    }

    private fun appendReasoningDelta(paramsObject: JsonObject) {
        val delta = extractTextDelta(paramsObject)
        if (shouldIgnoreStreamingTextDelta(delta)) {
            return
        }
        val eventObject = envelopeEventObject(paramsObject)
        val turnId = extractTurnId(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        restoreRunningStateFromStreamingEvent(
            threadId = threadId,
            resolvedTurnId = resolvedTurnId,
        )
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        val itemId = extractItemId(
            paramsObject = paramsObject,
            eventObject = eventObject,
            itemObject = extractIncomingItemObject(paramsObject, eventObject),
        )
        val messageId = streamingMessageId(
            itemId = itemId,
            turnId = resolvedTurnId,
            fallbackPrefix = "reasoning",
        )
        appendTimelineMutation(
            threadId = threadId,
            mutation = TimelineMutation.ReasoningTextDelta(
                messageId = messageId,
                turnId = resolvedTurnId.orEmpty(),
                itemId = itemId,
                delta = delta,
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = messageId,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                ),
            ),
            isRunning = true,
        )
    }

    private fun appendPlanDelta(paramsObject: JsonObject) {
        val delta = extractTextDelta(paramsObject)
        if (shouldIgnoreStreamingTextDelta(delta)) {
            return
        }
        val turnId = extractTurnId(paramsObject)?.takeIf(String::isNotBlank) ?: return
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        threadIdByTurnId[turnId] = threadId
        val eventObject = envelopeEventObject(paramsObject)
        upsertPlanMessage(
            threadId = threadId,
            turnId = turnId,
            itemId = extractItemId(
                paramsObject = paramsObject,
                eventObject = eventObject,
                itemObject = extractIncomingItemObject(paramsObject, eventObject),
            ),
            text = delta,
            planState = null,
            isStreaming = true,
            textIsDelta = true,
        )
    }

    private fun handleStructuredUserInputRequest(
        requestId: JsonElement,
        paramsObject: JsonObject,
    ) {
        val threadId = resolveThreadId(paramsObject) ?: return
        val turnId = extractTurnId(paramsObject)
        if (turnId != null) {
            threadIdByTurnId[turnId] = threadId
        }
        val itemId = extractItemId(
            paramsObject = paramsObject,
            eventObject = envelopeEventObject(paramsObject),
        ) ?: "request-${rpcIdKey(requestId) ?: requestId}"
        val request = decodeStructuredUserInputRequest(
            requestId = requestId,
            questionValues = paramsObject.firstArray("questions").orEmpty(),
        ) ?: return
        upsertStructuredUserInputPrompt(
            threadId = threadId,
            turnId = turnId,
            itemId = itemId,
            request = request,
        )
    }

    private fun handleServerRequestResolvedNotification(paramsObject: JsonObject) {
        val requestId = paramsObject.firstValue("requestId", "request_id") ?: return
        val threadId = resolveThreadId(paramsObject)
        if (backingPendingApprovalRequest.value?.requestId == requestId && threadId != null) {
            updateThreadWaitingOnApproval(
                threadId = threadId,
                isWaitingOnApproval = false,
            )
        }
        removePendingApprovalRequest(
            requestId = requestId,
            threadIdHint = threadId,
        )
        removeStructuredUserInputPrompt(
            requestId = requestId,
            threadIdHint = threadId,
        )
    }

    private suspend fun handleApprovalRequest(
        requestId: JsonElement,
        method: String,
        paramsObject: JsonObject?,
        params: JsonElement?,
    ) {
        val request = RemodexApprovalRequest(
            id = rpcIdKey(requestId) ?: requestId.toString(),
            requestId = requestId,
            method = method,
            command = paramsObject?.firstString("command"),
            reason = paramsObject?.firstString("reason"),
            threadId = paramsObject?.firstString("threadId", "thread_id"),
            turnId = paramsObject?.firstString("turnId", "turn_id"),
            approvalId = paramsObject?.firstString("approvalId", "approval_id"),
            cwd = paramsObject?.firstString("cwd", "workingDirectory", "working_directory"),
            requestedPermissions = decodeRequestedPermissions(paramsObject),
            params = params,
        )
        if (approvalAccessMode(threadId = request.threadId) == RemodexAccessMode.FULL_ACCESS) {
            try {
                val result = if (request.kind == RemodexApprovalKind.PERMISSIONS) {
                    buildPermissionsApprovalResponse(
                        permissions = requestedPermissionsPayload(request)
                            ?: buildGrantedPermissionsProfile(request.requestedPermissions),
                        scope = RemodexPermissionGrantScope.SESSION,
                    )
                } else {
                    buildApprovalDecisionResponse("accept")
                }
                secureConnectionCoordinator.sendResponse(
                    id = requestId,
                    result = result,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                request.threadId?.let { threadId ->
                    updateThreadWaitingOnApproval(
                        threadId = threadId,
                        isWaitingOnApproval = true,
                    )
                }
                backingPendingApprovalRequest.value = request
            }
            return
        }
        request.threadId?.let { threadId ->
            updateThreadWaitingOnApproval(
                threadId = threadId,
                isWaitingOnApproval = true,
            )
        }
        backingPendingApprovalRequest.value = request
    }

    private fun clearPendingApprovalRequest(request: RemodexApprovalRequest) {
        backingPendingApprovalRequest.value = null
        request.threadId?.let { threadId ->
            updateThreadWaitingOnApproval(
                threadId = threadId,
                isWaitingOnApproval = false,
            )
        }
    }

    private fun decodeRequestedPermissions(paramsObject: JsonObject?): RemodexRequestedPermissions? {
        val permissionsObject = paramsObject?.firstObject("permissions") ?: return null
        val fileSystemObject = permissionsObject.firstObject("fileSystem", "file_system")
        val readPaths = fileSystemObject
            ?.firstArray("read")
            .orEmpty()
            .mapNotNull(JsonElement::stringOrNull)
            .map(String::trim)
            .filter(String::isNotEmpty)
        val writePaths = fileSystemObject
            ?.firstArray("write")
            .orEmpty()
            .mapNotNull(JsonElement::stringOrNull)
            .map(String::trim)
            .filter(String::isNotEmpty)
        val networkEnabled = permissionsObject.firstObject("network")?.firstBoolean("enabled")
        if (networkEnabled == null && readPaths.isEmpty() && writePaths.isEmpty()) {
            return null
        }
        return RemodexRequestedPermissions(
            networkEnabled = networkEnabled,
            readPaths = readPaths,
            writePaths = writePaths,
        )
    }

    private fun requestedPermissionsPayload(request: RemodexApprovalRequest): JsonObject? {
        val paramsObject = request.params?.jsonObjectOrNull
        return paramsObject?.firstObject("permissions")
    }

    private fun buildGrantedPermissionsProfile(
        requestedPermissions: RemodexRequestedPermissions?,
    ): JsonObject {
        return buildJsonObject {
            requestedPermissions?.networkEnabled?.let { enabled ->
                put(
                    "network",
                    buildJsonObject {
                        put("enabled", JsonPrimitive(enabled))
                    },
                )
            }
            if (requestedPermissions?.readPaths?.isNotEmpty() == true || requestedPermissions?.writePaths?.isNotEmpty() == true) {
                put(
                    "fileSystem",
                    buildJsonObject {
                        if (requestedPermissions.readPaths.isNotEmpty()) {
                            put(
                                "read",
                                buildJsonArray {
                                    requestedPermissions.readPaths.forEach { path ->
                                        add(JsonPrimitive(path))
                                    }
                                },
                            )
                        }
                        if (requestedPermissions.writePaths.isNotEmpty()) {
                            put(
                                "write",
                                buildJsonArray {
                                    requestedPermissions.writePaths.forEach { path ->
                                        add(JsonPrimitive(path))
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun appendFileChangeDelta(paramsObject: JsonObject) {
        appendSystemTextDelta(
            paramsObject = paramsObject,
            kind = ConversationItemKind.FILE_CHANGE,
            fallbackPrefix = "filechange",
        )
    }

    private fun appendToolCallDelta(paramsObject: JsonObject) {
        val eventObject = envelopeEventObject(paramsObject)
        val itemObject = extractIncomingItemObject(paramsObject, eventObject)
        val delta = extractTextDelta(paramsObject)
        if (delta.isBlank()) {
            return
        }
        if (isLikelyFileChangeToolCall(itemObject = itemObject, fallbackText = delta)) {
            appendSystemTextDelta(
                paramsObject = paramsObject,
                kind = ConversationItemKind.FILE_CHANGE,
                fallbackPrefix = "filechange",
            )
            return
        }
        val activityLines = extractToolCallActivityLines(delta)
        if (activityLines.isEmpty()) {
            return
        }
        val turnId = extractTurnId(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        completeAssistantStreamingItemsForTurn(
            threadId = threadId,
            turnId = resolvedTurnId,
        )
        val itemId = extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject)
        val messageId = streamingMessageId(
            itemId = itemId,
            turnId = resolvedTurnId,
            fallbackPrefix = "toolactivity",
        )
        appendTimelineMutation(
            threadId = threadId,
            mutation = TimelineMutation.ActivityLine(
                messageId = messageId,
                turnId = resolvedTurnId.orEmpty(),
                itemId = itemId,
                line = activityLines.joinToString(separator = "\n"),
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = messageId,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.TOOL_ACTIVITY,
                    systemTurnOrderingHint = ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE,
                ),
                systemTurnOrderingHint = ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE,
            ),
            isRunning = true,
        )
    }

    private fun handleMcpToolCallProgressNotification(paramsObject: JsonObject) {
        val context = resolveNotificationContext(paramsObject) ?: return
        val progressMessage = summarizeTimelineSupportingText(
            paramsObject.firstString("message")?.trim()?.takeIf(String::isNotEmpty),
        ) ?: return
        completeAssistantStreamingItemsForTurn(
            threadId = context.threadId,
            turnId = context.turnId,
        )
        val messageId = streamingMessageId(
            itemId = context.itemId,
            turnId = context.turnId,
            fallbackPrefix = streamingFallbackPrefix(ConversationItemKind.MCP_TOOL_CALL),
        )
        val existingItem = projectedTimelineItem(
            threadId = context.threadId,
            messageId = messageId,
            turnId = context.turnId,
            itemId = context.itemId,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.MCP_TOOL_CALL,
        ) ?: reusableSystemStreamingItem(
            threadId = context.threadId,
            turnId = context.turnId,
            itemId = context.itemId,
            kind = ConversationItemKind.MCP_TOOL_CALL,
            text = "Calling MCP tool",
        )
        val baseText = existingItem?.text?.takeIf(String::isNotBlank) ?: "Calling MCP tool"
        upsertStreamingItem(
            threadId = context.threadId,
            item = timelineItem(
                id = existingItem?.id ?: messageId,
                speaker = ConversationSpeaker.SYSTEM,
                text = baseText,
                kind = ConversationItemKind.MCP_TOOL_CALL,
                supportingText = progressMessage,
                turnId = context.turnId,
                itemId = context.itemId ?: existingItem?.itemId,
                isStreaming = true,
                attachments = existingItem?.attachments.orEmpty(),
                orderIndex = resolveOrderIndex(
                    threadId = context.threadId,
                    messageId = existingItem?.id ?: messageId,
                    turnId = context.turnId,
                    itemId = context.itemId ?: existingItem?.itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.MCP_TOOL_CALL,
                    systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                        ConversationItemKind.MCP_TOOL_CALL,
                    ),
                ),
                systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                    ConversationItemKind.MCP_TOOL_CALL,
                ),
            ),
            isRunning = true,
        )
    }

    private fun appendCommandExecutionDelta(paramsObject: JsonObject) {
        val context = resolveNotificationContext(paramsObject) ?: return
        completeAssistantStreamingItemsForTurn(
            threadId = context.threadId,
            turnId = context.turnId,
        )
        val existingItem = projectedTimelineItem(
            threadId = context.threadId,
            messageId = streamingMessageId(
                itemId = context.itemId,
                turnId = context.turnId,
                fallbackPrefix = "commandexecution",
            ),
            turnId = context.turnId,
            itemId = context.itemId,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
        )
        val hasCommandHint = extractCommandExecutionCommand(context.payloadObject) != null
            || context.payloadObject.firstValue("command", "cmd", "raw_command", "rawCommand") != null
        if (!hasCommandHint && existingItem != null) {
            appendCommandExecutionOutputToDetails(context = context, paramsObject = paramsObject)
            return
        }
        upsertCommandExecutionDetails(
            context = context,
            payloadObject = context.payloadObject,
            isCompleted = false,
        )
        appendCommandExecutionOutputToDetails(context = context, paramsObject = paramsObject)
        publishCommandExecutionStatus(
            context = context,
            payloadObject = context.payloadObject,
            isCompleted = false,
            onlyIfMissing = true,
        )
    }

    private fun handleCommandExecutionTerminalInteraction(paramsObject: JsonObject) {
        val context = resolveNotificationContext(paramsObject) ?: return
        val existingItem = projectedTimelineItem(
            threadId = context.threadId,
            messageId = streamingMessageId(
                itemId = context.itemId,
                turnId = context.turnId,
                fallbackPrefix = "commandexecution",
            ),
            turnId = context.turnId,
            itemId = context.itemId,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
        )
        val state = decodeCommandExecutionRunState(
            payloadObject = context.payloadObject,
            paramsObject = paramsObject,
            isCompleted = false,
        )
        if (existingItem != null && !existingItem.isStreaming && state.phase == CommandExecutionRunPhase.RUNNING) {
            return
        }
        upsertCommandExecutionDetails(
            context = context,
            payloadObject = context.payloadObject,
            isCompleted = state.phase != CommandExecutionRunPhase.RUNNING,
            paramsObject = paramsObject,
        )
        publishCommandExecutionStatus(
            context = context,
            payloadObject = context.payloadObject,
            isCompleted = state.phase != CommandExecutionRunPhase.RUNNING,
            onlyIfMissing = false,
            paramsObject = paramsObject,
        )
    }

    private fun handleItemLifecycle(
        paramsObject: JsonObject,
        isCompleted: Boolean,
    ) {
        val eventObject = envelopeEventObject(paramsObject)
        val itemObject = extractIncomingItemObject(paramsObject, eventObject)
        val turnId = extractAssistantTurnId(paramsObject, eventObject)
            ?: extractTurnId(paramsObject)
            ?: extractTurnIdForTurnLifecycleEvent(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId)
        if (itemObject == null) {
            if (threadId != null && reviewDebugThreadIds.contains(threadId)) {
                emitReviewDebugLog(
                    "stage=itemLifecycleSkip threadId=$threadId turnId=${turnId.orEmpty()} reason=noItemObject completed=$isCompleted payload=${sanitizeReviewDebugJson((eventObject ?: paramsObject).toString())}",
                )
            }
            if (isCompleted) {
                handleCompletedAssistantMessageFallback(
                    paramsObject = paramsObject,
                    eventObject = eventObject,
                )
            }
            return
        }
        val itemType = normalizeItemType(itemObject.firstString("type").orEmpty())
        if (itemType.isEmpty()) {
            if (threadId != null && reviewDebugThreadIds.contains(threadId)) {
                emitReviewDebugLog(
                    "stage=itemLifecycleSkip threadId=$threadId turnId=${turnId.orEmpty()} reason=emptyItemType completed=$isCompleted payload=${sanitizeReviewDebugJson(itemObject.toString())}",
                )
            }
            return
        }
        if (itemType == "usermessage" || (itemType == "message" && itemObject.firstString("role")?.contains("user", ignoreCase = true) == true)) {
            if (isCompleted) {
                handleUserMessageLifecycle(
                    paramsObject = paramsObject,
                    itemObject = itemObject,
                )
            }
            return
        }
        if (isAssistantHistoryItem(itemType, itemObject)) {
            if (threadId != null && reviewDebugThreadIds.contains(threadId)) {
                emitReviewDebugLog(
                    "stage=itemLifecycleDispatch threadId=$threadId turnId=${turnId.orEmpty()} itemType=$itemType branch=assistant completed=$isCompleted itemId=${extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject).orEmpty()}",
                )
            }
            handleAssistantLifecycle(
                paramsObject = paramsObject,
                itemObject = itemObject,
                isCompleted = isCompleted,
            )
            return
        }
        if (threadId != null && reviewDebugThreadIds.contains(threadId)) {
            emitReviewDebugLog(
                "stage=itemLifecycleDispatch threadId=$threadId turnId=${turnId.orEmpty()} itemType=$itemType branch=structured completed=$isCompleted itemId=${extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject).orEmpty()}",
            )
        }
        val handled = handleStructuredItemLifecycle(
            itemObject = itemObject,
            paramsObject = paramsObject,
            itemType = itemType,
            isCompleted = isCompleted,
        )
        if (threadId != null && reviewDebugThreadIds.contains(threadId)) {
            emitReviewDebugLog(
                "stage=itemLifecycleHandled threadId=$threadId turnId=${turnId.orEmpty()} itemType=$itemType completed=$isCompleted handled=$handled",
            )
        }
    }

    private fun handleCompletedAssistantMessageFallback(
        paramsObject: JsonObject,
        eventObject: JsonObject?,
    ) {
        val text = completedAssistantFallbackTextValue(
            paramsObject = paramsObject,
            eventObject = eventObject,
        )
        if (text.isBlank()) {
            return
        }
        val turnId = extractAssistantTurnId(paramsObject, eventObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        if (shouldSuppressAssistantCompletion(threadId = threadId, turnId = turnId, itemId = null, text = text)) {
            return
        }
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        logReviewDebug(
            stage = "assistantFallback",
            method = "codex/event/agent_message",
            paramsObject = paramsObject,
            extra = "resolvedThreadId=$threadId resolvedTurnId=${resolvedTurnId.orEmpty()} fallbackText=${text.take(120)}",
        )
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        val itemId = extractItemId(paramsObject = paramsObject, eventObject = eventObject)
        val preferredMessageId = streamingMessageId(
            itemId = itemId,
            turnId = resolvedTurnId,
            fallbackPrefix = "assistant",
        )
        val existingItem = reusableAssistantCompletionItem(
            threadId = threadId,
            turnId = resolvedTurnId,
            itemId = itemId,
            text = text,
            preferredMessageId = preferredMessageId,
        )
        val effectiveTurnId = resolvedTurnId ?: existingItem?.turnId
        val effectiveItemId = itemId ?: existingItem?.itemId
        val messageId = existingItem?.id ?: preferredMessageId
        upsertAssistantTimelineItem(
            threadId = threadId,
            item = timelineItem(
                id = messageId,
                speaker = ConversationSpeaker.ASSISTANT,
                text = text,
                kind = ConversationItemKind.CHAT,
                turnId = effectiveTurnId,
                itemId = effectiveItemId,
                isStreaming = false,
                orderIndex = resolveAssistantOrderIndex(
                    threadId = threadId,
                    existingItem = existingItem,
                ),
                assistantChangeSet = existingItem?.assistantChangeSet,
            ),
        )
        recordAssistantOutputObserved(
            threadId = threadId,
            turnId = effectiveTurnId,
            itemId = effectiveItemId,
            messageId = messageId,
        )
        recordAssistantCompletionFingerprint(threadId = threadId, text = text)
    }

    private fun handleAssistantLifecycle(
        paramsObject: JsonObject,
        itemObject: JsonObject,
        isCompleted: Boolean,
    ) {
        val eventObject = envelopeEventObject(paramsObject)
        val turnId = extractAssistantTurnId(paramsObject, eventObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        val normalizedItemType = normalizeItemType(itemObject.firstString("type").orEmpty())
        val itemId = extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject)
        val debugBody = if (isCompleted) {
            decodeCompletedAssistantBody(
                itemType = normalizedItemType,
                itemObject = itemObject,
            ).take(120)
        } else {
            ""
        }
        logReviewDebug(
            stage = if (isCompleted) "assistantCompleted" else "assistantStarted",
            method = if (isCompleted) "item/completed" else "item/started",
            paramsObject = paramsObject,
            extra = "itemType=$normalizedItemType resolvedThreadId=$threadId resolvedTurnId=${resolvedTurnId.orEmpty()} itemId=${itemId.orEmpty()} body=$debugBody",
        )
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        val baseMessageId = assistantBaseMessageId(
            turnId = resolvedTurnId,
            itemId = itemId,
        )
        val existingItem = reusableAssistantStreamingItem(
            threadId = threadId,
            turnId = resolvedTurnId,
            itemId = itemId,
            preferredMessageId = baseMessageId,
        )
        val resolvedMessageId = existingItem?.id
            ?: resolvedTurnId?.let { nonNullTurnId ->
                nextAssistantSegmentMessageId(
                    threadId = threadId,
                    turnId = nonNullTurnId,
                    itemId = itemId,
                )
            }
            ?: baseMessageId
        val effectiveItemId = itemId ?: existingItem?.itemId
        if (!isCompleted) {
            if (resolvedTurnId.isNullOrBlank()) {
                return
            }
            beginAssistantMessage(
                threadId = threadId,
                turnId = resolvedTurnId,
                itemId = effectiveItemId,
            )
            return
        }

        val body = decodeCompletedAssistantBody(
            itemType = normalizeItemType(itemObject.firstString("type").orEmpty()),
            itemObject = itemObject,
        )
        if (body.isNotBlank()) {
            if (shouldSuppressAssistantCompletion(threadId = threadId, turnId = resolvedTurnId, itemId = itemId, text = body)) {
                return
            }
            val completionItem = reusableAssistantCompletionItem(
                threadId = threadId,
                turnId = resolvedTurnId,
                itemId = itemId,
                text = body,
                preferredMessageId = resolvedMessageId,
            )
            val effectiveTurnId = resolvedTurnId ?: completionItem?.turnId
            val effectiveItemId = itemId ?: completionItem?.itemId
            val completionMessageId = completionItem?.id ?: resolvedMessageId
            upsertAssistantTimelineItem(
                threadId = threadId,
                item = timelineItem(
                    id = completionMessageId,
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = body,
                    kind = ConversationItemKind.CHAT,
                    turnId = effectiveTurnId,
                    itemId = effectiveItemId,
                    isStreaming = false,
                    orderIndex = resolveAssistantOrderIndex(
                        threadId = threadId,
                        existingItem = completionItem ?: existingItem,
                    ),
                    assistantChangeSet = completionItem?.assistantChangeSet ?: existingItem?.assistantChangeSet,
                ),
            )
            recordAssistantOutputObserved(
                threadId = threadId,
                turnId = effectiveTurnId,
                itemId = effectiveItemId,
                messageId = completionMessageId,
            )
            recordAssistantCompletionFingerprint(threadId = threadId, text = body)
        } else {
            appendTimelineMutation(
                threadId = threadId,
                mutation = TimelineMutation.Complete(messageId = resolvedMessageId),
            )
        }
        if (isFinalAnswerAssistantPhase(itemObject)) {
            logAssistantStreamingTraceFinalAnswer(
                threadId = threadId,
                turnId = resolvedTurnId,
                itemId = itemObject.firstString("id"),
            )
        }
    }

    private fun handleUserMessageLifecycle(
        paramsObject: JsonObject,
        itemObject: JsonObject,
    ) {
        val eventObject = envelopeEventObject(paramsObject)
        val turnId = extractAssistantTurnId(paramsObject, eventObject)
            ?: extractTurnId(paramsObject)
            ?: extractTurnIdForTurnLifecycleEvent(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        val itemId = extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject)
        val attachments = decodeImageAttachments(itemObject)
        val text = androidUserMessageText(
            prompt = decodeItemText(itemObject),
            attachmentCount = attachments.size,
        )
        if (text.isBlank() && attachments.isEmpty()) {
            return
        }
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return
        val existingItem = TurnTimelineReducer.reduce(snapshot.timelineMutations)
            .asReversed()
            .firstOrNull { candidate ->
                candidate.speaker == ConversationSpeaker.USER &&
                    candidate.deliveryState != RemodexMessageDeliveryState.FAILED &&
                    (
                        normalizeAssistantCompletionIdentifierValue(candidate.turnId) == null ||
                            normalizeAssistantCompletionIdentifierValue(candidate.turnId) == normalizeAssistantCompletionIdentifierValue(resolvedTurnId)
                        ) &&
                    (
                        candidate.text.trim() == text ||
                            (
                                attachments.isEmpty() &&
                                    candidate.attachments.isEmpty() &&
                                    shouldReplaceOptimisticReviewPromptValue(candidate.text, text)
                                )
                        )
            }
        val resolvedAttachments = when {
            attachments.isEmpty() -> existingItem?.attachments.orEmpty()
            shouldPreserveExistingPreviewAttachments(
                existingAttachments = existingItem?.attachments.orEmpty(),
                incomingAttachments = attachments,
            ) -> existingItem?.attachments.orEmpty()
            else -> attachments
        }
        val messageId = existingItem?.id ?: itemId ?: "user-${UUID.randomUUID()}"
        upsertStreamingItem(
            threadId = threadId,
            item = timelineItem(
                id = messageId,
                speaker = ConversationSpeaker.USER,
                text = text,
                turnId = resolvedTurnId ?: existingItem?.turnId,
                itemId = itemId ?: existingItem?.itemId,
                deliveryState = RemodexMessageDeliveryState.CONFIRMED,
                createdAtEpochMs = existingItem?.createdAtEpochMs
                    ?: decodeHistoryTimestampMillis(
                        itemObject,
                        "createdAt",
                        "created_at",
                        "timestamp",
                    ),
                attachments = resolvedAttachments,
                orderIndex = existingItem?.orderIndex ?: resolveOrderIndex(
                    threadId = threadId,
                    messageId = messageId,
                    turnId = resolvedTurnId ?: existingItem?.turnId,
                    itemId = itemId ?: existingItem?.itemId,
                    speaker = ConversationSpeaker.USER,
                    kind = ConversationItemKind.CHAT,
                ),
            ),
            isRunning = true,
        )
    }

    private fun shouldPreserveExistingPreviewAttachments(
        existingAttachments: List<RemodexConversationAttachment>,
        incomingAttachments: List<RemodexConversationAttachment>,
    ): Boolean {
        if (existingAttachments.isEmpty() || incomingAttachments.isEmpty()) {
            return false
        }
        if (existingAttachments.size != incomingAttachments.size) {
            return false
        }
        if (existingAttachments.none { attachment -> !attachment.previewDataUrl.isNullOrBlank() }) {
            return false
        }
        return incomingAttachments.all { attachment ->
            attachment.uriString.trim().equals("remodex://history-image-elided", ignoreCase = true) ||
                isInlineImageDataUrl(attachment.uriString)
        }
    }

    private fun handleStructuredItemLifecycle(
        itemObject: JsonObject,
        paramsObject: JsonObject,
        itemType: String,
        isCompleted: Boolean,
    ): Boolean {
        if (!supportsStructuredLifecycleItem(itemType)) {
            return false
        }
        val eventObject = envelopeEventObject(paramsObject)
        val turnId = extractTurnId(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return true
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        val debugEnabled = reviewDebugThreadIds.contains(threadId)
        if (itemType == "enteredreviewmode" || itemType == "exitedreviewmode") {
            logReviewDebug(
                stage = if (isCompleted) "structuredCompleted" else "structuredStarted",
                method = if (isCompleted) "item/completed" else "item/started",
                paramsObject = paramsObject,
                extra = "itemType=$itemType resolvedThreadId=$threadId resolvedTurnId=${resolvedTurnId.orEmpty()} itemId=${extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject).orEmpty()}",
            )
        }
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        val itemId = extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject)
        if (shouldFinalizeAssistantStreamBeforeStructuredItem(itemType)) {
            completeAssistantStreamingItemsForTurn(
                threadId = threadId,
                turnId = resolvedTurnId,
            )
        }

        val presentation = decodeStructuredTimelinePresentation(
            itemObject = itemObject,
            itemType = itemType,
            isCompleted = isCompleted,
        )
        val kind = presentation.kind
        val body = presentation.body
        val planState = presentation.planState
        val subagentAction = presentation.subagentAction

        val messageId = streamingMessageId(
            itemId = itemId,
            turnId = resolvedTurnId,
            fallbackPrefix = streamingFallbackPrefix(kind),
        )
        if (debugEnabled) {
            emitReviewDebugLog(
                "stage=structuredResolve threadId=$threadId turnId=${resolvedTurnId.orEmpty()} itemType=$itemType kind=${kind.name} completed=$isCompleted itemId=${itemId.orEmpty()} messageId=$messageId body=${body.take(120).replace('\n', ' ')}",
            )
        }
        val existingItem = projectedTimelineItem(
            threadId = threadId,
            messageId = messageId,
            turnId = resolvedTurnId,
            itemId = itemId,
            speaker = ConversationSpeaker.SYSTEM,
            kind = kind,
        ) ?: reusableSystemStreamingItem(
            threadId = threadId,
            turnId = resolvedTurnId,
            itemId = itemId,
            kind = kind,
            text = body,
        )
        if (kind == ConversationItemKind.COMMAND_EXECUTION) {
            upsertCommandExecutionDetails(
                context = NotificationContext(
                    threadId = threadId,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    payloadObject = itemObject,
                ),
                payloadObject = itemObject,
                isCompleted = isCompleted,
            )
        }
        if (kind == ConversationItemKind.COMMAND_EXECUTION && isCompleted && existingItem != null) {
            if (debugEnabled) {
                emitReviewDebugLog(
                    "stage=structuredShortCircuit threadId=$threadId turnId=${resolvedTurnId.orEmpty()} itemType=$itemType reason=completedCommandExisting messageId=$messageId",
                )
            }
            publishCommandExecutionStatus(
                context = NotificationContext(
                    threadId = threadId,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    payloadObject = itemObject,
                ),
                payloadObject = itemObject,
                isCompleted = true,
                onlyIfMissing = false,
            )
            appendTimelineMutation(
                threadId = threadId,
                mutation = TimelineMutation.Complete(messageId = existingItem.id),
            )
            return true
        }
        if (
            kind == ConversationItemKind.REASONING &&
            isCompleted &&
            shouldDropCompletedReasoningPlaceholderValue(
                existingText = existingItem?.text,
                completedBody = body,
            )
        ) {
            removeTimelineMessage(
                threadId = threadId,
                messageId = messageId,
            )
            return true
        }
        if (kind == ConversationItemKind.PLAN) {
            upsertPlanMessage(
                threadId = threadId,
                turnId = resolvedTurnId,
                itemId = itemId,
                text = body.takeIf(String::isNotBlank),
                planState = planState,
                isStreaming = !isCompleted,
            )
            return true
        }
        if (
            subagentAction == null &&
            body.isBlank() &&
            presentation.supportingText.isNullOrBlank() &&
            presentation.attachments.isEmpty() &&
            isCompleted
        ) {
            if (debugEnabled) {
                emitReviewDebugLog(
                    "stage=structuredShortCircuit threadId=$threadId turnId=${resolvedTurnId.orEmpty()} itemType=$itemType reason=blankBodyCompleted messageId=$messageId",
                )
            }
            appendTimelineMutation(
                threadId = threadId,
                mutation = TimelineMutation.Complete(messageId = messageId),
            )
            return true
        }
        upsertStreamingItem(
            threadId = threadId,
            item = timelineItem(
                id = existingItem?.id ?: messageId,
                speaker = ConversationSpeaker.SYSTEM,
                text = when {
                    subagentAction != null -> subagentAction.summaryText
                    kind == ConversationItemKind.REASONING && existingItem != null -> {
                        ThreadHistoryReconciler.mergeStreamingSnapshotText(
                            existingText = existingItem.text,
                            incomingText = body.ifBlank { existingItem.text },
                        )
                    }

                    else -> body.ifBlank { kind.name.replace('_', ' ') }
                },
                kind = kind,
                supportingText = presentation.supportingText ?: existingItem?.supportingText,
                turnId = resolvedTurnId,
                itemId = itemId ?: existingItem?.itemId,
                isStreaming = !isCompleted,
                attachments = if (presentation.attachments.isNotEmpty()) {
                    presentation.attachments
                } else {
                    existingItem?.attachments.orEmpty()
                },
                planState = planState,
                subagentAction = subagentAction,
                structuredUserInputRequest = presentation.structuredUserInputRequest,
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = existingItem?.id ?: messageId,
                    turnId = resolvedTurnId,
                    itemId = itemId ?: existingItem?.itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = kind,
                    systemTurnOrderingHint = presentation.systemTurnOrderingHint,
                ),
                assistantChangeSet = existingItem?.assistantChangeSet,
                systemTurnOrderingHint = if (
                    presentation.systemTurnOrderingHint == ConversationSystemTurnOrderingHint.AUTO
                ) {
                    existingItem?.systemTurnOrderingHint ?: ConversationSystemTurnOrderingHint.AUTO
                } else {
                    presentation.systemTurnOrderingHint
                },
            ),
            isRunning = !isCompleted || activeTurnIdByThread[threadId] != null,
        )
        return true
    }

    private fun upsertPlanMessage(
        threadId: String,
        turnId: String?,
        itemId: String?,
        text: String?,
        planState: RemodexPlanState?,
        isStreaming: Boolean,
        textIsDelta: Boolean = false,
    ) {
        val messageId = streamingMessageId(
            itemId = itemId,
            turnId = turnId,
            fallbackPrefix = "plan",
        )
        val existingItem = projectedTimelineItem(
            threadId = threadId,
            messageId = messageId,
            turnId = turnId,
            itemId = itemId,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.PLAN,
        ) ?: reusablePlanStreamingItem(
            threadId = threadId,
            turnId = turnId,
            itemId = itemId,
        )
        val effectiveMessageId = existingItem?.id ?: messageId
        val effectiveItemId = itemId ?: existingItem?.itemId
        val resolvedPlanState = when {
            existingItem?.planState == null -> planState
            planState == null -> existingItem.planState
            else -> existingItem.planState.copy(
                explanation = planState.explanation ?: existingItem.planState.explanation,
                steps = if (planState.steps.isNotEmpty()) planState.steps else existingItem.planState.steps,
            )
        }
        val resolvedText = resolvedPlanText(
            existingText = existingItem?.text,
            incomingText = text,
            resolvedPlanState = resolvedPlanState,
            isStreaming = isStreaming,
            incomingTextIsDelta = textIsDelta,
        ) ?: if (isStreaming) "Planning..." else "Plan updated."
        upsertStreamingItem(
            threadId = threadId,
            item = timelineItem(
                id = effectiveMessageId,
                speaker = ConversationSpeaker.SYSTEM,
                text = resolvedText,
                kind = ConversationItemKind.PLAN,
                turnId = turnId,
                itemId = effectiveItemId,
                isStreaming = isStreaming,
                planState = resolvedPlanState,
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = effectiveMessageId,
                    turnId = turnId,
                    itemId = effectiveItemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.PLAN,
                ),
                assistantChangeSet = existingItem?.assistantChangeSet,
            ),
            isRunning = isStreaming || activeTurnIdByThread[threadId] != null,
        )
    }

    private fun upsertPlanUpdateMessage(
        threadId: String,
        turnId: String?,
        planState: RemodexPlanState?,
        isStreaming: Boolean,
    ) {
        val messageId = streamingMessageId(
            itemId = null,
            turnId = turnId,
            fallbackPrefix = "planupdate",
        )
        val existingItem = projectedTimelineItem(
            threadId = threadId,
            messageId = messageId,
            turnId = turnId,
            itemId = null,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.PLAN_UPDATE,
        )
        val resolvedPlanState = when {
            existingItem?.planState == null -> planState
            planState == null -> existingItem.planState
            else -> existingItem.planState.copy(
                explanation = planState.explanation ?: existingItem.planState.explanation,
                steps = if (planState.steps.isNotEmpty()) planState.steps else existingItem.planState.steps,
            )
        }
        val resolvedText = resolvedPlanText(
            existingText = existingItem?.text,
            incomingText = null,
            resolvedPlanState = resolvedPlanState,
            isStreaming = isStreaming,
            incomingTextIsDelta = false,
        ) ?: if (isStreaming) "Updating plan..." else "Updated plan."
        upsertStreamingItem(
            threadId = threadId,
            item = timelineItem(
                id = existingItem?.id ?: messageId,
                speaker = ConversationSpeaker.SYSTEM,
                text = resolvedText,
                kind = ConversationItemKind.PLAN_UPDATE,
                turnId = turnId,
                itemId = null,
                isStreaming = isStreaming,
                planState = resolvedPlanState,
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = existingItem?.id ?: messageId,
                    turnId = turnId,
                    itemId = null,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.PLAN_UPDATE,
                ),
                assistantChangeSet = existingItem?.assistantChangeSet,
            ),
            isRunning = isStreaming || activeTurnIdByThread[threadId] != null,
        )
    }

    private fun upsertStructuredUserInputPrompt(
        threadId: String,
        turnId: String?,
        itemId: String,
        request: RemodexStructuredUserInputRequest,
    ) {
        val fallbackText = request.questions.joinToString(separator = "\n\n") { question ->
            val header = question.header.trim()
            val prompt = question.question.trim()
            if (header.isEmpty()) {
                prompt
            } else {
                "$header\n$prompt"
            }
        }
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId }
        val existingItem = snapshot?.let { existingSnapshot ->
            TurnTimelineReducer.reduce(existingSnapshot.timelineMutations).lastOrNull { candidate ->
                candidate.speaker == ConversationSpeaker.SYSTEM &&
                candidate.kind == ConversationItemKind.USER_INPUT_PROMPT &&
                candidate.structuredUserInputRequest?.requestIdKey == request.requestIdKey
            }
        }
        val messageId = existingItem?.id ?: itemId
        upsertStreamingItem(
            threadId = threadId,
            item = timelineItem(
                id = messageId,
                speaker = ConversationSpeaker.SYSTEM,
                text = fallbackText,
                kind = ConversationItemKind.USER_INPUT_PROMPT,
                turnId = turnId ?: existingItem?.turnId,
                itemId = itemId,
                structuredUserInputRequest = request,
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = messageId,
                    turnId = turnId ?: existingItem?.turnId,
                    itemId = itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.USER_INPUT_PROMPT,
                ),
                assistantChangeSet = existingItem?.assistantChangeSet,
            ),
            isRunning = true,
        )
    }

    private fun removeStructuredUserInputPrompt(
        requestId: JsonElement,
        threadIdHint: String?,
    ) {
        val resolvedRequestId = rpcIdKey(requestId) ?: return
        val threadIds = threadIdHint?.let(::listOf) ?: backingThreads.value.map(ThreadSyncSnapshot::id)
        threadIds.forEach { threadId ->
            val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return@forEach
            TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
                .firstOrNull { item ->
                    item.kind == ConversationItemKind.USER_INPUT_PROMPT &&
                        item.structuredUserInputRequest?.requestIdKey == resolvedRequestId
                }?.let { item ->
                    val request = item.structuredUserInputRequest
                    val summaryText = request?.questions?.size?.let { questionCount ->
                        if (questionCount == 1) {
                            "Asked 1 question"
                        } else {
                            "Asked $questionCount questions"
                        }
                    } ?: item.text
                    upsertStreamingItem(
                        threadId = threadId,
                        item = item.copy(
                            text = summaryText,
                            isStreaming = false,
                        ),
                    )
                }
        }
    }

    private fun removePendingApprovalRequest(
        requestId: JsonElement,
        threadIdHint: String? = null,
    ) {
        val resolvedRequestId = rpcIdKey(requestId) ?: return
        val currentRequest = backingPendingApprovalRequest.value ?: return
        if (currentRequest.id != resolvedRequestId) {
            return
        }
        if (threadIdHint != null && currentRequest.threadId != null && currentRequest.threadId != threadIdHint) {
            return
        }
        backingPendingApprovalRequest.value = null
    }

    private fun approvalAccessMode(threadId: String?): RemodexAccessMode {
        if (threadId == null) {
            return RemodexAccessMode.ON_REQUEST
        }
        return backingThreads.value
            .firstOrNull { snapshot -> snapshot.id == threadId }
            ?.runtimeConfig
            ?.accessMode
            ?: RemodexAccessMode.ON_REQUEST
    }

    private fun appendSystemTextDelta(
        paramsObject: JsonObject,
        kind: ConversationItemKind,
        fallbackPrefix: String,
    ) {
        val delta = extractTextDelta(paramsObject)
        if (shouldIgnoreStreamingTextDelta(delta)) {
            return
        }
        val eventObject = envelopeEventObject(paramsObject)
        val turnId = extractTurnId(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        val itemId = extractItemId(
            paramsObject = paramsObject,
            eventObject = eventObject,
            itemObject = extractIncomingItemObject(paramsObject, eventObject),
        )
        val messageId = streamingMessageId(
            itemId = itemId,
            turnId = resolvedTurnId,
            fallbackPrefix = fallbackPrefix,
        )
        appendTimelineMutation(
            threadId = threadId,
            mutation = TimelineMutation.SystemTextDelta(
                messageId = messageId,
                turnId = resolvedTurnId.orEmpty(),
                itemId = itemId,
                delta = delta,
                kind = kind,
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = messageId,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = kind,
                ),
            ),
            isRunning = true,
        )
    }

    private fun publishCommandExecutionStatus(
        context: NotificationContext,
        payloadObject: JsonObject,
        isCompleted: Boolean,
        onlyIfMissing: Boolean,
        paramsObject: JsonObject? = null,
    ) {
        val messageId = streamingMessageId(
            itemId = context.itemId,
            turnId = context.turnId,
            fallbackPrefix = "commandexecution",
        )
        val statusText = commandExecutionStatusText(
            decodeCommandExecutionRunState(
                payloadObject = payloadObject,
                paramsObject = paramsObject,
                isCompleted = isCompleted,
            ),
        )
        val existingItem = projectedTimelineItem(
            threadId = context.threadId,
            messageId = messageId,
            turnId = context.turnId,
            itemId = context.itemId,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
        ) ?: reusableSystemStreamingItem(
            threadId = context.threadId,
            turnId = context.turnId,
            itemId = context.itemId,
            kind = ConversationItemKind.COMMAND_EXECUTION,
            text = statusText,
        )
        if (onlyIfMissing && existingItem != null) {
            return
        }
        if (statusText.isBlank()) {
            return
        }
        upsertStreamingItem(
            threadId = context.threadId,
            item = timelineItem(
                id = existingItem?.id ?: messageId,
                speaker = ConversationSpeaker.SYSTEM,
                text = statusText,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                turnId = context.turnId,
                itemId = context.itemId ?: existingItem?.itemId,
                isStreaming = !isCompleted,
                orderIndex = resolveOrderIndex(
                    threadId = context.threadId,
                    messageId = existingItem?.id ?: messageId,
                    turnId = context.turnId,
                    itemId = context.itemId ?: existingItem?.itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.COMMAND_EXECUTION,
                    systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                        ConversationItemKind.COMMAND_EXECUTION,
                    ),
                ),
                systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                    ConversationItemKind.COMMAND_EXECUTION,
                ),
            ),
            isRunning = !isCompleted,
        )
    }

    private fun handleToolCallNotificationFallback(
        method: String,
        paramsObject: JsonObject,
    ): Boolean {
        val normalizedMethod = normalizeMethod(method)
        if (!normalizedMethod.contains("toolcall")) {
            return false
        }
        return when {
            normalizedMethod.contains("delta") || normalizedMethod.contains("partadded") -> {
                appendToolCallDelta(paramsObject)
                true
            }

            normalizedMethod.contains("started") -> handleStructuredFallback(paramsObject, itemType = "toolcall", isCompleted = false)
            normalizedMethod.contains("completed")
                || normalizedMethod.contains("finished")
                || normalizedMethod.contains("done") -> handleStructuredFallback(paramsObject, itemType = "toolcall", isCompleted = true)

            else -> handleStructuredFallback(paramsObject, itemType = "toolcall", isCompleted = false)
        }
    }

    private fun handleFileChangeNotificationFallback(
        method: String,
        paramsObject: JsonObject,
    ): Boolean {
        val normalizedMethod = normalizeMethod(method)
        if (!normalizedMethod.contains("filechange")) {
            return false
        }
        return when {
            normalizedMethod.contains("delta") || normalizedMethod.contains("partadded") -> {
                appendFileChangeDelta(paramsObject)
                true
            }

            normalizedMethod.contains("started") -> handleStructuredFallback(paramsObject, itemType = "filechange", isCompleted = false)
            normalizedMethod.contains("completed")
                || normalizedMethod.contains("finished")
                || normalizedMethod.contains("done") -> handleStructuredFallback(paramsObject, itemType = "filechange", isCompleted = true)

            else -> handleStructuredFallback(paramsObject, itemType = "filechange", isCompleted = false)
        }
    }

    private fun handleDiffNotificationFallback(
        method: String,
        paramsObject: JsonObject,
    ): Boolean {
        val normalizedMethod = normalizeMethod(method)
        val isDiffMethod = normalizedMethod.contains("turndiff")
            || normalizedMethod.contains("/diff/")
            || normalizedMethod.startsWith("diff/")
            || normalizedMethod.endsWith("/diff")
            || normalizedMethod.contains("itemdiff")
        if (!isDiffMethod) {
            return false
        }
        return handleStructuredFallback(paramsObject, itemType = "diff", isCompleted = true)
    }

    private fun handleStructuredFallback(
        paramsObject: JsonObject,
        itemType: String,
        isCompleted: Boolean,
    ): Boolean {
        val eventObject = envelopeEventObject(paramsObject)
        val itemObject = extractIncomingItemObject(paramsObject, eventObject) ?: (eventObject ?: paramsObject)
        return handleStructuredItemLifecycle(
            itemObject = itemObject,
            paramsObject = paramsObject,
            itemType = itemType,
            isCompleted = isCompleted,
        )
    }

    private fun touchThread(
        threadId: String,
        isRunning: Boolean? = null,
    ) {
        val now = nowEpochMs()
        backingThreads.value = backingThreads.value.map { snapshot ->
            if (snapshot.id != threadId) {
                snapshot
            } else {
                snapshot.copy(
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                ).withResolvedLiveThreadState(
                    threadId = threadId,
                    isRunning = isRunning,
                )
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private fun appendTimelineMutation(
        threadId: String,
        mutation: TimelineMutation,
        isRunning: Boolean? = null,
    ) {
        val now = nowEpochMs()
        backingThreads.value = backingThreads.value.map { snapshot ->
            if (snapshot.id != threadId) {
                snapshot
            } else {
                val nextMutations = snapshot.timelineMutations + mutation
                if (reviewDebugThreadIds.contains(threadId)) {
                    val projected = TurnTimelineReducer.reduceProjected(nextMutations)
                    emitReviewDebugLog(
                        "stage=timelineMutation threadId=$threadId mutation=${describeReviewDebugMutation(mutation)} projectedCount=${projected.size} lastProjected=${projected.lastOrNull()?.let(::describeReviewDebugItem).orEmpty()}",
                    )
                }
                snapshot.copy(
                    timelineMutations = nextMutations,
                    preview = if (mutationAffectsThreadPreview(mutation)) {
                        derivePreview(snapshot = snapshot, nextMutations = nextMutations)
                    } else {
                        snapshot.preview
                    },
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                ).withResolvedLiveThreadState(
                    threadId = threadId,
                    isRunning = isRunning,
                )
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private fun upsertAssistantTimelineItem(
        threadId: String,
        item: com.emanueledipietro.remodex.model.RemodexConversationItem,
        isRunning: Boolean? = null,
    ) {
        val now = nowEpochMs()
        val upsertMutation = TimelineMutation.Upsert(item)
        backingThreads.value = backingThreads.value.map { snapshot ->
            if (snapshot.id != threadId) {
                snapshot
            } else {
                val nextMutations = coalesceAssistantMessageMutations(
                    mutations = snapshot.timelineMutations,
                    item = item,
                )
                snapshot.copy(
                    timelineMutations = nextMutations,
                    preview = if (mutationAffectsThreadPreview(upsertMutation)) {
                        derivePreview(snapshot = snapshot, nextMutations = nextMutations)
                    } else {
                        snapshot.preview
                    },
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                ).withResolvedLiveThreadState(
                    threadId = threadId,
                    isRunning = isRunning,
                )
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private fun upsertStreamingItem(
        threadId: String,
        item: com.emanueledipietro.remodex.model.RemodexConversationItem,
        isRunning: Boolean? = null,
    ) {
        appendTimelineMutation(
            threadId = threadId,
            mutation = TimelineMutation.Upsert(item),
            isRunning = isRunning,
        )
    }

    private fun coalesceAssistantMessageMutations(
        mutations: List<TimelineMutation>,
        item: com.emanueledipietro.remodex.model.RemodexConversationItem,
    ): List<TimelineMutation> {
        val nextMutations = ArrayList<TimelineMutation>(mutations.size + 1)
        mutations.forEach { mutation ->
            if (!matchesAssistantMessageMutation(mutation = mutation, messageId = item.id)) {
                nextMutations += mutation
            }
        }
        nextMutations += TimelineMutation.Upsert(item)
        return nextMutations
    }

    private fun matchesAssistantMessageMutation(
        mutation: TimelineMutation,
        messageId: String,
    ): Boolean {
        return when (mutation) {
            is TimelineMutation.Upsert -> {
                mutation.item.id == messageId &&
                    mutation.item.speaker == ConversationSpeaker.ASSISTANT &&
                    mutation.item.kind == ConversationItemKind.CHAT
            }

            is TimelineMutation.AssistantTextDelta -> mutation.messageId == messageId
            is TimelineMutation.Complete -> mutation.messageId == messageId
            is TimelineMutation.ReasoningTextDelta,
            is TimelineMutation.ActivityLine,
            is TimelineMutation.SystemTextDelta,
            -> false
        }
    }

    private fun appendStreamingTextDelta(
        existingText: String,
        delta: String,
    ): String {
        if (delta.isEmpty()) {
            return existingText
        }
        if (existingText.isEmpty()) {
            return delta
        }
        return existingText + delta
    }

    private fun derivePreview(
        snapshot: ThreadSyncSnapshot,
        nextMutations: List<TimelineMutation>,
    ): String {
        val previewItem = TurnTimelineReducer.reduceProjected(nextMutations)
            .lastOrNull { item ->
                item.kind == ConversationItemKind.CHAT
                    && (item.speaker == ConversationSpeaker.USER || item.speaker == ConversationSpeaker.ASSISTANT)
            }
        return previewItem?.text
            ?.lineSequence()
            ?.joinToString(separator = " ")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(160)
            ?: snapshot.preview
    }

    private fun resolveOrderIndex(
        threadId: String,
        messageId: String,
        turnId: String?,
        itemId: String?,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
        systemTurnOrderingHint: ConversationSystemTurnOrderingHint =
            ConversationSystemTurnOrderingHint.AUTO,
    ): Long {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return 0L
        val existingItem = projectedTimelineItem(
            threadId = threadId,
            messageId = messageId,
            turnId = turnId,
            itemId = itemId,
            speaker = speaker,
            kind = kind,
        )
        return when {
            existingItem == null -> nextOrderIndex(snapshot)
            shouldAdvanceInterleavedSystemActivityOrderIndex(
                snapshot = snapshot,
                existingItem = existingItem,
                turnId = turnId,
                speaker = speaker,
                kind = kind,
                systemTurnOrderingHint = systemTurnOrderingHint,
            ) -> nextOrderIndex(snapshot)

            else -> existingItem.orderIndex
        }
    }

    private fun shouldAdvanceInterleavedSystemActivityOrderIndex(
        snapshot: ThreadSyncSnapshot,
        existingItem: com.emanueledipietro.remodex.model.RemodexConversationItem,
        turnId: String?,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
        systemTurnOrderingHint: ConversationSystemTurnOrderingHint,
    ): Boolean {
        val resolvedTurnId = turnId?.trim()?.takeIf(String::isNotEmpty)
            ?: existingItem.turnId?.trim()?.takeIf(String::isNotEmpty)
            ?: return false
        if (speaker != ConversationSpeaker.SYSTEM) {
            return false
        }
        if (
            !TurnTimelineReducer.shouldPreserveLateSystemActivityChronology(
                kind = kind,
                hint = if (
                    systemTurnOrderingHint == ConversationSystemTurnOrderingHint.AUTO
                ) {
                    existingItem.systemTurnOrderingHint
                } else {
                    systemTurnOrderingHint
                },
            )
        ) {
            return false
        }

        return TurnTimelineReducer.reduce(snapshot.timelineMutations).any { candidate ->
            candidate.turnId == resolvedTurnId &&
                candidate.speaker == ConversationSpeaker.ASSISTANT &&
                candidate.orderIndex > existingItem.orderIndex
        }
    }

    private fun isInterleavableSystemActivityKind(
        kind: ConversationItemKind,
    ): Boolean {
        return TurnTimelineReducer.isInterleavableSystemActivityKind(kind)
    }

    private fun explicitSystemTurnOrderingHint(
        kind: ConversationItemKind,
    ): ConversationSystemTurnOrderingHint {
        return when (kind) {
            ConversationItemKind.TOOL_ACTIVITY,
            ConversationItemKind.MCP_TOOL_CALL,
            ConversationItemKind.WEB_SEARCH,
            ConversationItemKind.COMMAND_EXECUTION,
            ConversationItemKind.CONTEXT_COMPACTION,
            ConversationItemKind.IMAGE_VIEW,
            ConversationItemKind.IMAGE_GENERATION,
            ConversationItemKind.SUBAGENT_ACTION -> {
                ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE
            }

            ConversationItemKind.CHAT,
            ConversationItemKind.REASONING,
            ConversationItemKind.FILE_CHANGE,
            ConversationItemKind.PLAN_UPDATE,
            ConversationItemKind.PLAN,
            ConversationItemKind.USER_INPUT_PROMPT -> ConversationSystemTurnOrderingHint.AUTO
        }
    }

    private fun projectedTimelineItem(
        threadId: String,
        messageId: String,
        turnId: String?,
        itemId: String?,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): com.emanueledipietro.remodex.model.RemodexConversationItem? {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        return TurnTimelineReducer.reduce(snapshot.timelineMutations).firstOrNull { item ->
            (
                item.id == messageId &&
                    item.kind == kind &&
                    item.speaker == speaker
                )
                || (
                    item.itemId != null &&
                        itemId != null &&
                        item.itemId == itemId &&
                        item.kind == kind &&
                        item.speaker == speaker &&
                        (turnId == null || item.turnId == null || item.turnId == turnId)
                    )
                || (
                    item.itemId == null &&
                        itemId == null &&
                        item.turnId == turnId &&
                        item.kind == kind &&
                        item.speaker == speaker
                )
        }
    }

    private fun reusableSystemStreamingItem(
        threadId: String,
        turnId: String?,
        itemId: String?,
        kind: ConversationItemKind,
        text: String,
    ): com.emanueledipietro.remodex.model.RemodexConversationItem? {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        val normalizedTurnId = turnId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val normalizedItemId = itemId?.trim()?.takeIf(String::isNotEmpty)
        val projected = TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
        if (kind == ConversationItemKind.COMMAND_EXECUTION) {
            val commandKey = normalizedCommandExecutionPreviewKey(text) ?: return null
            return projected
                .asReversed()
                .firstOrNull { candidate ->
                    candidate.speaker == ConversationSpeaker.SYSTEM &&
                        candidate.kind == ConversationItemKind.COMMAND_EXECUTION &&
                        candidate.turnId == normalizedTurnId &&
                        (
                            (normalizedItemId != null && candidate.itemId == normalizedItemId) ||
                                normalizedCommandExecutionPreviewKey(candidate.text) == commandKey
                            )
                }
        }

        if (normalizedItemId == null) {
            return null
        }

        val turnScopedMessageId = streamingMessageId(
            itemId = null,
            turnId = normalizedTurnId,
            fallbackPrefix = streamingFallbackPrefix(kind),
        )
        val candidates = projected.filter { candidate ->
            candidate.speaker == ConversationSpeaker.SYSTEM &&
                candidate.kind == kind &&
                candidate.turnId == normalizedTurnId &&
                candidate.itemId == null &&
                candidate.id == turnScopedMessageId
        }
        return candidates.singleOrNull()
    }

    private fun streamingFallbackPrefix(
        kind: ConversationItemKind,
    ): String {
        return when (kind) {
            ConversationItemKind.REASONING -> "reasoning"
            ConversationItemKind.TOOL_ACTIVITY -> "toolactivity"
            ConversationItemKind.MCP_TOOL_CALL -> "mcptoolcall"
            ConversationItemKind.WEB_SEARCH -> "websearch"
            ConversationItemKind.COMMAND_EXECUTION -> "commandexecution"
            ConversationItemKind.CONTEXT_COMPACTION -> "contextcompaction"
            ConversationItemKind.IMAGE_VIEW -> "imageview"
            ConversationItemKind.IMAGE_GENERATION -> "imagegeneration"
            ConversationItemKind.FILE_CHANGE -> "filechange"
            ConversationItemKind.PLAN_UPDATE -> "planupdate"
            ConversationItemKind.PLAN -> "plan"
            ConversationItemKind.USER_INPUT_PROMPT -> "userinputprompt"
            ConversationItemKind.SUBAGENT_ACTION -> "subagentaction"
            ConversationItemKind.CHAT -> "chat"
        }
    }

    private fun reusablePlanStreamingItem(
        threadId: String,
        turnId: String?,
        itemId: String?,
    ): com.emanueledipietro.remodex.model.RemodexConversationItem? {
        if (itemId.isNullOrBlank()) {
            return null
        }
        val normalizedTurnId = turnId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        return TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
            .asReversed()
            .firstOrNull { candidate ->
                candidate.speaker == ConversationSpeaker.SYSTEM &&
                    candidate.kind == ConversationItemKind.PLAN &&
                    candidate.turnId == normalizedTurnId &&
                    candidate.itemId.isNullOrBlank()
            }
    }

    private fun resolvedPlanText(
        existingText: String?,
        incomingText: String?,
        resolvedPlanState: RemodexPlanState?,
        isStreaming: Boolean,
        incomingTextIsDelta: Boolean,
    ): String? {
        val trimmedIncoming = incomingText?.trim()?.takeIf(String::isNotEmpty)
        val trimmedExisting = existingText?.takeIf(String::isNotBlank)
        if (trimmedIncoming != null) {
            return when {
                trimmedExisting.isNullOrBlank() -> trimmedIncoming
                isPlanPlaceholderText(trimmedExisting) -> trimmedIncoming
                isPlanStateFallbackText(
                    text = trimmedExisting,
                    planState = resolvedPlanState,
                ) -> trimmedIncoming
                incomingTextIsDelta -> trimmedExisting + incomingText
                !isStreaming -> trimmedIncoming
                else -> ThreadHistoryReconciler.mergeStreamingSnapshotText(
                    existingText = trimmedExisting,
                    incomingText = trimmedIncoming,
                )
            }
        }
        return resolvedPlanState?.explanation?.trim()?.takeIf(String::isNotEmpty)
            ?: resolvedPlanState?.steps?.firstOrNull()?.step?.trim()?.takeIf(String::isNotEmpty)
            ?: trimmedExisting
    }

    private fun isPlanPlaceholderText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed == "Planning..." || trimmed == "Plan updated."
    }

    private fun isPlanStateFallbackText(
        text: String,
        planState: RemodexPlanState?,
    ): Boolean {
        val trimmed = text.trim()
        val explanation = planState?.explanation?.trim().orEmpty()
        val firstStep = planState?.steps?.firstOrNull()?.step?.trim().orEmpty()
        return trimmed == explanation || trimmed == firstStep
    }

    private fun reusableAssistantCompletionItem(
        threadId: String,
        turnId: String?,
        itemId: String?,
        text: String,
        preferredMessageId: String,
    ): com.emanueledipietro.remodex.model.RemodexConversationItem? {
        reusableAssistantStreamingItem(
            threadId = threadId,
            turnId = turnId,
            itemId = itemId,
            preferredMessageId = preferredMessageId,
        )?.let { return it }
        latestAssistantSegmentItem(
            threadId = threadId,
            turnId = turnId,
            itemId = itemId,
            baseMessageId = preferredMessageId,
        )?.let { return it }
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        return findReusableAssistantCompletionItemValue(
            items = TurnTimelineReducer.reduceProjected(snapshot.timelineMutations),
            turnId = turnId,
            itemId = itemId,
            text = text,
        )
    }

    private fun reusableAssistantStreamingItem(
        threadId: String,
        turnId: String?,
        itemId: String?,
        preferredMessageId: String,
    ): com.emanueledipietro.remodex.model.RemodexConversationItem? {
        val exact = projectedTimelineItem(
            threadId = threadId,
            messageId = preferredMessageId,
            turnId = turnId,
            itemId = itemId,
            speaker = ConversationSpeaker.ASSISTANT,
            kind = ConversationItemKind.CHAT,
        )
        if (exact?.isStreaming == true) {
            return exact
        }
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        val normalizedTurnId = turnId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val normalizedItemId = itemId?.trim()?.takeIf(String::isNotEmpty)
        return TurnTimelineReducer.reduce(snapshot.timelineMutations)
            .asReversed()
            .firstOrNull { candidate ->
                candidate.speaker == ConversationSpeaker.ASSISTANT &&
                    candidate.kind == ConversationItemKind.CHAT &&
                    candidate.turnId == normalizedTurnId &&
                    candidate.isStreaming &&
                    (
                        candidate.itemId == null ||
                            (normalizedItemId != null && candidate.itemId == normalizedItemId)
                        )
            }
    }

    private fun assistantBaseMessageId(
        turnId: String?,
        itemId: String?,
    ): String = streamingMessageId(
        itemId = itemId,
        turnId = turnId,
        fallbackPrefix = "assistant",
    )

    private fun nextAssistantSegmentMessageId(
        threadId: String,
        turnId: String,
        itemId: String?,
    ): String {
        val baseMessageId = assistantBaseMessageId(
            turnId = turnId,
            itemId = itemId,
        )
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return baseMessageId
        val existingSegments = TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
            .count { candidate ->
                candidate.speaker == ConversationSpeaker.ASSISTANT &&
                    candidate.kind == ConversationItemKind.CHAT &&
                    candidate.turnId == turnId &&
                    assistantSegmentMatches(
                        candidate = candidate,
                        itemId = itemId,
                        baseMessageId = baseMessageId,
                    )
            }
        return if (existingSegments == 0) {
            baseMessageId
        } else {
            "$baseMessageId-seg-$existingSegments"
        }
    }

    private fun latestAssistantSegmentItem(
        threadId: String,
        turnId: String?,
        itemId: String?,
        baseMessageId: String,
    ): com.emanueledipietro.remodex.model.RemodexConversationItem? {
        val normalizedTurnId = turnId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        return TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
            .asReversed()
            .firstOrNull { candidate ->
                candidate.speaker == ConversationSpeaker.ASSISTANT &&
                    candidate.kind == ConversationItemKind.CHAT &&
                    candidate.turnId == normalizedTurnId &&
                    assistantSegmentMatches(
                        candidate = candidate,
                        itemId = itemId,
                        baseMessageId = baseMessageId,
                    )
            }
    }

    private fun assistantSegmentMatches(
        candidate: com.emanueledipietro.remodex.model.RemodexConversationItem,
        itemId: String?,
        baseMessageId: String,
    ): Boolean {
        val normalizedItemId = itemId?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedItemId != null) {
            return candidate.itemId == normalizedItemId
        }
        if (!candidate.itemId.isNullOrBlank()) {
            return false
        }
        return candidate.id == baseMessageId || candidate.id.startsWith("$baseMessageId-seg-")
    }

    private fun resolveAssistantOrderIndex(
        threadId: String,
        existingItem: com.emanueledipietro.remodex.model.RemodexConversationItem?,
    ): Long {
        if (existingItem != null) {
            return existingItem.orderIndex
        }
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId }
        return snapshot?.let(::nextOrderIndex) ?: 0L
    }

    private fun completeAssistantStreamingItemsForTurn(
        threadId: String,
        turnId: String?,
    ) {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return
        val completionMutations = TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
            .filter { item ->
                item.speaker == ConversationSpeaker.ASSISTANT &&
                    item.kind == ConversationItemKind.CHAT &&
                    item.isStreaming &&
                    (
                        turnId == null ||
                            item.turnId == turnId ||
                            item.turnId.isNullOrBlank()
                        )
            }
            .map { item -> TimelineMutation.Complete(messageId = item.id) }
        if (completionMutations.isEmpty()) {
            return
        }
        val now = nowEpochMs()
        backingThreads.value = backingThreads.value.map { existing ->
            if (existing.id != threadId) {
                existing
            } else {
                existing.copy(
                    timelineMutations = existing.timelineMutations + completionMutations,
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                ).withResolvedLiveThreadState(threadId = threadId)
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private fun shouldFinalizeAssistantStreamBeforeStructuredItem(
        itemType: String,
    ): Boolean {
        return itemType == "toolcall" ||
            itemType == "commandexecution" ||
            itemType == "enteredreviewmode" ||
            itemType == "contextcompaction" ||
            itemType == "filechange" ||
            itemType == "diff" ||
            itemType == "mcptoolcall" ||
            itemType == "websearch" ||
            itemType == "imageview" ||
            itemType == "imagegeneration"
    }

    private fun beginAssistantMessage(
        threadId: String,
        turnId: String,
        itemId: String? = null,
    ) {
        val baseMessageId = assistantBaseMessageId(
            turnId = turnId,
            itemId = itemId,
        )
        val existingItem = reusableAssistantStreamingItem(
            threadId = threadId,
            turnId = turnId,
            itemId = itemId,
            preferredMessageId = baseMessageId,
        )
        val resolvedMessageId = existingItem?.id ?: nextAssistantSegmentMessageId(
            threadId = threadId,
            turnId = turnId,
            itemId = itemId,
        )
        val resolvedItemId = itemId ?: existingItem?.itemId
        trackAssistantMessageReference(
            threadId = threadId,
            turnId = turnId,
            itemId = resolvedItemId,
            messageId = resolvedMessageId,
        )
        upsertAssistantTimelineItem(
            threadId = threadId,
            item = timelineItem(
                id = resolvedMessageId,
                speaker = ConversationSpeaker.ASSISTANT,
                text = assistantLifecycleStartedText(existingItem?.text),
                kind = ConversationItemKind.CHAT,
                turnId = turnId,
                itemId = resolvedItemId,
                isStreaming = true,
                createdAtEpochMs = existingItem?.createdAtEpochMs,
                orderIndex = resolveAssistantOrderIndex(
                    threadId = threadId,
                    existingItem = existingItem,
                ),
                assistantChangeSet = existingItem?.assistantChangeSet,
            ),
            isRunning = true,
        )
    }

    private fun shouldSuppressAssistantCompletion(
        threadId: String,
        turnId: String?,
        itemId: String?,
        text: String,
    ): Boolean {
        val previous = assistantCompletionFingerprintByThread[threadId] ?: return false
        return shouldSuppressAnonymousAssistantCompletionValue(
            turnId = turnId,
            itemId = itemId,
            text = text,
            previousText = previous.text,
            elapsedMs = nowEpochMs() - previous.timestampMs,
        )
    }

    private fun recordAssistantCompletionFingerprint(
        threadId: String,
        text: String,
    ) {
        val normalizedText = normalizeAssistantCompletionTextValue(text)
        if (normalizedText.isEmpty()) {
            return
        }
        assistantCompletionFingerprintByThread[threadId] = AssistantCompletionFingerprint(
            text = normalizedText,
            timestampMs = nowEpochMs(),
        )
    }

    private fun hasStreamingMessage(threadId: String): Boolean {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return false
        return TurnTimelineReducer.reduceProjected(snapshot.timelineMutations).any { item -> item.isStreaming }
    }

    private fun confirmLatestPendingUserMessage(
        threadId: String,
        turnId: String,
    ) {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return
        val pendingUser = TurnTimelineReducer.reduce(snapshot.timelineMutations)
            .asReversed()
            .firstOrNull { item ->
                item.speaker == ConversationSpeaker.USER &&
                    item.deliveryState == RemodexMessageDeliveryState.PENDING &&
                    (item.turnId == null || item.turnId == turnId)
            } ?: return
        appendTimelineMutation(
            threadId = threadId,
            mutation = TimelineMutation.Upsert(
                pendingUser.copy(
                    deliveryState = RemodexMessageDeliveryState.CONFIRMED,
                    turnId = turnId,
                ),
            ),
            isRunning = true,
        )
    }

    private fun removeLatestPendingUserMessage(
        threadId: String,
        matchingText: String,
        matchingAttachments: List<RemodexComposerAttachment>,
    ) {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return
        val targetItem = TurnTimelineReducer.reduce(snapshot.timelineMutations)
            .asReversed()
            .firstOrNull { item ->
                item.speaker == ConversationSpeaker.USER &&
                    item.deliveryState == RemodexMessageDeliveryState.PENDING &&
                    item.text == androidUserMessageText(
                        prompt = matchingText,
                        attachmentCount = matchingAttachments.size,
                    ) &&
                    item.attachments.map(RemodexConversationAttachment::uriString) ==
                    matchingAttachments.map(RemodexComposerAttachment::uriString)
            } ?: return
        val nextMutations = snapshot.timelineMutations.filterNot { mutation ->
            when (mutation) {
                is TimelineMutation.Upsert -> mutation.item.id == targetItem.id
                is TimelineMutation.Complete -> mutation.messageId == targetItem.id
                else -> false
            }
        }
        val now = nowEpochMs()
        backingThreads.value = backingThreads.value.map { existing ->
            if (existing.id != threadId) {
                existing
            } else {
                existing.copy(
                    timelineMutations = nextMutations,
                    preview = derivePreview(existing, nextMutations),
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                ).withResolvedLiveThreadState(threadId = threadId)
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private fun mutationAffectsThreadPreview(mutation: TimelineMutation): Boolean {
        return mutationAffectsThreadPreviewValue(mutation)
    }

    private fun removeTimelineMessage(
        threadId: String,
        messageId: String,
    ) {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return
        val nextMutations = snapshot.timelineMutations.filterNot { mutation ->
            when (mutation) {
                is TimelineMutation.Upsert -> mutation.item.id == messageId
                is TimelineMutation.Complete -> mutation.messageId == messageId
                is TimelineMutation.AssistantTextDelta -> mutation.messageId == messageId
                is TimelineMutation.ReasoningTextDelta -> mutation.messageId == messageId
                is TimelineMutation.ActivityLine -> mutation.messageId == messageId
                is TimelineMutation.SystemTextDelta -> mutation.messageId == messageId
            }
        }
        if (nextMutations.size == snapshot.timelineMutations.size) {
            return
        }
        val now = nowEpochMs()
        backingThreads.value = backingThreads.value.map { existing ->
            if (existing.id != threadId) {
                existing
            } else {
                existing.copy(
                    timelineMutations = nextMutations,
                    preview = derivePreview(existing, nextMutations),
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                ).withResolvedLiveThreadState(threadId = threadId)
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private fun completeStreamingItemsForThread(
        threadId: String,
        turnId: String?,
    ) {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return
        val completionMutations = TurnTimelineReducer.reduce(snapshot.timelineMutations)
            .filter { item ->
                item.isStreaming &&
                    (
                        turnId == null ||
                            item.turnId == turnId ||
                            item.turnId.isNullOrBlank()
                        )
            }
            .map { item -> TimelineMutation.Complete(messageId = item.id) }
        if (completionMutations.isEmpty()) {
            return
        }
        val now = nowEpochMs()
        backingThreads.value = backingThreads.value.map { existing ->
            if (existing.id != threadId) {
                existing
            } else {
                existing.copy(
                    timelineMutations = existing.timelineMutations + completionMutations,
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                ).withResolvedLiveThreadState(threadId = threadId)
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private suspend fun postTurnCompletionCatchup(
        threadId: String,
        turnId: String?,
    ) {
        for (delayMs in postTurnCatchupDelaysMs) {
            if (!shouldContinueTurnCompletionCatchup(threadId = threadId, turnId = turnId)) {
                return
            }
            delay(delayMs)
            refreshThreads()
            hydrateThread(threadId)
        }
    }

    private fun shouldContinueTurnCompletionCatchup(
        threadId: String,
        turnId: String?,
    ): Boolean {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return false
        val projected = TurnTimelineReducer.reduceProjected(snapshot.timelineMutations)
        val hasAssistantForTurn = projected.any { item ->
            item.speaker == ConversationSpeaker.ASSISTANT &&
                item.text.isNotBlank() &&
                (turnId == null || item.turnId == turnId)
        }
        val hasCompletedThinkingPlaceholder = projected.any { item ->
            item.speaker == ConversationSpeaker.SYSTEM &&
                item.kind == ConversationItemKind.REASONING &&
                !item.isStreaming &&
                (turnId == null || item.turnId == turnId || item.turnId.isNullOrBlank()) &&
                ThinkingDisclosureParser.normalizedThinkingContent(item.text).isEmpty()
        }
        return !hasAssistantForTurn || hasCompletedThinkingPlaceholder
    }

    private fun supportsStructuredLifecycleItem(itemType: String): Boolean {
        return itemType == "reasoning"
            || itemType == "filechange"
            || itemType == "toolcall"
            || itemType == "commandexecution"
            || itemType == "diff"
            || itemType == "plan"
            || itemType == "enteredreviewmode"
            || itemType == "contextcompaction"
            || itemType == "userinputprompt"
            || itemType == "requestuserinput"
            || itemType == "mcptoolcall"
            || itemType == "websearch"
            || itemType == "imageview"
            || itemType == "imagegeneration"
            || isSubagentHistoryItemType(itemType)
            || (
                itemType.isNotBlank() &&
                    itemType != "hookprompt" &&
                    itemType != "usermessage" &&
                    itemType != "agentmessage" &&
                    itemType != "assistantmessage" &&
                    itemType != "message"
                )
    }

    private fun decodeStructuredTimelinePresentation(
        itemObject: JsonObject,
        itemType: String,
        isCompleted: Boolean,
    ): StructuredTimelinePresentation {
        return when {
            itemType == "reasoning" -> StructuredTimelinePresentation(
                kind = ConversationItemKind.REASONING,
                body = decodeIncomingReasoningBody(itemObject),
            )

            itemType == "filechange" || itemType == "diff" -> StructuredTimelinePresentation(
                kind = ConversationItemKind.FILE_CHANGE,
                body = decodeFileChangeLifecycleBody(
                    itemObject = itemObject,
                    itemType = itemType,
                    isCompleted = isCompleted,
                ),
            )

            itemType == "toolcall" -> {
                if (isLikelyFileChangeToolCall(itemObject, decodeItemText(itemObject))) {
                    StructuredTimelinePresentation(
                        kind = ConversationItemKind.FILE_CHANGE,
                        body = decodeFileChangeLifecycleBody(
                            itemObject = itemObject,
                            itemType = itemType,
                            isCompleted = isCompleted,
                        ),
                    )
                } else {
                    StructuredTimelinePresentation(
                        kind = ConversationItemKind.TOOL_ACTIVITY,
                        body = decodeToolCallActivityBody(
                            itemObject = itemObject,
                            isCompleted = isCompleted,
                        ),
                        systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                            ConversationItemKind.TOOL_ACTIVITY,
                        ),
                    )
                }
            }

            itemType == "commandexecution" || itemType == "enteredreviewmode" || itemType == "contextcompaction" -> {
                val resolvedKind = if (itemType == "contextcompaction") {
                    ConversationItemKind.CONTEXT_COMPACTION
                } else {
                    ConversationItemKind.COMMAND_EXECUTION
                }
                StructuredTimelinePresentation(
                    kind = resolvedKind,
                    body = when (itemType) {
                        "enteredreviewmode" -> decodeEnteredReviewModeText(itemObject)
                        "contextcompaction" -> if (isCompleted) "Context compacted" else "Compacting context..."
                        else -> decodeCommandExecutionStatusText(
                            payloadObject = itemObject,
                            isCompleted = isCompleted,
                        )
                    },
                    systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                        resolvedKind,
                    ),
                )
            }

            itemType == "plan" -> StructuredTimelinePresentation(
                kind = ConversationItemKind.PLAN,
                body = decodePlanItemText(itemObject).ifBlank {
                    if (isCompleted) "Plan updated." else "Planning..."
                },
                planState = decodeHistoryPlanState(itemObject),
            )

            itemType == "userinputprompt" || itemType == "requestuserinput" -> StructuredTimelinePresentation(
                kind = ConversationItemKind.USER_INPUT_PROMPT,
                body = decodeStructuredUserInputSummary(itemObject),
                structuredUserInputRequest = decodeStructuredUserInputRequest(itemObject),
            )

            itemType == "mcptoolcall" -> StructuredTimelinePresentation(
                kind = ConversationItemKind.MCP_TOOL_CALL,
                body = decodeMcpToolCallBody(itemObject = itemObject, isCompleted = isCompleted),
                supportingText = decodeMcpToolCallSupportingText(itemObject),
                systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                    ConversationItemKind.MCP_TOOL_CALL,
                ),
            )

            itemType == "websearch" -> StructuredTimelinePresentation(
                kind = ConversationItemKind.WEB_SEARCH,
                body = decodeWebSearchBody(isCompleted = isCompleted),
                supportingText = decodeWebSearchSupportingText(itemObject),
                systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                    ConversationItemKind.WEB_SEARCH,
                ),
            )

            itemType == "imageview" -> StructuredTimelinePresentation(
                kind = ConversationItemKind.IMAGE_VIEW,
                body = "Viewed Image",
                supportingText = decodeImageViewSupportingText(itemObject),
                attachments = decodeImageViewAttachments(itemObject),
                systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                    ConversationItemKind.IMAGE_VIEW,
                ),
            )

            itemType == "imagegeneration" -> StructuredTimelinePresentation(
                kind = ConversationItemKind.IMAGE_GENERATION,
                body = "Generated Image",
                supportingText = decodeImageGenerationSupportingText(itemObject),
                attachments = decodeImageGenerationAttachments(itemObject),
                systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                    ConversationItemKind.IMAGE_GENERATION,
                ),
            )

            isSubagentHistoryItemType(itemType) -> StructuredTimelinePresentation(
                kind = ConversationItemKind.SUBAGENT_ACTION,
                body = "",
                subagentAction = decodeSubagentAction(itemObject),
                systemTurnOrderingHint = explicitSystemTurnOrderingHint(
                    ConversationItemKind.SUBAGENT_ACTION,
                ),
            )

            else -> StructuredTimelinePresentation(
                kind = ConversationItemKind.CHAT,
                body = decodeUnknownStructuredItemBody(
                    itemType = itemType,
                    itemObject = itemObject,
                    isCompleted = isCompleted,
                ),
            )
        }
    }

    private fun decodeStructuredUserInputSummary(itemObject: JsonObject): String {
        return decodeItemText(itemObject)
            .trim()
            .takeIf(String::isNotEmpty)
            ?: itemObject.firstArray("questions")
                ?.size
                ?.takeIf { size -> size > 0 }
                ?.let { size -> "Codex needs $size answer${if (size == 1) "" else "s"} to continue." }
            ?: "Codex needs your input to continue."
    }

    private fun decodeMcpToolCallBody(
        itemObject: JsonObject,
        isCompleted: Boolean,
    ): String {
        val invocation = formatMcpInvocation(itemObject)
        val normalizedStatus = normalizeStatus(itemObject.firstString("status").orEmpty())
        return when {
            normalizedStatus.contains("fail") || normalizedStatus.contains("error") -> "MCP failed: $invocation"
            isCompleted -> "Called $invocation"
            else -> "Calling $invocation"
        }
    }

    private fun decodeMcpToolCallSupportingText(itemObject: JsonObject): String? {
        val errorMessage = itemObject.firstObject("error")
            ?.firstString("message", "detail", "description")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (errorMessage != null) {
            return summarizeTimelineSupportingText(errorMessage)
        }
        return summarizeTimelineSupportingText(
            extractStructuredOutputText(itemObject)
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.joinToString(separator = "\n")
            ?.takeIf(String::isNotBlank),
        )
    }

    private fun formatMcpInvocation(itemObject: JsonObject): String {
        val server = itemObject.firstString("server", "serverName", "server_name")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val tool = itemObject.firstString("tool", "name")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        return listOfNotNull(server, tool)
            .joinToString(separator = "/")
            .ifBlank { "MCP tool" }
    }

    private fun decodeWebSearchBody(
        isCompleted: Boolean,
    ): String = if (isCompleted) "Searched the web" else "Searching the web"

    private fun decodeWebSearchSupportingText(itemObject: JsonObject): String? {
        val actionObject = itemObject.firstObject("action")
        val query = itemObject.firstString("query")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val actionType = normalizeItemType(actionObject?.firstString("type").orEmpty())
        return when (actionType) {
            "search" -> {
                val queries = actionObject?.firstArray("queries")
                    .orEmpty()
                    .mapNotNull(JsonElement::stringOrNull)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                when {
                    queries.isNotEmpty() -> queries.joinToString(separator = "\n")
                    !query.isNullOrBlank() -> query
                    else -> actionObject?.firstString("query")?.trim()?.takeIf(String::isNotEmpty)
                }
            }

            "openpage" -> actionObject?.firstString("url")?.trim()?.takeIf(String::isNotEmpty)
            "findinpage" -> listOfNotNull(
                actionObject?.firstString("url")?.trim()?.takeIf(String::isNotEmpty),
                actionObject?.firstString("pattern")?.trim()?.takeIf(String::isNotEmpty),
            ).joinToString(separator = "\n").takeIf(String::isNotBlank)

            else -> query
        }?.let(::summarizeTimelineSupportingText)
    }

    private fun summarizeTimelineSupportingText(
        value: String?,
        maxLines: Int = 24,
        maxChars: Int = 4_000,
    ): String? {
        val normalized = value
            ?.lineSequence()
            ?.map(String::trimEnd)
            ?.joinToString(separator = "\n")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null

        val lines = normalized.lineSequence().toList()
        val limitedByLines = if (lines.size > maxLines) {
            lines.take(maxLines).joinToString(separator = "\n")
        } else {
            normalized
        }
        val limitedByChars = if (limitedByLines.length > maxChars) {
            limitedByLines.take(maxChars)
        } else {
            limitedByLines
        }.trimEnd()

        val wasTruncated = lines.size > maxLines || normalized.length > maxChars
        return if (wasTruncated) {
            "$limitedByChars\n…"
        } else {
            limitedByChars
        }
    }

    private fun decodeImageViewSupportingText(itemObject: JsonObject): String? {
        return itemObject.firstString("path")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun decodeImageViewAttachments(itemObject: JsonObject): List<RemodexConversationAttachment> {
        val path = itemObject.firstString("path")?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        return listOfNotNull(
            conversationAttachmentForPath(
                rawPath = path,
                itemId = itemObject.firstString("id"),
                fallbackName = "Viewed image",
            ),
        )
    }

    private fun decodeImageGenerationSupportingText(itemObject: JsonObject): String? {
        val lines = buildList {
            itemObject.firstString("revisedPrompt", "revised_prompt")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(::add)
            itemObject.firstString("savedPath", "saved_path")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { savedPath -> add("Saved to: $savedPath") }
            if (isEmpty()) {
                itemObject.firstString("result")
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let(::add)
            }
        }
        return lines.joinToString(separator = "\n").takeIf(String::isNotBlank)
    }

    private fun decodeImageGenerationAttachments(itemObject: JsonObject): List<RemodexConversationAttachment> {
        val savedPath = itemObject.firstString("savedPath", "saved_path")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return emptyList()
        return listOfNotNull(
            conversationAttachmentForPath(
                rawPath = savedPath,
                itemId = itemObject.firstString("id"),
                fallbackName = "Generated image",
            ),
        )
    }

    private fun conversationAttachmentForPath(
        rawPath: String,
        itemId: String?,
        fallbackName: String,
    ): RemodexConversationAttachment? {
        val normalizedPath = rawPath.trim().takeIf(String::isNotEmpty) ?: return null
        val displayName = File(normalizedPath).name.takeIf(String::isNotBlank) ?: fallbackName
        return RemodexConversationAttachment(
            id = itemId ?: "attachment-$displayName",
            uriString = normalizeConversationAttachmentUri(normalizedPath),
            displayName = displayName,
            previewDataUrl = normalizedPath.takeIf(::isInlineImageDataUrl),
        )
    }

    private fun normalizeConversationAttachmentUri(rawPath: String): String {
        val trimmed = rawPath.trim()
        return when {
            trimmed.isEmpty() -> trimmed
            isInlineImageDataUrl(trimmed) -> trimmed
            "://" in trimmed -> trimmed
            trimmed.startsWith("file:") -> trimmed
            trimmed.startsWith("/") -> File(trimmed).toURI().toString()
            else -> trimmed
        }
    }

    private fun decodeUnknownStructuredItemBody(
        itemType: String,
        itemObject: JsonObject,
        isCompleted: Boolean,
    ): String {
        val decodedText = decodeItemText(itemObject)
            .trim()
            .takeIf(String::isNotEmpty)
            ?: extractStructuredOutputText(itemObject)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        if (decodedText != null) {
            return decodedText
        }
        val label = itemType
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("(?<=.)([A-Z])"), " $1")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
        return if (isCompleted) {
            label.ifEmpty { "System activity completed" }
        } else {
            label.ifEmpty { "System activity" }
        }
    }

    private fun decodeIncomingReasoningBody(itemObject: JsonObject): String {
        val summary = decodeStringParts(itemObject.firstValue("summary")).joinToString(separator = "\n")
        val content = decodeStringParts(itemObject.firstValue("content")).joinToString(separator = "\n\n")
        val inline = decodeItemText(itemObject)
        return listOf(summary, content, inline)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(separator = "\n\n")
            .ifBlank { "Thinking..." }
    }

    private fun decodeFileChangeLifecycleBody(
        itemObject: JsonObject,
        itemType: String,
        isCompleted: Boolean,
    ): String {
        val decoded = when (itemType) {
            "toolcall" -> decodeToolCallFileChangeBody(
                itemObject = itemObject,
                isCompleted = isCompleted,
            )

            "diff" -> decodeDiffItemBody(
                itemObject = itemObject,
                isCompleted = isCompleted,
            )

            else -> decodeFileChangeItemBody(
                itemObject = itemObject,
                isCompleted = isCompleted,
            )
        }
        if (!decoded.isNullOrBlank()) {
            return decoded
        }
        return decodeStructuredLifecycleBody(
            itemObject = itemObject,
            inProgressFallback = "Updating files...",
            completedFallback = "Updated files.",
            isCompleted = isCompleted,
        )
    }

    private fun decodeFileChangeItemBody(
        itemObject: JsonObject,
        isCompleted: Boolean,
    ): String? {
        val status = normalizedFileChangeStatus(
            itemObject = itemObject,
            isCompleted = isCompleted,
        )
        val changes = decodeFileChangeEntries(
            rawChanges = extractFileChangeChanges(itemObject),
        )
        if (changes.isNotEmpty()) {
            return renderFileChangeEntries(
                status = status,
                changes = changes,
            )
        }
        val diff = extractUnifiedDiffText(itemObject)
        if (!diff.isNullOrBlank() && RemodexUnifiedPatchParser.looksLikePatchText(diff)) {
            return renderUnifiedDiffBody(
                diff = diff,
                status = status,
            )
        }
        val output = extractStructuredOutputText(itemObject)
        if (!output.isNullOrBlank()) {
            return "Status: $status\n\n${output.trim()}"
        }
        return null
    }

    private fun renderFileChangeEntries(
        status: String,
        changes: List<DecodedFileChangeEntry>,
    ): String {
        val renderedChanges = changes.map { entry ->
            buildString {
                append("Path: ")
                append(entry.path)
                append("\nKind: ")
                append(entry.kind)
                entry.inlineTotals?.let { totals ->
                    append("\nTotals: +")
                    append(totals.additions)
                    append(" -")
                    append(totals.deletions)
                }
                if (entry.diff.isNotBlank()) {
                    append("\n\n```diff\n")
                    append(entry.diff)
                    append("\n```")
                }
            }
        }
        return buildString {
            append("Status: ")
            append(status)
            if (renderedChanges.isNotEmpty()) {
                append("\n\n")
                append(renderedChanges.joinToString(separator = "\n\n---\n\n"))
            }
        }
    }

    private fun decodeStructuredLifecycleBody(
        itemObject: JsonObject,
        inProgressFallback: String,
        completedFallback: String,
        isCompleted: Boolean,
    ): String {
        return decodeItemText(itemObject)
            .ifBlank {
                decodeStringParts(itemObject.firstValue("output")).joinToString(separator = "\n")
            }
            .ifBlank {
                if (isCompleted) completedFallback else inProgressFallback
            }
    }

    private fun decodeToolCallActivityBody(
        itemObject: JsonObject,
        isCompleted: Boolean,
    ): String {
        val output = decodeItemText(itemObject)
            .ifBlank { decodeStringParts(itemObject.firstValue("output")).joinToString(separator = "\n") }
        val activityLines = extractToolCallActivityLines(output)
        if (activityLines.isNotEmpty()) {
            return activityLines.joinToString(separator = "\n")
        }
        return if (isCompleted) {
            "Tool activity finished."
        } else {
            "Running tool activity..."
        }
    }

    private fun decodeStringParts(value: JsonElement?): List<String> {
        return when (value) {
            null,
            JsonNull -> emptyList()

            is JsonPrimitive -> listOfNotNull(value.contentOrNull?.trim()?.takeIf(String::isNotEmpty))
            is JsonArray -> value.flatMap(::decodeStringParts)
            is JsonObject -> {
                listOfNotNull(
                    value.firstString("text", "summary", "part", "message", "description"),
                ) + listOf(
                    value.firstValue("data"),
                    value.firstValue("content"),
                    value.firstValue("output"),
                    value.firstValue("delta"),
                ).flatMap(::decodeStringParts)
            }
        }
    }

    private fun decodeFileChangeEntries(
        rawChanges: JsonElement?,
    ): List<DecodedFileChangeEntry> {
        val changeObjects = mutableListOf<JsonObject>()
        when (rawChanges) {
            is JsonArray -> rawChanges.forEach { value ->
                value.jsonObjectOrNull?.let(changeObjects::add)
            }

            is JsonObject -> rawChanges.keys.sorted().forEach { key ->
                when (val value = rawChanges[key]) {
                    is JsonObject -> {
                        if (value["path"] == null) {
                            changeObjects += buildJsonObject {
                                put("path", JsonPrimitive(key))
                                value.forEach { (nestedKey, nestedValue) ->
                                    put(nestedKey, nestedValue)
                                }
                            }
                        } else {
                            changeObjects += value
                        }
                    }

                    is JsonPrimitive -> {
                        value.contentOrNull
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?.let { diff ->
                                changeObjects += buildJsonObject {
                                    put("path", JsonPrimitive(key))
                                    put("diff", JsonPrimitive(diff))
                                }
                            }
                    }

                    else -> Unit
                }
            }

            else -> Unit
        }

        return changeObjects.map { changeObject ->
            val path = decodeChangePath(changeObject)
            val kind = decodeChangeKind(changeObject)
            var diff = decodeChangeDiff(changeObject)
            val totals = decodeChangeInlineTotals(changeObject)
            if (diff.isBlank()) {
                changeObject.firstString("content")
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { content ->
                        diff = synthesizeUnifiedDiffFromContent(
                            content = content,
                            kind = kind,
                            path = path,
                        )
                    }
            }
            DecodedFileChangeEntry(
                path = path,
                kind = kind,
                diff = diff,
                inlineTotals = totals,
            )
        }
    }

    private fun decodeChangePath(changeObject: JsonObject): String {
        return listOf(
            "path",
            "file",
            "file_path",
            "filePath",
            "relative_path",
            "relativePath",
            "new_path",
            "newPath",
            "to",
            "target",
            "name",
            "old_path",
            "oldPath",
            "from",
        ).firstNotNullOfOrNull { key ->
            changeObject.firstString(key)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        } ?: "unknown"
    }

    private fun decodeChangeKind(changeObject: JsonObject): String {
        return listOfNotNull(
            changeObject.firstString("kind"),
            changeObject.firstString("action"),
            changeObject.firstObject("kind")?.firstString("type"),
            changeObject.firstString("type"),
        ).map(String::trim)
            .firstOrNull(String::isNotEmpty)
            ?: "update"
    }

    private fun decodeChangeDiff(changeObject: JsonObject): String {
        return changeObject.firstString(
            "diff",
            "unified_diff",
            "unifiedDiff",
            "patch",
            "delta",
        )?.trim().orEmpty()
    }

    private fun decodeChangeInlineTotals(
        changeObject: JsonObject,
    ): DecodedFileChangeInlineTotals? {
        val additions = decodeNumericField(
            objectValue = changeObject,
            keys = listOf(
                "additions",
                "lines_added",
                "line_additions",
                "lineAdditions",
                "added",
                "insertions",
                "inserted",
                "num_added",
            ),
        ) ?: 0
        val deletions = decodeNumericField(
            objectValue = changeObject,
            keys = listOf(
                "deletions",
                "lines_deleted",
                "line_deletions",
                "lineDeletions",
                "removed",
                "deleted",
                "num_deleted",
                "num_removed",
            ),
        ) ?: 0
        if (additions <= 0 && deletions <= 0) {
            return null
        }
        return DecodedFileChangeInlineTotals(
            additions = additions,
            deletions = deletions,
        )
    }

    private fun decodeNumericField(
        objectValue: JsonObject,
        keys: List<String>,
    ): Int? {
        keys.forEach { key ->
            objectValue.firstInt(key)?.let { return it }
            objectValue.firstDouble(key)?.toInt()?.let { return it }
            objectValue.firstString(key)?.trim()?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun synthesizeUnifiedDiffFromContent(
        content: String,
        kind: String,
        path: String,
    ): String {
        val normalizedKind = kind.lowercase(Locale.ROOT)
        val contentLines = content.split('\n')
        if (normalizedKind.contains("add") || normalizedKind.contains("create")) {
            return buildList {
                add("diff --git a/$path b/$path")
                add("new file mode 100644")
                add("--- /dev/null")
                add("+++ b/$path")
                addAll(contentLines.map { line -> "+$line" })
            }.joinToString(separator = "\n")
        }
        if (normalizedKind.contains("delete") || normalizedKind.contains("remove")) {
            return buildList {
                add("diff --git a/$path b/$path")
                add("deleted file mode 100644")
                add("--- a/$path")
                add("+++ /dev/null")
                addAll(contentLines.map { line -> "-$line" })
            }.joinToString(separator = "\n")
        }
        return ""
    }

    private fun renderUnifiedDiffBody(
        diff: String,
        status: String,
    ): String {
        val perFileDiffs = splitUnifiedDiffByFile(diff)
        if (perFileDiffs.isEmpty()) {
            return "Status: $status\n\n```diff\n${diff.trim()}\n```"
        }
        val renderedChanges = perFileDiffs.map { change ->
            "Path: ${normalizeDiffPath(change.first)}\nKind: update\n\n```diff\n${change.second}\n```"
        }
        return "Status: $status\n\n${renderedChanges.joinToString(separator = "\n\n---\n\n")}"
    }

    private fun splitUnifiedDiffByFile(diff: String): List<Pair<String, String>> {
        val lines = diff.split('\n')
        if (lines.isEmpty()) {
            return emptyList()
        }
        val chunks = mutableListOf<Pair<String, String>>()
        var currentLines = mutableListOf<String>()
        var currentPath: String? = null

        fun flushChunk() {
            if (currentLines.isEmpty()) {
                return
            }
            val fallbackPath = currentPath ?: parsePathFromDiffLines(currentLines) ?: "unknown"
            val chunkText = currentLines.joinToString(separator = "\n").trim()
            if (chunkText.isNotEmpty()) {
                chunks += fallbackPath to chunkText
            }
            currentLines = mutableListOf()
        }

        lines.forEach { line ->
            if (line.startsWith("diff --git ") && currentLines.isNotEmpty()) {
                flushChunk()
                currentPath = null
            }
            if (currentPath == null) {
                parsePathFromDiffLine(line)?.let { currentPath = it }
            }
            currentLines += line
        }
        flushChunk()
        return chunks
    }

    private fun parsePathFromDiffLines(lines: List<String>): String? {
        return lines.firstNotNullOfOrNull(::parsePathFromDiffLine)
    }

    private fun parsePathFromDiffLine(line: String): String? {
        if (line.startsWith("+++ ")) {
            return normalizeDiffPath(line.removePrefix("+++ ").trim())
                .takeIf { path -> path != "unknown" }
        }
        if (line.startsWith("diff --git ")) {
            val components = line.split(' ', limit = 5)
            if (components.size >= 4) {
                return normalizeDiffPath(components[3]).takeIf { path -> path != "unknown" }
            }
        }
        return null
    }

    private fun normalizeDiffPath(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty() || trimmed == "/dev/null") {
            return "unknown"
        }
        return when {
            trimmed.startsWith("a/") || trimmed.startsWith("b/") -> trimmed.drop(2)
            else -> trimmed
        }
    }

    private fun normalizedFileChangeStatus(
        itemObject: JsonObject,
        isCompleted: Boolean,
    ): String {
        return firstNestedString(
            element = itemObject,
            keys = setOf("status"),
        ) ?: if (isCompleted) {
            "completed"
        } else {
            "inProgress"
        }
    }

    private fun extractFileChangeChanges(itemObject: JsonObject): JsonElement? {
        return firstNestedValue(
            element = itemObject,
            keys = setOf(
                "changes",
                "file_changes",
                "fileChanges",
                "files",
                "edits",
                "modified_files",
                "modifiedFiles",
                "patches",
            ),
        )
    }

    private fun extractUnifiedDiffText(itemObject: JsonObject): String? {
        return firstNestedString(
            element = itemObject,
            keys = setOf("diff", "unified_diff", "unifiedDiff", "patch"),
        )
    }

    private fun extractToolCallChanges(itemObject: JsonObject): JsonElement? = extractFileChangeChanges(itemObject)

    private fun extractToolCallUnifiedDiff(itemObject: JsonObject): String? = extractUnifiedDiffText(itemObject)

    private fun extractStructuredOutputText(itemObject: JsonObject): String? {
        val segments = linkedSetOf<String>()
        decodeItemText(itemObject)
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let(segments::add)
        listOf(
            firstNestedValue(itemObject, setOf("output")),
            firstNestedValue(itemObject, setOf("result")),
            firstNestedValue(itemObject, setOf("payload")),
            firstNestedValue(itemObject, setOf("data")),
        ).forEach { value ->
            decodeStringParts(value).forEach { part ->
                part.trim().takeIf(String::isNotEmpty)?.let(segments::add)
            }
        }
        listOf("stdout", "stderr", "output_text", "outputText").forEach { key ->
            firstNestedString(itemObject, setOf(key))
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(segments::add)
        }
        return segments.joinToString(separator = "\n\n").takeIf(String::isNotBlank)
    }

    private fun decodeToolCallFileChangeBody(
        itemObject: JsonObject,
        isCompleted: Boolean,
    ): String? {
        if (!isLikelyFileChangeToolCall(itemObject, extractStructuredOutputText(itemObject).orEmpty())) {
            return null
        }
        return decodeFileChangeItemBody(
            itemObject = itemObject,
            isCompleted = isCompleted,
        )
    }

    private fun decodeDiffItemBody(
        itemObject: JsonObject,
        isCompleted: Boolean,
    ): String? {
        val status = normalizedFileChangeStatus(
            itemObject = itemObject,
            isCompleted = isCompleted,
        )
        val diff = extractToolCallUnifiedDiff(itemObject)
        if (!diff.isNullOrBlank() && RemodexUnifiedPatchParser.looksLikePatchText(diff)) {
            return renderUnifiedDiffBody(
                diff = diff,
                status = status,
            )
        }
        val output = extractStructuredOutputText(itemObject)
        if (!output.isNullOrBlank()) {
            return "Status: $status\n\n${output.trim()}"
        }
        return null
    }

    private fun firstNestedString(
        element: JsonElement?,
        keys: Set<String>,
        depth: Int = 0,
    ): String? {
        return firstNestedValue(
            element = element,
            keys = keys,
            depth = depth,
        )?.stringOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun firstNestedValue(
        element: JsonElement?,
        keys: Set<String>,
        depth: Int = 0,
    ): JsonElement? {
        if (element == null || depth > MaxPatchSearchDepth) {
            return null
        }
        return when (element) {
            is JsonObject -> {
                keys.firstNotNullOfOrNull { key ->
                    element[key]
                } ?: element.values.firstNotNullOfOrNull { value ->
                    firstNestedValue(
                        element = value,
                        keys = keys,
                        depth = depth + 1,
                    )
                }
            }

            is JsonArray -> element.firstNotNullOfOrNull { value ->
                firstNestedValue(
                    element = value,
                    keys = keys,
                    depth = depth + 1,
                )
            }

            else -> null
        }
    }

    private fun extractToolCallActivityLines(delta: String): List<String> {
        val acceptedPrefixes = listOf(
            "running ",
            "read ",
            "search ",
            "searched ",
            "exploring ",
            "list ",
            "listing ",
            "open ",
            "opened ",
            "find ",
            "finding ",
            "edit ",
            "edited ",
            "write ",
            "wrote ",
            "apply ",
            "applied ",
        )
        val seen = linkedSetOf<String>()
        return delta.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter { line -> line.length <= 140 }
            .filterNot { line -> line.contains("```") || line.startsWith("{") || line.startsWith("[") }
            .filterNot(RemodexUnifiedPatchParser::looksLikePatchText)
            .filter { line ->
                val normalized = line.lowercase(Locale.ROOT)
                acceptedPrefixes.any { prefix -> normalized.startsWith(prefix) } && seen.add(normalized)
            }
            .toList()
    }

    private fun isLikelyFileChangeToolCall(
        itemObject: JsonObject?,
        fallbackText: String,
    ): Boolean {
        if (itemObject == null) {
            return RemodexUnifiedPatchParser.looksLikePatchText(fallbackText)
        }
        if (extractFileChangeChanges(itemObject) != null || extractUnifiedDiffText(itemObject) != null) {
            return true
        }
        val toolLabel = normalizeStatus(
            itemObject.firstString("tool", "name", "call", "callName", "call_name").orEmpty(),
        )
        if (toolLabel.contains("patch")
            || toolLabel.contains("edit")
            || toolLabel.contains("write")
            || toolLabel.contains("file")
        ) {
            return true
        }
        return RemodexUnifiedPatchParser.looksLikePatchText(fallbackText)
    }

    private fun decodeCommandExecutionStatusText(
        payloadObject: JsonObject,
        isCompleted: Boolean,
    ): String {
        return commandExecutionStatusText(
            decodeCommandExecutionRunState(
                payloadObject = payloadObject,
                paramsObject = null,
                isCompleted = isCompleted,
            ),
        )
    }

    private fun extractCommandExecutionCommand(payloadObject: JsonObject): String? {
        extractLegacyCommandArray(payloadObject.firstValue("command"))?.let { return it }
        payloadObject.firstString("command", "cmd", "raw_command", "rawCommand", "input", "invocation")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return it }
        payloadObject.firstArray("argv", "args")?.mapNotNull(JsonElement::stringOrNull)
            ?.joinToString(separator = " ")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return it }
        val eventObject = envelopeEventObject(payloadObject)
        extractLegacyCommandArray(eventObject?.firstValue("command"))?.let { return it }
        eventObject?.firstString("command", "cmd", "raw_command", "rawCommand", "input", "invocation")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return it }
        return eventObject?.firstArray("argv", "args")?.mapNotNull(JsonElement::stringOrNull)
            ?.joinToString(separator = " ")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun decodeCommandExecutionRunState(
        payloadObject: JsonObject,
        paramsObject: JsonObject?,
        isCompleted: Boolean,
    ): CommandExecutionRunState {
        val rawStatus = normalizeStatus(
            payloadObject.firstString("status")
                ?: payloadObject.firstObject("status")?.firstString("type", "statusType", "status_type")
                ?: payloadObject.firstObject("result")?.firstString("status")
                ?: payloadObject.firstObject("output")?.firstString("status")
                ?: paramsObject?.firstString("status")
                ?: paramsObject?.firstObject("event")?.firstString("status")
                ?: "",
        )
        val phase = commandExecutionRunPhase(
            rawStatus = rawStatus,
            isCompleted = isCompleted,
        )
        val fullCommand = extractCommandExecutionCommand(payloadObject).orEmpty().ifBlank { "command" }
        val cwd = payloadObject.firstString("cwd", "working_directory", "workingDirectory")
            ?: paramsObject?.firstString("cwd", "working_directory", "workingDirectory")
        val itemId = payloadObject.firstString("id", "call_id", "callId")
            ?: paramsObject?.firstString("itemId", "item_id")
        val exitCode = payloadObject.firstInt("exitCode", "exit_code")
            ?: payloadObject.firstObject("result")?.firstInt("exitCode", "exit_code")
        val durationMs = payloadObject.firstInt("durationMs", "duration_ms")
            ?: payloadObject.firstObject("result")?.firstInt("durationMs", "duration_ms")
        return CommandExecutionRunState(
            itemId = itemId,
            phase = phase,
            shortCommand = shortCommandPreview(fullCommand),
            fullCommand = fullCommand,
            cwd = cwd,
            exitCode = exitCode,
            durationMs = durationMs,
        )
    }

    private fun commandExecutionStatusText(state: CommandExecutionRunState): String {
        return "${state.phase.label} ${state.shortCommand}"
    }

    private fun normalizedCommandExecutionPreviewKey(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val tokens = trimmed.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (tokens.isEmpty()) {
            return null
        }
        val statusPrefixes = setOf("running", "completed", "failed", "stopped")
        val commandTokens = if (tokens.first().lowercase(Locale.ROOT) in statusPrefixes) {
            tokens.drop(1)
        } else {
            tokens
        }
        return commandTokens.joinToString(separator = " ").trim().ifEmpty { null }?.lowercase(Locale.ROOT)
    }

    private fun commandExecutionRunPhase(
        rawStatus: String,
        isCompleted: Boolean,
    ): CommandExecutionRunPhase {
        return when {
            rawStatus.contains("fail") || rawStatus.contains("error") -> CommandExecutionRunPhase.FAILED
            rawStatus.contains("cancel")
                || rawStatus.contains("abort")
                || rawStatus.contains("interrupt")
                || rawStatus.contains("stop") -> CommandExecutionRunPhase.STOPPED

            isCompleted || rawStatus.contains("done") || rawStatus.contains("complete") || rawStatus.contains("finish") -> {
                CommandExecutionRunPhase.COMPLETED
            }

            else -> CommandExecutionRunPhase.RUNNING
        }
    }

    private fun shortCommandPreview(
        rawCommand: String,
        maxLength: Int = 92,
    ): String {
        val compact = rawCommand
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { "command" }
        val unwrapped = unwrapShellCommandPreview(compact)
        return if (unwrapped.length <= maxLength) {
            unwrapped
        } else {
            unwrapped.take(maxLength - 3) + "..."
        }
    }

    private fun unwrapShellCommandPreview(rawCommand: String): String {
        var result = rawCommand.trim()
        val normalized = result.lowercase(Locale.ROOT)
        val shellPrefixes = listOf(
            "/usr/bin/bash -lc ",
            "/usr/bin/bash -c ",
            "/bin/bash -lc ",
            "/bin/bash -c ",
            "bash -lc ",
            "bash -c ",
            "/bin/sh -c ",
            "sh -c ",
        )
        shellPrefixes.firstOrNull { prefix -> normalized.startsWith(prefix) }?.let { prefix ->
            result = result.drop(prefix.length).trim()
            if ((result.startsWith('"') && result.endsWith('"')) || (result.startsWith('\'') && result.endsWith('\''))) {
                result = result.drop(1).dropLast(1)
            }
            result.substringAfter("&&", missingDelimiterValue = result).trim().let { stripped ->
                result = stripped
            }
        }
        return result.substringBefore(" | ").trim()
    }

    private fun extractLegacyCommandArray(value: JsonElement?): String? {
        val array = value?.jsonArrayOrNull ?: return null
        return array.mapNotNull(JsonElement::stringOrNull)
            .joinToString(separator = " ")
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private fun upsertCommandExecutionDetails(
        context: NotificationContext,
        payloadObject: JsonObject,
        isCompleted: Boolean,
        paramsObject: JsonObject? = null,
    ) {
        val state = decodeCommandExecutionRunState(
            payloadObject = payloadObject,
            paramsObject = paramsObject,
            isCompleted = isCompleted,
        )
        val itemId = context.itemId?.takeIf(String::isNotBlank) ?: state.itemId?.takeIf(String::isNotBlank) ?: return
        backingCommandExecutionDetails.value = backingCommandExecutionDetails.value.toMutableMap().apply {
            val existing = this[itemId]
            this[itemId] = if (existing == null) {
                RemodexCommandExecutionDetails(
                    fullCommand = state.fullCommand,
                    cwd = state.cwd,
                    exitCode = state.exitCode,
                    durationMs = state.durationMs,
                    outputTail = "",
                )
            } else {
                existing.copy(
                    fullCommand = if (state.fullCommand.length > existing.fullCommand.length) {
                        state.fullCommand
                    } else {
                        existing.fullCommand
                    },
                    cwd = existing.cwd ?: state.cwd,
                    exitCode = state.exitCode ?: existing.exitCode,
                    durationMs = state.durationMs ?: existing.durationMs,
                )
            }
        }
    }

    private fun appendCommandExecutionOutputToDetails(
        context: NotificationContext,
        paramsObject: JsonObject,
    ) {
        val itemId = context.itemId?.takeIf(String::isNotBlank) ?: return
        val chunk = commandExecutionOutputChunk(
            paramsObject = paramsObject,
            eventObject = envelopeEventObject(paramsObject),
        ) ?: return
        val nextDetails = backingCommandExecutionDetails.value[itemId]?.appendedOutput(chunk) ?: return
        backingCommandExecutionDetails.value = backingCommandExecutionDetails.value.toMutableMap().apply {
            this[itemId] = nextDetails
        }
    }

    private fun commandExecutionOutputChunk(
        paramsObject: JsonObject,
        eventObject: JsonObject?,
    ): String? {
        return paramsObject.firstString("delta", "textDelta", "text_delta", "text", "output")
            ?: eventObject?.firstString("delta", "text", "output")
    }

    private fun indexTurnIds(
        threadId: String,
        threadObject: JsonObject,
    ) {
        threadObject.firstArray("turns").orEmpty().forEach { turnValue ->
            val turnObject = turnValue.jsonObjectOrNull ?: return@forEach
            turnObject.firstString("id", "turnId", "turn_id")?.let { turnId ->
                threadIdByTurnId[turnId] = threadId
            }
        }
    }

    private fun envelopeEventObject(paramsObject: JsonObject?): JsonObject? {
        return paramsObject?.firstObject("msg", "event")
    }

    private fun extractIncomingItemObject(
        paramsObject: JsonObject,
        eventObject: JsonObject?,
    ): JsonObject? {
        return paramsObject.firstObject("item")
            ?: eventObject?.firstObject("item")
            ?: paramsObject.firstObject("event")?.firstObject("item")
            ?: paramsObject.takeIf(::isLikelyIncomingItemPayload)
            ?: eventObject?.takeIf(::isLikelyIncomingItemPayload)
            ?: paramsObject.firstObject("event")?.takeIf(::isLikelyIncomingItemPayload)
    }

    private fun extractAssistantTurnId(
        paramsObject: JsonObject,
        eventObject: JsonObject?,
    ): String? = extractAssistantTurnIdValue(
        paramsObject = paramsObject,
        eventObject = eventObject,
        extractedTurnId = extractTurnId(paramsObject),
    )

    private fun isLikelyIncomingItemPayload(objectValue: JsonObject): Boolean {
        val normalizedType = normalizeItemType(objectValue.firstString("type").orEmpty())
        if (normalizedType.isEmpty()) {
            return false
        }
        return objectValue.firstValue("content", "status", "output") != null
            || objectValue.firstValue("changes", "files", "diff", "patch") != null
            || objectValue.firstValue("result", "payload", "data") != null
            || objectValue.firstValue("path", "savedPath", "saved_path", "action", "query") != null
    }

    private fun extractItemId(
        paramsObject: JsonObject?,
        eventObject: JsonObject?,
        itemObject: JsonObject? = null,
    ): String? {
        return itemObject?.firstString("id", "call_id", "callId", "messageId", "message_id")
            ?: paramsObject?.firstString("itemId", "item_id", "call_id", "callId", "messageId", "message_id")
            ?: paramsObject?.firstObject("item")?.firstString("id")
            ?: eventObject?.firstString("itemId", "item_id", "call_id", "callId", "messageId", "message_id")
            ?: eventObject?.firstObject("item")?.firstString("id")
            ?: paramsObject?.firstString("id")
    }

    private fun extractTextDelta(paramsObject: JsonObject): String {
        val eventObject = envelopeEventObject(paramsObject)
        return paramsObject.firstRawString("delta", "textDelta", "text_delta", "text", "summary", "part")
            ?: eventObject?.firstRawString("delta", "text", "summary", "part")
            ?: paramsObject.firstObject("event")?.firstRawString("delta", "text", "summary", "part")
            ?: ""
    }

    private fun extractTurnIdForTurnLifecycleEvent(paramsObject: JsonObject): String? {
        return extractTurnId(paramsObject)
            ?: envelopeEventObject(paramsObject)?.firstString("id")
            ?: paramsObject.firstObject("event")?.firstString("id")
            ?: paramsObject.firstString("id")
    }

    private fun resolveThreadId(
        paramsObject: JsonObject?,
        turnIdHint: String? = null,
    ): String? {
        if (paramsObject == null) {
            return null
        }
        extractThreadId(paramsObject)?.let { threadId ->
            (turnIdHint ?: extractTurnId(paramsObject))?.let { turnId ->
                threadIdByTurnId[turnId] = threadId
            }
            return threadId
        }
        val resolvedTurnId = turnIdHint ?: extractTurnId(paramsObject)
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId]?.let { return it }
        }
        if (activeTurnIdByThread.size == 1) {
            return activeTurnIdByThread.keys.firstOrNull()
        }
        if (backingThreads.value.size == 1) {
            return backingThreads.value.first().id
        }
        resolveActiveThreadHint()?.let { return it }
        return null
    }

    private fun extractThreadId(paramsObject: JsonObject): String? {
        val eventObject = envelopeEventObject(paramsObject)
        return paramsObject.firstString("threadId", "thread_id", "conversationId", "conversation_id")
            ?: paramsObject.firstObject("thread")?.firstString("id", "threadId", "thread_id")
            ?: paramsObject.firstObject("turn")?.firstString("threadId", "thread_id", "conversationId", "conversation_id")
            ?: paramsObject.firstObject("item")?.firstString("threadId", "thread_id", "conversationId", "conversation_id")
            ?: eventObject?.firstString("threadId", "thread_id", "conversationId", "conversation_id")
            ?: eventObject?.firstObject("thread")?.firstString("id", "threadId", "thread_id")
            ?: eventObject?.firstObject("turn")?.firstString("threadId", "thread_id", "conversationId", "conversation_id")
            ?: eventObject?.firstObject("item")?.firstString("threadId", "thread_id", "conversationId", "conversation_id")
            ?: paramsObject.firstObject("event")?.firstString("threadId", "thread_id", "conversationId", "conversation_id")
            ?: paramsObject.firstObject("event")?.firstObject("thread")?.firstString("id", "threadId", "thread_id")
            ?: paramsObject.firstObject("event")?.firstObject("turn")?.firstString("threadId", "thread_id", "conversationId", "conversation_id")
    }

    private fun resolveActiveThreadHint(): String? {
        val hint = activeThreadIdHint ?: return null
        if (activeTurnIdByThread.containsKey(hint)) {
            return hint
        }
        if (runningThreadFallbackIds.contains(hint)) {
            return hint
        }
        if (resumedThreadIds.contains(hint)) {
            return hint
        }
        if (backingThreads.value.isEmpty()) {
            return hint
        }
        val snapshot = backingThreads.value.firstOrNull { thread -> thread.id == hint } ?: return null
        if (snapshot.isRunning || snapshot.isWaitingOnApproval) {
            return hint
        }
        return hint.takeIf { hasStreamingMessage(hint) }
    }

    private fun resolveNotificationContext(paramsObject: JsonObject): NotificationContext? {
        val eventObject = envelopeEventObject(paramsObject)
        val itemObject = extractIncomingItemObject(paramsObject, eventObject)
        val turnId = extractTurnId(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return null
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        return NotificationContext(
            threadId = threadId,
            turnId = resolvedTurnId,
            itemId = extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject),
            payloadObject = itemObject ?: eventObject ?: paramsObject,
        )
    }

    private fun streamingMessageId(
        itemId: String?,
        turnId: String?,
        fallbackPrefix: String,
    ): String {
        return itemId?.takeIf(String::isNotBlank)
            ?: turnId?.takeIf(String::isNotBlank)?.let { resolvedTurnId -> "$fallbackPrefix-$resolvedTurnId" }
            ?: "$fallbackPrefix-${UUID.randomUUID()}"
    }

    private fun normalizeMethod(value: String): String {
        return value
            .trim()
            .replace("_", "")
            .replace("-", "")
            .lowercase(Locale.ROOT)
    }

    private data class NotificationContext(
        val threadId: String,
        val turnId: String?,
        val itemId: String?,
        val payloadObject: JsonObject,
    )

    private data class StructuredTimelinePresentation(
        val kind: ConversationItemKind,
        val body: String,
        val supportingText: String? = null,
        val attachments: List<RemodexConversationAttachment> = emptyList(),
        val planState: RemodexPlanState? = null,
        val subagentAction: RemodexSubagentAction? = null,
        val structuredUserInputRequest: RemodexStructuredUserInputRequest? = null,
        val systemTurnOrderingHint: ConversationSystemTurnOrderingHint =
            ConversationSystemTurnOrderingHint.AUTO,
    )

    private data class TurnReadState(
        val interruptibleTurnId: String?,
        val hasInterruptibleTurnWithoutId: Boolean,
    )

    private data class ThreadActivityState(
        val isWaitingOnApproval: Boolean,
        val isWaitingOnUserInput: Boolean,
    )

    private data class CommandExecutionRunState(
        val itemId: String?,
        val phase: CommandExecutionRunPhase,
        val shortCommand: String,
        val fullCommand: String,
        val cwd: String?,
        val exitCode: Int?,
        val durationMs: Int?,
    )

    private enum class CommandExecutionRunPhase(
        val label: String,
    ) {
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed"),
        STOPPED("stopped"),
    }

    private fun parseThreadSnapshot(
        threadObject: JsonObject,
        syncState: RemodexThreadSyncState,
        existing: ThreadSyncSnapshot?,
    ): ThreadSyncSnapshot? {
        val id = threadObject.firstString("id") ?: return null
        val title = threadObject.firstString("title")
            ?: existing?.title
            ?: "Conversation"
        val name = threadObject.firstString("name")
            ?: existing?.name
        val preview = threadObject.firstString("preview")
            ?: existing?.preview
            ?: ""
        val projectPath = threadObject.firstString(
            "cwd",
            "current_working_directory",
            "working_directory",
        ) ?: existing?.projectPath.orEmpty()
        val updatedEpochMs = decodeTimestampMillis(
            threadObject.firstDouble("updatedAt", "updated_at")
                ?: threadObject.firstDouble("createdAt", "created_at"),
        ) ?: existing?.lastUpdatedEpochMs
            ?: nowEpochMs()
        val modelId = threadObject.firstString("model")
            ?: threadObject.firstObject("metadata")?.firstString("model", "model_name")
            ?: existing?.runtimeConfig?.selectedModelId
        val runtimeConfig = mergeRuntimeConfig(
            existing = existing?.runtimeConfig,
            incoming = RemodexRuntimeConfig(
                selectedModelId = modelId,
                availableModels = availableModels.value,
            ),
        )
        val activityState = decodeThreadActivityState(
            threadObject.firstObject("status"),
        )
        if (activityState != null && !threadHasKnownRunningState(id)) {
            markThreadAsRunningFallback(id)
        }
        return ThreadSyncSnapshot(
            id = id,
            title = title,
            name = name,
            preview = preview,
            projectPath = projectPath,
            lastUpdatedLabel = relativeUpdatedLabel(updatedEpochMs),
            lastUpdatedEpochMs = updatedEpochMs,
            isRunning = threadHasKnownRunningState(id),
            isWaitingOnApproval = activityState?.isWaitingOnApproval ?: existing?.isWaitingOnApproval ?: false,
            syncState = syncState,
            parentThreadId = threadObject.firstString("parentThreadId", "parent_thread_id") ?: existing?.parentThreadId,
            agentNickname = threadObject.firstString("agentNickname", "agent_nickname") ?: existing?.agentNickname,
            agentRole = threadObject.firstString("agentRole", "agent_role") ?: existing?.agentRole,
            activeTurnId = activeTurnIdByThread[id],
            latestTurnTerminalState = latestTurnTerminalStateByThread[id],
            stoppedTurnIds = stoppedTurnIdsByThread[id].orEmpty(),
            runtimeConfig = runtimeConfig,
            timelineMutations = existing?.timelineMutations.orEmpty(),
        )
    }

    private fun decodeHistoryItems(
        threadId: String,
        threadObject: JsonObject,
    ): List<TimelineMutation> {
        val turns = threadObject.firstArray("turns").orEmpty()
        val repoRoot = threadObject.firstString(
            "cwd",
            "current_working_directory",
            "working_directory",
        )
        val decodedItems = mutableListOf<DecodedHistoryItem>()
        val baseTimestampMs = decodeHistoryBaseTimestampMillis(threadObject) ?: 0L
        var syntheticOffsetMs = 0L

        turns.forEach { turnValue ->
            val turnObject = turnValue.jsonObjectOrNull ?: return@forEach
            val turnId = turnObject.firstString("id", "turnId", "turn_id")
            val turnTimestampMs = decodeHistoryTimestampMillis(turnObject)
            val items = turnObject.firstArray("items").orEmpty()
            val assistantMessageId = items.mapNotNull { itemValue ->
                val itemObject = itemValue.jsonObjectOrNull ?: return@mapNotNull null
                val itemType = normalizeItemType(itemObject.firstString("type").orEmpty())
                if (!isAssistantHistoryItem(itemType, itemObject)) {
                    return@mapNotNull null
                }
                itemObject.firstString("id")
            }.lastOrNull()
            val assistantChangeSet = assistantMessageId?.let { messageId ->
                buildAssistantChangeSet(
                    threadId = threadId,
                    repoRoot = repoRoot,
                    turnObject = turnObject,
                    items = items,
                    assistantMessageId = messageId,
                )
            }
            items.forEach { itemValue ->
                val itemObject = itemValue.jsonObjectOrNull ?: return@forEach
                val itemType = normalizeItemType(itemObject.firstString("type").orEmpty())
                val itemId = itemObject.firstString("id")
                val syntheticTimestampMs = (turnTimestampMs ?: baseTimestampMs) + syntheticOffsetMs
                val resolvedTimestampMs = decodeHistoryTimestampMillis(itemObject) ?: syntheticTimestampMs
                val sequence = syntheticOffsetMs
                syntheticOffsetMs += 1L
                val text = decodeItemText(itemObject)
                val speaker = when (itemType) {
                    "usermessage" -> ConversationSpeaker.USER
                    "agentmessage", "assistantmessage", "exitedreviewmode" -> ConversationSpeaker.ASSISTANT
                    "message" -> {
                        if (itemObject.firstString("role")?.contains("user", ignoreCase = true) == true) {
                            ConversationSpeaker.USER
                        } else {
                            ConversationSpeaker.ASSISTANT
                        }
                    }

                    else -> ConversationSpeaker.SYSTEM
                }
                val presentation = if (speaker == ConversationSpeaker.SYSTEM) {
                    decodeStructuredTimelinePresentation(
                        itemObject = itemObject,
                        itemType = itemType,
                        isCompleted = true,
                    )
                } else {
                    StructuredTimelinePresentation(
                        kind = ConversationItemKind.CHAT,
                        body = "",
                    )
                }
                val kind = if (speaker == ConversationSpeaker.SYSTEM) {
                    presentation.kind
                } else {
                    ConversationItemKind.CHAT
                }
                val attachments = if (speaker == ConversationSpeaker.USER) {
                    decodeImageAttachments(itemObject)
                } else if (speaker == ConversationSpeaker.SYSTEM) {
                    presentation.attachments
                } else {
                    emptyList()
                }
                val planState = if (kind == ConversationItemKind.PLAN) {
                    presentation.planState
                } else {
                    null
                }
                val structuredUserInputRequest = if (kind == ConversationItemKind.USER_INPUT_PROMPT) {
                    presentation.structuredUserInputRequest
                } else {
                    null
                }
                val subagentAction = if (kind == ConversationItemKind.SUBAGENT_ACTION) {
                    presentation.subagentAction
                } else {
                    null
                }
                val resolvedText = when {
                    speaker == ConversationSpeaker.SYSTEM -> presentation.body
                    itemType == "exitedreviewmode" -> decodeCompletedAssistantBody(
                        itemType = itemType,
                        itemObject = itemObject,
                    )
                    subagentAction != null -> subagentAction.summaryText
                    speaker == ConversationSpeaker.USER && attachments.isNotEmpty() -> {
                        androidUserMessageText(
                            prompt = text,
                            attachmentCount = attachments.size,
                        )
                    }
                    else -> text
                }
                if (kind == ConversationItemKind.COMMAND_EXECUTION) {
                    upsertCommandExecutionDetails(
                        context = NotificationContext(
                            threadId = threadId,
                            turnId = turnId,
                            itemId = itemId,
                            payloadObject = itemObject,
                        ),
                        payloadObject = itemObject,
                        isCompleted = true,
                    )
                    decodeStringParts(itemObject.firstValue("output")).joinToString(separator = "\n")
                        .takeIf(String::isNotBlank)
                        ?.let { outputChunk ->
                            if (itemId != null) {
                                val details = backingCommandExecutionDetails.value[itemId]
                                if (details != null) {
                                    backingCommandExecutionDetails.value =
                                        backingCommandExecutionDetails.value.toMutableMap().apply {
                                            this[itemId] = details.appendedOutput(outputChunk)
                                        }
                                }
                            }
                        }
                }
                if (
                    resolvedText.isNotBlank()
                    || !presentation.supportingText.isNullOrBlank()
                    || itemType == "plan"
                    || attachments.isNotEmpty()
                    || structuredUserInputRequest != null
                    || subagentAction != null
                ) {
                    decodedItems += DecodedHistoryItem(
                        timestampMs = resolvedTimestampMs,
                        sequence = sequence,
                        item = timelineItem(
                            id = itemId ?: "$threadId-history-$sequence",
                            speaker = speaker,
                            text = resolvedText.ifBlank { itemType.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } },
                            kind = kind,
                            supportingText = presentation.supportingText,
                            turnId = turnId,
                            itemId = itemId,
                            createdAtEpochMs = resolvedTimestampMs.takeIf { it > 0L },
                            attachments = attachments,
                            planState = planState,
                            subagentAction = subagentAction,
                            structuredUserInputRequest = structuredUserInputRequest,
                            orderIndex = 0L,
                            assistantChangeSet = if (
                                speaker == ConversationSpeaker.ASSISTANT &&
                                itemId != null &&
                                itemId == assistantMessageId
                            ) {
                                assistantChangeSet
                            } else {
                                null
                            },
                            systemTurnOrderingHint = presentation.systemTurnOrderingHint,
                        ),
                    )
                }
            }
        }

        return decodedItems
            .sortedWith(compareBy<DecodedHistoryItem>({ it.timestampMs }, { it.sequence }))
            .mapIndexed { index, decoded ->
                TimelineMutation.Upsert(
                    decoded.item.copy(orderIndex = index.toLong()),
                )
            }
    }

    private fun decodeHistoryBaseTimestampMillis(threadObject: JsonObject): Long? {
        return decodeHistoryTimestampMillis(threadObject, "createdAt", "created_at")
            ?: decodeHistoryTimestampMillis(threadObject, "updatedAt", "updated_at")
    }

    private fun decodeHistoryTimestampMillis(objectValue: JsonObject): Long? {
        return decodeHistoryTimestampMillis(
            objectValue,
            "createdAt",
            "created_at",
            "timestamp",
            "time",
            "updatedAt",
            "updated_at",
        )
    }

    private fun decodeHistoryTimestampMillis(
        objectValue: JsonObject,
        vararg keys: String,
    ): Long? {
        keys.forEach { key ->
            objectValue.firstDouble(key)?.let(::decodeTimestampMillis)?.let { return it }
            objectValue.firstInt(key)?.toDouble()?.let(::decodeTimestampMillis)?.let { return it }
            objectValue.firstString(key)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { raw ->
                    raw.toDoubleOrNull()?.let(::decodeTimestampMillis)?.let { return it }
                    runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }
                }
        }
        return null
    }

    private fun decodeHistoryReasoningBody(itemObject: JsonObject): String {
        val summary = decodeStringParts(itemObject.firstValue("summary")).joinToString(separator = "\n")
        val content = decodeStringParts(itemObject.firstValue("content")).joinToString(separator = "\n\n")
        return listOf(summary, content)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(separator = "\n\n")
            .ifBlank { "Thinking..." }
    }

    private fun decodeImageAttachments(itemObject: JsonObject): List<RemodexConversationAttachment> {
        val content = itemObject.firstArray("content").orEmpty()
        return content.mapIndexedNotNull { index, value ->
            val objectValue = value.jsonObjectOrNull ?: return@mapIndexedNotNull null
            when (normalizeItemType(objectValue.firstString("type").orEmpty())) {
                "image", "localimage" -> {
                    val uri = objectValue.firstString("image_url", "url", "path").orEmpty().trim()
                    if (uri.isEmpty()) {
                        null
                    } else {
                        RemodexConversationAttachment(
                            id = objectValue.firstString("id") ?: "${itemObject.firstString("id").orEmpty()}-attachment-$index",
                            uriString = uri,
                            displayName = objectValue.firstString("name", "fileName", "filename")
                                ?: fallbackConversationImageDisplayName(
                                    uriString = uri,
                                    attachmentIndex = index,
                                ),
                            previewDataUrl = uri.takeIf(::isInlineImageDataUrl),
                        )
                    }
                }

                else -> null
            }
        }
    }

    private fun decodePlanItemText(itemObject: JsonObject): String {
        val fullBody = decodeItemText(itemObject).trim()
        if (fullBody.isNotEmpty()) {
            return fullBody
        }
        val planState = decodeHistoryPlanState(itemObject)
        val explanation = planState?.explanation?.trim().orEmpty()
        if (explanation.isNotEmpty()) {
            return explanation
        }
        return planState?.steps?.firstOrNull()?.step.orEmpty()
    }

    private fun decodeFileChangeHistoryBody(
        itemObject: JsonObject,
        itemType: String,
    ): String {
        return decodeFileChangeLifecycleBody(
            itemObject = itemObject,
            itemType = itemType,
            isCompleted = true,
        )
    }

    private fun decodeHistoryPlanState(itemObject: JsonObject): RemodexPlanState? {
        val explanation = itemObject.firstString("explanation", "summary")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val steps = itemObject.firstArray("plan").orEmpty().mapIndexedNotNull { index, value ->
            val stepObject = value.jsonObjectOrNull ?: return@mapIndexedNotNull null
            val step = stepObject.firstString("step")?.trim().orEmpty()
            if (step.isEmpty()) {
                return@mapIndexedNotNull null
            }
            val status = when (stepObject.firstString("status")?.trim()?.lowercase(Locale.ROOT)) {
                "completed" -> RemodexPlanStepStatus.COMPLETED
                "in_progress", "inprogress" -> RemodexPlanStepStatus.IN_PROGRESS
                else -> RemodexPlanStepStatus.PENDING
            }
            RemodexPlanStep(
                id = stepObject.firstString("id") ?: "${itemObject.firstString("id").orEmpty()}-plan-$index",
                step = step,
                status = status,
            )
        }
        if (explanation == null && steps.isEmpty()) {
            return null
        }
        return RemodexPlanState(
            explanation = explanation,
            steps = steps,
        )
    }

    private fun decodeStructuredUserInputRequest(itemObject: JsonObject): RemodexStructuredUserInputRequest? {
        val requestId = itemObject.firstValue("requestId", "request_id", "id") ?: return null
        if (rpcIdKey(requestId) == null) {
            return null
        }
        return decodeStructuredUserInputRequest(
            requestId = requestId,
            questionValues = itemObject.firstArray("questions").orEmpty(),
        )
    }

    private fun decodeStructuredUserInputRequest(
        requestId: JsonElement,
        questionValues: List<JsonElement>,
    ): RemodexStructuredUserInputRequest? {
        val questions = questionValues.mapIndexedNotNull { index, value ->
            val questionObject = value.jsonObjectOrNull ?: return@mapIndexedNotNull null
            val id = questionObject.firstString("id")?.trim().orEmpty()
            val header = questionObject.firstRawString("header")?.trim().orEmpty()
            val question = questionObject.firstRawString("question")?.trim().orEmpty()
            if (id.isEmpty() || question.isEmpty()) {
                return@mapIndexedNotNull null
            }
            RemodexStructuredUserInputQuestion(
                id = id,
                header = header,
                question = question,
                isOther = questionObject.firstBoolean("isOther") ?: false,
                isSecret = questionObject.firstBoolean("isSecret") ?: false,
                options = questionObject.firstArray("options").orEmpty().mapIndexedNotNull { optionIndex, optionValue ->
                    val optionObject = optionValue.jsonObjectOrNull ?: return@mapIndexedNotNull null
                    val label = optionObject.firstString("label")?.trim().orEmpty()
                    val description = optionObject.firstString("description")?.trim().orEmpty()
                    if (label.isEmpty() || description.isEmpty()) {
                        return@mapIndexedNotNull null
                    }
                    RemodexStructuredUserInputOption(
                        id = optionObject.firstString("id") ?: "$id-option-$optionIndex",
                        label = label,
                        description = description,
                    )
                },
            )
        }
        if (questions.isEmpty()) {
            return null
        }
        return RemodexStructuredUserInputRequest(
            requestId = requestId,
            questions = questions,
        )
    }

    private fun isSubagentHistoryItemType(itemType: String): Boolean {
        return itemType == "collabagenttoolcall"
            || itemType == "collabtoolcall"
            || itemType.startsWith("collabagentspawn")
            || itemType.startsWith("collabwaiting")
            || itemType.startsWith("collabclose")
            || itemType.startsWith("collabresume")
            || itemType.startsWith("collabagentinteraction")
    }

    private fun decodeSubagentAction(itemObject: JsonObject): RemodexSubagentAction? {
        val receiverThreadIds = decodeSubagentReceiverThreadIds(itemObject)
        val receiverAgents = decodeSubagentReceiverAgents(itemObject, receiverThreadIds)
        val agentStates = decodeSubagentAgentStates(itemObject)
        val prompt = itemObject.firstString("prompt", "task", "message")?.trim()?.takeIf(String::isNotEmpty)
        val model = itemObject.firstString("model", "modelName", "model_name", "requestedModel", "requested_model")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (
            receiverThreadIds.isEmpty() &&
            receiverAgents.isEmpty() &&
            agentStates.isEmpty() &&
            prompt == null &&
            model == null
        ) {
            return null
        }
        return RemodexSubagentAction(
            tool = itemObject.firstString("tool", "name") ?: "spawnAgent",
            status = itemObject.firstString("status") ?: "in_progress",
            prompt = prompt,
            model = model,
            receiverThreadIds = receiverThreadIds,
            receiverAgents = receiverAgents,
            agentStates = agentStates,
        )
    }

    private fun decodeSubagentReceiverThreadIds(itemObject: JsonObject): List<String> {
        val directIds = itemObject.firstArray("receiverThreadIds", "receiver_thread_ids").orEmpty()
            .mapNotNull(JsonElement::stringOrNull)
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (directIds.isNotEmpty()) {
            return directIds.distinct()
        }
        return itemObject.firstArray("receiverThreads", "receivers").orEmpty()
            .mapNotNull { value ->
                value.jsonObjectOrNull?.firstString("threadId", "thread_id")?.trim()
            }
            .filter(String::isNotEmpty)
            .distinct()
    }

    private fun decodeSubagentReceiverAgents(
        itemObject: JsonObject,
        fallbackThreadIds: List<String>,
    ): List<RemodexSubagentRef> {
        val rawAgents = itemObject.firstArray("receiverAgents", "receiver_agents", "receivers").orEmpty()
        val decodedAgents = rawAgents.mapNotNull { value ->
            val objectValue = value.jsonObjectOrNull ?: return@mapNotNull null
            val threadId = objectValue.firstString("threadId", "thread_id")?.trim().orEmpty()
            if (threadId.isEmpty()) {
                return@mapNotNull null
            }
            RemodexSubagentRef(
                threadId = threadId,
                agentId = objectValue.firstString("agentId", "agent_id"),
                nickname = objectValue.firstString("nickname", "name"),
                role = objectValue.firstString("role"),
                model = objectValue.firstString("model", "modelName", "model_name"),
                prompt = objectValue.firstString("prompt", "task", "message"),
            )
        }
        if (decodedAgents.isNotEmpty()) {
            return decodedAgents
        }
        return fallbackThreadIds.map { threadId -> RemodexSubagentRef(threadId = threadId) }
    }

    private fun decodeSubagentAgentStates(itemObject: JsonObject): Map<String, RemodexSubagentState> {
        val rawStates = itemObject.firstObject("agentStates", "agent_states") ?: return emptyMap()
        return buildMap {
            rawStates.forEach { (threadId, value) ->
                val objectValue = value.jsonObjectOrNull ?: return@forEach
                val status = objectValue.firstString("status")?.trim().orEmpty()
                if (threadId.isBlank() || status.isEmpty()) {
                    return@forEach
                }
                put(
                    threadId,
                    RemodexSubagentState(
                        threadId = threadId,
                        status = status,
                        message = objectValue.firstString("message", "summary", "description"),
                    ),
                )
            }
        }
    }

    private fun decodeItemText(itemObject: JsonObject): String {
        return decodeIncomingMessageTextValue(itemObject)
    }

    private fun buildAssistantChangeSet(
        threadId: String,
        repoRoot: String?,
        turnObject: JsonObject,
        items: List<JsonElement>,
        assistantMessageId: String,
    ): RemodexAssistantChangeSet? {
        val turnId = turnObject.firstString("id", "turnId", "turn_id") ?: return null
        var turnDiffPatch: String? = null
        val fallbackPatches = mutableListOf<String>()
        items.forEach { itemValue ->
            val itemObject = itemValue.jsonObjectOrNull ?: return@forEach
            val itemType = normalizeItemType(itemObject.firstString("type").orEmpty())
            when (itemType) {
                "diff" -> {
                    if (turnDiffPatch == null) {
                        turnDiffPatch = extractChangeSetUnifiedPatch(itemObject, itemType)
                    }
                }

                "toolcall", "filechange" -> {
                    extractChangeSetUnifiedPatch(itemObject, itemType)?.let(fallbackPatches::add)
                }
            }
        }

        val normalizedPatch = turnDiffPatch ?: fallbackPatches.firstOrNull() ?: return null
        val source = if (turnDiffPatch != null) {
            RemodexAssistantChangeSetSource.TURN_DIFF
        } else {
            RemodexAssistantChangeSetSource.FILE_CHANGE_FALLBACK
        }
        val analysis = RemodexUnifiedPatchParser.analyze(normalizedPatch)
        val unsupportedReasons = when {
            source == RemodexAssistantChangeSetSource.FILE_CHANGE_FALLBACK && fallbackPatches.size > 1 -> {
                listOf("This response emitted multiple file-change patches, so v1 cannot safely auto-revert it.")
            }

            analysis.fileChanges.isEmpty() && analysis.unsupportedReasons.isEmpty() -> {
                listOf("This response cannot be auto-reverted because no exact patch was captured.")
            }

            else -> analysis.unsupportedReasons
        }
        val status = when {
            !isTurnTerminal(turnObject) -> RemodexAssistantChangeSetStatus.COLLECTING
            source == RemodexAssistantChangeSetSource.FILE_CHANGE_FALLBACK && fallbackPatches.size > 1 -> {
                RemodexAssistantChangeSetStatus.NOT_REVERTABLE
            }

            unsupportedReasons.isNotEmpty() || analysis.fileChanges.isEmpty() -> {
                RemodexAssistantChangeSetStatus.NOT_REVERTABLE
            }

            else -> RemodexAssistantChangeSetStatus.READY
        }

        return RemodexAssistantChangeSet(
            id = assistantMessageId.ifBlank { "changeset-$turnId" },
            repoRoot = repoRoot,
            threadId = threadId,
            turnId = turnId,
            assistantMessageId = assistantMessageId,
            status = status,
            source = source,
            forwardUnifiedPatch = normalizedPatch,
            fileChanges = analysis.fileChanges,
            unsupportedReasons = unsupportedReasons,
            fallbackPatchCount = fallbackPatches.size,
        )
    }

    private fun isAssistantHistoryItem(
        itemType: String,
        itemObject: JsonObject,
    ): Boolean {
        return isAssistantLifecycleItemValue(
            itemType = itemType,
            role = itemObject.firstString("role"),
        )
    }

    private fun decodeCompletedAssistantBody(
        itemType: String,
        itemObject: JsonObject,
    ): String {
        if (itemType == "exitedreviewmode") {
            return decodeCompletedReviewText(itemObject)
        }
        return decodeItemText(itemObject)
    }

    private fun isFinalAnswerAssistantPhase(itemObject: JsonObject): Boolean {
        val normalizedPhase = itemObject
            .firstString("phase", "messagePhase", "message_phase")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.filter(Char::isLetterOrDigit)
            .orEmpty()
        return normalizedPhase == "finalanswer" || normalizedPhase == "final"
    }

    private fun decodeEnteredReviewModeText(itemObject: JsonObject): String {
        val reviewLabel = decodeStringParts(itemObject.firstValue("review"))
            .joinToString(separator = "\n")
            .trim()
            .ifEmpty { "changes" }
        return "Reviewing $reviewLabel..."
    }

    private fun decodeCompletedReviewText(itemObject: JsonObject): String {
        return decodeStringParts(itemObject.firstValue("review"))
            .joinToString(separator = "\n")
            .trim()
    }

    private fun isTurnTerminal(turnObject: JsonObject): Boolean {
        val normalizedStatus = normalizeStatus(
            turnObject.firstString("status", "turnStatus", "turn_status").orEmpty(),
        )
        if (normalizedStatus.isEmpty()) {
            return true
        }
        return normalizedStatus.contains("complete")
            || normalizedStatus.contains("done")
            || normalizedStatus.contains("fail")
            || normalizedStatus.contains("error")
            || normalizedStatus.contains("interrupt")
            || normalizedStatus.contains("cancel")
            || normalizedStatus.contains("stopped")
            || normalizedStatus.contains("idle")
    }

    private fun extractChangeSetUnifiedPatch(
        itemObject: JsonObject,
        itemType: String,
    ): String? {
        return when (itemType) {
            "diff", "toolcall" -> {
                collectPatchCandidates(itemObject)
                    .firstOrNull { candidate -> RemodexUnifiedPatchParser.looksLikePatchText(candidate) }
                    ?.let(RemodexUnifiedPatchParser::normalize)
            }

            "filechange" -> {
                val joinedPatch = itemObject.firstValue(
                    "changes",
                    "file_changes",
                    "fileChanges",
                    "files",
                    "edits",
                    "modified_files",
                    "modifiedFiles",
                    "patches",
                )?.let { changesValue ->
                    val normalizedPatches = collectPatchCandidates(changesValue)
                        .mapNotNull(RemodexUnifiedPatchParser::normalize)
                    normalizedPatches.takeIf(List<String>::isNotEmpty)?.joinToString(separator = "\n")
                }
                RemodexUnifiedPatchParser.normalize(joinedPatch)
                    ?: collectPatchCandidates(itemObject)
                        .firstOrNull { candidate -> RemodexUnifiedPatchParser.looksLikePatchText(candidate) }
                        ?.let(RemodexUnifiedPatchParser::normalize)
            }

            else -> null
        }
    }

    private fun collectPatchCandidates(
        element: JsonElement?,
        depth: Int = 0,
    ): List<String> {
        if (element == null || depth > MaxPatchSearchDepth) {
            return emptyList()
        }
        return when (element) {
            is JsonObject -> {
                val directMatches = listOfNotNull(
                    element.firstString("diff", "unified_diff", "unifiedDiff", "patch"),
                )
                directMatches + element.entries.flatMap { (key, value) ->
                    when (key) {
                        "changes",
                        "file_changes",
                        "fileChanges",
                        "files",
                        "edits",
                        "modified_files",
                        "modifiedFiles",
                        "patches",
                        "output",
                        "result",
                        "payload",
                        "data",
                        "event",
                        "content" -> collectPatchCandidates(value, depth + 1)

                        else -> emptyList()
                    }
                }
            }

            is JsonArray -> element.flatMap { value -> collectPatchCandidates(value, depth + 1) }
            else -> emptyList()
        }
    }

    private fun decodeRevertPreview(resultObject: JsonObject?): RemodexRevertPreviewResult {
        return RemodexRevertPreviewResult(
            canRevert = resultObject?.firstBoolean("canRevert") ?: false,
            affectedFiles = resultObject?.firstArray("affectedFiles").orEmpty()
                .mapNotNull(JsonElement::stringOrNull),
            conflicts = decodeRevertConflicts(resultObject?.firstArray("conflicts")),
            unsupportedReasons = resultObject?.firstArray("unsupportedReasons").orEmpty()
                .mapNotNull(JsonElement::stringOrNull),
            stagedFiles = resultObject?.firstArray("stagedFiles").orEmpty()
                .mapNotNull(JsonElement::stringOrNull),
        )
    }

    private fun decodeRevertApply(resultObject: JsonObject?): RemodexRevertApplyResult {
        return RemodexRevertApplyResult(
            success = resultObject?.firstBoolean("success") ?: false,
            revertedFiles = resultObject?.firstArray("revertedFiles").orEmpty()
                .mapNotNull(JsonElement::stringOrNull),
            conflicts = decodeRevertConflicts(resultObject?.firstArray("conflicts")),
            unsupportedReasons = resultObject?.firstArray("unsupportedReasons").orEmpty()
                .mapNotNull(JsonElement::stringOrNull),
            stagedFiles = resultObject?.firstArray("stagedFiles").orEmpty()
                .mapNotNull(JsonElement::stringOrNull),
            status = resultObject?.firstObject("status")?.let(::decodeGitRepoSync),
        )
    }

    private fun decodeRevertConflicts(conflicts: JsonArray?): List<RemodexRevertConflict> {
        return conflicts.orEmpty().mapNotNull { value ->
            val conflictObject = value.jsonObjectOrNull ?: return@mapNotNull null
            RemodexRevertConflict(
                path = conflictObject.firstString("path") ?: "unknown",
                message = conflictObject.firstString("message") ?: "Patch conflict.",
            )
        }
    }

    private fun resolveInterruptibleTurnId(threadObject: JsonObject): String? {
        return resolveTurnReadState(threadObject).interruptibleTurnId
    }

    private fun resolveTurnReadState(threadObject: JsonObject): TurnReadState {
        val turns = threadObject.firstArray("turns").orEmpty()
        var hasInterruptibleTurnWithoutId = false
        val interruptibleTurnId = turns.reversed().firstNotNullOfOrNull { element ->
            val turnObject = element.jsonObjectOrNull ?: return@firstNotNullOfOrNull null
            val normalizedStatus = normalizeStatus(
                turnObject.firstString("status", "turnStatus", "turn_status").orEmpty(),
            )
            if (!isInterruptibleTurnStatus(normalizedStatus)) {
                null
            } else {
                turnObject.firstString("id", "turnId", "turn_id")
                    ?: run {
                        hasInterruptibleTurnWithoutId = true
                        null
                    }
            }
        }
        return TurnReadState(
            interruptibleTurnId = interruptibleTurnId,
            hasInterruptibleTurnWithoutId = hasInterruptibleTurnWithoutId,
        )
    }

    private fun isInterruptibleTurnStatus(normalizedStatus: String): Boolean {
        if (
            normalizedStatus.contains("inprogress") ||
            normalizedStatus.contains("running") ||
            normalizedStatus.contains("pending") ||
            normalizedStatus.contains("started")
        ) {
            return true
        }

        if (
            normalizedStatus.contains("complete") ||
            normalizedStatus.contains("done") ||
            normalizedStatus.contains("finish") ||
            normalizedStatus.contains("idle") ||
            normalizedStatus.contains("notloaded") ||
            normalizedStatus.contains("success") ||
            normalizedStatus.contains("succeed") ||
            normalizedStatus.contains("fail") ||
            normalizedStatus.contains("error") ||
            normalizedStatus.contains("interrupt") ||
            normalizedStatus.contains("cancel") ||
            normalizedStatus.contains("stopped")
        ) {
            return false
        }

        return true
    }

    private fun applyTurnReadState(
        threadId: String,
        turnReadState: TurnReadState,
    ) {
        when {
            turnReadState.interruptibleTurnId != null -> {
                setActiveTurnId(threadId = threadId, turnId = turnReadState.interruptibleTurnId)
            }

            turnReadState.hasInterruptibleTurnWithoutId -> {
                markThreadAsRunningFallback(threadId)
            }

            else -> {
                clearThreadRunningState(threadId)
            }
        }
    }

    private fun threadHasKnownRunningState(threadId: String): Boolean {
        return activeTurnIdByThread.containsKey(threadId) || runningThreadFallbackIds.contains(threadId)
    }

    private fun decodeThreadActivityState(statusObject: JsonObject?): ThreadActivityState? {
        val flags = statusObject
            ?.firstArray("activeFlags", "active_flags")
            .orEmpty()
            .mapNotNull { value -> value.jsonPrimitive.contentOrNull }
            .map(::normalizeStatus)
            .toSet()
        val normalizedStatus = normalizeStatus(
            statusObject?.firstString("type", "statusType", "status_type").orEmpty(),
        )
        if (normalizedStatus != "active" && flags.isEmpty()) {
            return null
        }
        return ThreadActivityState(
            isWaitingOnApproval = "waitingonapproval" in flags,
            isWaitingOnUserInput = "waitingonuserinput" in flags,
        )
    }

    private fun updateThreadWaitingOnApproval(
        threadId: String,
        isWaitingOnApproval: Boolean,
    ) {
        if (threadId.isBlank()) {
            return
        }
        updateThread(threadId) { snapshot ->
            if (snapshot.isWaitingOnApproval == isWaitingOnApproval) {
                snapshot
            } else {
                snapshot.copy(isWaitingOnApproval = isWaitingOnApproval)
            }
        }
    }

    private fun ThreadSyncSnapshot.withResolvedLiveThreadState(
        threadId: String,
        isRunning: Boolean? = null,
    ): ThreadSyncSnapshot {
        return copy(
            isRunning = isRunning ?: threadHasKnownRunningState(threadId),
            activeTurnId = activeTurnIdByThread[threadId],
            latestTurnTerminalState = latestTurnTerminalStateByThread[threadId],
            stoppedTurnIds = stoppedTurnIdsByThread[threadId].orEmpty(),
        )
    }

    private fun terminalStateForNormalizedStatus(normalizedStatus: String): RemodexTurnTerminalState? {
        return when (normalizedStatus) {
            "completed", "done", "finished", "idle", "notloaded" -> RemodexTurnTerminalState.COMPLETED
            "stopped" -> RemodexTurnTerminalState.STOPPED
            "systemerror" -> RemodexTurnTerminalState.FAILED
            else -> null
        }
    }

    private fun setLatestTurnTerminalState(
        threadId: String,
        state: RemodexTurnTerminalState,
        turnId: String?,
    ) {
        latestTurnTerminalStateByThread[threadId] = state
        if (state != RemodexTurnTerminalState.COMPLETED) {
            assistantResponseTraceByThread.remove(threadId)
        }
        if (state == RemodexTurnTerminalState.STOPPED && !turnId.isNullOrBlank()) {
            stoppedTurnIdsByThread[threadId] = stoppedTurnIdsByThread[threadId].orEmpty() + turnId
        }
        syncThreadLifecycleState(threadId = threadId)
    }

    private fun setActiveTurnId(
        threadId: String,
        turnId: String,
    ) {
        activeTurnIdByThread[threadId] = turnId
        threadIdByTurnId[turnId] = threadId
        streamingTraceByThread[threadId] = AssistantStreamingTrace(lastTurnId = turnId)
        val existingTrace = assistantResponseTraceByThread[threadId]
        assistantResponseTraceByThread[threadId] = if (existingTrace?.turnId == turnId) {
            existingTrace
        } else {
            AssistantResponseTrace(
                requestStartedAtMs = nowEpochMs(),
                turnId = turnId,
                baselineTotalOutputTokens = latestAssistantTotalOutputTokensByThread[threadId],
            )
        }
        runningThreadFallbackIds.remove(threadId)
        latestTurnTerminalStateByThread.remove(threadId)
        stoppedTurnIdsByThread[threadId] = stoppedTurnIdsByThread[threadId].orEmpty() - turnId
        syncThreadLifecycleState(threadId = threadId, isRunning = true)
    }

    private fun markThreadAsRunningFallback(threadId: String) {
        activeTurnIdByThread.remove(threadId)
        runningThreadFallbackIds.add(threadId)
        latestTurnTerminalStateByThread.remove(threadId)
        syncThreadLifecycleState(threadId = threadId, isRunning = true)
    }

    private fun clearThreadRunningState(threadId: String) {
        activeTurnIdByThread.remove(threadId)
        runningThreadFallbackIds.remove(threadId)
        syncThreadLifecycleState(threadId = threadId, isRunning = false)
    }

    private fun trackAssistantMessageReference(
        threadId: String,
        turnId: String?,
        itemId: String?,
        messageId: String,
    ) {
        val now = nowEpochMs()
        val previous = assistantResponseTraceByThread[threadId]
        val nextTrace = if (previous != null && (turnId == null || previous.turnId == null || previous.turnId == turnId)) {
            previous.copy(
                turnId = turnId ?: previous.turnId,
                itemId = itemId ?: previous.itemId,
                messageId = messageId,
            )
        } else {
            AssistantResponseTrace(
                requestStartedAtMs = now,
                turnId = turnId,
                itemId = itemId,
                messageId = messageId,
                baselineTotalOutputTokens = latestAssistantTotalOutputTokensByThread[threadId],
            )
        }
        assistantResponseTraceByThread[threadId] = nextTrace
    }

    private fun recordAssistantOutputObserved(
        threadId: String,
        turnId: String?,
        itemId: String?,
        messageId: String,
    ) {
        val now = nowEpochMs()
        val previous = assistantResponseTraceByThread[threadId]
        val nextTrace = if (previous != null && (turnId == null || previous.turnId == null || previous.turnId == turnId)) {
            previous.copy(
                turnId = turnId ?: previous.turnId,
                itemId = itemId ?: previous.itemId,
                messageId = messageId,
                firstOutputAtMs = previous.firstOutputAtMs ?: now,
                lastOutputAtMs = now,
                outputObservationCount = previous.outputObservationCount + 1,
            )
        } else {
            AssistantResponseTrace(
                requestStartedAtMs = now,
                turnId = turnId,
                itemId = itemId,
                messageId = messageId,
                firstOutputAtMs = now,
                lastOutputAtMs = now,
                baselineTotalOutputTokens = latestAssistantTotalOutputTokensByThread[threadId],
                outputObservationCount = 1,
            )
        }
        assistantResponseTraceByThread[threadId] = nextTrace
        maybePublishAssistantResponseMetrics(threadId)
    }

    private fun recordAssistantOutputTokens(
        threadId: String,
        turnId: String?,
        totalOutputTokens: Int?,
        lastOutputTokens: Int?,
    ) {
        if ((totalOutputTokens == null || totalOutputTokens < 0) && (lastOutputTokens == null || lastOutputTokens < 0)) {
            return
        }
        val now = nowEpochMs()
        val previous = assistantResponseTraceByThread[threadId]
        val normalizedTotalOutputTokens = totalOutputTokens?.takeIf { it >= 0 }
        val normalizedLastOutputTokens = lastOutputTokens?.takeIf { it >= 0 }
        val rememberedTotalOutputTokens = latestAssistantTotalOutputTokensByThread[threadId]
        val baselineTotalOutputTokens = previous?.baselineTotalOutputTokens
            ?: rememberedTotalOutputTokens
            ?: if (normalizedTotalOutputTokens != null && normalizedLastOutputTokens != null) {
                (normalizedTotalOutputTokens - normalizedLastOutputTokens).coerceAtLeast(0)
            } else {
                null
            }
        normalizedTotalOutputTokens?.let { total ->
            latestAssistantTotalOutputTokensByThread[threadId] = maxOf(total, rememberedTotalOutputTokens ?: total)
        }
        val derivedOutputTokens = when {
            normalizedTotalOutputTokens != null && baselineTotalOutputTokens != null -> {
                (normalizedTotalOutputTokens - baselineTotalOutputTokens).coerceAtLeast(0)
            }

            previous?.outputTokens != null && normalizedLastOutputTokens != null -> {
                previous.outputTokens!! + normalizedLastOutputTokens
            }

            normalizedLastOutputTokens != null -> normalizedLastOutputTokens
            else -> previous?.outputTokens
        }
        val nextTrace = if (previous != null && (turnId == null || previous.turnId == null || previous.turnId == turnId)) {
            previous.copy(
                turnId = turnId ?: previous.turnId,
                baselineTotalOutputTokens = previous.baselineTotalOutputTokens ?: baselineTotalOutputTokens,
                outputTokens = derivedOutputTokens?.let { maxOf(it, previous.outputTokens ?: 0) } ?: previous.outputTokens,
            )
        } else {
            AssistantResponseTrace(
                requestStartedAtMs = now,
                turnId = turnId,
                outputTokens = derivedOutputTokens,
                baselineTotalOutputTokens = baselineTotalOutputTokens,
            )
        }
        assistantResponseTraceByThread[threadId] = nextTrace
        maybePublishAssistantResponseMetrics(threadId)
    }

    private fun markAssistantTurnCompleted(
        threadId: String,
        turnId: String?,
    ) {
        val previous = assistantResponseTraceByThread[threadId] ?: return
        if (turnId != null && previous.turnId != null && previous.turnId != turnId) {
            return
        }
        assistantResponseTraceByThread[threadId] = previous.copy(
            turnId = turnId ?: previous.turnId,
            turnCompleted = true,
        )
        maybePublishAssistantResponseMetrics(threadId)
    }

    private fun maybePublishAssistantResponseMetrics(threadId: String) {
        val trace = assistantResponseTraceByThread[threadId] ?: return
        if (!trace.turnCompleted) {
            return
        }
        val messageId = trace.messageId ?: return
        val outputTokens = trace.outputTokens ?: return
        val firstOutputAtMs = trace.firstOutputAtMs ?: return
        val lastOutputAtMs = trace.lastOutputAtMs ?: return
        val metrics = buildAssistantResponseMetrics(
            messageId = messageId,
            turnId = trace.turnId,
            outputTokens = outputTokens,
            requestStartedAtMs = trace.requestStartedAtMs,
            firstOutputAtMs = firstOutputAtMs,
            lastOutputAtMs = lastOutputAtMs,
            outputObservationCount = trace.outputObservationCount,
        ) ?: return
        backingAssistantResponseMetricsByThreadId.value =
            backingAssistantResponseMetricsByThreadId.value + (threadId to metrics)
    }

    private fun scheduleLifecycleCatchup(
        threadId: String,
        turnId: String? = null,
        forceDelayedRetries: Boolean = false,
    ) {
        val existingJob = lifecycleCatchupJobByThread[threadId]
        if (existingJob?.isActive == true) {
            return
        }

        lifecycleCatchupJobByThread[threadId] = scope.launch {
            try {
                performLifecycleCatchup(threadId)
                if (shouldRunDelayedLifecycleCatchupPasses(
                        threadId = threadId,
                        turnId = turnId,
                        forceDelayedRetries = forceDelayedRetries,
                    )
                ) {
                    for (delayMs in postTurnCatchupDelaysMs) {
                        if (!shouldRunDelayedLifecycleCatchupPasses(
                                threadId = threadId,
                                turnId = turnId,
                                forceDelayedRetries = forceDelayedRetries,
                            )
                        ) {
                            break
                        }
                        delay(delayMs)
                        if (!isConnected()) {
                            break
                        }
                        performLifecycleCatchup(threadId)
                    }
                }
            } finally {
                lifecycleCatchupJobByThread.remove(threadId)
            }
        }
    }

    private suspend fun performLifecycleCatchup(threadId: String) {
        refreshThreads()
        hydrateThread(threadId)
    }

    private fun shouldRunDelayedLifecycleCatchupPasses(
        threadId: String,
        turnId: String?,
        forceDelayedRetries: Boolean,
    ): Boolean {
        if (forceDelayedRetries) {
            return true
        }
        return hasStreamingMessage(threadId) ||
            shouldContinueTurnCompletionCatchup(threadId = threadId, turnId = turnId)
    }

    private fun syncThreadLifecycleState(
        threadId: String,
        isRunning: Boolean? = null,
    ) {
        var previousSnapshot: ThreadSyncSnapshot? = null
        var nextSnapshot: ThreadSyncSnapshot? = null
        backingThreads.value = backingThreads.value.map { snapshot ->
            if (snapshot.id == threadId) {
                previousSnapshot = snapshot
                snapshot.withResolvedLiveThreadState(
                    threadId = threadId,
                    isRunning = isRunning,
                ).also { updatedSnapshot ->
                    nextSnapshot = updatedSnapshot
                }
            } else {
                snapshot
            }
        }
        if (shouldLogLifecycleTransition(previousSnapshot, nextSnapshot)) {
            logThreadRecovery(
                "event=syncThreadLifecycleState threadId=$threadId previous=${threadSnapshotSummary(previousSnapshot)} " +
                    "next=${threadSnapshotSummary(nextSnapshot)} fallbackRunning=${threadId in runningThreadFallbackIds}",
            )
        }
    }

    private fun buildTurnStartParams(
        threadId: String,
        prompt: String,
        attachments: List<RemodexComposerAttachment>,
        runtimeConfig: RemodexRuntimeConfig,
        includeServiceTier: Boolean,
        imageUrlKey: String,
        includeCollaborationMode: Boolean,
    ): JsonObject {
        val inputItems = buildJsonArray {
            attachments.forEach { attachment ->
                val payloadDataUrl = attachment.payloadDataUrl
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: attachment.uriString
                        .takeIf { uri -> uri.startsWith("data:image", ignoreCase = true) }
                if (payloadDataUrl == null) {
                    return@forEach
                }
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("image"))
                        put(imageUrlKey, JsonPrimitive(payloadDataUrl))
                    },
                )
            }
            if (prompt.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(prompt))
                    },
                )
            }
        }
        return buildJsonObject {
            put("threadId", JsonPrimitive(threadId))
            put("input", inputItems)
            val projectPath = backingThreads.value.firstOrNull { it.id == threadId }?.projectPath
            if (!projectPath.isNullOrBlank()) {
                put("cwd", JsonPrimitive(projectPath))
            }
            runtimeConfig.selectedModelId?.takeIf { it.isNotBlank() }?.let { put("model", JsonPrimitive(it)) }
            runtimeConfig.reasoningEffort?.takeIf { it.isNotBlank() }?.let {
                put("reasoningEffort", JsonPrimitive(it))
            }
            if (includeServiceTier) {
                runtimeConfig.serviceTier?.let { put("serviceTier", JsonPrimitive(it.wireValue)) }
            }
            if (includeCollaborationMode) {
                buildCollaborationModePayload(runtimeConfig)?.let { put("collaborationMode", it) }
            }
        }
    }

    private fun buildCollaborationModePayload(
        runtimeConfig: RemodexRuntimeConfig,
    ): JsonObject? {
        val mode = when (runtimeConfig.planningMode) {
            RemodexPlanningMode.PLAN -> "plan"
            RemodexPlanningMode.AUTO -> "default"
        }

        if (!supportsTurnCollaborationMode) {
            return null
        }

        val settings = buildJsonObject {
            runtimeConfig.selectedModelId?.takeIf(String::isNotBlank)?.let {
                put("model", JsonPrimitive(it))
            }
            runtimeConfig.reasoningEffort?.takeIf(String::isNotBlank)?.let {
                put("reasoning_effort", JsonPrimitive(it))
            }
        }

        return buildJsonObject {
            put("mode", JsonPrimitive(mode))
            if (settings.isNotEmpty()) {
                put("settings", settings)
            }
        }
    }

    private fun optimisticAppendUserMessage(
        threadId: String,
        prompt: String,
        attachments: List<RemodexComposerAttachment>,
        runtimeConfig: RemodexRuntimeConfig,
    ) {
        val now = nowEpochMs()
        backingThreads.value = backingThreads.value.map { snapshot ->
            if (snapshot.id != threadId) {
                snapshot
            } else {
                val orderIndex = nextOrderIndex(snapshot)
                snapshot.copy(
                    preview = androidUserMessageText(
                        prompt = prompt,
                        attachmentCount = attachments.size,
                    ),
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                    isRunning = true,
                    runtimeConfig = runtimeConfig,
                    timelineMutations = snapshot.timelineMutations + TimelineMutation.Upsert(
                        timelineItem(
                            id = "user-local-$now",
                            speaker = ConversationSpeaker.USER,
                            text = androidUserMessageText(
                                prompt = prompt,
                                attachmentCount = attachments.size,
                            ),
                            deliveryState = RemodexMessageDeliveryState.PENDING,
                            createdAtEpochMs = now,
                            attachments = attachments.map { attachment -> attachment.toConversationAttachment() },
                            orderIndex = orderIndex,
                        ),
                    ),
                ).withResolvedLiveThreadState(
                    threadId = threadId,
                    isRunning = true,
                )
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    private fun updateThread(
        threadId: String,
        transform: (ThreadSyncSnapshot) -> ThreadSyncSnapshot,
    ) {
        backingThreads.value = backingThreads.value.map { snapshot ->
            if (snapshot.id == threadId) transform(snapshot) else snapshot
        }
    }

    private fun mergeRuntimeConfig(
        existing: RemodexRuntimeConfig?,
        incoming: RemodexRuntimeConfig,
    ): RemodexRuntimeConfig {
        if (existing == null) {
            return incoming.normalizeSelections()
        }
        return incoming.copy(
            availableModels = if (incoming.availableModels.isNotEmpty()) incoming.availableModels else existing.availableModels,
            availableReasoningEfforts = if (incoming.availableReasoningEfforts.isNotEmpty()) {
                incoming.availableReasoningEfforts
            } else {
                existing.availableReasoningEfforts
            },
            availableServiceTiers = if (incoming.availableServiceTiers.isNotEmpty()) incoming.availableServiceTiers else existing.availableServiceTiers,
            selectedModelId = incoming.selectedModelId ?: existing.selectedModelId,
            planningMode = incoming.planningMode,
            reasoningEffort = incoming.reasoningEffort,
            accessMode = incoming.accessMode,
            serviceTier = incoming.serviceTier ?: existing.serviceTier,
        ).normalizeSelections()
    }

    private fun parseModelOption(value: JsonElement): RemodexModelOption? {
        val modelObject = value.jsonObjectOrNull ?: return null
        val model = modelObject.firstString("model", "id")?.trim().orEmpty()
        val id = modelObject.firstString("id")?.trim()?.takeIf { value -> value.isNotEmpty() } ?: model
        if (id.isEmpty() && model.isEmpty()) {
            return null
        }
        return RemodexModelOption(
            id = id.ifEmpty { model },
            model = model.ifEmpty { id },
            displayName = modelObject.firstString("displayName", "display_name")?.trim()?.takeIf { value ->
                value.isNotEmpty()
            }
                ?: model.ifEmpty { id },
            description = modelObject.firstString("description")?.trim().orEmpty(),
            isDefault = modelObject.firstBoolean("isDefault", "is_default") ?: false,
            supportedReasoningEfforts = modelObject
                .firstArray("supportedReasoningEfforts", "supported_reasoning_efforts")
                .orEmpty()
                .mapNotNull(::parseReasoningEffortOption),
            defaultReasoningEffort = modelObject
                .firstString("defaultReasoningEffort", "default_reasoning_effort")
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        ).normalizedOrNull()
    }

    private fun parseReasoningEffortOption(value: JsonElement): RemodexReasoningEffortOption? {
        val primitiveValue = value.stringOrNull?.trim()
        if (!primitiveValue.isNullOrEmpty()) {
            return RemodexReasoningEffortOption(
                reasoningEffort = primitiveValue,
                description = "",
            ).normalizedOrNull()
        }

        val optionObject = value.jsonObjectOrNull ?: return null
        return RemodexReasoningEffortOption(
            reasoningEffort = optionObject.firstString("reasoningEffort", "reasoning_effort", "id", "name").orEmpty(),
            description = optionObject.firstString("description").orEmpty(),
        ).normalizedOrNull()
    }

    private fun nextOrderIndex(snapshot: ThreadSyncSnapshot): Long {
        return (snapshot.timelineMutations
            .mapNotNull { mutation ->
                when (mutation) {
                    is TimelineMutation.Upsert -> mutation.item.orderIndex
                    is TimelineMutation.AssistantTextDelta -> mutation.orderIndex
                    is TimelineMutation.ReasoningTextDelta -> mutation.orderIndex
                    is TimelineMutation.ActivityLine -> mutation.orderIndex
                    is TimelineMutation.SystemTextDelta -> mutation.orderIndex
                    is TimelineMutation.Complete -> null
                }
            }.maxOrNull() ?: -1L) + 1L
    }

    private fun extractTurnId(value: JsonElement?): String? {
        val objectValue = value?.jsonObjectOrNull ?: return null
        val eventObject = envelopeEventObject(objectValue)
        return objectValue.firstString("turnId", "turn_id")
            ?: objectValue.firstObject("turn")?.firstString("id", "turnId", "turn_id")
            ?: objectValue.firstObject("item")?.firstString("turnId", "turn_id")
            ?: eventObject?.firstString("turnId", "turn_id")
            ?: eventObject?.firstObject("turn")?.firstString("id", "turnId", "turn_id")
            ?: eventObject?.firstObject("item")?.firstString("turnId", "turn_id")
            ?: objectValue.firstObject("event")?.firstString("turnId", "turn_id")
            ?: objectValue.firstObject("event")?.firstObject("turn")?.firstString("id", "turnId", "turn_id")
    }

    private fun decodeTimestampMillis(raw: Double?): Long? {
        if (raw == null) {
            return null
        }
        return if (abs(raw) > 10_000_000_000) raw.toLong() else (raw * 1000).toLong()
    }

    private fun relativeUpdatedLabel(epochMs: Long): String {
        val deltaSeconds = ((nowEpochMs() - epochMs) / 1000).coerceAtLeast(0)
        return when {
            deltaSeconds < 60 -> "Updated just now"
            deltaSeconds < 3600 -> "Updated ${deltaSeconds / 60}m ago"
            deltaSeconds < 86_400 -> "Updated ${deltaSeconds / 3600}h ago"
            deltaSeconds < 172_800 -> "Updated yesterday"
            else -> {
                val instant = Instant.ofEpochMilli(epochMs)
                "Updated ${instant.toString().take(10)}"
            }
        }
    }

    private fun currentSyncState(threadId: String): RemodexThreadSyncState {
        return backingThreads.value.firstOrNull { it.id == threadId }?.syncState
            ?: RemodexThreadSyncState.LIVE
    }

    private fun normalizeItemType(value: String): String {
        return value
            .trim()
            .replace("_", "")
            .replace("-", "")
            .lowercase(Locale.ROOT)
    }

    private fun normalizeStatus(value: String): String {
        return value
            .trim()
            .replace("_", "")
            .replace("-", "")
            .lowercase(Locale.ROOT)
    }

    private suspend fun <T> runBackgroundSyncOperation(
        operation: String,
        block: suspend () -> T,
    ): T? {
        return try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException || error is SecureTransportException) {
                Log.d(logTag, "$operation skipped: ${error.message.orEmpty()}")
                return null
            }
            throw error
        }
    }

    private fun isConnected(): Boolean {
        return secureConnectionCoordinator.isEncryptedSessionReady()
    }

    private fun shouldLogReviewDebug(
        method: String,
        paramsObject: JsonObject?,
    ): Boolean {
        if (paramsObject == null) {
            return false
        }
        val eventObject = envelopeEventObject(paramsObject)
        val resolvedTurnId = extractAssistantTurnId(paramsObject, eventObject)
            ?: extractTurnId(paramsObject)
            ?: extractTurnIdForTurnLifecycleEvent(paramsObject)
        val resolvedThreadId = resolveThreadId(paramsObject, turnIdHint = resolvedTurnId)
        if ((resolvedTurnId != null && reviewDebugTurnIds.contains(resolvedTurnId)) ||
            (resolvedThreadId != null && reviewDebugThreadIds.contains(resolvedThreadId))
        ) {
            return true
        }
        val normalizedMethod = method.trim().lowercase(Locale.ROOT)
        if (normalizedMethod == "turn/completed") {
            return true
        }
        if (normalizedMethod.contains("review")) {
            return true
        }
        if (
            normalizedMethod == "codex/event/agent_message" ||
            normalizedMethod == "codex/event/item_completed" ||
            normalizedMethod == "item/completed" ||
            normalizedMethod == "item/started" ||
            normalizedMethod == "codex/event/item_started" ||
            normalizedMethod == "codex/event/agent_message_content_delta" ||
            normalizedMethod == "codex/event/agent_message_delta" ||
            normalizedMethod == "item/agentmessage/delta"
        ) {
            val itemObject = extractIncomingItemObject(paramsObject, eventObject)
            val itemType = normalizeItemType(
                itemObject?.firstString("type")
                    ?: eventObject?.firstString("type")
                    ?: paramsObject.firstString("type")
                    ?: "",
            )
            if (itemType == "enteredreviewmode" || itemType == "exitedreviewmode") {
                return true
            }
            if (itemObject?.firstValue("review") != null || eventObject?.firstValue("review") != null) {
                return true
            }
        }
        return false
    }

    private fun logAssistantStreamingTraceDelta(
        threadId: String,
        turnId: String,
        itemId: String?,
        deltaLength: Int,
    ) {
        val nextTrace = (streamingTraceByThread[threadId] ?: AssistantStreamingTrace()).copy(
            deltaCount = streamingTraceByThread[threadId]?.deltaCount?.plus(1) ?: 1,
            totalChars = (streamingTraceByThread[threadId]?.totalChars ?: 0) + deltaLength,
            lastDeltaLength = deltaLength,
            lastTurnId = turnId,
            lastItemId = itemId,
        )
        streamingTraceByThread[threadId] = nextTrace
        if (nextTrace.deltaCount <= 3 || nextTrace.deltaCount % 32 == 0) {
            emitStreamingTraceLog(
                "assistantStreamTrace event=delta threadId=$threadId turnId=$turnId itemId=${itemId.orEmpty()} count=${nextTrace.deltaCount} chars=${nextTrace.totalChars} lastDelta=$deltaLength activeTurnId=${activeTurnIdByThread[threadId].orEmpty()}",
            )
        }
    }

    private fun logAssistantStreamingTraceFinalAnswer(
        threadId: String,
        turnId: String?,
        itemId: String?,
    ) {
        val previous = streamingTraceByThread[threadId] ?: AssistantStreamingTrace()
        val nextTrace = previous.copy(
            finalAnswerCompleted = true,
            lastTurnId = turnId ?: previous.lastTurnId,
            lastItemId = itemId ?: previous.lastItemId,
        )
        streamingTraceByThread[threadId] = nextTrace
        emitStreamingTraceLog(
            "assistantStreamTrace event=finalAnswerCompleted threadId=$threadId turnId=${(turnId ?: nextTrace.lastTurnId).orEmpty()} itemId=${(itemId ?: nextTrace.lastItemId).orEmpty()} count=${nextTrace.deltaCount} chars=${nextTrace.totalChars} lastDelta=${nextTrace.lastDeltaLength} activeTurnId=${activeTurnIdByThread[threadId].orEmpty()}",
        )
    }

    private fun logAssistantStreamingTraceThreadStatus(
        threadId: String,
        normalizedStatus: String,
    ) {
        val previous = streamingTraceByThread[threadId] ?: AssistantStreamingTrace()
        val nextTrace = previous.copy(
            idleObserved = previous.idleObserved || normalizedStatus == "idle",
        )
        streamingTraceByThread[threadId] = nextTrace
        emitStreamingTraceLog(
            "assistantStreamTrace event=threadStatus threadId=$threadId status=$normalizedStatus turnId=${nextTrace.lastTurnId.orEmpty()} itemId=${nextTrace.lastItemId.orEmpty()} count=${nextTrace.deltaCount} chars=${nextTrace.totalChars} finalAnswer=${nextTrace.finalAnswerCompleted} activeTurnId=${activeTurnIdByThread[threadId].orEmpty()}",
        )
    }

    private fun logAssistantStreamingTraceTerminal(
        threadId: String,
        event: String,
        turnId: String?,
    ) {
        val previous = streamingTraceByThread[threadId] ?: AssistantStreamingTrace()
        val nextTrace = previous.copy(
            turnCompletedObserved = previous.turnCompletedObserved || event == "turnCompleted",
            lastTurnId = turnId ?: previous.lastTurnId,
        )
        emitStreamingTraceLog(
            "assistantStreamTrace event=$event threadId=$threadId turnId=${(turnId ?: nextTrace.lastTurnId).orEmpty()} itemId=${nextTrace.lastItemId.orEmpty()} count=${nextTrace.deltaCount} chars=${nextTrace.totalChars} finalAnswer=${nextTrace.finalAnswerCompleted} idle=${nextTrace.idleObserved} activeTurnId=${activeTurnIdByThread[threadId].orEmpty()}",
        )
        streamingTraceByThread.remove(threadId)
    }

    private fun emitStreamingTraceLog(message: String) {
        runCatching { Log.d(logTag, message) }
    }

    private fun logThreadRecovery(message: String) {
        runCatching { Log.d(logTag, message) }
    }

    private fun shouldLogLifecycleTransition(
        previous: ThreadSyncSnapshot?,
        next: ThreadSyncSnapshot?,
    ): Boolean {
        if (previous == null || next == null) {
            return false
        }
        return previous.isRunning != next.isRunning ||
            previous.isWaitingOnApproval != next.isWaitingOnApproval ||
            previous.activeTurnId != next.activeTurnId ||
            previous.latestTurnTerminalState != next.latestTurnTerminalState ||
            previous.timelineMutations.size != next.timelineMutations.size
    }

    private fun threadSnapshotSummary(snapshot: ThreadSyncSnapshot?): String {
        return if (snapshot == null) {
            "null"
        } else {
            "id=${snapshot.id} isRunning=${snapshot.isRunning} waiting=${snapshot.isWaitingOnApproval} " +
                "activeTurnId=${snapshot.activeTurnId.orEmpty()} terminal=${snapshot.latestTurnTerminalState?.name ?: "null"} " +
                "timelineMutations=${snapshot.timelineMutations.size} syncState=${snapshot.syncState.name} projectPath=${snapshot.projectPath}"
        }
    }

    private fun turnReadStateSummary(state: TurnReadState): String {
        return "interruptibleTurnId=${state.interruptibleTurnId.orEmpty()} " +
            "hasInterruptibleTurnWithoutId=${state.hasInterruptibleTurnWithoutId}"
    }

    private fun threadActivitySummary(activityState: ThreadActivityState?): String {
        return if (activityState == null) {
            "null"
        } else {
            "waitingOnApproval=${activityState.isWaitingOnApproval} waitingOnUserInput=${activityState.isWaitingOnUserInput}"
        }
    }

    private fun threadStatusSummary(statusObject: JsonObject?): String {
        if (statusObject == null) {
            return "null"
        }
        val normalizedType = normalizeStatus(
            statusObject.firstString("type", "statusType", "status_type").orEmpty(),
        )
        val flags = statusObject
            .firstArray("activeFlags", "active_flags")
            .orEmpty()
            .mapNotNull { value -> value.jsonPrimitive.contentOrNull }
            .joinToString(separator = ",")
        return "type=$normalizedType flags=$flags"
    }

    private fun logReviewDebug(
        stage: String,
        method: String,
        paramsObject: JsonObject?,
        extra: String? = null,
    ) {
        if (paramsObject == null) {
            return
        }
        val eventObject = envelopeEventObject(paramsObject)
        val itemObject = extractIncomingItemObject(paramsObject, eventObject)
        val rawPayload = sanitizeReviewDebugJson(
            (itemObject ?: eventObject ?: paramsObject).toString(),
        )
        val summary = buildString {
            append("stage=").append(stage)
            append(" method=").append(method)
            append(" threadId=").append(resolveThreadId(paramsObject).orEmpty())
            append(" turnId=").append(
                extractAssistantTurnId(paramsObject, eventObject)
                    ?: extractTurnId(paramsObject)
                    ?: extractTurnIdForTurnLifecycleEvent(paramsObject)
                    ?: "",
            )
            append(" itemId=").append(
                extractItemId(
                    paramsObject = paramsObject,
                    eventObject = eventObject,
                    itemObject = itemObject,
                ).orEmpty(),
            )
            append(" itemType=").append(
                normalizeItemType(
                    itemObject?.firstString("type")
                        ?: eventObject?.firstString("type")
                        ?: paramsObject.firstString("type")
                        ?: "",
                ),
            )
            if (!extra.isNullOrBlank()) {
                append(" ").append(extra)
            }
            append(" payload=").append(rawPayload)
        }
        emitReviewDebugLog(summary)
    }

    private fun emitReviewDebugLog(message: String) {
        runCatching { Log.d(ReviewDebugTag, message) }
    }

    private fun describeReviewDebugMutation(
        mutation: TimelineMutation,
    ): String {
        return when (mutation) {
            is TimelineMutation.Upsert -> {
                "upsert[id=${mutation.item.id},speaker=${mutation.item.speaker.name},kind=${mutation.item.kind.name},turnId=${mutation.item.turnId.orEmpty()},itemId=${mutation.item.itemId.orEmpty()},streaming=${mutation.item.isStreaming},text=${mutation.item.text.take(80).replace('\n', ' ')}]"
            }

            is TimelineMutation.AssistantTextDelta -> {
                "assistantDelta[id=${mutation.messageId},turnId=${mutation.turnId},itemId=${mutation.itemId.orEmpty()},delta=${mutation.delta.take(80).replace('\n', ' ')}]"
            }

            is TimelineMutation.ReasoningTextDelta -> {
                "reasoningDelta[id=${mutation.messageId},turnId=${mutation.turnId},itemId=${mutation.itemId.orEmpty()},delta=${mutation.delta.take(80).replace('\n', ' ')}]"
            }

            is TimelineMutation.ActivityLine -> {
                "activityLine[id=${mutation.messageId},turnId=${mutation.turnId},itemId=${mutation.itemId.orEmpty()},line=${mutation.line.take(80).replace('\n', ' ')}]"
            }

            is TimelineMutation.SystemTextDelta -> {
                "systemDelta[id=${mutation.messageId},turnId=${mutation.turnId},itemId=${mutation.itemId.orEmpty()},kind=${mutation.kind.name},delta=${mutation.delta.take(80).replace('\n', ' ')}]"
            }

            is TimelineMutation.Complete -> {
                "complete[id=${mutation.messageId}]"
            }
        }
    }

    private fun describeReviewDebugItem(
        item: com.emanueledipietro.remodex.model.RemodexConversationItem,
    ): String {
        return "id=${item.id},speaker=${item.speaker.name},kind=${item.kind.name},turnId=${item.turnId.orEmpty()},itemId=${item.itemId.orEmpty()},streaming=${item.isStreaming},text=${item.text.take(80).replace('\n', ' ')}"
    }

    companion object {
        private const val logTag = "RemodexThreadSync"
        private const val MaxPatchSearchDepth = 4
        private const val ReviewDebugTag = "RemodexReviewDebug"
    }
}

internal fun sanitizeReviewDebugJson(raw: String): String {
    return raw
        .replace(Regex("\"sessionId\"\\s*:\\s*\"[^\"]*\""), "\"sessionId\":\"<redacted>\"")
        .replace(Regex("\"session_id\"\\s*:\\s*\"[^\"]*\""), "\"session_id\":\"<redacted>\"")
        .replace(Regex("\"ciphertext\"\\s*:\\s*\"[^\"]*\""), "\"ciphertext\":\"<redacted>\"")
        .replace(Regex("\"tag\"\\s*:\\s*\"[^\"]*\""), "\"tag\":\"<redacted>\"")
        .replace(Regex("\"macSignature\"\\s*:\\s*\"[^\"]*\""), "\"macSignature\":\"<redacted>\"")
}

internal fun isAssistantLifecycleItemValue(
    itemType: String,
    role: String?,
): Boolean {
    val normalizedRole = role?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return itemType == "agentmessage"
        || itemType == "assistantmessage"
        || itemType == "exitedreviewmode"
        || (itemType == "message" && !normalizedRole.contains("user"))
}

internal fun completedAssistantFallbackTextValue(
    paramsObject: JsonObject,
    eventObject: JsonObject?,
): String {
    return paramsObject.firstRawString("message")
        ?: eventObject?.firstRawString("message")
        ?: paramsObject.firstObject("event")?.firstRawString("message")
        ?: ""
}

internal fun extractAssistantTurnIdValue(
    paramsObject: JsonObject,
    eventObject: JsonObject?,
    extractedTurnId: String?,
): String? {
    return extractedTurnId
        ?: paramsObject.firstString("id")?.takeIf(String::isNotBlank)
            ?.takeIf { paramsObject.firstValue("msg", "event") != null }
        ?: eventObject?.firstObject("turn")?.firstString("id")
        ?: paramsObject.firstObject("event")?.firstObject("turn")?.firstString("id")
}

internal fun decodeIncomingMessageTextValue(itemObject: JsonObject): String {
    val parts = mutableListOf<String>()
    itemObject.firstArray("content").orEmpty().forEach { element ->
        val objectValue = element.jsonObjectOrNull ?: return@forEach
        val contentType = objectValue.firstString("type")?.trim()?.lowercase(Locale.ROOT)
        val isTextType = contentType == null
            || contentType == "text"
            || contentType == "inputtext"
            || contentType == "input_text"
            || contentType == "outputtext"
            || contentType == "output_text"
            || contentType == "message"
        if (contentType == "skill") {
            objectValue.firstString("id", "name")?.let { skillId ->
                if (skillId.isNotBlank()) {
                    parts += "\$$skillId"
                }
            }
            return@forEach
        }
        if (!isTextType) {
            return@forEach
        }
        objectValue.firstRawString("text")
            ?.takeIf(String::isNotEmpty)
            ?.let(parts::add)
            ?: objectValue.firstRawString("delta")
                ?.takeIf(String::isNotEmpty)
                ?.let(parts::add)
            ?: objectValue.firstObject("data")
                ?.firstRawString("text")
                ?.takeIf(String::isNotEmpty)
                ?.let(parts::add)
    }
    val joined = parts.joinToString(separator = "\n").trim()
    if (joined.isNotEmpty()) {
        return joined
    }
    return itemObject.firstString(
        "text",
        "message",
        "summary",
        "description",
        "stdout",
        "stderr",
        "output_text",
        "outputText",
    ).orEmpty()
}

internal fun shouldRetryWithApprovalPolicyFallbackValue(error: Throwable): Boolean {
    val rpcError = error as? RpcError ?: return false
    if (rpcError.code != -32600 && rpcError.code != -32602) {
        return false
    }
    val message = rpcError.message.lowercase(Locale.ROOT)
    if (message.contains("thread not found") || message.contains("unknown thread")) {
        return false
    }
    return message.contains("invalid params")
        || message.contains("invalid param")
        || message.contains("unknown field")
        || message.contains("unexpected field")
        || message.contains("unrecognized field")
        || message.contains("failed to parse")
        || message.contains("unsupported")
}

internal fun shouldRetryWithImageUrlFieldFallbackValue(error: Throwable): Boolean {
    val rpcError = error as? RpcError ?: return false
    if (rpcError.code != -32600 && rpcError.code != -32602) {
        return false
    }
    val message = rpcError.message.lowercase(Locale.ROOT)
    if (!message.contains("image_url")) {
        return false
    }
    return message.contains("missing")
        || message.contains("unknown field")
        || message.contains("expected")
        || message.contains("invalid")
}

internal fun buildStructuredUserInputResponse(
    answersByQuestionId: Map<String, List<String>>,
): JsonObject {
    return buildJsonObject {
        put(
            "answers",
            buildJsonObject {
                answersByQuestionId.forEach { (questionId, answers) ->
                    val filteredAnswers = answers
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                    put(
                        questionId,
                        buildJsonObject {
                            put(
                                "answers",
                                buildJsonArray {
                                    filteredAnswers.forEach { answer ->
                                        add(JsonPrimitive(answer))
                                    }
                                },
                            )
                        },
                    )
                }
            },
        )
    }
}

internal fun buildApprovalDecisionResponse(decision: String): JsonObject {
    return buildJsonObject {
        put("decision", JsonPrimitive(decision))
    }
}

internal fun buildPermissionsApprovalResponse(
    permissions: JsonObject,
    scope: RemodexPermissionGrantScope,
): JsonObject {
    return buildJsonObject {
        put("permissions", permissions)
        put("scope", JsonPrimitive(scope.wireValue))
    }
}

internal fun shouldRetryWithoutCollaborationMode(error: Throwable): Boolean {
    val rpcError = error as? RpcError ?: return false
    if (rpcError.code != -32600 && rpcError.code != -32602) {
        return false
    }
    val message = rpcError.message.lowercase(Locale.ROOT)
    if (!message.contains("collaborationmode") && !message.contains("collaboration_mode")) {
        return false
    }
    return message.contains("experimentalapi")
        || message.contains("unsupported")
        || message.contains("unknown")
        || message.contains("unexpected")
        || message.contains("unrecognized")
        || message.contains("invalid")
        || message.contains("expected")
        || message.contains("field")
        || message.contains("mode")
        || message.contains("type")
}

internal fun shouldRetryAfterThreadMaterializationValue(error: Throwable): Boolean {
    val message = when (error) {
        is RpcError -> error.message
        else -> error.message ?: error.localizedMessage ?: error.toString()
    }.lowercase(Locale.ROOT)

    if (message.contains("thread not found") || message.contains("unknown thread")) {
        return false
    }
    return message.contains("not materialized")
        || message.contains("not yet materialized")
        || message.contains("no rollout found for thread")
        || message.contains("no rollout file found for thread")
        || (
            message.contains("rollout") &&
                message.contains("thread") &&
                (message.contains("missing") || message.contains("not found"))
            )
}

internal fun shouldTreatAsThreadNotFoundValue(error: Throwable): Boolean {
    val message = when (error) {
        is RpcError -> error.message
        else -> error.message ?: error.localizedMessage ?: error.toString()
    }.lowercase(Locale.ROOT)

    if (message.contains("not materialized") || message.contains("not yet materialized")) {
        return false
    }
    return message.contains("thread not found") || message.contains("unknown thread")
}
