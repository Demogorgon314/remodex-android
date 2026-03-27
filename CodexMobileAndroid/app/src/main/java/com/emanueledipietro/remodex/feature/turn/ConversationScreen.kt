package com.emanueledipietro.remodex.feature.turn

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.model.RemodexAssistantRevertPresentation
import com.emanueledipietro.remodex.model.RemodexAssistantRevertRiskLevel
import com.emanueledipietro.remodex.model.RemodexAssistantRevertSheetState
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import com.emanueledipietro.remodex.model.RemodexComposerAutocompletePanel
import com.emanueledipietro.remodex.model.RemodexComposerForkDestination
import com.emanueledipietro.remodex.model.RemodexComposerReviewTarget
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexFuzzyFileMatch
import com.emanueledipietro.remodex.model.RemodexGitState
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexPlanState
import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexRuntimeMetaMapper
import com.emanueledipietro.remodex.model.RemodexServiceTier
import com.emanueledipietro.remodex.model.RemodexSkillMetadata
import com.emanueledipietro.remodex.model.RemodexSlashCommand
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputRequest
import com.emanueledipietro.remodex.model.RemodexSubagentAction
import com.emanueledipietro.remodex.model.RemodexSubagentThreadPresentation
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexTurnTerminalState
import com.emanueledipietro.remodex.model.RemodexUsageStatus
import com.emanueledipietro.remodex.model.RemodexContextWindowUsage
import com.emanueledipietro.remodex.model.RemodexRateLimitBucket
import com.emanueledipietro.remodex.model.RemodexRateLimitDisplayRow
import com.emanueledipietro.remodex.ui.theme.RemodexConversationChrome
import com.emanueledipietro.remodex.ui.theme.RemodexConversationShapes
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.flow.drop
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val ComposerFollowBottomThreshold = 12.dp
private val ComposerTrailingButtonSize = 32.dp
private val ComposerLeadingIconTapTarget = 24.dp
private val ComposerAttachmentThumbnailSize = 70.dp
private val ComposerAttachmentRemoveButtonSize = 22.dp
private val ComposerModelMenuMaxWidth = 160.dp
private val ComposerReasoningMenuMaxWidth = 132.dp
private val FileAutocompleteRowHeight = 38.dp
private val ConversationBottomAnchorHeight = 1.dp
private val FileChangeAddedColor = Color(0xFF22A653)
private val FileChangeDeletedColor = Color(0xFFE04646)
private val ComposerMentionFileTint = Color(0xFF2563EB)
private val ComposerMentionSkillTint = Color(0xFF4F46E5)
private val ComposerMentionChipCornerRadius = 8.dp
private val ComposerMentionRemoveButtonSize = 14.dp

private fun isCodexManagedWorktreeProject(projectPath: String): Boolean =
    com.emanueledipietro.remodex.feature.threads.isCodexManagedWorktreeProject(projectPath)

@Composable
private fun remodexConversationChrome(): RemodexConversationChrome =
    com.emanueledipietro.remodex.ui.theme.remodexConversationChrome()

private data class TimelineBottomAnchorRequest(
    val targetIndex: Int,
    val lastItemId: String,
    val lastItemTextLength: Int,
    val lastItemSupportingTextLength: Int,
    val lastItemStreaming: Boolean,
    val latestRunningIndicatorMessageId: String?,
    val composerFocused: Boolean,
    val imeBottomPx: Int,
    val autocompleteVisible: Boolean,
    val queuedDraftCount: Int,
    val pinnedPlanItemId: String?,
)
private val SkillAutocompleteRowHeight = 50.dp
private val SlashAutocompleteRowHeight = 50.dp
private const val MaxAutocompleteVisibleRows = 6
internal const val ComposerAutocompletePanelTag = "composer_autocomplete_panel"
internal const val ComposerAutocompleteDismissLayerTag = "composer_autocomplete_dismiss_layer"
internal const val ComposerSendButtonTag = "composer_send_button"
internal const val ConversationRunningIndicatorTag = "conversation_running_indicator"
internal const val ConversationCopyButtonTag = "conversation_copy_button"
internal const val ConversationSelectableTextSheetTag = "conversation_selectable_text_sheet"
internal const val ConversationStatusSheetTag = "conversation_status_sheet"

private data class ComposerAccessoryChipColors(
    val tint: Color,
    val background: Color,
    val removeBackground: Color,
    val border: BorderStroke? = null,
)

private fun rememberMentionChipColors(
    tint: Color,
    chrome: RemodexConversationChrome,
): ComposerAccessoryChipColors {
    val backgroundAlpha = if (chrome.canvas.luminance() < 0.45f) 0.18f else 0.08f
    val removeAlpha = if (chrome.canvas.luminance() < 0.45f) 0.24f else 0.14f
    return ComposerAccessoryChipColors(
        tint = tint,
        background = tint.copy(alpha = backgroundAlpha),
        removeBackground = tint.copy(alpha = removeAlpha),
    )
}

internal fun syncComposerInputValue(
    currentValue: TextFieldValue,
    externalText: String,
): TextFieldValue {
    if (currentValue.text == externalText) {
        return currentValue
    }
    return TextFieldValue(
        text = externalText,
        selection = TextRange(externalText.length),
    )
}

private data class ConversationBlockAccessoryState(
    val showsRunningIndicator: Boolean = false,
    val copyText: String? = null,
    val blockDiffText: String? = null,
    val blockDiffEntries: List<FileChangeSummaryEntry>? = null,
    val blockRevertPresentation: RemodexAssistantRevertPresentation? = null,
)

private data class SelectableMessageTextSheetState(
    val role: ConversationSpeaker,
    val text: String,
    val usesMarkdownSelection: Boolean,
) {
    val title: String
        get() = when (role) {
            ConversationSpeaker.ASSISTANT -> "Assistant Message"
            ConversationSpeaker.SYSTEM -> "System Message"
            ConversationSpeaker.USER -> "Message"
        }
}

internal data class FileChangeSheetPresentation(
    val title: String = "Changes",
    val messageId: String,
    val renderState: FileChangeRenderState,
    val diffChunks: List<PerFileDiffChunk>,
)

internal fun buildRepositoryDiffSheetPresentation(rawPatch: String): FileChangeSheetPresentation? {
    val normalizedPatch = rawPatch.trim()
    if (normalizedPatch.isEmpty()) {
        return null
    }
    val renderState = FileChangeRenderParser.renderState(normalizedPatch)
    val diffChunks = FileChangeRenderParser.diffChunks(
        bodyText = normalizedPatch,
        entries = renderState.summary?.entries.orEmpty(),
    )
    return FileChangeSheetPresentation(
        title = "Repository Changes",
        messageId = "repo-diff-${normalizedPatch.hashCode()}",
        renderState = renderState.copy(bodyText = normalizedPatch),
        diffChunks = diffChunks,
    )
}

@Composable
fun ConversationScreen(
    uiState: AppUiState,
    onRetryConnection: () -> Unit,
    onComposerInputChanged: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onStopTurn: () -> Unit,
    onSendQueuedDraft: (String) -> Unit,
    onSelectModel: (String?) -> Unit,
    onSelectPlanningMode: (RemodexPlanningMode) -> Unit,
    onSelectReasoningEffort: (String) -> Unit,
    onSelectAccessMode: (RemodexAccessMode) -> Unit,
    onSelectServiceTier: (RemodexServiceTier?) -> Unit,
    onOpenAttachmentPicker: () -> Unit,
    onOpenCameraCapture: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSelectFileAutocomplete: (RemodexFuzzyFileMatch) -> Unit,
    onRemoveMentionedFile: (String) -> Unit,
    onSelectSkillAutocomplete: (RemodexSkillMetadata) -> Unit,
    onRemoveMentionedSkill: (String) -> Unit,
    onSelectSlashCommand: (RemodexSlashCommand) -> Unit,
    onSelectCodeReviewTarget: (RemodexComposerReviewTarget) -> Unit,
    onClearReviewSelection: () -> Unit,
    onClearSubagentsSelection: () -> Unit,
    onCloseComposerAutocomplete: () -> Unit,
    onSelectGitBaseBranch: (String) -> Unit,
    onRefreshGitState: () -> Unit,
    onRefreshUsageStatus: () -> Unit = {},
    onCheckoutGitBranch: (String) -> Unit,
    onCreateGitBranch: (String) -> Unit,
    onCreateGitWorktree: (String) -> Unit,
    onCommitGitChanges: () -> Unit,
    onPullGitChanges: () -> Unit,
    onPushGitChanges: () -> Unit,
    onDiscardRuntimeChangesAndSync: () -> Unit,
    onForkThread: (RemodexComposerForkDestination) -> Unit,
    onOpenSubagentThread: (String) -> Unit,
    onHydrateSubagentThread: (String) -> Unit,
    onStartAssistantRevertPreview: (String) -> Unit,
    onConfirmAssistantRevert: () -> Unit,
    onDismissAssistantRevertSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thread = uiState.selectedThread
    val chrome = remodexConversationChrome()
    if (thread == null) {
        EmptyConversationState(modifier = modifier)
        return
    }
    val autocompleteVisible = uiState.composer.autocomplete.panel != RemodexComposerAutocompletePanel.NONE
    val showsThreadRunningUi = thread.isRunning && thread.latestTurnTerminalState == null

    var gitSheetExpanded by rememberSaveable(thread.id) { mutableStateOf(false) }
    var planSheetExpanded by rememberSaveable(thread.id) { mutableStateOf(false) }
    var statusSheetExpanded by rememberSaveable(thread.id) { mutableStateOf(false) }
    var commandDetailsMessageId by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var fileChangeSheetPresentation by remember(thread.id) { mutableStateOf<FileChangeSheetPresentation?>(null) }
    var composerFocused by rememberSaveable(thread.id) { mutableStateOf(false) }
    val pinnedPlanItem = thread.messages.lastOrNull { item -> item.kind == ConversationItemKind.PLAN }
    val timelineItems = thread.messages.filterNot { item -> item.id == pinnedPlanItem?.id }
    val blockAccessories = remember(
        timelineItems,
        thread.isRunning,
        thread.activeTurnId,
        thread.latestTurnTerminalState,
        thread.stoppedTurnIds,
        uiState.assistantRevertStatesByMessageId,
    ) {
        buildConversationBlockAccessories(
            items = timelineItems,
            isThreadRunning = thread.isRunning,
            activeTurnId = thread.activeTurnId,
            latestTurnTerminalState = thread.latestTurnTerminalState,
            stoppedTurnIds = thread.stoppedTurnIds,
            assistantRevertStatesByMessageId = uiState.assistantRevertStatesByMessageId,
        )
    }
    val timelineState = rememberLazyListState()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val showsEmptyTimelineState = timelineItems.isEmpty() && pinnedPlanItem == null
    val lastTimelineItem = timelineItems.lastOrNull()
    val lastTimelineItemId = lastTimelineItem?.id
    val bottomAnchorIndex = timelineItems.size
    val followBottomThresholdPx = with(density) { ComposerFollowBottomThreshold.roundToPx() }
    var initialScrollApplied by rememberSaveable(thread.id) { mutableStateOf(false) }
    var keepTimelinePinnedToBottom by rememberSaveable(thread.id) { mutableStateOf(true) }
    var composerSawImeWhileFocused by rememberSaveable(thread.id) { mutableStateOf(false) }
    var handledComposerAnchorSignal by rememberSaveable(thread.id) { mutableStateOf(0L) }
    var pendingAssistantAnchorSignal by rememberSaveable(thread.id) { mutableStateOf(0L) }
    val latestRunningIndicatorMessageId = remember(timelineItems, blockAccessories) {
        timelineItems.lastOrNull { item -> blockAccessories[item.id]?.showsRunningIndicator == true }?.id
    }
    val assistantResponseAnchorIndex = remember(timelineItems, thread.activeTurnId) {
        TurnTimelineReducer.assistantResponseAnchorIndex(
            items = timelineItems,
            activeTurnId = thread.activeTurnId,
        )
    }
    val bottomAnchorRequest = remember(
        initialScrollApplied,
        keepTimelinePinnedToBottom,
        timelineItems.size,
        lastTimelineItemId,
        lastTimelineItem?.text?.length,
        lastTimelineItem?.supportingText?.length,
        lastTimelineItem?.isStreaming,
        latestRunningIndicatorMessageId,
        composerFocused,
        imeBottomPx,
        autocompleteVisible,
        uiState.composer.queuedDrafts.size,
        pinnedPlanItem?.id,
    ) {
        if (
            !initialScrollApplied ||
            !keepTimelinePinnedToBottom ||
            timelineItems.isEmpty() ||
            lastTimelineItem == null
        ) {
            null
        } else {
            TimelineBottomAnchorRequest(
                targetIndex = bottomAnchorIndex,
                lastItemId = lastTimelineItem.id,
                lastItemTextLength = lastTimelineItem.text.length,
                lastItemSupportingTextLength = lastTimelineItem.supportingText?.length ?: 0,
                lastItemStreaming = lastTimelineItem.isStreaming,
                latestRunningIndicatorMessageId = latestRunningIndicatorMessageId,
                composerFocused = composerFocused,
                imeBottomPx = imeBottomPx,
                autocompleteVisible = autocompleteVisible,
                queuedDraftCount = uiState.composer.queuedDrafts.size,
                pinnedPlanItemId = pinnedPlanItem?.id,
            )
        }
    }
    val selectedCommandExecutionItem = remember(thread.messages, commandDetailsMessageId) {
        commandDetailsMessageId?.let { messageId ->
            thread.messages.firstOrNull { item -> item.id == messageId && item.kind == ConversationItemKind.COMMAND_EXECUTION }
        }
    }
    val selectedCommandExecutionStatus = remember(selectedCommandExecutionItem?.text) {
        selectedCommandExecutionItem
            ?.text
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.map(::parseCommandExecutionStatus)
            ?.firstOrNull { status -> status != null }
    }
    val handleSelectSlashCommand: (RemodexSlashCommand) -> Unit = remember(
        onSelectSlashCommand,
        onRefreshUsageStatus,
    ) {
        { command ->
            onSelectSlashCommand(command)
            if (command == RemodexSlashCommand.STATUS) {
                statusSheetExpanded = true
                onRefreshUsageStatus()
            }
        }
    }
    LaunchedEffect(thread.id, lastTimelineItemId) {
        if (!initialScrollApplied && timelineItems.isNotEmpty()) {
            withFrameNanos { }
            timelineState.scrollToItem(bottomAnchorIndex)
            withFrameNanos { }
            timelineState.scrollToItem(bottomAnchorIndex)
            keepTimelinePinnedToBottom = true
            initialScrollApplied = true
        }
    }

    LaunchedEffect(thread.id, followBottomThresholdPx) {
        snapshotFlow { timelineState.isScrollInProgress }
            .drop(1)
            .collect { isScrolling ->
                if (!isScrolling) {
                    keepTimelinePinnedToBottom = isTimelineNearBottom(
                        state = timelineState,
                        thresholdPx = followBottomThresholdPx,
                    )
                }
            }
    }

    LaunchedEffect(thread.id, composerFocused, imeBottomPx) {
        when {
            !composerFocused -> composerSawImeWhileFocused = false
            imeBottomPx > 0 -> composerSawImeWhileFocused = true
            composerSawImeWhileFocused -> {
                focusManager.clearFocus(force = true)
                composerFocused = false
                composerSawImeWhileFocused = false
                onCloseComposerAutocomplete()
            }
        }
    }

    LaunchedEffect(thread.id, uiState.composerSendDismissSignal) {
        if (uiState.composerSendDismissSignal == 0L) {
            return@LaunchedEffect
        }
        focusManager.clearFocus(force = true)
        composerFocused = false
        composerSawImeWhileFocused = false
        onCloseComposerAutocomplete()
    }

    LaunchedEffect(thread.id, uiState.composerSendAnchorSignal) {
        val signal = uiState.composerSendAnchorSignal
        if (signal == 0L || signal == handledComposerAnchorSignal) {
            return@LaunchedEffect
        }

        handledComposerAnchorSignal = signal
        keepTimelinePinnedToBottom = true
        pendingAssistantAnchorSignal = signal

        if (timelineItems.isNotEmpty()) {
            withFrameNanos { }
            timelineState.scrollToItem(bottomAnchorIndex)
            withFrameNanos { }
            timelineState.scrollToItem(bottomAnchorIndex)
        }
    }

    LaunchedEffect(thread.id, pendingAssistantAnchorSignal, assistantResponseAnchorIndex) {
        if (pendingAssistantAnchorSignal == 0L || assistantResponseAnchorIndex == null) {
            return@LaunchedEffect
        }

        withFrameNanos { }
        timelineState.scrollToItem(assistantResponseAnchorIndex)
        // After we jump back to the current turn, keep follow-bottom enabled so
        // streaming output continues to track downward like a live chat.
        keepTimelinePinnedToBottom = true
        pendingAssistantAnchorSignal = 0L
    }

    LaunchedEffect(thread.id, bottomAnchorRequest) {
        val request = bottomAnchorRequest ?: return@LaunchedEffect
        withFrameNanos { }
        timelineState.scrollToItem(request.targetIndex)
        if (
            request.imeBottomPx > 0 ||
            request.composerFocused ||
            request.autocompleteVisible
        ) {
            withFrameNanos { }
            timelineState.scrollToItem(request.targetIndex)
        }
    }

    BackHandler(enabled = autocompleteVisible) {
        onCloseComposerAutocomplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(chrome.canvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ConversationTopOverlays(
                        uiState = uiState,
                        onRetryConnection = onRetryConnection,
                    )

                    if (showsEmptyTimelineState) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyThreadTimelineCard()
                        }
                    } else {
                        LazyColumn(
                            state = timelineState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Bottom),
                        ) {
                            if (timelineItems.isEmpty()) {
                                item {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp),
                                    )
                                }
                            } else {
                                items(timelineItems, key = { it.id }) { message ->
                                    when (message.speaker) {
                                        ConversationSpeaker.USER -> UserConversationRow(item = message)
                                        ConversationSpeaker.ASSISTANT -> AssistantConversationRow(
                                            item = message,
                                            accessoryState = blockAccessories[message.id],
                                            assistantRevertPresentation = uiState.assistantRevertStatesByMessageId[message.id],
                                            onTapAssistantRevert = onStartAssistantRevertPreview,
                                            onOpenFileChangeDetails = { presentation ->
                                                fileChangeSheetPresentation = presentation
                                            },
                                        )

                                        ConversationSpeaker.SYSTEM -> SystemConversationRow(
                                            item = message,
                                            accessoryState = blockAccessories[message.id],
                                            commandExecutionDetails = message.itemId?.let(uiState.commandExecutionDetailsByItemId::get),
                                            onOpenFileChangeDetails = { presentation ->
                                                fileChangeSheetPresentation = presentation
                                            },
                                            onOpenCommandExecutionDetails = { messageId ->
                                                commandDetailsMessageId = messageId
                                            },
                                            parentThreadId = thread.id,
                                            threads = if (message.kind == ConversationItemKind.SUBAGENT_ACTION) {
                                                uiState.threads
                                            } else {
                                                emptyList()
                                            },
                                            parentThreadMessages = if (message.kind == ConversationItemKind.SUBAGENT_ACTION) {
                                                thread.messages
                                            } else {
                                                emptyList()
                                            },
                                            onOpenSubagentThread = onOpenSubagentThread,
                                            onHydrateSubagentThread = onHydrateSubagentThread,
                                        )
                                    }
                                }
                                item(key = "conversation-bottom-anchor") {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ConversationBottomAnchorHeight),
                                    )
                                }
                            }
                        }
                    }
                }

                if (autocompleteVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .testTag(ComposerAutocompleteDismissLayerTag)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCloseComposerAutocomplete,
                            ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .imePadding(),
            ) {
                if (autocompleteVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCloseComposerAutocomplete,
                            ),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pinnedPlanItem?.let { planItem ->
                        PlanAccessoryCard(
                            planItem = planItem,
                            onClick = { planSheetExpanded = true },
                        )
                    }

                    if (uiState.composer.queuedDrafts.isNotEmpty()) {
                        QueuedDraftsCard(
                            queuedDrafts = uiState.composer.queuedDrafts,
                            canSendQueuedDrafts = !showsThreadRunningUi,
                            onSendQueuedDraft = onSendQueuedDraft,
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            AnimatedVisibility(
                                visible = autocompleteVisible,
                                enter = fadeIn(animationSpec = tween(durationMillis = 160)) +
                                    slideInVertically(
                                        animationSpec = tween(durationMillis = 180),
                                        initialOffsetY = { fullHeight -> fullHeight / 6 },
                                    ) +
                                    scaleIn(
                                        animationSpec = tween(durationMillis = 160),
                                        initialScale = 0.98f,
                                    ),
                                exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                                    slideOutVertically(
                                        animationSpec = tween(durationMillis = 120),
                                        targetOffsetY = { fullHeight -> fullHeight / 8 },
                                    ) +
                                    scaleOut(
                                        animationSpec = tween(durationMillis = 110),
                                        targetScale = 0.985f,
                                    ),
                            ) {
                                AutocompletePanel(
                                    uiState = uiState,
                                    onSelectFileAutocomplete = onSelectFileAutocomplete,
                                    onSelectSkillAutocomplete = onSelectSkillAutocomplete,
                                    onSelectSlashCommand = handleSelectSlashCommand,
                                    onSelectCodeReviewTarget = onSelectCodeReviewTarget,
                                    onCloseComposerAutocomplete = onCloseComposerAutocomplete,
                                    onForkThread = onForkThread,
                                    modifier = Modifier
                                        .padding(bottom = 6.dp),
                                )
                            }

                            ComposerCard(
                                uiState = uiState,
                                onComposerInputChanged = onComposerInputChanged,
                                onSendPrompt = onSendPrompt,
                                onStopTurn = onStopTurn,
                                onSelectModel = onSelectModel,
                                onSelectPlanningMode = onSelectPlanningMode,
                                onSelectReasoningEffort = onSelectReasoningEffort,
                                onSelectAccessMode = onSelectAccessMode,
                                onSelectServiceTier = onSelectServiceTier,
                                onOpenAttachmentPicker = onOpenAttachmentPicker,
                                onOpenCameraCapture = onOpenCameraCapture,
                                onRemoveAttachment = onRemoveAttachment,
                                onSelectFileAutocomplete = onSelectFileAutocomplete,
                                onRemoveMentionedFile = onRemoveMentionedFile,
                                onSelectSkillAutocomplete = onSelectSkillAutocomplete,
                                onRemoveMentionedSkill = onRemoveMentionedSkill,
                                onSelectSlashCommand = handleSelectSlashCommand,
                                onSelectCodeReviewTarget = onSelectCodeReviewTarget,
                                onClearReviewSelection = onClearReviewSelection,
                                onClearSubagentsSelection = onClearSubagentsSelection,
                                onCloseComposerAutocomplete = onCloseComposerAutocomplete,
                                onForkThread = onForkThread,
                                onComposerFocusChanged = { isFocused ->
                                    composerFocused = isFocused
                                },
                            )
                        }
                    }

                    if (!composerFocused || (composerSawImeWhileFocused && imeBottomPx == 0)) {
                        ComposerSecondaryBar(
                            thread = thread,
                            gitState = uiState.composer.gitState,
                            usageStatus = uiState.usageStatus,
                            isRefreshingUsage = uiState.isRefreshingUsage,
                            accessMode = uiState.composer.runtimeConfig.accessMode,
                            onSelectAccessMode = onSelectAccessMode,
                            onRefreshUsageStatus = onRefreshUsageStatus,
                            onOpenGitSheet = { gitSheetExpanded = true },
                        )
                    }
                }
            }
        }

        if (gitSheetExpanded) {
            DetailedGitSheet(
                gitState = uiState.composer.gitState,
                selectedBaseBranch = uiState.composer.selectedGitBaseBranch,
                onDismiss = { gitSheetExpanded = false },
                onSelectBaseBranch = onSelectGitBaseBranch,
                onRefresh = onRefreshGitState,
                onCheckoutBranch = onCheckoutGitBranch,
                onCreateBranch = onCreateGitBranch,
                onCreateWorktree = onCreateGitWorktree,
                onCommit = onCommitGitChanges,
                onPull = onPullGitChanges,
                onPush = onPushGitChanges,
                onDiscardRuntimeChangesAndSync = onDiscardRuntimeChangesAndSync,
            )
        }

        if (planSheetExpanded && pinnedPlanItem != null) {
            PlanDetailsSheet(
                planItem = pinnedPlanItem,
                onDismiss = { planSheetExpanded = false },
            )
        }

        if (statusSheetExpanded) {
            ConversationStatusSheet(
                usageStatus = uiState.usageStatus,
                isRefreshingUsage = uiState.isRefreshingUsage,
                onRefreshUsageStatus = onRefreshUsageStatus,
                onDismiss = { statusSheetExpanded = false },
            )
        }

        if (selectedCommandExecutionItem != null && selectedCommandExecutionStatus != null) {
            CommandExecutionDetailSheet(
                status = selectedCommandExecutionStatus,
                details = selectedCommandExecutionItem.itemId?.let(uiState.commandExecutionDetailsByItemId::get),
                onDismiss = { commandDetailsMessageId = null },
            )
        }

        fileChangeSheetPresentation?.let { presentation ->
                FileChangeDetailSheet(
                title = presentation.title,
                messageId = presentation.messageId,
                renderState = presentation.renderState,
                diffChunks = presentation.diffChunks,
                onDismiss = { fileChangeSheetPresentation = null },
            )
        }

        uiState.assistantRevertSheet?.let { sheetState ->
            AssistantRevertSheet(
                sheetState = sheetState,
                onClose = onDismissAssistantRevertSheet,
                onConfirm = onConfirmAssistantRevert,
            )
        }
    }
}

