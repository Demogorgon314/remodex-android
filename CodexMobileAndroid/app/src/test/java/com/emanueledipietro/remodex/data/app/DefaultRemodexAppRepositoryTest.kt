package com.emanueledipietro.remodex.data.app

import com.emanueledipietro.remodex.data.connection.InMemorySecureStore
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.UnexpectedRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.UnusedTrustedSessionResolver
import com.emanueledipietro.remodex.data.preferences.AppPreferences
import com.emanueledipietro.remodex.data.preferences.AppPreferencesRepository
import com.emanueledipietro.remodex.data.threads.FakeThreadSyncService
import com.emanueledipietro.remodex.data.threads.InMemoryThreadCacheStore
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexReasoningEffort
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        repository.setModelId("thread-notifications", "gpt-5.4")
        repository.setReasoningEffort("thread-notifications", RemodexReasoningEffort.LOW)
        repository.setServiceTier("thread-notifications", null)
        repository.setAccessMode("thread-notifications", RemodexAccessMode.ON_REQUEST)
        advanceUntilIdle()

        val selectedThread = repository.session.value.selectedThread
        assertEquals("gpt-5.4", selectedThread?.runtimeConfig?.selectedModelId)
        assertEquals(RemodexPlanningMode.PLAN, selectedThread?.runtimeConfig?.planningMode)
        assertEquals(RemodexReasoningEffort.LOW, selectedThread?.runtimeConfig?.reasoningEffort)
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

        repository.setDefaultReasoningEffort(RemodexReasoningEffort.HIGH)
        repository.setDefaultAccessMode(RemodexAccessMode.FULL_ACCESS)
        repository.setAppearanceMode(RemodexAppearanceMode.DARK)
        advanceUntilIdle()

        val session = repository.session.value
        assertEquals(RemodexReasoningEffort.HIGH, session.selectedThread?.runtimeConfig?.reasoningEffort)
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

    private fun createSecureCoordinator(scope: CoroutineScope): SecureConnectionCoordinator {
        return SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = scope,
        )
    }
}
