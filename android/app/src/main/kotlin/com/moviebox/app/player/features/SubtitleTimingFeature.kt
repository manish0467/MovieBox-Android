package com.moviebox.app.player.features

import androidx.media3.common.text.CueGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One cue update, stamped with the player position it arrived at. */
internal data class CueEvent(
    val atMs: Long,
    val cueGroup: CueGroup,
)

internal const val MAX_SUBTITLE_OFFSET_MS = 10_000L
private const val CUE_HISTORY_WINDOW_MS = 30_000L

/**
 * The newest cue whose native time has already passed under the offset: a
 * positive delay holds cues back until the player reaches [offsetMs] past
 * their native time, a negative one shows them early. Null clears the screen.
 */
internal fun selectCueEvent(
    events: List<CueEvent>,
    positionMs: Long,
    offsetMs: Long,
): CueGroup? =
    events.lastOrNull { it.atMs <= positionMs - offsetMs }
        ?.cueGroup
        ?.takeIf { it.cues.isNotEmpty() }

/**
 * The subtitle customizer and its timing offset.
 *
 * While [rendersCues] is true the player hides Media3's SubtitleView and this
 * feature owns rendering: [onCues] buffers every cue update with the position
 * it arrived at, and [refresh] reselects against the playback clock
 * ([positionMs]) every frame, so the offset — and seeks, pauses and the speed
 * boost, which move the clock themselves — applies live.
 *
 * The player listener and the Compose frame loop both call in on the main
 * thread; the event buffer relies on that.
 */
class SubtitleTimingFeature(
    /**
     * The playback clock the timing offset is measured against. Read it inside
     * a frame loop (`withFrameMillis`) while rendering: it is the player's
     * position, so it follows seeks, pauses and the speed boost by itself.
     */
    val positionMs: () -> Long,
) {
    /** Whether the customizer exists in this build. Its timing controls hide while false. */
    val available: Boolean = true

    /**
     * When true the player hides Media3's SubtitleView and draws the cues in
     * Compose instead, which is what makes a live timing offset possible.
     */
    val rendersCues: Boolean = true

    private val mutableCues = MutableStateFlow<CueGroup?>(null)

    /** The cue group that should be on screen right now, offset applied. */
    val cues: StateFlow<CueGroup?> = mutableCues.asStateFlow()

    private val mutableOffsetMs = MutableStateFlow(0L)

    /** The live subtitle delay; positive shows subtitles later. */
    val offsetMs: StateFlow<Long> = mutableOffsetMs.asStateFlow()

    private val events = ArrayDeque<CueEvent>()

    /** Called from [androidx.media3.common.Player.Listener.onCues]. */
    fun onCues(cueGroup: CueGroup) {
        val atMs = positionMs()
        events.addLast(CueEvent(atMs, cueGroup))
        pruneTo(atMs)
        refresh()
    }

    /** Reselects the visible cue against the current clock; call per frame. */
    fun refresh() {
        mutableCues.value = selectCueEvent(events.toList(), positionMs(), mutableOffsetMs.value)
    }

    /** Shifts the offset by [deltaMs], clamped to ±10 seconds. */
    fun adjustOffset(deltaMs: Long) {
        setOffset(mutableOffsetMs.value + deltaMs)
    }

    fun setOffset(offsetMs: Long) {
        mutableOffsetMs.value = offsetMs.coerceIn(-MAX_SUBTITLE_OFFSET_MS, MAX_SUBTITLE_OFFSET_MS)
        refresh()
    }

    fun resetOffset() = setOffset(0L)

    private fun pruneTo(nowMs: Long) {
        val keepFrom = nowMs - CUE_HISTORY_WINDOW_MS
        // The newest event always survives so an empty CueGroup can clear the
        // screen even when everything else is pruned.
        while (events.size > 1 && events.first().atMs < keepFrom) {
            events.removeFirst()
        }
    }
}
