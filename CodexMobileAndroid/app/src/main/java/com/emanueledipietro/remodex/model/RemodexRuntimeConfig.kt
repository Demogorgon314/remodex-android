package com.emanueledipietro.remodex.model

import kotlinx.serialization.Serializable

@Serializable
enum class RemodexPlanningMode {
    AUTO,
    PLAN,
    ;

    val label: String
        get() = when (this) {
            AUTO -> "Auto"
            PLAN -> "Plan"
        }
}

@Serializable
enum class RemodexAccessMode {
    ON_REQUEST,
    FULL_ACCESS,
    ;

    val label: String
        get() = when (this) {
            ON_REQUEST -> "On-Request"
            FULL_ACCESS -> "Full Access"
        }

    val shortLabel: String
        get() = when (this) {
            ON_REQUEST -> "Ask"
            FULL_ACCESS -> "Full"
        }

    val approvalPolicyCandidates: List<String>
        get() = when (this) {
            ON_REQUEST -> listOf("on-request", "onRequest")
            FULL_ACCESS -> listOf("never")
        }
}

@Serializable
enum class RemodexServiceTier {
    FAST,
    ;

    val label: String
        get() = when (this) {
            FAST -> "Fast"
        }

    val wireValue: String
        get() = when (this) {
            FAST -> "fast"
        }
}

@Serializable
data class RemodexReasoningEffortOption(
    val reasoningEffort: String,
    val description: String = "",
) {
    val label: String
        get() = RemodexRuntimeMetaMapper.reasoningTitle(reasoningEffort)

    fun normalizedOrNull(): RemodexReasoningEffortOption? {
        val normalizedEffort = reasoningEffort.trim()
        if (normalizedEffort.isEmpty()) {
            return null
        }
        return copy(
            reasoningEffort = normalizedEffort,
            description = description.trim(),
        )
    }
}

@Serializable
data class RemodexModelOption(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val supportedReasoningEfforts: List<RemodexReasoningEffortOption> = emptyList(),
    val defaultReasoningEffort: String? = null,
) {
    fun normalizedOrNull(): RemodexModelOption? {
        val normalizedModel = model.trim()
        val normalizedId = id.trim().ifEmpty { normalizedModel }
        if (normalizedId.isEmpty() && normalizedModel.isEmpty()) {
            return null
        }
        val normalizedEfforts = supportedReasoningEfforts
            .mapNotNull(RemodexReasoningEffortOption::normalizedOrNull)
            .distinctBy(RemodexReasoningEffortOption::reasoningEffort)
        val normalizedDefaultReasoning = defaultReasoningEffort
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { defaultValue ->
                normalizedEfforts.any { option -> option.reasoningEffort == defaultValue }
            }
        return copy(
            id = normalizedId.ifEmpty { normalizedModel },
            model = normalizedModel.ifEmpty { normalizedId },
            displayName = displayName.trim().ifEmpty { normalizedModel.ifEmpty { normalizedId } },
            description = description.trim(),
            supportedReasoningEfforts = normalizedEfforts,
            defaultReasoningEffort = normalizedDefaultReasoning,
        )
    }
}

@Serializable
data class RemodexRuntimeOverrides(
    val planningMode: RemodexPlanningMode? = null,
    val reasoningEffort: String? = null,
    val accessMode: RemodexAccessMode? = null,
    val serviceTier: RemodexServiceTier? = null,
)

@Serializable
data class RemodexRuntimeDefaults(
    val modelId: String? = null,
    val reasoningEffort: String? = null,
    val accessMode: RemodexAccessMode = RemodexAccessMode.ON_REQUEST,
    val serviceTier: RemodexServiceTier? = null,
)

