package com.emanueledipietro.remodex.model

data class RemodexGitDiffTotals(
    val additions: Int = 0,
    val deletions: Int = 0,
    val binaryFiles: Int = 0,
) {
    val hasChanges: Boolean
        get() = additions > 0 || deletions > 0 || binaryFiles > 0
}

data class RemodexGitChangedFile(
    val path: String,
    val status: String,
)

data class RemodexGitRepoSync(
    val repoRoot: String? = null,
    val currentBranch: String? = null,
    val trackingBranch: String? = null,
    val isDirty: Boolean = false,
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
    val localOnlyCommitCount: Int = 0,
    val state: String = "up_to_date",
    val canPush: Boolean = false,
    val isPublishedToRemote: Boolean = false,
    val files: List<RemodexGitChangedFile> = emptyList(),
    val diffTotals: RemodexGitDiffTotals? = null,
)

data class RemodexGitRepoDiff(
    val patch: String = "",
)

data class RemodexGitCommit(
    val sha: String,
    val message: String,
    val author: String = "",
    val date: String = "",
) {
    val title: String
        get() = message.trim().ifEmpty { sha }
}

data class RemodexGitBranches(
    val branches: List<String> = emptyList(),
    val branchesCheckedOutElsewhere: Set<String> = emptySet(),
    val worktreePathByBranch: Map<String, String> = emptyMap(),
    val localCheckoutPath: String? = null,
    val currentBranch: String? = null,
    val defaultBranch: String? = null,
)

data class RemodexGitWorktreeResult(
    val branch: String,
    val worktreePath: String,
    val alreadyExisted: Boolean,
)

data class RemodexGitManagedWorktreeResult(
    val worktreePath: String,
    val alreadyExisted: Boolean,
    val baseBranch: String,
    val headMode: String,
    val transferredChanges: Boolean,
)

enum class RemodexGitWorktreeChangeTransferMode {
    MOVE,
    COPY,
    NONE,
}

data class RemodexGitRemoteUrl(
    val url: String = "",
    val ownerRepo: String? = null,
)

data class RemodexGitState(
    val sync: RemodexGitRepoSync? = null,
    val branches: RemodexGitBranches = RemodexGitBranches(),
    val isLoading: Boolean = false,
    val lastActionMessage: String? = null,
    val errorMessage: String? = null,
) {
    val hasContext: Boolean
        get() = sync != null || branches.branches.isNotEmpty()
}
