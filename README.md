# FloatingLyrics: Android Floating Timed Lyrics & Scrobbler Companion

A modern, battery-efficient Android application featuring an interactive floating overlay window (`WindowManager` + Jetpack Compose) that seamlessly shifts between a compact floating **Pill** and an expanded **Floating Card**. 

The app intercepts playing media across music players, cleans and guesses track titles using fuzzy algorithms and Last.fm corrections, aggregates timed lyrics across multiple providers (LRCLIB, Netease, Genius), supports audio fingerprinting fallbacks, logs listening history with CSV export, translates lyrics in real time, and provides full **bi-directional interactive playback control**.

---

## What Was Added & Implemented

### 1. Theme Engine & Font Size Selection
- **Themes**: AMOLED Pure Black, Frosted Glass Blur, Neon Cyberpunk, Paper Light, and Dynamic Album Art Palette extraction.
- **Font Scaling**: 4 typography presets (`COMPACT`, `BALANCED`, `COMFORTABLE`, `JUMBO`) with dynamic line heights.
- **Opacity Slider**: Adjustable from 20% to 100% background transparency.
- **Source Code**:
  - [`OverlayTheme.kt`](src/main/java/com/lyrics/floating/ui/theme/OverlayTheme.kt)
  - [`OverlayPreferences.kt`](src/main/java/com/lyrics/floating/data/preferences/OverlayPreferences.kt)
  - [`ThemeAndFontSettingsSheet.kt`](src/main/java/com/lyrics/floating/ui/overlay/ThemeAndFontSettingsSheet.kt)

### 2. Dual-Language Translation & Romanization
- Displays synchronized translation (English/custom language) or phonetic romanization (Romaji, Pinyin, Hangul) beneath each line.
- Native API translations (e.g. Netease `tlyric`) merged directly; fallback to on-device ML Kit / cloud translation.
- **Source Code**:
  - [`DualLanguageLyricLine.kt`](src/main/java/com/lyrics/floating/domain/translation/DualLanguageLyricLine.kt)
  - [`TranslationEngine.kt`](src/main/java/com/lyrics/floating/domain/translation/TranslationEngine.kt)

### 3. Intensive Fallback Sync Pipeline
- **Tier 1**: Word-by-word syllable karaoke timestamps.
- **Tier 2**: Standard millisecond `.lrc` lines.
- **Tier 3 (Heuristic Cadence Pacing)**: When only plain unsynced text exists, estimates speech cadence and line timestamps based on word density across track duration so lyrics scroll smoothly.
- **Tier 4**: Acoustic audio fingerprinting match (Chromaprint / AcoustID / ACRCloud).
- **Tier 5**: Instrumental audio visualizer mode.
- **Source Code**:
  - [`BiDirectionalSyncEngine.kt`](src/main/java/com/lyrics/floating/domain/sync/BiDirectionalSyncEngine.kt)

### 4. Bi-Directional Interactive Playback Control
- **Overlay to Player**:
  - Tapping **any lyric line** in the overlay jumps the active music player (Spotify, YT Music) straight to that exact millisecond!
  - Transport buttons: Play/Pause, Next, Previous, and `[-0.5s]` / `[+0.5s]` offset calibration.
- **Player to Overlay**:
  - Real-time `PlaybackStateCompat` updates with 30fps interpolation ticker.
- **Source Code**:
  - [`MediaSessionControllerBridge.kt`](src/main/java/com/lyrics/floating/service/media/MediaSessionControllerBridge.kt)
  - [`FloatingLyricsCardView.kt`](src/main/java/com/lyrics/floating/ui/overlay/FloatingLyricsCardView.kt)

---

## Future Development Roadmap

- **v1.0 (MVP Foundation)**: Core overlay, LRCLIB integration, notification listener.
- **v1.2 (Theming & Translation)**: Dynamic palettes, AMOLED black, ML Kit offline translation.
- **v1.5 (Bi-Directional Sync & History)**: Interactive scrub-to-seek, cadence pacing fallback, Room DB history, CSV export, Pano Scrobbler synergy.
- **v2.0 (Acoustic AI & Karaoke)**: Android 10+ `AudioPlaybackCapture`, Chromaprint NDK, syllable bouncing ball animation.
- **v2.5 (Ecosystem Expansion)**: WearOS smartwatch wrist companion, Android Auto dashboard display, social lyric poster generator.

---

## Project Structure

```
floating_lyrics_app/
├── docs/
│   ├── ARCHITECTURE_PLAN.md               # Master technical plan & specifications
│   └── LYRICS_SOURCES_GUIDE.md            # Deep dive on LRCLIB, Netease, Genius & Local LRC
├── src/main/java/com/lyrics/floating/
│   ├── data/
│   │   ├── preferences/
│   │   │   └── OverlayPreferences.kt      # DataStore preferences & settings
│   │   └── sources/
│   │       ├── LyricsSource.kt            # Base source interface & DTO
│   │       ├── LrclibSource.kt            # LRCLIB API client
│   │       ├── NeteaseSource.kt           # Netease 163 client (LRC + tlyric + romalrc)
│   │       ├── LocalLrcSource.kt          # Offline device .lrc file reader
│   │       └── LyricsAggregatorRepository.kt # Multi-source cascading & cache
│   ├── domain/
│   │   ├── sync/
│   │   │   └── BiDirectionalSyncEngine.kt # 5-tier fallback & seek engine
│   │   └── translation/
│   │       ├── DualLanguageLyricLine.kt   # Dual language data model
│   │       └── TranslationEngine.kt       # Multi-tier translation pipeline
│   ├── service/
│   │   └── media/
│   │       └── MediaSessionControllerBridge.kt # Bi-directional transport bridge
│   └── ui/
│       ├── overlay/
│       │   ├── FloatingLyricsCardView.kt  # Interactive Compose overlay
│       │   └── ThemeAndFontSettingsSheet.kt # Settings modal
│       └── theme/
│           └── OverlayTheme.kt            # Palettes, AMOLED, Neon, typography
├── scripts/                               # Helper scripts
└── README.md
```
