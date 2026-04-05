package com.emanueledipietro.remodex.feature.turn

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
    fun `current branch picker disables checked out elsewhere rows when worktree path is missing`() {
        assertTrue(
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
