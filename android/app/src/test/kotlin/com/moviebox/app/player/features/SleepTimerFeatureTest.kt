package com.moviebox.app.player.features

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerFeatureTest {
    @Test
    fun wallClockTimerFiresAtItsDeadlineAndClearsTimerState() = runTest {
        var pauseCalls = 0
        val feature = SleepTimerFeature(
            scope = this,
            onPause = { pauseCalls++ },
            nowMs = { testScheduler.currentTime },
        )

        feature.start(5)
        runCurrent()
        assertEquals(300_000L, feature.uiState.value.endsAtMs)

        advanceTimeBy(299_000L)
        runCurrent()
        assertEquals(1_000L, feature.uiState.value.remainingMs)
        assertEquals(0, pauseCalls)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, pauseCalls)
        assertNull(feature.uiState.value.endsAtMs)
        assertNull(feature.uiState.value.remainingMs)
        assertTrue(feature.uiState.value.finishedMessageVisible)

        advanceTimeBy(3_000L)
        runCurrent()
        assertFalse(feature.uiState.value.finishedMessageVisible)
    }

    @Test
    fun cancelClearsDeadlineAndPreventsPause() = runTest {
        var pauseCalls = 0
        val feature = SleepTimerFeature(
            scope = this,
            onPause = { pauseCalls++ },
            nowMs = { testScheduler.currentTime },
        )

        feature.start(10)
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()
        feature.cancel()

        advanceTimeBy(600_000L)
        runCurrent()
        assertEquals(0, pauseCalls)
        assertNull(feature.uiState.value.endsAtMs)
        assertFalse(feature.uiState.value.endOfEpisode)
    }

    @Test
    fun endOfEpisodePausesOnlyWhenPlaybackEnds() = runTest {
        var pauseCalls = 0
        val feature = SleepTimerFeature(
            scope = this,
            onPause = { pauseCalls++ },
            nowMs = { testScheduler.currentTime },
        )

        feature.startEndOfEpisode()
        advanceTimeBy(60 * 60 * 1_000L)
        assertEquals(0, pauseCalls)
        assertTrue(feature.uiState.value.endOfEpisode)

        feature.onPlaybackEnded()
        assertEquals(1, pauseCalls)
        assertNull(feature.uiState.value.endsAtMs)
        assertFalse(feature.uiState.value.endOfEpisode)
        assertTrue(feature.uiState.value.finishedMessageVisible)
    }

    @Test
    fun minuteChoicesMatchThePlaybackPageOptions() {
        assertEquals(listOf(5, 10, 15, 30, 45, 60), sleepTimerMinuteOptions)
    }
}
