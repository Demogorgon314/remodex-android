package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.model.RemodexUnifiedPatchParser

internal enum class FileChangeAction(
    val label: String,
) {
    EDITED("Edited"),
    ADDED("Added"),
    DELETED("Deleted"),
    RENAMED("Renamed");

    companion object {
        fun fromKind(kind: String?): FileChangeAction? {
            return when (kind?.trim()?.lowercase()) {
                "add", "added", "create", "created" -> ADDED
                "delete", "deleted", "remove", "removed" -> DELETED
                "rename", "renamed", "move", "moved" -> RENAMED
                "update", "updated", "edit", "edited" -> EDITED
                else -> null
            }
        }
    }
}

internal data class FileChangeSummaryEntry(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val action: FileChangeAction?,
) {
    val compactPath: String
        get() = path.substringAfterLast('/')

    val fullDirectoryPath: String?
        get() = path.substringBeforeLast('/', missingDelimiterValue = "").takeIf(String::isNotEmpty)
}

internal data class FileChangeSummary(
    val entries: List<FileChangeSummaryEntry>,
)

internal data class FileChangeRenderState(
    val summary: FileChangeSummary?,
    val actionEntries: List<FileChangeSummaryEntry>,
    val bodyText: String,
)

internal data class FileChangeGroup(
    val key: String,
    val entries: List<FileChangeSummaryEntry>,
)

internal data class PerFileDiffChunk(
    val id: String,
    val path: String,
    val action: FileChangeAction,
    val additions: Int,
    val deletions: Int,
    val diffCode: String,
) {
    val compactPath: String
        get() = path.substringAfterLast('/')

    val fullDirectoryPath: String?
        get() = path.substringBeforeLast('/', missingDelimiterValue = "").takeIf(String::isNotEmpty)
}

internal object FileChangeRenderParser {
    fun renderState(sourceText: String): FileChangeRenderState {
        val summary = parseSummary(sourceText)
        val actionEntries = summary?.entries?.filter { entry -> entry.action != null }.orEmpty()
        return FileChangeRenderState(
            summary = summary,
            actionEntries = actionEntries,
            bodyText = sourceText,
        )
    }

    fun grouped(entries: List<FileChangeSummaryEntry>): List<FileChangeGroup> {
        if (entries.isEmpty()) {
            return emptyList()
        }
        val order = mutableListOf<String>()
        val groupedEntries = linkedMapOf<String, MutableList<FileChangeSummaryEntry>>()
        entries.forEach { entry ->
            val key = entry.action?.label ?: FileChangeAction.EDITED.label
            if (key !in groupedEntries) {
                order += key
            }
            groupedEntries.getOrPut(key) { mutableListOf() } += entry
        }
        return order.map { key ->
            FileChangeGroup(
                key = key,
                entries = groupedEntries[key].orEmpty(),
            )
        }
    }

    fun diffChunks(
        bodyText: String,
        entries: List<FileChangeSummaryEntry>,
    ): List<PerFileDiffChunk> {
        if (bodyText.isBlank()) {
            return emptyList()
        }
        val sectionChunks = parseSectionChunks(bodyText, entries)
        if (sectionChunks.isNotEmpty()) {
            return sectionChunks
        }
        val patchChunks = parseUnifiedPatchChunks(bodyText, entries)
        if (patchChunks.isNotEmpty()) {
            return patchChunks
        }
        return entries.mapIndexed { index, entry ->
            PerFileDiffChunk(
                id = "$index-${entry.path}",
                path = entry.path,
                action = entry.action ?: FileChangeAction.EDITED,
                additions = entry.additions,
                deletions = entry.deletions,
                diffCode = "",
            )
        }
    }

    private fun parseSummary(sourceText: String): FileChangeSummary? {
        val renderedEntries = parseRenderedSections(sourceText)
        if (renderedEntries.isNotEmpty()) {
            return FileChangeSummary(entries = renderedEntries)
        }
        val patchEntries = parseUnifiedPatchEntries(sourceText)
        if (patchEntries.isNotEmpty()) {
            return FileChangeSummary(entries = patchEntries)
        }
        return null
    }

    private fun parseRenderedSections(sourceText: String): List<FileChangeSummaryEntry> {
        val sections = sourceText
            .split("\n\n---\n\n")
            .filter { section -> section.isNotBlank() }
        if (sections.isEmpty()) {
            return emptyList()
        }
        return sections.mapNotNull { section ->
            val lines = section.lines()
            val path = lines.firstNotNullOfOrNull(::parsePathLine)
            val kind = lines.firstNotNullOfOrNull(::parseKindLine)
            val diffCode = extractFencedCode(lines)
            when {
                path != null && diffCode != null && RemodexUnifiedPatchParser.looksLikePatchText(diffCode) -> {
                    val patchEntry = parsePatchChunk(diffCode).firstOrNull()
                    FileChangeSummaryEntry(
                        path = patchEntry?.path ?: path,
                        additions = patchEntry?.additions ?: 0,
                        deletions = patchEntry?.deletions ?: 0,
                        action = FileChangeAction.fromKind(kind) ?: patchEntry?.action ?: FileChangeAction.EDITED,
                    )
                }

                path != null -> FileChangeSummaryEntry(
                    path = path,
                    additions = 0,
                    deletions = 0,
                    action = FileChangeAction.fromKind(kind) ?: FileChangeAction.EDITED,
                )

                else -> null
            }
        }
    }

