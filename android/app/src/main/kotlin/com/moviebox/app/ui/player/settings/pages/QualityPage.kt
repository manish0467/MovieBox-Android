package com.moviebox.app.ui.player.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moviebox.app.R
import com.moviebox.app.ui.player.PlayerUiState
import com.moviebox.app.ui.player.settings.SettingsOptionRow
import com.moviebox.app.ui.player.settings.SettingsPageScaffold

/**
 * Quality page: the resolution list, moved here from the old dialog.
 */
@Composable
fun QualityPage(
    uiState: PlayerUiState,
    onSelectResolution: (label: String, height: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val resolutions = listOf(
        "Auto" to null,
        "1080p" to 1080,
        "720p" to 720,
        "480p" to 480,
        "360p" to 360,
    )

    SettingsPageScaffold(
        title = stringResource(R.string.quality_resolution),
        onClose = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(resolutions) { (label, height) ->
                SettingsOptionRow(
                    title = if (label == "Auto") stringResource(R.string.auto) else label,
                    selected = uiState.selectedResolution == label,
                    onClick = {
                        onSelectResolution(label, height)
                        onDismiss()
                    },
                )
            }
        }
    }
}
