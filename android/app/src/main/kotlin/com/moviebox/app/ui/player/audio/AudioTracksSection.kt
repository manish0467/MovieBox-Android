package com.moviebox.app.ui.player.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moviebox.app.R
import com.moviebox.app.player.features.AudioTracksFeature
import com.moviebox.app.player.features.presentAudioTrack
import com.moviebox.app.player.features.shouldShowAudioTrackSection
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
    if (!shouldShowAudioTrackSection(state.options.size)) return

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
            val presentation = presentAudioTrack(
                option,
                fallbackTrackLabel = stringResource(R.string.track_number, option.trackNumber),
            )
            val title = if (presentation.channelCountSuffix != null) {
                stringResource(
                    R.string.audio_track_channels,
                    presentation.label,
                    presentation.channelCountSuffix,
                )
            } else {
                presentation.label
            }
            SettingsOptionRow(
                title = title,
                selected = state.selectedGroupId == option.groupId,
                onClick = { feature.select(option.groupId) },
            )
        }
    }
}
