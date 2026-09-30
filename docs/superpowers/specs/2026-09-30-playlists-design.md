# Playlists — Design Spec

- **Date:** 2026-09-30
- **Status:** Awaiting review
- **Scope decision:** Local-first. Playlists are created and stored in Nebula's own database. YouTube Music sync is explicitly deferred.

---

## 1. Goal

Turn the existing playlist mockup into a working playlists feature. A user can create a playlist, add songs to it, play it in order, and manage the queue.

## 2. Scope decision and what it costs

This is a **local-first** build. That decision is recorded here so it is not a surprise later.

A local playlist has no YouTube Music identity. It is not in the user's YTM library, does not appear on other devices, and cannot be shared back to YouTube. Liked Songs and the other auto-playlists are not reachable. Every playlist starts empty and is filled by hand.

The tradeoff accepted: the feature is buildable and testable today with no login, no network dependency, and no private-API breakage risk. A YouTube Music sync layer can be added later as a nullable `browseId` column plus a repository — it does not require rewriting the schema.

## 3. Data model

Two tables. Both new. `LocalSong` (`local_songs`) is untouched and unrelated — playlist entries are YouTube video references, not device files.

### `playlists`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | `autoGenerate = true` — matches the existing `LocalSong` pattern |
| `name` | TEXT | not blank |
| `thumbnailUrl` | TEXT? | null until a song is added |
| `createdAt` | INTEGER | epoch millis |
| `lastUpdatedAt` | INTEGER | epoch millis |

**Why `id` is not `browseId`.** The earlier draft used `browseId TEXT` as the primary key, which is correct only for a remote-first build — a local playlist has no YouTube ID. Using an `autoGenerate` Long matches `LocalSong.id` (`app/src/main/java/com/example/nebula/data/db/entities/LocalSong.kt:8`) and keeps the entity consistent with the rest of the database. A nullable `browseId` can be added in a later migration as a single nullable column; making it the primary key now would mean rewriting the primary key later, which is a table rebuild.

### `playlist_songs`

| Column | Type | Notes |
|---|---|---|
| `playlistId` | INTEGER | part of composite PK, FK → `playlists.id` `ON DELETE CASCADE` |
| `videoId` | TEXT | part of composite PK |
| `title` | TEXT | denormalized, see below |
| `artist` | TEXT | denormalized |
| `thumbnailUrl` | TEXT? | denormalized |
| `durationMs` | INTEGER? | denormalized, nullable |
| `position` | INTEGER | manual ordering, gap-free 0..n-1 |
| `addedAt` | INTEGER | epoch millis |

Primary key: `(playlistId, videoId)`. Index on `playlistId`.

**Why metadata is denormalized here.** The detail screen must render instantly and work offline. If only `videoId` were stored, opening a playlist would require a network round-trip per track to resolve titles. A playlist is a snapshot of "these songs as they were when I added them," which is a reasonable semantic for a local list, and it means the detail screen needs no join and no network.

The cost is duplicated metadata when a video appears in several playlists. Accepted — a few hundred bytes per row. If dedup ever matters it is a migration, not a redesign.

## 4. Migration

`NebulaDatabase` goes `version = 1` → `version = 2`, with `MIGRATION_1_2` defined in `NebulaDatabase.kt` (the existing single-file layout — no new `Migrations.kt`).

- Two `CREATE TABLE IF NOT EXISTS` statements, one `CREATE INDEX IF NOT EXISTS`
- **`fallbackToDestructiveMigration()` is not used.** It would silently wipe the user's existing `local_songs` rows.
- `exportSchema` stays `false`. Flipping it to `true` without configuring a schema location produces Room warnings and adds a schema directory to maintain.
- **No database file backup inside the migration.** Backups on every migration grow without bound and have no cleanup policy. The `build/` and `app/` outputs are already disposable; the database is not committed and should not be.

## 5. Layers

Follows Nebula's existing conventions, not Echo's. No Hilt, no NavHost, no new Gradle module.

