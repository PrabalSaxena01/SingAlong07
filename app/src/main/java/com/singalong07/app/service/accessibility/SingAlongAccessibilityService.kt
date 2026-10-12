package com.singalong07.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.singalong07.app.SingAlongApp

/**
 * SingAlongAccessibilityService
 *
 * Automatically inspects on-screen results from Google Sound Search / Music Search
 * (com.google.android.googlequicksearchbox), extracts the identified song title and artist,
 * auto-dismisses Google via GLOBAL_ACTION_BACK, and sends the song to SingAlong07 to
 * display floating lyrics and log to history.
 */
class SingAlongAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastIdentifiedKey: String? = null
    private var lastIdentifiedTime: Long = 0L

    companion object {
        private const val TAG = "SingAlongA11y"
        var isServiceRunning = false
            private set

        private val IGNORED_PHRASES = setOf(
            "listening",
            "listening...",
            "listening for music",
            "listening for a song",
            "play, sing or hum a song",
            "try again",
            "couldn't find a match",
            "what's this song?",
            "search",
            "google",
            "sound search",
            "music search",
            "listen",
            "share",
            "lyrics",
            "videos",
            "overview",
            "explore",
            "more results",
            "feedback",
            "privacy",
            "terms",
            "about this result",
            "youtube",
            "spotify",
            "apple music",
            "amazon music",
            "youtube music",
            "open in",
            "web search",
            "search results",
            "tap to search"
        )

        fun isAccessibilityEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            val myService = "${context.packageName}/${SingAlongAccessibilityService::class.java.canonicalName}"
            val shortService = "${context.packageName}/.service.accessibility.SingAlongAccessibilityService"
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(myService, ignoreCase = true) ||
                    componentName.equals(shortService, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "SingAlongAccessibilityService connected and ready")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }

    override fun onInterrupt() {
        Log.d(TAG, "SingAlongAccessibilityService interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (!packageName.contains("googlequicksearchbox")) return

        val rootNode = rootInActiveWindow ?: return
        try {
            val song = extractSongInfo(rootNode)
            if (song != null) {
                val (rawTitle, rawArtist) = song
                val cleanTitle = cleanCandidateText(rawTitle)
                val cleanArtist = cleanCandidateText(rawArtist)

                if (cleanTitle.length in 2..100 && cleanArtist.length in 2..100) {
                    val key = "${cleanArtist.lowercase()}__${cleanTitle.lowercase()}"
                    val now = System.currentTimeMillis()

                    // Debounce duplicate events for same song within 15 seconds
                    if (key != lastIdentifiedKey || now - lastIdentifiedTime > 15000L) {
                        lastIdentifiedKey = key
                        lastIdentifiedTime = now
                        Log.i(TAG, "Successfully intercepted song: $cleanTitle by $cleanArtist")

                        // 1. Dispatch to SingAlongApp
                        val app = application as? SingAlongApp
                        app?.onExternalSongIdentified(cleanTitle, cleanArtist, "google.soundsearch")

                        // 2. Automatically press BACK to dismiss Google Sound Search back to user's music/app
                        mainHandler.postDelayed({
                            try {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to auto-dismiss Google window", e)
                            }
                        }, 350L)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while analyzing Google Sound Search window", e)
        } finally {
            try {
                rootNode.recycle()
            } catch (_: Exception) {}
        }
    }

    private data class NodeTextItem(
        val text: String,
        val viewId: String?,
        val depth: Int
    )

    private fun extractSongInfo(root: AccessibilityNodeInfo): Pair<String, String>? {
        val items = mutableListOf<NodeTextItem>()
        collectTextNodes(root, 0, items)

        if (items.isEmpty()) return null

        // Strategy 1: Check known Google Sound Search view ID names
        var idTitle: String? = null
        var idArtist: String? = null
        for (item in items) {
            val id = item.viewId?.lowercase().orEmpty()
            if (id.contains("title") && !id.contains("subtitle") && idTitle == null && isValidSongText(item.text)) {
                idTitle = item.text
            } else if ((id.contains("artist") || id.contains("subtitle") || id.contains("sub_title")) && idArtist == null && isValidSongText(item.text)) {
                idArtist = item.text
            }
        }
        if (!idTitle.isNullOrBlank() && !idArtist.isNullOrBlank()) {
            return Pair(idTitle, parseArtistSubtitle(idArtist))
        }

        // Strategy 2: Look for "Song by <Artist>" pattern
        for (i in items.indices) {
            val current = items[i].text
            if (current.startsWith("Song by ", ignoreCase = true) || current.contains("• Song by ", ignoreCase = true)) {
                val artist = current.replace(Regex("(?i).*Song by\\s+"), "").trim()
                // The title is typically the preceding non-blacklisted item
                val prev = items.take(i).lastOrNull { isValidSongText(it.text) }
                if (prev != null && artist.isNotBlank()) {
                    return Pair(prev.text, artist)
                }
            }
        }

        // Strategy 3: Check for "<Title> by <Artist>" or "<Title> - <Artist>" single string
        for (item in items) {
            val text = item.text
            if (text.contains(" by ", ignoreCase = true) && !text.startsWith("Song by", ignoreCase = true)) {
                val parts = text.split(Regex("(?i)\\s+by\\s+"), limit = 2)
                if (isValidSongText(parts[0]) && isValidSongText(parts[1])) {
                    return Pair(parts[0], parseArtistSubtitle(parts[1]))
                }
            } else if (text.contains(" - ")) {
                val parts = text.split(" - ", limit = 2)
                if (isValidSongText(parts[0]) && isValidSongText(parts[1])) {
                    return Pair(parts[0], parseArtistSubtitle(parts[1]))
                }
            }
        }

        // Strategy 4: Filter significant valid text nodes sequentially
        val validTexts = items
            .map { it.text.trim() }
            .filter { isValidSongText(it) }
            .distinct()

        if (validTexts.size >= 2) {
            val candidateTitle = validTexts[0]
            val candidateArtist = parseArtistSubtitle(validTexts[1])
            if (candidateTitle.isNotBlank() && candidateArtist.isNotBlank() && candidateTitle != candidateArtist) {
                return Pair(candidateTitle, candidateArtist)
            }
        }

        return null
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo?, depth: Int, outList: MutableList<NodeTextItem>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName

        val chosenText = when {
            !text.isNullOrBlank() -> text
            !contentDesc.isNullOrBlank() -> contentDesc
            else -> null
        }

        if (!chosenText.isNullOrBlank()) {
            outList.add(NodeTextItem(chosenText, viewId, depth))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextNodes(child, depth + 1, outList)
            try {
                child.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun isValidSongText(text: String): Boolean {
        if (text.length < 2 || text.length > 120) return false
        val lower = text.lowercase().trim()
        if (IGNORED_PHRASES.contains(lower)) return false
        if (lower.startsWith("listening") || lower.startsWith("play, sing") || lower.startsWith("try again")) return false
        return true
    }

    private fun parseArtistSubtitle(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("Song by ", ignoreCase = true)) {
            clean = clean.substring(8).trim()
        }
        if (clean.contains(" • ")) {
            clean = clean.split(" • ")[0].trim()
        }
        return clean
    }

    private fun cleanCandidateText(text: String): String {
        return text
            .replace(Regex("^[\"']+|[\"']+$"), "")
            .trim()
    }
}
