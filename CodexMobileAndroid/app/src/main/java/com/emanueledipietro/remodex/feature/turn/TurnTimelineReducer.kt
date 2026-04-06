package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.data.threads.TimelineMutation
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.ConversationSystemTurnOrderingHint
import com.emanueledipietro.remodex.model.RemodexConversationItem

object TurnTimelineReducer {
    private const val ManualPushResetMarkerItemId = "git.push.reset.marker"

    internal enum class SystemTurnOrderingPolicy(
        val leadingPriority: Int,
        val interleavable: Boolean,
        val preserveChronologyWhenLateAfterAssistant: Boolean,
    ) {
        REASONING(leadingPriority = 1, interleavable = true, preserveChronologyWhenLateAfterAssistant = true),
        TOOL_ACTIVITY(leadingPriority = 2, interleavable = true, preserveChronologyWhenLateAfterAssistant = false),
        MCP_TOOL_CALL(leadingPriority = 2, interleavable = true, preserveChronologyWhenLateAfterAssistant = true),
        WEB_SEARCH(leadingPriority = 2, interleavable = true, preserveChronologyWhenLateAfterAssistant = true),
        COMMAND_EXECUTION(leadingPriority = 2, interleavable = true, preserveChronologyWhenLateAfterAssistant = true),
        CONTEXT_COMPACTION(leadingPriority = 2, interleavable = true, preserveChronologyWhenLateAfterAssistant = true),
        IMAGE_VIEW(leadingPriority = 3, interleavable = true, preserveChronologyWhenLateAfterAssistant = true),
        IMAGE_GENERATION(leadingPriority = 3, interleavable = true, preserveChronologyWhenLateAfterAssistant = true),
        SUBAGENT_ACTION(leadingPriority = 3, interleavable = true, preserveChronologyWhenLateAfterAssistant = true),
        CHAT(leadingPriority = 4, interleavable = false, preserveChronologyWhenLateAfterAssistant = false),
        PLAN_UPDATE(leadingPriority = 4, interleavable = false, preserveChronologyWhenLateAfterAssistant = false),
        PLAN(leadingPriority = 4, interleavable = false, preserveChronologyWhenLateAfterAssistant = false),
        FILE_CHANGE(leadingPriority = 5, interleavable = false, preserveChronologyWhenLateAfterAssistant = false),
        USER_INPUT_PROMPT(leadingPriority = 6, interleavable = false, preserveChronologyWhenLateAfterAssistant = false),
    }

    internal fun systemTurnOrderingPolicy(
        kind: ConversationItemKind,
    ): SystemTurnOrderingPolicy {
        return when (kind) {
            ConversationItemKind.REASONING -> SystemTurnOrderingPolicy.REASONING
            ConversationItemKind.TOOL_ACTIVITY -> SystemTurnOrderingPolicy.TOOL_ACTIVITY
            ConversationItemKind.MCP_TOOL_CALL -> SystemTurnOrderingPolicy.MCP_TOOL_CALL
            ConversationItemKind.WEB_SEARCH -> SystemTurnOrderingPolicy.WEB_SEARCH
            ConversationItemKind.COMMAND_EXECUTION -> SystemTurnOrderingPolicy.COMMAND_EXECUTION
            ConversationItemKind.CONTEXT_COMPACTION -> SystemTurnOrderingPolicy.CONTEXT_COMPACTION
            ConversationItemKind.IMAGE_VIEW -> SystemTurnOrderingPolicy.IMAGE_VIEW
            ConversationItemKind.IMAGE_GENERATION -> SystemTurnOrderingPolicy.IMAGE_GENERATION
            ConversationItemKind.SUBAGENT_ACTION -> SystemTurnOrderingPolicy.SUBAGENT_ACTION
            ConversationItemKind.CHAT -> SystemTurnOrderingPolicy.CHAT
            ConversationItemKind.PLAN_UPDATE -> SystemTurnOrderingPolicy.PLAN_UPDATE
            ConversationItemKind.PLAN -> SystemTurnOrderingPolicy.PLAN
            ConversationItemKind.FILE_CHANGE -> SystemTurnOrderingPolicy.FILE_CHANGE
            ConversationItemKind.USER_INPUT_PROMPT -> SystemTurnOrderingPolicy.USER_INPUT_PROMPT
        }
    }

    internal fun isInterleavableSystemActivityKind(
        kind: ConversationItemKind,
    ): Boolean = systemTurnOrderingPolicy(kind).interleavable

    internal fun shouldPreserveLateSystemActivityChronology(
        kind: ConversationItemKind,
        hint: ConversationSystemTurnOrderingHint = ConversationSystemTurnOrderingHint.AUTO,
    ): Boolean {
        return when (hint) {
            ConversationSystemTurnOrderingHint.PRESERVE_CHRONOLOGY_WHEN_LATE -> true
            ConversationSystemTurnOrderingHint.AUTO ->
                systemTurnOrderingPolicy(kind).preserveChronologyWhenLateAfterAssistant
        }
    }

    internal fun shouldPreserveLateSystemActivityChronology(
        item: RemodexConversationItem,
    ): Boolean = shouldPreserveLateSystemActivityChronology(
        kind = item.kind,
        hint = item.systemTurnOrderingHint,
    )