private fun isTimelineNearBottom(
    state: LazyListState,
    thresholdPx: Int,
): Boolean {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) {
        return true
    }

    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return true
    val lastItemIndex = totalItems - 1
    if (lastVisibleItem.index != lastItemIndex) {
        return false
    }

    val bottomOverflowPx = (lastVisibleItem.offset + lastVisibleItem.size) - layoutInfo.viewportEndOffset
    return bottomOverflowPx <= thresholdPx
}

@Composable
private fun ConversationCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    hapticOnClick: Boolean = false,
) {
    val chrome = remodexConversationChrome()
    val performLightHaptic = rememberLightImpactHaptic()
    val buttonColor = when {
        filled && enabled -> chrome.sendButton
        filled -> chrome.sendButtonDisabled
        else -> chrome.mutedSurface
    }
    val iconTint = when {
        filled && enabled -> chrome.sendIcon
        filled -> chrome.sendIconDisabled
        else -> if (enabled) chrome.titleText else chrome.secondaryText.copy(alpha = 0.72f)
    }
    Surface(
        modifier = modifier.requiredSize(ComposerTrailingButtonSize),
        color = buttonColor,
        shape = CircleShape,
        border = if (filled) null else BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = enabled,
                    onClick = {
                        if (hapticOnClick) {
                            performLightHaptic()
                        }
                        onClick()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(14.dp),
                tint = iconTint,
            )
        }
    }
}

@Composable
private fun ConversationMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = remodexConversationChrome().bodyText,
    enablesSelection: Boolean = false,
) {
    ConversationRichMarkdownContent(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        enablesSelection = enablesSelection,
    )
}

private enum class ConversationTextBlockKind {
    PROSE,
    CODE,
}

private data class ConversationTextBlock(
    val kind: ConversationTextBlockKind,
    val text: String,
)

private val conversationInlineMarkdownPattern = Regex("""\[[^\]]+]\([^)]+\)|`[^`]+`|\*\*[^*]+\*\*|__[^_]+__""")
private val heavyStreamingMarkdownSignalRegex = Regex(
    pattern = """(?m)^\s{0,3}(#{1,6}\s|[-*+]\s|>\s|\d+\.\s|```|\|)|!\[[^\]]*]\([^)]+\)|```mermaid""",
)

internal fun shouldUseLightweightStreamingAssistantText(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return true
    }
    return !heavyStreamingMarkdownSignalRegex.containsMatchIn(trimmed)
}

private fun parseConversationTextBlocks(text: String): List<ConversationTextBlock> {
    if (text.isBlank()) {
        return emptyList()
    }

    val blocks = mutableListOf<ConversationTextBlock>()
    val lines = text.replace("\r\n", "\n").split('\n')
    val buffer = StringBuilder()
    var inCodeFence = false

    fun flushBuffer() {
        if (buffer.isEmpty()) {
            return
        }
        val raw = buffer.toString().trimEnd('\n')
        buffer.clear()
        if (raw.isBlank()) {
            return
        }
        blocks += ConversationTextBlock(
            kind = if (inCodeFence) ConversationTextBlockKind.CODE else ConversationTextBlockKind.PROSE,
            text = raw,
        )
    }

    lines.forEach { line ->
        if (line.trim().startsWith("```")) {
            flushBuffer()
            inCodeFence = !inCodeFence
        } else {
            buffer.append(line).append('\n')
        }
    }
    flushBuffer()

    return if (blocks.isEmpty()) {
        listOf(ConversationTextBlock(ConversationTextBlockKind.PROSE, text.trim()))
    } else {
        blocks
    }
}

private fun buildConversationAnnotatedString(
    text: String,
    chrome: RemodexConversationChrome,
    monoFamily: FontFamily?,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0

    conversationInlineMarkdownPattern.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }

        val raw = match.value
        when {
            raw.startsWith("[") -> {
                val label = raw.substringAfter('[').substringBefore(']')
                withStyle(
                    SpanStyle(
                        color = chrome.accent,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append(label)
                }
            }

            raw.startsWith("`") -> {
                withStyle(
                    SpanStyle(
                        fontFamily = monoFamily,
                        background = chrome.nestedSurface,
                        color = chrome.bodyText,
                    ),
                ) {
                    append(raw.removePrefix("`").removeSuffix("`"))
                }
            }

            raw.startsWith("**") || raw.startsWith("__") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(raw.drop(2).dropLast(2))
                }
            }
        }

        cursor = match.range.last + 1
    }

    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

@Composable
private fun ConversationTopOverlays(
    uiState: AppUiState,
    onRetryConnection: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    if (uiState.conversationBanner == null && uiState.isConnected) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.conversationBanner?.let { banner ->
            BannerCard(text = banner)
        }

        if (!uiState.isConnected) {
            Surface(
                color = chrome.panelSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, chrome.subtleBorder),
                shape = RemodexConversationShapes.card,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = uiState.connectionHeadline,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = chrome.titleText,
                        )
                        Text(
                            text = uiState.connectionMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = chrome.secondaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SecondaryBarAction(label = "Reconnect", onClick = onRetryConnection)
                }
            }
        }
    }
}

@Composable
private fun BannerCard(text: String) {
    val chrome = remodexConversationChrome()
    Surface(
        color = chrome.accentSurface,
        shape = RemodexConversationShapes.card,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.bodyText,
            )
        }
    }
}

@Composable
private fun PlanAccessoryCard(
    planItem: RemodexConversationItem,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val planSummary = planItem.planState?.explanation
        ?.takeIf(String::isNotBlank)
        ?: planItem.planState?.steps?.firstOrNull()?.step
        ?: planItem.text
    Surface(
        color = chrome.panelSurface,
        shape = RemodexConversationShapes.card,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = chrome.accentSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(7.dp),
                        shape = CircleShape,
                        color = chrome.accent,
                    ) {}
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Plan",
                    style = MaterialTheme.typography.labelMedium,
                    color = chrome.secondaryText,
                )
                Text(
                    text = planSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.bodyText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationState(modifier: Modifier = Modifier) {
    val chrome = remodexConversationChrome()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(chrome.canvas)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Select a thread",
                style = MaterialTheme.typography.titleLarge,
                color = chrome.titleText,
            )
            Text(
                text = "The phone conversation path now follows the iOS visual language, including the updated message and composer chrome.",
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.secondaryText,
            )
        }
    }
}

