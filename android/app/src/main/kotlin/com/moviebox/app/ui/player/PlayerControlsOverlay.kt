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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.moviebox.app.R
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
    onUpdateSubtitleStyle: (textSizeSp: Float, textColor: Int, backgroundColor: Int) -> Unit,
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

        // Gesture HUD (brightness / volume indicator) or Seek Preview Thumbnail
        if (!uiState.isInPipMode) {
            if (uiState.seekScrubPositionMs != null) {
                SeekPreviewThumbnail(
                    targetPositionMs = uiState.seekScrubPositionMs,
                    durationMs = uiState.durationMs,
                    deltaText = uiState.gestureHudState?.deltaText,
                    coverUrl = uiState.coverUrl,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                PlayerGestureHud(
                    state = uiState.gestureHudState,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
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
                        contentDescription = stringResource(R.string.player_resolution, if (uiState.selectedResolution == "Auto") stringResource(R.string.auto) else uiState.selectedResolution),
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

        // Dialogs
        if (!uiState.isInPipMode) {
            when (uiState.activeDialog) {
                PlayerDialogType.SUBTITLES -> {
                    AlertDialog(
                        onDismissRequest = onDismissDialog,
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.subtitles))
                                TextButton(onClick = { onOpenDialog(PlayerDialogType.SUBTITLE_STYLE) }) {
                                    Text(stringResource(R.string.style))
                                }
                            }
                        },
                        text = {
                            LazyColumn {
                                item {
                                    DialogOptionRow(
                                        title = stringResource(R.string.none_off),
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
                                Text(stringResource(R.string.close))
                            }
                        },
                    )
                }

                PlayerDialogType.QUALITY -> {
                    AlertDialog(
                        onDismissRequest = onDismissDialog,
                        title = { Text(stringResource(R.string.quality_resolution)) },
                        text = {
                            LazyColumn {
                                items(resolutions) { (label, height) ->
                                    DialogOptionRow(
                                        title = if (label == "Auto") stringResource(R.string.auto) else label,
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
                                Text(stringResource(R.string.close))
                            }
                        },
                    )
                }

                PlayerDialogType.SPEED -> {
                    AlertDialog(
                        onDismissRequest = onDismissDialog,
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.playback_speed))
                                if (uiState.playbackSpeed != 1.0f) {
                                    TextButton(onClick = {
                                        onSelectSpeed(1.0f)
                                        onDismissDialog()
                                    }) {
                                        Text(stringResource(R.string.reset_playback_speed))
                                    }
                                }
                            }
                        },
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
                                Text(stringResource(R.string.close))
                            }
                        },
                    )
                }

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
                                    Text(stringResource(R.string.close))
                                }
                            },
                        )
                    } else {
                        onDismissDialog()
                    }
                }

                PlayerDialogType.SUBTITLE_STYLE -> {
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

                    var currentSize by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(com.moviebox.app.player.PlayerSessionPreferences.subtitleTextSizeSp)
                    }
                    var currentColor by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(com.moviebox.app.player.PlayerSessionPreferences.subtitleTextColor)
                    }
                    var currentBg by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(com.moviebox.app.player.PlayerSessionPreferences.subtitleBackgroundColor)
                    }

                    AlertDialog(
                        onDismissRequest = onDismissDialog,
                        title = { Text(stringResource(R.string.subtitle_styling)) },
                        text = {
                            LazyColumn {
                                item {
                                    Text(
                                        text = stringResource(R.string.subtitle_text_size),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    )
                                }
                                items(fontSizes) { (label, size) ->
                                    DialogOptionRow(
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
                                    DialogOptionRow(
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
                                    DialogOptionRow(
                                        title = label,
                                        selected = currentBg == bgInt,
                                        onClick = {
                                            currentBg = bgInt
                                            onUpdateSubtitleStyle(currentSize, currentColor, currentBg)
                                        },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = onDismissDialog) {
                                Text(stringResource(R.string.done))
                            }
                        },
                    )
                }

                PlayerDialogType.NONE -> Unit
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
