package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.model.RemodexGitBranches
import com.emanueledipietro.remodex.model.RemodexGitRepoSync
import com.emanueledipietro.remodex.model.RemodexGitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationScreenGitBranchPickerTest {
    @Test
    fun `normalized created branch name adds remodex prefix once`() {
        assertEquals("remodex/feature-a", remodexNormalizedCreatedBranchName("feature-a"))
        assertEquals("remodex/feature-a", remodexNormalizedCreatedBranchName("remodex/feature-a"))
        assertEquals("", remodexNormalizedCreatedBranchName("   "))
    }

    @Test
    fun `base branch picker disables current branch`() {
        assertTrue(
            remodexCurrentBranchSelectionIsDisabled(
                branch = "main",
                currentBranch = "main",
                allowsSelectingCurrentBranch = false,
            ),
        )
        assertFalse(
            remodexCurrentBranchSelectionIsDisabled(
                branch = "feature/test",
                currentBranch = "main",
                allowsSelectingCurrentBranch = false,
            ),
        )
        assertFalse(
            remodexCurrentBranchSelectionIsDisabled(
                branch = "main",
                currentBranch = "main",
                allowsSelectingCurrentBranch = true,
            ),
        )
    }

    @Test
    fun `current branch picker keeps checked out elsewhere rows selectable when worktree path is missing`() {
        assertFalse(
            remodexCurrentBranchSelectionIsDisabled(
                branch = "remodex/feature-a",
                currentBranch = "main",
                branchesCheckedOutElsewhere = setOf("remodex/feature-a"),
                worktreePathByBranch = emptyMap(),
                allowsSelectingCurrentBranch = true,
            ),
        )
    }

    @Test
    fun `current branch picker keeps checked out elsewhere rows selectable when worktree path is blank`() {
        assertFalse(
            remodexCurrentBranchSelectionIsDisabled(
                branch = "remodex/feature-a",
                currentBranch = "main",
                branchesCheckedOutElsewhere = setOf("remodex/feature-a"),
                worktreePathByBranch = mapOf("remodex/feature-a" to "   "),
                allowsSelectingCurrentBranch = true,
            ),
        )
    }

    @Test
    fun `current branch picker keeps checked out elsewhere rows enabled when worktree path exists`() {
        assertFalse(
            remodexCurrentBranchSelectionIsDisabled(
                branch = "remodex/feature-a",
                currentBranch = "main",
                branchesCheckedOutElsewhere = setOf("remodex/feature-a"),
                worktreePathByBranch = mapOf("remodex/feature-a" to "/tmp/remodex-feature-a"),
                allowsSelectingCurrentBranch = true,
            ),
        )
    }

    @Test
    fun `git controls only show while connected and git context exists`() {
        val gitState = RemodexGitState(
            sync = RemodexGitRepoSync(currentBranch = "main"),
            branches = RemodexGitBranches(branches = listOf("main")),
        )

        assertTrue(remodexShowsGitControls(isConnected = true, gitState = gitState))
        assertFalse(remodexShowsGitControls(isConnected = false, gitState = gitState))
        assertFalse(remodexShowsGitControls(isConnected = true, gitState = RemodexGitState()))
    }

    @Test
    fun `git ui actions are unavailable while loading running or creating worktree`() {
        val gitState = RemodexGitState(
            sync = RemodexGitRepoSync(currentBranch = "main"),
            branches = RemodexGitBranches(branches = listOf("main")),
        )

        assertTrue(
            remodexGitUiActionIsAvailable(
                isConnected = true,
                gitState = gitState,
                isThreadRunning = false,
                isCreatingGitWorktree = false,
            ),
        )
        assertFalse(
            remodexGitUiActionIsAvailable(
                isConnected = true,
                gitState = gitState.copy(isLoading = true),
                isThreadRunning = false,
                isCreatingGitWorktree = false,
            ),
        )
        assertFalse(
            remodexGitUiActionIsAvailable(
                isConnected = true,
                gitState = gitState,
                isThreadRunning = true,
                isCreatingGitWorktree = false,
            ),
        )
        assertFalse(
            remodexGitUiActionIsAvailable(
                isConnected = true,
                gitState = gitState,
                isThreadRunning = false,
                isCreatingGitWorktree = true,
            ),
        )
    }

    @Test
    fun `worktree handoff entry stays hidden for existing worktree projects`() {
        val gitState = RemodexGitState(
            sync = RemodexGitRepoSync(currentBranch = "main"),
            branches = RemodexGitBranches(branches = listOf("main")),
        )

        assertTrue(
            remodexWorktreeHandoffEntryIsAvailable(
                isConnected = true,
                gitState = gitState,
                isThreadRunning = false,
                isWorktreeProject = false,
                isCreatingGitWorktree = false,
            ),
        )
        assertFalse(
            remodexWorktreeHandoffEntryIsAvailable(
                isConnected = true,
                gitState = gitState,
                isThreadRunning = false,
                isWorktreeProject = true,
                isCreatingGitWorktree = false,
            ),
        )
    }


    @Test
    fun `base branch picker allows selecting current default branch`() {
        assertFalse(
            remodexCurrentBranchSelectionIsDisabled(
                branch = "main",
                currentBranch = "main",
                allowsSelectingCurrentBranch = true,
            ),
        )
    }

    @Test
    fun `ordered branches prioritize selected branch when search is empty`() {
        val ordered = gitBranchPickerOrderedBranches(
            branches = listOf("feature/a", "feature/b", "feature/c"),
            selectedBranch = "feature/b",
            defaultBranch = "main",
            searchQuery = "",
        )

        assertEquals(listOf("feature/b", "feature/a", "feature/c"), ordered)
    }

    @Test
    fun `ordered branches filter by search without reprioritizing`() {
        val ordered = gitBranchPickerOrderedBranches(
            branches = listOf("feature/a", "feature/b", "bugfix/c"),
            selectedBranch = "feature/b",
            defaultBranch = "main",
            searchQuery = "fix",
        )

        assertEquals(listOf("bugfix/c"), ordered)
    }

    @Test
    fun `suggested create branch name hides existing branch names`() {
        assertEquals(
            "remodex/new-idea",
            gitBranchPickerSuggestedCreateBranchName(
                searchQuery = "new-idea",
                branches = listOf("feature/a"),
                defaultBranch = "main",
                allowsSelectingCurrentBranch = true,
            ),
        )
        assertEquals(
            "remodex/feature/a",
            gitBranchPickerSuggestedCreateBranchName(
                searchQuery = "feature/a",
                branches = listOf("feature/a"),
                defaultBranch = "main",
                allowsSelectingCurrentBranch = true,
            ),
        )
        assertNull(
            gitBranchPickerSuggestedCreateBranchName(
                searchQuery = "remodex/feature/a",
                branches = listOf("feature/a", "remodex/feature/a"),
                defaultBranch = "main",
                allowsSelectingCurrentBranch = true,
            ),
        )
        assertNull(
            gitBranchPickerSuggestedCreateBranchName(
                searchQuery = "new-idea",
                branches = listOf("feature/a"),
                defaultBranch = "main",
                allowsSelectingCurrentBranch = false,
            ),
        )
    }
}