@Composable
private fun EmptyThreadTimelineCard() {
    val chrome = remodexConversationChrome()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = chrome.titleText,
        )
        Text(
            text = "The next reply from your Mac will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = chrome.secondaryText,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationStatusSheet(
    usageStatus: RemodexUsageStatus,
    isRefreshingUsage: Boolean,
    onRefreshUsageStatus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ConversationStatusSheetTag)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = chrome.titleText,
            )

            Surface(
                color = chrome.panelSurface,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, chrome.subtleBorder),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ComposerUsageStatusSummaryContent(
                        contextWindowUsage = usageStatus.contextWindowUsage,
                        rateLimitBuckets = usageStatus.rateLimitBuckets,
                        rateLimitsErrorMessage = usageStatus.rateLimitsErrorMessage,
                        isRefreshing = isRefreshingUsage,
                        onRefresh = onRefreshUsageStatus,
                        showRefreshButton = false,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDetailsSheet(
    planItem: RemodexConversationItem,
    onDismiss: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val planState = planItem.planState
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Active plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = chrome.titleText,
            )
            planState?.explanation?.takeIf(String::isNotBlank)?.let { explanation ->
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = chrome.bodyText,
                )
            }
            if (!planState?.steps.isNullOrEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    planState?.steps.orEmpty().forEach { step ->
                        Surface(
                            color = chrome.mutedSurface,
                            shape = RemodexConversationShapes.nestedCard,
                            border = BorderStroke(1.dp, chrome.subtleBorder),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = step.step,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = chrome.bodyText,
                                )
                                MetaPill(
                                    label = step.status.label,
                                    backgroundColor = chrome.accentSurface,
                                    contentColor = chrome.titleText,
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = planItem.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = chrome.bodyText,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QueuedDraftsCard(
    queuedDrafts: List<RemodexQueuedDraft>,
    canSendQueuedDrafts: Boolean,
    onSendQueuedDraft: (String) -> Unit,
) {
    val chrome = remodexConversationChrome()
    Surface(
        color = chrome.panelSurface,
        shape = RemodexConversationShapes.card,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            queuedDrafts.forEach { draft ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "↩",
                        style = MaterialTheme.typography.bodySmall,
                        color = chrome.secondaryText,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = draft.text.ifBlank {
                                when (draft.attachments.size) {
                                    0 -> "Saved Android follow-up"
                                    1 -> "Saved follow-up with 1 image"
                                    else -> "Saved follow-up with ${draft.attachments.size} images"
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.bodyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (draft.attachments.isNotEmpty()) {
                            Text(
                                text = "${draft.attachments.size} attachment(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = chrome.secondaryText,
                            )
                        }
                    }
                    if (canSendQueuedDrafts) {
                        SecondaryBarAction(label = "Send", onClick = { onSendQueuedDraft(draft.id) })
                    } else {
                        Text(
                            text = "Queued",
                            style = MaterialTheme.typography.labelSmall,
                            color = chrome.secondaryText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GitContextCard(
    gitState: RemodexGitState,
    selectedBaseBranch: String,
    onSelectBaseBranch: (String) -> Unit,
    onRefresh: () -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onCreateBranch: (String) -> Unit,
    onCreateWorktree: (String) -> Unit,
    onCommit: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onDiscardRuntimeChangesAndSync: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    if (!gitState.hasContext) {
        return
    }

    var branchDraft by rememberSaveable { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = chrome.panelSurfaceStrong,
        ),
        shape = RemodexConversationShapes.panel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Git & Worktree",
                style = MaterialTheme.typography.titleMedium,
                color = chrome.titleText,
            )
            gitState.sync?.let { sync ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = buildString {
                            append(sync.currentBranch ?: "Unknown branch")
                            sync.trackingBranch?.takeIf(String::isNotBlank)?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = chrome.titleText,
                    )
                    Text(
                        text = buildString {
                            append(sync.state.replace('_', ' '))
                            if (sync.diffTotals != null) {
                                append(" · +${sync.diffTotals.additions} / -${sync.diffTotals.deletions}")
                            }
                            if (sync.files.isNotEmpty()) {
                                append(" · ${sync.files.size} files")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = chrome.secondaryText,
                    )
                }
            }
            gitState.lastActionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.accent,
                )
            }
            RuntimeControlsSection(
                title = "Compare Against",
                options = gitState.branches.branches,
                selected = selectedBaseBranch.takeIf(String::isNotBlank),
                label = { it },
                includeClearChoice = false,
                itemKey = { it },
                onSelect = onSelectBaseBranch,
            )
            RuntimeControlsSection(
                title = "Checkout",
                options = gitState.branches.branches,
                selected = gitState.branches.currentBranch,
                label = { branch ->
                    if (gitState.branches.branchesCheckedOutElsewhere.contains(branch)) {
                        "$branch (elsewhere)"
                    } else {
                        branch
                    }
                },
                includeClearChoice = false,
                itemKey = { it },
                onSelect = { branch ->
                    if (!gitState.branches.branchesCheckedOutElsewhere.contains(branch)) {
                        onCheckoutBranch(branch)
                    }
                },
            )
            OutlinedTextField(
                value = branchDraft,
                onValueChange = { branchDraft = it },
                label = { Text("Branch or worktree name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onRefresh) {
                    Text(if (gitState.isLoading) "Refreshing..." else "Refresh")
                }
                OutlinedButton(onClick = onPull) {
                    Text("Pull")
                }
                OutlinedButton(onClick = onPush) {
                    Text("Push")
                }
                OutlinedButton(onClick = onCommit) {
                    Text("Commit")
                }
                OutlinedButton(
                    onClick = {
                        if (branchDraft.isNotBlank()) {
                            onCreateBranch(branchDraft.trim())
                            branchDraft = ""
                        }
                    },
                ) {
                    Text("Create branch")
                }
                OutlinedButton(
                    onClick = {
                        if (branchDraft.isNotBlank()) {
                            onCreateWorktree(branchDraft.trim())
                            branchDraft = ""
                        }
                    },
                ) {
                    Text("Create worktree")
                }
                OutlinedButton(onClick = onDiscardRuntimeChangesAndSync) {
                    Text("Discard & sync")
                }
            }
        }
    }
}

@Composable
private fun ComposerSecondaryBar(
    thread: RemodexThreadSummary,
    gitState: RemodexGitState,
    usageStatus: RemodexUsageStatus,
    isRefreshingUsage: Boolean,
    accessMode: RemodexAccessMode,
    onSelectAccessMode: (RemodexAccessMode) -> Unit,
    onRefreshUsageStatus: () -> Unit,
    onOpenGitSheet: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val uriHandler = LocalUriHandler.current
    val isWorktreeProject = remember(thread.projectPath) {
        isCodexManagedWorktreeProject(thread.projectPath)
    }
    val showsThreadRunningUi = thread.isRunning && thread.latestTurnTerminalState == null
    val runtimeLabel = if (isWorktreeProject) "Worktree" else "Local"
    val branchLabel = remember(gitState) { composerSecondaryBarBranchLabel(gitState) }
    val showsGitBranchSelector = gitState.hasContext
    val branchSelectorEnabled = showsGitBranchSelector && !showsThreadRunningUi
    val canHandOffToWorktree = showsGitBranchSelector && !showsThreadRunningUi && !isWorktreeProject
    val isEmptyThread = thread.messages.isEmpty()
    var runtimeExpanded by remember(thread.id) { mutableStateOf(false) }
    var accessExpanded by remember(thread.id) { mutableStateOf(false) }
    var usageExpanded by remember(thread.id) { mutableStateOf(false) }
    val usageRingSize = 34.dp
    val branchPillMaxWidth = 168.dp

    LaunchedEffect(usageExpanded, thread.id) {
        if (usageExpanded && !isRefreshingUsage) {
            onRefreshUsageStatus()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    SecondaryBarPill(
                        onClick = { runtimeExpanded = !runtimeExpanded },
                        hapticOnClick = true,
                    ) {
                        if (isWorktreeProject) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = chrome.secondaryText,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Computer,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = chrome.secondaryText,
                            )
                        }
                        Text(
                            text = runtimeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = chrome.secondaryText,
                            maxLines = 1,
                        )
                        RuntimeSelectorChevron()
                    }
                    ComposerDropdownMenu(
                        expanded = runtimeExpanded,
                        onDismissRequest = { runtimeExpanded = false },
                    ) {
                        RuntimeMenuSectionLabel("Continue in")
                        ComposerDropdownMenuItem(
                            text = { Text("Cloud") },
                            onClick = {
                                runtimeExpanded = false
                                uriHandler.openUri("https://chatgpt.com/codex")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = chrome.secondaryText,
                                )
                            },
                        )
                        ComposerDropdownMenuItem(
                            text = {
                                Text(
                                    if (isEmptyThread) "New worktree" else "Hand off to worktree",
                                )
                            },
                            onClick = {
                                runtimeExpanded = false
                                onOpenGitSheet()
                            },
                            enabled = canHandOffToWorktree,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = chrome.secondaryText,
                                )
                            },
                        )
                        ComposerDropdownMenuItem(
                            text = { Text("Local") },
                            onClick = { runtimeExpanded = false },
                            enabled = false,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Computer,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = chrome.secondaryText,
                                )
                            },
                        )
                    }
                }

                Box {
                    SecondaryBarPill(
                        onClick = { accessExpanded = !accessExpanded },
                        hapticOnClick = true,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (accessMode == RemodexAccessMode.FULL_ACCESS) {
                                Color(0xFFFF9500)
                            } else {
                                chrome.secondaryText
                            },
                        )
                        RuntimeSelectorChevron()
                    }
                    ComposerDropdownMenu(
                        expanded = accessExpanded,
                        onDismissRequest = { accessExpanded = false },
                    ) {
                        RemodexAccessMode.entries.forEach { mode ->
                            ComposerDropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    accessExpanded = false
                                    onSelectAccessMode(mode)
                                },
                                trailingIcon = if (mode == accessMode) {
                                    { ComposerMenuCheckmark() }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (showsGitBranchSelector) {
                    SecondaryBarPill(
                        onClick = onOpenGitSheet,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .weight(1f, fill = false)
                            .widthIn(max = branchPillMaxWidth),
                        enabled = branchSelectorEnabled,
                        hapticOnClick = true,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = chrome.secondaryText,
                        )
                        Text(
                            text = branchLabel,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = chrome.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RuntimeSelectorChevron()
                    }
                }

                Box(
                    modifier = Modifier.requiredSize(usageRingSize),
                    contentAlignment = Alignment.Center,
                ) {
                    ContextWindowStatusRing(
                        usage = usageStatus.contextWindowUsage,
                        isRefreshing = isRefreshingUsage,
                        onClick = { usageExpanded = true },
                    )
                    ComposerStatusPopover(
                        expanded = usageExpanded,
                        onDismissRequest = { usageExpanded = false },
                    ) {
                        ComposerUsageStatusSummaryContent(
                            contextWindowUsage = usageStatus.contextWindowUsage,
                            rateLimitBuckets = usageStatus.rateLimitBuckets,
                            rateLimitsErrorMessage = usageStatus.rateLimitsErrorMessage,
                            isRefreshing = isRefreshingUsage,
                            onRefresh = onRefreshUsageStatus,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryBarAction(
    label: String,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    Surface(
        color = chrome.mutedSurface,
        shape = RemodexConversationShapes.pill,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = chrome.secondaryText,
        )
    }
}

@Composable
private fun SecondaryBarPill(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticOnClick: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val chrome = remodexConversationChrome()
    val performLightHaptic = rememberLightImpactHaptic()
    Surface(
        modifier = modifier,
        color = chrome.mutedSurface,
        shape = RemodexConversationShapes.pill,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    enabled = enabled && onClick != null,
                    onClick = {
                        if (hapticOnClick) {
                            performLightHaptic()
                        }
                        onClick?.invoke()
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun ContextWindowStatusRing(
    usage: RemodexContextWindowUsage?,
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val density = LocalDensity.current
    val performLightHaptic = rememberLightImpactHaptic()
    val progress = usage?.fractionUsed?.coerceIn(0.0, 1.0)?.toFloat()
    val ringColor = when {
        progress == null -> chrome.secondaryText.copy(alpha = 0.55f)
        progress >= 0.85f -> FileChangeDeletedColor
        progress >= 0.65f -> Color(0xFFFF9500)
        else -> chrome.secondaryText.copy(alpha = 0.68f)
    }

    Surface(
        color = chrome.mutedSurface,
        shape = CircleShape,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clickable(
                    onClick = {
                        performLightHaptic()
                        onClick()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (progress == null && isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.6.dp,
                    color = chrome.secondaryText.copy(alpha = 0.6f),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        val strokeWidth = with(density) { 2.25.dp.toPx() }
                        drawCircle(
                            color = chrome.subtleBorder.copy(alpha = 0.88f),
                            style = Stroke(width = strokeWidth),
                        )
                        if (progress != null) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                ),
                            )
                        }
                    }
                    progress?.let {
                        Text(
                            text = "${(it * 100).toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 6.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = ringColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerStatusPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chrome = remodexConversationChrome()
    val density = LocalDensity.current
    val verticalGapPx = with(density) { 8.dp.roundToPx() }
    val windowMarginPx = with(density) { 12.dp.roundToPx() }
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }
    if (!transitionState.currentState && !transitionState.targetState) {
        return
    }

    Popup(
        popupPositionProvider = remember(verticalGapPx, windowMarginPx) {
            ComposerMenuPositionProvider(
                verticalGapPx = verticalGapPx,
                windowMarginPx = windowMarginPx,
            )
        },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 190),
                    initialOffsetY = { fullHeight -> fullHeight / 10 },
                ) +
                scaleIn(
                    animationSpec = tween(durationMillis = 170),
                    initialScale = 0.97f,
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 120),
                    targetOffsetY = { fullHeight -> fullHeight / 14 },
                ) +
                scaleOut(
                    animationSpec = tween(durationMillis = 120),
                    targetScale = 0.985f,
                ),
        ) {
            Surface(
                color = chrome.panelSurfaceStrong,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, chrome.subtleBorder),
                shadowElevation = 8.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 260.dp, max = 320.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun ComposerUsageStatusSummaryContent(
    contextWindowUsage: RemodexContextWindowUsage?,
    rateLimitBuckets: List<RemodexRateLimitBucket>,
    rateLimitsErrorMessage: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showRefreshButton: Boolean = true,
) {
    val chrome = remodexConversationChrome()
    val visibleRows = remember(rateLimitBuckets) {
        RemodexRateLimitBucket.visibleDisplayRows(rateLimitBuckets)
    }

    if (showRefreshButton) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = chrome.secondaryText,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = chrome.secondaryText,
                    )
                }
                Text(
                    text = if (isRefreshing) "Refreshing..." else "Refresh",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = chrome.secondaryText,
                )
            }
        }
    }

    Text(
        text = "Rate limits",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = chrome.titleText,
    )

    when {
        visibleRows.isNotEmpty() -> {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                visibleRows.forEach { row ->
                    ComposerUsageRateLimitRow(row = row)
                }
            }
        }

        rateLimitsErrorMessage?.isNotBlank() == true -> {
            Text(
                text = rateLimitsErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
            )
        }

        isRefreshing -> {
            Text(
                text = "Loading current limits...",
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
            )
        }

        else -> {
            Text(
                text = "Rate limits are unavailable for this account.",
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
            )
        }
    }

    HorizontalDivider(color = chrome.subtleBorder.copy(alpha = 0.7f))

    Text(
        text = "Context window",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = chrome.titleText,
    )

    if (contextWindowUsage != null) {
        ComposerUsageMetricRow(
            label = "Context",
            value = "${contextWindowUsage.percentRemaining}% left",
            detail = "(${composerCompactTokenCount(contextWindowUsage.tokensUsed)} used / ${composerCompactTokenCount(contextWindowUsage.tokenLimit)})",
        )
        ComposerUsageProgressBar(progress = contextWindowUsage.fractionUsed.toFloat())
    } else {
        ComposerUsageMetricRow(
            label = "Context",
            value = "Unavailable",
            detail = "Waiting for token usage",
        )
    }
}

@Composable
private fun ComposerUsageRateLimitRow(
    row: RemodexRateLimitDisplayRow,
) {
    val chrome = remodexConversationChrome()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${row.window.remainingPercent}% left",
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.titleText,
                fontFamily = FontFamily.Monospace,
            )
            composerResetLabel(row.window)?.let { label ->
                Text(
                    text = " ($label)",
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryText,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        ComposerUsageProgressBar(progress = row.window.clampedUsedPercent / 100f)
    }
}

@Composable
private fun ComposerUsageMetricRow(
    label: String,
    value: String,
    detail: String,
) {
    val chrome = remodexConversationChrome()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = chrome.secondaryText,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = chrome.titleText,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = " $detail",
            style = MaterialTheme.typography.bodySmall,
            color = chrome.secondaryText,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ComposerUsageProgressBar(progress: Float) {
    val chrome = remodexConversationChrome()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(
                color = chrome.subtleBorder.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(12.dp)
                .background(
                    color = chrome.titleText,
                    shape = RoundedCornerShape(10.dp),
                ),
        )
    }
}

private fun composerSecondaryBarBranchLabel(gitState: RemodexGitState): String {
    return gitState.branches.currentBranch
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: gitState.sync?.currentBranch
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        ?: gitState.branches.defaultBranch
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        ?: "Branch"
}

private fun composerCompactTokenCount(count: Int): String {
    return when {
        count >= 1_000_000 -> {
            val value = count / 100_000.0
            if (value % 10.0 == 0.0) {
                "${(value / 10.0).toInt()}M"
            } else {
                String.format(Locale.US, "%.1fM", value / 10.0)
            }
        }
        count >= 1_000 -> {
            val value = count / 100.0
            if (value % 10.0 == 0.0) {
                "${(value / 10.0).toInt()}K"
            } else {
                String.format(Locale.US, "%.1fK", value / 10.0)
            }
        }
        else -> "%,d".format(Locale.US, count)
    }
}

private fun composerResetLabel(window: com.emanueledipietro.remodex.model.RemodexRateLimitWindow): String? {
    val resetsAtEpochMs = window.resetsAtEpochMs ?: return null
    val now = System.currentTimeMillis()
    val remainingMs = (resetsAtEpochMs - now).coerceAtLeast(0L)
    val totalMinutes = remainingMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L -> "resets ${hours}h ${minutes}m"
        totalMinutes > 0L -> "resets ${totalMinutes}m"
        else -> "resets soon"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailedGitSheet(
    gitState: RemodexGitState,
    selectedBaseBranch: String,
    onDismiss: () -> Unit,
    onSelectBaseBranch: (String) -> Unit,
    onRefresh: () -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onCreateBranch: (String) -> Unit,
    onCreateWorktree: (String) -> Unit,
    onCommit: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onDiscardRuntimeChangesAndSync: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        GitContextCard(
            gitState = gitState,
            selectedBaseBranch = selectedBaseBranch,
            onSelectBaseBranch = onSelectBaseBranch,
            onRefresh = onRefresh,
            onCheckoutBranch = onCheckoutBranch,
            onCreateBranch = onCreateBranch,
            onCreateWorktree = onCreateWorktree,
            onCommit = onCommit,
            onPull = onPull,
            onPush = onPush,
            onDiscardRuntimeChangesAndSync = onDiscardRuntimeChangesAndSync,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerCard(
    uiState: AppUiState,
    onComposerInputChanged: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onStopTurn: () -> Unit,
    onSelectModel: (String?) -> Unit,
    onSelectPlanningMode: (RemodexPlanningMode) -> Unit,
    onSelectReasoningEffort: (String) -> Unit,
    onSelectAccessMode: (RemodexAccessMode) -> Unit,
    onSelectServiceTier: (RemodexServiceTier?) -> Unit,
    onOpenAttachmentPicker: () -> Unit,
    onOpenCameraCapture: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSelectFileAutocomplete: (RemodexFuzzyFileMatch) -> Unit,
    onRemoveMentionedFile: (String) -> Unit,
    onSelectSkillAutocomplete: (RemodexSkillMetadata) -> Unit,
    onRemoveMentionedSkill: (String) -> Unit,
    onSelectSlashCommand: (RemodexSlashCommand) -> Unit,
    onSelectCodeReviewTarget: (RemodexComposerReviewTarget) -> Unit,
    onClearReviewSelection: () -> Unit,
    onClearSubagentsSelection: () -> Unit,
    onCloseComposerAutocomplete: () -> Unit,
    onForkThread: (RemodexComposerForkDestination) -> Unit,
    onComposerFocusChanged: (Boolean) -> Unit,
) {
    val chrome = remodexConversationChrome()
    val composer = uiState.composer
    var composerInputValue by rememberSaveable(
        uiState.selectedThread?.id,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(
            TextFieldValue(
                text = composer.draftText,
                selection = TextRange(composer.draftText.length),
            ),
        )
    }
    LaunchedEffect(composer.draftText) {
        composerInputValue = syncComposerInputValue(
            currentValue = composerInputValue,
            externalText = composer.draftText,
        )
    }
    val queuedCount = composer.queuedDrafts.size
    val orderedModels = remember(composer.runtimeConfig.availableModels) {
        RemodexRuntimeMetaMapper.orderedModels(composer.runtimeConfig.availableModels)
    }
    val selectedModelOption = remember(orderedModels, composer.runtimeConfig.selectedModelId) {
        orderedModels.firstOrNull { option ->
            option.id == composer.runtimeConfig.selectedModelId || option.model == composer.runtimeConfig.selectedModelId
        }
    }
    val selectedModelTitle = selectedModelOption
        ?.let(RemodexRuntimeMetaMapper::modelTitle)
        ?: composer.runtimeConfig.selectedModelId
        ?: "Auto"
    val selectedReasoningOption = remember(
        composer.runtimeConfig.availableReasoningEfforts,
        composer.runtimeConfig.reasoningEffort,
    ) {
        composer.runtimeConfig.availableReasoningEfforts.firstOrNull { option ->
            option.reasoningEffort == composer.runtimeConfig.reasoningEffort
        }
    }
    val canAddAttachments = composer.attachments.size < composer.maxAttachments
    var plusMenuExpanded by rememberSaveable(uiState.selectedThread?.id) { mutableStateOf(false) }

    Surface(
        color = chrome.panelSurfaceStrong,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shape = RemodexConversationShapes.composer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (composer.attachments.isNotEmpty() ||
                composer.mentionedFiles.isNotEmpty() ||
                composer.mentionedSkills.isNotEmpty() ||
                composer.reviewSelection != null ||
                composer.isSubagentsSelectionArmed
            ) {
                ComposerAccessoryStrip(
                    uiState = uiState,
                    onRemoveAttachment = onRemoveAttachment,
                    onRemoveMentionedFile = onRemoveMentionedFile,
                    onRemoveMentionedSkill = onRemoveMentionedSkill,
                    onClearReviewSelection = onClearReviewSelection,
                    onClearSubagentsSelection = onClearSubagentsSelection,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (composerInputValue.text.isBlank()) {
                    Text(
                        text = if (composer.canStop) {
                            "Queue a follow-up"
                        } else {
                            "Ask anything... @files, \$skills, /commands"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = chrome.secondaryText.copy(alpha = 0.86f),
                    )
                }
                BasicTextField(
                    value = composerInputValue,
                    onValueChange = { nextValue ->
                        composerInputValue = nextValue
                        if (nextValue.text != composer.draftText) {
                            onComposerInputChanged(nextValue.text)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            onComposerFocusChanged(focusState.isFocused)
                        },
                    minLines = 1,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = chrome.bodyText,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                    cursorBrush = SolidColor(chrome.accent),
                )
            }

            composer.attachmentLimitMessage?.let { limitMessage ->
                Text(
                    text = limitMessage,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.destructive,
                )
            }
            composer.composerMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.destructive,
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            ) {
                val compactSpacing = if (maxWidth < 360.dp) 4.dp else 6.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(compactSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            val performLightHaptic = rememberLightImpactHaptic()
                            Box(
                                modifier = Modifier
                                    .requiredSize(ComposerLeadingIconTapTarget)
                                    .clickable(
                                        onClick = {
                                            performLightHaptic()
                                            plusMenuExpanded = !plusMenuExpanded
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = chrome.secondaryText,
                                )
                            }
                            ComposerDropdownMenu(
                                expanded = plusMenuExpanded,
                                onDismissRequest = { plusMenuExpanded = false },
                            ) {
                                ComposerDropdownMenuItem(
                                    text = { Text("Take a photo") },
                                    enabled = canAddAttachments,
                                    onClick = {
                                        plusMenuExpanded = false
                                        onOpenCameraCapture()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.CameraAlt,
                                            contentDescription = null,
                                        )
                                    },
                                )
                                ComposerDropdownMenuItem(
                                    text = { Text("Photo library") },
                                    enabled = canAddAttachments,
                                    onClick = {
                                        plusMenuExpanded = false
                                        onOpenAttachmentPicker()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.AddPhotoAlternate,
                                            contentDescription = null,
                                        )
                                    },
                                )
                                ComposerDropdownMenuItem(
                                    text = { Text("Plan mode") },
                                    onClick = {
                                        plusMenuExpanded = false
                                        onSelectPlanningMode(
                                            if (composer.runtimeConfig.planningMode == RemodexPlanningMode.PLAN) {
                                                RemodexPlanningMode.AUTO
                                            } else {
                                                RemodexPlanningMode.PLAN
                                            },
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Checklist,
                                            contentDescription = null,
                                        )
                                    },
                                    trailingIcon = if (composer.runtimeConfig.planningMode == RemodexPlanningMode.PLAN) {
                                        {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }

                        CompactRuntimeSelector(
                            title = selectedModelTitle,
                            options = orderedModels,
                            selected = selectedModelOption,
                            label = { option -> RemodexRuntimeMetaMapper.modelTitle(option) },
                            key = { option -> option.id },
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = ComposerModelMenuMaxWidth),
                            leadingIcon = if (composer.runtimeConfig.serviceTier != null) {
                                Icons.Outlined.Bolt
                            } else {
                                null
                            },
                            onClear = { onSelectModel(null) },
                            onSelect = { option -> onSelectModel(option.id) },
                        )
                        ReasoningRuntimeSelector(
                            title = composer.runtimeConfig.reasoningEffort
                                ?.let(RemodexRuntimeMetaMapper::reasoningTitle)
                                ?: "Auto",
                            reasoningOptions = composer.runtimeConfig.availableReasoningEfforts,
                            selectedReasoning = selectedReasoningOption,
                            onSelectReasoning = { option -> onSelectReasoningEffort(option.reasoningEffort) },
                            speedOptions = composer.runtimeConfig.availableServiceTiers,
                            selectedSpeed = composer.runtimeConfig.serviceTier,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = ComposerReasoningMenuMaxWidth),
                            onSelectSpeed = onSelectServiceTier,
                        )
                        if (composer.runtimeConfig.planningMode == RemodexPlanningMode.PLAN) {
                            ComposerPlanIndicator()
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (composer.canStop) {
                            ConversationCircleButton(
                                icon = Icons.Outlined.Close,
                                contentDescription = "Stop",
                                onClick = onStopTurn,
                            )
                        }
                        Box {
                            ConversationCircleButton(
                                modifier = Modifier.testTag(ComposerSendButtonTag),
                                icon = Icons.Outlined.KeyboardArrowUp,
                                contentDescription = composer.sendLabel,
                                onClick = onSendPrompt,
                                enabled = composer.canSend,
                                filled = true,
                                hapticOnClick = true,
                            )
                            if (queuedCount > 0) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-6).dp),
                                    shape = CircleShape,
                                    color = if (composer.canStop) {
                                        chrome.warning
                                    } else {
                                        chrome.accent
                                    },
                                ) {
                                    Text(
                                        text = queuedCount.toString(),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = chrome.sendIcon,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerAccessoryStrip(
    uiState: AppUiState,
    onRemoveAttachment: (String) -> Unit,
    onRemoveMentionedFile: (String) -> Unit,
    onRemoveMentionedSkill: (String) -> Unit,
    onClearReviewSelection: () -> Unit,
    onClearSubagentsSelection: () -> Unit,
) {
    val composer = uiState.composer
    val chrome = remodexConversationChrome()
    val neutralChipColors = remember(chrome) {
        ComposerAccessoryChipColors(
            tint = chrome.titleText,
            background = chrome.mutedSurface,
            removeBackground = chrome.subtleBorder.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, chrome.subtleBorder),
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (composer.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                composer.attachments.forEach { attachment ->
                    AttachmentPreviewCard(
                        attachment = attachment,
                        onRemoveAttachment = onRemoveAttachment,
                    )
                }
            }
        }

        if (composer.mentionedFiles.isNotEmpty()) {
            val fileChipColors = rememberMentionChipColors(
                tint = ComposerMentionFileTint,
                chrome = chrome,
            )
            AccessoryChipRow {
                composer.mentionedFiles.forEach { mention ->
                    AccessoryChip(
                        label = mention.fileName,
                        leadingGlyph = "</>",
                        colors = fileChipColors,
                        onRemove = { onRemoveMentionedFile(mention.id) },
                    )
                }
            }
        }

        if (composer.mentionedSkills.isNotEmpty()) {
            val skillChipColors = rememberMentionChipColors(
                tint = ComposerMentionSkillTint,
                chrome = chrome,
            )
            AccessoryChipRow {
                composer.mentionedSkills.forEach { mention ->
                    AccessoryChip(
                        label = formatSkillDisplayName(mention.name),
                        leadingGlyph = "$",
                        colors = skillChipColors,
                        onRemove = { onRemoveMentionedSkill(mention.id) },
                    )
                }
            }
        }

        composer.reviewSelection?.target?.let { target ->
            AccessoryChipRow {
                AccessoryChip(
                    label = "Code Review: ${target.title}",
                    colors = neutralChipColors,
                    onRemove = onClearReviewSelection,
                )
            }
        }

        if (composer.isSubagentsSelectionArmed) {
            AccessoryChipRow {
                AccessoryChip(
                    label = "Subagents",
                    colors = neutralChipColors,
                    onRemove = onClearSubagentsSelection,
                )
            }
        }
    }
}

@Composable
private fun AccessoryChipRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun AccessoryChip(
    label: String,
    leadingGlyph: String? = null,
    colors: ComposerAccessoryChipColors,
    onRemove: () -> Unit,
) {
    Surface(
        color = colors.background,
        shape = RoundedCornerShape(ComposerMentionChipCornerRadius),
        border = colors.border,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leadingGlyph != null) {
                Text(
                    text = leadingGlyph,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                    ),
                    color = colors.tint,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.tint,
            )
            Box(
                modifier = Modifier
                    .size(ComposerMentionRemoveButtonSize)
                    .background(
                        color = colors.removeBackground,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove $label",
                    modifier = Modifier.size(8.dp),
                    tint = colors.tint,
                )
            }
        }
    }
}

@Composable
private fun AutocompletePanel(
    uiState: AppUiState,
    onSelectFileAutocomplete: (RemodexFuzzyFileMatch) -> Unit,
    onSelectSkillAutocomplete: (RemodexSkillMetadata) -> Unit,
    onSelectSlashCommand: (RemodexSlashCommand) -> Unit,
    onSelectCodeReviewTarget: (RemodexComposerReviewTarget) -> Unit,
    onCloseComposerAutocomplete: () -> Unit,
    onForkThread: (RemodexComposerForkDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = remodexConversationChrome()
    val autocomplete = uiState.composer.autocomplete
    if (autocomplete.panel == RemodexComposerAutocompletePanel.NONE) {
        return
    }

    Surface(
        modifier = modifier.testTag(ComposerAutocompletePanelTag),
        shape = RemodexConversationShapes.panel,
        color = chrome.panelSurfaceStrong,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        when (autocomplete.panel) {
            RemodexComposerAutocompletePanel.FILES -> {
                FileAutocompletePanel(
                    items = autocomplete.fileItems,
                    isLoading = autocomplete.isFileLoading,
                    query = autocomplete.fileQuery,
                    onSelect = onSelectFileAutocomplete,
                )
            }

            RemodexComposerAutocompletePanel.SKILLS -> {
                SkillAutocompletePanel(
                    items = autocomplete.skillItems,
                    isLoading = autocomplete.isSkillLoading,
                    query = autocomplete.skillQuery,
                    onSelect = onSelectSkillAutocomplete,
                )
            }

            RemodexComposerAutocompletePanel.COMMANDS -> {
                SlashCommandAutocompletePanel(
                    autocomplete = autocomplete,
                    onSelectCommand = onSelectSlashCommand,
                    onSelectReviewTarget = onSelectCodeReviewTarget,
                    onSelectForkDestination = onForkThread,
                    onClose = onCloseComposerAutocomplete,
                )
            }

            RemodexComposerAutocompletePanel.REVIEW_TARGETS,
            RemodexComposerAutocompletePanel.FORK_DESTINATIONS,
            -> {
                SlashCommandAutocompletePanel(
                    autocomplete = autocomplete,
                    onSelectCommand = onSelectSlashCommand,
                    onSelectReviewTarget = onSelectCodeReviewTarget,
                    onSelectForkDestination = onForkThread,
                    onClose = onCloseComposerAutocomplete,
                )
            }

            RemodexComposerAutocompletePanel.NONE -> Unit
        }
    }
}

@Composable
private fun FileAutocompletePanel(
    items: List<RemodexFuzzyFileMatch>,
    isLoading: Boolean,
    query: String,
    onSelect: (RemodexFuzzyFileMatch) -> Unit,
) {
    if (isLoading) {
        AutocompleteLoadingState("Searching files...")
        return
    }
    if (items.isEmpty()) {
        AutocompleteEmptyState("No files for @$query")
        return
    }

    AutocompleteListContainer(maxHeight = FileAutocompleteRowHeight * MaxAutocompleteVisibleRows) {
        items.forEachIndexed { index, item ->
            FileAutocompleteRow(
                item = item,
                onClick = { onSelect(item) },
            )
            if (index != items.lastIndex) {
                HorizontalDivider(color = remodexConversationChrome().subtleBorder)
            }
        }
    }
}

@Composable
private fun SkillAutocompletePanel(
    items: List<RemodexSkillMetadata>,
    isLoading: Boolean,
    query: String,
    onSelect: (RemodexSkillMetadata) -> Unit,
) {
    if (isLoading) {
        AutocompleteLoadingState("Searching skills...")
        return
    }
    if (items.isEmpty()) {
        AutocompleteEmptyState("No skills for ${'$'}$query")
        return
    }

    AutocompleteListContainer(maxHeight = SkillAutocompleteRowHeight * MaxAutocompleteVisibleRows) {
        items.forEachIndexed { index, skill ->
            SkillAutocompleteRow(
                skill = skill,
                onClick = { onSelect(skill) },
            )
            if (index != items.lastIndex) {
                HorizontalDivider(color = remodexConversationChrome().subtleBorder)
            }
        }
    }
}

@Composable
private fun SlashCommandAutocompletePanel(
    autocomplete: com.emanueledipietro.remodex.model.RemodexComposerAutocompleteState,
    onSelectCommand: (RemodexSlashCommand) -> Unit,
    onSelectReviewTarget: (RemodexComposerReviewTarget) -> Unit,
    onSelectForkDestination: (RemodexComposerForkDestination) -> Unit,
    onClose: () -> Unit,
) {
    when (autocomplete.panel) {
        RemodexComposerAutocompletePanel.COMMANDS -> {
            if (autocomplete.slashCommands.isEmpty()) {
                AutocompleteEmptyState("No commands for /${autocomplete.slashQuery}")
                return
            }
            AutocompleteListContainer(maxHeight = SlashAutocompleteRowHeight * MaxAutocompleteVisibleRows) {
                autocomplete.slashCommands.forEachIndexed { index, command ->
                    val enabled = isCommandEnabled(command, autocomplete)
                    SlashCommandRow(
                        command = command,
                        enabled = enabled,
                        subtitle = commandSubtitle(command, autocomplete, enabled),
                        onClick = { onSelectCommand(command) },
                    )
                    if (index != autocomplete.slashCommands.lastIndex) {
                        HorizontalDivider(color = remodexConversationChrome().subtleBorder)
                    }
                }
            }
        }

        RemodexComposerAutocompletePanel.REVIEW_TARGETS -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                AutocompleteSubmenuHeader(
                    title = "Code Review",
                    subtitle = "Choose what the reviewer should compare.",
                    closeContentDescription = "Close code review options",
                    onClose = onClose,
                )
                autocomplete.reviewTargets.forEachIndexed { index, target ->
                    ReviewTargetRow(
                        target = target,
                        subtitle = reviewTargetSubtitle(target, autocomplete),
                        enabled = target != RemodexComposerReviewTarget.BASE_BRANCH ||
                            resolvedBaseBranchName(autocomplete) != null,
                        onClick = { onSelectReviewTarget(target) },
                    )
                    if (index != autocomplete.reviewTargets.lastIndex) {
                        HorizontalDivider(color = remodexConversationChrome().subtleBorder)
                    }
                }
            }
        }

        RemodexComposerAutocompletePanel.FORK_DESTINATIONS -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                AutocompleteSubmenuHeader(
                    title = "Fork",
                    subtitle = forkDestinationSubtitle(autocomplete.forkDestinations),
                    closeContentDescription = "Close fork options",
                    onClose = onClose,
                )
                autocomplete.forkDestinations.forEachIndexed { index, destination ->
                    ForkDestinationRow(
                        destination = destination,
                        onClick = { onSelectForkDestination(destination) },
                    )
                    if (index != autocomplete.forkDestinations.lastIndex) {
                        HorizontalDivider(color = remodexConversationChrome().subtleBorder)
                    }
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun AutocompleteListContainer(
    maxHeight: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
        content = content,
    )
}

@Composable
private fun AutocompleteLoadingState(text: String) {
    val chrome = remodexConversationChrome()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = chrome.secondaryText,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = chrome.secondaryText,
        )
    }
}

@Composable
private fun AutocompleteEmptyState(text: String) {
    val chrome = remodexConversationChrome()
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodySmall,
        color = chrome.secondaryText,
    )
}

@Composable
private fun FileAutocompleteRow(
    item: RemodexFuzzyFileMatch,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val performLightHaptic = rememberLightImpactHaptic()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FileAutocompleteRowHeight)
            .clickable(
                onClick = {
                    performLightHaptic()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.fileName,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            fontWeight = FontWeight.SemiBold,
            color = chrome.titleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.path,
            style = MaterialTheme.typography.labelSmall,
            color = chrome.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SkillAutocompleteRow(
    skill: RemodexSkillMetadata,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val performLightHaptic = rememberLightImpactHaptic()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SkillAutocompleteRowHeight)
            .clickable(
                onClick = {
                    performLightHaptic()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formatSkillDisplayName(skill.name),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                fontWeight = FontWeight.SemiBold,
                color = chrome.titleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$${skill.name}",
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
                maxLines = 1,
            )
        }
        normalizedSkillDescription(skill.description)?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = chrome.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SlashCommandRow(
    command: RemodexSlashCommand,
    enabled: Boolean,
    subtitle: String,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val performLightHaptic = rememberLightImpactHaptic()
    val contentColor = if (enabled) chrome.titleText else chrome.secondaryText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SlashAutocompleteRowHeight)
            .clickable(
                enabled = enabled,
                onClick = {
                    performLightHaptic()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = slashCommandIcon(command),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.width(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = command.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = chrome.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = command.token,
            style = MaterialTheme.typography.bodySmall,
            color = chrome.secondaryText,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReviewTargetRow(
    target: RemodexComposerReviewTarget,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val performLightHaptic = rememberLightImpactHaptic()
    val contentColor = if (enabled) chrome.titleText else chrome.secondaryText
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(SlashAutocompleteRowHeight)
            .clickable(
                enabled = enabled,
                onClick = {
                    performLightHaptic()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = target.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = remodexConversationChrome().secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ForkDestinationRow(
    destination: RemodexComposerForkDestination,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val performLightHaptic = rememberLightImpactHaptic()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SlashAutocompleteRowHeight)
            .clickable(
                onClick = {
                    performLightHaptic()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = forkDestinationIcon(destination),
            contentDescription = null,
            tint = chrome.titleText,
            modifier = Modifier.width(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = destination.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = chrome.titleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = destination.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = chrome.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AutocompleteSubmenuHeader(
    title: String,
    subtitle: String,
    closeContentDescription: String,
    onClose: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = chrome.titleText,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = chrome.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = CircleShape,
            color = chrome.nestedSurface,
            border = BorderStroke(1.dp, chrome.subtleBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = closeContentDescription,
                    tint = chrome.secondaryText,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun formatSkillDisplayName(rawName: String): String {
    val normalized = rawName.trim()
    if (normalized.isEmpty()) {
        return rawName
    }
    val parts = normalized
        .split(Regex("[-_]+"))
        .filter(String::isNotEmpty)
        .map { part ->
            part.lowercase().replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
        }
    return if (parts.isEmpty()) normalized else parts.joinToString(separator = " ")
}

private fun normalizedSkillDescription(rawDescription: String?): String? {
    val normalized = rawDescription
        ?.split(Regex("\\s+"))
        ?.filter(String::isNotEmpty)
        ?.joinToString(separator = " ")
        ?.trim()
        .orEmpty()
    return normalized.takeIf(String::isNotEmpty)
}

private fun isCommandEnabled(
    command: RemodexSlashCommand,
    autocomplete: com.emanueledipietro.remodex.model.RemodexComposerAutocompleteState,
): Boolean {
    return when (command) {
        RemodexSlashCommand.CODE_REVIEW -> !autocomplete.hasComposerContentConflictingWithReview
        RemodexSlashCommand.FORK -> !autocomplete.isThreadRunning
        RemodexSlashCommand.STATUS,
        RemodexSlashCommand.SUBAGENTS,
        -> true
    }
}

private fun commandSubtitle(
    command: RemodexSlashCommand,
    autocomplete: com.emanueledipietro.remodex.model.RemodexComposerAutocompleteState,
    enabled: Boolean,
): String {
    if (command == RemodexSlashCommand.FORK && autocomplete.isThreadRunning) {
        return "Wait for the current response to finish first"
    }
    if (!enabled) {
        return "Clear draft text, files, skills, and images first"
    }
    return command.subtitle
}

private fun reviewTargetSubtitle(
    target: RemodexComposerReviewTarget,
    autocomplete: com.emanueledipietro.remodex.model.RemodexComposerAutocompleteState,
): String {
    return when (target) {
        RemodexComposerReviewTarget.UNCOMMITTED_CHANGES -> "Review everything currently modified in the repo"
        RemodexComposerReviewTarget.BASE_BRANCH -> {
            resolvedBaseBranchName(autocomplete)?.let { branch ->
                "Diff against $branch"
            } ?: "Pick a base branch first"
        }
    }
}

private fun resolvedBaseBranchName(
    autocomplete: com.emanueledipietro.remodex.model.RemodexComposerAutocompleteState,
): String? {
    return autocomplete.selectedGitBaseBranch.trim().takeIf(String::isNotEmpty)
        ?: autocomplete.gitDefaultBranch.trim().takeIf(String::isNotEmpty)
}

private fun forkDestinationSubtitle(
    destinations: List<RemodexComposerForkDestination>,
): String {
    val showsLocal = destinations.contains(RemodexComposerForkDestination.LOCAL)
    val showsNewWorktree = destinations.contains(RemodexComposerForkDestination.NEW_WORKTREE)
    return when {
        showsLocal && showsNewWorktree -> "Fork this thread into local or a new worktree."
        showsLocal -> "Fork this thread into a new local thread."
        showsNewWorktree -> "Fork this thread into a new worktree."
        else -> "Fork this thread."
    }
}

private fun slashCommandIcon(command: RemodexSlashCommand): ImageVector {
    return when (command) {
        RemodexSlashCommand.CODE_REVIEW -> Icons.Outlined.BugReport
        RemodexSlashCommand.FORK -> Icons.AutoMirrored.Outlined.CallSplit
        RemodexSlashCommand.STATUS -> Icons.Outlined.Speed
        RemodexSlashCommand.SUBAGENTS -> Icons.Outlined.AccountCircle
    }
}

private fun forkDestinationIcon(destination: RemodexComposerForkDestination): ImageVector {
    return when (destination) {
        RemodexComposerForkDestination.LOCAL -> Icons.Outlined.Folder
        RemodexComposerForkDestination.NEW_WORKTREE -> Icons.AutoMirrored.Outlined.CallSplit
    }
}

@Composable
private fun AttachmentPreviewCard(
    attachment: RemodexComposerAttachment,
    onRemoveAttachment: (String) -> Unit,
) {
    val chrome = remodexConversationChrome()
    Box(
        modifier = Modifier
            .size(ComposerAttachmentThumbnailSize + 8.dp)
            .padding(top = 4.dp, end = 4.dp),
    ) {
        Surface(
            modifier = Modifier.size(ComposerAttachmentThumbnailSize),
            color = chrome.nestedSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, chrome.subtleBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = chrome.secondaryText,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                )
                AsyncImage(
                    model = attachment.uriString,
                    contentDescription = attachment.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(ComposerAttachmentRemoveButtonSize),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.68f),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = { onRemoveAttachment(attachment.id) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove attachment",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaPill(
    label: String,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val chrome = remodexConversationChrome()
    Surface(
        color = backgroundColor,
        shape = RemodexConversationShapes.pill,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun <T> CompactRuntimeSelector(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    val chrome = remodexConversationChrome()
    var expanded by remember { mutableStateOf(false) }
    val selectedKey = selected?.let(key)
    Box(modifier = modifier) {
        ComposerMenuTrigger(
            title = title,
            leadingIcon = leadingIcon,
            onClick = { expanded = !expanded },
        )
        ComposerDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            onClear?.let {
                ComposerDropdownMenuItem(
                    text = { Text("Auto") },
                    onClick = {
                        expanded = false
                        it()
                    },
                    trailingIcon = if (selected == null) {
                        { ComposerMenuCheckmark() }
                    } else {
                        null
                    },
                )
            }
            options.forEach { option ->
                ComposerDropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (selectedKey == key(option)) {
                        { ComposerMenuCheckmark() }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun ReasoningRuntimeSelector(
    title: String,
    reasoningOptions: List<com.emanueledipietro.remodex.model.RemodexReasoningEffortOption>,
    selectedReasoning: com.emanueledipietro.remodex.model.RemodexReasoningEffortOption?,
    onSelectReasoning: (com.emanueledipietro.remodex.model.RemodexReasoningEffortOption) -> Unit,
    speedOptions: List<RemodexServiceTier>,
    selectedSpeed: RemodexServiceTier?,
    modifier: Modifier = Modifier,
    onSelectSpeed: (RemodexServiceTier?) -> Unit,
) {
    val chrome = remodexConversationChrome()
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        ComposerMenuTrigger(
            title = title,
            onClick = { expanded = !expanded },
        )
        ComposerDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            RuntimeMenuSectionLabel("Speed")
            speedOptions.forEach { option ->
                ComposerDropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelectSpeed(option)
                    },
                    trailingIcon = if (selectedSpeed == option) {
                        { ComposerMenuCheckmark() }
                    } else {
                        null
                    },
                )
            }
            ComposerDropdownMenuItem(
                text = { Text("Normal") },
                onClick = {
                    expanded = false
                    onSelectSpeed(null)
                },
                trailingIcon = if (selectedSpeed == null) {
                    { ComposerMenuCheckmark() }
                } else {
                    null
                },
            )

            HorizontalDivider()
            RuntimeMenuSectionLabel("Reasoning")
            reasoningOptions.forEach { option ->
                ComposerDropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelectReasoning(option)
                    },
                    trailingIcon = if (selectedReasoning?.reasoningEffort == option.reasoningEffort) {
                        { ComposerMenuCheckmark() }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun RuntimeSelectorChevron() {
    val chrome = remodexConversationChrome()
    Icon(
        imageVector = Icons.Outlined.ExpandMore,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = chrome.secondaryText,
    )
}

@Composable
private fun ComposerMenuTrigger(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val chrome = remodexConversationChrome()
    val performLightHaptic = rememberLightImpactHaptic()
    Row(
        modifier = modifier
            .clickable(
                onClick = {
                    performLightHaptic()
                    onClick()
                },
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leadingIcon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = chrome.secondaryText,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = chrome.secondaryText,
        )
        RuntimeSelectorChevron()
    }
}

@Composable
private fun ComposerPlanIndicator() {
    val chrome = remodexConversationChrome()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(chrome.subtleBorder),
        )
        Text(
            text = "Plan",
            style = MaterialTheme.typography.labelMedium,
            color = chrome.accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun ComposerDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chrome = remodexConversationChrome()
    val density = LocalDensity.current
    val verticalGapPx = with(density) { 8.dp.roundToPx() }
    val windowMarginPx = with(density) { 12.dp.roundToPx() }
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }
    if (!transitionState.currentState && !transitionState.targetState) {
        return
    }

    Popup(
        popupPositionProvider = remember(verticalGapPx, windowMarginPx) {
            ComposerMenuPositionProvider(
                verticalGapPx = verticalGapPx,
                windowMarginPx = windowMarginPx,
            )
        },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false),
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 180),
                    initialOffsetY = { fullHeight -> fullHeight / 8 },
                ) +
                scaleIn(
                    animationSpec = tween(durationMillis = 160),
                    initialScale = 0.96f,
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 120),
                    targetOffsetY = { fullHeight -> fullHeight / 12 },
                ) +
                scaleOut(
                    animationSpec = tween(durationMillis = 110),
                    targetScale = 0.98f,
                ),
        ) {
            Surface(
                color = chrome.panelSurfaceStrong,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, chrome.subtleBorder),
                shadowElevation = 6.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 228.dp)
                        .padding(vertical = 2.dp),
                    content = content,
                )
            }
        }
    }
}

private class ComposerMenuPositionProvider(
    private val verticalGapPx: Int,
    private val windowMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.left
            LayoutDirection.Rtl -> anchorBounds.right - popupContentSize.width
        }
        val maxX = (windowSize.width - popupContentSize.width - windowMarginPx).coerceAtLeast(windowMarginPx)
        val resolvedX = preferredX.coerceIn(windowMarginPx, maxX)

        val aboveY = anchorBounds.top - popupContentSize.height - verticalGapPx
        val belowY = anchorBounds.bottom + verticalGapPx
        val maxY = (windowSize.height - popupContentSize.height - windowMarginPx).coerceAtLeast(windowMarginPx)
        val resolvedY = when {
            aboveY >= windowMarginPx -> aboveY
            belowY <= maxY -> belowY
            else -> aboveY.coerceIn(windowMarginPx, maxY)
        }

        return IntOffset(resolvedX, resolvedY)
    }
}

@Composable
private fun RuntimeMenuSectionLabel(title: String) {
    val chrome = remodexConversationChrome()
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = chrome.secondaryText,
    )
}

@Composable
private fun ComposerDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val performLightHaptic = rememberLightImpactHaptic()
    DropdownMenuItem(
        text = text,
        onClick = {
            performLightHaptic()
            onClick()
        },
        modifier = modifier.heightIn(min = 30.dp),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    )
}

@Composable
private fun ConversationMessageActionContainer(
    text: String,
    messageRole: ConversationSpeaker,
    usesMarkdownSelection: Boolean,
    allowsSelectText: Boolean,
    modifier: Modifier = Modifier,
    alignMenuToEnd: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val trimmedText = remember(text) { text.trim() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val performLightHaptic = rememberLightImpactHaptic()
    var menuExpanded by rememberSaveable(trimmedText, messageRole.name) { mutableStateOf(false) }
    var menuPressOffset by remember(trimmedText, messageRole.name) { mutableStateOf(IntOffset.Zero) }
    var selectableTextSheetState by remember(trimmedText, messageRole.name, usesMarkdownSelection) {
        mutableStateOf<SelectableMessageTextSheetState?>(null)
    }

    val gestureModifier = if (trimmedText.isNotEmpty() || onClick != null) {
        Modifier
            .pointerInput(trimmedText, messageRole.name, onClick, density) {
                detectTapGestures(
                    onTap = {
                        onClick?.invoke()
                    },
                    onLongPress = { pressOffset ->
                        performLightHaptic()
                        menuPressOffset = IntOffset(
                            x = pressOffset.x.toInt(),
                            y = pressOffset.y.toInt(),
                        )
                        menuExpanded = true
                    },
                )
            }
            .semantics(mergeDescendants = false) {
                role = Role.Button
                if (onClick != null) {
                    onClick {
                        onClick.invoke()
                        true
                    }
                }
                if (trimmedText.isNotEmpty()) {
                    onLongClick {
                        performLightHaptic()
                        menuPressOffset = IntOffset.Zero
                        menuExpanded = true
                        true
                    }
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(gestureModifier),
        contentAlignment = if (alignMenuToEnd) Alignment.TopEnd else Alignment.TopStart,
    ) {
        content()
        ConversationMessageContextMenu(
            expanded = menuExpanded,
            alignToTrailing = alignMenuToEnd,
            pressOffset = menuPressOffset,
            showsSelectTextAction = allowsSelectText && trimmedText.isNotEmpty(),
            onSelectText = {
                selectableTextSheetState = SelectableMessageTextSheetState(
                    role = messageRole,
                    text = trimmedText,
                    usesMarkdownSelection = usesMarkdownSelection,
                )
                menuExpanded = false
            },
            onCopy = {
                copyPlainTextToClipboard(
                    context = context,
                    label = "Conversation message",
                    text = trimmedText,
                )
                menuExpanded = false
            },
            onDismissRequest = { menuExpanded = false },
        )
    }

    selectableTextSheetState?.let { state ->
        SelectableMessageTextSheet(
            state = state,
            onDismiss = { selectableTextSheetState = null },
        )
    }
}

@Composable
private fun ConversationMessageContextMenu(
    expanded: Boolean,
    alignToTrailing: Boolean,
    pressOffset: IntOffset,
    showsSelectTextAction: Boolean,
    onSelectText: () -> Unit,
    onCopy: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val density = LocalDensity.current
    val verticalGapPx = with(density) { 8.dp.roundToPx() }
    val windowMarginPx = with(density) { 12.dp.roundToPx() }
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }
    if (!transitionState.currentState && !transitionState.targetState) {
        return
    }

    Popup(
        popupPositionProvider = remember(verticalGapPx, windowMarginPx, alignToTrailing, pressOffset) {
            ConversationContextMenuPositionProvider(
                verticalGapPx = verticalGapPx,
                windowMarginPx = windowMarginPx,
                alignToTrailing = alignToTrailing,
                pressOffset = pressOffset,
            )
        },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 180),
                    initialOffsetY = { fullHeight -> fullHeight / 10 },
                ) +
                scaleIn(
                    animationSpec = tween(durationMillis = 160),
                    initialScale = 0.97f,
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 120),
                    targetOffsetY = { fullHeight -> fullHeight / 14 },
                ) +
                scaleOut(
                    animationSpec = tween(durationMillis = 110),
                    targetScale = 0.985f,
                ),
        ) {
            Surface(
                color = chrome.panelSurfaceStrong,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, chrome.subtleBorder),
                shadowElevation = 6.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 168.dp, max = 220.dp)
                        .padding(vertical = 2.dp),
                ) {
                    if (showsSelectTextAction) {
                        ComposerDropdownMenuItem(
                            text = { Text("Select Text") },
                            onClick = onSelectText,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.TextFields,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    ComposerDropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = onCopy,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

private class ConversationContextMenuPositionProvider(
    private val verticalGapPx: Int,
    private val windowMarginPx: Int,
    private val alignToTrailing: Boolean,
    private val pressOffset: IntOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val touchX = anchorBounds.left + pressOffset.x
        val touchY = anchorBounds.top + pressOffset.y
        val preferredX = when {
            pressOffset != IntOffset.Zero && alignToTrailing -> touchX - popupContentSize.width
            pressOffset != IntOffset.Zero -> touchX
            alignToTrailing && layoutDirection == LayoutDirection.Ltr ->
                anchorBounds.right - popupContentSize.width
            alignToTrailing && layoutDirection == LayoutDirection.Rtl ->
                anchorBounds.left
            layoutDirection == LayoutDirection.Ltr ->
                anchorBounds.left
            else ->
                anchorBounds.right - popupContentSize.width
        }
        val maxX = (windowSize.width - popupContentSize.width - windowMarginPx).coerceAtLeast(windowMarginPx)
        val resolvedX = preferredX.coerceIn(windowMarginPx, maxX)

        val referenceTop = if (pressOffset != IntOffset.Zero) touchY else anchorBounds.top
        val referenceBottom = if (pressOffset != IntOffset.Zero) touchY else anchorBounds.bottom
        val aboveY = referenceTop - popupContentSize.height - verticalGapPx
        val belowY = referenceBottom + verticalGapPx
        val maxY = (windowSize.height - popupContentSize.height - windowMarginPx).coerceAtLeast(windowMarginPx)
        val resolvedY = when {
            aboveY >= windowMarginPx -> aboveY
            belowY <= maxY -> belowY
            else -> aboveY.coerceIn(windowMarginPx, maxY)
        }

        return IntOffset(resolvedX, resolvedY)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectableMessageTextSheet(
    state: SelectableMessageTextSheetState,
    onDismiss: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ConversationSelectableTextSheetTag)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = chrome.titleText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }

            Surface(
                color = chrome.panelSurface,
                shape = RemodexConversationShapes.card,
                border = BorderStroke(1.dp, chrome.subtleBorder),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    if (state.usesMarkdownSelection) {
                        ConversationMarkdownText(
                            text = state.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.bodyText,
                            enablesSelection = true,
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = state.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = chrome.bodyText,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun copyPlainTextToClipboard(
    context: android.content.Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun rememberLightImpactHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}

@Composable
private fun ComposerMenuCheckmark() {
    val chrome = remodexConversationChrome()
    Text(
        text = "✓",
        style = MaterialTheme.typography.labelMedium,
        color = chrome.secondaryText,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRuntimeControlsSection(
    title: String,
    options: List<RemodexModelOption>,
    selectedModelId: String?,
    onClear: () -> Unit,
    onSelect: (RemodexModelOption) -> Unit,
) {
    val chrome = remodexConversationChrome()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = chrome.secondaryText,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedModelId == null,
                onClick = onClear,
                label = { Text("Auto") },
            )
            options.forEach { option ->
                val isSelected = option.id == selectedModelId || option.model == selectedModelId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = { Text(option.displayName) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> RuntimeControlsSection(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    includeClearChoice: Boolean = false,
    clearChoiceLabel: String = "Auto",
    itemKey: (T) -> Any = { option -> option as Any },
    onClear: (() -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    val chrome = remodexConversationChrome()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = chrome.secondaryText,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (includeClearChoice && onClear != null) {
                FilterChip(
                    selected = selected == null,
                    onClick = onClear,
                    label = { Text(clearChoiceLabel) },
                )
            }
            options.forEach { option ->
                FilterChip(
                    selected = itemKey(option) == selected?.let(itemKey),
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun ConversationBubble(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
    assistantRevertPresentation: RemodexAssistantRevertPresentation?,
    onTapAssistantRevert: (String) -> Unit,
    commandExecutionDetailsByItemId: Map<String, RemodexCommandExecutionDetails>,
    onOpenFileChangeDetails: (FileChangeSheetPresentation) -> Unit,
    onOpenCommandExecutionDetails: (String) -> Unit,
    parentThreadId: String,
    threads: List<RemodexThreadSummary>,
    parentThreadMessages: List<RemodexConversationItem>,
    onOpenSubagentThread: (String) -> Unit,
    onHydrateSubagentThread: (String) -> Unit,
) {
    when (item.speaker) {
        ConversationSpeaker.USER -> UserConversationRow(item = item)
        ConversationSpeaker.ASSISTANT -> AssistantConversationRow(
            item = item,
            accessoryState = accessoryState,
            assistantRevertPresentation = assistantRevertPresentation,
            onTapAssistantRevert = onTapAssistantRevert,
            onOpenFileChangeDetails = onOpenFileChangeDetails,
        )
        ConversationSpeaker.SYSTEM -> SystemConversationRow(
            item = item,
            accessoryState = accessoryState,
            commandExecutionDetails = item.itemId?.let(commandExecutionDetailsByItemId::get),
            onOpenFileChangeDetails = onOpenFileChangeDetails,
            onOpenCommandExecutionDetails = onOpenCommandExecutionDetails,
            parentThreadId = parentThreadId,
            threads = threads,
            parentThreadMessages = parentThreadMessages,
            onOpenSubagentThread = onOpenSubagentThread,
            onHydrateSubagentThread = onHydrateSubagentThread,
        )
    }
}

@Composable
private fun UserConversationRow(item: RemodexConversationItem) {
    val chrome = remodexConversationChrome()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        ConversationMessageActionContainer(
            text = item.text,
            messageRole = ConversationSpeaker.USER,
            usesMarkdownSelection = false,
            allowsSelectText = false,
            modifier = Modifier.fillMaxWidth(0.8f),
            alignMenuToEnd = true,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.attachments.isNotEmpty()) {
                    MessageAttachmentStrip(
                        attachments = item.attachments,
                        alignToEnd = true,
                    )
                }
                if (item.text.isNotBlank()) {
                    Surface(
                        color = chrome.userBubble,
                        shape = RemodexConversationShapes.bubble,
                        border = BorderStroke(1.dp, chrome.userBubbleBorder),
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = highlightMentions(item.text, chrome),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.bodyText,
                        )
                    }
                }
                MessageDeliveryStatus(
                    state = item.deliveryState,
                    createdAtEpochMs = item.createdAtEpochMs,
                )
            }
        }
    }
}

@Composable
private fun AssistantConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
    assistantRevertPresentation: RemodexAssistantRevertPresentation?,
    onTapAssistantRevert: (String) -> Unit,
    onOpenFileChangeDetails: (FileChangeSheetPresentation) -> Unit,
) {
    val chrome = remodexConversationChrome()
    val blockDiffPresentation = remember(
        item.id,
        accessoryState?.blockDiffText,
        accessoryState?.blockDiffEntries,
    ) {
        val blockDiffText = accessoryState?.blockDiffText?.trim().orEmpty()
        val blockDiffEntries = accessoryState?.blockDiffEntries.orEmpty()
        if (blockDiffText.isBlank() || blockDiffEntries.isEmpty()) {
            null
        } else {
            val renderState = FileChangeRenderState(
                summary = FileChangeSummary(blockDiffEntries),
                actionEntries = blockDiffEntries.filter { entry -> entry.action != null },
                bodyText = blockDiffText,
            )
            val diffChunks = FileChangeRenderParser.diffChunks(
                bodyText = blockDiffText,
                entries = blockDiffEntries,
            )
            if (diffChunks.isEmpty()) {
                null
            } else {
                FileChangeSheetPresentation(
                    title = "Changes",
                    messageId = item.id,
                    renderState = renderState,
                    diffChunks = diffChunks,
                )
            }
        }
    }
    val revertPresentation = accessoryState?.blockRevertPresentation ?: assistantRevertPresentation
    ConversationMessageActionContainer(
        text = item.text,
        messageRole = ConversationSpeaker.ASSISTANT,
        usesMarkdownSelection = true,
        allowsSelectText = true,
        modifier = Modifier.fillMaxWidth(0.94f),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
                if (item.text.isNotBlank()) {
                    if (item.isStreaming && shouldUseLightweightStreamingAssistantText(item.text)) {
                        LightweightStreamingAssistantMarkdownText(
                            text = item.text,
                            chrome = chrome,
                        )
                    } else {
                        ConversationMarkdownText(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.bodyText,
                        )
                    }
                }
            item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryText,
                )
            }
            if (accessoryState?.showsRunningIndicator == true) {
                TerminalRunningIndicator()
            }
            if (revertPresentation != null) {
                AssistantRevertAction(
                    presentation = revertPresentation,
                    onTap = { onTapAssistantRevert(item.id) },
                )
            }
            if (blockDiffPresentation != null) {
                AssistantBlockDiffAction(
                    entries = blockDiffPresentation.renderState.summary?.entries.orEmpty(),
                    onTap = { onOpenFileChangeDetails(blockDiffPresentation) },
                )
            }
            accessoryState?.copyText?.let { copyText ->
                ConversationCopyBlockButton(text = copyText)
            }
        }
    }
}

@Composable
private fun LightweightStreamingAssistantMarkdownText(
    text: String,
    chrome: RemodexConversationChrome,
) {
    val monoFamily = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Monospace
    val blocks = remember(text) { parseConversationTextBlocks(text) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block.kind) {
                ConversationTextBlockKind.PROSE -> Text(
                    text = buildConversationAnnotatedString(
                        text = block.text,
                        chrome = chrome,
                        monoFamily = monoFamily,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.bodyText,
                )

                ConversationTextBlockKind.CODE -> Surface(
                    color = chrome.mutedSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, chrome.subtleBorder),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = block.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = monoFamily),
                        color = chrome.bodyText,
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
    commandExecutionDetails: RemodexCommandExecutionDetails?,
    onOpenFileChangeDetails: (FileChangeSheetPresentation) -> Unit,
    onOpenCommandExecutionDetails: (String) -> Unit,
    parentThreadId: String,
    threads: List<RemodexThreadSummary>,
    parentThreadMessages: List<RemodexConversationItem>,
    onOpenSubagentThread: (String) -> Unit,
    onHydrateSubagentThread: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (item.kind) {
            ConversationItemKind.REASONING -> ThinkingConversationRow(
                item = item,
                accessoryState = accessoryState,
            )
            ConversationItemKind.TOOL_ACTIVITY -> ToolActivityConversationRow(
                item = item,
                accessoryState = accessoryState,
            )
            ConversationItemKind.FILE_CHANGE -> FileChangeConversationRow(
                item = item,
                accessoryState = accessoryState,
                onOpenDetails = onOpenFileChangeDetails,
            )
            ConversationItemKind.COMMAND_EXECUTION -> CommandExecutionConversationRow(
                item = item,
                accessoryState = accessoryState,
                details = commandExecutionDetails,
                onOpenDetails = { onOpenCommandExecutionDetails(item.id) },
            )
            ConversationItemKind.SUBAGENT_ACTION -> SubagentActionRow(
                item = item,
                accessoryState = accessoryState,
                parentThreadId = parentThreadId,
                threads = threads,
                parentThreadMessages = parentThreadMessages,
                onOpenSubagentThread = onOpenSubagentThread,
                onHydrateSubagentThread = onHydrateSubagentThread,
            )
            ConversationItemKind.USER_INPUT_PROMPT -> StructuredUserInputRow(item.structuredUserInputRequest)
            ConversationItemKind.PLAN -> Unit
            ConversationItemKind.CHAT -> DefaultSystemRow(
                item = item,
                accessoryState = accessoryState,
            )
        }
        accessoryState?.copyText?.let { copyText ->
            ConversationCopyBlockButton(text = copyText)
        }
    }
}

@Composable
private fun ToolActivityConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
) {
    val chrome = remodexConversationChrome()
    val joined = remember(item.text) {
        item.text
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(separator = "\n")
    }
    ConversationMessageActionContainer(
        text = item.text,
        messageRole = ConversationSpeaker.SYSTEM,
        usesMarkdownSelection = false,
        allowsSelectText = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (joined.isNotEmpty()) {
                Text(
                    text = joined,
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryText,
                )
            }
            if (item.isStreaming && accessoryState?.showsRunningIndicator != true) {
                MiniTypingIndicator()
            }
            if (accessoryState?.showsRunningIndicator == true) {
                TerminalRunningIndicator()
            }
        }
    }
}

@Composable
private fun ThinkingConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
) {
    val chrome = remodexConversationChrome()
    val thinkingText = remember(item.text) {
        ThinkingDisclosureParser.normalizedThinkingContent(item.text)
    }
    val activityPreview = remember(thinkingText) {
        if (thinkingText.isEmpty()) {
            null
        } else {
            ThinkingDisclosureParser.compactActivityPreview(thinkingText)
        }
    }
    val showDetailedThinking = !item.isStreaming && activityPreview == null && thinkingText.isNotEmpty()
    val thinkingContent = remember(item.id, thinkingText, showDetailedThinking) {
        if (showDetailedThinking) {
            ThinkingDisclosureParser.parse(thinkingText)
        } else {
            ThinkingDisclosureContent(
                sections = emptyList(),
                fallbackText = "",
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            activityPreview != null -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThinkingTitle(isStreaming = item.isStreaming)
                    ThinkingActivityPreviewText(
                        preview = activityPreview,
                        chrome = chrome,
                    )
                }
            }

            thinkingText.isEmpty() -> {
                ThinkingTitle(isStreaming = item.isStreaming)
            }

            item.isStreaming -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThinkingTitle(isStreaming = true)
                    StreamingThinkingPreviewText(
                        text = thinkingText,
                        chrome = chrome,
                    )
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThinkingTitle(isStreaming = item.isStreaming)
                    ThinkingDisclosureContentView(
                        messageId = item.id,
                        content = thinkingContent,
                        chrome = chrome,
                    )
                }
            }
        }
        item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
            )
        }
        if (accessoryState?.showsRunningIndicator == true) {
            TerminalRunningIndicator()
        }
    }
}

@Composable
private fun StreamingThinkingPreviewText(
    text: String,
    chrome: RemodexConversationChrome,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = chrome.secondaryText.copy(alpha = 0.86f),
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ThinkingTitle(
    isStreaming: Boolean,
) {
    val chrome = remodexConversationChrome()
    Text(
        text = "Thinking...",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = chrome.secondaryText.copy(alpha = if (isStreaming) 0.92f else 1f),
        modifier = Modifier.remodexLabelShimmer(
            enabled = isStreaming,
            durationMillis = 1600,
            highlightAlpha = 0.45f,
            bandCoverage = 0.6f,
            startPhase = -0.6f,
            endPhase = 1.4f,
        ),
    )
}

@Composable
private fun ThinkingActivityPreviewText(
    preview: String,
    chrome: RemodexConversationChrome,
) {
    val trimmed = preview.trim()
    if (trimmed.isEmpty()) {
        return
    }

    val splitIndex = trimmed.indexOf(' ').takeIf { it >= 0 }
    val leading = if (splitIndex == null) trimmed else trimmed.substring(0, splitIndex)
    val remainder = if (splitIndex == null) "" else trimmed.substring(splitIndex)
    val capitalizedLeading = leading.replaceFirstChar(Char::titlecase)

    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = chrome.secondaryText,
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                append(capitalizedLeading)
            }
            withStyle(
                SpanStyle(color = chrome.tertiaryText),
            ) {
                append(remainder)
            }
        },
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ThinkingDisclosureContentView(
    messageId: String,
    content: ThinkingDisclosureContent,
    chrome: RemodexConversationChrome,
) {
    var expandedSectionIds by rememberSaveable(messageId) { mutableStateOf(emptyList<String>()) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (content.showsDisclosure) {
            content.sections.forEach { section ->
                val isExpanded = section.id in expandedSectionIds
                val hasDetail = section.detail.isNotBlank()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = hasDetail) {
                                expandedSectionIds = if (isExpanded) {
                                    expandedSectionIds - section.id
                                } else {
                                    expandedSectionIds + section.id
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = if (hasDetail) chrome.secondaryText else chrome.tertiaryText,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer { rotationZ = if (isExpanded) 0f else -90f },
                        )
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = chrome.secondaryText,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (isExpanded && hasDetail) {
                        ConversationMarkdownText(
                            text = section.detail,
                            modifier = Modifier.padding(start = 22.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = chrome.secondaryText,
                        )
                    }
                }
            }
        } else if (content.fallbackText.isNotBlank()) {
            ConversationMarkdownText(
                text = content.fallbackText,
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
            )
        }
    }
}

@Composable
private fun FileChangeConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
    onOpenDetails: (FileChangeSheetPresentation) -> Unit,
) {
    val chrome = remodexConversationChrome()
    val renderState = remember(item.text) {
        FileChangeRenderParser.renderState(item.text)
    }
    val summaryEntries = remember(renderState) {
        if (renderState.actionEntries.isNotEmpty()) {
            renderState.actionEntries
        } else {
            renderState.summary?.entries.orEmpty()
        }
    }
    val groupedEntries = remember(summaryEntries) {
        FileChangeRenderParser.grouped(summaryEntries)
    }
    val diffChunks = remember(item.text, renderState.summary?.entries) {
        FileChangeRenderParser.diffChunks(
            bodyText = item.text,
            entries = renderState.summary?.entries.orEmpty(),
        )
    }
    val detailsPresentation = remember(item.id, renderState, diffChunks) {
        if (diffChunks.isEmpty()) {
            null
        } else {
            FileChangeSheetPresentation(
                title = "Changes",
                messageId = item.id,
                renderState = renderState,
                diffChunks = diffChunks,
            )
        }
    }
    ConversationMessageActionContainer(
        text = item.text,
        messageRole = ConversationSpeaker.SYSTEM,
        usesMarkdownSelection = false,
        allowsSelectText = true,
        onClick = detailsPresentation?.let { presentation -> { onOpenDetails(presentation) } },
    ) {
        Surface(
            color = chrome.panelSurface,
            shape = RemodexConversationShapes.card,
            border = BorderStroke(1.dp, chrome.subtleBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (groupedEntries.isNotEmpty()) {
                    groupedEntries.forEach { group ->
                        FileChangeInlineGroup(
                            group = group,
                            chrome = chrome,
                        )
                    }
                } else if (item.text.isNotBlank()) {
                    ConversationMarkdownText(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = chrome.bodyText,
                    )
                }
                item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = chrome.secondaryText,
                    )
                }
                if (accessoryState?.showsRunningIndicator == true) {
                    TerminalRunningIndicator()
                } else if (item.isStreaming) {
                    StreamingIndicator(label = "Running")
                }
            }
        }
    }
}

@Composable
private fun FileChangeInlineGroup(
    group: FileChangeGroup,
    chrome: RemodexConversationChrome,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = group.key,
            style = MaterialTheme.typography.labelSmall,
            color = chrome.secondaryText.copy(alpha = 0.72f),
        )
        group.entries.forEach { entry ->
            FileChangeInlineActionRow(
                entry = entry,
                showActionLabel = false,
                chrome = chrome,
            )
        }
    }
}

@Composable
private fun FileChangeInlineActionRow(
    entry: FileChangeSummaryEntry,
    showActionLabel: Boolean,
    chrome: RemodexConversationChrome,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (showActionLabel) {
            Text(
                text = entry.action?.label ?: FileChangeAction.EDITED.label,
                style = MaterialTheme.typography.labelSmall,
                color = chrome.secondaryText.copy(alpha = 0.72f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.compactPath,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = chrome.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.fullDirectoryPath?.let { directoryPath ->
                    Text(
                        text = directoryPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = chrome.tertiaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FileChangeDiffCounts(
                additions = entry.additions,
                deletions = entry.deletions,
            )
        }
    }
}

@Composable
private fun FileChangeDiffCounts(
    additions: Int,
    deletions: Int,
) {
    if (additions <= 0 && deletions <= 0) {
        return
    }

    val monoFamily = MaterialTheme.typography.labelLarge.fontFamily ?: FontFamily.Monospace
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (additions > 0) {
            Text(
                text = "+$additions",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFamily),
                color = FileChangeAddedColor,
            )
        }
        if (deletions > 0) {
            Text(
                text = "-$deletions",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFamily),
                color = FileChangeDeletedColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileChangeDetailSheet(
    title: String = "Changes",
    messageId: String,
    renderState: FileChangeRenderState,
    diffChunks: List<PerFileDiffChunk>,
    onDismiss: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    var expandedChunkIds by rememberSaveable(messageId) { mutableStateOf(emptyList<String>()) }
    val allExpanded = remember(diffChunks, expandedChunkIds) {
        diffChunks.isNotEmpty() && diffChunks.all { chunk -> chunk.id in expandedChunkIds }
    }

    LaunchedEffect(messageId, diffChunks) {
        expandedChunkIds = diffChunks.map(PerFileDiffChunk::id)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = chrome.accent,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${diffChunks.size} file${if (diffChunks.size == 1) "" else "s"} changed",
                        style = MaterialTheme.typography.labelMedium,
                        color = chrome.secondaryText,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (diffChunks.size > 1) {
                        TextButton(
                            onClick = {
                                expandedChunkIds = if (allExpanded) {
                                    emptyList()
                                } else {
                                    diffChunks.map(PerFileDiffChunk::id)
                                }
                            },
                        ) {
                            Text(if (allExpanded) "Collapse All" else "Expand All")
                        }
                    }
                }
            }

            diffChunks.forEach { chunk ->
                FileChangeDetailFileCard(
                    chunk = chunk,
                    isExpanded = chunk.id in expandedChunkIds,
                    onToggleExpanded = {
                        expandedChunkIds = if (chunk.id in expandedChunkIds) {
                            expandedChunkIds - chunk.id
                        } else {
                            expandedChunkIds + chunk.id
                        }
                    },
                )
            }

            if (diffChunks.isEmpty() && renderState.bodyText.isNotBlank()) {
                Surface(
                    color = chrome.mutedSurface,
                    shape = RemodexConversationShapes.card,
                    border = BorderStroke(1.dp, chrome.subtleBorder),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    ConversationMarkdownText(
                        text = renderState.bodyText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = chrome.bodyText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FileChangeDetailFileCard(
    chunk: PerFileDiffChunk,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val actionColor = fileChangeActionColor(
        action = chunk.action,
        chrome = chrome,
    )
    val monoFamily = MaterialTheme.typography.labelLarge.fontFamily ?: FontFamily.Monospace

    Surface(
        color = chrome.panelSurface,
        shape = RemodexConversationShapes.card,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = chrome.secondaryText,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = if (isExpanded) 0f else -90f },
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = actionColor, shape = CircleShape),
                    )
                    Text(
                        text = chunk.compactPath,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = monoFamily),
                        fontWeight = FontWeight.Medium,
                        color = chrome.bodyText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = chunk.action.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFamily),
                        color = actionColor,
                        modifier = Modifier
                            .background(
                                color = actionColor.copy(alpha = 0.12f),
                                shape = RemodexConversationShapes.pill,
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    FileChangeDiffCounts(
                        additions = chunk.additions,
                        deletions = chunk.deletions,
                    )
                }

                chunk.fullDirectoryPath?.let { directoryPath ->
                    Text(
                        text = directoryPath,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFamily),
                        color = chrome.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 22.dp),
                    )
                }
            }

            if (isExpanded && chunk.diffCode.isNotBlank()) {
                HorizontalDivider(color = chrome.subtleBorder)
                Surface(
                    color = chrome.mutedSurface,
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    SelectionContainer {
                        Text(
                            text = chunk.diffCode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = monoFamily),
                            color = chrome.bodyText,
                        )
                    }
                }
            }
        }
    }
}

private fun fileChangeActionColor(
    action: FileChangeAction,
    chrome: RemodexConversationChrome,
): Color {
    return when (action) {
        FileChangeAction.ADDED -> FileChangeAddedColor
        FileChangeAction.DELETED -> FileChangeDeletedColor
        FileChangeAction.RENAMED -> chrome.accent
        FileChangeAction.EDITED -> chrome.warning
    }
}

@Composable
private fun CommandExecutionConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
    details: RemodexCommandExecutionDetails?,
    onOpenDetails: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val resolvedRows = remember(item.text, item.isStreaming, details) {
        resolvedCommandExecutionStatusPresentations(
            item = item,
            details = details,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (resolvedRows.isNotEmpty()) {
            resolvedRows.forEachIndexed { index, status ->
                CommandExecutionStatusCard(
                    status = status,
                    onClick = if (index == 0) {
                        onOpenDetails
                    } else {
                        null
                    },
                )
            }
        } else {
            item.text.takeIf(String::isNotBlank)?.let { text ->
                ConversationMarkdownText(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryText,
                )
            }
        }
        item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
            )
        }
        if (accessoryState?.showsRunningIndicator == true) {
            TerminalRunningIndicator()
        }
    }
}

@Composable
private fun SubagentActionRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
    parentThreadId: String,
    threads: List<RemodexThreadSummary>,
    parentThreadMessages: List<RemodexConversationItem>,
    onOpenSubagentThread: (String) -> Unit,
    onHydrateSubagentThread: (String) -> Unit,
) {
    val action = item.subagentAction
    if (action == null) {
        DefaultSystemRow(item = item, accessoryState = accessoryState)
        return
    }
    val chrome = remodexConversationChrome()
    var expanded by rememberSaveable(item.id) { mutableStateOf(true) }
    var selectedAgentDetails by remember(item.id) { mutableStateOf<RemodexSubagentThreadPresentation?>(null) }
    val resolvedRows = remember(action, threads, parentThreadId, parentThreadMessages) {
        action.agentRows.map { presentation ->
            resolveSubagentPresentation(
                presentation = presentation,
                parentThreadId = parentThreadId,
                threads = threads,
                parentThreadMessages = parentThreadMessages,
            )
        }
    }
    LaunchedEffect(resolvedRows.map(RemodexSubagentThreadPresentation::threadId)) {
        resolvedRows
            .map(RemodexSubagentThreadPresentation::threadId)
            .filter(String::isNotBlank)
            .distinct()
            .forEach(onHydrateSubagentThread)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = action.summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = chrome.tertiaryText,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = if (expanded) 0f else -90f },
            )
        }
        if (expanded && resolvedRows.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(subagentAgentRowSpacing(action.normalizedTool)),
                modifier = Modifier.padding(top = subagentAgentRowsTopPadding(action.normalizedTool)),
            ) {
                resolvedRows.forEach { row ->
                    val observedThread = remember(row.threadId, threads) {
                        observedSubagentThread(
                            presentation = row,
                            threads = threads,
                        )
                    }
                    val status = remember(action, row, observedThread) {
                        resolvedSubagentStatusPresentation(
                            action = action,
                            presentation = row,
                            observedThread = observedThread,
                        )
                    }
                    val statusText = remember(action.normalizedTool, status.label) {
                        readableSubagentStatus(
                            normalizedTool = action.normalizedTool,
                            label = status.label,
                        )
                    }
                    val detailModelLabel = remember(row, observedThread) {
                        resolvedSubagentModelLabel(
                            presentation = row,
                            observedThread = observedThread,
                        )
                    }
                    val detailText = remember(action.prompt, row.prompt, row.fallbackMessage) {
                        detailSubagentText(
                            action = action,
                            presentation = row,
                        )
                    }
                    SubagentAgentRowView(
                        title = resolvedSubagentTitle(row),
                        statusText = statusText,
                        modelLabel = if (action.normalizedTool == "spawnagent") null else detailModelLabel,
                        showsDetails = detailText != null || detailModelLabel != null,
                        onShowDetails = {
                            selectedAgentDetails = row
                        },
                        onOpen = {
                            selectedAgentDetails = null
                            onOpenSubagentThread(row.threadId)
                        },
                    )
                }
            }
        }
        if (item.isStreaming) {
            MiniTypingIndicator()
        }
        if (accessoryState?.showsRunningIndicator == true) {
            TerminalRunningIndicator()
        }
    }
    selectedAgentDetails?.let { selected ->
        val observedThread = remember(selected.threadId, threads) {
            observedSubagentThread(
                presentation = selected,
                threads = threads,
            )
        }
        val status = remember(action, selected, observedThread) {
            resolvedSubagentStatusPresentation(
                action = action,
                presentation = selected,
                observedThread = observedThread,
            )
        }
        val modelLabel = remember(selected, observedThread) {
            resolvedSubagentModelLabel(
                presentation = selected,
                observedThread = observedThread,
                prefixRequested = false,
            )
        }
        val detailText = remember(action.prompt, selected.prompt, selected.fallbackMessage) {
            detailSubagentText(
                action = action,
                presentation = selected,
            )
        }
        SubagentAgentDetailSheet(
            title = resolvedSubagentTitle(selected),
            accentColor = subagentNicknameColorForTitle(resolvedSubagentTitle(selected)),
            statusText = readableSubagentStatus(
                normalizedTool = action.normalizedTool,
                label = status.label,
            ),
            modelTitle = if (selected.modelIsRequestedHint) "Requested model" else "Model",
            modelLabel = modelLabel,
            instructionText = trimmedSubagentValue(selected.prompt) ?: trimmedSubagentValue(action.prompt),
            latestUpdateText = trimmedSubagentValue(selected.fallbackMessage),
            onDismiss = { selectedAgentDetails = null },
            onOpen = {
                selectedAgentDetails = null
                onOpenSubagentThread(selected.threadId)
            },
        )
    }
}

