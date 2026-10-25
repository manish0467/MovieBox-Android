package com.moviebox.app.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/** The thirds of the player surface a double-tap can land in. */
enum class TapZone { BACK, CENTER, FORWARD }

/** Side strips this dense are reserved for the system back gesture; a touch
 *  that starts there never opens the double-tap window. */
private val SystemGestureEdge = 20.dp

/** How long a second double-tap keeps accumulating seek seconds. */
const val DOUBLE_TAP_SEEK_WINDOW_MS = 2_000L

/** Maps a tap x to its zone by thirds. */
fun tapZoneOf(x: Float, width: Float): TapZone = when {
    width <= 0f -> TapZone.CENTER
    x < width / 3f -> TapZone.BACK
    x > width * 2f / 3f -> TapZone.FORWARD
    else -> TapZone.CENTER
}

/** Whether a new double-tap continues the previous zone's seek accumulation. */
fun isContinuingDoubleTap(
    lastForward: Boolean?,
    lastTapAtMs: Long,
    forward: Boolean,
    nowMs: Long,
    windowMs: Long = DOUBLE_TAP_SEEK_WINDOW_MS,
): Boolean = lastForward == forward && nowMs - lastTapAtMs < windowMs

/**
 * Tap detection for the player surface, following Flow's player.
 *
 * The first tap acts the instant the finger lifts — hidden controls reveal
 * without waiting — while hiding waits for the double-tap window to close, so
 * a double-tap never flashes the controls: [onDoubleTap] re-hides whatever
 * the first tap revealed before acting on the zone. The drag gestures and the
 * hold-to-boost modifier keep their own detectors; recognized taps are
 * consumed so the drag detector never sees them as its own.
 */
fun Modifier.playerTapZones(
    enabled: Boolean,
    controlsVisible: () -> Boolean,
    onShowControls: () -> Unit,
    onHideControls: () -> Unit,
    onDoubleTap: (TapZone) -> Unit,
): Modifier = composed {
    val visibleState = rememberUpdatedState(controlsVisible)
    val showState = rememberUpdatedState(onShowControls)
    val hideState = rememberUpdatedState(onHideControls)
    val doubleTapState = rememberUpdatedState(onDoubleTap)

    if (!enabled) return@composed Modifier

    pointerInput(Unit) {
        var revealedByTap = false
        var hidePending = false

        detectPlayerTaps(
            isEdge = { offset ->
                val edge = SystemGestureEdge.toPx()
                offset.x < edge || offset.x > size.width - edge
            },
            onTapUp = {
                if (!visibleState.value()) {
                    showState.value()
                    revealedByTap = true
                    hidePending = false
                } else {
                    hidePending = true
                    revealedByTap = false
                }
            },
            onSingleTapConfirmed = {
                if (hidePending) hideState.value()
                hidePending = false
                revealedByTap = false
            },
            onDoubleTap = { offset ->
                hidePending = false
                val zone = tapZoneOf(offset.x, size.width.toFloat())
                // A seek double-tap re-hides controls the first tap revealed,
                // so the seek overlay owns the screen.
                if (zone != TapZone.CENTER && revealedByTap) {
                    hideState.value()
                }
                revealedByTap = false
                doubleTapState.value(zone)
            },
        )
    }
}

/**
 * Tap detection that reports the up event before the double-tap window has
 * resolved: the tap acts immediately, the single-tap confirmation only fires
 * once the window closes with no second touch, and the double tap fires on
 * the second up. Ported from Flow's player.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPlayerTaps(
    isEdge: (Offset) -> Boolean,
    onTapUp: (Offset) -> Unit,
    onSingleTapConfirmed: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startsAtEdge = isEdge(down.position)

        val firstUp = awaitStillUp(down) ?: return@awaitEachGesture
        firstUp.consume()
        onTapUp(firstUp.position)
        if (startsAtEdge) {
            onSingleTapConfirmed()
            return@awaitEachGesture
        }

        val secondUp = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            val secondDown = awaitFirstDown(requireUnconsumed = false)
            if (isEdge(secondDown.position)) null else awaitStillUp(secondDown)
        }
        if (secondUp == null) {
            onSingleTapConfirmed()
        } else {
            secondUp.consume()
            onDoubleTap(secondUp.position)
        }
    }
}

/** The up of [down], or null once it moves past touch slop, leaves the bounds
 *  or is consumed (by the drag detector or the speed-boost hold). */
private suspend fun AwaitPointerEventScope.awaitStillUp(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
        if (change.changedToUp()) return change
        if (change.isConsumed || change.isOutOfBounds(size, extendedTouchPadding)) return null
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return null
    }
}
