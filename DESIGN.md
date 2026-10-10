# DESIGN SYSTEM: SingAlong07

## 1. Visual Identity & Design Philosophy
- **Core Visual Metaphor**: "Crisp Vector Cockpit & Tactile Audio Companion"
- **Foundational Principles**:
  - **Zero Broken Text**: Every text element has explicit line limits, ellipsis behavior, or scroll wrappers.
  - **Zero Layout Clipping**: Fixed overlay window (`320dp × 440dp`) must never clip content; all sheets (Settings, Edit Search, Fallback) must have sticky headers and scrollable content bodies.
  - **Tactile High-Contrast Ergonomics**: Touch targets are minimum 32dp–40dp; active lines use distinct weight and scale (`scale: 1.04f`).
  - **Strict State Clarity**: Loading, Not Found, Synced, and Paced Plain states have distinctive visual iconography.
- **Dial Settings**:
  - `VARIANCE`: 4/10 (Consistent, disciplined component anatomy across all screens)
  - `MOTION`: 7/10 (Spring morphing between Pill and Card, smooth lyric scrolling, 30fps ticker)
  - `DENSITY`: 8/10 (Optimal information density engineered for floating multitasking)

---

## 2. Color Palette & Theming Tokens

### 1. AMOLED Dark (`OverlayThemeMode.AMOLED_DARK`)
- `--bg`: `#000000` (Pure pitch black for OLED power savings)
- `--surface`: `#141416`
- `--surface-alt`: `#1F2024`
- `--text-active`: `#FFFFFF`
- `--text-inactive`: `#7A7E85`
- `--accent`: `#1DB954` (Spotify Green)
- `--translation`: `#4E95FF` (Sapphire Blue)

### 2. Frosted Glass (`OverlayThemeMode.FROSTED_GLASS`)
- `--bg`: `#1A202C` (Deep slate navy with translucent blur)
- `--surface`: `#242D3D`
- `--surface-alt`: `#2E3A4E`
- `--text-active`: `#FFFFFF`
- `--text-inactive`: `#8E9AA8`
- `--accent`: `#60A5FA` (Sky Blue)
- `--translation`: `#A78BFA` (Lavender)

### 3. Neon Cyberpunk (`OverlayThemeMode.NEON_CYBERPUNK`)
- `--bg`: `#0A0017` (Deep synthwave violet)
- `--surface`: `#16032B`
- `--surface-alt`: `#240645`
- `--text-active`: `#00FFFF` (Electric Cyan)
- `--text-inactive`: `#A855F7` (Neon Purple)
- `--accent`: `#FF007F` (Neon Magenta)
- `--translation`: `#FFE600` (Electric Yellow)

### 4. Paper Light (`OverlayThemeMode.PAPER_LIGHT`)
- `--bg`: `#F8F9FA`
- `--surface`: `#FFFFFF`
- `--surface-alt`: `#ECEFF1`
- `--text-active`: `#1A1D20`
- `--text-inactive`: `#757575`
- `--accent`: `#FF6F00` (Warm Amber)
- `--translation`: `#00897B` (Teal)

---

## 3. Typography Hierarchy & Scales

| Role | Font Size | Line Height | Weight | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Active Lyric Line** | 16sp – 24sp | 22sp – 30sp | Bold (700) | Current singing line, scaled 1.04x |
| **Inactive Lyric Line**| 14sp – 20sp | 20sp – 26sp | Medium (500) | Surrounding lyrics, faded |
| **Secondary Translation** | 11sp – 14sp | 15sp – 18sp | Normal (400) | Dual-language & Romanized pinyin |
| **Card Header Title** | 14sp | 18sp | Bold (700) | Song title, single line, ellipsis |
| **Card Header Artist**| 11sp | 14sp | Normal (400) | Artist metadata, single line |
| **Status / Provider Badge** | 10sp | 12sp | SemiBold (600) | Sync tier and provider chip |
| **Input Field Text** | 12sp – 13sp| 16sp | Normal (400) | Edit search fields |
| **Action Button Label**| 11sp – 12sp| 14sp | Bold (700) | Retry, Search, Exit buttons |

---

## 4. Spacing, Radii & Layout Constraints

### Window Dimensional Constraints
- **Pill State**: `WRAP_CONTENT` (approx `170dp × 36dp`), pinned to top-start.
- **Card State**: Fixed `320dp × 440dp` with `20dp` rounded corners.
- **Companion Exit Ghost Button**: `38dp × 38dp`, circular, positioned at `x + 275dp, y - 6dp`.

### Spacing Scale
- `2dp`: Micro nudge / item gap
- `4dp`: Component internal gap
- `6dp`: Chip spacing & row gap
- `8dp`: Section separation
- `10dp`: Vertical item spacing
- `14dp`: Container edge padding

### Border Radii Tokens
- **Pill**: `24dp` (Full capsule)
- **Overlay Window**: `20dp – 22dp`
- **Settings & Edit Cards**: `20dp`
- **Buttons & Chips**: `8dp – 10dp`
- **Touch Target Circles**: `CircleShape` (32dp – 38dp)

---

## 5. Component Patterns & Negative Constraints

### Strict Rules:
1. **Never Let Ghost Mode Affect Pill or Cross Icon**:
   - The Pill must NEVER have `FLAG_NOT_TOUCHABLE`. It must remain 100% draggable and clickable at all times.
   - When Ghost Mode is activated in Card mode, touches pass through the lyrics body, but a companion `Close / Cross (✕)` icon must always be rendered with touchable flags so the user can exit ghost mode or close at any time.
2. **Never Overflow 320dp Width**:
   - Multi-option chips (Providers: Auto, JioSaavn, LRCLIB, KuGou, Netease, Lyrics.ovh) MUST be inside a `Row` with `.horizontalScroll(rememberScrollState())`.
   - Theme options MUST be placed in a 2×2 grid (`Row` of 2 + `Row` of 2), never 4 inline items.
3. **Never Clip 440dp Height**:
   - Any overlay sheet (Settings, Edit Search, Lyrics Not Found) MUST use a sticky top header and place the body inside `verticalScroll(rememberScrollState())`.
4. **Action Clarity**:
   - Always provide immediate visual feedback for provider switches and title searches.
