package com.singalong07.app.domain.audio

import android.app.ActivityOptions
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.singalong07.app.service.accessibility.SingAlongAccessibilityService

/**
 * AudioCaptureManager
 *
 * Launches Google Sound Search / Music Search in a compact mini-window launcher
 * so that Google listens to audio and SingAlongAccessibilityService intercepts
 * the recognized song title and artist.
 */
class AudioCaptureManager {

    /**
     * Directly triggers Google Sound Search / Music Search intent in a very small
     * floating mini-window launcher (Shazam-style) and auto-extracts via Accessibility.
     */
    fun launchGoogleMusicSearch(context: Context): Boolean {
        if (!SingAlongAccessibilityService.isAccessibilityEnabled(context)) {
            android.widget.Toast.makeText(
                context,
                "Tip: Enable SingAlong07 in Accessibility to auto-extract songs from Google Sound Search!",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        val metrics = context.resources.displayMetrics
        val density = metrics.density
        // Very small compact dimensions (Shazam pop-up style): 220dp x 180dp
        val targetWidthPx = (220 * density).toInt().coerceIn((metrics.widthPixels * 0.35).toInt(), (metrics.widthPixels * 0.55).toInt())
        val targetHeightPx = (180 * density).toInt().coerceIn((metrics.heightPixels * 0.18).toInt(), (metrics.heightPixels * 0.28).toInt())
        val left = (metrics.widthPixels - targetWidthPx) / 2
        val top = (metrics.heightPixels - targetHeightPx - (40 * density).toInt()).coerceAtLeast(0)
        val bounds = Rect(left, top, left + targetWidthPx, top + targetHeightPx)

        val activityOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ActivityOptions.makeBasic().apply {
                setLaunchBounds(bounds)
            }.toBundle()
        } else null

        val candidates = listOf(
            Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").apply {
                setPackage("com.google.android.googlequicksearchbox")
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.googlequicksearchbox.MusicSearchActivity"
                )
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.gsa.soundsearch.SoundSearchActivity"
                )
            },
            Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://soundsearch")),
            Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH"),
            Intent("com.google.android.gms.actions.SEARCH_ACTION").apply {
                putExtra("query", "What's this song?")
            },
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, "What song is this?")
            }
        )

        for (intent in candidates) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            // OEM floating mini-window extras (Samsung One UI, Xiaomi HyperOS/MIUI, ColorOS)
            intent.putExtra("android.intent.extra.WINDOW_MODE", 5) // Freeform
            intent.putExtra("com.samsung.android.intent.extra.WINDOW_MODE", 5)
            intent.putExtra("miui.intent.extra.FREEFORM_LAUNCH", true)
            intent.putExtra("miui.intent.extra.OPEN_IN_MINI_WINDOW", true)
            intent.putExtra("oppo.intent.extra.OPEN_IN_MINI_WINDOW", true)

            try {
                val activities = context.packageManager.queryIntentActivities(intent, 0)
                if (activities.isNotEmpty()) {
                    context.startActivity(intent, activityOptions)
                    return true
                }
            } catch (_: Exception) {}
        }

        // Direct launch fallback without package manager query
        for (intent in candidates) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            intent.putExtra("android.intent.extra.WINDOW_MODE", 5)
            intent.putExtra("com.samsung.android.intent.extra.WINDOW_MODE", 5)
            intent.putExtra("miui.intent.extra.FREEFORM_LAUNCH", true)
            try {
                context.startActivity(intent, activityOptions)
                return true
            } catch (_: Exception) {}
        }
        return false
    }
}