| File | Role |
|---|---|
| `data/db/entities/PlaylistEntity.kt` | new — the `playlists` table |
| `data/db/entities/PlaylistSongEntity.kt` | new — the `playlist_songs` table |
| `data/db/dao/PlaylistDao.kt` | new — `Flow`-returning DAO |
| `data/PlaylistRepository.kt` | new — thin layer over the DAO |
| `viewmodel/PlaylistsViewModel.kt` | new — `AndroidViewModel`, holds the list state |
| `viewmodel/PlaylistDetailViewModel.kt` | new — `AndroidViewModel`, holds one playlist's songs |

`AndroidViewModel` is used because the DAO needs a `Context` to reach `NebulaDatabase.getDatabase(app)`, and it is the standard way to get one. `HomeViewModel` is a plain `ViewModel` because its repository needs no context.

### DAO surface

Read:
- `observePlaylists(): Flow<List<PlaylistWithCount>>` — `songCount` computed in SQL via a correlated subquery, not in Kotlin
- `observeSongs(playlistId): Flow<List<PlaylistSongEntity>>` — `ORDER BY position ASC`
- `observePlaylist(id): Flow<PlaylistEntity?>`

Write:
- `insert(playlist): Long`
- `updateName(id, name)`
- `delete(id)`
- `insertSongs(songs)` — `OnConflictStrategy.IGNORE` so re-adding is a no-op
- `removeSong(playlistId, videoId)`
- `updatePosition(playlistId, videoId, position)`
- `nextPosition(playlistId): Int` — `MAX(position) + 1`, or `0` when empty

## 6. Screens

### `PlaylistsScreen.kt` — edited, not new

The VoxMusic design is kept **verbatim**: the 3D offset-shadow banner, the `N PLAYLISTS` pill, `PlaylistCard`'s 3px black border and offset shadow, the accent-colored tile. Only the data source changes — `stubPlaylists` is deleted and the list reads `PlaylistsViewModel`.

The accent tile becomes a real thumbnail via Coil when one exists, falling back to the existing `QueueMusic` icon.

Three distinct states, because empty and not-yet-created are different situations:
1. **No playlists** — a "Create your first playlist" prompt
2. **Playlists exist** — the list
3. **Loading** — a spinner, not an empty list

### `PlaylistDetailScreen.kt` — new

Cover, name, song count, a Play button and a Shuffle button, then a `LazyColumn` of songs. Long-press a song for a menu: play next, add to queue, remove from playlist.

### `QueueScreen.kt` — new

Reads `queueList` and `queueIndex` from `PlayerViewModel`. Highlights the current track, tap to jump, remove to drop a track. Reordering the queue is out of scope for v1.

### `AddToPlaylistDialog.kt` — new

A sheet listing the user's playlists with a create-new field at the top. Appears from a long-press "Add to playlist" action.

**How songs get into a playlist.** This is load-bearing for a local-first build and the earlier draft left it unanswered, which would have produced an unusable feature. The answer is an "Add to playlist" long-press action on song rows that already exist in the app — search results and album detail. This is cheaper than building a dedicated song-picker search dialog and covers the real use case, because the user is already looking at the song.

### `SettingsScreen.kt` — unchanged

No login section. Remote-first was chosen, then local-first. There is nothing to configure, so nothing is added.

## 7. Player integration

`PlayerViewModel` already holds `queue: MutableList<SearchResult>`, `queueIndex`, `upNext`, and delegates advance decisions to the pure, already-unit-tested `PlaybackModes.next()` (`app/src/main/java/com/example/nebula/player/PlaybackModes.kt`). `next()`, `previous()`, `handleAutoAdvance()`, shuffle, and repeat all work today.

**Three functions are added. `PlayerManager` is not modified.**

```
fun playPlaylist(songs: List<SearchResult>, startIndex: Int = 0)
fun jumpTo(index: Int)
fun removeAt(index: Int)
```

`playPlaylist` sets `queue`, sets `queueIndex`, and calls the existing `resolveAndPlay`. `jumpTo` and `removeAt` do not exist yet — the earlier draft referenced a `jumpTo` that was never written, so it is listed here as new work.

