# SingAlong07: Android Floating Timed Lyrics & Scrobbler Companion

A modern, battery-efficient Android application featuring an interactive floating overlay window (`WindowManager` + Jetpack Compose) that seamlessly shifts between a compact floating **Pill** and an expanded **Floating Card**. 

The app intercepts playing media across music players, cleans and guesses track titles using fuzzy algorithms and Last.fm corrections, aggregates timed lyrics across multiple providers (LRCLIB, Netease, Genius), supports audio fingerprinting fallbacks, logs listening history with CSV export, translates lyrics in real time, and provides full **bi-directional interactive playback control**.

---

## What Was Built in v1

### 1. Overlay Controls & Pill ↔ Card Morphing
- **Floating Pill**: Compact capsule with scrolling track marquee, album art orb, and animated equalizer bars.
- **Floating Card**: Full lyrics sheet with smooth auto-scroll to active line, dynamic themes, and transport controls.
- **Click-Through Ghost Mode** (`FLAG_NOT_TOUCHABLE`): Let taps pass directly through to background games or reading apps.
- **Source Code**:
  - [`FloatingOverlayService.kt`](app/src/main/java/com/singalong07/app/service/overlay/FloatingOverlayService.kt)
  - [`FloatingOverlayRoot.kt`](app/src/main/java/com/singalong07/app/ui/overlay/FloatingOverlayRoot.kt)
  - [`FloatingPillView.kt`](app/src/main/java/com/singalong07/app/ui/overlay/FloatingPillView.kt)
  - [`FloatingLyricsCardView.kt`](app/src/main/java/com/singalong07/app/ui/overlay/FloatingLyricsCardView.kt)

### 2. Bi-Directional Interactive Playback Control
- **Interactive Seek-to-Line**: Tapping any lyric line in the floating overlay instantly jumps the active music player (Spotify, YouTube Music) to that exact millisecond.
- **Transport Controls**: Play/Pause, Next Track, Previous Track, and `[-0.5s]` / `[+0.5s]` timing offset calibration.
- **Source Code**:
  - [`MediaSessionControllerBridge.kt`](app/src/main/java/com/singalong07/app/service/media/MediaSessionControllerBridge.kt)
  - [`BiDirectionalSyncEngine.kt`](app/src/main/java/com/singalong07/app/domain/sync/BiDirectionalSyncEngine.kt)

### 3. Intensive Fallback Sync Pipeline
- **Tier 1**: Word-by-word syllable karaoke timestamps.
- **Tier 2**: Standard millisecond `.lrc` lines.
- **Tier 3 (Heuristic Cadence Pacing)**: When only plain unsynced text exists, estimates speech cadence and line timestamps based on word density across track duration so lyrics scroll smoothly.
- **Tier 4**: Acoustic audio fingerprinting match (Chromaprint / AcoustID / ACRCloud).
- **Tier 5**: Instrumental audio visualizer mode.
- **Source Code**:
  - [`BiDirectionalSyncEngine.kt`](app/src/main/java/com/singalong07/app/domain/sync/BiDirectionalSyncEngine.kt)
  - [`LrcParser.kt`](app/src/main/java/com/singalong07/app/domain/parser/LrcParser.kt)

### 4. Theme Engine & Font Scaling
- **Themes**: AMOLED Pure Black, Frosted Glass Blur, Neon Cyberpunk, Paper Light, and Dynamic Album Art Palette extraction.
- **Font Scaling**: 4 typography presets (`COMPACT`, `BALANCED`, `COMFORTABLE`, `JUMBO`) with dynamic line heights.
- **Opacity Slider**: Adjustable from 20% to 100% background transparency.
- **Source Code**:
  - [`OverlayTheme.kt`](app/src/main/java/com/singalong07/app/ui/theme/OverlayTheme.kt)
  - [`OverlayPreferences.kt`](app/src/main/java/com/singalong07/app/data/preferences/OverlayPreferences.kt)
  - [`ThemeAndFontSettingsSheet.kt`](app/src/main/java/com/singalong07/app/ui/overlay/ThemeAndFontSettingsSheet.kt)

