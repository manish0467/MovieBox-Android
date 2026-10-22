package com.moviebox.app.player.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Seam for the sleep timer.
 *
 * Inert: no timer can be started, so [remainingMs] stays null and nothing is
 * ever paused. The timer counts wall-clock time, not playback time.
 */
class SleepTimerFeature(
    /** Scope the countdown runs in; the activity's lifecycle is the right owner. */
    val scope: CoroutineScope,
    /** Pauses playback when the timer fires. */
    val onPause: () -> Unit,
) {
    /** Whether the timer row exists in this build. It hides while false. */
    val available: Boolean = false

    private val remaining = MutableStateFlow<Long?>(null)

    /** Milliseconds left, or null when no timer runs. */
    val remainingMs: StateFlow<Long?> = remaining.asStateFlow()

    /** Starts a timer of [minutes] wall-clock minutes. */
    fun start(minutes: Int) = Unit

    /** Stops playback when the current episode ends. */
    fun startEndOfEpisode() = Unit

    fun cancel() = Unit

    /** Called from [androidx.media3.common.Player.Listener.onPlaybackStateChanged]. */
    fun onPlaybackEnded() = Unit
}
