package com.moviebox.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moviebox.app.core.SubtitleOption
import com.moviebox.app.player.features.PlayerFeatures
import com.moviebox.app.ui.player.cues.PlayerSubtitleLayer
import com.moviebox.app.ui.player.lock.LockShield
import com.moviebox.app.ui.player.settings.PlayerSettingsSheet
import kotlinx.coroutines.delay

/** Transient double-tap seek feedback: direction plus the stacked seconds. */
private data class SeekIndicator(
    val forward: Boolean,
    val seconds: Int,
)

private const val SEEK_STEP_MS = 10_000L
private const val SEEK_INDICATOR_MS = 800L
private const val PLAY_PAUSE_INDICATOR_MS = 700L

/**
 * Pure composable player controls overlay over the PlayerView.
 *
 * This is the composition root only: the gesture surface, the tap zones, the
 * top bar, the HUD, the lock shield, the dialogs and the settings sheet each
 * live in their own file.
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
    onDragStart: (GestureType, startX: Float, startY: Float, surfaceWidthPx: Float) -> Unit,
    onDragStep: (GestureType, deltaX: Float, deltaY: Float, totalX: Float, totalY: Float) -> Unit,
    onDragEnd: (GestureType) -> Unit,
    onShowControls: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekPreview: (Long?) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onHideControls: () -> Unit,
    onOpenExternal: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val locked by features.lock.isLocked.collectAsState()

    // Double-tap seek accumulation: consecutive double-taps in one zone keep
    // extending the same seek, like Flow's player.
    var seekIndicator by remember { mutableStateOf<SeekIndicator?>(null) }
    var pendingSeekTargetMs by remember { mutableLongStateOf(0L) }
    var pendingSeekSeconds by remember { mutableStateOf(0) }
    var lastSeekTapForward by remember { mutableStateOf<Boolean?>(null) }
    var lastSeekTapAtMs by remember { mutableLongStateOf(0L) }

    val handleDoubleTap: (TapZone) -> Unit = { zone ->
        when (zone) {
            TapZone.CENTER -> onPlayPause()
            TapZone.BACK, TapZone.FORWARD -> {
                val forward = zone == TapZone.FORWARD
                val now = System.currentTimeMillis()
                val continuing = isContinuingDoubleTap(
                    lastForward = lastSeekTapForward,
                    lastTapAtMs = lastSeekTapAtMs,
                    forward = forward,
                    nowMs = now,
                )
                val base = if (continuing) pendingSeekTargetMs else uiState.currentPositionMs
                var target = if (forward) base + SEEK_STEP_MS else base - SEEK_STEP_MS
                if (!forward) target = target.coerceAtLeast(0L)
                if (forward && uiState.durationMs > 0L) target = target.coerceAtMost(uiState.durationMs)

                pendingSeekTargetMs = target
                lastSeekTapForward = forward
                lastSeekTapAtMs = now
                onSeekCommit(target)
                seekIndicator = SeekIndicator(
                    forward = forward,
                    seconds = if (continuing) pendingSeekSeconds + 10 else 10,
                )
                pendingSeekSeconds = seekIndicator?.seconds ?: 0
            }
        }
    }

    LaunchedEffect(seekIndicator) {
        if (seekIndicator != null) {
            delay(SEEK_INDICATOR_MS)
            seekIndicator = null
            pendingSeekSeconds = 0
        }
    }

    // The center pulse reacts to every play-state change, whatever caused it.
    var centerIndicatorVisible by remember { mutableStateOf(false) }
    var centerIndicatorPlaying by remember { mutableStateOf(false) }
    var observedPlayState by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isPlaying) {
        if (observedPlayState) {
            centerIndicatorPlaying = uiState.isPlaying
            centerIndicatorVisible = true
            delay(PLAY_PAUSE_INDICATOR_MS)
            centerIndicatorVisible = false
        }
        observedPlayState = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Subtitles sit above the video and below every control.
        PlayerSubtitleLayer(
            feature = features.subtitles,
            modifier = Modifier.fillMaxSize(),
        )

        // Tap zones own taps (instant reveal, deferred hide, double-tap seek);
        // the drag detector and the boost hold keep their own layers beneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerTapZones(
                    enabled = !uiState.isInPipMode && !locked,
                    controlsVisible = { uiState.controlsVisible },
                    onShowControls = onShowControls,
                    onHideControls = onHideControls,
                    onDoubleTap = handleDoubleTap,
                )
                .playerSurfaceGestures(
                    enabled = !uiState.isInPipMode && !locked,
                    onDragStart = onDragStart,
                    onDragStep = onDragStep,
                    onDragEnd = onDragEnd,
                    onTap = {},
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
            onOpenExternal = onOpenExternal,
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

        seekIndicator?.let { indicator ->
            DoubleTapSeekIndicator(
                forward = indicator.forward,
                seconds = indicator.seconds,
                modifier = Modifier
                    .align(
                        if (indicator.forward) Alignment.CenterEnd else Alignment.CenterStart,
                    )
                    .padding(horizontal = 28.dp),
            )
        }

        CenterPlayPauseIndicator(
            playing = centerIndicatorPlaying,
            visible = centerIndicatorVisible,
            modifier = Modifier.align(Alignment.Center),
        )

        PlayerTransportControls(
            uiState = uiState,
            onPlayPause = onPlayPause,
            onSeekPreview = onSeekPreview,
            onSeekCommit = onSeekCommit,
            onSkipBack = onSkipBack,
            onSkipForward = onSkipForward,
            onHideControls = onHideControls,
            modifier = Modifier.align(Alignment.BottomCenter),
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
                    onOpenExternal = onOpenExternal,
                )

                else -> Unit
            }
        }
    }
}
