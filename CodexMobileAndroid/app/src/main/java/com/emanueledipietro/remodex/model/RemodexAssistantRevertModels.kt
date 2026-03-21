package com.emanueledipietro.remodex.model

import kotlinx.serialization.Serializable

@Serializable
enum class RemodexAssistantChangeSetStatus {
    COLLECTING,
    READY,
    REVERTED,
    FAILED,
    NOT_REVERTABLE,
}

@Serializable
enum class RemodexAssistantChangeSetSource {
    TURN_DIFF,
    FILE_CHANGE_FALLBACK,
}

@Serializable
data class RemodexAssistantFileChange(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val isBinary: Boolean = false,
    val isRenameOrModeOnly: Boolean = false,
)

@Serializable
data class RemodexAssistantChangeSet(
    val id: String,
    val repoRoot: String? = null,
    val threadId: String,
    val turnId: String,
    val assistantMessageId: String? = null,
    val status: RemodexAssistantChangeSetStatus = RemodexAssistantChangeSetStatus.COLLECTING,
    val source: RemodexAssistantChangeSetSource = RemodexAssistantChangeSetSource.TURN_DIFF,
    val forwardUnifiedPatch: String = "",
    val fileChanges: List<RemodexAssistantFileChange> = emptyList(),
    val unsupportedReasons: List<String> = emptyList(),
    val fallbackPatchCount: Int = 0,
) {
    val affectedFiles: List<String>
        get() = fileChanges.map(RemodexAssistantFileChange::path)
}

enum class RemodexAssistantRevertRiskLevel {
    SAFE,
    WARNING,
    BLOCKED,
}

data class RemodexAssistantRevertPresentation(
    val title: String,
    val isEnabled: Boolean,
    val helperText: String? = null,
    val riskLevel: RemodexAssistantRevertRiskLevel = RemodexAssistantRevertRiskLevel.SAFE,
    val warningText: String? = null,
    val overlappingFiles: List<String> = emptyList(),
)

data class RemodexRevertConflict(
    val path: String,
    val message: String,
)

data class RemodexRevertPreviewResult(
    val canRevert: Boolean,
    val affectedFiles: List<String>,
    val conflicts: List<RemodexRevertConflict>,
    val unsupportedReasons: List<String>,
    val stagedFiles: List<String>,
)

data class RemodexRevertApplyResult(
    val success: Boolean,
    val revertedFiles: List<String>,
    val conflicts: List<RemodexRevertConflict>,
    val unsupportedReasons: List<String>,
    val stagedFiles: List<String>,
    val status: RemodexGitRepoSync? = null,
)

data class RemodexAssistantRevertSheetState(
    val messageId: String,
    val threadId: String,
    val changeSet: RemodexAssistantChangeSet,
    val presentation: RemodexAssistantRevertPresentation,
    val preview: RemodexRevertPreviewResult? = null,
    val isLoadingPreview: Boolean = false,
    val isApplying: Boolean = false,
    val errorMessage: String? = null,
)

data class RemodexUnifiedPatchAnalysis(
    val fileChanges: List<RemodexAssistantFileChange>,
    val unsupportedReasons: List<String>,
)

