package com.emanueledipietro.remodex.feature.turn

import com.emanueledipietro.remodex.feature.appshell.PlanComposerSessionUiState
import com.emanueledipietro.remodex.model.ConversationItemKind
import com.emanueledipietro.remodex.model.ConversationSpeaker
import com.emanueledipietro.remodex.model.RemodexConversationItem
import com.emanueledipietro.remodex.model.RemodexPlanningMode
import com.emanueledipietro.remodex.model.RemodexPlanState
import com.emanueledipietro.remodex.model.RemodexPlanStep
import com.emanueledipietro.remodex.model.RemodexPlanStepStatus
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputQuestion
import com.emanueledipietro.remodex.model.RemodexStructuredUserInputRequest
import com.emanueledipietro.remodex.model.RemodexTurnTerminalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.JsonPrimitive

class ConversationScreenPlanAccessoryTest {
    @Test
    fun `timeline layout pins only the latest active plan and keeps completed plans in timeline`() {
        val completedPlan = planItem(
            id = "plan-complete",
            steps = listOf(
                RemodexPlanStep(id = "1", step = "Wrap up", status = RemodexPlanStepStatus.COMPLETED),
            ),
        )
        val activePlan = planItem(
            id = "plan-active",
            steps = listOf(
                RemodexPlanStep(id = "2", step = "Compare iOS behavior", status = RemodexPlanStepStatus.IN_PROGRESS),
                RemodexPlanStep(id = "3", step = "Patch Android", status = RemodexPlanStepStatus.PENDING),
            ),
        )
        val chatItem = RemodexConversationItem(
            id = "assistant-chat",
            speaker = ConversationSpeaker.ASSISTANT,
            text = "Working on it",
        )

        val layout = buildConversationTimelineLayout(
            listOf(completedPlan, chatItem, activePlan),
        )

        assertEquals("plan-active", layout.pinnedPlanItem?.id)
        assertEquals(
            listOf("plan-complete", "assistant-chat"),
            layout.timelineItems.map(RemodexConversationItem::id),
        )
    }

    @Test
    fun `timeline layout keeps completed plans visible when nothing is pinned`() {
        val completedPlan = planItem(
            id = "plan-complete",
            steps = listOf(
                RemodexPlanStep(id = "1", step = "Done", status = RemodexPlanStepStatus.COMPLETED),
            ),
        )

        val layout = buildConversationTimelineLayout(listOf(completedPlan))

        assertNull(layout.pinnedPlanItem)
        assertEquals(listOf("plan-complete"), layout.timelineItems.map(RemodexConversationItem::id))
    }

    @Test
    fun `timeline layout hides composer takeover prompt while keeping completed plan visible`() {
        val prompt = promptItem(id = "prompt")
        val completedPlan = planItem(
            id = "plan-complete",
            steps = listOf(
                RemodexPlanStep(id = "1", step = "Done", status = RemodexPlanStepStatus.COMPLETED),
            ),
        )

        val layout = buildConversationTimelineLayout(
            messages = listOf(prompt, completedPlan),
            hiddenPromptItemId = "prompt",
        )

        assertEquals(listOf("plan-complete"), layout.timelineItems.map(RemodexConversationItem::id))
    }

    @Test
    fun `plan composer flow surfaces remote prompt before completed plan`() {
        val anchor = assistantMessage(id = "anchor")
        val prompt = promptItem(id = "prompt")
        val completedPlan = planItem(
            id = "plan-complete",
            steps = listOf(
                RemodexPlanStep(id = "1", step = "Done", status = RemodexPlanStepStatus.COMPLETED),
            ),
        )

        val snapshot = resolvePlanComposerFlow(
            messages = listOf(anchor, prompt, completedPlan),
            session = PlanComposerSessionUiState(anchorMessageId = "anchor"),
            latestTurnTerminalState = null,
            activePlanningMode = RemodexPlanningMode.PLAN,
            hasQueuedFollowUps = false,
        )

        assertEquals("prompt", snapshot.takeoverPromptItem?.id)
        assertNull(snapshot.completedPlanItem)
    }

    @Test
    fun `plan composer flow surfaces completed plan only after turn completion`() {
        val anchor = assistantMessage(id = "anchor")
        val completedPlan = planItem(
            id = "plan-complete",
            steps = listOf(
                RemodexPlanStep(id = "1", step = "Done", status = RemodexPlanStepStatus.COMPLETED),
            ),
        )

        val snapshot = resolvePlanComposerFlow(
            messages = listOf(anchor, completedPlan),
            session = PlanComposerSessionUiState(anchorMessageId = "anchor"),
            latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
            activePlanningMode = RemodexPlanningMode.PLAN,
            hasQueuedFollowUps = false,
        )

        assertNull(snapshot.takeoverPromptItem)
        assertEquals("plan-complete", snapshot.completedPlanItem?.id)
    }

