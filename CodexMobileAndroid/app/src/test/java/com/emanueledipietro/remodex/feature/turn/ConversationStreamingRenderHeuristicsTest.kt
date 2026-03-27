package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStreamingRenderHeuristicsTest {
    @Test
    fun `plain prose uses lightweight streaming renderer`() {
        assertTrue(shouldUseLightweightStreamingAssistantText("This is a plain streaming paragraph."))
    }

    @Test
    fun `markdown links force full renderer`() {
        assertFalse(shouldUseLightweightStreamingAssistantText("See [README](README.md) for details."))
    }

    @Test
    fun `code fences force full renderer`() {
        assertFalse(
            shouldUseLightweightStreamingAssistantText(
                """
                ```kotlin
                println("hello")
                ```
                """.trimIndent(),
            ),
        )
    }
}
