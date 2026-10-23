package com.moviebox.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * Press feedback for a surface that opens something: the card dips a little and
 * fades, then springs back. The tap also fires the platform's virtual-key
 * feedback, the same weight as the system's own touch feedback, so the device's
 * haptics setting still governs whether it is felt.
 *
 * A spring rather than a tween on the scale is what makes the release feel
 * physical instead of merely animated; the short alpha tween keeps the dip from
 * reading as a blink.
 */
@Composable
fun Modifier.pressScale(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.7f),
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "pressAlpha",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }.clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        enabled = enabled,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
    )
}