internal enum class CommandExecutionStatusAccent {
    RUNNING,
    COMPLETED,
    FAILED,
}

internal data class CommandExecutionStatusPresentation(
    val command: String,
    val statusLabel: String,
    val accent: CommandExecutionStatusAccent,
)

internal data class HumanizedCommandInfo(
    val verb: String,
    val target: String,
)

@Composable
private fun CommandExecutionStatusCard(
    status: CommandExecutionStatusPresentation,
    onClick: (() -> Unit)?,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    CommandExecutionCardBody(
        status = status,
        modifier = clickableModifier.fillMaxWidth(),
        showsChevron = onClick != null,
    )
}

@Composable
private fun CommandExecutionCardBody(
    status: CommandExecutionStatusPresentation,
    modifier: Modifier = Modifier,
    showsChevron: Boolean,
) {
    val chrome = remodexConversationChrome()
    val humanized = remember(status.command, status.accent) {
        humanizeCommand(
            raw = status.command,
            isRunning = status.accent == CommandExecutionStatusAccent.RUNNING,
        )
    }
    val accentColor = when (status.accent) {
        CommandExecutionStatusAccent.RUNNING -> chrome.secondaryText.copy(alpha = 0.5f)
        CommandExecutionStatusAccent.COMPLETED -> chrome.secondaryText.copy(alpha = 0.5f)
        CommandExecutionStatusAccent.FAILED -> chrome.destructive
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = chrome.secondaryText,
                        fontWeight = FontWeight.Medium,
                    ),
                ) {
                    append(humanized.verb)
                }
                withStyle(SpanStyle(color = chrome.tertiaryText)) {
                    append(" ")
                    append(humanized.target)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = status.statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
        )
        if (showsChevron) {
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = chrome.tertiaryText,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(10.dp)
                    .graphicsLayer { rotationZ = -90f },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandExecutionDetailSheet(
    status: CommandExecutionStatusPresentation,
    details: RemodexCommandExecutionDetails?,
    onDismiss: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val monoFamily = MaterialTheme.typography.labelLarge.fontFamily ?: FontFamily.Monospace
    var outputExpanded by rememberSaveable(status.command) { mutableStateOf(false) }
    val statusColor = when (status.accent) {
        CommandExecutionStatusAccent.RUNNING -> chrome.accent
        CommandExecutionStatusAccent.COMPLETED -> chrome.secondaryText
        CommandExecutionStatusAccent.FAILED -> chrome.destructive
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Command",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = monoFamily),
                    color = chrome.accent,
                )
                Surface(
                    color = chrome.mutedSurface,
                    shape = RemodexConversationShapes.card,
                    border = BorderStroke(1.dp, chrome.subtleBorder),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    SelectionContainer {
                        Text(
                            text = details?.fullCommand ?: status.command,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = monoFamily),
                            color = chrome.bodyText,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details?.cwd?.takeIf(String::isNotBlank)?.let { cwd ->
                    CommandExecutionMetadataRow(
                        label = "Directory",
                        value = cwd,
                    )
                }
                details?.exitCode?.let { exitCode ->
                    CommandExecutionMetadataRow(
                        label = "Exit code",
                        value = exitCode.toString(),
                        valueColor = if (exitCode == 0) chrome.accent else chrome.destructive,
                    )
                }
                details?.durationMs?.let { durationMs ->
                    CommandExecutionMetadataRow(
                        label = "Duration",
                        value = formatCommandExecutionDuration(durationMs),
                    )
                }
                CommandExecutionMetadataRow(
                    label = "Status",
                    value = status.statusLabel,
                    valueColor = statusColor,
                )
            }

            details?.outputTail?.takeIf(String::isNotBlank)?.let { outputTail ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { outputExpanded = !outputExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = chrome.secondaryText,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer { rotationZ = if (outputExpanded) 0f else -90f },
                        )
                        Text(
                            text = "Output (last ${RemodexCommandExecutionDetails.MaxOutputLines} lines)",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = monoFamily),
                            color = chrome.secondaryText,
                        )
                    }
                    if (outputExpanded) {
                        Surface(
                            color = chrome.mutedSurface,
                            shape = RemodexConversationShapes.card,
                            border = BorderStroke(1.dp, chrome.subtleBorder),
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                        ) {
                            SelectionContainer {
                                Text(
                                    text = outputTail,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = monoFamily),
                                    color = chrome.bodyText,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CommandExecutionMetadataRow(
    label: String,
    value: String,
    valueColor: Color = remodexConversationChrome().bodyText,
) {
    val chrome = remodexConversationChrome()
    val monoFamily = MaterialTheme.typography.labelLarge.fontFamily ?: FontFamily.Monospace
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFamily),
            color = chrome.secondaryText,
        )
        Spacer(modifier = Modifier.weight(1f))
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFamily),
                color = valueColor,
            )
        }
    }
}