### 5. Multi-Source Lyrics Aggregation
- Cascades through **Local Storage (.lrc vault)** ➔ **LRCLIB** (free community timed LRC) ➔ **Netease Cloud 163** (vast catalog with synchronized translations and romanized pronunciation) ➔ **Genius / Pacing Fallback**.
- **Source Code**:
  - [`LyricsSource.kt`](app/src/main/java/com/singalong07/app/data/sources/LyricsSource.kt)
  - [`LrclibSource.kt`](app/src/main/java/com/singalong07/app/data/sources/LrclibSource.kt)
  - [`NeteaseSource.kt`](app/src/main/java/com/singalong07/app/data/sources/NeteaseSource.kt)
  - [`LocalLrcSource.kt`](app/src/main/java/com/singalong07/app/data/sources/LocalLrcSource.kt)
  - [`LyricsAggregatorRepository.kt`](app/src/main/java/com/singalong07/app/data/sources/LyricsAggregatorRepository.kt)

### 6. Room Listening History & CSV Export
- Archives every recognized song, duration, player package, lyric source, and user offset in local SQLite via Room DB.
- One-tap export to standard RFC-4180 CSV via Android Storage Access Framework (SAF).
- **Source Code**:
  - [`SingAlongDatabase.kt`](app/src/main/java/com/singalong07/app/data/db/SingAlongDatabase.kt)
  - [`ListeningHistoryEntity.kt`](app/src/main/java/com/singalong07/app/data/db/ListeningHistoryEntity.kt)
  - [`HistoryDao.kt`](app/src/main/java/com/singalong07/app/data/db/HistoryDao.kt)
  - [`HistoryCsvExporter.kt`](app/src/main/java/com/singalong07/app/data/export/HistoryCsvExporter.kt)
  - [`MainActivity.kt`](app/src/main/java/com/singalong07/app/ui/main/MainActivity.kt)

---

## Documentation

- **Full Architecture Plan**: [ARCHITECTURE_PLAN.md](docs/ARCHITECTURE_PLAN.md)
- **Lyrics Sources & API Guide**: [LYRICS_SOURCES_GUIDE.md](docs/LYRICS_SOURCES_GUIDE.md)

---

## Project Structure

```
SingAlong07/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/singalong07/app/
│       │   │   ├── SingAlongApp.kt
│       │   │   ├── data/
│       │   │   │   ├── db/ (Room database, entities & DAO)
│       │   │   │   ├── export/ (HistoryCsvExporter)
│       │   │   │   ├── preferences/ (OverlayPreferencesManager)
│       │   │   │   └── sources/ (LRCLIB, Netease, Local LRC, Aggregator)
│       │   │   ├── domain/
│       │   │   │   ├── cleaner/ (TrackMetadataCleaner)
│       │   │   │   ├── parser/ (LrcParser)
│       │   │   │   ├── sync/ (BiDirectionalSyncEngine)
│       │   │   │   └── translation/ (DualLanguageLyricLine & TranslationEngine)
│       │   │   ├── service/
│       │   │   │   ├── media/ (MusicTrackingNotificationService & ControllerBridge)
│       │   │   │   └── overlay/ (FloatingOverlayService WindowManager)
│       │   │   └── ui/
│       │   │       ├── main/ (MainActivity)
│       │   │       ├── overlay/ (FloatingOverlayRoot, PillView, CardView, SettingsSheet)
│       │   │       └── theme/ (OverlayTheme, Palettes, Typography)
│       │   └── res/ (strings, xml network security)
│       └── test/java/com/singalong07/app/ (Unit test suite)
├── docs/
│   ├── ARCHITECTURE_PLAN.md
│   └── LYRICS_SOURCES_GUIDE.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
└── README.md
```
