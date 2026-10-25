package com.moviebox.app.ui.player.settings.pages

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moviebox.app.R
import com.moviebox.app.player.PlayerSessionPreferences
import com.moviebox.app.player.features.PlayerFeatures
import com.moviebox.app.player.features.SubtitleTimingFeature
import com.moviebox.app.ui.player.PlayerUiState
import com.moviebox.app.ui.player.settings.SettingsOptionRow
import com.moviebox.app.ui.player.settings.SettingsPageScaffold

/**
 * Subtitles page: the track list, with a "Style" entry that swaps the page for
 * the customizer. Both used to be separate dialogs.
 */
@Composable
fun SubtitlesPage(
    uiState: PlayerUiState,
    features: PlayerFeatures,
    initialStyle: Boolean,
    onSelectSubtitle: (com.moviebox.app.core.SubtitleOption?) -> Unit,
    onUpdateSubtitleStyle: (textSizeSp: Float, textColor: Int, backgroundColor: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var styleVisible by remember { mutableStateOf(initialStyle) }

    if (styleVisible) {
        SubtitleStyleSection(
            feature = features.subtitles,
            onUpdateSubtitleStyle = onUpdateSubtitleStyle,
            onDone = { styleVisible = false },
        )
        return
    }

    SettingsPageScaffold(
        title = stringResource(R.string.subtitles),
        onClose = onDismiss,
        actions = {
            TextButton(onClick = { styleVisible = true }) {
                Text(stringResource(R.string.style))
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            item {
                SettingsOptionRow(
                    title = stringResource(R.string.none_off),
                    selected = uiState.selectedSubtitle == null || uiState.selectedSubtitle == "None",
                    onClick = {
                        onSelectSubtitle(null)
                        onDismiss()
                    },
                )
            }
            items(uiState.availableSubtitles) { sub ->
                SettingsOptionRow(
                    title = sub.name,
                    selected = uiState.selectedSubtitle == sub.name,
                    onClick = {
                        onSelectSubtitle(sub)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Subtitle customizer. Holds the styling that used to live in its own dialog;
 * the timing offset and the preview card belong here as well.
 */
@Composable
fun SubtitleStyleSection(
    feature: SubtitleTimingFeature,
    onUpdateSubtitleStyle: (textSizeSp: Float, textColor: Int, backgroundColor: Int) -> Unit,
    onDone: () -> Unit,
) {
    val fontSizes = listOf(
        stringResource(R.string.size_small) to 14f,
        stringResource(R.string.size_medium) to 18f,
        stringResource(R.string.size_large) to 24f,
        stringResource(R.string.size_extra_large) to 30f,
    )
    val fontColors = listOf(
        stringResource(R.string.colour_white) to android.graphics.Color.WHITE,
        stringResource(R.string.colour_yellow) to android.graphics.Color.YELLOW,
        stringResource(R.string.colour_cyan) to android.graphics.Color.CYAN,
        stringResource(R.string.colour_green) to android.graphics.Color.GREEN,
    )
    val bgColors = listOf(
        stringResource(R.string.background_transparent) to android.graphics.Color.TRANSPARENT,
        stringResource(R.string.background_semi_transparent_black) to android.graphics.Color.argb(128, 0, 0, 0),
        stringResource(R.string.background_solid_black) to android.graphics.Color.BLACK,
    )

    var currentSize by remember { mutableStateOf(PlayerSessionPreferences.subtitleTextSizeSp) }
    var currentColor by remember { mutableStateOf(PlayerSessionPreferences.subtitleTextColor) }
    var currentBg by remember { mutableStateOf(PlayerSessionPreferences.subtitleBackgroundColor) }

    SettingsPageScaffold(
        title = stringResource(R.string.subtitle_styling),
        onClose = onDone,
        closeLabel = stringResource(R.string.done),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            item {
                Text(
                    text = stringResource(R.string.subtitle_text_size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(fontSizes) { (label, size) ->
                SettingsOptionRow(
                    title = label,
                    selected = currentSize == size,
                    onClick = {
                        currentSize = size
                        onUpdateSubtitleStyle(currentSize, currentColor, currentBg)
                    },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.subtitle_text_colour),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(fontColors) { (label, colorInt) ->
                SettingsOptionRow(
                    title = label,
                    selected = currentColor == colorInt,
                    onClick = {
                        currentColor = colorInt
                        onUpdateSubtitleStyle(currentSize, currentColor, currentBg)
                    },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.subtitle_background_colour),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(bgColors) { (label, bgInt) ->
                SettingsOptionRow(
                    title = label,
                    selected = currentBg == bgInt,
                    onClick = {
                        currentBg = bgInt
                        onUpdateSubtitleStyle(currentSize, currentColor, currentBg)
                    },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.subtitle_timing),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            item { SubtitleTimingControls(feature = feature) }
        }
    }
}

/** Formats a subtitle offset for the timing controls, e.g. "+0.5" or "−1.25". */
internal fun formatSubtitleOffset(offsetMs: Long): String {
    if (offsetMs == 0L) return "0"
    val seconds = offsetMs / 1000.0
    val sign = if (offsetMs > 0) "+" else "−"
    val body = "%.2f".format(kotlin.math.abs(seconds)).trimEnd('0').trimEnd('.')
    return sign + body
}

@Composable
private fun SubtitleTimingControls(
    feature: SubtitleTimingFeature,
    modifier: Modifier = Modifier,
) {
    val offsetMs by feature.offsetMs.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { feature.adjustOffset(-500L) }) {
            Text(stringResource(R.string.subtitle_timing_seconds, formatSubtitleOffset(-500)))
        }
        TextButton(onClick = { feature.adjustOffset(-250L) }) {
            Text(stringResource(R.string.subtitle_timing_seconds, formatSubtitleOffset(-250)))
        }
        Text(
            text = stringResource(R.string.subtitle_timing_seconds, formatSubtitleOffset(offsetMs)),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        TextButton(onClick = { feature.adjustOffset(250L) }) {
            Text(stringResource(R.string.subtitle_timing_seconds, formatSubtitleOffset(250)))
        }
        TextButton(onClick = { feature.adjustOffset(500L) }) {
            Text(stringResource(R.string.subtitle_timing_seconds, formatSubtitleOffset(500)))
        }
        TextButton(onClick = feature::resetOffset) {
            Text(stringResource(R.string.subtitle_timing_reset))
        }
    }
}