    fun activeTurnAnchorIndex(
        items: List<RemodexConversationItem>,
        activeTurnId: String?,
    ): Int? {
        assistantResponseAnchorIndex(items, activeTurnId)?.let { index ->
            return index
        }

        if (activeTurnId != null) {
            val activeTurnIndex = items.indexOfLast { item ->
                item.turnId == activeTurnId
            }
            if (activeTurnIndex >= 0) {
                return activeTurnIndex
            }
        }

        val streamingItemIndex = items.indexOfLast { item -> item.isStreaming }
        return streamingItemIndex.takeIf { it >= 0 }
    }

    fun assistantResponseAnchorIndex(
        items: List<RemodexConversationItem>,
        activeTurnId: String?,
    ): Int? {
        if (activeTurnId != null) {
            val activeTurnIndex = items.indexOfLast { item ->
                item.speaker == ConversationSpeaker.ASSISTANT &&
                    item.turnId == activeTurnId
            }
            if (activeTurnIndex >= 0) {
                return activeTurnIndex
            }
        }

        val streamingAssistantIndex = items.indexOfLast { item ->
            item.speaker == ConversationSpeaker.ASSISTANT && item.isStreaming
        }
        return streamingAssistantIndex.takeIf { it >= 0 }
    }

    fun reduce(mutations: List<TimelineMutation>): List<RemodexConversationItem> {
        return mutations.fold(emptyList<RemodexConversationItem>()) { items, mutation ->
            reduce(items, mutation)
        }.sortedBy(RemodexConversationItem::orderIndex)
    }

    fun reduceProjected(mutations: List<TimelineMutation>): List<RemodexConversationItem> {
        return project(reduce(mutations))
    }

    fun applyProjectedFastPath(
        items: List<RemodexConversationItem>,
        mutation: TimelineMutation,
    ): List<RemodexConversationItem>? {
        return when (mutation) {
            is TimelineMutation.Upsert -> applyProjectedUpsertFastPath(
                items = items,
                item = mutation.item,
            )

            is TimelineMutation.AssistantTextDelta -> applyAssistantProjectedTextDelta(
                items = items,
                messageId = mutation.messageId,
                turnId = mutation.turnId,
                itemId = mutation.itemId,
                delta = mutation.delta,
                orderIndex = mutation.orderIndex,
            )

            else -> null
        }
    }

    private fun applyProjectedUpsertFastPath(
        items: List<RemodexConversationItem>,
        item: RemodexConversationItem,
    ): List<RemodexConversationItem>? {
        if (
            item.speaker != ConversationSpeaker.ASSISTANT ||
            item.kind != ConversationItemKind.CHAT
        ) {
            return null
        }
        val existingIndex = items.indexOfFirst { candidate ->
            candidate.id == item.id &&
                candidate.speaker == ConversationSpeaker.ASSISTANT &&
                candidate.kind == ConversationItemKind.CHAT
        }
        if (existingIndex == -1) {
            return null
        }

        val existing = items[existingIndex]
        if (existing == item) {
            return items
        }

        return items.toMutableList().apply {
            this[existingIndex] = item
        }
    }

    fun project(items: List<RemodexConversationItem>): List<RemodexConversationItem> {
        if (items.isEmpty()) {
            return emptyList()
        }
        val visibleItems = removeHiddenSystemMarkers(items)
        val reordered = enforceIntraTurnOrder(visibleItems)
        val collapsedThinking = collapseThinkingMessages(reordered)
        val withoutCommandThinkingEchoes = removeRedundantThinkingCommandActivityMessages(collapsedThinking)
        val dedupedFileChanges = removeDuplicateFileChangeMessages(withoutCommandThinkingEchoes)
        val dedupedSubagentActions = removeDuplicateSubagentActionMessages(dedupedFileChanges)
        return removeDuplicateAssistantMessages(dedupedSubagentActions)
    }

    private fun removeHiddenSystemMarkers(
        items: List<RemodexConversationItem>,
    ): List<RemodexConversationItem> {
        return items.filterNot { item ->
            item.speaker == ConversationSpeaker.SYSTEM &&
                item.itemId == ManualPushResetMarkerItemId
        }
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
                systemTurnOrderingHint = mutation.systemTurnOrderingHint,
            )

