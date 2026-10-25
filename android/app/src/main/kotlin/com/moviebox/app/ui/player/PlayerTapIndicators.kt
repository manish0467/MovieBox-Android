package com.moviebox.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moviebox.app.R

/**
 * The accumulated double-tap seek feedback: a pill on the tapped side showing
 * the direction and the stacked seconds, like Flow's seek overlay.
 */
@Composable
fun DoubleTapSeekIndicator(
    forward: Boolean,
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                contentDescription = stringResource(
                    if (forward) R.string.player_seek_forward_desc else R.string.player_seek_back_desc,
                ),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "${if (forward) "+" else "−"}${seconds}s",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The center play/pause pulse shown on every play-state change, whichever
 * control caused it (button, dialog, double-tap or a headset key).
 */
@Composable
fun CenterPlayPauseIndicator(
    playing: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.6f) + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.player_play_pause),
                tint = Color.White,
                modifier = Modifier
                    .padding(18.dp)
                    .size(38.dp),
            )
        }
    }
}
