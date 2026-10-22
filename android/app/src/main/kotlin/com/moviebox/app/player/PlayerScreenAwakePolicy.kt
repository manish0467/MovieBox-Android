package com.moviebox.app.player

import androidx.media3.common.Player

/** True while playback is wanted and the player has work in flight. */
internal fun shouldKeepPlayerScreenAwake(
    playWhenReady: Boolean,
    playbackState: Int,
): Boolean =
    playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
