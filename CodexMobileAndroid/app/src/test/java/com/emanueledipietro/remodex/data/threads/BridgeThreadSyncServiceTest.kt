package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.data.connection.InMemorySecureStore
import com.emanueledipietro.remodex.data.connection.RpcError
import com.emanueledipietro.remodex.data.connection.RpcMessage
import com.emanueledipietro.remodex.data.connection.ScriptedRpcRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.data.connection.SecureConnectionState
import com.emanueledipietro.remodex.data.connection.UnexpectedRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.UnusedTrustedSessionResolver
import com.emanueledipietro.remodex.data.connection.createTestMacIdentity
import com.emanueledipietro.remodex.data.connection.createTestPairingPayload
import com.emanueledipietro.remodex.data.connection.firstArray
import com.emanueledipietro.remodex.data.connection.firstObject
import com.emanueledipietro.remodex.data.connection.firstString
import com.emanueledipietro.remodex.data.connection.jsonObjectOrNull
import com.emanueledipietro.remodex.feature.turn.FileChangeAction
import com.emanueledipietro.remodex.feature.turn.FileChangeRenderParser
import com.emanueledipietro.remodex.feature.turn.TurnTimelineReducer
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.ConversationSystemTurnOrderingHint
import com.emanueledipietro.remodex.model.RemodexApprovalRequest
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexContextWindowUsage
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexPermissionGrantScope
import com.emanueledipietro.remodex.model.RemodexRequestedPermissions
import com.emanueledipietro.remodex.model.remodexBridgeUpdateCommand
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputRequest
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexCommandExecutionLiveStatus
import com.emanueledipietro.remodex.model.RemodexCommandExecutionSource
import com.emanueledipietro.remodex.model.RemodexTurnTerminalState
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.model.RemodexRuntimeConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun JsonObject.firstBooleanValue(vararg keys: String): Boolean? {
    keys.forEach { key ->
        this[key]?.jsonPrimitive?.contentOrNull?.let { raw ->
            raw.toBooleanStrictOrNull()?.let { return it }
        }
    }
    return null
}

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
    fun `history file change summary separates metadata only rows from displayable rows`() {
        val displayableFileChange = RemodexConversationItem(
            id = "file-change-displayable",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.FILE_CHANGE,
            text = """
                Status: completed

                Path: src/Visible.kt
                Kind: update
                Totals: +3 -1
            """.trimIndent(),
        )
        val metadataOnlyFileChange = RemodexConversationItem(
            id = "file-change-metadata",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.FILE_CHANGE,
            text = """
                Status: completed

                Path: src/Hidden.kt
                Kind: update
            """.trimIndent(),
        )
        val assistantMessage = RemodexConversationItem(
            id = "assistant-message",
            speaker = ConversationSpeaker.ASSISTANT,
            text = "Done",
        )

        val summary = summarizeThreadHistoryFileChanges(
            listOf(displayableFileChange, metadataOnlyFileChange, assistantMessage),
        )

        assertEquals(3, summary.totalItems)
        assertEquals(2, summary.fileChangeItems)
        assertEquals(1, summary.displayableFileChangeItems)
        assertEquals(1, summary.metadataOnlyFileChangeItems)
    }

    @Test
    fun `approval decision response wraps decision in object shape`() {
        assertEquals(
            buildJsonObject {
                put("decision", JsonPrimitive("accept"))
            },
            buildApprovalDecisionResponse("accept"),
        )
        assertEquals(
            buildJsonObject {
                put("decision", JsonPrimitive("decline"))
            },
            buildApprovalDecisionResponse("decline"),
        )
    }

    @Test
    fun `permissions approval response wraps permissions and scope in object shape`() {
        val permissions = buildJsonObject {
            put(
                "network",
                buildJsonObject {
                    put("enabled", JsonPrimitive(true))
                },
            )
        }

        assertEquals(
            buildJsonObject {
                put("permissions", permissions)
                put("scope", JsonPrimitive("session"))
            },
            buildPermissionsApprovalResponse(
                permissions = permissions,
                scope = RemodexPermissionGrantScope.SESSION,
            ),
        )
    }

    @Test
    fun `initialize session skips when encrypted state races ahead of secure session readiness`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = backgroundScope,
        )
        setSecureConnectionState(
            coordinator = coordinator,
            snapshot = SecureConnectionSnapshot(
                phaseMessage = "Connected",
                secureState = SecureConnectionState.ENCRYPTED,
                attempt = 1,
            ),
        )

        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )
        advanceUntilIdle()

        assertTrue(service.availableModels.value.isEmpty())
        assertTrue(service.threads.value.isEmpty())
    }

    @Test
    fun `refresh threads skips when encrypted snapshot outlives secure transport session`() = runTest {
        val connected = createConnectedBridgeService()

        try {
            clearSecureTransportSession(connected.coordinator)

            connected.service.refreshThreads()
            advanceUntilIdle()

            assertTrue(connected.service.threads.value.isEmpty())
        } finally {
            connected.coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `decode requested permissions parses network and filesystem access`() {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = TestScope(),
            ),
            scope = TestScope(),
        )

        val decoded = invokePrivateMethod(
            service,
            "decodeRequestedPermissions",
            buildJsonObject {
                put(
                    "permissions",
                    buildJsonObject {
                        put(
                            "network",
                            buildJsonObject {
                                put("enabled", JsonPrimitive(true))
                            },
                        )
                        put(
                            "fileSystem",
                            buildJsonObject {
                                put(
                                    "read",
                                    buildJsonArray {
                                        add(JsonPrimitive("/tmp/readme.md"))
                                    },
                                )
                                put(
                                    "write",
                                    buildJsonArray {
                                        add(JsonPrimitive("/tmp/output.txt"))
                                    },
                                )
                            },
                        )
                    },
                )
            },
        ) as RemodexRequestedPermissions?

        assertEquals(
            RemodexRequestedPermissions(
                networkEnabled = true,
                readPaths = listOf("/tmp/readme.md"),
                writePaths = listOf("/tmp/output.txt"),
            ),
            decoded,
        )
    }

    @Test
    fun `approve pending file change for session sends acceptForSession response`() = runTest {
        val connected = createConnectedBridgeService()

        try {
            setPendingApprovalRequest(
                connected.service,
                RemodexApprovalRequest(
                    id = "approval-file",
                    requestId = JsonPrimitive("req-file"),
                    method = "item/fileChange/requestApproval",
                ),
            )

            connected.service.approvePendingApproval(forSession = true)
            advanceUntilIdle()

            val response = connected.relayFactory.receivedRequests.last()
            assertEquals(JsonPrimitive("req-file"), response.id)
            assertEquals(
                buildApprovalDecisionResponse("acceptForSession"),
                response.result,
            )
        } finally {
            connected.coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `granting pending permissions approval echoes requested permissions with scope`() = runTest {
        val connected = createConnectedBridgeService()
        val requestedPermissions = buildJsonObject {
            put(
                "network",
                buildJsonObject {
                    put("enabled", JsonPrimitive(true))
                },
            )
            put(
                "fileSystem",
                buildJsonObject {
                    put(
                        "read",
                        buildJsonArray {
                            add(JsonPrimitive("/tmp/project"))
                        },
                    )
                },
            )
        }

        try {
            setPendingApprovalRequest(
                connected.service,
                RemodexApprovalRequest(
                    id = "approval-permissions",
                    requestId = JsonPrimitive("req-permissions"),
                    method = "item/permissions/requestApproval",
                    params = buildJsonObject {
                        put("permissions", requestedPermissions)
                    },
                ),
            )

            connected.service.grantPendingPermissionsApproval(RemodexPermissionGrantScope.SESSION)
            advanceUntilIdle()

            val response = connected.relayFactory.receivedRequests.last()
            assertEquals(JsonPrimitive("req-permissions"), response.id)
            assertEquals(
                buildPermissionsApprovalResponse(
                    permissions = requestedPermissions,
                    scope = RemodexPermissionGrantScope.SESSION,
                ),
                response.result,
            )
        } finally {
            connected.coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `declining pending permissions approval sends empty permissions for the turn`() = runTest {
        val connected = createConnectedBridgeService()

        try {
            setPendingApprovalRequest(
                connected.service,
                RemodexApprovalRequest(
                    id = "approval-permissions",
                    requestId = JsonPrimitive("req-permissions"),
                    method = "item/permissions/requestApproval",
                ),
            )

            connected.service.declinePendingApproval()
            advanceUntilIdle()

            val response = connected.relayFactory.receivedRequests.last()
            assertEquals(JsonPrimitive("req-permissions"), response.id)
            assertEquals(
                buildPermissionsApprovalResponse(
                    permissions = buildJsonObject { },
                    scope = RemodexPermissionGrantScope.TURN,
                ),
                response.result,
            )
        } finally {
            connected.coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `assistant lifecycle start text preserves existing streaming content and ignores lifecycle body`() {
        assertEquals("", assistantLifecycleStartedText(null))
        assertEquals("already streamed", assistantLifecycleStartedText("already streamed"))
        assertEquals("  indented\ntext", assistantLifecycleStartedText("  indented\ntext"))
    }

    @Test
    fun `legacy token count publishes assistant response metrics after completion`() {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = TestScope(),
            ),
            scope = TestScope(),
        )

        invokePrivateMethod(service, "setActiveTurnId", "thread-metrics", "turn-metrics")
        Thread.sleep(5)
        invokePrivateMethod(
            service,
            "trackAssistantMessageReference",
            "thread-metrics",
            "turn-metrics",
            "item-metrics",
            "assistant-message",
        )
        invokePrivateMethod(
            service,
            "recordAssistantOutputObserved",
            "thread-metrics",
            "turn-metrics",
            "item-metrics",
            "assistant-message",
        )
        invokePrivateMethod(
            service,
            "handleLegacyTokenCountEvent",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-metrics"))
                put("turnId", JsonPrimitive("turn-metrics"))
                put(
                    "info",
                    buildJsonObject {
                        put(
                            "last_token_usage",
                            buildJsonObject {
                                put("output_tokens", JsonPrimitive(64))
                            },
                        )
                    },
                )
            },
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-metrics"))
                put("turnId", JsonPrimitive("turn-metrics"))
            },
        )
        invokePrivateMethod(service, "markAssistantTurnCompleted", "thread-metrics", "turn-metrics")

        val metrics = service.assistantResponseMetricsByThreadId.value["thread-metrics"]

        assertNotNull(metrics)
        assertEquals("assistant-message", metrics?.messageId)
        assertEquals("turn-metrics", metrics?.turnId)
        assertEquals(64, metrics?.outputTokens)
        assertTrue((metrics?.tokensPerSecond ?: 0.0) > 0.0)
        assertTrue((metrics?.ttftMs ?: -1L) >= 0L)
    }

    @Test
    fun `thread token usage updated publishes assistant response metrics after completion`() {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = TestScope(),
            ),
            scope = TestScope(),
        )

        invokePrivateMethod(service, "setActiveTurnId", "thread-v2-metrics", "turn-v2-metrics")
        Thread.sleep(5)
        invokePrivateMethod(
            service,
            "trackAssistantMessageReference",
            "thread-v2-metrics",
            "turn-v2-metrics",
            "item-v2-metrics",
            "assistant-message-v2",
        )
        invokePrivateMethod(
            service,
            "recordAssistantOutputObserved",
            "thread-v2-metrics",
            "turn-v2-metrics",
            "item-v2-metrics",
            "assistant-message-v2",
        )
        invokePrivateMethod(
            service,
            "handleThreadTokenUsageUpdatedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-v2-metrics"))
                put("turnId", JsonPrimitive("turn-v2-metrics"))
                put(
                    "tokenUsage",
                    buildJsonObject {
                        put(
                            "last",
                            buildJsonObject {
                                put("outputTokens", JsonPrimitive(9))
                            },
                        )
                    },
                )
            },
        )
        invokePrivateMethod(service, "markAssistantTurnCompleted", "thread-v2-metrics", "turn-v2-metrics")

        val metrics = service.assistantResponseMetricsByThreadId.value["thread-v2-metrics"]

        assertNotNull(metrics)
        assertEquals("assistant-message-v2", metrics?.messageId)
        assertEquals("turn-v2-metrics", metrics?.turnId)
        assertEquals(9, metrics?.outputTokens)
        assertTrue((metrics?.tokensPerSecond ?: 0.0) > 0.0)
    }

    @Test
    fun `thread token usage updated publishes context window usage`() {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = TestScope(),
            ),
            scope = TestScope(),
        )

        invokePrivateMethod(
            service,
            "handleThreadTokenUsageUpdatedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-usage"))
                put(
                    "usage",
                    buildJsonObject {
                        put("tokensUsed", JsonPrimitive(173_033))
                        put("tokenLimit", JsonPrimitive(258_400))
                    },
                )
            },
        )

        assertEquals(
            RemodexContextWindowUsage(tokensUsed = 173_033, tokenLimit = 258_400),
            service.contextWindowUsageByThreadId.value["thread-usage"],
        )
    }

    @Test
    fun `legacy token count publishes context window usage`() {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = TestScope(),
            ),
            scope = TestScope(),
        )

        invokePrivateMethod(
            service,
            "handleLegacyTokenCountEvent",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-legacy-usage"))
                put(
                    "info",
                    buildJsonObject {
                        put(
                            "last_token_usage",
                            buildJsonObject {
                                put("total_tokens", JsonPrimitive(200_930))
                            },
                        )
                        put("model_context_window", JsonPrimitive(258_400))
                    },
                )
            },
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-legacy-usage"))
            },
        )

        assertEquals(
            RemodexContextWindowUsage(tokensUsed = 200_930, tokenLimit = 258_400),
            service.contextWindowUsageByThreadId.value["thread-legacy-usage"],
        )
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
    fun `thread snapshot keeps active thread running when status reports waitingOnApproval`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        val snapshot = invokePrivateMethod(
            service,
            "parseThreadSnapshot",
            buildJsonObject {
                put("id", JsonPrimitive("thread-waiting-approval"))
                put("title", JsonPrimitive("Approval thread"))
                put("cwd", JsonPrimitive("/tmp/thread-waiting-approval"))
                put(
                    "status",
                    buildJsonObject {
                        put("type", JsonPrimitive("active"))
                        put(
                            "activeFlags",
                            buildJsonArray {
                                add(JsonPrimitive("waitingOnApproval"))
                            },
                        )
                    },
                )
            },
            RemodexThreadSyncState.LIVE,
            null,
        ) as ThreadSyncSnapshot?

        assertNotNull(snapshot)
        assertTrue(snapshot?.isWaitingOnApproval == true)
        assertTrue(snapshot?.isRunning == true)
    }

    @Test
    fun `thread read merge uses latest live timeline instead of stale snapshot`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = ScriptedRpcRelayWebSocketFactory(
                macDeviceId = "mac-stale-thread-read",
                macIdentity = macIdentity,
            ),
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )
        val staleSnapshot = ThreadSyncSnapshot(
            id = "thread-live",
            title = "Live thread",
            preview = "Hello",
            projectPath = "/tmp/project-live",
            lastUpdatedLabel = "Updated just now",
            lastUpdatedEpochMs = 1L,
            isRunning = true,
            runtimeConfig = RemodexRuntimeConfig(),
            timelineMutations = listOf(
                TimelineMutation.Upsert(
                    timelineItem(
                        id = "assistant-1",
                        speaker = ConversationSpeaker.ASSISTANT,
                        text = "Hello",
                        turnId = "turn-1",
                        itemId = "assistant-1",
                        isStreaming = true,
                        orderIndex = 0L,
                    ),
                ),
            ),
        )
        seedThreads(
            service = service,
            snapshots = listOf(
                staleSnapshot.copy(
                    preview = "Hello world",
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            timelineItem(
                                id = "assistant-1",
                                speaker = ConversationSpeaker.ASSISTANT,
                                text = "Hello world",
                                turnId = "turn-1",
                                itemId = "assistant-1",
                                isStreaming = true,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        invokePrivateMethod(
            service,
            "setActiveTurnId",
            "thread-live",
            "turn-1",
        )

        val refreshedSnapshot = invokePrivateMethod(
            service,
            "mergeThreadSnapshotResponse",
            "thread/read",
            "thread-live",
            RpcMessage.response(
                id = null,
                result = buildJsonObject {
                    put(
                        "thread",
                        buildJsonObject {
                            put("id", JsonPrimitive("thread-live"))
                            put("title", JsonPrimitive("Live thread"))
                            put("cwd", JsonPrimitive("/tmp/project-live"))
                            put(
                                "turns",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive("turn-1"))
                                            put("status", JsonPrimitive("in_progress"))
                                            put(
                                                "items",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("id", JsonPrimitive("assistant-1"))
                                                            put("type", JsonPrimitive("agent_message"))
                                                            put("text", JsonPrimitive("Hello"))
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
            ),
            staleSnapshot,
            RemodexThreadSyncState.LIVE,
            true,
        ) as ThreadSyncSnapshot

        val assistantItems = TurnTimelineReducer.reduceProjected(refreshedSnapshot.timelineMutations)
            .filter { item -> item.speaker == ConversationSpeaker.ASSISTANT }
        assertEquals(listOf("Hello world"), assistantItems.map(RemodexConversationItem::text))

        val storedThread = service.threads.value.first { it.id == "thread-live" }
        val storedAssistantText = TurnTimelineReducer.reduceProjected(storedThread.timelineMutations)
            .first { item -> item.id == "assistant-1" }
            .text
        assertEquals("Hello world", storedAssistantText)
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
    fun `assistant delta revives completed thread running state when turn started is missed`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-revive-running",
                    title = "Revived thread",
                    preview = "Previous answer",
                    projectPath = "/tmp/project-revive-running",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = false,
                    activeTurnId = null,
                    latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-revive-running"))
                put("turnId", JsonPrimitive("turn-revive-running"))
                put("delta", JsonPrimitive("Streaming answer resumed"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-revive-running" }
        val assistantMessages = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .filter { item -> item.speaker == ConversationSpeaker.ASSISTANT }

        assertTrue(thread.isRunning)
        assertEquals("turn-revive-running", thread.activeTurnId)
        assertNull(thread.latestTurnTerminalState)
        assertEquals(listOf("Streaming answer resumed"), assistantMessages.map(RemodexConversationItem::text))
        assertTrue(assistantMessages.single().isStreaming)
    }

    @Test
    fun `assistant completion does not shrink local streaming text while turn is still running`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-completion-no-shrink",
                    title = "Completion no shrink thread",
                    preview = "Recovered partial prefix plus newer local tail",
                    projectPath = "/tmp/project-completion-no-shrink",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "assistant-completion-no-shrink",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "Recovered partial prefix plus newer local tail",
                                turnId = "turn-completion-no-shrink",
                                itemId = "assistant-item-completion-no-shrink",
                                isStreaming = true,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        invokePrivateMethod(
            service,
            "setActiveTurnId",
            "thread-completion-no-shrink",
            "turn-completion-no-shrink",
        )

        invokePrivateMethod(
            service,
            "handleAssistantLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-completion-no-shrink"))
                put("turnId", JsonPrimitive("turn-completion-no-shrink"))
            },
            buildJsonObject {
                put("id", JsonPrimitive("assistant-item-completion-no-shrink"))
                put("type", JsonPrimitive("agent_message"))
                put("text", JsonPrimitive("Recovered partial prefix"))
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-completion-no-shrink" }
        val assistant = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .first { item -> item.id == "assistant-completion-no-shrink" }

        assertTrue(thread.isRunning)
        assertEquals("turn-completion-no-shrink", thread.activeTurnId)
        assertNull(thread.latestTurnTerminalState)
        assertEquals("Recovered partial prefix plus newer local tail", assistant.text)
        assertTrue(assistant.isStreaming)
    }

    @Test
    fun `assistant completion fallback does not shrink local streaming text while turn is still running`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-completion-fallback-no-shrink",
                    title = "Completion fallback no shrink thread",
                    preview = "Recovered partial prefix plus newer local tail",
                    projectPath = "/tmp/project-completion-fallback-no-shrink",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "assistant-completion-fallback-no-shrink",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "Recovered partial prefix plus newer local tail",
                                turnId = "turn-completion-fallback-no-shrink",
                                itemId = "assistant-item-completion-fallback-no-shrink",
                                isStreaming = true,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        invokePrivateMethod(
            service,
            "setActiveTurnId",
            "thread-completion-fallback-no-shrink",
            "turn-completion-fallback-no-shrink",
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-completion-fallback-no-shrink"))
                put("turnId", JsonPrimitive("turn-completion-fallback-no-shrink"))
                put("message", JsonPrimitive("Recovered partial prefix"))
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-completion-fallback-no-shrink" }
        val assistant = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .first { item -> item.id == "assistant-completion-fallback-no-shrink" }

        assertTrue(thread.isRunning)
        assertEquals("turn-completion-fallback-no-shrink", thread.activeTurnId)
        assertNull(thread.latestTurnTerminalState)
        assertEquals("Recovered partial prefix plus newer local tail", assistant.text)
        assertTrue(assistant.isStreaming)
    }

    @Test
    fun `completed reasoning item keeps local streaming text and rebinds the provisional row while turn is still running`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-reasoning-completion-no-shrink",
                    title = "Reasoning completion no shrink thread",
                    preview = "Recovered partial prefix plus newer local tail",
                    projectPath = "/tmp/project-reasoning-completion-no-shrink",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "reasoning-turn-reasoning-completion-no-shrink",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.REASONING,
                                text = "Recovered partial prefix plus newer local tail",
                                turnId = "turn-reasoning-completion-no-shrink",
                                itemId = null,
                                isStreaming = true,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        invokePrivateMethod(
            service,
            "setActiveTurnId",
            "thread-reasoning-completion-no-shrink",
            "turn-reasoning-completion-no-shrink",
        )

        invokePrivateMethod(
            service,
            "handleStructuredItemLifecycle",
            buildJsonObject {
                put("id", JsonPrimitive("reasoning-item-completion-no-shrink"))
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("output_text"))
                                put("text", JsonPrimitive("Recovered partial prefix"))
                            },
                        )
                    },
                )
            },
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-reasoning-completion-no-shrink"))
                put("turnId", JsonPrimitive("turn-reasoning-completion-no-shrink"))
            },
            "reasoning",
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-reasoning-completion-no-shrink" }
        val reasoning = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .single { item -> item.kind == ConversationItemKind.REASONING }

        assertEquals("reasoning-turn-reasoning-completion-no-shrink", reasoning.id)
        assertEquals("reasoning-item-completion-no-shrink", reasoning.itemId)
        assertEquals("Recovered partial prefix plus newer local tail", reasoning.text)
        assertTrue(reasoning.isStreaming)
        assertEquals(1, thread.timelineMutations.size)
        assertTrue(thread.timelineMutations.single() is TimelineMutation.Upsert)
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
    fun `assistant delta accepts conversation id legacy envelopes like ios`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-legacy-envelope",
                    title = "Legacy envelope thread",
                    preview = "",
                    projectPath = "/tmp/project-legacy-envelope",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("conversationId", JsonPrimitive("thread-legacy-envelope"))
                put("id", JsonPrimitive("turn-legacy-envelope"))
                put(
                    "msg",
                    buildJsonObject {
                        put("type", JsonPrimitive("agent_message_content_delta"))
                        put("message_id", JsonPrimitive("message-legacy-1"))
                        put("delta", JsonPrimitive("Primo blocco"))
                    },
                )
            },
        )

        val assistantMessages = TurnTimelineReducer.reduceProjected(
            service.threads.value.first { it.id == "thread-legacy-envelope" }.timelineMutations,
        ).filter { item -> item.speaker == ConversationSpeaker.ASSISTANT }

        assertEquals(1, assistantMessages.size)
        assertEquals("turn-legacy-envelope", assistantMessages.single().turnId)
        assertEquals("message-legacy-1", assistantMessages.single().itemId)
        assertEquals("Primo blocco", assistantMessages.single().text)
        assertTrue(assistantMessages.single().isStreaming)
    }

    @Test
    fun `assistant delta falls back to the active thread hint when payload omits thread context`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-active-hint",
                    title = "Active hint thread",
                    preview = "",
                    projectPath = "/tmp/project-active-hint",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
                ThreadSyncSnapshot(
                    id = "thread-other",
                    title = "Other thread",
                    preview = "",
                    projectPath = "/tmp/project-other",
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
            "setActiveTurnId",
            "thread-active-hint",
            "turn-active-hint",
        )
        service.setActiveThreadHint("thread-active-hint")

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("delta", JsonPrimitive("chunk-1"))
            },
        )

        val assistantMessages = TurnTimelineReducer.reduceProjected(
            service.threads.value.first { it.id == "thread-active-hint" }.timelineMutations,
        ).filter { item -> item.speaker == ConversationSpeaker.ASSISTANT }

        assertEquals(1, assistantMessages.size)
        assertEquals("turn-active-hint", assistantMessages.single().turnId)
        assertEquals("chunk-1", assistantMessages.single().text)
        assertTrue(assistantMessages.single().isStreaming)
        assertTrue(
            TurnTimelineReducer.reduceProjected(
                service.threads.value.first { it.id == "thread-other" }.timelineMutations,
            ).none { item -> item.speaker == ConversationSpeaker.ASSISTANT },
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
                        if (it.params?.jsonObjectOrNull?.firstString("archived") == "true") {
                            put("data", buildJsonArray { })
                            return@buildJsonObject
                        }
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
    fun `continue on mac sends desktop handoff request with thread id`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-desktop-handoff",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "desktop/continueOnMac" to {
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
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

            service.continueOnMac("thread-mac-handoff")
            advanceUntilIdle()

            val request = relayFactory.receivedRequests.firstOrNull { it.method == "desktop/continueOnMac" }
            assertNotNull(request)
            assertEquals(
                "thread-mac-handoff",
                request?.params?.jsonObjectOrNull?.firstString("threadId"),
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `continue on mac rejects invalid response payload`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-desktop-invalid-response",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "desktop/continueOnMac" to { buildJsonObject { } },
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

            try {
                service.continueOnMac("thread-mac-handoff")
                fail("Expected invalid response error")
            } catch (error: IllegalStateException) {
                assertEquals("The Mac app did not return a valid response.", error.message)
            }
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `continue on mac maps bridge error codes to user-facing copy`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-desktop-handoff-error",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "desktop/continueOnMac" to {
                    throw RpcError(
                        code = -32000,
                        message = "Mac handoff is only available when the bridge is running on macOS.",
                        data = buildJsonObject {
                            put("errorCode", JsonPrimitive("unsupported_platform"))
                        },
                    )
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

            try {
                service.continueOnMac("thread-mac-handoff")
                fail("Expected mapped desktop handoff error")
            } catch (error: IllegalStateException) {
                assertEquals(
                    "Mac handoff works only when the bridge is running on macOS.",
                    error.message,
                )
            }
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
            val hydratedForkedThread = service.threads.value.firstOrNull { snapshot ->
                snapshot.id == "thread-forked"
            }
            assertNotNull(hydratedForkedThread)
            assertEquals("/tmp/project-forked", hydratedForkedThread?.projectPath)
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
    fun `send prompt confirms optimistic user and creates assistant placeholder when turn start returns turn id like ios`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-send-placeholder",
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
                                        put("id", JsonPrimitive("thread-send"))
                                        put("title", JsonPrimitive("Send thread"))
                                        put("cwd", JsonPrimitive("/tmp/project-send"))
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
                                put("id", JsonPrimitive("thread-send"))
                                put("title", JsonPrimitive("Send thread"))
                                put("cwd", JsonPrimitive("/tmp/project-send"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to {
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-send"))
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

            service.sendPrompt(
                threadId = "thread-send",
                prompt = "Ship the Android fix.",
                runtimeConfig = RemodexRuntimeConfig(),
                attachments = emptyList(),
            )
            advanceUntilIdle()

            val matchingThreads = service.threads.value.filter { it.id == "thread-send" }
            val projectedTimelines = matchingThreads.map { candidate ->
                candidate to TurnTimelineReducer.reduceProjected(candidate.timelineMutations)
            }

            assertTrue("expected thread-send snapshot to exist", matchingThreads.isNotEmpty())
            assertTrue(
                "expected at least one confirmed user row with preserved timestamp after turn/start",
                projectedTimelines.any { (_, items) ->
                    items.any { item ->
                        item.speaker == ConversationSpeaker.USER &&
                            item.deliveryState == RemodexMessageDeliveryState.CONFIRMED &&
                            item.turnId == "turn-send" &&
                            item.createdAtEpochMs != null
                    }
                },
            )
            assertTrue(
                "expected an empty streaming assistant placeholder row for turn-send",
                projectedTimelines.any { (_, items) ->
                    items.any { item ->
                        item.speaker == ConversationSpeaker.ASSISTANT &&
                            item.turnId == "turn-send" &&
                            item.isStreaming &&
                            item.text.isEmpty()
                    }
                },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
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
                        if (it.params?.jsonObjectOrNull?.firstString("archived") == "true") {
                            put("data", buildJsonArray { })
                            return@buildJsonObject
                        }
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
                request = com.emanueledipietro.remodex.model.RemodexCodeReviewRequest(
                    target = com.emanueledipietro.remodex.model.RemodexComposerReviewTarget.UNCOMMITTED_CHANGES,
                ),
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
    fun `hydrate thread requires repeated ambiguous terminal thread read before clearing running fallback`() = runTest {
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
    fun `hydrate thread keeps running when thread status remains active without interruptible turn id`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-thread-status-active",
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
                                        put("id", JsonPrimitive("thread-status-active"))
                                        put("title", JsonPrimitive("Thread status active"))
                                        put("cwd", JsonPrimitive("/tmp/project-thread-status-active"))
                                        put("updatedAt", JsonPrimitive(1_713_222_334))
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
                                put("id", JsonPrimitive("thread-status-active"))
                                put("title", JsonPrimitive("Thread status active"))
                                put("cwd", JsonPrimitive("/tmp/project-thread-status-active"))
                                put(
                                    "status",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("active"))
                                    },
                                )
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-completed"))
                                                put("status", JsonPrimitive("completed"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-active-status"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive("Assistant text is still catching up."))
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

            service.hydrateThread("thread-status-active")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-status-active" }
            assertTrue(thread.isRunning)
            assertNull(thread.latestTurnTerminalState)
            assertNull(thread.activeTurnId)
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
    fun `hydrate thread preserves local streaming assistant text when thread read loses running turn midstream`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-hydrate-streaming-preserve",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val relayFactory = ScriptedRpcRelayWebSocketFactory(
            macDeviceId = payload.macDeviceId,
            macIdentity = macIdentity,
            requestHandlers = mapOf(
                "initialize" to { buildJsonObject { } },
                "thread/read" to {
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-hydrate-streaming-preserve"))
                                put("title", JsonPrimitive("Streaming preserve thread"))
                                put("cwd", JsonPrimitive("/tmp/project-hydrate-streaming-preserve"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-hydrate-streaming-preserve"))
                                                put("status", JsonPrimitive("completed"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-item-streaming-preserve"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive("Recovered partial prefix"))
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

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-hydrate-streaming-preserve",
                    title = "Streaming preserve thread",
                    preview = "Recovered partial prefix plus newer local tail",
                    projectPath = "/tmp/project-hydrate-streaming-preserve",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "assistant-item-streaming-preserve",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "Recovered partial prefix plus newer local tail",
                                turnId = "turn-hydrate-streaming-preserve",
                                itemId = "assistant-item-streaming-preserve",
                                isStreaming = true,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        invokePrivateMethod(
            service,
            "setActiveTurnId",
            "thread-hydrate-streaming-preserve",
            "turn-hydrate-streaming-preserve",
        )

        try {
            coordinator.rememberRelayPairing(payload)
            coordinator.retryConnection()
            awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)

            service.hydrateThread("thread-hydrate-streaming-preserve")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-hydrate-streaming-preserve" }
            val assistant = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
                .first { item -> item.id == "assistant-item-streaming-preserve" }
            assertTrue(thread.isRunning)
            assertEquals("turn-hydrate-streaming-preserve", thread.activeTurnId)
            assertNull(thread.latestTurnTerminalState)
            assertEquals("Recovered partial prefix plus newer local tail", assistant.text)
            assertTrue(assistant.isStreaming)
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
    fun `archive thread keeps optimistic archived state when archive rpc fails`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-archive-thread",
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
                                        put("id", JsonPrimitive("thread-archive"))
                                        put("title", JsonPrimitive("Archive me"))
                                        put("cwd", JsonPrimitive("/tmp/project-archive"))
                                        put("updatedAt", JsonPrimitive(1_713_222_440))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/archive" to {
                    throw RpcError(
                        code = -32600,
                        message = "No rollout found for thread id thread-archive",
                    )
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

            service.archiveThread("thread-archive", unarchive = false)
            advanceUntilIdle()

            val archivedThread = service.threads.value.first { it.id == "thread-archive" }
            assertEquals(RemodexThreadSyncState.ARCHIVED_LOCAL, archivedThread.syncState)
            assertTrue(
                relayFactory.receivedRequests.any { request ->
                    request.method == "thread/archive"
                },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `delete thread keeps local removal when archive rpc fails`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-delete-thread",
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
                                        put("id", JsonPrimitive("thread-delete"))
                                        put("title", JsonPrimitive("Delete me"))
                                        put("cwd", JsonPrimitive("/tmp/project-delete"))
                                        put("updatedAt", JsonPrimitive(1_713_222_440))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/archive" to {
                    throw RpcError(
                        code = -32600,
                        message = "No rollout found for thread id thread-delete",
                    )
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

            service.deleteThread("thread-delete")
            advanceUntilIdle()

            assertTrue(service.threads.value.none { thread -> thread.id == "thread-delete" })
            assertTrue(
                relayFactory.receivedRequests.any { request ->
                    request.method == "thread/archive"
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
                    assertEquals(true, params?.firstBooleanValue("persistExtendedHistory"))
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
    fun `resume thread retries without persist extended history when bridge rejects the field`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-thread-resume-persist-fallback",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var resumeCalls = 0
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
                                        put("id", JsonPrimitive("thread-resume-fallback"))
                                        put("title", JsonPrimitive("Resume fallback target"))
                                        put("cwd", JsonPrimitive("/tmp/project-resume-fallback"))
                                        put("updatedAt", JsonPrimitive(1_713_222_445))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/resume" to { request ->
                    resumeCalls += 1
                    val params = request.params?.jsonObjectOrNull
                    if (resumeCalls == 1) {
                        assertEquals(true, params?.firstBooleanValue("persistExtendedHistory"))
                        throw RpcError(code = -32602, message = "Unknown field persistExtendedHistory")
                    }
                    assertEquals(null, params?.firstBooleanValue("persistExtendedHistory"))
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-resume-fallback"))
                                put("title", JsonPrimitive("Resume fallback target"))
                                put("cwd", JsonPrimitive("/tmp/project-resume-fallback"))
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
            awaitThreads(service, expectedCount = 1)

            service.resumeThread(
                threadId = "thread-resume-fallback",
                preferredProjectPath = "/tmp/project-resume-fallback",
                modelIdentifier = null,
            )
            advanceUntilIdle()

            assertEquals(2, resumeCalls)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `create thread retries without persist extended history when bridge rejects the field`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-thread-start-persist-fallback",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var startCalls = 0
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
                "thread/start" to { request ->
                    startCalls += 1
                    val params = request.params?.jsonObjectOrNull
                    if (startCalls == 1) {
                        assertEquals(true, params?.firstBooleanValue("persistExtendedHistory"))
                        throw RpcError(code = -32602, message = "Unknown field persistExtendedHistory")
                    }
                    assertEquals(null, params?.firstBooleanValue("persistExtendedHistory"))
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-start-fallback"))
                                put("title", JsonPrimitive("Start fallback target"))
                                put("cwd", JsonPrimitive("/tmp/project-start-fallback"))
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

            val created = service.createThread(
                preferredProjectPath = "/tmp/project-start-fallback",
                runtimeDefaults = RemodexRuntimeDefaults(),
            )
            advanceUntilIdle()

            assertNotNull(created)
            assertTrue(startCalls >= 2)
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
                    assertEquals(true, message.params?.jsonObjectOrNull?.firstBooleanValue("persistExtendedHistory"))
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
            assertTrue(service.supportsThreadFork.value)
            assertEquals(
                "Update Remodex on your Mac to use Speed controls",
                service.bridgeUpdatePrompt.value?.title,
            )
            assertEquals(
                "This Mac bridge does not support the selected speed setting yet. Update the Remodex npm package to use Fast Mode and other speed controls.",
                service.bridgeUpdatePrompt.value?.message,
            )
            assertEquals(remodexBridgeUpdateCommand, service.bridgeUpdatePrompt.value?.command)

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
    fun `fork unsupported emits upgrade prompt and disables capability`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-fork-unsupported",
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
                                        put("id", JsonPrimitive("thread-source"))
                                        put("title", JsonPrimitive("Source thread"))
                                        put("cwd", JsonPrimitive("/tmp/project-source"))
                                    },
                                )
                            },
                        )
                    }
                },
                "thread/fork" to {
                    throw RpcError(code = -32601, message = "Method not found: thread/fork")
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

            runCatching {
                service.forkThreadIntoProjectPath(
                    threadId = "thread-source",
                    projectPath = "/tmp/project-forked",
                )
            }.onSuccess {
                fail("Expected thread/fork to fail")
            }

            assertFalse(service.supportsThreadFork.value)
            assertEquals("Update Remodex on your Mac to use /fork", service.bridgeUpdatePrompt.value?.title)
            assertEquals(
                "This Mac bridge does not support native conversation forks yet. Update the Remodex npm package to use /fork and worktree fork flows.",
                service.bridgeUpdatePrompt.value?.message,
            )
            assertEquals(remodexBridgeUpdateCommand, service.bridgeUpdatePrompt.value?.command)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `fork override fallback retries minimal params without disabling capability`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-fork-override-fallback",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val capturedForkParamKeys = mutableListOf<Set<String>>()
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
                    val params = message.params?.jsonObjectOrNull ?: buildJsonObject { }
                    capturedForkParamKeys += params.keys
                    if ("cwd" in params) {
                        throw RpcError(code = -32602, message = "Unknown field cwd")
                    }
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
                "thread/read" to {
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
            assertEquals(3, capturedForkParamKeys.size)
            assertTrue(capturedForkParamKeys.dropLast(1).any { keys -> "cwd" in keys })
            assertEquals(setOf("threadId", "persistExtendedHistory", "approvalPolicy"), capturedForkParamKeys.last())
            assertTrue(service.supportsThreadFork.value)
            assertNull(service.bridgeUpdatePrompt.value)
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
    fun `send prompt encodes plan collaboration mode using object payload like ios`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-plan-mode",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var capturedCollaborationMode: JsonObject? = null
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
                                put("id", JsonPrimitive("thread-plan-mode"))
                                put("title", JsonPrimitive("Plan thread"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to { message ->
                    capturedCollaborationMode = message.params
                        ?.jsonObjectOrNull
                        ?.get("collaborationMode")
                        ?.jsonObjectOrNull
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-plan-mode"))
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
                threadId = "thread-plan-mode",
                prompt = "Plan the Android fix.",
                runtimeConfig = RemodexRuntimeConfig(
                    planningMode = RemodexPlanningMode.PLAN,
                    selectedModelId = "gpt-5.4",
                    reasoningEffort = "high",
                ),
                attachments = emptyList(),
            )
            advanceUntilIdle()

            assertEquals("plan", capturedCollaborationMode?.firstString("mode"))
            val settings = capturedCollaborationMode
                ?.get("settings")
                ?.jsonObjectOrNull
            assertEquals(
                "gpt-5.4",
                settings?.firstString("model"),
            )
            assertEquals(
                "high",
                settings?.firstString("reasoning_effort"),
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `send prompt encodes default collaboration mode when follow up exits plan mode`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-default-mode",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var capturedCollaborationMode: JsonObject? = null
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
                                put("id", JsonPrimitive("thread-default-mode"))
                                put("title", JsonPrimitive("Default thread"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to { message ->
                    capturedCollaborationMode = message.params
                        ?.jsonObjectOrNull
                        ?.get("collaborationMode")
                        ?.jsonObjectOrNull
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-default-mode"))
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
                threadId = "thread-default-mode",
                prompt = "Implement plan",
                runtimeConfig = RemodexRuntimeConfig(
                    planningMode = RemodexPlanningMode.AUTO,
                    selectedModelId = "gpt-5.4",
                    reasoningEffort = "low",
                ),
                attachments = emptyList(),
            )
            advanceUntilIdle()

            assertEquals("default", capturedCollaborationMode?.firstString("mode"))
            val settings = capturedCollaborationMode
                ?.get("settings")
                ?.jsonObjectOrNull
            assertEquals("gpt-5.4", settings?.firstString("model"))
            assertEquals("low", settings?.firstString("reasoning_effort"))
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `send prompt retries without collaboration mode when runtime rejects field`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-plan-mode-fallback",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        val capturedModes = mutableListOf<JsonObject?>()
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
                                put("id", JsonPrimitive("thread-plan-mode-fallback"))
                                put("title", JsonPrimitive("Plan fallback thread"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to { message ->
                    capturedModes += message.params
                        ?.jsonObjectOrNull
                        ?.get("collaborationMode")
                        ?.jsonObjectOrNull
                    if (capturedModes.size == 1) {
                        throw RpcError(
                            code = -32600,
                            message = "Invalid request: invalid type for turn/start.collaborationMode, expected struct CollaborationMode",
                        )
                    }
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-plan-mode-fallback"))
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
                threadId = "thread-plan-mode-fallback",
                prompt = "Retry without plan payload if needed.",
                runtimeConfig = RemodexRuntimeConfig(
                    planningMode = RemodexPlanningMode.PLAN,
                    selectedModelId = "gpt-5.4",
                    reasoningEffort = "high",
                ),
                attachments = emptyList(),
            )
            advanceUntilIdle()

            assertEquals(2, capturedModes.size)
            assertEquals("plan", capturedModes.first()?.firstString("mode"))
            assertNull(capturedModes.last())
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `structured user input response matches ios answer envelope`() {
        val response = buildStructuredUserInputResponse(
            answersByQuestionId = mapOf(
                "project" to listOf("android"),
                "scope" to listOf("ui", "interaction"),
            ),
        )

        val answersObject = response["answers"]?.jsonObjectOrNull
        assertEquals(
            listOf("android"),
            answersObject?.get("project")?.jsonObjectOrNull?.firstArray("answers")
                ?.mapNotNull { value -> value.jsonPrimitive.contentOrNull },
        )
        assertEquals(
            listOf("ui", "interaction"),
            answersObject?.get("scope")?.jsonObjectOrNull?.firstArray("answers")
                ?.mapNotNull { value -> value.jsonPrimitive.contentOrNull },
        )
    }

    @Test
    fun `plan item text keeps the full body instead of collapsing to summary`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
            scope = this,
        )
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        val decoded = invokePrivateMethod(
            service,
            "decodePlanItemText",
            buildJsonObject {
                put("summary", JsonPrimitive("Short summary"))
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("output_text"))
                                put("text", JsonPrimitive("# Full Plan\n\n- Step 1\n- Step 2"))
                            },
                        )
                    },
                )
            },
        ) as String

        assertEquals("# Full Plan\n\n- Step 1\n- Step 2", decoded)
    }

    @Test
    fun `item plan delta after turn plan update stays separate from checklist plan updates`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-plan-live",
                    title = "Plan live",
                    preview = "",
                    projectPath = "/tmp/project-plan-live",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleTurnPlanUpdatedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-plan-live"))
                put("turnId", JsonPrimitive("turn-plan-live"))
                put("explanation", JsonPrimitive("Work through the rollout safely."))
                put(
                    "plan",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("step", JsonPrimitive("Audit current flow"))
                                put("status", JsonPrimitive("inProgress"))
                            },
                        )
                        add(
                            buildJsonObject {
                                put("step", JsonPrimitive("Implement Android fix"))
                                put("status", JsonPrimitive("pending"))
                            },
                        )
                    },
                )
            },
        )
        invokePrivateMethod(
            service,
            "appendPlanDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-plan-live"))
                put("turnId", JsonPrimitive("turn-plan-live"))
                put("itemId", JsonPrimitive("plan-item-1"))
                put("delta", JsonPrimitive("# Final plan"))
            },
        )
        invokePrivateMethod(
            service,
            "appendPlanDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-plan-live"))
                put("turnId", JsonPrimitive("turn-plan-live"))
                put("itemId", JsonPrimitive("plan-item-1"))
                put("delta", JsonPrimitive("\n- Audit current flow\n- Implement Android fix"))
            },
        )

        val items = TurnTimelineReducer.reduceProjected(service.threads.value.single().timelineMutations)
        val planUpdateItems = items.filter { item -> item.kind == ConversationItemKind.PLAN_UPDATE }
        val planItems = items.filter { item -> item.kind == ConversationItemKind.PLAN }

        assertEquals(1, planUpdateItems.size)
        assertEquals("planupdate-turn-plan-live", planUpdateItems.single().id)
        assertEquals("Work through the rollout safely.", planUpdateItems.single().text)
        assertEquals(
            listOf("Audit current flow", "Implement Android fix"),
            planUpdateItems.single().planState?.steps?.map { step -> step.step },
        )
        assertEquals(1, planItems.size)
        assertEquals("plan-item-1", planItems.single().id)
        assertEquals("plan-item-1", planItems.single().itemId)
        assertEquals("# Final plan\n- Audit current flow\n- Implement Android fix", planItems.single().text)
        assertNull(planItems.single().planState)
    }

    @Test
    fun `completed plan lifecycle after turn plan update keeps checklist and proposed plan rows separate`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-plan-complete",
                    title = "Plan complete",
                    preview = "",
                    projectPath = "/tmp/project-plan-complete",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleTurnPlanUpdatedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-plan-complete"))
                put("turnId", JsonPrimitive("turn-plan-complete"))
                put("explanation", JsonPrimitive("Ship the Android fix safely."))
                put(
                    "plan",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("step", JsonPrimitive("Align identifiers"))
                                put("status", JsonPrimitive("completed"))
                            },
                        )
                        add(
                            buildJsonObject {
                                put("step", JsonPrimitive("Verify focused tests"))
                                put("status", JsonPrimitive("completed"))
                            },
                        )
                    },
                )
            },
        )
        invokePrivateMethod(
            service,
            "handleStructuredItemLifecycle",
            buildJsonObject {
                put("id", JsonPrimitive("plan-item-complete"))
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("output_text"))
                                put("text", JsonPrimitive("# Final plan\n- Align identifiers\n- Verify focused tests"))
                            },
                        )
                    },
                )
                put("explanation", JsonPrimitive("Ship the Android fix safely."))
                put(
                    "plan",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("step", JsonPrimitive("Align identifiers"))
                                put("status", JsonPrimitive("completed"))
                            },
                        )
                        add(
                            buildJsonObject {
                                put("step", JsonPrimitive("Verify focused tests"))
                                put("status", JsonPrimitive("completed"))
                            },
                        )
                    },
                )
            },
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-plan-complete"))
                put("turnId", JsonPrimitive("turn-plan-complete"))
            },
            "plan",
            true,
        )

        val items = TurnTimelineReducer.reduceProjected(service.threads.value.single().timelineMutations)
        val planUpdateItems = items.filter { item -> item.kind == ConversationItemKind.PLAN_UPDATE }
        val planItems = items.filter { item -> item.kind == ConversationItemKind.PLAN }

        assertEquals(1, planUpdateItems.size)
        assertEquals(
            listOf("Align identifiers", "Verify focused tests"),
            planUpdateItems.single().planState?.steps?.map { step -> step.step },
        )
        assertEquals(1, planItems.size)
        assertEquals("plan-item-complete", planItems.single().id)
        assertEquals("plan-item-complete", planItems.single().itemId)
        assertFalse(planItems.single().isStreaming)
        assertEquals("# Final plan\n- Align identifiers\n- Verify focused tests", planItems.single().text)
        assertEquals(
            listOf("Align identifiers", "Verify focused tests"),
            planItems.single().planState?.steps?.map { step -> step.step },
        )
    }

    @Test
    fun `plan delta does not overwrite a non plan row that happens to share the item id`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-plan-collision",
                    title = "Plan collision",
                    preview = "",
                    projectPath = "/tmp/project-plan-collision",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            timelineItem(
                                id = "prompt-shared",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.USER_INPUT_PROMPT,
                                text = "Need one more answer before planning.",
                                turnId = "turn-plan-collision",
                                itemId = "shared-item-id",
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendPlanDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-plan-collision"))
                put("turnId", JsonPrimitive("turn-plan-collision"))
                put("itemId", JsonPrimitive("shared-item-id"))
                put("delta", JsonPrimitive("# Final plan"))
            },
        )

        val items = TurnTimelineReducer.reduceProjected(service.threads.value.single().timelineMutations)
        val promptItem = items.firstOrNull { item -> item.kind == ConversationItemKind.USER_INPUT_PROMPT }
        val planItem = items.firstOrNull { item -> item.kind == ConversationItemKind.PLAN }

        assertEquals(
            listOf(ConversationItemKind.PLAN, ConversationItemKind.USER_INPUT_PROMPT),
            items.map(RemodexConversationItem::kind),
        )
        assertEquals("Need one more answer before planning.", promptItem?.text)
        assertEquals("prompt-shared", promptItem?.id)
        assertEquals("# Final plan", planItem?.text)
        assertEquals("shared-item-id", planItem?.itemId)
    }

    @Test
    fun `structured user input request is inserted immediately and keeps blank header questions`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-plan-request",
                    title = "Plan request",
                    preview = "",
                    projectPath = "/tmp/project-plan-request",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )
        advanceUntilIdle()

        invokePrivateMethod(
            service,
            "handleStructuredUserInputRequest",
            JsonPrimitive("request-plan-1"),
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-plan-request"))
                put("turnId", JsonPrimitive("turn-plan-request"))
                put("itemId", JsonPrimitive("item-plan-request"))
                put(
                    "questions",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", JsonPrimitive("direction"))
                                put("header", JsonPrimitive(""))
                                put("question", JsonPrimitive("Which path should we take?"))
                                put("isOther", JsonPrimitive(false))
                                put("isSecret", JsonPrimitive(false))
                                put(
                                    "options",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("label", JsonPrimitive("Ship it"))
                                                put("description", JsonPrimitive("Build the fastest version"))
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

        val items = TurnTimelineReducer.reduceProjected(service.threads.value.single().timelineMutations)
        val prompt = items.singleOrNull { item -> item.kind == ConversationItemKind.USER_INPUT_PROMPT }
        assertNotNull(prompt)
        assertEquals(JsonPrimitive("request-plan-1"), prompt?.structuredUserInputRequest?.requestId)
        assertEquals("", prompt?.structuredUserInputRequest?.questions?.singleOrNull()?.header)
        assertEquals("Which path should we take?", prompt?.structuredUserInputRequest?.questions?.singleOrNull()?.question)
    }

    @Test
    fun `server request resolved notification keeps structured user input prompt as collapsed summary`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-plan-resolved",
                    title = "Plan resolved",
                    preview = "",
                    projectPath = "/tmp/project-plan-resolved",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            timelineItem(
                                id = "item-plan-request",
                                speaker = ConversationSpeaker.SYSTEM,
                                text = "Which path should we take?",
                                kind = ConversationItemKind.USER_INPUT_PROMPT,
                                turnId = "turn-plan-request",
                                itemId = "item-plan-request",
                                structuredUserInputRequest = RemodexStructuredUserInputRequest(
                                    requestId = JsonPrimitive("request-plan-1"),
                                    questions = listOf(
                                        com.emanueledipietro.remodex.model.RemodexStructuredUserInputQuestion(
                                            id = "direction",
                                            header = "",
                                            question = "Which path should we take?",
                                            options = emptyList(),
                                        ),
                                    ),
                                ),
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        invokePrivateMethod(
            service,
            "handleServerRequestResolvedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-plan-resolved"))
                put("requestId", JsonPrimitive("request-plan-1"))
            },
        )

        val items = TurnTimelineReducer.reduceProjected(service.threads.value.single().timelineMutations)
        val prompt = items.singleOrNull { item -> item.kind == ConversationItemKind.USER_INPUT_PROMPT }
        assertNotNull(prompt)
        assertEquals(false, prompt?.isStreaming)
        assertEquals("Asked 1 question", prompt?.text)
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
    fun `attachment only user lifecycle event confirms optimistic message instead of dropping to history placeholder`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-image-lifecycle",
                    title = "Image lifecycle",
                    preview = "Shared 1 image from Android.",
                    projectPath = "/tmp/project-image-lifecycle",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            timelineItem(
                                id = "user-local-image",
                                speaker = ConversationSpeaker.USER,
                                text = "Shared 1 image from Android.",
                                deliveryState = RemodexMessageDeliveryState.PENDING,
                                attachments = listOf(
                                    RemodexConversationAttachment(
                                        id = "local-attachment",
                                        uriString = "content://media/external/images/media/1",
                                        displayName = "1000012658.jpg",
                                        previewDataUrl = "data:image/jpeg;base64,LOCAL",
                                    ),
                                ),
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleUserMessageLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-image-lifecycle"))
                put("turnId", JsonPrimitive("turn-image-lifecycle"))
            },
            buildJsonObject {
                put("id", JsonPrimitive("item-user-image"))
                put("type", JsonPrimitive("user_message"))
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("image"))
                                put("url", JsonPrimitive("remodex://history-image-elided"))
                            },
                        )
                    },
                )
            },
        )

        val items = TurnTimelineReducer.reduceProjected(service.threads.value.single().timelineMutations)
        assertEquals(1, items.size)
        assertEquals("user-local-image", items.single().id)
        assertEquals(RemodexMessageDeliveryState.CONFIRMED, items.single().deliveryState)
        assertEquals("turn-image-lifecycle", items.single().turnId)
        assertEquals("data:image/jpeg;base64,LOCAL", items.single().attachments.single().previewDataUrl)
    }

    @Test
    fun `user lifecycle preserves optimistic timestamp when confirming local message`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-user-time",
                    title = "User timestamp",
                    preview = "hello",
                    projectPath = "/tmp/project-user-time",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            timelineItem(
                                id = "user-local-time",
                                speaker = ConversationSpeaker.USER,
                                text = "hello",
                                deliveryState = RemodexMessageDeliveryState.PENDING,
                                createdAtEpochMs = 1234L,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleUserMessageLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-user-time"))
                put("turnId", JsonPrimitive("turn-user-time"))
            },
            buildJsonObject {
                put("id", JsonPrimitive("item-user-time"))
                put("type", JsonPrimitive("user_message"))
                put("text", JsonPrimitive("hello"))
            },
        )

        val items = TurnTimelineReducer.reduceProjected(service.threads.value.single().timelineMutations)
        assertEquals(1, items.size)
        assertEquals(RemodexMessageDeliveryState.CONFIRMED, items.single().deliveryState)
        assertEquals("turn-user-time", items.single().turnId)
        assertEquals(1234L, items.single().createdAtEpochMs)
    }

    @Test
    fun `attachment only user lifecycle keeps local attachment metadata when incoming image is inline data url`() = runTest {
        val coordinator = SecureConnectionCoordinator(
            store = InMemorySecureStore(),
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-image-inline-lifecycle",
                    title = "Inline image lifecycle",
                    preview = "Shared 1 image from Android.",
                    projectPath = "/tmp/project-image-inline-lifecycle",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            timelineItem(
                                id = "user-local-inline-image",
                                speaker = ConversationSpeaker.USER,
                                text = "Shared 1 image from Android.",
                                deliveryState = RemodexMessageDeliveryState.PENDING,
                                attachments = listOf(
                                    RemodexConversationAttachment(
                                        id = "local-attachment",
                                        uriString = "content://media/external/images/media/1",
                                        displayName = "1000012658.jpg",
                                        previewDataUrl = "data:image/jpeg;base64,LOCAL",
                                    ),
                                ),
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleUserMessageLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-image-inline-lifecycle"))
                put("turnId", JsonPrimitive("turn-image-inline-lifecycle"))
            },
            buildJsonObject {
                put("id", JsonPrimitive("item-user-inline-image"))
                put("type", JsonPrimitive("user_message"))
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("image"))
                                put("url", JsonPrimitive("data:image/jpeg;base64,/9k="))
                            },
                        )
                    },
                )
            },
        )

        val items = TurnTimelineReducer.reduceProjected(service.threads.value.single().timelineMutations)
        assertEquals(1, items.size)
        assertEquals("user-local-inline-image", items.single().id)
        assertEquals(RemodexMessageDeliveryState.CONFIRMED, items.single().deliveryState)
        assertEquals("turn-image-inline-lifecycle", items.single().turnId)
        assertEquals("1000012658.jpg", items.single().attachments.single().displayName)
        assertEquals("data:image/jpeg;base64,LOCAL", items.single().attachments.single().previewDataUrl)
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
                                                                put("source", JsonPrimitive("userShell"))
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
            assertEquals(RemodexCommandExecutionLiveStatus.COMPLETED, details?.liveStatus)
            assertEquals(RemodexCommandExecutionSource.USER_SHELL, details?.source)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread preserves running command execution details when history status is in progress`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-command-details-running",
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
                                            put("id", JsonPrimitive("thread-command-running"))
                                            put("title", JsonPrimitive("Running command thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-command-running"))
                                            put("updatedAt", JsonPrimitive(1_713_222_223))
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
                                put("id", JsonPrimitive("thread-command-running"))
                                put("title", JsonPrimitive("Running command thread"))
                                put("cwd", JsonPrimitive("/tmp/project-command-running"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-command-running"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("command-item-running"))
                                                                put("type", JsonPrimitive("command_execution"))
                                                                put("status", JsonPrimitive("in_progress"))
                                                                put("command", JsonPrimitive("bash -lc \"sleep 30\""))
                                                                put("cwd", JsonPrimitive("/tmp/project-command-running"))
                                                                put("source", JsonPrimitive("unifiedExecStartup"))
                                                                put("output", JsonPrimitive("tick 1\ntick 2"))
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
            service.hydrateThread("thread-command-running")
            advanceUntilIdle()

            val details = service.commandExecutionDetails.value["command-item-running"]
            assertNotNull(details)
            assertEquals("bash -lc \"sleep 30\"", details?.fullCommand)
            assertEquals("tick 1\ntick 2", details?.outputTail)
            assertEquals(RemodexCommandExecutionLiveStatus.RUNNING, details?.liveStatus)
            assertEquals(RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP, details?.source)
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
    fun `command status refresh keeps later command updates after the assistant reply in interleaved turns`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
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
                    id = "thread-command-refresh",
                    title = "Command refresh thread",
                    preview = "First response",
                    projectPath = "/tmp/project-command-refresh",
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
                                text = "Inspecting the repository",
                                turnId = "turn-command-refresh",
                                itemId = "thinking-1",
                                isStreaming = false,
                                orderIndex = 0L,
                            ),
                        ),
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "command-item-1",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "running git status --short",
                                turnId = "turn-command-refresh",
                                itemId = "command-item-1",
                                isStreaming = true,
                                orderIndex = 1L,
                            ),
                        ),
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "assistant-1",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "First response",
                                turnId = "turn-command-refresh",
                                itemId = "assistant-item-1",
                                isStreaming = true,
                                orderIndex = 2L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleCommandExecutionTerminalInteraction",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-command-refresh"))
                put("turnId", JsonPrimitive("turn-command-refresh"))
                put("itemId", JsonPrimitive("command-item-1"))
                put("status", JsonPrimitive("completed"))
                put("command", JsonPrimitive("git status --short"))
                put("cwd", JsonPrimitive("/tmp/project-command-refresh"))
                put("exitCode", JsonPrimitive(0))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-command-refresh" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)

        assertEquals(
            listOf("thinking-1", "assistant-1", "command-item-1"),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals(
            "completed git status --short",
            projected.last().text,
        )
    }

    @Test
    fun `terminal interaction keeps background command running details even after row completed`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-background-terminal",
                    title = "Background terminal thread",
                    preview = "completed sleep 30",
                    projectPath = "/tmp/project-background-terminal",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "command-item",
                                itemId = "command-item",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "completed sleep 30",
                                turnId = "turn-background-terminal",
                                isStreaming = false,
                                orderIndex = 1L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        setCommandExecutionDetails(
            service = service,
            detailsByItemId = mapOf(
                "command-item" to RemodexCommandExecutionDetails(
                    fullCommand = "bash -lc \"sleep 30\"",
                    cwd = "/tmp/project-background-terminal",
                    exitCode = 0,
                    outputTail = "done",
                    liveStatus = RemodexCommandExecutionLiveStatus.COMPLETED,
                    source = RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP,
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleCommandExecutionTerminalInteraction",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-background-terminal"))
                put("turnId", JsonPrimitive("turn-background-terminal"))
                put("itemId", JsonPrimitive("command-item"))
                put("status", JsonPrimitive("running"))
                put("command", JsonPrimitive("bash -lc \"sleep 30\""))
                put("cwd", JsonPrimitive("/tmp/project-background-terminal"))
            },
        )

        val details = service.commandExecutionDetails.value["command-item"]
        val thread = service.threads.value.first { it.id == "thread-background-terminal" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)

        assertEquals(RemodexCommandExecutionLiveStatus.RUNNING, details?.liveStatus)
        assertEquals("completed sleep 30", projected.single().text)
        assertFalse(projected.single().isStreaming)
    }

    @Test
    fun `terminal interaction appends waited background terminal history row`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-background-wait",
                    title = "Background wait thread",
                    preview = "sleep 30",
                    projectPath = "/tmp/project-background-wait",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "command-item",
                                itemId = "command-item",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "completed sleep 30",
                                turnId = "turn-background-wait",
                                isStreaming = false,
                                orderIndex = 1L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        setCommandExecutionDetails(
            service = service,
            detailsByItemId = mapOf(
                "command-item" to RemodexCommandExecutionDetails(
                    fullCommand = "bash -lc \"sleep 30\"",
                    liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                    source = RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP,
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleCommandExecutionTerminalInteraction",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-background-wait"))
                put("turnId", JsonPrimitive("turn-background-wait"))
                put("itemId", JsonPrimitive("command-item"))
                put("processId", JsonPrimitive("process-background-wait"))
                put("stdin", JsonPrimitive(""))
                put("status", JsonPrimitive("running"))
                put("command", JsonPrimitive("bash -lc \"sleep 30\""))
            },
        )

        val projected = TurnTimelineReducer.reduceProjected(
            service.threads.value.first { it.id == "thread-background-wait" }.timelineMutations,
        )

        assertEquals(listOf("completed sleep 30", "Waited for background terminal"), projected.map(RemodexConversationItem::text))
        assertEquals("sleep 30", projected.last().supportingText)
    }

    @Test
    fun `terminal interaction reuses startup item id when process matches running background terminal`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-background-process",
                    title = "Background process thread",
                    preview = "sleep 30",
                    projectPath = "/tmp/project-background-process",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "startup-item",
                                itemId = "startup-item",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "running sleep 30",
                                turnId = "turn-background-process",
                                isStreaming = false,
                                orderIndex = 1L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        setCommandExecutionDetails(
            service = service,
            detailsByItemId = mapOf(
                "startup-item" to RemodexCommandExecutionDetails(
                    fullCommand = "bash -lc \"sleep 30\"",
                    liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                    source = RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP,
                    processId = "process-background-process",
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleCommandExecutionTerminalInteraction",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-background-process"))
                put("turnId", JsonPrimitive("turn-background-process"))
                put("itemId", JsonPrimitive("interaction-item"))
                put("processId", JsonPrimitive("process-background-process"))
                put("stdin", JsonPrimitive(""))
            },
        )

        val projected = TurnTimelineReducer.reduceProjected(
            service.threads.value.first { it.id == "thread-background-process" }.timelineMutations,
        )

        assertEquals(
            listOf("running sleep 30", "Waited for background terminal"),
            projected.map(RemodexConversationItem::text),
        )
        assertEquals("startup-item", projected.last().itemId)
    }

    @Test
    fun `terminal interaction appends interacted background terminal history row`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-background-interaction",
                    title = "Background interaction thread",
                    preview = "sleep 30",
                    projectPath = "/tmp/project-background-interaction",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "command-item",
                                itemId = "command-item",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "completed sleep 30",
                                turnId = "turn-background-interaction",
                                isStreaming = false,
                                orderIndex = 1L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        setCommandExecutionDetails(
            service = service,
            detailsByItemId = mapOf(
                "command-item" to RemodexCommandExecutionDetails(
                    fullCommand = "bash -lc \"sleep 30\"",
                    liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                    source = RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP,
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleCommandExecutionTerminalInteraction",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-background-interaction"))
                put("turnId", JsonPrimitive("turn-background-interaction"))
                put("itemId", JsonPrimitive("command-item"))
                put("processId", JsonPrimitive("process-background-interaction"))
                put("stdin", JsonPrimitive("echo hello"))
                put("status", JsonPrimitive("running"))
                put("command", JsonPrimitive("bash -lc \"sleep 30\""))
            },
        )

        val projected = TurnTimelineReducer.reduceProjected(
            service.threads.value.first { it.id == "thread-background-interaction" }.timelineMutations,
        )

        assertEquals(
            listOf("completed sleep 30", "Interacted with background terminal"),
            projected.map(RemodexConversationItem::text),
        )
        assertEquals("sleep 30", projected.last().supportingText)
    }

    @Test
    fun `terminal interaction does not append background history row for foreground commands`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-foreground-command",
                    title = "Foreground command thread",
                    preview = "pwd",
                    projectPath = "/tmp/project-foreground-command",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "command-item",
                                itemId = "command-item",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "running pwd",
                                turnId = "turn-foreground-command",
                                isStreaming = true,
                                orderIndex = 1L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        setCommandExecutionDetails(
            service = service,
            detailsByItemId = mapOf(
                "command-item" to RemodexCommandExecutionDetails(
                    fullCommand = "pwd",
                    liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                    source = RemodexCommandExecutionSource.USER_SHELL,
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleCommandExecutionTerminalInteraction",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-foreground-command"))
                put("turnId", JsonPrimitive("turn-foreground-command"))
                put("itemId", JsonPrimitive("command-item"))
                put("processId", JsonPrimitive("process-foreground-command"))
                put("stdin", JsonPrimitive(""))
                put("status", JsonPrimitive("running"))
                put("command", JsonPrimitive("pwd"))
            },
        )

        val projected = TurnTimelineReducer.reduceProjected(
            service.threads.value.first { it.id == "thread-foreground-command" }.timelineMutations,
        )

        assertEquals(listOf("running pwd"), projected.map(RemodexConversationItem::text))
    }

    @Test
    fun `command status rebind keeps the same row when item id arrives after turn scoped placeholder`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-command-rebind",
                    title = "Command rebind thread",
                    preview = "First response",
                    projectPath = "/tmp/project-command-rebind",
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
                                text = "Inspecting the repository",
                                turnId = "turn-command-rebind",
                                itemId = "thinking-1",
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
                                turnId = "turn-command-rebind",
                                itemId = "assistant-item-1",
                                isStreaming = true,
                                orderIndex = 1L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendCommandExecutionDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-command-rebind"))
                put("turnId", JsonPrimitive("turn-command-rebind"))
                put("status", JsonPrimitive("running"))
                put("command", JsonPrimitive("git status --short"))
            },
        )

        invokePrivateMethod(
            service,
            "handleCommandExecutionTerminalInteraction",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-command-rebind"))
                put("turnId", JsonPrimitive("turn-command-rebind"))
                put("itemId", JsonPrimitive("command-item-1"))
                put("status", JsonPrimitive("completed"))
                put("command", JsonPrimitive("git status --short"))
                put("cwd", JsonPrimitive("/tmp/project-command-rebind"))
                put("exitCode", JsonPrimitive(0))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-command-rebind" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val commandItems = projected.filter { it.kind == ConversationItemKind.COMMAND_EXECUTION }

        assertEquals(1, commandItems.size)
        assertEquals("command-item-1", commandItems.single().itemId)
        assertEquals(
            listOf("thinking-1", "assistant-1", commandItems.single().id),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals("completed git status --short", commandItems.single().text)
    }

    @Test
    fun `assistant delta rebind keeps the same row when item id arrives after turn scoped placeholder`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-assistant-rebind",
                    title = "Assistant rebind thread",
                    preview = "现在这个文件同时负责环境变量校验",
                    projectPath = "/tmp/project-assistant-rebind",
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
                                text = "Inspecting the repository",
                                turnId = "turn-assistant-rebind",
                                itemId = "thinking-1",
                                isStreaming = false,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-assistant-rebind"))
                put("turnId", JsonPrimitive("turn-assistant-rebind"))
                put("delta", JsonPrimitive("现在这个文件同时负责环境变量校验"))
            },
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-assistant-rebind"))
                put("turnId", JsonPrimitive("turn-assistant-rebind"))
                put("itemId", JsonPrimitive("assistant-item-1"))
                put("delta", JsonPrimitive("环境变量"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-assistant-rebind" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val assistantItems = projected.filter { it.speaker == ConversationSpeaker.ASSISTANT }

        assertEquals(1, assistantItems.size)
        assertEquals("assistant-item-1", assistantItems.single().itemId)
        assertEquals(
            "现在这个文件同时负责环境变量校验环境变量",
            assistantItems.single().text,
        )
        assertEquals(
            listOf("thinking-1", assistantItems.single().id),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals(2, thread.timelineMutations.size)
        assertTrue(thread.timelineMutations.none { mutation ->
            mutation is TimelineMutation.AssistantTextDelta
        })
    }

    @Test
    fun `assistant deltas coalesce into a single streaming upsert mutation`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-assistant-coalesce",
                    title = "Assistant coalescing",
                    preview = "",
                    projectPath = "/tmp/project-assistant-coalesce",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-assistant-coalesce"))
                put("turnId", JsonPrimitive("turn-assistant-coalesce"))
                put("itemId", JsonPrimitive("assistant-item-coalesce"))
                put("delta", JsonPrimitive("```mer"))
            },
        )
        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-assistant-coalesce"))
                put("turnId", JsonPrimitive("turn-assistant-coalesce"))
                put("itemId", JsonPrimitive("assistant-item-coalesce"))
                put("delta", JsonPrimitive("maid\nflowchart LR"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-assistant-coalesce" }
        val assistant = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .single { item -> item.speaker == ConversationSpeaker.ASSISTANT }

        assertEquals("```mermaid\nflowchart LR", assistant.text)
        assertTrue(assistant.isStreaming)
        assertEquals(1, thread.timelineMutations.size)
        assertTrue(thread.timelineMutations.single() is TimelineMutation.Upsert)
        assertTrue(thread.timelineMutations.none { mutation ->
            mutation is TimelineMutation.AssistantTextDelta
        })
    }

    @Test
    fun `reasoning deltas coalesce into a single streaming upsert mutation`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-reasoning-coalesce",
                    title = "Reasoning coalescing",
                    preview = "",
                    projectPath = "/tmp/project-reasoning-coalesce",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendReasoningDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-reasoning-coalesce"))
                put("turnId", JsonPrimitive("turn-reasoning-coalesce"))
                put("itemId", JsonPrimitive("reasoning-item-coalesce"))
                put("delta", JsonPrimitive("```thin"))
            },
        )
        invokePrivateMethod(
            service,
            "appendReasoningDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-reasoning-coalesce"))
                put("turnId", JsonPrimitive("turn-reasoning-coalesce"))
                put("itemId", JsonPrimitive("reasoning-item-coalesce"))
                put("delta", JsonPrimitive("king\nstep"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-reasoning-coalesce" }
        val reasoning = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .single { item -> item.kind == ConversationItemKind.REASONING }

        assertEquals("```thinking\nstep", reasoning.text)
        assertTrue(reasoning.isStreaming)
        assertEquals(1, thread.timelineMutations.size)
        assertTrue(thread.timelineMutations.single() is TimelineMutation.Upsert)
        assertTrue(thread.timelineMutations.none { mutation ->
            mutation is TimelineMutation.ReasoningTextDelta
        })
    }

    @Test
    fun `structured work finalizes the current assistant segment before later assistant deltas`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-assistant-segments",
                    title = "Assistant segments",
                    preview = "",
                    projectPath = "/tmp/project-assistant-segments",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-assistant-segments"))
                put("turnId", JsonPrimitive("turn-assistant-segments"))
                put("delta", JsonPrimitive("First streamed answer block"))
            },
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-assistant-segments"))
                put("turnId", JsonPrimitive("turn-assistant-segments"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("search-item-segment"))
                        put("type", JsonPrimitive("webSearch"))
                        put("query", JsonPrimitive("compose lazycolumn"))
                    },
                )
            },
            false,
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-assistant-segments"))
                put("turnId", JsonPrimitive("turn-assistant-segments"))
                put("delta", JsonPrimitive("Second streamed answer block"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-assistant-segments" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val assistantItems = projected.filter { it.speaker == ConversationSpeaker.ASSISTANT }
        val webSearchItem = projected.single { it.kind == ConversationItemKind.WEB_SEARCH }

        assertEquals(2, assistantItems.size)
        assertEquals("assistant-turn-assistant-segments", assistantItems[0].id)
        assertFalse(assistantItems[0].isStreaming)
        assertEquals("First streamed answer block", assistantItems[0].text)
        assertEquals("assistant-turn-assistant-segments-seg-1", assistantItems[1].id)
        assertTrue(assistantItems[1].isStreaming)
        assertEquals("Second streamed answer block", assistantItems[1].text)
        assertEquals(
            listOf(
                assistantItems[0].id,
                webSearchItem.id,
                assistantItems[1].id,
            ),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `entered review mode finalizes the current assistant segment before later assistant deltas`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-review-segments",
                    title = "Review segments",
                    preview = "",
                    projectPath = "/tmp/project-review-segments",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-review-segments"))
                put("turnId", JsonPrimitive("turn-review-segments"))
                put("delta", JsonPrimitive("First review preface"))
            },
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-review-segments"))
                put("turnId", JsonPrimitive("turn-review-segments"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("review-mode-item"))
                        put("type", JsonPrimitive("enteredReviewMode"))
                        put("review", JsonPrimitive("current changes"))
                    },
                )
            },
            false,
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-review-segments"))
                put("turnId", JsonPrimitive("turn-review-segments"))
                put("delta", JsonPrimitive("Second review finding"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-review-segments" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val assistantItems = projected.filter { it.speaker == ConversationSpeaker.ASSISTANT }
        val reviewModeItem = projected.single {
            it.kind == ConversationItemKind.COMMAND_EXECUTION && it.id == "review-mode-item"
        }

        assertEquals(2, assistantItems.size)
        assertEquals("assistant-turn-review-segments", assistantItems[0].id)
        assertFalse(assistantItems[0].isStreaming)
        assertEquals("First review preface", assistantItems[0].text)
        assertEquals("assistant-turn-review-segments-seg-1", assistantItems[1].id)
        assertTrue(assistantItems[1].isStreaming)
        assertEquals("Second review finding", assistantItems[1].text)
        assertEquals(
            listOf(
                assistantItems[0].id,
                reviewModeItem.id,
                assistantItems[1].id,
            ),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `context compaction finalizes the current assistant segment before later assistant deltas`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-compaction-segments",
                    title = "Compaction segments",
                    preview = "",
                    projectPath = "/tmp/project-compaction-segments",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-compaction-segments"))
                put("turnId", JsonPrimitive("turn-compaction-segments"))
                put("delta", JsonPrimitive("First compaction note"))
            },
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-compaction-segments"))
                put("turnId", JsonPrimitive("turn-compaction-segments"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("compaction-item"))
                        put("type", JsonPrimitive("contextCompaction"))
                    },
                )
            },
            false,
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-compaction-segments"))
                put("turnId", JsonPrimitive("turn-compaction-segments"))
                put("delta", JsonPrimitive("Second compaction note"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-compaction-segments" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val assistantItems = projected.filter { it.speaker == ConversationSpeaker.ASSISTANT }
        val compactionItem = projected.single {
            it.kind == ConversationItemKind.CONTEXT_COMPACTION && it.id == "compaction-item"
        }

        assertEquals(2, assistantItems.size)
        assertEquals("assistant-turn-compaction-segments", assistantItems[0].id)
        assertFalse(assistantItems[0].isStreaming)
        assertEquals("First compaction note", assistantItems[0].text)
        assertEquals("assistant-turn-compaction-segments-seg-1", assistantItems[1].id)
        assertTrue(assistantItems[1].isStreaming)
        assertEquals("Second compaction note", assistantItems[1].text)
        assertEquals(
            listOf(
                assistantItems[0].id,
                compactionItem.id,
                assistantItems[1].id,
            ),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `hydrate thread clears stale active turn when history shows the turn completed`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-idle-catchup",
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
                                            put("id", JsonPrimitive("thread-idle-catchup"))
                                            put("title", JsonPrimitive("Idle catchup thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-idle-catchup"))
                                            put("updatedAt", JsonPrimitive(1_713_222_220))
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
                                put("id", JsonPrimitive("thread-idle-catchup"))
                                put("title", JsonPrimitive("Idle catchup thread"))
                                put("cwd", JsonPrimitive("/tmp/project-idle-catchup"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-idle-catchup"))
                                                put("status", JsonPrimitive("completed"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-item-idle"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive("Finished response"))
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

            invokePrivateMethod(
                service,
                "setActiveTurnId",
                "thread-idle-catchup",
                "turn-idle-catchup",
            )
            service.hydrateThread("thread-idle-catchup")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-idle-catchup" }
            val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            assertFalse(thread.isRunning)
            assertNull(thread.activeTurnId)
            assertEquals(listOf("Finished response"), projected.map(RemodexConversationItem::text))
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `hydrate thread clears stale active turn when thread read reports the turn idle`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-turn-idle-status",
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
                                            put("id", JsonPrimitive("thread-turn-idle-status"))
                                            put("title", JsonPrimitive("Idle turn status thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-turn-idle-status"))
                                            put("updatedAt", JsonPrimitive(1_713_222_420))
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
                                put("id", JsonPrimitive("thread-turn-idle-status"))
                                put("title", JsonPrimitive("Idle turn status thread"))
                                put("cwd", JsonPrimitive("/tmp/project-turn-idle-status"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-turn-idle-status"))
                                                put("status", JsonPrimitive("idle"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-item-turn-idle"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive("Finished response after idle turn status"))
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

            invokePrivateMethod(
                service,
                "setActiveTurnId",
                "thread-turn-idle-status",
                "turn-turn-idle-status",
            )
            service.hydrateThread("thread-turn-idle-status")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-turn-idle-status" }
            val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            assertFalse(thread.isRunning)
            assertNull(thread.activeTurnId)
            assertEquals(
                listOf("Finished response after idle turn status"),
                projected.map(RemodexConversationItem::text),
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `thread status idle preserves local streaming state until catchup confirms completion`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = ScriptedRpcRelayWebSocketFactory(
                macDeviceId = "mac-idle-local-complete",
                macIdentity = macIdentity,
                requestHandlers = emptyMap(),
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
                    id = "thread-idle-retry",
                    title = "Idle retry thread",
                    preview = "Recovered final response",
                    projectPath = "/tmp/project-idle-retry",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "assistant-item-idle-retry",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "Recovered final response",
                                turnId = "turn-idle-retry",
                                itemId = "assistant-item-idle-retry",
                                isStreaming = true,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleThreadStatusChangedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-idle-retry"))
                put("status", JsonPrimitive("idle"))
            },
        )
        advanceUntilIdle()

        val thread = service.threads.value.first { it.id == "thread-idle-retry" }
        val assistant = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .first { item -> item.id == "assistant-item-idle-retry" }
        assertEquals("Recovered final response", assistant.text)
        assertTrue(assistant.isStreaming)
        assertTrue(thread.isRunning)
        assertNull(thread.activeTurnId)
        assertNull(thread.latestTurnTerminalState)
    }

    @Test
    fun `thread status idle keeps active turn visible until completion is confirmed`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-idle-active-turn",
                    title = "Idle active turn thread",
                    preview = "Still running",
                    projectPath = "/tmp/project-idle-active-turn",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )
        invokePrivateMethod(
            service,
            "setActiveTurnId",
            "thread-idle-active-turn",
            "turn-idle-active-turn",
        )

        invokePrivateMethod(
            service,
            "handleThreadStatusChangedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-idle-active-turn"))
                put("status", JsonPrimitive("idle"))
            },
        )
        advanceUntilIdle()

        val thread = service.threads.value.first { it.id == "thread-idle-active-turn" }
        assertTrue(thread.isRunning)
        assertEquals("turn-idle-active-turn", thread.activeTurnId)
        assertNull(thread.latestTurnTerminalState)
    }

    @Test
    fun `thread status idle preserves fallback running state without forcing lifecycle catchup`() = runTest {
        val connected = createConnectedBridgeService()

        try {
            seedThreads(
                service = connected.service,
                snapshots = listOf(
                    ThreadSyncSnapshot(
                        id = "thread-idle-fallback",
                        title = "Idle fallback thread",
                        preview = "Still thinking",
                        projectPath = "/tmp/project-idle-fallback",
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = 0L,
                        isRunning = true,
                        runtimeConfig = RemodexRuntimeConfig(),
                        timelineMutations = emptyList(),
                    ),
                ),
            )
            invokePrivateMethod(
                connected.service,
                "markThreadAsRunningFallback",
                "thread-idle-fallback",
            )
            advanceUntilIdle()

            val baselineThreadListRequests = connected.relayFactory.receivedRequests.count { request ->
                request.method == "thread/list"
            }
            val baselineThreadReadRequests = connected.relayFactory.receivedRequests.count { request ->
                request.method == "thread/read"
            }

            invokePrivateMethod(
                connected.service,
                "handleThreadStatusChangedNotification",
                buildJsonObject {
                    put("threadId", JsonPrimitive("thread-idle-fallback"))
                    put("status", JsonPrimitive("idle"))
                },
            )
            advanceUntilIdle()

            val thread = connected.service.threads.value.first { it.id == "thread-idle-fallback" }
            assertTrue(thread.isRunning)
            assertNull(thread.activeTurnId)
            assertNull(thread.latestTurnTerminalState)
            assertEquals(
                baselineThreadListRequests,
                connected.relayFactory.receivedRequests.count { request -> request.method == "thread/list" },
            )
            assertEquals(
                baselineThreadReadRequests,
                connected.relayFactory.receivedRequests.count { request -> request.method == "thread/read" },
            )
        } finally {
            connected.coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `stop turn keeps running state until follow-up sync confirms completion`() = runTest {
        val connected = createConnectedBridgeService()

        try {
            seedThreads(
                service = connected.service,
                snapshots = listOf(
                    ThreadSyncSnapshot(
                        id = "thread-stop-visible",
                        title = "Stop visible thread",
                        preview = "Stopping soon",
                        projectPath = "/tmp/project-stop-visible",
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = 0L,
                        isRunning = true,
                        runtimeConfig = RemodexRuntimeConfig(),
                        timelineMutations = emptyList(),
                    ),
                ),
            )
            invokePrivateMethod(
                connected.service,
                "setActiveTurnId",
                "thread-stop-visible",
                "turn-stop-visible",
            )

            connected.service.stopTurn("thread-stop-visible")
            advanceUntilIdle()

            val thread = connected.service.threads.value.first { it.id == "thread-stop-visible" }
            assertTrue(thread.isRunning)
            assertEquals("turn-stop-visible", thread.activeTurnId)
            assertTrue(
                connected.relayFactory.receivedRequests.any { request ->
                    request.method == "turn/interrupt"
                },
            )
        } finally {
            connected.coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `steer prompt sends turn steer with the active turn id`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-steer-test",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
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
                "turn/steer" to {
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-live"))
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
            seedThreads(
                service = service,
                snapshots = listOf(
                    ThreadSyncSnapshot(
                        id = "thread-steer",
                        title = "Steer thread",
                        preview = "Running",
                        projectPath = "/tmp/thread-steer",
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = 0L,
                        isRunning = true,
                        runtimeConfig = RemodexRuntimeConfig(),
                        timelineMutations = emptyList(),
                    ),
                ),
            )
            invokePrivateMethod(
                service,
                "setActiveTurnId",
                "thread-steer",
                "turn-live",
            )

            service.steerPrompt(
                threadId = "thread-steer",
                prompt = "Ship the Android queued draft fix.",
                runtimeConfig = RemodexRuntimeConfig(),
                attachments = emptyList(),
            )
            advanceUntilIdle()

            val steerRequest = relayFactory.receivedRequests.last { request -> request.method == "turn/steer" }
            assertEquals("thread-steer", steerRequest.params?.jsonObjectOrNull?.firstString("threadId"))
            assertEquals("turn-live", steerRequest.params?.jsonObjectOrNull?.firstString("expectedTurnId"))
            val projected = TurnTimelineReducer.reduceProjected(
                service.threads.value.first { it.id == "thread-steer" }.timelineMutations,
            )
            assertTrue(
                projected.any { item ->
                    item.speaker == ConversationSpeaker.USER &&
                        item.deliveryState == RemodexMessageDeliveryState.CONFIRMED &&
                        item.turnId == "turn-live" &&
                        item.text.contains("Ship the Android queued draft fix.")
                },
            )
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `steer prompt falls back to turn start when thread read shows no active turn`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-steer-fallback-test",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
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
                                put("id", JsonPrimitive("thread-steer-fallback"))
                                put("turns", buildJsonArray { })
                            },
                        )
                    }
                },
                "turn/start" to {
                    buildJsonObject {
                        put("turnId", JsonPrimitive("turn-new"))
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
            seedThreads(
                service = service,
                snapshots = listOf(
                    ThreadSyncSnapshot(
                        id = "thread-steer-fallback",
                        title = "Steer fallback thread",
                        preview = "Running",
                        projectPath = "/tmp/thread-steer-fallback",
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = 0L,
                        isRunning = true,
                        runtimeConfig = RemodexRuntimeConfig(),
                        timelineMutations = emptyList(),
                    ),
                ),
            )

            service.steerPrompt(
                threadId = "thread-steer-fallback",
                prompt = "Retry this queued draft as a fresh turn.",
                runtimeConfig = RemodexRuntimeConfig(),
                attachments = emptyList(),
            )
            advanceUntilIdle()

            val requestMethods = relayFactory.receivedRequests.map { request -> request.method }
            assertTrue(requestMethods.contains("thread/read"))
            assertTrue(requestMethods.contains("turn/start"))
            assertFalse(requestMethods.contains("turn/steer"))
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `final answer completion alone does not clear running state without terminal lifecycle events`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-final-answer-fallback",
                    title = "Final answer fallback thread",
                    preview = "Working...",
                    projectPath = "/tmp/project-final-answer-fallback",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 0L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = listOf(
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "assistant-item-final",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "Partial",
                                turnId = "turn-final-answer-fallback",
                                itemId = "assistant-item-final",
                                isStreaming = true,
                                orderIndex = 0L,
                            ),
                        ),
                    ),
                ),
            ),
        )
        invokePrivateMethod(
            service,
            "setActiveTurnId",
            "thread-final-answer-fallback",
            "turn-final-answer-fallback",
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-final-answer-fallback"))
                put("turnId", JsonPrimitive("turn-final-answer-fallback"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("assistant-item-final"))
                        put("type", JsonPrimitive("agent_message"))
                        put("phase", JsonPrimitive("final_answer"))
                        put("text", JsonPrimitive("Finished response"))
                    },
                )
            },
            true,
        )
        advanceUntilIdle()

        val thread = service.threads.value.first { it.id == "thread-final-answer-fallback" }
        val assistant = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .first { item -> item.id == "assistant-item-final" }

        assertEquals("Finished response", assistant.text)
        assertFalse(assistant.isStreaming)
        assertTrue(thread.isRunning)
        assertEquals("turn-final-answer-fallback", thread.activeTurnId)
        assertNull(thread.latestTurnTerminalState)
    }

    @Test
    fun `forced lifecycle catchup keeps retrying until delayed history is complete`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-idle-retry-catchup",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
        var threadReadCalls = 0
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
                                            put("id", JsonPrimitive("thread-idle-retry"))
                                            put("title", JsonPrimitive("Idle retry thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-idle-retry"))
                                            put("updatedAt", JsonPrimitive(1_713_222_620))
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
                "thread/read" to {
                    threadReadCalls += 1
                    val assistantText = if (threadReadCalls == 1) {
                        "Recovered final response"
                    } else {
                        "Recovered final response after delayed hydration."
                    }
                    buildJsonObject {
                        put(
                            "thread",
                            buildJsonObject {
                                put("id", JsonPrimitive("thread-idle-retry"))
                                put("title", JsonPrimitive("Idle retry thread"))
                                put("cwd", JsonPrimitive("/tmp/project-idle-retry"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-idle-retry"))
                                                put("status", JsonPrimitive("completed"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("assistant-item-idle-retry"))
                                                                put("type", JsonPrimitive("agent_message"))
                                                                put("text", JsonPrimitive(assistantText))
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

            seedThreads(
                service = service,
                snapshots = listOf(
                    ThreadSyncSnapshot(
                        id = "thread-idle-retry",
                        title = "Idle retry thread",
                        preview = "Recovered final response",
                        projectPath = "/tmp/project-idle-retry",
                        lastUpdatedLabel = "Updated just now",
                        lastUpdatedEpochMs = 0L,
                        isRunning = true,
                        runtimeConfig = RemodexRuntimeConfig(),
                        timelineMutations = listOf(
                            TimelineMutation.Upsert(
                                RemodexConversationItem(
                                    id = "assistant-item-idle-retry",
                                    speaker = ConversationSpeaker.ASSISTANT,
                                    kind = ConversationItemKind.CHAT,
                                    text = "Recovered final response",
                                    turnId = "turn-idle-retry",
                                    itemId = "assistant-item-idle-retry",
                                    isStreaming = true,
                                    orderIndex = 0L,
                                ),
                            ),
                        ),
                    ),
                ),
            )

            service.refreshThreads()
            awaitThreads(service, expectedCount = 1)

            service.hydrateThread("thread-idle-retry")
            advanceUntilIdle()
            service.hydrateThread("thread-idle-retry")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-idle-retry" }
            val assistant = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
                .first { item -> item.id == "assistant-item-idle-retry" }
            assertEquals("Recovered final response after delayed hydration.", assistant.text)
            assertFalse(assistant.isStreaming)
            assertFalse(thread.isRunning)
            assertTrue(threadReadCalls >= 2)
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
    fun `file change progress notifications reuse the live row and attach realtime diff`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-filechange-live",
                    title = "Live file change thread",
                    preview = "",
                    projectPath = "/tmp/project-filechange-live",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-filechange-live"))
                put("turnId", JsonPrimitive("turn-filechange-live"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("patch-item-1"))
                        put("type", JsonPrimitive("fileChange"))
                        put("status", JsonPrimitive("in_progress"))
                        put(
                            "changes",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("path", JsonPrimitive("app/src/Main.kt"))
                                        put("kind", JsonPrimitive("add"))
                                    },
                                )
                            },
                        )
                    },
                )
            },
            false,
        )

        invokePrivateMethod(
            service,
            "appendFileChangeDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-filechange-live"))
                put("turnId", JsonPrimitive("turn-filechange-live"))
                put("itemId", JsonPrimitive("patch-item-1"))
                put("delta", JsonPrimitive("Edited app/src/Main.kt\n"))
            },
        )

        invokePrivateMethod(
            service,
            "handleTurnDiffUpdatedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-filechange-live"))
                put("turnId", JsonPrimitive("turn-filechange-live"))
                put(
                    "diff",
                    JsonPrimitive(
                        """
                        diff --git a/app/src/Main.kt b/app/src/Main.kt
                        new file mode 100644
                        --- /dev/null
                        +++ b/app/src/Main.kt
                        @@ -0,0 +1 @@
                        +hello
                        """.trimIndent(),
                    ),
                )
            },
        )

        val thread = service.threads.value.first { it.id == "thread-filechange-live" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val fileChangeItems = projected.filter { item -> item.kind == ConversationItemKind.FILE_CHANGE }

        assertEquals(1, fileChangeItems.size)
        assertEquals("patch-item-1", fileChangeItems.single().itemId)
        assertEquals(
            """
            Status: in_progress

            Path: app/src/Main.kt
            Kind: update

            ```diff
            diff --git a/app/src/Main.kt b/app/src/Main.kt
            new file mode 100644
            --- /dev/null
            +++ b/app/src/Main.kt
            @@ -0,0 +1 @@
            +hello
            ```
            """.trimIndent(),
            fileChangeItems.single().text,
        )
        val renderState = FileChangeRenderParser.renderState(fileChangeItems.single().text)
        val summaryEntry = renderState.summary?.entries?.single()
        assertEquals("app/src/Main.kt", summaryEntry?.path)
        assertEquals(FileChangeAction.ADDED, summaryEntry?.action)
        assertEquals(1, summaryEntry?.additions)
        assertEquals(0, summaryEntry?.deletions)
        val diffChunks = FileChangeRenderParser.diffChunks(
            bodyText = fileChangeItems.single().text,
            entries = renderState.summary?.entries.orEmpty(),
        )
        assertEquals(1, diffChunks.size)
        assertTrue(diffChunks.single().diffCode.contains("+hello"))
        assertTrue(fileChangeItems.single().isStreaming)
    }

    @Test
    fun `turn diff updates create a live file change row when no structured item exists`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-turn-diff-live",
                    title = "Turn diff live thread",
                    preview = "",
                    projectPath = "/tmp/project-turn-diff-live",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleTurnDiffUpdatedNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-turn-diff-live"))
                put("turnId", JsonPrimitive("turn-turn-diff-live"))
                put(
                    "diff",
                    JsonPrimitive(
                        """
                        diff --git a/app/src/Main.kt b/app/src/Main.kt
                        index 1111111..2222222 100644
                        --- a/app/src/Main.kt
                        +++ b/app/src/Main.kt
                        @@ -1 +1 @@
                        -old
                        +new
                        """.trimIndent(),
                    ),
                )
            },
        )

        val thread = service.threads.value.first { it.id == "thread-turn-diff-live" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val fileChangeItems = projected.filter { item -> item.kind == ConversationItemKind.FILE_CHANGE }

        assertEquals(1, fileChangeItems.size)
        assertNull(fileChangeItems.single().itemId)
        assertEquals(
            """
            Status: in_progress

            Path: app/src/Main.kt
            Kind: update

            ```diff
            diff --git a/app/src/Main.kt b/app/src/Main.kt
            index 1111111..2222222 100644
            --- a/app/src/Main.kt
            +++ b/app/src/Main.kt
            @@ -1 +1 @@
            -old
            +new
            ```
            """.trimIndent(),
            fileChangeItems.single().text,
        )
        assertTrue(fileChangeItems.single().isStreaming)
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
    fun `hydrate thread keeps metadata-only file change items without renderable summary evidence`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-filechange-metadata-only",
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
                                            put("id", JsonPrimitive("thread-filechange-metadata"))
                                            put("title", JsonPrimitive("Metadata only file change thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-filechange-metadata"))
                                            put("updatedAt", JsonPrimitive(1_713_333_555))
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
                                put("id", JsonPrimitive("thread-filechange-metadata"))
                                put("title", JsonPrimitive("Metadata only file change thread"))
                                put("cwd", JsonPrimitive("/tmp/project-filechange-metadata"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-filechange-metadata"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("filechange-item-metadata"))
                                                                put("type", JsonPrimitive("file_change"))
                                                                put("status", JsonPrimitive("completed"))
                                                                put(
                                                                    "changes",
                                                                    buildJsonArray {
                                                                        add(
                                                                            buildJsonObject {
                                                                                put("path", JsonPrimitive("app/src/Main.kt"))
                                                                                put("kind", JsonPrimitive("update"))
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

            service.hydrateThread("thread-filechange-metadata")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-filechange-metadata" }
            val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            val fileChangeItem = projected.single { item ->
                item.kind == ConversationItemKind.FILE_CHANGE
            }

            assertEquals(
                """
                Status: completed

                Path: app/src/Main.kt
                Kind: update
                """.trimIndent(),
                fileChangeItem.text,
            )
            val renderState = FileChangeRenderParser.renderState(fileChangeItem.text)
            assertNull(renderState.summary)
        } finally {
            coordinator.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `raw history file change summary distinguishes patch diffs from metadata only changes`() {
        val summary = summarizeRawThreadHistoryFileChanges(
            buildJsonObject {
                put(
                    "turns",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", JsonPrimitive("turn-raw-history"))
                                put(
                                    "items",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("filechange-add"))
                                                put("type", JsonPrimitive("fileChange"))
                                                put(
                                                    "changes",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("path", JsonPrimitive("README.md"))
                                                                put("kind", JsonPrimitive("add"))
                                                                put("diff", JsonPrimitive("hello\n"))
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("filechange-update"))
                                                put("type", JsonPrimitive("fileChange"))
                                                put(
                                                    "changes",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("path", JsonPrimitive("src/Main.kt"))
                                                                put("kind", JsonPrimitive("update"))
                                                                put(
                                                                    "diff",
                                                                    JsonPrimitive(
                                                                        """
                                                                        diff --git a/src/Main.kt b/src/Main.kt
                                                                        index 1111111..2222222 100644
                                                                        --- a/src/Main.kt
                                                                        +++ b/src/Main.kt
                                                                        @@ -1 +1 @@
                                                                        -old
                                                                        +new
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
                                                put("id", JsonPrimitive("toolcall-metadata"))
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
                                                                        put("path", JsonPrimitive("src/Empty.kt"))
                                                                        put("kind", JsonPrimitive("update"))
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

        assertEquals(3, summary.fileChangeLikeItems)
        assertEquals(mapOf("filechange" to 2, "toolcall" to 1), summary.typeCounts)
        assertEquals(3, summary.itemsWithChangesPayload)
        assertEquals(2, summary.itemsWithAnyDiffField)
        assertEquals(1, summary.itemsWithPatchLikeDiff)
        assertEquals(0, summary.itemsWithInlineTotals)
        assertEquals(0, summary.itemsWithContentField)
        assertEquals(
            listOf(
                "filechange-add:filechange:changes=true:anyDiff=true:patchDiff=false:inlineTotals=false:content=false",
                "filechange-update:filechange:changes=true:anyDiff=true:patchDiff=true:inlineTotals=false:content=false",
                "toolcall-metadata:toolcall:changes=true:anyDiff=false:patchDiff=false:inlineTotals=false:content=false",
            ),
            summary.sampleDescriptors,
        )
    }

    @Test
    fun `hydrate thread decodes app server file changes with hunk-only diffs`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-filechange-hunk-history",
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
                                            put("id", JsonPrimitive("thread-filechange-hunk"))
                                            put("title", JsonPrimitive("App server hunk diff thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-filechange-hunk"))
                                            put("updatedAt", JsonPrimitive(1_713_333_666))
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
                                put("id", JsonPrimitive("thread-filechange-hunk"))
                                put("title", JsonPrimitive("App server hunk diff thread"))
                                put("cwd", JsonPrimitive("/tmp/project-filechange-hunk"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-filechange-hunk"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("filechange-item-hunk"))
                                                                put("type", JsonPrimitive("fileChange"))
                                                                put("status", JsonPrimitive("completed"))
                                                                put(
                                                                    "changes",
                                                                    buildJsonArray {
                                                                        add(
                                                                            buildJsonObject {
                                                                                put(
                                                                                    "path",
                                                                                    JsonPrimitive("/tmp/project-filechange-hunk/src/Main.kt"),
                                                                                )
                                                                                put(
                                                                                    "kind",
                                                                                    buildJsonObject {
                                                                                        put("type", JsonPrimitive("update"))
                                                                                        put("move_path", JsonNull)
                                                                                    },
                                                                                )
                                                                                put(
                                                                                    "diff",
                                                                                    JsonPrimitive(
                                                                                        """
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

            service.hydrateThread("thread-filechange-hunk")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-filechange-hunk" }
            val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            val fileChangeItem = projected.single { item ->
                item.kind == ConversationItemKind.FILE_CHANGE
            }

            val renderState = FileChangeRenderParser.renderState(fileChangeItem.text)
            val entry = renderState.summary?.entries?.single()
            assertEquals("/tmp/project-filechange-hunk/src/Main.kt", entry?.path)
            assertEquals(FileChangeAction.EDITED, entry?.action)
            assertEquals(2, entry?.additions)
            assertEquals(1, entry?.deletions)
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

    @Test
    fun `mcp tool call progress and completion keep a dedicated MCP row`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-mcp",
                    title = "MCP thread",
                    preview = "Search docs",
                    projectPath = "/tmp/project-mcp",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-mcp"))
                put("turnId", JsonPrimitive("turn-mcp"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("mcp-item-1"))
                        put("type", JsonPrimitive("mcpToolCall"))
                        put("server", JsonPrimitive("web"))
                        put("tool", JsonPrimitive("search_query"))
                        put("status", JsonPrimitive("in_progress"))
                    },
                )
            },
            false,
        )

        invokePrivateMethod(
            service,
            "handleMcpToolCallProgressNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-mcp"))
                put("turnId", JsonPrimitive("turn-mcp"))
                put("itemId", JsonPrimitive("mcp-item-1"))
                put("message", JsonPrimitive("Searching official docs"))
            },
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-mcp"))
                put("turnId", JsonPrimitive("turn-mcp"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("mcp-item-1"))
                        put("type", JsonPrimitive("mcpToolCall"))
                        put("server", JsonPrimitive("web"))
                        put("tool", JsonPrimitive("search_query"))
                        put("status", JsonPrimitive("completed"))
                        put(
                            "result",
                            buildJsonObject {
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", JsonPrimitive("text"))
                                                put("text", JsonPrimitive("Found the networking docs"))
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-mcp" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val mcpItems = projected.filter { item -> item.kind == ConversationItemKind.MCP_TOOL_CALL }

        assertEquals(1, mcpItems.size)
        assertEquals("Called web/search_query", mcpItems.single().text)
        assertEquals("mcp-item-1", mcpItems.single().itemId)
        assertTrue(mcpItems.single().supportingText.orEmpty().contains("Found the networking docs"))
        assertFalse(mcpItems.single().isStreaming)
    }

    @Test
    fun `mcp progress rebind keeps the same row when item id arrives after turn scoped placeholder`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-mcp-rebind",
                    title = "MCP rebind thread",
                    preview = "First response",
                    projectPath = "/tmp/project-mcp-rebind",
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
                                text = "Inspecting tools",
                                turnId = "turn-mcp-rebind",
                                itemId = "thinking-1",
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
                                turnId = "turn-mcp-rebind",
                                itemId = "assistant-item-1",
                                isStreaming = true,
                                orderIndex = 1L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-mcp-rebind"))
                put("turnId", JsonPrimitive("turn-mcp-rebind"))
                put(
                    "item",
                    buildJsonObject {
                        put("type", JsonPrimitive("mcpToolCall"))
                        put("server", JsonPrimitive("web"))
                        put("tool", JsonPrimitive("search_query"))
                    },
                )
            },
            false,
        )

        invokePrivateMethod(
            service,
            "handleMcpToolCallProgressNotification",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-mcp-rebind"))
                put("turnId", JsonPrimitive("turn-mcp-rebind"))
                put("itemId", JsonPrimitive("mcp-item-1"))
                put("message", JsonPrimitive("Searching official docs"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-mcp-rebind" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val mcpItems = projected.filter { item -> item.kind == ConversationItemKind.MCP_TOOL_CALL }

        assertEquals(1, mcpItems.size)
        assertEquals("mcp-item-1", mcpItems.single().itemId)
        assertEquals(
            listOf("thinking-1", "assistant-1", mcpItems.single().id),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals("Searching official docs", mcpItems.single().supportingText)
    }

    @Test
    fun `mcp lifecycle truncates oversized supporting text before it reaches the timeline`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-mcp-large-result",
                    title = "Large MCP result thread",
                    preview = "Search",
                    projectPath = "/tmp/project-mcp-large-result",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        val largeResult = (1..200).joinToString(separator = "\n") { index ->
            "result line $index ${"x".repeat(80)}"
        }

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-mcp-large-result"))
                put("turnId", JsonPrimitive("turn-mcp-large-result"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("mcp-item-large"))
                        put("type", JsonPrimitive("mcpToolCall"))
                        put("server", JsonPrimitive("web"))
                        put("tool", JsonPrimitive("search_query"))
                        put(
                            "result",
                            buildJsonObject {
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", JsonPrimitive("text"))
                                                put("text", JsonPrimitive(largeResult))
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-mcp-large-result" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val mcpItem = projected.single { item -> item.kind == ConversationItemKind.MCP_TOOL_CALL }

        assertTrue(mcpItem.supportingText.orEmpty().length <= 4_002)
        assertTrue(mcpItem.supportingText.orEmpty().endsWith("…"))
    }

    @Test
    fun `web search lifecycle renders as dedicated web search row`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-web-search",
                    title = "Web search thread",
                    preview = "Search",
                    projectPath = "/tmp/project-web-search",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-web-search"))
                put("turnId", JsonPrimitive("turn-web-search"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("search-item-1"))
                        put("type", JsonPrimitive("webSearch"))
                        put("query", JsonPrimitive("kotlin stateflow"))
                        put(
                            "action",
                            buildJsonObject {
                                put("type", JsonPrimitive("search"))
                                put(
                                    "queries",
                                    buildJsonArray {
                                        add(JsonPrimitive("kotlin stateflow"))
                                        add(JsonPrimitive("android compose flow"))
                                    },
                                )
                            },
                        )
                    },
                )
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-web-search" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val webSearchItem = projected.single { item -> item.kind == ConversationItemKind.WEB_SEARCH }

        assertEquals("Searched the web", webSearchItem.text)
        assertTrue(webSearchItem.supportingText.orEmpty().contains("kotlin stateflow"))
        assertTrue(webSearchItem.supportingText.orEmpty().contains("android compose flow"))
        assertFalse(webSearchItem.isStreaming)
    }

    @Test
    fun `web search completion without turn id still stays after assistant in interleaved turns`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-web-search-refresh",
                    title = "Web search refresh thread",
                    preview = "First response",
                    projectPath = "/tmp/project-web-search-refresh",
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
                                text = "Inspecting search results",
                                turnId = "turn-web-search-refresh",
                                itemId = "thinking-1",
                                isStreaming = false,
                                orderIndex = 0L,
                            ),
                        ),
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "search-item-1",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.WEB_SEARCH,
                                text = "Searching the web",
                                supportingText = "kotlin stateflow",
                                turnId = "turn-web-search-refresh",
                                itemId = "search-item-1",
                                isStreaming = true,
                                orderIndex = 1L,
                            ),
                        ),
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "assistant-1",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "First response",
                                turnId = "turn-web-search-refresh",
                                itemId = "assistant-item-1",
                                isStreaming = true,
                                orderIndex = 2L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-web-search-refresh"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("search-item-1"))
                        put("type", JsonPrimitive("webSearch"))
                        put("query", JsonPrimitive("kotlin stateflow"))
                        put(
                            "action",
                            buildJsonObject {
                                put("type", JsonPrimitive("search"))
                                put(
                                    "queries",
                                    buildJsonArray {
                                        add(JsonPrimitive("kotlin stateflow"))
                                    },
                                )
                            },
                        )
                    },
                )
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-web-search-refresh" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)

        assertEquals(
            listOf("thinking-1", "assistant-1", "search-item-1"),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals("Searched the web", projected.last().text)
    }

    @Test
    fun `completed command execution preserves fallback running thread state`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-command-fallback",
                    title = "Command fallback thread",
                    preview = "Still thinking",
                    projectPath = "/tmp/project-command-fallback",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )
        invokePrivateMethod(
            service,
            "markThreadAsRunningFallback",
            "thread-command-fallback",
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-command-fallback"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("command-item-fallback"))
                        put("type", JsonPrimitive("commandExecution"))
                        put(
                            "command",
                            buildJsonArray {
                                add(JsonPrimitive("cat"))
                                add(JsonPrimitive("README.md"))
                            },
                        )
                    },
                )
            },
            false,
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-command-fallback"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("command-item-fallback"))
                        put("type", JsonPrimitive("commandExecution"))
                        put(
                            "command",
                            buildJsonArray {
                                add(JsonPrimitive("cat"))
                                add(JsonPrimitive("README.md"))
                            },
                        )
                        put("status", JsonPrimitive("completed"))
                    },
                )
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-command-fallback" }
        val commandItem = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
            .single { item -> item.id == "command-item-fallback" }

        assertEquals(ConversationItemKind.COMMAND_EXECUTION, commandItem.kind)
        assertFalse(commandItem.isStreaming)
        assertTrue(thread.isRunning)
        assertNull(thread.activeTurnId)
        assertNull(thread.latestTurnTerminalState)
    }

    @Test
    fun `web search that starts after the first assistant block stays after it in an empty turn`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-web-search-first-turn",
                    title = "First turn web search thread",
                    preview = "",
                    projectPath = "/tmp/project-web-search-first-turn",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-web-search-first-turn"))
                put("turnId", JsonPrimitive("turn-web-search-first-turn"))
                put("delta", JsonPrimitive("First streamed answer block"))
            },
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-web-search-first-turn"))
                put("turnId", JsonPrimitive("turn-web-search-first-turn"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("search-item-first-turn"))
                        put("type", JsonPrimitive("webSearch"))
                        put("query", JsonPrimitive("compose lazycolumn"))
                    },
                )
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-web-search-first-turn" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)

        assertEquals(
            listOf("assistant-turn-web-search-first-turn", "search-item-first-turn"),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals("First streamed answer block", projected.first().text)
        assertEquals("Searched the web", projected.last().text)
    }

    @Test
    fun `tool call lifecycle item that starts after the first assistant block stays after it in an empty turn`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-toolcall-first-turn",
                    title = "First turn tool call thread",
                    preview = "",
                    projectPath = "/tmp/project-toolcall-first-turn",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-toolcall-first-turn"))
                put("turnId", JsonPrimitive("turn-toolcall-first-turn"))
                put("delta", JsonPrimitive("First streamed answer block"))
            },
        )

        invokePrivateMethod(
            service,
            "handleItemLifecycle",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-toolcall-first-turn"))
                put("turnId", JsonPrimitive("turn-toolcall-first-turn"))
                put(
                    "item",
                    buildJsonObject {
                        put("id", JsonPrimitive("toolcall-item-first-turn"))
                        put("type", JsonPrimitive("toolCall"))
                        put(
                            "output",
                            buildJsonArray {
                                add(JsonPrimitive("searched app/src/main"))
                            },
                        )
                    },
                )
            },
            true,
        )

        val thread = service.threads.value.first { it.id == "thread-toolcall-first-turn" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val toolItem = projected.last()

        assertEquals(
            listOf("assistant-turn-toolcall-first-turn", "toolcall-item-first-turn"),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals(ConversationItemKind.TOOL_ACTIVITY, toolItem.kind)
        assertEquals(
            ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE,
            toolItem.systemTurnOrderingHint,
        )
        assertTrue(toolItem.text.contains("searched app/src/main"))
    }

    @Test
    fun `tool activity delta that starts after the first assistant block stays after it in an empty turn`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-tool-activity-first-turn",
                    title = "First turn tool activity thread",
                    preview = "",
                    projectPath = "/tmp/project-tool-activity-first-turn",
                    lastUpdatedLabel = "Updated just now",
                    lastUpdatedEpochMs = 1L,
                    isRunning = true,
                    runtimeConfig = RemodexRuntimeConfig(),
                    timelineMutations = emptyList(),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendAssistantDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-tool-activity-first-turn"))
                put("turnId", JsonPrimitive("turn-tool-activity-first-turn"))
                put("delta", JsonPrimitive("First streamed answer block"))
            },
        )

        invokePrivateMethod(
            service,
            "appendToolCallDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-tool-activity-first-turn"))
                put("turnId", JsonPrimitive("turn-tool-activity-first-turn"))
                put("itemId", JsonPrimitive("tool-activity-item-first-turn"))
                put("delta", JsonPrimitive("searched app/src/main"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-tool-activity-first-turn" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val toolItem = projected.last()

        assertEquals(
            listOf("assistant-turn-tool-activity-first-turn", "tool-activity-item-first-turn"),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals(ConversationItemKind.TOOL_ACTIVITY, toolItem.kind)
        assertEquals(
            ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE,
            toolItem.systemTurnOrderingHint,
        )
        assertTrue(toolItem.text.contains("searched app/src/main"))
    }

    @Test
    fun `tool activity delta rebind keeps the same row when item id arrives after turn scoped placeholder`() = runTest {
        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = SecureConnectionCoordinator(
                store = InMemorySecureStore(),
                trustedSessionResolver = UnusedTrustedSessionResolver,
                relayWebSocketFactory = UnexpectedRelayWebSocketFactory(),
                scope = this,
            ),
            scope = backgroundScope,
        )

        seedThreads(
            service = service,
            snapshots = listOf(
                ThreadSyncSnapshot(
                    id = "thread-tool-activity-rebind",
                    title = "Tool activity rebind thread",
                    preview = "First response",
                    projectPath = "/tmp/project-tool-activity-rebind",
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
                                text = "Inspecting files",
                                turnId = "turn-tool-activity-rebind",
                                itemId = "thinking-1",
                                isStreaming = false,
                                orderIndex = 0L,
                            ),
                        ),
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "toolactivity-turn-tool-activity-rebind",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.TOOL_ACTIVITY,
                                text = "search src",
                                turnId = "turn-tool-activity-rebind",
                                itemId = null,
                                isStreaming = true,
                                orderIndex = 1L,
                            ),
                        ),
                        TimelineMutation.Upsert(
                            RemodexConversationItem(
                                id = "assistant-1",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "First response",
                                turnId = "turn-tool-activity-rebind",
                                itemId = "assistant-item-1",
                                isStreaming = true,
                                orderIndex = 2L,
                            ),
                        ),
                    ),
                ),
            ),
        )

        invokePrivateMethod(
            service,
            "appendToolCallDelta",
            buildJsonObject {
                put("threadId", JsonPrimitive("thread-tool-activity-rebind"))
                put("turnId", JsonPrimitive("turn-tool-activity-rebind"))
                put("itemId", JsonPrimitive("tool-item-1"))
                put("delta", JsonPrimitive("searched src/main"))
            },
        )

        val thread = service.threads.value.first { it.id == "thread-tool-activity-rebind" }
        val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)
        val toolItems = projected.filter { item -> item.kind == ConversationItemKind.TOOL_ACTIVITY }

        assertEquals(1, toolItems.size)
        assertEquals("tool-item-1", toolItems.single().itemId)
        assertEquals(
            listOf("thinking-1", "assistant-1", "toolactivity-turn-tool-activity-rebind"),
            projected.map(RemodexConversationItem::id),
        )
        assertTrue(toolItems.single().text.contains("search src"))
        assertTrue(toolItems.single().text.contains("searched src/main"))
    }

    @Test
    fun `hydrate thread decodes visible codex items into dedicated Android timeline rows`() = runTest {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-visible-items-history",
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
                                            put("id", JsonPrimitive("thread-visible-items"))
                                            put("title", JsonPrimitive("Visible items thread"))
                                            put("cwd", JsonPrimitive("/tmp/project-visible-items"))
                                            put("updatedAt", JsonPrimitive(1_713_555_666))
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
                                put("id", JsonPrimitive("thread-visible-items"))
                                put("title", JsonPrimitive("Visible items thread"))
                                put("cwd", JsonPrimitive("/tmp/project-visible-items"))
                                put(
                                    "turns",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", JsonPrimitive("turn-visible-items"))
                                                put(
                                                    "items",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("mcp-history-1"))
                                                                put("type", JsonPrimitive("mcp_tool_call"))
                                                                put("server", JsonPrimitive("web"))
                                                                put("tool", JsonPrimitive("search_query"))
                                                                put("status", JsonPrimitive("completed"))
                                                                put(
                                                                    "result",
                                                                    buildJsonObject {
                                                                        put(
                                                                            "content",
                                                                            buildJsonArray {
                                                                                add(
                                                                                    buildJsonObject {
                                                                                        put("type", JsonPrimitive("text"))
                                                                                        put("text", JsonPrimitive("MCP result summary"))
                                                                                    },
                                                                                )
                                                                            },
                                                                        )
                                                                    },
                                                                )
                                                            },
                                                        )
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("search-history-1"))
                                                                put("type", JsonPrimitive("web_search"))
                                                                put("query", JsonPrimitive("compose image preview"))
                                                                put(
                                                                    "action",
                                                                    buildJsonObject {
                                                                        put("type", JsonPrimitive("openPage"))
                                                                        put("url", JsonPrimitive("https://developer.android.com"))
                                                                    },
                                                                )
                                                            },
                                                        )
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("image-view-1"))
                                                                put("type", JsonPrimitive("image_view"))
                                                                put("path", JsonPrimitive("/tmp/preview.png"))
                                                            },
                                                        )
                                                        add(
                                                            buildJsonObject {
                                                                put("id", JsonPrimitive("image-generation-1"))
                                                                put("type", JsonPrimitive("image_generation"))
                                                                put("revisedPrompt", JsonPrimitive("A local architecture diagram"))
                                                                put("savedPath", JsonPrimitive("/tmp/generated-diagram.png"))
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

            service.hydrateThread("thread-visible-items")
            advanceUntilIdle()

            val thread = service.threads.value.first { it.id == "thread-visible-items" }
            val projected = TurnTimelineReducer.reduceProjected(thread.timelineMutations)

            val mcpItem = projected.single { item -> item.kind == ConversationItemKind.MCP_TOOL_CALL }
            assertEquals("Called web/search_query", mcpItem.text)
            assertTrue(mcpItem.supportingText.orEmpty().contains("MCP result summary"))

            val webSearchItem = projected.single { item -> item.kind == ConversationItemKind.WEB_SEARCH }
            assertEquals("Searched the web", webSearchItem.text)
            assertTrue(webSearchItem.supportingText.orEmpty().contains("https://developer.android.com"))

            val imageViewItem = projected.single { item -> item.kind == ConversationItemKind.IMAGE_VIEW }
            assertEquals("Viewed Image", imageViewItem.text)
            assertEquals(1, imageViewItem.attachments.size)
            assertTrue(imageViewItem.attachments.single().uriString.startsWith("file:"))

            val imageGenerationItem = projected.single { item -> item.kind == ConversationItemKind.IMAGE_GENERATION }
            assertEquals("Generated Image", imageGenerationItem.text)
            assertTrue(imageGenerationItem.supportingText.orEmpty().contains("A local architecture diagram"))
            assertEquals(1, imageGenerationItem.attachments.size)
            assertTrue(imageGenerationItem.attachments.single().uriString.startsWith("file:"))
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

    private suspend fun TestScope.awaitSecureTransportReady(
        coordinator: SecureConnectionCoordinator,
    ) {
        val field = coordinator.javaClass.getDeclaredField("secureSession")
        field.isAccessible = true
        repeat(40) {
            advanceUntilIdle()
            if (field.get(coordinator) != null) {
                return
            }
            Thread.sleep(10)
        }
        fail("Expected secure transport session to be ready.")
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

    @Suppress("UNCHECKED_CAST")
    private fun setCommandExecutionDetails(
        service: BridgeThreadSyncService,
        detailsByItemId: Map<String, RemodexCommandExecutionDetails>,
    ) {
        val field = service.javaClass.getDeclaredField("backingCommandExecutionDetails")
        field.isAccessible = true
        val state =
            field.get(service) as kotlinx.coroutines.flow.MutableStateFlow<Map<String, RemodexCommandExecutionDetails>>
        state.value = detailsByItemId
    }

    @Suppress("UNCHECKED_CAST")
    private fun setSecureConnectionState(
        coordinator: SecureConnectionCoordinator,
        snapshot: SecureConnectionSnapshot,
    ) {
        val field = coordinator.javaClass.getDeclaredField("connectionState")
        field.isAccessible = true
        val state = field.get(coordinator) as kotlinx.coroutines.flow.MutableStateFlow<SecureConnectionSnapshot>
        state.value = snapshot
    }

    private fun clearSecureTransportSession(
        coordinator: SecureConnectionCoordinator,
    ) {
        val field = coordinator.javaClass.getDeclaredField("secureSession")
        field.isAccessible = true
        field.set(coordinator, null)
    }

    private suspend fun TestScope.createConnectedBridgeService(): ConnectedBridgeService {
        val store = InMemorySecureStore()
        val macIdentity = createTestMacIdentity()
        val payload = createTestPairingPayload(
            macDeviceId = "mac-approval-test",
            macIdentityPublicKey = macIdentity.publicKeyBase64,
        )
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
            ),
        )
        val coordinator = SecureConnectionCoordinator(
            store = store,
            trustedSessionResolver = UnusedTrustedSessionResolver,
            relayWebSocketFactory = relayFactory,
            scope = this,
        )
        coordinator.rememberRelayPairing(payload)
        coordinator.retryConnection()
        awaitSecureState(coordinator, SecureConnectionState.ENCRYPTED)
        awaitSecureTransportReady(coordinator)
        advanceUntilIdle()

        val service = BridgeThreadSyncService(
            secureConnectionCoordinator = coordinator,
            scope = backgroundScope,
        )

        return ConnectedBridgeService(
            service = service,
            coordinator = coordinator,
            relayFactory = relayFactory,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun setPendingApprovalRequest(
        service: BridgeThreadSyncService,
        request: RemodexApprovalRequest?,
    ) {
        val field = service.javaClass.getDeclaredField("backingPendingApprovalRequest")
        field.isAccessible = true
        val state = field.get(service) as kotlinx.coroutines.flow.MutableStateFlow<RemodexApprovalRequest?>
        state.value = request
    }

    private data class ConnectedBridgeService(
        val service: BridgeThreadSyncService,
        val coordinator: SecureConnectionCoordinator,
        val relayFactory: ScriptedRpcRelayWebSocketFactory,
    )

}
