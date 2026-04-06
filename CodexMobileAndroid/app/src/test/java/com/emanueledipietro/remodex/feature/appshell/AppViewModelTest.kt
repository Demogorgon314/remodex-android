package com.emanueledipietro.remodex.feature.appshell

import com.emanueledipietro.remodex.MainDispatcherRule
import com.emanueledipietro.remodex.data.app.RemodexAppRepository
import com.emanueledipietro.remodex.data.connection.RpcError
import com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot
import com.emanueledipietro.remodex.data.threads.StopTurnResult
import com.emanueledipietro.remodex.data.threads.StreamingAssistantTextState
import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetSource
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexAssistantFileChange
import com.emanueledipietro.remodex.model.RemodexAssistantResponseMetrics
import com.emanueledipietro.remodex.model.RemodexAssistantRevertRiskLevel
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexApprovalRequest
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexBridgeVersionStatus
import com.emanueledipietro.remodex.model.RemodexBridgeUpdatePrompt
import com.emanueledipietro.remodex.model.RemodexCodeReviewRequest
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerAutocompletePanel
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexCommandExecutionLiveStatus
import com.emanueledipietro.remodex.model.RemodexCommandExecutionSource
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerMentionedFile
import com.emanueledipietro.remodex.model.RemodexComposerMentionedSkill
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexGitDiffTotals
import com.emanueledipietro.remodex.model.RemodexGitBranches
import com.emanueledipietro.remodex.model.RemodexGitCommit
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitRemoteUrl
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitRepoSync
import com.emanueledipietro.remodex.model.RemodexGitWorktreeChangeTransferMode
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexPermissionGrantScope
import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexQueuedDraftContext
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexSlashCommand
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexTurnTerminalState
import com.emanueledipietro.remodex.model.RemodexUsageStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private data class GitWorktreeRequest(
    val threadId: String,
    val name: String,
    val baseBranch: String?,
    val changeTransfer: RemodexGitWorktreeChangeTransferMode,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `ui state reflects repository updates`() = runTest {
        val repository = TestRemodexAppRepository()
        val viewModel = AppViewModel(repository)

        repository.snapshot.value = repository.snapshot.value.copy(
            onboardingCompleted = true,
            connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 2),
            secureConnection = SecureConnectionSnapshot(
                phaseMessage = "Secure handshake complete. Android is connected to your trusted Remodex session.",
                secureState = SecureConnectionState.ENCRYPTED,
                attempt = 2,
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.onboardingCompleted)
        assertTrue(viewModel.uiState.value.isConnected)
        assertEquals("Connected to Remodex", viewModel.uiState.value.connectionHeadline)
    }

    @Test
    fun `ui state reflects runtime bridge update prompt and supports dismiss`() = runTest {
        val repository = TestRemodexAppRepository()
        val viewModel = AppViewModel(repository)
        repository.snapshot.value = repository.snapshot.value.copy(
            bridgeUpdatePrompt = RemodexBridgeUpdatePrompt(
                title = "Update Remodex on your Mac to use /fork",
                message = "Old bridge detected.",
            ),
        )
        advanceUntilIdle()

        assertEquals(
            "Update Remodex on your Mac to use /fork",
            viewModel.uiState.value.bridgeUpdatePrompt?.title,
        )

        viewModel.dismissBridgeUpdatePrompt()
        advanceUntilIdle()

        assertEquals(1, repository.dismissBridgeUpdatePromptCalls)
        assertNull(viewModel.uiState.value.bridgeUpdatePrompt)
    }

    @Test
    fun `retry connection after bridge update dismisses prompt and retries`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                bridgeUpdatePrompt = RemodexBridgeUpdatePrompt(
                    title = "Update Remodex on your Mac to use Speed controls",
                    message = "Old bridge detected.",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.retryConnectionAfterBridgeUpdate()
        advanceUntilIdle()

        assertEquals(1, repository.dismissBridgeUpdatePromptCalls)
        assertEquals(1, repository.retryConnectionCalls)
        assertNull(viewModel.uiState.value.bridgeUpdatePrompt)
    }

    @Test
    fun `pending approval is shown only for the selected thread and approve delegates to repository`() = runTest {
        val repository = TestRemodexAppRepository()
        val selectedThread = threadSummary(
            id = "thread-1",
            title = "Composer thread",
            projectPath = "/tmp/remodex",
        )
        val otherThread = threadSummary(
            id = "thread-2",
            title = "Other thread",
            projectPath = "/tmp/remodex-2",
        )
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(selectedThread, otherThread),
            selectedThreadId = selectedThread.id,
            selectedThreadSnapshot = selectedThread,
            pendingApprovalRequest = RemodexApprovalRequest(
                id = "approval-1",
                requestId = JsonPrimitive("req-1"),
                method = "item/commandExecution/requestApproval",
                command = "git status",
                reason = "Need to inspect the repo state.",
                threadId = selectedThread.id,
            ),
        )
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        assertEquals("approval-1", viewModel.uiState.value.pendingApprovalRequest?.id)
        assertNull(viewModel.uiState.value.approvalBanner)

        viewModel.approvePendingApproval()
        advanceUntilIdle()

        assertEquals(listOf(false), repository.approvePendingApprovalRequests)

        repository.snapshot.value = repository.snapshot.value.copy(
            pendingApprovalRequest = RemodexApprovalRequest(
                id = "approval-2",
                requestId = JsonPrimitive("req-2"),
                method = "item/commandExecution/requestApproval",
                threadId = otherThread.id,
            ),
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingApprovalRequest)
        assertNull(viewModel.uiState.value.approvalBanner)
    }

    @Test
    fun `declining pending approval delegates to repository`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                pendingApprovalRequest = RemodexApprovalRequest(
                    id = "approval-1",
                    requestId = JsonPrimitive("req-1"),
                    method = "item/fileChange/requestApproval",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.declinePendingApproval()
        advanceUntilIdle()

        assertEquals(1, repository.declinePendingApprovalRequests)
    }

    @Test
    fun `approving pending approval for session delegates command approvals to repository`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                pendingApprovalRequest = RemodexApprovalRequest(
                    id = "approval-1",
                    requestId = JsonPrimitive("req-1"),
                    method = "item/commandExecution/requestApproval",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.approvePendingApprovalForSession()
        advanceUntilIdle()

        assertEquals(listOf(true), repository.approvePendingApprovalRequests)
    }

    @Test
    fun `approving permissions approval delegates grant scope to repository`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                pendingApprovalRequest = RemodexApprovalRequest(
                    id = "approval-1",
                    requestId = JsonPrimitive("req-1"),
                    method = "item/permissions/requestApproval",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.approvePendingApproval()
        viewModel.approvePendingApprovalForSession()
        advanceUntilIdle()

        assertEquals(
            listOf(RemodexPermissionGrantScope.TURN, RemodexPermissionGrantScope.SESSION),
            repository.grantPendingPermissionsApprovalScopes,
        )
        assertTrue(repository.approvePendingApprovalRequests.isEmpty())
    }

    @Test
    fun `canceling pending approval delegates to repository`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                pendingApprovalRequest = RemodexApprovalRequest(
                    id = "approval-1",
                    requestId = JsonPrimitive("req-1"),
                    method = "item/fileChange/requestApproval",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.cancelPendingApproval()
        advanceUntilIdle()

        assertEquals(1, repository.cancelPendingApprovalRequests)
    }

    @Test
    fun `foreground approval banner appears for a different selected thread and can be dismissed`() = runTest {
        val repository = TestRemodexAppRepository()
        val selectedThread = threadSummary(
            id = "thread-1",
            title = "Current thread",
        )
        val approvalThread = threadSummary(
            id = "thread-2",
            title = "Approval thread",
            isWaitingOnApproval = true,
        )
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(selectedThread, approvalThread),
            selectedThreadId = selectedThread.id,
            selectedThreadSnapshot = selectedThread,
            pendingApprovalRequest = RemodexApprovalRequest(
                id = "approval-2",
                requestId = JsonPrimitive("req-2"),
                method = "item/commandExecution/requestApproval",
                command = "git status",
                reason = "Need to inspect the repo state.",
                threadId = approvalThread.id,
            ),
        )
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.onAppForegroundChanged(true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingApprovalRequest)
        assertEquals("thread-2", viewModel.uiState.value.approvalBanner?.threadId)
        assertEquals("Approval thread", viewModel.uiState.value.approvalBanner?.title)

        viewModel.dismissApprovalBanner()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.approvalBanner)
    }

    @Test
    fun `composer state reflects the selected thread draft rules`() = runTest {
        val repository = TestRemodexAppRepository()
        val viewModel = AppViewModel(repository)

        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                com.emanueledipietro.remodex.model.RemodexThreadSummary(
                    id = "thread-1",
                    title = "Composer thread",
                    preview = "Preview",
                    projectPath = "/tmp/remodex",
                    lastUpdatedLabel = "Updated just now",
                    isRunning = true,
                    queuedDrafts = 0,
                    runtimeLabel = "Plan, medium reasoning",
                    messages = emptyList(),
                ),
            ),
            selectedThreadId = "thread-1",
        )
        advanceUntilIdle()
        viewModel.updateComposerInput("Queue this next")
        advanceUntilIdle()

        assertEquals("Queue follow-up", viewModel.uiState.value.composer.sendLabel)
        assertTrue(viewModel.uiState.value.composer.canStop)
        assertTrue(viewModel.uiState.value.composer.canSend)
    }

    @Test
    fun `fork slash command disappears when runtime marks fork unsupported`() = runTest {
        val repository = TestRemodexAppRepository()
        val selectedThread = threadSummary(
            id = "thread-1",
            title = "Composer thread",
            projectPath = "/tmp/remodex",
        )
        val viewModel = AppViewModel(repository)
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(selectedThread),
            selectedThreadId = selectedThread.id,
            selectedThreadSnapshot = selectedThread,
            supportsThreadFork = true,
        )
        advanceUntilIdle()

        viewModel.updateComposerInput("/")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.composer.autocomplete.availableCommands.contains(RemodexSlashCommand.FORK))

        repository.snapshot.value = repository.snapshot.value.copy(supportsThreadFork = false)
        advanceUntilIdle()
        viewModel.updateComposerInput("/")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.composer.autocomplete.availableCommands.contains(RemodexSlashCommand.FORK))
        assertTrue(viewModel.uiState.value.composer.autocomplete.forkDestinations.isEmpty())
    }

    @Test
    fun `fork slash command only offers local when thread cannot create a worktree`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(phase = RemodexConnectionPhase.CONNECTED),
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Running thread",
                        projectPath = "/tmp/remodex",
                        isRunning = true,
                    ),
                ),
                selectedThreadId = "thread-1",
                supportsThreadFork = true,
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    currentBranch = "main",
                    defaultBranch = "main",
                    branches = listOf("main", "feature/test"),
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.updateComposerInput("/")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.FORK)
        advanceUntilIdle()

        assertEquals(
            listOf(RemodexComposerForkDestination.LOCAL),
            viewModel.uiState.value.composer.autocomplete.forkDestinations,
        )
    }

    @Test
    fun `fork slash command does not offer new worktree from an existing worktree thread`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(phase = RemodexConnectionPhase.CONNECTED),
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Worktree thread",
                        projectPath = "/tmp/remodex/.codex/worktrees/feature/existing",
                    ),
                ),
                selectedThreadId = "thread-1",
                supportsThreadFork = true,
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    currentBranch = "feature/existing",
                    defaultBranch = "main",
                    branches = listOf("main", "feature/existing"),
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.updateComposerInput("/")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.FORK)
        advanceUntilIdle()

        assertEquals(
            listOf(RemodexComposerForkDestination.LOCAL),
            viewModel.uiState.value.composer.autocomplete.forkDestinations,
        )
    }

    @Test
    fun `fork thread suppresses bridge update failures once runtime prompt is available`() = runTest {
        val selectedThread = threadSummary(
            id = "thread-1",
            title = "Composer thread",
            projectPath = "/tmp/remodex",
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(selectedThread),
                selectedThreadId = selectedThread.id,
                selectedThreadSnapshot = selectedThread,
            )
            forkThreadError = IllegalStateException("This Mac bridge does not support native conversation forks yet.")
            forkThreadFailureSessionSnapshot = snapshot.value.copy(
                threads = listOf(selectedThread),
                selectedThreadId = selectedThread.id,
                selectedThreadSnapshot = selectedThread,
                bridgeUpdatePrompt = RemodexBridgeUpdatePrompt(
                    title = "Update Remodex on your Mac to use /fork",
                    message = "Old bridge detected.",
                ),
                supportsThreadFork = false,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.forkThread(RemodexComposerForkDestination.LOCAL)
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("thread-1", RemodexComposerForkDestination.LOCAL, null)),
            repository.forkThreadRequests,
        )
        assertEquals(
            "Update Remodex on your Mac to use /fork",
            viewModel.uiState.value.bridgeUpdatePrompt?.title,
        )
        assertFalse(viewModel.uiState.value.supportsThreadFork)
        assertNull(viewModel.uiState.value.gitSyncAlert)
    }

    @Test
    fun `restored selected thread hydrates immediately`() = runTest {
        val repository = TestRemodexAppRepository()
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(threadSummary(id = "thread-1", title = "Recovered thread")),
            selectedThreadId = "thread-1",
        )

        AppViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.hydrateRequests)
    }

    @Test
    fun `selected thread hydration state stays visible until hydrate completes`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            hydrateDelayMs = 1_000L
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Recovered thread")),
                selectedThreadId = "thread-1",
            )
        }

        val viewModel = AppViewModel(repository)
        runCurrent()

        assertTrue(viewModel.uiState.value.isSelectedThreadHydrating)

        advanceTimeBy(repository.hydrateDelayMs)
        runCurrent()

        assertFalse(viewModel.uiState.value.isSelectedThreadHydrating)
    }

    @Test
    fun `selected thread does not rehydrate just because connection becomes active`() = runTest {
        val repository = TestRemodexAppRepository()
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(threadSummary(id = "thread-1", title = "Recovered thread")),
            selectedThreadId = "thread-1",
        )

        AppViewModel(repository)
        advanceUntilIdle()
        repository.hydrateRequests.clear()

        repository.snapshot.value = repository.snapshot.value.copy(
            connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
            secureConnection = SecureConnectionSnapshot(
                phaseMessage = "Connected",
                secureState = SecureConnectionState.ENCRYPTED,
                attempt = 1,
            ),
        )
        advanceUntilIdle()

        assertTrue(repository.hydrateRequests.isEmpty())
    }

    @Test
    fun `explicit thread selection does not trigger a second session hydration pass`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Connected",
                    secureState = SecureConnectionState.ENCRYPTED,
                    attempt = 1,
                ),
                threads = listOf(
                    threadSummary(id = "thread-1", title = "First thread"),
                    threadSummary(id = "thread-2", title = "Second thread"),
                ),
                selectedThreadId = "thread-1",
                selectedThreadSnapshot = threadSummary(id = "thread-1", title = "First thread"),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        repository.hydrateRequests.clear()
        repository.selectedThreadRequests.clear()

        viewModel.selectThread("thread-2")
        advanceUntilIdle()

        assertEquals(listOf("thread-2"), repository.selectedThreadRequests)
        assertTrue(repository.hydrateRequests.isEmpty())
    }

    @Test
    fun `foreground changes are forwarded to the repository active sync controller`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Connected",
                    secureState = SecureConnectionState.ENCRYPTED,
                    attempt = 1,
                ),
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Recovered thread",
                        isRunning = true,
                    ),
                ),
                selectedThreadId = "thread-1",
                selectedThreadSnapshot = threadSummary(
                    id = "thread-1",
                    title = "Recovered thread",
                    isRunning = true,
                ),
            )
        }

        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.onAppForegroundChanged(true)
        runCurrent()

        viewModel.onAppForegroundChanged(false)
        advanceUntilIdle()
        assertEquals(listOf(true, false), repository.foregroundStates)
    }

    @Test
    fun `attachment limit keeps only the allowed images and shows a limit message`() = runTest {
        val repository = TestRemodexAppRepository()
        val viewModel = AppViewModel(repository)

        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(threadSummary(id = "thread-1", title = "Attachment thread")),
            selectedThreadId = "thread-1",
        )
        advanceUntilIdle()

        viewModel.addAttachments(
            List(5) { index ->
                RemodexComposerAttachment(
                    id = "attachment-$index",
                    uriString = "content://attachments/$index",
                    displayName = "Image $index",
                )
            },
        )
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.composer.attachments.size)
        assertEquals(
            "Only 4 images are allowed per message.",
            viewModel.uiState.value.composer.attachmentLimitMessage,
        )
    }

    @Test
    fun `adding attachments clears any composer error message`() = runTest {
        val repository = TestRemodexAppRepository()
        val viewModel = AppViewModel(repository)

        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(threadSummary(id = "thread-1", title = "Camera thread")),
            selectedThreadId = "thread-1",
        )
        advanceUntilIdle()

        viewModel.presentComposerMessage("Camera permission was denied.")
        advanceUntilIdle()
        assertEquals("Camera permission was denied.", viewModel.uiState.value.composer.composerMessage)

        viewModel.addAttachments(
            listOf(
                RemodexComposerAttachment(
                    id = "attachment-1",
                    uriString = "content://attachments/1",
                    displayName = "Capture.jpg",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.composer.composerMessage)
    }

    @Test
    fun `assistant revert preview and apply update the sheet and final button state`() = runTest {
        val repository = TestRemodexAppRepository()
        repository.snapshot.value = repository.snapshot.value.copy(
            connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
            secureConnection = SecureConnectionSnapshot(
                phaseMessage = "Connected",
                secureState = SecureConnectionState.ENCRYPTED,
                attempt = 1,
            ),
            threads = listOf(
                threadSummary(
                    id = "thread-1",
                    title = "Undo thread",
                    messages = listOf(
                        assistantMessage(
                            id = "assistant-1",
                            threadId = "thread-1",
                            patchPath = "src/App.kt",
                        ),
                    ),
                ),
            ),
            selectedThreadId = "thread-1",
        )
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.startAssistantRevertPreview("assistant-1")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.assistantRevertSheet)
        assertTrue(viewModel.uiState.value.assistantRevertSheet?.preview?.canRevert == true)
        assertEquals(1, repository.previewRequests.size)

        viewModel.confirmAssistantRevert()
        advanceUntilIdle()

        assertEquals(1, repository.applyRequests.size)
        assertEquals(null, viewModel.uiState.value.assistantRevertSheet)
        assertEquals(
            "Already undone",
            viewModel.uiState.value.assistantRevertStatesByMessageId["assistant-1"]?.title,
        )
    }

    @Test
    fun `assistant revert warns when another live thread touched the same file`() = runTest {
        val repository = TestRemodexAppRepository()
        repository.snapshot.value = repository.snapshot.value.copy(
            connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
            secureConnection = SecureConnectionSnapshot(
                phaseMessage = "Connected",
                secureState = SecureConnectionState.ENCRYPTED,
                attempt = 1,
            ),
            threads = listOf(
                threadSummary(
                    id = "thread-1",
                    title = "Current",
                    messages = listOf(
                        assistantMessage(
                            id = "assistant-1",
                            threadId = "thread-1",
                            patchPath = "src/shared/File.kt",
                        ),
                    ),
                ),
                threadSummary(
                    id = "thread-2",
                    title = "Sibling",
                    messages = listOf(
                        assistantMessage(
                            id = "assistant-2",
                            threadId = "thread-2",
                            patchPath = "src/shared/File.kt",
                        ),
                    ),
                ),
            ),
            selectedThreadId = "thread-1",
        )
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            RemodexAssistantRevertRiskLevel.WARNING,
            viewModel.uiState.value.assistantRevertStatesByMessageId["assistant-1"]?.riskLevel,
        )
    }

    @Test
    fun `thread completion banner appears when another thread stops running`() = runTest {
        val repository = TestRemodexAppRepository()
        val viewModel = AppViewModel(repository)

        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                threadSummary(id = "thread-active", title = "Current chat"),
                threadSummary(id = "thread-finished", title = "Finished chat", isRunning = true),
            ),
            selectedThreadId = "thread-active",
        )
        advanceUntilIdle()

        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                threadSummary(id = "thread-active", title = "Current chat"),
                threadSummary(
                    id = "thread-finished",
                    title = "Finished chat",
                    isRunning = false,
                    latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
                ),
            ),
            selectedThreadId = "thread-active",
        )
        advanceUntilIdle()

        assertEquals("thread-finished", viewModel.uiState.value.threadCompletionBanner?.threadId)
        assertEquals("Finished chat", viewModel.uiState.value.threadCompletionBanner?.title)
    }

    @Test
    fun `composer keeps running controls when selected thread still reports running despite stale completion state`() = runTest {
        val repository = TestRemodexAppRepository()
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                threadSummary(
                    id = "thread-active",
                    title = "Current chat",
                    isRunning = true,
                    latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
                ),
            ),
            selectedThreadId = "thread-active",
        )
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        assertEquals("Queue follow-up", viewModel.uiState.value.composer.sendLabel)
        assertTrue(viewModel.uiState.value.composer.canStop)
    }

    @Test
    fun `completion haptic signal increments when foreground thread run completes`() = runTest {
        val repository = TestRemodexAppRepository()
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                threadSummary(
                    id = "thread-active",
                    title = "Current chat",
                    isRunning = true,
                ),
            ),
            selectedThreadId = "thread-active",
        )
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.onAppForegroundChanged(true)
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                threadSummary(
                    id = "thread-active",
                    title = "Current chat",
                    isRunning = false,
                    latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
                ),
            ),
            selectedThreadId = "thread-active",
        )
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.completionHapticSignal)
    }

    @Test
    fun `refresh threads refreshes while connected and reuses reconnect recovery when disconnected`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Connected",
                    secureState = SecureConnectionState.ENCRYPTED,
                    attempt = 1,
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.refreshThreads()
        runCurrent()

        assertTrue(viewModel.uiState.value.isRefreshingThreads)
        assertEquals(1, repository.refreshRequests)

        advanceTimeBy(repository.refreshDelayMs)
        runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshingThreads)

        repository.snapshot.value = repository.snapshot.value.copy(
            connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.DISCONNECTED, attempt = 1),
            secureConnection = repository.snapshot.value.secureConnection.copy(
                secureState = SecureConnectionState.TRUSTED_MAC,
            ),
        )
        advanceUntilIdle()

        viewModel.refreshThreads()
        runCurrent()

        assertEquals(1, repository.refreshRequests)
        assertEquals(1, repository.retryConnectionCalls)
        assertFalse(viewModel.uiState.value.isRefreshingThreads)
    }

    @Test
    fun `refresh threads does not duplicate reconnect while auto reconnect is already retrying`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                onboardingCompleted = true,
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Saved pairing ready.",
                    secureState = SecureConnectionState.TRUSTED_MAC,
                ),
                trustedMac = com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation(
                    deviceId = "mac-1",
                    name = "Kai-MBP",
                ),
            )
        }
        val viewModel = AppViewModel(repository).apply {
            autoReconnectAttemptLimitOverride = 1
            autoReconnectBackoffMillisOverride = listOf(10L)
            reconnectSleepChunkMillisOverride = 10L
        }
        advanceUntilIdle()

        viewModel.onAppForegroundChanged(true)
        runCurrent()

        assertEquals(1, repository.retryConnectionCalls)
        assertEquals(RemodexConnectionPhase.RETRYING, viewModel.uiState.value.connectionStatus.phase)

        viewModel.refreshThreads()
        runCurrent()

        assertEquals(1, repository.retryConnectionCalls)
        assertEquals(0, repository.refreshRequests)
        assertFalse(viewModel.uiState.value.isRefreshingThreads)
    }

    @Test
    fun `foreground auto reconnect retries a saved trusted pairing`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                onboardingCompleted = true,
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "The trusted Mac session is temporarily unavailable.",
                    secureState = SecureConnectionState.TRUSTED_MAC,
                ),
                trustedMac = com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation(
                    deviceId = "mac-1",
                    name = "Kai-MBP",
                ),
            )
        }
        val viewModel = AppViewModel(repository).apply {
            autoReconnectAttemptLimitOverride = 1
            autoReconnectBackoffMillisOverride = listOf(10L)
            reconnectSleepChunkMillisOverride = 10L
        }
        advanceUntilIdle()

        viewModel.onAppForegroundChanged(true)
        runCurrent()

        assertEquals(1, repository.retryConnectionCalls)
        assertEquals(RemodexConnectionPhase.RETRYING, viewModel.uiState.value.connectionStatus.phase)
    }

    @Test
    fun `foreground auto reconnect stays idle when secure state disables auto reconnect`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                onboardingCompleted = true,
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Secure reconnect could not be restored from the saved session. Try reconnecting again.",
                    secureState = SecureConnectionState.LIVE_SESSION_UNRESOLVED,
                    autoReconnectAllowed = false,
                ),
                trustedMac = com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation(
                    deviceId = "mac-1",
                    name = "Kai-MBP",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.onAppForegroundChanged(true)
        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals(0, repository.retryConnectionCalls)
        assertEquals(RemodexConnectionPhase.DISCONNECTED, viewModel.uiState.value.connectionStatus.phase)
    }

    @Test
    fun `foreground auto reconnect keeps the same loop alive while reconnect is in progress`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                onboardingCompleted = true,
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "The trusted Mac session is temporarily unavailable.",
                    secureState = SecureConnectionState.LIVE_SESSION_UNRESOLVED,
                    autoReconnectAllowed = true,
                ),
                trustedMac = com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation(
                    deviceId = "mac-1",
                    name = "Kai-MBP",
                ),
            )
            onRetryConnection = {
                snapshot.value = snapshot.value.copy(
                    connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.RETRYING, attempt = retryConnectionCalls),
                    secureConnection = snapshot.value.secureConnection.copy(
                        secureState = SecureConnectionState.RECONNECTING,
                        attempt = retryConnectionCalls,
                    ),
                )
            }
        }
        val viewModel = AppViewModel(repository).apply {
            autoReconnectAttemptLimitOverride = 2
            autoReconnectBackoffMillisOverride = listOf(10L)
            reconnectSleepChunkMillisOverride = 10L
        }
        advanceUntilIdle()

        viewModel.onAppForegroundChanged(true)
        runCurrent()
        assertEquals(1, repository.retryConnectionCalls)

        repository.snapshot.value = repository.snapshot.value.copy(
            connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.DISCONNECTED, attempt = 1),
            secureConnection = repository.snapshot.value.secureConnection.copy(
                secureState = SecureConnectionState.LIVE_SESSION_UNRESOLVED,
                phaseMessage = "The trusted Mac is offline right now.",
                autoReconnectAllowed = true,
            ),
        )
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(1, repository.retryConnectionCalls)

        viewModel.onAppForegroundChanged(false)
        advanceTimeBy(20)
        runCurrent()
    }

    @Test
    fun `foreground auto reconnect stops after three attempts until manual reconnect`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                onboardingCompleted = true,
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "The trusted Mac is offline right now.",
                    secureState = SecureConnectionState.LIVE_SESSION_UNRESOLVED,
                    autoReconnectAllowed = true,
                ),
                trustedMac = com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation(
                    deviceId = "mac-1",
                    name = "Kai-MBP",
                ),
            )
        }
        val viewModel = AppViewModel(repository).apply {
            autoReconnectBackoffMillisOverride = listOf(10L)
            reconnectSleepChunkMillisOverride = 10L
        }
        advanceUntilIdle()

        viewModel.onAppForegroundChanged(true)
        advanceTimeBy(2_000)
        advanceUntilIdle()

        assertEquals(3, repository.retryConnectionCalls)

        viewModel.onAppForegroundChanged(false)
        advanceUntilIdle()
        viewModel.onAppForegroundChanged(true)
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals(3, repository.retryConnectionCalls)

        viewModel.retryConnection()
        advanceUntilIdle()

        assertEquals(4, repository.retryConnectionCalls)
    }

    @Test
    fun `switch bridge preempts active reconnect recovery`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                onboardingCompleted = true,
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.RETRYING, attempt = 2),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "The trusted Mac is offline right now.",
                    secureState = SecureConnectionState.RECONNECTING,
                    attempt = 2,
                    autoReconnectAllowed = true,
                ),
                trustedMac = com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation(
                    deviceId = "mac-1",
                    name = "Kai-MBP",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.activateBridgeProfile("profile-2")
        runCurrent()

        assertEquals(listOf("profile-2"), repository.activateBridgeProfileRequests)
    }

    @Test
    fun `cancelling trusted mac switch aborts activation after stopping the active run`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            stopTurnDelayMs = 5_000L
            snapshot.value = snapshot.value.copy(
                bridgeProfiles = listOf(
                    bridgeProfile(
                        profileId = "profile-2",
                        deviceId = "mac-2",
                        isActive = false,
                    ),
                ),
                threads = listOf(
                    threadSummary(
                        id = "thread-running",
                        title = "Running thread",
                        isRunning = true,
                    ),
                ),
                selectedThreadId = "thread-running",
                selectedThreadSnapshot = threadSummary(
                    id = "thread-running",
                    title = "Running thread",
                    isRunning = true,
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.switchToTrustedMac("mac-2")
        runCurrent()
        assertTrue(viewModel.uiState.value.isSwitchingMac)

        viewModel.requestMacSwitchCancellation()
        advanceUntilIdle()

        assertEquals(listOf("thread-running"), repository.stopTurnRequests)
        assertTrue(repository.activateBridgeProfileRequests.isEmpty())
        assertFalse(viewModel.uiState.value.isSwitchingMac)
        assertNull(viewModel.uiState.value.switchingMacDeviceId)
        assertNull(viewModel.uiState.value.macSwitchNotice)
    }

    @Test
    fun `stop turn shows banner while interruptible turn id is still pending`() = runTest {
        val runningThread = threadSummary(
            id = "thread-running",
            title = "Running thread",
            isRunning = true,
        )
        val repository = TestRemodexAppRepository().apply {
            stopTurnResult = StopTurnResult.INTERRUPT_TURN_ID_PENDING
            snapshot.value = snapshot.value.copy(
                threads = listOf(runningThread),
                selectedThreadId = runningThread.id,
                selectedThreadSnapshot = runningThread,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.stopTurn()
        advanceUntilIdle()

        assertEquals(listOf("thread-running"), repository.stopTurnRequests)
        assertEquals(
            "Still waiting for the active run id. Try Stop again in a moment.",
            viewModel.uiState.value.transientBanner,
        )
    }

    @Test
    fun `cancelling scanned mac switch aborts deferred pairing`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            pairWithQrPayloadDelayMs = 5_000L
        }
        val viewModel = AppViewModel(repository)
        val payload = PairingQrPayload(
            v = 2,
            relay = "wss://relay.example",
            sessionId = "session-1",
            macDeviceId = "mac-new",
            macIdentityPublicKey = "public-key",
            expiresAt = 123L,
        )
        advanceUntilIdle()

        viewModel.switchToScannedMac(payload)
        runCurrent()
        assertTrue(viewModel.uiState.value.isSwitchingMac)

        viewModel.requestMacSwitchCancellation()
        advanceUntilIdle()

        assertTrue(repository.pairWithQrPayloadRequests.isEmpty())
        assertFalse(viewModel.uiState.value.isSwitchingMac)
        assertNull(viewModel.uiState.value.switchingMacDeviceId)
        assertNull(viewModel.uiState.value.macSwitchNotice)
    }

    @Test
    fun `manual disconnect suppresses foreground auto reconnect`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                onboardingCompleted = true,
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Saved pairing ready.",
                    secureState = SecureConnectionState.TRUSTED_MAC,
                ),
                trustedMac = com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation(
                    deviceId = "mac-1",
                    name = "Kai-MBP",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.disconnect()
        advanceUntilIdle()
        viewModel.onAppForegroundChanged(true)
        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals(1, repository.disconnectCalls)
        assertEquals(0, repository.retryConnectionCalls)
    }

    @Test
    fun `opening the scanner cancels reconnect and disconnects the active session`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                onboardingCompleted = true,
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Connected",
                    secureState = SecureConnectionState.ENCRYPTED,
                    attempt = 1,
                ),
                trustedMac = com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation(
                    deviceId = "mac-1",
                    name = "Kai-MBP",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.prepareForManualScan()
        advanceUntilIdle()
        viewModel.onAppForegroundChanged(true)
        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals(1, repository.disconnectCalls)
        assertEquals(0, repository.retryConnectionCalls)
    }

    @Test
    fun `file autocomplete matches iOS debounce and closes after confirmed mention prose`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            fileSearchResults = listOf(
                RemodexFuzzyFileMatch(
                    root = "/tmp/remodex",
                    path = "app/src/main/java/com/emanueledipietro/remodex/feature/turn/ConversationScreen.kt",
                    fileName = "ConversationScreen.kt",
                    score = 1.0,
                ),
            )
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Autocomplete thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("@co")
        runCurrent()

        assertEquals(RemodexComposerAutocompletePanel.FILES, viewModel.uiState.value.composer.autocomplete.panel)
        assertTrue(viewModel.uiState.value.composer.autocomplete.isFileLoading)

        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(
            listOf("ConversationScreen.kt"),
            viewModel.uiState.value.composer.autocomplete.fileItems.map(RemodexFuzzyFileMatch::fileName),
        )

        viewModel.selectFileAutocomplete(repository.fileSearchResults.first())
        advanceUntilIdle()
        viewModel.updateComposerInput("${viewModel.uiState.value.composer.draftText}notes")
        advanceUntilIdle()

        assertEquals(RemodexComposerAutocompletePanel.NONE, viewModel.uiState.value.composer.autocomplete.panel)
        assertEquals(1, viewModel.uiState.value.composer.mentionedFiles.size)
    }

    @Test
    fun `skill autocomplete matches iOS debounce and hides under minimum query length`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            skillResults = listOf(
                RemodexSkillMetadata(
                    name = "android-ui-review",
                    description = "Review Android UI parity before shipping.",
                    enabled = true,
                ),
                RemodexSkillMetadata(
                    name = "bridge-protocol-check",
                    description = "Validate bridge JSON-RPC compatibility.",
                    enabled = true,
                ),
                RemodexSkillMetadata(
                    name = "disabled-skill",
                    description = "Should never appear.",
                    enabled = false,
                ),
            )
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Skills thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("\$an")
        runCurrent()

        assertEquals(RemodexComposerAutocompletePanel.SKILLS, viewModel.uiState.value.composer.autocomplete.panel)
        assertTrue(viewModel.uiState.value.composer.autocomplete.isSkillLoading)

        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(
            listOf("android-ui-review"),
            viewModel.uiState.value.composer.autocomplete.skillItems.map(RemodexSkillMetadata::name),
        )

        viewModel.updateComposerInput("\$a")
        advanceUntilIdle()

        assertEquals(RemodexComposerAutocompletePanel.NONE, viewModel.uiState.value.composer.autocomplete.panel)
    }

    @Test
    fun `slash command ordering and status action match iOS behavior`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Commands thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/")
        advanceUntilIdle()

        assertEquals(
            listOf(
                RemodexSlashCommand.CODE_REVIEW,
                RemodexSlashCommand.FORK,
                RemodexSlashCommand.STATUS,
                RemodexSlashCommand.COMPACT,
                RemodexSlashCommand.PLAN,
                RemodexSlashCommand.SUBAGENTS,
                RemodexSlashCommand.PS,
                RemodexSlashCommand.STOP,
            ),
            viewModel.uiState.value.composer.autocomplete.slashCommands,
        )

        viewModel.updateComposerInput("/status")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.STATUS)
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.refreshUsageStatusRequests)
        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertEquals(RemodexComposerAutocompletePanel.NONE, viewModel.uiState.value.composer.autocomplete.panel)
        assertEquals(1L, viewModel.uiState.value.statusSheetSignal)
    }

    @Test
    fun `send prompt routes standalone status command to the existing usage popover`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Commands thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/status")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.refreshUsageStatusRequests)
        assertTrue(repository.sentPrompts.isEmpty())
        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertEquals(1L, viewModel.uiState.value.statusSheetSignal)
    }

    @Test
    fun `selecting ps opens the background terminal sheet signal and clears the composer token`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Commands thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/ps")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.PS)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertEquals(RemodexComposerAutocompletePanel.NONE, viewModel.uiState.value.composer.autocomplete.panel)
        assertEquals(1L, viewModel.uiState.value.backgroundTerminalSheetSignal)
        assertEquals(listOf("thread-1"), repository.activeThreadSyncRequests)
    }

    @Test
    fun `send prompt routes standalone ps command to the background terminal sheet`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Commands thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/ps")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.backgroundTerminalSheetSignal)
        assertEquals(listOf("thread-1"), repository.activeThreadSyncRequests)
        assertTrue(repository.sentPrompts.isEmpty())
        assertEquals("", viewModel.uiState.value.composer.draftText)
    }

    @Test
    fun `send prompt routes standalone stop command through clean background terminals api`() = runTest {
        val backgroundItemId = "command-bg"
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Commands thread",
                        messages = listOf(
                            RemodexConversationItem(
                                id = backgroundItemId,
                                itemId = backgroundItemId,
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "running bash -lc \"sleep 30\"",
                                orderIndex = 1,
                            ),
                        ),
                    ),
                ),
                selectedThreadId = "thread-1",
            )
            commandDetails.value = mapOf(
                backgroundItemId to RemodexCommandExecutionDetails(
                    fullCommand = "bash -lc \"sleep 30\"",
                    liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                    source = RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP,
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/stop")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.cleanBackgroundTerminalsRequests)
        assertTrue(repository.sentPrompts.isEmpty())
        assertEquals("", viewModel.uiState.value.composer.draftText)
    }

    @Test
    fun `send prompt stop still cleans background terminals when local thread snapshot is stale`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Commands thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/stop")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.cleanBackgroundTerminalsRequests)
        assertTrue(repository.sentPrompts.isEmpty())
        assertEquals("", viewModel.uiState.value.composer.draftText)
    }

    @Test
    fun `compact slash command inserts the real command token into the draft`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Compact thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/com")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.COMPACT)
        advanceUntilIdle()

        assertEquals("/compact ", viewModel.uiState.value.composer.draftText)
        assertEquals(RemodexComposerAutocompletePanel.NONE, viewModel.uiState.value.composer.autocomplete.panel)
    }

    @Test
    fun `send prompt routes standalone compact command through compact thread api`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Compact thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/compact")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.compactThreadRequests)
        assertTrue(repository.sentPrompts.isEmpty())
        assertEquals("", viewModel.uiState.value.composer.draftText)
    }

    @Test
    fun `send prompt blocks standalone compact command while thread is running`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Compact thread",
                        isRunning = true,
                    ),
                ),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/compact")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertTrue(repository.compactThreadRequests.isEmpty())
        assertTrue(repository.sentPrompts.isEmpty())
        assertEquals("/compact", viewModel.uiState.value.composer.draftText)
        assertEquals(
            "Wait for the current response to finish first.",
            viewModel.uiState.value.composer.composerMessage,
        )
    }

    @Test
    fun `completed context compaction refreshes usage for selected thread`() = runTest {
        val repository = TestRemodexAppRepository()
        val streamingThread = threadSummary(
            id = "thread-1",
            title = "Compact thread",
            messages = listOf(
                contextCompactionMessage(
                    id = "compaction-1",
                    threadId = "thread-1",
                    isStreaming = true,
                    text = "Compacting context...",
                ),
            ),
        )
        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(streamingThread),
            selectedThreadId = "thread-1",
        )
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        repository.refreshUsageStatusRequests.clear()

        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                streamingThread.copy(
                    isRunning = false,
                    messages = listOf(
                        contextCompactionMessage(
                            id = "compaction-1",
                            threadId = "thread-1",
                            isStreaming = false,
                            text = "Context compacted",
                        ),
                    ),
                ),
            ),
            selectedThreadId = "thread-1",
        )
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.refreshUsageStatusRequests)
    }

    @Test
    fun `slash code review selection matches ios behavior`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Review thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/review")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.CODE_REVIEW)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertEquals(RemodexComposerAutocompletePanel.REVIEW_TARGETS, viewModel.uiState.value.composer.autocomplete.panel)
        assertEquals(null, viewModel.uiState.value.composer.reviewSelection?.request)
    }

    @Test
    fun `slash code review refuses conflicting draft content like ios`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Review thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("Please review this /review")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.CODE_REVIEW)
        advanceUntilIdle()

        assertEquals("Please review this", viewModel.uiState.value.composer.draftText)
        assertNull(viewModel.uiState.value.composer.reviewSelection)
        assertEquals(RemodexComposerAutocompletePanel.NONE, viewModel.uiState.value.composer.autocomplete.panel)
    }

    @Test
    fun `sending code review uses repository review start and not prompt send`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Review thread",
                        projectPath = "/tmp/remodex-review",
                    ),
                ),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/review")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.CODE_REVIEW)
        viewModel.selectCodeReviewTarget(RemodexComposerReviewTarget.UNCOMMITTED_CHANGES)
        advanceUntilIdle()

        viewModel.sendPrompt()
        advanceUntilIdle()

        assertTrue(repository.sentPrompts.isEmpty())
        assertEquals(
            listOf(
                "thread-1" to RemodexCodeReviewRequest(
                    target = RemodexComposerReviewTarget.UNCOMMITTED_CHANGES,
                ),
            ),
            repository.codeReviewRequests,
        )
        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertNull(viewModel.uiState.value.composer.reviewSelection)
        assertEquals("thread-1", viewModel.uiState.value.selectedThread?.id)
        assertEquals(1L, viewModel.uiState.value.composerSendDismissSignal)
        assertEquals(1L, viewModel.uiState.value.composerSendAnchorSignal)
    }

    @Test
    fun `review commit selection loads commits and sends commit request on current thread`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            gitCommitResults = listOf(
                RemodexGitCommit(
                    sha = "abc123def456",
                    message = "Fix flaky /review behavior",
                    author = "Kai",
                ),
            )
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Review thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/review")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.CODE_REVIEW)
        viewModel.selectCodeReviewTarget(RemodexComposerReviewTarget.COMMIT)
        advanceUntilIdle()
        viewModel.selectCodeReviewCommit(repository.gitCommitResults.first())
        advanceUntilIdle()

        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "thread-1" to RemodexCodeReviewRequest(
                    target = RemodexComposerReviewTarget.COMMIT,
                    commitSha = "abc123def456",
                    commitTitle = "Fix flaky /review behavior",
                ),
            ),
            repository.codeReviewRequests,
        )
        assertTrue(repository.sentPrompts.isEmpty())
    }

    @Test
    fun `inline review prompt sends custom instructions request`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Review thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/review please audit dependencies")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "thread-1" to RemodexCodeReviewRequest(
                    target = RemodexComposerReviewTarget.CUSTOM_INSTRUCTIONS,
                    customInstructions = "please audit dependencies",
                ),
            ),
            repository.codeReviewRequests,
        )
        assertTrue(repository.sentPrompts.isEmpty())
    }

    @Test
    fun `review send is blocked while current thread is running`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Review thread",
                        isRunning = true,
                    ),
                ),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/review something important")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertTrue(repository.codeReviewRequests.isEmpty())
        assertEquals(
            "Wait for the current run to finish before starting a code review.",
            viewModel.uiState.value.composer.composerMessage,
        )
    }

    @Test
    fun `create thread explicitly selects the new thread when repository leaves selection unchanged`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Connected",
                    secureState = SecureConnectionState.ENCRYPTED,
                    attempt = 1,
                ),
                threads = listOf(threadSummary(id = "thread-1", title = "Existing thread")),
                selectedThreadId = "thread-1",
                selectedThreadSnapshot = threadSummary(id = "thread-1", title = "Existing thread"),
            )
            createThreadSelectsCreatedThread = false
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.createThread("/tmp/new-thread")
        advanceUntilIdle()

        assertEquals(listOf("/tmp/new-thread" to null), repository.createThreadRequests)
        assertEquals(listOf("thread-created"), repository.selectedThreadRequests)
        assertEquals("thread-created", viewModel.uiState.value.selectedThread?.id)
        assertEquals("/tmp/new-thread", viewModel.uiState.value.selectedThread?.projectPath)
    }

    @Test
    fun `create thread keeps showing the current thread while creation is still in flight`() = runTest {
        val existingThread = threadSummary(id = "thread-1", title = "Existing thread")
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Connected",
                    secureState = SecureConnectionState.ENCRYPTED,
                    attempt = 1,
                ),
                threads = listOf(existingThread),
                selectedThreadId = existingThread.id,
                selectedThreadSnapshot = existingThread,
            )
            createThreadDelayMs = 1_000L
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.createThread("/tmp/new-thread")
        runCurrent()
        advanceTimeBy(100L)

        assertEquals(listOf("/tmp/new-thread" to null), repository.createThreadRequests)
        assertTrue(viewModel.uiState.value.isCreatingThread)
        assertEquals("thread-1", viewModel.uiState.value.selectedThread?.id)
        assertEquals("Existing thread", viewModel.uiState.value.selectedThread?.title)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCreatingThread)
        assertEquals("thread-created", viewModel.uiState.value.selectedThread?.id)
        assertEquals("/tmp/new-thread", viewModel.uiState.value.selectedThread?.projectPath)
    }

    @Test
    fun `create thread completion callback waits for the new thread id`() = runTest {
        val existingThread = threadSummary(id = "thread-1", title = "Existing thread")
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Connected",
                    secureState = SecureConnectionState.ENCRYPTED,
                    attempt = 1,
                ),
                threads = listOf(existingThread),
                selectedThreadId = existingThread.id,
                selectedThreadSnapshot = existingThread,
            )
            createThreadDelayMs = 1_000L
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        var createdThreadId: String? = "sentinel"

        viewModel.createThread("/tmp/new-thread") { createdThreadId = it }
        createdThreadId = null
        runCurrent()
        advanceTimeBy(100L)

        assertNull(createdThreadId)

        advanceUntilIdle()

        assertEquals("thread-created", createdThreadId)
        assertEquals("thread-created", viewModel.uiState.value.selectedThread?.id)
    }

    @Test
    fun `create thread completion callback stays null when creation fails`() = runTest {
        val existingThread = threadSummary(id = "thread-1", title = "Existing thread")
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                secureConnection = SecureConnectionSnapshot(
                    phaseMessage = "Connected",
                    secureState = SecureConnectionState.ENCRYPTED,
                    attempt = 1,
                ),
                threads = listOf(existingThread),
                selectedThreadId = existingThread.id,
                selectedThreadSnapshot = existingThread,
            )
            createThreadDelayMs = 1_000L
            createThreadError = IllegalStateException("Bridge down")
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        var createdThreadId = "sentinel"

        viewModel.createThread("/tmp/new-thread") { createdThreadId = it ?: "failed" }
        runCurrent()
        advanceTimeBy(100L)

        assertTrue(viewModel.uiState.value.isCreatingThread)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCreatingThread)
        assertEquals("failed", createdThreadId)
        assertEquals("thread-1", viewModel.uiState.value.selectedThread?.id)
    }

    @Test
    fun `send clears composer immediately and restores full state on failure`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Send thread")),
                selectedThreadId = "thread-1",
            )
            sendPromptDelayMs = 100
            sendPromptError = IllegalStateException("Bridge down")
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("Ship this")
        viewModel.addAttachments(
            listOf(
                RemodexComposerAttachment(
                    id = "attachment-1",
                    uriString = "content://attachments/1",
                    displayName = "Shot.png",
                ),
            ),
        )
        viewModel.selectSlashCommand(RemodexSlashCommand.SUBAGENTS)
        advanceUntilIdle()

        val initialDismissSignal = viewModel.uiState.value.composerSendDismissSignal
        val initialAnchorSignal = viewModel.uiState.value.composerSendAnchorSignal

        viewModel.sendPrompt()
        runCurrent()

        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertTrue(viewModel.uiState.value.composer.attachments.isEmpty())
        assertFalse(viewModel.uiState.value.composer.isSubagentsSelectionArmed)
        assertEquals(initialDismissSignal + 1, viewModel.uiState.value.composerSendDismissSignal)
        assertEquals(initialAnchorSignal + 1, viewModel.uiState.value.composerSendAnchorSignal)

        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals("Ship this", viewModel.uiState.value.composer.draftText)
        assertEquals(1, viewModel.uiState.value.composer.attachments.size)
        assertTrue(viewModel.uiState.value.composer.isSubagentsSelectionArmed)
        assertEquals("Bridge down", viewModel.uiState.value.composer.composerMessage)
        assertEquals(initialAnchorSignal + 1, viewModel.uiState.value.composerSendAnchorSignal)
    }

    @Test
    fun `send emits dismiss and anchor signals before repository send completes`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Send thread")),
                selectedThreadId = "thread-1",
            )
            sendPromptDelayMs = 100
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("Ship this")
        advanceUntilIdle()

        val initialDismissSignal = viewModel.uiState.value.composerSendDismissSignal
        val initialAnchorSignal = viewModel.uiState.value.composerSendAnchorSignal

        viewModel.sendPrompt()
        runCurrent()

        assertEquals(initialDismissSignal + 1, viewModel.uiState.value.composerSendDismissSignal)
        assertEquals(initialAnchorSignal + 1, viewModel.uiState.value.composerSendAnchorSignal)

        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals(initialDismissSignal + 1, viewModel.uiState.value.composerSendDismissSignal)
        assertEquals(initialAnchorSignal + 1, viewModel.uiState.value.composerSendAnchorSignal)
    }

    @Test
    fun `plan mode send starts a local plan composer session`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Plan thread",
                        runtimeConfig = RemodexRuntimeConfig(planningMode = RemodexPlanningMode.PLAN),
                        messages = listOf(
                            RemodexConversationItem(
                                id = "anchor",
                                speaker = ConversationSpeaker.USER,
                                text = "Before plan",
                            ),
                        ),
                    ),
                ),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("Make a plan")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals("anchor", viewModel.uiState.value.planComposerSession?.anchorMessageId)
        assertEquals(
            listOf(Triple("thread-1", "Make a plan", emptyList<RemodexComposerAttachment>())),
            repository.sentPrompts,
        )
    }

    @Test
    fun `plan mode session clears when plan send fails`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Plan thread",
                        runtimeConfig = RemodexRuntimeConfig(planningMode = RemodexPlanningMode.PLAN),
                    ),
                ),
                selectedThreadId = "thread-1",
            )
            sendPromptError = IllegalStateException("Bridge down")
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("Make a plan")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.planComposerSession)
        assertEquals("Bridge down", viewModel.uiState.value.composer.composerMessage)
    }

    @Test
    fun `plan follow up sends fixed prompt and clears local session`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Plan thread",
                        runtimeConfig = RemodexRuntimeConfig(planningMode = RemodexPlanningMode.PLAN),
                    ),
                ),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("Make a plan")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        viewModel.sendPlanFollowUp(
            prompt = "Implement plan",
            shouldExitPlanMode = true,
        )
        advanceUntilIdle()

        assertEquals(
            Triple("thread-1", "Implement plan", emptyList<RemodexComposerAttachment>()),
            repository.sentPrompts.last(),
        )
        assertEquals(
            listOf("thread-1" to RemodexPlanningMode.AUTO),
            repository.setPlanningModeRequests,
        )
        assertEquals(
            listOf(null, RemodexPlanningMode.AUTO),
            repository.sentPromptPlanningModeOverrides,
        )
        assertNull(viewModel.uiState.value.planComposerSession)
    }

    @Test
    fun `plan revision follow up stays in plan mode`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Plan thread",
                        runtimeConfig = RemodexRuntimeConfig(planningMode = RemodexPlanningMode.PLAN),
                    ),
                ),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("Make a plan")
        advanceUntilIdle()
        viewModel.sendPrompt()
        advanceUntilIdle()

        viewModel.sendPlanFollowUp(
            prompt = "Please make it more detailed",
            shouldExitPlanMode = false,
        )
        advanceUntilIdle()

        assertTrue(repository.setPlanningModeRequests.isEmpty())
        assertEquals(
            Triple("thread-1", "Please make it more detailed", emptyList<RemodexComposerAttachment>()),
            repository.sentPrompts.last(),
        )
        assertEquals(
            listOf(null, null),
            repository.sentPromptPlanningModeOverrides,
        )
    }

    @Test
    fun `thread completion auto sends the next queued draft`() = runTest {
        val queuedDraft = RemodexQueuedDraft(
            id = "draft-1",
            text = "Queued follow-up",
            createdAtEpochMs = 1L,
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Queued thread",
                        isRunning = true,
                        queuedDraftItems = listOf(queuedDraft),
                    ),
                ),
                selectedThreadId = "thread-1",
                selectedThreadSnapshot = threadSummary(
                    id = "thread-1",
                    title = "Queued thread",
                    isRunning = true,
                    queuedDraftItems = listOf(queuedDraft),
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                threadSummary(
                    id = "thread-1",
                    title = "Queued thread",
                    isRunning = false,
                    latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
                    queuedDraftItems = listOf(queuedDraft),
                ),
            ),
            selectedThreadSnapshot = threadSummary(
                id = "thread-1",
                title = "Queued thread",
                isRunning = false,
                latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
                queuedDraftItems = listOf(queuedDraft),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("thread-1" to "draft-1"), repository.sentQueuedDrafts)
    }

    @Test
    fun `restoring latest queued draft overwrites composer and keeps plan mode`() = runTest {
        val earlierDraft = RemodexQueuedDraft(
            id = "draft-1",
            text = "Earlier queued",
            createdAtEpochMs = 1L,
        )
        val latestDraft = RemodexQueuedDraft(
            id = "draft-2",
            text = "Latest queued",
            createdAtEpochMs = 2L,
            attachments = listOf(
                RemodexComposerAttachment(
                    id = "attachment-1",
                    uriString = "content://queued/image",
                    displayName = "queued.jpg",
                ),
            ),
            planningMode = RemodexPlanningMode.PLAN,
        )
        val thread = threadSummary(
            id = "thread-1",
            title = "Queued thread",
            queuedDraftItems = listOf(earlierDraft, latestDraft),
            runtimeConfig = RemodexRuntimeConfig(planningMode = RemodexPlanningMode.AUTO),
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(thread),
                selectedThreadId = thread.id,
                selectedThreadSnapshot = thread,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("stale draft")
        advanceUntilIdle()
        viewModel.restoreLatestQueuedDraftToComposer()
        advanceUntilIdle()

        assertEquals("Latest queued", viewModel.uiState.value.composer.draftText)
        assertEquals(latestDraft.attachments, viewModel.uiState.value.composer.attachments)
        assertEquals(listOf("thread-1" to RemodexPlanningMode.PLAN), repository.setPlanningModeRequests)
        assertEquals(listOf("draft-1"), repository.snapshot.value.selectedThread?.queuedDraftItems?.map(RemodexQueuedDraft::id))
    }

    @Test
    fun `restoring a selected queued draft restores raw composer state`() = runTest {
        val earlierDraft = RemodexQueuedDraft(
            id = "draft-1",
            text = "Earlier queued",
            createdAtEpochMs = 1L,
        )
        val selectedDraft = RemodexQueuedDraft(
            id = "draft-2",
            text = "Inspect @app/src/main/java/com/emanueledipietro/remodex/feature/turn/ConversationScreen.kt",
            createdAtEpochMs = 2L,
            attachments = listOf(
                RemodexComposerAttachment(
                    id = "attachment-1",
                    uriString = "content://queued/image",
                    displayName = "queued.jpg",
                ),
            ),
            planningMode = RemodexPlanningMode.PLAN,
            rawInput = "Inspect @ConversationScreen.kt",
            rawMentionedFiles = listOf(
                RemodexComposerMentionedFile(
                    id = "file-1",
                    fileName = "ConversationScreen.kt",
                    path = "app/src/main/java/com/emanueledipietro/remodex/feature/turn/ConversationScreen.kt",
                ),
            ),
            rawMentionedSkills = listOf(
                RemodexComposerMentionedSkill(
                    id = "skill-1",
                    name = "gh-address-comments",
                    path = "/tmp/gh-address-comments/SKILL.md",
                    description = "Address review feedback",
                ),
            ),
            rawSubagentsSelectionArmed = true,
        )
        val thread = threadSummary(
            id = "thread-1",
            title = "Queued thread",
            queuedDraftItems = listOf(earlierDraft, selectedDraft),
            runtimeConfig = RemodexRuntimeConfig(planningMode = RemodexPlanningMode.AUTO),
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(thread),
                selectedThreadId = thread.id,
                selectedThreadSnapshot = thread,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.restoreQueuedDraftToComposer("draft-2")
        advanceUntilIdle()

        assertEquals("Inspect @ConversationScreen.kt", viewModel.uiState.value.composer.draftText)
        assertEquals(selectedDraft.attachments, viewModel.uiState.value.composer.attachments)
        assertEquals(selectedDraft.rawMentionedFiles, viewModel.uiState.value.composer.mentionedFiles)
        assertEquals(selectedDraft.rawMentionedSkills, viewModel.uiState.value.composer.mentionedSkills)
        assertTrue(viewModel.uiState.value.composer.isSubagentsSelectionArmed)
        assertEquals(listOf("thread-1" to RemodexPlanningMode.PLAN), repository.setPlanningModeRequests)
        assertEquals(listOf("draft-1"), repository.snapshot.value.selectedThread?.queuedDraftItems?.map(RemodexQueuedDraft::id))
        assertEquals(listOf("thread-1" to "draft-2"), repository.poppedQueuedDrafts)
    }

    @Test
    fun `queued draft auto send failure pauses queue and resume retries oldest draft`() = runTest {
        val firstDraft = RemodexQueuedDraft(
            id = "draft-1",
            text = "First queued",
            createdAtEpochMs = 1L,
        )
        val secondDraft = RemodexQueuedDraft(
            id = "draft-2",
            text = "Second queued",
            createdAtEpochMs = 2L,
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Queued thread",
                        isRunning = true,
                        queuedDraftItems = listOf(firstDraft, secondDraft),
                    ),
                ),
                selectedThreadId = "thread-1",
                selectedThreadSnapshot = threadSummary(
                    id = "thread-1",
                    title = "Queued thread",
                    isRunning = true,
                    queuedDraftItems = listOf(firstDraft, secondDraft),
                ),
            )
            sendQueuedDraftError = IllegalStateException("temporary outage")
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        repository.snapshot.value = repository.snapshot.value.copy(
            threads = listOf(
                threadSummary(
                    id = "thread-1",
                    title = "Queued thread",
                    isRunning = false,
                    latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
                    queuedDraftItems = listOf(firstDraft, secondDraft),
                ),
            ),
            selectedThreadSnapshot = threadSummary(
                id = "thread-1",
                title = "Queued thread",
                isRunning = false,
                latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
                queuedDraftItems = listOf(firstDraft, secondDraft),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("thread-1" to "draft-1"), repository.sentQueuedDrafts)
        assertTrue(viewModel.uiState.value.composer.isQueuePaused)
        assertEquals("temporary outage", viewModel.uiState.value.composer.queuePauseMessage)

        repository.sendQueuedDraftError = null
        viewModel.resumeQueue()
        advanceUntilIdle()

        assertEquals(
            listOf("thread-1" to "draft-1", "thread-1" to "draft-1"),
            repository.sentQueuedDrafts,
        )
        assertFalse(viewModel.uiState.value.composer.isQueuePaused)
    }

    @Test
    fun `load repository diff exposes loading state and returns patch to caller`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Diff thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    repoRoot = "/tmp/remodex",
                    diffTotals = RemodexGitDiffTotals(additions = 4, deletions = 2),
                ),
            )
            gitDiffDelayMs = 100
            gitDiffResult = RemodexGitRepoDiff(
                patch = """
                    diff --git a/app/src/main.kt b/app/src/main.kt
                    --- a/app/src/main.kt
                    +++ b/app/src/main.kt
                    @@ -1 +1 @@
                    -old
                    +new
                """.trimIndent(),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        var loadedDiff: RemodexGitRepoDiff? = null
        viewModel.loadRepositoryDiff(onLoaded = { diff ->
            loadedDiff = diff
        })
        runCurrent()

        assertFalse(viewModel.uiState.value.composer.gitState.isLoading)
        assertEquals(1, repository.gitDiffRequests)

        advanceTimeBy(100)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.composer.gitState.isLoading)
        assertEquals(repository.gitDiffResult.patch, loadedDiff?.patch)
        assertEquals(null, viewModel.uiState.value.composer.gitState.errorMessage)
    }

    @Test
    fun `load repository diff keeps sheet closed when patch is empty`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Diff thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    repoRoot = "/tmp/remodex",
                    diffTotals = RemodexGitDiffTotals(additions = 1, deletions = 0),
                ),
            )
            gitDiffResult = RemodexGitRepoDiff()
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        var callbackCount = 0
        viewModel.loadRepositoryDiff(onLoaded = {
            callbackCount += 1
        })
        advanceUntilIdle()

        assertEquals(0, callbackCount)
        assertEquals(
            "There are no repository changes to show.",
            viewModel.uiState.value.gitSyncAlert?.message,
        )
    }

    @Test
    fun `passive git state refresh errors are swallowed without a banner or alert`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateError = IllegalStateException(
                "fatal: not a git repository (or any of the parent directories): .git",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.refreshGitState()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.conversationBanner)
        assertEquals(null, viewModel.uiState.value.gitSyncAlert)
        assertFalse(viewModel.uiState.value.composer.gitState.isLoading)
        assertEquals(null, viewModel.uiState.value.composer.gitState.errorMessage)
    }

    @Test
    fun `ui state exposes repo refresh signal for latest repo affecting message`() = runTest {
        val repoMessage = RemodexConversationItem(
            id = "file-change-2",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.FILE_CHANGE,
            text = "updated diff",
            isStreaming = true,
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Git thread",
                        messages = listOf(
                            RemodexConversationItem(
                                id = "chat-1",
                                speaker = ConversationSpeaker.ASSISTANT,
                                text = "Not repo related",
                            ),
                            RemodexConversationItem(
                                id = "file-change-1",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.FILE_CHANGE,
                                text = "old diff",
                            ),
                            repoMessage,
                        ),
                    ),
                ),
                selectedThreadId = "thread-1",
            )
        }

        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            "file-change-2|12|true",
            viewModel.uiState.value.repoRefreshSignal,
        )
    }

    @Test
    fun `scheduled git state refresh debounces repeated repo updates`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        repository.gitSyncRequests = 0

        viewModel.scheduleGitStateRefresh()
        viewModel.scheduleGitStateRefresh()
        advanceTimeBy(349)
        runCurrent()

        assertEquals(0, repository.gitSyncRequests)

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(1, repository.gitSyncRequests)
    }

    @Test
    fun `active git actions surface a dismissible git alert`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            checkoutGitBranchError = IllegalStateException(
                "Cannot switch branches: this branch is already open in another worktree.",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertEquals("Branch Switch Failed", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals(
            "Cannot switch branches: this branch is already open in another worktree.",
            viewModel.uiState.value.gitSyncAlert?.message,
        )

        viewModel.dismissGitSyncAlert()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.gitSyncAlert)
    }

    @Test
    fun `checking out an elsewhere branch shows open chat alert when matching thread exists`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Feature thread",
                        projectPath = "/tmp/remodex/.codex/worktrees/feature/test",
                    ),
                    threadSummary(
                        id = "thread-2",
                        title = "Current thread",
                        projectPath = "/tmp/remodex",
                    ),
                ),
                selectedThreadId = "thread-2",
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    branchesCheckedOutElsewhere = setOf("feature/test"),
                    worktreePathByBranch = mapOf(
                        "feature/test" to "/tmp/remodex/.codex/worktrees/feature/test",
                    ),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertEquals("Branch already open elsewhere", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals(
            "'feature/test' is already checked out in another worktree. Open that chat to continue there.",
            viewModel.uiState.value.gitSyncAlert?.message,
        )
        assertEquals(
            listOf("Close", "Open Chat"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )
        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
        assertTrue(repository.selectedThreadRequests.isEmpty())

        viewModel.performGitSyncAlertAction(RemodexGitSyncAlertAction.OPEN_EXISTING_BRANCH_CHAT)
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.selectedThreadRequests)
        assertEquals("thread-1", viewModel.uiState.value.selectedThread?.id)
        assertNull(viewModel.uiState.value.gitSyncAlert)
    }

    @Test
    fun `checking out an elsewhere branch matches trailing slash normalized worktree paths before opening chat`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Feature thread",
                        projectPath = "/tmp/remodex/.codex/worktrees/feature/test/",
                    ),
                    threadSummary(
                        id = "thread-2",
                        title = "Current thread",
                        projectPath = "/tmp/remodex/",
                    ),
                ),
                selectedThreadId = "thread-2",
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    branchesCheckedOutElsewhere = setOf("feature/test"),
                    worktreePathByBranch = mapOf(
                        "feature/test" to "/tmp/remodex/.codex/worktrees/feature/test",
                    ),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertEquals(
            listOf("Close", "Open Chat"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )
        assertTrue(repository.selectedThreadRequests.isEmpty())

        viewModel.performGitSyncAlertAction(RemodexGitSyncAlertAction.OPEN_EXISTING_BRANCH_CHAT)
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.selectedThreadRequests)
        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
    }

    @Test
    fun `checking out an elsewhere branch matches canonicalized worktree paths before opening chat`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Feature thread",
                        projectPath = "/tmp/remodex/.codex/worktrees/feature/../feature/test",
                    ),
                    threadSummary(
                        id = "thread-2",
                        title = "Current thread",
                        projectPath = "/tmp/remodex",
                    ),
                ),
                selectedThreadId = "thread-2",
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    branchesCheckedOutElsewhere = setOf("feature/test"),
                    worktreePathByBranch = mapOf(
                        "feature/test" to "/tmp/remodex/.codex/worktrees/feature/test",
                    ),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertEquals(
            listOf("Close", "Open Chat"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )

        viewModel.performGitSyncAlertAction(RemodexGitSyncAlertAction.OPEN_EXISTING_BRANCH_CHAT)
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.selectedThreadRequests)
        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
    }


    @Test
    fun `checking out current branch does not show elsewhere alert when already in matching worktree`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Feature thread",
                        projectPath = "/tmp/remodex/.codex/worktrees/feature/test",
                    ),
                ),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    branchesCheckedOutElsewhere = setOf("feature/test"),
                    worktreePathByBranch = mapOf(
                        "feature/test" to "/tmp/remodex/.codex/worktrees/feature/test",
                    ),
                    currentBranch = "feature/test",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.gitSyncAlert)
        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
        assertTrue(repository.selectedThreadRequests.isEmpty())
    }

    @Test
    fun `checking out an elsewhere branch without worktree path shows the iOS parity failure alert`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-2", title = "Current thread", projectPath = "/tmp/remodex")),
                selectedThreadId = "thread-2",
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    branchesCheckedOutElsewhere = setOf("feature/test"),
                    worktreePathByBranch = emptyMap(),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertEquals("Branch Switch Failed", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals(
            "Cannot switch branches: this branch is already open in another worktree.",
            viewModel.uiState.value.gitSyncAlert?.message,
        )
        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
        assertTrue(repository.selectedThreadRequests.isEmpty())
    }

    @Test
    fun `checking out an elsewhere branch without matching thread keeps sidebar guidance`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-2", title = "Current thread", projectPath = "/tmp/remodex")),
                selectedThreadId = "thread-2",
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    branchesCheckedOutElsewhere = setOf("feature/test"),
                    worktreePathByBranch = mapOf(
                        "feature/test" to "/tmp/remodex/.codex/worktrees/feature/test",
                    ),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertEquals("Branch already open elsewhere", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals(
            "'feature/test' is already checked out in another worktree. Open that chat from the sidebar to continue there.",
            viewModel.uiState.value.gitSyncAlert?.message,
        )
        assertEquals(
            listOf("Close"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )
        assertTrue(repository.selectedThreadRequests.isEmpty())
        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
    }

    @Test
    fun `checking out an elsewhere branch ignores archived thread matches`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-archived",
                        title = "Archived feature thread",
                        projectPath = "/tmp/remodex/.codex/worktrees/feature/test",
                        syncState = RemodexThreadSyncState.ARCHIVED_LOCAL,
                    ),
                    threadSummary(
                        id = "thread-2",
                        title = "Current thread",
                        projectPath = "/tmp/remodex",
                    ),
                ),
                selectedThreadId = "thread-2",
            )
            gitStateResult = RemodexGitState(
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    branchesCheckedOutElsewhere = setOf("feature/test"),
                    worktreePathByBranch = mapOf(
                        "feature/test" to "/tmp/remodex/.codex/worktrees/feature/test",
                    ),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertEquals("Branch already open elsewhere", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals(
            listOf("Close"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )
        assertTrue(repository.selectedThreadRequests.isEmpty())
        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
    }

    @Test
    fun `discard runtime changes requires confirmation before calling repository`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "feature/test",
                    isDirty = true,
                    aheadCount = 2,
                    state = "dirty",
                ),
                branches = RemodexGitBranches(defaultBranch = "main"),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.discardRuntimeChangesAndSync()
        advanceUntilIdle()

        assertTrue(repository.discardRuntimeChangesRequests.isEmpty())
        assertEquals("Discard local changes?", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals(
            listOf("Cancel", "Discard Changes"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )

        viewModel.confirmGitSyncAlert()
        advanceUntilIdle()

        assertEquals(listOf("thread-1"), repository.discardRuntimeChangesRequests)
        assertNull(viewModel.uiState.value.gitSyncAlert)
    }

    @Test
    fun `create pull request shows validation alert when still on default branch`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "main",
                    trackingBranch = "origin/main",
                    isPublishedToRemote = true,
                ),
                branches = RemodexGitBranches(
                    branches = listOf("main"),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.composer.canCreatePullRequest)

        var openedUrl: String? = null
        viewModel.createPullRequest { openedUrl = it }
        advanceUntilIdle()

        assertNull(openedUrl)
        assertEquals(
            "Switch to a feature branch before creating a PR.",
            viewModel.uiState.value.gitSyncAlert?.message,
        )
    }

    @Test
    fun `create pull request opens compare url when git state is valid`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "feature/test",
                    trackingBranch = "origin/feature/test",
                    isPublishedToRemote = true,
                    aheadCount = 0,
                ),
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    currentBranch = "feature/test",
                    defaultBranch = "main",
                ),
            )
            remoteUrlResult = RemodexGitRemoteUrl(
                url = "git@github.com:openai/remodex.git",
                ownerRepo = "openai/remodex",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.composer.canCreatePullRequest)

        var openedUrl: String? = null
        viewModel.createPullRequest { openedUrl = it }
        advanceUntilIdle()

        assertEquals(
            "https://github.com/openai/remodex/compare/main...feature/test?expand=1",
            openedUrl,
        )
        assertNull(viewModel.uiState.value.gitSyncAlert)
    }

    @Test
    fun `commit and push delegates to repository`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.commitAndPushGitChanges()
        advanceUntilIdle()

        assertEquals(listOf("thread-1" to null), repository.commitAndPushRequests)
    }

    @Test
    fun `sync git changes pulls immediately when branch is behind only`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "feature/test",
                    trackingBranch = "origin/feature/test",
                    state = "behind_only",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        repository.gitStateRequests = 0
        repository.pullGitChangesRequests.clear()

        viewModel.syncGitChanges()
        advanceUntilIdle()

        assertEquals(1, repository.gitStateRequests)
        assertEquals(listOf("thread-1"), repository.pullGitChangesRequests)
        assertNull(viewModel.uiState.value.gitSyncAlert)
    }

    @Test
    fun `sync git changes shows pull rebase alert when branch diverged`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "feature/test",
                    trackingBranch = "origin/feature/test",
                    state = "diverged",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.syncGitChanges()
        advanceUntilIdle()

        assertTrue(repository.pullGitChangesRequests.isEmpty())
        assertEquals("Branch diverged from remote", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals(
            listOf("Cancel", "Pull & Rebase"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )
    }

    @Test
    fun `sync git changes shows pull rebase alert when local changes need attention`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "feature/test",
                    trackingBranch = "origin/feature/test",
                    state = "dirty_and_behind",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.syncGitChanges()
        advanceUntilIdle()

        assertTrue(repository.pullGitChangesRequests.isEmpty())
        assertEquals("Local changes need attention", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals(
            "You have local changes and the remote branch moved ahead. Pull with rebase only if you're ready to reconcile those changes.",
            viewModel.uiState.value.gitSyncAlert?.message,
        )
    }

    @Test
    fun `commit git changes shows nothing to commit alert`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            commitGitChangesError = RpcError(
                code = -32000,
                message = "Nothing to commit",
                data = buildJsonObject {
                    put("errorCode", "nothing_to_commit")
                },
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.commitGitChanges()
        advanceUntilIdle()

        assertEquals(listOf("thread-1" to null), repository.commitGitChangesRequests)
        assertEquals("Nothing to Commit", viewModel.uiState.value.gitSyncAlert?.title)
        assertEquals("There are no changes to commit.", viewModel.uiState.value.gitSyncAlert?.message)
    }

    @Test
    fun `creating a dirty branch prompts before carrying or committing changes`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "main",
                    isDirty = true,
                    localOnlyCommitCount = 2,
                    files = listOf(
                        com.emanueledipietro.remodex.model.RemodexGitChangedFile(
                            path = "app/src/main/kotlin/App.kt",
                            status = "M",
                        ),
                    ),
                ),
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.createGitBranch("feature/test")
        advanceUntilIdle()

        assertTrue(repository.createGitBranchRequests.isEmpty())
        assertEquals(
            listOf("Cancel", "Carry to New Branch", "Commit, Create & Switch"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )

        viewModel.performGitSyncAlertAction(RemodexGitSyncAlertAction.COMMIT_AND_CONTINUE_GIT_BRANCH_OPERATION)
        advanceUntilIdle()

        assertEquals(listOf("thread-1" to "WIP before switching branches"), repository.commitGitChangesRequests)
        assertEquals(listOf("thread-1" to "feature/test"), repository.createGitBranchRequests)
    }

    @Test
    fun `switching branches with dirty changes prompts for commit and continue`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "main",
                    isDirty = true,
                    files = listOf(
                        com.emanueledipietro.remodex.model.RemodexGitChangedFile(
                            path = "README.md",
                            status = "M",
                        ),
                    ),
                ),
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
        assertEquals(
            listOf("Cancel", "Commit & Switch"),
            viewModel.uiState.value.gitSyncAlert?.buttons?.map { button -> button.title },
        )

        viewModel.performGitSyncAlertAction(RemodexGitSyncAlertAction.COMMIT_AND_CONTINUE_GIT_BRANCH_OPERATION)
        advanceUntilIdle()

        assertEquals(listOf("thread-1" to "WIP before switching branches"), repository.commitGitChangesRequests)
        assertEquals(listOf("thread-1" to "feature/test"), repository.checkoutGitBranchRequests)
    }

    @Test
    fun `checkout branch ignores requests while a git branch operation is already loading`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Git thread")),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                isLoading = true,
                sync = RemodexGitRepoSync(
                    currentBranch = "main",
                ),
                branches = RemodexGitBranches(
                    branches = listOf("main", "feature/test"),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.checkoutGitBranch("feature/test")
        advanceUntilIdle()

        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
        assertNull(viewModel.uiState.value.gitSyncAlert)
    }

    @Test
    fun `request continue on mac opens confirm dialog`() = runTest {
        val selectedThread = threadSummary(id = "thread-1", title = "Git thread")
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED),
                threads = listOf(selectedThread),
                selectedThreadId = selectedThread.id,
                selectedThreadSnapshot = selectedThread,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.requestContinueOnMac()
        runCurrent()

        assertTrue(viewModel.uiState.value.showDesktopHandoffConfirm)
        assertFalse(viewModel.uiState.value.isHandingOffToMac)
        assertNull(viewModel.uiState.value.desktopHandoffErrorMessage)
    }

    @Test
    fun `confirm continue on mac delegates to repository and clears loading state`() = runTest {
        val selectedThread = threadSummary(id = "thread-1", title = "Git thread")
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED),
                threads = listOf(selectedThread),
                selectedThreadId = selectedThread.id,
                selectedThreadSnapshot = selectedThread,
            )
            continueOnMacDelayMs = 1_000L
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.requestContinueOnMac()
        viewModel.confirmContinueOnMac()
        runCurrent()

        assertEquals(listOf("thread-1"), repository.continueOnMacRequests)
        assertTrue(viewModel.uiState.value.isHandingOffToMac)
        assertFalse(viewModel.uiState.value.showDesktopHandoffConfirm)

        advanceTimeBy(1_000L)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isHandingOffToMac)
        assertFalse(viewModel.uiState.value.showDesktopHandoffConfirm)
        assertNull(viewModel.uiState.value.desktopHandoffErrorMessage)
    }

    @Test
    fun `continue on mac failure surfaces error dialog`() = runTest {
        val selectedThread = threadSummary(id = "thread-1", title = "Git thread")
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED),
                threads = listOf(selectedThread),
                selectedThreadId = selectedThread.id,
                selectedThreadSnapshot = selectedThread,
            )
            continueOnMacError = IllegalStateException("Could not relaunch Codex.app on your Mac.")
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.requestContinueOnMac()
        viewModel.confirmContinueOnMac()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showDesktopHandoffConfirm)
        assertFalse(viewModel.uiState.value.isHandingOffToMac)
        assertEquals(
            "Could not relaunch Codex.app on your Mac.",
            viewModel.uiState.value.desktopHandoffErrorMessage,
        )

        viewModel.dismissDesktopHandoffDialogs()
        runCurrent()

        assertNull(viewModel.uiState.value.desktopHandoffErrorMessage)
    }

    @Test
    fun `handoff to worktree creates a move worktree and rebinds the current thread`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(
                    threadSummary(
                        id = "thread-1",
                        title = "Git thread",
                        projectPath = "/tmp/remodex",
                    ),
                ),
                selectedThreadId = "thread-1",
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "main",
                ),
                branches = RemodexGitBranches(
                    branches = listOf("main"),
                    currentBranch = "main",
                    defaultBranch = "main",
                ),
            )
            gitWorktreeResult = com.emanueledipietro.remodex.model.RemodexGitWorktreeResult(
                branch = "feature/handoff",
                worktreePath = "/tmp/remodex/.codex/worktrees/feature/handoff",
                alreadyExisted = false,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()
        val canonicalWorktreePath = RemodexWorktreeRouting.canonicalProjectPath(
            "/tmp/remodex/.codex/worktrees/feature/handoff",
        ) ?: "/tmp/remodex/.codex/worktrees/feature/handoff"

        viewModel.handoffThreadToWorktree("feature/handoff", "main")
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("thread-1", "feature/handoff", "main")),
            repository.createGitWorktreeResultRequests,
        )
        assertTrue(repository.createGitWorktreeRequests.isEmpty())
        assertEquals(
            listOf("thread-1" to canonicalWorktreePath),
            repository.moveThreadToProjectPathRequests,
        )
        assertEquals(
            canonicalWorktreePath,
            viewModel.uiState.value.selectedThread?.projectPath,
        )
        assertEquals(
            "Now in worktree feature/handoff.",
            viewModel.uiState.value.transientBanner,
        )
    }

    @Test
    fun `fork into new worktree creates a copy worktree then forks into that project path`() = runTest {
        val selectedThread = threadSummary(
            id = "thread-1",
            title = "Git thread",
            projectPath = "/tmp/remodex",
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(selectedThread),
                selectedThreadId = selectedThread.id,
                selectedThreadSnapshot = selectedThread,
            )
            gitWorktreeResult = com.emanueledipietro.remodex.model.RemodexGitWorktreeResult(
                branch = "feature/forked",
                worktreePath = "/tmp/remodex/.codex/worktrees/feature/forked",
                alreadyExisted = false,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.forkThreadIntoNewWorktree(
            name = "feature/forked",
            baseBranch = "main",
        )
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("thread-1", "feature/forked", "main")),
            repository.createGitWorktreeResultRequests,
        )
        assertTrue(repository.createGitWorktreeRequests.isEmpty())
        assertEquals(
            listOf("thread-1" to "/tmp/remodex/.codex/worktrees/feature/forked"),
            repository.forkThreadIntoProjectPathRequests,
        )
        assertEquals(
            "Now in worktree feature/forked.",
            viewModel.uiState.value.transientBanner,
        )
    }

    @Test
    fun `fork into new worktree falls back to current branch when base branch is omitted`() = runTest {
        val selectedThread = threadSummary(
            id = "thread-1",
            title = "Git thread",
            projectPath = "/tmp/remodex",
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(selectedThread),
                selectedThreadId = selectedThread.id,
                selectedThreadSnapshot = selectedThread,
            )
            gitStateResult = RemodexGitState(
                sync = RemodexGitRepoSync(
                    currentBranch = "feature/current",
                ),
                branches = RemodexGitBranches(
                    branches = listOf("feature/current", "main"),
                    currentBranch = "feature/current",
                    defaultBranch = "main",
                ),
            )
            gitWorktreeResult = com.emanueledipietro.remodex.model.RemodexGitWorktreeResult(
                branch = "feature/forked",
                worktreePath = "/tmp/remodex/.codex/worktrees/feature/forked",
                alreadyExisted = false,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()
        viewModel.refreshGitState()
        advanceUntilIdle()

        viewModel.forkThreadIntoNewWorktree(
            name = "feature/forked",
            baseBranch = "",
        )
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("thread-1", "feature/forked", "feature/current")),
            repository.createGitWorktreeResultRequests,
        )
        assertTrue(repository.createGitWorktreeRequests.isEmpty())
        assertEquals(
            listOf("thread-1" to "/tmp/remodex/.codex/worktrees/feature/forked"),
            repository.forkThreadIntoProjectPathRequests,
        )
    }

    @Test
    fun `prepare fork destination selection clears trailing slash command token and autocomplete`() = runTest {
        val selectedThread = threadSummary(
            id = "thread-1",
            title = "Git thread",
            projectPath = "/tmp/remodex",
        )
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(selectedThread),
                selectedThreadId = selectedThread.id,
                selectedThreadSnapshot = selectedThread,
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("/")
        advanceUntilIdle()
        assertEquals("/", viewModel.uiState.value.composer.draftText)
        assertEquals(
            com.emanueledipietro.remodex.model.RemodexComposerAutocompletePanel.COMMANDS,
            viewModel.uiState.value.composer.autocomplete.panel,
        )

        viewModel.prepareForkDestinationSelection()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertEquals(
            com.emanueledipietro.remodex.model.RemodexComposerAutocompletePanel.NONE,
            viewModel.uiState.value.composer.autocomplete.panel,
        )
    }

    private class TestRemodexAppRepository : RemodexAppRepository {
        val snapshot = MutableStateFlow(RemodexSessionSnapshot())
        val commandDetails = MutableStateFlow<Map<String, RemodexCommandExecutionDetails>>(emptyMap())
        val assistantResponseMetricsState = MutableStateFlow<Map<String, RemodexAssistantResponseMetrics>>(emptyMap())
        val streamingAssistantTextsState = MutableStateFlow<Map<String, StreamingAssistantTextState>>(emptyMap())
        val gptAccountSnapshotFlow = MutableStateFlow(RemodexGptAccountSnapshot())
        val gptAccountErrorMessageFlow = MutableStateFlow<String?>(null)
        val bridgeVersionStatusFlow = MutableStateFlow(RemodexBridgeVersionStatus())
        val usageStatusFlow = MutableStateFlow(RemodexUsageStatus())
        val foregroundStates = mutableListOf<Boolean>()
        val hydrateRequests = mutableListOf<String>()
        val activeThreadSyncRequests = mutableListOf<String>()
        val selectedThreadRequests = mutableListOf<String>()
        val refreshUsageStatusRequests = mutableListOf<String?>()
        val previewRequests = mutableListOf<Pair<String, String>>()
        val applyRequests = mutableListOf<Pair<String, String>>()
        val sentPrompts = mutableListOf<Triple<String, String, List<RemodexComposerAttachment>>>()
        val sentQueuedDrafts = mutableListOf<Pair<String, String>>()
        val steeredQueuedDrafts = mutableListOf<Pair<String, String>>()
        val poppedQueuedDrafts = mutableListOf<Pair<String, String>>()
        val compactThreadRequests = mutableListOf<String>()
        val cleanBackgroundTerminalsRequests = mutableListOf<String>()
        val sentPromptPlanningModeOverrides = mutableListOf<RemodexPlanningMode?>()
        val setPlanningModeRequests = mutableListOf<Pair<String, RemodexPlanningMode>>()
        val codeReviewRequests = mutableListOf<Pair<String, RemodexCodeReviewRequest>>()
        val createThreadRequests = mutableListOf<Pair<String?, String?>>()
        val checkoutGitBranchRequests = mutableListOf<Pair<String, String>>()
        val createGitBranchRequests = mutableListOf<Pair<String, String>>()
        val createGitWorktreeRequests = mutableListOf<GitWorktreeRequest>()
        val createGitWorktreeResultRequests = mutableListOf<Triple<String, String, String?>>()
        val commitGitChangesRequests = mutableListOf<Pair<String, String?>>()
        val commitAndPushRequests = mutableListOf<Pair<String, String?>>()
        val pullGitChangesRequests = mutableListOf<String>()
        val continueOnMacRequests = mutableListOf<String>()
        val approvePendingApprovalRequests = mutableListOf<Boolean>()
        val grantPendingPermissionsApprovalScopes = mutableListOf<RemodexPermissionGrantScope>()
        val discardRuntimeChangesRequests = mutableListOf<String>()
        val moveThreadToProjectPathRequests = mutableListOf<Pair<String, String>>()
        val forkThreadRequests = mutableListOf<Triple<String, RemodexComposerForkDestination, String?>>()
        val forkThreadIntoProjectPathRequests = mutableListOf<Pair<String, String>>()
        var fileSearchResults: List<RemodexFuzzyFileMatch> = emptyList()
        var skillResults: List<RemodexSkillMetadata> = emptyList()
        var refreshRequests = 0
        var retryConnectionCalls = 0
        var disconnectCalls = 0
        val activateBridgeProfileRequests = mutableListOf<String>()
        val pairWithQrPayloadRequests = mutableListOf<PairingQrPayload>()
        val stopTurnRequests = mutableListOf<String>()
        var dismissBridgeUpdatePromptCalls = 0
        var onRetryConnection: (suspend TestRemodexAppRepository.() -> Unit)? = null
        var refreshDelayMs = 1_000L
        var hydrateDelayMs = 0L
        var activeThreadSyncDelayMs = 0L
        var sendPromptDelayMs = 0L
        var continueOnMacDelayMs = 0L
        var stopTurnDelayMs = 0L
        var stopTurnResult = StopTurnResult.INTERRUPT_REQUESTED
        var activateBridgeProfileDelayMs = 0L
        var pairWithQrPayloadDelayMs = 0L
        var createThreadDelayMs = 0L
        var createThreadError: Throwable? = null
        var sendPromptError: Throwable? = null
        var sendQueuedDraftError: Throwable? = null
        var compactThreadError: Throwable? = null
        var continueOnMacError: Throwable? = null
        var declinePendingApprovalRequests = 0
        var cancelPendingApprovalRequests = 0
        var forkThreadError: Throwable? = null
        var forkThreadResult: String? = null
        var forkThreadFailureSessionSnapshot: RemodexSessionSnapshot? = null
        var gitDiffRequests = 0
        var gitDiffDelayMs = 0L
        var gitDiffResult = RemodexGitRepoDiff()
        var gitStateResult = RemodexGitState()
        var gitStateRequests = 0
        var gitSyncResult: RemodexGitRepoSync? = null
        var gitSyncRequests = 0
        var gitCommitResults: List<RemodexGitCommit> = emptyList()
        var gitStateError: Throwable? = null
        var gitSyncError: Throwable? = null
        var checkoutGitBranchError: Throwable? = null
        var commitGitChangesError: Throwable? = null
        var commitAndPushGitChangesError: Throwable? = null
        var pullGitChangesError: Throwable? = null
        var remoteUrlResult = RemodexGitRemoteUrl(
            url = "git@github.com:example/remodex.git",
            ownerRepo = "example/remodex",
        )
        var gitWorktreeResult = com.emanueledipietro.remodex.model.RemodexGitWorktreeResult(
            branch = "feature/worktree",
            worktreePath = "/tmp/remodex/.codex/worktrees/feature/worktree",
            alreadyExisted = false,
        )
        var nextCreatedThreadId = "thread-created"
        var createThreadSelectsCreatedThread = true
        var previewResult = RemodexRevertPreviewResult(
            canRevert = true,
            affectedFiles = listOf("src/App.kt"),
            conflicts = emptyList(),
            unsupportedReasons = emptyList(),
            stagedFiles = emptyList(),
        )
        var applyResult = RemodexRevertApplyResult(
            success = true,
            revertedFiles = listOf("src/App.kt"),
            conflicts = emptyList(),
            unsupportedReasons = emptyList(),
            stagedFiles = emptyList(),
            status = null,
        )

        override val session: StateFlow<RemodexSessionSnapshot> = snapshot
        override val commandExecutionDetails: StateFlow<Map<String, RemodexCommandExecutionDetails>> = commandDetails
        override val assistantResponseMetricsByThreadId: StateFlow<Map<String, RemodexAssistantResponseMetrics>> = assistantResponseMetricsState
        override val streamingAssistantTextsByMessageId: StateFlow<Map<String, StreamingAssistantTextState>> =
            streamingAssistantTextsState
        override val gptAccountSnapshot: StateFlow<RemodexGptAccountSnapshot> = gptAccountSnapshotFlow
        override val gptAccountErrorMessage: StateFlow<String?> = gptAccountErrorMessageFlow
        override val bridgeVersionStatus: StateFlow<RemodexBridgeVersionStatus> = bridgeVersionStatusFlow
        override val usageStatus: StateFlow<RemodexUsageStatus> = usageStatusFlow

        override fun setAppForeground(isForeground: Boolean) {
            foregroundStates += isForeground
        }

        override suspend fun completeOnboarding() {
            snapshot.value = snapshot.value.copy(onboardingCompleted = true)
        }

        override suspend fun refreshThreads() {
            refreshRequests += 1
            delay(refreshDelayMs)
        }

        override suspend fun hydrateThread(threadId: String) {
            hydrateRequests += threadId
            delay(hydrateDelayMs)
        }

        override suspend fun syncActiveThread(threadId: String) {
            activeThreadSyncRequests += threadId
            delay(activeThreadSyncDelayMs)
        }

        override suspend fun selectThread(threadId: String) {
            selectedThreadRequests += threadId
            val selectedThread = snapshot.value.threads.firstOrNull { thread -> thread.id == threadId }
            snapshot.value = snapshot.value.copy(
                selectedThreadId = threadId,
                selectedThreadSnapshot = selectedThread,
            )
        }

        override suspend fun setProjectGroupCollapsed(
            groupId: String,
            collapsed: Boolean,
        ) {
            val updatedCollapsedGroupIds = snapshot.value.collapsedProjectGroupIds.toMutableSet().apply {
                if (collapsed) {
                    add(groupId)
                } else {
                    remove(groupId)
                }
            }
            snapshot.value = snapshot.value.copy(collapsedProjectGroupIds = updatedCollapsedGroupIds)
        }

        override suspend fun createThread(
            preferredProjectPath: String?,
            inheritRuntimeFromThreadId: String?,
        ) {
            createThreadRequests += (preferredProjectPath to inheritRuntimeFromThreadId)
            if (createThreadDelayMs > 0) {
                delay(createThreadDelayMs)
            }
            createThreadError?.let { throw it }
            val createdThread = com.emanueledipietro.remodex.model.RemodexThreadSummary(
                id = nextCreatedThreadId,
                title = "Review current changes",
                preview = "Preview",
                projectPath = preferredProjectPath.orEmpty(),
                lastUpdatedLabel = "Updated just now",
                isRunning = false,
                queuedDrafts = 0,
                runtimeLabel = "Auto, medium reasoning",
                messages = emptyList(),
            )
            snapshot.value = snapshot.value.copy(
                threads = listOf(createdThread) + snapshot.value.threads,
                selectedThreadId = if (createThreadSelectsCreatedThread) {
                    createdThread.id
                } else {
                    snapshot.value.selectedThreadId
                },
                selectedThreadSnapshot = if (createThreadSelectsCreatedThread) {
                    createdThread
                } else {
                    snapshot.value.selectedThreadSnapshot
                },
            )
        }

        override suspend fun renameThread(threadId: String, name: String) = Unit

        override suspend fun archiveThread(threadId: String) = Unit

        override suspend fun unarchiveThread(threadId: String) = Unit

        override suspend fun deleteThread(threadId: String) = Unit

        override suspend fun archiveProject(projectPath: String) = Unit

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            attachments: List<RemodexComposerAttachment>,
            planningModeOverride: RemodexPlanningMode?,
            queuedDraftContext: RemodexQueuedDraftContext?,
            forceQueue: Boolean,
        ) {
            if (sendPromptDelayMs > 0) {
                delay(sendPromptDelayMs)
            }
            sendPromptError?.let { throw it }
            sentPrompts += Triple(threadId, prompt, attachments)
            sentPromptPlanningModeOverrides += planningModeOverride
        }

        override suspend fun compactThread(threadId: String) {
            compactThreadError?.let { throw it }
            compactThreadRequests += threadId
        }

        override suspend fun cleanBackgroundTerminals(threadId: String) {
            cleanBackgroundTerminalsRequests += threadId
        }

        override suspend fun respondToStructuredUserInput(
            requestId: JsonElement,
            answersByQuestionId: Map<String, List<String>>,
        ) = Unit

        override suspend fun approvePendingApproval(forSession: Boolean) {
            approvePendingApprovalRequests += forSession
        }

        override suspend fun declinePendingApproval() {
            declinePendingApprovalRequests += 1
        }

        override suspend fun cancelPendingApproval() {
            cancelPendingApprovalRequests += 1
        }

        override suspend fun grantPendingPermissionsApproval(scope: RemodexPermissionGrantScope) {
            grantPendingPermissionsApprovalScopes += scope
        }

        override suspend fun continueOnMac(threadId: String) {
            continueOnMacRequests += threadId
            if (continueOnMacDelayMs > 0) {
                delay(continueOnMacDelayMs)
            }
            continueOnMacError?.let { throw it }
        }

        override suspend fun stopTurn(threadId: String): StopTurnResult {
            stopTurnRequests += threadId
            if (stopTurnDelayMs > 0) {
                delay(stopTurnDelayMs)
            }
            return stopTurnResult
        }

        override suspend fun sendQueuedDraft(threadId: String, draftId: String) {
            sentQueuedDrafts += threadId to draftId
            sendQueuedDraftError?.let { throw it }
        }

        override suspend fun steerQueuedDraft(threadId: String, draftId: String) {
            steeredQueuedDrafts += threadId to draftId
        }

        override suspend fun appendQueuedDraft(
            threadId: String,
            draft: RemodexQueuedDraft,
        ) {
            val selectedThread = snapshot.value.threads.firstOrNull { thread -> thread.id == threadId } ?: return
            val updatedThread = selectedThread.copy(
                queuedDrafts = selectedThread.queuedDrafts + 1,
                queuedDraftItems = selectedThread.queuedDraftItems + draft,
            )
            snapshot.value = snapshot.value.copy(
                threads = snapshot.value.threads.map { thread -> if (thread.id == threadId) updatedThread else thread },
                selectedThreadSnapshot = snapshot.value.selectedThreadSnapshot?.let { thread ->
                    if (thread.id == threadId) updatedThread else thread
                },
            )
        }

        override suspend fun removeQueuedDraft(threadId: String, draftId: String) {
            popQueuedDraft(threadId, draftId)
        }

        override suspend fun popQueuedDraft(threadId: String, draftId: String): RemodexQueuedDraft? {
            poppedQueuedDrafts += threadId to draftId
            val selectedThread = snapshot.value.threads.firstOrNull { thread -> thread.id == threadId } ?: return null
            val draft = selectedThread.queuedDraftItems.firstOrNull { it.id == draftId } ?: return null
            val updatedThread = selectedThread.copy(
                queuedDrafts = (selectedThread.queuedDrafts - 1).coerceAtLeast(0),
                queuedDraftItems = selectedThread.queuedDraftItems.filterNot { it.id == draftId },
            )
            snapshot.value = snapshot.value.copy(
                threads = snapshot.value.threads.map { thread ->
                    if (thread.id == threadId) {
                        updatedThread
                    } else {
                        thread
                    }
                },
                selectedThreadSnapshot = snapshot.value.selectedThreadSnapshot?.let { thread ->
                    if (thread.id == threadId) {
                        updatedThread
                    } else {
                        thread
                    }
                },
            )
            return draft
        }

        override suspend fun popLatestQueuedDraft(threadId: String): RemodexQueuedDraft? {
            val selectedThread = snapshot.value.threads.firstOrNull { thread -> thread.id == threadId } ?: return null
            val latestDraft = selectedThread.queuedDraftItems.lastOrNull() ?: return null
            return popQueuedDraft(threadId, latestDraft.id)
        }

        override suspend fun setPlanningMode(
            threadId: String,
            planningMode: RemodexPlanningMode,
        ) {
            setPlanningModeRequests += threadId to planningMode
            snapshot.value = snapshot.value.copy(
                threads = snapshot.value.threads.map { thread ->
                    if (thread.id == threadId) {
                        thread.copy(
                            runtimeConfig = thread.runtimeConfig.copy(planningMode = planningMode),
                        )
                    } else {
                        thread
                    }
                },
                selectedThreadSnapshot = snapshot.value.selectedThreadSnapshot?.let { thread ->
                    if (thread.id == threadId) {
                        thread.copy(
                            runtimeConfig = thread.runtimeConfig.copy(planningMode = planningMode),
                        )
                    } else {
                        thread
                    }
                },
            )
        }

        override suspend fun setSelectedModelId(modelId: String?) = Unit

        override suspend fun setReasoningEffort(
            threadId: String,
            reasoningEffort: String,
        ) = Unit

        override suspend fun setAccessMode(
            threadId: String,
            accessMode: RemodexAccessMode,
        ) = Unit

        override suspend fun setServiceTier(threadId: String, serviceTier: RemodexServiceTier?) = Unit

        override suspend fun setDefaultModelId(modelId: String?) = Unit

        override suspend fun setDefaultReasoningEffort(reasoningEffort: String?) = Unit

        override suspend fun setDefaultAccessMode(accessMode: RemodexAccessMode) = Unit

        override suspend fun setDefaultServiceTier(serviceTier: RemodexServiceTier?) = Unit

        override suspend fun setAppearanceMode(mode: RemodexAppearanceMode) = Unit

        override suspend fun setAppFontStyle(style: RemodexAppFontStyle) = Unit

        override suspend fun setMacNickname(deviceId: String, nickname: String?) = Unit

        override suspend fun activateBridgeProfile(profileId: String): Boolean {
            if (activateBridgeProfileDelayMs > 0) {
                delay(activateBridgeProfileDelayMs)
            }
            activateBridgeProfileRequests += profileId
            return true
        }

        override suspend fun removeBridgeProfile(profileId: String): String? = null

        override suspend fun refreshGptAccountState() = Unit

        override suspend fun logoutGptAccount() = Unit

        override suspend fun refreshUsageStatus(threadId: String?) {
            refreshUsageStatusRequests += threadId
        }

        override suspend fun fuzzyFileSearch(
            threadId: String,
            query: String,
        ): List<RemodexFuzzyFileMatch> = fileSearchResults

        override suspend fun listSkills(
            threadId: String,
            forceReload: Boolean,
        ): List<RemodexSkillMetadata> = skillResults

        override suspend fun startCodeReview(
            threadId: String,
            request: RemodexCodeReviewRequest,
        ) {
            codeReviewRequests += threadId to request
        }

        override suspend fun loadGitCommits(threadId: String): List<RemodexGitCommit> = gitCommitResults

        override suspend fun forkThread(
            threadId: String,
            destination: RemodexComposerForkDestination,
            baseBranch: String?,
        ): String? {
            forkThreadRequests += Triple(threadId, destination, baseBranch)
            forkThreadFailureSessionSnapshot?.let { snapshot.value = it }
            forkThreadError?.let { throw it }
            return forkThreadResult
        }

        override suspend fun forkThreadIntoProjectPath(
            threadId: String,
            projectPath: String,
        ): String? {
            forkThreadIntoProjectPathRequests += threadId to projectPath
            return "thread-forked"
        }

        override suspend fun loadGitState(threadId: String): RemodexGitState {
            gitStateRequests += 1
            gitStateError?.let { throw it }
            return gitStateResult
        }

        override suspend fun loadGitSync(threadId: String): RemodexGitRepoSync? {
            gitSyncRequests += 1
            gitSyncError?.let { throw it }
            return gitSyncResult ?: gitStateResult.sync
        }

        override suspend fun loadGitDiff(threadId: String): RemodexGitRepoDiff {
            gitDiffRequests += 1
            if (gitDiffDelayMs > 0) {
                delay(gitDiffDelayMs)
            }
            return gitDiffResult
        }

        override suspend fun checkoutGitBranch(
            threadId: String,
            branch: String,
        ): RemodexGitState {
            checkoutGitBranchRequests += threadId to branch
            checkoutGitBranchError?.let { throw it }
            return RemodexGitState()
        }

        override suspend fun createGitBranch(
            threadId: String,
            branch: String,
        ): RemodexGitState {
            createGitBranchRequests += threadId to branch
            return RemodexGitState()
        }

        override suspend fun createGitWorktree(
            threadId: String,
            name: String,
            baseBranch: String?,
            changeTransfer: RemodexGitWorktreeChangeTransferMode,
        ): RemodexGitState {
            createGitWorktreeRequests += GitWorktreeRequest(threadId, name, baseBranch, changeTransfer)
            return RemodexGitState()
        }

        override suspend fun createGitWorktreeResult(
            threadId: String,
            name: String,
            baseBranch: String?,
            changeTransfer: RemodexGitWorktreeChangeTransferMode,
        ): com.emanueledipietro.remodex.model.RemodexGitWorktreeResult {
            createGitWorktreeResultRequests += Triple(threadId, name, baseBranch)
            return gitWorktreeResult.copy(branch = name)
        }

        override suspend fun commitGitChanges(
            threadId: String,
            message: String?,
        ): RemodexGitState {
            commitGitChangesRequests += threadId to message
            commitGitChangesError?.let { throw it }
            return RemodexGitState()
        }

        override suspend fun commitAndPushGitChanges(
            threadId: String,
            message: String?,
        ): RemodexGitState {
            commitAndPushRequests += threadId to message
            commitAndPushGitChangesError?.let { throw it }
            return RemodexGitState(lastActionMessage = "Committed and pushed the current branch.")
        }

        override suspend fun pullGitChanges(threadId: String): RemodexGitState {
            pullGitChangesRequests += threadId
            pullGitChangesError?.let { throw it }
            return RemodexGitState()
        }

        override suspend fun pushGitChanges(threadId: String): RemodexGitState = RemodexGitState()

        override suspend fun loadGitRemoteUrl(threadId: String): RemodexGitRemoteUrl = remoteUrlResult

        override suspend fun discardRuntimeChangesAndSync(threadId: String): RemodexGitState {
            discardRuntimeChangesRequests += threadId
            return RemodexGitState()
        }

        override suspend fun moveThreadToProjectPath(
            threadId: String,
            projectPath: String,
        ) {
            moveThreadToProjectPathRequests += threadId to projectPath
            snapshot.value = snapshot.value.copy(
                threads = snapshot.value.threads.map { thread ->
                    if (thread.id == threadId) {
                        thread.copy(projectPath = projectPath)
                    } else {
                        thread
                    }
                },
                selectedThreadSnapshot = snapshot.value.selectedThreadSnapshot?.let { thread ->
                    if (thread.id == threadId) {
                        thread.copy(projectPath = projectPath)
                    } else {
                        thread
                    }
                },
            )
        }

        override suspend fun previewAssistantRevert(
            threadId: String,
            forwardPatch: String,
        ): RemodexRevertPreviewResult {
            previewRequests += threadId to forwardPatch
            return previewResult
        }

        override suspend fun applyAssistantRevert(
            threadId: String,
            forwardPatch: String,
        ): RemodexRevertApplyResult {
            applyRequests += threadId to forwardPatch
            return applyResult
        }

        override suspend fun pairWithQrPayload(payload: PairingQrPayload) {
            if (pairWithQrPayloadDelayMs > 0) {
                delay(pairWithQrPayloadDelayMs)
            }
            pairWithQrPayloadRequests += payload
        }

        override suspend fun dismissBridgeUpdatePrompt() {
            dismissBridgeUpdatePromptCalls += 1
            snapshot.value = snapshot.value.copy(bridgeUpdatePrompt = null)
        }

        override suspend fun retryConnection() {
            retryConnectionCalls += 1
            onRetryConnection?.invoke(this)
        }

        override suspend fun disconnect() {
            disconnectCalls += 1
            snapshot.value = snapshot.value.copy(
                connectionStatus = RemodexConnectionStatus(),
                secureConnection = snapshot.value.secureConnection.copy(
                    secureState = SecureConnectionState.TRUSTED_MAC,
                    phaseMessage = "Saved pairing ready.",
                ),
            )
        }

        override suspend fun forgetTrustedMac() = Unit

        override suspend fun forgetTrustedMac(deviceId: String) = Unit
    }

    private fun threadSummary(
        id: String,
        title: String,
        projectPath: String = "/tmp/remodex",
        isRunning: Boolean = false,
        isWaitingOnApproval: Boolean = false,
        syncState: RemodexThreadSyncState = RemodexThreadSyncState.LIVE,
        latestTurnTerminalState: RemodexTurnTerminalState? = null,
        queuedDraftItems: List<RemodexQueuedDraft> = emptyList(),
        runtimeConfig: RemodexRuntimeConfig = RemodexRuntimeConfig(),
        messages: List<RemodexConversationItem> = emptyList(),
    ): com.emanueledipietro.remodex.model.RemodexThreadSummary {
        return com.emanueledipietro.remodex.model.RemodexThreadSummary(
            id = id,
            title = title,
            preview = "Preview",
            projectPath = projectPath,
            lastUpdatedLabel = "Updated just now",
            isRunning = isRunning,
            isWaitingOnApproval = isWaitingOnApproval,
            syncState = syncState,
            latestTurnTerminalState = latestTurnTerminalState,
            queuedDrafts = queuedDraftItems.size,
            queuedDraftItems = queuedDraftItems,
            runtimeLabel = "Auto, medium reasoning",
            runtimeConfig = runtimeConfig,
            messages = messages,
        )
    }

    private fun bridgeProfile(
        profileId: String,
        deviceId: String,
        isActive: Boolean,
        isConnected: Boolean = false,
    ): com.emanueledipietro.remodex.model.RemodexBridgeProfilePresentation {
        return com.emanueledipietro.remodex.model.RemodexBridgeProfilePresentation(
            profileId = profileId,
            title = "Saved Pair",
            name = "Mac $deviceId",
            isActive = isActive,
            isConnected = isConnected,
            macDeviceId = deviceId,
        )
    }

    private fun assistantMessage(
        id: String,
        threadId: String,
        patchPath: String,
    ): RemodexConversationItem {
        return RemodexConversationItem(
            id = id,
            speaker = ConversationSpeaker.ASSISTANT,
            text = "Applied changes.",
            turnId = "turn-$id",
            orderIndex = 0,
            assistantChangeSet = RemodexAssistantChangeSet(
                id = "changeset-$id",
                repoRoot = "/tmp/remodex",
                threadId = threadId,
                turnId = "turn-$id",
                assistantMessageId = id,
                status = RemodexAssistantChangeSetStatus.READY,
                source = RemodexAssistantChangeSetSource.TURN_DIFF,
                forwardUnifiedPatch = """
                    diff --git a/$patchPath b/$patchPath
                    --- a/$patchPath
                    +++ b/$patchPath
                    @@ -1 +1 @@
                    -old
                    +new
                """.trimIndent(),
                fileChanges = listOf(
                    RemodexAssistantFileChange(
                        path = patchPath,
                        additions = 1,
                        deletions = 1,
                    ),
                ),
            ),
        )
    }

    private fun contextCompactionMessage(
        id: String,
        threadId: String,
        isStreaming: Boolean,
        text: String,
    ): RemodexConversationItem {
        return RemodexConversationItem(
            id = id,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.CONTEXT_COMPACTION,
            text = text,
            turnId = "turn-$threadId",
            isStreaming = isStreaming,
            orderIndex = 0,
        )
    }
}
