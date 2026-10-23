package com.moviebox.app.ui.player.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import com.moviebox.app.R
import com.moviebox.app.player.features.AudioTracksFeature
import com.moviebox.app.ui.player.settings.SettingsOptionRow

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
    val state by feature.uiState.collectAsState()
    if (state.options.size <= 1) return

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.player_audio),
            style = MaterialTheme.typography.titleSmall,
        )
        SettingsOptionRow(
            title = "${stringResource(R.string.audio_track)}: ${stringResource(R.string.auto)}",
            selected = state.selectedGroupId == null,
            onClick = feature::reset,
        )
        state.options.forEach { option ->
            val baseLabel = option.label
                ?: option.language
                ?: stringResource(R.string.track_number, option.trackNumber)
            val title = if (option.channelCount > 1) {
                stringResource(R.string.audio_track_channels, baseLabel, option.channelCount)
            } else {
                baseLabel
            }
            SettingsOptionRow(
                title = title,
                selected = state.selectedGroupId == option.groupId,
                onClick = { feature.select(option.groupId) },
            )
        }
    }
}
