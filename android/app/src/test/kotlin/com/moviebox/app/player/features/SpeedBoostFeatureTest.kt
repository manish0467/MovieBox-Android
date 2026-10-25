package com.moviebox.app.player.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedBoostFeatureTest {
    @Test
    fun holdDoublesTheSpeedOnceAndReleaseRestoresThePickedSpeed() {
        val applied = mutableListOf<Float>()
        var picked = 1.5f
        val feature = SpeedBoostFeature(
            currentSpeed = { picked },
            onSpeedChanged = { applied.add(it) },
        )

        assertTrue(feature.available)

        feature.onHoldStart()
        feature.onHoldStart() // a second start must not re-apply
        assertTrue(feature.isBoosting.value)
        assertEquals(listOf(SpeedBoostFeature.BOOST_SPEED), applied)

        picked = 1.75f // the user changes speed mid-hold
        feature.onHoldEnd()
        feature.onHoldEnd() // a second end must not re-restore
        assertFalse(feature.isBoosting.value)
        assertEquals(listOf(SpeedBoostFeature.BOOST_SPEED, 1.75f), applied)
    }

    @Test
    fun releaseWithoutHoldTouchesNothing() {
        val applied = mutableListOf<Float>()
        val feature = SpeedBoostFeature(
            currentSpeed = { 1.0f },
            onSpeedChanged = { applied.add(it) },
        )

        feature.onHoldEnd()
        assertTrue(applied.isEmpty())
    }
}
