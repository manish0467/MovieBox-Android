package com.moviebox.app.ui.player.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moviebox.app.player.features.LockModeFeature

/**
 * Lock button for the player top bar.
 *
 * Empty until the lock-mode feature lands.
 */
@Composable
fun LockButton(
    feature: LockModeFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
}

/**
 * Touch shield drawn above the controls while the player is locked.
 *
 * Empty until the lock-mode feature lands.
 */
@Composable
fun LockShield(
    feature: LockModeFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
}
