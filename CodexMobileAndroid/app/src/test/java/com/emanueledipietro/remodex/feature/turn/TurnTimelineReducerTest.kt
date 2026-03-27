package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.data.threads.TimelineMutation
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetSource
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.RemodexAssistantFileChange
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnTimelineReducerTest {
    @Test
    fun `assistant deltas extend the existing item scoped response`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "First chunk",
                    orderIndex = 1,
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "Second chunk",
                    orderIndex = 1,
                ),
                TimelineMutation.Complete(
                    messageId = "assistant-1",
                ),
            ),
        )

        assertEquals(1, projected.size)
        assertEquals("assistant-1", projected.first().id)
        assertTrue(projected.first().text.contains("First chunk"))
        assertTrue(projected.first().text.contains("Second chunk"))
        assertFalse(projected.first().isStreaming)
    }

    @Test
    fun `assistant deltas preserve token leading spaces while streaming`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "Hello",
                    orderIndex = 1,
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = " world",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals(1, projected.size)
        assertEquals("Hello world", projected.first().text)
        assertTrue(projected.first().isStreaming)
    }

    @Test
    fun `assistant deltas do not drop repeated substring chunks that are not overlaps`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "现在这个文件同时负责环境变量校验",
                    orderIndex = 1,
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "环境变量",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals("现在这个文件同时负责环境变量校验环境变量", projected.first().text)
    }

    @Test
    fun `reasoning deltas preserve whitespace only chunks while streaming`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.ReasoningTextDelta(
                    messageId = "reasoning-1",
                    turnId = "turn-1",
                    itemId = "reasoning-item",
                    delta = "  indented",
                    orderIndex = 1,
                ),
                TimelineMutation.ReasoningTextDelta(
                    messageId = "reasoning-1",
                    turnId = "turn-1",
                    itemId = "reasoning-item",
                    delta = "\n",
                    orderIndex = 1,
                ),
                TimelineMutation.ReasoningTextDelta(
                    messageId = "reasoning-1",
                    turnId = "turn-1",
                    itemId = "reasoning-item",
                    delta = "    detail",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals(1, projected.size)
        assertEquals("  indented\n    detail", projected.first().text)
        assertTrue(projected.first().isStreaming)
    }

    @Test
    fun `supported system streaming kinds preserve whitespace only chunks`() {
        val systemChat = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.SystemTextDelta(
                    messageId = "system-1",
                    turnId = "turn-1",
                    itemId = "system-item",
                    delta = "line one",
                    kind = ConversationItemKind.CHAT,
                    orderIndex = 1,
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "system-1",
                    turnId = "turn-1",
                    itemId = "system-item",
                    delta = "  ",
                    kind = ConversationItemKind.CHAT,
                    orderIndex = 1,
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "system-1",
                    turnId = "turn-1",
                    itemId = "system-item",
                    delta = "line two",
                    kind = ConversationItemKind.CHAT,
                    orderIndex = 1,
                ),
            ),
        )
        val plan = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.SystemTextDelta(
                    messageId = "plan-1",
                    turnId = "turn-1",
                    itemId = "plan-item",
                    delta = "1. step",
                    kind = ConversationItemKind.PLAN,
                    orderIndex = 1,
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "plan-1",
                    turnId = "turn-1",
                    itemId = "plan-item",
                    delta = "\n  ",
                    kind = ConversationItemKind.PLAN,
                    orderIndex = 1,
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "plan-1",
                    turnId = "turn-1",
                    itemId = "plan-item",
                    delta = "2. next",
                    kind = ConversationItemKind.PLAN,
                    orderIndex = 1,
                ),
            ),
        )
        val fileChange = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.SystemTextDelta(
                    messageId = "file-1",
                    turnId = "turn-1",
                    itemId = "file-item",
                    delta = "@@ -1 +1 @@\n",
                    kind = ConversationItemKind.FILE_CHANGE,
                    orderIndex = 1,
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "file-1",
                    turnId = "turn-1",
                    itemId = "file-item",
                    delta = "    ",
                    kind = ConversationItemKind.FILE_CHANGE,
                    orderIndex = 1,
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "file-1",
                    turnId = "turn-1",
                    itemId = "file-item",
                    delta = "+value",
                    kind = ConversationItemKind.FILE_CHANGE,
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals("line one  line two", systemChat.first().text)
        assertEquals("1. step\n  2. next", plan.first().text)
        assertEquals("@@ -1 +1 @@\n    +value", fileChange.first().text)
    }

    @Test
    fun `command execution keeps trimming whitespace only chunks`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.SystemTextDelta(
                    messageId = "command-1",
                    turnId = "turn-1",
                    itemId = "command-item",
                    delta = "running pwd",
                    kind = ConversationItemKind.COMMAND_EXECUTION,
                    orderIndex = 1,
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "command-1",
                    turnId = "turn-1",
                    itemId = "command-item",
                    delta = "   ",
                    kind = ConversationItemKind.COMMAND_EXECUTION,
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals("running pwd", projected.first().text)
    }

    @Test
    fun `assistant response anchor prefers the active turn response`() {
        val items = listOf(
            RemodexConversationItem(
                id = "assistant-1",
                speaker = ConversationSpeaker.ASSISTANT,
                text = "Earlier reply",
                turnId = "turn-1",
                orderIndex = 1,
            ),
            RemodexConversationItem(
                id = "assistant-2",
                speaker = ConversationSpeaker.ASSISTANT,
                text = "Current reply",
                turnId = "turn-2",
                isStreaming = true,
                orderIndex = 2,
            ),
        )

        assertEquals(1, TurnTimelineReducer.assistantResponseAnchorIndex(items, activeTurnId = "turn-2"))
    }

    @Test
    fun `assistant response anchor falls back to the latest streaming assistant`() {
        val items = listOf(
            RemodexConversationItem(
                id = "assistant-1",
                speaker = ConversationSpeaker.ASSISTANT,
                text = "Earlier reply",
                turnId = "turn-1",
                orderIndex = 1,
            ),
            RemodexConversationItem(
                id = "assistant-2",
                speaker = ConversationSpeaker.ASSISTANT,
                text = "Streaming reply",
                turnId = "turn-2",
                isStreaming = true,
                orderIndex = 2,
            ),
        )

        assertEquals(1, TurnTimelineReducer.assistantResponseAnchorIndex(items, activeTurnId = null))
    }

    @Test
    fun `active turn anchor falls back to the latest item in the active turn when no assistant exists`() {
        val items = listOf(
            RemodexConversationItem(
                id = "user-1",
                speaker = ConversationSpeaker.USER,
                text = "Run review",
                turnId = "turn-1",
                orderIndex = 0,
            ),
            RemodexConversationItem(
                id = "reasoning-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.REASONING,
                text = "Thinking...",
                turnId = "turn-2",
                isStreaming = true,
                orderIndex = 1,
            ),
            RemodexConversationItem(
                id = "command-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "running git status",
                turnId = "turn-2",
                isStreaming = true,
                orderIndex = 2,
            ),
        )

        assertEquals(2, TurnTimelineReducer.activeTurnAnchorIndex(items, activeTurnId = "turn-2"))
    }

    @Test
    fun `active turn anchor falls back to the latest streaming item when the active turn id is unavailable`() {
        val items = listOf(
            RemodexConversationItem(
                id = "assistant-1",
                speaker = ConversationSpeaker.ASSISTANT,
                text = "Earlier reply",
                turnId = "turn-1",
                orderIndex = 1,
            ),
            RemodexConversationItem(
                id = "command-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "running git diff",
                turnId = "turn-2",
                isStreaming = true,
                orderIndex = 2,
            ),
        )

        assertEquals(1, TurnTimelineReducer.activeTurnAnchorIndex(items, activeTurnId = null))
    }

    @Test
    fun `projected fast path updates only the streaming assistant row`() {
        val user = RemodexConversationItem(
            id = "user-1",
            speaker = ConversationSpeaker.USER,
            text = "Ship it",
            orderIndex = 0,
        )
        val assistant = RemodexConversationItem(
            id = "assistant-1",
            speaker = ConversationSpeaker.ASSISTANT,
            kind = ConversationItemKind.CHAT,
            text = "First chunk",
            turnId = "turn-1",
            itemId = "assistant-item",
            isStreaming = true,
            orderIndex = 1,
        )

        val projected = listOf(user, assistant)
        val next = TurnTimelineReducer.applyProjectedFastPath(
            items = projected,
            mutation = TimelineMutation.AssistantTextDelta(
                messageId = "assistant-1",
                turnId = "turn-1",
                itemId = "assistant-item",
                delta = "Second chunk",
                orderIndex = 1,
            ),
        )

        assertNotNull(next)
        assertTrue(next!![0] === user)
        assertTrue(next[1] !== assistant)
        assertTrue(next[1].text.contains("First chunk"))
        assertTrue(next[1].text.contains("Second chunk"))
    }

    @Test
    fun `activity lines become dedicated tool activity rows while reasoning stays separate`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "reasoning-1",
                        speaker = ConversationSpeaker.SYSTEM,
                        kind = ConversationItemKind.REASONING,
                        text = "Thinking...",
                        turnId = "turn-1",
                        itemId = "reasoning-item",
                        isStreaming = false,
                        orderIndex = 1,
                    ),
                ),
                TimelineMutation.ActivityLine(
                    messageId = "tool-activity-1",
                    turnId = "turn-1",
                    itemId = "tool-activity-item",
                    line = "Running ./gradlew :app:testDebugUnitTest",
                    orderIndex = 2,
                ),
                TimelineMutation.ReasoningTextDelta(
                    messageId = "reasoning-1",
                    turnId = "turn-1",
                    itemId = "reasoning-item",
                    delta = "Merged late reasoning detail",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals(2, projected.size)
        val reasoning = projected.first { it.kind == ConversationItemKind.REASONING }
        val toolActivity = projected.first { it.kind == ConversationItemKind.TOOL_ACTIVITY }
        assertTrue(reasoning.text.contains("Merged late reasoning detail"))
        assertEquals("reasoning-item", reasoning.itemId)
        assertTrue(reasoning.isStreaming)
        assertEquals("tool-activity-item", toolActivity.itemId)
        assertTrue(toolActivity.text.contains("Running ./gradlew"))
        assertTrue(toolActivity.isStreaming)
    }

    @Test
    fun `system text deltas keep command status and append streaming output`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "command-1",
                        speaker = ConversationSpeaker.SYSTEM,
                        kind = ConversationItemKind.COMMAND_EXECUTION,
                        text = "running pwd",
                        turnId = "turn-1",
                        itemId = "command-item",
                        isStreaming = true,
                        orderIndex = 1,
                    ),
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "command-1",
                    turnId = "turn-1",
                    itemId = "command-item",
                    delta = "/tmp/remodex\n",
                    kind = ConversationItemKind.COMMAND_EXECUTION,
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals(1, projected.size)
        assertTrue(projected.first().text.contains("running pwd"))
        assertTrue(projected.first().text.contains("/tmp/remodex"))
        assertTrue(projected.first().isStreaming)
    }

    @Test
    fun `assistant revert metadata survives later deltas and completion`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "assistant-1",
                        speaker = ConversationSpeaker.ASSISTANT,
                        kind = ConversationItemKind.CHAT,
                        text = "Started reply",
                        turnId = "turn-1",
                        itemId = "assistant-item",
                        orderIndex = 1,
                        assistantChangeSet = RemodexAssistantChangeSet(
                            id = "changeset-1",
                            repoRoot = "/tmp/remodex",
                            threadId = "thread-1",
                            turnId = "turn-1",
                            assistantMessageId = "assistant-1",
                            status = RemodexAssistantChangeSetStatus.READY,
                            source = RemodexAssistantChangeSetSource.TURN_DIFF,
                            forwardUnifiedPatch = """
                                diff --git a/src/App.kt b/src/App.kt
                                --- a/src/App.kt
                                +++ b/src/App.kt
                                @@ -1 +1 @@
                                -old
                                +new
                            """.trimIndent(),
                            fileChanges = listOf(
                                RemodexAssistantFileChange(
                                    path = "src/App.kt",
                                    additions = 1,
                                    deletions = 1,
                                ),
                            ),
                        ),
                    ),
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "Finished reply",
                    orderIndex = 2,
                ),
                TimelineMutation.Complete(messageId = "assistant-1"),
            ),
        )

        assertEquals(1, projected.size)
        assertNotNull(projected.first().assistantChangeSet)
        assertEquals("src/App.kt", projected.first().assistantChangeSet?.fileChanges?.first()?.path)
        assertFalse(projected.first().isStreaming)
    }

    @Test
    fun `project reorders single item turns so activity comes before assistant and file changes trail`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "user-1",
                    speaker = ConversationSpeaker.USER,
                    text = "Run the tests",
                    turnId = "turn-1",
                    orderIndex = 0,
                ),
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "Here is the result",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    orderIndex = 1,
                ),
                RemodexConversationItem(
                    id = "activity-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.TOOL_ACTIVITY,
                    text = "Searching app/src",
                    turnId = "turn-1",
                    itemId = "activity-item",
                    orderIndex = 2,
                ),
                RemodexConversationItem(
                    id = "file-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.FILE_CHANGE,
                    text = "src/App.kt",
                    turnId = "turn-1",
                    itemId = "file-item",
                    orderIndex = 3,
                ),
            ),
        )

        assertEquals(
            listOf("user-1", "activity-1", "assistant-1", "file-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project filters hidden push reset markers`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "visible-diff",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.FILE_CHANGE,
                    text = "Edited src/App.kt +2 -1",
                    orderIndex = 0,
                ),
                RemodexConversationItem(
                    id = "hidden-push-reset",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.CHAT,
                    text = "Reset per-chat diff totals after manual push",
                    itemId = "git.push.reset.marker",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals(listOf("visible-diff"), projected.map(RemodexConversationItem::id))
    }

    @Test
    fun `project collapses repeated thinking rows inside the same system segment`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "thinking-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = "Thinking...",
                    turnId = "turn-1",
                    orderIndex = 0,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "thinking-2",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = "Checking the Gradle task graph",
                    turnId = "turn-1",
                    orderIndex = 1,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(1, projected.size)
        assertEquals(ConversationItemKind.REASONING, projected.first().kind)
        assertEquals("Checking the Gradle task graph", projected.first().text)
        assertFalse(projected.first().isStreaming)
    }

    @Test
    fun `project prunes completed empty thinking placeholders`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "thinking-placeholder",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = "Thinking...",
                    turnId = "turn-1",
                    orderIndex = 1,
                    isStreaming = false,
                ),
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "Final answer",
                    turnId = "turn-1",
                    orderIndex = 2,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(listOf("assistant-1"), projected.map(RemodexConversationItem::id))
    }

    @Test
    fun `project removes duplicate assistant echoes when history repeats the same text`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-streaming",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "Finished answer",
                    turnId = "turn-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "assistant-history",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "Finished answer",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    orderIndex = 2,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(1, projected.size)
        assertEquals("assistant-streaming", projected.first().id)
    }

    @Test
    fun `project removes superseded file change rows once a finalized snapshot arrives`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "file-streaming",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.FILE_CHANGE,
                    text = "src/App.kt\nsrc/Main.kt",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "file-final",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.FILE_CHANGE,
                    text = "src/App.kt\nsrc/Main.kt",
                    turnId = "turn-1",
                    orderIndex = 2,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(listOf("file-final"), projected.map(RemodexConversationItem::id))
    }

    @Test
    fun `project upgrades placeholder subagent cards when populated agent rows arrive`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "subagent-placeholder",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.SUBAGENT_ACTION,
                    text = "Spawning 1 agent",
                    turnId = "turn-1",
                    itemId = "subagent-item",
                    orderIndex = 1,
                    isStreaming = true,
                    subagentAction = com.emanueledipietro.remodex.model.RemodexSubagentAction(
                        tool = "spawnAgent",
                        status = "running",
                    ),
                ),
                RemodexConversationItem(
                    id = "subagent-populated",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.SUBAGENT_ACTION,
                    text = "Spawning 1 agent",
                    turnId = "turn-1",
                    itemId = "subagent-item",
                    orderIndex = 2,
                    isStreaming = false,
                    subagentAction = com.emanueledipietro.remodex.model.RemodexSubagentAction(
                        tool = "spawnAgent",
                        status = "completed",
                        receiverThreadIds = listOf("child-thread-1"),
                    ),
                ),
            ),
        )

        assertEquals(1, projected.size)
        assertEquals("subagent-populated", projected.first().id)
        assertEquals(1, projected.first().subagentAction?.agentRows?.size)
    }
}
