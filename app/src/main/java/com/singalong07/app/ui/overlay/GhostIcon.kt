package com.singalong07.app.ui.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val GhostVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Ghost",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.White)) {
        // Ghost dome and flowing silhouette
        moveTo(12f, 2f)
        curveTo(7.03f, 2f, 3f, 6.03f, 3f, 11f)
        lineTo(3f, 22f)
        lineTo(6f, 19f)
        lineTo(9f, 22f)
        lineTo(12f, 19f)
        lineTo(15f, 22f)
        lineTo(18f, 19f)
        lineTo(21f, 22f)
        lineTo(21f, 11f)
        curveTo(21f, 6.03f, 16.97f, 2f, 12f, 2f)
        close()

        // Left eye cutout
        moveTo(9f, 8f)
        curveTo(9.83f, 8f, 10.5f, 8.67f, 10.5f, 9.5f)
        curveTo(10.5f, 10.33f, 9.83f, 11f, 9f, 11f)
        curveTo(8.17f, 11f, 7.5f, 10.33f, 7.5f, 9.5f)
        curveTo(7.5f, 8.67f, 8.17f, 8f, 9f, 8f)
        close()

        // Right eye cutout
        moveTo(15f, 8f)
        curveTo(15.83f, 8f, 16.5f, 8.67f, 16.5f, 9.5f)
        curveTo(16.5f, 10.33f, 15.83f, 11f, 15f, 11f)
        curveTo(14.17f, 11f, 13.5f, 10.33f, 13.5f, 9.5f)
        curveTo(13.5f, 8.67f, 14.17f, 8f, 15f, 8f)
        close()
    }.build()
}
