package com.emanueledipietro.remodex.feature.turn

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
    fun `assistant text render mode stays lightweight until settling completes`() {
        val mode = resolveAssistantTextRenderMode(
            text = "completed response",
            isStreaming = false,
            richRenderArmed = false,
        )

        assertEquals(AssistantTextRenderMode.LIGHTWEIGHT_PLAIN, mode)
    }

    @Test
    fun `assistant text render mode upgrades to rich markdown after settling`() {
        val mode = resolveAssistantTextRenderMode(
            text = "completed response",
            isStreaming = false,
            richRenderArmed = true,
        )

        assertEquals(AssistantTextRenderMode.RICH_MARKDOWN, mode)
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
}
