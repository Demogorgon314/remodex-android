package com.emanueledipietro.remodex.model

data class RemodexTrailingToken(
    val query: String,
    val startIndex: Int,
)

object RemodexComposerCommandLogic {
    private val fileMentionSegmentRegex = Regex("[A-Z]+(?=$|[A-Z][a-z]|\\d)|[A-Z]?[a-z]+|\\d+")
    private val disallowedBareSwiftFileMentionQueries = setOf(
        "Binding",
        "Environment",
        "EnvironmentObject",
        "FocusState",
        "MainActor",
        "Namespace",
        "Observable",
        "ObservedObject",
        "Published",
        "SceneBuilder",
        "State",
        "StateObject",
        "UIApplicationDelegateAdaptor",
        "ViewBuilder",
        "testable",
    )

    fun trailingFileToken(text: String): RemodexTrailingToken? {
        if (text.isEmpty() || text.last().isWhitespace()) {
            return null
        }
        val triggerIndex = text.lastIndexOf('@')
        if (triggerIndex < 0) {
            return null
        }
        if (triggerIndex > 0 && !text[triggerIndex - 1].isWhitespace()) {
            return null
        }
        val rawQuery = text.substring(triggerIndex + 1)
        val query = rawQuery.trim()
        if (query.isEmpty() || query.any { character -> character == '\n' || character == '\r' }) {
            return null
        }
        if (!isAllowedFileAutocompleteQuery(query)) {
            return null
        }
        if (query.any(Char::isWhitespace)) {
            val looksFileLike = query.contains('/') || query.contains('\\') || query.contains('.')
            if (!looksFileLike) {
                return null
            }
        }
        return RemodexTrailingToken(query = query, startIndex = triggerIndex)
    }

    fun trailingSkillToken(text: String): RemodexTrailingToken? {
        val token = trailingToken(text = text, trigger = '$') ?: return null
        return token.takeIf { candidate -> candidate.query.any(Char::isLetter) }
    }

    fun trailingSlashCommandToken(text: String): RemodexTrailingToken? {
        if (text.isEmpty()) {
            return null
        }
        val triggerIndex = text.lastIndexOf('/')
        if (triggerIndex < 0) {
            return null
        }
        if (triggerIndex > 0 && !text[triggerIndex - 1].isWhitespace()) {
            return null
        }
        val query = text.substring(triggerIndex + 1)
        if (query.any(Char::isWhitespace)) {
            return null
        }
        return RemodexTrailingToken(query = query, startIndex = triggerIndex)
    }

    fun replaceTrailingFileToken(text: String, selectedPath: String): String? {
        val trimmedPath = selectedPath.trim()
        val token = trailingFileToken(text) ?: return null
        if (trimmedPath.isEmpty()) {
            return null
        }
        return text.replaceRange(token.startIndex, text.length, "@$trimmedPath ")
    }

    fun replaceTrailingSkillToken(text: String, selectedSkill: String): String? {
        val trimmedSkill = selectedSkill.trim()
        val token = trailingSkillToken(text) ?: return null
        if (trimmedSkill.isEmpty()) {
            return null
        }
        return text.replaceRange(token.startIndex, text.length, "\$$trimmedSkill ")
    }

    fun removeTrailingSlashCommandToken(text: String): String? {
        val token = trailingSlashCommandToken(text) ?: return null
        return text.replaceRange(token.startIndex, text.length, "").trim()
    }

    fun replaceTrailingSlashCommandToken(
        text: String,
        commandToken: String,
    ): String? {
        val normalizedCommandToken = commandToken.trim()
        val token = trailingSlashCommandToken(text) ?: return null
        if (normalizedCommandToken.isEmpty()) {
            return null
        }
        return text.replaceRange(token.startIndex, text.length, "$normalizedCommandToken ")
    }

    fun isStandaloneSlashCommand(
        text: String,
        commandToken: String,
    ): Boolean {
        val normalizedText = text.trim()
        val normalizedCommandToken = commandToken.trim()
        if (normalizedText.isEmpty() || normalizedCommandToken.isEmpty()) {
            return false
        }
        return normalizedText == normalizedCommandToken
    }