private fun formatCommandExecutionDuration(durationMs: Int): String {
    if (durationMs < 1_000) {
        return "${durationMs}ms"
    }
    val seconds = durationMs / 1_000.0
    if (seconds < 60) {
        return String.format(Locale.US, "%.1fs", seconds)
    }
    val wholeSeconds = seconds.toInt()
    val minutes = wholeSeconds / 60
    val remainingSeconds = wholeSeconds % 60
    return "${minutes}m ${remainingSeconds}s"
}

@Composable
private fun MiniTypingIndicator() {
    val chrome = remodexConversationChrome()
    val transition = rememberInfiniteTransition(label = "mini_typing")
    val highlightAlpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mini_typing_alpha",
    )
    Surface(
        color = chrome.mutedSurface,
        shape = RemodexConversationShapes.pill,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .width(22.dp)
                .height(4.dp)
                .background(
                    color = chrome.secondaryText.copy(alpha = highlightAlpha),
                    shape = RemodexConversationShapes.pill,
                ),
        )
    }
}

@Composable
private fun TerminalRunningIndicator() {
    val chrome = remodexConversationChrome()
    val transition = rememberInfiniteTransition(label = "terminal_running")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "terminal_running_cursor",
    )

    Row(
        modifier = Modifier
            .testTag(ConversationRunningIndicatorTag)
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = chrome.mutedSurface,
            border = BorderStroke(1.dp, chrome.subtleBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = ">",
                    style = MaterialTheme.typography.labelSmall,
                    color = chrome.secondaryText,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .width(4.dp)
                        .height(1.dp)
                        .background(
                            color = chrome.secondaryText.copy(alpha = cursorAlpha),
                            shape = RemodexConversationShapes.pill,
                        ),
                )
            }
        }
        Text(
            text = "Remodex is thinking...",
            style = MaterialTheme.typography.labelSmall,
            color = chrome.secondaryText,
            modifier = Modifier.remodexLabelShimmer(
                enabled = true,
                durationMillis = 4000,
                highlightAlpha = 0.35f,
                bandCoverage = 0.5f,
                startPhase = -0.5f,
                endPhase = 5f,
            ),
        )
    }
}

