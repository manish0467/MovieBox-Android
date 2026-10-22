package com.moviebox.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviebox.app.R
import com.moviebox.app.player.features.PlayerFeatures
import com.moviebox.app.ui.player.lock.LockButton

/**
 * Top bar of the player controls: back, the title, and the entry points for the
 * settings sheet. Lock mode adds a button of its own when it is available.
 */
@Composable
fun PlayerTopBar(
    uiState: PlayerUiState,
    features: PlayerFeatures,
    onBack: () -> Unit,
    onTogglePip: () -> Unit,
    onOpenDialog: (PlayerDialogType) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = uiState.controlsVisible && !uiState.isInPipMode,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (uiState.season > 0 || uiState.episode > 0) {
                    Text(
                        text = stringResource(R.string.season_episode, uiState.season, uiState.episode),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                    )
                }
            }

            // Chapters button (shown only when chapters are present)
            if (uiState.chapters.isNotEmpty()) {
                IconButton(onClick = { onOpenDialog(PlayerDialogType.CHAPTERS) }) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = stringResource(R.string.chapters),
                        tint = Color.White,
                    )
                }
            }

            // Playback speed button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                modifier = Modifier.clickable { onOpenDialog(PlayerDialogType.SPEED) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = stringResource(R.string.speed),
                        tint = if (uiState.playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = "${uiState.playbackSpeed}x",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }

            // Resolution picker
            IconButton(onClick = { onOpenDialog(PlayerDialogType.QUALITY) }) {
                Icon(
                    Icons.Filled.HighQuality,
                    contentDescription = stringResource(
                        R.string.player_resolution,
                        if (uiState.selectedResolution == "Auto") stringResource(R.string.auto) else uiState.selectedResolution,
                    ),
                    tint = if (uiState.selectedResolution != "Auto") MaterialTheme.colorScheme.primary else Color.White,
                )
            }

            // Subtitles button
            IconButton(onClick = { onOpenDialog(PlayerDialogType.SUBTITLES) }) {
                Icon(
                    Icons.Filled.Subtitles,
                    contentDescription = stringResource(R.string.player_subtitles, uiState.selectedSubtitle ?: stringResource(R.string.none)),
                    tint = if (uiState.selectedSubtitle != "None" && uiState.selectedSubtitle != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White
                    },
                )
            }

            // Lock mode adds its button here; hidden until the feature is enabled.
            LockButton(feature = features.lock)

            // Picture-in-Picture button
            IconButton(onClick = onTogglePip) {
                Icon(
                    Icons.Filled.PictureInPictureAlt,
                    contentDescription = stringResource(R.string.picture_in_picture),
                    tint = Color.White,
                )
            }
        }
    }
}
