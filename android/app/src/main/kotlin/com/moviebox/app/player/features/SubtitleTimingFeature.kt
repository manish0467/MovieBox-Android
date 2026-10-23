package com.moviebox.app.player.features

import androidx.media3.common.text.CueGroup

/**
 * Seam for the subtitle customizer and its timing offset.
 *
 * Inert: [rendersCues] is false, so Media3's own SubtitleView keeps rendering
 * subtitles exactly as before, and no cue is intercepted.
 */
class SubtitleTimingFeature(
    /**
     * The playback clock the timing offset is measured against. Read it inside
     * a frame loop (`withFrameMillis`) while rendering: it is the player's
     * position, so it follows seeks, pauses and the speed boost by itself.
     */
    val positionMs: () -> Long,
) {
    /** Whether the customizer exists in this build. Its timing controls hide while false. */
    val available: Boolean = false

    /**
     * When true the player hides Media3's SubtitleView and draws the cues in
     * Compose instead, which is what makes a live timing offset possible.
     */
    val rendersCues: Boolean = false

    /** Called from [androidx.media3.common.Player.Listener.onCues]. */
    fun onCues(cueGroup: CueGroup) = Unit
}
