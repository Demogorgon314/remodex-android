package com.emanueledipietro.remodex.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConversationSpeaker {
    USER,
    ASSISTANT,
    SYSTEM,
}

@Serializable
enum class ConversationItemKind {
    CHAT,
    REASONING,
    TOOL_ACTIVITY,
    FILE_CHANGE,
    COMMAND_EXECUTION,
    SUBAGENT_ACTION,
    PLAN,
    USER_INPUT_PROMPT,
}

@Serializable
data class RemodexConversationItem(
    val id: String,
    val speaker: ConversationSpeaker,
    val kind: ConversationItemKind = ConversationItemKind.CHAT,
    val text: String,
    val supportingText: String? = null,
    val turnId: String? = null,
    val itemId: String? = null,
    val isStreaming: Boolean = false,
    val deliveryState: RemodexMessageDeliveryState = RemodexMessageDeliveryState.CONFIRMED,
    val attachments: List<RemodexConversationAttachment> = emptyList(),
    val planState: RemodexPlanState? = null,
    val subagentAction: RemodexSubagentAction? = null,
    val structuredUserInputRequest: RemodexStructuredUserInputRequest? = null,
    val orderIndex: Long = 0L,
    val assistantChangeSet: RemodexAssistantChangeSet? = null,
)
