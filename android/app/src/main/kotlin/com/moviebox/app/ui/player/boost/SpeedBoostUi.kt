package com.moviebox.app.ui.player.boost

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moviebox.app.R
import com.moviebox.app.player.features.SpeedBoostFeature

/**
 * One-line hint for the long-press boost, shown on the Playback page.
 */
@Composable
fun SpeedBoostHint(
    feature: SpeedBoostFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
    Text(
        text = stringResource(R.string.player_boost_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * Speed chip, shown in the player HUD while the video is held down.
 */
@Composable
fun BoostChip(
    feature: SpeedBoostFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
    val boosting by feature.isBoosting.collectAsState()
    if (!boosting) return

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.player_boost_chip),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
