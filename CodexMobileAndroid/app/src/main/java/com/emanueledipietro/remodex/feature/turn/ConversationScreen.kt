package com.emanueledipietro.remodex.feature.turn

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import com.emanueledipietro.remodex.ui.theme.RemodexConversationChrome
import com.emanueledipietro.remodex.ui.theme.RemodexConversationShapes
import com.emanueledipietro.remodex.ui.theme.remodexConversationChrome
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private val ComposerFollowBottomThreshold = 156.dp
private val ComposerTrailingButtonSize = 32.dp
private val ComposerLeadingIconTapTarget = 24.dp
private val ComposerModelMenuMaxWidth = 160.dp
private val ComposerReasoningMenuMaxWidth = 132.dp
private val FileAutocompleteRowHeight = 50.dp
private val SkillAutocompleteRowHeight = 50.dp
private val SlashAutocompleteRowHeight = 50.dp
private const val MaxAutocompleteVisibleRows = 6
internal const val ComposerAutocompletePanelTag = "composer_autocomplete_panel"
internal const val ComposerAutocompleteDismissLayerTag = "composer_autocomplete_dismiss_layer"
internal const val ComposerSendButtonTag = "composer_send_button"
internal const val ConversationRunningIndicatorTag = "conversation_running_indicator"

private data class ConversationBlockAccessoryState(
    val showsRunningIndicator: Boolean = false,
)

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
    onCheckoutGitBranch: (String) -> Unit,
    onCreateGitBranch: (String) -> Unit,
    onCreateGitWorktree: (String) -> Unit,
    onCommitGitChanges: () -> Unit,
    onPullGitChanges: () -> Unit,
    onPushGitChanges: () -> Unit,
    onDiscardRuntimeChangesAndSync: () -> Unit,
    onForkThread: (RemodexComposerForkDestination) -> Unit,
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

    var gitSheetExpanded by rememberSaveable(thread.id) { mutableStateOf(false) }
    var planSheetExpanded by rememberSaveable(thread.id) { mutableStateOf(false) }
    var composerFocused by rememberSaveable(thread.id) { mutableStateOf(false) }
    val pinnedPlanItem = thread.messages.lastOrNull { item -> item.kind == ConversationItemKind.PLAN }
    val timelineItems = thread.messages.filterNot { item -> item.id == pinnedPlanItem?.id }
    val blockAccessories = remember(timelineItems, thread.isRunning) {
        buildConversationBlockAccessories(
            items = timelineItems,
            isThreadRunning = thread.isRunning,
        )
    }
    val timelineState = rememberLazyListState()
    val density = LocalDensity.current
    val lastTimelineItemId = timelineItems.lastOrNull()?.id
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val followBottomThresholdPx = with(density) { ComposerFollowBottomThreshold.roundToPx() }
    var initialScrollApplied by rememberSaveable(thread.id) { mutableStateOf(false) }
    var keepTimelinePinnedToBottom by rememberSaveable(thread.id) { mutableStateOf(true) }

    LaunchedEffect(thread.id, lastTimelineItemId) {
        if (!initialScrollApplied && timelineItems.isNotEmpty()) {
            withFrameNanos { }
            timelineState.scrollToItem(timelineItems.lastIndex)
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

    LaunchedEffect(
        thread.id,
        lastTimelineItemId,
        thread.isRunning,
        initialScrollApplied,
        keepTimelinePinnedToBottom,
    ) {
        if (
            initialScrollApplied &&
            timelineItems.isNotEmpty() &&
            thread.isRunning &&
            keepTimelinePinnedToBottom
        ) {
            timelineState.animateScrollToItem(timelineItems.lastIndex)
        }
    }

    LaunchedEffect(
        thread.id,
        composerFocused,
        imeVisible,
        keepTimelinePinnedToBottom,
        lastTimelineItemId,
    ) {
        if (
            timelineItems.isNotEmpty() &&
            composerFocused &&
            imeVisible &&
            keepTimelinePinnedToBottom
        ) {
            timelineState.animateScrollToItem(timelineItems.lastIndex)
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

                    LazyColumn(
                        state = timelineState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (timelineItems.isEmpty()) {
                            item {
                                if (pinnedPlanItem == null) {
                                    EmptyThreadTimelineCard()
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp),
                                    )
                                }
                            }
                        } else {
                            items(timelineItems, key = { it.id }) { message ->
                                ConversationBubble(
                                    item = message,
                                    accessoryState = blockAccessories[message.id],
                                    assistantRevertPresentation = uiState.assistantRevertStatesByMessageId[message.id],
                                    onTapAssistantRevert = onStartAssistantRevertPreview,
                                )
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
                            canSendQueuedDrafts = !thread.isRunning,
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
                            if (autocompleteVisible) {
                                AutocompletePanel(
                                    uiState = uiState,
                                    onSelectFileAutocomplete = onSelectFileAutocomplete,
                                    onSelectSkillAutocomplete = onSelectSkillAutocomplete,
                                    onSelectSlashCommand = onSelectSlashCommand,
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
                                onRemoveAttachment = onRemoveAttachment,
                                onSelectFileAutocomplete = onSelectFileAutocomplete,
                                onRemoveMentionedFile = onRemoveMentionedFile,
                                onSelectSkillAutocomplete = onSelectSkillAutocomplete,
                                onRemoveMentionedSkill = onRemoveMentionedSkill,
                                onSelectSlashCommand = onSelectSlashCommand,
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

                    if (!composerFocused) {
                        ComposerSecondaryBar(
                            gitState = uiState.composer.gitState,
                            selectedBaseBranch = uiState.composer.selectedGitBaseBranch,
                            accessMode = uiState.composer.runtimeConfig.accessMode,
                            onRefreshGitState = onRefreshGitState,
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
) {
    val chrome = remodexConversationChrome()
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
                .clickable(enabled = enabled, onClick = onClick),
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
) {
    val chrome = remodexConversationChrome()
    val monoFamily = MaterialTheme.typography.labelLarge.fontFamily
    val blocks = remember(text) { parseConversationTextBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block.kind) {
                ConversationTextBlockKind.PROSE -> Text(
                    text = buildConversationAnnotatedString(
                        text = block.text,
                        chrome = chrome,
                        monoFamily = monoFamily,
                    ),
                    style = style,
                    color = color,
                )

                ConversationTextBlockKind.CODE -> {
                    Surface(
                        color = chrome.mutedSurface,
                        shape = RemodexConversationShapes.nestedCard,
                        border = BorderStroke(1.dp, chrome.subtleBorder),
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = block.text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                lineHeight = 18.sp,
                                fontFamily = monoFamily,
                            ),
                            color = chrome.bodyText,
                        )
                    }
                }
            }
        }
    }
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
            gitState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.destructive,
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
    gitState: RemodexGitState,
    selectedBaseBranch: String,
    accessMode: RemodexAccessMode,
    onRefreshGitState: () -> Unit,
    onOpenGitSheet: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetaPill("Local", backgroundColor = chrome.mutedSurface, contentColor = chrome.titleText)
        MetaPill(
            accessMode.label,
            backgroundColor = chrome.mutedSurface,
            contentColor = chrome.titleText,
        )
        gitState.branches.currentBranch?.takeIf(String::isNotBlank)?.let { branch ->
            MetaPill(branch, backgroundColor = chrome.mutedSurface, contentColor = chrome.titleText)
        }
        if (selectedBaseBranch.isNotBlank()) {
            MetaPill(
                label = "vs $selectedBaseBranch",
                backgroundColor = chrome.mutedSurface,
                contentColor = chrome.titleText,
            )
        }
        SecondaryBarAction(label = "Refresh", onClick = onRefreshGitState)
        SecondaryBarAction(label = "Git", onClick = onOpenGitSheet)
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
                .padding(top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                if (composer.draftText.isBlank()) {
                    Text(
                        text = if (uiState.selectedThread?.isRunning == true) {
                            "Queue a follow-up"
                        } else {
                            "Ask anything... @files, \$skills, /commands"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = chrome.secondaryText.copy(alpha = 0.86f),
                    )
                }
                BasicTextField(
                    value = composer.draftText,
                    onValueChange = onComposerInputChanged,
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
                            Box(
                                modifier = Modifier
                                    .requiredSize(ComposerLeadingIconTapTarget)
                                    .clickable(onClick = { plusMenuExpanded = !plusMenuExpanded }),
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
                                    text = {
                                        Text(
                                            if (composer.runtimeConfig.planningMode == RemodexPlanningMode.PLAN) {
                                                "Disable plan mode"
                                            } else {
                                                "Enable plan mode"
                                            },
                                        )
                                    },
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
                                )
                                ComposerDropdownMenuItem(
                                    text = { Text("Photo library") },
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
                                    text = { Text("Take a photo") },
                                    enabled = false,
                                    onClick = {
                                        plusMenuExpanded = false
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
                            )
                            if (queuedCount > 0) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-6).dp),
                                    shape = CircleShape,
                                    color = if (uiState.selectedThread?.isRunning == true) {
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (composer.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
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
            AccessoryChipRow {
                composer.mentionedFiles.forEach { mention ->
                    AccessoryChip(
                        label = "@${mention.fileName}",
                        onRemove = { onRemoveMentionedFile(mention.id) },
                    )
                }
            }
        }

        if (composer.mentionedSkills.isNotEmpty()) {
            AccessoryChipRow {
                composer.mentionedSkills.forEach { mention ->
                    AccessoryChip(
                        label = "\$${mention.name}",
                        onRemove = { onRemoveMentionedSkill(mention.id) },
                    )
                }
            }
        }

        composer.reviewSelection?.target?.let { target ->
            AccessoryChipRow {
                AccessoryChip(
                    label = "Code Review: ${target.title}",
                    onRemove = onClearReviewSelection,
                )
            }
        }

        if (composer.isSubagentsSelectionArmed) {
            AccessoryChipRow {
                AccessoryChip(
                    label = "Subagents",
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
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun AccessoryChip(
    label: String,
    onRemove: () -> Unit,
) {
    val chrome = remodexConversationChrome()
    Surface(
        color = chrome.mutedSurface,
        shape = RemodexConversationShapes.pill,
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = chrome.titleText,
            )
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove $label",
                tint = chrome.secondaryText,
            )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FileAutocompleteRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = chrome.titleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.path,
            style = MaterialTheme.typography.bodySmall,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(SkillAutocompleteRowHeight)
            .clickable(onClick = onClick)
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
                style = MaterialTheme.typography.bodyMedium,
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
    val contentColor = if (enabled) chrome.titleText else chrome.secondaryText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SlashAutocompleteRowHeight)
            .clickable(enabled = enabled, onClick = onClick)
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
    val contentColor = if (enabled) chrome.titleText else chrome.secondaryText
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(SlashAutocompleteRowHeight)
            .clickable(enabled = enabled, onClick = onClick)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SlashAutocompleteRowHeight)
            .clickable(onClick = onClick)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attachment.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.titleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onRemoveAttachment(attachment.id) }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove attachment",
                        tint = chrome.secondaryText,
                    )
                }
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
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
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
    if (!expanded) {
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
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.heightIn(min = 30.dp),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    )
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
) {
    when (item.speaker) {
        ConversationSpeaker.USER -> UserConversationRow(item = item)
        ConversationSpeaker.ASSISTANT -> AssistantConversationRow(
            item = item,
            accessoryState = accessoryState,
            assistantRevertPresentation = assistantRevertPresentation,
            onTapAssistantRevert = onTapAssistantRevert,
        )
        ConversationSpeaker.SYSTEM -> SystemConversationRow(
            item = item,
            accessoryState = accessoryState,
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
        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
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
            MessageDeliveryStatus(item.deliveryState)
        }
    }
}

@Composable
private fun AssistantConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
    assistantRevertPresentation: RemodexAssistantRevertPresentation?,
    onTapAssistantRevert: (String) -> Unit,
) {
    val chrome = remodexConversationChrome()
    Column(
        modifier = Modifier.fillMaxWidth(0.94f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (item.text.isNotBlank()) {
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
        }
        if (assistantRevertPresentation != null) {
            AssistantRevertAction(
                presentation = assistantRevertPresentation,
                onTap = { onTapAssistantRevert(item.id) },
            )
        }
    }
}

@Composable
private fun SystemConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
) {
    when (item.kind) {
        ConversationItemKind.REASONING -> ThinkingConversationRow(
            item = item,
            accessoryState = accessoryState,
        )
        ConversationItemKind.FILE_CHANGE -> FileChangeConversationRow(
            item = item,
            accessoryState = accessoryState,
        )
        ConversationItemKind.COMMAND_EXECUTION -> CommandExecutionConversationRow(
            item = item,
            accessoryState = accessoryState,
        )
        ConversationItemKind.SUBAGENT_ACTION -> SubagentActionRow(
            item = item,
            accessoryState = accessoryState,
        )
        ConversationItemKind.USER_INPUT_PROMPT -> StructuredUserInputRow(item.structuredUserInputRequest)
        ConversationItemKind.PLAN -> Unit
        ConversationItemKind.CHAT -> DefaultSystemRow(
            item = item,
            accessoryState = accessoryState,
        )
    }
}

@Composable
private fun ThinkingConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
) {
    val chrome = remodexConversationChrome()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Thinking...",
            style = MaterialTheme.typography.labelMedium,
            color = chrome.secondaryText,
        )
        if (item.text.isNotBlank()) {
            ConversationMarkdownText(
                text = item.text,
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

@Composable
private fun FileChangeConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "File changes",
                style = MaterialTheme.typography.labelMedium,
                color = chrome.secondaryText,
            )
            if (item.text.isNotBlank()) {
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

@Composable
private fun CommandExecutionConversationRow(
    item: RemodexConversationItem,
    accessoryState: ConversationBlockAccessoryState?,
) {
    val chrome = remodexConversationChrome()
    val commandRows = remember(item.text) {
        item.text
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    }
    val parsedRows = remember(commandRows) {
        commandRows.map { line -> parseCommandExecutionStatus(line) }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (parsedRows.all { it != null } && parsedRows.isNotEmpty()) {
            parsedRows.filterNotNull().forEach { status ->
                CommandExecutionStatusLine(status = status)
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
) {
    val chrome = remodexConversationChrome()
    val action = item.subagentAction
    var expanded by rememberSaveable(item.id) { mutableStateOf(true) }
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
                text = action?.summaryText ?: item.text,
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
        if (expanded) {
            action?.agentRows?.forEach { row ->
                Surface(
                    color = chrome.nestedSurface,
                    shape = RemodexConversationShapes.nestedCard,
                    border = BorderStroke(1.dp, chrome.subtleBorder),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = row.displayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = chrome.titleText,
                        )
                        readableSubagentStatus(
                            action = action,
                            rawStatus = row.fallbackStatus,
                        )?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = subagentStatusColor(
                                    chrome = chrome,
                                    action = action,
                                    rawStatus = row.fallbackStatus,
                                ),
                            )
                        }
                        row.fallbackMessage?.takeIf(String::isNotBlank)?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = chrome.secondaryText,
                            )
                        }
                    }
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
}

private enum class CommandExecutionStatusAccent {
    RUNNING,
    COMPLETED,
    FAILED,
}

private data class CommandExecutionStatusPresentation(
    val command: String,
    val statusLabel: String,
    val accent: CommandExecutionStatusAccent,
)

private data class HumanizedCommandInfo(
    val verb: String,
    val target: String,
)

@Composable
private fun CommandExecutionStatusLine(status: CommandExecutionStatusPresentation) {
    val chrome = remodexConversationChrome()
    val humanized = remember(status.command, status.accent) {
        humanizeCommand(
            raw = status.command,
            isRunning = status.accent == CommandExecutionStatusAccent.RUNNING,
        )
    }
    val accentColor = when (status.accent) {
        CommandExecutionStatusAccent.RUNNING -> chrome.warning
        CommandExecutionStatusAccent.COMPLETED -> chrome.secondaryText.copy(alpha = 0.7f)
        CommandExecutionStatusAccent.FAILED -> chrome.destructive
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${humanized.verb} ${humanized.target}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = chrome.bodyText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = status.statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
        )
    }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        )
    }
}

private fun buildConversationBlockAccessories(
    items: List<RemodexConversationItem>,
    isThreadRunning: Boolean,
): Map<String, ConversationBlockAccessoryState> {
    if (!isThreadRunning || items.isEmpty()) {
        return emptyMap()
    }

    val latestBlockEnd = items.indexOfLast { it.speaker != ConversationSpeaker.USER }
    if (latestBlockEnd == -1) {
        return emptyMap()
    }

    val activeTurnId = items.asReversed().firstNotNullOfOrNull { item ->
        item.turnId?.trim()?.takeIf(String::isNotEmpty)
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

        val showsRunningIndicator = when {
            activeTurnId != null && blockTurnId != null -> activeTurnId == blockTurnId
            else -> blockEnd == latestBlockEnd
        }

        if (showsRunningIndicator) {
            accessories[items[blockEnd].id] = ConversationBlockAccessoryState(
                showsRunningIndicator = true,
            )
        }

        cursor = blockStart - 1
    }

    return accessories
}

private fun parseCommandExecutionStatus(text: String): CommandExecutionStatusPresentation? {
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

private fun humanizeCommand(
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

private fun readableSubagentStatus(
    action: RemodexSubagentAction?,
    rawStatus: String?,
): String? {
    val label = normalizedSubagentStatus(rawStatus ?: action?.status)
    return when (action?.normalizedTool) {
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

private fun normalizedSubagentStatus(rawStatus: String?): String {
    return rawStatus?.trim()
        ?.lowercase()
        ?.replace("_", "")
        ?.replace("-", "")
        ?.ifBlank { null }
        ?: "idle"
}

private fun subagentStatusColor(
    chrome: RemodexConversationChrome,
    action: RemodexSubagentAction?,
    rawStatus: String?,
): Color {
    return when (normalizedSubagentStatus(rawStatus ?: action?.status)) {
        "failed" -> chrome.destructive
        "completed" -> chrome.secondaryText
        "running" -> chrome.warning
        else -> chrome.secondaryText
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
private fun MessageDeliveryStatus(state: RemodexMessageDeliveryState) {
    val chrome = remodexConversationChrome()
    val label = when (state) {
        RemodexMessageDeliveryState.PENDING -> "sending..."
        RemodexMessageDeliveryState.FAILED -> "send failed"
        RemodexMessageDeliveryState.CONFIRMED -> null
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
