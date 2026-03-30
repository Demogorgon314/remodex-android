package com.emanueledipietro.remodex.feature.threads

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation
import com.emanueledipietro.remodex.ui.theme.RemodexTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThreadsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun threadsScreenKeepsFooterVisibleAndShowsProjectCreateAction() {
        composeRule.setContent {
            RemodexTheme {
                ThreadsScreen(
                    uiState = AppUiState(
                        connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                        trustedMac = RemodexTrustedMacPresentation(
                            name = "Kai-Wang---MBP-lan.lan",
                            systemName = "Saved Mac",
                        ),
                        threads = List(24) { index ->
                            threadSummary(
                                id = "thread-$index",
                                title = "Conversation $index",
                                projectPath = "/tmp/project-$index",
                            )
                        },
                    ),
                    onSelectThread = {},
                    onRefreshThreads = {},
                    onRetryConnection = {},
                    onCreateThread = {},
                    onSetProjectGroupCollapsed = { _, _ -> },
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onUnarchiveThread = {},
                    onDeleteThread = {},
                    onArchiveProject = {},
                    onOpenSettings = {},
                    onSearchActiveChange = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("New conversation in project-0").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Project actions for project-0").assertCountEquals(0)
        composeRule.onNodeWithText("Connected to Mac").assertIsDisplayed()
        composeRule.onNodeWithText("Kai-Wang---MBP-lan.lan").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun newChatOpensIosStyleProjectPickerSheet() {
        composeRule.setContent {
            RemodexTheme {
                ThreadsScreen(
                    uiState = AppUiState(
                        connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                        threads = listOf(
                            threadSummary(
                                id = "thread-1",
                                title = "Conversation",
                                projectPath = "/tmp/DeepSpeed",
                            ),
                        ),
                    ),
                    onSelectThread = {},
                    onRefreshThreads = {},
                    onRetryConnection = {},
                    onCreateThread = {},
                    onSetProjectGroupCollapsed = { _, _ -> },
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onUnarchiveThread = {},
                    onDeleteThread = {},
                    onArchiveProject = {},
                    onOpenSettings = {},
                    onSearchActiveChange = {},
                )
            }
        }

        composeRule.onNodeWithTag("sidebar_new_chat_button").performClick()

        composeRule.onNodeWithText("Start new chat").assertIsDisplayed()
        composeRule.onNodeWithText("Choose a project for this chat.").assertIsDisplayed()
        composeRule.onNodeWithText("Local").assertIsDisplayed()
        composeRule.onNodeWithText("DeepSpeed").assertIsDisplayed()
        composeRule.onNodeWithText("Cloud").assertIsDisplayed()
    }

    @Test
    fun renameOpensSharedDialogAndCommitsLatestDraft() {
        var renamedThreadId: String? = null
        var renamedTitle: String? = null

        composeRule.setContent {
            RemodexTheme {
                ThreadsScreen(
                    uiState = AppUiState(
                        connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                        threads = listOf(
                            threadSummary(
                                id = "thread-1",
                                title = "Conversation 0",
                                projectPath = "/tmp/project-0",
                            ),
                        ),
                    ),
                    onSelectThread = {},
                    onRefreshThreads = {},
                    onRetryConnection = {},
                    onCreateThread = {},
                    onSetProjectGroupCollapsed = { _, _ -> },
                    onRenameThread = { threadId, name ->
                        renamedThreadId = threadId
                        renamedTitle = name
                    },
                    onArchiveThread = {},
                    onUnarchiveThread = {},
                    onDeleteThread = {},
                    onArchiveProject = {},
                    onOpenSettings = {},
                    onSearchActiveChange = {},
                )
            }
        }

        composeRule.onNodeWithText("Conversation 0").performTouchInput {
            longClick(center)
        }
        composeRule.onNodeWithText("Rename").performClick()

        composeRule.onNodeWithText("Rename Conversation").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar_rename_text_field").performTextClearance()
        composeRule.onNodeWithTag("sidebar_rename_text_field").performTextInput("Renamed thread")
        composeRule.onNodeWithText("Rename").performClick()

        composeRule.runOnIdle {
            assertEquals("thread-1", renamedThreadId)
            assertEquals("Renamed thread", renamedTitle)
        }
        composeRule.onAllNodesWithText("Rename Conversation").assertCountEquals(0)
    }

    @Test
    fun projectGroupCollapseRespectsPersistedCollapsedStateAfterRecreate() {
        val collapsedGroupIds = androidx.compose.runtime.mutableStateOf(emptySet<String>())

        fun render() {
            composeRule.setContent {
                RemodexTheme {
                    ThreadsScreen(
                        uiState = AppUiState(
                            connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.CONNECTED, attempt = 1),
                            collapsedProjectGroupIds = collapsedGroupIds.value,
                            threads = listOf(
                                threadSummary(
                                    id = "thread-1",
                                    title = "Conversation 0",
                                    projectPath = "/tmp/project-0",
                                ),
                            ),
                        ),
                        onSelectThread = {},
                        onRefreshThreads = {},
                        onRetryConnection = {},
                        onCreateThread = {},
                        onSetProjectGroupCollapsed = { groupId, collapsed ->
                            collapsedGroupIds.value = collapsedGroupIds.value.toMutableSet().apply {
                                if (collapsed) {
                                    add(groupId)
                                } else {
                                    remove(groupId)
                                }
                            }
                        },
                        onRenameThread = { _, _ -> },
                        onArchiveThread = {},
                        onUnarchiveThread = {},
                        onDeleteThread = {},
                        onArchiveProject = {},
                        onOpenSettings = {},
                        onSearchActiveChange = {},
                    )
                }
            }
        }

        render()

        composeRule.onNodeWithText("Conversation 0").assertIsDisplayed()
        composeRule.onNodeWithText("project-0").performClick()
        composeRule.onAllNodesWithText("Conversation 0").assertCountEquals(0)

        render()

        composeRule.onAllNodesWithText("Conversation 0").assertCountEquals(0)
    }

    @Test
    fun disconnectedSidebarKeepsThreadsVisibleAndDisablesCreateActions() {
        composeRule.setContent {
            RemodexTheme {
                ThreadsScreen(
                    uiState = AppUiState(
                        connectionStatus = RemodexConnectionStatus(RemodexConnectionPhase.DISCONNECTED, attempt = 1),
                        threads = listOf(
                            threadSummary(
                                id = "thread-1",
                                title = "Recovered conversation",
                                projectPath = "/tmp/project-0",
                            ),
                        ),
                    ),
                    onSelectThread = {},
                    onRefreshThreads = {},
                    onRetryConnection = {},
                    onCreateThread = {},
                    onSetProjectGroupCollapsed = { _, _ -> },
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onUnarchiveThread = {},
                    onDeleteThread = {},
                    onArchiveProject = {},
                    onOpenSettings = {},
                    onSearchActiveChange = {},
                )
            }
        }

        composeRule.onNodeWithText("Recovered conversation").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar_new_chat_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("sidebar_project_new_chat_button_project-0").assertIsNotEnabled()
    }

    private fun threadSummary(
        id: String,
        title: String,
        projectPath: String,
    ): RemodexThreadSummary {
        return RemodexThreadSummary(
            id = id,
            title = title,
            preview = "Preview",
            projectPath = projectPath,
            lastUpdatedLabel = "Updated 51m ago",
            isRunning = false,
            queuedDrafts = 0,
            runtimeLabel = "Auto",
            messages = emptyList(),
        )
    }
}
