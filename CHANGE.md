# Nebula — Change Log

## [v0.4.0] — 2026-09-30

### [In Progress] Playlists — spec + implementation plan written, awaiting review
- `docs/superpowers/specs/2026-09-30-playlists-design.md` — design spec
- `docs/superpowers/plans/2026-09-30-playlists-local-store.md` — Plan A (waves 0-1: the store)
- Scope: **local-first**. Playlists in Nebula's own Room DB. YouTube Music sync deferred.
- Replaces the `stubPlaylists` mockup in `PlaylistsScreen.kt` (no ViewModel, no DB, `onClick = {}`)
- Plan: 2 new tables (`playlists`, `playlist_songs`), `version 1 → 2` with a real migration
- Reuses existing player queue in `PlayerViewModel` — `PlayerManager` untouched
- No Hilt, no NavHost, no new Gradle module, no new dependencies
- Full VoxMusic visuals on `PlaylistsScreen` kept verbatim; only the data source changes
- Plan B (waves 2-4: detail screen, queue screen, add-to-playlist) not yet specced
- **Blocker:** repo has no `.git`. Plan Task 0 runs `git init` — commit steps and the
  final review range both depend on it.
- Nothing implemented yet. Plan awaits review before any code is written.

---

## [v0.3.0] — 2026-09-30

### [In Progress] Bug 1: Downloads Tab Crash
- Moved `dm.downloadIndex.getDownloads()` from `build()` to `rehydrateDownloads()` on `Dispatchers.IO`
- `NebulaDownloads.kt`: DB rehydration now runs on background thread via `rehydrateScope.launch {}`
- Fixes main-thread crash when opening Downloads tab

### [In Progress] Bug 2: Download Fails on Mobile Data
- Added `ACCESS_NETWORK_STATE` permission to `AndroidManifest.xml`
- Added `NetworkConnectivityObserver` class using `ConnectivityManager.NetworkCallback`
- Added `DownloadPrefs` object with DataStore persistence for WiFi-only setting
- Added `observeNetworkState()` to reactively update `DownloadManager.requirements`
- Added WiFi-only toggle in `SettingsScreen.kt`
- Follows Echo-Music's pattern for network-aware downloads

### [In Progress] Bug 3: Icon1 Shows Android Default Logo
- Changed `ic_icon_foreground_clear.xml` from `<shape>` to `<vector>` drawable
- `<shape>` doesn't render correctly as adaptive icon foreground — causes fallback to Android robot
- `<vector>` with same transparent fill renders correctly

### [In Progress] Bug 4: Auto-Play on App Reopen
- `PlayerViewModel.kt`: `restoreNowPlaying()` no longer calls `resolveAndPlay()`
- Added `savedPositionMs` field to store position for when user presses play
- `togglePlay()` now resumes from `savedPositionMs` when player has no media item
- Follows Echo-Music's pattern: restore queue but stay paused

### [In Progress] Bug 5: Nav Bar Customization Resets to Default
- Added DataStore persistence for tab order (`nebula_nav`)
- `MainActivity.kt`: reads saved tab order on launch via `LaunchedEffect`, writes on `onSave`
- Tab order stored as comma-separated string under `tab_order` key

### [In Progress] Bug 6: Theme Uses SharedPreferences (Migrate to DataStore)
- Migrated `ThemeStore` from SharedPreferences to DataStore (`nebula_theme`)
- `ThemeStore.init()` reads from DataStore via `runBlocking`
- `setCustom()`, `setMode()`, `setPalette()` write to DataStore via `CoroutineScope(Dispatchers.IO)`
- Added `androidx.datastore:datastore-preferences:1.1.1` dependency
- API preserved — no composable changes needed

---

## [v0.2.0] — 2026-09-30

### [In Progress] Bug 5: Nav Bar Customization Resets to Default
- Added DataStore persistence for tab order (`nebula_nav`)
- `MainActivity.kt`: reads saved tab order on launch via `LaunchedEffect`, writes on `onSave`
- Tab order stored as comma-separated string under `tab_order` key

### [In Progress] Bug 6: Theme Uses SharedPreferences (Migrate to DataStore)
- Migrated `ThemeStore` from SharedPreferences to DataStore (`nebula_theme`)
- `ThemeStore.init()` reads from DataStore via `runBlocking`
- `setCustom()`, `setMode()`, `setPalette()` write to DataStore via `CoroutineScope(Dispatchers.IO)`
- Added `androidx.datastore:datastore-preferences:1.1.1` dependency
- API preserved — no composable changes needed

---

## [v0.1.0] — 2026-09-01

### [Done] Initial project setup
- Jetpack Compose + Material 3 base
- VoxMusic-inspired design system (6 palettes + custom palette)
- Bottom navigation with tab customization
- Settings screen with theme/palette picker