            is TimelineMutation.SystemTextDelta -> mergeTextDelta(
                items = items,
                messageId = mutation.messageId,
                turnId = mutation.turnId,
                itemId = mutation.itemId,
                delta = mutation.delta,
                orderIndex = mutation.orderIndex,
                speaker = ConversationSpeaker.SYSTEM,
                kind = mutation.kind,
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

    private fun applyAssistantProjectedTextDelta(
        items: List<RemodexConversationItem>,
        messageId: String,
        turnId: String,
        itemId: String?,
        delta: String,
        orderIndex: Long,
    ): List<RemodexConversationItem>? {
        val existingIndex = items.indexOfFirst { item ->
            item.id == messageId &&
                item.speaker == ConversationSpeaker.ASSISTANT &&
                item.kind == ConversationItemKind.CHAT
        }
        if (existingIndex == -1) {
            return null
        }

        val existing = items[existingIndex]
        // Assistant message deltas are incremental protocol chunks, not
        // cumulative snapshots. Appending them verbatim preserves repeated
        // characters like markdown fences that may legitimately span chunks.
        val nextText = appendIncrementalText(existing.text, delta)
        val nextTurnId = turnId.ifBlank { existing.turnId.orEmpty() }
        val nextItemId = itemId ?: existing.itemId

        if (
            nextText == existing.text &&
            existing.isStreaming &&
            existing.turnId == nextTurnId &&
            existing.itemId == nextItemId &&
            existing.orderIndex >= orderIndex
        ) {
            return items
        }

        val updated = existing.copy(
            text = nextText,
            turnId = nextTurnId,
            itemId = nextItemId,
            isStreaming = true,
            orderIndex = preservedStreamingOrderIndex(
                existing = existing,
                incomingOrderIndex = orderIndex,
            ),
        )

        return items.toMutableList().apply {
            this[existingIndex] = updated
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
        val normalizedDelta = normalizeIncomingDelta(
            delta = delta,
            speaker = speaker,
            kind = kind,
        )
        val existingIndex = items.indexOfFirst { item ->
            matchesStreamingMessageTarget(
                item = item,
                messageId = messageId,
                turnId = turnId,
                itemId = itemId,
                speaker = speaker,
                kind = kind,
            )
        }
        val nextItem = if (existingIndex == -1) {
            RemodexConversationItem(
                id = messageId,
                speaker = speaker,
                kind = kind,
                text = normalizedDelta,
                turnId = turnId,
                itemId = itemId,
                isStreaming = true,
                orderIndex = orderIndex,
            )
        } else {
            val existing = items[existingIndex]
            existing.copy(
                text = mergeDeltaText(
                    existing = existing.text,
                    incoming = normalizedDelta,
                    speaker = speaker,
                    kind = kind,
                ),
                turnId = turnId,
                itemId = itemId ?: existing.itemId,
                isStreaming = true,
                orderIndex = preservedStreamingOrderIndex(
                    existing = existing,
                    incomingOrderIndex = orderIndex,
                ),
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

    private fun mergeDeltaText(
        existing: String,
        incoming: String,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): String {
        if (usesIncrementalDeltaAppend(speaker = speaker, kind = kind)) {
            return appendIncrementalText(existing, incoming)
        }
        if (preservesStreamingWhitespace(speaker = speaker, kind = kind)) {
            return mergeText(existing, incoming)
        }
        return mergeSystemText(existing, incoming)
    }

    private fun mergeSystemText(
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
        if (incomingTrimmed in existingTrimmed) {
            return existingTrimmed
        }
        if (existingTrimmed in incomingTrimmed) {
            return incomingTrimmed
        }
        if (!existing.contains('\n') && !incoming.startsWith('\n')) {
            return "$existingTrimmed\n$incoming"
        }
        return existing + incoming
    }

    private fun mergeActivityLine(
        items: List<RemodexConversationItem>,
        messageId: String,
        turnId: String,
        itemId: String?,
        line: String,
        orderIndex: Long,
        systemTurnOrderingHint: ConversationSystemTurnOrderingHint,
    ): List<RemodexConversationItem> {
        val trimmedLine = line.trim()
        val existingIndex = items.indexOfFirst { item ->
            matchesStreamingMessageTarget(
                item = item,
                messageId = messageId,
                turnId = turnId,
                itemId = itemId,
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.TOOL_ACTIVITY,
            )
        }
        val nextItem = if (existingIndex == -1) {
            RemodexConversationItem(
                id = messageId,
                speaker = ConversationSpeaker.SYSTEM,
                kind = ConversationItemKind.TOOL_ACTIVITY,
                text = trimmedLine,
                turnId = turnId,
                itemId = itemId,
                isStreaming = true,
                orderIndex = orderIndex,
                systemTurnOrderingHint = systemTurnOrderingHint,
            )
        } else {
            val existing = items[existingIndex]
            existing.copy(
                text = mergeToolActivityText(existing.text, trimmedLine),
                turnId = turnId,
                itemId = itemId ?: existing.itemId,
                isStreaming = true,
                orderIndex = maxOf(existing.orderIndex, orderIndex),
                systemTurnOrderingHint = if (
                    systemTurnOrderingHint == ConversationSystemTurnOrderingHint.AUTO
                ) {
                    existing.systemTurnOrderingHint
                } else {
                    systemTurnOrderingHint
                },
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

    private fun mergeToolActivityText(
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
        if (incomingTrimmed.equals(existingTrimmed, ignoreCase = true)) {
            return existingTrimmed
        }

        val mergedLines = existingTrimmed
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toMutableList()
        incomingTrimmed.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { line ->
                if (mergedLines.none { existingLine -> existingLine.equals(line, ignoreCase = true) }) {
                    mergedLines += line
                }
            }
        return mergedLines.joinToString(separator = "\n")
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
        if (incoming.isEmpty()) {
            return existing
        }
        if (existing.isEmpty()) {
            return incoming
        }
        if (existing.isBlank() || incoming.isBlank()) {
            return existing + incoming
        }
        if (incoming == existing) {
            return existing
        }
        if (existing.endsWith(incoming)) {
            return existing
        }
        if (incoming.length > existing.length && incoming.startsWith(existing)) {
            return incoming
        }
        if (existing.length > incoming.length && existing.startsWith(incoming)) {
            return existing
        }

        val maxOverlap = minOf(existing.length, incoming.length)
        if (maxOverlap > 0) {
            for (overlap in maxOverlap downTo 1) {
                if (existing.takeLast(overlap) == incoming.take(overlap)) {
                    return existing + incoming.drop(overlap)
                }
            }
        }

        return existing + incoming
    }

    private fun appendIncrementalText(
        existing: String,
        incoming: String,
    ): String {
        if (incoming.isEmpty()) {
            return existing
        }
        if (existing.isEmpty()) {
            return incoming
        }
        return existing + incoming
    }

    private fun usesIncrementalDeltaAppend(
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): Boolean {
        return when (speaker) {
            ConversationSpeaker.ASSISTANT -> kind == ConversationItemKind.CHAT
            ConversationSpeaker.SYSTEM -> kind == ConversationItemKind.REASONING ||
                kind == ConversationItemKind.MCP_TOOL_CALL ||
                kind == ConversationItemKind.WEB_SEARCH ||
                kind == ConversationItemKind.IMAGE_VIEW ||
                kind == ConversationItemKind.IMAGE_GENERATION ||
                kind == ConversationItemKind.FILE_CHANGE
            ConversationSpeaker.USER -> false
        }
    }

    private fun normalizeIncomingDelta(
        delta: String,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): String {
        return if (preservesStreamingWhitespace(speaker = speaker, kind = kind)) {
            delta
        } else {
            delta.trim()
        }
    }

    internal fun preservesStreamingWhitespace(
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): Boolean {
        return when (speaker) {
            ConversationSpeaker.ASSISTANT -> kind == ConversationItemKind.CHAT
            ConversationSpeaker.SYSTEM -> kind == ConversationItemKind.CHAT ||
                kind == ConversationItemKind.REASONING ||
                kind == ConversationItemKind.MCP_TOOL_CALL ||
                kind == ConversationItemKind.WEB_SEARCH ||
                kind == ConversationItemKind.IMAGE_VIEW ||
                kind == ConversationItemKind.IMAGE_GENERATION ||
                kind == ConversationItemKind.PLAN_UPDATE ||
                kind == ConversationItemKind.PLAN ||
                kind == ConversationItemKind.FILE_CHANGE
            ConversationSpeaker.USER -> false
        }
    }

    private fun enforceIntraTurnOrder(
        items: List<RemodexConversationItem>,
    ): List<RemodexConversationItem> {
        val indicesByTurn = mutableMapOf<String, MutableList<Int>>()
        items.forEachIndexed { index, item ->
            normalizedIdentifier(item.turnId)?.let { turnId ->
                indicesByTurn.getOrPut(turnId) { mutableListOf() }.add(index)
            }
        }

        if (indicesByTurn.isEmpty()) {
            return items
        }

        val result = items.toMutableList()
        indicesByTurn.values.forEach { indices ->
            if (indices.size <= 1) {
                return@forEach
            }

            val turnItems = indices.map(result::get)
            val sorted = if (hasInterleavedAssistantActivityFlow(turnItems)) {
                turnItems.sortedWith(
                    compareBy<RemodexConversationItem> { it.speaker != ConversationSpeaker.USER }
                        .thenBy(RemodexConversationItem::orderIndex),
                )
            } else {
                turnItems.sortedWith(
                    compareBy<RemodexConversationItem>(::intraTurnPriority)
                        .thenBy(RemodexConversationItem::orderIndex),
                )
            }

            indices.forEachIndexed { position, originalIndex ->
                result[originalIndex] = sorted[position]
            }
        }

        return result
    }

    private fun hasInterleavedAssistantActivityFlow(
        turnItems: List<RemodexConversationItem>,
    ): Boolean {
        val distinctAssistantMessageIds = turnItems
            .asSequence()
            .filter { item -> item.speaker == ConversationSpeaker.ASSISTANT }
            .map(RemodexConversationItem::id)
            .toSet()
        if (distinctAssistantMessageIds.size > 1) {
            return true
        }

        val distinctAssistantItemIds = turnItems
            .asSequence()
            .filter { item -> item.speaker == ConversationSpeaker.ASSISTANT }
            .mapNotNull { item -> normalizedIdentifier(item.itemId) }
            .toSet()
        if (distinctAssistantItemIds.size > 1) {
            return true
        }

        val ordered = turnItems.sortedBy(RemodexConversationItem::orderIndex)
        if (hasLateAnchoredSystemActivityForDifferentAssistantItem(ordered)) {
            return true
        }
        var hasActivityBeforeAssistant = false
        var seenAssistant = false
        ordered.forEach { item ->
            when {
                item.speaker == ConversationSpeaker.ASSISTANT -> seenAssistant = true
                isInterleavableSystemActivity(item) && !seenAssistant -> hasActivityBeforeAssistant = true
                isInterleavableSystemActivity(item) && hasActivityBeforeAssistant -> return true
            }
        }
        return false
    }

    private fun hasLateAnchoredSystemActivityForDifferentAssistantItem(
        ordered: List<RemodexConversationItem>,
    ): Boolean {
        var seenAssistant = false
        var latestAssistantItemId: String? = null
        ordered.forEach { item ->
            when {
                item.speaker == ConversationSpeaker.ASSISTANT -> {
                    seenAssistant = true
                    latestAssistantItemId = normalizedIdentifier(item.itemId)
                }

                seenAssistant &&
                    item.speaker == ConversationSpeaker.SYSTEM &&
                    shouldAnchorSystemActivityAfterAssistant(item) -> {
                    val activityItemId = normalizedIdentifier(item.itemId)
                    if (latestAssistantItemId == null || activityItemId == null || activityItemId != latestAssistantItemId) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun shouldAnchorSystemActivityAfterAssistant(
        item: RemodexConversationItem,
    ): Boolean {
        return shouldPreserveLateSystemActivityChronology(item)
    }

    private fun isInterleavableSystemActivity(
        item: RemodexConversationItem,
    ): Boolean {
        if (item.speaker != ConversationSpeaker.SYSTEM) {
            return false
        }
        return isInterleavableSystemActivityKind(item.kind)
    }

    private fun intraTurnPriority(item: RemodexConversationItem): Int {
        return when (item.speaker) {
            ConversationSpeaker.USER -> 0
            ConversationSpeaker.SYSTEM -> systemTurnOrderingPolicy(item.kind).leadingPriority

            ConversationSpeaker.ASSISTANT -> 4
        }
    }

    private fun collapseThinkingMessages(
        items: List<RemodexConversationItem>,
    ): List<RemodexConversationItem> {
        val result = mutableListOf<RemodexConversationItem>()
        items.forEach { item ->
            if (item.speaker != ConversationSpeaker.SYSTEM || item.kind != ConversationItemKind.REASONING) {
                result += item
                return@forEach
            }

            val previousIndex = latestReusableThinkingIndex(result, item)
            if (previousIndex == -1) {
                result += item
                return@forEach
            }

            val previous = result[previousIndex]
            val incoming = item.text.trim()
            result[previousIndex] = previous.copy(
                text = if (incoming.isNotEmpty()) {
                    mergeThinkingText(existing = previous.text, incoming = incoming)
                } else {
                    previous.text
                },
                isStreaming = item.isStreaming,
                turnId = item.turnId ?: previous.turnId,
                itemId = item.itemId ?: previous.itemId,
                orderIndex = maxOf(previous.orderIndex, item.orderIndex),
            )
        }
        return result
    }

    private fun latestReusableThinkingIndex(
        items: List<RemodexConversationItem>,
        incoming: RemodexConversationItem,
    ): Int {
        for (index in items.indices.reversed()) {
            val candidate = items[index]
            if (candidate.speaker == ConversationSpeaker.ASSISTANT || candidate.speaker == ConversationSpeaker.USER) {
                break
            }
            if (candidate.speaker != ConversationSpeaker.SYSTEM || candidate.kind != ConversationItemKind.REASONING) {
                continue
            }
            if (shouldMergeThinkingRows(candidate, incoming)) {
                return index
            }
        }
        return -1
    }

    private fun shouldMergeThinkingRows(
        previous: RemodexConversationItem,
        incoming: RemodexConversationItem,
    ): Boolean {
        val previousItemId = normalizedIdentifier(previous.itemId)
        val incomingItemId = normalizedIdentifier(incoming.itemId)
        if (previousItemId != null && incomingItemId != null && previousItemId == incomingItemId) {
            return true
        }

        if (!hasCompatibleThinkingTurnScope(previous, incoming)) {
            return false
        }
        if (isPlaceholderThinkingRow(previous)) {
            return true
        }

        val previousHasStableIdentity = hasStableThinkingIdentity(previous)
        val incomingHasStableIdentity = hasStableThinkingIdentity(incoming)
        if (previousHasStableIdentity && incomingHasStableIdentity && previousItemId != null && incomingItemId != null) {
            return false
        }
        if (isPlaceholderThinkingRow(incoming)) {
            return !previousHasStableIdentity
        }
        if (!previousHasStableIdentity || !incomingHasStableIdentity) {
            return thinkingSnapshotsOverlap(previous, incoming)
        }
        return false
    }

    private fun hasCompatibleThinkingTurnScope(
        previous: RemodexConversationItem,
        incoming: RemodexConversationItem,
    ): Boolean {
        val previousTurnId = normalizedIdentifier(previous.turnId)
        val incomingTurnId = normalizedIdentifier(incoming.turnId)
        return previousTurnId == null || incomingTurnId == null || previousTurnId == incomingTurnId
    }

    private fun hasStableThinkingIdentity(item: RemodexConversationItem): Boolean {
        return normalizedIdentifier(item.itemId) != null
    }

    private fun isPlaceholderThinkingRow(item: RemodexConversationItem): Boolean {
        return normalizedThinkingContent(item.text).isEmpty()
    }

    private fun thinkingSnapshotsOverlap(
        previous: RemodexConversationItem,
        incoming: RemodexConversationItem,
    ): Boolean {
        val previousText = normalizedThinkingContent(previous.text)
        val incomingText = normalizedThinkingContent(incoming.text)
        if (previousText.isEmpty() || incomingText.isEmpty()) {
        return previousText.isEmpty() || incomingText.isEmpty()
        }
        val previousLower = previousText.lowercase()
        val incomingLower = incomingText.lowercase()
        return previousLower == incomingLower ||
            previousLower.contains(incomingLower) ||
            incomingLower.contains(previousLower)
    }

    private fun mergeThinkingText(
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
        if (incomingLower in placeholderValues) {
            return existingTrimmed
        }
        if (existingLower in placeholderValues) {
            return incomingTrimmed
        }
        if (incomingLower == existingLower) {
            return incomingTrimmed
        }
        if (incomingTrimmed.contains(existingTrimmed)) {
            return incomingTrimmed
        }
        if (existingTrimmed.contains(incomingTrimmed)) {
            return existingTrimmed
        }
        return "$existingTrimmed\n$incomingTrimmed"
    }

    private fun removeRedundantThinkingCommandActivityMessages(
        items: List<RemodexConversationItem>,
    ): List<RemodexConversationItem> {
        val commandKeysByTurn = buildMap<String, Set<String>> {
            items.forEach { item ->
                val turnId = normalizedIdentifier(item.turnId)
                val commandKey = commandActivityKey(item.text)
                if (
                    item.speaker == ConversationSpeaker.SYSTEM &&
                    item.kind == ConversationItemKind.COMMAND_EXECUTION &&
                    turnId != null &&
                    commandKey != null
                ) {
                    put(turnId, (get(turnId).orEmpty() + commandKey))
                }
            }
        }
        if (commandKeysByTurn.isEmpty()) {
            return items
        }

        return items.filter { item ->
            val turnId = normalizedIdentifier(item.turnId)
            val commandKeys = turnId?.let(commandKeysByTurn::get)
            if (
                item.speaker != ConversationSpeaker.SYSTEM ||
                item.kind != ConversationItemKind.REASONING ||
                commandKeys.isNullOrEmpty()
            ) {
                return@filter true
            }

            val lines = normalizedThinkingContent(item.text)
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
            if (lines.isEmpty()) {
                return@filter true
            }

            !lines.all { line ->
                commandActivityKey(line)?.let(commandKeys::contains) == true
            }
        }
    }

    private fun commandActivityKey(text: String): String? {
        val tokens = text.trim()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
        if (tokens.size < 2) {
            return null
        }
        val status = tokens.first().lowercase()
        if (status !in setOf("running", "completed", "failed", "stopped")) {
            return null
        }
        val command = tokens.drop(1).joinToString(" ").trim().lowercase()
        return command.ifEmpty { null }
    }

    private fun removeDuplicateAssistantMessages(
        items: List<RemodexConversationItem>,
    ): List<RemodexConversationItem> {
        val seenKeys = mutableSetOf<String>()
        val seenNoTurnByText = mutableMapOf<String, Long>()
        val seenTurnText = mutableMapOf<String, AssistantTurnTextObservation>()
        val result = mutableListOf<RemodexConversationItem>()

        items.forEach { item ->
            if (item.speaker != ConversationSpeaker.ASSISTANT) {
                result += item
                return@forEach
            }

            val normalizedText = item.text.trim()
            if (normalizedText.isEmpty()) {
                result += item
                return@forEach
            }

            val turnId = normalizedIdentifier(item.turnId)
            if (turnId != null) {
                val dedupeScope = normalizedIdentifier(item.itemId)
                val key = "$turnId|${dedupeScope ?: "no-item"}|$normalizedText"
                if (seenKeys.contains(key)) {
                    return@forEach
                }

                val hasStableIdentity = dedupeScope != null
                val turnTextKey = "$turnId|$normalizedText"
                val previous = seenTurnText[turnTextKey]
                if (
                    previous != null &&
                    kotlin.math.abs(item.orderIndex - previous.orderIndex) <= 2L &&
                    (!previous.hasStableIdentity || !hasStableIdentity)
                ) {
                    return@forEach
                }

                seenKeys += key
                seenTurnText[turnTextKey] = AssistantTurnTextObservation(
                    orderIndex = item.orderIndex,
                    hasStableIdentity = hasStableIdentity,
                )
                result += item
                return@forEach
            }

            val previousOrderIndex = seenNoTurnByText[normalizedText]
            if (previousOrderIndex != null && kotlin.math.abs(item.orderIndex - previousOrderIndex) <= 2L) {
                return@forEach
            }
            seenNoTurnByText[normalizedText] = item.orderIndex
            result += item
        }

        return result
    }

    private fun matchesStreamingMessageTarget(
        item: RemodexConversationItem,
        messageId: String,
        turnId: String,
        itemId: String?,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): Boolean {
        if (item.id == messageId) {
            return true
        }
        if (item.kind != kind || item.speaker != speaker) {
            return false
        }

        val normalizedTurnId = turnId.trim().takeIf(String::isNotEmpty)
        if (normalizedTurnId != null && item.turnId != normalizedTurnId) {
            return false
        }
        if (item.itemId == itemId) {
            return normalizedTurnId == null || item.turnId == normalizedTurnId
        }

        if (normalizedTurnId == null || itemId.isNullOrBlank() || item.itemId != null) {
            return false
        }

        return item.id == turnScopedStreamingMessageId(
            speaker = speaker,
            kind = kind,
            turnId = normalizedTurnId,
        )
    }

    private fun turnScopedStreamingMessageId(
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
        turnId: String,
    ): String? {
        val normalizedTurnId = turnId.trim().takeIf(String::isNotEmpty) ?: return null
        val prefix = when (speaker) {
            ConversationSpeaker.ASSISTANT -> {
                if (kind == ConversationItemKind.CHAT) {
                    "assistant"
                } else {
                    null
                }
            }

            ConversationSpeaker.SYSTEM -> when (kind) {
                ConversationItemKind.REASONING -> "reasoning"
                ConversationItemKind.TOOL_ACTIVITY -> "toolactivity"
                ConversationItemKind.MCP_TOOL_CALL -> "mcptoolcall"
                ConversationItemKind.WEB_SEARCH -> "websearch"
                ConversationItemKind.COMMAND_EXECUTION -> "commandexecution"
                ConversationItemKind.CONTEXT_COMPACTION -> "contextcompaction"
                ConversationItemKind.IMAGE_VIEW -> "imageview"
                ConversationItemKind.IMAGE_GENERATION -> "imagegeneration"
                ConversationItemKind.FILE_CHANGE -> "filechange"
                ConversationItemKind.PLAN_UPDATE -> "planupdate"
                ConversationItemKind.PLAN -> "plan"
                ConversationItemKind.USER_INPUT_PROMPT -> "userinputprompt"
                ConversationItemKind.SUBAGENT_ACTION -> "subagentaction"
                ConversationItemKind.CHAT -> null
            }

            ConversationSpeaker.USER -> null
        } ?: return null
        return "$prefix-$normalizedTurnId"
    }

    private fun preservedStreamingOrderIndex(
        existing: RemodexConversationItem,
        incomingOrderIndex: Long,
    ): Long {
        if (
            existing.speaker == ConversationSpeaker.ASSISTANT &&
            existing.kind == ConversationItemKind.CHAT
        ) {
            return existing.orderIndex
        }
        return maxOf(existing.orderIndex, incomingOrderIndex)
    }

    private fun removeDuplicateFileChangeMessages(
        items: List<RemodexConversationItem>,
    ): List<RemodexConversationItem> {
        val signatures = items.map(::fileChangeDedupSignature)
        val supersededIndices = mutableSetOf<Int>()

        for (olderIndex in items.indices) {
            val olderSignature = signatures[olderIndex] ?: continue
            for (newerIndex in items.indices) {
                if (newerIndex <= olderIndex) {
                    continue
                }
                val newerSignature = signatures[newerIndex] ?: continue
                if (fileChangeMessage(newerSignature, olderSignature)) {
                    supersededIndices += olderIndex
                    break
                }
            }
        }

        return items.filterIndexed { index, _ ->
            signatures[index] == null || index !in supersededIndices
        }
    }

    private fun fileChangeDedupSignature(
        item: RemodexConversationItem,
    ): FileChangeDedupSignature? {
        if (item.speaker != ConversationSpeaker.SYSTEM || item.kind != ConversationItemKind.FILE_CHANGE) {
            return null
        }
        val normalizedText = item.text.trim()
        val paths = extractFileChangePaths(item.text)
        val key = normalizedText.takeIf(String::isNotEmpty)
        if (key == null && paths.isEmpty()) {
            return null
        }
        return FileChangeDedupSignature(
            turnId = normalizedIdentifier(item.turnId),
            key = key,
            paths = paths,
            isStreaming = item.isStreaming,
            hasNonZeroTotals = fileChangeHasNonZeroTotals(item.text),
        )
    }

    private fun extractFileChangePaths(text: String): Set<String> {
        val keys = mutableSetOf<String>()
        text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { line ->
                var candidate = line
                if (candidate.startsWith("- ") || candidate.startsWith("* ") || candidate.startsWith("• ")) {
                    candidate = candidate.drop(2).trim()
                }

                when {
                    candidate.startsWith("Path:", ignoreCase = true) -> {
                        normalizedFileChangePathKey(candidate.drop(5))?.let(keys::add)
                    }

                    candidate.startsWith("+++ ") || candidate.startsWith("--- ") -> {
                        normalizedFileChangePathKey(candidate.drop(4))?.let(keys::add)
                    }

                    candidate.startsWith("diff --git ") -> {
                        val components = candidate.split(' ').filter(String::isNotBlank)
                        if (components.size >= 4) {
                            normalizedFileChangePathKey(components[3])?.let(keys::add)
                        }
                    }

                    else -> {
                        val lower = candidate.lowercase()
                        val actionVerb = fileChangeActionPrefixes.firstOrNull(lower::startsWith)
                        if (actionVerb != null) {
                            val rawPath = inlineTotalsRegex.replace(
                                candidate.drop(actionVerb.length).trim(),
                                "",
                            ).trim()
                            normalizedFileChangePathKey(rawPath)?.let(keys::add)
                        }
                    }
                }
            }

        if (keys.isNotEmpty()) {
            return keys
        }

        return filePathRegex.findAll(text)
            .mapNotNull { match -> normalizedFileChangePathKey(match.value) }
            .toSet()
    }

    private fun normalizedFileChangePathKey(rawPath: String): String? {
        var normalized = rawPath.trim()
        if (normalized.isEmpty() || normalized == "/dev/null") {
            return null
        }

        normalized = normalized
            .replace('\\', '/')
            .replace("`", "")
            .replace("\"", "")
            .replace("'", "")

        if (normalized.startsWith("(") && normalized.endsWith(")") && normalized.length > 2) {
            normalized = normalized.drop(1).dropLast(1)
        }
        if (normalized.startsWith("a/") || normalized.startsWith("b/")) {
            normalized = normalized.drop(2)
        }
        if (normalized.startsWith("./")) {
            normalized = normalized.drop(2)
        }

        normalized = lineNumberSuffixRegex.replace(normalized, "")
        while (normalized.isNotEmpty() && normalized.last() in trailingPathPunctuation) {
            normalized = normalized.dropLast(1)
        }

        normalized = normalized.trim().replace(duplicateSlashRegex, "/")
        return normalized.takeIf(String::isNotEmpty)?.lowercase()
    }

    private fun fileChangeHasNonZeroTotals(text: String): Boolean {
        if (text.lineSequence().any { line -> inlineTotalsRegex.containsMatchIn(line.trim()) }) {
            return true
        }
        return FileChangeRenderParser.renderState(text).summary?.entries?.any { entry ->
            entry.additions > 0 || entry.deletions > 0
        } == true
    }

    private fun fileChangePathsContainedBy(
        olderPaths: Set<String>,
        newerPaths: Set<String>,
    ): Boolean {
        return olderPaths.all { olderPath ->
            newerPaths.any { newerPath -> fileChangePathMatches(lhs = olderPath, rhs = newerPath) }
        }
    }

    private fun fileChangePathMatches(
        lhs: String,
        rhs: String,
    ): Boolean {
        if (lhs == rhs) {
            return true
        }

        val (longer, shorter) = if (lhs.length >= rhs.length) lhs to rhs else rhs to lhs
        if (!longer.endsWith("/$shorter")) {
            return false
        }

        return shorter.count { it == '/' } >= 1
    }

    private fun fileChangeMessage(
        newer: FileChangeDedupSignature,
        older: FileChangeDedupSignature,
    ): Boolean {
        val sameTurn = when {
            newer.turnId != null && older.turnId != null -> newer.turnId == older.turnId
            else -> newer.turnId == null || older.turnId == null
        }
        if (!sameTurn) {
            return false
        }
        if (newer.key != null && older.key != null && newer.key == older.key) {
            return true
        }
        if (newer.paths.isEmpty() || older.paths.isEmpty()) {
            return false
        }
        val newerContainsOlderPaths = fileChangePathsContainedBy(
            olderPaths = older.paths,
            newerPaths = newer.paths,
        )
        if (older.turnId == null && newer.turnId != null && newerContainsOlderPaths) {
            return true
        }
        if (older.isStreaming && !newer.isStreaming && newerContainsOlderPaths) {
            return true
        }
        if (newerContainsOlderPaths && newer.hasNonZeroTotals && !older.hasNonZeroTotals) {
            return true
        }
        return false
    }

    private fun removeDuplicateSubagentActionMessages(
        items: List<RemodexConversationItem>,
    ): List<RemodexConversationItem> {
        val result = mutableListOf<RemodexConversationItem>()
        items.forEach { item ->
            val action = item.subagentAction
            if (
                action == null ||
                item.speaker != ConversationSpeaker.SYSTEM ||
                item.kind != ConversationItemKind.SUBAGENT_ACTION
            ) {
                result += item
                return@forEach
            }

            val previous = result.lastOrNull()
            val previousAction = previous?.subagentAction
            if (
                previous == null ||
                previousAction == null ||
                !shouldMergeSubagentActionMessages(previous, previousAction, item, action)
            ) {
                result += item
                return@forEach
            }

            result[result.lastIndex] = preferredSubagentActionMessage(previous, item)
        }
        return result
    }

    private fun shouldMergeSubagentActionMessages(
        previous: RemodexConversationItem,
        previousAction: com.emanueledipietro.remodex.model.RemodexSubagentAction,
        incoming: RemodexConversationItem,
        incomingAction: com.emanueledipietro.remodex.model.RemodexSubagentAction,
    ): Boolean {
        if (
            previous.speaker != ConversationSpeaker.SYSTEM ||
            previous.kind != ConversationItemKind.SUBAGENT_ACTION ||
            normalizedIdentifier(previous.turnId) != normalizedIdentifier(incoming.turnId) ||
            previousAction.normalizedTool != incomingAction.normalizedTool ||
            previous.text != incoming.text
        ) {
            return false
        }

        val previousItemId = normalizedIdentifier(previous.itemId) ?: return false
        val incomingItemId = normalizedIdentifier(incoming.itemId) ?: return false
        if (previousItemId != incomingItemId) {
            return false
        }

        val previousRows = previousAction.agentRows
        val incomingRows = incomingAction.agentRows
        if (previousRows.isEmpty() && incomingRows.isNotEmpty()) {
            return true
        }
        return previousRows == incomingRows
    }

    private fun preferredSubagentActionMessage(
        previous: RemodexConversationItem,
        incoming: RemodexConversationItem,
    ): RemodexConversationItem {
        val previousRows = previous.subagentAction?.agentRows.orEmpty()
        val incomingRows = incoming.subagentAction?.agentRows.orEmpty()
        if (previousRows.isEmpty() && incomingRows.isNotEmpty()) {
            return incoming
        }
        if (incoming.isStreaming != previous.isStreaming) {
            return if (incoming.isStreaming) previous else incoming
        }
        return if (incoming.orderIndex >= previous.orderIndex) incoming else previous
    }

    private fun normalizedThinkingContent(text: String): String {
        return ThinkingDisclosureParser.normalizedThinkingContent(text)
    }

    private fun normalizedIdentifier(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.ifEmpty { null }
    }

    private data class AssistantTurnTextObservation(
        val orderIndex: Long,
        val hasStableIdentity: Boolean,
    )

    private data class FileChangeDedupSignature(
        val turnId: String?,
        val key: String?,
        val paths: Set<String>,
        val isStreaming: Boolean,
        val hasNonZeroTotals: Boolean,
    )

    private val filePathRegex = Regex("""(?<![\w/])(?:[\w.-]+/)*[\w.-]+\.[A-Za-z0-9_-]+""")
    private val inlineTotalsRegex = Regex("""\s*[+\uFF0B]\s*\d+\s*[-\u2212\u2013\u2014\uFE63\uFF0D]\s*\d+\s*$""")
    private val lineNumberSuffixRegex = Regex(""":\d+(?::\d+)?$""")
    private val duplicateSlashRegex = Regex("/+")
    private val trailingPathPunctuation = setOf(',', '.', ';')
    private val fileChangeActionPrefixes = listOf(
        "edited ",
        "updated ",
        "added ",
        "created ",
        "deleted ",
        "removed ",
        "renamed ",
        "moved ",
    )
}
