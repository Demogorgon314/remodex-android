package com.emanueledipietro.remodex.feature.turn

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationScreenComposerInputStateTest {
    @Test
    fun `sync composer input preserves selection when external text is unchanged`() {
        val currentState = TextFieldState(
            initialText = "@Dockerfile ",
            initialSelection = TextRange(3),
        )

        val didSync = syncComposerInputState(
            currentState = currentState,
            externalText = "@Dockerfile ",
        )

        assertFalse(didSync)
        assertEquals("@Dockerfile ", currentState.text.toString())
        assertEquals(TextRange(3), currentState.selection)
    }

    @Test
    fun `sync composer input moves caret to end after file mention replacement`() {
        val currentState = TextFieldState(
            initialText = "@Do",
            initialSelection = TextRange(3),
        )

        val didSync = syncComposerInputState(
            currentState = currentState,
            externalText = "@Dockerfile ",
        )

        assertTrue(didSync)
        assertEquals("@Dockerfile ", currentState.text.toString())
        assertEquals(TextRange("@Dockerfile ".length), currentState.selection)
    }

    @Test
    fun `sync composer input resets caret to zero when composer is cleared`() {
        val currentState = TextFieldState(
            initialText = "\$mat-cli ",
            initialSelection = TextRange(4),
        )

        val didSync = syncComposerInputState(
            currentState = currentState,
            externalText = "",
        )

        assertTrue(didSync)
        assertEquals("", currentState.text.toString())
        assertEquals(TextRange(0), currentState.selection)
    }
}
