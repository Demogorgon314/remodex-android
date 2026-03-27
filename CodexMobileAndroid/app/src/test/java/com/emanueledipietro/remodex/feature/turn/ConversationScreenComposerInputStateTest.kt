package com.emanueledipietro.remodex.feature.turn

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationScreenComposerInputStateTest {
    @Test
    fun `sync composer input preserves selection when external text is unchanged`() {
        val currentValue = TextFieldValue(
            text = "@Dockerfile ",
            selection = TextRange(3),
        )

        val syncedValue = syncComposerInputValue(
            currentValue = currentValue,
            externalText = "@Dockerfile ",
        )

        assertEquals(currentValue, syncedValue)
    }

    @Test
    fun `sync composer input moves caret to end after file mention replacement`() {
        val currentValue = TextFieldValue(
            text = "@Do",
            selection = TextRange(3),
        )

        val syncedValue = syncComposerInputValue(
            currentValue = currentValue,
            externalText = "@Dockerfile ",
        )

        assertEquals("@Dockerfile ", syncedValue.text)
        assertEquals(TextRange("@Dockerfile ".length), syncedValue.selection)
    }

    @Test
    fun `sync composer input resets caret to zero when composer is cleared`() {
        val currentValue = TextFieldValue(
            text = "\$mat-cli ",
            selection = TextRange(4),
        )

        val syncedValue = syncComposerInputValue(
            currentValue = currentValue,
            externalText = "",
        )

        assertEquals("", syncedValue.text)
        assertEquals(TextRange(0), syncedValue.selection)
    }
}
