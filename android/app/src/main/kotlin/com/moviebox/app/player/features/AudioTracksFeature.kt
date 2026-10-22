package com.moviebox.app.player.features

import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

/**
 * Seam for the in-file audio track picker.
 *
 * Inert: no track list is published and no selection is applied.
 * The picker lives on the Playback page of the player settings sheet.
 */
class AudioTracksFeature(
    /** Supplier for the running player; null before it is created. */
    val player: () -> ExoPlayer?,
) {
    /** Whether the picker exists in this build. Its section hides while false. */
    val available: Boolean = false

    /** Called from [androidx.media3.common.Player.Listener.onTracksChanged]. */
    fun onTracks(tracks: Tracks) = Unit

    /** Selects one audio track, or resets to the player's default when null. */
    fun select(trackId: String?) = Unit

    /** Clears the selection override. */
    fun reset() = Unit
}
