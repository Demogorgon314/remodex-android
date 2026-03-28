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
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeThreadSyncServiceTest {
    @Test
    fun `streaming text delta filter keeps whitespace only chunks`() {
        assertTrue(shouldIgnoreStreamingTextDelta(""))
        assertFalse(shouldIgnoreStreamingTextDelta(" "))
        assertFalse(shouldIgnoreStreamingTextDelta("\n"))
        assertFalse(shouldIgnoreStreamingTextDelta("    "))
    }

    @Test
    fun `assistant lifecycle start text preserves existing streaming content and ignores lifecycle body`() {
        assertEquals("", assistantLifecycleStartedText(null))
        assertEquals("already streamed", assistantLifecycleStartedText("already streamed"))
        assertEquals("  indented\ntext", assistantLifecycleStartedText("  indented\ntext"))
    }

    @Test
    fun `assistant completion reuses same turn duplicate text like ios`() {
        val existing = RemodexConversationItem(
            id = "assistant-turn-1",
            speaker = ConversationSpeaker.ASSISTANT,
            kind = ConversationItemKind.CHAT,
            text = "Review result",
            turnId = "turn-1",
            orderIndex = 7L,
        )

        val resolved = findReusableAssistantCompletionItemValue(
            items = listOf(existing),
            turnId = "turn-1",
            itemId = "review-exit",
            text = "Review result",
        )

        assertEquals("assistant-turn-1", resolved?.id)
    }

    @Test
    fun `anonymous assistant completion suppression only applies to recent duplicate text without identity`() {
        assertTrue(
            shouldSuppressAnonymousAssistantCompletionValue(
                turnId = null,
                itemId = null,
                text = "Review result",
                previousText = "Review result",
                elapsedMs = 500L,
            ),
        )
        assertFalse(
            shouldSuppressAnonymousAssistantCompletionValue(
                turnId = "turn-1",
                itemId = null,
                text = "Review result",
                previousText = "Review result",
                elapsedMs = 500L,
            ),
        )
        assertFalse(
            shouldSuppressAnonymousAssistantCompletionValue(
                turnId = null,
                itemId = null,
                text = "Review result",
                previousText = "Review result",
                elapsedMs = 60_000L,
            ),
        )
    }

    @Test
    fun `optimistic review prompt can be replaced by authoritative review user message`() {
        assertTrue(
            shouldReplaceOptimisticReviewPromptValue(
                localText = "Review current changes",
                incomingText = "Review the current code changes (staged, unstaged, and untracked files) and provide prioritized findings.",
            ),
        )
        assertFalse(
            shouldReplaceOptimisticReviewPromptValue(
                localText = "Ship notifications",
                incomingText = "Review the current code changes (staged, unstaged, and untracked files) and provide prioritized findings.",
            ),
        )
    }

    @Test
    fun `completed reasoning placeholder should be dropped when no real reasoning text exists`() {
        assertTrue(
            shouldDropCompletedReasoningPlaceholderValue(
                existingText = "Thinking...",
                completedBody = "Thinking...",
            ),
        )
        assertFalse(
            shouldDropCompletedReasoningPlaceholderValue(
                existingText = "Inspecting git diff",
                completedBody = "Thinking...",
            ),
        )
    }

    @Test
    fun `thread read history merge skips running threads when local timeline already exists`() {
        assertFalse(
            shouldMergeThreadReadHistory(
                threadIsRunning = true,
                existingHasTimeline = true,
                allowWhileRunning = false,
            ),
        )
        assertTrue(
            shouldMergeThreadReadHistory(
                threadIsRunning = true,
                existingHasTimeline = false,
                allowWhileRunning = false,
            ),
        )
        assertTrue(
            shouldMergeThreadReadHistory(
                threadIsRunning = true,
                existingHasTimeline = true,
                allowWhileRunning = true,
            ),
        )
    }

    @Test
    fun `assistant delta without resolved turn id is ignored during reconnect fallback like ios`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = ScriptedRpcRelayWebSocketFactory(
                macDeviceId = "mac-turnless-assistant-delta",
                macIdentity = macIdentity,
            ),
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-turnless-assistant",
                    title = "Turn-less assistant delta",
                    preview = "",
                    projectPath = "/tmp/project-turnless-assistant",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = false,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "markThreadAsRunningFallback",
            "thread-turnless-assistant",
        )
        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-turnless-assistant"))
                put("delta", JsonPrimitive("Hello from the reconnect gap"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-turnless-assistant" }
        val items = TurnTimelineReducer.reduce(thread.timelineMutations)

        assertTrue(thread.isRunning)
        assertTrue(items.none { item ->
            item.speaker == ConversationSpeaker.ASSISTANT &&
                item.text.contains("Hello from the reconnect gap")
        })
    }

    @Test
    fun `assistant item started without resolved turn id does not create orphan streaming row`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = ScriptedRpcRelayWebSocketFactory(
                macDeviceId = "mac-turnless-assistant-start",
                macIdentity = macIdentity,
            ),
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-turnless-start",
                    title = "Turn-less assistant start",
                    preview = "",
                    projectPath = "/tmp/project-turnless-start",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = false,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "markThreadAsRunningFallback",
            "thread-turnless-start",
        )
        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-turnless-start"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("assistant-item-1"))
                        put("type", JsonPrimitive("agent_message"))
                    },
                )
            },
            false,
        )

        val thread = service.threads.value.first { it.id == "thread-turnless-start" }
        val items = TurnTimelineReducer.reduce(thread.timelineMutations)

        assertTrue(thread.isRunning)
        assertTrue(items.none { item ->
            item.speaker == ConversationSpeaker.ASSISTANT && item.isStreaming
        })
    }

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
    fun `thread materialization retry matcher only accepts rollout race errors`() {
        assertTrue(
            shouldRetryAfterThreadMaterializationValue(
                RpcError(
                    code = -32000,
                    message = "No rollout found for thread id thread-1",
                ),
            ),
        )
        assertTrue(
            shouldRetryAfterThreadMaterializationValue(
                IllegalStateException("Thread thread-1 is not yet materialized."),
            ),
        )
        assertFalse(
            shouldRetryAfterThreadMaterializationValue(
                RpcError(
                    code = -32000,
                    message = "thread not found: thread-1",
                ),
            ),
        )
        assertFalse(
            shouldRetryAfterThreadMaterializationValue(
                RpcError(
                    code = -32602,
                    message = "Invalid params",
                ),
            ),
        )
    }

    @Test
    fun `assistant lifecycle matcher treats generic messages without a user role as assistant like ios`() {
        assertTrue(
            isAssistantLifecycleItemValue(
                itemType = "message",
                role = null,
            ),
        )
        assertTrue(
            isAssistantLifecycleItemValue(
                itemType = "message",
                role = "assistant",
            ),
        )
        assertFalse(
            isAssistantLifecycleItemValue(
                itemType = "message",
                role = "user",
            ),
        )
    }

    @Test
    fun `completed assistant fallback reads plain message text from legacy agent message envelopes`() {
        val eventObject = buildJsonObject {
            put("message", JsonPrimitive("Legacy review result"))
        }
        val paramsObject = buildJsonObject {
            put("threadId", JsonPrimitive("thread-1"))
            put("turnId", JsonPrimitive("turn-1"))
            put("event", eventObject)
        }

        assertEquals(
            "Legacy review result",
            completedAssistantFallbackTextValue(
                paramsObject = paramsObject,
                eventObject = eventObject,
            ),
        )
    }

    @Test
    fun `assistant turn id fallback matches ios legacy agent envelopes`() {
        val eventObject = buildJsonObject {
            put(
                "turn",
                buildJsonObject {
                    put("id", JsonPrimitive("turn-from-event"))
                },
            )
        }

        val paramsWithLegacyId = buildJsonObject {
            put("id", JsonPrimitive("turn-from-params"))
            put("event", eventObject)
        }
        assertEquals(
            "turn-from-params",
            extractAssistantTurnIdValue(
                paramsObject = paramsWithLegacyId,
                eventObject = eventObject,
                extractedTurnId = null,
            ),
        )

        val paramsWithoutLegacyId = buildJsonObject {
            put("event", eventObject)
        }
        assertEquals(
            "turn-from-event",
            extractAssistantTurnIdValue(
                paramsObject = paramsWithoutLegacyId,
                eventObject = eventObject,
                extractedTurnId = null,
            ),
        )
    }

    @Test
    fun `incoming message text decoder matches ios content fallbacks`() {
        val itemObject = buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("output_text"))
                            put(
                                "data",
                                buildJsonObject {
                                    put("text", JsonPrimitive("Line one"))
                                },
                            )
                        },
                    )
                    add(
                        buildJsonObject {
                            put("delta", JsonPrimitive("Line two"))
                        },
                    )
                },
            )
        }

        assertEquals(
            "Line one\nLine two",
            decodeIncomingMessageTextValue(itemObject),
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
    fun `fork thread encodes sandbox using bridge runtime values`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-fork-sandbox",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var capturedForkParams: JsonObject? = null
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-source"))
                                        put("title", JsonPrimitive("Source thread"))
                                        put("cwd", JsonPrimitive("/tmp/project-source"))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/fork" to { message ->
                    capturedForkParams = message.params?.jsonObjectOrNull
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-forked"))
                                put("title", JsonPrimitive("Forked thread"))
                                put("cwd", JsonPrimitive("/tmp/project-local"))
                            },
                        )
                    }
                },
                "thread/read" to { request ->
                    val threadId = request.params?.jsonObjectOrNull?.firstString("threadId")
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive(threadId ?: "thread-forked"))
                                put("title", JsonPrimitive("Forked thread"))
                                put("cwd", JsonPrimitive("/tmp/project-local"))
                                put("turns", buildJsonArray { })
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

            service.forkThreadIntoProjectPath(
                threadId = "thread-source",
                projectPath = "/tmp/project-local",
            )
            advanceUntilIdle()

            assertEquals("workspace-write", capturedForkParams?.firstString("sandbox"))
            assertEquals("on-request", capturedForkParams?.firstString("approvalPolicy"))
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `fork thread into prepared project path upserts immediately and retries hydration until materialized`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-fork-materialize",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var forkedThreadReadCalls = 0
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
                                            put("id", JsonPrimitive("thread-source"))
                                            put("title", JsonPrimitive("Source thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-source"))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
                "thread/fork" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-forked"))
                                put("title", JsonPrimitive("Forked thread"))
                                put("cwd", JsonPrimitive("/tmp/project-forked"))
                            },
                        )
                    }
                },
                "thread/read" to { request ->
                    val threadId = request.params?.jsonObjectOrNull?.firstString("threadId", "thread_id")
                    when (threadId) {
                        "thread-forked" -> {
                            forkedThreadReadCalls += 1
                            if (forkedThreadReadCalls == 1) {
                                throw RpcError(
                                    code = -32000,
                                    message = "No rollout found for thread id thread-forked",
                                )
                            }
                            buildJsonObject {
                                put(
                                    "thread",
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-forked"))
                                        put("title", JsonPrimitive("Forked thread"))
                                        put("cwd", JsonPrimitive("/tmp/project-forked"))
                                        put("turns", buildJsonArray { })
                                    },
                                )
                            }
                        }

                        else -> buildJsonObject {
                            put(
                                "thread",
                                buildJsonObject {
                                    put("id", JsonPrimitive(threadId ?: "thread-source"))
                                    put("title", JsonPrimitive("Source thread"))
                                    put("cwd", JsonPrimitive("/tmp/project-source"))
                                    put("turns", buildJsonArray { })
                                },
                            )
                        }
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

            val forkedThread = service.forkThreadIntoProjectPath(
                threadId = "thread-source",
                projectPath = "/tmp/project-forked",
            )
            advanceUntilIdle()

            assertEquals("thread-forked", forkedThread?.id)
            assertEquals("thread-forked", service.threads.value.firstOrNull()?.id)
            assertEquals("/tmp/project-forked", service.threads.value.firstOrNull()?.projectPath)
            assertEquals(2, forkedThreadReadCalls)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `image url fallback only retries compatibility-shaped rpc errors`() {
        assertTrue(
            shouldRetryWithImageUrlFieldFallbackValue(
                RpcError(
                    code = -32600,
                    message = "Invalid request: missing field `image_url`",
                ),
            ),
        )
        assertFalse(
            shouldRetryWithImageUrlFieldFallbackValue(
                RpcError(
                    code = -32603,
                    message = "backend unavailable",
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
    fun `hydrate thread restores review enter and exit items like ios`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-review-history",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", JsonPrimitive("thread-review"))
                                        put("title", JsonPrimitive("Review thread"))
                                        put("cwd", JsonPrimitive("/tmp/project-review"))
                                        put("updatedAt", JsonPrimitive(1_713_111_222))
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
                                put("id", JsonPrimitive("thread-review"))
                                put("title", JsonPrimitive("Review thread"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-review"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("review-enter"))
                                                                put("type", JsonPrimitive("enteredReviewMode"))
                                                                put(
                                                                    "review",
                                                                    buildJsonObject {
                                                                        put("summary", JsonPrimitive("base branch"))
                                                                    },
                                                                )
                                                            },
                                                        )
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("review-exit"))
                                                                put("type", JsonPrimitive("exitedReviewMode"))
                                                                put(
                                                                    "review",
                                                                    buildJsonObject {
                                                                        put(
                                                                            "content",
                                                                            buildJsonArray {
                                                                                add(JsonPrimitive("Line one"))
                                                                                add(JsonPrimitive("Line two"))
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

            service.hydrateThread("thread-review")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-review" }
            val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)

            assertEquals(2, projected.size)
            assertEquals("Reviewing base branch...", projected[0].text)
            assertEquals(
                com.emanueledipietro.remodex.model.ConversationItemKind.COMMAND_EXECUTION,
                projected[0].kind,
            )
            assertEquals("Line one\nLine two", projected[1].text)
            assertEquals(
                com.emanueledipietro.remodex.model.ConversationItemKind.CHAT,
                projected[1].kind,
            )
            assertEquals(
                com.emanueledipietro.remodex.model.ConversationSpeaker.ASSISTANT,
                projected[1].speaker,
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `refresh threads preserves running review thread when thread list lags behind server state`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-review-running-preserve",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var threadListReads = 0
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to { message ->
                    val archived = message.params?.jsonObjectOrNull?.firstString("archived") == "true"
                    threadListReads += 1
                    buildJsonObject {
                        put(
                            "data",
                            buildJsonArray {
                                if (!archived && threadListReads <= 2) {
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive("thread-review-running"))
                                            put("title", JsonPrimitive("Review thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-review-running"))
                                            put("updatedAt", JsonPrimitive(1_713_111_333))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
                "review/start" to {
                    buildJsonObject {
                        put(
                            "turn",
                            buildJsonObject {
                                put("id", JsonPrimitive("turn-review-running"))
                                put("status", JsonPrimitive("inProgress"))
                                put("items", buildJsonArray { })
                            },
                        )
                        put("reviewThreadId", JsonPrimitive("thread-review-running"))
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

            service.startCodeReview(
                threadId = "thread-review-running",
                target = com.emanueledipietro.remodex.model.RemodexComposerReviewTarget.UNCOMMITTED_CHANGES,
                baseBranch = null,
            )
            advanceUntilIdle()

            service.refreshThreads()
            advanceUntilIdle()

            val thread = service.threads.value.firstOrNull { it.id == "thread-review-running" }
            assertNotNull(thread)
            assertTrue(thread?.isRunning == true)
            assertTrue(thread?.timelineMutations?.isNotEmpty() == true)
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
    fun `hydrate thread merges server history for running thread even when local timeline exists`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-running-history-merge",
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
                                        put("id", JsonPrimitive("thread-running-history"))
                                        put("title", JsonPrimitive("Reconnect target"))
                                        put("cwd", JsonPrimitive("/tmp/project-running-history"))
                                        put("updatedAt", JsonPrimitive(1_713_222_335))
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
                                put("id", JsonPrimitive("thread-running-history"))
                                put("title", JsonPrimitive("Reconnect target"))
                                put("cwd", JsonPrimitive("/tmp/project-running-history"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-running-history"))
                                                put("status", JsonPrimitive("running"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-history"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put(
                                                                    "text",
                                                                    JsonPrimitive("Recovered assistant output after reconnect."),
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

            service.appendLocalSystemMessage("thread-running-history", "Local reconnect note")
            advanceUntilIdle()

            service.hydrateThread("thread-running-history")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-running-history" }
            val items = TurnTimelineReducer.reduce(thread.timelineMutations)
            assertTrue(thread.isRunning)
            assertTrue(items.any { it.text == "Local reconnect note" })
            assertTrue(items.any { it.text == "Recovered assistant output after reconnect." })
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
    fun `send prompt retries transient missing rollout errors for newly created threads`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-rollout-race",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var turnStartCalls = 0
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        put("data", buildJsonArray { })
                    }
                },
                "thread/start" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-rollout-race"))
                                put("title", JsonPrimitive("Rollout race"))
                                put("cwd", JsonPrimitive("/tmp/project-rollout-race"))
                            },
                        )
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-rollout-race"))
                                put("title", JsonPrimitive("Rollout race"))
                                put("cwd", JsonPrimitive("/tmp/project-rollout-race"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to {
                    turnStartCalls += 1
                    if (turnStartCalls == 1) {
                        throw RpcError(
                            code = -32000,
                            message = "No rollout found for thread id thread-rollout-race",
                        )
                    }
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-rollout-race"))
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
                preferredProjectPath = "/tmp/project-rollout-race",
                runtimeDefaults = RemodexRuntimeDefaults(),
            )
            advanceUntilIdle()

            service.sendPrompt(
                threadId = "thread-rollout-race",
                prompt = "Retry the first turn after rollout materializes.",
                runtimeConfig = createdThread!!.runtimeConfig,
                attachments = emptyList(),
            )
            advanceUntilIdle()

            assertEquals(2, turnStartCalls)
            val thread = service.threads.value.first { it.id == "thread-rollout-race" }
            assertTrue(
                TurnTimelineReducer.reduce(thread.timelineMutations).any { item ->
                    item.text.contains("Retry the first turn after rollout materializes.")
                },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `send prompt encodes image attachments as url data payloads`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-image-url",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var capturedImageItem: JsonObject? = null
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        put("data", buildJsonArray { })
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-image-url"))
                                put("title", JsonPrimitive("Image thread"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to { message ->
                    capturedImageItem = message.params
                        ?.jsonObjectOrNull
                        ?.firstArray("input")
                        ?.firstOrNull()
                        ?.jsonObjectOrNull
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-image-url"))
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

            service.sendPrompt(
                threadId = "thread-image-url",
                prompt = "",
                runtimeConfig = RemodexRuntimeConfig(),
                attachments = listOf(
                    RemodexComposerAttachment(
                        id = "attachment-1",
                        uriString = "content://media/external/images/media/1",
                        displayName = "photo.jpg",
                        payloadDataUrl = "data:image/jpeg;base64,AAAA",
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals("image", capturedImageItem?.firstString("type"))
            assertEquals("data:image/jpeg;base64,AAAA", capturedImageItem?.firstString("url"))
            assertNull(capturedImageItem?.firstString("image_url"))
            assertNull(capturedImageItem?.firstString("name"))
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `send prompt falls back to image_url for legacy runtimes`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-image-url-fallback",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val imageFieldKeys = mutableListOf<String?>()
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/list" to {
                    buildJsonObject {
                        put("data", buildJsonArray { })
                    }
                },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-image-url-fallback"))
                                put("title", JsonPrimitive("Legacy image thread"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to { message ->
                    val imageItem = message.params
                        ?.jsonObjectOrNull
                        ?.firstArray("input")
                        ?.firstOrNull()
                        ?.jsonObjectOrNull
                    imageFieldKeys += when {
                        imageItem?.containsKey("url") == true -> "url"
                        imageItem?.containsKey("image_url") == true -> "image_url"
                        else -> null
                    }
                    if (imageFieldKeys.size == 1) {
                        throw RpcError(code = -32600, message = "Invalid request: missing field `image_url`")
                    }
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-image-url-fallback"))
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

            service.sendPrompt(
                threadId = "thread-image-url-fallback",
                prompt = "",
                runtimeConfig = RemodexRuntimeConfig(),
                attachments = listOf(
                    RemodexComposerAttachment(
                        id = "attachment-1",
                        uriString = "content://media/external/images/media/1",
                        displayName = "photo.jpg",
                        payloadDataUrl = "data:image/jpeg;base64,BBBB",
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(listOf("url", "image_url"), imageFieldKeys)
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

    private fun invokePrivateMethod(
        target: Any,
        methodName: String,
        vararg args: Any?,
    ): Any? {
        val method = target.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterCount == args.size
        } ?: error("Missing method $methodName with ${args.size} parameters")
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedThreads(
        service: BridgeThreadSyncService,
        snapshots: List<ThreadSyncSnapshot>,
    ) {
        val field = service.javaClass.getDeclaredField("backingThreads")
        field.isAccessible = true
        val state = field.get(service) as kotlinx.coroutines.flow.MutableStateFlow<List<ThreadSyncSnapshot>>
        state.value = snapshots
    }

}
