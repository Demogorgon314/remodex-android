package com.emanueledipietro.remodex.data.threads

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
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitBranches
import com.emanueledipietro.remodex.model.RemodexGitChangedFile
import com.emanueledipietro.remodex.model.RemodexGitDiffTotals
import com.emanueledipietro.remodex.model.RemodexGitRepoSync
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitWorktreeResult
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanState
import com.emanueledipietro.remodex.model.RemodexPlanStep
import com.emanueledipietro.remodex.model.RemodexPlanStepStatus
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertConflict
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputOption
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputQuestion
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputRequest
import com.emanueledipietro.remodex.model.RemodexSubagentAction
import com.emanueledipietro.remodex.model.RemodexSubagentRef
import com.emanueledipietro.remodex.model.RemodexSubagentState
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexUnifiedPatchParser
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
    private val backingThreads = MutableStateFlow<List<ThreadSyncSnapshot>>(emptyList())
    private val activeTurnIdByThread = mutableMapOf<String, String>()
    private var initializedAttempt: Int? = null

    override val threads: StateFlow<List<ThreadSyncSnapshot>> = backingThreads

    init {
        scope.launch {
            secureConnectionCoordinator.state.collectLatest { snapshot ->
                if (snapshot.secureState == SecureConnectionState.ENCRYPTED) {
                    if (initializedAttempt != snapshot.attempt) {
                        initializedAttempt = snapshot.attempt
                        initializeSession()
                        refreshThreads()
                    }
                } else {
                    initializedAttempt = null
                    activeTurnIdByThread.clear()
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
        val params = buildJsonObject {
            preferredProjectPath?.trim()?.takeIf(String::isNotEmpty)?.let { put("cwd", JsonPrimitive(it)) }
            runtimeDefaults.modelId?.takeIf(String::isNotBlank)?.let { put("model", JsonPrimitive(it)) }
            runtimeDefaults.serviceTier?.let { put("serviceTier", JsonPrimitive(it.wireValue)) }
        }
        val response = sendRequestWithApprovalPolicyFallback(
            method = "thread/start",
            baseParams = params,
            accessMode = runtimeDefaults.accessMode,
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
                    selectedModelId = runtimeDefaults.modelId,
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

        val request = buildTurnStartParams(
            threadId = threadId,
            prompt = trimmedPrompt,
            attachments = attachments,
            runtimeConfig = runtimeConfig,
        )
        val response = sendRequestWithApprovalPolicyFallback(
            method = "turn/start",
            baseParams = request,
            accessMode = runtimeConfig.accessMode,
        )
        val turnId = extractTurnId(response.result)
        if (turnId != null) {
            activeTurnIdByThread[threadId] = turnId
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
        var includeServiceTier = runtimeConfig.serviceTier != null
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
                    includeServiceTier && (message.contains("servicetier") || message.contains("service tier")) -> {
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
        accessMode: com.emanueledipietro.remodex.model.RemodexAccessMode,
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

    private suspend fun handleNotification(message: RpcMessage) {
        when (message.method?.trim()) {
            "thread/name/updated" -> refreshThreads()
            "turn/started" -> {
                val paramsObject = message.params?.jsonObjectOrNull ?: return
                val threadId = resolveThreadId(paramsObject) ?: return
                extractTurnId(message.params)?.let { activeTurnIdByThread[threadId] = it }
                updateThread(threadId) { snapshot -> snapshot.copy(isRunning = true) }
            }

            "turn/completed" -> {
                val paramsObject = message.params?.jsonObjectOrNull ?: return
                val threadId = resolveThreadId(paramsObject) ?: return
                activeTurnIdByThread.remove(threadId)
                updateThread(threadId) { snapshot -> snapshot.copy(isRunning = false) }
                refreshThreads()
                hydrateThread(threadId)
            }

            "thread/status/changed" -> {
                val paramsObject = message.params?.jsonObjectOrNull ?: return
                val threadId = resolveThreadId(paramsObject) ?: return
                val normalizedStatus = normalizeStatus(
                    paramsObject.firstString("status")
                        ?: paramsObject.firstObject("status")?.firstString("type", "statusType", "status_type")
                        ?: "",
                )
                when {
                    normalizedStatus.contains("running")
                        || normalizedStatus.contains("active")
                        || normalizedStatus.contains("started")
                        || normalizedStatus.contains("pending") -> {
                        updateThread(threadId) { snapshot -> snapshot.copy(isRunning = true) }
                    }

                    normalizedStatus.contains("done")
                        || normalizedStatus.contains("completed")
                        || normalizedStatus.contains("stopped")
                        || normalizedStatus.contains("idle") -> {
                        activeTurnIdByThread.remove(threadId)
                        updateThread(threadId) { snapshot -> snapshot.copy(isRunning = false) }
                    }
                }
            }
        }
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
                availableModels = modelId?.let {
                    listOf(
                        RemodexModelOption(
                            id = it,
                            model = it,
                            displayName = it,
                        ),
                    )
                }.orEmpty(),
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
                    "toolcall", "commandexecution", "contextcompaction" -> ConversationItemKind.COMMAND_EXECUTION
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
                    subagentAction != null -> subagentAction.summaryText
                    else -> text
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
            put("reasoningEffort", JsonPrimitive(runtimeConfig.reasoningEffort.name.lowercase(Locale.ROOT)))
            runtimeConfig.serviceTier?.let { put("serviceTier", JsonPrimitive(it.wireValue)) }
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
            return incoming
        }
        return incoming.copy(
            availableModels = if (incoming.availableModels.isNotEmpty()) incoming.availableModels else existing.availableModels,
            availableServiceTiers = if (incoming.availableServiceTiers.isNotEmpty()) incoming.availableServiceTiers else existing.availableServiceTiers,
            selectedModelId = incoming.selectedModelId ?: existing.selectedModelId,
            planningMode = incoming.planningMode,
            reasoningEffort = incoming.reasoningEffort,
            accessMode = incoming.accessMode,
            serviceTier = incoming.serviceTier ?: existing.serviceTier,
        )
    }

    private fun nextOrderIndex(snapshot: ThreadSyncSnapshot): Long {
        return (snapshot.timelineMutations
            .mapNotNull { mutation ->
                when (mutation) {
                    is TimelineMutation.Upsert -> mutation.item.orderIndex
                    is TimelineMutation.AssistantTextDelta -> mutation.orderIndex
                    is TimelineMutation.ReasoningTextDelta -> mutation.orderIndex
                    is TimelineMutation.ActivityLine -> mutation.orderIndex
                    is TimelineMutation.Complete -> null
                }
            }.maxOrNull() ?: -1L) + 1L
    }

    private fun resolveThreadId(paramsObject: JsonObject): String? {
        val turnObject = paramsObject.firstObject("turn")
        return paramsObject.firstString("threadId", "thread_id")
            ?: turnObject?.firstString("threadId", "thread_id")
    }

    private fun extractTurnId(value: JsonElement?): String? {
        val objectValue = value?.jsonObjectOrNull ?: return null
        return objectValue.firstString("turnId", "turn_id")
            ?: objectValue.firstObject("turn")?.firstString("id", "turnId", "turn_id")
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
