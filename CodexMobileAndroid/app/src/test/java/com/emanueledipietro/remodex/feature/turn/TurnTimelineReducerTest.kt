package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.data.threads.TimelineMutation
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSet
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetSource
import com.emanueledipietro.remodex.model.RemodexAssistantChangeSetStatus
import com.emanueledipietro.remodex.model.RemodexAssistantFileChange
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.ConversationSystemTurnOrderingHint
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
    fun `assistant deltas preserve repeated markdown fence characters across chunk boundaries`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "``",
                    orderIndex = 1,
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "`kotlin\nprintln(1)\n```",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals(
            "```kotlin\nprintln(1)\n```",
            projected.first().text,
        )
    }

    @Test
    fun `assistant deltas preserve repeated single character chunks`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "`",
                    orderIndex = 1,
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    delta = "`",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals("``", projected.first().text)
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
    fun `reasoning deltas preserve repeated markdown fence characters across chunk boundaries`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.ReasoningTextDelta(
                    messageId = "reasoning-1",
                    turnId = "turn-1",
                    itemId = "reasoning-item",
                    delta = "``",
                    orderIndex = 1,
                ),
                TimelineMutation.ReasoningTextDelta(
                    messageId = "reasoning-1",
                    turnId = "turn-1",
                    itemId = "reasoning-item",
                    delta = "`thinking",
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals("```thinking", projected.first().text)
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
    fun `active turn anchor stays unset until an assistant response exists in the active turn`() {
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

        assertEquals(null, TurnTimelineReducer.activeTurnAnchorIndex(items, activeTurnId = "turn-2"))
    }

    @Test
    fun `active turn anchor stays unset when no active turn assistant response is available`() {
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

        assertEquals(null, TurnTimelineReducer.activeTurnAnchorIndex(items, activeTurnId = null))
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
    fun `projected fast path updates coalesced assistant upserts in place`() {
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
            mutation = TimelineMutation.Upsert(
                assistant.copy(
                    text = "First chunkSecond chunk",
                    isStreaming = true,
                ),
            ),
        )

        assertNotNull(next)
        assertTrue(next!![0] === user)
        assertTrue(next[1] !== assistant)
        assertEquals("First chunkSecond chunk", next[1].text)
        assertTrue(next[1].isStreaming)
    }

    @Test
    fun `projected list fast path updates assistant text-only replacements`() {
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

        val previousTimeline = listOf(user, assistant)
        val previousProjected = listOf(user, assistant)
        val nextTimeline = listOf(
            user,
            assistant.copy(
                text = "First chunkSecond chunk",
                isStreaming = false,
            ),
        )

        val nextProjected = TurnTimelineReducer.applyProjectedListFastPath(
            previousTimelineItems = previousTimeline,
            nextTimelineItems = nextTimeline,
            previousProjectedItems = previousProjected,
        )

        assertNotNull(nextProjected)
        assertTrue(nextProjected!![0] === user)
        assertEquals("First chunkSecond chunk", nextProjected[1].text)
        assertFalse(nextProjected[1].isStreaming)
    }

    @Test
    fun `projected list fast path falls back when assistant reorder can affect turn layout`() {
        val reasoning = RemodexConversationItem(
            id = "reasoning-1",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.REASONING,
            text = "Thinking",
            turnId = "turn-1",
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

        val previousTimeline = listOf(reasoning, assistant)
        val previousProjected = TurnTimelineReducer.project(previousTimeline)
        val nextTimeline = listOf(
            reasoning,
            assistant.copy(
                text = "First chunkSecond chunk",
                orderIndex = 2,
            ),
        )

        val nextProjected = TurnTimelineReducer.applyProjectedListFastPath(
            previousTimelineItems = previousTimeline,
            nextTimelineItems = nextTimeline,
            previousProjectedItems = previousProjected,
        )

        assertEquals(null, nextProjected)
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
                    systemTurnOrderingHint = ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE,
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
        assertEquals(
            ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE,
            toolActivity.systemTurnOrderingHint,
        )
    }

    @Test
    fun `visible codex system activity items keep their intended intra turn ordering`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "Final answer",
                    turnId = "turn-1",
                    itemId = "assistant-item",
                    orderIndex = 9,
                ),
                RemodexConversationItem(
                    id = "search-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.WEB_SEARCH,
                    text = "Searched the web",
                    turnId = "turn-1",
                    itemId = "search-item",
                    orderIndex = 3,
                ),
                RemodexConversationItem(
                    id = "reasoning-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = "Inspecting available tools",
                    turnId = "turn-1",
                    itemId = "reasoning-item",
                    orderIndex = 1,
                ),
                RemodexConversationItem(
                    id = "mcp-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.MCP_TOOL_CALL,
                    text = "Called web/search_query",
                    turnId = "turn-1",
                    itemId = "mcp-item",
                    orderIndex = 2,
                ),
                RemodexConversationItem(
                    id = "image-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.IMAGE_GENERATION,
                    text = "Generated Image",
                    turnId = "turn-1",
                    itemId = "image-item",
                    orderIndex = 4,
                ),
            ),
        )

        assertEquals(
            listOf("reasoning-1", "mcp-1", "search-1", "image-1", "assistant-1"),
            projected.map(RemodexConversationItem::id),
        )
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
    fun `file change deltas preserve repeated diff fence characters across chunk boundaries`() {
        val projected = TurnTimelineReducer.reduce(
            listOf(
                TimelineMutation.SystemTextDelta(
                    messageId = "file-1",
                    turnId = "turn-1",
                    itemId = "file-item",
                    delta = "@@",
                    kind = ConversationItemKind.FILE_CHANGE,
                    orderIndex = 1,
                ),
                TimelineMutation.SystemTextDelta(
                    messageId = "file-1",
                    turnId = "turn-1",
                    itemId = "file-item",
                    delta = "@ -1 +1 @@\n+value",
                    kind = ConversationItemKind.FILE_CHANGE,
                    orderIndex = 1,
                ),
            ),
        )

        assertEquals("@@@ -1 +1 @@\n+value", projected.first().text)
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
    fun `project preserves later command activity after assistant in partial interleaved turns`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "thinking-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = "Inspecting the repository",
                    turnId = "turn-1",
                    itemId = "thinking-item-1",
                    orderIndex = 0,
                ),
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "First response",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                ),
                RemodexConversationItem(
                    id = "command-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.COMMAND_EXECUTION,
                    text = "completed git status --short",
                    turnId = "turn-1",
                    itemId = "command-item-1",
                    orderIndex = 2,
                ),
            ),
        )

        assertEquals(
            listOf("thinking-1", "assistant-1", "command-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project preserves later thinking after assistant when reasoning arrives late`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "First response",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "thinking-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = "Inspecting follow-up details",
                    turnId = "turn-1",
                    itemId = "thinking-item-1",
                    orderIndex = 2,
                    isStreaming = true,
                ),
            ),
        )

        assertEquals(
            listOf("assistant-1", "thinking-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project preserves chronological steer follow up user messages within the same turn`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "user-initial",
                    speaker = ConversationSpeaker.USER,
                    kind = ConversationItemKind.CHAT,
                    text = "Initial prompt",
                    turnId = "turn-1",
                    orderIndex = 0,
                ),
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "Streaming answer so far",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "user-steer",
                    speaker = ConversationSpeaker.USER,
                    kind = ConversationItemKind.CHAT,
                    text = "Please focus on the Android fix",
                    turnId = "turn-1",
                    orderIndex = 2,
                ),
                RemodexConversationItem(
                    id = "assistant-2",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "Following the steer",
                    turnId = "turn-1",
                    itemId = "assistant-item-2",
                    orderIndex = 3,
                    isStreaming = true,
                ),
            ),
        )

        assertEquals(
            listOf("user-initial", "assistant-1", "user-steer", "assistant-2"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project preserves command after assistant when a new activity item starts later in the turn`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "First response chunk",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "command-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.COMMAND_EXECUTION,
                    text = "running rg -n activeTurnIdByThread",
                    turnId = "turn-1",
                    itemId = "command-item-1",
                    orderIndex = 2,
                    isStreaming = true,
                ),
            ),
        )

        assertEquals(
            listOf("assistant-1", "command-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project preserves web search after assistant when a new activity item starts later in the turn`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "First response chunk",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "search-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.WEB_SEARCH,
                    text = "Searching the web",
                    turnId = "turn-1",
                    itemId = "search-item-1",
                    orderIndex = 2,
                    isStreaming = true,
                ),
            ),
        )

        assertEquals(
            listOf("assistant-1", "search-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project preserves explicit tool activity after assistant when a new item starts later in the turn`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "First response chunk",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "tool-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.TOOL_ACTIVITY,
                    text = "Searching app/src",
                    turnId = "turn-1",
                    itemId = "tool-item-1",
                    orderIndex = 2,
                    isStreaming = true,
                    systemTurnOrderingHint = ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE,
                ),
            ),
        )

        assertEquals(
            listOf("assistant-1", "tool-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project preserves mcp tool call after assistant when a new activity item starts later in the turn`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "First response chunk",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "mcp-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.MCP_TOOL_CALL,
                    text = "Calling browser/open_page",
                    turnId = "turn-1",
                    itemId = "mcp-item-1",
                    orderIndex = 2,
                    isStreaming = true,
                ),
            ),
        )

        assertEquals(
            listOf("assistant-1", "mcp-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project preserves image generation after assistant when a new activity item starts later in the turn`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "First response chunk",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "image-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.IMAGE_GENERATION,
                    text = "Generated Image",
                    turnId = "turn-1",
                    itemId = "image-item-1",
                    orderIndex = 2,
                    isStreaming = true,
                ),
            ),
        )

        assertEquals(
            listOf("assistant-1", "image-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project preserves subagent action after assistant when a new activity item starts later in the turn`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "First response chunk",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "subagent-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.SUBAGENT_ACTION,
                    text = "Spawned 1 helper agent",
                    turnId = "turn-1",
                    itemId = "subagent-item-1",
                    orderIndex = 2,
                    isStreaming = true,
                ),
            ),
        )

        assertEquals(
            listOf("assistant-1", "subagent-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `reduce projected keeps assistant anchored before later command when subsequent deltas arrive`() {
        val projected = TurnTimelineReducer.reduceProjected(
            listOf(
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "thinking-1",
                        speaker = ConversationSpeaker.SYSTEM,
                        kind = ConversationItemKind.REASONING,
                        text = "Inspecting the repository",
                        turnId = "turn-1",
                        itemId = "thinking-item-1",
                        orderIndex = 0,
                    ),
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    delta = "I will first inspect the codebase",
                    orderIndex = 1,
                ),
                TimelineMutation.Upsert(
                    RemodexConversationItem(
                        id = "command-1",
                        speaker = ConversationSpeaker.SYSTEM,
                        kind = ConversationItemKind.COMMAND_EXECUTION,
                        text = "running rg -n activeTurnIdByThread",
                        turnId = "turn-1",
                        itemId = "command-item-1",
                        orderIndex = 2,
                    ),
                ),
                TimelineMutation.AssistantTextDelta(
                    messageId = "assistant-1",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    delta = " and compare the iOS flow",
                    orderIndex = 3,
                ),
            ),
        )

        assertEquals(
            listOf("thinking-1", "assistant-1", "command-1"),
            projected.map(RemodexConversationItem::id),
        )
        assertEquals(1L, projected.first { it.id == "assistant-1" }.orderIndex)
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
    fun `project keeps completed empty thinking placeholders to match ios timeline projection`() {
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

        assertEquals(
            listOf("thinking-placeholder", "assistant-1"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project drops completed rollout thinking placeholder when same turn already has authoritative content`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "command-1",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.COMMAND_EXECUTION,
                    text = "Read app/DefaultRemodexAppRepository.kt",
                    turnId = "turn-1",
                    itemId = "command-item-1",
                    orderIndex = 1,
                ),
                RemodexConversationItem(
                    id = "thinking-rollout",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = "Thinking...",
                    turnId = "turn-1",
                    itemId = "rollout-thinking:thread-1:turn-1",
                    orderIndex = 2,
                    isStreaming = false,
                ),
                RemodexConversationItem(
                    id = "assistant-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    kind = ConversationItemKind.CHAT,
                    text = "Final answer",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 3,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(
            listOf("command-1", "assistant-1"),
            projected.map(RemodexConversationItem::id),
        )
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
    fun `project keeps distinct stable assistant items with the same text when neither is a review summary`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "assistant-item-1",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "Same text",
                    turnId = "turn-1",
                    itemId = "assistant-item-1",
                    orderIndex = 1,
                    isStreaming = false,
                ),
                RemodexConversationItem(
                    id = "assistant-item-2",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "Same text",
                    turnId = "turn-1",
                    itemId = "assistant-item-2",
                    orderIndex = 2,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(
            listOf("assistant-item-1", "assistant-item-2"),
            projected.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `project removes duplicate review summary echo when review turn emits the same stable assistant text twice`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "review-summary-assistant",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "No discrete regressions found.",
                    turnId = "turn-review-1",
                    itemId = "turn-review-1",
                    orderIndex = 2,
                    isStreaming = false,
                ),
                RemodexConversationItem(
                    id = "review-rollout-assistant",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "No discrete regressions found.",
                    turnId = "turn-review-1",
                    itemId = "review_rollout_assistant",
                    orderIndex = 6,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(
            listOf("review-summary-assistant"),
            projected.map(RemodexConversationItem::id),
        )
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
    fun `project removes duplicate file change rows when finalized relative path payload supersedes absolute path recap`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "file-absolute",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.FILE_CHANGE,
                    text = """
                        Edited /Users/wangkai/Developer/github/remodex/CodexMobileAndroid/app/src/main/java/com/emanueledipietro/remodex/feature/turn/ConversationScreen.kt
                    """.trimIndent(),
                    turnId = "turn-1",
                    orderIndex = 1,
                    isStreaming = false,
                ),
                RemodexConversationItem(
                    id = "file-relative",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.FILE_CHANGE,
                    text = """
                        Edited CodexMobileAndroid/app/src/main/java/com/emanueledipietro/remodex/feature/turn/ConversationScreen.kt +12 -11
                    """.trimIndent(),
                    turnId = "turn-1",
                    orderIndex = 2,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(listOf("file-relative"), projected.map(RemodexConversationItem::id))
    }

    @Test
    fun `project keeps distinct file change rows when paths differ`() {
        val projected = TurnTimelineReducer.project(
            listOf(
                RemodexConversationItem(
                    id = "file-client",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.FILE_CHANGE,
                    text = "Edited client/src/App.kt +2 -1",
                    turnId = "turn-1",
                    orderIndex = 1,
                    isStreaming = false,
                ),
                RemodexConversationItem(
                    id = "file-server",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.FILE_CHANGE,
                    text = "Edited server/src/App.kt +3 -2",
                    turnId = "turn-1",
                    orderIndex = 2,
                    isStreaming = false,
                ),
            ),
        )

        assertEquals(listOf("file-client", "file-server"), projected.map(RemodexConversationItem::id))
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
