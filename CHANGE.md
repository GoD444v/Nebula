# Nebula — Change Log


## [v2.0.0] — 2026-10-19

### What's new

- **Playback & Player**
  - Crossfade switch (Settings → Playback): last 3s fade out, next song fades in over 3s (opt-in, off by default).
  - System Equalizer studio (full-screen, Vox-style): 6 house presets, vertical faders with 3D knobs, spatial FX (surround + reverb); persists across restarts.
  - Volume as tap-to-reveal card below Download; drives device MEDIA volume, syncs with hardware buttons via ContentObserver.
  - Swipe-down-to-dismiss full player (100dp threshold / 1200px/s flick, spring-back otherwise).
  - Player art at full resolution: `maxresdefault.jpg` (1280×720) leads, queue thumbnail fallback; `ArtImage` falls forward on Coil `error`. New `artUrls()` + `ArtUrlsTest` (5 cases).
  - Local song art now loads (MediaStore uri first, YouTube URL fallback).
  - Lyrics in the thumbnail slot: tapping Lyrics fades 280dp art out and lyrics in (360dp), Echo-style; duplicate below-controls card removed.
  - Lyrics styles now animate on line-level lyrics via `WordTiming` (proportional word split; kana/Han per-char).
  - Korean romanization (Revised Romanization, no dictionary) + on-device English translation (ML Kit, auto-detects script).
  - Lyrics provider registry: on/off toggles + priority reordering in Settings; new YouTube Music lyrics provider.
  - Blacklist: Block/Unblock song row in every 3-dot menu; hidden from Search & Home; Settings → Blocked songs manager.
  - Pause history switch; save whole queue as playlist (💾 in Up Next).
  - Shuffle/repeat are per-scope (the playlist you're playing), not global; Shuffle now actually starts playback at a random song. Play all / Shuffle / Repeat on Liked songs, albums, artists, Local.
  - Autoplay wraps the queue (off = stop); lone song + Autoplay starts radio; radio top-ups itself 5 songs from the end.
  - Up Next queue sheet with highlighted playing row; optional remove-from-queue.
  - MiniPlayer: previous button added; queue-list icon removed.
- **Local music**
  - Tile landing (Songs / Albums / Artists / Folders) with live counts; layered back (button == gesture).
  - Folders view (browse by folder) + Album artists (ALBUM_ARTIST tag, majority-artist grouping for tag-less albums).
  - Sort in a 3-dot menu (Name/Artist/Album/Date updated + direction, persisted).
  - Per-view search wording ("Search songs/albums/artists/folders"); tighter controls gap.
  - Pull-to-refresh rescan; artwork via Coil + letter fallback.
- **Playlists & Library**
  - Delete created playlist from its own screen (confirm dialog).
  - YouTube playlists: overflow top-right, sort actually re-sorts, remove/add songs, "Save a local copy".
  - Bulk Download all (confirm dialog, dedup, skip already-downloaded) on playlist/album/liked/Home.
  - Speed dial: pin playlists to Home top row (DB-backed, JOINs live name/count).
  - Play next / Add to queue for whole playlists.
  - Playlists tab scrollable, dedup "liked" entries, reordered (Liked → Local → Downloaded → YouTube → user).
  - Liked songs artwork fix (`unwrapHeader`, 3 levels).
  - Account playlists via cookie login; "YouTube liked songs" card.
  - Genres cleared on connect/sign-out.
- **Home & Search**
  - Echo-style horizontal layout toggle: QuickPicks hero carousel (5s auto-scroll), 4-row song grid, mixed cards.
  - Load-more (continuation) infinite scroll.
  - Listen-together button in header.
  - Blocked-song filtering in Home/Search.
  - Search history (recent searches) + Play history screen (date-grouped).
  - View artist / View album / Song info / Refetch wired up.
  - Shuffle/Share icons visible on dark palettes; playlist header shadow slab fixed.
- **Settings & Auth**
  - Settings hub + sub-screens with slide animation + swipe-back gesture.
  - Cookie-based login (replaces Google Sign-In): WebView + paste fallback; cookie persistence fixed.
  - Blocked songs manager; Equalizer moved out of Settings into player.
  - In-app update checker (GitHub releases, auto-check).
  - App icon switcher (3 icons, Reset launcher icon).
- **Listen Together**
  - Wire format matched to server (Protobuf/JSON dialect); host approval UI.
  - Guest plays host's song on join; room survives screen re-entry; host track changes broadcast.
  - Listen Together button wired in all 3-dot menus.
  - Guest crash fix (wrong thread).
- **Downloads**
  - Download notification "Cancel" rename; bulk download all.

### Fixes
- App crash on every song play (StackOverflowError in `ChunkedDataSource.chunked()` self-recursion) — one-line fix + test.
- Songs failing at 0:00–0:00 (SABR/HLS guard, header injection, expiresAt cache, `DefaultDataSource` upstream for `content://`).
- Audio stalls on first play (150ms load control, buffering state, `StallWatchdog`, 1MB chunked source).
- Local songs never played (MediaStore content URI instead of deprecated DATA path; `DefaultDataSource`).
- Playlist sort never re-sorted (shadowed `sortType`/`descending` locals).
- Thumbnail flat (border drawn over photo; offset extrusion added).
- Hide Lyrics ran 1000ms vs Show's 500ms (keyed off `artAlpha`) — now keyed off `showLyrics`.
- Shuffle/Share icons invisible on dark palettes (icon ink follows accent luminance).
- Playlist header black slab (shadow sized to slot, pill content-sized) — shadow now `drawBehind` on the same Box.
- Account playlists blank artwork + raw browseId title (`unwrapHeader`, 3 levels).
- Cookie persistence lost on restart (`save`/`clear` now `suspend` and awaited).
- Duplicate "Liked songs" entry (renamed to "YouTube liked songs").
- Lyrics styles did nothing on some songs (line-level lyrics now get approximate word timings).
- Korean romanizer liquid-coda + coda-cluster defects fixed.
- LRCLIB crash on error object response.
- YouTube Music lyrics provider dead (missing `ContentNegotiation`) — fixed.
- Full-screen lyrics button solid black (extrusion box removed).
- MiniPlayer thumb left at `mqdefault` (320px, fine at that size).
- Settings refactor hub/sub-screens; About unchanged.


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
