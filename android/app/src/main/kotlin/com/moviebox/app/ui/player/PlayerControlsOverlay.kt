package com.moviebox.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.core.SubtitleOption

/**
 * Pure composable player controls overlay over the PlayerView.
 */
@Composable
fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onTogglePip: () -> Unit,
    onOpenDialog: (PlayerDialogType) -> Unit,
    onDismissDialog: () -> Unit,
    onSelectResolution: (label: String, height: Int?) -> Unit,
    onSelectSubtitle: (SubtitleOption?) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSeekToChapter: (PlayerChapter) -> Unit,
    onDragStart: (GestureType, startX: Float, startY: Float) -> Unit,
    onDragStep: (GestureType, deltaX: Float, deltaY: Float, totalX: Float, totalY: Float) -> Unit,
    onDragEnd: (GestureType) -> Unit,
    onTapSurface: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolutions = listOf(
        "Auto" to null,
        "1080p" to 1080,
        "720p" to 720,
        "480p" to 480,
        "360p" to 360,
    )

    val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Box(modifier = modifier.fillMaxSize()) {
        // Gesture detection layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerSurfaceGestures(
                    enabled = !uiState.isInPipMode,
                    onDragStart = onDragStart,
                    onDragStep = onDragStep,
                    onDragEnd = onDragEnd,
                    onTap = onTapSurface,
                ),
        )

        // Gesture HUD (brightness / volume / scrub indicator)
        if (!uiState.isInPipMode) {
            PlayerGestureHud(
                state = uiState.gestureHudState,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Top bar controls (hidden in PiP mode or when controls are toggled off)
        AnimatedVisibility(
            visible = uiState.controlsVisible && !uiState.isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
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
                        contentDescription = "Back",
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
                            text = "Season ${uiState.season} · Episode ${uiState.episode}",
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
                            contentDescription = "Chapters",
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
                            contentDescription = "Speed",
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
                        contentDescription = "Resolution (${uiState.selectedResolution})",
                        tint = if (uiState.selectedResolution != "Auto") MaterialTheme.colorScheme.primary else Color.White,
                    )
                }

                // Subtitles button
                IconButton(onClick = { onOpenDialog(PlayerDialogType.SUBTITLES) }) {
                    Icon(
                        Icons.Filled.Subtitles,
                        contentDescription = "Subtitles (${uiState.selectedSubtitle})",
                        tint = if (uiState.selectedSubtitle != "None" && uiState.selectedSubtitle != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    )
                }

                // Picture-in-Picture button
                IconButton(onClick = onTogglePip) {
                    Icon(
                        Icons.Filled.PictureInPictureAlt,
                        contentDescription = "Picture in picture",
                        tint = Color.White,
                    )
                }
            }
        }

        // Dialogs
        if (!uiState.isInPipMode) {
            when (uiState.activeDialog) {
                PlayerDialogType.SUBTITLES -> {
                    AlertDialog(
                        onDismissRequest = onDismissDialog,
                        title = { Text("Subtitles") },
                        text = {
                            LazyColumn {
                                item {
                                    DialogOptionRow(
                                        title = "None (Off)",
                                        selected = uiState.selectedSubtitle == null || uiState.selectedSubtitle == "None",
                                        onClick = {
                                            onSelectSubtitle(null)
                                            onDismissDialog()
                                        },
                                    )
                                }
                                items(uiState.availableSubtitles) { sub ->
                                    DialogOptionRow(
                                        title = sub.name,
                                        selected = uiState.selectedSubtitle == sub.name,
                                        onClick = {
                                            onSelectSubtitle(sub)
                                            onDismissDialog()
                                        },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = onDismissDialog) {
                                Text("Close")
                            }
                        },
                    )
                }

                PlayerDialogType.QUALITY -> {
                    AlertDialog(
                        onDismissRequest = onDismissDialog,
                        title = { Text("Quality / Resolution") },
                        text = {
                            LazyColumn {
                                items(resolutions) { (label, height) ->
                                    DialogOptionRow(
                                        title = label,
                                        selected = uiState.selectedResolution == label,
                                        onClick = {
                                            onSelectResolution(label, height)
                                            onDismissDialog()
                                        },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = onDismissDialog) {
                                Text("Close")
                            }
                        },
                    )
                }

                PlayerDialogType.SPEED -> {
                    AlertDialog(
                        onDismissRequest = onDismissDialog,
                        title = { Text("Playback Speed") },
                        text = {
                            LazyColumn {
                                items(playbackSpeeds) { speed ->
                                    DialogOptionRow(
                                        title = "${speed}x" + if (speed == 1.0f) " (Normal)" else "",
                                        selected = uiState.playbackSpeed == speed,
                                        onClick = {
                                            onSelectSpeed(speed)
                                            onDismissDialog()
                                        },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = onDismissDialog) {
                                Text("Close")
                            }
                        },
                    )
                }

                PlayerDialogType.CHAPTERS -> {
                    AlertDialog(
                        onDismissRequest = onDismissDialog,
                        title = { Text("Chapters") },
                        text = {
                            LazyColumn {
                                items(uiState.chapters) { chapter ->
                                    val startSec = chapter.startTimeMs / 1000
                                    val min = startSec / 60
                                    val sec = startSec % 60
                                    val timeLabel = String.format("%02d:%02d", min, sec)

                                    DialogOptionRow(
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
                                Text("Close")
                            }
                        },
                    )
                }

                PlayerDialogType.NONE, PlayerDialogType.SUBTITLE_STYLE -> Unit
            }
        }
    }
}

@Composable
fun DialogOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
