package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationScreenFileChangeSheetTest {
    @Test
    fun `initial expansion keeps small single file diff open`() {
        val chunks = listOf(
            diffChunk(
                id = "first",
                diffCode = """
                    diff --git a/src/Main.kt b/src/Main.kt
                    @@ -1 +1 @@
                    -before
                    +after
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("first"), initialExpandedFileChangeChunkIds(chunks))
    }

    @Test
    fun `initial expansion collapses multi file diffs`() {
        val chunks = listOf(
            diffChunk(id = "first"),
            diffChunk(id = "second", path = "src/Second.kt"),
        )

        assertEquals(emptyList<String>(), initialExpandedFileChangeChunkIds(chunks))
    }

    @Test
    fun `initial expansion collapses oversized single file diff`() {
        val largeDiff = buildString {
            appendLine("diff --git a/src/Main.kt b/src/Main.kt")
            repeat(200) { index ->
                appendLine("@@ -$index +$index @@")
            }
        }.trim()
        val chunks = listOf(diffChunk(id = "first", diffCode = largeDiff))

        assertEquals(emptyList<String>(), initialExpandedFileChangeChunkIds(chunks))
    }

    private fun diffChunk(
        id: String,
        path: String = "src/Main.kt",
        diffCode: String = """
            diff --git a/src/Main.kt b/src/Main.kt
            @@ -1 +1 @@
            -before
            +after
        """.trimIndent(),
    ): PerFileDiffChunk {
        return PerFileDiffChunk(
            id = id,
            path = path,
            action = FileChangeAction.EDITED,
            additions = 1,
            deletions = 1,
            diffCode = diffCode,
        )
    }
}
