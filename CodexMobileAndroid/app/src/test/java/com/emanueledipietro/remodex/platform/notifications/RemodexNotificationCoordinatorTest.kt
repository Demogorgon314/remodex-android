package com.emanueledipietro.remodex.platform.notifications

import com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemodexNotificationCoordinatorTest {
    @Test
    fun `notification permission denial keeps completion events in-app without posting`() {
        val permissionChecker = FakePermissionChecker(enabled = false)
        val poster = RecordingPoster()
        val coordinator = RemodexNotificationCoordinator(
            permissionChecker = permissionChecker,
            poster = poster,
        )

        coordinator.onSessionSnapshot(sessionSnapshot(thread(isRunning = true)))
        coordinator.onSessionSnapshot(sessionSnapshot(thread(isRunning = false)))

        assertTrue(poster.events.isEmpty())
    }

    @Test
    fun `granted permission posts attention-needed notifications for the relevant thread`() {
        val poster = RecordingPoster()
        val coordinator = RemodexNotificationCoordinator(
            permissionChecker = FakePermissionChecker(enabled = true),
            poster = poster,
        )

        coordinator.onSessionSnapshot(
            sessionSnapshot(
                thread(
                    messages = listOf(
                        conversationItem(
                            id = "message-1",
                            text = "Still streaming",
                        ),
                    ),
                ),
            ),
        )
        coordinator.onSessionSnapshot(
            sessionSnapshot(
                thread(
                    messages = listOf(
                        conversationItem(
                            id = "message-1",
                            text = "Still streaming",
                        ),
                        conversationItem(
                            id = "message-2",
                            text = "Needs attention from Android",
                            kind = ConversationItemKind.COMMAND_EXECUTION,
                            speaker = ConversationSpeaker.SYSTEM,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, poster.events.size)
        assertEquals("thread-1", poster.events.single().threadId)
        assertEquals(RemodexThreadNotificationType.ATTENTION_NEEDED, poster.events.single().type)
    }

    private class FakePermissionChecker(
        private val enabled: Boolean,
    ) : NotificationPermissionChecker {
        override fun canPostNotifications(): Boolean = enabled
    }

    private class RecordingPoster : RemodexThreadNotificationPoster {
        val events = mutableListOf<RemodexThreadNotificationEvent>()

        override fun createChannels() = Unit

        override fun post(event: RemodexThreadNotificationEvent) {
            events += event
        }
    }
}

private fun sessionSnapshot(vararg threads: RemodexThreadSummary): RemodexSessionSnapshot {
    return RemodexSessionSnapshot(
        threads = threads.toList(),
        selectedThreadId = threads.firstOrNull()?.id,
    )
}

private fun thread(
    id: String = "thread-1",
    title: String = "Android notifications",
    isRunning: Boolean = false,
    messages: List<RemodexConversationItem> = emptyList(),
): RemodexThreadSummary {
    return RemodexThreadSummary(
        id = id,
        title = title,
        preview = "Preview",
        projectPath = "/tmp/remodex",
        lastUpdatedLabel = "Updated just now",
        isRunning = isRunning,
        queuedDrafts = 0,
        runtimeLabel = "Auto, medium reasoning",
        messages = messages,
    )
}

private fun conversationItem(
    id: String,
    text: String,
    kind: ConversationItemKind = ConversationItemKind.CHAT,
    speaker: ConversationSpeaker = ConversationSpeaker.ASSISTANT,
): RemodexConversationItem {
    return RemodexConversationItem(
        id = id,
        text = text,
        kind = kind,
        speaker = speaker,
    )
}
