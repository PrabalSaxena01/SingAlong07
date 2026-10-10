package com.singalong07.app.ui.overlay

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singalong07.app.ui.theme.OverlayColorScheme

@Composable
fun FloatingPillView(
    trackTitle: String,
    artistName: String,
    isPlaying: Boolean,
    colorScheme: OverlayColorScheme,
    onExpandToCard: () -> Unit,
    albumArt: Bitmap? = null,
    onDragWindow: (Float, Float) -> Unit = { _, _ -> },
    onDragWindowEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Mini equalizer animation
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val barHeight1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val barHeight2 by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    var accumulatedDelta = Offset.Zero
                    val pointerId = down.id

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                if (!isDrag) {
                                    onExpandToCard()
                                }
                                break
                            }
                            val delta = change.position - change.previousPosition
                            accumulatedDelta += delta
                            if (!isDrag && accumulatedDelta.getDistance() > viewConfiguration.touchSlop) {
                                isDrag = true
                            }
                            if (isDrag) {
                                change.consume()
                                onDragWindow(delta.x, delta.y)
                            }
                        }
                    } finally {
                        if (isDrag) {
                            onDragWindowEnd()
                        }
                    }
                }
            },
        color = colorScheme.backgroundColor.copy(alpha = colorScheme.backgroundAlpha),
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Orb / Music Icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorScheme.accentColor),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Track title & artist
            Column(modifier = Modifier.widthIn(max = 140.dp)) {
                Text(
                    text = trackTitle.ifBlank { "SingAlong07" },
                    color = colorScheme.activeLineColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artistName.ifBlank { "Tap to open" },
                    color = colorScheme.inactiveLineColor,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Animated Equalizer Bars
            if (isPlaying) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(barHeight1.dp)
                            .background(colorScheme.accentColor, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(barHeight2.dp)
                            .background(colorScheme.accentColor, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun Surface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    color: Color,
    shadowElevation: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        shadowElevation = shadowElevation,
        content = content
    )
}