private fun buildConversationBlockAccessories(
    items: List<RemodexConversationItem>,
    isThreadRunning: Boolean,
    activeTurnId: String?,
    latestTurnTerminalState: RemodexTurnTerminalState?,
    stoppedTurnIds: Set<String>,
    assistantRevertStatesByMessageId: Map<String, RemodexAssistantRevertPresentation>,
): Map<String, ConversationBlockAccessoryState> {
    if (items.isEmpty()) {
        return emptyMap()
    }

    val latestBlockEnd = items.indexOfLast { it.speaker != ConversationSpeaker.USER }
    if (latestBlockEnd == -1) {
        return emptyMap()
    }

    val accessories = mutableMapOf<String, ConversationBlockAccessoryState>()
    var cursor = items.lastIndex
    while (cursor >= 0) {
        if (items[cursor].speaker == ConversationSpeaker.USER) {
            cursor -= 1
            continue
        }

        val blockEnd = cursor
        var blockStart = cursor
        while (blockStart > 0 && items[blockStart - 1].speaker != ConversationSpeaker.USER) {
            blockStart -= 1
        }

        val blockTurnId = items.subList(blockStart, blockEnd + 1)
            .asReversed()
            .firstNotNullOfOrNull { item -> item.turnId?.trim()?.takeIf(String::isNotEmpty) }
        val blockText = items.subList(blockStart, blockEnd + 1)
            .map(RemodexConversationItem::text)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(separator = "\n\n")
            .takeIf(String::isNotBlank)
        val fileChangeMessages = items.subList(blockStart, blockEnd + 1).filter { item ->
            item.speaker == ConversationSpeaker.SYSTEM &&
                item.kind == ConversationItemKind.FILE_CHANGE &&
                !item.isStreaming
        }
        val blockDiffEntries = fileChangeMessages
            .flatMap { message ->
                FileChangeRenderParser.renderState(message.text).summary?.entries.orEmpty()
            }
            .takeIf(List<FileChangeSummaryEntry>::isNotEmpty)
        val blockDiffText = fileChangeMessages
            .map(RemodexConversationItem::text)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(separator = "\n\n")
            .takeIf(String::isNotBlank)
        val blockRevertPresentation = items.subList(blockStart, blockEnd + 1)
            .asReversed()
            .firstNotNullOfOrNull { item -> assistantRevertStatesByMessageId[item.id] }

        val effectiveThreadRunning = isThreadRunning && latestTurnTerminalState == null
        val showsRunningIndicator = when {
            !effectiveThreadRunning -> false
            blockTurnId != null && blockTurnId in stoppedTurnIds -> false
            activeTurnId != null && blockTurnId != null -> activeTurnId == blockTurnId
            else -> blockEnd == latestBlockEnd && blockEnd == items.lastIndex
        }
        val showsCopyButton = when {
            blockText == null -> false
            blockTurnId != null && blockTurnId in stoppedTurnIds -> false
            latestTurnTerminalState == RemodexTurnTerminalState.STOPPED && blockEnd == latestBlockEnd -> false
            !effectiveThreadRunning -> true
            activeTurnId != null && blockTurnId != null -> activeTurnId != blockTurnId
            else -> blockEnd != latestBlockEnd
        }

        if (showsRunningIndicator || showsCopyButton) {
            accessories[items[blockEnd].id] = ConversationBlockAccessoryState(
                showsRunningIndicator = showsRunningIndicator,
                copyText = if (showsCopyButton) blockText else null,
                blockDiffText = blockDiffText,
                blockDiffEntries = blockDiffEntries,
                blockRevertPresentation = blockRevertPresentation,
            )
        } else if (blockDiffEntries != null || blockRevertPresentation != null) {
            accessories[items[blockEnd].id] = ConversationBlockAccessoryState(
                showsRunningIndicator = false,
                copyText = null,
                blockDiffText = blockDiffText,
                blockDiffEntries = blockDiffEntries,
                blockRevertPresentation = blockRevertPresentation,
            )
        }

        cursor = blockStart - 1
    }

    return accessories
}

