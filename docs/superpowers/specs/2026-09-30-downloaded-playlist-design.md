# Downloaded Playlist — Design Spec

- **Date:** 2026-09-30
- **Status:** Awaiting review
- **Scope:** Feature 2 of 5. Features 1 (offline playback), 3 (live progress), 4 (playlist search) and 5 (bulk playlist download) are separate.
- **Reference:** `C:\Users\Vignesh\Desktop\app\music\Echo-Music-main` — conceptual only, no code copied.

---

## 1. Goal

A "Downloaded" playlist that always lists exactly what is on disk, and behaves like any other playlist: play, shuffle, repeat, rename, reorder.

## 2. What was already decided

- "Downloaded" is a **real playlist row**, kept in sync with the download index.
- It is a **system playlist**: undeletable, renameable.
- User-created playlists are deletable, and deleting one **never** touches downloaded songs.
- Removing a song from the Downloaded playlist **removes the download too**.
- Offline playback **already works** — verified on device 2026-09-30. The wiring is in `PlaybackService.kt:43-56` (downloadCache → playerCache → network), not in `PlayerViewModel`. **No work in this spec.**
- Live download progress is **not** pursued. See §7.

## 3. What the "Downloaded" playlist is

A row in the existing `playlists` table, marked `isSystem = 1`, kept in sync with Media3's download index. It reuses `PlaylistDao`, `PlaylistsViewModel` and the existing detail-screen patterns rather than introducing a parallel concept.

**Why a real row and not a live view.** A live view of the download index has no name and no order, so rename and reorder have nowhere to live. A row gives both for free through the columns that already exist.

**The cost, accepted.** Two representations of the same thing can disagree. §5 is the mechanism that keeps them agreeing.

## 4. Schema

One new column, `version = 2` → `3`:

```
ALTER TABLE playlists ADD COLUMN isSystem INTEGER NOT NULL DEFAULT 0
```

`MIGRATION_2_3` in `NebulaDatabase.kt`, `CREATE`-free, no data backfill — existing playlists get `0` (user playlists) automatically. `fallbackToDestructiveMigration()` stays off.

`isSystem` is the only schema change. No new table, no new column on `playlist_songs`.

## 5. Sync

Event-driven from the listener that already exists in `NebulaDownloads.build()` (`NebulaDownloads.kt:~128`), plus a reconcile pass on startup.

**Event-driven** — in `onDownloadChanged`, when `download.state == Download.STATE_COMPLETED`, upsert the song into the Downloaded playlist. In `onDownloadRemoved`, delete the row. This is sufficient: both events fire reliably, and neither depends on progress ticks (see §7).

**Reconcile on init** — after a crash or force-stop the listener can miss events, so on first access: read the download index, diff it against the playlist's rows, and repair. Repairs are two statements — insert songs present in the index but missing from the playlist, delete rows whose `videoId` is no longer in the index.

**Ensuring the row exists** — on init, if no `isSystem = 1` playlist exists, create one named "Downloaded". Done once; thereafter found by `isSystem`, never by name, so a rename does not break sync.

## 6. The gap that must be closed first

`FullSheetPlayer.kt:293` calls `NebulaDownloads.enqueue(vm.currentVideoId, vm.currentSongTitle)`. **Artist and artwork are discarded at enqueue time.** A Downloaded playlist built from that would show titles with empty artists permanently, because the download index never had them.

`enqueue` therefore takes a `SearchResult` instead of a bare `videoId` + `title`, and the playlist rows are written with real title, artist and thumbnail.

**Both call sites must be updated** — there are two, and the second is easy to miss:

- `FullSheetPlayer.kt:293` — the production caller
- `DownloadRequestKeyTest.kt:36` — the test added with the cache-key fix; it would fail to compile on a signature change

A test breaking is a useful signal here, not a nuisance: it proves the signature change is load-bearing.

## 7. Why live progress is out of scope

`DownloadManager.updateProgress()` runs every 5000ms and writes to `downloadIndex`, but **never posts `MSG_DOWNLOAD_UPDATE`**, so no listener is called. `onDownloadChanged` fires on state transitions only — start and completion — and not in between. Separately, `Download` has no `equals()`, so a `StateFlow<Map<String, Download>>` cannot detect a same-object mutation even if one were emitted.

