package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.data.threads.TimelineMutation
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem

object TurnTimelineReducer {
    private const val ManualPushResetMarkerItemId = "git.push.reset.marker"

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

    fun project(items: List<RemodexConversationItem>): List<RemodexConversationItem> {
        if (items.isEmpty()) {
            return emptyList()
        }
        val visibleItems = removeHiddenSystemMarkers(items)
        val reordered = enforceIntraTurnOrder(visibleItems)
        val collapsedThinking = collapseThinkingMessages(reordered)
        val withoutCompletedThinkingPlaceholders = removeCompletedThinkingPlaceholders(collapsedThinking)
        val withoutCommandThinkingEchoes = removeRedundantThinkingCommandActivityMessages(withoutCompletedThinkingPlaceholders)
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
        val nextText = mergeText(existing.text, delta)
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
            orderIndex = maxOf(existing.orderIndex, orderIndex),
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

    private fun mergeDeltaText(
        existing: String,
        incoming: String,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): String {
        if (speaker == ConversationSpeaker.ASSISTANT || kind == ConversationItemKind.REASONING) {
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
    ): List<RemodexConversationItem> {
        val trimmedLine = line.trim()
        val existingIndex = items.indexOfFirst { item ->
            (
                item.id == messageId &&
                    item.kind == ConversationItemKind.TOOL_ACTIVITY
            ) || (
                item.turnId == turnId &&
                    item.itemId == itemId &&
                    item.kind == ConversationItemKind.TOOL_ACTIVITY
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
            )
        } else {
            val existing = items[existingIndex]
            existing.copy(
                text = mergeToolActivityText(existing.text, trimmedLine),
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

        val placeholderValues = setOf("thinking...")
        val existingTrimmed = existing.trim()
        val incomingTrimmed = incoming.trim()
        val existingLower = existingTrimmed.lowercase()
        val incomingLower = incomingTrimmed.lowercase()
        if (placeholderValues.contains(incomingLower)) {
            return existing
        }
        if (placeholderValues.contains(existingLower)) {
            return incoming
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
        if (existingLower == incomingLower || incomingTrimmed in existingTrimmed) {
            return existing
        }
        if (existingTrimmed in incomingTrimmed) {
            return incoming
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

    private fun normalizeIncomingDelta(
        delta: String,
        speaker: ConversationSpeaker,
        kind: ConversationItemKind,
    ): String {
        return if (speaker == ConversationSpeaker.ASSISTANT && kind == ConversationItemKind.CHAT) {
            delta
        } else {
            delta.trim()
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
        val distinctAssistantItemIds = turnItems
            .asSequence()
            .filter { item -> item.speaker == ConversationSpeaker.ASSISTANT }
            .mapNotNull { item -> normalizedIdentifier(item.itemId) }
            .toSet()
        if (distinctAssistantItemIds.size > 1) {
            return true
        }

        val ordered = turnItems.sortedBy(RemodexConversationItem::orderIndex)
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

    private fun isInterleavableSystemActivity(
        item: RemodexConversationItem,
    ): Boolean {
        if (item.speaker != ConversationSpeaker.SYSTEM) {
            return false
        }
        return when (item.kind) {
            ConversationItemKind.REASONING,
            ConversationItemKind.TOOL_ACTIVITY,
            ConversationItemKind.COMMAND_EXECUTION,
            -> true

            ConversationItemKind.CHAT,
            ConversationItemKind.FILE_CHANGE,
            ConversationItemKind.SUBAGENT_ACTION,
            ConversationItemKind.PLAN,
            ConversationItemKind.USER_INPUT_PROMPT,
            -> false
        }
    }

    private fun intraTurnPriority(item: RemodexConversationItem): Int {
        return when (item.speaker) {
            ConversationSpeaker.USER -> 0
            ConversationSpeaker.SYSTEM -> when (item.kind) {
                ConversationItemKind.REASONING -> 1
                ConversationItemKind.TOOL_ACTIVITY -> 2
                ConversationItemKind.COMMAND_EXECUTION -> 2
                ConversationItemKind.SUBAGENT_ACTION -> 3
                ConversationItemKind.CHAT,
                ConversationItemKind.PLAN,
                -> 4

                ConversationItemKind.FILE_CHANGE -> 5
                ConversationItemKind.USER_INPUT_PROMPT -> 6
            }

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

    private fun removeCompletedThinkingPlaceholders(
        items: List<RemodexConversationItem>,
    ): List<RemodexConversationItem> {
        return items.filterNot(::shouldPruneThinkingRowAfterCompletion)
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

    private fun shouldPruneThinkingRowAfterCompletion(
        item: RemodexConversationItem,
    ): Boolean {
        if (item.speaker != ConversationSpeaker.SYSTEM || item.kind != ConversationItemKind.REASONING || item.isStreaming) {
            return false
        }
        val trimmedText = item.text.trim()
        if (trimmedText.isEmpty()) {
            return true
        }
        return normalizedThinkingContent(trimmedText).isEmpty()
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
        )
    }

    private fun extractFileChangePaths(text: String): Set<String> {
        return filePathRegex.findAll(text)
            .map { match -> match.value }
            .filter { value -> value.contains('.') }
            .toSet()
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
        if (older.turnId == null && newer.turnId != null && older.paths.all(newer.paths::contains)) {
            return true
        }
        if (older.isStreaming && !newer.isStreaming && older.paths.all(newer.paths::contains)) {
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
    )

    private val filePathRegex = Regex("""(?<![\w/])(?:[\w.-]+/)*[\w.-]+\.[A-Za-z0-9_-]+""")
}
