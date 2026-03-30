package com.emanueledipietro.remodex.platform.notifications

import com.emanueledipietro.remodex.data.app.RemodexSessionSnapshot
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.remodexApprovalRequestSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

enum class RemodexThreadNotificationType {
    TURN_COMPLETED,
    ATTENTION_NEEDED,
    APPROVAL_NEEDED,
}

data class RemodexThreadNotificationEvent(
    val threadId: String,
    val threadTitle: String,
    val title: String,
    val body: String,
    val type: RemodexThreadNotificationType,
    val requestId: String? = null,
)

interface NotificationPermissionChecker {
    fun canPostNotifications(): Boolean
}

interface RemodexThreadNotificationPoster {
    fun createChannels()

    fun post(event: RemodexThreadNotificationEvent)

    fun cancel(notificationId: Int)
}

interface AppForegroundStateProvider {
    fun isAppForeground(): Boolean
}

class RemodexNotificationCoordinator(
    private val permissionChecker: NotificationPermissionChecker,
    private val poster: RemodexThreadNotificationPoster,
    private val appForegroundStateProvider: AppForegroundStateProvider? = null,
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
        cancelStaleApprovalNotification(
            previousSnapshot = previousSnapshot,
            currentSnapshot = snapshot,
            isAppForeground = appForegroundStateProvider?.isAppForeground() == true,
        )
        val events = detectThreadNotificationEvents(
            previousSnapshot = previousSnapshot,
            currentSnapshot = snapshot,
            isAppForeground = appForegroundStateProvider?.isAppForeground() == true,
        )
        previousSnapshot = snapshot
        if (!permissionChecker.canPostNotifications()) {
            return
        }
        events.forEach(poster::post)
    }

    private fun cancelStaleApprovalNotification(
        previousSnapshot: RemodexSessionSnapshot?,
        currentSnapshot: RemodexSessionSnapshot,
        isAppForeground: Boolean,
    ) {
        val previousRequest = previousSnapshot?.pendingApprovalRequest ?: return
        val previousThreadId = previousRequest.threadId?.trim()?.takeIf(String::isNotEmpty) ?: return
        val currentRequestId = currentSnapshot.pendingApprovalRequest?.id
        if (currentRequestId == previousRequest.id && !isAppForeground) {
            return
        }
        poster.cancel(
            remodexNotificationId(
                threadId = previousThreadId,
                type = RemodexThreadNotificationType.APPROVAL_NEEDED,
                requestId = previousRequest.id,
            ),
        )
    }
}

fun detectThreadNotificationEvents(
    previousSnapshot: RemodexSessionSnapshot?,
    currentSnapshot: RemodexSessionSnapshot,
    isAppForeground: Boolean = false,
): List<RemodexThreadNotificationEvent> {
    if (previousSnapshot == null) {
        return emptyList()
    }

    val previousThreadsById = previousSnapshot.threads.associateBy(RemodexThreadSummary::id)
    return buildList {
        currentSnapshot.threads.forEach { currentThread ->
            val previousThread = previousThreadsById[currentThread.id] ?: return@forEach

            if (previousThread.isRunning && !currentThread.isRunning) {
                val displayTitle = currentThread.displayTitle
                add(
                    RemodexThreadNotificationEvent(
                        threadId = currentThread.id,
                        threadTitle = displayTitle,
                        title = "$displayTitle is ready",
                        body = currentThread.preview,
                        type = RemodexThreadNotificationType.TURN_COMPLETED,
                    ),
                )
            }

            detectAttentionMessage(previousThread, currentThread)?.let { attentionMessage ->
                val displayTitle = currentThread.displayTitle
                add(
                    RemodexThreadNotificationEvent(
                        threadId = currentThread.id,
                        threadTitle = displayTitle,
                        title = "$displayTitle needs attention",
                        body = attentionMessage.text,
                        type = RemodexThreadNotificationType.ATTENTION_NEEDED,
                    ),
                )
            }

        }
        detectApprovalNotification(
            previousSnapshot = previousSnapshot,
            currentSnapshot = currentSnapshot,
            isAppForeground = isAppForeground,
        )?.let(::add)
    }
}

private fun detectApprovalNotification(
    previousSnapshot: RemodexSessionSnapshot,
    currentSnapshot: RemodexSessionSnapshot,
    isAppForeground: Boolean,
): RemodexThreadNotificationEvent? {
    if (isAppForeground) {
        return null
    }
    val request = currentSnapshot.pendingApprovalRequest ?: return null
    if (request.id == previousSnapshot.pendingApprovalRequest?.id) {
        return null
    }
    val threadId = request.threadId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val thread = currentSnapshot.threads.firstOrNull { candidate -> candidate.id == threadId } ?: return null
    val displayTitle = thread.displayTitle
    return RemodexThreadNotificationEvent(
        threadId = threadId,
        threadTitle = displayTitle,
        title = "$displayTitle needs approval",
        body = remodexApprovalRequestSummary(request),
        type = RemodexThreadNotificationType.APPROVAL_NEEDED,
        requestId = request.id,
    )
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
