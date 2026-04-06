package com.emanueledipietro.remodex.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

const val StructuredSecretAnswerPlaceholder = "Answered"

@Serializable
enum class RemodexMessageDeliveryState {
    PENDING,
    CONFIRMED,
    FAILED,
}

@Serializable
enum class RemodexTurnTerminalState {
    COMPLETED,
    FAILED,
    STOPPED,
}

@Serializable
data class RemodexConversationAttachment(
    val id: String,
    val uriString: String,
    val displayName: String,
    val previewDataUrl: String? = null,
) {
    val renderUriString: String
        get() = previewDataUrl
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: uriString
}

fun RemodexComposerAttachment.toConversationAttachment(): RemodexConversationAttachment {
    val previewDataUrl = payloadDataUrl
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: uriString
            .trim()
            .takeIf(::isInlineImageDataUrl)
    return RemodexConversationAttachment(
        id = id,
        uriString = uriString,
        displayName = displayName,
        previewDataUrl = previewDataUrl,
    )
}

fun androidUserMessageFallbackText(attachmentCount: Int): String {
    return when (attachmentCount) {
        0 -> "Sent a prompt from Android."
        1 -> "Shared 1 image from Android."
        else -> "Shared $attachmentCount images from Android."
    }
}

fun androidUserMessageText(
    prompt: String,
    @Suppress("UNUSED_PARAMETER")
    attachmentCount: Int,
): String {
    return prompt.trim()
}

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
    val requestId: JsonElement,
    val questions: List<RemodexStructuredUserInputQuestion>,
) {
    val requestIdKey: String
        get() = when (requestId) {
            JsonNull -> "null"
            is JsonPrimitive -> requestId.contentOrNull ?: requestId.toString()
            else -> requestId.toString()
        }.trim().ifEmpty { requestId.toString() }
}

@Serializable
data class RemodexStructuredUserInputAnswer(
    val answers: List<String> = emptyList(),
)

@Serializable
data class RemodexStructuredUserInputResponse(
    val answersByQuestionId: Map<String, RemodexStructuredUserInputAnswer> = emptyMap(),
) {
    fun answersFor(questionId: String): List<String> {
        return answersByQuestionId[questionId]?.answers.orEmpty()
    }
}

@Serializable
enum class RemodexApprovalKind {
    COMMAND,
    FILE_CHANGE,
    PERMISSIONS,
    UNKNOWN,
}

@Serializable
enum class RemodexPermissionGrantScope {
    TURN,
    SESSION,
    ;

    val wireValue: String
        get() = when (this) {
            TURN -> "turn"
            SESSION -> "session"
        }
}

@Serializable
data class RemodexRequestedPermissions(
    val networkEnabled: Boolean? = null,
    val readPaths: List<String> = emptyList(),
    val writePaths: List<String> = emptyList(),
)

@Serializable
data class RemodexApprovalRequest(
    val id: String,
    val requestId: JsonElement,
    val method: String,
    val command: String? = null,
    val reason: String? = null,
    val threadId: String? = null,
    val turnId: String? = null,
    val approvalId: String? = null,
    val cwd: String? = null,
    val requestedPermissions: RemodexRequestedPermissions? = null,
    val params: JsonElement? = null,
) {
    val kind: RemodexApprovalKind
        get() = when (method.trim()) {
            "item/commandExecution/requestApproval",
            "item/command_execution/request_approval" -> RemodexApprovalKind.COMMAND
            "item/fileChange/requestApproval",
            "item/file_change/request_approval" -> RemodexApprovalKind.FILE_CHANGE
            "item/permissions/requestApproval",
            "item/permissions/request_approval" -> RemodexApprovalKind.PERMISSIONS
            else -> RemodexApprovalKind.UNKNOWN
        }
}

fun remodexApprovalRequestMessage(request: RemodexApprovalRequest): String {
    val lines = buildList {
        when (request.kind) {
            RemodexApprovalKind.PERMISSIONS -> {
                request.reason
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let(::add)
                requestedPermissionsMessage(request.requestedPermissions)?.let(::add)
            }
            else -> {
                request.reason
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let(::add)
                request.command
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { command -> add("Command: $command") }
                request.cwd
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { cwd -> add("Working directory: $cwd") }
            }
        }
    }
    return if (lines.isEmpty()) {
        "Codex is requesting permission to continue."
    } else {
        lines.joinToString(separator = "\n\n")
    }
}

fun remodexApprovalRequestSummary(request: RemodexApprovalRequest): String {
    return when (request.kind) {
        RemodexApprovalKind.PERMISSIONS -> {
            request.reason
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: requestedPermissionsSummary(request.requestedPermissions)
                ?: "Codex is requesting additional permissions."
        }
        else -> {
            request.reason
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: request.command
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { command -> "Command: $command" }
                ?: "Codex is requesting permission to continue."
        }
    }
}

private fun requestedPermissionsMessage(requestedPermissions: RemodexRequestedPermissions?): String? {
    val permissions = requestedPermissions ?: return null
    val sections = buildList {
        permissions.networkEnabled?.let { enabled ->
            add("Network: ${if (enabled) "enabled" else "restricted"}")
        }
        if (permissions.readPaths.isNotEmpty()) {
            add("Read paths:\n${permissions.readPaths.joinToString(separator = "\n")}")
        }
        if (permissions.writePaths.isNotEmpty()) {
            add("Write paths:\n${permissions.writePaths.joinToString(separator = "\n")}")
        }
    }
    return sections.takeIf(List<String>::isNotEmpty)?.joinToString(separator = "\n\n")
}

private fun requestedPermissionsSummary(requestedPermissions: RemodexRequestedPermissions?): String? {
    val permissions = requestedPermissions ?: return null
    val parts = buildList {
        if (permissions.networkEnabled == true) {
            add("network")
        }
        if (permissions.readPaths.isNotEmpty()) {
            add("file read")
        }
        if (permissions.writePaths.isNotEmpty()) {
            add("file write")
        }
    }
    if (parts.isEmpty()) {
        return null
    }
    return "Requested permissions: ${parts.joinToString()}"
}

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
                    modelIsRequestedHint = matchingAgent?.model == null && model != null,
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
    val modelIsRequestedHint: Boolean = false,
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
