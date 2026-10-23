package com.moviebox.app.player.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

internal val sleepTimerMinuteOptions = listOf(5, 10, 15, 30, 45, 60)
private const val MILLIS_PER_MINUTE = 60_000L
private const val TIMER_UPDATE_INTERVAL_MS = 1_000L
private const val FINISHED_MESSAGE_DURATION_MS = 3_000L

internal fun sleepTimerDeadline(nowMs: Long, minutes: Int): Long =
    nowMs + minutes * MILLIS_PER_MINUTE

internal fun sleepTimerRemaining(endsAtMs: Long, nowMs: Long): Long =
    (endsAtMs - nowMs).coerceAtLeast(0L)

data class SleepTimerUiState(
    /** Wall-clock deadline for a timed timer, or null for Off/end-of-episode. */
    val endsAtMs: Long? = null,
    val remainingMs: Long? = null,
    val selectedMinutes: Int? = null,
    val endOfEpisode: Boolean = false,
    val finishedMessageVisible: Boolean = false,
)

/** Wall-clock sleep timer. The owning Activity scope keeps it alive in PiP. */
class SleepTimerFeature(
    /** Scope the countdown runs in; the activity's lifecycle is the right owner. */
    private val scope: CoroutineScope,
    /** Pauses playback when the timer fires. */
    private val onPause: () -> Unit,
    /** Injected wall clock so deadline behavior can be tested without sleeping. */
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    val available: Boolean = true

    private val mutableUiState = MutableStateFlow(SleepTimerUiState())
    val uiState: StateFlow<SleepTimerUiState> = mutableUiState.asStateFlow()

    private var timerJob: Job? = null
    private var messageJob: Job? = null

    /** Starts a wall-clock timer for one of the offered minute values. */
    fun start(minutes: Int) {
        if (minutes !in sleepTimerMinuteOptions) return
        timerJob?.cancel()
        messageJob?.cancel()

        val endsAtMs = sleepTimerDeadline(nowMs(), minutes)
        mutableUiState.value = SleepTimerUiState(
            endsAtMs = endsAtMs,
            remainingMs = sleepTimerRemaining(endsAtMs, nowMs()),
            selectedMinutes = minutes,
        )
        timerJob = scope.launch {
            while (true) {
                val remainingMs = sleepTimerRemaining(endsAtMs, nowMs())
                if (remainingMs == 0L) {
                    finishTimer()
                    break
                }
                mutableUiState.value = mutableUiState.value.copy(remainingMs = remainingMs)
                delay(min(TIMER_UPDATE_INTERVAL_MS, remainingMs))
            }
        }
    }

    /** Schedules a pause when the current media item reaches its end. */
    fun startEndOfEpisode() {
        timerJob?.cancel()
        timerJob = null
        messageJob?.cancel()
        messageJob = null
        mutableUiState.value = SleepTimerUiState(endOfEpisode = true)
    }

    /** Turns the timer off without pausing playback. */
    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        messageJob?.cancel()
        messageJob = null
        mutableUiState.value = SleepTimerUiState()
    }

    /** Called from Player.Listener.onPlaybackStateChanged. */
    fun onPlaybackEnded() {
        if (mutableUiState.value.endOfEpisode) finishTimer()
    }

    private fun finishTimer() {
        timerJob = null
        messageJob?.cancel()
        mutableUiState.value = SleepTimerUiState(finishedMessageVisible = true)
        onPause()
        messageJob = scope.launch {
            delay(FINISHED_MESSAGE_DURATION_MS)
            if (mutableUiState.value.finishedMessageVisible) {
                mutableUiState.value = SleepTimerUiState()
            }
        }
    }
}
