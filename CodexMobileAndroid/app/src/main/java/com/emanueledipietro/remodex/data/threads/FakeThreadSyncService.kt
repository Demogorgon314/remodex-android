package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.feature.turn.TurnTimelineReducer
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetSource
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitBranches
import com.emanueledipietro.remodex.model.RemodexGitChangedFile
import com.emanueledipietro.remodex.model.RemodexGitDiffTotals
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitRepoSync
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexReasoningEffortOption
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexUnifiedPatchParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

class FakeThreadSyncService(
    initialThreads: List<ThreadSyncSnapshot> = seededThreadSnapshots(),
) : ThreadSyncService, ThreadCommandService {
    private val backingAvailableModels = MutableStateFlow(seededModelOptions())
    private val backingThreads = MutableStateFlow(initialThreads.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs))
    private val backingCommandExecutionDetails = MutableStateFlow<Map<String, RemodexCommandExecutionDetails>>(emptyMap())
    private val gitStateByThreadId = initialThreads.associate { snapshot ->
        snapshot.id to seedGitState(snapshot)
    }.toMutableMap()

    override val threads: StateFlow<List<ThreadSyncSnapshot>> = backingThreads
    override val availableModels: StateFlow<List<RemodexModelOption>> = backingAvailableModels
    override val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>> = backingCommandExecutionDetails

    fun updateThreads(threads: List<ThreadSyncSnapshot>) {
        backingThreads.value = threads.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
    }

    override suspend fun createThread(
        preferredProjectPath: String?,
        runtimeDefaults: RemodexRuntimeDefaults,
    ): ThreadSyncSnapshot {
        val now = System.currentTimeMillis()
        val snapshot = ThreadSyncSnapshot(
            id = "thread-${UUID.randomUUID()}",
            title = "New Chat",
            preview = "Start a new conversation from Android.",
            projectPath = preferredProjectPath.orEmpty(),
            lastUpdatedLabel = "Updated just now",
            lastUpdatedEpochMs = now,
            isRunning = false,
            runtimeConfig = RemodexRuntimeConfig(
                availableModels = availableModels.value,
                selectedModelId = runtimeDefaults.modelId,
                reasoningEffort = runtimeDefaults.reasoningEffort ?: "medium",
                accessMode = runtimeDefaults.accessMode,
                serviceTier = runtimeDefaults.serviceTier,
            ).normalizeSelections(),
            timelineMutations = emptyList(),
        )
        backingThreads.update { threads ->
            (listOf(snapshot) + threads).sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
        }
        return snapshot
    }

    override suspend fun renameThread(
        threadId: String,
        name: String,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return
        }
        backingThreads.update { threads ->
            threads.map { snapshot ->
                if (snapshot.id == threadId) {
                    snapshot.copy(title = trimmedName)
                } else {
                    snapshot
                }
            }
        }
    }

    override suspend fun archiveThread(
        threadId: String,
        unarchive: Boolean,
    ) {
        backingThreads.update { threads ->
            threads.map { snapshot ->
                if (snapshot.id == threadId) {
                    snapshot.copy(
                        syncState = if (unarchive) {
                            RemodexThreadSyncState.LIVE
                        } else {
                            RemodexThreadSyncState.ARCHIVED_LOCAL
                        },
                    )
                } else {
                    snapshot
                }
            }
        }
    }

    override suspend fun deleteThread(threadId: String) {
        backingThreads.update { threads ->
            threads.filterNot { snapshot -> snapshot.id == threadId }
        }
    }

    override suspend fun sendPrompt(
        threadId: String,
        prompt: String,
        runtimeConfig: RemodexRuntimeConfig,
        attachments: List<RemodexComposerAttachment>,
    ) {
        val now = System.currentTimeMillis()
        backingThreads.update { threads ->
            threads.map { snapshot ->
                if (snapshot.id != threadId) {
                    snapshot
                } else {
                    val nextOrderIndex = nextOrderIndex(snapshot)
                    val turnId = "turn-${UUID.randomUUID()}"
                    val reasoningMessageId = "reasoning-${UUID.randomUUID()}"
                    val assistantMessageId = "assistant-${UUID.randomUUID()}"
                    val normalizedPrompt = prompt.ifBlank {
                        when (attachments.size) {
                            0 -> "Shared a follow-up from Android."
                            1 -> "Shared 1 image from Android."
                            else -> "Shared ${attachments.size} images from Android."
                        }
                    }
                    snapshot.copy(
                        preview = normalizedPrompt,
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = now,
                        isRunning = true,
                        runtimeConfig = runtimeConfig,
                        timelineMutations = snapshot.timelineMutations + listOf(
                            TimelineMutation.Upsert(
                                timelineItem(
                                    id = "user-${UUID.randomUUID()}",
                                    speaker = ConversationSpeaker.USER,
                                    text = normalizedPrompt,
                                    turnId = turnId,
                                    deliveryState = RemodexMessageDeliveryState.PENDING,
                                    attachments = attachments.map { attachment ->
                                        RemodexConversationAttachment(
                                            id = attachment.id,
                                            uriString = attachment.uriString,
                                            displayName = attachment.displayName,
                                        )
                                    },
                                    orderIndex = nextOrderIndex,
                                ),
                            ),
                            TimelineMutation.ReasoningTextDelta(
                                messageId = reasoningMessageId,
                                turnId = turnId,
                                itemId = "reasoning-$turnId",
                                delta = "Using ${runtimeConfig.runtimeLabel.lowercase()} for this Android handoff.",
                                orderIndex = nextOrderIndex + 1,
                            ),
                            TimelineMutation.ActivityLine(
                                messageId = "tool-activity-$turnId",
                                turnId = turnId,
                                itemId = "tool-activity-$turnId",
                                line = if (attachments.isEmpty()) {
                                    "Streaming the local-first turn to the paired Mac bridge."
                                } else {
                                    "Streaming the local-first turn plus ${attachments.size} attachment(s) to the paired Mac bridge."
                                },
                                orderIndex = nextOrderIndex + 1,
                            ),
                            TimelineMutation.AssistantTextDelta(
                                messageId = assistantMessageId,
                                turnId = turnId,
                                itemId = "assistant-$turnId",
                                delta = "Started a new turn for \"$normalizedPrompt\".",
                                orderIndex = nextOrderIndex + 2,
                            ),
                        ),
                    )
                }
            }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
        }
    }

    override suspend fun startCodeReview(
        threadId: String,
        target: RemodexComposerReviewTarget,
        baseBranch: String?,
    ) {
        sendPrompt(
            threadId = threadId,
            prompt = when (target) {
                RemodexComposerReviewTarget.UNCOMMITTED_CHANGES -> "Review current changes"
                RemodexComposerReviewTarget.BASE_BRANCH -> {
                    val normalizedBaseBranch = baseBranch?.trim().orEmpty()
                    if (normalizedBaseBranch.isEmpty()) {
                        "Review against base branch"
                    } else {
                        "Review against base branch $normalizedBaseBranch"
                    }
                }
            },
            runtimeConfig = backingThreads.value.firstOrNull { it.id == threadId }?.runtimeConfig ?: RemodexRuntimeConfig(),
            attachments = emptyList(),
        )
    }

    override suspend fun forkThread(
        threadId: String,
        destination: RemodexComposerForkDestination,
        baseBranch: String?,
    ): ThreadSyncSnapshot? {
        val source = backingThreads.value.firstOrNull { it.id == threadId } ?: return null
        val now = System.currentTimeMillis()
        val nextProjectPath = when (destination) {
            RemodexComposerForkDestination.LOCAL -> source.projectPath
            RemodexComposerForkDestination.NEW_WORKTREE -> "${source.projectPath}-worktree"
        }
        val forked = ThreadSyncSnapshot(
            id = "thread-${UUID.randomUUID()}",
            title = "${source.title} (Fork)",
            preview = "Forked from ${source.title}.",
            projectPath = nextProjectPath,
            lastUpdatedLabel = "Updated just now",
            lastUpdatedEpochMs = now,
            isRunning = false,
            runtimeConfig = source.runtimeConfig,
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            timelineItem(
                                id = "fork-${UUID.randomUUID()}",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = if (destination == RemodexComposerForkDestination.NEW_WORKTREE) {
                                    "Forked this thread into a new worktree based on ${baseBranch ?: "main"}."
                                } else {
                                    "Forked this thread into a new local conversation."
                                },
                        orderIndex = 0,
                    ),
                ),
            ),
        )
        backingThreads.update { threads ->
            (listOf(forked) + threads).sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
        }
        gitStateByThreadId[forked.id] = seedGitState(forked)
        return forked
    }

    override suspend fun fuzzyFileSearch(
        threadId: String,
        query: String,
    ): List<RemodexFuzzyFileMatch> {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return emptyList()
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        val roots = listOf(
            "${snapshot.projectPath}/src/main/java/com/emanueledipietro/remodex/feature/turn/ConversationScreen.kt",
            "${snapshot.projectPath}/src/main/java/com/emanueledipietro/remodex/feature/appshell/AppViewModel.kt",
            "${snapshot.projectPath}/src/main/java/com/emanueledipietro/remodex/feature/threads/ThreadsScreen.kt",
            "${snapshot.projectPath}/src/main/java/com/emanueledipietro/remodex/feature/settings/SettingsScreen.kt",
        )
        return roots
            .map { path ->
                val fileName = path.substringAfterLast('/')
                RemodexFuzzyFileMatch(
                    root = snapshot.projectPath,
                    path = path,
                    fileName = fileName,
                    score = if (fileName.lowercase().contains(normalizedQuery)) 1.0 else 0.5,
                )
            }
            .filter { match ->
                match.fileName.lowercase().contains(normalizedQuery) ||
                    match.path.lowercase().contains(normalizedQuery)
            }
            .take(6)
    }

    override suspend fun listSkills(
        threadId: String,
        forceReload: Boolean,
    ): List<RemodexSkillMetadata> {
        return listOf(
            RemodexSkillMetadata(
                name = "android-ui-review",
                description = "Review Android UI parity before shipping.",
                path = "/skills/android-ui-review",
            ),
            RemodexSkillMetadata(
                name = "bridge-protocol-check",
                description = "Validate bridge JSON-RPC compatibility and fallbacks.",
                path = "/skills/bridge-protocol-check",
            ),
            RemodexSkillMetadata(
                name = "git-cleanup",
                description = "Summarize local repo state before git actions.",
                path = "/skills/git-cleanup",
            ),
        )
    }

    override suspend fun loadGitState(threadId: String): RemodexGitState {
        return gitStateByThreadId[threadId] ?: RemodexGitState()
    }

    override suspend fun loadGitDiff(threadId: String): RemodexGitRepoDiff {
        val sync = gitStateByThreadId[threadId]?.sync ?: return RemodexGitRepoDiff()
        val patch = buildString {
            sync.files.forEachIndexed { index, file ->
                if (index > 0) {
                    append('\n')
                }
                appendLine("diff --git a/${file.path} b/${file.path}")
                appendLine("index 1111111..2222222 100644")
                appendLine("--- a/${file.path}")
                appendLine("+++ b/${file.path}")
                appendLine("@@ -1 +1,2 @@")
                appendLine("-old")
                appendLine("+new")
                appendLine("+more")
            }
        }.trim()
        return RemodexGitRepoDiff(patch = patch)
    }

    override suspend fun checkoutGitBranch(
        threadId: String,
        branch: String,
    ): RemodexGitState {
        return updateGitState(threadId) { current ->
            current.copy(
                sync = current.sync?.copy(
                    currentBranch = branch,
                    trackingBranch = "origin/$branch",
                    state = "up_to_date",
                    aheadCount = 0,
                    behindCount = 0,
                ),
                branches = current.branches.copy(currentBranch = branch),
                lastActionMessage = "Switched to $branch.",
                errorMessage = null,
            )
        }
    }

    override suspend fun createGitBranch(
        threadId: String,
        branch: String,
    ): RemodexGitState {
        return updateGitState(threadId) { current ->
            current.copy(
                sync = current.sync?.copy(currentBranch = branch, trackingBranch = null, canPush = true),
                branches = current.branches.copy(
                    branches = (current.branches.branches + branch).distinct().sorted(),
                    currentBranch = branch,
                ),
                lastActionMessage = "Created branch $branch.",
                errorMessage = null,
            )
        }
    }

    override suspend fun createGitWorktree(
        threadId: String,
        name: String,
        baseBranch: String?,
    ): RemodexGitState {
        return updateGitState(threadId) { current ->
            current.copy(
                branches = current.branches.copy(
                    branches = (current.branches.branches + name).distinct().sorted(),
                    worktreePathByBranch = current.branches.worktreePathByBranch + (
                        name to "${current.sync?.repoRoot ?: current.branches.localCheckoutPath ?: ""}/worktrees/$name"
                    ),
                ),
                lastActionMessage = "Created worktree $name from ${baseBranch ?: current.branches.defaultBranch ?: "main"}.",
                errorMessage = null,
            )
        }
    }

    override suspend fun commitGitChanges(
        threadId: String,
        message: String?,
    ): RemodexGitState {
        return updateGitState(threadId) { current ->
            current.copy(
                sync = current.sync?.copy(
                    isDirty = false,
                    state = "ahead_only",
                    aheadCount = 1,
                    canPush = true,
                    files = emptyList(),
                    diffTotals = null,
                ),
                lastActionMessage = message?.trim()?.takeIf(String::isNotEmpty) ?: "Committed local changes.",
                errorMessage = null,
            )
        }
    }

    override suspend fun pullGitChanges(threadId: String): RemodexGitState {
        return updateGitState(threadId) { current ->
            current.copy(
                sync = current.sync?.copy(state = "up_to_date", behindCount = 0),
                lastActionMessage = "Pulled the latest remote changes.",
                errorMessage = null,
            )
        }
    }

    override suspend fun pushGitChanges(threadId: String): RemodexGitState {
        return updateGitState(threadId) { current ->
            current.copy(
                sync = current.sync?.copy(state = "up_to_date", aheadCount = 0, canPush = false),
                lastActionMessage = "Pushed the current branch.",
                errorMessage = null,
            )
        }
    }

    override suspend fun discardRuntimeChangesAndSync(threadId: String): RemodexGitState {
        return updateGitState(threadId) { current ->
            current.copy(
                sync = current.sync?.copy(
                    isDirty = false,
                    state = "up_to_date",
                    files = emptyList(),
                    diffTotals = null,
                    behindCount = 0,
                ),
                lastActionMessage = "Discarded local runtime changes and synced from remote.",
                errorMessage = null,
            )
        }
    }

    override suspend fun previewAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertPreviewResult {
        val normalizedPatch = RemodexUnifiedPatchParser.normalize(forwardPatch)
            ?: throw IllegalStateException("This response cannot be auto-reverted because no exact patch was captured.")
        val analysis = RemodexUnifiedPatchParser.analyze(normalizedPatch)
        return RemodexRevertPreviewResult(
            canRevert = analysis.fileChanges.isNotEmpty() && analysis.unsupportedReasons.isEmpty(),
            affectedFiles = analysis.fileChanges.map { fileChange -> fileChange.path },
            conflicts = emptyList(),
            unsupportedReasons = analysis.unsupportedReasons,
            stagedFiles = emptyList(),
        )
    }

    override suspend fun applyAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertApplyResult {
        val preview = previewAssistantRevert(threadId, forwardPatch)
        if (!preview.canRevert) {
            return RemodexRevertApplyResult(
                success = false,
                revertedFiles = emptyList(),
                conflicts = preview.conflicts,
                unsupportedReasons = preview.unsupportedReasons,
                stagedFiles = preview.stagedFiles,
                status = gitStateByThreadId[threadId]?.sync,
            )
        }

        val now = System.currentTimeMillis()
        backingThreads.update { threads ->
            threads.map { snapshot ->
                if (snapshot.id != threadId) {
                    snapshot
                } else {
                    snapshot.copy(
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = now,
                        timelineMutations = snapshot.timelineMutations + TimelineMutation.Upsert(
                            timelineItem(
                                id = "revert-${UUID.randomUUID()}",
                                speaker = ConversationSpeaker.SYSTEM,
                                text = "Reverted changes from this response.",
                                orderIndex = nextOrderIndex(snapshot),
                            ),
                        ),
                    )
                }
            }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
        }
        val updatedGitState = updateGitState(threadId) { current ->
            current.copy(
                sync = current.sync?.copy(
                    isDirty = false,
                    state = "up_to_date",
                    files = emptyList(),
                    diffTotals = null,
                ),
                lastActionMessage = "Reverted changes from this response.",
                errorMessage = null,
            )
        }
        return RemodexRevertApplyResult(
            success = true,
            revertedFiles = preview.affectedFiles,
            conflicts = emptyList(),
            unsupportedReasons = emptyList(),
            stagedFiles = emptyList(),
            status = updatedGitState.sync,
        )
    }

    override suspend fun stopTurn(threadId: String) {
        val now = System.currentTimeMillis()
        backingThreads.update { threads ->
            threads.map { snapshot ->
                if (snapshot.id != threadId || !snapshot.isRunning) {
                    snapshot
                } else {
                    val projectedItems = TurnTimelineReducer.reduce(snapshot.timelineMutations)
                    val completionMutations = projectedItems
                        .filter { item -> item.isStreaming }
                        .map { item -> TimelineMutation.Complete(messageId = item.id) }
                    val nextOrderIndex = (projectedItems.maxOfOrNull { item -> item.orderIndex } ?: -1L) + 1L
                    snapshot.copy(
                        preview = "Turn stopped on Android. You can send the next step when ready.",
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = now,
                        isRunning = false,
                        timelineMutations = snapshot.timelineMutations + completionMutations + TimelineMutation.Upsert(
                            timelineItem(
                                id = "activity-${UUID.randomUUID()}",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "Stopped the active turn from Android. Any queued follow-ups stay saved locally.",
                                orderIndex = nextOrderIndex,
                            ),
                        ),
                    )
                }
            }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
        }
    }

    private fun updateGitState(
        threadId: String,
        transform: (RemodexGitState) -> RemodexGitState,
    ): RemodexGitState {
        val current = gitStateByThreadId[threadId] ?: RemodexGitState()
        return transform(current).also { next -> gitStateByThreadId[threadId] = next }
    }
}

