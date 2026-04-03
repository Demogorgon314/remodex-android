package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationScreenFileChangeSheetTest {
    @Test
    fun `timeline file change presentation opens for hunk only rendered diff`() {
        val renderState = FileChangeRenderParser.renderState(
            """
                Status: completed

                Path: src/Main.kt
                Kind: update
                ```diff
                @@ -1 +1,2 @@
                -old
                +new
                +more
                ```
            """.trimIndent(),
        )

        val presentation = buildTimelineFileChangeSheetPresentation(
            messageId = "message-1",
            renderState = renderState,
        )

        assertEquals(listOf("src/Main.kt"), presentation?.diffChunks?.map(PerFileDiffChunk::path))
        assertEquals(2, presentation?.diffChunks?.single()?.additions)
        assertEquals(1, presentation?.diffChunks?.single()?.deletions)
    }

    @Test
    fun `timeline file change presentation ignores totals only summaries`() {
        val renderState = FileChangeRenderParser.renderState(
            """
                Status: completed

                Path: src/SummaryOnly.kt
                Kind: update
                Totals: +4 -2
            """.trimIndent(),
        )

        val presentation = buildTimelineFileChangeSheetPresentation(
            messageId = "message-2",
            renderState = renderState,
        )

        assertEquals(null, presentation)
    }

    @Test
    fun `file change group counts aggregate additions and deletions`() {
        val entries = listOf(
            FileChangeSummaryEntry(
                path = "src/Main.kt",
                additions = 3,
                deletions = 1,
                action = FileChangeAction.EDITED,
            ),
            FileChangeSummaryEntry(
                path = "src/Other.kt",
                additions = 2,
                deletions = 4,
                action = FileChangeAction.EDITED,
            ),
        )

        assertEquals(5 to 5, aggregateFileChangeDiffCounts(entries))
    }

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