    private fun parseUnifiedPatchEntries(sourceText: String): List<FileChangeSummaryEntry> {
        return splitUnifiedPatchByFile(sourceText).mapNotNull { chunk ->
            parsePatchChunk(chunk).firstOrNull()
        }
    }

    private fun parsePatchChunk(chunk: String): List<FileChangeSummaryEntry> {
        val normalized = RemodexUnifiedPatchParser.normalize(chunk) ?: return emptyList()
        val analysis = RemodexUnifiedPatchParser.analyze(normalized)
        return splitUnifiedPatchByFile(normalized).mapNotNull { perFileChunk ->
            val fileChange = RemodexUnifiedPatchParser.analyze(perFileChunk).fileChanges.firstOrNull() ?: return@mapNotNull null
            FileChangeSummaryEntry(
                path = fileChange.path,
                additions = fileChange.additions,
                deletions = fileChange.deletions,
                action = inferPatchAction(perFileChunk),
            )
        }.ifEmpty {
            analysis.fileChanges.map { fileChange ->
                FileChangeSummaryEntry(
                    path = fileChange.path,
                    additions = fileChange.additions,
                    deletions = fileChange.deletions,
                    action = FileChangeAction.EDITED,
                )
            }
        }
    }

    private fun parseSectionChunks(
        bodyText: String,
        entries: List<FileChangeSummaryEntry>,
    ): List<PerFileDiffChunk> {
        val sections = bodyText
            .split("\n\n---\n\n")
            .filter { section -> section.isNotBlank() }
        val looksLikeRenderedSections = sections.any { section ->
            val lines = section.lines()
            lines.any { line -> parsePathLine(line) != null } || lines.any { line -> line.trim().startsWith("```") }
        }
        if (!looksLikeRenderedSections) {
            return emptyList()
        }
        return sections.mapIndexedNotNull { index, section ->
            val lines = section.lines()
            val path = lines.firstNotNullOfOrNull(::parsePathLine)
                ?: entries.getOrNull(index)?.path
                ?: return@mapIndexedNotNull null
            val entry = entries.firstOrNull { it.path == path }
            val diffCode = extractFencedCode(lines).orEmpty()
            PerFileDiffChunk(
                id = "$index-$path",
                path = path,
                action = FileChangeAction.fromKind(lines.firstNotNullOfOrNull(::parseKindLine))
                    ?: entry?.action
                    ?: FileChangeAction.EDITED,
                additions = entry?.additions ?: 0,
                deletions = entry?.deletions ?: 0,
                diffCode = diffCode,
            )
        }
    }

    private fun parseUnifiedPatchChunks(
        bodyText: String,
        entries: List<FileChangeSummaryEntry>,
    ): List<PerFileDiffChunk> {
        return splitUnifiedPatchByFile(bodyText).mapIndexedNotNull { index, chunk ->
            val entry = parsePatchChunk(chunk).firstOrNull() ?: entries.getOrNull(index) ?: return@mapIndexedNotNull null
            PerFileDiffChunk(
                id = "$index-${entry.path}",
                path = entry.path,
                action = entry.action ?: FileChangeAction.EDITED,
                additions = entry.additions,
                deletions = entry.deletions,
                diffCode = chunk.trim(),
            )
        }
    }

    private fun splitUnifiedPatchByFile(diff: String): List<String> {
        if (!RemodexUnifiedPatchParser.looksLikePatchText(diff)) {
            return emptyList()
        }
        val lines = diff.split('\n')
        if (lines.isEmpty()) {
            return emptyList()
        }
        val chunks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        lines.forEach { line ->
            if (line.startsWith("diff --git ") && current.isNotEmpty()) {
                chunks += current
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) {
            chunks += current
        }
        return chunks.map { chunk -> chunk.joinToString(separator = "\n").trim() }.filter(String::isNotBlank)
    }

    private fun parsePathLine(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("Path:")) {
            return null
        }
        return trimmed.removePrefix("Path:").trim().trim('`').takeIf(String::isNotEmpty)
    }

    private fun parseKindLine(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("Kind:")) {
            return null
        }
        return trimmed.removePrefix("Kind:").trim().takeIf(String::isNotEmpty)
    }

    private fun extractFencedCode(lines: List<String>): String? {
        var inFence = false
        val codeLines = mutableListOf<String>()
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inFence) {
                    return codeLines.joinToString(separator = "\n")
                }
                inFence = true
            } else if (inFence) {
                codeLines += line
            }
        }
        return null
    }

    private fun inferPatchAction(chunk: String): FileChangeAction {
        val normalized = chunk.lowercase()
        return when {
            normalized.contains("\nrename from ") || normalized.contains("\nrename to ") || normalized.contains("\ncopy from ") -> {
                FileChangeAction.RENAMED
            }

            normalized.contains("\nnew file mode ") || normalized.contains("\n--- /dev/null") -> FileChangeAction.ADDED
            normalized.contains("\ndeleted file mode ") || normalized.contains("\n+++ /dev/null") -> FileChangeAction.DELETED
            else -> FileChangeAction.EDITED
        }
    }
}
