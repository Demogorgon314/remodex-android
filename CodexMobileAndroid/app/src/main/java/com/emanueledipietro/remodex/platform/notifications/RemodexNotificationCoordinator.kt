package com.emanueledipietro.remodex.platform.notifications

import com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

enum class RemodexThreadNotificationType {
    TURN_COMPLETED,
    ATTENTION_NEEDED,
}

data class RemodexThreadNotificationEvent(
    val threadId: String,
    val threadTitle: String,
    val title: String,
    val body: String,
    val type: RemodexThreadNotificationType,
)

interface NotificationPermissionChecker {
    fun canPostNotifications(): Boolean
}

interface RemodexThreadNotificationPoster {
    fun createChannels()

    fun post(event: RemodexThreadNotificationEvent)
}

class RemodexNotificationCoordinator(
    private val permissionChecker: NotificationPermissionChecker,
    private val poster: RemodexThreadNotificationPoster,
) {
    private var previousSnapshot: RemodexSessionSnapshot? = null

    fun start(
        scope: CoroutineScope,
        sessionSnapshots: Flow<RemodexSessionSnapshot>,
    ) {
        poster.createChannels()
        scope.launch {
            sessionSnapshots.collect { snapshot ->
                onSessionSnapshot(snapshot)
            }
        }
    }

    fun onSessionSnapshot(snapshot: RemodexSessionSnapshot) {
        val events = detectThreadNotificationEvents(previousSnapshot, snapshot)
        previousSnapshot = snapshot
        if (!permissionChecker.canPostNotifications()) {
            return
        }
        events.forEach(poster::post)
    }
}

fun detectThreadNotificationEvents(
    previousSnapshot: RemodexSessionSnapshot?,
    currentSnapshot: RemodexSessionSnapshot,
): List<RemodexThreadNotificationEvent> {
    if (previousSnapshot == null) {
        return emptyList()
    }

    val previousThreadsById = previousSnapshot.threads.associateBy(RemodexThreadSummary::id)
    return buildList {
        currentSnapshot.threads.forEach { currentThread ->
            val previousThread = previousThreadsById[currentThread.id] ?: return@forEach

            if (previousThread.isRunning && !currentThread.isRunning) {
                add(
                    RemodexThreadNotificationEvent(
                        threadId = currentThread.id,
                        threadTitle = currentThread.title,
                        title = "${currentThread.title} is ready",
                        body = currentThread.preview,
                        type = RemodexThreadNotificationType.TURN_COMPLETED,
                    ),
                )
            }

            detectAttentionMessage(previousThread, currentThread)?.let { attentionMessage ->
                add(
                    RemodexThreadNotificationEvent(
                        threadId = currentThread.id,
                        threadTitle = currentThread.title,
                        title = "${currentThread.title} needs attention",
                        body = attentionMessage.text,
                        type = RemodexThreadNotificationType.ATTENTION_NEEDED,
                    ),
                )
            }
        }
    }
}

private fun detectAttentionMessage(
    previousThread: RemodexThreadSummary,
    currentThread: RemodexThreadSummary,
): RemodexConversationItem? {
    val previousIds = previousThread.messages.mapTo(mutableSetOf(), RemodexConversationItem::id)
    return currentThread.messages
        .asSequence()
        .filterNot { message -> previousIds.contains(message.id) }
        .lastOrNull(::isAttentionNeededMessage)
}

private fun isAttentionNeededMessage(message: RemodexConversationItem): Boolean {
    if (message.speaker != ConversationSpeaker.SYSTEM) {
        return false
    }
    val haystack = buildString {
        append(message.text)
        message.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
            append(' ')
            append(supportingText)
        }
    }.lowercase()
    return AttentionKeywords.any(haystack::contains)
}

private val AttentionKeywords = listOf(
    "attention",
    "review required",
    "action required",
    "needs input",
)
