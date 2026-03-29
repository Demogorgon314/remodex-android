package com.emanueledipietro.remodex.data.app

import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import kotlinx.coroutines.flow.StateFlow
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitRemoteUrl
import com.emanueledipietro.remodex.model.RemodexGitWorktreeChangeTransferMode
import com.emanueledipietro.remodex.model.RemodexGitWorktreeResult
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexBridgeVersionStatus
import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexUsageStatus
import kotlinx.serialization.json.JsonElement

interface RemodexAppRepository {
    val session: StateFlow<RemodexSessionSnapshot>
    val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>>
    val gptAccountSnapshot: StateFlow<RemodexGptAccountSnapshot>
    val gptAccountErrorMessage: StateFlow<String?>
    val bridgeVersionStatus: StateFlow<RemodexBridgeVersionStatus>
    val usageStatus: StateFlow<RemodexUsageStatus>

    suspend fun completeOnboarding()

    suspend fun refreshThreads()

    suspend fun hydrateThread(threadId: String) = Unit

    suspend fun selectThread(threadId: String)

    suspend fun createThread(
        preferredProjectPath: String? = null,
        inheritRuntimeFromThreadId: String? = null,
    )

    suspend fun renameThread(
        threadId: String,
        name: String,
    )

    suspend fun archiveThread(threadId: String)

    suspend fun unarchiveThread(threadId: String)

    suspend fun deleteThread(threadId: String)

    suspend fun archiveProject(projectPath: String)

    suspend fun sendPrompt(
        threadId: String,
        prompt: String,
        attachments: List<RemodexComposerAttachment>,
        planningModeOverride: RemodexPlanningMode? = null,
    )

    suspend fun respondToStructuredUserInput(
        requestId: JsonElement,
        answersByQuestionId: Map<String, List<String>>,
    )

    suspend fun stopTurn(threadId: String)

    suspend fun sendQueuedDraft(
        threadId: String,
        draftId: String,
    )

    suspend fun setPlanningMode(
        threadId: String,
        planningMode: RemodexPlanningMode,
    )

    suspend fun setSelectedModelId(
        modelId: String?,
    )

    suspend fun setReasoningEffort(
        threadId: String,
        reasoningEffort: String,
    )

    suspend fun setAccessMode(
        threadId: String,
        accessMode: RemodexAccessMode,
    )

    suspend fun setServiceTier(
        threadId: String,
        serviceTier: RemodexServiceTier?,
    )

    suspend fun setDefaultModelId(modelId: String?)

    suspend fun setDefaultReasoningEffort(reasoningEffort: String?)

    suspend fun setDefaultAccessMode(accessMode: RemodexAccessMode)

    suspend fun setDefaultServiceTier(serviceTier: RemodexServiceTier?)

    suspend fun setAppearanceMode(mode: RemodexAppearanceMode)

    suspend fun setAppFontStyle(style: RemodexAppFontStyle)

    suspend fun setMacNickname(
        deviceId: String,
        nickname: String?,
    )

    suspend fun refreshGptAccountState()

    suspend fun logoutGptAccount()

    suspend fun refreshUsageStatus(threadId: String?)

    suspend fun fuzzyFileSearch(
        threadId: String,
        query: String,
    ): List<RemodexFuzzyFileMatch>

    suspend fun listSkills(
        threadId: String,
        forceReload: Boolean = false,
    ): List<RemodexSkillMetadata>

    suspend fun startCodeReview(
        threadId: String,
        target: RemodexComposerReviewTarget,
        baseBranch: String? = null,
    )

    suspend fun forkThread(
        threadId: String,
        destination: RemodexComposerForkDestination,
        baseBranch: String? = null,
    ): String?

    suspend fun forkThreadIntoProjectPath(
        threadId: String,
        projectPath: String,
    ): String?

    suspend fun loadGitState(threadId: String): RemodexGitState

    suspend fun loadGitDiff(threadId: String): RemodexGitRepoDiff = RemodexGitRepoDiff()

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

    suspend fun moveThreadToProjectPath(
        threadId: String,
        projectPath: String,
    )

    suspend fun loadGitRemoteUrl(threadId: String): RemodexGitRemoteUrl

    suspend fun previewAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertPreviewResult

    suspend fun applyAssistantRevert(
        threadId: String,
        forwardPatch: String,
    ): RemodexRevertApplyResult

    suspend fun pairWithQrPayload(payload: PairingQrPayload)

    suspend fun retryConnection()

    suspend fun disconnect()

    suspend fun forgetTrustedMac()
}
