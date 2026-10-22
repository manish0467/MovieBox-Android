package com.moviebox.app.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenAwakePolicyTest {
    @Test
    fun keepsScreenAwakeWhilePlayingOrBuffering() {
        assertTrue(shouldKeepPlayerScreenAwake(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
        assertTrue(shouldKeepPlayerScreenAwake(playWhenReady = true, playbackState = Player.STATE_READY))
    }

    @Test
    fun releasesScreenWhenPausedIdleOrEnded() {
        assertFalse(shouldKeepPlayerScreenAwake(playWhenReady = false, playbackState = Player.STATE_READY))
        assertFalse(shouldKeepPlayerScreenAwake(playWhenReady = true, playbackState = Player.STATE_IDLE))
        assertFalse(shouldKeepPlayerScreenAwake(playWhenReady = true, playbackState = Player.STATE_ENDED))
    }
}
