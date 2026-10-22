package com.moviebox.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moviebox.app.player.features.PlayerFeatures
import com.moviebox.app.ui.player.boost.BoostChip
import com.moviebox.app.ui.player.sleep.SleepTimerPill

/**
 * Floating player feedback: the brightness/volume HUD, the seek preview, and
 * the chips of the features that show live state.
 */
@Composable
fun PlayerHud(
    uiState: PlayerUiState,
    features: PlayerFeatures,
    modifier: Modifier = Modifier,
) {
    if (uiState.isInPipMode) return

    Box(modifier = modifier) {
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

        // Live feature chips. Both are hidden until their feature is available.
        BoostChip(
            feature = features.boost,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 84.dp, end = 12.dp),
        )
        SleepTimerPill(
            feature = features.sleep,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp),
        )
    }
}
