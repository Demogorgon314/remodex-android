package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexCommandExecutionLiveStatus
import com.emanueledipietro.remodex.model.RemodexCommandExecutionSource
import com.emanueledipietro.remodex.model.RemodexConversationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandExecutionStatusPresentationTest {
    @Test
    fun `resolved command status falls back to completed once row stops streaming`() {
        val item = RemodexConversationItem(
            id = "command-1",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
            text = "running pwd",
            isStreaming = false,
            orderIndex = 1L,
        )

        val resolved = resolvedCommandExecutionStatusPresentations(
            item = item,
            details = RemodexCommandExecutionDetails(
                fullCommand = "pwd",
                durationMs = 120,
            ),
        ).single()

        assertEquals("completed", resolved.statusLabel)
        assertEquals(CommandExecutionStatusAccent.COMPLETED, resolved.accent)
        assertEquals("pwd", resolved.command)
    }

    @Test
    fun `resolved command status prefers failed when exit code is non zero`() {
        val item = RemodexConversationItem(
            id = "command-2",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
            text = "running ./gradlew test",
            isStreaming = false,
            orderIndex = 1L,
        )

        val resolved = resolvedCommandExecutionStatusPresentations(
            item = item,
            details = RemodexCommandExecutionDetails(
                fullCommand = "./gradlew test",
                exitCode = 1,
                durationMs = 4200,
            ),
        ).single()

        assertEquals("failed", resolved.statusLabel)
        assertEquals(CommandExecutionStatusAccent.FAILED, resolved.accent)
        assertEquals("./gradlew test", resolved.command)
    }

    @Test
    fun `resolved command status keeps running when background terminal is still active`() {
        val item = RemodexConversationItem(
            id = "command-3",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
            text = "running cargo test -p codex-core",
            isStreaming = false,
            orderIndex = 1L,
        )

        val resolved = resolvedCommandExecutionStatusPresentations(
            item = item,
            details = RemodexCommandExecutionDetails(
                fullCommand = "cargo test -p codex-core",
                liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
            ),
        ).single()

        assertEquals("running", resolved.statusLabel)
        assertEquals(CommandExecutionStatusAccent.RUNNING, resolved.accent)
    }

    @Test
    fun `background terminal presentations keep only running command executions`() {
        val runningItem = RemodexConversationItem(
            id = "message-running",
            itemId = "command-running",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
            text = "running sleep 30",
            orderIndex = 1L,
        )
        val foregroundItem = RemodexConversationItem(
            id = "message-foreground",
            itemId = "command-foreground",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
            text = "running pwd",
            orderIndex = 2L,
        )
        val completedItem = RemodexConversationItem(
            id = "message-completed",
            itemId = "command-completed",
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.COMMAND_EXECUTION,
            text = "completed pwd",
            orderIndex = 3L,
        )

        val sessions = resolveBackgroundTerminalPresentations(
            messages = listOf(runningItem, foregroundItem, completedItem),
            detailsByItemId = mapOf(
                "command-running" to RemodexCommandExecutionDetails(
                    fullCommand = "bash -lc \"sleep 30\"",
                    outputTail = "tick 1\ntick 2\ntick 3\ntick 4",
                    liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                    source = RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP,
                ),
                "command-foreground" to RemodexCommandExecutionDetails(
                    fullCommand = "pwd",
                    liveStatus = RemodexCommandExecutionLiveStatus.RUNNING,
                    source = RemodexCommandExecutionSource.USER_SHELL,
                ),
                "command-completed" to RemodexCommandExecutionDetails(
                    fullCommand = "pwd",
                    liveStatus = RemodexCommandExecutionLiveStatus.COMPLETED,
                    source = RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP,
                ),
            ),
        )

        assertEquals(1, sessions.size)
        assertEquals("command-running", sessions.single().itemId)
        assertTrue(sessions.single().commandPreview.contains("sleep 30"))
        assertEquals(listOf("tick 2", "tick 3", "tick 4"), sessions.single().recentOutputLines)
    }
}