internal fun parseCommandExecutionStatus(text: String): CommandExecutionStatusPresentation? {
    val parts = text.trim().split(Regex("\\s+"), limit = 2)
    val first = parts.firstOrNull()?.lowercase() ?: return null
    val commandLabel = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "command" }

    return when (first) {
        "running" -> CommandExecutionStatusPresentation(
            command = commandLabel,
            statusLabel = "running",
            accent = CommandExecutionStatusAccent.RUNNING,
        )
        "completed" -> CommandExecutionStatusPresentation(
            command = commandLabel,
            statusLabel = "completed",
            accent = CommandExecutionStatusAccent.COMPLETED,
        )
        "failed", "stopped" -> CommandExecutionStatusPresentation(
            command = commandLabel,
            statusLabel = first,
            accent = CommandExecutionStatusAccent.FAILED,
        )
        else -> null
    }
}

internal fun resolvedCommandExecutionStatusPresentations(
    item: RemodexConversationItem,
    details: RemodexCommandExecutionDetails?,
): List<CommandExecutionStatusPresentation> {
    val parsedRows = item.text
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull(::parseCommandExecutionStatus)
        .toList()
    if (parsedRows.isNotEmpty()) {
        return parsedRows.mapIndexed { index, status ->
            resolveCommandExecutionStatusPresentation(
                status = status,
                item = item,
                details = if (index == 0) details else null,
            )
        }
    }
    val fallbackStatus = fallbackCommandExecutionStatusPresentation(
        item = item,
        details = details,
    ) ?: return emptyList()
    return listOf(fallbackStatus)
}

internal fun resolveCommandExecutionStatusPresentation(
    status: CommandExecutionStatusPresentation,
    item: RemodexConversationItem,
    details: RemodexCommandExecutionDetails?,
): CommandExecutionStatusPresentation {
    val resolvedAccent = when {
        details?.exitCode?.let { it != 0 } == true -> CommandExecutionStatusAccent.FAILED
        details?.exitCode == 0 -> CommandExecutionStatusAccent.COMPLETED
        !item.isStreaming && status.accent == CommandExecutionStatusAccent.RUNNING -> CommandExecutionStatusAccent.COMPLETED
        !item.isStreaming && details != null -> CommandExecutionStatusAccent.COMPLETED
        else -> status.accent
    }
    val resolvedStatusLabel = when {
        resolvedAccent == CommandExecutionStatusAccent.FAILED && status.statusLabel == "stopped" -> "stopped"
        resolvedAccent == CommandExecutionStatusAccent.FAILED -> "failed"
        resolvedAccent == CommandExecutionStatusAccent.COMPLETED -> "completed"
        else -> "running"
    }
    return status.copy(
        command = details?.fullCommand?.takeIf(String::isNotBlank) ?: status.command,
        statusLabel = resolvedStatusLabel,
        accent = resolvedAccent,
    )
}

private fun fallbackCommandExecutionStatusPresentation(
    item: RemodexConversationItem,
    details: RemodexCommandExecutionDetails?,
): CommandExecutionStatusPresentation? {
    val fullCommand = details?.fullCommand?.trim().orEmpty()
    if (fullCommand.isEmpty()) {
        return null
    }
    val accent = when {
        details?.exitCode?.let { it != 0 } == true -> CommandExecutionStatusAccent.FAILED
        !item.isStreaming || details?.exitCode == 0 || details?.durationMs != null -> CommandExecutionStatusAccent.COMPLETED
        else -> CommandExecutionStatusAccent.RUNNING
    }
    return CommandExecutionStatusPresentation(
        command = fullCommand,
        statusLabel = when (accent) {
            CommandExecutionStatusAccent.RUNNING -> "running"
            CommandExecutionStatusAccent.COMPLETED -> "completed"
            CommandExecutionStatusAccent.FAILED -> "failed"
        },
        accent = accent,
    )
}

internal fun humanizeCommand(
    raw: String,
    isRunning: Boolean,
): HumanizedCommandInfo {
    val command = unwrapShellCommand(raw)
    val firstSpace = command.indexOf(' ')
    val rawTool = if (firstSpace == -1) command else command.substring(0, firstSpace)
    val tool = rawTool.substringAfterLast('/').lowercase()
    val args = if (firstSpace == -1) "" else command.substring(firstSpace + 1)

    return when (tool) {
        "cat", "nl", "head", "tail", "sed", "less", "more" -> HumanizedCommandInfo(
            verb = if (isRunning) "Reading" else "Read",
            target = lastPathComponent(args, fallback = "file"),
        )
        "rg", "grep", "ag", "ack" -> HumanizedCommandInfo(
            verb = if (isRunning) "Searching" else "Searched",
            target = searchSummary(args),
        )
        "ls" -> HumanizedCommandInfo(
            verb = if (isRunning) "Listing" else "Listed",
            target = lastPathComponent(args, fallback = "directory"),
        )
        "find", "fd" -> HumanizedCommandInfo(
            verb = if (isRunning) "Finding" else "Found",
            target = lastPathComponent(args, fallback = "files"),
        )
        "mkdir" -> HumanizedCommandInfo(
            verb = if (isRunning) "Creating" else "Created",
            target = lastPathComponent(args, fallback = "directory"),
        )
        "rm" -> HumanizedCommandInfo(
            verb = if (isRunning) "Removing" else "Removed",
            target = lastPathComponent(args, fallback = "file"),
        )
        "cp", "mv" -> HumanizedCommandInfo(
            verb = when {
                tool == "cp" && isRunning -> "Copying"
                tool == "cp" -> "Copied"
                isRunning -> "Moving"
                else -> "Moved"
            },
            target = lastPathComponent(args, fallback = "file"),
        )
        "git" -> gitHumanizedCommand(args, isRunning)
        else -> HumanizedCommandInfo(
            verb = if (isRunning) "Running" else "Ran",
            target = command.ifBlank { "command" },
        )
    }
}

private fun Modifier.remodexLabelShimmer(
    enabled: Boolean,
    durationMillis: Int,
    highlightAlpha: Float,
    bandCoverage: Float,
    startPhase: Float,
    endPhase: Float,
): Modifier = composed {
    if (!enabled) {
        return@composed this
    }
    val transition = rememberInfiniteTransition(label = "remodex_label_shimmer")
    val phase by transition.animateFloat(
        initialValue = startPhase,
        targetValue = endPhase,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "remodex_label_shimmer_phase",
    )
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            if (size.width <= 0f || size.height <= 0f) {
                return@drawWithContent
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = highlightAlpha),
                        Color.White.copy(alpha = highlightAlpha),
                        Color.Transparent,
                    ),
                    startX = phase * size.width,
                    endX = phase * size.width + size.width * bandCoverage,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                blendMode = BlendMode.SrcAtop,
            )
        }
}

private fun unwrapShellCommand(raw: String): String {
    var result = raw.trim()
    val lowered = result.lowercase()
    val shellPrefixes = listOf(
        "/usr/bin/bash -lc ",
        "/usr/bin/bash -c ",
        "/bin/bash -lc ",
        "/bin/bash -c ",
        "/bin/zsh -lc ",
        "/bin/zsh -c ",
        "bash -lc ",
        "bash -c ",
        "zsh -lc ",
        "zsh -c ",
        "/bin/sh -c ",
        "sh -c ",
    )

    shellPrefixes.firstOrNull { lowered.startsWith(it) }?.let { prefix ->
        result = result.drop(prefix.length)
            .trim()
            .trim('"', '\'')
        val andIndex = result.indexOf("&&")
        if (andIndex != -1) {
            result = result.substring(andIndex + 2).trim()
        }
    }

    val pipeIndex = result.indexOf(" | ")
    if (pipeIndex != -1) {
        result = result.substring(0, pipeIndex).trim()
    }

    return result
}

private fun lastPathComponent(args: String, fallback: String): String {
    return args.split(Regex("\\s+"))
        .asReversed()
        .map { it.trim('"', '\'') }
        .firstOrNull { token -> token.isNotBlank() && !token.startsWith("-") }
        ?.let(::compactPath)
        ?: fallback
}

private fun compactPath(path: String): String {
    val parts = path.split('/').filter(String::isNotBlank)
    return if (parts.size > 2) {
        parts.takeLast(2).joinToString("/")
    } else {
        path
    }
}

private fun searchSummary(args: String): String {
    val tokens = tokenizeCommandArgs(args)
    val pattern = tokens.firstOrNull { token -> token.isNotBlank() && !token.startsWith("-") } ?: "..."
    val path = tokens.dropWhile { it != pattern }
        .drop(1)
        .firstOrNull { token -> token.isNotBlank() && !token.startsWith("-") }

    return if (path != null) {
        "for $pattern in ${compactPath(path)}"
    } else {
        "for $pattern"
    }
}

private fun tokenizeCommandArgs(input: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false

    input.forEach { char ->
        when {
            escaped -> {
                current.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            quote != null && char == quote -> quote = null
            quote != null -> current.append(char)
            char == '"' || char == '\'' -> quote = char
            char.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }
            else -> current.append(char)
        }
    }

    if (current.isNotEmpty()) {
        tokens += current.toString()
    }
    return tokens
}

private fun gitHumanizedCommand(
    args: String,
    isRunning: Boolean,
): HumanizedCommandInfo {
    val tokens = tokenizeCommandArgs(args)
    val subcommand = tokens.firstOrNull()?.lowercase()
    val target = tokens.drop(1).firstOrNull()?.trim().orEmpty()

    return when (subcommand) {
        "status" -> HumanizedCommandInfo(if (isRunning) "Checking" else "Checked", "git status")
        "diff" -> HumanizedCommandInfo(if (isRunning) "Inspecting" else "Inspected", "git diff")
        "checkout", "switch" -> HumanizedCommandInfo(
            verb = if (isRunning) "Switching" else "Switched",
            target = target.ifBlank { "branch" },
        )
        "branch" -> HumanizedCommandInfo(
            verb = if (isRunning) "Updating" else "Updated",
            target = target.ifBlank { "branch" },
        )
        "add" -> HumanizedCommandInfo(
            verb = if (isRunning) "Staging" else "Staged",
            target = target.ifBlank { "changes" },
        )
        "commit" -> HumanizedCommandInfo(
            verb = if (isRunning) "Committing" else "Committed",
            target = "changes",
        )
        "push" -> HumanizedCommandInfo(
            verb = if (isRunning) "Pushing" else "Pushed",
            target = target.ifBlank { "branch" },
        )
        "pull" -> HumanizedCommandInfo(
            verb = if (isRunning) "Pulling" else "Pulled",
            target = target.ifBlank { "changes" },
        )
        else -> HumanizedCommandInfo(
            verb = if (isRunning) "Running" else "Ran",
            target = "git ${args.trim()}".trim(),
        )
    }
}

@Composable
private fun SubagentAgentRowView(
    title: String,
    statusText: String,
    modelLabel: String?,
    showsDetails: Boolean,
    onShowDetails: (() -> Unit)?,
    onOpen: (() -> Unit)?,
) {
    val chrome = remodexConversationChrome()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = chrome.nestedSurface,
            shape = RemodexConversationShapes.nestedCard,
            border = BorderStroke(1.dp, chrome.subtleBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onOpen != null) { onOpen?.invoke() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = styledSubagentTitleAndStatus(
                        title = title,
                        statusText = statusText,
                        statusColor = chrome.secondaryText,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!modelLabel.isNullOrBlank()) {
                    Text(
                        text = modelLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = chrome.secondaryText,
                        maxLines = 1,
                    )
                }
                if (onOpen != null) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = chrome.tertiaryText,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = -90f },
                    )
                }
            }
        }
        if (showsDetails && onShowDetails != null) {
            IconButton(onClick = onShowDetails) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Subagent details",
                    tint = chrome.secondaryText,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubagentAgentDetailSheet(
    title: String,
    accentColor: Color,
    statusText: String,
    modelTitle: String,
    modelLabel: String?,
    instructionText: String?,
    latestUpdateText: String?,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val chrome = remodexConversationChrome()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = styledSubagentTitle(title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }

            Surface(
                color = chrome.panelSurface,
                shape = RemodexConversationShapes.card,
                border = BorderStroke(1.dp, chrome.subtleBorder),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.18f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color = accentColor, shape = CircleShape),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.labelSmall,
                            color = chrome.secondaryText,
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.bodyText,
                        )
                    }
                }
            }

            if (!modelLabel.isNullOrBlank()) {
                SubagentDetailSection(
                    title = modelTitle,
                    value = modelLabel,
                    monospace = true,
                )
            }

            if (!instructionText.isNullOrBlank()) {
                SubagentDetailSection(
                    title = "Instructions",
                    value = instructionText,
                )
            }

            if (!latestUpdateText.isNullOrBlank()) {
                SubagentDetailSection(
                    title = "Latest update",
                    value = latestUpdateText,
                )
            }

            if (instructionText.isNullOrBlank() && latestUpdateText.isNullOrBlank()) {
                Text(
                    text = "No extra details yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryText,
                )
            }

            onOpen?.let { openChildThread ->
                Button(
                    onClick = {
                        onDismiss()
                        openChildThread()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Open child thread")
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer { rotationZ = -90f },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SubagentDetailSection(
    title: String,
    value: String,
    monospace: Boolean = false,
) {
    val chrome = remodexConversationChrome()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = chrome.secondaryText,
        )
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = chrome.bodyText,
        )
    }
}

