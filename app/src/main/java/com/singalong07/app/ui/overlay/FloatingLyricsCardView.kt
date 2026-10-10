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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    onSeekToTimestamp: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onNudgeOffset: (Long) -> Unit,
    onSwitchProvider: (String) -> Unit,
    onRetryLyrics: () -> Unit = {},
    onToggleGhostMode: () -> Unit = {},
    onOpenEditSearch: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onMinimizeToPill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    // When track title changes, immediately reset scroll to top
    LaunchedEffect(trackTitle) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(syncState.activeLineIndex) {
        if (!isUserScrubbing && syncState.activeLineIndex >= 0 && syncState.lines.isNotEmpty()) {
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
            // Header: Minimize, Title & Artist (clickable to edit), Ghost Toggle, Settings Cog
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenEditSearch),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = trackTitle ?: "SingAlong07",
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.activeLineColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Search",
                            tint = colorScheme.inactiveLineColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        text = artistName ?: "Awaiting Playback...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.inactiveLineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ghost Icon: Toggle Click-Through Ghost Mode
                    IconButton(onClick = onToggleGhostMode, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = GhostVector,
                            contentDescription = if (settings.isClickThroughEnabled) "Ghost Mode Active" else "Enable Ghost Mode",
                            tint = if (settings.isClickThroughEnabled) colorScheme.accentColor else colorScheme.activeLineColor.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Settings Cog
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colorScheme.activeLineColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Sync Tier & Provider Switcher Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tierBadge = when (syncState.tier) {
                    SyncResolutionTier.TIER_1_WORD_BY_WORD -> "Karaoke Synced"
                    SyncResolutionTier.TIER_2_LINE_BY_LINE -> "LRC Synced"
                    SyncResolutionTier.TIER_3_HEURISTIC_PACING -> "Paced (Plain)"
                    SyncResolutionTier.TIER_4_FINGERPRINT_MATCH -> "Audio Match"
                    SyncResolutionTier.TIER_5_INSTRUMENTAL -> if (syncState.lyricsNotFound) "No Lyrics" else "Instrumental"
                }

                val fullBadge = if (!syncState.sourceName.isNullOrBlank()) {
                    "$tierBadge • ${syncState.sourceName}"
                } else {
                    tierBadge
                }

                Text(
                    text = fullBadge,
                    color = colorScheme.accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Provider Switcher Chip
                val providerLabel = when (settings.preferredProviderId) {
                    "jiosaavn" -> "JioSaavn"
                    "lrclib" -> "LRCLIB"
                    "kugou" -> "KuGou"
                    "netease" -> "Netease"
                    "lyricsovh" -> "Lyrics.ovh"
                    else -> "Auto Source"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.surfaceColor,
                    modifier = Modifier.clickable {
                        // Cycle through providers on click: auto -> jiosaavn -> lrclib -> kugou -> netease -> lyricsovh
                        val next = when (settings.preferredProviderId) {
                            "auto" -> "jiosaavn"
                            "jiosaavn" -> "lrclib"
                            "lrclib" -> "kugou"
                            "kugou" -> "netease"
                            "netease" -> "lyricsovh"
                            else -> "auto"
                        }
                        onSwitchProvider(next)
                    }
                ) {
                    Text(
                        text = "$providerLabel ▾",
                        color = colorScheme.activeLineColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Middle Content: Loading, Lyrics Not Found Fallback, or Active Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (trackTitle == null) {
                    // No media detected
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colorScheme.inactiveLineColor,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Play music in any app!",
                                color = colorScheme.activeLineColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Spotify, YouTube, JioSaavn, Apple Music...",
                                color = colorScheme.inactiveLineColor,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (syncState.isLoading) {
                    // Actively loading lyrics
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = colorScheme.accentColor,
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Searching lyrics...",
                                color = colorScheme.activeLineColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val searchHint = when (settings.preferredProviderId) {
                                "jiosaavn" -> "Checking JioSaavn Bollywood..."
                                "lrclib" -> "Checking LRCLIB database..."
                                "kugou" -> "Checking KuGou 50M+ database..."
                                "netease" -> "Checking Netease database..."
                                "lyricsovh" -> "Checking Lyrics.ovh database..."
                                else -> "Checking LRCLIB, JioSaavn, KuGou & Netease..."
                            }
                            Text(
                                text = searchHint,
                                color = colorScheme.inactiveLineColor,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (syncState.lines.isEmpty()) {
                    // Prominent Fallback: Lyrics Not Found UI
                    LyricsNotFoundFallbackView(
                        trackTitle = trackTitle,
                        artistName = artistName,
                        preferredProviderId = settings.preferredProviderId,
                        colorScheme = colorScheme,
                        onSwitchProvider = onSwitchProvider,
                        onOpenEditSearch = onOpenEditSearch,
                        onRetry = onRetryLyrics
                    )
                } else {
                    // Synced lines LazyColumn
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 24.dp)
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
                }
            }

            // Interactive Seek Bar / Timeline Scrubber
            if (syncState.durationMs > 0) {
                val currentPos = if (isUserScrubbing) (scrubProgress * syncState.durationMs).toLong() else syncState.currentPositionMs
                val progress = if (syncState.durationMs > 0) (currentPos.toFloat() / syncState.durationMs).coerceIn(0f, 1f) else 0f

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPos),
                        color = colorScheme.inactiveLineColor,
                        fontSize = 10.sp
                    )

                    Slider(
                        value = if (isUserScrubbing) scrubProgress else progress,
                        onValueChange = {
                            isUserScrubbing = true
                            scrubProgress = it
                        },
                        onValueChangeFinished = {
                            val targetMs = (scrubProgress * syncState.durationMs).toLong()
                            onSeekToTimestamp(targetMs)
                            isUserScrubbing = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.accentColor,
                            activeTrackColor = colorScheme.accentColor,
                            inactiveTrackColor = colorScheme.surfaceColor
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(24.dp)
                    )

                    Text(
                        text = formatTime(syncState.durationMs),
                        color = colorScheme.inactiveLineColor,
                        fontSize = 10.sp
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev", tint = colorScheme.activeLineColor)
                        }
                        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (syncState.isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = colorScheme.accentColor
                            )
                        }
                        IconButton(onClick = onSkipNext, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = colorScheme.activeLineColor)
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
                onClick = onClick // Tapping line scrubs media player to this line!
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

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun LyricsNotFoundFallbackView(
    trackTitle: String,
    artistName: String?,
    preferredProviderId: String,
    colorScheme: OverlayColorScheme,
    onSwitchProvider: (String) -> Unit,
    onOpenEditSearch: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colorScheme.surfaceColor,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = "Not Found",
                    tint = colorScheme.accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Lyrics Not Found",
            color = colorScheme.activeLineColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Checked sources for \"$trackTitle\"",
            color = colorScheme.inactiveLineColor,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Switch provider to retry:",
            color = colorScheme.activeLineColor.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ProviderRetryOption(
                label = "JioSaavn (Bollywood / Hindi)",
                isSelected = preferredProviderId == "jiosaavn",
                colorScheme = colorScheme,
                onClick = { onSwitchProvider("jiosaavn") }
            )
            ProviderRetryOption(
                label = "LRCLIB (Global LRC Database)",
                isSelected = preferredProviderId == "lrclib",
                colorScheme = colorScheme,
                onClick = { onSwitchProvider("lrclib") }
            )
            ProviderRetryOption(
                label = "KuGou (50M+ Synced Tracks)",
                isSelected = preferredProviderId == "kugou",
                colorScheme = colorScheme,
                onClick = { onSwitchProvider("kugou") }
            )
            ProviderRetryOption(
                label = "Netease (Anime / Asian / C-Pop)",
                isSelected = preferredProviderId == "netease",
                colorScheme = colorScheme,
                onClick = { onSwitchProvider("netease") }
            )
            ProviderRetryOption(
                label = "Lyrics.ovh (English / Western Plain)",
                isSelected = preferredProviderId == "lyricsovh",
                colorScheme = colorScheme,
                onClick = { onSwitchProvider("lyricsovh") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onOpenEditSearch,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.accentColor),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.weight(1.2f).height(30.dp)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Search", modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Edit Title", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.accentColor),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f).height(30.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Retry", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProviderRetryOption(
    label: String,
    isSelected: Boolean,
    colorScheme: OverlayColorScheme,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) colorScheme.accentColor.copy(alpha = 0.2f) else colorScheme.surfaceColor,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, colorScheme.accentColor) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = colorScheme.activeLineColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = colorScheme.accentColor,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
