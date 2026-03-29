package com.emanueledipietro.remodex.data.app

import com.emanueledipietro.remodex.data.connection.InMemorySecureStore
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.SuccessfulQrBootstrapRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.UnexpectedRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.UnusedTrustedSessionResolver
import com.emanueledipietro.remodex.data.connection.createTestMacIdentity
import com.emanueledipietro.remodex.data.connection.createTestPairingPayload
import com.emanueledipietro.remodex.data.preferences.AppPreferences
import com.emanueledipietro.remodex.data.preferences.AppPreferencesRepository
import com.emanueledipietro.remodex.data.threads.FakeThreadSyncService
import com.emanueledipietro.remodex.data.threads.InMemoryThreadCacheStore
import com.emanueledipietro.remodex.data.threads.ThreadHydrationService
import com.emanueledipietro.remodex.data.threads.ThreadLocalTimelineService
import com.emanueledipietro.remodex.data.threads.ThreadResumeService
import com.emanueledipietro.remodex.data.threads.ThreadSyncService
import com.emanueledipietro.remodex.data.threads.ThreadSyncSnapshot
import com.emanueledipietro.remodex.data.connection.RpcError
import com.emanueledipietro.remodex.data.threads.TimelineMutation
import com.emanueledipietro.remodex.data.threads.ThreadCommandService
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRemodexAppRepositoryTest {
    @Test
    fun `complete onboarding updates persisted preferences`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = FakeThreadSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )

        repository.completeOnboarding()
        advanceUntilIdle()

        assertTrue(repository.session.value.onboardingCompleted)
    }

    @Test
    fun `selected thread persists when sync refresh keeps the thread available`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = FakeThreadSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        assertTrue(repository.session.value.threads.isNotEmpty())
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        assertEquals("thread-notifications", repository.session.value.selectedThread?.id)
        assertEquals("thread-notifications", preferencesRepository.preferencesState.value.selectedThreadId)

        syncService.updateThreads(
            syncService.threads.value.map { snapshot ->
                if (snapshot.id == "thread-notifications") {
                    snapshot.copy(
                        title = "Android notifications deep links",
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = snapshot.lastUpdatedEpochMs + 1_000,
                    )
                } else {
                    snapshot
                }
            },
        )
        advanceUntilIdle()

        assertEquals("thread-notifications", repository.session.value.selectedThread?.id)
        assertEquals("thread-notifications", preferencesRepository.preferencesState.value.selectedThreadId)
        assertEquals(SecureConnectionState.NOT_PAIRED, repository.session.value.secureConnection.secureState)
    }

    @Test
    fun `cached thread conversion preserves projected timeline order`() {
        val cachedThread = com.emanueledipietro.remodex.data.threads.CachedThreadRecord(
            id = "thread-projected",
            title = "Projected order",
            preview = "Review this change.",
            projectPath = "/tmp/projected-order",
            lastUpdatedLabel = "Updated now",
            lastUpdatedEpochMs = 1L,
            isRunning = true,
            runtimeConfig = RemodexRuntimeConfig(),
            timelineItems = listOf(
                RemodexConversationItem(
                    id = "user-1",
                    speaker = ConversationSpeaker.USER,
                    text = "Review this change.",
                    turnId = "turn-review",
                    orderIndex = 0,
                ),
                RemodexConversationItem(
                    id = "reasoning-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = "Thinking...",
                    turnId = "turn-review",
                    orderIndex = 2,
                ),
                RemodexConversationItem(
                    id = "command-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.COMMAND_EXECUTION,
                    text = "running git diff",
                    turnId = "turn-review",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals(
            listOf("user-1", "reasoning-1", "command-1"),
            cachedThread.toBaseThreadSummary().messages.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `thread projection cache falls back to full reprojection when assistant deltas can change intra-turn order`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        val baselineSnapshot = ThreadSyncSnapshot(
            id = "thread-fast-path-order",
            title = "Fast path order",
            preview = "First response",
            projectPath = "/tmp/project-fast-path-order",
            lastUpdatedLabel = "Updated just now",
            lastUpdatedEpochMs = 1L,
            isRunning = true,
            runtimeConfig = RemodexRuntimeConfig(),
            timelineMutations = listOf(
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "thinking-1",
                        speaker = ConversationSpeaker.SYSTEM,
                        kind = ConversationItemKind.REASONING,
                        text = "Inspecting repository state",
                        turnId = "turn-fast-path-order",
                        itemId = "thinking-item-1",
                        isStreaming = false,
                        orderIndex = 0L,
                    ),
                ),
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "assistant-1",
                        speaker = ConversationSpeaker.ASSISTANT,
                        kind = ConversationItemKind.CHAT,
                        text = "First response",
                        turnId = "turn-fast-path-order",
                        itemId = "assistant-item-1",
                        isStreaming = true,
                        orderIndex = 1L,
                    ),
                ),
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "command-1",
                        speaker = ConversationSpeaker.SYSTEM,
                        kind = ConversationItemKind.COMMAND_EXECUTION,
                        text = "completed git status --short",
                        turnId = "turn-fast-path-order",
                        itemId = "command-item-1",
                        isStreaming = false,
                        orderIndex = 2L,
                    ),
                ),
            ),
        )
        val nextSnapshot = baselineSnapshot.copy(
            timelineMutations = baselineSnapshot.timelineMutations + TimelineMutation.AssistantTextDelta(
                messageId = "assistant-1",
                turnId = "turn-fast-path-order",
                itemId = "assistant-item-1",
                delta = " with more detail",
                orderIndex = 3L,
            ),
        )

        val initialProjected = invokePrivateMethod<List<RemodexConversationItem>>(
            repository,
            "projectThreadTimelineItems",
            baselineSnapshot,
        )
        val cachedProjected = invokePrivateMethod<List<RemodexConversationItem>>(
            repository,
            "projectThreadTimelineItems",
            nextSnapshot,
        )
        val fullProjected = com.emanueledipietro.remodex.feature.turn.TurnTimelineReducer
            .reduceProjected(nextSnapshot.timelineMutations)

        assertEquals(initialProjected.map(RemodexConversationItem::id), listOf("thinking-1", "assistant-1", "command-1"))
        assertEquals(fullProjected.map(RemodexConversationItem::id), cachedProjected.map(RemodexConversationItem::id))
        assertEquals(fullProjected.map(RemodexConversationItem::text), cachedProjected.map(RemodexConversationItem::text))
    }

    @Test
    fun `encrypted reconnect resumes selected running thread to recover live output`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = ReconnectResumeSyncService(clearRunningOnRefresh = true)
        syncService.updateThreads(
            syncService.threads.value.map { snapshot ->
                if (snapshot.id == "thread-notifications") snapshot.copy(isRunning = true) else snapshot
            },
        )
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-repository-test",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = SuccessfulQrBootstrapRelayWebSocketFactory(
                macDeviceId = payload.macDeviceId,
                macIdentity = macIdentity,
            ),
            scope = this,
        )
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = coordinator,
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val hydrateCallsBeforeReconnect = syncService.hydrateCalls
        val resumeCallsBeforeReconnect = syncService.resumeCalls

        coordinator.rememberRelayPairing(payload)
        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
        advanceUntilIdle()

        assertTrue(syncService.hydrateCalls > hydrateCallsBeforeReconnect)
        assertEquals(resumeCallsBeforeReconnect + 1, syncService.resumeCalls)
    }

    @Test
    fun `encrypted reconnect keeps catching up selected streaming thread after initial hydrate`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = ReconnectStreamingCatchupSyncService()
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-repository-streaming-catchup",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = SuccessfulQrBootstrapRelayWebSocketFactory(
                macDeviceId = payload.macDeviceId,
                macIdentity = macIdentity,
            ),
            scope = this,
        )
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = coordinator,
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val hydrateCallsBeforeReconnect = syncService.hydrateCalls
        syncService.finalizeAfterHydrateCalls = hydrateCallsBeforeReconnect + 2

        coordinator.rememberRelayPairing(payload)
        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
        var catchupCompleted = false
        for (attempt in 0 until 100) {
            advanceUntilIdle()
            val selectedThread = repository.session.value.selectedThread
            if (
                selectedThread?.id == "thread-notifications" &&
                selectedThread.messages.any { item ->
                    item.id == "assistant-streaming-recovery" &&
                        item.text == "Recovered final output after delayed reconnect catchup." &&
                        !item.isStreaming
                }
            ) {
                catchupCompleted = true
                break
            }
            if (attempt < 99) {
                Thread.sleep(20)
            }
        }
        if (!catchupCompleted) {
            fail(
                "Expected reconnect catchup to finish but hydrateCalls=${syncService.hydrateCalls} refreshCalls=${syncService.refreshCalls} resumeCalls=${syncService.resumeCalls} selectedMessages=${repository.session.value.selectedThread?.messages}",
            )
        }
        assertTrue(syncService.hydrateCalls >= hydrateCallsBeforeReconnect + 2)
        assertEquals(0, syncService.resumeCalls)
    }

    @Test
    fun `duplicate encrypted snapshots in same attempt do not rerun selected thread recovery`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = ReconnectResumeSyncService()
        syncService.updateThreads(
            syncService.threads.value.map { snapshot ->
                if (snapshot.id == "thread-notifications") snapshot.copy(isRunning = true) else snapshot
            },
        )
        val coordinator = createConnectedSecureCoordinator()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = coordinator,
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val hydrateCallsAfterInitialRecovery = syncService.hydrateCalls
        val resumeCallsAfterInitialRecovery = syncService.resumeCalls
        val encryptedSnapshot = coordinator.state.value
        emitSecureSnapshot(
            coordinator = coordinator,
            snapshot = encryptedSnapshot.copy(phaseMessage = "${encryptedSnapshot.phaseMessage} duplicate"),
        )
        advanceUntilIdle()

        assertEquals(hydrateCallsAfterInitialRecovery, syncService.hydrateCalls)
        assertEquals(resumeCallsAfterInitialRecovery, syncService.resumeCalls)
    }

    @Test
    fun `move thread to project path keeps empty resumed thread local without forcing resume`() = runTest {
        val syncService = LocalProjectMoveSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.createThread(preferredProjectPath = "/tmp/local-main")
        advanceUntilIdle()
        awaitSelectedThread(
            repository = repository,
            description = "the newly created local thread before moving its project path",
        ) { thread ->
            thread?.projectPath == "/tmp/local-main"
        }
        val createdThreadId = requireNotNull(repository.session.value.selectedThread?.id) {
            "Expected a created thread"
        }

        repository.moveThreadToProjectPath(
            threadId = createdThreadId,
            projectPath = "/tmp/.codex/worktrees/feature-empty",
        )
        advanceUntilIdle()

        assertEquals(0, syncService.resumeCalls)
        assertEquals(
            "/tmp/.codex/worktrees/feature-empty",
            repository.session.value.selectedThread?.projectPath,
        )
    }

    @Test
    fun `delete thread persists deleted root and keeps descendants archived locally`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = FakeThreadSyncService(
            initialThreads = listOf(
                testThreadSnapshot(
                    id = "thread-parent",
                    title = "Parent thread",
                    projectPath = "/tmp/project-delete-parent",
                    lastUpdatedEpochMs = 3L,
                ),
                testThreadSnapshot(
                    id = "thread-child",
                    title = "Child thread",
                    projectPath = "/tmp/project-delete-parent",
                    lastUpdatedEpochMs = 2L,
                    parentThreadId = "thread-parent",
                ),
                testThreadSnapshot(
                    id = "thread-sibling",
                    title = "Sibling thread",
                    projectPath = "/tmp/project-delete-sibling",
                    lastUpdatedEpochMs = 1L,
                ),
            ),
        )
        val repository = createRepository(
            scope = backgroundScope,
            preferencesRepository = preferencesRepository,
            syncService = syncService,
        )
        advanceUntilIdle()

        repository.selectThread("thread-parent")
        advanceUntilIdle()
        repository.deleteThread("thread-parent")
        advanceUntilIdle()

        assertEquals(
            setOf("thread-child", "thread-sibling"),
            repository.session.value.threads.map(RemodexThreadSummary::id).toSet(),
        )
        assertEquals(
            RemodexThreadSyncState.ARCHIVED_LOCAL,
            repository.session.value.threads.first { thread -> thread.id == "thread-child" }.syncState,
        )
        assertEquals(setOf("thread-parent"), preferencesRepository.preferencesState.value.deletedThreadIds)
        assertEquals("thread-sibling", repository.session.value.selectedThread?.id)
    }

    @Test
    fun `persisted deleted thread ids keep sync results hidden`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = FakeThreadSyncService(
            initialThreads = listOf(
                testThreadSnapshot(
                    id = "thread-deleted",
                    title = "Deleted thread",
                    projectPath = "/tmp/project-deleted",
                    lastUpdatedEpochMs = 2L,
                ),
                testThreadSnapshot(
                    id = "thread-visible",
                    title = "Visible thread",
                    projectPath = "/tmp/project-visible",
                    lastUpdatedEpochMs = 1L,
                ),
            ),
        )
        val repository = createRepository(
            scope = backgroundScope,
            preferencesRepository = preferencesRepository,
            syncService = syncService,
        )
        advanceUntilIdle()

        repository.deleteThread("thread-deleted")
        advanceUntilIdle()

        assertEquals(listOf("thread-visible"), repository.session.value.threads.map(RemodexThreadSummary::id))

        syncService.updateThreads(
            listOf(
                testThreadSnapshot(
                    id = "thread-deleted",
                    title = "Deleted thread restored remotely",
                    projectPath = "/tmp/project-deleted",
                    lastUpdatedEpochMs = 4L,
                ),
                testThreadSnapshot(
                    id = "thread-visible",
                    title = "Visible thread",
                    projectPath = "/tmp/project-visible",
                    lastUpdatedEpochMs = 3L,
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("thread-visible"), repository.session.value.threads.map(RemodexThreadSummary::id))
    }

    @Test
    fun `move thread to project path reverts local change when resume fails for materialized thread`() = runTest {
        val syncService = LocalProjectMoveSyncService(
            shouldFailResumeForThreadId = "thread-notifications",
        )
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val originalProjectPath = requireNotNull(repository.session.value.selectedThread?.projectPath) {
            "Expected selected thread"
        }

        try {
            repository.moveThreadToProjectPath(
                threadId = "thread-notifications",
                projectPath = "/tmp/.codex/worktrees/feature-materialized",
            )
            fail("Expected moveThreadToProjectPath to throw")
        } catch (error: IllegalStateException) {
            assertEquals("resume failed", error.message)
        }
        advanceUntilIdle()

        assertEquals(1, syncService.resumeCalls)
        assertEquals(originalProjectPath, repository.session.value.selectedThread?.projectPath)
    }

    @Test
    fun `fork into prepared project path selects returned fork thread immediately`() = runTest {
        val syncService = DelayedForkVisibilitySyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val selectedForkThreadId = repository.forkThreadIntoProjectPath(
            threadId = "thread-notifications",
            projectPath = "/tmp/.codex/worktrees/feature-forked",
        )
        advanceUntilIdle()

        assertEquals("thread-forked-delayed", selectedForkThreadId)
        assertEquals("thread-forked-delayed", repository.session.value.selectedThread?.id)
        assertEquals("/tmp/.codex/worktrees/feature-forked", repository.session.value.selectedThread?.projectPath)
    }

    @Test
    fun `fork into prepared project path creates a fresh thread for empty resumed source`() = runTest {
        val syncService = EmptyThreadForkFallbackSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.createThread(preferredProjectPath = "/tmp/local-main")
        advanceUntilIdle()
        val sourceThreadId = requireNotNull(repository.session.value.selectedThread?.id) {
            "Expected created source thread"
        }

        val forkedThreadId = repository.forkThreadIntoProjectPath(
            threadId = sourceThreadId,
            projectPath = "/tmp/.codex/worktrees/feature-empty-fork",
        )
        advanceUntilIdle()

        assertEquals(0, syncService.forkThreadIntoProjectPathCalls)
        assertNotNull(forkedThreadId)
        assertNotEquals(sourceThreadId, forkedThreadId)
        assertEquals(forkedThreadId, repository.session.value.selectedThread?.id)
        assertEquals("/tmp/.codex/worktrees/feature-empty-fork", repository.session.value.selectedThread?.projectPath)
    }

    @Test
    fun `send prompt starts a local turn immediately when the thread is idle`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-notifications",
            prompt = "Ship the Android notification channels next.",
            attachments = emptyList(),
        )
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertEquals("thread-notifications", selectedThread?.id)
        assertTrue(selectedThread?.isRunning == true)
        assertEquals("Ship the Android notification channels next.", selectedThread?.preview)
        assertTrue(selectedThread?.messages.orEmpty().any { item ->
            item.text.contains("Ship the Android notification channels next.")
        })
    }

    @Test
    fun `send prompt publishes a pending user message before command service send returns`() = runTest {
        val syncService = BlockingSendPromptSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val sendJob = backgroundScope.launch {
            repository.sendPrompt(
                threadId = "thread-notifications",
                prompt = "Show this message before the bridge send finishes.",
                attachments = emptyList(),
            )
        }
        runCurrent()

        val selectedThread = repository.session.value.selectedThread
        val optimisticMessage = selectedThread?.messages.orEmpty().lastOrNull()

        assertEquals("thread-notifications", selectedThread?.id)
        assertTrue(selectedThread?.isRunning == true)
        assertEquals(
            "Show this message before the bridge send finishes.",
            selectedThread?.preview,
        )
        assertEquals(
            RemodexMessageDeliveryState.PENDING,
            optimisticMessage?.deliveryState,
        )
        assertEquals(
            "Show this message before the bridge send finishes.",
            optimisticMessage?.text,
        )

        syncService.allowSend()
        advanceUntilIdle()
        sendJob.join()
    }

    @Test
    fun `create thread selects the newly created chat`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        advanceUntilIdle()

        repository.createThread("/tmp/new-project")
        advanceUntilIdle()
        awaitSelectedThread(
            repository = repository,
            description = "the newly created chat thread",
        ) { thread ->
            thread?.projectPath == "/tmp/new-project"
        }

        val session = repository.session.value
        val selectedThread = session.selectedThread
        assertTrue(selectedThread != null)
        assertEquals(selectedThread?.id, session.selectedThreadId)
        assertEquals("/tmp/new-project", selectedThread?.projectPath)
    }

    @Test
    fun `create thread normalizes unsupported reasoning defaults for the selected model`() = runTest {
        val syncService = CreateThreadDefaultsCaptureSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.setDefaultModelId("gpt-5.3-codex")
        repository.setDefaultReasoningEffort("xhigh")
        advanceUntilIdle()

        repository.createThread("/tmp/review-project")
        advanceUntilIdle()

        assertEquals("gpt-5.3-codex", syncService.lastCreateThreadDefaults?.modelId)
        assertEquals("medium", syncService.lastCreateThreadDefaults?.reasoningEffort)
    }

    @Test
    fun `create thread can inherit effective runtime from the source thread`() = runTest {
        val syncService = CreateThreadDefaultsCaptureSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.setDefaultModelId("gpt-5.3-codex")
        repository.setDefaultReasoningEffort("xhigh")
        repository.selectThread("thread-notifications")
        advanceUntilIdle()
        repository.setReasoningEffort("thread-notifications", "medium")
        advanceUntilIdle()

        repository.createThread(
            preferredProjectPath = "/tmp/review-project",
            inheritRuntimeFromThreadId = "thread-notifications",
        )
        advanceUntilIdle()

        assertEquals("gpt-5.3-codex", syncService.lastCreateThreadDefaults?.modelId)
        assertEquals("medium", syncService.lastCreateThreadDefaults?.reasoningEffort)
        assertEquals("medium", repository.session.value.selectedThread?.runtimeConfig?.reasoningEffort)
    }

    @Test
    fun `newly created thread sends first prompt without an extra resume`() = runTest {
        val syncService = NewlyCreatedThreadTrackingSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.createThread("/tmp/new-project")
        advanceUntilIdle()

        val createdThreadId = repository.session.value.selectedThreadId
        assertTrue(createdThreadId != null)

        repository.sendPrompt(
            threadId = createdThreadId!!,
            prompt = "Open the brand new Android chat immediately.",
            attachments = emptyList(),
        )
        advanceUntilIdle()

        assertEquals(0, syncService.resumeCalls)
        assertEquals(createdThreadId, syncService.lastSendThreadId)
        assertTrue(repository.session.value.selectedThread?.isRunning == true)
    }

    @Test
    fun `send prompt keeps going when pre-send resume hits missing rollout after reconnect`() = runTest {
        val syncService = MaterializationRaceOnResumeSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.createThread("/tmp/reconnect-rollout-race")
        advanceUntilIdle()

        val createdThreadId = requireNotNull(repository.session.value.selectedThreadId) {
            "Expected a created thread"
        }
        syncService.forgetLocalResume(createdThreadId)

        repository.sendPrompt(
            threadId = createdThreadId,
            prompt = "Materialize this thread after reconnect.",
            attachments = emptyList(),
        )
        advanceUntilIdle()

        assertEquals(1, syncService.resumeCalls)
        assertEquals(createdThreadId, syncService.lastSendThreadId)
        assertTrue(repository.session.value.selectedThread?.isRunning == true)
    }

    @Test
    fun `streaming selected thread updates reach session state before cache persistence finishes`() = runTest {
        val syncService = FakeThreadSyncService()
        val cacheStore = BlockingThreadCacheStore()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createSecureCoordinator(backgroundScope),
            threadCacheStore = cacheStore,
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        cacheStore.blockWrites()
        syncService.updateThreads(
            syncService.threads.value.map { snapshot ->
                if (snapshot.id == "thread-notifications") {
                    snapshot.copy(
                        title = "Streaming title from sync",
                        isRunning = true,
                    )
                } else {
                    snapshot
                }
            },
        )

        awaitSelectedThreadTitle(
            repository = repository,
            expectedTitle = "Streaming title from sync",
        )

        cacheStore.allowWrites()
        advanceUntilIdle()
    }

    @Test
    fun `selected thread projection updates immediately during streaming and thread list catches up afterwards`() = runTest {
        val syncService = FakeThreadSyncService()
        val repository = createRepository(
            scope = backgroundScope,
            syncService = syncService,
        )
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        syncService.updateThreads(
            syncService.threads.value.map { snapshot ->
                if (snapshot.id == "thread-notifications") {
                    snapshot.copy(
                        title = "Streaming title only for the active conversation",
                        isRunning = true,
                    )
                } else {
                    snapshot
                }
            },
        )

        runCurrent()

        assertEquals(
            "Streaming title only for the active conversation",
            repository.session.value.selectedThread?.title,
        )

        advanceUntilIdle()

        assertEquals(
            "Streaming title only for the active conversation",
            repository.session.value.threads.firstOrNull { thread -> thread.id == "thread-notifications" }?.title,
        )
    }

    @Test
    fun `sending while a turn is running persists a queued follow up`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = FakeThreadSyncService()
        val repository = createRepository(
            scope = backgroundScope,
            preferencesRepository = preferencesRepository,
            syncService = syncService,
        )
        repository.selectThread("thread-android-client")
        advanceUntilIdle()
        repository.setPlanningMode("thread-android-client", RemodexPlanningMode.PLAN)
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-android-client",
            prompt = "Queue the composer and runtime follow-up.",
            attachments = emptyList(),
        )
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertTrue(selectedThread?.isRunning == true)
        assertEquals(1, selectedThread?.queuedDrafts)
        assertEquals(
            "Queue the composer and runtime follow-up.",
            selectedThread?.queuedDraftItems?.firstOrNull()?.text,
        )
        assertEquals(
            RemodexPlanningMode.PLAN,
            selectedThread?.queuedDraftItems?.firstOrNull()?.planningMode,
        )
        assertEquals(RemodexPlanningMode.PLAN, selectedThread?.runtimeConfig?.planningMode)
        assertEquals(
            1,
            preferencesRepository.preferencesState.value.queuedDraftsByThread["thread-android-client"]?.size,
        )
    }

    @Test
    fun `queued follow up preserves an explicit auto planning override`() = runTest {
        val syncService = PlanningModeCaptureSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        repository.selectThread("thread-android-client")
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-android-client",
            prompt = "Implement plan",
            attachments = emptyList(),
            planningModeOverride = RemodexPlanningMode.AUTO,
        )
        advanceUntilIdle()

        val queuedDraftId = repository.session.value.selectedThread
            ?.queuedDraftItems
            ?.firstOrNull()
            ?.also { draft ->
                assertEquals(RemodexPlanningMode.AUTO, draft.planningMode)
            }
            ?.id
        assertTrue(queuedDraftId != null)

        repository.stopTurn("thread-android-client")
        advanceUntilIdle()

        repository.sendQueuedDraft("thread-android-client", queuedDraftId!!)
        advanceUntilIdle()

        assertEquals(RemodexPlanningMode.AUTO, syncService.lastSendPlanningMode)
    }

    @Test
    fun `send prompt honors an explicit planning mode override`() = runTest {
        val syncService = PlanningModeCaptureSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        repository.selectThread("thread-notifications")
        advanceUntilIdle()
        repository.setPlanningMode("thread-notifications", RemodexPlanningMode.PLAN)
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-notifications",
            prompt = "Implement plan",
            attachments = emptyList(),
            planningModeOverride = RemodexPlanningMode.AUTO,
        )
        advanceUntilIdle()

        assertEquals(RemodexPlanningMode.AUTO, syncService.lastSendPlanningMode)
        assertTrue(
            repository.session.value.selectedThread?.messages.orEmpty().any { item ->
                item.text.contains("Implement plan")
            },
        )
    }

    @Test
    fun `send prompt resumes thread state before sending and queues when resume reveals a running turn`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = ResumeReportsRunningSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-notifications",
            prompt = "Queue after resume detects the remote run.",
            attachments = emptyList(),
        )
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertEquals("thread-notifications", selectedThread?.id)
        assertTrue(selectedThread?.isRunning == true)
        assertEquals(1, syncService.resumeCalls)
        assertEquals(0, syncService.sendPromptCalls)
        assertEquals(1, selectedThread?.queuedDrafts)
        assertEquals(
            "Queue after resume detects the remote run.",
            selectedThread?.queuedDraftItems?.firstOrNull()?.text,
        )
        assertEquals(
            "Completion and attention-needed alerts still need Android plumbing.",
            selectedThread?.preview,
        )
        assertFalse(
            selectedThread?.messages.orEmpty().any { item ->
                item.text.contains("Queue after resume detects the remote run.")
            },
        )
    }

    @Test
    fun `send prompt creates a continuation thread when the original thread is missing`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = FakeThreadSyncService()
        val commandService = MissingThreadContinuationCommandService(syncService)
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = commandService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()
        repository.setDefaultModelId("gpt-5.3-codex")
        repository.setDefaultReasoningEffort("medium")
        repository.setDefaultAccessMode(RemodexAccessMode.ON_REQUEST)
        repository.setPlanningMode("thread-notifications", RemodexPlanningMode.PLAN)
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-notifications",
            prompt = "Continue from the missing conversation.",
            attachments = emptyList(),
        )
        advanceUntilIdle()
        awaitSelectedThread(
            repository = repository,
            description = "a continuation thread after the original thread is missing",
        ) { thread ->
            thread != null && thread.id != "thread-notifications"
        }

        val session = repository.session.value
        val selectedThread = session.selectedThread
        assertTrue(selectedThread != null)
        assertTrue(selectedThread?.id != "thread-notifications")
        assertEquals(selectedThread?.id, session.selectedThreadId)
        assertEquals(selectedThread?.id, commandService.lastSuccessfulSendThreadId)
        assertTrue(
            selectedThread?.messages.orEmpty().any { item ->
                item.text.contains("Continued from archived thread `thread-notifications`")
            },
        )
        assertTrue(
            selectedThread?.messages.orEmpty().any { item ->
                item.text.contains("Continue from the missing conversation.")
            },
        )
        assertEquals("gpt-5.3-codex", selectedThread?.runtimeConfig?.selectedModelId)
        assertEquals("medium", selectedThread?.runtimeConfig?.reasoningEffort)
        assertEquals(RemodexAccessMode.ON_REQUEST, selectedThread?.runtimeConfig?.accessMode)
        assertEquals(RemodexPlanningMode.PLAN, selectedThread?.runtimeConfig?.planningMode)
        assertEquals(
            RemodexThreadSyncState.ARCHIVED_LOCAL,
            session.threads.first { thread -> thread.id == "thread-notifications" }.syncState,
        )
        assertEquals(
            "/Users/emanueledipietro/Developer/remodex/CodexMobileAndroid",
            selectedThread?.projectPath,
        )
    }

    @Test
    fun `send prompt creates a continuation thread when resume reports the original thread missing`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = MissingThreadOnResumeSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-notifications",
            prompt = "Continue after resume says the thread is gone.",
            attachments = emptyList(),
        )
        advanceUntilIdle()
        awaitSelectedThread(
            repository = repository,
            description = "a continuation thread after resume reports the original thread missing",
        ) { thread ->
            thread != null && thread.id != "thread-notifications"
        }

        val session = repository.session.value
        val selectedThread = session.selectedThread
        assertTrue(selectedThread != null)
        assertTrue(selectedThread?.id != "thread-notifications")
        assertEquals(selectedThread?.id, session.selectedThreadId)
        assertEquals(selectedThread?.id, syncService.lastSuccessfulSendThreadId)
        assertEquals(1, syncService.resumeCalls)
        assertTrue(
            selectedThread?.messages.orEmpty().any { item ->
                item.text.contains("Continued from archived thread `thread-notifications`")
            },
        )
        assertEquals(
            RemodexThreadSyncState.ARCHIVED_LOCAL,
            session.threads.first { thread -> thread.id == "thread-notifications" }.syncState,
        )
    }

    @Test
    fun `stop turn clears running state and queued drafts can send afterwards`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        repository.selectThread("thread-android-client")
        advanceUntilIdle()
        repository.setPlanningMode("thread-android-client", RemodexPlanningMode.PLAN)
        advanceUntilIdle()
        repository.sendPrompt(
            threadId = "thread-android-client",
            prompt = "Queue after stop.",
            attachments = emptyList(),
        )
        advanceUntilIdle()

        repository.stopTurn("thread-android-client")
        advanceUntilIdle()

        val stoppedThread = repository.session.value.selectedThread
        assertFalse(stoppedThread?.isRunning == true)
        val queuedDraftId = stoppedThread?.queuedDraftItems?.firstOrNull()?.id
        assertTrue(queuedDraftId != null)

        repository.sendQueuedDraft("thread-android-client", queuedDraftId!!)
        advanceUntilIdle()

        val resumedThread = repository.session.value.selectedThread
        assertTrue(resumedThread?.isRunning == true)
        assertEquals(0, resumedThread?.queuedDrafts)
        assertEquals(RemodexPlanningMode.PLAN, resumedThread?.runtimeConfig?.planningMode)
    }

    @Test
    fun `runtime override selections update the effective composer configuration`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        repository.setPlanningMode("thread-notifications", RemodexPlanningMode.PLAN)
        repository.setSelectedModelId("gpt-5.4")
        repository.setReasoningEffort("thread-notifications", "low")
        repository.setServiceTier("thread-notifications", null)
        repository.setAccessMode("thread-notifications", RemodexAccessMode.ON_REQUEST)
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertEquals("gpt-5.4", selectedThread?.runtimeConfig?.selectedModelId)
        assertEquals(RemodexPlanningMode.PLAN, selectedThread?.runtimeConfig?.planningMode)
        assertEquals("low", selectedThread?.runtimeConfig?.reasoningEffort)
        assertEquals(RemodexAccessMode.ON_REQUEST, selectedThread?.runtimeConfig?.accessMode)
        assertEquals("gpt-5.4, Plan, low reasoning", selectedThread?.runtimeLabel)
    }

    @Test
    fun `runtime defaults update the selected thread and appearance preference`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val repository = createRepository(
            scope = backgroundScope,
            preferencesRepository = preferencesRepository,
        )
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        repository.setDefaultReasoningEffort("high")
        repository.setDefaultAccessMode(RemodexAccessMode.FULL_ACCESS)
        repository.setAppearanceMode(RemodexAppearanceMode.DARK)
        advanceUntilIdle()

        val session = repository.session.value
        assertEquals("high", session.selectedThread?.runtimeConfig?.reasoningEffort)
        assertEquals(RemodexAccessMode.FULL_ACCESS, session.selectedThread?.runtimeConfig?.accessMode)
        assertEquals(RemodexAppearanceMode.DARK, session.appearanceMode)
        assertEquals(RemodexAppearanceMode.DARK, preferencesRepository.preferencesState.value.appearanceMode)
    }

    @Test
    fun `attachment only sends produce a local Android preview and attachment metadata`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-notifications",
            prompt = "",
            attachments = listOf(
                RemodexComposerAttachment(
                    id = "attachment-1",
                    uriString = "content://remodex/attachment-1",
                    displayName = "screenshot.png",
                    payloadDataUrl = "data:image/jpeg;base64,AAAA",
                ),
            ),
        )
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertEquals("Shared 1 image from Android.", selectedThread?.preview)
        assertTrue(
            selectedThread?.messages.orEmpty().any { item ->
                item.attachments.any { attachment ->
                    attachment.displayName == "screenshot.png" &&
                        attachment.previewDataUrl == "data:image/jpeg;base64,AAAA" &&
                        attachment.renderUriString == "data:image/jpeg;base64,AAAA"
                }
            },
        )
    }

    @Test
    fun `selected model is global across threads and xhigh survives normalization`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        repository.setSelectedModelId("gpt-5.4")
        repository.setReasoningEffort("thread-notifications", "xhigh")
        advanceUntilIdle()

        repository.selectThread("thread-reconnect")
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertEquals("gpt-5.4", selectedThread?.runtimeConfig?.selectedModelId)
        assertEquals("gpt-5.4", repository.session.value.runtimeDefaults.modelId)
        assertTrue(repository.session.value.availableModels.any { model -> model.id == "gpt-5.4" })
        assertTrue(
            repository.session.value.threads.first { thread -> thread.id == "thread-notifications" }
                .runtimeConfig.availableReasoningEfforts
                .any { option -> option.reasoningEffort == "xhigh" },
        )
    }

    private class TestAppPreferencesRepository : AppPreferencesRepository {
        private val backingState = MutableStateFlow(AppPreferences())
        val preferencesState: MutableStateFlow<AppPreferences> = backingState

        override val preferences: Flow<AppPreferences> = backingState

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            backingState.value = backingState.value.copy(onboardingCompleted = completed)
        }

        override suspend fun setSelectedThreadId(threadId: String?) {
            backingState.value = backingState.value.copy(selectedThreadId = threadId)
        }

        override suspend fun setThreadDeleted(
            threadId: String,
            deleted: Boolean,
        ) {
            val updatedDeletedThreadIds = backingState.value.deletedThreadIds.toMutableSet().apply {
                if (deleted) {
                    add(threadId)
                } else {
                    remove(threadId)
                }
            }
            backingState.value = backingState.value.copy(deletedThreadIds = updatedDeletedThreadIds)
        }

        override suspend fun setQueuedDrafts(
            threadId: String,
            drafts: List<com.emanueledipietro.remodex.model.RemodexQueuedDraft>,
        ) {
            val updatedDrafts = backingState.value.queuedDraftsByThread.toMutableMap().apply {
                if (drafts.isEmpty()) {
                    remove(threadId)
                } else {
                    this[threadId] = drafts
                }
            }
            backingState.value = backingState.value.copy(queuedDraftsByThread = updatedDrafts)
        }

        override suspend fun setRuntimeOverrides(
            threadId: String,
            overrides: com.emanueledipietro.remodex.model.RemodexRuntimeOverrides?,
        ) {
            val updatedOverrides = backingState.value.runtimeOverridesByThread.toMutableMap().apply {
                if (overrides == null) {
                    remove(threadId)
                } else {
                    this[threadId] = overrides
                }
            }
            backingState.value = backingState.value.copy(runtimeOverridesByThread = updatedOverrides)
        }

        override suspend fun setRuntimeDefaults(defaults: RemodexRuntimeDefaults) {
            backingState.value = backingState.value.copy(runtimeDefaults = defaults)
        }

        override suspend fun setAppearanceMode(mode: RemodexAppearanceMode) {
            backingState.value = backingState.value.copy(appearanceMode = mode)
        }

        override suspend fun setAppFontStyle(style: RemodexAppFontStyle) {
            backingState.value = backingState.value.copy(appFontStyle = style)
        }

        override suspend fun setMacNickname(deviceId: String, nickname: String?) {
            val updatedNicknames = backingState.value.macNicknamesByDeviceId.toMutableMap().apply {
                val normalizedNickname = nickname?.trim().orEmpty()
                if (normalizedNickname.isEmpty()) {
                    remove(deviceId)
                } else {
                    this[deviceId] = normalizedNickname
                }
            }
            backingState.value = backingState.value.copy(macNicknamesByDeviceId = updatedNicknames)
        }
    }

    private fun createRepository(
        scope: CoroutineScope,
        preferencesRepository: TestAppPreferencesRepository = TestAppPreferencesRepository(),
        syncService: FakeThreadSyncService = FakeThreadSyncService(),
    ): DefaultRemodexAppRepository {
        return DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = createSecureCoordinator(scope),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = scope,
        )
    }

    private fun testThreadSnapshot(
        id: String,
        title: String,
        projectPath: String,
        lastUpdatedEpochMs: Long,
        parentThreadId: String? = null,
    ): ThreadSyncSnapshot {
        return ThreadSyncSnapshot(
            id = id,
            title = title,
            preview = "$title preview",
            projectPath = projectPath,
            lastUpdatedLabel = "Updated just now",
            lastUpdatedEpochMs = lastUpdatedEpochMs,
            isRunning = false,
            syncState = RemodexThreadSyncState.LIVE,
            parentThreadId = parentThreadId,
            runtimeConfig = RemodexRuntimeConfig(),
            timelineMutations = emptyList(),
        )
    }

    private class BlockingThreadCacheStore : com.emanueledipietro.remodex.data.threads.ThreadCacheStore {
        private val backingThreads = MutableStateFlow<List<com.emanueledipietro.remodex.data.threads.CachedThreadRecord>>(emptyList())
        override val threads: Flow<List<com.emanueledipietro.remodex.data.threads.CachedThreadRecord>> = backingThreads

        private var gate = CompletableDeferred<Unit>()
        private var shouldBlock = false

        fun blockWrites() {
            shouldBlock = true
            gate = CompletableDeferred()
        }

        fun allowWrites() {
            shouldBlock = false
            gate.complete(Unit)
        }

        override suspend fun replaceThreads(threads: List<com.emanueledipietro.remodex.data.threads.CachedThreadRecord>) {
            if (shouldBlock) {
                gate.await()
            }
            backingThreads.value = threads
        }
    }

    private fun createSecureCoordinator(scope: CoroutineScope): SecureConnectionCoordinator {
        return SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = scope,
        )
    }

    private suspend fun TestScope.createConnectedSecureCoordinator(): SecureConnectionCoordinator {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-repository-test",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = SuccessfulQrBootstrapRelayWebSocketFactory(
                macDeviceId = payload.macDeviceId,
                macIdentity = macIdentity,
            ),
            scope = this,
        )
        coordinator.rememberRelayPairing(payload)
        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
        return coordinator
    }

    private suspend fun TestScope.awaitSecureState(
        coordinator: SecureConnectionCoordinator,
        expectedState: SecureConnectionState,
    ) {
        repeat(40) {
            advanceUntilIdle()
            if (coordinator.state.value.secureState == expectedState) {
                return
            }
            Thread.sleep(10)
        }
        fail("Expected $expectedState but was ${coordinator.state.value.secureState}")
    }

    private suspend fun TestScope.awaitSelectedThreadTitle(
        repository: DefaultRemodexAppRepository,
        expectedTitle: String,
    ) {
        repeat(200) {
            advanceUntilIdle()
            runCurrent()
            if (repository.session.value.selectedThread?.title == expectedTitle) {
                return
            }
            Thread.sleep(10)
        }
        fail("Expected selected thread title to be $expectedTitle but was ${repository.session.value.selectedThread?.title}")
    }

    private suspend fun TestScope.awaitSelectedThread(
        repository: DefaultRemodexAppRepository,
        description: String,
        predicate: (RemodexThreadSummary?) -> Boolean,
    ) {
        repeat(500) {
            advanceUntilIdle()
            runCurrent()
            val selectedThread = repository.session.value.selectedThread
            if (predicate(selectedThread)) {
                return
            }
            Thread.sleep(10)
        }
        fail("Expected $description but selected thread was ${repository.session.value.selectedThread?.id}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> invokePrivateMethod(
        target: Any,
        methodName: String,
        vararg args: Any?,
    ): T {
        val method = target.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterCount == args.size
        } ?: error("Missing method $methodName with ${args.size} parameters")
        method.isAccessible = true
        return method.invoke(target, *args) as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun emitSecureSnapshot(
        coordinator: SecureConnectionCoordinator,
        snapshot: SecureConnectionSnapshot,
    ) {
        val field = coordinator.javaClass.getDeclaredField("connectionState")
        field.isAccessible = true
        val state = field.get(coordinator) as MutableStateFlow<SecureConnectionSnapshot>
        state.value = snapshot
    }

    private class ResumeReportsRunningSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var resumeCalls: Int = 0
            private set
        var sendPromptCalls: Int = 0
            private set

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            if (threadId == "thread-notifications") {
                resumeCalls += 1
                delegate.updateThreads(
                    delegate.threads.value.map { snapshot ->
                        if (snapshot.id == threadId) {
                            snapshot.copy(isRunning = true)
                        } else {
                            snapshot
                        }
                    },
                )
            }
            return delegate.threads.value.firstOrNull { snapshot -> snapshot.id == threadId }
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return delegate.isThreadResumedLocally(threadId)
        }

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            runtimeConfig: RemodexRuntimeConfig,
            attachments: List<RemodexComposerAttachment>,
        ) {
            sendPromptCalls += 1
            delegate.sendPrompt(threadId, prompt, runtimeConfig, attachments)
        }
    }

    private class BlockingSendPromptSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate {
        private val sendGate = CompletableDeferred<Unit>()

        fun allowSend() {
            sendGate.complete(Unit)
        }

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            runtimeConfig: RemodexRuntimeConfig,
            attachments: List<RemodexComposerAttachment>,
        ) {
            sendGate.await()
            delegate.sendPrompt(threadId, prompt, runtimeConfig, attachments)
        }
    }

    private class PlanningModeCaptureSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate {
        var lastSendPlanningMode: RemodexPlanningMode? = null
            private set

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            runtimeConfig: RemodexRuntimeConfig,
            attachments: List<RemodexComposerAttachment>,
        ) {
            lastSendPlanningMode = runtimeConfig.planningMode
            delegate.sendPrompt(threadId, prompt, runtimeConfig, attachments)
        }
    }

    private class MissingThreadContinuationCommandService(
        private val syncService: FakeThreadSyncService,
    ) : ThreadCommandService by syncService {
        var lastSuccessfulSendThreadId: String? = null
            private set
        private var firstMissingThreadId: String? = null

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            runtimeConfig: RemodexRuntimeConfig,
            attachments: List<RemodexComposerAttachment>,
        ) {
            if (firstMissingThreadId == null && threadId == "thread-notifications") {
                firstMissingThreadId = threadId
                throw RpcError(
                    code = -32600,
                    message = "thread not found: $threadId",
                )
            }
            lastSuccessfulSendThreadId = threadId
            syncService.sendPrompt(
                threadId = threadId,
                prompt = prompt,
                runtimeConfig = runtimeConfig,
                attachments = attachments,
            )
        }
    }

    private class MissingThreadOnResumeSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var resumeCalls: Int = 0
            private set
        var lastSuccessfulSendThreadId: String? = null
            private set

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            if (threadId == "thread-notifications") {
                resumeCalls += 1
                throw RpcError(
                    code = -32600,
                    message = "thread not found: $threadId",
                )
            }
            return delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return delegate.isThreadResumedLocally(threadId)
        }

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            runtimeConfig: RemodexRuntimeConfig,
            attachments: List<RemodexComposerAttachment>,
        ) {
            lastSuccessfulSendThreadId = threadId
            delegate.sendPrompt(threadId, prompt, runtimeConfig, attachments)
        }
    }

    private class NewlyCreatedThreadTrackingSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var resumeCalls: Int = 0
            private set
        var lastSendThreadId: String? = null
            private set

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            resumeCalls += 1
            return delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return delegate.isThreadResumedLocally(threadId)
        }

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            runtimeConfig: RemodexRuntimeConfig,
            attachments: List<RemodexComposerAttachment>,
        ) {
            lastSendThreadId = threadId
            delegate.sendPrompt(threadId, prompt, runtimeConfig, attachments)
        }
    }

    private class MaterializationRaceOnResumeSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadResumeService, ThreadLocalTimelineService by delegate {
        private val forgottenLocalResumeThreadIds = mutableSetOf<String>()
        var resumeCalls: Int = 0
            private set
        var lastSendThreadId: String? = null
            private set

        fun forgetLocalResume(threadId: String) {
            forgottenLocalResumeThreadIds.add(threadId)
        }

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            if (threadId in forgottenLocalResumeThreadIds) {
                resumeCalls += 1
                throw RpcError(
                    code = -32000,
                    message = "No rollout found for thread id $threadId",
                )
            }
            return delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return threadId !in forgottenLocalResumeThreadIds && delegate.isThreadResumedLocally(threadId)
        }

        override suspend fun sendPrompt(
            threadId: String,
            prompt: String,
            runtimeConfig: RemodexRuntimeConfig,
            attachments: List<RemodexComposerAttachment>,
        ) {
            lastSendThreadId = threadId
            delegate.sendPrompt(threadId, prompt, runtimeConfig, attachments)
        }
    }

    private class CreateThreadDefaultsCaptureSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadResumeService by delegate, ThreadLocalTimelineService by delegate, ThreadCommandService by delegate {
        var lastCreateThreadDefaults: RemodexRuntimeDefaults? = null
            private set

        override suspend fun createThread(
            preferredProjectPath: String?,
            runtimeDefaults: RemodexRuntimeDefaults,
        ): ThreadSyncSnapshot? {
            lastCreateThreadDefaults = runtimeDefaults
            return delegate.createThread(preferredProjectPath, runtimeDefaults)
        }
    }

    private class ReconnectResumeSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
        private val clearRunningOnRefresh: Boolean = false,
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadHydrationService, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var hydrateCalls: Int = 0
            private set
        var resumeCalls: Int = 0
        var refreshCalls: Int = 0
            private set

        override suspend fun refreshThreads() {
            refreshCalls += 1
            if (clearRunningOnRefresh) {
                delegate.updateThreads(
                    delegate.threads.value.map { snapshot ->
                        snapshot.copy(isRunning = false)
                    },
                )
            }
        }

        override suspend fun hydrateThread(threadId: String) {
            hydrateCalls += 1
        }

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            resumeCalls += 1
            return delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return delegate.isThreadResumedLocally(threadId)
        }

        fun updateThreads(threads: List<ThreadSyncSnapshot>) {
            delegate.updateThreads(threads)
        }
    }

    private class ReconnectStreamingCatchupSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadHydrationService, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var hydrateCalls: Int = 0
            private set
        var finalizeAfterHydrateCalls: Int = 2
        var refreshCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set

        init {
            delegate.updateThreads(
                delegate.threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = false,
                            timelineMutations = listOf(
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "assistant-streaming-recovery",
                                        speaker = ConversationSpeaker.ASSISTANT,
                                        kind = ConversationItemKind.CHAT,
                                        text = "Recovered final output",
                                        turnId = "turn-streaming-recovery",
                                        itemId = "assistant-streaming-recovery",
                                        isStreaming = true,
                                        orderIndex = 0L,
                                    ),
                                ),
                            ),
                        )
                    } else {
                        snapshot
                    }
                },
            )
        }

        override suspend fun refreshThreads() {
            refreshCalls += 1
        }

        override suspend fun hydrateThread(threadId: String) {
            hydrateCalls += 1
            if (threadId == "thread-notifications" && hydrateCalls >= finalizeAfterHydrateCalls) {
                delegate.updateThreads(
                    delegate.threads.value.map { snapshot ->
                        if (snapshot.id == threadId) {
                            snapshot.copy(
                                isRunning = false,
                                timelineMutations = listOf(
                                    TimelineMutation.Upsert(
                                        RemodexConversationItem(
                                            id = "assistant-streaming-recovery",
                                            speaker = ConversationSpeaker.ASSISTANT,
                                            kind = ConversationItemKind.CHAT,
                                            text = "Recovered final output after delayed reconnect catchup.",
                                            turnId = "turn-streaming-recovery",
                                            itemId = "assistant-streaming-recovery",
                                            isStreaming = false,
                                            orderIndex = 0L,
                                        ),
                                    ),
                                ),
                            )
                        } else {
                            snapshot
                        }
                    },
                )
            }
        }

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            resumeCalls += 1
            return delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return delegate.isThreadResumedLocally(threadId)
        }
    }

    private class LocalProjectMoveSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
        private val shouldFailResumeForThreadId: String? = null,
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var resumeCalls: Int = 0
            private set

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            resumeCalls += 1
            if (threadId == shouldFailResumeForThreadId) {
                throw IllegalStateException("resume failed")
            }
            return delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
        }

        override suspend fun updateThreadProjectPathLocally(
            threadId: String,
            projectPath: String,
        ): ThreadSyncSnapshot? {
            return delegate.updateThreadProjectPathLocally(threadId, projectPath)
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return delegate.isThreadResumedLocally(threadId)
        }
    }

    private class DelayedForkVisibilitySyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate {
        override suspend fun forkThreadIntoProjectPath(
            threadId: String,
            projectPath: String,
        ): ThreadSyncSnapshot? {
            val source = delegate.threads.value.firstOrNull { snapshot -> snapshot.id == threadId } ?: return null
            return ThreadSyncSnapshot(
                id = "thread-forked-delayed",
                title = "${source.title} fork",
                name = null,
                preview = source.preview,
                projectPath = projectPath,
                lastUpdatedLabel = source.lastUpdatedLabel,
                lastUpdatedEpochMs = source.lastUpdatedEpochMs + 1,
                isRunning = false,
                syncState = RemodexThreadSyncState.LIVE,
                parentThreadId = source.id,
                agentNickname = source.agentNickname,
                agentRole = source.agentRole,
                activeTurnId = null,
                latestTurnTerminalState = null,
                stoppedTurnIds = emptySet(),
                runtimeConfig = source.runtimeConfig,
                timelineMutations = source.timelineMutations,
            )
        }
    }

    private class EmptyThreadForkFallbackSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadResumeService by delegate {
        var forkThreadIntoProjectPathCalls: Int = 0

        override suspend fun forkThreadIntoProjectPath(
            threadId: String,
            projectPath: String,
        ): ThreadSyncSnapshot? {
            forkThreadIntoProjectPathCalls += 1
            return delegate.forkThreadIntoProjectPath(threadId, projectPath)
        }
    }
}
