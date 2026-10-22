package com.moviebox.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moviebox.app.R
import kotlinx.coroutines.delay

@Composable
fun PlayerTransportControls(
    uiState: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeekPreview: (Long?) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onHideControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.controlsVisible, uiState.isPlaying, uiState.activeDialog, uiState.seekScrubPositionMs) {
        if (!uiState.controlsVisible || !uiState.isPlaying || uiState.activeDialog != PlayerDialogType.NONE || uiState.seekScrubPositionMs != null) {
            return@LaunchedEffect
        }
        delay(CONTROLS_AUTO_HIDE_MS)
        onHideControls()
    }

    if (!uiState.controlsVisible || uiState.isInPipMode) return

    val durationMs = uiState.durationMs.coerceAtLeast(0L)
    val playedFraction = if (durationMs > 0L) uiState.currentPositionMs.toFloat() / durationMs else 0f
    var seekFraction by remember { mutableFloatStateOf(playedFraction.coerceIn(0f, 1f)) }
    var isSeeking by remember { mutableStateOf(false) }
    LaunchedEffect(playedFraction, isSeeking) {
        if (!isSeeking) seekFraction = playedFraction.coerceIn(0f, 1f)
    }

    Surface(
        color = Color.Black.copy(alpha = 0.68f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlayerTime((durationMs * seekFraction).toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                Slider(
                    value = seekFraction,
                    onValueChange = { fraction ->
                        seekFraction = fraction
                        isSeeking = true
                        onSeekPreview((durationMs * fraction).toLong())
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0L) onSeekCommit((durationMs * seekFraction).toLong())
                        isSeeking = false
                        onSeekPreview(null)
                    },
                    enabled = durationMs > 0L,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatPlayerTime(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSkipBack) {
                    Icon(
                        imageVector = Icons.Filled.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (uiState.isPlaying) R.string.pause else R.string.play),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onSkipForward) {
                    Icon(
                        imageVector = Icons.Filled.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private fun formatPlayerTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val CONTROLS_AUTO_HIDE_MS = 5_000L
