package com.emanueledipietro.remodex.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Immutable
data class RemodexConversationChrome(
    val canvas: Color,
    val shellSurface: Color,
    val panelSurface: Color,
    val panelSurfaceStrong: Color,
    val mutedSurface: Color,
    val nestedSurface: Color,
    val userBubble: Color,
    val userBubbleBorder: Color,
    val subtleBorder: Color,
    val titleText: Color,
    val bodyText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val accent: Color,
    val accentSurface: Color,
    val warning: Color,
    val destructive: Color,
    val sendButton: Color,
    val sendIcon: Color,
    val sendButtonDisabled: Color,
    val sendIconDisabled: Color,
)

object RemodexConversationShapes {
    val shell = RoundedCornerShape(28.dp)
    val panel = RoundedCornerShape(24.dp)
    val card = RoundedCornerShape(20.dp)
    val nestedCard = RoundedCornerShape(18.dp)
    val bubble = RoundedCornerShape(24.dp)
    val composer = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(999.dp)
}

@Composable
fun remodexConversationChrome(): RemodexConversationChrome {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.45f

    return if (isDark) {
        RemodexConversationChrome(
            canvas = scheme.background,
            shellSurface = scheme.surface.copy(alpha = 0.94f),
            panelSurface = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
            panelSurfaceStrong = scheme.surfaceContainerHighest.copy(alpha = 0.98f),
            mutedSurface = scheme.surfaceContainerHigh,
            nestedSurface = scheme.surfaceContainerHighest,
            userBubble = scheme.surfaceContainer,
            userBubbleBorder = scheme.outline.copy(alpha = 0.28f),
            subtleBorder = scheme.outline.copy(alpha = 0.22f),
            titleText = scheme.onSurface,
            bodyText = scheme.onSurface,
            secondaryText = scheme.onSurfaceVariant,
            tertiaryText = scheme.onSurfaceVariant.copy(alpha = 0.82f),
            accent = scheme.primary,
            accentSurface = scheme.primary.copy(alpha = 0.16f),
            warning = scheme.tertiary,
            destructive = scheme.error,
            sendButton = scheme.onSurface,
            sendIcon = scheme.surface,
            sendButtonDisabled = scheme.surfaceContainerHighest,
            sendIconDisabled = scheme.onSurfaceVariant.copy(alpha = 0.78f),
        )
    } else {
        RemodexConversationChrome(
            canvas = scheme.surface,
            shellSurface = scheme.surface,
            panelSurface = scheme.surface,
            panelSurfaceStrong = scheme.surface,
            mutedSurface = scheme.surfaceContainerHigh,
            nestedSurface = scheme.surfaceContainer,
            userBubble = scheme.surfaceContainer,
            userBubbleBorder = scheme.outlineVariant.copy(alpha = 0.55f),
            subtleBorder = scheme.outlineVariant.copy(alpha = 0.76f),
            titleText = scheme.onSurface,
            bodyText = scheme.onSurface,
            secondaryText = scheme.onSurfaceVariant,
            tertiaryText = scheme.onSurfaceVariant.copy(alpha = 0.82f),
            accent = scheme.primary,
            accentSurface = scheme.primary.copy(alpha = 0.1f),
            warning = scheme.tertiary,
            destructive = scheme.error,
            sendButton = scheme.onSurface,
            sendIcon = scheme.surface,
            sendButtonDisabled = scheme.surfaceContainerHighest,
            sendIconDisabled = scheme.onSurfaceVariant.copy(alpha = 0.86f),
        )
    }
}
