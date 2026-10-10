package com.singalong07.app.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singalong07.app.data.preferences.OverlaySettings
import com.singalong07.app.ui.theme.LyricFontScale
import com.singalong07.app.ui.theme.OverlayThemeMode

@Composable
fun ThemeAndFontSettingsSheet(
    settings: OverlaySettings,
    onThemeSelect: (OverlayThemeMode) -> Unit,
    onFontScaleSelect: (LyricFontScale) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onToggleTranslation: (Boolean) -> Unit,
    onToggleRomanization: (Boolean) -> Unit,
    onToggleClickThrough: (Boolean) -> Unit,
    onProviderSelect: (String) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SingAlong07 Customization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Preferred Provider Section
            Text(text = "Preferred Lyric Provider", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "auto" to "Auto",
                    "jiosaavn" to "Bollywood",
                    "lrclib" to "LRCLIB",
                    "netease" to "Netease"
                ).forEach { (id, label) ->
                    val isSelected = settings.preferredProviderId == id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onProviderSelect(id) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Theme Presets
            Text(text = "Theme Preset", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOptionButton(
                    label = "Amoled",
                    color = Color.Black,
                    isSelected = settings.themeMode == OverlayThemeMode.AMOLED_DARK,
                    onClick = { onThemeSelect(OverlayThemeMode.AMOLED_DARK) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionButton(
                    label = "Frosted",
                    color = Color(0xFF2C3240),
                    isSelected = settings.themeMode == OverlayThemeMode.FROSTED_GLASS,
                    onClick = { onThemeSelect(OverlayThemeMode.FROSTED_GLASS) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionButton(
                    label = "Neon",
                    color = Color(0xFF00FFFF),
                    isSelected = settings.themeMode == OverlayThemeMode.NEON_CYBERPUNK,
                    onClick = { onThemeSelect(OverlayThemeMode.NEON_CYBERPUNK) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionButton(
                    label = "Palette",
                    color = Color(0xFFE91E63),
                    isSelected = settings.themeMode == OverlayThemeMode.DYNAMIC_PALETTE,
                    onClick = { onThemeSelect(OverlayThemeMode.DYNAMIC_PALETTE) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Font Scale
            Text(text = "Lyrics Typography Scale", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LyricFontScale.values().forEach { scale ->
                    val isSelected = settings.fontScale == scale
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFontScaleSelect(scale) },
                        label = { Text(scale.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Opacity Slider
            Text(
                text = "Background Opacity: ${(settings.backgroundOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = settings.backgroundOpacity,
                onValueChange = onOpacityChange,
                valueRange = 0.2f..1.0f,
                steps = 8
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            // 5. Translation & Romanization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Dual-Language Translation", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = settings.isTranslationEnabled, onCheckedChange = onToggleTranslation)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Romanized Pronunciation", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = settings.isRomanizationEnabled, onCheckedChange = onToggleRomanization)
            }

            // 6. Click-Through Ghost Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Click-Through Ghost Mode", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Touch passes through to background apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.isClickThroughEnabled, onCheckedChange = onToggleClickThrough)
            }
        }
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
