package com.emanueledipietro.remodex.data.threads

import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationAttachment
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexMessageDeliveryState

internal object ThreadHistoryReconciler {
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

        val merged = existing.toMutableList()
        history.forEach { item ->
            val matchIndex = findMatchIndex(
                merged = merged,
                historyItem = item,
                threadIsActive = threadIsActive,
                threadIsRunning = threadIsRunning,
            )
            if (matchIndex == -1) {
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

        val maxOverlap = minOf(existingText.length, incomingText.length)
        for (overlap in maxOverlap downTo 1) {
            if (existingText.takeLast(overlap) == incomingText.take(overlap)) {
                return existingText + incomingText.drop(overlap)
            }
        }
        return incomingText
    }

    private fun findMatchIndex(
        merged: List<RemodexConversationItem>,
        historyItem: RemodexConversationItem,
        threadIsActive: Boolean,
        threadIsRunning: Boolean,
    ): Int {
        if (historyItem.speaker == ConversationSpeaker.ASSISTANT) {
            val turnId = normalizedIdentifier(historyItem.turnId)
            val incomingItemId = normalizedIdentifier(historyItem.itemId)
            if (turnId != null) {
                merged.indexOfLast { candidate ->
                    candidate.speaker == ConversationSpeaker.ASSISTANT &&
                        candidate.turnId == turnId &&
                        normalizedText(candidate.text) == normalizedText(historyItem.text)
                }.takeIf { it >= 0 }?.let { return it }

                merged.indexOfLast { candidate ->
                    candidate.speaker == ConversationSpeaker.ASSISTANT &&
                        candidate.turnId == turnId &&
                        (normalizedIdentifier(candidate.itemId) == null ||
                            normalizedIdentifier(candidate.itemId) == incomingItemId)
                }.takeIf { it >= 0 }?.let { return it }

                if (threadIsActive || threadIsRunning) {
                    merged.indexOfLast { candidate ->
                        candidate.speaker == ConversationSpeaker.ASSISTANT &&
                            candidate.turnId == turnId &&
                            candidate.isStreaming
                    }.takeIf { it >= 0 }?.let { return it }
                }
            }
        }

        if (historyItem.speaker == ConversationSpeaker.USER) {
            val turnId = normalizedIdentifier(historyItem.turnId)
            if (turnId != null) {
                merged.indexOfLast { candidate ->
                    candidate.speaker == ConversationSpeaker.USER &&
                        candidate.deliveryState != RemodexMessageDeliveryState.FAILED &&
                        normalizedText(candidate.text) == normalizedText(historyItem.text) &&
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
                    normalizedText(candidate.text) == normalizedText(historyItem.text) &&
                    attachmentSignature(candidate.attachments) == attachmentSignature(historyItem.attachments) &&
                    (candidate.turnId == null || candidate.turnId == turnId)
            }.takeIf { it >= 0 }?.let { return it }
        }

        if (historyItem.speaker == ConversationSpeaker.SYSTEM) {
            val turnId = normalizedIdentifier(historyItem.turnId)
            when (historyItem.kind) {
                ConversationItemKind.REASONING,
                ConversationItemKind.FILE_CHANGE,
                ConversationItemKind.PLAN,
                ConversationItemKind.SUBAGENT_ACTION,
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

                ConversationItemKind.COMMAND_EXECUTION -> {
                    val incomingItemId = normalizedIdentifier(historyItem.itemId)
                    if (incomingItemId != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == ConversationItemKind.COMMAND_EXECUTION &&
                                normalizedIdentifier(candidate.itemId) == incomingItemId
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                    val incomingCommandKey = normalizedCommandExecutionPreviewKey(historyItem.text)
                    if (turnId != null && incomingCommandKey != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == ConversationItemKind.COMMAND_EXECUTION &&
                                candidate.turnId == turnId &&
                                normalizedCommandExecutionPreviewKey(candidate.text) == incomingCommandKey
                        }.takeIf { it >= 0 }?.let { return it }
                    }
                    if (turnId != null) {
                        merged.indexOfLast { candidate ->
                            candidate.speaker == ConversationSpeaker.SYSTEM &&
                                candidate.kind == ConversationItemKind.COMMAND_EXECUTION &&
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

        return -1
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
                    value.kind == ConversationItemKind.TOOL_ACTIVITY &&
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
        val nextAssistantChangeSet = serverItem.assistantChangeSet ?: value.assistantChangeSet
        val nextStreaming = if (value.speaker == ConversationSpeaker.ASSISTANT || value.speaker == ConversationSpeaker.SYSTEM) {
            if (preservesRunningPresentation) {
                localItem.isStreaming || serverItem.isStreaming || threadIsRunning
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
            assistantChangeSet = nextAssistantChangeSet,
            isStreaming = nextStreaming,
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
