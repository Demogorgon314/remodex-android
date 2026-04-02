package com.emanueledipietro.remodex.feature.turn

import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.core.content.FileProvider
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.feature.appshell.ComposerVoiceButtonMode
import com.emanueledipietro.remodex.feature.appshell.ComposerUiState
import com.emanueledipietro.remodex.feature.appshell.ComposerVoiceUiState
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexCommandExecutionLiveStatus
import com.emanueledipietro.remodex.model.RemodexCommandExecutionSource
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexSubagentAction
import com.emanueledipietro.remodex.model.RemodexComposerAutocompletePanel
import com.emanueledipietro.remodex.model.RemodexComposerAutocompleteState
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexPlanState
import com.emanueledipietro.remodex.model.RemodexPlanStep
import com.emanueledipietro.remodex.model.RemodexPlanStepStatus
import com.emanueledipietro.remodex.model.RemodexSlashCommand
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.ui.theme.RemodexTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
    fun composerShowsVoiceButton() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(RemodexComposerAutocompleteState()),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

        composeRule.onAllNodesWithTag(ComposerVoiceButtonTag).assertCountEquals(1)
    }

    @Test
    fun composerShowsReasoningTriggerIcon() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(RemodexComposerAutocompleteState()),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

        composeRule.onNodeWithTag(ComposerReasoningTriggerIconTag).assertIsDisplayed()
    }

    @Test
    fun tappingExpandedReasoningTriggerCollapsesMenu() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(RemodexComposerAutocompleteState()),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

        composeRule.onNodeWithTag(ComposerReasoningTriggerTag).performClick()
        composeRule.onNodeWithText("Speed").assertIsDisplayed()
        composeRule.onNodeWithTag(ComposerReasoningTriggerTag).performClick()
        composeRule.onAllNodesWithText("Speed").assertCountEquals(0)
    }

    @Test
    fun activeVoiceRecordingShowsCapsule() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        voice = ComposerVoiceUiState(
                            buttonMode = ComposerVoiceButtonMode.RECORDING,
                            isConnected = true,
                            audioLevels = List(24) { index -> (index % 6) / 5f },
                            durationSeconds = 12.4,
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

        composeRule.onNodeWithTag(ComposerVoiceRecordingCapsuleTag).assertIsDisplayed()
    }

    @Test
    fun runtimeMenuShowsContinueOnMacWhenConnected() {
        var continueClicks = 0

        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                    ).copy(
                        connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
                    onClearReviewSelection = {},
                    onClearSubagentsSelection = {},
                    onCloseComposerAutocomplete = {},
                    onRefreshUsageStatus = {},
                    onRequestContinueOnMac = { continueClicks += 1 },
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

        composeRule.onNodeWithText("Local").performClick()
        composeRule.onNodeWithText("Continue on Mac").assertIsDisplayed().performClick()

        assertEquals(1, continueClicks)
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
    fun activePlanAccessoryRefreshesWhenPlanProgressAdvances() {
        val uiState = mutableStateOf(
            conversationUiState(
                autocompleteState = RemodexComposerAutocompleteState(),
                messages = listOf(
                    planItem(
                        id = "plan-1",
                        isStreaming = true,
                        steps = listOf(
                            RemodexPlanStep(
                                id = "step-1",
                                step = "Audit current flow",
                                status = RemodexPlanStepStatus.IN_PROGRESS,
                            ),
                            RemodexPlanStep(
                                id = "step-2",
                                step = "Implement Android fix",
                                status = RemodexPlanStepStatus.PENDING,
                            ),
                        ),
                    ),
                ),
            ),
        )

        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = uiState.value,
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

        composeRule.onNodeWithText("Audit current flow").assertIsDisplayed()
        composeRule.onNodeWithText("0/2").assertIsDisplayed()

        composeRule.runOnIdle {
            uiState.value = conversationUiState(
                autocompleteState = RemodexComposerAutocompleteState(),
                messages = listOf(
                    planItem(
                        id = "plan-1",
                        isStreaming = true,
                        steps = listOf(
                            RemodexPlanStep(
                                id = "step-1",
                                step = "Audit current flow",
                                status = RemodexPlanStepStatus.COMPLETED,
                            ),
                            RemodexPlanStep(
                                id = "step-2",
                                step = "Implement Android fix",
                                status = RemodexPlanStepStatus.IN_PROGRESS,
                            ),
                        ),
                    ),
                ),
            )
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithText("Implement Android fix").assertIsDisplayed()
        composeRule.onNodeWithText("1/2").assertIsDisplayed()
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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
    fun backgroundTerminalTrayOpensSheetForRunningSessions() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        messages = listOf(
                            RemodexConversationItem(
                                id = "command-1",
                                itemId = "command-1",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "running bash -lc \"sleep 30\"",
                                orderIndex = 1,
                            ),
                        ),
                        commandExecutionDetailsByItemId = mapOf(
                            "command-1" to RemodexCommandExecutionDetails(
                                fullCommand = "bash -lc \"sleep 30\"",
                                outputTail = "tick 1\ntick 2\ntick 3",
                                liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                                source = RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP,
                            ),
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

        composeRule.onNodeWithTag(BackgroundTerminalTrayTag).assertIsDisplayed()
        composeRule.onNodeWithText("1 background terminal running").assertIsDisplayed()

        composeRule.onNodeWithTag(BackgroundTerminalTrayTag).performClick()

        composeRule.onNodeWithTag(BackgroundTerminalSheetTag).assertIsDisplayed()
        composeRule.onNodeWithText("Background terminals").assertIsDisplayed()
        composeRule.onNodeWithText("sleep 30", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Recent output").assertIsDisplayed()
    }

    @Test
    fun foregroundRunningCommandDoesNotShowBackgroundTerminalTray() {
        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(
                        autocompleteState = RemodexComposerAutocompleteState(),
                        messages = listOf(
                            RemodexConversationItem(
                                id = "command-foreground",
                                itemId = "command-foreground",
                                speaker = ConversationSpeaker.SYSTEM,
                                kind = ConversationItemKind.COMMAND_EXECUTION,
                                text = "running pwd",
                                orderIndex = 1,
                            ),
                        ),
                        commandExecutionDetailsByItemId = mapOf(
                            "command-foreground" to RemodexCommandExecutionDetails(
                                fullCommand = "pwd",
                                liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                                source = RemodexCommandExecutionSource.USER_SHELL,
                            ),
                        ),
                    ),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

        composeRule.onAllNodesWithTag(BackgroundTerminalTrayTag).assertCountEquals(0)
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
                    onRestoreLatestQueuedDraft = {},
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
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

    @Test
    fun clipboardImageReceiveForwardsUrisToComposerCallback() {
        assertComposerReceiveContentForSource(ContentInfoCompat.SOURCE_CLIPBOARD)
    }

    @Test
    fun inputMethodImageReceiveForwardsUrisToComposerCallback() {
        assertComposerReceiveContentForSource(ContentInfoCompat.SOURCE_INPUT_METHOD)
    }

    private fun conversationUiState(
        autocompleteState: RemodexComposerAutocompleteState,
        isRunning: Boolean = false,
        canStop: Boolean = false,
        isSelectedThreadHydrating: Boolean = false,
        messages: List<RemodexConversationItem> = emptyList(),
        voice: ComposerVoiceUiState = ComposerVoiceUiState(isConnected = true),
        commandExecutionDetailsByItemId: Map<String, RemodexCommandExecutionDetails> = emptyMap(),
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
                voice = voice,
                autocomplete = autocompleteState,
            ),
            commandExecutionDetailsByItemId = commandExecutionDetailsByItemId,
        )
    }

    private fun planItem(
        id: String,
        steps: List<RemodexPlanStep>,
        isStreaming: Boolean = false,
    ): RemodexConversationItem {
        return RemodexConversationItem(
            id = id,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.PLAN,
            text = "Plan update",
            isStreaming = isStreaming,
            planState = RemodexPlanState(
                explanation = "Keep the rollout moving safely.",
                steps = steps,
            ),
            orderIndex = 1,
        )
    }

    private fun assertComposerReceiveContentForSource(source: Int) {
        val receivedUriCalls = mutableListOf<List<Uri>>()
        val imageUri = createComposerImageUri()

        composeRule.setContent {
            RemodexTheme {
                ConversationScreen(
                    uiState = conversationUiState(RemodexComposerAutocompleteState()),
                    onRetryConnection = {},
                    onComposerInputChanged = {},
                    onSendPrompt = {},
                    onStopTurn = {},
                    onRestoreLatestQueuedDraft = {},
                    onSelectModel = {},
                    onSelectPlanningMode = {},
                    onSelectReasoningEffort = {},
                    onSelectAccessMode = {},
                    onSelectServiceTier = {},
                    onOpenAttachmentPicker = {},
                    onOpenCameraCapture = {},
                    onReceiveComposerAttachmentUris = { uris ->
                        receivedUriCalls += uris
                    },
                    onRemoveAttachment = {},
                    onSelectFileAutocomplete = {},
                    onRemoveMentionedFile = {},
                    onSelectSkillAutocomplete = {},
                    onRemoveMentionedSkill = {},
                    onSelectSlashCommand = {},
                    onSelectCodeReviewTarget = {},
                    onSelectCodeReviewBranch = {},
                    onSelectCodeReviewCommit = {},
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

        composeRule.onNodeWithTag(ComposerInputFieldTag).performClick()
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            val activity = composeRule.activity
            val composeRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            ViewCompat.performReceiveContent(
                composeRoot,
                ContentInfoCompat.Builder(
                    ClipData.newUri(activity.contentResolver, "Composer image", imageUri),
                    source,
                ).build(),
            )
        }
        composeRule.waitForIdle()

        assertEquals(listOf(listOf(imageUri)), receivedUriCalls)
    }

    private fun createComposerImageUri(): Uri {
        val activity = composeRule.activity
        val captureDirectory = File(activity.cacheDir, "composer-captures").apply {
            mkdirs()
        }
        val imageFile = File(captureDirectory, "conversation-screen-test-${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        }
        return FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            imageFile,
        )
    }
}
