# Nebula — Design

## 1. Product overview
Native Android YouTube Music client (`nebula.music`). Ad-free streaming via
InnerTube, synced lyrics, offline downloads, local-first playlists, local
on-device audio. No accounts, no servers. Early development — see
[CHANGE.md](CHANGE.md); APKs ship via GitHub Releases.

Non-goals (v1): account sync, podcasts, crossfade.

## 2. Architecture — MVVM, no DI
No Hilt, no NavHost, no serialization-plugin. Tab state lives in
`MainActivity`; screens are Compose + `StateFlow` viewmodels; singletons are
hand-rolled (`NebulaApplication`, `NebulaDatabase.getDatabase`,
`ThemeStore`/`OnboardingStore`/`HomePrefs` init-once pattern:
`mutableStateOf` + `runBlocking first()` in `MainActivity.onCreate` +
`scope.launch` edit setters).

```
MainActivity (tabs, stores init)
 ├─ HomeViewModel / SearchViewModel / PlaylistsViewModel / PlayerViewModel / DownloadViewModel
 ├─ data: SearchRepository, LyricsRepository, BackupManager, PlaylistCsv, HomePrefs
 ├─ data/db: Room v3 (LocalSong, PlaylistEntity, PlaylistSongEntity)
 ├─ data/download: NebulaDownloads (Media3 DownloadService wrapper)
 └─ player: PlaybackService (MediaSessionService) + PlayerManager + NebulaDownloadService
```

Failure policy: network/personalized fetches fail to empty sections, never
block; toasts, never crashes; malformed import files → null + toast.

## 3. File map
```
app/src/main/java/nebula/music/
  MainActivity.kt, NebulaApplication.kt
  player/         PlaybackService.kt, PlayerManager.kt, PlaybackModes.kt, NebulaDownloadService.kt
  viewmodel/      Player/Home/Search/Playlists/Download viewmodels
  data/           SearchRepository.kt, LyricsRepository.kt, BackupManager.kt,
                  PlaylistCsv.kt, HomePrefs.kt, LocalMusicRepository.kt,
                  models/ (SearchResult, HomeFeed, Lyrics, LyricsStyle, SongStats)
                  lyrics/ (KpoeLyrics, KugouLyrics, UnisonLyrics)
                  download/ (NebulaDownloads, DownloadedPlaylistSync, SongCodec, DownloadPrefs)
                  db/ NebulaDatabase.kt, dao/ (LocalSongDao, PlaylistDao),
                      entities/ (LocalSong, PlaylistEntity, PlaylistSongEntity)
  ui/screens/     Home, Search, Playlists, AlbumDetail/AlbumsScreen, Settings,
                  Onboarding, FullSheetPlayer, NowPlaying, LyricsScreen,
                  LyricsStylePicker, DownloadedPlaylistScreen,
                  download/DownloadQueueScreen, TabCustomizerScreen, PlaylistSort.kt
  ui/components/  ChunkyWindow.kt, ChunkyAction (in ChunkyWindow), MiniPlayer,
                  SongMenuSheet, SongInfoDialog, AddToPlaylistDialog, ImmersiveMode
  ui/theme/       Color.kt, Theme.kt (ThemeStore, palettes), OnboardingStore.kt
app/src/main/res/ mipmap-*/ic_icon{1,2,3}.png, drawable-xxxhdpi/ic_icon*_art.png,
                  drawable/ic_notification.xml, xml/ (backup_rules, data_extraction_rules)
assets/app-icons/ icon1-vortex.png, icon1.jpg, icon2.jpeg, icon3.jpeg
```

## 4. Design system (VoxMusic-inspired)
- Backgrounds: Cream `#FFF6E9` light / AMOLED `#000000` dark (never `#121212`).
- Idiom is law: 3px `BorderBlack`, offset shadow (4–6dp black box behind),
  16–24dp corners, `ChunkyWindow`/`ChunkyAction`. Read colors from palette
  (`pal.*`) — never hardcode cream/pink/white ink (all three caused real bugs).
