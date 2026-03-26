package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertEquals
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
}
