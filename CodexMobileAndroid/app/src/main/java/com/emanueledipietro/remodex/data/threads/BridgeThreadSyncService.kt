package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.data.connection.RpcError
import com.emanueledipietro.remodex.data.connection.RpcMessage
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.firstArray
import com.emanueledipietro.remodex.data.connection.firstBoolean
import com.emanueledipietro.remodex.data.connection.firstDouble
import com.emanueledipietro.remodex.data.connection.firstInt
import com.emanueledipietro.remodex.data.connection.firstObject
import com.emanueledipietro.remodex.data.connection.firstString
import com.emanueledipietro.remodex.data.connection.firstValue
import com.emanueledipietro.remodex.data.connection.jsonArrayOrNull
import com.emanueledipietro.remodex.data.connection.jsonObjectOrNull
import com.emanueledipietro.remodex.data.connection.stringOrNull
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetSource
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
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
import com.emanueledipietro.remodex.model.RemodexGitWorktreeResult
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanState
import com.emanueledipietro.remodex.model.RemodexPlanStep
import com.emanueledipietro.remodex.model.RemodexPlanStepStatus
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexReasoningEffortOption
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertConflict
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexAccessMode
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
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexUnifiedPatchParser
import com.emanueledipietro.remodex.feature.turn.TurnTimelineReducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class BridgeThreadSyncService(
    private val secureConnectionCoordinator: SecureConnectionCoordinator,
    private val scope: CoroutineScope,
    private val appVersionName: String = "1.0",
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : ThreadSyncService, ThreadCommandService, ThreadHydrationService {
    private val backingAvailableModels = MutableStateFlow<List<RemodexModelOption>>(emptyList())
    private val backingThreads = MutableStateFlow<List<ThreadSyncSnapshot>>(emptyList())
    private val backingCommandExecutionDetails =
        MutableStateFlow<Map<String, RemodexCommandExecutionDetails>>(emptyMap())
    private val activeTurnIdByThread = mutableMapOf<String, String>()
    private val threadIdByTurnId = mutableMapOf<String, String>()
    private var initializedAttempt: Int? = null
    private var supportsServiceTier = true

    override val availableModels: StateFlow<List<RemodexModelOption>> = backingAvailableModels
    override val threads: StateFlow<List<ThreadSyncSnapshot>> = backingThreads
    override val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>> =
        backingCommandExecutionDetails

    init {
        scope.launch {
            secureConnectionCoordinator.state.collectLatest { snapshot ->
                if (snapshot.secureState == SecureConnectionState.ENCRYPTED) {
                    if (initializedAttempt != snapshot.attempt) {
                        initializedAttempt = snapshot.attempt
                        supportsServiceTier = true
                        initializeSession()
                        refreshAvailableModels()
                        refreshThreads()
                    }
                } else {
                    initializedAttempt = null
                    supportsServiceTier = true
                    activeTurnIdByThread.clear()
                    threadIdByTurnId.clear()
                    backingCommandExecutionDetails.value = emptyMap()
                }
            }
        }

        scope.launch {
            secureConnectionCoordinator.notifications.collectLatest(::handleNotification)
        }
    }

    override suspend fun refreshThreads() {
        if (!isConnected()) {
            return
        }

        val activeThreads = listThreads(archived = false)
        val archivedThreads = listThreads(archived = true)
        val existingById = backingThreads.value.associateBy(ThreadSyncSnapshot::id)
        val merged = (activeThreads + archivedThreads)
            .map { incoming ->
                val existing = existingById[incoming.id]
                incoming.copy(
                    isRunning = activeTurnIdByThread.containsKey(incoming.id) || existing?.isRunning == true,
                    timelineMutations = existing?.timelineMutations ?: incoming.timelineMutations,
                    runtimeConfig = mergeRuntimeConfig(
                        existing = existing?.runtimeConfig,
                        incoming = incoming.runtimeConfig,
                    ),
                )
            }
            .sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
        backingThreads.value = merged
    }

    override suspend fun hydrateThread(threadId: String) {
        if (!isConnected() || threadId.isBlank()) {
            return
        }

        val response = runThreadRead(threadId) ?: return
        val resultObject = response.result?.jsonObjectOrNull ?: return
        val threadObject = resultObject.firstObject("thread") ?: return
        indexTurnIds(threadId = threadId, threadObject = threadObject)
        val historyItems = decodeHistoryItems(threadId = threadId, threadObject = threadObject)
        val refreshedSnapshot = parseThreadSnapshot(
            threadObject = threadObject,
            syncState = currentSyncState(threadId),
            existing = backingThreads.value.firstOrNull { it.id == threadId },
        )?.copy(
            timelineMutations = historyItems,
            isRunning = resolveInterruptibleTurnId(threadObject)?.also { activeTurnIdByThread[threadId] = it } != null
                || backingThreads.value.firstOrNull { it.id == threadId }?.isRunning == true,
        ) ?: return

        backingThreads.value = backingThreads.value
            .map { snapshot -> if (snapshot.id == threadId) refreshedSnapshot else snapshot }
            .ifEmpty { listOf(refreshedSnapshot) }
            .sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
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
        hydrateThread(snapshot.id)
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
            )
        }
        secureConnectionCoordinator.sendRequest(
            method = "thread/name/set",
            params = buildJsonObject {
                put("thread_id", JsonPrimitive(threadId))
                put("name", JsonPrimitive(trimmedName))
            },
        )
    }

    override suspend fun archiveThread(
        threadId: String,
        unarchive: Boolean,
    ) {
        if (!isConnected()) {
            return
        }
        secureConnectionCoordinator.sendRequest(
            method = if (unarchive) "thread/unarchive" else "thread/archive",
            params = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
            },
        )
        updateThread(threadId) { snapshot ->
            snapshot.copy(
                syncState = if (unarchive) {
                    RemodexThreadSyncState.LIVE
                } else {
                    RemodexThreadSyncState.ARCHIVED_LOCAL
                },
            )
        }
    }

    override suspend fun deleteThread(threadId: String) {
        if (!isConnected()) {
            return
        }
        secureConnectionCoordinator.sendRequest(
            method = "thread/archive",
            params = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
            },
        )
        activeTurnIdByThread.remove(threadId)
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

        optimisticAppendUserMessage(
            threadId = threadId,
            prompt = trimmedPrompt,
            attachments = attachments,
            runtimeConfig = runtimeConfig,
        )

        val response = sendRequestWithServiceTierFallback(
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
                )
            },
        )
        val turnId = extractTurnId(response.result)
        if (turnId != null) {
            activeTurnIdByThread[threadId] = turnId
            threadIdByTurnId[turnId] = threadId
        }
        refreshThreads()
        hydrateThread(threadId)
    }

    override suspend fun startCodeReview(
        threadId: String,
        target: RemodexComposerReviewTarget,
        baseBranch: String?,
    ) {
        if (!isConnected()) {
            return
        }

        val prompt = when (target) {
            RemodexComposerReviewTarget.UNCOMMITTED_CHANGES -> "Review current changes"
            RemodexComposerReviewTarget.BASE_BRANCH -> {
                val trimmedBaseBranch = baseBranch?.trim().orEmpty()
                if (trimmedBaseBranch.isEmpty()) {
                    "Review against base branch"
                } else {
                    "Review against base branch $trimmedBaseBranch"
                }
            }
        }
        val runtimeConfig = backingThreads.value.firstOrNull { it.id == threadId }?.runtimeConfig
            ?: RemodexRuntimeConfig()
        optimisticAppendUserMessage(
            threadId = threadId,
            prompt = prompt,
            attachments = emptyList(),
            runtimeConfig = runtimeConfig,
        )
        val targetObject = when (target) {
            RemodexComposerReviewTarget.UNCOMMITTED_CHANGES -> buildJsonObject {
                put("type", JsonPrimitive("uncommittedChanges"))
            }

            RemodexComposerReviewTarget.BASE_BRANCH -> buildJsonObject {
                put("type", JsonPrimitive("baseBranch"))
                baseBranch?.trim()?.takeIf(String::isNotEmpty)?.let { put("branch", JsonPrimitive(it)) }
            }
        }
        val response = sendRequestWithApprovalPolicyFallback(
            method = "review/start",
            baseParams = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
                put("delivery", JsonPrimitive("inline"))
                put("target", targetObject)
            },
            accessMode = runtimeConfig.accessMode,
        )
        extractTurnId(response.result)?.let { turnId ->
            activeTurnIdByThread[threadId] = turnId
            threadIdByTurnId[turnId] = threadId
        }
        refreshThreads()
        hydrateThread(threadId)
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
            RemodexComposerForkDestination.LOCAL -> sourceThread.projectPath
            RemodexComposerForkDestination.NEW_WORKTREE -> {
                val defaultBranch = loadGitBranches(threadId).defaultBranch
                val branchName = defaultForkBranchName(sourceThread.title)
                createWorktree(
                    threadId = threadId,
                    name = branchName,
                    baseBranch = baseBranch?.trim()?.takeIf(String::isNotEmpty) ?: defaultBranch,
                ).worktreePath
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
        return RemodexGitState(
            sync = loadGitSync(threadId),
            branches = loadGitBranches(threadId),
        )
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
    ): RemodexGitState {
        val worktreeResult = createWorktree(threadId, name, baseBranch)
        return loadGitState(threadId).copy(
            lastActionMessage = if (worktreeResult.alreadyExisted) {
                "Opened existing worktree for ${worktreeResult.branch}."
            } else {
                "Created worktree ${worktreeResult.branch}."
            },
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
        activeTurnIdByThread.remove(threadId)
        backingThreads.value = backingThreads.value.map { snapshot ->
            if (snapshot.id == threadId) {
                snapshot.copy(isRunning = false)
            } else {
                snapshot
            }
        }
        refreshThreads()
        hydrateThread(threadId)
    }

    private suspend fun initializeSession() {
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

        runCatching {
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
        }.getOrThrow()

        secureConnectionCoordinator.sendNotification(
            method = "initialized",
            params = null,
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
        }.getOrNull()
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
    ): RemodexGitWorktreeResult {
        val resultObject = runGitRequest(
            threadId = threadId,
            method = "git/createWorktree",
            params = buildJsonObject {
                put("name", JsonPrimitive(name))
                baseBranch?.trim()?.takeIf(String::isNotEmpty)?.let { put("baseBranch", JsonPrimitive(it)) }
                put("changeTransfer", JsonPrimitive("copy"))
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
        var includeServiceTier = shouldIncludeServiceTier(runtimeConfig.serviceTier)
        var includeSandbox = true
        var useMinimalParams = false
        while (true) {
            val baseParams = buildJsonObject {
                put("threadId", JsonPrimitive(threadId))
                if (!useMinimalParams) {
                    projectPath?.trim()?.takeIf(String::isNotEmpty)?.let { put("cwd", JsonPrimitive(it)) }
                    runtimeConfig.selectedModelId?.takeIf(String::isNotBlank)?.let { put("model", JsonPrimitive(it)) }
                    if (includeServiceTier) {
                        runtimeConfig.serviceTier?.let { put("serviceTier", JsonPrimitive(it.wireValue)) }
                    }
                    if (includeSandbox) {
                        put("sandbox", JsonPrimitive(runtimeConfig.accessMode.approvalPolicyCandidates.first()))
                    }
                }
            }
            try {
                val response = sendRequestWithApprovalPolicyFallback(
                    method = "thread/fork",
                    baseParams = baseParams,
                    accessMode = runtimeConfig.accessMode,
                )
                val threadObject = response.result?.jsonObjectOrNull?.firstObject("thread") ?: return null
                return parseThreadSnapshot(
                    threadObject = threadObject,
                    syncState = RemodexThreadSyncState.LIVE,
                    existing = null,
                )
            } catch (error: Throwable) {
                val message = error.message.orEmpty().lowercase(Locale.ROOT)
                when {
                    consumeUnsupportedServiceTier(error, includeServiceTier) -> {
                        includeServiceTier = false
                    }

                    includeSandbox && (message.contains("sandbox") || message.contains("approvalpolicy")) -> {
                        includeSandbox = false
                    }

                    !useMinimalParams && (message.contains("unsupported") || message.contains("unknown field")) -> {
                        useMinimalParams = true
                    }

                    else -> throw error
                }
            }
        }
    }

    private suspend fun runGitRequest(
        threadId: String,
        method: String,
        params: JsonObject = buildJsonObject {},
    ): RpcMessage {
        val projectPath = backingThreads.value.firstOrNull { it.id == threadId }?.projectPath?.trim().orEmpty()
        require(projectPath.isNotEmpty()) { "Missing local working directory for $method." }
        return secureConnectionCoordinator.sendRequest(
            method = method,
            params = JsonObject(params + ("cwd" to JsonPrimitive(projectPath))),
        )
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
        for (policy in accessMode.approvalPolicyCandidates) {
            val params = JsonObject(baseParams + ("approvalPolicy" to JsonPrimitive(policy)))
            try {
                return secureConnectionCoordinator.sendRequest(method = method, params = params)
            } catch (error: Throwable) {
                lastError = error
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

    private fun consumeUnsupportedServiceTier(
        error: Throwable,
        includeServiceTier: Boolean,
    ): Boolean {
        if (!includeServiceTier || !shouldRetryWithoutServiceTier(error)) {
            return false
        }
        supportsServiceTier = false
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

    private suspend fun handleNotification(message: RpcMessage) {
        val method = message.method?.trim().orEmpty()
        val paramsObject = message.params?.jsonObjectOrNull
        when (method) {
            "thread/name/updated" -> refreshThreads()
            "turn/started" -> paramsObject?.let(::handleTurnStartedNotification)
            "turn/completed" -> paramsObject?.let(::handleTurnCompletedNotification)
            "thread/status/changed" -> paramsObject?.let(::handleThreadStatusChangedNotification)

            "item/agentMessage/delta",
            "codex/event/agent_message_content_delta",
            "codex/event/agent_message_delta" -> paramsObject?.let(::appendAssistantDelta)

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
            }
        }
    }

    private fun handleTurnStartedNotification(paramsObject: JsonObject) {
        val threadId = resolveThreadId(paramsObject)
        val turnId = extractTurnIdForTurnLifecycleEvent(paramsObject)
        if (threadId != null && turnId != null) {
            activeTurnIdByThread[threadId] = turnId
            threadIdByTurnId[turnId] = threadId
            confirmLatestPendingUserMessage(threadId = threadId, turnId = turnId)
        }
        threadId?.let { id ->
            touchThread(threadId = id, isRunning = true)
        }
    }

    private fun handleTurnCompletedNotification(paramsObject: JsonObject) {
        val turnId = extractTurnIdForTurnLifecycleEvent(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        if (turnId != null) {
            threadIdByTurnId[turnId] = threadId
        }
        activeTurnIdByThread.remove(threadId)
        completeStreamingItemsForThread(threadId = threadId, turnId = turnId)
        touchThread(threadId = threadId, isRunning = false)
        scope.launch {
            refreshThreads()
            hydrateThread(threadId)
        }
    }

    private fun handleThreadStatusChangedNotification(paramsObject: JsonObject) {
        val threadId = resolveThreadId(paramsObject) ?: return
        val eventObject = envelopeEventObject(paramsObject)
        val statusObject = paramsObject.firstObject("status")
            ?: eventObject?.firstObject("status")
            ?: paramsObject.firstObject("event")?.firstObject("status")
        val normalizedStatus = normalizeStatus(
            statusObject?.firstString("type", "statusType", "status_type")
                ?: paramsObject.firstString("status")
                ?: eventObject?.firstString("status")
                ?: "",
        )
        when {
            normalizedStatus == "running"
                || normalizedStatus == "active"
                || normalizedStatus == "processing"
                || normalizedStatus == "inprogress"
                || normalizedStatus == "started"
                || normalizedStatus == "pending" -> {
                touchThread(threadId = threadId, isRunning = true)
            }

            normalizedStatus == "idle"
                || normalizedStatus == "notloaded"
                || normalizedStatus == "completed"
                || normalizedStatus == "done"
                || normalizedStatus == "finished"
                || normalizedStatus == "stopped"
                || normalizedStatus == "systemerror" -> {
                if (activeTurnIdByThread[threadId] != null || hasStreamingMessage(threadId)) {
                    return
                }
                activeTurnIdByThread.remove(threadId)
                touchThread(threadId = threadId, isRunning = false)
            }
        }
    }

    private fun appendAssistantDelta(paramsObject: JsonObject) {
        val delta = extractTextDelta(paramsObject)
        if (delta.isBlank()) {
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
            fallbackPrefix = "assistant",
        )
        appendTimelineMutation(
            threadId = threadId,
            mutation = TimelineMutation.AssistantTextDelta(
                messageId = messageId,
                turnId = resolvedTurnId.orEmpty(),
                itemId = itemId,
                delta = delta,
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = messageId,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                ),
            ),
            isRunning = true,
        )
    }

    private fun appendReasoningDelta(paramsObject: JsonObject) {
        val delta = extractTextDelta(paramsObject)
        if (delta.isBlank()) {
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
                ),
            ),
            isRunning = true,
        )
    }

    private fun appendCommandExecutionDelta(paramsObject: JsonObject) {
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
        val itemObject = extractIncomingItemObject(paramsObject, eventObject) ?: return
        val itemType = normalizeItemType(itemObject.firstString("type").orEmpty())
        if (itemType.isEmpty()) {
            return
        }
        if (isAssistantHistoryItem(itemType, itemObject)) {
            handleAssistantLifecycle(
                paramsObject = paramsObject,
                itemObject = itemObject,
                isCompleted = isCompleted,
            )
            return
        }
        handleStructuredItemLifecycle(
            itemObject = itemObject,
            paramsObject = paramsObject,
            itemType = itemType,
            isCompleted = isCompleted,
        )
    }

    private fun handleAssistantLifecycle(
        paramsObject: JsonObject,
        itemObject: JsonObject,
        isCompleted: Boolean,
    ) {
        val eventObject = envelopeEventObject(paramsObject)
        val turnId = extractTurnId(paramsObject)
        val threadId = resolveThreadId(paramsObject, turnIdHint = turnId) ?: return
        val resolvedTurnId = turnId ?: activeTurnIdByThread[threadId]
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        val itemId = extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject)
        val messageId = streamingMessageId(
            itemId = itemId,
            turnId = resolvedTurnId,
            fallbackPrefix = "assistant",
        )
        val body = decodeItemText(itemObject)
        if (!isCompleted) {
            if (body.isBlank()) {
                return
            }
            upsertStreamingItem(
                threadId = threadId,
                item = timelineItem(
                    id = messageId,
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = body,
                    kind = ConversationItemKind.CHAT,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    isStreaming = true,
                    orderIndex = resolveOrderIndex(
                        threadId = threadId,
                        messageId = messageId,
                        turnId = resolvedTurnId,
                        itemId = itemId,
                        speaker = ConversationSpeaker.ASSISTANT,
                        kind = ConversationItemKind.CHAT,
                    ),
                ),
                isRunning = true,
            )
            return
        }

        if (body.isNotBlank()) {
            upsertStreamingItem(
                threadId = threadId,
                item = timelineItem(
                    id = messageId,
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = body,
                    kind = ConversationItemKind.CHAT,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    isStreaming = false,
                    orderIndex = resolveOrderIndex(
                        threadId = threadId,
                        messageId = messageId,
                        turnId = resolvedTurnId,
                        itemId = itemId,
                        speaker = ConversationSpeaker.ASSISTANT,
                        kind = ConversationItemKind.CHAT,
                    ),
                    assistantChangeSet = projectedTimelineItem(
                        threadId = threadId,
                        messageId = messageId,
                        turnId = resolvedTurnId,
                        itemId = itemId,
                        speaker = ConversationSpeaker.ASSISTANT,
                        kind = ConversationItemKind.CHAT,
                    )?.assistantChangeSet,
                ),
            )
        } else {
            appendTimelineMutation(
                threadId = threadId,
                mutation = TimelineMutation.Complete(messageId = messageId),
            )
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
        if (resolvedTurnId != null) {
            threadIdByTurnId[resolvedTurnId] = threadId
        }
        val itemId = extractItemId(paramsObject = paramsObject, eventObject = eventObject, itemObject = itemObject)

        val kind: ConversationItemKind
        val body: String
        val planState: RemodexPlanState?
        val subagentAction: RemodexSubagentAction?
        when {
            itemType == "reasoning" -> {
                kind = ConversationItemKind.REASONING
                body = decodeIncomingReasoningBody(itemObject)
                planState = null
                subagentAction = null
            }

            itemType == "filechange" || itemType == "diff" -> {
                kind = ConversationItemKind.FILE_CHANGE
                body = decodeStructuredLifecycleBody(
                    itemObject = itemObject,
                    inProgressFallback = "Updating files...",
                    completedFallback = "Updated files.",
                    isCompleted = isCompleted,
                )
                planState = null
                subagentAction = null
            }

            itemType == "toolcall" -> {
                if (isLikelyFileChangeToolCall(itemObject, decodeItemText(itemObject))) {
                    kind = ConversationItemKind.FILE_CHANGE
                    body = decodeStructuredLifecycleBody(
                        itemObject = itemObject,
                        inProgressFallback = "Updating files...",
                        completedFallback = "Updated files.",
                        isCompleted = isCompleted,
                    )
                } else {
                    kind = ConversationItemKind.TOOL_ACTIVITY
                    body = decodeToolCallActivityBody(
                        itemObject = itemObject,
                        isCompleted = isCompleted,
                    )
                }
                planState = null
                subagentAction = null
            }

            itemType == "commandexecution" || itemType == "enteredreviewmode" || itemType == "contextcompaction" -> {
                kind = ConversationItemKind.COMMAND_EXECUTION
                body = when (itemType) {
                    "enteredreviewmode" -> "Reviewing changes..."
                    "contextcompaction" -> if (isCompleted) "Context compacted" else "Compacting context..."
                    else -> decodeCommandExecutionStatusText(
                        payloadObject = itemObject,
                        isCompleted = isCompleted,
                    )
                }
                planState = null
                subagentAction = null
            }

            itemType == "plan" -> {
                kind = ConversationItemKind.PLAN
                body = decodePlanItemText(itemObject).ifBlank {
                    if (isCompleted) "Plan updated." else "Planning..."
                }
                planState = decodeHistoryPlanState(itemObject)
                subagentAction = null
            }

            else -> {
                kind = ConversationItemKind.SUBAGENT_ACTION
                body = ""
                planState = null
                subagentAction = decodeSubagentAction(itemObject)
            }
        }

        val messageId = streamingMessageId(
            itemId = itemId,
            turnId = resolvedTurnId,
            fallbackPrefix = kind.name.lowercase(Locale.ROOT),
        )
        val existingItem = projectedTimelineItem(
            threadId = threadId,
            messageId = messageId,
            turnId = resolvedTurnId,
            itemId = itemId,
            speaker = ConversationSpeaker.SYSTEM,
            kind = kind,
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
            appendTimelineMutation(
                threadId = threadId,
                mutation = TimelineMutation.Complete(messageId = messageId),
            )
            return true
        }
        if (subagentAction == null && body.isBlank() && isCompleted) {
            appendTimelineMutation(
                threadId = threadId,
                mutation = TimelineMutation.Complete(messageId = messageId),
            )
            return true
        }
        upsertStreamingItem(
            threadId = threadId,
            item = timelineItem(
                id = messageId,
                speaker = ConversationSpeaker.SYSTEM,
                text = subagentAction?.summaryText ?: body.ifBlank { kind.name.replace('_', ' ') },
                kind = kind,
                turnId = resolvedTurnId,
                itemId = itemId,
                isStreaming = !isCompleted,
                planState = planState,
                subagentAction = subagentAction,
                orderIndex = resolveOrderIndex(
                    threadId = threadId,
                    messageId = messageId,
                    turnId = resolvedTurnId,
                    itemId = itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = kind,
                ),
                assistantChangeSet = existingItem?.assistantChangeSet,
            ),
            isRunning = !isCompleted || activeTurnIdByThread[threadId] != null,
        )
        return true
    }

    private fun appendSystemTextDelta(
        paramsObject: JsonObject,
        kind: ConversationItemKind,
        fallbackPrefix: String,
    ) {
        val delta = extractTextDelta(paramsObject)
        if (delta.isBlank()) {
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
        val existingItem = projectedTimelineItem(
            threadId = context.threadId,
            messageId = messageId,
            turnId = context.turnId,
            itemId = context.itemId,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
        )
        if (onlyIfMissing && existingItem != null) {
            return
        }
        val statusText = commandExecutionStatusText(
            decodeCommandExecutionRunState(
                payloadObject = payloadObject,
                paramsObject = paramsObject,
                isCompleted = isCompleted,
            ),
        )
        if (statusText.isBlank()) {
            return
        }
        upsertStreamingItem(
            threadId = context.threadId,
            item = timelineItem(
                id = messageId,
                speaker = ConversationSpeaker.SYSTEM,
                text = statusText,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                turnId = context.turnId,
                itemId = context.itemId,
                isStreaming = !isCompleted,
                orderIndex = resolveOrderIndex(
                    threadId = context.threadId,
                    messageId = messageId,
                    turnId = context.turnId,
                    itemId = context.itemId,
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.COMMAND_EXECUTION,
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
                    isRunning = isRunning ?: snapshot.isRunning,
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
                snapshot.copy(
                    timelineMutations = nextMutations,
                    preview = if (mutationAffectsThreadPreview(mutation)) {
                        derivePreview(snapshot = snapshot, nextMutations = nextMutations)
                    } else {
                        snapshot.preview
                    },
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                    isRunning = isRunning ?: snapshot.isRunning,
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
    ): Long {
        val snapshot = backingThreads.value.firstOrNull { it.id == threadId } ?: return 0L
        return projectedTimelineItem(
            threadId = threadId,
            messageId = messageId,
            turnId = turnId,
            itemId = itemId,
            speaker = speaker,
            kind = kind,
        )?.orderIndex ?: nextOrderIndex(snapshot)
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
            item.id == messageId
                || (item.itemId != null && itemId != null && item.itemId == itemId)
                || (
                    item.itemId == null &&
                        itemId == null &&
                        item.turnId == turnId &&
                        item.kind == kind &&
                        item.speaker == speaker
                    )
        }
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

    private fun mutationAffectsThreadPreview(mutation: TimelineMutation): Boolean {
        return mutationAffectsThreadPreviewValue(mutation)
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
                )
            }
        }.sortedByDescending(ThreadSyncSnapshot::lastUpdatedEpochMs)
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
            || isSubagentHistoryItemType(itemType)
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
                    value.firstValue("content"),
                    value.firstValue("output"),
                    value.firstValue("delta"),
                ).flatMap(::decodeStringParts)
            }
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
        if (itemObject.firstValue(
                "changes",
                "file_changes",
                "fileChanges",
                "files",
                "diff",
                "patch",
            ) != null
        ) {
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

    private fun isLikelyIncomingItemPayload(objectValue: JsonObject): Boolean {
        val normalizedType = normalizeItemType(objectValue.firstString("type").orEmpty())
        if (normalizedType.isEmpty()) {
            return false
        }
        return objectValue.firstValue("content", "status", "output") != null
            || objectValue.firstValue("changes", "files", "diff", "patch") != null
            || objectValue.firstValue("result", "payload", "data") != null
    }

    private fun extractItemId(
        paramsObject: JsonObject?,
        eventObject: JsonObject?,
        itemObject: JsonObject? = null,
    ): String? {
        return itemObject?.firstString("id", "call_id", "callId")
            ?: paramsObject?.firstString("itemId", "item_id", "call_id", "callId", "id")
            ?: paramsObject?.firstObject("item")?.firstString("id")
            ?: eventObject?.firstString("itemId", "item_id", "call_id", "callId")
            ?: eventObject?.firstObject("item")?.firstString("id")
    }

    private fun extractTextDelta(paramsObject: JsonObject): String {
        val eventObject = envelopeEventObject(paramsObject)
        return paramsObject.firstString("delta", "textDelta", "text_delta", "text", "summary", "part")
            ?: eventObject?.firstString("delta", "text", "summary", "part")
            ?: paramsObject.firstObject("event")?.firstString("delta", "text", "summary", "part")
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
        return null
    }

    private fun extractThreadId(paramsObject: JsonObject): String? {
        val eventObject = envelopeEventObject(paramsObject)
        return paramsObject.firstString("threadId", "thread_id")
            ?: paramsObject.firstObject("thread")?.firstString("id", "threadId", "thread_id")
            ?: paramsObject.firstObject("turn")?.firstString("threadId", "thread_id")
            ?: paramsObject.firstObject("item")?.firstString("threadId", "thread_id")
            ?: eventObject?.firstString("threadId", "thread_id")
            ?: eventObject?.firstObject("thread")?.firstString("id", "threadId", "thread_id")
            ?: eventObject?.firstObject("turn")?.firstString("threadId", "thread_id")
            ?: eventObject?.firstObject("item")?.firstString("threadId", "thread_id")
            ?: paramsObject.firstObject("event")?.firstString("threadId", "thread_id")
            ?: paramsObject.firstObject("event")?.firstObject("thread")?.firstString("id", "threadId", "thread_id")
            ?: paramsObject.firstObject("event")?.firstObject("turn")?.firstString("threadId", "thread_id")
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
        val title = threadObject.firstString("name", "title")
            ?: existing?.title
            ?: "Conversation"
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
        return ThreadSyncSnapshot(
            id = id,
            title = title,
            preview = preview,
            projectPath = projectPath,
            lastUpdatedLabel = relativeUpdatedLabel(updatedEpochMs),
            lastUpdatedEpochMs = updatedEpochMs,
            isRunning = activeTurnIdByThread.containsKey(id) || existing?.isRunning == true,
            syncState = syncState,
            parentThreadId = threadObject.firstString("parentThreadId", "parent_thread_id") ?: existing?.parentThreadId,
            agentNickname = threadObject.firstString("agentNickname", "agent_nickname") ?: existing?.agentNickname,
            agentRole = threadObject.firstString("agentRole", "agent_role") ?: existing?.agentRole,
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
        val mutations = mutableListOf<TimelineMutation>()
        var orderIndex = 0L

        turns.forEach { turnValue ->
            val turnObject = turnValue.jsonObjectOrNull ?: return@forEach
            val turnId = turnObject.firstString("id", "turnId", "turn_id")
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
                val text = decodeItemText(itemObject)
                val speaker = when (itemType) {
                    "usermessage" -> ConversationSpeaker.USER
                    "agentmessage", "assistantmessage" -> ConversationSpeaker.ASSISTANT
                    "message" -> {
                        if (itemObject.firstString("role")?.contains("user", ignoreCase = true) == true) {
                            ConversationSpeaker.USER
                        } else {
                            ConversationSpeaker.ASSISTANT
                        }
                    }

                    else -> ConversationSpeaker.SYSTEM
                }
                val kind = when (itemType) {
                    "reasoning" -> ConversationItemKind.REASONING
                    "plan" -> ConversationItemKind.PLAN
                    "filechange", "diff" -> ConversationItemKind.FILE_CHANGE
                    "toolcall" -> ConversationItemKind.TOOL_ACTIVITY
                    "commandexecution", "contextcompaction" -> ConversationItemKind.COMMAND_EXECUTION
                    "userinputprompt", "requestuserinput" -> ConversationItemKind.USER_INPUT_PROMPT
                    else -> if (isSubagentHistoryItemType(itemType)) {
                        ConversationItemKind.SUBAGENT_ACTION
                    } else {
                        ConversationItemKind.CHAT
                    }
                }
                val attachments = if (speaker == ConversationSpeaker.USER) {
                    decodeImageAttachments(itemObject)
                } else {
                    emptyList()
                }
                val planState = if (kind == ConversationItemKind.PLAN) {
                    decodeHistoryPlanState(itemObject)
                } else {
                    null
                }
                val structuredUserInputRequest = if (kind == ConversationItemKind.USER_INPUT_PROMPT) {
                    decodeStructuredUserInputRequest(itemObject)
                } else {
                    null
                }
                val subagentAction = if (kind == ConversationItemKind.SUBAGENT_ACTION) {
                    decodeSubagentAction(itemObject)
                } else {
                    null
                }
                val resolvedText = when {
                    kind == ConversationItemKind.PLAN -> decodePlanItemText(itemObject)
                    itemType == "toolcall" -> decodeToolCallActivityBody(
                        itemObject = itemObject,
                        isCompleted = true,
                    )
                    subagentAction != null -> subagentAction.summaryText
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
                    || itemType == "plan"
                    || attachments.isNotEmpty()
                    || structuredUserInputRequest != null
                    || subagentAction != null
                ) {
                    mutations += TimelineMutation.Upsert(
                        timelineItem(
                            id = itemId ?: "$threadId-history-$orderIndex",
                            speaker = speaker,
                            text = resolvedText.ifBlank { itemType.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } },
                            kind = kind,
                            turnId = turnId,
                            itemId = itemId,
                            attachments = attachments,
                            planState = planState,
                            subagentAction = subagentAction,
                            structuredUserInputRequest = structuredUserInputRequest,
                            orderIndex = orderIndex,
                            assistantChangeSet = if (
                                speaker == ConversationSpeaker.ASSISTANT &&
                                itemId != null &&
                                itemId == assistantMessageId
                            ) {
                                assistantChangeSet
                            } else {
                                null
                            },
                        ),
                    )
                    orderIndex += 1L
                }
            }
        }

        return mutations
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
                                ?: uri.substringAfterLast('/').ifBlank { "image-${index + 1}" },
                        )
                    }
                }

                else -> null
            }
        }
    }

    private fun decodePlanItemText(itemObject: JsonObject): String {
        val planState = decodeHistoryPlanState(itemObject)
        val explanation = planState?.explanation?.trim().orEmpty()
        if (explanation.isNotEmpty()) {
            return explanation
        }
        return planState?.steps?.firstOrNull()?.step.orEmpty()
    }

    private fun decodeHistoryPlanState(itemObject: JsonObject): RemodexPlanState? {
        val explanation = itemObject.firstString("explanation", "text", "message", "summary")
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
        val questions = itemObject.firstArray("questions").orEmpty().mapIndexedNotNull { index, value ->
            val questionObject = value.jsonObjectOrNull ?: return@mapIndexedNotNull null
            val id = questionObject.firstString("id")?.trim().orEmpty()
            val header = questionObject.firstString("header")?.trim().orEmpty()
            val question = questionObject.firstString("question")?.trim().orEmpty()
            if (id.isEmpty() || header.isEmpty() || question.isEmpty()) {
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
        val requestId = itemObject.firstString("requestId", "request_id", "id")?.trim().orEmpty()
        if (requestId.isEmpty()) {
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
        val content = itemObject.firstArray("content").orEmpty()
        val contentText = content.mapNotNull { element ->
            val objectValue = element.jsonObjectOrNull ?: return@mapNotNull null
            when (normalizeItemType(objectValue.firstString("type").orEmpty())) {
                "text", "inputtext", "outputtext", "message" -> objectValue.firstString("text")
                "skill" -> objectValue.firstString("id", "name")?.let { "\$$it" }
                else -> null
            }
        }.joinToString(separator = "\n").trim()
        if (contentText.isNotBlank()) {
            return contentText
        }
        return itemObject.firstString("text", "message", "summary", "description").orEmpty()
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
        return when (itemType) {
            "agentmessage", "assistantmessage" -> true
            "message" -> itemObject.firstString("role")?.contains("assistant", ignoreCase = true) == true
            else -> false
        }
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
        val turns = threadObject.firstArray("turns").orEmpty()
        return turns.reversed().firstNotNullOfOrNull { element ->
            val turnObject = element.jsonObjectOrNull ?: return@firstNotNullOfOrNull null
            val normalizedStatus = normalizeStatus(
                turnObject.firstString("status", "turnStatus", "turn_status").orEmpty(),
            )
            if (normalizedStatus.contains("complete")
                || normalizedStatus.contains("fail")
                || normalizedStatus.contains("error")
                || normalizedStatus.contains("interrupt")
                || normalizedStatus.contains("cancel")
                || normalizedStatus.contains("stopped")
            ) {
                null
            } else {
                turnObject.firstString("id", "turnId", "turn_id")
            }
        }
    }

    private fun buildTurnStartParams(
        threadId: String,
        prompt: String,
        attachments: List<RemodexComposerAttachment>,
        runtimeConfig: RemodexRuntimeConfig,
        includeServiceTier: Boolean,
    ): JsonObject {
        val inputItems = buildJsonArray {
            if (prompt.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(prompt))
                    },
                )
            }
            attachments.forEach { attachment ->
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("image"))
                        put("image_url", JsonPrimitive(attachment.uriString))
                        put("name", JsonPrimitive(attachment.displayName))
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
            if (runtimeConfig.planningMode == RemodexPlanningMode.PLAN) {
                put("collaborationMode", JsonPrimitive("plan"))
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
                    preview = prompt.ifBlank {
                        when (attachments.size) {
                            0 -> "Sent a prompt from Android."
                            1 -> "Shared 1 image from Android."
                            else -> "Shared ${attachments.size} images from Android."
                        }
                    },
                    lastUpdatedEpochMs = now,
                    lastUpdatedLabel = relativeUpdatedLabel(now),
                    isRunning = true,
                    runtimeConfig = runtimeConfig,
                    timelineMutations = snapshot.timelineMutations + TimelineMutation.Upsert(
                        timelineItem(
                            id = "user-local-$now",
                            speaker = ConversationSpeaker.USER,
                            text = prompt.ifBlank {
                                when (attachments.size) {
                                    0 -> "Sent a prompt from Android."
                                    1 -> "Shared 1 image from Android."
                                    else -> "Shared ${attachments.size} images from Android."
                                }
                            },
                            deliveryState = RemodexMessageDeliveryState.PENDING,
                            attachments = attachments.map { attachment ->
                                RemodexConversationAttachment(
                                    id = attachment.id,
                                    uriString = attachment.uriString,
                                    displayName = attachment.displayName,
                                )
                            },
                            orderIndex = orderIndex,
                        ),
                    ),
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

    private fun isConnected(): Boolean {
        return secureConnectionCoordinator.state.value.secureState == SecureConnectionState.ENCRYPTED
    }

    companion object {
        private const val MaxPatchSearchDepth = 4
    }
}
