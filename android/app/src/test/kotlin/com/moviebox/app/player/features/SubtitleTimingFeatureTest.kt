package com.moviebox.app.player.features

import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTimingFeatureTest {
    private fun group(text: String): CueGroup =
        CueGroup(listOf(Cue.Builder().setText(text).build()), 0L)

    private fun event(atMs: Long, text: String) = CueEvent(atMs, group(text))

    @Test
    fun selectionAppliesTheOffsetAgainstTheClock() {
        val events = listOf(event(1_000, "one"), event(5_000, "five"))

        // No offset: the newest passed cue shows.
        assertEquals("five", selectCueEvent(events, 6_000, 0)?.cues?.first()?.text)

        // A positive delay holds cues back by the offset.
        assertEquals("one", selectCueEvent(events, 6_000, 2_000)?.cues?.first()?.text)

        // A negative offset shows coming cues early.
        assertEquals("five", selectCueEvent(events, 3_000, -2_000)?.cues?.first()?.text)

        // Before anything has passed, the screen is clear.
        assertNull(selectCueEvent(events, 500, 0))
    }

    @Test
    fun offsetIsClampedAndResettable() {
        var position = 0L
        val feature = SubtitleTimingFeature(positionMs = { position })

        feature.setOffset(60_000)
        assertEquals(10_000, feature.offsetMs.value)

        feature.setOffset(-60_000)
        assertEquals(-10_000, feature.offsetMs.value)

        feature.resetOffset()
        assertEquals(0, feature.offsetMs.value)
    }

    @Test
    fun cuesReplayImmediatelyWhenTheOffsetChanges() {
        var position = 0L
        val feature = SubtitleTimingFeature(positionMs = { position })

        position = 1_000
        val early = group("one")
        feature.onCues(early)
        position = 5_000
        val late = group("five")
        feature.onCues(late)

        position = 6_000
        feature.refresh()
        assertSame(late, feature.cues.value)

        feature.setOffset(2_000) // +2 s delay steps back to the earlier cue
        assertSame(early, feature.cues.value)

        feature.resetOffset()
        assertSame(late, feature.cues.value)
    }

    @Test
    fun anEmptyCueGroupClearsTheScreen() {
        var position = 0L
        val feature = SubtitleTimingFeature(positionMs = { position })

        position = 1_000
        feature.onCues(group("one"))
        position = 2_000
        feature.onCues(CueGroup(emptyList(), 0L))

        assertTrue(feature.available)
        assertTrue(feature.rendersCues)
        assertNull(feature.cues.value)
    }
}
