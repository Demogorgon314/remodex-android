package com.emanueledipietro.remodex.platform.notifications

import com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexApprovalRequest
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemodexNotificationCoordinatorTest {
    @Test
    fun `completion notifications use display title instead of raw fallback title`() {
        val events = detectThreadNotificationEvents(
            previousSnapshot = sessionSnapshot(
                thread(
                    title = "Conversation",
                    name = "Local project",
                    isRunning = true,
                ),
            ),
            currentSnapshot = sessionSnapshot(
                thread(
                    title = "Conversation",
                    name = "Local project",
                    isRunning = false,
                ),
            ),
        )

        assertEquals(1, events.size)
        assertEquals("Local project", events.single().threadTitle)
        assertEquals("Local project is ready", events.single().title)
    }

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

    @Test
    fun `background approval requests post approval notifications`() {
        val events = detectThreadNotificationEvents(
            previousSnapshot = sessionSnapshot(
                thread(
                    id = "thread-2",
                    title = "Approval thread",
                ),
            ),
            currentSnapshot = sessionSnapshot(
                thread(
                    id = "thread-2",
                    title = "Approval thread",
                    isWaitingOnApproval = true,
                ),
                pendingApprovalRequest = RemodexApprovalRequest(
                    id = "approval-1",
                    requestId = JsonPrimitive("req-1"),
                    method = "item/commandExecution/requestApproval",
                    command = "git status",
                    reason = "Need to inspect the repo state.",
                    threadId = "thread-2",
                ),
            ),
            isAppForeground = false,
        )

        assertEquals(1, events.size)
        assertEquals(RemodexThreadNotificationType.APPROVAL_NEEDED, events.single().type)
        assertEquals("thread-2", events.single().threadId)
        assertEquals("approval-1", events.single().requestId)
    }

    @Test
    fun `foreground approval requests stay in app without background notification`() {
        val events = detectThreadNotificationEvents(
            previousSnapshot = sessionSnapshot(
                thread(
                    id = "thread-2",
                    title = "Approval thread",
                ),
            ),
            currentSnapshot = sessionSnapshot(
                thread(
                    id = "thread-2",
                    title = "Approval thread",
                    isWaitingOnApproval = true,
                ),
                pendingApprovalRequest = RemodexApprovalRequest(
                    id = "approval-1",
                    requestId = JsonPrimitive("req-1"),
                    method = "item/commandExecution/requestApproval",
                    threadId = "thread-2",
                ),
            ),
            isAppForeground = true,
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `moving approval request back into foreground clears the background notification`() {
        val poster = RecordingPoster()
        val foregroundStateProvider = object : AppForegroundStateProvider {
            override fun isAppForeground(): Boolean = true
        }
        val coordinator = RemodexNotificationCoordinator(
            permissionChecker = FakePermissionChecker(enabled = true),
            poster = poster,
            appForegroundStateProvider = foregroundStateProvider,
        )

        coordinator.onSessionSnapshot(
            sessionSnapshot(
                thread(
                    id = "thread-2",
                    title = "Approval thread",
                    isWaitingOnApproval = true,
                ),
                pendingApprovalRequest = RemodexApprovalRequest(
                    id = "approval-1",
                    requestId = JsonPrimitive("req-1"),
                    method = "item/commandExecution/requestApproval",
                    threadId = "thread-2",
                ),
            ),
        )
        coordinator.onSessionSnapshot(
            sessionSnapshot(
                thread(
                    id = "thread-2",
                    title = "Approval thread",
                    isWaitingOnApproval = true,
                ),
                pendingApprovalRequest = RemodexApprovalRequest(
                    id = "approval-1",
                    requestId = JsonPrimitive("req-1"),
                    method = "item/commandExecution/requestApproval",
                    threadId = "thread-2",
                ),
            ),
        )

        assertEquals(
            listOf(
                remodexNotificationId(
                    threadId = "thread-2",
                    type = RemodexThreadNotificationType.APPROVAL_NEEDED,
                    requestId = "approval-1",
                ),
            ),
            poster.canceledNotificationIds,
        )
        assertTrue(poster.events.isEmpty())
    }

    private class FakePermissionChecker(
        private val enabled: Boolean,
    ) : NotificationPermissionChecker {
        override fun canPostNotifications(): Boolean = enabled
    }

    private class RecordingPoster : RemodexThreadNotificationPoster {
        val events = mutableListOf<RemodexThreadNotificationEvent>()
        val canceledNotificationIds = mutableListOf<Int>()

        override fun createChannels() = Unit

        override fun post(event: RemodexThreadNotificationEvent) {
            events += event
        }

        override fun cancel(notificationId: Int) {
            canceledNotificationIds += notificationId
        }
    }
}

private fun sessionSnapshot(
    vararg threads: RemodexThreadSummary,
    pendingApprovalRequest: RemodexApprovalRequest? = null,
): RemodexSessionSnapshot {
    return RemodexSessionSnapshot(
        threads = threads.toList(),
        selectedThreadId = threads.firstOrNull()?.id,
        pendingApprovalRequest = pendingApprovalRequest,
    )
}

private fun thread(
    id: String = "thread-1",
    title: String = "Android notifications",
    name: String? = null,
    isRunning: Boolean = false,
    isWaitingOnApproval: Boolean = false,
    messages: List<RemodexConversationItem> = emptyList(),
): RemodexThreadSummary {
    return RemodexThreadSummary(
        id = id,
        title = title,
        name = name,
        preview = "Preview",
        projectPath = "/tmp/remodex",
        lastUpdatedLabel = "Updated just now",
        isRunning = isRunning,
        isWaitingOnApproval = isWaitingOnApproval,
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
