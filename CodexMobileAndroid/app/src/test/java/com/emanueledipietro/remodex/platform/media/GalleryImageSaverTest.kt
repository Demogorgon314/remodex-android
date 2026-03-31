package com.emanueledipietro.remodex.platform.media

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryImageSaverTest {
    @Test
    fun `sanitize display name collapses unsupported characters`() {
        assertEquals(
            "Diagram-Flow-v1",
            GalleryImageSaver.sanitizeDisplayName(" Diagram / Flow : v1 "),
        )
    }

    @Test
    fun `sanitize display name falls back when input is blank`() {
        assertEquals(
            "image",
            GalleryImageSaver.sanitizeDisplayName(" ... "),
        )
    }

    @Test
    fun `build display name appends png suffix and timestamp`() {
        val fileName = GalleryImageSaver.buildDisplayName(
            suggestedName = "diagram",
            timestampMs = 1_743_405_000_000L,
        )
        assertTrue(fileName.startsWith("diagram-"))
        assertTrue(fileName.endsWith(".png"))
    }

    @Test
    fun `legacy devices require write permission`() {
        assertEquals(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            GalleryImageSaver.requiredWritePermission(Build.VERSION_CODES.P),
        )
        assertNull(GalleryImageSaver.requiredWritePermission(Build.VERSION_CODES.Q))
    }
}
