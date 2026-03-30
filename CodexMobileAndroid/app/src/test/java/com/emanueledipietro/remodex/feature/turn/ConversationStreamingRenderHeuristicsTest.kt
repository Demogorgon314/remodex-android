package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertEquals
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
}
