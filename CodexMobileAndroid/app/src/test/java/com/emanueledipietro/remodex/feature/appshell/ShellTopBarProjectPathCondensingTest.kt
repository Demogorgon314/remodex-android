package com.emanueledipietro.remodex.feature.appshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellTopBarProjectPathCondensingTest {
    @Test
    fun `project path candidates keep the repo tail before collapsing the head`() {
        assertEquals(
            listOf(
                "/Users/wangkai/Developer/github/remodex",
                "/Users/wangkai/.../github/remodex",
                "/Users/.../github/remodex",
                "/.../github/remodex",
                "/Users/wangkai/.../remodex",
                "/Users/.../remodex",
                "/.../remodex",
            ),
            projectPathDisplayCandidates("/Users/wangkai/Developer/github/remodex"),
        )
    }

    @Test
    fun `project path candidates keep short paths unchanged`() {
        assertEquals(
            listOf("/Users/wangkai/remodex"),
            projectPathDisplayCandidates("/Users/wangkai/remodex"),
        )
    }

    @Test
    fun `fit project path picks the first candidate that fits`() {
        val widthBudget = "/Users/.../github/remodex".length.toFloat()

        assertEquals(
            "/Users/.../github/remodex",
            fitProjectPathForWidth(
                projectPath = "/Users/wangkai/Developer/github/remodex",
                maxWidthPx = widthBudget,
                measureTextWidth = { candidate -> candidate.length.toFloat() },
            ),
        )
    }

    @Test
    fun `fit project path falls back to the most aggressive tail preserving candidate`() {
        assertEquals(
            "/.../remodex",
            fitProjectPathForWidth(
                projectPath = "/Users/wangkai/Developer/github/remodex",
                maxWidthPx = 1f,
                measureTextWidth = { candidate -> candidate.length.toFloat() },
            ),
        )
    }

    @Test
    fun `fit project path returns null for blank values`() {
        assertNull(
            fitProjectPathForWidth(
                projectPath = "   ",
                maxWidthPx = 120f,
                measureTextWidth = { candidate -> candidate.length.toFloat() },
            ),
        )
    }
}
