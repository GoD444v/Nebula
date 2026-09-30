# Downloaded Playlist — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Downloaded" playlist that always lists exactly what is on disk, with play, shuffle, repeat, rename and reorder.

**Architecture:** One new `isSystem` column on `playlists` (DB v2→v3). A `DownloadedPlaylistSync` class listens to the `DownloadManager.Listener` that already exists in `NebulaDownloads` and reconciles on init. Play/shuffle/repeat/rename/reorder all reuse existing code.

**Tech Stack:** Kotlin 2.4.10, Compose BOM 2024.12.01, Material 3, Room 2.7.0 (KSP), Media3 1.6.1.

**Spec:** `docs/superpowers/specs/2026-09-30-downloaded-playlist-design.md`

## Global Constraints

- Package root `com.example.nebula`. **Not** `com.nebula`, despite what `AGENTS.md` says.
- No Hilt, no NavHost, no new Gradle module, no new dependencies.
- The Downloaded playlist has **no Delete affordance at all** — omit the control rather than disable it.
- Removing a song from the Downloaded playlist removes the download. That action lives in the download queue screen, not the playlist screen.
- Sync keys on `isSystem = 1`, **never** on the playlist name, so a rename cannot break it.
- `fallbackToDestructiveMigration()` must not be used.
- `exportSchema` stays `false`.
- Live download progress is **out of scope** — Media3's `updateProgress()` never notifies listeners. Do not add polling.
- VoxMusic visuals on any new UI: 3px `BorderBlack` border, offset shadow, 16dp corners.
- Instrumented tests run on `ANDROID_SERIAL=d9f99d20` (API 34). Espresso cannot run on the API 37 emulator.

## Review Focus

- **A rename must not break sync** — the biggest silent failure. Test it.
- **Reconcile after a simulated crash** — a missed event must self-heal, or the playlist lies.
- **Deleting a user playlist must not touch downloads** — the acceptance criterion most likely to be quietly broken.
- **Blank playlist name on the system playlist** — `rename` rejects blanks; a system playlist that cannot be renamed is a dead end.
- **`enqueue` has two call sites** — `FullSheetPlayer.kt` and `DownloadRequestKeyTest.kt`. Missing the test one breaks the build.

---

### Task 1: `isSystem` column and the v2→v3 migration

**Files:**
- Modify: `app/src/main/java/com/example/nebula/data/db/entities/PlaylistEntity.kt`
- Modify: `app/src/main/java/com/example/nebula/data/db/NebulaDatabase.kt:17,42`
- Modify: `app/src/androidTest/java/com/example/nebula/PlaylistMigrationTest.kt`

**Interfaces:**
- Produces: `PlaylistEntity.isSystem: Boolean = false`; `MIGRATION_2_3`; `NebulaDatabase` at `version = 3`, builder registering both migrations.

- [ ] **Step 1: Extend the migration test with a v2→v3 case**

Add to `PlaylistMigrationTest.kt`. The existing `V1FixtureDatabase` is v1, so this needs a **v2 fixture**: a test-only `@Database(entities = [LocalSong::class, PlaylistEntity::class, PlaylistSongEntity::class], version = 2)` class. Seed it with one playlist and one song row, then open `NebulaDatabase` over the same file.

```kotlin
@Test fun migratesV2ToV3_addsIsSystemDefaultingToZero()
@Test fun migratesV2ToV3_preservesExistingPlaylistsAndSongs()
```

The first reads `PRAGMA table_info(playlists)`, asserts an `isSystem` column exists, and asserts a playlist seeded before migration now reads `isSystem = 0`. The second asserts the playlist name and its song row survive.

- [ ] **Step 2: Run to verify RED**

Run: `$env:ANDROID_SERIAL="d9f99d20"; .\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistMigrationTest"`

Expected: FAIL — `V2FixtureDatabase` does not exist.

- [ ] **Step 3: Add the column to the entity**

In `PlaylistEntity.kt` add one field, last, defaulted so no existing call site breaks:

```kotlin
val isSystem: Boolean = false
```

- [ ] **Step 4: Add MIGRATION_2_3 and bump the version**

