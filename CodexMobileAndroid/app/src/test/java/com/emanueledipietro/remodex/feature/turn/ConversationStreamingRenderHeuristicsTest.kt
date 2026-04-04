package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexPlanState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStreamingRenderHeuristicsTest {
    @Test
    fun `assistant text render mode stays lightweight while streaming`() {
        val mode = resolveAssistantTextRenderMode(
            text = "partial response",
            isStreaming = true,
            richRenderArmed = true,
        )

        assertEquals(AssistantTextRenderMode.LIGHTWEIGHT_PLAIN, mode)
    }

    @Test
    fun `assistant text render mode upgrades to rich markdown as soon as streaming completes`() {
        val mode = resolveAssistantTextRenderMode(
            text = "completed response",
            isStreaming = false,
            richRenderArmed = true,
        )

        assertEquals(AssistantTextRenderMode.RICH_MARKDOWN, mode)
    }

    @Test
    fun `assistant text render mode renders completed plain prose as rich markdown`() {
        val mode = resolveAssistantTextRenderMode(
            text = "completed response",
            isStreaming = false,
            richRenderArmed = true,
        )

        assertEquals(AssistantTextRenderMode.RICH_MARKDOWN, mode)
    }

    @Test
    fun `assistant text render mode upgrades to rich markdown after settling for markdown content`() {
        val mode = resolveAssistantTextRenderMode(
            text = """
            ## Summary
            - first
            - second
            """.trimIndent(),
            isStreaming = false,
            richRenderArmed = true,
        )

        assertEquals(AssistantTextRenderMode.RICH_MARKDOWN, mode)
    }

    @Test
    fun `assistant rich markdown preference ignores plain prose`() {
        assertFalse(assistantTextPrefersRichMarkdown("This is a normal paragraph."))
    }

    @Test
    fun `assistant rich markdown preference detects markdown structure`() {
        assertTrue(
            assistantTextPrefersRichMarkdown(
                """
                ```kotlin
                println("hi")
                ```
                """.trimIndent(),
            ),
        )
        assertTrue(assistantTextPrefersRichMarkdown("1. first\n2. second"))
    }

    @Test
    fun `rich render armed state is not reset for any settled assistant content`() {
        assertFalse(
            shouldResetAssistantRichRenderArmed(
                hasText = true,
                isStreaming = false,
                prefersRichMarkdown = true,
            ),
        )
    }

    @Test
    fun `rich render armed state resets while streaming or when text is absent`() {
        assertTrue(
            shouldResetAssistantRichRenderArmed(
                hasText = true,
                isStreaming = true,
                prefersRichMarkdown = true,
            ),
        )
        assertTrue(
            shouldResetAssistantRichRenderArmed(
                hasText = false,
                isStreaming = false,
                prefersRichMarkdown = false,
            ),
        )
    }

    @Test
    fun `streaming plain text display leaves tabs and newlines untouched`() {
        val formatted = formatStreamingPlainTextForDisplay("A\n\tB")

        assertEquals("A\n\tB", formatted)
    }

    @Test
    fun `streaming plain text display leaves mermaid fences untouched`() {
        val formatted = formatStreamingPlainTextForDisplay(
            """
            ```mermaid
            graph TD
            A-->B
            ```
            """.trimIndent(),
        )

        assertEquals(
            """
            ```mermaid
            graph TD
            A-->B
            ```
            """.trimIndent(),
            formatted,
        )
    }

    @Test
    fun `attachment thumbnails skip desktop file uris`() {
        assertFalse(
            canRenderAttachmentThumbnail(
                "file:/Users/wangkai/chat_timeline_concept_search_based_20260331.png",
            ),
        )
    }

    @Test
    fun `attachment thumbnails allow android accessible uris`() {
        assertTrue(canRenderAttachmentThumbnail("content://media/external/images/media/1"))
        assertTrue(canRenderAttachmentThumbnail("https://example.com/image.png"))
        assertTrue(canRenderAttachmentThumbnail("file:///storage/emulated/0/Pictures/test.png"))
    }

    @Test
    fun `markdown prewarm requests include stable assistant prose along with markdown history`() {
        val requests = selectConversationMarkdownPrewarmRequests(
            timelineItems = listOf(
                RemodexConversationItem(
                    id = "user-1",
                    speaker = ConversationSpeaker.USER,
                    text = "hello",
                ),
                RemodexConversationItem(
                    id = "assistant-plain",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "plain prose only",
                ),
                RemodexConversationItem(
                    id = "assistant-md",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "## Summary\n- one",
                ),
                RemodexConversationItem(
                    id = "assistant-streaming",
                    speaker = ConversationSpeaker.ASSISTANT,
                    text = "```kotlin\nprintln(1)\n```",
                    isStreaming = true,
                ),
                RemodexConversationItem(
                    id = "system-plan",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.PLAN,
                    text = "",
                    planState = RemodexPlanState(
                        steps = emptyList(),
                        explanation = null,
                    ),
                    supportingText = "Plan body",
                ),
                RemodexConversationItem(
                    id = "system-chat",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.CHAT,
                    text = "```json\n{}\n```",
                ),
            ),
            maxItems = 4,
        )

        assertEquals(listOf("assistant-plain", "assistant-md", "system-plan", "system-chat"), requests.map { it.messageId })
        assertEquals(
            listOf(
                ConversationMarkdownPrewarmStyle.PRIMARY_BODY,
                ConversationMarkdownPrewarmStyle.PRIMARY_BODY,
                ConversationMarkdownPrewarmStyle.PRIMARY_BODY,
                ConversationMarkdownPrewarmStyle.SECONDARY_BODY,
            ),
            requests.map { it.style },
        )
    }

    @Test
    fun `markdown prewarm requests expand detailed reasoning content`() {
        val requests = selectConversationMarkdownPrewarmRequests(
            timelineItems = listOf(
                RemodexConversationItem(
                    id = "reasoning",
                    speaker = ConversationSpeaker.SYSTEM,
                    kind = ConversationItemKind.REASONING,
                    text = """
                    Goal
                    - do thing

                    Detail
                    ```kotlin
                    println("hi")
                    ```
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { it.messageId.startsWith("reasoning:") })
    }

    @Test
    fun `visible timeline window keeps only tail slice and offsets active turn anchor`() {
        val messages = (1..50).map { index ->
            RemodexConversationItem(
                id = "message-$index",
                speaker = ConversationSpeaker.ASSISTANT,
                text = "message $index",
            )
        }

        val window = buildVisibleConversationTimelineWindow(
            timelineItems = messages,
            activeTurnAnchorIndex = 45,
            visibleTailCount = 40,
        )

        assertEquals(10, window.hiddenItemCount)
        assertTrue(window.hasEarlierMessages)
        assertEquals("message-11", window.items.first().id)
        assertEquals("message-50", window.items.last().id)
        assertEquals(41, window.bottomAnchorIndex)
        assertEquals(36, window.activeTurnAnchorIndex)
    }

    @Test
    fun `visible timeline window hides active turn anchor when it is outside tail slice`() {
        val messages = (1..50).map { index ->
            RemodexConversationItem(
                id = "message-$index",
                speaker = ConversationSpeaker.SYSTEM,
                text = "message $index",
            )
        }

        val window = buildVisibleConversationTimelineWindow(
            timelineItems = messages,
            activeTurnAnchorIndex = 3,
            visibleTailCount = 40,
        )

        assertEquals(null, window.activeTurnAnchorIndex)
    }
}
