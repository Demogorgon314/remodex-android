package com.emanueledipietro.remodex.data.app

import com.emanueledipietro.remodex.data.connection.InMemorySecureStore
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
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
import com.emanueledipietro.remodex.data.threads.ThreadCommandService
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitRepoDiff
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexRevertApplyResult
import com.emanueledipietro.remodex.model.RemodexRevertPreviewResult
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `create thread selects the newly created chat`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        advanceUntilIdle()

        repository.createThread("/tmp/new-project")
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertTrue(selectedThread != null)
        assertEquals(selectedThread?.id, repository.session.value.selectedThreadId)
        assertEquals("/tmp/new-project", selectedThread?.projectPath)
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

        runCurrent()

        assertEquals(
            "Streaming title from sync",
            repository.session.value.selectedThread?.title,
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
            1,
            preferencesRepository.preferencesState.value.queuedDraftsByThread["thread-android-client"]?.size,
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

        val selectedThread = repository.session.value.selectedThread
        assertTrue(selectedThread != null)
        assertTrue(selectedThread?.id != "thread-notifications")
        assertEquals(selectedThread?.id, repository.session.value.selectedThreadId)
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
            repository.session.value.threads.first { thread -> thread.id == "thread-notifications" }.syncState,
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

        val selectedThread = repository.session.value.selectedThread
        assertTrue(selectedThread != null)
        assertTrue(selectedThread?.id != "thread-notifications")
        assertEquals(selectedThread?.id, repository.session.value.selectedThreadId)
        assertEquals(selectedThread?.id, syncService.lastSuccessfulSendThreadId)
        assertEquals(1, syncService.resumeCalls)
        assertTrue(
            selectedThread?.messages.orEmpty().any { item ->
                item.text.contains("Continued from archived thread `thread-notifications`")
            },
        )
        assertEquals(
            RemodexThreadSyncState.ARCHIVED_LOCAL,
            repository.session.value.threads.first { thread -> thread.id == "thread-notifications" }.syncState,
        )
    }

    @Test
    fun `stop turn clears running state and queued drafts can send afterwards`() = runTest {
        val repository = createRepository(scope = backgroundScope)
        repository.selectThread("thread-android-client")
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
                ),
            ),
        )
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertEquals("Shared 1 image from Android.", selectedThread?.preview)
        assertTrue(
            selectedThread?.messages.orEmpty().any { item ->
                item.attachments.any { attachment -> attachment.displayName == "screenshot.png" }
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
}
