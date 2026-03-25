package com.emanueledipietro.remodex.model

import kotlinx.serialization.Serializable

@Serializable
enum class RemodexMessageDeliveryState {
    PENDING,
    CONFIRMED,
    FAILED,
}

@Serializable
data class RemodexConversationAttachment(
    val id: String,
    val uriString: String,
    val displayName: String,
)

@Serializable
enum class RemodexPlanStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ;

    val label: String
        get() = when (this) {
            PENDING -> "Pending"
            IN_PROGRESS -> "In progress"
            COMPLETED -> "Completed"
        }
}

@Serializable
data class RemodexPlanStep(
    val id: String,
    val step: String,
    val status: RemodexPlanStepStatus,
)

@Serializable
data class RemodexPlanState(
    val explanation: String? = null,
    val steps: List<RemodexPlanStep> = emptyList(),
)

@Serializable
data class RemodexStructuredUserInputOption(
    val id: String,
    val label: String,
    val description: String,
)

@Serializable
data class RemodexStructuredUserInputQuestion(
    val id: String,
    val header: String,
    val question: String,
    val isOther: Boolean = false,
    val isSecret: Boolean = false,
    val options: List<RemodexStructuredUserInputOption> = emptyList(),
)

@Serializable
data class RemodexStructuredUserInputRequest(
    val requestId: String,
    val questions: List<RemodexStructuredUserInputQuestion>,
)

@Serializable
data class RemodexSubagentRef(
    val threadId: String,
    val agentId: String? = null,
    val nickname: String? = null,
    val role: String? = null,
    val model: String? = null,
    val prompt: String? = null,
)

@Serializable
data class RemodexSubagentState(
    val threadId: String,
    val status: String,
    val message: String? = null,
)

@Serializable
data class RemodexSubagentAction(
    val tool: String,
    val status: String,
    val prompt: String? = null,
    val model: String? = null,
    val receiverThreadIds: List<String> = emptyList(),
    val receiverAgents: List<RemodexSubagentRef> = emptyList(),
    val agentStates: Map<String, RemodexSubagentState> = emptyMap(),
) {
    val normalizedTool: String
        get() = tool.trim()
            .lowercase()
            .replace("_", "")
            .replace("-", "")

    val normalizedStatus: String
        get() = status.trim()
            .lowercase()
            .replace("_", "")
            .replace("-", "")

    val summaryText: String
        get() {
            val count = maxOf(1, maxOf(agentRows.size, receiverThreadIds.size, receiverAgents.size))
            val noun = if (count == 1) "agent" else "agents"
            return when (normalizedTool) {
                "spawnagent" -> "Spawning $count $noun"
                "wait", "waitagent" -> "Waiting on $count $noun"
                "closeagent" -> "Closing $count $noun"
                "resumeagent" -> "Resuming $count $noun"
                "sendinput" -> if (count == 1) "Updating agent" else "Updating agents"
                else -> if (count == 1) "Agent activity" else "Agent activity ($count)"
            }
        }

    val agentRows: List<RemodexSubagentThreadPresentation>
        get() {
            val orderedThreadIds = buildList {
                receiverThreadIds.forEach { threadId ->
                    if (threadId.isNotBlank() && !contains(threadId)) {
                        add(threadId)
                    }
                }
                receiverAgents.forEach { agent ->
                    if (agent.threadId.isNotBlank() && !contains(agent.threadId)) {
                        add(agent.threadId)
                    }
                }
                agentStates.keys.sorted().forEach { threadId ->
                    if (threadId.isNotBlank() && !contains(threadId)) {
                        add(threadId)
                    }
                }
            }
            return orderedThreadIds.map { threadId ->
                val matchingAgent = receiverAgents.firstOrNull { agent -> agent.threadId == threadId }
                val state = agentStates[threadId]
                RemodexSubagentThreadPresentation(
                    threadId = threadId,
                    agentId = matchingAgent?.agentId,
                    nickname = matchingAgent?.nickname,
                    role = matchingAgent?.role,
                    model = matchingAgent?.model ?: model,
                    prompt = matchingAgent?.prompt,
                    fallbackStatus = state?.status,
                    fallbackMessage = state?.message,
                )
            }
        }
}

@Serializable
data class RemodexSubagentThreadPresentation(
    val threadId: String,
    val agentId: String? = null,
    val nickname: String? = null,
    val role: String? = null,
    val model: String? = null,
    val prompt: String? = null,
    val fallbackStatus: String? = null,
    val fallbackMessage: String? = null,
) {
    val displayLabel: String
        get() {
            val normalizedNickname = sanitizeIdentity(nickname)
            val normalizedRole = sanitizeIdentity(role)
            return when {
                normalizedNickname != null && normalizedRole != null -> "$normalizedNickname [$normalizedRole]"
                normalizedNickname != null -> normalizedNickname
                normalizedRole != null -> normalizedRole.replaceFirstChar(Char::titlecase)
                threadId.length > 14 -> "Agent ${threadId.takeLast(8)}"
                threadId.isNotBlank() -> threadId
                else -> "Agent"
            }
        }

    private fun sanitizeIdentity(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }
        return when (trimmed.lowercase()) {
            "collabagenttoolcall", "collabtoolcall" -> null
            else -> trimmed
        }
    }
}
