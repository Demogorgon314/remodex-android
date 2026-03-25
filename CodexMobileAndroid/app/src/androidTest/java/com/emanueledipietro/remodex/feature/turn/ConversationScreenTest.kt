package com.emanueledipietro.remodex.feature.turn

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onNodeWithTag(ComposerSendButtonTag).assertIsNotEnabled()
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
                    onStartAssistantRevertPreview = {},
                    onConfirmAssistantRevert = {},
                    onDismissAssistantRevertSheet = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(ConversationRunningIndicatorTag).assertCountEquals(1)
    }

    private fun conversationUiState(
        autocompleteState: RemodexComposerAutocompleteState,
        isRunning: Boolean = false,
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
            composer = ComposerUiState(
                draftText = "",
                autocomplete = autocompleteState,
            ),
        )
    }
}
