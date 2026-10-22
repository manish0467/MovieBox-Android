package com.moviebox.app.ui.player

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.moviebox.app.R
import com.moviebox.app.ui.player.settings.SettingsOptionRow

/**
 * The remaining player dialog. Chapters are a list rather than a setting, so
 * they stay out of the settings sheet.
 */
@Composable
fun PlayerDialogs(
    uiState: PlayerUiState,
    onDismissDialog: () -> Unit,
    onSeekToChapter: (PlayerChapter) -> Unit,
) {
    if (uiState.isInPipMode) return

    when (uiState.activeDialog) {
        PlayerDialogType.CHAPTERS -> {
            if (uiState.chapters.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = onDismissDialog,
                    title = { Text(stringResource(R.string.chapters)) },
                    text = {
                        LazyColumn {
                            items(uiState.chapters) { chapter ->
                                val startSec = chapter.startTimeMs / 1000
                                val min = startSec / 60
                                val sec = startSec % 60
                                val timeLabel = String.format("%02d:%02d", min, sec)

                                SettingsOptionRow(
                                    title = "${chapter.title} ($timeLabel)",
                                    selected = uiState.currentPositionMs in chapter.startTimeMs..chapter.endTimeMs,
                                    onClick = {
                                        onSeekToChapter(chapter)
                                        onDismissDialog()
                                    },
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissDialog) {
                            Text(stringResource(R.string.close))
                        }
                    },
                )
            } else {
                onDismissDialog()
            }
        }

        else -> Unit
    }
}
