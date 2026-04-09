package com.emanueledipietro.remodex.feature.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingScannerViewTest {
    @Test
    fun `full preview framing bounds uses full portrait container`() {
        assertEquals(
            ScannerFrameBounds(left = 0, top = 0, right = 1080, bottom = 1920),
            calculateFullPreviewFramingBounds(
                container = ScannerFrameBounds(left = 0, top = 0, right = 1080, bottom = 1920),
                surface = ScannerFrameBounds(left = 0, top = 0, right = 1080, bottom = 1920),
            ),
        )
    }

    @Test
    fun `full preview framing bounds uses full landscape container`() {
        assertEquals(
            ScannerFrameBounds(left = 0, top = 0, right = 1920, bottom = 1080),
            calculateFullPreviewFramingBounds(
                container = ScannerFrameBounds(left = 0, top = 0, right = 1920, bottom = 1080),
                surface = ScannerFrameBounds(left = 0, top = 0, right = 1920, bottom = 1080),
            ),
        )
    }

    @Test
    fun `full preview framing bounds keeps embedded scanner card fully scannable`() {
        assertEquals(
            ScannerFrameBounds(left = 0, top = 0, right = 920, bottom = 280),
            calculateFullPreviewFramingBounds(
                container = ScannerFrameBounds(left = 0, top = 0, right = 920, bottom = 280),
                surface = ScannerFrameBounds(left = 0, top = 0, right = 920, bottom = 280),
            ),
        )
    }

    @Test
    fun `full preview framing bounds uses visible intersection when preview is center cropped`() {
        assertEquals(
            ScannerFrameBounds(left = 0, top = 0, right = 1080, bottom = 1920),
            calculateFullPreviewFramingBounds(
                container = ScannerFrameBounds(left = 0, top = 0, right = 1080, bottom = 1920),
                surface = ScannerFrameBounds(left = -240, top = 0, right = 1320, bottom = 1920),
            ),
        )
    }
}
