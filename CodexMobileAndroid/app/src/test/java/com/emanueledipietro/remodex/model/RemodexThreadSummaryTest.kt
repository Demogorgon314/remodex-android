package com.emanueledipietro.remodex.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RemodexThreadSummaryTest {
    @Test
    fun `display title prefers explicit thread name like ios`() {
        val thread = threadSummary(
            title = "Conversation",
            name = "Local-first parity",
            preview = "fix sidebar title parity",
        )

        assertEquals("Local-first parity", thread.displayTitle)
    }

    @Test
    fun `display title falls back from generic placeholder to preview like ios`() {
        val thread = threadSummary(
            title = "Conversation",
            preview = "fix sidebar title parity",
        )

        assertEquals("Fix sidebar title parity", thread.displayTitle)
    }

    @Test
    fun `display title prefers subagent label when title is generic`() {
        val thread = threadSummary(
            title = "New Thread",
            preview = "",
            agentNickname = "Descartes",
            agentRole = "explorer",
        )

        assertEquals("Descartes [explorer]", thread.displayTitle)
    }

    @Test
    fun `display title falls back to ios default for brand new empty threads`() {
        val thread = threadSummary(
            title = "",
            preview = "",
        )

        assertEquals(RemodexThreadSummary.defaultDisplayTitle, thread.displayTitle)
    }

    private fun threadSummary(
        title: String,
        name: String? = null,
        preview: String,
        agentNickname: String? = null,
        agentRole: String? = null,
    ): RemodexThreadSummary {
        return RemodexThreadSummary(
            id = "thread-1",
            title = title,
            name = name,
            preview = preview,
            projectPath = "/tmp/remodex",
            lastUpdatedLabel = "Updated just now",
            isRunning = false,
            agentNickname = agentNickname,
            agentRole = agentRole,
            queuedDrafts = 0,
            runtimeLabel = "Auto",
            messages = emptyList(),
        )
    }
}
