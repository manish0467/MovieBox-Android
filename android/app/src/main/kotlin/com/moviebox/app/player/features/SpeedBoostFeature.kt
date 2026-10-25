package com.moviebox.app.player.features

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The long-press speed boost: holding the video doubles the speed and shows
 * the boost chip; lifting restores the speed the user picked.
 */
class SpeedBoostFeature(
    /** The speed the user picked from the speed list. */
    val currentSpeed: () -> Float,
    /** Applies a temporary speed to the player. */
    val onSpeedChanged: (Float) -> Unit,
) {
    /** Whether the boost exists in this build. Its hint and chip hide while false. */
    val available: Boolean = true

    private val boosting = MutableStateFlow(false)

    /** True while the finger holds the video down. */
    val isBoosting: StateFlow<Boolean> = boosting.asStateFlow()

    fun onHoldStart() {
        if (boosting.value) return
        boosting.value = true
        onSpeedChanged(BOOST_SPEED)
    }

    fun onHoldEnd() {
        if (!boosting.value) return
        boosting.value = false
        onSpeedChanged(currentSpeed())
    }

    companion object {
        const val BOOST_SPEED = 2.0f
    }
}
