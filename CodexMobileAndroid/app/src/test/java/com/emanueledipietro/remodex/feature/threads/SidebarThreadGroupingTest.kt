package com.emanueledipietro.remodex.feature.threads

import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarThreadGroupingTest {
    @Test
    fun `groups live chats by project and keeps archived chats separate`() {
        val groups = SidebarThreadGrouping.makeGroups(
            threads = listOf(
                thread(id = "1", title = "Alpha", projectPath = "/tmp/alpha"),
                thread(id = "2", title = "Beta", projectPath = "/tmp/beta"),
                thread(
                    id = "3",
                    title = "Archived",
                    projectPath = "/tmp/alpha",
                    syncState = RemodexThreadSyncState.ARCHIVED_LOCAL,
                ),
            ),
            query = "",
        )

        assertEquals(3, groups.size)
        assertEquals(SidebarThreadGroupKind.PROJECT, groups[0].kind)
        assertEquals("alpha", groups[0].label)
        assertEquals(SidebarThreadGroupKind.PROJECT, groups[1].kind)
        assertEquals("beta", groups[1].label)
        assertEquals(SidebarThreadGroupKind.ARCHIVED, groups[2].kind)
        assertEquals(listOf("3"), groups[2].threads.map(RemodexThreadSummary::id))
    }

    @Test
    fun `search query filters project and preview text`() {
        val groups = SidebarThreadGrouping.makeGroups(
            threads = listOf(
                thread(id = "1", title = "Alpha", preview = "fix notifications", projectPath = "/tmp/alpha"),
                thread(id = "2", title = "Beta", preview = "composer parity", projectPath = "/tmp/beta"),
            ),
            query = "composer",
        )

        assertEquals(1, groups.size)
        assertEquals("beta", groups.single().label)
        assertTrue(groups.single().threads.single().preview.contains("composer"))
    }

    @Test
    fun `search query uses iOS display title instead of generic placeholders`() {
        val groups = SidebarThreadGrouping.makeGroups(
            threads = listOf(
                thread(
                    id = "1",
                    title = "Conversation",
                    preview = "sidebar parity follow up",
                    projectPath = "/tmp/alpha",
                ),
            ),
            query = "follow",
        )

        assertEquals(1, groups.size)
        assertEquals("1", groups.single().threads.single().id)
    }

    private fun thread(
        id: String,
        title: String,
        preview: String = "",
        projectPath: String,
        syncState: RemodexThreadSyncState = RemodexThreadSyncState.LIVE,
    ): RemodexThreadSummary {
        return RemodexThreadSummary(
            id = id,
            title = title,
            preview = preview,
            projectPath = projectPath,
            lastUpdatedLabel = "Updated just now",
            isRunning = false,
            syncState = syncState,
            queuedDrafts = 0,
            runtimeLabel = "Auto",
            messages = emptyList(),
        )
    }
}
