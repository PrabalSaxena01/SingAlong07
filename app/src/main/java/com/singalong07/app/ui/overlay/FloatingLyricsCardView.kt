package com.singalong07.app.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singalong07.app.data.preferences.OverlaySettings
import com.singalong07.app.domain.sync.SyncPlaybackState
import com.singalong07.app.domain.sync.SyncResolutionTier
import com.singalong07.app.domain.translation.DualLanguageLyricLine
import com.singalong07.app.ui.theme.LyricFontScale
import com.singalong07.app.ui.theme.OverlayColorScheme

@Composable
fun FloatingLyricsCardView(
    syncState: SyncPlaybackState,
    settings: OverlaySettings,
    colorScheme: OverlayColorScheme,
    trackTitle: String?,
    artistName: String?,
    onLineClicked: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onNudgeOffset: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onMinimizeToPill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(syncState.activeLineIndex) {
        if (syncState.activeLineIndex >= 0 && syncState.lines.isNotEmpty()) {
            val targetScroll = (syncState.activeLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(colorScheme.backgroundColor.copy(alpha = settings.backgroundOpacity))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Header: Minimize, Title & Artist, Settings Cog
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMinimizeToPill, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize to Pill",
                        tint = colorScheme.activeLineColor
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = trackTitle ?: "SingAlong07",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.activeLineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = artistName ?: "Awaiting Playback...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.inactiveLineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = colorScheme.activeLineColor
                    )
                }
            }

            // Sync Tier Badge
            val tierBadge = when (syncState.tier) {
                SyncResolutionTier.TIER_1_WORD_BY_WORD -> "Karaoke Synced"
                SyncResolutionTier.TIER_2_LINE_BY_LINE -> "LRC Synced"
                SyncResolutionTier.TIER_3_HEURISTIC_PACING -> "Paced (Plain)"
                SyncResolutionTier.TIER_4_FINGERPRINT_MATCH -> "Audio Match"
                SyncResolutionTier.TIER_5_INSTRUMENTAL -> "Instrumental"
            }
            Text(
                text = tierBadge,
                color = colorScheme.accentColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)
            )

            // Lyrics List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 36.dp)
            ) {
                itemsIndexed(syncState.lines) { index, line ->
                    val isActive = index == syncState.activeLineIndex
                    InteractiveLyricLineItem(
                        line = line,
                        isActive = isActive,
                        colorScheme = colorScheme,
                        fontScale = settings.fontScale,
                        showTranslation = settings.isTranslationEnabled,
                        showRomanization = settings.isRomanizationEnabled,
                        onClick = { onLineClicked(index) }
                    )
                }
            }

            // Bottom Transport Bar
            Surface(
                color = colorScheme.surfaceColor.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timing Nudge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onNudgeOffset(-500L) },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("-0.5s", color = colorScheme.accentColor, fontSize = 11.sp)
                        }
                        Text(
                            text = if (syncState.userOffsetMs != 0L) "${syncState.userOffsetMs / 1000.0}s" else "0s",
                            color = colorScheme.inactiveLineColor,
                            fontSize = 11.sp
                        )
                        TextButton(
                            onClick = { onNudgeOffset(500L) },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("+0.5s", color = colorScheme.accentColor, fontSize = 11.sp)
                        }
                    }

                    // Transport Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Prev", tint = colorScheme.activeLineColor)
                        }
                        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (syncState.isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = colorScheme.accentColor
                            )
                        }
                        IconButton(onClick = onSkipNext, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = colorScheme.activeLineColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveLyricLineItem(
    line: DualLanguageLyricLine,
    isActive: Boolean,
    colorScheme: OverlayColorScheme,
    fontScale: LyricFontScale,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = if (isActive) 1.04f else 0.98f, label = "lineScale")
    val textColor by animateColorAsState(
        targetValue = if (isActive) colorScheme.activeLineColor else colorScheme.inactiveLineColor,
        label = "textColor"
    )

    val secondaryText = line.getSecondaryDisplay(showTranslation, showRomanization)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = line.originalText,
            color = textColor,
            fontSize = fontScale.primarySize,
            lineHeight = fontScale.lineHeight,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        if (!secondaryText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondaryText,
                color = if (isActive) colorScheme.translationColor else colorScheme.inactiveLineColor.copy(alpha = 0.7f),
                fontSize = fontScale.subTextSize,
                textAlign = TextAlign.Center
            )
        }
    }
}
