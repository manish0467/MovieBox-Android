package com.moviebox.app.ui.player.audio

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moviebox.app.player.features.AudioTracksFeature

/**
 * Audio track picker, shown on the Playback page.
 *
 * Empty until the audio-track feature lands.
 */
@Composable
fun AudioTracksSection(
    feature: AudioTracksFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
}
