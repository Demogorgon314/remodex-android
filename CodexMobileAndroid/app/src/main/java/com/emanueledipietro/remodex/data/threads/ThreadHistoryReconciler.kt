package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.ConversationSystemTurnOrderingHint
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState
import com.emanueledipietro.remodex.model.androidUserMessageFallbackText

internal object ThreadHistoryReconciler {
    private const val MatchNotFound = -1
    private const val MatchSuppress = -2

    fun mergeHistoryItems(
        existing: List<RemodexConversationItem>,
        history: List<RemodexConversationItem>,
        threadIsActive: Boolean,
        threadIsRunning: Boolean,
    ): List<RemodexConversationItem> {
        if (history.isEmpty()) {
            return existing.sortedBy(RemodexConversationItem::orderIndex)
        }
        if (existing.isEmpty()) {
            return history.sortedBy(RemodexConversationItem::orderIndex)
        }

        val assistantHistoryCountByTurn = history
            .asSequence()
            .filter { item ->
                item.speaker == ConversationSpeaker.ASSISTANT
            }
            .mapNotNull { item -> normalizedIdentifier(item.turnId) }
            .groupingBy { it }
            .eachCount()
        val merged = existing.toMutableList()
        history.forEach { item ->
            val matchIndex = findMatchIndex(
                merged = merged,
                historyItem = item,
                threadIsActive = threadIsActive,
                threadIsRunning = threadIsRunning,
                assistantHistoryCountByTurn = assistantHistoryCountByTurn,
            )
            if (matchIndex == MatchSuppress) {
                return@forEach
            }
            if (matchIndex == MatchNotFound) {
                merged += item
            } else {
                merged[matchIndex] = reconcileExistingItem(
                    localItem = merged[matchIndex],
                    serverItem = item,
                    threadIsActive = threadIsActive,
                    threadIsRunning = threadIsRunning,
                )
            }
        }
        return merged.sortedBy(RemodexConversationItem::orderIndex)
    }

    fun mergeStreamingSnapshotText(
        existingText: String,
        incomingText: String,
    ): String {
        if (existingText.isEmpty()) {
            return incomingText
        }
        if (incomingText == existingText) {
            return existingText
        }
        if (existingText.endsWith(incomingText)) {
            return existingText
        }
        if (incomingText.length > existingText.length && incomingText.startsWith(existingText)) {
            return incomingText
        }
        if (existingText.length > incomingText.length && existingText.startsWith(incomingText)) {
            return existingText
        }
        val existingCompacted = compactStreamingText(existingText)
        val incomingCompacted = compactStreamingText(incomingText)
        if (existingCompacted == incomingCompacted) {
            return existingText
        }
        if (existingCompacted.contains(incomingCompacted)) {
            return existingText
        }
        if (incomingCompacted.startsWith(existingCompacted)) {
            return appendCompactedSuffix(
                existingText = existingText,
                incomingText = incomingText,
                matchedCompactedLength = existingCompacted.length,
            )
        }

        val maxOverlap = minOf(existingCompacted.length, incomingCompacted.length)
        for (overlap in maxOverlap downTo 1) {
            if (existingCompacted.takeLast(overlap) == incomingCompacted.take(overlap)) {
                return appendCompactedSuffix(
                    existingText = existingText,
                    incomingText = incomingText,
                    matchedCompactedLength = overlap,
                )
            }
        }
        return incomingText
    }

    private fun appendCompactedSuffix(
        existingText: String,
        incomingText: String,
        matchedCompactedLength: Int,
    ): String {
        if (matchedCompactedLength <= 0) {
            return existingText + incomingText
        }
        var consumed = 0
        var startIndex = incomingText.length
        for ((index, character) in incomingText.withIndex()) {
            if (!character.isWhitespace()) {
                consumed += 1
                if (consumed == matchedCompactedLength) {
                    startIndex = index + 1
                    break
                }
            }
        }
        if (startIndex >= incomingText.length) {
            return existingText
        }
        return existingText + incomingText.substring(startIndex)
    }