In `NebulaDatabase.kt` change `version = 2` to `3`, add `MIGRATION_2_3` to the builder's `.addMigrations(...)` chain alongside `MIGRATION_1_2`, and define it as a single `ALTER TABLE` adding `isSystem INTEGER NOT NULL DEFAULT 0`. No backfill — the default handles existing rows.

- [ ] **Step 5: Run to verify GREEN**

Expected: PASS, 5/5 (3 existing + 2 new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/nebula/data/db app/src/androidTest/java/com/example/nebula/PlaylistMigrationTest.kt
git commit -m "feat(db): add isSystem column for the Downloaded playlist"
```

---

### Task 2: DAO support for the system playlist

**Files:**
- Modify: `app/src/main/java/com/example/nebula/data/db/dao/PlaylistDao.kt`
- Create: `app/src/androidTest/java/com/example/nebula/DownloadedPlaylistDaoTest.kt`

**Interfaces:**
- Consumes: `PlaylistEntity.isSystem` from Task 1
- Produces: `observeSystemPlaylist(): Flow<PlaylistEntity?>`, `getSystemPlaylistBlocking(): PlaylistEntity?`, `insertSystemPlaylist(name): Long`, `deleteSongsNotIn(videoIds: List<String>, playlistId: Long)`, `deleteSystemPlaylist()`. Plus `delete` now refuses system playlists.

- [ ] **Step 1: Write the failing DAO test**

`DownloadedPlaylistDaoTest.kt`, in-memory Room, `@Before` clearing both tables.

```kotlin
@Test fun insertSystemPlaylist_isFoundByBothSystemLookups()
@Test fun insertSystemPlaylist_doesNotDuplicateOnSecondCall()
@Test fun delete_refusesASystemPlaylist()
@Test fun delete_removesUserPlaylistAndItsSongs()
@Test fun deleteSongsNotIn_prunesOnlyRowsMissingFromTheIndex()
@Test fun rename_worksOnASystemPlaylist()
```

`delete_removesUserPlaylistAndItsSongs` is Review Focus: assert the playlist row is gone **and** its songs are gone, which the existing `ON DELETE CASCADE` provides.

`delete_refusesASystemPlaylist` is the guard: `delete(id)` on a system playlist must affect zero rows and leave the row present.

- [ ] **Step 2: Run to verify RED**

Expected: compile failure, members do not exist.

- [ ] **Step 3: Implement the DAO members**

Add exactly these to `PlaylistDao`:

- `observeSystemPlaylist(): Flow<PlaylistEntity?>` — `WHERE isSystem = 1 LIMIT 1`
- `getSystemPlaylistBlocking(): PlaylistEntity?` — same predicate, suspend
- `insertSystemPlaylist(name: String): Long` — inserts with `isSystem = true`
- `deleteSongsNotIn(videoIds: List<String>, playlistId: Long)` — `DELETE FROM playlist_songs WHERE playlistId = :playlistId AND videoId NOT IN (:videoIds)`. **Guard the empty-list case in Kotlin**: an empty `NOT IN ()` is invalid SQL, so when `videoIds` is empty delete every row for that playlist instead.
- Rewrite `delete` to `DELETE FROM playlists WHERE id = :id AND isSystem = 0` so the guard lives in SQL rather than only in the ViewModel.

`rename` needs no change — it already works on any id, and the system playlist must be renameable.

- [ ] **Step 4: Run to verify GREEN**

Expected: PASS, 6/6.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/nebula/data/db/dao app/src/androidTest/java/com/example/nebula/DownloadedPlaylistDaoTest.kt
git commit -m "feat(db): DAO support for the system Downloaded playlist"
```

---

### Task 3: Widen `enqueue` to keep artist and artwork

**Files:**
- Modify: `app/src/main/java/com/example/nebula/data/download/NebulaDownloads.kt:187`
- Modify: `app/src/main/java/com/example/nebula/ui/screens/FullSheetPlayer.kt:293`
- Modify: `app/src/androidTest/java/com/example/nebula/DownloadRequestKeyTest.kt:36`

**Interfaces:**
- Consumes: `SearchResult(videoId, title, artist, thumbnailUrl)`
- Produces: `NebulaDownloads.enqueue(song: SearchResult)` replacing `enqueue(videoId, title)`

- [ ] **Step 1: Update the existing test to the new signature**

`DownloadRequestKeyTest.kt` calls `enqueue(videoId, "Download key probe")`. Change it to build a `SearchResult` and call `enqueue(song)`, keeping its existing assertion on `request.customCacheKey` unchanged. **That assertion is the whole point of the test — do not weaken it.**

- [ ] **Step 2: Run to verify RED**

Expected: compile failure — `enqueue(SearchResult)` does not exist.

- [ ] **Step 3: Change the signature**

```kotlin
fun enqueue(song: SearchResult) {
    val request = DownloadRequest.Builder(song.videoId, Uri.parse(IDLE_URI_PREFIX + song.videoId))
        .setCustomCacheKey(song.videoId)
        .setData(song.title.toByteArray())
        .build()
    DownloadService.sendAddDownload(...)
}
```

`videoId` becomes `song.videoId` throughout. The cache-key fix from `d43023e` must survive — `setCustomCacheKey` stays.

- [ ] **Step 4: Update `FullSheetPlayer.kt:293`**

It currently passes `vm.currentVideoId, vm.currentSongTitle`. Check what artwork the player already holds; build a `SearchResult` from it. If no artwork is available there, pass `thumbnailUrl = ""` — a blank is honest, a wrong URL is not.

- [ ] **Step 5: Run to verify GREEN**

Run the `DownloadRequestKeyTest` class alone. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/nebula/data/download app/src/main/java/com/example/nebula/ui/screens/FullSheetPlayer.kt app/src/androidTest/java/com/example/nebula/DownloadRequestKeyTest.kt
git commit -m "refactor(download): enqueue takes a SearchResult so artist and art survive"
```

---

### Task 4: `DownloadedPlaylistSync`

**Files:**
- Create: `app/src/main/java/com/example/nebula/data/download/DownloadedPlaylistSync.kt`
- Modify: `app/src/main/java/com/example/nebula/data/download/NebulaDownloads.kt` (call `sync.reconcile()` from `init`)
- Create: `app/src/androidTest/java/com/example/nebula/DownloadedPlaylistSyncTest.kt`

**Interfaces:**
- Consumes: `PlaylistDao` from Task 2, `NebulaDownloads.downloads`, `NebulaDownloads.reconcileFromIndex()`
- Produces: `DownloadedPlaylistSync(dao, downloadIndex)` with `ensureExists()`, `onDownloadCompleted(song)`, `onDownloadRemoved(videoId)`, `reconcile()`

- [ ] **Step 1: Write the failing sync test**

`DownloadedPlaylistSyncTest.kt`, in-memory Room. A real `DownloadManager` needs a device and real files, so the sync class takes the **index's contents as a parameter** rather than reaching for Media3 itself — that makes it testable.

```kotlin
@Test fun ensureExists_createsExactlyOneSystemPlaylist()
@Test fun onDownloadCompleted_appendsTheSongWithItsArtistAndArtwork()
@Test fun onDownloadCompleted_isIdempotentForTheSameVideoId()
@Test fun onDownloadRemoved_dropsTheRow()
@Test fun reconcile_insertsSongsTheEventsMissed()
@Test fun reconcile_deletesRowsWhoseDownloadIsGone()
@Test fun reconcile_survivesAnEmptyDownloadIndex()
@Test fun rename_thenReconcile_stillSyncs()   // Review Focus
```

`rename_thenReconcile_stillSyncs` renames the system playlist to something else, runs `reconcile()`, and asserts it still added the song — proving sync keys on `isSystem`, not the name.

- [ ] **Step 2: Run to verify RED**

Expected: compile failure — the class does not exist.

- [ ] **Step 3: Implement `DownloadedPlaylistSync`**

```kotlin
class DownloadedPlaylistSync(private val dao: PlaylistDao) {
    suspend fun ensureExists() {
        if (dao.getSystemPlaylistBlocking() == null) {
            dao.insertSystemPlaylist(DEFAULT_NAME)
        }
    }
    suspend fun onCompleted(song: SearchResult) { /* ensureExists, then insertSongs */ }
    suspend fun onRemoved(videoId: String) { /* delete the row */ }
    suspend fun reconcile(indexed: List<DownloadedSong>) { /* diff both directions */ }
}
```

`reconcile` reads the system playlist id, inserts any indexed song missing from it, then calls `deleteSongsNotIn`. `insertSongs` uses the existing `OnConflictStrategy.IGNORE`, which makes `onCompleted` idempotent for free.

Define a small `DownloadedSong(videoId, title, artist, thumbnailUrl)` — the sync never needs download state, only identity and metadata.

- [ ] **Step 4: Wire it into `NebulaDownloads.init`**

In the existing `dm.addListener(...)`, extend `onDownloadChanged` so that when `download.state == Download.STATE_COMPLETED` it calls `sync.onCompleted(...)`, and extend `onDownloadRemoved` to call `sync.onRemoved(...)`. Also launch `sync.reconcile(...)` next to the existing `rehydrateDownloads()` in `init`.

**Do not** change the existing `_downloads` emission — the StateFlow conflation is real but out of scope, and touching it risks regressing the badge.

- [ ] **Step 5: Run to verify GREEN**

Expected: PASS, 8/8.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/nebula/data/download app/src/androidTest/java/com/example/nebula/DownloadedPlaylistSyncTest.kt
git commit -m "feat(downloads): sync completed downloads into the Downloaded playlist"
```

---

### Task 5: The Downloaded playlist screen

**Files:**
- Create: `app/src/main/java/com/example/nebula/ui/screens/DownloadedPlaylistScreen.kt`
- Modify: `app/src/main/java/com/example/nebula/MainActivity.kt` (route)
- Create: `app/src/androidTest/java/com/example/nebula/DownloadedPlaylistScreenTest.kt`

**Interfaces:**
- Consumes: `PlaylistsViewModel` (rename), `PlayerViewModel.playAll` / `toggleShuffle`, `NebulaDownloads`
- Produces: `DownloadedPlaylistScreen(vm, playerVm, onBack)`

- [ ] **Step 1: Write the failing Compose test**

```kotlin
@Test fun showsEveryDownloadedSong()
@Test fun playButtonQueuesEveryDownloadedSong()
@Test fun renameDialogIsOffered()
@Test fun overflowMenuHasNoDeleteOption()   // Review Focus
```

`overflowMenuHasNoDeleteOption` opens the overflow and asserts no node with text "Delete" exists. Use `onAllNodesWithText("Delete").assertCountEquals(0)`, not a single-node matcher — asserting absence that way is what broke a previous test.

- [ ] **Step 2: Run to verify RED**

Expected: compile failure.

- [ ] **Step 3: Implement the screen**

Cover art, name, song count, a Play button calling `playerVm.playAll(items)`, a Shuffle toggle calling `playerVm.toggleShuffle()`, a rename dialog calling `vm.rename(id, name)`, and a `LazyColumn` of songs. Rows follow the existing VoxMusic card anatomy. **No Delete control, and no remove-song affordance** — removal is the queue screen's job.

- [ ] **Step 4: Implement reorder**

`PlaylistDao.updatePosition` exists but no gesture does. Implement drag-to-reorder with `detectDragGesturesAfterLongPress`, persisting via a new `PlaylistsViewModel.reorder(playlistId, fromIndex, toIndex)` that renumbers positions inside a transaction.

**If drag proves fiddly, ship up/down buttons instead** — they satisfy the same acceptance criterion at a fraction of the risk. Record which one shipped.

- [ ] **Step 5: Route it from MainActivity**

Extend the existing `screen` + `detailId` navigation state. Do not restructure the navigation model. Make sure `BackHandler` clears the Downloaded playlist before falling back to the tab.

- [ ] **Step 6: Run to verify GREEN, then the full suite**

Run the new class, then `connectedDebugAndroidTest testDebugUnitTest assembleDebug`. Expected: all green, 19 existing tests plus the new ones.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/nebula/ui app/src/main/java/com/example/nebula/MainActivity.kt app/src/androidTest/java/com/example/nebula/DownloadedPlaylistScreenTest.kt
git commit -m "feat(ui): Downloaded playlist screen with play, shuffle, rename and reorder"
```

## Out of scope

Live progress polling · a Delete control on the system playlist · remove-from-downloaded inside the playlist screen · drag-reorder if buttons are used instead · any change to the `_downloads` StateFlow emission · `browseId` or pinned columns.
