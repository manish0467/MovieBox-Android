package com.moviebox.app.ui.player

import kotlin.math.min

/** Maps accumulated horizontal travel to a bounded seek target using Flow's 90-second span. */
internal class PlayerSeekDragMapper(
    startPositionMs: Long,
    durationMs: Long,
    private val surfaceWidthPx: Float,
    seekSpanMs: Long = DEFAULT_SEEK_DRAG_SPAN_MS,
) {
    private val durationMs = durationMs.coerceAtLeast(0L)
    val startPositionMs = startPositionMs.coerceIn(0L, this.durationMs)
    private val spanMs = min(seekSpanMs.coerceAtLeast(0L), this.durationMs)
    private var travelPx = 0f

    var targetPositionMs: Long = this.startPositionMs
        private set

    fun addDragDelta(deltaXPx: Float): Long {
        if (surfaceWidthPx <= 0f || spanMs <= 0L || !deltaXPx.isFinite()) return targetPositionMs

        travelPx += deltaXPx
        val rawTarget = startPositionMs + (travelPx / surfaceWidthPx * spanMs).toLong()
        targetPositionMs = rawTarget.coerceIn(0L, durationMs)
        if (targetPositionMs != rawTarget) {
            // Rebase travel at an edge so the user can immediately drag back into the valid range.
            travelPx = (targetPositionMs - startPositionMs).toFloat() / spanMs * surfaceWidthPx
        }
        return targetPositionMs
    }
}

private const val DEFAULT_SEEK_DRAG_SPAN_MS = 90_000L
