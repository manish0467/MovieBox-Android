package com.moviebox.app.ui.player.cues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.player.PlayerSessionPreferences
import com.moviebox.app.player.features.SubtitleTimingFeature
import kotlinx.coroutines.android.awaitFrame

/**
 * Subtitle renderer used when the customizer takes over from Media3's
 * SubtitleView: it draws the feature's selected cue with the session styling
 * and reselects against the playback clock every frame, so the timing offset,
 * seeks and the speed boost apply live.
 */
@Composable
fun PlayerSubtitleLayer(
    feature: SubtitleTimingFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.rendersCues) return
    val cueGroup by feature.cues.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            feature.refresh()
            awaitFrame()
        }
    }

    val text = cueGroup
        ?.cues
        ?.mapNotNull { cue -> cue.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        ?.joinToString("\n")
        ?.takeIf(String::isNotEmpty)
        ?: return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = Color(PlayerSessionPreferences.subtitleTextColor),
            fontSize = PlayerSessionPreferences.subtitleTextSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(PlayerSessionPreferences.subtitleBackgroundColor))
                .padding(horizontal = 24.dp, vertical = 2.dp)
                .padding(bottom = 36.dp),
        )
    }
}
