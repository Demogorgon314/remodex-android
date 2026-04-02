package com.emanueledipietro.remodex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityPendingThreadDeepLinkTest {
    @Test
    fun `uses the launch intent when there is no saved state`() {
        assertEquals(
            "thread-from-intent",
            resolvePendingThreadDeepLinkId(
                savedPendingThreadDeepLinkId = null,
                didRestoreSavedState = false,
                intentThreadDeepLinkId = "thread-from-intent",
            ),
        )
    }

    @Test
    fun `does not replay a consumed deep link after activity recreation`() {
        assertNull(
            resolvePendingThreadDeepLinkId(
                savedPendingThreadDeepLinkId = null,
                didRestoreSavedState = true,
                intentThreadDeepLinkId = "thread-from-old-intent",
            ),
        )
    }

    @Test
    fun `keeps a pending deep link during activity recreation until it is handled`() {
        assertEquals(
            "thread-still-pending",
            resolvePendingThreadDeepLinkId(
                savedPendingThreadDeepLinkId = "thread-still-pending",
                didRestoreSavedState = true,
                intentThreadDeepLinkId = "thread-from-old-intent",
            ),
        )
    }
}
