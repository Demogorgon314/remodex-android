package com.emanueledipietro.remodex.feature.turn

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.feature.appshell.ComposerUiState
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexSubagentAction
import com.emanueledipietro.remodex.model.RemodexComposerAutocompletePanel
import com.emanueledipietro.remodex.model.RemodexComposerAutocompleteState
import com.emanueledipietro.remodex.model.RemodexSlashCommand
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.ui.theme.RemodexTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConversationScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun outsideTapDismissesComposerAutocomplete() {
        composeRule.setContent {
            RemodexTheme {
                var autocompleteState by remember {
                    mutableStateOf(
                        RemodexComposerAutocompleteState(
                            panel = RemodexComposerAutocompletePanel.COMMANDS,
                            slashCommands = listOf(RemodexSlashCommand.STATUS),
                        ),
                    )
                }

                ConversationScreen(
                    uiState = conversationUiState(autocompleteState),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {
                        autocompleteState = RemodexComposerAutocompleteState()
                    },
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(ComposerAutocompletePanelTag).assertCountEquals(1)
        composeRule.onNodeWithTag(ComposerAutocompleteDismissLayerTag).performClick()
        composeRule.onAllNodesWithTag(ComposerAutocompletePanelTag).assertCountEquals(0)
    }

    @Test
    fun systemBackDismissesComposerAutocompleteBeforeLeavingConversation() {
        composeRule.setContent {
            RemodexTheme {
                var autocompleteState by remember {
                    mutableStateOf(
                        RemodexComposerAutocompleteState(
                            panel = RemodexComposerAutocompletePanel.COMMANDS,
                            slashCommands = listOf(RemodexSlashCommand.STATUS),
                        ),
                    )
                }

                ConversationScreen(
                    uiState = conversationUiState(autocompleteState),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {
                        autocompleteState = RemodexComposerAutocompleteState()
                    },
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(ComposerAutocompletePanelTag).assertCountEquals(1)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(ComposerAutocompletePanelTag).assertCountEquals(0)
    }

    @Test
    fun emptyComposerKeepsSendButtonDisabled() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(RemodexComposerAutocompleteState()),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onNodeWithTag(ComposerSendButtonTag).assertIsNotEnabled()
    }

    @Test
    fun runningComposerShowsStopButtonAndForwardsClicks() {
        var stopClicks = 0

        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        isRunning = true,
                        canStop = true,
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = { stopClicks += 1 },
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onNodeWithTag(ComposerStopButtonTag).performClick()
        composeRule.runOnIdle {
            assertEquals(1, stopClicks)
        }
    }

    @Test
    fun selectingStatusSlashCommandOpensStatusSheet() {
        composeRule.setContent {
            RemodexTheme {
                var autocompleteState by remember {
                    mutableStateOf(
                        RemodexComposerAutocompleteState(
                            panel = RemodexComposerAutocompletePanel.COMMANDS,
                            slashCommands = listOf(RemodexSlashCommand.STATUS),
                        ),
                    )
                }

                ConversationScreen(
                    uiState = conversationUiState(autocompleteState),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {
                        autocompleteState = RemodexComposerAutocompleteState()
                    },
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {
                        autocompleteState = RemodexComposerAutocompleteState()
                    },
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onRefreshUsageStatus = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onNodeWithText("Status").performClick()
        composeRule.onAllNodesWithTag(ConversationStatusSheetTag).assertCountEquals(1)
    }

    @Test
    fun runningConversationShowsThinkingAccessoryOnLatestBlock() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        isRunning = true,
                        messages = listOf(
                            RemodexConversationItem(
                                id = "subagent-1",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.SUBAGENT_ACTION,
                                text = "Waiting on 1 agent",
                                turnId = "turn-1",
                                orderIndex = 1,
                                isStreaming = true,
                                subagentAction = RemodexSubagentAction(
                                    tool = "wait_agent",
                                    status = "running",
                                    receiverThreadIds = listOf("agent-thread-1"),
                                ),
                            ),
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(ConversationRunningIndicatorTag).assertCountEquals(1)
    }

    @Test
    fun emptyConversationShowsIosStyledWelcomeState() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(autocompleteState = RemodexComposerAutocompleteState()),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(ConversationWelcomeStateTag).assertCountEquals(1)
        composeRule.onAllNodesWithText("Hi! How can I help you?").assertCountEquals(1)
        composeRule.onAllNodesWithText("Chats are End-to-end encrypted").assertCountEquals(1)
        composeRule.onAllNodesWithTag(ConversationWelcomeLoadingTag).assertCountEquals(0)
    }

    @Test
    fun hydratingEmptyConversationShowsLoadingIndicatorInsteadOfLegacyEmptyState() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        isSelectedThreadHydrating = true,
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(ConversationWelcomeLoadingTag).assertCountEquals(1)
        composeRule.onAllNodesWithText("No messages yet").assertCountEquals(0)
    }

    @Test
    fun completedAssistantBlockShowsCopyAccessory() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        messages = listOf(
                            RemodexConversationItem(
                                id = "assistant-1",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "Assistant answer",
                                turnId = "turn-1",
                                orderIndex = 1,
                            ),
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(ConversationCopyButtonTag).assertCountEquals(1)
    }

    @Test
    fun longPressAssistantMessageShowsSelectTextAndCopy() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        isRunning = true,
                        messages = listOf(
                            RemodexConversationItem(
                                id = "assistant-1",
                                speaker = ConversationSpeaker.ASSISTANT,
                                kind = ConversationItemKind.CHAT,
                                text = "Assistant answer",
                                turnId = "turn-1",
                                orderIndex = 1,
                            ),
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onNodeWithText("Assistant answer", substring = true)
            .performTouchInput { longClick() }

        composeRule.onAllNodesWithText("Select Text").assertCountEquals(1)
        composeRule.onAllNodesWithText("Copy").assertCountEquals(1)
    }

    @Test
    fun longPressSystemMarkdownMessageShowsSelectTextAndCopy() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        messages = listOf(
                            RemodexConversationItem(
                                id = "system-1",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.CHAT,
                                text = "System note with **markdown** and `code`",
                                orderIndex = 1,
                            ),
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onNodeWithText("System note", substring = true)
            .performTouchInput { longClick() }

        composeRule.onAllNodesWithText("Select Text").assertCountEquals(1)
        composeRule.onAllNodesWithText("Copy").assertCountEquals(1)
    }

    @Test
    fun longPressCommandExecutionCardShowsSelectTextAndCopy() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        messages = listOf(
                            RemodexConversationItem(
                                id = "command-1",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "running npm test",
                                orderIndex = 1,
                            ),
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onNodeWithText("npm test", substring = true)
            .performTouchInput { longClick() }

        composeRule.onAllNodesWithText("Select Text").assertCountEquals(1)
        composeRule.onAllNodesWithText("Copy").assertCountEquals(1)
    }

    @Test
    fun longPressUserMessageShowsCopyWithoutSelectText() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        messages = listOf(
                            RemodexConversationItem(
                                id = "user-1",
                                speaker = ConversationSpeaker.USER,
                                kind = ConversationItemKind.CHAT,
                                text = "User question",
                                orderIndex = 1,
                            ),
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onSendQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onSelectGitBaseBranch = {},
                    onRefreshGitState = {},
                    onCheckoutGitBranch = {},
                    onCreateGitBranch = {},
                    onCreateGitWorktree = {},
                    onCommitGitChanges = {},
                    onPullGitChanges = {},
                    onPushGitChanges = {},
                    onDiscardRuntimeChangesAndSync = {},
                    onForkThread = {},
                    onOpenSubagentThread = {},
                    onHydrateSubagentThread = {},
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onNodeWithText("User question", substring = true)
            .performTouchInput { longClick() }

        composeRule.onAllNodesWithText("Copy").assertCountEquals(1)
        composeRule.onAllNodesWithText("Select Text").assertCountEquals(0)
    }

    private fun conversationUiState(
        autocompleteState: RemodexComposerAutocompleteState,
        isRunning: Boolean = false,
        canStop: Boolean = false,
        isSelectedThreadHydrating: Boolean = false,
        messages: List<RemodexConversationItem> = emptyList(),
    ): AppUiState {
        val thread = RemodexThreadSummary(
            id = "thread-1",
            title = "Conversation",
            preview = "",
            projectPath = "/tmp/project",
            lastUpdatedLabel = "Updated now",
            isRunning = isRunning,
            queuedDrafts = 0,
            runtimeLabel = "Auto",
            messages = messages,
        )
        return AppUiState(
            selectedThread = thread,
            threads = listOf(thread),
            isSelectedThreadHydrating = isSelectedThreadHydrating,
            composer = ComposerUiState(
                draftText = "",
                canStop = canStop,
                autocomplete = autocompleteState,
            ),
        )
    }
}
