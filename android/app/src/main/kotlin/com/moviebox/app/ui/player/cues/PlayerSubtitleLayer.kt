package com.moviebox.app.ui.player.cues

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moviebox.app.player.features.SubtitleTimingFeature

/**
 * Subtitle renderer used when the customizer takes over from Media3's
 * SubtitleView. Empty until the subtitle feature lands; Media3 keeps drawing
 * the cues in the meantime.
 */
@Composable
fun PlayerSubtitleLayer(
    feature: SubtitleTimingFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.rendersCues) return
}
