package com.emanueledipietro.remodex.feature.appshell

import com.emanueledipietro.remodex.MainDispatcherRule
import com.emanueledipietro.remodex.data.app.RemodexAppRepository
import com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot
import com.emanueledipietro.remodex.data.connection.PairingQrPayload
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetSource
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexAssistantFileChange
import com.emanueledipietro.remodex.model.RemodexAssistantRevertRiskLevel
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexBridgeVersionStatus
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerAutocompletePanel
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexGitDiffTotals
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitRepoSync
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexSlashCommand
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.ConversationSpeaker
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
    fun `selected thread hydrates again when connection becomes active`() = runTest {
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

        assertEquals(listOf("thread-1"), repository.hydrateRequests)
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
    fun `composer clears running controls when selected thread already has terminal completion state`() = runTest {
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

        assertEquals("Send", viewModel.uiState.value.composer.sendLabel)
        assertFalse(viewModel.uiState.value.composer.canStop)
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
    fun `refresh threads delegates only while connected and exposes refreshing state`() = runTest {
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
                RemodexSlashCommand.SUBAGENTS,
            ),
            viewModel.uiState.value.composer.autocomplete.slashCommands,
        )

        viewModel.updateComposerInput("/status")
        advanceUntilIdle()
        viewModel.selectSlashCommand(RemodexSlashCommand.STATUS)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertEquals(RemodexComposerAutocompletePanel.NONE, viewModel.uiState.value.composer.autocomplete.panel)
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
        assertEquals(initialAnchorSignal, viewModel.uiState.value.composerSendAnchorSignal)

        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals("Ship this", viewModel.uiState.value.composer.draftText)
        assertEquals(1, viewModel.uiState.value.composer.attachments.size)
        assertTrue(viewModel.uiState.value.composer.isSubagentsSelectionArmed)
        assertEquals("Bridge down", viewModel.uiState.value.composer.composerMessage)
        assertEquals(initialAnchorSignal, viewModel.uiState.value.composerSendAnchorSignal)
    }

    @Test
    fun `successful send emits dismiss and anchor signals`() = runTest {
        val repository = TestRemodexAppRepository().apply {
            snapshot.value = snapshot.value.copy(
                threads = listOf(threadSummary(id = "thread-1", title = "Send thread")),
                selectedThreadId = "thread-1",
            )
        }
        val viewModel = AppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateComposerInput("Ship this")
        advanceUntilIdle()

        val initialDismissSignal = viewModel.uiState.value.composerSendDismissSignal
        val initialAnchorSignal = viewModel.uiState.value.composerSendAnchorSignal

        viewModel.sendPrompt()
        advanceUntilIdle()

        assertEquals(initialDismissSignal + 1, viewModel.uiState.value.composerSendDismissSignal)
        assertEquals(initialAnchorSignal + 1, viewModel.uiState.value.composerSendAnchorSignal)
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
        viewModel.loadRepositoryDiff { diff ->
            loadedDiff = diff
        }
        runCurrent()

        assertTrue(viewModel.uiState.value.composer.gitState.isLoading)
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
        viewModel.loadRepositoryDiff {
            callbackCount += 1
        }
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

    private class TestRemodexAppRepository : RemodexAppRepository {
        val snapshot = MutableStateFlow(RemodexSessionSnapshot())
        val commandDetails = MutableStateFlow<Map<String, RemodexCommandExecutionDetails>>(emptyMap())
        val gptAccountSnapshotFlow = MutableStateFlow(RemodexGptAccountSnapshot())
        val gptAccountErrorMessageFlow = MutableStateFlow<String?>(null)
        val bridgeVersionStatusFlow = MutableStateFlow(RemodexBridgeVersionStatus())
        val usageStatusFlow = MutableStateFlow(RemodexUsageStatus())
        val hydrateRequests = mutableListOf<String>()
        val previewRequests = mutableListOf<Pair<String, String>>()
        val applyRequests = mutableListOf<Pair<String, String>>()
        val sentPrompts = mutableListOf<Triple<String, String, List<RemodexComposerAttachment>>>()
        var fileSearchResults: List<RemodexFuzzyFileMatch> = emptyList()
        var skillResults: List<RemodexSkillMetadata> = emptyList()
        var refreshRequests = 0
        var retryConnectionCalls = 0
        var disconnectCalls = 0
        var refreshDelayMs = 1_000L
        var sendPromptDelayMs = 0L
        var sendPromptError: Throwable? = null
        var gitDiffRequests = 0
        var gitDiffDelayMs = 0L
        var gitDiffResult = RemodexGitRepoDiff()
        var gitStateResult = RemodexGitState()
        var gitStateError: Throwable? = null
        var checkoutGitBranchError: Throwable? = null
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
        override val gptAccountSnapshot: StateFlow<RemodexGptAccountSnapshot> = gptAccountSnapshotFlow
        override val gptAccountErrorMessage: StateFlow<String?> = gptAccountErrorMessageFlow
        override val bridgeVersionStatus: StateFlow<RemodexBridgeVersionStatus> = bridgeVersionStatusFlow
        override val usageStatus: StateFlow<RemodexUsageStatus> = usageStatusFlow

        override suspend fun completeOnboarding() {
            snapshot.value = snapshot.value.copy(onboardingCompleted = true)
        }

        override suspend fun refreshThreads() {
            refreshRequests += 1
            delay(refreshDelayMs)
        }

        override suspend fun hydrateThread(threadId: String) {
            hydrateRequests += threadId
        }

        override suspend fun selectThread(threadId: String) = Unit

        override suspend fun createThread(preferredProjectPath: String?) = Unit

        override suspend fun renameThread(threadId: String, name: String) = Unit

        override suspend fun archiveThread(threadId: String) = Unit

        override suspend fun unarchiveThread(threadId: String) = Unit

        override suspend fun deleteThread(threadId: String) = Unit

        override suspend fun archiveProject(projectPath: String) = Unit

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            attachments: List<RemodexComposerAttachment>,
        ) {
            if (sendPromptDelayMs > 0) {
                delay(sendPromptDelayMs)
            }
            sendPromptError?.let { throw it }
            sentPrompts += Triple(threadId, prompt, attachments)
        }

        override suspend fun stopTurn(threadId: String) = Unit

        override suspend fun sendQueuedDraft(threadId: String, draftId: String) = Unit

        override suspend fun setPlanningMode(
            threadId: String,
            planningMode: RemodexPlanningMode,
        ) = Unit

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

        override suspend fun refreshGptAccountState() = Unit

        override suspend fun logoutGptAccount() = Unit

        override suspend fun refreshUsageStatus(threadId: String?) = Unit

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
            target: RemodexComposerReviewTarget,
            baseBranch: String?,
        ) = Unit

        override suspend fun forkThread(
            threadId: String,
            destination: RemodexComposerForkDestination,
            baseBranch: String?,
        ): String? = null

        override suspend fun loadGitState(threadId: String): RemodexGitState {
            gitStateError?.let { throw it }
            return gitStateResult
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
            checkoutGitBranchError?.let { throw it }
            return RemodexGitState()
        }

        override suspend fun createGitBranch(
            threadId: String,
            branch: String,
        ): RemodexGitState = RemodexGitState()

        override suspend fun createGitWorktree(
            threadId: String,
            name: String,
            baseBranch: String?,
        ): RemodexGitState = RemodexGitState()

        override suspend fun commitGitChanges(
            threadId: String,
            message: String?,
        ): RemodexGitState = RemodexGitState()

        override suspend fun pullGitChanges(threadId: String): RemodexGitState = RemodexGitState()

        override suspend fun pushGitChanges(threadId: String): RemodexGitState = RemodexGitState()

        override suspend fun discardRuntimeChangesAndSync(threadId: String): RemodexGitState = RemodexGitState()

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

        override suspend fun pairWithQrPayload(payload: PairingQrPayload) = Unit

        override suspend fun retryConnection() {
            retryConnectionCalls += 1
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
    }

    private fun threadSummary(
        id: String,
        title: String,
        isRunning: Boolean = false,
        latestTurnTerminalState: RemodexTurnTerminalState? = null,
        messages: List<RemodexConversationItem> = emptyList(),
    ): com.emanueledipietro.remodex.model.RemodexThreadSummary {
        return com.emanueledipietro.remodex.model.RemodexThreadSummary(
            id = id,
            title = title,
            preview = "Preview",
            projectPath = "/tmp/remodex",
            lastUpdatedLabel = "Updated just now",
            isRunning = isRunning,
            latestTurnTerminalState = latestTurnTerminalState,
            queuedDrafts = 0,
            runtimeLabel = "Auto, medium reasoning",
            messages = messages,
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
}
