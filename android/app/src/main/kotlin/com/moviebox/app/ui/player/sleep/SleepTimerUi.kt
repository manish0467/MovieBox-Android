package com.moviebox.app.ui.player.sleep

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moviebox.app.player.features.SleepTimerFeature

/**
 * Sleep timer, shown on the Playback page.
 *
 * Empty until the sleep-timer feature lands.
 */
@Composable
fun SleepTimerSection(
    feature: SleepTimerFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
}

/**
 * Countdown pill, shown in the player HUD while a timer runs.
 *
 * Empty until the sleep-timer feature lands.
 */
@Composable
fun SleepTimerPill(
    feature: SleepTimerFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
}