    private fun findMatchIndex(
        merged: List<RemodexConversationItem>,
        historyItem: RemodexConversationItem,
        threadIsActive: Boolean,
        threadIsRunning: Boolean,
        assistantHistoryCountByTurn: Map<String, Int>,
    ): Int {
        if (historyItem.speaker == ConversationSpeaker.ASSISTANT) {
            val turnId = normalizedIdentifier(historyItem.turnId)
            val incomingItemId = normalizedIdentifier(historyItem.itemId)
            if (turnId != null) {
                if (incomingItemId == null) {
                    merged.indexOfLast { candidate ->
                        candidate.speaker == ConversationSpeaker.ASSISTANT &&
                            candidate.turnId == turnId &&
                            normalizedIdentifier(candidate.itemId) == null
                    }.takeIf { it >= 0 }?.let { return it }
                }

                if (threadIsActive || threadIsRunning) {
                    merged.indexOfLast { candidate ->
                        candidate.speaker == ConversationSpeaker.ASSISTANT &&
                            candidate.turnId == turnId &&
                            candidate.isStreaming
                    }.takeIf { it >= 0 }?.let { return it }
                }

                val threadIsStillActive = threadIsActive || threadIsRunning
                if (!threadIsStillActive && assistantHistoryCountByTurn[turnId] == 1) {
                    val candidateIndices = merged.indices.filter { index ->
                        val candidate = merged[index]
                        candidate.speaker == ConversationSpeaker.ASSISTANT &&
                            candidate.turnId == turnId &&
                            !candidate.isStreaming
                    }
                    if (candidateIndices.size == 1) {
                        val candidateIndex = candidateIndices.single()
                        return if (
                            shouldReplaceClosedAssistantMessage(
                                localItem = merged[candidateIndex],
                                historyItem = historyItem,
                            )
                        ) {
                            candidateIndex
                        } else {
                            MatchSuppress
                        }
                    }
                }

                if (incomingItemId != null) {
                    merged.indexOfLast { candidate ->
                        candidate.speaker == ConversationSpeaker.ASSISTANT &&
                            candidate.turnId == turnId &&
                            normalizedIdentifier(candidate.itemId) == incomingItemId
                    }.takeIf { it >= 0 }?.let { return it }
                }

                merged.indexOfLast { candidate ->
                    candidate.speaker == ConversationSpeaker.ASSISTANT &&
                        candidate.turnId == turnId &&
                        normalizedText(candidate.text) == normalizedText(historyItem.text)
                }.takeIf { it >= 0 }?.let { return it }
            }
        }

        if (historyItem.speaker == ConversationSpeaker.USER) {
            val turnId = normalizedIdentifier(historyItem.turnId)
            if (turnId != null) {
                merged.indexOfLast { candidate ->
                    candidate.speaker == ConversationSpeaker.USER &&
                        candidate.deliveryState != RemodexMessageDeliveryState.FAILED &&
                        userHistoryTextCompatible(
                            localItem = candidate,
                            historyItem = historyItem,
                        ) &&
                        normalizedIdentifier(candidate.turnId) == turnId &&
                        userAttachmentsCompatible(
                            localAttachments = candidate.attachments,
                            serverAttachments = historyItem.attachments,
                        )
                }.takeIf { it >= 0 }?.let { return it }
            }
            merged.indexOfLast { candidate ->
                candidate.speaker == ConversationSpeaker.USER &&
                    candidate.deliveryState != RemodexMessageDeliveryState.FAILED &&
                    userHistoryTextCompatible(
                        localItem = candidate,
                        historyItem = historyItem,
                    ) &&
                    attachmentSignature(candidate.attachments) == attachmentSignature(historyItem.attachments) &&
                    (candidate.turnId == null || candidate.turnId == turnId)
            }.takeIf { it >= 0 }?.let { return it }
        }

        if (historyItem.speaker == ConversationSpeaker.SYSTEM) {
            val turnId = normalizedIdentifier(historyItem.turnId)
            when (historyItem.kind) {
                ConversationItemKind.REASONING,
                ConversationItemKind.MCP_TOOL_CALL,
                ConversationItemKind.WEB_SEARCH,
                ConversationItemKind.IMAGE_VIEW,
                ConversationItemKind.IMAGE_GENERATION,
                ConversationItemKind.FILE_CHANGE,
                ConversationItemKind.SUBAGENT_ACTION,
                ConversationItemKind.PLAN_UPDATE,
                ConversationItemKind.USER_INPUT_PROMPT,
                -> {
                    if (turnId != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == historyItem.kind &&
                                (candidate.turnId == null || candidate.turnId == turnId)
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                }

                ConversationItemKind.PLAN -> {
                    val incomingItemId = normalizedIdentifier(historyItem.itemId)
                    if (incomingItemId != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == ConversationItemKind.PLAN &&
                                normalizedIdentifier(candidate.itemId) == incomingItemId
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                    if (turnId != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == ConversationItemKind.PLAN &&
                                (candidate.turnId == null || candidate.turnId == turnId) &&
                                normalizedIdentifier(candidate.itemId) == null
                        }.takeIf { it >= 0 }?.let { return it }

                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == ConversationItemKind.PLAN &&
                                (candidate.turnId == null || candidate.turnId == turnId)
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                }

                ConversationItemKind.TOOL_ACTIVITY -> {
                    val incomingItemId = normalizedIdentifier(historyItem.itemId)
                    if (incomingItemId != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == ConversationItemKind.TOOL_ACTIVITY &&
                                normalizedIdentifier(candidate.itemId) == incomingItemId
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                    if (turnId != null) {
                        val incomingPreview = normalizedToolActivityPreview(historyItem.text)
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == ConversationItemKind.TOOL_ACTIVITY &&
                                candidate.turnId == turnId &&
                                incomingPreview != null &&
                                normalizedToolActivityPreview(candidate.text) == incomingPreview
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                }

                ConversationItemKind.MCP_TOOL_CALL,
                ConversationItemKind.WEB_SEARCH,
                ConversationItemKind.IMAGE_VIEW,
                ConversationItemKind.IMAGE_GENERATION,
                ConversationItemKind.CONTEXT_COMPACTION,
                ConversationItemKind.COMMAND_EXECUTION -> {
                    val incomingItemId = normalizedIdentifier(historyItem.itemId)
                    if (incomingItemId != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == historyItem.kind &&
                                normalizedIdentifier(candidate.itemId) == incomingItemId
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                    val incomingCommandKey = normalizedCommandExecutionPreviewKey(historyItem.text)
                    if (
                        historyItem.kind == ConversationItemKind.COMMAND_EXECUTION &&
                        turnId != null &&
                        incomingCommandKey != null
                    ) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == historyItem.kind &&
                                candidate.turnId == turnId &&
                                normalizedCommandExecutionPreviewKey(candidate.text) == incomingCommandKey
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                    if (turnId != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == historyItem.kind &&
                                candidate.turnId == turnId
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                }

                ConversationItemKind.CHAT -> Unit
            }
        }

        val historyKey = historyKey(historyItem)
        merged.indexOfFirst { historyKey(it) == historyKey }
            .takeIf { it >= 0 }
            ?.let { return it }

        if (historyItem.speaker == ConversationSpeaker.USER) {
            merged.indexOfLast { candidate ->
                candidate.speaker == ConversationSpeaker.USER &&
                    candidate.deliveryState == RemodexMessageDeliveryState.PENDING &&
                    normalizedText(candidate.text) == normalizedText(historyItem.text) &&
                    userAttachmentsCompatible(
                        localAttachments = candidate.attachments,
                        serverAttachments = historyItem.attachments,
                    )
            }.takeIf { it >= 0 }?.let { return it }
        }

        return MatchNotFound
    }

    private fun reconcileExistingItem(
        localItem: RemodexConversationItem,
        serverItem: RemodexConversationItem,
        threadIsActive: Boolean,
        threadIsRunning: Boolean,
    ): RemodexConversationItem {
        var value = localItem
        val preservesRunningPresentation = (threadIsActive || threadIsRunning) &&
            (
                localItem.turnId == null ||
                    serverItem.turnId == null ||
                    localItem.turnId == serverItem.turnId
                )

        val localItemId = normalizedIdentifier(value.itemId)
        val serverItemId = normalizedIdentifier(serverItem.itemId)

        if (value.deliveryState == RemodexMessageDeliveryState.PENDING) {
            value = value.copy(deliveryState = RemodexMessageDeliveryState.CONFIRMED)
        }
        if (value.turnId == null) {
            value = value.copy(turnId = serverItem.turnId)
        }
        if (
            localItemId == null ||
            (
                preservesRunningPresentation &&
                    value.speaker == ConversationSpeaker.ASSISTANT &&
                    localItem.isStreaming &&
                    serverItemId != null &&
                    localItemId != serverItemId
                ) ||
            (
                value.speaker == ConversationSpeaker.SYSTEM &&
                    (
                        value.kind == ConversationItemKind.TOOL_ACTIVITY ||
                            value.kind == ConversationItemKind.MCP_TOOL_CALL ||
                            value.kind == ConversationItemKind.WEB_SEARCH ||
                            value.kind == ConversationItemKind.IMAGE_VIEW ||
                            value.kind == ConversationItemKind.IMAGE_GENERATION
                        ) &&
                    serverItemId != null &&
                    localItemId != serverItemId
                )
        ) {
            value = value.copy(itemId = serverItemId)
        }
        if (value.kind == ConversationItemKind.CHAT && serverItem.kind != ConversationItemKind.CHAT) {
            value = value.copy(kind = serverItem.kind)
        }
        if (value.attachments.isEmpty() && serverItem.attachments.isNotEmpty()) {
            value = value.copy(attachments = serverItem.attachments)
        }
        if (value.supportingText.isNullOrBlank() && !serverItem.supportingText.isNullOrBlank()) {
            value = value.copy(supportingText = serverItem.supportingText)
        }

        val serverText = normalizedText(serverItem.text)
        if (serverText.isNotEmpty()) {
            val mergedText = when {
                value.kind == ConversationItemKind.REASONING -> {
                    mergeStreamingSnapshotText(existingText = value.text, incomingText = serverItem.text)
                }

                value.speaker == ConversationSpeaker.ASSISTANT || value.speaker == ConversationSpeaker.SYSTEM -> {
                    if (preservesRunningPresentation && localItem.isStreaming) {
                        mergeStreamingSnapshotText(existingText = value.text, incomingText = serverItem.text)
                    } else {
                        serverItem.text
                    }
                }

                else -> serverItem.text
            }
            value = value.copy(text = mergedText)
        }

        val nextPlanState = mergePlanState(value.planState, serverItem.planState)
        val nextSupportingText = serverItem.supportingText ?: value.supportingText
        val nextSubagentAction = serverItem.subagentAction ?: value.subagentAction
        val nextStructuredRequest = serverItem.structuredUserInputRequest ?: value.structuredUserInputRequest
        val nextStructuredResponse = serverItem.structuredUserInputResponse ?: value.structuredUserInputResponse
        val nextAssistantChangeSet = serverItem.assistantChangeSet ?: value.assistantChangeSet
        val nextSystemTurnOrderingHint = if (
            serverItem.systemTurnOrderingHint != ConversationSystemTurnOrderingHint.AUTO
        ) {
            serverItem.systemTurnOrderingHint
        } else {
            value.systemTurnOrderingHint
        }
        val nextStreaming = if (value.speaker == ConversationSpeaker.ASSISTANT || value.speaker == ConversationSpeaker.SYSTEM) {
            if (preservesRunningPresentation) {
                localItem.isStreaming || serverItem.isStreaming
            } else {
                false
            }
        } else {
            false
        }

        return value.copy(
            supportingText = nextSupportingText,
            planState = nextPlanState,
            subagentAction = nextSubagentAction,
            structuredUserInputRequest = nextStructuredRequest,
            structuredUserInputResponse = nextStructuredResponse,
            assistantChangeSet = nextAssistantChangeSet,
            createdAtEpochMs = value.createdAtEpochMs ?: serverItem.createdAtEpochMs,
            isStreaming = nextStreaming,
            systemTurnOrderingHint = nextSystemTurnOrderingHint,
        )
    }

    private fun mergePlanState(
        local: com.emanueledipietro.remodex.model.RemodexPlanState?,
        incoming: com.emanueledipietro.remodex.model.RemodexPlanState?,
    ): com.emanueledipietro.remodex.model.RemodexPlanState? {
        if (local == null) {
            return incoming
        }
        if (incoming == null) {
            return local
        }
        return local.copy(
            explanation = incoming.explanation ?: local.explanation,
            steps = if (incoming.steps.isNotEmpty()) incoming.steps else local.steps,
        )
    }

    private fun historyKey(item: RemodexConversationItem): String {
        val itemId = normalizedIdentifier(item.itemId)
        if (itemId != null) {
            return "item:${item.speaker}:${item.kind}:$itemId"
        }
        return listOf(
            item.speaker.name,
            item.kind.name,
            item.turnId ?: "no-turn",
            item.text,
            attachmentSignature(item.attachments),
        ).joinToString(separator = "|")
    }

    private fun attachmentSignature(attachments: List<RemodexConversationAttachment>): String {
        return attachments.joinToString(separator = "|") { attachment ->
            listOf(attachment.id, attachment.uriString, attachment.displayName).joinToString(separator = ":")
        }
    }

    private fun userHistoryTextCompatible(
        localItem: RemodexConversationItem,
        historyItem: RemodexConversationItem,
    ): Boolean {
        val localText = normalizedText(localItem.text)
        val historyText = normalizedText(historyItem.text)
        if (localText == historyText) {
            return true
        }
        if (!userAttachmentsCompatible(localItem.attachments, historyItem.attachments)) {
            return false
        }
        if (!historyText.equals("usermessage", ignoreCase = true)) {
            return false
        }
        return localText == normalizedText(androidUserMessageFallbackText(localItem.attachments.size))
    }

    private fun userAttachmentsCompatible(
        localAttachments: List<RemodexConversationAttachment>,
        serverAttachments: List<RemodexConversationAttachment>,
    ): Boolean {
        if (attachmentSignature(localAttachments) == attachmentSignature(serverAttachments)) {
            return true
        }
        if (localAttachments.isEmpty() || serverAttachments.isEmpty()) {
            return false
        }
        if (localAttachments.size != serverAttachments.size) {
            return false
        }
        return localAttachments.any(::looksLikeInlineOrLocalImageAttachment) &&
            serverAttachments.any(::looksLikeHistoryImageReferenceAttachment)
    }

    private fun looksLikeInlineOrLocalImageAttachment(attachment: RemodexConversationAttachment): Boolean {
        val value = attachment.uriString.trim().lowercase()
        return value.startsWith("content://") ||
            value.startsWith("file://") ||
            value.startsWith("data:image")
    }

    private fun looksLikeHistoryImageReferenceAttachment(attachment: RemodexConversationAttachment): Boolean {
        val value = attachment.uriString.trim().lowercase()
        return value == "remodex://history-image-elided" || value.startsWith("data:image")
    }

    private fun normalizedText(value: String): String = value.trim()

    private fun shouldReplaceClosedAssistantMessage(
        localItem: RemodexConversationItem,
        historyItem: RemodexConversationItem,
    ): Boolean {
        val localText = normalizedText(localItem.text)
        val historyText = normalizedText(historyItem.text)
        if (historyText.isEmpty()) {
            return false
        }
        if (localText.isEmpty() || localText == historyText) {
            return true
        }
        return historyText.length > localText.length && historyText.startsWith(localText)
    }

    private fun compactStreamingText(value: String): String {
        return buildString(value.length) {
            value.forEach { character ->
                if (!character.isWhitespace()) {
                    append(character)
                }
            }
        }
    }

    private fun normalizedIdentifier(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.ifEmpty { null }
    }

    private fun normalizedToolActivityPreview(value: String): String? {
        val lines = normalizedText(value)
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (lines.isEmpty()) {
            return null
        }
        return lines.joinToString(separator = "\n").lowercase()
    }

    private fun normalizedCommandExecutionPreviewKey(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val tokens = trimmed
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
        if (tokens.isEmpty()) {
            return null
        }
        val statusPrefixes = setOf("running", "completed", "failed", "stopped")
        val commandTokens = if (tokens.first().lowercase() in statusPrefixes) {
            tokens.drop(1)
        } else {
            tokens
        }
        return commandTokens.joinToString(separator = " ").trim().ifEmpty { null }?.lowercase()
    }
}