Both causes are outside Nebula's code. The badge therefore updates on start and finish and does not climb. The playlist does not care: it needs completions and removals, and both arrive as events.

Live progress would require polling the index on a timer, the way `rehydrateDownloads()` already does. Deferred by decision, not oversight.

## 8. Screen

Renders the Downloaded playlist through the same detail-screen shape as any other playlist:

- **Play** → `playAll(items)` on `PlayerViewModel`, already implemented
- **Shuffle** → `toggleShuffle()`, already implemented
- **Repeat** → the existing repeat-mode control, already implemented
- **Rename** → `PlaylistsViewModel.rename`, already implemented
- **Reorder** → `updatePosition` on `PlaylistDao`, already implemented. The **drag gesture is entirely new**: this project has no `pointerInput`, `detectDragGestures`, or reorderable list anywhere, so nothing can be reused. It is the largest single piece of new UI in this spec.

**Not offered on this playlist:** delete, and remove-song-means-remove-download. Removal goes through the download queue screen, where the consequence is explicit. The playlist's own overflow menu omits Delete when `isSystem` is true, and omitting it is safer than showing a disabled control.

## 9. Component boundaries

| Unit | Responsibility | Depends on |
|---|---|---|
| `DownloadedPlaylistSync` (new) | Listen for completions/removals, reconcile on init | `DownloadManager.Listener`, `PlaylistDao` |
| `PlaylistsViewModel` (edit) | Expose the Downloaded playlist, honour `isSystem` on delete | `PlaylistDao` |
| `DownloadedPlaylistScreen` (new) | Render and drive one playlist | `PlaylistsViewModel`, `PlayerViewModel` |
| `NebulaDownloads` (edit) | `enqueue` takes a `SearchResult` | none |

The sync lives in its own file rather than inside `NebulaDownloads` or the ViewModel, so it can be tested without a device and so the listener registration is in one readable place.

## 10. Testing

- **Sync, instrumented.** A completed download appears in the playlist; a removed download disappears; reconcile-after-missed-events repairs a deliberately desynced playlist.
- **`isSystem`, instrumented.** A `version 2` database migrates to `3` with existing playlists still `0`; the Downloaded row is created once and not duplicated on a second init; a rename does not cause a second row.
- **Delete guard, unit.** Deleting an `isSystem` playlist is refused; deleting a user playlist is allowed and leaves downloads alone.
- **Reorder, unit.** `updatePosition` renumbers without gaps.
- **Regression.** All 19 existing instrumented tests and all unit tests stay green.

## 11. Acceptance criteria

1. Opening Playlists shows a "Downloaded" playlist alongside user playlists.
2. Downloading a song and letting it finish adds it to that playlist.
3. Removing the download from the queue screen removes it from the playlist.
4. Renaming the playlist works, and it still syncs afterwards.
5. The rename survives an app restart — sync keys on `isSystem`, not on the name.
6. Play plays the downloaded songs in order; shuffle and repeat work.
7. Reordering changes playback order.
8. The playlist has no Delete option.
9. Deleting a **user** playlist leaves every downloaded song intact.
10. Killing the app mid-download and reopening leaves the playlist matching the download index.
11. Every downloaded song shows its real artist and artwork.
12. No existing test regresses.

## 12. Risks

- **Desync.** Two sources of truth. Mitigated by reconcile-on-init, and every user-visible action goes through the listener, so drift can only come from a process kill — which reconcile covers.
- **Renaming a user playlist to "Downloaded"** could confuse a human but is harmless, because sync looks up `isSystem`, never the name.
- **The `enqueue` signature change** touches `FullSheetPlayer.kt:293`, the only call site. Anything else calling it must be found and updated.
- **Reorder is presentation-only.** Media3's download queue has its own order; reordering the playlist does not reorder pending downloads. Stated so it is not mistaken for a bug later.
- **Drag-to-reorder is unproven complexity.** No gesture infrastructure exists here. If it proves fiddly, up/down buttons achieve the same acceptance criterion at a fraction of the risk — that fallback is allowed, not a failure.
