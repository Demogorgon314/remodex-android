package com.emanueledipietro.remodex.feature.turn

import android.text.Spanned
import com.emanueledipietro.remodex.model.RemodexCommandExecutionDetails
import com.emanueledipietro.remodex.model.RemodexCommandExecutionLiveStatus
import com.emanueledipietro.remodex.model.RemodexConversationItem
import java.util.LinkedHashMap

private class BoundedRenderCache<K, V>(
    private val maxEntries: Int,
) {
    private val values = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    fun getOrPut(
        key: K,
        block: () -> V,
    ): V = synchronized(values) {
        values[key] ?: block().also { computed ->
            values[key] = computed
        }
    }
}

private data class FileChangeSheetCacheKey(
    val messageId: String,
    val text: String,
)

private data class CommandExecutionPresentationCacheKey(
    val text: String,
    val isStreaming: Boolean,
    val fullCommand: String?,
    val liveStatus: RemodexCommandExecutionLiveStatus?,
    val exitCode: Int?,
    val durationMs: Int?,
)

private data class MarkdownCodeBlockHighlightedTextCacheKey(
    val token: ConversationMarkdownCodeBlockRenderToken,
    val normalizedLanguage: String?,
)

private data class ConversationMessageRowRenderModelCacheKey(
    val messageId: String,
    val text: String,
    val isStreaming: Boolean,
    val blockDiffText: String?,
    val blockDiffEntries: List<FileChangeSummaryEntry>?,
    val commandExecutionPresentationKey: CommandExecutionPresentationCacheKey?,
)

internal data class ConversationMessageRowRenderModel(
    val assistantBlockDiffPresentation: FileChangeSheetPresentation? = null,
    val thinkingText: String = "",
    val thinkingActivityPreview: String? = null,
    val thinkingDisclosureContent: ThinkingDisclosureContent? = null,
    val fileChangeRenderState: FileChangeRenderState? = null,
    val fileChangeDetailsPresentation: FileChangeSheetPresentation? = null,
    val fileChangeGroupedEntries: List<FileChangeGroup> = emptyList(),
    val commandExecutionStatuses: List<CommandExecutionStatusPresentation> = emptyList(),
)

private object ConversationRenderCaches {
    val thinkingText = BoundedRenderCache<String, String>(maxEntries = 128)
    val thinkingActivityPreview = BoundedRenderCache<String, String?>(maxEntries = 128)
    val thinkingDisclosureContent = BoundedRenderCache<String, ThinkingDisclosureContent>(maxEntries = 64)
    val fileChangeRenderState = BoundedRenderCache<String, FileChangeRenderState>(maxEntries = 96)
    val fileChangeSheetPresentation = BoundedRenderCache<FileChangeSheetCacheKey, FileChangeSheetPresentation?>(maxEntries = 96)
    val fileChangeGroupedEntries = BoundedRenderCache<String, List<FileChangeGroup>>(maxEntries = 96)
    val commandExecutionPresentations =
        BoundedRenderCache<CommandExecutionPresentationCacheKey, List<CommandExecutionStatusPresentation>>(maxEntries = 128)
    val markdownSegments = BoundedRenderCache<String, List<ConversationMarkdownSegment>>(maxEntries = 128)
    val markdownSpans = BoundedRenderCache<ConversationMarkdownTextRenderToken, Spanned>(maxEntries = 96)
    val markdownCodeBlockHighlightedText =
        BoundedRenderCache<MarkdownCodeBlockHighlightedTextCacheKey, CharSequence>(maxEntries = 96)
    val messageRowRenderModels =
        BoundedRenderCache<ConversationMessageRowRenderModelCacheKey, ConversationMessageRowRenderModel>(maxEntries = 160)
}

internal fun cachedThinkingText(
    text: String,
): String = ConversationRenderCaches.thinkingText.getOrPut(text) {
    ThinkingDisclosureParser.normalizedThinkingContent(text)
}

