package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.ConversationSystemTurnOrderingHint
import com.emanueledipietro.remodex.model.RemodexApprovalRequest
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantResponseMetrics
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexCodeReviewRequest
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitCommit
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitRemoteUrl
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitWorktreeChangeTransferMode
import com.emanueledipietro.remodex.model.RemodexGitWorktreeResult
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexBridgeUpdatePrompt
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanState
import com.emanueledipietro.remodex.model.RemodexPermissionGrantScope
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputRequest
import com.emanueledipietro.remodex.model.RemodexSubagentAction
import com.emanueledipietro.remodex.model.RemodexTurnTerminalState
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

data class ThreadSyncSnapshot(
    val id: String,
    val title: String,
    val name: String? = null,
    val preview: String,
    val projectPath: String,
    val lastUpdatedLabel: String,
    val lastUpdatedEpochMs: Long,
    val isRunning: Boolean,
    val isWaitingOnApproval: Boolean = false,
    val syncState: RemodexThreadSyncState = RemodexThreadSyncState.LIVE,
    val parentThreadId: String? = null,
    val agentNickname: String? = null,
    val agentRole: String? = null,
    val activeTurnId: String? = null,
    val latestTurnTerminalState: RemodexTurnTerminalState? = null,
    val stoppedTurnIds: Set<String> = emptySet(),
    val runtimeConfig: RemodexRuntimeConfig,
    val timelineMutations: List<TimelineMutation>,
)

interface ThreadSyncService {
    val threads: StateFlow<List<ThreadSyncSnapshot>>
    val availableModels: StateFlow<List<RemodexModelOption>>
    val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>>
    val assistantResponseMetricsByThreadId: StateFlow<Map<String, RemodexAssistantResponseMetrics>>
    val pendingApprovalRequest: StateFlow<RemodexApprovalRequest?>
    val bridgeUpdatePrompt: StateFlow<RemodexBridgeUpdatePrompt?>
    val supportsThreadFork: StateFlow<Boolean>

    fun dismissBridgeUpdatePrompt()
}

interface ThreadCommandService {
    suspend fun createThread(
        preferredProjectPath: String?,
        runtimeDefaults: RemodexRuntimeDefaults,
    ): ThreadSyncSnapshot?

    suspend fun renameThread(
        threadId: String,
        name: String,
    )

    suspend fun archiveThread(
        threadId: String,
        unarchive: Boolean,
    )

    suspend fun deleteThread(threadId: String)

    suspend fun sendPrompt(
        threadId: String,
        prompt: String,
        runtimeConfig: RemodexRuntimeConfig,
        attachments: List<RemodexComposerAttachment>,
    )

    suspend fun compactThread(threadId: String)

    suspend fun respondToStructuredUserInput(
        requestId: JsonElement,
        answersByQuestionId: Map<String, List<String>>,
    )

    suspend fun approvePendingApproval(forSession: Boolean = false)

    suspend fun declinePendingApproval()

    suspend fun cancelPendingApproval()

    suspend fun grantPendingPermissionsApproval(
        scope: RemodexPermissionGrantScope = RemodexPermissionGrantScope.TURN,
    )

    suspend fun continueOnMac(threadId: String)

    suspend fun startCodeReview(
        threadId: String,
        request: RemodexCodeReviewRequest,
    )

    suspend fun forkThread(
        threadId: String,
        destination: RemodexComposerForkDestination,
        baseBranch: String? = null,
    ): ThreadSyncSnapshot?

    suspend fun forkThreadIntoProjectPath(
        threadId: String,
        projectPath: String,
    ): ThreadSyncSnapshot?

    suspend fun fuzzyFileSearch(
        threadId: String,
        query: String,
    ): List<RemodexFuzzyFileMatch>

    suspend fun listSkills(
        threadId: String,
        forceReload: Boolean = false,
    ): List<RemodexSkillMetadata>

    suspend fun loadGitState(threadId: String): RemodexGitState

    suspend fun loadGitDiff(threadId: String): RemodexGitRepoDiff = RemodexGitRepoDiff()

    suspend fun loadGitCommits(threadId: String): List<RemodexGitCommit> = emptyList()

    suspend fun checkoutGitBranch(
        threadId: String,
        branch: String,
    ): RemodexGitState

    suspend fun createGitBranch(
        threadId: String,
        branch: String,
    ): RemodexGitState

    suspend fun createGitWorktree(
        threadId: String,
        name: String,
        baseBranch: String?,
        changeTransfer: RemodexGitWorktreeChangeTransferMode = RemodexGitWorktreeChangeTransferMode.COPY,
    ): RemodexGitState

    suspend fun createGitWorktreeResult(
        threadId: String,
        name: String,
        baseBranch: String?,
        changeTransfer: RemodexGitWorktreeChangeTransferMode = RemodexGitWorktreeChangeTransferMode.COPY,
    ): RemodexGitWorktreeResult