- Accents only: Neon Pink `#FF6B9D`, Yellow `#FFC700`, Teal `#00D4AA`.
  No dynamic color. 6 palettes + custom builder; light/dark/system modes.
- NowPlaying signatures: big art + pink glow pulse, yellow slanted title
  banner, chunky 3D buttons, bottom nav.

## 5. Data layer
Room `NebulaDatabase` v3, name `nebula_database`, no destructive migration:
- `local_songs` (scanned on-device tracks)
- `playlists` (`id, name, thumbnailUrl, createdAt, lastUpdatedAt, isSystem`;
  v2→v3 adds `isSystem DEFAULT 0`; Downloaded mirror is the system row)
- `playlist_songs` (composite key `playlistId+videoId`, `position`, `addedAt`,
  `CASCADE` delete; reorder = up/down arrows, sort = custom/name/date-added)

DataStores (fixed names, package-rename safe): `nebula_theme`,
`nebula_onboarding`, `home_prefs`, plus download prefs. Queue persists across
restarts, resumes paused, never auto-plays.

## 6. Playback & downloads
- `PlaybackService` = `MediaSessionService` + ExoPlayer, 3-layer read:
  downloadCache (read-only) → playerCache (read/write, `FLAG_BLOCK_ON_CACHE`)
  → network. Downloaded songs play from disk with no localUri branch.
- Shuffle-proof Previous via history stack. Timeline holds one item, so
  custom `nebula_prev`/`nebula_next` layout buttons bypass the timeline check.
- Notification small icon: white-note `ic_notification.xml`
  (`DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(...) }`
  — setter lives on the built provider in Media3 1.6.1, not the Builder).
- `NebulaDownloadService` (Media3 `DownloadService`, `dataSync` foreground
  type) with progress notification + cancel; WiFi-only mode; download queue
  screen with active-count badge.

Stack: Kotlin 2.4.10, Media3 1.6.1, Room 2.7.0, DataStore 1.1.1,
innertubex 0.7.1 (Ktor 3.5.2, serialization-json 1.11.0), Coil3 3.2.0,
minSdk 24.

## 7. Lyrics
Multi-provider fan-out with tier-ranked best-pick + session cache:
Kpoe/LyricsPlus, Kugou (KRC decode port), Unison (TTML parse), LRCLIB.
Word-by-word timings where available; Apple V2 default style; picker for
highlight/alignment/font-size/word-timings.

## 8. Search & home
Grouped results: Songs → Videos → Playlists → Albums → Artists. Home feed has
chips/cards/sections with per-section play/shuffle/repeat/download.
Personalized without accounts: genre picks (onboarding, Funk/Tamil/J-Pop
pinned first), "Because you have X" (own playlists), "Because you like Y"
(picked artists). Home visibility: master switch + per-playlist toggles
(Downloaded mirror off by default).

## 9. Import / export / backup formats
- YouTube URL import (anonymous playlist link), CSV import (Nebula/Spotify/
  Others shape), pasted `Artist - Title` tracklists (first search hit wins).
- Export: Echo-compatible CSV + plain-text share from detail overflow.
- Backup: one JSON via system picker — playlists + songs + genres + artists +
  theme + tabs + visibility. Full restore wipes and re-imports.

## 10. Attribution & license
License: GPL-3.0-or-later (`LICENSE`, SPDX headers). Full details: [NOTICE](NOTICE).
- innertubex (GPL-3.0): Gradle dependency, not vendored.
- VoxMusic palette/design (MIT): `licenses/VOXMUSIC-MIT.txt`.
- kugou-lyric KRC algorithm (ISC): `licenses/KUGOU-ISC.txt`.
- Echo-Music: pattern reference only, zero copied blocks (scan-proven).
- AndroidX/Media3/Room/DataStore/Coil/Ktor: Apache-2.0 deps.