object RemodexUnifiedPatchParser {
    fun normalize(rawPatch: String?): String? {
        val trimmed = rawPatch?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }
        return if (trimmed.endsWith('\n')) trimmed else "$trimmed\n"
    }

    fun looksLikePatchText(rawPatch: String?): Boolean {
        val normalized = rawPatch?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return false
        }
        return normalized.contains("diff --git ")
            || (normalized.contains("\n@@ ") && normalized.contains("\n+++ "))
            || normalized.startsWith("--- ")
    }

    fun analyze(rawPatch: String): RemodexUnifiedPatchAnalysis {
        val patch = rawPatch.trim()
        if (patch.isEmpty()) {
            return RemodexUnifiedPatchAnalysis(
                fileChanges = emptyList(),
                unsupportedReasons = listOf("No exact patch was captured."),
            )
        }

        val chunks = splitIntoChunks(patch)
        if (chunks.isEmpty()) {
            return RemodexUnifiedPatchAnalysis(
                fileChanges = emptyList(),
                unsupportedReasons = listOf("No exact patch was captured."),
            )
        }

        val fileChanges = mutableListOf<RemodexAssistantFileChange>()
        val unsupportedReasons = linkedSetOf<String>()
        chunks.forEach { chunk ->
            val analysis = analyzeChunk(chunk)
            analysis.fileChange?.let(fileChanges::add)
            unsupportedReasons += analysis.unsupportedReasons
        }

        if (fileChanges.isEmpty()) {
            unsupportedReasons += "No exact patch was captured."
        }

        return RemodexUnifiedPatchAnalysis(
            fileChanges = fileChanges,
            unsupportedReasons = unsupportedReasons.toList(),
        )
    }

    private data class PatchChunkAnalysis(
        val fileChange: RemodexAssistantFileChange?,
        val unsupportedReasons: List<String>,
    )

    private fun splitIntoChunks(patch: String): List<List<String>> {
        val lines = patch.lines()
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
        return chunks
    }

    private fun analyzeChunk(lines: List<String>): PatchChunkAnalysis {
        val path = extractPatchPath(lines)
        val isBinary = lines.any { line ->
            line.startsWith("Binary files ") || line == "GIT binary patch"
        }
        val isRenameOrModeOnly = lines.any { line ->
            line.startsWith("rename from ")
                || line.startsWith("rename to ")
                || line.startsWith("copy from ")
                || line.startsWith("copy to ")
                || line.startsWith("old mode ")
                || line.startsWith("new mode ")
                || line.startsWith("similarity index ")
                || line.startsWith("new file mode 120")
                || line.startsWith("deleted file mode 120")
        }

        var additions = 0
        var deletions = 0
        lines.forEach { line ->
            when {
                line.startsWith("+") && !line.startsWith("+++") -> additions += 1
                line.startsWith("-") && !line.startsWith("---") -> deletions += 1
            }
        }

        val unsupportedReasons = mutableListOf<String>()
        if (isBinary) {
            unsupportedReasons += "Binary changes are not auto-revertable in v1."
        }
        if (isRenameOrModeOnly) {
            unsupportedReasons += "Rename, mode-only, or symlink changes are not auto-revertable in v1."
        }

        val fileChange = if (
            path != null && (
                additions > 0
                    || deletions > 0
                    || lines.any { line -> line == "--- /dev/null" || line == "+++ /dev/null" }
            )
        ) {
            RemodexAssistantFileChange(
                path = path,
                additions = additions,
                deletions = deletions,
                isBinary = isBinary,
                isRenameOrModeOnly = isRenameOrModeOnly,
            )
        } else {
            if (!isBinary && !isRenameOrModeOnly) {
                unsupportedReasons += "No exact patch was captured."
            }
            null
        }

        return PatchChunkAnalysis(
            fileChange = fileChange,
            unsupportedReasons = unsupportedReasons.distinct(),
        )
    }

    private fun extractPatchPath(lines: List<String>): String? {
        lines.forEach { line ->
            if (line.startsWith("+++ ")) {
                normalizeDiffPath(line.removePrefix("+++ ").trim())?.let { normalized ->
                    if (normalized != "/dev/null") {
                        return normalized
                    }
                }
            }
        }
        lines.forEach { line ->
            if (line.startsWith("--- ")) {
                normalizeDiffPath(line.removePrefix("--- ").trim())?.let { normalized ->
                    if (normalized != "/dev/null") {
                        return normalized
                    }
                }
            }
        }
        return null
    }

    private fun normalizeDiffPath(rawPath: String): String? {
        val trimmed = rawPath.trim().removePrefix("a/").removePrefix("b/")
        return trimmed.ifEmpty { null }
    }
}
