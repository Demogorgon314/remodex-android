package com.emanueledipietro.remodex.data.app

import com.emanueledipietro.remodex.data.connection.InMemorySecureStore
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.ScriptedRpcRelayWebSocketFactory
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
import com.emanueledipietro.remodex.data.voice.RemodexVoiceTranscriptionService
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerMentionedFile
import com.emanueledipietro.remodex.model.RemodexComposerMentionedSkill
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexContextWindowUsage
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexQueuedDraftContext
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRemodexAppRepositoryTest {
    @Test
    fun `live context window usage updates selected thread usage status`() = runTest {
        val syncService = FakeThreadSyncService()
        val repository = createRepository(scope = backgroundScope, syncService = syncService)
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        setLiveContextWindowUsage(
            repository = repository,
            usageByThread = mapOf(
                "thread-notifications" to
                    RemodexContextWindowUsage(tokensUsed = 173_033, tokenLimit = 258_400),
            ),
        )
        invokePrivateMethod<Unit>(
            repository,
            "publishSelectedThreadContextWindowUsage",
            "thread-notifications",
        )

        assertEquals(173_033, repository.usageStatus.value.contextWindowUsage?.tokensUsed)
        assertEquals(258_400, repository.usageStatus.value.contextWindowUsage?.tokenLimit)
    }

    @Test
    fun `selected thread switch remaps cached context window usage`() = runTest {
        val syncService = FakeThreadSyncService()
        val repository = createRepository(scope = backgroundScope, syncService = syncService)
        advanceUntilIdle()

        setLiveContextWindowUsage(
            repository = repository,
            usageByThread = mapOf(
                "thread-notifications" to
                    RemodexContextWindowUsage(tokensUsed = 173_033, tokenLimit = 258_400),
                "thread-reconnect" to
                    RemodexContextWindowUsage(tokensUsed = 91_000, tokenLimit = 128_000),
            ),
        )

        repository.selectThread("thread-notifications")
        advanceUntilIdle()
        invokePrivateMethod<Unit>(
            repository,
            "publishSelectedThreadContextWindowUsage",
            "thread-notifications",
        )
        assertEquals(173_033, repository.usageStatus.value.contextWindowUsage?.tokensUsed)

        repository.selectThread("thread-reconnect")
        advanceUntilIdle()
        invokePrivateMethod<Unit>(
            repository,
            "publishSelectedThreadContextWindowUsage",
            "thread-reconnect",
        )
        assertEquals(91_000, repository.usageStatus.value.contextWindowUsage?.tokensUsed)
        assertEquals(128_000, repository.usageStatus.value.contextWindowUsage?.tokenLimit)
    }

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
    fun `selected thread detail survives when thread list temporarily omits the selected thread`() = runTest {
        val syncService = FakeThreadSyncService()
        val repository = createRepository(scope = backgroundScope, syncService = syncService)
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val hydratedSelectedThread = requireNotNull(repository.session.value.selectedThread).copy(
            title = "Hydrated Android notifications",
        )
        setSelectedThreadDetail(
            repository = repository,
            threadId = hydratedSelectedThread.id,
            thread = hydratedSelectedThread,
        )

        syncService.updateThreads(
            syncService.threads.value.filterNot { snapshot ->
                snapshot.id == hydratedSelectedThread.id
            },
        )
        advanceUntilIdle()

        assertEquals(hydratedSelectedThread.id, repository.session.value.selectedThreadId)
        assertEquals(
            "Hydrated Android notifications",
            repository.session.value.selectedThreadSnapshot?.title,
        )
        assertEquals(hydratedSelectedThread.id, repository.session.value.selectedThread?.id)
    }

    @Test
    fun `switching selected thread clears previously retained selected detail`() = runTest {
        val syncService = FakeThreadSyncService()
        val repository = createRepository(scope = backgroundScope, syncService = syncService)
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()
        val hydratedSelectedThread = requireNotNull(repository.session.value.selectedThread).copy(
            title = "Hydrated Android notifications",
        )
        setSelectedThreadDetail(
            repository = repository,
            threadId = hydratedSelectedThread.id,
            thread = hydratedSelectedThread,
        )

        repository.selectThread("thread-reconnect")
        advanceUntilIdle()

        assertEquals("thread-reconnect", repository.session.value.selectedThreadId)
        assertEquals("thread-reconnect", repository.session.value.selectedThreadSnapshot?.id)
        assertEquals("thread-reconnect", repository.session.value.selectedThread?.id)
        assertNotEquals(
            "Hydrated Android notifications",
            repository.session.value.selectedThreadSnapshot?.title,
        )
    }

    @Test
    fun `selected thread updates the active thread hint for live sync attribution`() = runTest {
        val syncService = FakeThreadSyncService()
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

        assertEquals("thread-notifications", syncService.activeThreadHint)
    }

    @Test
    fun `project group collapse persists in preferences and session`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val repository = createRepository(
            scope = backgroundScope,
            preferencesRepository = preferencesRepository,
        )
        advanceUntilIdle()

        repository.setProjectGroupCollapsed(
            groupId = "project:/tmp/remodex",
            collapsed = true,
        )
        advanceUntilIdle()

        assertEquals(
            setOf("project:/tmp/remodex"),
            preferencesRepository.preferencesState.value.collapsedProjectGroupIds,
        )
        assertEquals(
            setOf("project:/tmp/remodex"),
            repository.session.value.collapsedProjectGroupIds,
        )

        repository.setProjectGroupCollapsed(
            groupId = "project:/tmp/remodex",
            collapsed = false,
        )
        advanceUntilIdle()

        assertTrue(preferencesRepository.preferencesState.value.collapsedProjectGroupIds.isEmpty())
        assertTrue(repository.session.value.collapsedProjectGroupIds.isEmpty())
    }

    @Test
    fun `trusted reconnect disconnect preserves last known threads instead of clearing sidebar`() = runTest {
        val syncService = FakeThreadSyncService()
        val coordinator = createConnectedSecureCoordinator()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = coordinator,
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        val expectedThreadIds = repository.session.value.threads.map(RemodexThreadSummary::id)
        assertTrue(expectedThreadIds.isNotEmpty())

        coordinator.disconnect()
        syncService.updateThreads(emptyList())
        advanceUntilIdle()

        assertEquals(SecureConnectionState.TRUSTED_MAC, coordinator.state.value.secureState)
        assertEquals(expectedThreadIds, repository.session.value.threads.map(RemodexThreadSummary::id))
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
    fun `thread projection cache reuses projected items when timeline mutations are unchanged`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        val snapshot = ThreadSyncSnapshot(
            id = "thread-cache-hit",
            title = "Cache hit",
            preview = "Streaming output",
            projectPath = "/tmp/project-cache-hit",
            lastUpdatedLabel = "Updated just now",
            lastUpdatedEpochMs = 1L,
            isRunning = true,
            runtimeConfig = RemodexRuntimeConfig(),
            timelineMutations = listOf(
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "assistant-1",
                        speaker = ConversationSpeaker.ASSISTANT,
                        kind = ConversationItemKind.CHAT,
                        text = "Streaming output",
                        turnId = "turn-cache-hit",
                        itemId = "assistant-item-1",
                        isStreaming = true,
                        orderIndex = 0L,
                    ),
                ),
            ),
        )

        val initialProjected = invokePrivateMethod<List<RemodexConversationItem>>(
            repository,
            "projectThreadTimelineItems",
            snapshot,
        )
        val cachedProjected = invokePrivateMethod<List<RemodexConversationItem>>(
            repository,
            "projectThreadTimelineItems",
            snapshot.copy(lastUpdatedEpochMs = 2L),
        )

        assertTrue(initialProjected === cachedProjected)
    }

    @Test
    fun `thread projection cache keeps incremental correctness when streaming assistant upsert replaces the tail mutation`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        val userMutation = TimelineMutation.Upsert(
            RemodexConversationItem(
                id = "user-1",
                speaker = ConversationSpeaker.USER,
                kind = ConversationItemKind.CHAT,
                text = "Ship it",
                orderIndex = 0L,
            ),
        )
        val assistantMutation = TimelineMutation.Upsert(
            RemodexConversationItem(
                id = "assistant-1",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "First chunk",
                turnId = "turn-cache-tail-replace",
                itemId = "assistant-item-1",
                isStreaming = true,
                orderIndex = 1L,
            ),
        )
        val baselineSnapshot = ThreadSyncSnapshot(
            id = "thread-cache-tail-replace",
            title = "Tail replacement",
            preview = "First chunk",
            projectPath = "/tmp/project-cache-tail-replace",
            lastUpdatedLabel = "Updated just now",
            lastUpdatedEpochMs = 1L,
            isRunning = true,
            runtimeConfig = RemodexRuntimeConfig(),
            timelineMutations = listOf(
                userMutation,
                assistantMutation,
            ),
        )
        val nextSnapshot = baselineSnapshot.copy(
            timelineMutations = listOf(
                userMutation,
                TimelineMutation.Upsert(
                    (assistantMutation as TimelineMutation.Upsert).item.copy(
                        text = "First chunkSecond chunk",
                        isStreaming = true,
                    ),
                ),
            ),
        )

        invokePrivateMethod<List<RemodexConversationItem>>(
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

        assertEquals(fullProjected.map(RemodexConversationItem::id), cachedProjected.map(RemodexConversationItem::id))
        assertEquals(fullProjected.map(RemodexConversationItem::text), cachedProjected.map(RemodexConversationItem::text))
        assertEquals("First chunkSecond chunk", cachedProjected.last().text)
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

        assertEquals(hydrateCallsBeforeReconnect, syncService.hydrateCalls)
        assertEquals(resumeCallsBeforeReconnect + 1, syncService.resumeCalls)
    }

    @Test
    fun `encrypted reconnect resumes selected thread that is waiting on approval`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = ReconnectResumeSyncService(clearRunningOnRefresh = true)
        syncService.updateThreads(
            syncService.threads.value.map { snapshot ->
                if (snapshot.id == "thread-notifications") {
                    snapshot.copy(
                        isRunning = false,
                        isWaitingOnApproval = true,
                    )
                } else {
                    snapshot
                }
            },
        )
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-repository-waiting-approval",
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

        assertEquals(hydrateCallsBeforeReconnect, syncService.hydrateCalls)
        assertEquals(resumeCallsBeforeReconnect + 1, syncService.resumeCalls)
    }

    @Test
    fun `encrypted reconnect only hydrates selected idle thread without resuming live recovery`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = ReconnectResumeSyncService(clearRunningOnRefresh = true)
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-repository-idle-thread",
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
        val refreshCallsBeforeReconnect = syncService.refreshCalls

        coordinator.rememberRelayPairing(payload)
        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
        advanceUntilIdle()

        assertEquals(hydrateCallsBeforeReconnect + 1, syncService.hydrateCalls)
        assertEquals(resumeCallsBeforeReconnect, syncService.resumeCalls)
        assertEquals(refreshCallsBeforeReconnect, syncService.refreshCalls)
    }

    @Test
    fun `encrypted reconnect without a selected thread auto-selects a recent running thread and resumes it`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = ReconnectAutoSelectRunningThreadSyncService()
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-repository-auto-select-running-thread",
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

        assertEquals(null, repository.session.value.selectedThreadId)
        assertTrue(repository.session.value.threads.isEmpty())

        coordinator.rememberRelayPairing(payload)
        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
        advanceUntilIdle()

        assertEquals("thread-recovered-running", repository.session.value.selectedThread?.id)
        assertTrue(repository.session.value.selectedThread?.isRunning == true)
        assertTrue(syncService.refreshCalls >= 1)
        assertEquals(0, syncService.hydrateCalls)
        assertEquals(1, syncService.resumeCalls)
    }

    @Test
    fun `reconnect recovery candidates include locally known running threads even when sync snapshots are empty`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = FakeThreadSyncService(initialThreads = emptyList())
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
        invokePrivateMethod<Unit>(
            repository,
            "refreshThreadsLocally",
            listOf(
                RemodexThreadSummary(
                    id = "thread-cached-running",
                    title = "Cached running thread",
                    preview = "Recovered from local cache.",
                    projectPath = "/tmp/project-cached-running",
                    lastUpdatedLabel = "Updated just now",
                    isRunning = true,
                    queuedDrafts = 0,
                    runtimeLabel = "",
                    runtimeConfig = RemodexRuntimeConfig(),
                    messages = emptyList(),
                ),
            ),
            false,
            null,
        )
        advanceUntilIdle()

        assertEquals("thread-cached-running", repository.session.value.selectedThread?.id)
        assertTrue(repository.session.value.selectedThread?.isRunning == true)
        assertTrue(syncService.threads.value.isEmpty())
        val candidateThreadIds = invokePrivateMethod<List<String>>(
            repository,
            "collectReconnectRecoveryCandidateThreadIds",
            3,
        )
        assertTrue(candidateThreadIds.contains("thread-cached-running"))
    }

    @Test
    fun `selecting a connected thread waiting on approval resumes it to recover the approval prompt`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = false,
                            isWaitingOnApproval = true,
                        )
                    } else {
                        snapshot
                    }
                },
            )
        }
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(0, syncService.hydrateCalls)
        assertEquals(2, syncService.resumeCalls)
    }

    @Test
    fun `hydrating a connected thread waiting on approval resumes it to recover the approval prompt`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = false,
                            isWaitingOnApproval = true,
                        )
                    } else {
                        snapshot
                    }
                },
            )
        }
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.hydrateThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(1, syncService.hydrateCalls)
        assertEquals(1, syncService.resumeCalls)
    }

    @Test
    fun `selecting a connected running thread resumes it to recover live output`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = true,
                            isWaitingOnApproval = false,
                        )
                    } else {
                        snapshot
                    }
                },
            )
        }
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(0, syncService.hydrateCalls)
        assertEquals(2, syncService.resumeCalls)
    }

    @Test
    fun `hydrating a connected running thread resumes it to recover live output`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = true,
                            isWaitingOnApproval = false,
                        )
                    } else {
                        snapshot
                    }
                },
            )
        }
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.hydrateThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(1, syncService.hydrateCalls)
        assertEquals(2, syncService.resumeCalls)
    }

    @Test
    fun `syncing an active running thread force resumes it to recover missed live output`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = true,
                            isWaitingOnApproval = false,
                        )
                    } else {
                        snapshot
                    }
                },
            )
        }
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        repository.syncActiveThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(0, syncService.hydrateCalls)
        assertEquals(1, syncService.resumeCalls)
    }

    @Test
    fun `sync active thread falls back to hydrate when force resume does not clear a stale running final answer`() = runTest {
        val syncService = ResumeMissesTerminalHydrationSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        awaitSelectedThread(
            repository = repository,
            description = "selected stuck running thread",
        ) { selectedThread ->
            selectedThread?.id == "thread-notifications" &&
                selectedThread.isRunning &&
                selectedThread.messages.any { item ->
                    item.speaker == ConversationSpeaker.ASSISTANT &&
                        item.text == "Finished response" &&
                        !item.isStreaming
                }
        }

        syncService.clearRecordedCalls()

        repository.syncActiveThread("thread-notifications")
        advanceUntilIdle()

        assertTrue(syncService.resumeCalls >= 1)
        assertTrue(
            "syncActiveThread should self-heal a stale running final answer by hydrating authoritative thread state",
            syncService.hydrateCalls >= 1,
        )
        assertFalse(repository.session.value.selectedThread?.isRunning ?: true)
        assertEquals(
            null,
            repository.session.value.selectedThread?.activeTurnId,
        )
    }

    @Test
    fun `foreground connected selected running thread keeps syncing active thread at repository level`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                listOf(
                    activeSyncThreadSnapshot(
                        id = "thread-active-sync",
                        title = "Repository active sync",
                        isRunning = true,
                    ),
                ),
            )
        }
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        ).apply {
            activeThreadSyncRunningIntervalMillisOverride = 50L
            activeThreadSyncIdleIntervalMillisOverride = 250L
        }
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        repository.setAppForeground(true)
        runCurrent()
        val initialResumeCount = syncService.resumeThreadIds.size
        assertTrue(initialResumeCount >= 1)
        assertTrue(syncService.resumeThreadIds.all { threadId -> threadId == "thread-active-sync" })

        advanceTimeBy(50L)
        runCurrent()

        assertTrue(syncService.resumeThreadIds.size > initialResumeCount)
    }

    @Test
    fun `repository active thread sync retargets to the newly selected foreground thread`() = runTest {
        val firstThread = activeSyncThreadSnapshot(
            id = "thread-active-sync-1",
            title = "First foreground thread",
            isRunning = true,
            lastUpdatedEpochMs = 2L,
        )
        val secondThread = activeSyncThreadSnapshot(
            id = "thread-active-sync-2",
            title = "Second foreground thread",
            isRunning = true,
            lastUpdatedEpochMs = 1L,
        )
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(listOf(firstThread, secondThread))
        }
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        ).apply {
            activeThreadSyncRunningIntervalMillisOverride = 50L
            activeThreadSyncIdleIntervalMillisOverride = 250L
        }
        advanceUntilIdle()

        repository.setAppForeground(true)
        runCurrent()
        assertEquals("thread-active-sync-1", syncService.resumeThreadIds.lastOrNull())

        syncService.clearRecordedCalls()
        repository.selectThread("thread-active-sync-2")
        advanceUntilIdle()

        assertTrue(syncService.resumeThreadIds.contains("thread-active-sync-2"))
        assertEquals("thread-active-sync-2", syncService.resumeThreadIds.lastOrNull())
    }

    @Test
    fun `repository active thread sync stops when the app goes to background`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                listOf(
                    activeSyncThreadSnapshot(
                        id = "thread-active-sync",
                        title = "Foreground only sync",
                        isRunning = true,
                    ),
                ),
            )
        }
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        ).apply {
            activeThreadSyncRunningIntervalMillisOverride = 50L
            activeThreadSyncIdleIntervalMillisOverride = 250L
        }
        advanceUntilIdle()

        repository.setAppForeground(true)
        runCurrent()
        assertTrue(syncService.resumeThreadIds.isNotEmpty())
        assertTrue(syncService.resumeThreadIds.all { threadId -> threadId == "thread-active-sync" })

        repository.setAppForeground(false)
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        advanceTimeBy(500L)
        runCurrent()

        assertTrue(syncService.resumeThreadIds.isEmpty())
    }

    @Test
    fun `selecting a connected thread with streaming history resumes it to recover live output`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = false,
                            isWaitingOnApproval = false,
                            timelineMutations = listOf(
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "assistant-streaming-history",
                                        speaker = ConversationSpeaker.ASSISTANT,
                                        kind = ConversationItemKind.CHAT,
                                        text = "Streaming history that still needs live attach",
                                        turnId = "turn-streaming-history",
                                        itemId = "assistant-streaming-history",
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
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(0, syncService.hydrateCalls)
        assertEquals(2, syncService.resumeCalls)
    }

    @Test
    fun `reselecting an already hydrated idle thread reuses local timeline without extra recovery`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()
        assertEquals(0, syncService.hydrateCalls)
        assertEquals(1, syncService.resumeCalls)

        syncService.clearRecordedCalls()
        repository.selectThread("thread-reconnect")
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(0, syncService.hydrateCalls)
        assertEquals(0, syncService.resumeCalls)
    }

    @Test
    fun `hydrating a connected thread with streaming history resumes it to recover live output`() = runTest {
        val syncService = WaitingApprovalRecoverySyncService().apply {
            updateThreads(
                threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = false,
                            isWaitingOnApproval = false,
                            timelineMutations = listOf(
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "assistant-streaming-history",
                                        speaker = ConversationSpeaker.ASSISTANT,
                                        kind = ConversationItemKind.CHAT,
                                        text = "Streaming history that still needs live attach",
                                        turnId = "turn-streaming-history",
                                        itemId = "assistant-streaming-history",
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
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.hydrateThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(1, syncService.hydrateCalls)
        assertEquals(1, syncService.resumeCalls)
    }

    @Test
    fun `selecting a connected idle thread reuses local resumed state without extra recovery`() = runTest {
        val syncService = StaleLocalResumeRecoverySyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        syncService.clearRecordedCalls()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        assertEquals(0, syncService.hydrateCalls)
        assertEquals(0, syncService.resumeCalls)
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
        assertEquals(1, syncService.resumeCalls)
    }

    @Test
    fun `hydrate thread drops stale thinking placeholder when rollout recovery cannot resume`() = runTest {
        val syncService = RolloutMissingThinkingTailSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = syncService,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        awaitSelectedThread(
            repository = repository,
            description = "selected thread with stale reconnect timeline",
        ) { selectedThread ->
            selectedThread?.id == "thread-notifications"
        }

        repository.hydrateThread("thread-notifications")
        advanceUntilIdle()

        assertTrue(syncService.resumeCalls >= 1)
        assertTrue(syncService.hydrateCalls >= 1)
        assertTrue(
            "Stale reconnect placeholder should not remain in selected messages after failed rollout recovery",
            repository.session.value.selectedThread
                ?.messages
                ?.none { item ->
                    item.kind == ConversationItemKind.REASONING && item.text == "Thinking..."
                } == true,
        )
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
    fun `optimistic running fallback updates selected thread immediately without adding a user row`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val originalThread = requireNotNull(repository.session.value.selectedThread) {
            "Expected thread-notifications to be selected"
        }

        val optimisticState = invokePrivateMethod<Any?>(
            target = repository,
            methodName = "publishOptimisticRunningState",
            "thread-notifications",
            originalThread.runtimeConfig.copy(planningMode = RemodexPlanningMode.PLAN),
        )

        val selectedThreadWhileOptimistic = repository.session.value.selectedThread
        assertTrue(optimisticState != null)
        assertEquals("thread-notifications", selectedThreadWhileOptimistic?.id)
        assertTrue(selectedThreadWhileOptimistic?.isRunning == true)
        assertEquals(
            RemodexPlanningMode.PLAN,
            selectedThreadWhileOptimistic?.runtimeConfig?.planningMode,
        )
        assertEquals(
            originalThread.messages,
            selectedThreadWhileOptimistic?.messages,
        )

        invokePrivateMethod<Unit>(
            target = repository,
            methodName = "restoreOptimisticRunningState",
            optimisticState,
        )

        val restoredThread = repository.session.value.selectedThread
        assertEquals(originalThread.isRunning, restoredThread?.isRunning)
        assertEquals(
            originalThread.runtimeConfig.planningMode,
            restoredThread?.runtimeConfig?.planningMode,
        )
        assertEquals(originalThread.messages, restoredThread?.messages)
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
    fun `stale deferred thread list publish does not restore the previous selection after create thread`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        val staleBaseThreads = repository.session.value.threads
        val staleGeneration = invokePrivateMethod<Long>(
            repository,
            "nextThreadListPublishGeneration",
        )
        invokePrivateMethod<Unit>(
            repository,
            "scheduleThreadListPublish",
            staleBaseThreads,
            AppPreferences(selectedThreadId = "thread-notifications"),
            repository.session.value.availableModels,
            repository.session.value.secureConnection,
            repository.session.value.notificationRegistration,
            staleGeneration,
        )

        repository.createThread("/tmp/new-project")
        advanceUntilIdle()
        awaitSelectedThread(
            repository = repository,
            description = "the newly created chat thread after a stale deferred publish",
        ) { thread ->
            thread?.projectPath == "/tmp/new-project"
        }

        val session = repository.session.value
        val selectedThread = session.selectedThread
        assertEquals("/tmp/new-project", selectedThread?.projectPath)
        assertEquals(selectedThread?.id, session.selectedThreadId)
        assertTrue(session.threads.any { thread -> thread.projectPath == "/tmp/new-project" })
    }

    @Test
    fun `switching bridge clears the selected thread from the previous bridge`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val secureStore = InMemorySecureStore()
        val firstMacIdentity = createTestMacIdentity()
        val secondMacIdentity = createTestMacIdentity()
        val syncService = FakeThreadSyncService()
        val coordinator = SecureConnectionCoordinator(
            store = secureStore,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = backgroundScope,
        )
        coordinator.rememberRelayPairing(
            createTestPairingPayload(
                macDeviceId = "mac-one",
                macIdentityPublicKey = firstMacIdentity.publicKeyBase64,
                sessionId = "session-one",
            ),
        )
        val firstProfileId = requireNotNull(coordinator.bridgeProfiles.value.activeProfileId)
        coordinator.rememberRelayPairing(
            createTestPairingPayload(
                macDeviceId = "mac-two",
                macIdentityPublicKey = secondMacIdentity.publicKeyBase64,
                sessionId = "session-two",
            ),
        )

        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = coordinator,
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        repository.selectThread("thread-notifications")
        advanceUntilIdle()
        assertEquals("thread-notifications", repository.session.value.selectedThreadId)

        repository.activateBridgeProfile(firstProfileId)
        advanceUntilIdle()

        assertEquals(null, repository.session.value.selectedThreadId)
        assertEquals(null, repository.session.value.selectedThreadSnapshot)
        assertEquals(null, repository.session.value.selectedThread)
        assertTrue(repository.session.value.threads.isEmpty())
        assertEquals(null, preferencesRepository.preferencesState.value.selectedThreadId)
    }

    @Test
    fun `forgetting the active trusted mac clears old bridge threads and switches to the fallback profile`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val secureStore = InMemorySecureStore()
        val firstMacIdentity = createTestMacIdentity()
        val secondMacIdentity = createTestMacIdentity()
        val syncService = FakeThreadSyncService()
        val coordinator = SecureConnectionCoordinator(
            store = secureStore,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = backgroundScope,
        )
        coordinator.rememberRelayPairing(
            createTestPairingPayload(
                macDeviceId = "mac-one",
                macIdentityPublicKey = firstMacIdentity.publicKeyBase64,
                sessionId = "session-one",
            ),
        )
        val fallbackProfileId = requireNotNull(coordinator.bridgeProfiles.value.activeProfileId)
        coordinator.rememberRelayPairing(
            createTestPairingPayload(
                macDeviceId = "mac-two",
                macIdentityPublicKey = secondMacIdentity.publicKeyBase64,
                sessionId = "session-two",
            ),
        )

        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = coordinator,
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        advanceUntilIdle()
        repository.selectThread("thread-notifications")
        advanceUntilIdle()

        repository.forgetTrustedMac("mac-two")
        advanceUntilIdle()

        assertEquals(fallbackProfileId, coordinator.bridgeProfiles.value.activeProfileId)
        assertTrue(repository.session.value.threads.isEmpty())
        assertEquals(null, repository.session.value.selectedThreadId)
        assertEquals(null, repository.session.value.selectedThread)
        assertEquals(null, preferencesRepository.preferencesState.value.selectedThreadId)
        assertEquals(fallbackProfileId, coordinator.state.value.activeProfileId)
    }

    @Test
    fun `stale thread list rebuild keeps sticky selected thread when the new thread is temporarily missing`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        advanceUntilIdle()
        val previousThreads = repository.session.value.threads
        val previousPreferences = AppPreferences(selectedThreadId = "thread-notifications")

        repository.createThread("/tmp/new-project")
        advanceUntilIdle()
        val selectedAfterCreate = requireNotNull(repository.session.value.selectedThread) {
            "Expected the new thread to be selected after create"
        }

        invokePrivateMethod<Unit>(
            repository,
            "publishMaterializedThreads",
            previousThreads,
            previousPreferences,
            repository.session.value.availableModels,
            repository.session.value.secureConnection,
            repository.session.value.notificationRegistration,
        )
        advanceUntilIdle()

        assertEquals(selectedAfterCreate.id, repository.session.value.selectedThread?.id)
        assertEquals("/tmp/new-project", repository.session.value.selectedThread?.projectPath)
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
    fun `sending while a turn is running preserves raw queued composer state`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        repository.selectThread("thread-android-client")
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-android-client",
            prompt = "Inspect @app/src/main/java/com/emanueledipietro/remodex/feature/turn/ConversationScreen.kt",
            attachments = emptyList(),
            planningModeOverride = null,
            queuedDraftContext = RemodexQueuedDraftContext(
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
            ),
        )
        advanceUntilIdle()

        val queuedDraft = repository.session.value.selectedThread?.queuedDraftItems?.firstOrNull()
        assertEquals("Inspect @ConversationScreen.kt", queuedDraft?.rawInput)
        assertEquals(
            listOf("app/src/main/java/com/emanueledipietro/remodex/feature/turn/ConversationScreen.kt"),
            queuedDraft?.rawMentionedFiles?.map(RemodexComposerMentionedFile::path),
        )
        assertEquals(listOf("gh-address-comments"), queuedDraft?.rawMentionedSkills?.map(RemodexComposerMentionedSkill::name))
        assertTrue(queuedDraft?.rawSubagentsSelectionArmed == true)
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
    fun `pop latest queued draft removes only the newest queued follow up`() = runTest {
        val repository = createRepository(
            scope = backgroundScope,
        )
        repository.selectThread("thread-android-client")
        advanceUntilIdle()

        repository.sendPrompt(
            threadId = "thread-android-client",
            prompt = "First queued",
            attachments = emptyList(),
        )
        advanceUntilIdle()
        repository.sendPrompt(
            threadId = "thread-android-client",
            prompt = "Latest queued",
            attachments = emptyList(),
            planningModeOverride = RemodexPlanningMode.PLAN,
        )
        advanceUntilIdle()

        val restoredDraft = repository.popLatestQueuedDraft("thread-android-client")
        advanceUntilIdle()

        assertEquals("Latest queued", restoredDraft?.text)
        assertEquals(RemodexPlanningMode.PLAN, restoredDraft?.planningMode)
        assertEquals(
            listOf("First queued"),
            repository.session.value.selectedThread?.queuedDraftItems?.map(RemodexQueuedDraft::text),
        )
        assertEquals(1, repository.session.value.selectedThread?.queuedDrafts)
    }

    @Test
    fun `steering queued draft removes only selected row and preserves order`() = runTest {
        val preferencesRepository = TestAppPreferencesRepository()
        val syncService = SteeringCaptureSyncService()
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = preferencesRepository,
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = syncService,
            threadHydrationService = null,
            scope = backgroundScope,
        )
        val firstDraft = RemodexQueuedDraft(id = "draft-1", text = "first", createdAtEpochMs = 1L)
        val secondDraft = RemodexQueuedDraft(id = "draft-2", text = "second", createdAtEpochMs = 2L)
        val thirdDraft = RemodexQueuedDraft(id = "draft-3", text = "third", createdAtEpochMs = 3L)

        syncService.updateThreads(
            syncService.threads.value.map { snapshot ->
                if (snapshot.id == "thread-android-client") {
                    snapshot.copy(
                        isRunning = true,
                        activeTurnId = "turn-live",
                    )
                } else {
                    snapshot
                }
            },
        )
        preferencesRepository.setQueuedDrafts(
            threadId = "thread-android-client",
            drafts = listOf(firstDraft, secondDraft, thirdDraft),
        )
        advanceUntilIdle()
        repository.selectThread("thread-android-client")
        advanceUntilIdle()
        awaitSelectedThread(
            repository = repository,
            description = "thread-android-client to be running",
        ) { selectedThread ->
            selectedThread?.id == "thread-android-client" && selectedThread.isRunning
        }

        repository.steerQueuedDraft("thread-android-client", secondDraft.id)
        advanceUntilIdle()

        assertEquals(listOf("second"), syncService.steeredPrompts)
        assertEquals(
            listOf("draft-1", "draft-3"),
            repository.session.value.selectedThread?.queuedDraftItems?.map(RemodexQueuedDraft::id),
        )
    }

    @Test
    fun `continue on mac delegates to thread command service`() = runTest {
        val syncService = FakeThreadSyncService()
        val commandService = RecordingContinueOnMacCommandService(syncService)
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = createConnectedSecureCoordinator(),
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = syncService,
            threadCommandService = commandService,
            threadHydrationService = null,
            scope = backgroundScope,
        )

        repository.continueOnMac("thread-notifications")

        assertEquals(listOf("thread-notifications"), commandService.threadIds)
    }

    @Test
    fun `successful voice transcription refreshes GPT account status`() = runTest {
        var accountStatusReadCount = 0
        val coordinator = createConnectedSecureCoordinator(
            requestHandlers = mapOf(
                "account/status/read" to {
                    accountStatusReadCount += 1
                    buildJsonObject {
                        put("status", JsonPrimitive("authenticated"))
                        put("authMethod", JsonPrimitive("chatgpt"))
                        put("email", JsonPrimitive("voice@example.com"))
                        put("tokenReady", JsonPrimitive(accountStatusReadCount >= 2))
                    }
                },
            ),
        )
        val repository = DefaultRemodexAppRepository(
            appPreferencesRepository = TestAppPreferencesRepository(),
            secureConnectionCoordinator = coordinator,
            threadCacheStore = InMemoryThreadCacheStore(),
            threadSyncService = FakeThreadSyncService(),
            threadCommandService = FakeThreadSyncService(),
            threadHydrationService = null,
            voiceTranscriptionService = object : RemodexVoiceTranscriptionService {
                override suspend fun transcribeVoiceAudioFile(
                    file: File,
                    durationSeconds: Double,
                ): String = "ready transcript"
            },
            scope = backgroundScope,
        )
        val audioFile = File.createTempFile("remodex-repository-voice-", ".wav").apply {
            writeBytes(ByteArray(16))
            deleteOnExit()
        }

        try {
            repository.refreshGptAccountState()
            assertFalse(repository.gptAccountSnapshot.value.isVoiceTokenReady)

            val transcript = repository.transcribeVoiceAudioFile(
                file = audioFile,
                durationSeconds = 1.0,
            )

            assertEquals("ready transcript", transcript)
            assertTrue(repository.gptAccountSnapshot.value.isVoiceTokenReady)
            assertEquals(2, accountStatusReadCount)
        } finally {
            audioFile.delete()
            coordinator.disconnect()
            advanceUntilIdle()
        }
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
        assertEquals(2, syncService.resumeCalls)
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
        assertEquals(2, syncService.resumeCalls)
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
    fun `attachment only sends keep an empty preview and attach metadata without Android fallback text`() = runTest {
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
        assertEquals("", selectedThread?.preview)
        assertTrue(
            selectedThread?.messages.orEmpty().any { item ->
                item.speaker == com.emanueledipietro.remodex.model.ConversationSpeaker.USER &&
                    item.deliveryState == RemodexMessageDeliveryState.PENDING &&
                    item.text.isEmpty()
            },
        )
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

        override fun setActiveBridgeProfileId(profileId: String?) = Unit

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            backingState.value = backingState.value.copy(onboardingCompleted = completed)
        }

        override suspend fun setSelectedThreadId(threadId: String?) {
            backingState.value = backingState.value.copy(selectedThreadId = threadId)
        }

        override suspend fun setProjectGroupCollapsed(
            groupId: String,
            collapsed: Boolean,
        ) {
            val normalizedGroupId = groupId.trim()
            val updatedCollapsedGroupIds = backingState.value.collapsedProjectGroupIds.toMutableSet().apply {
                if (collapsed) {
                    add(normalizedGroupId)
                } else {
                    remove(normalizedGroupId)
                }
            }
            backingState.value = backingState.value.copy(collapsedProjectGroupIds = updatedCollapsedGroupIds)
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

    private fun activeSyncThreadSnapshot(
        id: String,
        title: String,
        isRunning: Boolean,
        lastUpdatedEpochMs: Long = 1L,
    ): ThreadSyncSnapshot {
        return ThreadSyncSnapshot(
            id = id,
            title = title,
            preview = "$title preview",
            projectPath = "/tmp/$id",
            lastUpdatedLabel = "Updated just now",
            lastUpdatedEpochMs = lastUpdatedEpochMs,
            isRunning = isRunning,
            syncState = RemodexThreadSyncState.LIVE,
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

    private suspend fun TestScope.createConnectedSecureCoordinator(
        requestHandlers: Map<String, (com.emanueledipietro.remodex.data.connection.RpcMessage) -> JsonElement> = emptyMap(),
    ): SecureConnectionCoordinator {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-repository-test",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = if (requestHandlers.isEmpty()) {
                SuccessfulQrBootstrapRelayWebSocketFactory(
                    macDeviceId = payload.macDeviceId,
                    macIdentity = macIdentity,
                )
            } else {
                ScriptedRpcRelayWebSocketFactory(
                    macDeviceId = payload.macDeviceId,
                    macIdentity = macIdentity,
                    requestHandlers = requestHandlers,
                )
            },
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
    private fun setLiveContextWindowUsage(
        repository: DefaultRemodexAppRepository,
        usageByThread: Map<String, RemodexContextWindowUsage>,
    ) {
        val field = repository.javaClass.getDeclaredField("liveContextWindowUsageByThreadState")
        field.isAccessible = true
        val state = field.get(repository) as MutableStateFlow<Map<String, RemodexContextWindowUsage>>
        state.value = usageByThread
    }

    private fun setSelectedThreadDetail(
        repository: DefaultRemodexAppRepository,
        threadId: String?,
        thread: RemodexThreadSummary?,
    ) {
        invokePrivateMethod<Unit>(
            target = repository,
            methodName = "setSelectedThreadDetail",
            threadId,
            thread,
        )
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
                delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
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

    private class WaitingApprovalRecoverySyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadHydrationService, ThreadResumeService by delegate {
        var hydrateCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set
        val hydrateThreadIds = mutableListOf<String>()
        val resumeThreadIds = mutableListOf<String>()

        fun updateThreads(threads: List<ThreadSyncSnapshot>) {
            delegate.updateThreads(threads)
        }

        fun clearRecordedCalls() {
            hydrateCalls = 0
            resumeCalls = 0
            hydrateThreadIds.clear()
            resumeThreadIds.clear()
        }

        override val threads = delegate.threads

        override suspend fun refreshThreads() = Unit

        override suspend fun hydrateThread(threadId: String) {
            hydrateCalls += 1
            hydrateThreadIds += threadId
        }

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            resumeCalls += 1
            resumeThreadIds += threadId
            return delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
        }
    }

    private class StaleLocalResumeRecoverySyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadHydrationService, ThreadResumeService {
        var hydrateCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set

        fun clearRecordedCalls() {
            hydrateCalls = 0
            resumeCalls = 0
        }

        override suspend fun refreshThreads() = Unit

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
            return threadId == "thread-notifications" || delegate.isThreadResumedLocally(threadId)
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

    private class SteeringCaptureSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate {
        val steeredPrompts = mutableListOf<String>()

        fun updateThreads(threads: List<ThreadSyncSnapshot>) {
            delegate.updateThreads(threads)
        }

        override suspend fun steerPrompt(
            threadId: String,
            prompt: String,
            runtimeConfig: RemodexRuntimeConfig,
            attachments: List<RemodexComposerAttachment>,
        ) {
            steeredPrompts += prompt
            delegate.steerPrompt(threadId, prompt, runtimeConfig, attachments)
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

    private class ResumeMissesTerminalHydrationSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadHydrationService, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var hydrateCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set

        init {
            delegate.updateThreads(
                delegate.threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = true,
                            activeTurnId = "turn-stale-final-answer",
                            latestTurnTerminalState = null,
                            timelineMutations = listOf(
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "assistant-stale-final-answer",
                                        speaker = ConversationSpeaker.ASSISTANT,
                                        kind = ConversationItemKind.CHAT,
                                        text = "Finished response",
                                        turnId = "turn-stale-final-answer",
                                        itemId = "assistant-stale-final-answer",
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

        fun clearRecordedCalls() {
            hydrateCalls = 0
            resumeCalls = 0
        }

        override suspend fun refreshThreads() = Unit

        override suspend fun hydrateThread(threadId: String) {
            hydrateCalls += 1
            if (threadId != "thread-notifications") {
                return
            }
            delegate.updateThreads(
                delegate.threads.value.map { snapshot ->
                    if (snapshot.id == threadId) {
                        snapshot.copy(
                            isRunning = false,
                            activeTurnId = null,
                            latestTurnTerminalState = com.emanueledipietro.remodex.model.RemodexTurnTerminalState.COMPLETED,
                            timelineMutations = listOf(
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "assistant-stale-final-answer",
                                        speaker = ConversationSpeaker.ASSISTANT,
                                        kind = ConversationItemKind.CHAT,
                                        text = "Finished response",
                                        turnId = "turn-stale-final-answer",
                                        itemId = "assistant-stale-final-answer",
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

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            resumeCalls += 1
            return null
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return delegate.isThreadResumedLocally(threadId)
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

    private class RolloutMissingThinkingTailSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadHydrationService, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var hydrateCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set

        init {
            delegate.updateThreads(
                delegate.threads.value.map { snapshot ->
                    if (snapshot.id == "thread-notifications") {
                        snapshot.copy(
                            isRunning = true,
                            timelineMutations = listOf(
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "user-reconnect-tail",
                                        speaker = ConversationSpeaker.USER,
                                        text = "安卓客户端时间线里面为什么中间会有thinking",
                                        turnId = "turn-reconnect-tail",
                                        orderIndex = 0L,
                                    ),
                                ),
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "command-reconnect-tail-1",
                                        speaker = ConversationSpeaker.SYSTEM,
                                        kind = ConversationItemKind.COMMAND_EXECUTION,
                                        text = "Read app/DefaultRemodexAppRepository.kt",
                                        turnId = "turn-reconnect-tail",
                                        itemId = "command-reconnect-tail-1",
                                        orderIndex = 1L,
                                    ),
                                ),
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "reasoning-reconnect-tail",
                                        speaker = ConversationSpeaker.SYSTEM,
                                        kind = ConversationItemKind.REASONING,
                                        text = "Thinking...",
                                        turnId = "turn-reconnect-tail",
                                        itemId = "rollout-thinking:thread-notifications:turn-reconnect-tail",
                                        orderIndex = 2L,
                                    ),
                                ),
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "command-reconnect-tail-2",
                                        speaker = ConversationSpeaker.SYSTEM,
                                        kind = ConversationItemKind.COMMAND_EXECUTION,
                                        text = "Search coordinator.disconnect",
                                        turnId = "turn-reconnect-tail",
                                        itemId = "command-reconnect-tail-2",
                                        orderIndex = 3L,
                                    ),
                                ),
                                TimelineMutation.Upsert(
                                    RemodexConversationItem(
                                        id = "assistant-reconnect-tail",
                                        speaker = ConversationSpeaker.ASSISTANT,
                                        kind = ConversationItemKind.CHAT,
                                        text = "我先用单测把这条具体分支钉住。",
                                        turnId = "turn-reconnect-tail",
                                        itemId = "assistant-reconnect-tail",
                                        orderIndex = 4L,
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

        override suspend fun refreshThreads() = Unit

        override suspend fun hydrateThread(threadId: String) {
            hydrateCalls += 1
            if (threadId == "thread-notifications") {
                throw RpcError(
                    code = -32000,
                    message = "No rollout found for thread id $threadId",
                )
            }
        }

        override suspend fun resumeThread(
            threadId: String,
            preferredProjectPath: String?,
            modelIdentifier: String?,
        ): ThreadSyncSnapshot? {
            resumeCalls += 1
            if (threadId == "thread-notifications") {
                throw RpcError(
                    code = -32000,
                    message = "No rollout found for thread id $threadId",
                )
            }
            return delegate.resumeThread(threadId, preferredProjectPath, modelIdentifier)
        }

        override fun isThreadResumedLocally(threadId: String): Boolean {
            return delegate.isThreadResumedLocally(threadId)
        }
    }

    private class ReconnectAutoSelectRunningThreadSyncService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(initialThreads = emptyList()),
    ) : ThreadSyncService by delegate, ThreadCommandService by delegate, ThreadHydrationService, ThreadResumeService, ThreadLocalTimelineService by delegate {
        var hydrateCalls: Int = 0
            private set
        var refreshCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set

        override suspend fun refreshThreads() {
            refreshCalls += 1
            if (delegate.threads.value.isNotEmpty()) {
                return
            }
            delegate.updateThreads(
                listOf(
                    ThreadSyncSnapshot(
                        id = "thread-recovered-running",
                        title = "Recovered running thread",
                        preview = "Live output recovered after reconnect.",
                        projectPath = "/tmp/project-recovered-running",
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = 10L,
                        isRunning = true,
                        runtimeConfig = RemodexRuntimeConfig(),
                        timelineMutations = emptyList(),
                    ),
                ),
            )
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

    private class RecordingContinueOnMacCommandService(
        private val delegate: FakeThreadSyncService = FakeThreadSyncService(),
    ) : ThreadCommandService by delegate {
        val threadIds = mutableListOf<String>()

        override suspend fun continueOnMac(threadId: String) {
            threadIds += threadId
        }
    }
}
