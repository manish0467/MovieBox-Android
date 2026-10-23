package com.moviebox.app.ui.player.sleep

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moviebox.app.R
import com.moviebox.app.player.features.SleepTimerFeature
import com.moviebox.app.player.features.sleepTimerMinuteOptions
import com.moviebox.app.ui.player.settings.SettingsOptionRow
import kotlin.math.max

@Composable
fun SleepTimerSection(
    feature: SleepTimerFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
    val state by feature.uiState.collectAsState()

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.sleep_timer),
            style = MaterialTheme.typography.titleSmall,
        )
        sleepTimerMinuteOptions.forEach { minutes ->
            SettingsOptionRow(
                title = stringResource(R.string.sleep_timer_minutes, minutes),
                selected = state.selectedMinutes == minutes,
                onClick = { feature.start(minutes) },
            )
        }
        SettingsOptionRow(
            title = stringResource(R.string.sleep_timer_end_of_episode),
            selected = state.endOfEpisode,
            onClick = feature::startEndOfEpisode,
        )
        SettingsOptionRow(
            title = stringResource(R.string.none_off),
            selected = state.endsAtMs == null && !state.endOfEpisode,
            onClick = feature::cancel,
        )
    }
}

@Composable
fun SleepTimerPill(
    feature: SleepTimerFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
    val state by feature.uiState.collectAsState()
    val remainingMs = state.remainingMs
    val label = when {
        state.finishedMessageVisible -> stringResource(R.string.sleep_timer_finished)
        remainingMs != null -> {
            val minutesLeft = max(1L, (remainingMs + 59_999L) / 60_000L).toInt()
            val duration = stringResource(R.string.sleep_timer_minutes, minutesLeft)
            stringResource(R.string.sleep_timer_remaining, duration)
        }
        state.endOfEpisode -> stringResource(R.string.sleep_timer_end_of_episode)
        else -> return
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