fun ThreadSyncSnapshot.toCachedThreadRecord(): CachedThreadRecord {
    return CachedThreadRecord(
        id = id,
        title = title,
        preview = preview,
        projectPath = projectPath,
        lastUpdatedLabel = lastUpdatedLabel,
        lastUpdatedEpochMs = lastUpdatedEpochMs,
        isRunning = isRunning,
        syncState = syncState,
        parentThreadId = parentThreadId,
        agentNickname = agentNickname,
        agentRole = agentRole,
        runtimeConfig = runtimeConfig,
        timelineItems = TurnTimelineReducer.reduceProjected(timelineMutations),
    )
}

private fun nextOrderIndex(snapshot: ThreadSyncSnapshot): Long {
    return (TurnTimelineReducer.reduce(snapshot.timelineMutations).maxOfOrNull { item -> item.orderIndex } ?: -1L) + 1L
}

private fun seededThreadSnapshots(): List<ThreadSyncSnapshot> {
    val availableModels = seededModelOptions()
    return listOf(
        ThreadSyncSnapshot(
            id = "thread-android-client",
            title = "Android client bootstrap",
            preview = "Mirror the iOS shell, but make the UI feel native on Android.",
            projectPath = "/Users/emanueledipietro/Developer/remodex/CodexMobileAndroid",
            lastUpdatedLabel = "Updated 2m ago",
            lastUpdatedEpochMs = 1_713_000_000_000,
            isRunning = true,
            runtimeConfig = RemodexRuntimeConfig(
                availableModels = availableModels,
                selectedModelId = "gpt-5.4",
                planningMode = com.emanueledipietro.remodex.model.RemodexPlanningMode.PLAN,
                reasoningEffort = "medium",
                accessMode = RemodexAccessMode.ON_REQUEST,
            ).normalizeSelections(),
            timelineMutations = listOf(
                TimelineMutation.Upsert(
                    timelineItem(
                        id = "android-user-1",
                        speaker = ConversationSpeaker.USER,
                        text = "Reference the iOS interface, but use Android best practices where needed.",
                        turnId = "turn-android-client",
                        orderIndex = 0,
                    ),
                ),
                TimelineMutation.ReasoningTextDelta(
                    messageId = "android-reasoning-1",
                    turnId = "turn-android-client",
                    itemId = "reasoning-android-client",
                    delta = "Thinking...",
                    orderIndex = 1,
                ),
                TimelineMutation.ActivityLine(
                    messageId = "android-tool-activity-1",
                    turnId = "turn-android-client",
                    itemId = "tool-activity-android-client",
                    line = "Comparing the iOS reducer with Android models.",
                    orderIndex = 1,
                ),
                TimelineMutation.ReasoningTextDelta(
                    messageId = "android-reasoning-1",
                    turnId = "turn-android-client",
                    itemId = "reasoning-android-client",
                    delta = "Locking selection persistence and a Room-backed cache.",
                    orderIndex = 1,
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "android-assistant-1",
                    turnId = "turn-android-client",
                    itemId = "assistant-android-client",
                    delta = "Starting with local-first thread cache, selection persistence, and a reducer-backed conversation timeline.",
                    orderIndex = 2,
                ),
            ),
        ),
        ThreadSyncSnapshot(
            id = "thread-reconnect",
            title = "Trusted reconnect edge cases",
            preview = "Keep QR recovery close without assuming a hosted relay.",
            projectPath = "/Users/emanueledipietro/Developer/remodex/phodex-bridge",
            lastUpdatedLabel = "Updated 28m ago",
            lastUpdatedEpochMs = 1_712_999_500_000,
            isRunning = false,
            runtimeConfig = RemodexRuntimeConfig(
                availableModels = availableModels,
                selectedModelId = "gpt-5.4",
                reasoningEffort = "low",
                accessMode = RemodexAccessMode.ON_REQUEST,
            ).normalizeSelections(),
            timelineMutations = listOf(
                TimelineMutation.Upsert(
                    timelineItem(
                        id = "reconnect-system-1",
                        speaker = ConversationSpeaker.SYSTEM,
                        kind = ConversationItemKind.COMMAND_EXECUTION,
                        text = "Saved pairing is available for this Mac.",
                        supportingText = "Recovery stays visible until trust is valid again.",
                        orderIndex = 0,
                    ),
                ),
                TimelineMutation.Upsert(
                    timelineItem(
                        id = "reconnect-assistant-1",
                        speaker = ConversationSpeaker.ASSISTANT,
                        text = "Recovery should stay visible when trust is stale or the bridge session rotates.",
                        orderIndex = 1,
                    ),
                ),
            ),
        ),
        ThreadSyncSnapshot(
            id = "thread-notifications",
            title = "Android notifications follow-up",
            preview = "Completion and attention-needed alerts still need Android plumbing.",
            projectPath = "/Users/emanueledipietro/Developer/remodex/CodexMobileAndroid",
            lastUpdatedLabel = "Updated yesterday",
            lastUpdatedEpochMs = 1_712_913_600_000,
            isRunning = false,
            runtimeConfig = RemodexRuntimeConfig(
                availableModels = availableModels,
                selectedModelId = "gpt-5.4",
                planningMode = com.emanueledipietro.remodex.model.RemodexPlanningMode.AUTO,
                reasoningEffort = "xhigh",
                accessMode = RemodexAccessMode.FULL_ACCESS,
            ).normalizeSelections(),
            timelineMutations = listOf(
                TimelineMutation.Upsert(
                    timelineItem(
                        id = "notifications-user-1",
                        speaker = ConversationSpeaker.USER,
                        text = "What do we need for notification channels?",
                        orderIndex = 0,
                    ),
                ),
                TimelineMutation.Upsert(
                    timelineItem(
                        id = "notifications-assistant-1",
                        speaker = ConversationSpeaker.ASSISTANT,
                        text = "First we need the app shell, thread cache, and timeline state to route users back into a thread.",
                        orderIndex = 1,
                    ),
                ),
            ),
        ),
    )
}

