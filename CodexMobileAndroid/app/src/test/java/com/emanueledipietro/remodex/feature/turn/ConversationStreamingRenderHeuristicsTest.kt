package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStreamingRenderHeuristicsTest {
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
    fun `streaming markdown preview allows lightweight inline formatting`() {
        assertTrue(
            shouldRenderStreamingMarkdownPreview(
                "Status is **ready** and run `./gradlew test`.",
            ),
        )
    }

    @Test
    fun `streaming markdown preview skips mermaid blocks`() {
        assertFalse(
            shouldRenderStreamingMarkdownPreview(
                """
                ```mermaid
                graph TD
                A-->B
                ```
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `streaming markdown preview skips markdown tables`() {
        assertFalse(
            shouldRenderStreamingMarkdownPreview(
                """
                | Name | Value |
                | --- | --- |
                | A | B |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `streaming markdown preview skips markdown images`() {
        assertFalse(
            shouldRenderStreamingMarkdownPreview(
                "Look at ![diagram](https://example.com/diagram.png)",
            ),
        )
    }

    @Test
    fun `streaming markdown preview skips very long content`() {
        val longText = buildString {
            repeat(3_501) {
                append('a')
            }
        }

        assertFalse(shouldRenderStreamingMarkdownPreview(longText))
    }
}
