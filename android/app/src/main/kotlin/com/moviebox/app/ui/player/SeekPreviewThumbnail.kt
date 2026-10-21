package com.moviebox.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Floating seek preview thumbnail shown while dragging / scrubbing playback position.
 */
@Composable
fun SeekPreviewThumbnail(
    targetPositionMs: Long?,
    durationMs: Long,
    deltaText: String?,
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = targetPositionMs != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        if (targetPositionMs == null) return@AnimatedVisibility

        val targetSec = targetPositionMs / 1000
        val targetMin = targetSec / 60
        val targetRemainderSec = targetSec % 60
        val targetFormatted = if (targetMin >= 60) {
            val hours = targetMin / 60
            val min = targetMin % 60
            String.format("%02d:%02d:%02d", hours, min, targetRemainderSec)
        } else {
            String.format("%02d:%02d", targetMin, targetRemainderSec)
        }

        val totalSec = durationMs / 1000
        val totalMin = totalSec / 60
        val totalRemainderSec = totalSec % 60
        val totalFormatted = if (totalMin >= 60) {
            val hours = totalMin / 60
            val min = totalMin % 60
            String.format("%02d:%02d:%02d", hours, min, totalRemainderSec)
        } else {
            String.format("%02d:%02d", totalMin, totalRemainderSec)
        }

        val progress = if (durationMs > 0) {
            (targetPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
            shadowElevation = 10.dp,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(160.dp),
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Seek preview thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(160.dp)
                            .height(90.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(Color(0xFF1E1E2E)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(Color(0xFF1E1E2E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (deltaText?.startsWith("+") == true) {
                                Icons.Filled.FastForward
                            } else {
                                Icons.Filled.FastRewind
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = targetFormatted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (!deltaText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = deltaText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (deltaText.startsWith("+")) {
                                    Color(0xFFA6E3A1)
                                } else {
                                    Color(0xFFF38BA8)
                                },
                            )
                        }
                    }

                    if (durationMs > 0) {
                        Text(
                            text = "of $totalFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .width(140.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )
                    }
                }
            }
        }
    }
}