    fun slashCommandArgs(
        text: String,
        commandToken: String,
    ): String? {
        val normalizedCommandToken = commandToken.trim()
        if (normalizedCommandToken.isEmpty() || text.isEmpty() || text.first().isWhitespace()) {
            return null
        }
        if (!text.startsWith(normalizedCommandToken)) {
            return null
        }
        if (text.length == normalizedCommandToken.length) {
            return null
        }
        val nextCharacter = text[normalizedCommandToken.length]
        if (!nextCharacter.isWhitespace()) {
            return null
        }
        return text.substring(normalizedCommandToken.length)
            .trim()
            .takeIf(String::isNotEmpty)
    }

    fun hasClosedConfirmedFileMentionPrefix(
        text: String,
        confirmedMentions: List<RemodexComposerMentionedFile>,
    ): Boolean {
        if (confirmedMentions.isEmpty()) {
            return false
        }
        val triggerIndex = text.lastIndexOf('@')
        if (triggerIndex < 0) {
            return false
        }
        if (triggerIndex > 0 && !text[triggerIndex - 1].isWhitespace()) {
            return false
        }
        val tail = text.substring(triggerIndex + 1)
        if (tail.isEmpty()) {
            return false
        }

        val ambiguousKeys = ambiguousFileNameAliasKeys(confirmedMentions)
        confirmedMentions.forEach { mention ->
            val collisionKey = fileNameAliasCollisionKey(mention.fileName)
            val allowFileNameAliases = collisionKey?.let { key -> !ambiguousKeys.contains(key) } ?: true
            fileMentionAliases(
                fileName = mention.fileName,
                path = mention.path,
                allowFileNameAliases = allowFileNameAliases,
            ).forEach { alias ->
                if (tail.startsWith(alias, ignoreCase = true) &&
                    tail.length > alias.length &&
                    tail[alias.length].isWhitespace()
                ) {
                    return true
                }
            }
        }
        return false
    }

    fun applySubagentsSelection(
        text: String,
        isSelected: Boolean,
    ): String {
        val trimmed = text.trim()
        if (!isSelected) {
            return trimmed
        }
        val cannedPrompt = RemodexSlashCommand.SUBAGENTS.cannedPrompt ?: return trimmed
        if (trimmed.isEmpty()) {
            return cannedPrompt
        }
        return "$cannedPrompt\n\n$trimmed"
    }

    fun hasContentConflictingWithReview(
        trimmedInput: String,
        mentionedFileCount: Int,
        mentionedSkillCount: Int,
        attachmentCount: Int,
        hasSubagentsSelection: Boolean,
    ): Boolean {
        val draftText = removeTrailingSlashCommandToken(trimmedInput) ?: trimmedInput
        return draftText.isNotEmpty() ||
            mentionedFileCount > 0 ||
            mentionedSkillCount > 0 ||
            attachmentCount > 0 ||
            hasSubagentsSelection
    }

    fun canOfferForkSlashCommand(
        text: String,
        mentionedFileCount: Int = 0,
        mentionedSkillCount: Int = 0,
        attachmentCount: Int = 0,
        hasReviewSelection: Boolean = false,
        hasSubagentsSelection: Boolean = false,
        isPlanModeArmed: Boolean = false,
    ): Boolean {
        val token = trailingSlashCommandToken(text) ?: return false
        val remainingDraft = text.replaceRange(token.startIndex, text.length, "").trim()
        return remainingDraft.isEmpty() &&
            mentionedFileCount == 0 &&
            mentionedSkillCount == 0 &&
            attachmentCount == 0 &&
            !hasReviewSelection &&
            !hasSubagentsSelection &&
            !isPlanModeArmed
    }

