package com.emanueledipietro.remodex.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingScreenTest {
    @Test
    fun `continue action shows codex install confirmation on install step`() {
        assertEquals(
            OnboardingContinueAction.SHOW_CODEX_INSTALL_CONFIRMATION,
            onboardingContinueAction(currentPage = 2),
        )
    }

    @Test
    fun `continue action advances on later setup steps before QR pairing`() {
        assertEquals(
            OnboardingContinueAction.ADVANCE,
            onboardingContinueAction(currentPage = 3),
        )
    }

    @Test
    fun `continue action completes flow on final page`() {
        assertEquals(
            OnboardingContinueAction.COMPLETE,
            onboardingContinueAction(currentPage = 4),
        )
    }
}
