package com.emanueledipietro.remodex.feature.threads

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.model.RemodexConnectionPhase
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation
import com.emanueledipietro.remodex.ui.theme.RemodexTheme
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