    fun replaceFileMentionAliases(
        text: String,
        mention: RemodexComposerMentionedFile,
    ): String {
        val replacement = "@${mention.path}"
        val placeholder = "__remodex_file_mention__${mention.path.hashCode()}__"
        val ambiguousKeys = ambiguousFileNameAliasKeys(listOf(mention))
        val collisionKey = fileNameAliasCollisionKey(mention.fileName)
        val allowFileNameAliases = collisionKey?.let { key -> !ambiguousKeys.contains(key) } ?: true
        val replacedText = fileMentionAliases(
            fileName = mention.fileName,
            path = mention.path,
            allowFileNameAliases = allowFileNameAliases,
        ).fold(text) { partialText, alias ->
            replaceBoundedToken(
                token = "@$alias",
                replacement = placeholder,
                text = partialText,
                ignoreCase = true,
            )
        }
        return replacedText.replace(placeholder, replacement)
    }

    fun removeFileMentionAliases(
        text: String,
        mention: RemodexComposerMentionedFile,
    ): String {
        val ambiguousKeys = ambiguousFileNameAliasKeys(listOf(mention))
        val collisionKey = fileNameAliasCollisionKey(mention.fileName)
        val allowFileNameAliases = collisionKey?.let { key -> !ambiguousKeys.contains(key) } ?: true
        return fileMentionAliases(
            fileName = mention.fileName,
            path = mention.path,
            allowFileNameAliases = allowFileNameAliases,
        ).fold(text) { partialText, alias ->
            removeBoundedToken(
                token = "@$alias",
                text = partialText,
                ignoreCase = true,
            )
        }
    }

    private fun trailingToken(
        text: String,
        trigger: Char,
    ): RemodexTrailingToken? {
        if (text.isEmpty()) {
            return null
        }
        val lastWhitespaceIndex = text.indexOfLast(Char::isWhitespace)
        val tokenStart = if (lastWhitespaceIndex >= 0) lastWhitespaceIndex + 1 else 0
        if (tokenStart >= text.length || text[tokenStart] != trigger) {
            return null
        }
        val query = text.substring(tokenStart + 1)
        if (query.isEmpty() || query.any(Char::isWhitespace)) {
            return null
        }
        return RemodexTrailingToken(query = query, startIndex = tokenStart)
    }