private fun resolvedSubagentStatusPresentation(
    action: RemodexSubagentAction,
    presentation: RemodexSubagentThreadPresentation,
    observedThread: RemodexThreadSummary?,
): SubagentStatusPresentation {
    if (observedThread?.isRunning == true) {
        return SubagentStatusPresentation(rawStatus = "running")
    }
    if (presentation.fallbackStatus == null) {
        return SubagentStatusPresentation(rawStatus = action.status)
    }
    return SubagentStatusPresentation(rawStatus = presentation.fallbackStatus)
}

private fun readableSubagentStatus(
    normalizedTool: String,
    label: String,
): String {
    return when (normalizedTool) {
        "spawnagent" -> when (label) {
            "running" -> "Starting child thread"
            "completed" -> "Child thread created"
            "failed" -> "Could not create child thread"
            "stopped" -> "Spawn interrupted"
            "queued" -> "Queued for spawn"
            else -> "Preparing child thread"
        }
        "wait", "waitagent" -> when (label) {
            "running" -> "Still working"
            "completed" -> "Finished"
            "failed" -> "Finished with error"
            "stopped" -> "Stopped early"
            "queued" -> "Queued"
            else -> "Waiting for updates"
        }
        "sendinput" -> when (label) {
            "running" -> "Working on new instructions"
            "completed" -> "Processed the update"
            "failed" -> "Update failed"
            "stopped" -> "Update interrupted"
            "queued" -> "Queued update"
            else -> "Instructions sent"
        }
        "resumeagent" -> when (label) {
            "running" -> "Back to work"
            "completed" -> "Resumed and completed"
            "failed" -> "Resume failed"
            "stopped" -> "Resume interrupted"
            "queued" -> "Queued to resume"
            else -> "Resuming agent"
        }
        "closeagent" -> when (label) {
            "running" -> "Closing"
            "completed" -> "Closed"
            "failed" -> "Close failed"
            "stopped" -> "Close interrupted"
            "queued" -> "Queued to close"
            else -> "Closing agent"
        }
        else -> when (label) {
            "running" -> "Working now"
            "completed" -> "Completed"
            "failed" -> "Ended with error"
            "stopped" -> "Stopped"
            "queued" -> "Queued"
            else -> "Idle"
        }
    }
}

private fun resolveSubagentPresentation(
    presentation: RemodexSubagentThreadPresentation,
    parentThreadId: String,
    threads: List<RemodexThreadSummary>,
    parentThreadMessages: List<RemodexConversationItem>,
): RemodexSubagentThreadPresentation {
    val normalizedThreadId = normalizedSubagentIdentifier(presentation.threadId)
    val normalizedAgentId = normalizedSubagentIdentifier(presentation.agentId)
    var resolvedThreadId = normalizedThreadId
    var resolvedAgentId = normalizedAgentId
    var resolvedNickname = normalizedSubagentIdentifier(presentation.nickname)
    var resolvedRole = normalizedSubagentIdentifier(presentation.role)
    var resolvedModel = normalizedSubagentModelLabel(presentation.model)
    var resolvedModelIsRequestedHint = presentation.modelIsRequestedHint
    var resolvedPrompt = normalizedSubagentIdentifier(presentation.prompt)

    fun mergeThreadMetadata(thread: RemodexThreadSummary?) {
        if (thread == null) {
            return
        }
        if (resolvedThreadId == null) {
            resolvedThreadId = normalizedSubagentIdentifier(thread.id)
        }
        normalizedSubagentIdentifier(thread.agentNickname)?.let { resolvedNickname = it }
        normalizedSubagentIdentifier(thread.agentRole)?.let { resolvedRole = it }
        normalizedSubagentModelLabel(thread.runtimeConfig.selectedModelId)?.let {
            resolvedModel = it
            resolvedModelIsRequestedHint = false
        }
    }

    threads.firstOrNull { thread ->
        normalizedSubagentIdentifier(thread.id) == normalizedThreadId
    }?.let(::mergeThreadMetadata)

    val lookupIdentifiers = setOfNotNull(normalizedThreadId, normalizedAgentId)
    if (lookupIdentifiers.isNotEmpty()) {
        outer@for (message in parentThreadMessages.asReversed()) {
            val action = message.subagentAction ?: continue
            for (candidate in action.agentRows.asReversed()) {
                val candidateThreadId = normalizedSubagentIdentifier(candidate.threadId)
                val candidateAgentId = normalizedSubagentIdentifier(candidate.agentId)
                val matchedIdentifiers = setOfNotNull(candidateThreadId, candidateAgentId)
                if (lookupIdentifiers.intersect(matchedIdentifiers).isEmpty()) {
                    continue
                }

                if (resolvedThreadId == null) {
                    resolvedThreadId = candidateThreadId
                }
                if (resolvedAgentId == null) {
                    resolvedAgentId = candidateAgentId
                }
                if (resolvedNickname == null) {
                    resolvedNickname = normalizedSubagentIdentifier(candidate.nickname)
                }
                if (resolvedRole == null) {
                    resolvedRole = normalizedSubagentIdentifier(candidate.role)
                }
                if (resolvedModel == null) {
                    resolvedModel = normalizedSubagentModelLabel(candidate.model)
                    resolvedModelIsRequestedHint = candidate.modelIsRequestedHint
                }
                if (resolvedPrompt == null) {
                    resolvedPrompt = normalizedSubagentIdentifier(candidate.prompt)
                }
                threads.firstOrNull { thread ->
                    normalizedSubagentIdentifier(thread.id) == candidateThreadId
                }?.let(::mergeThreadMetadata)
                break@outer
            }
        }
    }

    val finalThreadId = resolvedThreadId ?: normalizedThreadId ?: presentation.threadId
    return RemodexSubagentThreadPresentation(
        threadId = finalThreadId,
        agentId = resolvedAgentId,
        nickname = resolvedNickname,
        role = resolvedRole,
        model = resolvedModel,
        modelIsRequestedHint = resolvedModelIsRequestedHint,
        prompt = resolvedPrompt,
        fallbackStatus = presentation.fallbackStatus,
        fallbackMessage = presentation.fallbackMessage,
    )
}

private fun observedSubagentThread(
    presentation: RemodexSubagentThreadPresentation,
    threads: List<RemodexThreadSummary>,
): RemodexThreadSummary? {
    val normalizedThreadId = normalizedSubagentIdentifier(presentation.threadId)
    return threads.firstOrNull { thread ->
        normalizedSubagentIdentifier(thread.id) == normalizedThreadId
    }
}

private fun resolvedSubagentTitle(
    presentation: RemodexSubagentThreadPresentation,
): String = presentation.displayLabel

private fun resolvedSubagentModelLabel(
    presentation: RemodexSubagentThreadPresentation,
    observedThread: RemodexThreadSummary?,
    prefixRequested: Boolean = true,
): String? {
    normalizedSubagentModelLabel(presentation.model)?.let { model ->
        return if (presentation.modelIsRequestedHint && prefixRequested) {
            "requested: $model"
        } else {
            model
        }
    }
    return normalizedSubagentModelLabel(observedThread?.runtimeConfig?.selectedModelId)
}

private fun detailSubagentText(
    action: RemodexSubagentAction,
    presentation: RemodexSubagentThreadPresentation,
): String? {
    val sections = mutableListOf<String>()
    trimmedSubagentValue(presentation.prompt)
        ?: trimmedSubagentValue(action.prompt)
    ?.let(sections::add)
    val latestUpdate = trimmedSubagentValue(presentation.fallbackMessage)
    if (latestUpdate != null && latestUpdate !in sections) {
        sections += if (sections.isEmpty()) latestUpdate else "Latest update: $latestUpdate"
    }
    return sections.filter(String::isNotBlank).takeIf(List<String>::isNotEmpty)?.joinToString("\n\n")
}

private fun subagentAgentRowSpacing(normalizedTool: String): androidx.compose.ui.unit.Dp {
    return when (normalizedTool) {
        "wait", "waitagent", "resumeagent" -> 9.dp
        "spawnagent" -> 2.dp
        else -> 4.dp
    }
}

private fun subagentAgentRowsTopPadding(normalizedTool: String): androidx.compose.ui.unit.Dp {
    return when (normalizedTool) {
        "wait", "waitagent", "resumeagent" -> 3.dp
        else -> 2.dp
    }
}

private fun trimmedSubagentValue(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    return trimmed.takeIf(String::isNotEmpty)
}

private fun normalizedSubagentModelLabel(value: String?): String? {
    val trimmed = trimmedSubagentValue(value) ?: return null
    return if (trimmed.lowercase() == "openai") {
        null
    } else {
        trimmed
    }
}

private fun normalizedSubagentIdentifier(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return null
    }
    return when (trimmed.lowercase()) {
        "collabagenttoolcall", "collabtoolcall" -> null
        else -> trimmed
    }
}

private fun styledSubagentTitleAndStatus(
    title: String,
    statusText: String,
    statusColor: Color,
): AnnotatedString {
    val parts = parseSubagentTitle(title)
    return buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                color = subagentColorForName(parts.nickname),
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append(parts.nickname)
        }
        if (parts.roleSuffix.isNotEmpty()) {
            withStyle(style = SpanStyle(color = Color.Unspecified)) {
                append(parts.roleSuffix)
            }
        }
        withStyle(style = SpanStyle(color = statusColor)) {
            append(" $statusText")
        }
    }
}

private fun styledSubagentTitle(title: String): AnnotatedString {
    val parts = parseSubagentTitle(title)
    return buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                color = subagentColorForName(parts.nickname),
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append(parts.nickname)
        }
        if (parts.roleSuffix.isNotEmpty()) {
            withStyle(style = SpanStyle(color = Color.Unspecified)) {
                append(parts.roleSuffix)
            }
        }
    }
}

private data class ParsedSubagentTitle(
    val nickname: String,
    val roleSuffix: String,
)

private fun parseSubagentTitle(title: String): ParsedSubagentTitle {
    if (!title.endsWith("]")) {
        return ParsedSubagentTitle(nickname = title, roleSuffix = "")
    }
    val openBracket = title.lastIndexOf('[')
    if (openBracket == -1) {
        return ParsedSubagentTitle(nickname = title, roleSuffix = "")
    }
    val nickname = title.substring(0, openBracket).trim()
    val role = title.substring(openBracket + 1, title.length - 1).trim()
    if (role.isEmpty()) {
        return ParsedSubagentTitle(nickname = nickname.ifBlank { title }, roleSuffix = "")
    }
    val resolvedName = nickname.ifBlank { role.replaceFirstChar(Char::titlecase) }
    return ParsedSubagentTitle(
        nickname = resolvedName,
        roleSuffix = " ($role)",
    )
}

private fun subagentNicknameColorForTitle(title: String): Color {
    return subagentColorForName(parseSubagentTitle(title).nickname)
}

private fun subagentColorForName(name: String): Color {
    val palette = listOf(
        Color(0xFFE64D4D),
        Color(0xFF4DBF8C),
        Color(0xFF668CF2),
        Color(0xFFD99940),
        Color(0xFFB373D9),
        Color(0xFF40C7D1),
        Color(0xFFE68099),
        Color(0xFFA6BF4D),
    )
    var hash = 5381L
    name.forEach { char ->
        hash = ((hash shl 5) + hash) + char.code
    }
    return palette[(hash % palette.size).toInt().let { if (it < 0) it + palette.size else it }]
}

private data class SubagentStatusPresentation(
    val rawStatus: String?,
) {
    val normalized: String
        get() = rawStatus
            ?.trim()
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            .orEmpty()
            .ifBlank { "unknown" }

    val label: String
        get() = when (normalized) {
            "running", "inprogress" -> "running"
            "completed", "done", "finished", "success" -> "completed"
            "failed", "error", "errored" -> "failed"
            "stopped", "cancelled", "canceled", "interrupted" -> "stopped"
            "queued", "pending" -> "queued"
            else -> "idle"
        }
}

@Composable
private fun StructuredUserInputRow(request: RemodexStructuredUserInputRequest?) {
    if (request == null) {
        return
    }
    val chrome = remodexConversationChrome()
    Surface(
        color = chrome.accentSurface,
        shape = RemodexConversationShapes.card,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Needs input",
                style = MaterialTheme.typography.labelMedium,
                color = chrome.titleText,
            )
            request.questions.forEach { question ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = question.header,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = chrome.titleText,
                    )
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.bodyMedium,
                        color = chrome.bodyText,
                    )
                    question.options.forEach { option ->
                        Surface(
                            color = chrome.nestedSurface,
                            shape = RemodexConversationShapes.nestedCard,
                            border = BorderStroke(1.dp, chrome.subtleBorder),
                        ) {
                            Text(
                                text = "${option.label}: ${option.description}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = chrome.bodyText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultSystemRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
) {
    val chrome = remodexConversationChrome()
    ConversationMessageActionContainer(
        text = item.text,
        messageRole = ConversationSpeaker.SYSTEM,
        usesMarkdownSelection = false,
        allowsSelectText = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item.text.takeIf(String::isNotBlank)?.let { text ->
                ConversationMarkdownText(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryText,
                )
            }
            item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryText,
                )
            }
            if (accessoryState?.showsRunningIndicator == true) {
                TerminalRunningIndicator()
            }
        }
    }
}

@Composable
private fun MessageAttachmentStrip(
    attachments: List<RemodexConversationAttachment>,
    alignToEnd: Boolean,
) {
    val chrome = remodexConversationChrome()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignToEnd) Arrangement.End else Arrangement.Start,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            attachments.forEach { attachment ->
                Surface(
                    modifier = Modifier.width(140.dp),
                    color = chrome.nestedSurface,
                    shape = RemodexConversationShapes.nestedCard,
                    border = BorderStroke(1.dp, chrome.subtleBorder),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AsyncImage(
                            model = attachment.uriString,
                            contentDescription = attachment.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = attachment.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = chrome.titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageDeliveryStatus(
    state: RemodexMessageDeliveryState,
    createdAtEpochMs: Long?,
) {
    val chrome = remodexConversationChrome()
    val context = LocalContext.current
    val label = when (state) {
        RemodexMessageDeliveryState.PENDING -> "sending..."
        RemodexMessageDeliveryState.FAILED -> "send failed"
        RemodexMessageDeliveryState.CONFIRMED -> createdAtEpochMs?.let { epochMs ->
            DateFormat.getTimeFormat(context).format(Date(epochMs))
        }
    }
    if (label != null) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (state == RemodexMessageDeliveryState.FAILED) {
                chrome.destructive
            } else {
                chrome.secondaryText
            },
        )
    }
}

@Composable
private fun StreamingIndicator(label: String) {
    val chrome = remodexConversationChrome()
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = chrome.secondaryText,
        textDecoration = TextDecoration.None,
    )
}

private fun highlightMentions(text: String, chrome: RemodexConversationChrome) = buildAnnotatedString {
    val mentionRegex = Regex("([@\\$][^\\s]+)")
    var cursor = 0
    mentionRegex.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        withStyle(
            SpanStyle(
                color = if (match.value.startsWith("@")) chrome.accent else chrome.warning,
                fontWeight = FontWeight.Medium,
            ),
        ) {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

@Composable
private fun AssistantRevertAction(
    presentation: RemodexAssistantRevertPresentation,
    onTap: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val actionColor = when (presentation.riskLevel) {
        RemodexAssistantRevertRiskLevel.SAFE -> chrome.accent
        RemodexAssistantRevertRiskLevel.WARNING -> chrome.warning
        RemodexAssistantRevertRiskLevel.BLOCKED -> chrome.secondaryText
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            color = chrome.nestedSurface,
            shape = RemodexConversationShapes.nestedCard,
            border = BorderStroke(1.dp, chrome.subtleBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .clickable(
                        enabled = presentation.isEnabled,
                        onClick = onTap,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = actionColor,
                ) {}
                Text(
                    text = presentation.title,
                    color = actionColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        presentation.helperText?.takeIf(String::isNotBlank)?.let { helperText ->
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = chrome.secondaryText,
            )
        }
    }
}

@Composable
private fun AssistantBlockDiffAction(
    entries: List<FileChangeSummaryEntry>,
    onTap: () -> Unit,
) {
    if (entries.isEmpty()) {
        return
    }

    val chrome = remodexConversationChrome()
    val monoFamily = MaterialTheme.typography.labelLarge.fontFamily ?: FontFamily.Monospace
    val totalAdditions = remember(entries) { entries.sumOf(FileChangeSummaryEntry::additions) }
    val totalDeletions = remember(entries) { entries.sumOf(FileChangeSummaryEntry::deletions) }

    Surface(
        color = chrome.nestedSurface,
        shape = RemodexConversationShapes.nestedCard,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onTap)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = chrome.secondaryText,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Diff",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = monoFamily),
                color = chrome.bodyText,
            )
            FileChangeDiffCounts(
                additions = totalAdditions,
                deletions = totalDeletions,
            )
        }
    }
}

@Composable
private fun ConversationCopyBlockButton(text: String) {
    val chrome = remodexConversationChrome()
    val context = LocalContext.current
    var didCopy by remember(text) { mutableStateOf(false) }

    LaunchedEffect(didCopy) {
        if (didCopy) {
            delay(1_500)
            didCopy = false
        }
    }

    Row(
        modifier = Modifier
            .testTag(ConversationCopyButtonTag)
            .clickable {
                copyPlainTextToClipboard(
                    context = context,
                    label = "Assistant response",
                    text = text,
                )
                didCopy = true
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (didCopy) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = chrome.secondaryText,
            modifier = Modifier.size(15.dp),
        )
        if (didCopy) {
            Text(
                text = "Copied",
                style = MaterialTheme.typography.labelSmall,
                color = chrome.secondaryText,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantRevertSheet(
    sheetState: RemodexAssistantRevertSheetState,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    val affectedFiles = sheetState.preview?.affectedFiles
        ?.takeIf(List<String>::isNotEmpty)
        ?: sheetState.changeSet.fileChanges.map { fileChange -> fileChange.path }
    val totalAdditions = sheetState.changeSet.fileChanges.sumOf { fileChange -> fileChange.additions }
    val totalDeletions = sheetState.changeSet.fileChanges.sumOf { fileChange -> fileChange.deletions }
    val canConfirm = sheetState.preview?.canRevert == true && !sheetState.isLoadingPreview && !sheetState.isApplying

    ModalBottomSheet(
        onDismissRequest = onClose,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Undo this response",
                style = MaterialTheme.typography.headlineSmall,
                color = chrome.titleText,
            )

            OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "This action will try to undo only the changes from this response. Later local edits stay untouched unless they overlap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = chrome.bodyText,
                    )
                    Text(
                        text = "${affectedFiles.size} file(s) · +$totalAdditions · -$totalDeletions",
                        style = MaterialTheme.typography.labelMedium,
                        color = chrome.secondaryText,
                    )
                    affectedFiles.forEach { path ->
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            color = chrome.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    sheetState.presentation.warningText?.takeIf(String::isNotBlank)?.let { warningText ->
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            color = chrome.warning,
                        )
                    }
                }
            }

            when {
                sheetState.isLoadingPreview -> {
                    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text = "Checking whether the reverse patch applies cleanly...",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.bodyText,
                        )
                    }
                }

                sheetState.preview != null -> {
                    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = when {
                                    sheetState.preview.canRevert -> "This response can be undone cleanly."
                                    else -> "Could not safely undo this response."
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = chrome.titleText,
                            )
                            if (sheetState.preview.stagedFiles.isNotEmpty()) {
                                Text(
                                    text = sheetState.preview.stagedFiles.joinToString(
                                        prefix = "Staged files: ",
                                        separator = ", ",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = chrome.secondaryText,
                                )
                            }
                            if (sheetState.preview.unsupportedReasons.isNotEmpty()) {
                                sheetState.preview.unsupportedReasons.forEach { reason ->
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = chrome.secondaryText,
                                    )
                                }
                            }
                            if (sheetState.preview.conflicts.isNotEmpty()) {
                                sheetState.preview.conflicts.forEach { conflict ->
                                    Text(
                                        text = "${conflict.path}: ${conflict.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = chrome.secondaryText,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            sheetState.errorMessage?.takeIf(String::isNotBlank)?.let { errorMessage ->
                OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = chrome.destructive,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Close")
                }
                FilledTonalButton(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (sheetState.isApplying) "Undoing..." else "Undo")
                }
            }
        }
    }
}
