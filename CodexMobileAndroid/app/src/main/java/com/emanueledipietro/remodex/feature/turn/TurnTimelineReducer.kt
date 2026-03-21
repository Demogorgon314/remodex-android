package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.data.threads.TimelineMutation
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem

object TurnTimelineReducer {
    fun reduce(mutations: List<TimelineMutation>): List<RemodexConversationItem> {
        return mutations.fold(emptyList<RemodexConversationItem>()) { items, mutation ->
            reduce(items, mutation)
        }.sortedBy(RemodexConversationItem::orderIndex)
    }

    fun reduce(
        items: List<RemodexConversationItem>,
        mutation: TimelineMutation,
    ): List<RemodexConversationItem> {
        return when (mutation) {
            is TimelineMutation.Upsert -> upsert(items, mutation.item)
            is TimelineMutation.AssistantTextDelta -> mergeTextDelta(
                items = items,
                messageId = mutation.messageId,
                turnId = mutation.turnId,
                itemId = mutation.itemId,
                delta = mutation.delta,
                orderIndex = mutation.orderIndex,
                speaker = ConversationSpeaker.ASSISTANT,
                kind = ConversationItemKind.CHAT,
            )

            is TimelineMutation.ReasoningTextDelta -> mergeTextDelta(
                items = items,
                messageId = mutation.messageId,
                turnId = mutation.turnId,
                itemId = mutation.itemId,
                delta = mutation.delta,
                orderIndex = mutation.orderIndex,
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.REASONING,
            )

            is TimelineMutation.ActivityLine -> mergeActivityLine(
                items = items,
                messageId = mutation.messageId,
                turnId = mutation.turnId,
                itemId = mutation.itemId,
                line = mutation.line,
                orderIndex = mutation.orderIndex,
            )

            is TimelineMutation.Complete -> markComplete(
                items = items,
                messageId = mutation.messageId,
            )
        }
    }

    private fun upsert(
        items: List<RemodexConversationItem>,
        item: RemodexConversationItem,
    ): List<RemodexConversationItem> {
        val existingIndex = items.indexOfFirst { it.id == item.id }
        if (existingIndex == -1) {
            return items + item
        }
        return items.toMutableList().apply {
            this[existingIndex] = item
        }
    }

    private fun mergeTextDelta(
        items: List<RemodexConversationItem>,
        messageId: String,
        turnId: String,
        itemId: String?,
        delta: String,
        orderIndex: Long,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): List<RemodexConversationItem> {
        val trimmedDelta = delta.trim()
        val existingIndex = items.indexOfFirst { item ->
            item.id == messageId || (
                item.kind == kind &&
                    item.speaker == speaker &&
                    item.turnId == turnId &&
                    item.itemId == itemId
            )
        }
        val nextItem = if (existingIndex == -1) {
            RemodexConversationItem(
                id = messageId,
                speaker = speaker,
                kind = kind,
                text = trimmedDelta,
                turnId = turnId,
                itemId = itemId,
                isStreaming = true,
                orderIndex = orderIndex,
            )
        } else {
            val existing = items[existingIndex]
            existing.copy(
                text = mergeText(existing.text, trimmedDelta),
                turnId = turnId,
                itemId = itemId ?: existing.itemId,
                isStreaming = true,
                orderIndex = maxOf(existing.orderIndex, orderIndex),
            )
        }

        return if (existingIndex == -1) {
            items + nextItem
        } else {
            items.toMutableList().apply {
                this[existingIndex] = nextItem
            }
        }
    }

    private fun mergeActivityLine(
        items: List<RemodexConversationItem>,
        messageId: String,
        turnId: String,
        itemId: String?,
        line: String,
        orderIndex: Long,
    ): List<RemodexConversationItem> {
        val trimmedLine = line.trim()
        val existingIndex = items.indexOfFirst { item ->
            item.id == messageId || (
                item.turnId == turnId &&
                    item.itemId == itemId &&
                    item.kind == ConversationItemKind.COMMAND_EXECUTION
            )
        }
        val nextItem = if (existingIndex == -1) {
            RemodexConversationItem(
                id = messageId,
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.COMMAND_EXECUTION,
                text = trimmedLine,
                supportingText = "Activity",
                turnId = turnId,
                itemId = itemId,
                isStreaming = true,
                orderIndex = orderIndex,
            )
        } else {
            val existing = items[existingIndex]
            existing.copy(
                text = mergeText(existing.text, trimmedLine),
                supportingText = existing.supportingText ?: "Activity",
                turnId = turnId,
                itemId = itemId ?: existing.itemId,
                isStreaming = true,
                orderIndex = maxOf(existing.orderIndex, orderIndex),
            )
        }

        return if (existingIndex == -1) {
            items + nextItem
        } else {
            items.toMutableList().apply {
                this[existingIndex] = nextItem
            }
        }
    }

    private fun markComplete(
        items: List<RemodexConversationItem>,
        messageId: String,
    ): List<RemodexConversationItem> {
        val existingIndex = items.indexOfFirst { it.id == messageId }
        if (existingIndex == -1) {
            return items
        }
        return items.toMutableList().apply {
            this[existingIndex] = this[existingIndex].copy(isStreaming = false)
        }
    }

    private fun mergeText(
        existing: String,
        incoming: String,
    ): String {
        val existingTrimmed = existing.trim()
        val incomingTrimmed = incoming.trim()
        if (incomingTrimmed.isEmpty()) {
            return existingTrimmed
        }
        if (existingTrimmed.isEmpty()) {
            return incomingTrimmed
        }

        val placeholderValues = setOf("thinking...")
        val existingLower = existingTrimmed.lowercase()
        val incomingLower = incomingTrimmed.lowercase()
        if (placeholderValues.contains(incomingLower)) {
            return existingTrimmed
        }
        if (placeholderValues.contains(existingLower)) {
            return incomingTrimmed
        }
        if (incomingTrimmed in existingTrimmed) {
            return existingTrimmed
        }
        if (existingTrimmed in incomingTrimmed) {
            return incomingTrimmed
        }
        if (existingLower == incomingLower) {
            return existingTrimmed
        }
        if (existingTrimmed.endsWith(incomingTrimmed)) {
            return existingTrimmed
        }
        return "$existingTrimmed\n$incomingTrimmed"
    }
}
