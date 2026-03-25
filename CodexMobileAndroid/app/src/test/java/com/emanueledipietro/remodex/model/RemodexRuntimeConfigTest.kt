package com.emanueledipietro.remodex.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemodexRuntimeConfigTest {
    @Test
    fun `normalize selections exposes fast speed by default`() {
        val config = RemodexRuntimeConfig().normalizeSelections()

        assertEquals(listOf(RemodexServiceTier.FAST), config.availableServiceTiers)
    }

    @Test
    fun `normalize selections keeps xhigh when selected model supports it`() {
        val config = RemodexRuntimeConfig(
            availableModels = listOf(gpt54Model()),
            selectedModelId = "gpt-5.4",
            reasoningEffort = "xhigh",
        ).normalizeSelections()

        assertEquals("gpt-5.4", config.selectedModelId)
        assertEquals("xhigh", config.reasoningEffort)
        assertEquals("Extra High", RemodexRuntimeMetaMapper.reasoningTitle(config.reasoningEffort.orEmpty()))
        assertTrue(config.availableReasoningEfforts.any { option -> option.reasoningEffort == "xhigh" })
    }

    @Test
    fun `normalize selections falls back to model default when reasoning is unsupported`() {
        val config = RemodexRuntimeConfig(
            availableModels = listOf(gpt54Model(), gpt53CodexModel()),
            selectedModelId = "gpt-5.3-codex",
            reasoningEffort = "xhigh",
        ).normalizeSelections()

        assertEquals("gpt-5.3-codex", config.selectedModelId)
        assertEquals("medium", config.reasoningEffort)
    }

    @Test
    fun `defaults apply global model while thread override still controls reasoning`() {
        val config = RemodexRuntimeConfig(
            availableModels = listOf(gpt54Model(), gpt53CodexModel()),
            selectedModelId = "gpt-5.3-codex",
            reasoningEffort = "medium",
        ).applyDefaults(
            RemodexRuntimeDefaults(modelId = "gpt-5.4", reasoningEffort = "high"),
        ).applyThreadOverrides(
            RemodexRuntimeOverrides(reasoningEffort = "xhigh"),
        )

        assertEquals("gpt-5.4", config.selectedModelId)
        assertEquals("xhigh", config.reasoningEffort)
    }

    private fun gpt54Model(): RemodexModelOption {
        return RemodexModelOption(
            id = "gpt-5.4",
            model = "gpt-5.4",
            displayName = "GPT-5.4",
            isDefault = true,
            supportedReasoningEfforts = listOf(
                RemodexReasoningEffortOption("low", "Low"),
                RemodexReasoningEffortOption("medium", "Medium"),
                RemodexReasoningEffortOption("high", "High"),
                RemodexReasoningEffortOption("xhigh", "Extra High"),
            ),
            defaultReasoningEffort = "medium",
        )
    }

    private fun gpt53CodexModel(): RemodexModelOption {
        return RemodexModelOption(
            id = "gpt-5.3-codex",
            model = "gpt-5.3-codex",
            displayName = "GPT-5.3-Codex",
            supportedReasoningEfforts = listOf(
                RemodexReasoningEffortOption("low", "Low"),
                RemodexReasoningEffortOption("medium", "Medium"),
                RemodexReasoningEffortOption("high", "High"),
            ),
            defaultReasoningEffort = "medium",
        )
    }
}
