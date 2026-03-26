package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingDisclosureParserTest {
    @Test
    fun `normalized thinking content strips leading thinking label`() {
        assertEquals(
            "Inspecting the reducer",
            ThinkingDisclosureParser.normalizedThinkingContent(
                """
                Thinking...

                Inspecting the reducer
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `parse turns bold summary lines into disclosure sections`() {
        val parsed = ThinkingDisclosureParser.parse(
            """
            **Inspect code**
            Read the reducer implementation.
            
            **Plan update**
            Compare item ordering with iOS.
            """.trimIndent(),
        )

        assertTrue(parsed.showsDisclosure)
        assertEquals(2, parsed.sections.size)
        assertEquals("Inspect code", parsed.sections[0].title)
        assertEquals("Read the reducer implementation.", parsed.sections[0].detail)
        assertEquals("Plan update", parsed.sections[1].title)
    }

    @Test
    fun `parse merges preamble into the first disclosure section`() {
        val parsed = ThinkingDisclosureParser.parse(
            """
            Inspecting the timeline output.
            
            **Check ordering**
            Compare user, tool, and assistant rows.
            """.trimIndent(),
        )

        assertEquals(1, parsed.sections.size)
        assertTrue(parsed.sections[0].detail.contains("Inspecting the timeline output."))
        assertTrue(parsed.sections[0].detail.contains("Compare user, tool, and assistant rows."))
    }

    @Test
    fun `compact activity preview returns latest activity line when reasoning is activity only`() {
        assertEquals(
            "completed rg app/src",
            ThinkingDisclosureParser.compactActivityPreview(
                """
                running rg app/src
                completed rg app/src
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `compact activity preview returns null for normal reasoning prose`() {
        assertNull(
            ThinkingDisclosureParser.compactActivityPreview(
                """
                Compare the Android reducer.
                Then update the Compose layout.
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `parse falls back to raw normalized text when there are no disclosure headers`() {
        val parsed = ThinkingDisclosureParser.parse("Inspecting the reducer state transitions.")

        assertFalse(parsed.showsDisclosure)
        assertEquals("Inspecting the reducer state transitions.", parsed.fallbackText)
    }
}
