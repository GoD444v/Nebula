# Nebula — Change Log

## [v1.0.0] — 2026-10-03

**Streaming & Playback**
- YouTube Music search + streaming (songs, videos, albums, artists)
- Grouped results: Songs → Videos → Playlists → Albums → Artists headings
- Home feed with chips, cards, sections + per-section play/shuffle/repeat/download
- Personalized sections: genre picks, "Because you have X" (own playlists),
  "Because you like Y" (picked artists) — no account needed
- Background playback via Media3 MediaSessionService
- Mini player + full-sheet now-playing screen
- Repeat / shuffle, queue persistence across restarts (resumes paused)
- Shuffle-proof Previous (history stack) + notification Prev/Next buttons
- Start Radio (queue merge + dedup)
- Explicit badge + video-song filtering

**Downloads & Offline**
- Media3 DownloadService with foreground progress notification + cancel
- Download queue screen with active-count badge
- WiFi-only download mode (network-aware)
- Downloaded-songs playlist with play/shuffle
- Cache clearing

**Playlists & Library**
- Local-first playlists in Room (composite key, real migrations)
- Create / rename / delete playlists + unified Add window (New, Nebula,
  YouTube, Spotify, Others — no accounts)
- YouTube link import (anonymous), Nebula/Spotify/Others CSV import,
  pasted song lists
- CSV export + share from the detail overflow
- Full-app backup & restore (one JSON: playlists + all settings)
- Add-to-playlist sheet from song menu (Nebula-styled)
- Manual reorder + sort (custom / name / date added)
- Playlist covers via Coil with accent-tile fallback
- Albums browser + album detail pages
- Local on-device music scanning + playback
- Home visibility: section master switch + per-playlist toggles
  (Downloaded mirror off by default)

**Onboarding**
- 4 pages: genre picks, playlist import, live theme preview, favorite artists
- Each page persists immediately; Skip jumps to the end, never loops

**Lyrics**
- Multi-provider fan-out: Kpoe/LyricsPlus, Kugou (KRC), Unison (TTML), LRCLIB
- Tier-ranked best-pick with session cache
- Word-by-word timings where available
- Apple V2 default style; picker (highlight, alignment, font size, word-timings toggle)

**Customization**
- 6 theme palettes + custom palette editor with live preview
- Light / dark / system theme modes
- Reorderable bottom tabs (persisted)
- 3 switchable app icons (full density sets from real artwork)
- VoxMusic-inspired design system (3px borders, offset shadows, chunky components)

---
