package com.emanueledipietro.remodex.model

enum class RemodexThreadSyncState {
    LIVE,
    ARCHIVED_LOCAL,
}

data class RemodexThreadSummary(
    val id: String,
    val title: String,
    val name: String? = null,
    val preview: String,
    val projectPath: String,
    val lastUpdatedLabel: String,
    val isRunning: Boolean,
    val isWaitingOnApproval: Boolean = false,
    val syncState: RemodexThreadSyncState = RemodexThreadSyncState.LIVE,
    val parentThreadId: String? = null,
    val agentNickname: String? = null,
    val agentRole: String? = null,
    val activeTurnId: String? = null,
    val latestTurnTerminalState: RemodexTurnTerminalState? = null,
    val stoppedTurnIds: Set<String> = emptySet(),
    val queuedDrafts: Int,
    val queuedDraftItems: List<RemodexQueuedDraft> = emptyList(),
    val runtimeLabel: String,
    val runtimeConfig: RemodexRuntimeConfig = RemodexRuntimeConfig(),
    val messages: List<RemodexConversationItem>,
) {
    companion object {
        const val defaultDisplayTitle: String = "New Thread"

        fun isGenericPlaceholderTitle(value: String?): Boolean {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                return false
            }
            return trimmed.equals("Conversation", ignoreCase = true) ||
                trimmed.equals(defaultDisplayTitle, ignoreCase = true)
        }
    }

    val isSubagent: Boolean
        get() = !parentThreadId.isNullOrBlank()

    val displayTitle: String
        get() {
            val cleanedTitle = title.trim().takeIf { it.isNotEmpty() }
            val cleanedName = name?.trim()?.takeIf { it.isNotEmpty() }
            val cleanedPreview = preview.trim().takeIf { it.isNotEmpty() }
            val cleanedAgentLabel = agentDisplayLabel
            val effectiveTitle = if (isGenericPlaceholderTitle(cleanedTitle)) {
                null
            } else {
                cleanedTitle
            }

            if (!cleanedName.isNullOrBlank()) {
                return cleanedName
            }

            if (!cleanedAgentLabel.isNullOrBlank() && cleanedTitle == null) {
                return cleanedAgentLabel
            }

            if (!cleanedAgentLabel.isNullOrBlank() && isGenericPlaceholderTitle(cleanedTitle)) {
                return cleanedAgentLabel
            }

            if (!effectiveTitle.isNullOrBlank()) {
                return effectiveTitle
            }

            if (!cleanedPreview.isNullOrBlank()) {
                return cleanedPreview.replaceFirstChar { character ->
                    if (character.isLowerCase()) {
                        character.titlecase()
                    } else {
                        character.toString()
                    }
                }
            }

            return defaultDisplayTitle
        }

    private val agentDisplayLabel: String?
        get() {
            val cleanedNickname = agentNickname?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val cleanedRole = agentRole?.trim()?.takeIf { it.isNotEmpty() }
            return buildString {
                append(cleanedNickname)
                if (cleanedRole != null) {
                    append(" [")
                    append(cleanedRole)
                    append(']')
                }
            }
        }
}