Because the queue is Nebula-side bookkeeping and each advance re-calls `resolveAndPlay`, Media3's own queue is never used. `PlayerManager` keeps calling `setMediaItem` for a single track.

## 8. Navigation

No NavHost. `MainActivity` extends its existing state: a `playlistId: String?` alongside the existing `detailId`, plus a `"playlist"` screen branch. `BackHandler` at `MainActivity.kt:158` extends to clear `playlistId` before falling back to the tab.

The queue screen opens as a sheet or a full-screen branch, whichever matches how `FullSheetPlayer` is already presented.

## 9. What is not being built

Remote YouTube Music sync and login · Hilt · NavHost · a `core` Gradle module · AI playlist generation · Spotify import · M3U import · home-screen widgets · the six auto-playlists · `Collator` locale-aware sorting · the `position <= 3` preview view · multi-cover collages · queue reordering · separate `PlaylistEntity`/`PlaylistSong` files beyond the two above.

## 10. Waves

| Wave | Work | Visible outcome |
|---|---|---|
| 0 | Entities, DAO, `MIGRATION_1_2`, migration test | Nothing. Foundation only. |
| 1 | `PlaylistsViewModel`, `PlaylistsScreen` rewired, create/rename/delete | Playlists tab works, empty state correct |
| 2 | `PlaylistDetailScreen`, `PlaylistDetailViewModel`, `playPlaylist` | Play a playlist end to end |
| 3 | `QueueScreen`, `jumpTo`, `removeAt` | Queue is visible and editable |
| 4 | `AddToPlaylistDialog` wired into search and album detail | Songs can be added to a playlist |

Wave 0 ships nothing visible. That is intentional — the migration is the only risky step and it is cheapest to land alone.

## 11. Tests

- **Migration, instrumented.** Requires a v1 database fixture. None exists in the repo — it must be created as a test asset by hand. Asserts both tables exist and that pre-existing `local_songs` rows survive. This is a task, not a line item.
- **DAO, instrumented.** Insert a playlist, add songs, assert `songCount` from the subquery matches, assert ordering, assert `ON DELETE CASCADE` removes songs.
- **ViewModel, unit.** Create/rename/delete against a fake DAO, in the style of the existing `HomeFeedTest.kt` and `PlaybackModeTest.kt` in `app/src/test/java/com/example/nebula/`.
- **Queue, unit.** `playPlaylist` sets `queueIndex`; `jumpTo` clamps out-of-range indices instead of throwing.

## 12. Acceptance criteria

Each is falsifiable — there is a way for it to fail.

1. A v1 database migrates to v2 without crashing, and existing `local_songs` rows are still present afterwards.
2. With zero playlists, the Playlists tab shows a create prompt — not a blank list.
3. Creating a playlist named "Road Trip" makes it appear in the list with a count of 0.
4. Adding three songs makes the count read 3.
5. Tapping a playlist opens its detail screen showing all three song titles.
6. Tapping Play queues all three in order, and the first track starts.
7. Tapping the last track's next advances past the end without crashing.
8. Deleting a playlist removes it from the list and its songs are gone from the database.
9. Long-pressing a search result offers "Add to playlist" and the song lands in the chosen playlist.
10. The existing VoxMusic visuals — banner, pill, card borders, offset shadows — are unchanged.

## 13. Risks

- **Room schema change on an unshipped app.** Low. `versionCode = 1`, so there is no installed user base. The migration still needs testing because a dev device may hold data.
- **Position renumbering.** Removing a song from the middle leaves a gap. `nextPosition` uses `MAX(position) + 1`, so new songs append correctly, but existing positions should be renumbered on removal. Handled inside a transaction.
- **Denormalized metadata goes stale.** A retitled video keeps its old title in the playlist. Accepted for a local snapshot.
- **No undo.** Deleting a playlist is immediate. If that proves annoying, a snackbar with undo is a small follow-up.
