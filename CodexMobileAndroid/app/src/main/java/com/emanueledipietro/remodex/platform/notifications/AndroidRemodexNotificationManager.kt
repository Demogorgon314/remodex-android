package com.emanueledipietro.remodex.platform.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.emanueledipietro.remodex.MainActivity
import com.emanueledipietro.remodex.R
import com.emanueledipietro.remodex.model.RemodexNotificationAuthorizationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class RemodexNotificationPermissionUiState(
    val isEnabled: Boolean,
    val headline: String,
    val message: String,
    val actionLabel: String? = null,
    val canRequestPermission: Boolean = false,
    val requiresSystemSettings: Boolean = false,
)

class AndroidRemodexNotificationManager(
    private val context: Context,
) : NotificationPermissionChecker, RemodexThreadNotificationPoster, ManagedPushStatusProvider, AppForegroundStateProvider {
    private val refreshEventsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    @Volatile
    private var appForeground: Boolean = false
    val coordinator = RemodexNotificationCoordinator(
        permissionChecker = this,
        poster = this,
        appForegroundStateProvider = this,
    )
    override val refreshEvents: Flow<Unit> = refreshEventsFlow.asSharedFlow()

    override fun canPostNotifications(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!notificationsEnabled) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun permissionUiState(): RemodexNotificationPermissionUiState {
        return when {
            canPostNotifications() -> RemodexNotificationPermissionUiState(
                isEnabled = true,
                headline = "Notifications enabled",
                message = "Turn-complete, approval, and attention-needed alerts can bring you back into the relevant thread.",
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED -> {
                RemodexNotificationPermissionUiState(
                    isEnabled = false,
                    headline = "Notifications are waiting for permission",
                    message = "Allow Android notifications so Remodex can alert you when a turn finishes, needs approval, or needs input.",
                    actionLabel = "Allow notifications",
                    canRequestPermission = true,
                )
            }

            else -> {
                RemodexNotificationPermissionUiState(
                    isEnabled = false,
                    headline = "Notifications are off in system settings",
                    message = "Remodex will keep surfacing events in-app, but Android alerts stay muted until system notifications are re-enabled.",
                    actionLabel = "Open system settings",
                    requiresSystemSettings = true,
                )
            }
        }
    }

    override fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    ConversationChannelId,
                    "Remodex conversations",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Turn-complete and attention-needed updates from your paired Remodex session."
                },
                NotificationChannel(
                    ApprovalChannelId,
                    "Remodex approvals",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Approval requests that need your review before Codex can continue."
                    setShowBadge(true)
                },
            ),
        )
    }

    override fun authorizationStatus(): RemodexNotificationAuthorizationStatus {
        if (canPostNotifications()) {
            return RemodexNotificationAuthorizationStatus.AUTHORIZED
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return RemodexNotificationAuthorizationStatus.DENIED
        }
        return if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            RemodexNotificationAuthorizationStatus.DENIED
        } else {
            RemodexNotificationAuthorizationStatus.NOT_DETERMINED
        }
    }

    override fun alertsEnabled(): Boolean = canPostNotifications()

    override fun isAppForeground(): Boolean = appForeground

    fun setAppForeground(isForeground: Boolean) {
        appForeground = isForeground
    }

    fun notifyPushRegistrationStateMayHaveChanged() {
        refreshEventsFlow.tryEmit(Unit)
    }

    override fun post(event: RemodexThreadNotificationEvent) {
        NotificationManagerCompat.from(context).notify(
            remodexNotificationId(
                threadId = event.threadId,
                type = event.type,
                requestId = event.requestId,
            ),
            NotificationCompat.Builder(context, event.channelId())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(event.title)
                .setContentText(event.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
                .setContentIntent(threadPendingIntent(event.threadId))
                .setAutoCancel(true)
                .setCategory(event.category())
                .setPriority(event.priority())
                .setOnlyAlertOnce(event.type != RemodexThreadNotificationType.APPROVAL_NEEDED)
                .build(),
        )
    }

    override fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun threadPendingIntent(threadId: String): PendingIntent {
        val threadRoute = buildThreadRoute(threadId)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(threadRoute)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            threadRoute.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal fun remodexNotificationId(
    threadId: String,
    type: RemodexThreadNotificationType,
    requestId: String? = null,
): Int {
    val stableKey = requestId ?: (threadId + type.name)
    return 31_000 + stableKey.hashCode()
}

private const val ConversationChannelId = "remodex_conversations"
private const val ApprovalChannelId = "remodex_approvals"

private fun RemodexThreadNotificationEvent.channelId(): String {
    return when (type) {
        RemodexThreadNotificationType.APPROVAL_NEEDED -> ApprovalChannelId
        else -> ConversationChannelId
    }
}

private fun RemodexThreadNotificationEvent.category(): String {
    return when (type) {
        RemodexThreadNotificationType.APPROVAL_NEEDED -> NotificationCompat.CATEGORY_REMINDER
        else -> NotificationCompat.CATEGORY_MESSAGE
    }
}

private fun RemodexThreadNotificationEvent.priority(): Int {
    return when (type) {
        RemodexThreadNotificationType.APPROVAL_NEEDED -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }
}