internal fun cachedThinkingActivityPreview(
    thinkingText: String,
): String? = ConversationRenderCaches.thinkingActivityPreview.getOrPut(thinkingText) {
    if (thinkingText.isEmpty()) {
        null
    } else {
        ThinkingDisclosureParser.compactActivityPreview(thinkingText)
    }
}

internal fun cachedThinkingDisclosureContent(
    thinkingText: String,
): ThinkingDisclosureContent = ConversationRenderCaches.thinkingDisclosureContent.getOrPut(thinkingText) {
    ThinkingDisclosureParser.parse(thinkingText)
}

internal fun cachedFileChangeRenderState(
    text: String,
): FileChangeRenderState = ConversationRenderCaches.fileChangeRenderState.getOrPut(text) {
    FileChangeRenderParser.renderState(text)
}

internal fun cachedTimelineFileChangeSheetPresentation(
    messageId: String,
    renderState: FileChangeRenderState,
): FileChangeSheetPresentation? {
    val normalizedBodyText = renderState.bodyText.trim()
    if (normalizedBodyText.isEmpty()) {
        return null
    }
    return ConversationRenderCaches.fileChangeSheetPresentation.getOrPut(
        FileChangeSheetCacheKey(
            messageId = messageId,
            text = normalizedBodyText,
        ),
    ) {
        buildTimelineFileChangeSheetPresentation(
            messageId = messageId,
            renderState = renderState,
        )
    }
}

internal fun cachedFileChangeGroupedEntries(
    renderState: FileChangeRenderState,
): List<FileChangeGroup> {
    val summaryEntries = if (renderState.actionEntries.isNotEmpty()) {
        renderState.actionEntries
    } else {
        renderState.summary?.entries.orEmpty()
    }
    if (summaryEntries.isEmpty()) {
        return emptyList()
    }
    val key = buildString {
        summaryEntries.forEach { entry ->
            append(entry.compactPath)
            append('|')
            append(entry.action?.name.orEmpty())
            append('|')
            append(entry.additions)
            append('|')
            append(entry.deletions)
            append('\n')
        }
    }
    return ConversationRenderCaches.fileChangeGroupedEntries.getOrPut(key) {
        FileChangeRenderParser.grouped(summaryEntries)
    }
}

internal fun cachedCommandExecutionStatusPresentations(
    item: RemodexConversationItem,
    details: RemodexCommandExecutionDetails?,
): List<CommandExecutionStatusPresentation> {
    return ConversationRenderCaches.commandExecutionPresentations.getOrPut(
        CommandExecutionPresentationCacheKey(
            text = item.text,
            isStreaming = item.isStreaming,
            fullCommand = details?.fullCommand,
            liveStatus = details?.liveStatus,
            exitCode = details?.exitCode,
            durationMs = details?.durationMs,
        ),
    ) {
        resolvedCommandExecutionStatusPresentations(
            item = item,
            details = details,
        )
    }
}

internal fun cachedConversationMarkdownSegments(
    text: String,
): List<ConversationMarkdownSegment> = ConversationRenderCaches.markdownSegments.getOrPut(text) {
    parseConversationMarkdownSegments(text)
}

internal fun cachedConversationMarkdownSpanned(
    token: ConversationMarkdownTextRenderToken,
    block: () -> Spanned,
): Spanned = ConversationRenderCaches.markdownSpans.getOrPut(token, block)

internal fun cachedConversationMarkdownCodeBlockHighlightedText(
    token: ConversationMarkdownCodeBlockRenderToken,
    normalizedLanguage: String?,
    block: () -> CharSequence,
): CharSequence = ConversationRenderCaches.markdownCodeBlockHighlightedText.getOrPut(
    MarkdownCodeBlockHighlightedTextCacheKey(
        token = token,
        normalizedLanguage = normalizedLanguage,
    ),
    block,
)

