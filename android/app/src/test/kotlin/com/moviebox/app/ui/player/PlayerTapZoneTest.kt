package com.moviebox.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTapZoneTest {
    @Test
    fun zonesSplitTheSurfaceIntoThirds() {
        assertEquals(TapZone.BACK, tapZoneOf(0f, 300f))
        assertEquals(TapZone.BACK, tapZoneOf(99.9f, 300f))
        assertEquals(TapZone.CENTER, tapZoneOf(100.1f, 300f))
        assertEquals(TapZone.CENTER, tapZoneOf(150f, 300f))
        assertEquals(TapZone.CENTER, tapZoneOf(199.9f, 300f))
        assertEquals(TapZone.FORWARD, tapZoneOf(200.1f, 300f))
        assertEquals(TapZone.FORWARD, tapZoneOf(300f, 300f))
    }

    @Test
    fun degenerateWidthFallsBackToCenter() {
        assertEquals(TapZone.CENTER, tapZoneOf(10f, 0f))
    }

    @Test
    fun doubleTapsAccumulateOnlyWithinTheWindowAndZone() {
        val now = 100_000L
        assertTrue(
            isContinuingDoubleTap(
                lastForward = true,
                lastTapAtMs = now - 1_000L,
                forward = true,
                nowMs = now,
            ),
        )
        // Expired window.
        assertFalse(
            isContinuingDoubleTap(
                lastForward = true,
                lastTapAtMs = now - 2_500L,
                forward = true,
                nowMs = now,
            ),
        )
        // Opposite zone starts a fresh accumulation.
        assertFalse(
            isContinuingDoubleTap(
                lastForward = false,
                lastTapAtMs = now - 1_000L,
                forward = true,
                nowMs = now,
            ),
        )
        // First ever tap.
        assertFalse(
            isContinuingDoubleTap(
                lastForward = null,
                lastTapAtMs = 0L,
                forward = true,
                nowMs = now,
            ),
        )
    }
}