    @Test
    fun `plan composer flow does not surface completed plan before turn completion`() {
        val anchor = assistantMessage(id = "anchor")
        val completedPlan = planItem(
            id = "plan-complete",
            steps = listOf(
                RemodexPlanStep(id = "1", step = "Done", status = RemodexPlanStepStatus.COMPLETED),
            ),
        )

        val snapshot = resolvePlanComposerFlow(
            messages = listOf(anchor, completedPlan),
            session = PlanComposerSessionUiState(anchorMessageId = "anchor"),
            latestTurnTerminalState = null,
            activePlanningMode = RemodexPlanningMode.PLAN,
            hasQueuedFollowUps = false,
        )

        assertNull(snapshot.takeoverPromptItem)
        assertNull(snapshot.completedPlanItem)
    }

    @Test
    fun `plan composer flow does not surface completed plan when queued follow ups exist`() {
        val anchor = assistantMessage(id = "anchor")
        val completedPlan = planItem(
            id = "plan-complete",
            steps = listOf(
                RemodexPlanStep(id = "1", step = "Done", status = RemodexPlanStepStatus.COMPLETED),
            ),
        )

        val snapshot = resolvePlanComposerFlow(
            messages = listOf(anchor, completedPlan),
            session = PlanComposerSessionUiState(anchorMessageId = "anchor"),
            latestTurnTerminalState = RemodexTurnTerminalState.COMPLETED,
            activePlanningMode = RemodexPlanningMode.PLAN,
            hasQueuedFollowUps = true,
        )

        assertNull(snapshot.completedPlanItem)
    }

    @Test
    fun `plan accessory snapshot prioritizes current actionable step`() {
        val snapshot = planAccessorySnapshot(
            planItem(
                id = "plan",
                explanation = "High-level explanation",
                steps = listOf(
                    RemodexPlanStep(id = "1", step = "Finished", status = RemodexPlanStepStatus.COMPLETED),
                    RemodexPlanStep(id = "2", step = "Current task", status = RemodexPlanStepStatus.IN_PROGRESS),
                    RemodexPlanStep(id = "3", step = "Next task", status = RemodexPlanStepStatus.PENDING),
                ),
            ),
        )

        assertEquals("Current task", snapshot.summary)
        assertEquals(PlanAccessoryStatus.IN_PROGRESS, snapshot.status)
        assertEquals("1/3", snapshot.progressText)
    }

    @Test
    fun `plan accessory content description includes status progress and summary`() {
        val snapshot = planAccessorySnapshot(
            planItem(
                id = "plan",
                steps = listOf(
                    RemodexPlanStep(id = "1", step = "Investigate issue", status = RemodexPlanStepStatus.IN_PROGRESS),
                    RemodexPlanStep(id = "2", step = "Ship fix", status = RemodexPlanStepStatus.PENDING),
                ),
            ),
        )

        val description = planAccessoryContentDescription(snapshot)

        assertTrue(description.contains("Open active plan."))
        assertTrue(description.contains("In progress"))
        assertTrue(description.contains("0 of 2 complete"))
        assertTrue(description.contains("Investigate issue"))
    }

    private fun planItem(
        id: String,
        explanation: String? = null,
        steps: List<RemodexPlanStep>,
    ): RemodexConversationItem {
        return RemodexConversationItem(
            id = id,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.PLAN,
            text = explanation ?: "Plan update",
            planState = RemodexPlanState(
                explanation = explanation,
                steps = steps,
            ),
        )
    }

    private fun promptItem(id: String): RemodexConversationItem {
        return RemodexConversationItem(
            id = id,
            speaker = ConversationSpeaker.SYSTEM,
            kind = ConversationItemKind.USER_INPUT_PROMPT,
            text = "Needs input",
            structuredUserInputRequest = RemodexStructuredUserInputRequest(
                requestId = JsonPrimitive("request-1"),
                questions = listOf(
                    RemodexStructuredUserInputQuestion(
                        id = "q1",
                        header = "",
                        question = "What should we do?",
                    ),
                ),
            ),
        )
    }

    private fun assistantMessage(id: String): RemodexConversationItem {
        return RemodexConversationItem(
            id = id,
            speaker = ConversationSpeaker.ASSISTANT,
            text = "Anchor",
        )
    }
}
