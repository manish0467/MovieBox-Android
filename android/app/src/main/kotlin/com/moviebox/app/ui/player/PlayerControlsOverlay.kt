package com.moviebox.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moviebox.app.core.SubtitleOption
import com.moviebox.app.player.features.PlayerFeatures
import com.moviebox.app.ui.player.cues.PlayerSubtitleLayer
import com.moviebox.app.ui.player.lock.LockShield
import com.moviebox.app.ui.player.settings.PlayerSettingsSheet

/**
 * Pure composable player controls overlay over the PlayerView.
 *
 * This is the composition root only: the gesture surface, the top bar, the HUD,
 * the lock shield, the dialogs and the settings sheet each live in their own
 * file.
 */
@Composable
fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    features: PlayerFeatures,
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
    val locked by features.lock.isLocked.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // Subtitles sit above the video and below every control.
        PlayerSubtitleLayer(
            feature = features.subtitles,
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture detection layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerSurfaceGestures(
                    enabled = !uiState.isInPipMode && !locked,
                    onDragStart = onDragStart,
                    onDragStep = onDragStep,
                    onDragEnd = onDragEnd,
                    onTap = onTapSurface,
                )
                .playerHoldToBoost(
                    enabled = !uiState.isInPipMode && !locked && features.boost.available,
                    onHoldStart = { features.boost.onHoldStart() },
                    onHoldEnd = { features.boost.onHoldEnd() },
                ),
        )

        // Top bar controls (hidden in PiP mode or when controls are toggled off)
        PlayerTopBar(
            uiState = uiState,
            features = features,
            onBack = onBack,
            onTogglePip = onTogglePip,
            onOpenDialog = onOpenDialog,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // While locked, the shield swallows every touch meant for the controls.
        LockShield(
            feature = features.lock,
            modifier = Modifier.fillMaxSize(),
        )

        // HUD and feature chips stay visible above the shield.
        PlayerHud(
            uiState = uiState,
            features = features,
            modifier = Modifier.fillMaxSize(),
        )

        if (!uiState.isInPipMode) {
            PlayerDialogs(
                uiState = uiState,
                onDismissDialog = onDismissDialog,
                onSeekToChapter = onSeekToChapter,
            )

            when (uiState.activeDialog) {
                PlayerDialogType.SPEED,
                PlayerDialogType.SUBTITLES,
                PlayerDialogType.SUBTITLE_STYLE,
                PlayerDialogType.QUALITY,
                -> PlayerSettingsSheet(
                    uiState = uiState,
                    features = features,
                    onDismiss = onDismissDialog,
                    onSelectSpeed = onSelectSpeed,
                    onSelectResolution = onSelectResolution,
                    onSelectSubtitle = onSelectSubtitle,
                    onUpdateSubtitleStyle = onUpdateSubtitleStyle,
                )

                else -> Unit
            }
        }
    }
}
