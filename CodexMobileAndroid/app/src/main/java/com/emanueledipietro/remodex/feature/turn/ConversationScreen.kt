package com.emanueledipietro.remodex.feature.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
    if (thread == null) {
        EmptyConversationState(modifier = modifier)
        return
    }

    var gitSheetExpanded by rememberSaveable(thread.id) { mutableStateOf(false) }
    var planSheetExpanded by rememberSaveable(thread.id) { mutableStateOf(false) }
    var composerFocused by rememberSaveable(thread.id) { mutableStateOf(false) }
    val pinnedPlanItem = thread.messages.lastOrNull { item -> item.kind == ConversationItemKind.PLAN }
    val timelineItems = thread.messages.filterNot { item -> item.id == pinnedPlanItem?.id }
    val timelineState = rememberLazyListState()

    LaunchedEffect(thread.id) {
        if (timelineItems.isNotEmpty()) {
            timelineState.scrollToItem(timelineItems.lastIndex)
        }
    }

    LaunchedEffect(thread.id, timelineItems.lastOrNull()?.id, thread.isRunning) {
        if (timelineItems.isNotEmpty() && thread.isRunning) {
            timelineState.animateScrollToItem(timelineItems.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                            assistantRevertPresentation = uiState.assistantRevertStatesByMessageId[message.id],
                            onTapAssistantRevert = onStartAssistantRevertPreview,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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

                    if (uiState.composer.autocomplete.panel != RemodexComposerAutocompletePanel.NONE) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                                .offset(y = (-12).dp),
                        ) {
                            AutocompletePanel(
                                uiState = uiState,
                                onSelectFileAutocomplete = onSelectFileAutocomplete,
                                onSelectSkillAutocomplete = onSelectSkillAutocomplete,
                                onSelectSlashCommand = onSelectSlashCommand,
                                onSelectCodeReviewTarget = onSelectCodeReviewTarget,
                                onCloseComposerAutocomplete = onCloseComposerAutocomplete,
                                onForkThread = onForkThread,
                            )
                        }
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

@Composable
private fun ConversationTopOverlays(
    uiState: AppUiState,
    onRetryConnection: () -> Unit,
) {
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
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(18.dp),
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
                        )
                        Text(
                            text = uiState.connectionMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onRetryConnection) {
                        Text("Reconnect")
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun PlanAccessoryCard(
    planItem: RemodexConversationItem,
    onClick: () -> Unit,
) {
    val planSummary = planItem.planState?.explanation
        ?.takeIf(String::isNotBlank)
        ?: planItem.planState?.steps?.firstOrNull()?.step
        ?: planItem.text
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        shape = RoundedCornerShape(20.dp),
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
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(7.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = planSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Select a thread to open the iOS-aligned Android conversation view.",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun EmptyThreadTimelineCard() {
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
        )
        Text(
            text = "The next reply from your Mac will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDetailsSheet(
    planItem: RemodexConversationItem,
    onDismiss: () -> Unit,
) {
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
            )
            planState?.explanation?.takeIf(String::isNotBlank)?.let { explanation ->
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (!planState?.steps.isNullOrEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    planState?.steps.orEmpty().forEach { step ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(16.dp),
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
                                )
                                MetaPill(
                                    label = step.status.label,
                                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = planItem.text,
                    style = MaterialTheme.typography.bodyLarge,
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
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (draft.attachments.isNotEmpty()) {
                            Text(
                                text = "${draft.attachments.size} attachment(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (canSendQueuedDrafts) {
                        TextButton(onClick = { onSendQueuedDraft(draft.id) }) {
                            Text("Send")
                        }
                    } else {
                        Text(
                            text = "Queued",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    if (!gitState.hasContext) {
        return
    }

    var branchDraft by rememberSaveable { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(26.dp),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            gitState.lastActionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            gitState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetaPill("Local", backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f))
        MetaPill(
            accessMode.label,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
        )
        gitState.branches.currentBranch?.takeIf(String::isNotBlank)?.let { branch ->
            MetaPill(branch, backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f))
        }
        if (selectedBaseBranch.isNotBlank()) {
            MetaPill(
                label = "vs $selectedBaseBranch",
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(28.dp),
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
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
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
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }

            composer.attachmentLimitMessage?.let { limitMessage ->
                Text(
                    text = limitMessage,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            composer.composerMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f),
                        shape = CircleShape,
                    ) {
                        IconButton(onClick = { plusMenuExpanded = true }) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = plusMenuExpanded,
                        onDismissRequest = { plusMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
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
                        DropdownMenuItem(
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
                        DropdownMenuItem(
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
                    onSelectSpeed = onSelectServiceTier,
                )
                if (composer.runtimeConfig.planningMode == RemodexPlanningMode.PLAN) {
                    MetaPill(
                        label = "Plan",
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.74f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (composer.canStop) {
                    FilledTonalButton(
                        onClick = onStopTurn,
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Stop",
                        )
                    }
                }
                Button(
                    onClick = onSendPrompt,
                    enabled = composer.canSend,
                    shape = CircleShape,
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowUp,
                            contentDescription = composer.sendLabel,
                        )
                        if (queuedCount > 0) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-8).dp),
                                shape = CircleShape,
                                color = if (uiState.selectedThread?.isRunning == true) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ) {
                                Text(
                                    text = queuedCount.toString(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        shape = RoundedCornerShape(999.dp),
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
            )
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
) {
    val autocomplete = uiState.composer.autocomplete
    if (autocomplete.panel == RemodexComposerAutocompletePanel.NONE) {
        return
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (autocomplete.panel) {
                    RemodexComposerAutocompletePanel.FILES -> "Files"
                    RemodexComposerAutocompletePanel.SKILLS -> "Skills"
                    RemodexComposerAutocompletePanel.COMMANDS -> "Commands"
                    RemodexComposerAutocompletePanel.REVIEW_TARGETS -> "Code Review"
                    RemodexComposerAutocompletePanel.FORK_DESTINATIONS -> "Fork"
                    RemodexComposerAutocompletePanel.NONE -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (autocomplete.panel) {
                RemodexComposerAutocompletePanel.FILES -> {
                    if (autocomplete.isFileLoading) {
                        Text("Searching files for @${autocomplete.fileQuery}...")
                    }
                    autocomplete.fileItems.forEach { item ->
                        SuggestionRow(
                            title = item.fileName,
                            subtitle = item.path,
                            onClick = { onSelectFileAutocomplete(item) },
                        )
                    }
                    if (!autocomplete.isFileLoading && autocomplete.fileItems.isEmpty()) {
                        Text(
                            text = "No files found for @${autocomplete.fileQuery}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                RemodexComposerAutocompletePanel.SKILLS -> {
                    if (autocomplete.isSkillLoading) {
                        Text("Searching skills for \$${autocomplete.skillQuery}...")
                    }
                    autocomplete.skillItems.forEach { skill ->
                        SuggestionRow(
                            title = skill.name,
                            subtitle = skill.description ?: skill.path.orEmpty(),
                            onClick = { onSelectSkillAutocomplete(skill) },
                        )
                    }
                    if (!autocomplete.isSkillLoading && autocomplete.skillItems.isEmpty()) {
                        Text(
                            text = "No skills found for \$${autocomplete.skillQuery}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                RemodexComposerAutocompletePanel.COMMANDS -> {
                    autocomplete.slashCommands.forEach { command ->
                        SuggestionRow(
                            title = command.title,
                            subtitle = command.subtitle,
                            trailingLabel = command.token,
                            onClick = { onSelectSlashCommand(command) },
                        )
                    }
                    if (autocomplete.slashCommands.isEmpty()) {
                        Text(
                            text = "No commands available for /${autocomplete.slashQuery}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                RemodexComposerAutocompletePanel.REVIEW_TARGETS -> {
                    autocomplete.reviewTargets.forEach { target ->
                        SuggestionRow(
                            title = target.title,
                            subtitle = when (target) {
                                RemodexComposerReviewTarget.UNCOMMITTED_CHANGES -> "Review everything currently modified in the repo."
                                RemodexComposerReviewTarget.BASE_BRANCH -> {
                                    val branch = autocomplete.selectedGitBaseBranch.ifBlank {
                                        autocomplete.gitDefaultBranch.ifBlank { "main" }
                                    }
                                    "Review against $branch."
                                }
                            },
                            onClick = { onSelectCodeReviewTarget(target) },
                        )
                    }
                    if (autocomplete.hasComposerContentConflictingWithReview) {
                        Text(
                            text = "Clear text, files, skills, and images before starting a code review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                RemodexComposerAutocompletePanel.FORK_DESTINATIONS -> {
                    autocomplete.forkDestinations.forEach { destination ->
                        SuggestionRow(
                            title = destination.title,
                            subtitle = destination.subtitle,
                            onClick = { onForkThread(destination) },
                        )
                    }
                }

                RemodexComposerAutocompletePanel.NONE -> Unit
            }

            Text(
                text = "Tap outside the composer to close",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    title: String,
    subtitle: String,
    trailingLabel: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingLabel?.takeIf(String::isNotBlank)?.let { label ->
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachmentPreviewCard(
    attachment: RemodexComposerAttachment,
    onRemoveAttachment: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.width(140.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f),
        shape = RoundedCornerShape(18.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onRemoveAttachment(attachment.id) }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove attachment",
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
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(999.dp),
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
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
            shape = RoundedCornerShape(999.dp),
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = { expanded = true })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                leadingIcon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RuntimeSelectorChevron()
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            onClear?.let {
                DropdownMenuItem(
                    text = { Text("Auto") },
                    onClick = {
                        expanded = false
                        it()
                    },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
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
    onSelectSpeed: (RemodexServiceTier?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
            shape = RoundedCornerShape(999.dp),
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = { expanded = true })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RuntimeSelectorChevron()
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            RuntimeMenuSectionLabel("Reasoning")
            reasoningOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelectReasoning(option)
                    },
                )
            }

            HorizontalDivider()
            RuntimeMenuSectionLabel("Speed")
            DropdownMenuItem(
                text = { Text("Normal") },
                onClick = {
                    expanded = false
                    onSelectSpeed(null)
                },
                trailingIcon = if (selectedSpeed == null) {
                    {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
            )
            speedOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelectSpeed(option)
                    },
                    trailingIcon = if (selectedSpeed == option) {
                        {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
    Icon(
        imageVector = Icons.Outlined.ExpandMore,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RuntimeMenuSectionLabel(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    assistantRevertPresentation: RemodexAssistantRevertPresentation?,
    onTapAssistantRevert: (String) -> Unit,
) {
    when (item.speaker) {
        ConversationSpeaker.USER -> UserConversationRow(item = item)
        ConversationSpeaker.ASSISTANT -> AssistantConversationRow(
            item = item,
            assistantRevertPresentation = assistantRevertPresentation,
            onTapAssistantRevert = onTapAssistantRevert,
        )
        ConversationSpeaker.SYSTEM -> SystemConversationRow(item = item)
    }
}

@Composable
private fun UserConversationRow(item: RemodexConversationItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.82f),
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = highlightMentions(item.text),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        style = MaterialTheme.typography.bodyMedium,
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
    assistantRevertPresentation: RemodexAssistantRevertPresentation?,
    onTapAssistantRevert: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.92f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (item.text.isNotBlank()) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.isStreaming) {
            StreamingIndicator(label = "Streaming")
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
private fun SystemConversationRow(item: RemodexConversationItem) {
    when (item.kind) {
        ConversationItemKind.REASONING -> ThinkingConversationRow(item)
        ConversationItemKind.FILE_CHANGE -> SystemStatusRow(
            title = "File changes",
            item = item,
        )
        ConversationItemKind.COMMAND_EXECUTION -> SystemStatusRow(
            title = "Command execution",
            item = item,
        )
        ConversationItemKind.SUBAGENT_ACTION -> SubagentActionRow(item)
        ConversationItemKind.USER_INPUT_PROMPT -> StructuredUserInputRow(item.structuredUserInputRequest)
        ConversationItemKind.PLAN -> Unit
        ConversationItemKind.CHAT -> DefaultSystemRow(item)
    }
}

@Composable
private fun ThinkingConversationRow(item: RemodexConversationItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Thinking...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.text.isNotBlank()) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemStatusRow(
    title: String,
    item: RemodexConversationItem,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.text.isNotBlank()) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.isStreaming) {
                StreamingIndicator(label = "Running")
            }
        }
    }
}

@Composable
private fun SubagentActionRow(item: RemodexConversationItem) {
    val action = item.subagentAction
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.74f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Subagents",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = action?.summaryText ?: item.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            action?.agentRows?.forEach { row ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(14.dp),
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
                        )
                        row.fallbackStatus?.takeIf(String::isNotBlank)?.let { status ->
                            Text(
                                text = status.replace('_', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        row.fallbackMessage?.takeIf(String::isNotBlank)?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredUserInputRow(request: RemodexStructuredUserInputRequest?) {
    if (request == null) {
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
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
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            request.questions.forEach { question ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = question.header,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    question.options.forEach { option ->
                        Surface(
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = "${option.label}: ${option.description}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultSystemRow(item: RemodexConversationItem) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item.text.takeIf(String::isNotBlank)?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.supportingText?.takeIf(String::isNotBlank)?.let { supportingText ->
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageAttachmentStrip(
    attachments: List<RemodexConversationAttachment>,
    alignToEnd: Boolean,
) {
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
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f),
                    shape = RoundedCornerShape(18.dp),
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
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun StreamingIndicator(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = TextDecoration.None,
    )
}

private fun highlightMentions(text: String) = buildAnnotatedString {
    val mentionRegex = Regex("([@\\$][^\\s]+)")
    var cursor = 0
    mentionRegex.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        withStyle(
            SpanStyle(
                color = if (match.value.startsWith("@")) Color(0xFF2563EB) else Color(0xFF4F46E5),
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
    val actionColor = when (presentation.riskLevel) {
        RemodexAssistantRevertRiskLevel.SAFE -> MaterialTheme.colorScheme.primary
        RemodexAssistantRevertRiskLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        RemodexAssistantRevertRiskLevel.BLOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    )
                    Text(
                        text = "${affectedFiles.size} file(s) · +$totalAdditions · -$totalDeletions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    affectedFiles.forEach { path ->
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    sheetState.presentation.warningText?.takeIf(String::isNotBlank)?.let { warningText ->
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
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
                            )
                            if (sheetState.preview.stagedFiles.isNotEmpty()) {
                                Text(
                                    text = sheetState.preview.stagedFiles.joinToString(
                                        prefix = "Staged files: ",
                                        separator = ", ",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (sheetState.preview.unsupportedReasons.isNotEmpty()) {
                                sheetState.preview.unsupportedReasons.forEach { reason ->
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (sheetState.preview.conflicts.isNotEmpty()) {
                                sheetState.preview.conflicts.forEach { conflict ->
                                    Text(
                                        text = "${conflict.path}: ${conflict.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.error,
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
