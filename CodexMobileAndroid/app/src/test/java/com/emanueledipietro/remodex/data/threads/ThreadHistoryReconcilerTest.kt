package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThreadHistoryReconcilerTest {
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
}
