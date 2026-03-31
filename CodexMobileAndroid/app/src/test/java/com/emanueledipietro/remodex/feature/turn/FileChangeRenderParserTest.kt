package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChangeRenderParserTest {
    @Test
    fun diffChunks_extractsSingleRenderedSectionDiff() {
        val rendered = """
            Status: success

            Path: app/src/Main.kt
            Kind: update
            ```diff
            diff --git a/app/src/Main.kt b/app/src/Main.kt
            index 1111111..2222222 100644
            --- a/app/src/Main.kt
            +++ b/app/src/Main.kt
            @@ -1 +1,2 @@
            -old
            +new
            +more
            ```
        """.trimIndent()

        val renderState = FileChangeRenderParser.renderState(rendered)
        val entries = renderState.summary?.entries.orEmpty()
        val chunks = FileChangeRenderParser.diffChunks(rendered, entries)

        assertEquals(1, entries.size)
        assertEquals("app/src/Main.kt", entries.single().path)
        assertEquals(FileChangeAction.EDITED, entries.single().action)
        assertEquals(1, entries.single().deletions)
        assertEquals(2, entries.single().additions)
        assertEquals(1, chunks.size)
        assertTrue(chunks.single().diffCode.contains("@@ -1 +1,2 @@"))
    }

    @Test
    fun renderState_parsesUnifiedPatchIntoEntriesGroupsAndChunks() {
        val patch = """
            diff --git a/src/Edited.kt b/src/Edited.kt
            index 1111111..2222222 100644
            --- a/src/Edited.kt
            +++ b/src/Edited.kt
            @@ -1 +1 @@
            -before
            +after
            diff --git a/src/Deleted.kt b/src/Deleted.kt
            deleted file mode 100644
            index 3333333..0000000
            --- a/src/Deleted.kt
            +++ /dev/null
            @@ -1 +0,0 @@
            -gone
        """.trimIndent()

        val renderState = FileChangeRenderParser.renderState(patch)
        val entries = renderState.summary?.entries.orEmpty()
        val groups = FileChangeRenderParser.grouped(entries)
        val chunks = FileChangeRenderParser.diffChunks(patch, entries)

        assertEquals(listOf("src/Edited.kt", "src/Deleted.kt"), entries.map(FileChangeSummaryEntry::path))
        assertEquals(listOf(FileChangeAction.EDITED, FileChangeAction.DELETED), entries.map(FileChangeSummaryEntry::action))
        assertEquals(listOf("Edited", "Deleted"), groups.map(FileChangeGroup::key))
        assertEquals(2, chunks.size)
        assertTrue(chunks.first().diffCode.contains("diff --git a/src/Edited.kt b/src/Edited.kt"))
        assertTrue(chunks.last().diffCode.contains("deleted file mode 100644"))
    }

    @Test
    fun renderState_parsesIosStyleTotalsWithoutDiff() {
        val rendered = """
            Status: completed

            Path: src/SummaryOnly.kt
            Kind: update
            Totals: +4 -2
        """.trimIndent()

        val renderState = FileChangeRenderParser.renderState(rendered)
        val entry = renderState.summary?.entries?.single()

        assertEquals("src/SummaryOnly.kt", entry?.path)
        assertEquals(FileChangeAction.EDITED, entry?.action)
        assertEquals(4, entry?.additions)
        assertEquals(2, entry?.deletions)
    }

    @Test
    fun renderState_discardsMetadataOnlyRenderedEntriesWithoutTotalsOrPatchEvidence() {
        val rendered = """
            Status: completed

            Path: src/Empty.kt
            Kind: update
        """.trimIndent()

        val renderState = FileChangeRenderParser.renderState(rendered)
        val chunks = FileChangeRenderParser.diffChunks(rendered, renderState.summary?.entries.orEmpty())

        assertNull(renderState.summary)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun renderState_discardsZeroTotalsRenderedEntriesWithoutPatchEvidence() {
        val rendered = """
            Status: completed

            Path: src/Zero.kt
            Kind: add
            Totals: +0 -0
        """.trimIndent()

        val renderState = FileChangeRenderParser.renderState(rendered)
        val chunks = FileChangeRenderParser.diffChunks(rendered, renderState.summary?.entries.orEmpty())

        assertNull(renderState.summary)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun renderState_prefersPatchActionOverGenericUpdateKind() {
        val rendered = """
            Status: completed

            Path: src/Deleted.kt
            Kind: update
            Totals: +0 -1

            ```diff
            diff --git a/src/Deleted.kt b/src/Deleted.kt
            deleted file mode 100644
            index 3333333..0000000
            --- a/src/Deleted.kt
            +++ /dev/null
            @@ -1 +0,0 @@
            -gone
            ```
        """.trimIndent()

        val renderState = FileChangeRenderParser.renderState(rendered)
        val entry = renderState.summary?.entries?.single()

        assertEquals("src/Deleted.kt", entry?.path)
        assertEquals(FileChangeAction.DELETED, entry?.action)
        assertEquals(0, entry?.additions)
        assertEquals(1, entry?.deletions)
    }

    @Test
    fun diffChunks_extractsMultipleRenderedMessagesJoinedWithoutSectionSeparator() {
        val rendered = """
            Status: completed

            Path: src/First.kt
            Kind: update
            Totals: +1 -1

            ```diff
            diff --git a/src/First.kt b/src/First.kt
            index 1111111..2222222 100644
            --- a/src/First.kt
            +++ b/src/First.kt
            @@ -1 +1 @@
            -before
            +after
            ```

            Status: completed

            Path: src/Second.kt
            Kind: update
            Totals: +2 -0

            ```diff
            diff --git a/src/Second.kt b/src/Second.kt
            index 3333333..4444444 100644
            --- a/src/Second.kt
            +++ b/src/Second.kt
            @@ -1 +1,2 @@
            -value
            +value
            +extra
            ```
        """.trimIndent()
        val entries = listOf(
            FileChangeSummaryEntry(
                path = "src/First.kt",
                additions = 1,
                deletions = 1,
                action = FileChangeAction.EDITED,
            ),
            FileChangeSummaryEntry(
                path = "src/Second.kt",
                additions = 2,
                deletions = 0,
                action = FileChangeAction.EDITED,
            ),
        )

        val chunks = FileChangeRenderParser.diffChunks(rendered, entries)

        assertEquals(listOf("src/First.kt", "src/Second.kt"), chunks.map(PerFileDiffChunk::path))
        assertTrue(chunks[0].diffCode.contains("diff --git a/src/First.kt b/src/First.kt"))
        assertTrue(chunks[1].diffCode.contains("diff --git a/src/Second.kt b/src/Second.kt"))
    }

    @Test
    fun diffChunks_doesNotCreateEmptyDetailChunksFromTotalsOnlyEntries() {
        val rendered = """
            Status: completed

            Path: src/SummaryOnly.kt
            Kind: update
            Totals: +4 -2
        """.trimIndent()

        val renderState = FileChangeRenderParser.renderState(rendered)
        val entries = renderState.summary?.entries.orEmpty()
        val chunks = FileChangeRenderParser.diffChunks(rendered, entries)

        assertEquals(1, entries.size)
        assertTrue(chunks.isEmpty())
    }
}
