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

    @Test
    fun mappingFiltersUnsupportedFormatsAndPreservesOrder() {
        val formats = listOf(
            AudioTrackFormat("english", 0, "English", "eng", 2, supported = true),
            AudioTrackFormat("unsupported", 0, "Commentary", "und", 2, supported = false),
            AudioTrackFormat("hindi", 0, "Hindi", "hin", 6, supported = true),
        )

        val options = mapAudioTrackOptions(formats)

        assertEquals(listOf("english", "hindi"), options.map { it.groupId })
        assertEquals(listOf(1, 2), options.map { it.trackNumber })
    }

    @Test
    fun displayLabelUsesFormatLabelThenLanguageThenTrackNumber() {
        val byLabel = presentAudioTrack(hindi, fallbackTrackLabel = "Track 1")
        val byLanguage = presentAudioTrack(hindi.copy(label = null), fallbackTrackLabel = "Track 1")
        val byNumber = presentAudioTrack(
            hindi.copy(label = null, language = null, trackNumber = 3),
            fallbackTrackLabel = "Track 3",
        )

        assertEquals("Hindi", byLabel.label)
        assertEquals("hin", byLanguage.label)
        assertEquals("Track 3", byNumber.label)
    }

    @Test
    fun channelCountIsAppendedOnlyAboveOne() {
        assertNull(presentAudioTrack(hindi.copy(channelCount = 0), "Track 1").channelCountSuffix)
        assertNull(presentAudioTrack(hindi.copy(channelCount = 1), "Track 1").channelCountSuffix)
        assertEquals(2, presentAudioTrack(hindi.copy(channelCount = 2), "Track 1").channelCountSuffix)
        assertEquals(6, presentAudioTrack(hindi, "Track 1").channelCountSuffix)
    }

    @Test
    fun sectionIsHiddenForZeroOrOneUsableTracks() {
        assertEquals(false, shouldShowAudioTrackSection(0))
        assertEquals(false, shouldShowAudioTrackSection(1))
        assertEquals(true, shouldShowAudioTrackSection(2))
    }
}
