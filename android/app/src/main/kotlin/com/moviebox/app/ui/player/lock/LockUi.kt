package com.moviebox.app.ui.player.lock

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moviebox.app.R
import com.moviebox.app.player.features.LockModeFeature

/**
 * Lock button for the player top bar.
 */
@Composable
fun LockButton(
    feature: LockModeFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
    IconButton(onClick = feature::toggle, modifier = modifier) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = stringResource(R.string.player_lock),
            tint = Color.White,
        )
    }
}

/**
 * Touch shield drawn above the controls while the player is locked. It
 * swallows every touch meant for the controls below it and offers a single
 * unlock button of its own.
 */
@Composable
fun LockShield(
    feature: LockModeFeature,
    modifier: Modifier = Modifier,
) {
    if (!feature.available) return
    val locked by feature.isLocked.collectAsState()
    if (!locked) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // An empty tap handler consumes touches so they never reach
                // the controls underneath. The drag gestures are already
                // disabled by the lock while this shield is up.
                detectTapGestures { }
            },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.player_locked),
                    tint = Color.White,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                )
            }
            IconButton(
                onClick = feature::unlock,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Icon(
                    Icons.Filled.LockOpen,
                    contentDescription = stringResource(R.string.player_unlock),
                    tint = Color.White,
                )
            }
        }
    }
}
