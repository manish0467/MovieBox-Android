package com.moviebox.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

enum class GestureType {
    BRIGHTNESS,
    VOLUME,
    SEEK,
}

enum class GestureIconType {
    BRIGHTNESS_LOW,
    BRIGHTNESS_MED,
    BRIGHTNESS_HIGH,
    VOLUME_MUTE,
    VOLUME_LOW,
    VOLUME_MED,
    VOLUME_HIGH,
    SEEK_FORWARD,
    SEEK_REWIND,
}

data class GestureHudState(
    val type: GestureType,
    val valuePercent: Int, // 0..100
    val text: String,
    val iconType: GestureIconType,
    val deltaText: String? = null,
)

/**
 * Detects swipe gestures on the video player surface:
 * - Left vertical drag: screen brightness
 * - Right vertical drag: media volume
 * - Horizontal drag: video scrub / seek
 * - Tap: toggles player controls
 */
fun Modifier.playerSurfaceGestures(
    enabled: Boolean = true,
    onDragStart: (GestureType, startX: Float, startY: Float) -> Unit,
    onDragStep: (GestureType, deltaX: Float, deltaY: Float, totalX: Float, totalY: Float) -> Unit,
    onDragEnd: (GestureType) -> Unit,
    onTap: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            val startY = down.position.y
            val screenWidth = size.width
            val isLeftSide = startX < screenWidth / 2f

            var isDragging = false
            var activeGesture: GestureType? = null
            var prevX = startX
            var prevY = startY
            val touchSlop = viewConfiguration.touchSlop

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (!change.pressed) {
                    if (change.isConsumed) {
                        // A long press (speed boost) owned this gesture.
                        break
                    }
                    if (isDragging && activeGesture != null) {
                        change.consume()
                        onDragEnd(activeGesture)
                    } else if (!isDragging) {
                        onTap()
                    }
                    break
                }

                val currentX = change.position.x
                val currentY = change.position.y
                val totalDx = currentX - startX
                val totalDy = currentY - startY
                val stepDx = currentX - prevX
                val stepDy = currentY - prevY

                if (!isDragging) {
                    if (!change.isConsumed && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                        isDragging = true
                        activeGesture = if (abs(totalDx) > abs(totalDy)) {
                            GestureType.SEEK
                        } else if (isLeftSide) {
                            GestureType.BRIGHTNESS
                        } else {
                            GestureType.VOLUME
                        }
                        onDragStart(activeGesture, startX, startY)
                    }
                }

                if (isDragging && activeGesture != null) {
                    change.consume()
                    onDragStep(activeGesture, stepDx, stepDy, totalDx, totalDy)
                    prevX = currentX
                    prevY = currentY
                }
            }
        }
    }
}

/**
 * Holds the video to play it faster.
 *
 * Installed only when the speed boost is available, so it is inert otherwise.
 * The whole hold is consumed, which keeps the tap handler above quiet; a hold
 * that never reaches the timeout, or that turns into a drag, is left alone.
 */
fun Modifier.playerHoldToBoost(
    enabled: Boolean = true,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            onHoldStart()
            consumeUntilUp()
            onHoldEnd()
        }
    }
}

/** Swallows the rest of the gesture, including the release. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) return
    }
}

/**
 * Floating HUD displaying gesture feedback (Brightness, Volume, or Seek scrub).
 */
@Composable
fun PlayerGestureHud(
    state: GestureHudState?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        if (state == null) return@AnimatedVisibility

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.8f),
            shadowElevation = 8.dp,
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = iconFor(state.iconType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (!state.deltaText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.deltaText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.deltaText.startsWith("+")) {
                                    Color(0xFFA6E3A1) // light green
                                } else {
                                    Color(0xFFF38BA8) // light red
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (state.type == GestureType.BRIGHTNESS || state.type == GestureType.VOLUME) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (state.valuePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(120.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )
                    }
                }
            }
        }
    }
}

private fun iconFor(iconType: GestureIconType): ImageVector = when (iconType) {
    GestureIconType.BRIGHTNESS_LOW -> Icons.Filled.BrightnessLow
    GestureIconType.BRIGHTNESS_MED -> Icons.Filled.BrightnessMedium
    GestureIconType.BRIGHTNESS_HIGH -> Icons.Filled.BrightnessHigh
    GestureIconType.VOLUME_MUTE -> Icons.AutoMirrored.Filled.VolumeOff
    GestureIconType.VOLUME_LOW -> Icons.AutoMirrored.Filled.VolumeMute
    GestureIconType.VOLUME_MED -> Icons.AutoMirrored.Filled.VolumeDown
    GestureIconType.VOLUME_HIGH -> Icons.AutoMirrored.Filled.VolumeUp
    GestureIconType.SEEK_FORWARD -> Icons.Filled.FastForward
    GestureIconType.SEEK_REWIND -> Icons.Filled.FastRewind
}