    suspend fun commitGitChanges(
        threadId: String,
        message: String? = null,
    ): RemodexGitState

    suspend fun commitAndPushGitChanges(
        threadId: String,
        message: String? = null,
    ): RemodexGitState

    suspend fun pullGitChanges(threadId: String): RemodexGitState

    suspend fun pushGitChanges(threadId: String): RemodexGitState

    suspend fun discardRuntimeChangesAndSync(threadId: String): RemodexGitState

    suspend fun loadGitRemoteUrl(threadId: String): RemodexGitRemoteUrl

    suspend fun previewAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertPreviewResult

    suspend fun applyAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertApplyResult

    suspend fun stopTurn(threadId: String)
}

interface ThreadHydrationService {
    suspend fun refreshThreads()

    suspend fun hydrateThread(threadId: String)
}

interface ThreadResumeService {
    suspend fun resumeThread(
        threadId: String,
        preferredProjectPath: String? = null,
        modelIdentifier: String? = null,
    ): ThreadSyncSnapshot?

    suspend fun updateThreadProjectPathLocally(
        threadId: String,
        projectPath: String,
    ): ThreadSyncSnapshot? = null

    suspend fun moveThreadToProjectPath(
        threadId: String,
        projectPath: String,
        modelIdentifier: String? = null,
    ): ThreadSyncSnapshot? {
        return resumeThread(
            threadId = threadId,
            preferredProjectPath = projectPath,
            modelIdentifier = modelIdentifier,
        )
    }

    fun isThreadResumedLocally(threadId: String): Boolean = false
}

interface ThreadActiveContextService {
    fun setActiveThreadHint(threadId: String?)
}

interface ThreadLocalTimelineService {
    suspend fun appendLocalSystemMessage(
        threadId: String,
        text: String,
    )
}

sealed interface TimelineMutation {
    data class Upsert(
        val item: RemodexConversationItem,
    ) : TimelineMutation

    data class AssistantTextDelta(
        val messageId: String,
        val turnId: String,
        val itemId: String? = null,
        val delta: String,
        val orderIndex: Long,
    ) : TimelineMutation

    data class ReasoningTextDelta(
        val messageId: String,
        val turnId: String,
        val itemId: String? = null,
        val delta: String,
        val orderIndex: Long,
    ) : TimelineMutation

    data class ActivityLine(
        val messageId: String,
        val turnId: String,
        val itemId: String? = null,
        val line: String,
        val orderIndex: Long,
        val systemTurnOrderingHint: ConversationSystemTurnOrderingHint =
            ConversationSystemTurnOrderingHint.AUTO,
    ) : TimelineMutation

    data class SystemTextDelta(
        val messageId: String,
        val turnId: String,
        val itemId: String? = null,
        val delta: String,
        val kind: ConversationItemKind,
        val orderIndex: Long,
    ) : TimelineMutation

    data class Complete(
        val messageId: String,
    ) : TimelineMutation
}

fun timelineItem(
    id: String,
    speaker: ConversationSpeaker,
    text: String,
    kind: ConversationItemKind = ConversationItemKind.CHAT,
    supportingText: String? = null,
    turnId: String? = null,
    itemId: String? = null,
    isStreaming: Boolean = false,
    deliveryState: RemodexMessageDeliveryState = RemodexMessageDeliveryState.CONFIRMED,
    createdAtEpochMs: Long? = null,
    attachments: List<RemodexConversationAttachment> = emptyList(),
    planState: RemodexPlanState? = null,
    subagentAction: RemodexSubagentAction? = null,
    structuredUserInputRequest: RemodexStructuredUserInputRequest? = null,
    orderIndex: Long,
    assistantChangeSet: RemodexAssistantChangeSet? = null,
    systemTurnOrderingHint: ConversationSystemTurnOrderingHint =
        ConversationSystemTurnOrderingHint.AUTO,
): RemodexConversationItem {
    return RemodexConversationItem(
        id = id,
        speaker = speaker,
        kind = kind,
        text = text,
        supportingText = supportingText,
        turnId = turnId,
        itemId = itemId,
        isStreaming = isStreaming,
        deliveryState = deliveryState,
        createdAtEpochMs = createdAtEpochMs,
        attachments = attachments,
        planState = planState,
        subagentAction = subagentAction,
        structuredUserInputRequest = structuredUserInputRequest,
        orderIndex = orderIndex,
        assistantChangeSet = assistantChangeSet,
        systemTurnOrderingHint = systemTurnOrderingHint,
    )
}

internal fun mutationAffectsThreadPreviewValue(mutation: TimelineMutation): Boolean {
    return when (mutation) {
        is TimelineMutation.Upsert -> {
            mutation.item.kind == ConversationItemKind.CHAT &&
                (
                    mutation.item.speaker == ConversationSpeaker.USER ||
                        mutation.item.speaker == ConversationSpeaker.ASSISTANT
                    )
        }

        else -> false
    }
}
