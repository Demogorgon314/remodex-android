package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
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

    @Test
    fun `merge history items matches command execution by item id before turn fallback`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "command-local-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-1",
                orderIndex = 1L,
            ),
            RemodexConversationItem(
                id = "command-local-2",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran ls",
                turnId = "turn-1",
                itemId = "command-item-2",
                orderIndex = 2L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "command-history-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-1",
                orderIndex = 1L,
            ),
            RemodexConversationItem(
                id = "command-history-2",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran ls",
                turnId = "turn-1",
                itemId = "command-item-2",
                orderIndex = 2L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(listOf("command-item-1", "command-item-2"), merged.map(RemodexConversationItem::itemId))
        assertEquals(listOf(1L, 2L), merged.map(RemodexConversationItem::orderIndex))
    }

    @Test
    fun `merge history items keeps local command order index when history snapshot arrives later`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "command-local-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-1",
                orderIndex = 3L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "command-history-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-1",
                orderIndex = 99L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals(3L, merged.single().orderIndex)
        assertEquals("command-item-1", merged.single().itemId)
    }

    @Test
    fun `merge history items keeps local command item id when preview match rebinding would reorder later`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "command-local-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-local",
                orderIndex = 3L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "command-history-1",
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = "Ran pwd",
                turnId = "turn-1",
                itemId = "command-item-server",
                orderIndex = 99L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals("command-item-local", merged.single().itemId)
        assertEquals(3L, merged.single().orderIndex)
    }

    @Test
    fun `merge history items collapses image sends when relay elides inline history images`() {
        val existing = listOf(
            RemodexConversationItem(
                id = "user-local-image",
                speaker = ConversationSpeaker.USER,
                text = "这是什么",
                turnId = "turn-1",
                deliveryState = RemodexMessageDeliveryState.CONFIRMED,
                attachments = listOf(
                    RemodexConversationAttachment(
                        id = "local-attachment",
                        uriString = "content://media/external/images/media/1",
                        displayName = "1000012658.jpg",
                    ),
                ),
                orderIndex = 20L,
            ),
        )
        val history = listOf(
            RemodexConversationItem(
                id = "user-history-image",
                speaker = ConversationSpeaker.USER,
                text = "这是什么",
                turnId = "turn-1",
                attachments = listOf(
                    RemodexConversationAttachment(
                        id = "history-attachment",
                        uriString = "remodex://history-image-elided",
                        displayName = "history-image-elided",
                    ),
                ),
                orderIndex = 10L,
            ),
        )

        val merged = ThreadHistoryReconciler.mergeHistoryItems(
            existing = existing,
            history = history,
            threadIsActive = false,
            threadIsRunning = false,
        )

        assertEquals(1, merged.size)
        assertEquals("user-local-image", merged.single().id)
        assertEquals(RemodexMessageDeliveryState.CONFIRMED, merged.single().deliveryState)
        assertEquals("turn-1", merged.single().turnId)
    }
}
