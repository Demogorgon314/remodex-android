package com.emanueledipietro.remodex.feature.appshell

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CompactSidebarPresentationTest {
    @Test
    fun `compact sidebar offset keeps the panel just offscreen until opened`() {
        assertEquals((-330).dp, compactSidebarOffset(sidebarWidth = 330.dp, contentOffset = 0.dp))
        assertEquals(0.dp, compactSidebarOffset(sidebarWidth = 330.dp, contentOffset = 330.dp))
    }
}
