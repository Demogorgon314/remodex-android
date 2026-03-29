package com.emanueledipietro.remodex.feature.turn

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emanueledipietro.remodex.feature.appshell.ComposerVoiceButtonMode
import com.emanueledipietro.remodex.feature.appshell.ComposerVoiceUiState
import com.emanueledipietro.remodex.ui.theme.remodexConversationChrome
import kotlin.math.max
import kotlin.math.pow

private val VoiceButtonSize = 32.dp
private val VoiceStopGlyphSize = 10.dp
private val VoiceWaveformHeight = 18.dp
private val VoiceWaveformIdealBarWidth = 2.dp
private val VoiceWaveformBarSpacing = 1.5.dp

@Composable
internal fun ConversationVoiceButton(
    voiceUiState: ComposerVoiceUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = remodexConversationChrome()
    val hasCircleBackground = voiceUiState.buttonMode != ComposerVoiceButtonMode.IDLE
    val isEnabled = when (voiceUiState.buttonMode) {
        ComposerVoiceButtonMode.IDLE -> voiceUiState.isConnected
        ComposerVoiceButtonMode.RECORDING -> true
        ComposerVoiceButtonMode.PREFLIGHTING,
        ComposerVoiceButtonMode.TRANSCRIBING -> false
    }
    val backgroundColor = when (voiceUiState.buttonMode) {
        ComposerVoiceButtonMode.RECORDING -> Color(0xFFD64045)
        ComposerVoiceButtonMode.IDLE -> Color.Transparent
        ComposerVoiceButtonMode.PREFLIGHTING,
        ComposerVoiceButtonMode.TRANSCRIBING -> chrome.mutedSurface
    }
    val iconTint = when (voiceUiState.buttonMode) {
        ComposerVoiceButtonMode.RECORDING -> Color.White
        ComposerVoiceButtonMode.IDLE -> if (isEnabled) chrome.titleText else chrome.secondaryText.copy(alpha = 0.72f)
        ComposerVoiceButtonMode.PREFLIGHTING,
        ComposerVoiceButtonMode.TRANSCRIBING -> chrome.secondaryText
    }

    Surface(
        modifier = modifier.requiredSize(VoiceButtonSize),
        color = backgroundColor,
        shape = CircleShape,
        border = if (hasCircleBackground) {
            null
        } else {
            null
        },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = when (voiceUiState.buttonMode) {
                        ComposerVoiceButtonMode.IDLE -> "Start voice transcription"
                        ComposerVoiceButtonMode.PREFLIGHTING -> "Preparing microphone"
                        ComposerVoiceButtonMode.RECORDING -> "Stop voice recording"
                        ComposerVoiceButtonMode.TRANSCRIBING -> "Transcribing voice note"
                    }
                }
                .clickable(
                    enabled = isEnabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (voiceUiState.buttonMode) {
                ComposerVoiceButtonMode.IDLE -> {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = "Start voice transcription",
                        modifier = Modifier.size(18.dp),
                        tint = iconTint,
                    )
                }

                ComposerVoiceButtonMode.RECORDING -> {
                    Box(
                        modifier = Modifier
                            .size(VoiceStopGlyphSize)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White),
                    )
                }

                ComposerVoiceButtonMode.PREFLIGHTING,
                ComposerVoiceButtonMode.TRANSCRIBING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.8.dp,
                        color = iconTint,
                    )
                }
            }
        }
    }
}

@Composable
internal fun VoiceRecordingCapsule(
    voiceUiState: ComposerVoiceUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = remodexConversationChrome()
    val displayedLevels = remember(voiceUiState.audioLevels) {
        voiceUiState.audioLevels.takeLast(240)
    }
    val pulseAlpha by rememberVoicePulseAlpha()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ComposerVoiceRecordingCapsuleTag),
        color = chrome.panelSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = pulseAlpha }
                    .background(chrome.titleText, CircleShape),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(VoiceWaveformHeight),
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val spacing = VoiceWaveformBarSpacing.toPx()
                    val idealBarWidth = VoiceWaveformIdealBarWidth.toPx()
                    val slotCount = max(1, ((size.width + spacing) / (idealBarWidth + spacing)).toInt())
                    val resampledLevels = resampleLevels(
                        levels = displayedLevels,
                        slotCount = slotCount,
                    )
                    if (resampledLevels.isEmpty()) {
                        return@Canvas
                    }

                    val totalSpacing = spacing * (resampledLevels.size - 1)
                    val barWidth = max(1f, (size.width - totalSpacing) / resampledLevels.size)
                    val minBarHeight = 2.dp.toPx()
                    val maxBarHeight = size.height

                    resampledLevels.forEachIndexed { index, level ->
                        val boostedLevel = max(level, 0.04f).pow(0.72f)
                        val barHeight = minBarHeight + (maxBarHeight - minBarHeight) * boostedLevel
                        val left = index * (barWidth + spacing)
                        val top = (size.height - barHeight) / 2f
                        drawRoundRect(
                            color = chrome.titleText.copy(alpha = 0.22f + boostedLevel * 0.68f),
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                x = minOf(barWidth / 2f, 1.5.dp.toPx()),
                                y = minOf(barWidth / 2f, 1.5.dp.toPx()),
                            ),
                        )
                    }
                }
            }

            Text(
                text = formatVoiceDuration(voiceUiState.durationSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = chrome.titleText,
            )

            Surface(
                modifier = Modifier.size(22.dp),
                color = chrome.mutedSurface,
                shape = CircleShape,
                border = BorderStroke(1.dp, chrome.subtleBorder),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cancel voice recording",
                        modifier = Modifier.size(12.dp),
                        tint = chrome.secondaryText,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberVoicePulseAlpha(): androidx.compose.runtime.State<Float> {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "voice_pulse")
    return transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "voice_pulse_alpha",
    )
}

private fun resampleLevels(
    levels: List<Float>,
    slotCount: Int,
): List<Float> {
    if (slotCount <= 0) {
        return emptyList()
    }
    if (levels.isEmpty()) {
        return List(slotCount) { 0f }
    }
    if (levels.size <= slotCount) {
        return List(slotCount - levels.size) { 0f } + levels
    }

    return List(slotCount) { index ->
        val start = (index.toDouble() / slotCount * levels.size).toInt()
        val end = max(start + 1, (((index + 1).toDouble() / slotCount) * levels.size).toInt())
        levels.subList(start, minOf(end, levels.size)).maxOrNull() ?: 0f
    }
}

private fun formatVoiceDuration(durationSeconds: Double): String {
    val totalSeconds = durationSeconds.toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
