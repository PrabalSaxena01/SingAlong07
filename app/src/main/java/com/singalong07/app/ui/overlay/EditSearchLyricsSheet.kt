package com.singalong07.app.ui.overlay

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singalong07.app.ui.theme.OverlayColorScheme

@Composable
fun EditSearchLyricsSheet(
    initialTitle: String,
    initialArtist: String,
    preferredProviderId: String,
    colorScheme: OverlayColorScheme,
    onDismiss: () -> Unit,
    onSearch: (title: String, artist: String, providerId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var titleText by remember(initialTitle) { mutableStateOf(initialTitle) }
    var artistText by remember(initialArtist) { mutableStateOf(initialArtist) }
    var selectedProvider by remember(preferredProviderId) { mutableStateOf(preferredProviderId) }

    Card(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colorScheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Edit & Search Lyrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            // Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Refine the song title or artist if automatic metadata detection was inaccurate or noisy.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Track Title Input
                Column {
                    Text(text = "Song Title", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Kesariya", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Artist Name Input
                Column {
                    Text(text = "Artist Name", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = artistText,
                        onValueChange = { artistText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Arijit Singh", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Provider Choice
                Column {
                    Text(text = "Target Provider", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "auto" to "Auto Best",
                            "jiosaavn" to "Bollywood",
                            "lrclib" to "LRCLIB",
                            "kugou" to "KuGou",
                            "netease" to "Netease",
                            "lyricsovh" to "Lyrics.ovh"
                        ).forEach { (id, label) ->
                            val isSelected = selectedProvider == id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedProvider = id },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions: Cancel & Search
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (titleText.isNotBlank()) {
                                onSearch(titleText.trim(), artistText.trim(), selectedProvider)
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        enabled = titleText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.accentColor),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Search & Sync", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
