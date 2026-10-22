package com.moviebox.app.player.features

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Seam for the long-press speed boost.
 *
 * Inert: [available] is false, so the hold gesture is never installed and the
 * speed is never touched.
 */
class SpeedBoostFeature(
    /** The speed the user picked from the speed list. */
    val currentSpeed: () -> Float,
    /** Applies a temporary speed to the player. */
    val onSpeedChanged: (Float) -> Unit,
) {
    /** Whether the boost exists in this build. Its hint and chip hide while false. */
    val available: Boolean = false

    private val boosting = MutableStateFlow(false)

    /** True while the finger holds the video down. */
    val isBoosting: StateFlow<Boolean> = boosting.asStateFlow()

    fun onHoldStart() = Unit

    fun onHoldEnd() = Unit
}
