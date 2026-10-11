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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.scale
import com.singalong07.app.ui.theme.OverlayColorScheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingPillView(
    trackTitle: String,
    artistName: String,
    isPlaying: Boolean,
    colorScheme: OverlayColorScheme,
    onExpandToCard: () -> Unit,
    albumArt: Bitmap? = null,
    isAudioCatching: Boolean = false,
    audioCatchStatus: String? = null,
    onCatchAudio: () -> Unit = {},
    onLaunchGoogleSoundSearch: () -> Unit = {},
    onDragWindow: (Float, Float) -> Unit = { _, _ -> },
    onDragWindowEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(32.dp)),
        color = colorScheme.backgroundColor.copy(alpha = colorScheme.backgroundAlpha),
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main left section: Album art + Track info + Equalizer (drags & expands)
            Row(
                modifier = Modifier
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
                Column(modifier = Modifier.widthIn(max = 130.dp)) {
                    Text(
                        text = if (isAudioCatching) (audioCatchStatus ?: "Listening...") else trackTitle.ifBlank { "SingAlong07" },
                        color = if (isAudioCatching) Color(0xFF00E5FF) else colorScheme.activeLineColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isAudioCatching) "Ambient Audio ID" else artistName.ifBlank { "Tap to expand" },
                        color = colorScheme.inactiveLineColor,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Animated Equalizer Bars (only rendered and animated when playing)
                if (isPlaying) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AnimatedEqualizerBars(accentColor = colorScheme.accentColor)
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Vertical divider between music body and audio catch action
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(colorScheme.inactiveLineColor.copy(alpha = 0.25f))
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Audio Catching Icon Button
            // Tap: Capture & Identify ambient audio (AudD + auto lyrics sync)
            // Long Press: Launch Google Music Search (Sound Search)
            AudioCatchPillButton(
                isAudioCatching = isAudioCatching,
                colorScheme = colorScheme,
                onCatchAudio = onCatchAudio,
                onLaunchGoogleSoundSearch = onLaunchGoogleSoundSearch
            )
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

@Composable
private fun AnimatedEqualizerBars(accentColor: Color) {
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

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(barHeight1.dp)
                .background(accentColor, RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(barHeight2.dp)
                .background(accentColor, RoundedCornerShape(1.dp))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioCatchPillButton(
    isAudioCatching: Boolean,
    colorScheme: OverlayColorScheme,
    onCatchAudio: () -> Unit,
    onLaunchGoogleSoundSearch: () -> Unit
) {
    val pulseModifier = if (isAudioCatching) {
        val infiniteTransition = rememberInfiniteTransition(label = "catchPulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "catchPulse"
        )
        Modifier.scale(pulse)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .then(pulseModifier)
            .clip(CircleShape)
            .background(
                if (isAudioCatching) Color(0xFF00E5FF).copy(alpha = 0.25f)
                else colorScheme.accentColor.copy(alpha = 0.16f)
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isAudioCatching) Color(0xFF00E5FF)
                    else colorScheme.accentColor.copy(alpha = 0.35f)
                ),
                CircleShape
            )
            .combinedClickable(
                onClick = onCatchAudio,
                onLongClick = onLaunchGoogleSoundSearch
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isAudioCatching) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color(0xFF00E5FF),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Catch Audio (Tap for in-app ID, Long-press for Google Sound Search)",
                tint = colorScheme.accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

