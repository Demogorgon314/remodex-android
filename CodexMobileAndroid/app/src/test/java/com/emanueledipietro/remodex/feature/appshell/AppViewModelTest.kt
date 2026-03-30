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
import com.emanueledipietro.remodex.model.RemodexBridgeUpdatePrompt
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
import com.emanueledipietro.remodex.model.RemodexGitBranches
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitRemoteUrl
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexGitRepoSync
import com.emanueledipietro.remodex.model.RemodexGitWorktreeChangeTransferMode
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
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
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(null, viewModel.uiState.value.composer.reviewSelection?.target)
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
            listOf("/tmp/remodex-review" to "thread-1"),
            repository.createThreadRequests,
        )
        assertEquals(
            listOf(Triple("thread-created", RemodexComposerReviewTarget.UNCOMMITTED_CHANGES, null)),
            repository.codeReviewRequests,
        )
        assertEquals("", viewModel.uiState.value.composer.draftText)
        assertNull(viewModel.uiState.value.composer.reviewSelection)
        assertEquals("thread-created", viewModel.uiState.value.selectedThread?.id)
        assertEquals(0L, viewModel.uiState.value.composerSendDismissSignal)
        assertEquals(1L, viewModel.uiState.value.composerSendAnchorSignal)
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

    @Test
    fun `checking out an elsewhere branch selects the matching thread instead of calling git checkout`() = runTest {
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

        assertEquals(listOf("thread-1"), repository.selectedThreadRequests)
        assertTrue(repository.checkoutGitBranchRequests.isEmpty())
        assertEquals("thread-1", viewModel.uiState.value.selectedThread?.id)
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

        viewModel.handoffThreadToWorktree("feature/handoff", "main")
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("thread-1", "feature/handoff", "main")),
            repository.createGitWorktreeResultRequests,
        )
        assertEquals(
            listOf("thread-1" to "/tmp/remodex/.codex/worktrees/feature/handoff"),
            repository.moveThreadToProjectPathRequests,
        )
        assertEquals(
            "/tmp/remodex/.codex/worktrees/feature/handoff",
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
        val gptAccountSnapshotFlow = MutableStateFlow(RemodexGptAccountSnapshot())
        val gptAccountErrorMessageFlow = MutableStateFlow<String?>(null)
        val bridgeVersionStatusFlow = MutableStateFlow(RemodexBridgeVersionStatus())
        val usageStatusFlow = MutableStateFlow(RemodexUsageStatus())
        val hydrateRequests = mutableListOf<String>()
        val selectedThreadRequests = mutableListOf<String>()
        val previewRequests = mutableListOf<Pair<String, String>>()
        val applyRequests = mutableListOf<Pair<String, String>>()
        val sentPrompts = mutableListOf<Triple<String, String, List<RemodexComposerAttachment>>>()
        val sentPromptPlanningModeOverrides = mutableListOf<RemodexPlanningMode?>()
        val setPlanningModeRequests = mutableListOf<Pair<String, RemodexPlanningMode>>()
        val codeReviewRequests = mutableListOf<Triple<String, RemodexComposerReviewTarget, String?>>()
        val createThreadRequests = mutableListOf<Pair<String?, String?>>()
        val checkoutGitBranchRequests = mutableListOf<Pair<String, String>>()
        val createGitBranchRequests = mutableListOf<Pair<String, String>>()
        val createGitWorktreeResultRequests = mutableListOf<Triple<String, String, String?>>()
        val commitGitChangesRequests = mutableListOf<Pair<String, String?>>()
        val commitAndPushRequests = mutableListOf<Pair<String, String?>>()
        val continueOnMacRequests = mutableListOf<String>()
        val discardRuntimeChangesRequests = mutableListOf<String>()
        val moveThreadToProjectPathRequests = mutableListOf<Pair<String, String>>()
        val forkThreadRequests = mutableListOf<Triple<String, RemodexComposerForkDestination, String?>>()
        val forkThreadIntoProjectPathRequests = mutableListOf<Pair<String, String>>()
        var fileSearchResults: List<RemodexFuzzyFileMatch> = emptyList()
        var skillResults: List<RemodexSkillMetadata> = emptyList()
        var refreshRequests = 0
        var retryConnectionCalls = 0
        var disconnectCalls = 0
        var dismissBridgeUpdatePromptCalls = 0
        var refreshDelayMs = 1_000L
        var hydrateDelayMs = 0L
        var sendPromptDelayMs = 0L
        var continueOnMacDelayMs = 0L
        var createThreadDelayMs = 0L
        var createThreadError: Throwable? = null
        var sendPromptError: Throwable? = null
        var continueOnMacError: Throwable? = null
        var forkThreadError: Throwable? = null
        var forkThreadResult: String? = null
        var forkThreadFailureSessionSnapshot: RemodexSessionSnapshot? = null
        var gitDiffRequests = 0
        var gitDiffDelayMs = 0L
        var gitDiffResult = RemodexGitRepoDiff()
        var gitStateResult = RemodexGitState()
        var gitStateError: Throwable? = null
        var checkoutGitBranchError: Throwable? = null
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
            delay(hydrateDelayMs)
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
        ) {
            if (sendPromptDelayMs > 0) {
                delay(sendPromptDelayMs)
            }
            sendPromptError?.let { throw it }
            sentPrompts += Triple(threadId, prompt, attachments)
            sentPromptPlanningModeOverrides += planningModeOverride
        }

        override suspend fun respondToStructuredUserInput(
            requestId: JsonElement,
            answersByQuestionId: Map<String, List<String>>,
        ) = Unit

        override suspend fun continueOnMac(threadId: String) {
            continueOnMacRequests += threadId
            if (continueOnMacDelayMs > 0) {
                delay(continueOnMacDelayMs)
            }
            continueOnMacError?.let { throw it }
        }

        override suspend fun stopTurn(threadId: String) = Unit

        override suspend fun sendQueuedDraft(threadId: String, draftId: String) = Unit

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

        override suspend fun activateBridgeProfile(profileId: String): Boolean = true

        override suspend fun removeBridgeProfile(profileId: String): String? = null

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
        ) {
            codeReviewRequests += Triple(threadId, target, baseBranch)
        }

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
        ): RemodexGitState = RemodexGitState()

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
            return RemodexGitState()
        }

        override suspend fun commitAndPushGitChanges(
            threadId: String,
            message: String?,
        ): RemodexGitState {
            commitAndPushRequests += threadId to message
            return RemodexGitState(lastActionMessage = "Committed and pushed the current branch.")
        }

        override suspend fun pullGitChanges(threadId: String): RemodexGitState = RemodexGitState()

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

        override suspend fun pairWithQrPayload(payload: PairingQrPayload) = Unit

        override suspend fun dismissBridgeUpdatePrompt() {
            dismissBridgeUpdatePromptCalls += 1
            snapshot.value = snapshot.value.copy(bridgeUpdatePrompt = null)
        }

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
        projectPath: String = "/tmp/remodex",
        isRunning: Boolean = false,
        latestTurnTerminalState: RemodexTurnTerminalState? = null,
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
            latestTurnTerminalState = latestTurnTerminalState,
            queuedDrafts = 0,
            runtimeLabel = "Auto, medium reasoning",
            runtimeConfig = runtimeConfig,
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
