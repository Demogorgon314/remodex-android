package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexPlanState
import com.emanueledipietro.remodex.model.RemodexPlanStep
import com.emanueledipietro.remodex.model.RemodexPlanStepStatus
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputAnswer
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputQuestion
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputRequest
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.JsonPrimitive

class ThreadHistoryReconcilerTest {
    @Test
    fun `merge history items keeps local streaming assistant formatting when snapshot compacts whitespace`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "assistant-local",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = """
                    可以，下面给你一版适合直接当 backlog 用的任务清单。

                    **P0**

                    1. 拆分启动脚本
                       把 build/entrypoint.sh 拆成
                       env、openconnect、danted。
                """.trimIndent(),
                turnId = "turn-1",
                isStreaming = true,
                orderIndex = 2L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "assistant-history",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "可以，下面给你一版适合直接当 backlog 用的任务清单。**P0**1. 拆分启动脚本把 build/entrypoint.sh 拆成env、openconnect、danted。",
                turnId = "turn-1",
                itemId = "assistant-item-1",
                isStreaming = true,
                orderIndex = 5L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = true,
            threadIsRunning = true,
        )

        assertEquals(1, merged.size)
        assertEquals("assistant-item-1", merged.single().itemId)
        assertEquals(existing.single().text, merged.single().text)
        assertTrue(merged.single().isStreaming)
    }

    @Test
    fun `merge history items appends resumed streaming suffix while preserving local formatting`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "assistant-local",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = """
                    可以，下面给你一版适合直接当 backlog 用的任务清单。

                    **P0**

                    1. 拆分启动脚本
                       把 build/entrypoint.sh 拆成
                       env、openconnect、danted。
                """.trimIndent(),
                turnId = "turn-1",
                isStreaming = true,
                orderIndex = 2L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "assistant-history",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "可以，下面给你一版适合直接当 backlog 用的任务清单。**P0**1. 拆分启动脚本把 build/entrypoint.sh 拆成env、openconnect、danted。2. 增加重连恢复测试。",
                turnId = "turn-1",
                itemId = "assistant-item-1",
                isStreaming = true,
                orderIndex = 5L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = true,
            threadIsRunning = true,
        )

        assertEquals(1, merged.size)
        assertEquals("assistant-item-1", merged.single().itemId)
        assertEquals(
            """
                可以，下面给你一版适合直接当 backlog 用的任务清单。

                **P0**

                1. 拆分启动脚本
                   把 build/entrypoint.sh 拆成
                   env、openconnect、danted。2. 增加重连恢复测试。
            """.trimIndent(),
            merged.single().text,
        )
        assertTrue(merged.single().isStreaming)
    }

    @Test
    fun `merge history items keeps detailed reasoning when incoming snapshot is shorter`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "reasoning-live",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.REASONING,
                text = """
                    Investigating timeline merge

                    running rg -n "reasoning" app/src/main/java
                    opened BridgeThreadSyncService.kt
                """.trimIndent(),
                turnId = "turn-1",
                isStreaming = false,
                orderIndex = 2L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "reasoning-history",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.REASONING,
                text = "Investigating timeline merge",
                turnId = "turn-1",
                itemId = "reasoning-item-1",
                isStreaming = false,
                orderIndex = 5L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals("reasoning-item-1", merged.single().itemId)
        assertEquals(existing.single().text, merged.single().text)
        assertFalse(merged.single().isStreaming)
    }

    @Test
    fun `merge history items does not mark historical assistant rows streaming just because thread is running`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "assistant-local",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "final response",
                turnId = "turn-1",
                itemId = "assistant-item-1",
                isStreaming = false,
                orderIndex = 2L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "assistant-history",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "final response",
                turnId = "turn-1",
                itemId = "assistant-item-1",
                isStreaming = false,
                orderIndex = 5L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = true,
            threadIsRunning = true,
        )

        assertEquals(1, merged.size)
        assertFalse(merged.single().isStreaming)
    }

    @Test
    fun `merge history items suppresses shorter closed assistant snapshot instead of rolling visible text back`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "assistant-local",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "Here is the full answer with all details",
                turnId = "turn-1",
                itemId = "assistant-item-1",
                isStreaming = false,
                orderIndex = 2L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "assistant-history",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "Here is the full",
                turnId = "turn-1",
                itemId = "assistant-item-1",
                isStreaming = false,
                orderIndex = 5L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals(existing.single().text, merged.single().text)
        assertEquals("assistant-item-1", merged.single().itemId)
        assertFalse(merged.single().isStreaming)
    }

    @Test
    fun `merge history items prefers the active streaming assistant segment over earlier completed output`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "assistant-completed",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "First completed segment",
                turnId = "turn-1",
                itemId = "assistant-item-old",
                isStreaming = false,
                orderIndex = 1L,
            ),
            RemodexConversationItem(
                id = "assistant-streaming",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "Second segment in progress",
                turnId = "turn-1",
                itemId = null,
                isStreaming = true,
                orderIndex = 2L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "assistant-history",
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
                text = "Second segment in progress with more detail",
                turnId = "turn-1",
                itemId = "assistant-item-new",
                isStreaming = true,
                orderIndex = 3L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = true,
            threadIsRunning = true,
        )

        assertEquals(2, merged.size)
        assertEquals("First completed segment", merged[0].text)
        assertEquals("assistant-item-old", merged[0].itemId)
        assertEquals("assistant-streaming", merged[1].id)
        assertEquals("assistant-item-new", merged[1].itemId)
        assertEquals(
            "Second segment in progress with more detail",
            merged[1].text,
        )
        assertTrue(merged[1].isStreaming)
    }

    @Test
    fun `merge history items backfills missing local timestamp from history`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "user-local",
                speaker = ConversationSpeaker.USER,
                kind = ConversationItemKind.CHAT,
                text = "hello",
                turnId = "turn-1",
                deliveryState = RemodexMessageDeliveryState.CONFIRMED,
                createdAtEpochMs = null,
                orderIndex = 1L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "user-history",
                speaker = ConversationSpeaker.USER,
                kind = ConversationItemKind.CHAT,
                text = "hello",
                turnId = "turn-1",
                deliveryState = RemodexMessageDeliveryState.CONFIRMED,
                createdAtEpochMs = 42L,
                orderIndex = 2L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals(42L, merged.single().createdAtEpochMs)
    }

    @Test
    fun `merge history items preserves local structured user input response when history lacks it`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "prompt-local",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.USER_INPUT_PROMPT,
                text = "Asked 1 question",
                structuredUserInputRequest = RemodexStructuredUserInputRequest(
                    requestId = JsonPrimitive("request-1"),
                    questions = listOf(
                        RemodexStructuredUserInputQuestion(
                            id = "path",
                            header = "",
                            question = "Which path should we take?",
                        ),
                    ),
                ),
                structuredUserInputResponse = RemodexStructuredUserInputResponse(
                    answersByQuestionId = mapOf(
                        "path" to RemodexStructuredUserInputAnswer(
                            answers = listOf("Android local"),
                        ),
                    ),
                ),
                turnId = "turn-1",
                itemId = "prompt-item-1",
                orderIndex = 1L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "prompt-history",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.USER_INPUT_PROMPT,
                text = "Asked 1 question",
                structuredUserInputRequest = RemodexStructuredUserInputRequest(
                    requestId = JsonPrimitive("request-1"),
                    questions = listOf(
                        RemodexStructuredUserInputQuestion(
                            id = "path",
                            header = "",
                            question = "Which path should we take?",
                        ),
                    ),
                ),
                turnId = "turn-1",
                itemId = "prompt-item-1",
                orderIndex = 2L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(
            listOf("Android local"),
            merged.single().structuredUserInputResponse?.answersFor("path"),
        )
    }

    @Test
    fun `merge history items matches command execution by item id before turn fallback`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "command-local-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-1",
                orderIndex = 1L,
            ),
            RemodexConversationItem(
                id = "command-local-2",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran ls",
                turnId = "turn-1",
                itemId = "command-item-2",
                orderIndex = 2L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "command-history-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-1",
                orderIndex = 1L,
            ),
            RemodexConversationItem(
                id = "command-history-2",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran ls",
                turnId = "turn-1",
                itemId = "command-item-2",
                orderIndex = 2L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(listOf("command-item-1", "command-item-2"), merged.map(RemodexConversationItem::itemId))
        assertEquals(listOf(1L, 2L), merged.map(RemodexConversationItem::orderIndex))
    }

    @Test
    fun `merge history items keeps local command order index when history snapshot arrives later`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "command-local-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-1",
                orderIndex = 3L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "command-history-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-1",
                orderIndex = 99L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals(3L, merged.single().orderIndex)
        assertEquals("command-item-1", merged.single().itemId)
    }

    @Test
    fun `merge history items keeps local command item id when preview match rebinding would reorder later`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "command-local-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-local",
                orderIndex = 3L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "command-history-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-server",
                orderIndex = 99L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals("command-item-local", merged.single().itemId)
        assertEquals(3L, merged.single().orderIndex)
    }

    @Test
    fun `merge history items matches plan rows by item id before same turn fallback`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "plan-local-live",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.PLAN,
                text = "Current active plan",
                turnId = "turn-1",
                itemId = "plan-item-live",
                planState = RemodexPlanState(
                    explanation = "Keep the active plan attached to the right row.",
                ),
                isStreaming = true,
                orderIndex = 3L,
            ),
            RemodexConversationItem(
                id = "plan-local-other",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.PLAN,
                text = "Different plan row",
                turnId = "turn-1",
                itemId = "plan-item-other",
                planState = RemodexPlanState(
                    explanation = "This row should stay untouched.",
                ),
                orderIndex = 4L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "plan-history-live",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.PLAN,
                text = "# Final plan\n- Verify reconnect merge",
                turnId = "turn-1",
                itemId = "plan-item-live",
                planState = RemodexPlanState(
                    explanation = "Keep the active plan attached to the right row.",
                    steps = listOf(
                        RemodexPlanStep(
                            id = "step-1",
                            step = "Verify reconnect merge",
                            status = RemodexPlanStepStatus.IN_PROGRESS,
                        ),
                    ),
                ),
                isStreaming = true,
                orderIndex = 10L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = true,
            threadIsRunning = true,
        )

        assertEquals(2, merged.size)
        assertEquals("# Final plan\n- Verify reconnect merge", merged[0].text)
        assertEquals("plan-item-live", merged[0].itemId)
        assertEquals(
            listOf("Verify reconnect merge"),
            merged[0].planState?.steps?.map(RemodexPlanStep::step),
        )
        assertEquals("Different plan row", merged[1].text)
        assertEquals("plan-item-other", merged[1].itemId)
    }

    @Test
    fun `merge history items collapses image sends when relay elides inline history images`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "user-local-image",
                speaker = ConversationSpeaker.USER,
                text = "这是什么",
                turnId = "turn-1",
                deliveryState = RemodexMessageDeliveryState.CONFIRMED,
                attachments = listOf(
                    RemodexConversationAttachment(
                        id = "local-attachment",
                        uriString = "content://media/external/images/media/1",
                        displayName = "1000012658.jpg",
                        previewDataUrl = "data:image/jpeg;base64,LOCAL",
                    ),
                ),
                orderIndex = 20L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "user-history-image",
                speaker = ConversationSpeaker.USER,
                text = "这是什么",
                turnId = "turn-1",
                attachments = listOf(
                    RemodexConversationAttachment(
                        id = "history-attachment",
                        uriString = "remodex://history-image-elided",
                        displayName = "history-image-elided",
                    ),
                ),
                orderIndex = 10L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals("user-local-image", merged.single().id)
        assertEquals(RemodexMessageDeliveryState.CONFIRMED, merged.single().deliveryState)
        assertEquals("turn-1", merged.single().turnId)
        assertEquals("data:image/jpeg;base64,LOCAL", merged.single().attachments.single().previewDataUrl)
        assertEquals("data:image/jpeg;base64,LOCAL", merged.single().attachments.single().renderUriString)
    }

    @Test
    fun `merge history items keeps attachment only optimistic message when history text falls back to usermessage`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "user-local-image",
                speaker = ConversationSpeaker.USER,
                text = "Shared 1 image from Android.",
                turnId = "turn-1",
                deliveryState = RemodexMessageDeliveryState.PENDING,
                attachments = listOf(
                    RemodexConversationAttachment(
                        id = "local-attachment",
                        uriString = "content://media/external/images/media/1",
                        displayName = "1000012658.jpg",
                        previewDataUrl = "data:image/jpeg;base64,LOCAL",
                    ),
                ),
                orderIndex = 20L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "user-history-image",
                speaker = ConversationSpeaker.USER,
                text = "Usermessage",
                turnId = "turn-1",
                attachments = listOf(
                    RemodexConversationAttachment(
                        id = "history-attachment",
                        uriString = "remodex://history-image-elided",
                        displayName = "history-image-elided",
                    ),
                ),
                orderIndex = 10L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals("user-local-image", merged.single().id)
        assertEquals(RemodexMessageDeliveryState.CONFIRMED, merged.single().deliveryState)
        assertEquals("turn-1", merged.single().turnId)
        assertEquals("data:image/jpeg;base64,LOCAL", merged.single().attachments.single().previewDataUrl)
    }
}
