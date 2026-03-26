package com.emanueledipietro.remodex.feature.turn

data class ThinkingDisclosureSection(
    val id: String,
    val title: String,
    val detail: String,
)

data class ThinkingDisclosureContent(
    val sections: List<ThinkingDisclosureSection>,
    val fallbackText: String,
) {
    val showsDisclosure: Boolean
        get() = sections.isNotEmpty()
}

object ThinkingDisclosureParser {
    private val compactActivityPrefixes = listOf(
        "running ",
        "completed ",
        "failed ",
        "stopped ",
        "read ",
        "search ",
        "searched ",
        "exploring ",
        "list ",
        "listing ",
        "open ",
        "opened ",
        "find ",
        "finding ",
        "edit ",
        "edited ",
        "write ",
        "wrote ",
        "apply ",
        "applied ",
    )
    private val thinkingSummaryLineRegex = Regex("""^\s*\*\*(.+?)\*\*\s*$""")

    fun parse(rawText: String): ThinkingDisclosureContent {
        val normalizedText = normalizedThinkingContent(rawText)
        if (normalizedText.isEmpty()) {
            return ThinkingDisclosureContent(
                sections = emptyList(),
                fallbackText = "",
            )
        }

        val lines = normalizedText.split('\n')
        val preambleLines = mutableListOf<String>()
        var currentTitle: String? = null
        val currentDetailLines = mutableListOf<String>()
        val sections = mutableListOf<ThinkingDisclosureSection>()

        fun flushCurrentSection() {
            val resolvedTitle = currentTitle ?: return
            sections += ThinkingDisclosureSection(
                id = "${sections.size}-$resolvedTitle",
                title = resolvedTitle,
                detail = joinedThinkingBlock(currentDetailLines),
            )
            currentDetailLines.clear()
        }

        lines.forEach { line ->
            val summaryTitle = summaryTitle(line)
            if (summaryTitle != null) {
                flushCurrentSection()
                currentTitle = summaryTitle
            } else if (currentTitle == null) {
                preambleLines += line
            } else {
                currentDetailLines += line
            }
        }

        flushCurrentSection()

        if (sections.isNotEmpty()) {
            val preamble = joinedThinkingBlock(preambleLines)
            val withPreamble = if (preamble.isNotEmpty()) {
                val firstSection = sections.first()
                listOf(
                    firstSection.copy(
                        detail = listOf(preamble, firstSection.detail)
                            .filter(String::isNotEmpty)
                            .joinToString(separator = "\n\n"),
                    ),
                ) + sections.drop(1)
            } else {
                sections
            }

            return ThinkingDisclosureContent(
                sections = coalescedAdjacentSections(withPreamble),
                fallbackText = normalizedText,
            )
        }

        return ThinkingDisclosureContent(
            sections = emptyList(),
            fallbackText = normalizedText,
        )
    }

    fun normalizedThinkingContent(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return ""
        }

        val lowercase = trimmed.lowercase()
        return when {
            lowercase.startsWith("thinking...") -> trimmed
                .drop("thinking...".length)
                .trim()

            lowercase == "thinking" -> ""
            else -> trimmed
        }
    }

    fun compactActivityPreview(normalizedText: String): String? {
        val lines = normalizedText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

        if (lines.isEmpty()) {
            return null
        }

        val activityLines = lines.filter { line ->
            val normalizedLine = line.lowercase()
            compactActivityPrefixes.any(normalizedLine::startsWith)
        }
        val isActivityOnly = activityLines.size == lines.size

        return when {
            isActivityOnly -> activityLines.lastOrNull()
            activityLines.size == 1 -> activityLines.first()
            else -> null
        }
    }

    private fun summaryTitle(line: String): String? {
        val match = thinkingSummaryLineRegex.matchEntire(line) ?: return null
        return match.groupValues[1].trim().ifEmpty { null }
    }

    private fun joinedThinkingBlock(lines: List<String>): String {
        return lines.joinToString(separator = "\n").trim()
    }

    private fun coalescedAdjacentSections(
        sections: List<ThinkingDisclosureSection>,
    ): List<ThinkingDisclosureSection> {
        val collapsed = mutableListOf<ThinkingDisclosureSection>()
        sections.forEach { section ->
            val previous = collapsed.lastOrNull()
            if (previous == null || previous.title != section.title) {
                collapsed += section
                return@forEach
            }

            val mergedDetail = when {
                previous.detail == section.detail || section.detail.isEmpty() -> previous.detail
                previous.detail.isEmpty() || section.detail.contains(previous.detail) -> section.detail
                previous.detail.contains(section.detail) -> previous.detail
                else -> listOf(previous.detail, section.detail)
                    .filter(String::isNotEmpty)
                    .joinToString(separator = "\n\n")
            }
            collapsed[collapsed.lastIndex] = previous.copy(detail = mergedDetail)
        }
        return collapsed
    }
}