@Serializable
data class RemodexRuntimeConfig(
    val availableModels: List<RemodexModelOption> = emptyList(),
    val availablePlanningModes: List<RemodexPlanningMode> = RemodexPlanningMode.entries,
    val availableReasoningEfforts: List<RemodexReasoningEffortOption> = emptyList(),
    val availableAccessModes: List<RemodexAccessMode> = RemodexAccessMode.entries,
    val availableServiceTiers: List<RemodexServiceTier> = emptyList(),
    val selectedModelId: String? = null,
    val planningMode: RemodexPlanningMode = RemodexPlanningMode.AUTO,
    val reasoningEffort: String? = null,
    val accessMode: RemodexAccessMode = RemodexAccessMode.ON_REQUEST,
    val serviceTier: RemodexServiceTier? = null,
) {
    fun applyDefaults(defaults: RemodexRuntimeDefaults): RemodexRuntimeConfig {
        return copy(
            selectedModelId = defaults.modelId?.trim()?.takeIf(String::isNotEmpty) ?: selectedModelId,
            reasoningEffort = defaults.reasoningEffort?.trim()?.takeIf(String::isNotEmpty) ?: reasoningEffort,
            accessMode = defaults.accessMode,
            serviceTier = defaults.serviceTier ?: serviceTier,
        ).normalizeSelections()
    }

    fun applyThreadOverrides(overrides: RemodexRuntimeOverrides?): RemodexRuntimeConfig {
        if (overrides == null) {
            return normalizeSelections()
        }
        return copy(
            planningMode = overrides.planningMode ?: planningMode,
            reasoningEffort = overrides.reasoningEffort?.trim()?.takeIf(String::isNotEmpty) ?: reasoningEffort,
            accessMode = overrides.accessMode ?: accessMode,
            serviceTier = overrides.serviceTier ?: serviceTier,
        ).normalizeSelections()
    }

    fun withAvailableModels(models: List<RemodexModelOption>): RemodexRuntimeConfig {
        return copy(availableModels = models).normalizeSelections()
    }

    fun normalizeSelections(): RemodexRuntimeConfig {
        val normalizedServiceTiers = availableServiceTiers
            .ifEmpty { RemodexServiceTier.entries.toList() }
            .distinct()
        val normalizedServiceTier = serviceTier?.takeIf { tier -> tier in normalizedServiceTiers }
        val normalizedModels = availableModels
            .mapNotNull(RemodexModelOption::normalizedOrNull)
            .distinctBy(RemodexModelOption::id)
        if (normalizedModels.isEmpty()) {
            return copy(
                availableModels = emptyList(),
                availableReasoningEfforts = availableReasoningEfforts
                    .mapNotNull(RemodexReasoningEffortOption::normalizedOrNull)
                    .distinctBy(RemodexReasoningEffortOption::reasoningEffort),
                availableServiceTiers = normalizedServiceTiers,
                selectedModelId = selectedModelId?.trim()?.takeIf(String::isNotEmpty),
                reasoningEffort = reasoningEffort?.trim()?.takeIf(String::isNotEmpty),
                serviceTier = normalizedServiceTier,
            )
        }

        val resolvedModel = selectedModelOption(from = normalizedModels) ?: fallbackModel(from = normalizedModels)
        val supportedReasoningEfforts = RemodexRuntimeMetaMapper.orderedReasoningOptions(
            resolvedModel?.supportedReasoningEfforts.orEmpty(),
        )
        val supportedIds = supportedReasoningEfforts.map(RemodexReasoningEffortOption::reasoningEffort).toSet()
        val normalizedReasoning = when {
            supportedIds.isEmpty() -> null
            reasoningEffort?.trim().takeIf { !it.isNullOrEmpty() } in supportedIds -> reasoningEffort?.trim()
            resolvedModel?.defaultReasoningEffort in supportedIds -> resolvedModel?.defaultReasoningEffort
            "medium" in supportedIds -> "medium"
            else -> supportedReasoningEfforts.firstOrNull()?.reasoningEffort
        }

        return copy(
            availableModels = normalizedModels,
            availableReasoningEfforts = supportedReasoningEfforts,
            availableServiceTiers = normalizedServiceTiers,
            selectedModelId = resolvedModel?.id,
            reasoningEffort = normalizedReasoning,
            serviceTier = normalizedServiceTier,
        )
    }

    fun selectedModelOption(from: List<RemodexModelOption> = availableModels): RemodexModelOption? {
        val normalizedSelection = selectedModelId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return from.firstOrNull { option ->
            option.id == normalizedSelection || option.model == normalizedSelection
        }
    }

    val runtimeLabel: String
        get() = buildList {
            selectedModelId?.takeIf { it.isNotBlank() }?.let(::add)
            add(planningMode.label)
            reasoningEffort?.takeIf(String::isNotBlank)?.let { effort ->
                add("${RemodexRuntimeMetaMapper.reasoningTitle(effort).lowercase()} reasoning")
            }
            if (accessMode == RemodexAccessMode.FULL_ACCESS) {
                add(accessMode.label)
            }
            serviceTier?.let { add(it.label) }
        }.joinToString(", ")
}

private fun fallbackModel(from: List<RemodexModelOption>): RemodexModelOption? {
    return from.firstOrNull { option -> option.isDefault } ?: from.firstOrNull()
}
