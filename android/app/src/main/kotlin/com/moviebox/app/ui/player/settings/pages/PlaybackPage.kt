package com.moviebox.app.ui.player.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moviebox.app.R
import com.moviebox.app.player.features.PlayerFeatures
import com.moviebox.app.ui.player.PlayerUiState
import com.moviebox.app.ui.player.audio.AudioTracksSection
import com.moviebox.app.ui.player.boost.SpeedBoostHint
import com.moviebox.app.ui.player.settings.SettingsOptionRow
import com.moviebox.app.ui.player.settings.SettingsPageScaffold
import com.moviebox.app.ui.player.sleep.SleepTimerSection

/**
 * Playback page: the speed list, plus the sections that the audio, sleep timer
 * and speed boost features add. Those sections render nothing until enabled.
 */
@Composable
fun PlaybackPage(
    uiState: PlayerUiState,
    features: PlayerFeatures,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit = {},
) {
    val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    SettingsPageScaffold(
        title = stringResource(R.string.playback),
        onClose = onDismiss,
        actions = {
            if (uiState.playbackSpeed != 1.0f) {
                TextButton(onClick = {
                    onSelectSpeed(1.0f)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.reset_playback_speed))
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(playbackSpeeds) { speed ->
                SettingsOptionRow(
                    title = "${speed}x" + if (speed == 1.0f) " (Normal)" else "",
                    selected = uiState.playbackSpeed == speed,
                    onClick = {
                        onSelectSpeed(speed)
                        onDismiss()
                    },
                )
            }

            item { AudioTracksSection(feature = features.audio) }
            item { SleepTimerSection(feature = features.sleep) }
            item { SpeedBoostHint(feature = features.boost) }
            item {
                SettingsOptionRow(
                    title = "Open in external player",
                    selected = false,
                    onClick = {
                        onDismiss()
                        onOpenExternal()
                    },
                )
            }
        }
    }
}