private fun seededModelOptions(): List<RemodexModelOption> {
    return listOf(
        RemodexModelOption(
            id = "gpt-5.4",
            model = "gpt-5.4",
            displayName = "GPT-5.4",
            isDefault = true,
            supportedReasoningEfforts = listOf(
                RemodexReasoningEffortOption(reasoningEffort = "low", description = "Low"),
                RemodexReasoningEffortOption(reasoningEffort = "medium", description = "Medium"),
                RemodexReasoningEffortOption(reasoningEffort = "high", description = "High"),
                RemodexReasoningEffortOption(reasoningEffort = "xhigh", description = "Extra High"),
            ),
            defaultReasoningEffort = "medium",
        ),
        RemodexModelOption(
            id = "gpt-5.3-codex",
            model = "gpt-5.3-codex",
            displayName = "GPT-5.3-Codex",
            supportedReasoningEfforts = listOf(
                RemodexReasoningEffortOption(reasoningEffort = "low", description = "Low"),
                RemodexReasoningEffortOption(reasoningEffort = "medium", description = "Medium"),
                RemodexReasoningEffortOption(reasoningEffort = "high", description = "High"),
            ),
            defaultReasoningEffort = "medium",
        ),
    )
}

private fun seedGitState(snapshot: ThreadSyncSnapshot): RemodexGitState {
    val repoRoot = snapshot.projectPath.ifBlank { "/tmp/remodex" }
    val currentBranch = when {
        snapshot.projectPath.contains("bridge", ignoreCase = true) -> "trusted-reconnect"
        snapshot.projectPath.contains("Android", ignoreCase = true) -> "android-parity"
        else -> "main"
    }
    val repoName = File(repoRoot).name.ifBlank { "remodex" }
    return RemodexGitState(
        sync = RemodexGitRepoSync(
            repoRoot = repoRoot,
            currentBranch = currentBranch,
            trackingBranch = "origin/$currentBranch",
            isDirty = snapshot.isRunning,
            aheadCount = if (snapshot.isRunning) 1 else 0,
            behindCount = 0,
            localOnlyCommitCount = if (snapshot.isRunning) 1 else 0,
            state = if (snapshot.isRunning) "dirty" else "up_to_date",
            canPush = snapshot.isRunning,
            isPublishedToRemote = true,
            files = listOf(
                RemodexGitChangedFile(
                    path = "$repoName/src/main/java/${snapshot.title.lowercase().replace(' ', '-')}.kt",
                    status = if (snapshot.isRunning) "M" else "A",
                ),
            ),
            diffTotals = RemodexGitDiffTotals(additions = 12, deletions = 3),
        ),
        branches = RemodexGitBranches(
            branches = listOf("main", currentBranch, "release/mobile-parity").distinct(),
            branchesCheckedOutElsewhere = if (snapshot.projectPath.contains("bridge", ignoreCase = true)) {
                setOf("release/mobile-parity")
            } else {
                emptySet()
            },
            worktreePathByBranch = mapOf(
                "release/mobile-parity" to "$repoRoot/worktrees/release-mobile-parity",
            ),
            localCheckoutPath = repoRoot,
            currentBranch = currentBranch,
            defaultBranch = "main",
        ),
    )
}
