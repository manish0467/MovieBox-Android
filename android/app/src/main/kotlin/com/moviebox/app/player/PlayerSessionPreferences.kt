package com.moviebox.app.player

import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat

/**
 * Session-scoped preferences for the player.
 *
 * Keeps playback speed, subtitle styling, and other user adjustments
 * persistent across titles played in the same session without requiring
 * persistent disk migrations.
 */
object PlayerSessionPreferences {
    var playbackSpeed: Float = 1.0f

    // Subtitle styling
    var subtitleTextSizeSp: Float = 18f
    var subtitleTextColor: Int = Color.WHITE
    var subtitleBackgroundColor: Int = Color.TRANSPARENT
    var subtitleEdgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE
    var subtitleEdgeColor: Int = Color.BLACK
}
