package com.moviebox.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSeekDragMapperTest {
    @Test
    fun mapsAccumulatedTravelAcrossFixedNinetySecondSpan() {
        val mapper = PlayerSeekDragMapper(startPositionMs = 1_000_000L, durationMs = 7_200_000L, surfaceWidthPx = 1_000f)

        assertEquals(1_045_000L, mapper.addDragDelta(500f))
        assertEquals(955_000L, mapper.addDragDelta(-1_000f))
    }

    @Test
    fun allowsReversingAfterTheTargetReachesAnEdge() {
        val mapper = PlayerSeekDragMapper(startPositionMs = 95_000L, durationMs = 100_000L, surfaceWidthPx = 100f)

        assertEquals(100_000L, mapper.addDragDelta(100f))
        assertEquals(91_000.0, mapper.addDragDelta(-10f).toDouble(), 1.0)
    }

    @Test
    fun ignoresInvalidSurfaceWidthAndClampsStartPosition() {
        val mapper = PlayerSeekDragMapper(startPositionMs = 150L, durationMs = 100L, surfaceWidthPx = 0f)

        assertEquals(100L, mapper.targetPositionMs)
        assertEquals(100L, mapper.addDragDelta(-10f))
    }
}
