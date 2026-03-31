package com.emanueledipietro.remodex.model

data class RemodexFuzzyFileMatch(
    val root: String,
    val path: String,
    val fileName: String,
    val score: Double,
    val indices: List<Int> = emptyList(),
) {
    val id: String
        get() = "$root|$path"
}

data class RemodexSkillMetadata(
    val name: String,
    val description: String? = null,
    val path: String? = null,
    val scope: String? = null,
    val enabled: Boolean = true,
) {
    val id: String
        get() = name.trim().lowercase()
}

data class RemodexComposerMentionedFile(
    val id: String,
    val fileName: String,
    val path: String,
)

data class RemodexComposerMentionedSkill(
    val id: String,
    val name: String,
    val path: String? = null,
    val description: String? = null,
)

enum class RemodexComposerReviewTarget {
    UNCOMMITTED_CHANGES,
    BASE_BRANCH,
    ;

    val title: String
        get() = when (this) {
            UNCOMMITTED_CHANGES -> "Uncommitted changes"
            BASE_BRANCH -> "Base branch"
        }
}

data class RemodexComposerReviewSelection(
    val target: RemodexComposerReviewTarget?,
)

enum class RemodexComposerForkDestination {
    LOCAL,
    NEW_WORKTREE,
    ;

    val title: String
        get() = when (this) {
            LOCAL -> "Fork into local"
            NEW_WORKTREE -> "Fork into new worktree"
        }

    val subtitle: String
        get() = when (this) {
            LOCAL -> "Continue in a new local thread"
            NEW_WORKTREE -> "Continue in a new worktree"
        }

    val symbolName: String
        get() = when (this) {
            LOCAL -> "laptopcomputer"
            NEW_WORKTREE -> "arrow.triangle.branch"
        }
}

enum class RemodexSlashCommand(
    val title: String,
    val subtitle: String,
    val token: String,
    val cannedPrompt: String? = null,
    val isImplemented: Boolean = true,
) {
    CODE_REVIEW(
        title = "Code Review",
        subtitle = "Run the reviewer on your local changes",
        token = "/review",
    ),
    FORK(
        title = "Fork",
        subtitle = "Fork this thread into local or a new worktree",
        token = "/fork",
    ),
    STATUS(
        title = "Status",
        subtitle = "Show context usage and rate limits",
        token = "/status",
    ),
    COMPACT(
        title = "Compact",
        subtitle = "Compress the current thread context window",
        token = "/compact",
    ),
    PLAN(
        title = "Plan Mode",
        subtitle = "Toggle plan mode for this thread",
        token = "/plan",
    ),
    SUBAGENTS(
        title = "Subagents",
        subtitle = "Insert a canned prompt that asks Codex to delegate work",
        token = "/subagents",
        cannedPrompt = "Run subagents for different tasks. Delegate distinct work in parallel when helpful and then synthesize the results.",
    ),
    ;

    val symbolName: String
        get() = when (this) {
            CODE_REVIEW -> "ladybug"
            FORK -> "arrow.triangle.branch"
            STATUS -> "speedometer"
            COMPACT -> "arrow.down.left.and.arrow.up.right"
            PLAN -> "checklist"
            SUBAGENTS -> "person.crop.circle"
        }

    companion object {
        val allCommands: List<RemodexSlashCommand> = listOf(
            CODE_REVIEW,
            FORK,
            STATUS,
            COMPACT,
            PLAN,
            SUBAGENTS,
        )

        fun filtered(query: String): List<RemodexSlashCommand> {
            val trimmedQuery = query.trim().lowercase()
            if (trimmedQuery.isEmpty()) {
                return allCommands
            }
            return allCommands.filter { command ->
                buildString {
                    append(command.title)
                    append(' ')
                    append(command.subtitle)
                    append(' ')
                    append(command.token)
                }.lowercase().contains(trimmedQuery)
            }
        }
    }
}

enum class RemodexComposerAutocompletePanel {
    NONE,
    FILES,
    SKILLS,
    COMMANDS,
    REVIEW_TARGETS,
    FORK_DESTINATIONS,
}

data class RemodexComposerAutocompleteState(
    val panel: RemodexComposerAutocompletePanel = RemodexComposerAutocompletePanel.NONE,
    val fileItems: List<RemodexFuzzyFileMatch> = emptyList(),
    val isFileLoading: Boolean = false,
    val fileQuery: String = "",
    val skillItems: List<RemodexSkillMetadata> = emptyList(),
    val isSkillLoading: Boolean = false,
    val skillQuery: String = "",
    val availableCommands: List<RemodexSlashCommand> = RemodexSlashCommand.allCommands,
    val slashCommands: List<RemodexSlashCommand> = emptyList(),
    val slashQuery: String = "",
    val reviewTargets: List<RemodexComposerReviewTarget> = emptyList(),
    val forkDestinations: List<RemodexComposerForkDestination> = emptyList(),
    val hasComposerContentConflictingWithReview: Boolean = false,
    val isThreadRunning: Boolean = false,
    val selectedPlanningMode: RemodexPlanningMode = RemodexPlanningMode.AUTO,
    val selectedGitBaseBranch: String = "",
    val gitDefaultBranch: String = "",
)
