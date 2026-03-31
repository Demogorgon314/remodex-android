package com.emanueledipietro.remodex.feature.appshell

import com.emanueledipietro.remodex.model.RemodexGptAccountSnapshot
import com.emanueledipietro.remodex.model.RemodexGptAccountStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AppViewModelVoiceHelpersTest {
    @Test
    fun appendVoiceTranscriptUsesTranscriptWhenDraftIsEmpty() {
        assertEquals(
            "hello from voice",
            appendVoiceTranscriptToDraft(
                currentDraft = "",
                transcript = "  hello from voice  ",
            ),
        )
    }

    @Test
    fun appendVoiceTranscriptAddsSingleSpaceWhenDraftHasText() {
        assertEquals(
            "hello world",
            appendVoiceTranscriptToDraft(
                currentDraft = "hello",
                transcript = "world",
            ),
        )
    }

    @Test
    fun appendVoiceTranscriptRespectsExistingTrailingWhitespace() {
        assertEquals(
            "hello world",
            appendVoiceTranscriptToDraft(
                currentDraft = "hello ",
                transcript = "world",
            ),
        )
    }

    @Test
    fun resolveComposerVoiceRecoveryShowsReconnectGuidance() {
        val recovery = resolveComposerVoiceRecoveryUiState(
            reason = ComposerVoiceRecoveryReason.ReconnectRequired,
            isConnected = false,
            gptAccountSnapshot = RemodexGptAccountSnapshot(),
        )

        assertEquals("Reconnect to your Mac", recovery?.title)
        assertEquals(ComposerVoiceRecoveryAction.RETRY_CONNECTION, recovery?.action)
    }

    @Test
    fun resolveComposerVoiceRecoveryShowsLoginGuidance() {
        val recovery = resolveComposerVoiceRecoveryUiState(
            reason = ComposerVoiceRecoveryReason.LoginRequired,
            isConnected = true,
            gptAccountSnapshot = RemodexGptAccountSnapshot(
                status = RemodexGptAccountStatus.NOT_LOGGED_IN,
            ),
        )

        assertEquals("Use ChatGPT on your Mac", recovery?.title)
        assertEquals(ComposerVoiceRecoveryAction.OPEN_SETTINGS, recovery?.action)
    }

    @Test
    fun resolveComposerVoiceRecoveryShowsReauthGuidance() {
        val recovery = resolveComposerVoiceRecoveryUiState(
            reason = ComposerVoiceRecoveryReason.ReauthenticationRequired,
            isConnected = true,
            gptAccountSnapshot = RemodexGptAccountSnapshot(
                status = RemodexGptAccountStatus.AUTHENTICATED,
                needsReauth = true,
            ),
        )

        assertEquals("Refresh ChatGPT sign-in", recovery?.title)
        assertEquals(ComposerVoiceRecoveryAction.OPEN_SETTINGS, recovery?.action)
    }

    @Test
    fun resolveComposerVoiceRecoveryShowsVoiceSyncProgress() {
        val recovery = resolveComposerVoiceRecoveryUiState(
            reason = ComposerVoiceRecoveryReason.VoiceSyncInProgress,
            isConnected = true,
            gptAccountSnapshot = RemodexGptAccountSnapshot(
                status = RemodexGptAccountStatus.AUTHENTICATED,
                tokenReady = false,
            ),
        )

        assertEquals("Voice sync in progress", recovery?.title)
        assertEquals(ComposerVoiceRecoveryTone.PROGRESS, recovery?.tone)
    }

    @Test
    fun resolveComposerVoiceRecoveryShowsMicrophonePermissionRecovery() {
        val recovery = resolveComposerVoiceRecoveryUiState(
            reason = ComposerVoiceRecoveryReason.MicrophonePermissionRequired(requiresSettings = false),
            isConnected = true,
            gptAccountSnapshot = RemodexGptAccountSnapshot(
                status = RemodexGptAccountStatus.AUTHENTICATED,
                tokenReady = true,
            ),
        )

        assertEquals("Microphone access needed", recovery?.title)
        assertEquals(ComposerVoiceRecoveryAction.RETRY_VOICE, recovery?.action)
    }

    @Test
    fun resolveComposerVoiceRecoveryPreservesGenericFailures() {
        val recovery = resolveComposerVoiceRecoveryUiState(
            reason = ComposerVoiceRecoveryReason.Generic("Could not transcribe this voice note."),
            isConnected = true,
            gptAccountSnapshot = RemodexGptAccountSnapshot(
                status = RemodexGptAccountStatus.AUTHENTICATED,
                tokenReady = true,
            ),
        )

        assertEquals("Voice mode unavailable", recovery?.title)
        assertEquals("Could not transcribe this voice note.", recovery?.summary)
        assertEquals(ComposerVoiceRecoveryTone.ERROR, recovery?.tone)
    }
}