    private fun isAllowedFileAutocompleteQuery(query: String): Boolean {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return false
        }
        if (trimmedQuery.contains('/') || trimmedQuery.contains('\\') || trimmedQuery.contains('.')) {
            return true
        }
        return !disallowedBareSwiftFileMentionQueries.contains(trimmedQuery)
    }

    private fun fileMentionAliases(
        fileName: String,
        path: String,
        allowFileNameAliases: Boolean = true,
    ): List<String> {
        val aliases = linkedSetOf<String>()
        val seeds = mutableListOf(path, deletingPathExtension(path))
        if (allowFileNameAliases) {
            seeds.add(0, fileName)
            seeds += deletingPathExtension(fileName)
        }
        seeds.forEach { seed ->
            val trimmedSeed = seed.trim()
            if (trimmedSeed.isEmpty()) {
                return@forEach
            }
            aliases += trimmedSeed
            appendNormalizedFileMentionAliases(trimmedSeed, aliases)
        }
        return aliases
            .map(String::trim)
            .filter(String::isNotEmpty)
            .sortedWith(
                compareByDescending<String> { it.length }
                    .thenBy { it.lowercase() },
            )
    }

    private fun ambiguousFileNameAliasKeys(
        mentions: List<RemodexComposerMentionedFile>,
    ): Set<String> {
        return mentions
            .mapNotNull { mention -> fileNameAliasCollisionKey(mention.fileName) }
            .groupingBy { key -> key }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    }

    private fun fileNameAliasCollisionKey(fileName: String): String? {
        val trimmedName = fileName.trim()
        if (trimmedName.isEmpty()) {
            return null
        }
        val normalizedExtension = trimmedName.substringAfterLast('.', "").lowercase()
        val stem = deletingPathExtension(trimmedName)
        val tokens = mentionSearchTokens(stem)
        if (tokens.isEmpty()) {
            return normalizedExtension.takeIf(String::isNotEmpty)?.let { ".$it" }
        }
        val tokenKey = tokens.joinToString(separator = "|")
        return if (normalizedExtension.isEmpty()) {
            tokenKey
        } else {
            "$tokenKey.$normalizedExtension"
        }
    }

    private fun appendNormalizedFileMentionAliases(
        seed: String,
        aliases: MutableSet<String>,
    ) {
        val trimmedSeed = seed.trim()
        if (trimmedSeed.isEmpty()) {
            return
        }
        val normalizedExtension = trimmedSeed.substringAfterLast('.', "").lowercase()
        val stem = if (normalizedExtension.isEmpty()) {
            trimmedSeed
        } else {
            deletingPathExtension(trimmedSeed)
        }
        val tokens = mentionSearchTokens(stem)
        if (tokens.isEmpty()) {
            return
        }
        val baseVariants = linkedSetOf(
            tokens.joinToString(separator = " "),
            tokens.joinToString(separator = "-"),
            tokens.joinToString(separator = "_"),
            tokens.joinToString(separator = ""),
            lowerCamelCase(tokens),
            upperCamelCase(tokens),
        ).filter(String::isNotEmpty)
        baseVariants.forEach { variant ->
            aliases += variant
            if (normalizedExtension.isNotEmpty()) {
                aliases += "$variant.$normalizedExtension"
            }
        }
    }

    private fun mentionSearchTokens(value: String): List<String> {
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) {
            return emptyList()
        }
        return trimmedValue
            .split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotEmpty)
            .flatMap(::tokensFromMentionSegment)
    }

    private fun tokensFromMentionSegment(segment: String): List<String> {
        val trimmedSegment = segment.trim()
        if (trimmedSegment.isEmpty()) {
            return emptyList()
        }
        val rawTokens = fileMentionSegmentRegex.findAll(trimmedSegment)
            .map { match -> match.value }
            .toList()
        if (rawTokens.isEmpty()) {
            return listOf(trimmedSegment.lowercase())
        }
        val normalizedTokens = mutableListOf<String>()
        var index = 0
        while (index < rawTokens.size) {
            val token = rawTokens[index]
            if (token.length == 1 &&
                token == token.lowercase() &&
                index + 1 < rawTokens.size &&
                isAllCapsAcronym(rawTokens[index + 1])
            ) {
                normalizedTokens += (token + rawTokens[index + 1]).lowercase()
                index += 2
                continue
            }
            normalizedTokens += token.lowercase()
            index += 1
        }
        return normalizedTokens
    }

    private fun isAllCapsAcronym(token: String): Boolean {
        return token.length > 1 && token.all { character ->
            character.isUpperCase() || character.isDigit()
        }
    }

    private fun lowerCamelCase(tokens: List<String>): String {
        val first = tokens.firstOrNull() ?: return ""
        return first + tokens.drop(1).joinToString(separator = "") { token -> token.capitalizeToken() }
    }

    private fun upperCamelCase(tokens: List<String>): String {
        return tokens.joinToString(separator = "") { token -> token.capitalizeToken() }
    }

    private fun String.capitalizeToken(): String {
        val first = firstOrNull() ?: return this
        return first.uppercaseChar() + drop(1)
    }

    private fun deletingPathExtension(value: String): String {
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) {
            return ""
        }
        val extensionIndex = trimmedValue.lastIndexOf('.')
        return if (extensionIndex <= 0) {
            trimmedValue
        } else {
            trimmedValue.substring(0, extensionIndex)
        }
    }

    private fun replaceBoundedToken(
        token: String,
        replacement: String,
        text: String,
        ignoreCase: Boolean,
    ): String {
        val regex = Regex(
            pattern = Regex.escape(token) + "(?=[\\s,.;:!?)\\]}>]|$)",
            options = setOf(RegexOption.MULTILINE) + if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
        )
        return regex.replace(text, replacement)
    }

    private fun removeBoundedToken(
        token: String,
        text: String,
        ignoreCase: Boolean,
    ): String {
        val regex = Regex(
            pattern = Regex.escape(token) + "(?:[\\s,.;:!?)\\]}>]|$)",
            options = setOf(RegexOption.MULTILINE) + if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
        )
        return regex.replace(text, "").replace(Regex("\\s{2,}"), " ").trim()
    }
}
