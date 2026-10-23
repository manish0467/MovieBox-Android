package com.moviebox.app.player.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioTracksFeatureTest {
    private val hindi = AudioTrackOption(
        groupId = "episode-2-hin",
        trackIndex = 0,
        trackNumber = 1,
        label = "Hindi",
        language = "hin",
        channelCount = 6,
    )

    @Test
    fun preferredLanguageMatchesAcrossEpisodeGroupIdsAndOrder() {
        val nextEpisode = listOf(
            AudioTrackOption("episode-3-eng", 0, 1, "English", "eng", 2),
            hindi.copy(groupId = "episode-3-hin", trackNumber = 2),
        )

        assertEquals("episode-3-hin", preferredAudioTrack(nextEpisode, "HIN")?.groupId)
    }

    @Test
    fun absentLanguageHasNoTrackMatch() {
        assertNull(preferredAudioTrack(listOf(hindi), "jpn"))
        assertNull(preferredAudioTrack(listOf(hindi), null))
    }
}
