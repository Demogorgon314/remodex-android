package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.data.connection.InMemorySecureStore
import com.emanueledipietro.remodex.data.connection.RpcError
import com.emanueledipietro.remodex.data.connection.ScriptedRpcRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.UnusedTrustedSessionResolver
import com.emanueledipietro.remodex.data.connection.createTestMacIdentity
import com.emanueledipietro.remodex.data.connection.createTestPairingPayload
import com.emanueledipietro.remodex.data.connection.firstArray
import com.emanueledipietro.remodex.data.connection.firstString
import com.emanueledipietro.remodex.data.connection.jsonObjectOrNull
import com.emanueledipietro.remodex.feature.turn.FileChangeAction
import com.emanueledipietro.remodex.feature.turn.FileChangeRenderParser
import com.emanueledipietro.remodex.feature.turn.TurnTimelineReducer
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeThreadSyncServiceTest {
    @Test
    fun `approval policy fallback only retries compatibility-shaped rpc errors`() {
        assertTrue(
            shouldRetryWithApprovalPolicyFallbackValue(
                RpcError(
                    code = -32600,
                    message = "Invalid params: unknown field `approvalPolicy`",
                ),
            ),
        )
        assertFalse(
            shouldRetryWithApprovalPolicyFallbackValue(
                RpcError(
                    code = -32600,
                    message = "Invalid request: unknown variant `onRequest`, expected one of `untrusted`, `on-failure`, `on-request`, `granular`, `never`",
                ),
            ),
        )
    }

    @Test
    fun `approval policy fallback skips thread lookup errors`() {
        assertFalse(
            shouldRetryWithApprovalPolicyFallbackValue(
                RpcError(
                    code = -32602,
                    message = "Unknown thread: thread-missing",
                ),
            ),
        )
    }

    @Test
    fun `preview recompute only tracks chat upserts`() {
        assertTrue(
            mutationAffectsThreadPreviewValue(
                TimelineMutation.Upsert(
                    timelineItem(
                        id = "user-1",
                        speaker = com.emanueledipietro.remodex.model.ConversationSpeaker.USER,
                        text = "hello",
                        orderIndex = 0L,
                    ),
                ),
            ),
        )
        assertTrue(
            mutationAffectsThreadPreviewValue(
                TimelineMutation.Upsert(
                    timelineItem(
                        id = "assistant-1",
                        speaker = com.emanueledipietro.remodex.model.ConversationSpeaker.ASSISTANT,
                        text = "hi",
                        orderIndex = 1L,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `preview recompute ignores streaming system deltas`() {
        assertFalse(
            mutationAffectsThreadPreviewValue(
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    delta = "partial",
                    orderIndex = 1L,
                ),
            ),
        )
        assertFalse(
            mutationAffectsThreadPreviewValue(
                TimelineMutation.ReasoningTextDelta(
                    messageId = "thinking-1",
                    turnId = "turn-1",
                    delta = "thinking",
                    orderIndex = 2L,
                ),
            ),
        )
        assertFalse(
            mutationAffectsThreadPreviewValue(
                TimelineMutation.ActivityLine(
                    messageId = "tool-1",
                    turnId = "turn-1",
                    line = "running rg",
                    orderIndex = 3L,
                ),
            ),
        )
    }

    @Test
    fun `bridge thread sync service loads real threads and hydrates history`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-threads",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to { message ->
                    val archived = message.params?.jsonObjectOrNull?.firstString("archived") == "true"
                    if (archived) {
                        buildJsonObject {
                            put(
                                "data",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive("thread-archived"))
                                            put("title", JsonPrimitive("Archived thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-b"))
                                        },
                                    )
                                },
                            )
                        }
                    } else {
                        buildJsonObject {
                            put(
                                "data",
                                buildJsonArray {
                                    add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-live"))
                                        put("title", JsonPrimitive("Live thread"))
                                        put("name", JsonPrimitive("Live project"))
                                        put("preview", JsonPrimitive("Bridge-backed preview"))
                                        put("cwd", JsonPrimitive("/tmp/project-a"))
                                        put("updatedAt", JsonPrimitive(1_713_111_111))
                                        },
                                    )
                                },
                            )
                        }
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-live"))
                                put("title", JsonPrimitive("Live thread"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-1"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("user-1"))
                                                                put("type", JsonPrimitive("user_message"))
                                                                put("text", JsonPrimitive("Pair the Android client with the local bridge."))
                                                            },
                                                        )
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-1"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive("Thread hydration now uses real bridge data."))
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            service.refreshThreads()
            advanceUntilIdle()
            awaitThreads(service, expectedCount = 2)
            service.hydrateThread("thread-live")
            advanceUntilIdle()

            val liveThread = service.threads.value.first { it.id == "thread-live" }
            val archivedThread = service.threads.value.first { it.id == "thread-archived" }
            assertEquals("Live thread", liveThread.title)
            assertEquals("Live project", liveThread.name)
            assertEquals("/tmp/project-a", liveThread.projectPath)
            assertFalse(liveThread.timelineMutations.isEmpty())
            assertEquals(RemodexThreadSyncState.ARCHIVED_LOCAL, archivedThread.syncState)
            assertTrue(
                relayFactory.receivedRequests.any { request ->
                    request.method == "thread/read"
                },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread marks thread running when interruptible turn has no id`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-running-fallback",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        if (it.params?.jsonObjectOrNull?.firstString("archived") == "true") {
                            put("data", buildJsonArray { })
                            return@buildJsonObject
                        }
                        put(
                            "data",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-running-fallback"))
                                        put("title", JsonPrimitive("Long running thread"))
                                        put("cwd", JsonPrimitive("/tmp/project-running"))
                                        put("updatedAt", JsonPrimitive(1_713_222_333))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-running-fallback"))
                                put("title", JsonPrimitive("Long running thread"))
                                put("cwd", JsonPrimitive("/tmp/project-running"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("status", JsonPrimitive("running"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-live"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive("Still streaming without a stable turn id yet."))
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            service.refreshThreads()
            advanceUntilIdle()
            awaitThreads(service, expectedCount = 1)

            service.hydrateThread("thread-running-fallback")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-running-fallback" }
            assertTrue(thread.isRunning)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread clears stale running state when latest thread read is idle`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-running-clear",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var threadReadCount = 0
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        if (it.params?.jsonObjectOrNull?.firstString("archived") == "true") {
                            put("data", buildJsonArray { })
                            return@buildJsonObject
                        }
                        put(
                            "data",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-running-clear"))
                                        put("title", JsonPrimitive("Thread that just finished"))
                                        put("cwd", JsonPrimitive("/tmp/project-running-clear"))
                                        put("updatedAt", JsonPrimitive(1_713_222_334))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/read" to {
                    threadReadCount += 1
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-running-clear"))
                                put("title", JsonPrimitive("Thread that just finished"))
                                put("cwd", JsonPrimitive("/tmp/project-running-clear"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                if (threadReadCount == 1) {
                                                    put("status", JsonPrimitive("running"))
                                                } else {
                                                    put("status", JsonPrimitive("completed"))
                                                }
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-live"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put(
                                                                    "text",
                                                                    JsonPrimitive(
                                                                        if (threadReadCount == 1) {
                                                                            "Still streaming without a stable turn id yet."
                                                                        } else {
                                                                            "Run has completed."
                                                                        },
                                                                    ),
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            service.refreshThreads()
            advanceUntilIdle()
            awaitThreads(service, expectedCount = 1)

            service.hydrateThread("thread-running-clear")
            advanceUntilIdle()
            assertTrue(service.threads.value.first { it.id == "thread-running-clear" }.isRunning)

            service.hydrateThread("thread-running-clear")
            advanceUntilIdle()
            assertFalse(service.threads.value.first { it.id == "thread-running-clear" }.isRunning)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `rename thread keeps optimistic title when rename rpc fails`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-rename-thread",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        if (it.params?.jsonObjectOrNull?.firstString("archived") == "true") {
                            put("data", buildJsonArray { })
                            return@buildJsonObject
                        }
                        put(
                            "data",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-rename"))
                                        put("title", JsonPrimitive("Old title"))
                                        put("cwd", JsonPrimitive("/tmp/project-rename"))
                                        put("updatedAt", JsonPrimitive(1_713_222_440))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/name/set" to {
                    throw RpcError(code = -32603, message = "rename failed")
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            service.refreshThreads()
            advanceUntilIdle()
            awaitThreads(service, expectedCount = 1)

            service.renameThread("thread-rename", "Renamed locally")
            advanceUntilIdle()

            val renamedThread = service.threads.value.first { it.id == "thread-rename" }
            assertEquals("Renamed locally", renamedThread.title)
            assertTrue(
                relayFactory.receivedRequests.any { request ->
                    request.method == "thread/name/set"
                },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `resume thread uses thread resume and refreshes snapshot history`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-thread-resume",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        if (it.params?.jsonObjectOrNull?.firstString("archived") == "true") {
                            put("data", buildJsonArray { })
                            return@buildJsonObject
                        }
                        put(
                            "data",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-resume"))
                                        put("title", JsonPrimitive("Resume target"))
                                        put("cwd", JsonPrimitive("/tmp/project-resume"))
                                        put("updatedAt", JsonPrimitive(1_713_222_444))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/resume" to { request ->
                    val params = request.params?.jsonObjectOrNull
                    assertEquals("thread-resume", params?.firstString("threadId"))
                    assertEquals("/tmp/project-resume", params?.firstString("cwd"))
                    assertEquals("gpt-5.4", params?.firstString("model"))
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-resume"))
                                put("title", JsonPrimitive("Resume target"))
                                put("preview", JsonPrimitive("Resumed from bridge."))
                                put("cwd", JsonPrimitive("/tmp/project-resume"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-resume"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-resume"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive("Resume hydrated the conversation history."))
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            service.refreshThreads()
            advanceUntilIdle()
            awaitThreads(service, expectedCount = 1)

            val resumed = service.resumeThread(
                threadId = "thread-resume",
                preferredProjectPath = "/tmp/project-resume",
                modelIdentifier = "gpt-5.4",
            )
            advanceUntilIdle()

            assertNotNull(resumed)
            val thread = service.threads.value.first { it.id == "thread-resume" }
            assertTrue(
                TurnTimelineReducer.reduce(thread.timelineMutations).any { item ->
                    item.text.contains("Resume hydrated the conversation history.")
                },
            )
            assertTrue(
                relayFactory.receivedRequests.any { request ->
                    request.method == "thread/resume"
                },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `service tier retries once and future requests omit unsupported field`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-service-tier",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val threadStartServiceTiers = mutableListOf<String?>()
        val turnStartServiceTiers = mutableListOf<String?>()
        var threadReadCalls = 0
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "model/list" to {
                    buildJsonObject {
                        put(
                            "items",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("gpt-5.4"))
                                        put("model", JsonPrimitive("gpt-5.4"))
                                        put("displayName", JsonPrimitive("GPT-5.4"))
                                        put("isDefault", JsonPrimitive(true))
                                        put(
                                            "supported_reasoning_efforts",
                                            buildJsonArray {
                                                add(JsonPrimitive("medium"))
                                                add(JsonPrimitive("high"))
                                            },
                                        )
                                        put("default_reasoning_effort", JsonPrimitive("medium"))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/list" to {
                    buildJsonObject {
                        put("data", buildJsonArray { })
                    }
                },
                "thread/start" to { message ->
                    val serviceTier = message.params?.jsonObjectOrNull?.firstString("serviceTier")
                    threadStartServiceTiers += serviceTier
                    if (serviceTier == "fast") {
                        throw RpcError(code = -32602, message = "Unknown field serviceTier")
                    }
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-fast"))
                                put("title", JsonPrimitive("Fast thread"))
                                put("cwd", JsonPrimitive("/tmp/project-fast"))
                            },
                        )
                    }
                },
                "thread/read" to {
                    threadReadCalls += 1
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-fast"))
                                put("title", JsonPrimitive("Fast thread"))
                                put("cwd", JsonPrimitive("/tmp/project-fast"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to { message ->
                    turnStartServiceTiers += message.params?.jsonObjectOrNull?.firstString("serviceTier")
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-fast"))
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            advanceUntilIdle()

            val createdThread = service.createThread(
                preferredProjectPath = "/tmp/project-fast",
                runtimeDefaults = RemodexRuntimeDefaults(
                    accessMode = RemodexAccessMode.FULL_ACCESS,
                    serviceTier = RemodexServiceTier.FAST,
                ),
            )
            advanceUntilIdle()

            assertEquals(listOf("fast", null), threadStartServiceTiers)
            assertEquals(RemodexServiceTier.FAST, createdThread?.runtimeConfig?.serviceTier)
            assertEquals(listOf(RemodexServiceTier.FAST), createdThread?.runtimeConfig?.availableServiceTiers)
            assertEquals(0, threadReadCalls)
            assertTrue(service.isThreadResumedLocally("thread-fast"))

            service.sendPrompt(
                threadId = "thread-fast",
                prompt = "Ship this quickly.",
                runtimeConfig = createdThread!!.runtimeConfig,
                attachments = emptyList(),
            )
            advanceUntilIdle()

            assertEquals(listOf(null), turnStartServiceTiers)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread restores command execution details`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-command-details",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to { message ->
                    val archived = message.params?.jsonObjectOrNull?.firstString("archived") == "true"
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                if (!archived) {
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive("thread-command"))
                                            put("title", JsonPrimitive("Command thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-command"))
                                            put("updatedAt", JsonPrimitive(1_713_222_222))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-command"))
                                put("title", JsonPrimitive("Command thread"))
                                put("cwd", JsonPrimitive("/tmp/project-command"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-command"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("command-item-1"))
                                                                put("type", JsonPrimitive("command_execution"))
                                                                put("command", JsonPrimitive("git status --short"))
                                                                put("cwd", JsonPrimitive("/tmp/project-command"))
                                                                put("exitCode", JsonPrimitive(0))
                                                                put("durationMs", JsonPrimitive(1450))
                                                                put("output", JsonPrimitive("M app/src/Main.kt"))
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            advanceUntilIdle()

            service.refreshThreads()
            awaitThreads(service, expectedCount = 1)
            service.hydrateThread("thread-command")
            advanceUntilIdle()

            val details = service.commandExecutionDetails.value["command-item-1"]
            assertNotNull(details)
            assertEquals("git status --short", details?.fullCommand)
            assertEquals("/tmp/project-command", details?.cwd)
            assertEquals(0, details?.exitCode)
            assertEquals(1450, details?.durationMs)
            assertEquals("M app/src/Main.kt", details?.outputTail)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread orders command items before assistant replies when timestamps say they came first`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-command-order",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to { message ->
                    val archived = message.params?.jsonObjectOrNull?.firstString("archived") == "true"
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                if (!archived) {
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive("thread-command-order"))
                                            put("title", JsonPrimitive("Command ordering thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-command-order"))
                                            put("updatedAt", JsonPrimitive(1_713_222_230))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-command-order"))
                                put("title", JsonPrimitive("Command ordering thread"))
                                put("cwd", JsonPrimitive("/tmp/project-command-order"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-command-order"))
                                                put("createdAt", JsonPrimitive(1_713_222_220))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-item-1"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("createdAt", JsonPrimitive(1_713_222_225))
                                                                put("text", JsonPrimitive("The command finished successfully."))
                                                            },
                                                        )
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("command-item-1"))
                                                                put("type", JsonPrimitive("command_execution"))
                                                                put("createdAt", JsonPrimitive(1_713_222_223))
                                                                put("summary", JsonPrimitive("completed git status --short"))
                                                                put("command", JsonPrimitive("git status --short"))
                                                                put("output", JsonPrimitive("M app/src/Main.kt"))
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            advanceUntilIdle()

            service.refreshThreads()
            awaitThreads(service, expectedCount = 1)
            service.hydrateThread("thread-command-order")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-command-order" }
            val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)

            assertEquals(
                listOf("command-item-1", "assistant-item-1"),
                projected.map { it.id },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread decodes reasoning summary and content fields`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-reasoning-history",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to { message ->
                    val archived = message.params?.jsonObjectOrNull?.firstString("archived") == "true"
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                if (!archived) {
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive("thread-reasoning"))
                                            put("title", JsonPrimitive("Reasoning thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-reasoning"))
                                            put("updatedAt", JsonPrimitive(1_713_333_333))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-reasoning"))
                                put("title", JsonPrimitive("Reasoning thread"))
                                put("cwd", JsonPrimitive("/tmp/project-reasoning"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-reasoning"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("reasoning-item-1"))
                                                                put("type", JsonPrimitive("reasoning"))
                                                                put("summary", JsonPrimitive("Investigating timeline merge"))
                                                                put(
                                                                    "content",
                                                                    buildJsonArray {
                                                                        add(JsonPrimitive("running rg -n \"reasoning\" app/src/main/java"))
                                                                        add(JsonPrimitive("opened BridgeThreadSyncService.kt"))
                                                                    },
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            service.refreshThreads()
            advanceUntilIdle()

            service.hydrateThread("thread-reasoning")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-reasoning" }
            val reasoningItem = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
                .first { item -> item.kind == com.emanueledipietro.remodex.model.ConversationItemKind.REASONING }
            assertTrue(reasoningItem.text.contains("Investigating timeline merge"))
            assertTrue(reasoningItem.text.contains("running rg -n \"reasoning\" app/src/main/java"))
            assertTrue(reasoningItem.text.contains("opened BridgeThreadSyncService.kt"))
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread decodes file change items and file change toolcalls into rendered file rows`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-filechange-history",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to { message ->
                    val archived = message.params?.jsonObjectOrNull?.firstString("archived") == "true"
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                if (!archived) {
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive("thread-filechange"))
                                            put("title", JsonPrimitive("File change thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-filechange"))
                                            put("updatedAt", JsonPrimitive(1_713_333_444))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-filechange"))
                                put("title", JsonPrimitive("File change thread"))
                                put("cwd", JsonPrimitive("/tmp/project-filechange"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-filechange"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("filechange-item-1"))
                                                                put("type", JsonPrimitive("file_change"))
                                                                put("status", JsonPrimitive("completed"))
                                                                put(
                                                                    "changes",
                                                                    buildJsonArray {
                                                                        add(
                                                                            buildJsonObject {
                                                                                put("path", JsonPrimitive("app/src/Main.kt"))
                                                                                put("kind", JsonPrimitive("update"))
                                                                                put("additions", JsonPrimitive(2))
                                                                                put("deletions", JsonPrimitive(1))
                                                                                put(
                                                                                    "diff",
                                                                                    JsonPrimitive(
                                                                                        """
                                                                                        diff --git a/app/src/Main.kt b/app/src/Main.kt
                                                                                        index 1111111..2222222 100644
                                                                                        --- a/app/src/Main.kt
                                                                                        +++ b/app/src/Main.kt
                                                                                        @@ -1 +1,2 @@
                                                                                        -old
                                                                                        +new
                                                                                        +more
                                                                                        """.trimIndent(),
                                                                                    ),
                                                                                )
                                                                            },
                                                                        )
                                                                    },
                                                                )
                                                            },
                                                        )
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("toolcall-filechange-1"))
                                                                put("type", JsonPrimitive("tool_call"))
                                                                put("name", JsonPrimitive("apply_patch"))
                                                                put(
                                                                    "output",
                                                                    buildJsonObject {
                                                                        put(
                                                                            "changes",
                                                                            buildJsonArray {
                                                                                add(
                                                                                    buildJsonObject {
                                                                                        put("path", JsonPrimitive("app/src/Deleted.kt"))
                                                                                        put("kind", JsonPrimitive("delete"))
                                                                                        put("content", JsonPrimitive("gone"))
                                                                                        put("deletions", JsonPrimitive(1))
                                                                                    },
                                                                                )
                                                                            },
                                                                        )
                                                                    },
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            service.refreshThreads()
            advanceUntilIdle()

            service.hydrateThread("thread-filechange")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-filechange" }
            val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            val fileChangeItems = projected.filter { item ->
                item.kind == com.emanueledipietro.remodex.model.ConversationItemKind.FILE_CHANGE
            }

            assertEquals(2, fileChangeItems.size)

            val structuredRenderState = FileChangeRenderParser.renderState(fileChangeItems[0].text)
            val structuredEntry = structuredRenderState.summary?.entries?.single()
            assertEquals("app/src/Main.kt", structuredEntry?.path)
            assertEquals(FileChangeAction.EDITED, structuredEntry?.action)
            assertEquals(2, structuredEntry?.additions)
            assertEquals(1, structuredEntry?.deletions)

            val toolCallRenderState = FileChangeRenderParser.renderState(fileChangeItems[1].text)
            val toolCallEntry = toolCallRenderState.summary?.entries?.single()
            assertEquals("app/src/Deleted.kt", toolCallEntry?.path)
            assertEquals(FileChangeAction.DELETED, toolCallEntry?.action)
            assertEquals(0, toolCallEntry?.additions)
            assertEquals(1, toolCallEntry?.deletions)
            assertTrue(fileChangeItems[1].text.contains("Path: app/src/Deleted.kt"))
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread upserts restored active thread even when thread list omitted it`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-restored-thread",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to { message ->
                    val archived = message.params?.jsonObjectOrNull?.firstString("archived") == "true"
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                if (!archived) {
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive("thread-list-only"))
                                            put("title", JsonPrimitive("Listed thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-listed"))
                                            put("updatedAt", JsonPrimitive(1_713_444_444))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
                "thread/read" to { request ->
                    assertEquals("thread-restored", request.params?.jsonObjectOrNull?.firstString("threadId"))
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-restored"))
                                put("title", JsonPrimitive("Restored active thread"))
                                put("cwd", JsonPrimitive("/tmp/project-restored"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-restored"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-restored"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive("Recovered conversation history for the restored thread."))
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
            service.refreshThreads()
            advanceUntilIdle()
            awaitThreads(service, expectedCount = 1)

            service.hydrateThread("thread-restored")
            advanceUntilIdle()

            assertEquals(2, service.threads.value.size)
            val restoredThread = service.threads.value.first { it.id == "thread-restored" }
            assertTrue(
                TurnTimelineReducer.reduceProjected(restoredThread.timelineMutations).any { item ->
                    item.text.contains("Recovered conversation history for the restored thread.")
                },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
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

    private suspend fun TestScope.awaitThreads(
        service: BridgeThreadSyncService,
        expectedCount: Int,
    ) {
        repeat(40) {
            advanceUntilIdle()
            if (service.threads.value.size == expectedCount) {
                return
            }
            Thread.sleep(10)
        }
        fail("Expected $expectedCount threads but found ${service.threads.value.size}")
    }

}
