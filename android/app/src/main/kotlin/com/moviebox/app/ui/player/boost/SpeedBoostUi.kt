package com.moviebox.app.ui.player.boost

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moviebox.app.player.features.SpeedBoostFeature

/**
 * One-line hint for the long-press boost, shown on the Playback page.
 *
 * Empty until the speed-boost feature lands.
 */
@Composable
fun SpeedBoostHint(
    feature: SpeedBoostFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
}

/**
 * Speed chip, shown in the player HUD while the video is held down.
 *
 * Empty until the speed-boost feature lands.
 */
@Composable
fun BoostChip(
    feature: SpeedBoostFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
}