internal fun cachedConversationMessageRowRenderModel(
    item: RemodexConversationItem,
    blockDiffText: String?,
    blockDiffEntries: List<FileChangeSummaryEntry>?,
    commandExecutionDetails: RemodexCommandExecutionDetails?,
): ConversationMessageRowRenderModel {
    val commandExecutionPresentationKey = if (item.kind == com.emanueledipietro.remodex.model.ConversationItemKind.COMMAND_EXECUTION) {
        CommandExecutionPresentationCacheKey(
            text = item.text,
            isStreaming = item.isStreaming,
            fullCommand = commandExecutionDetails?.fullCommand,
            liveStatus = commandExecutionDetails?.liveStatus,
            exitCode = commandExecutionDetails?.exitCode,
            durationMs = commandExecutionDetails?.durationMs,
        )
    } else {
        null
    }
    return ConversationRenderCaches.messageRowRenderModels.getOrPut(
        ConversationMessageRowRenderModelCacheKey(
            messageId = item.id,
            text = item.text,
            isStreaming = item.isStreaming,
            blockDiffText = blockDiffText?.trim()?.takeIf(String::isNotEmpty),
            blockDiffEntries = blockDiffEntries?.takeIf(List<FileChangeSummaryEntry>::isNotEmpty),
            commandExecutionPresentationKey = commandExecutionPresentationKey,
        ),
    ) {
        val normalizedBlockDiffText = blockDiffText?.trim().orEmpty()
        val normalizedBlockDiffEntries = blockDiffEntries.orEmpty()
        val assistantBlockDiffPresentation = if (
            normalizedBlockDiffText.isBlank() || normalizedBlockDiffEntries.isEmpty()
        ) {
            null
        } else {
            val renderState = FileChangeRenderState(
                summary = FileChangeSummary(normalizedBlockDiffEntries),
                actionEntries = normalizedBlockDiffEntries.filter { entry -> entry.action != null },
                bodyText = normalizedBlockDiffText,
            )
            buildTimelineFileChangeSheetPresentation(
                messageId = item.id,
                renderState = renderState,
            )
        }
        val thinkingText = if (item.kind == com.emanueledipietro.remodex.model.ConversationItemKind.REASONING) {
            cachedThinkingText(item.text)
        } else {
            ""
        }
        val thinkingActivityPreview = if (thinkingText.isEmpty()) {
            null
        } else {
            cachedThinkingActivityPreview(thinkingText)
        }
        val thinkingDisclosureContent = if (
            item.kind == com.emanueledipietro.remodex.model.ConversationItemKind.REASONING &&
                !item.isStreaming &&
                thinkingActivityPreview == null &&
                thinkingText.isNotEmpty()
        ) {
            cachedThinkingDisclosureContent(thinkingText)
        } else {
            null
        }
        val fileChangeRenderState = if (item.kind == com.emanueledipietro.remodex.model.ConversationItemKind.FILE_CHANGE) {
            cachedFileChangeRenderState(item.text)
        } else {
            null
        }
        val fileChangeDetailsPresentation = if (fileChangeRenderState != null) {
            cachedTimelineFileChangeSheetPresentation(
                messageId = item.id,
                renderState = fileChangeRenderState,
            )
        } else {
            null
        }
        val fileChangeGroupedEntries = if (fileChangeRenderState != null) {
            cachedFileChangeGroupedEntries(fileChangeRenderState)
        } else {
            emptyList()
        }
        val commandExecutionStatuses = if (
            item.kind == com.emanueledipietro.remodex.model.ConversationItemKind.COMMAND_EXECUTION
        ) {
            cachedCommandExecutionStatusPresentations(
                item = item,
                details = commandExecutionDetails,
            )
        } else {
            emptyList()
        }
        ConversationMessageRowRenderModel(
            assistantBlockDiffPresentation = assistantBlockDiffPresentation,
            thinkingText = thinkingText,
            thinkingActivityPreview = thinkingActivityPreview,
            thinkingDisclosureContent = thinkingDisclosureContent,
            fileChangeRenderState = fileChangeRenderState,
            fileChangeDetailsPresentation = fileChangeDetailsPresentation,
            fileChangeGroupedEntries = fileChangeGroupedEntries,
            commandExecutionStatuses = commandExecutionStatuses,
        )
    }
}
