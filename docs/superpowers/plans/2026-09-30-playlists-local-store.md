# Playlists — Local Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `stubPlaylists` mockup with a Room-backed playlist list that a user can create, rename, and delete.

**Architecture:** Two new Room tables (`playlists`, `playlist_songs`) with a manual `MIGRATION_1_2`. A `PlaylistDao` computes `songCount` in SQL via a correlated subquery. A `PlaylistsViewModel` (`AndroidViewModel`) exposes a `StateFlow` the existing `PlaylistsScreen` renders. No Hilt, no NavHost, no new Gradle module.

**Tech Stack:** Kotlin 2.4.10, Compose BOM 2024.12.01, Material 3, Room 2.7.0 (KSP 2.3.10), Coil 3.2.0, AGP 9.2.1, JDK 17.

**Spec:** `docs/superpowers/specs/2026-09-30-playlists-design.md` — the plan argues from the spec, so the spec travels with it; executors read both.

## Global Constraints

- Package root is `com.example.nebula`. **Not** `com.nebula`, despite what `AGENTS.md` says. Do not "correct" it.
- No Hilt, no NavHost, no new Gradle module, no new dependencies. Everything needed is already in `app/build.gradle.kts`.
- `PlayerManager` is not modified by this plan. `PlayerViewModel` is not modified by this plan either — queue work is Plan B.
- `exportSchema` stays `false`. Do not add `room.schemaLocation`.
- `fallbackToDestructiveMigration()` must not be used anywhere.
- `PlaylistsScreen` keeps its VoxMusic visuals **verbatim** — the 3D offset-shadow banner, the `N PLAYLISTS` pill, `PlaylistCard`'s 3px black border and offset shadow, the accent tile. Only the data source changes.
- The database singleton lives in `NebulaDatabase.getDatabase(context)` and must keep its existing double-checked-locking shape.
- `compileSdk = 37`, `minSdk = 24`, `jvmTarget = 17`.
- **Deviation from spec §3, deliberate:** the entity has no `durationMs`, `explicit`, or `isVideoSong` column. `SearchResult` (`app/src/main/java/com/example/nebula/data/models/SearchResult.kt:3-10`) carries no duration, so `durationMs` would be permanently null, and nothing in this plan reads the other two. All three are trivial nullable adds later. Carrying permanently-null columns is what produced Echo-Music's 46 migrations.

## Review Focus

Five input classes the spec implies but no task's happy path exercises. Each has a test pinned to the task that owns the code.

1. **Empty database** — a first-run user with zero playlists must see a create prompt, not a blank list or a spinner that never resolves. → Task 4
2. **Two playlists with the same name** — both must persist and appear. Name is not a unique key; users legitimately keep "Road Trip" twice. → Task 3
3. **A playlist name far longer than the card width** — must truncate to one line, not wrap, not crash, not push the play button off-screen. → Task 4
4. **Deleting a playlist** — its `playlist_songs` rows must be removed by the foreign key, not orphaned. → Task 2
5. **Adding a song that is already in the playlist** — must not create a duplicate row or shift the existing row's `position`, and the caller must learn that zero songs were added. → Task 2 (storage) and Task 3 (reported count)

---

### Task 0: Initialize version control

Every later task commits, and `executing-plans` derives its review range from `git merge-base`. This must exist first.

**Files:**
- Create: `.git/` via `git init`
- Verify: `.gitignore` (already present)

**Interfaces:**
- Consumes: nothing
- Produces: a git repository with a baseline commit, so Task 1+ can commit and the final review has a `MERGE_BASE`

- [ ] **Step 1: Check what `.gitignore` already covers**

Run: `Get-Content .gitignore`
Expected: entries covering `build/`, `.gradle/`, `.idea/`, `local.properties`. If `local.properties` is absent, add it — it contains a machine-local SDK path and must never be committed.

- [ ] **Step 2: Initialize and make the baseline commit**

```bash
git init
git add -A
git commit -m "chore: baseline commit before playlist work"
```

Expected: commit succeeds with a non-empty file count. If it reports "nothing to commit", `.gitignore` is over-broad — inspect before continuing.

---

### Task 1: Schema and migration, driven by a failing migration test

**Files:**
- Create: `app/src/main/java/com/example/nebula/data/db/entities/PlaylistEntity.kt`
- Create: `app/src/main/java/com/example/nebula/data/db/entities/PlaylistSongEntity.kt`
- Create: `app/src/androidTest/java/com/example/nebula/PlaylistMigrationTest.kt`
- Modify: `app/src/main/java/com/example/nebula/data/db/NebulaDatabase.kt:10`

**Interfaces:**
- Consumes: `LocalSong` (existing, at `app/src/main/java/com/example/nebula/data/db/entities/LocalSong.kt`) — untouched
- Produces:
  - `PlaylistEntity(id: Long, name: String, thumbnailUrl: String?, createdAt: Long, lastUpdatedAt: Long)` in table `playlists`
  - `PlaylistSongEntity(playlistId: Long, videoId: String, title: String, artist: String, thumbnailUrl: String?, position: Int, addedAt: Long)` in table `playlist_songs`
  - `MIGRATION_1_2` in `NebulaDatabase.kt`
  - `NebulaDatabase` at `version = 2`

- [ ] **Step 1: Write the failing migration test**

Create `app/src/androidTest/java/com/example/nebula/PlaylistMigrationTest.kt`.

The fixture approach: define a **test-only** Room database in the `androidTest` source set annotated `@Database(entities = [LocalSong::class], version = 1)`, named `V1FixtureDatabase`. Open it against a temporary file name, insert one `LocalSong` row, close it. That file is now a real v1 database. Then open the production `NebulaDatabase` against the **same file name** — Room sees version 1 and runs `MIGRATION_1_2`.

Deriving the fixture from the live `LocalSong` entity is correct here: `versionCode = 1` means no v1 database exists in the wild, so the test asserts against the schema this app actually shipped rather than a hand-copied `CREATE TABLE` string that can silently drift.

Three test methods:

```kotlin
@Test fun migratesV1ToV2_createsBothPlaylistTables()
@Test fun migratesV1ToV2_preservesExistingLocalSongs()
@Test fun migratesV1ToV2_playlistSongHasForeignKeyToPlaylists()
```

- The first queries `sqlite_master` for `type='table' AND name IN ('playlists','playlist_songs')` and asserts both rows come back.
- The second reads all rows from `local_songs` and asserts the count is still 1 and the seeded title matches.
- The third reads `PRAGMA foreign_key_list('playlist_songs')` and asserts it reports a foreign key onto `playlists(id)` with `on_delete` = `CASCADE` (1).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistMigrationTest`

Expected: FAIL. `NebulaDatabase` is still `version = 1`, so Room opens the fixture without migrating and `sqlite_master` returns no `playlists` row.

If no emulator or device is attached, this step cannot run. Stop and report that rather than skipping to Step 3 — an unverified migration is the single riskiest thing in this plan.

- [ ] **Step 3: Create `PlaylistEntity`**

Create `app/src/main/java/com/example/nebula/data/db/entities/PlaylistEntity.kt`:

```kotlin
package com.example.nebula.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 4: Create `PlaylistSongEntity`**

Create `app/src/main/java/com/example/nebula/data/db/entities/PlaylistSongEntity.kt`. The composite primary key is what makes a duplicate add a no-op rather than a second row; the index on `playlistId` is what keeps `ORDER BY position` from scanning the whole table.

```kotlin
package com.example.nebula.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 5: Bump the database and add the migration**

Modify `app/src/main/java/com/example/nebula/data/db/NebulaDatabase.kt`. Change the annotation to `version = 2` and add both entities to `entities`. Add a top-level `MIGRATION_1_2` in the same file — not a new `Migrations.kt`, matching the existing single-file layout — and register it with `.addMigrations(MIGRATION_1_2)` on the builder, placed after `.build()`'s target database argument.

The migration body is exactly two `CREATE TABLE IF NOT EXISTS` statements and one `CREATE INDEX IF NOT EXISTS`. Write the column lists to match Steps 3 and 4 exactly, including the `FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE` clause and the two `playlistId`/`videoId` primary key columns. `IF NOT EXISTS` is what makes the migration idempotent.

Do not add `fallbackToDestructiveMigration()`. Do not change `exportSchema`.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistMigrationTest`

Expected: PASS, 3/3.

- [ ] **Step 7: Verify the app still builds and the old tab still renders**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL. The Playlists tab still shows the six hardcoded stub playlists, because nothing is wired to the new tables yet. That is expected at this task.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/nebula/data/db app/src/androidTest/java/com/example/nebula/PlaylistMigrationTest.kt
git commit -m "feat(db): add playlists and playlist_songs tables with v1 to v2 migration"
```

---

### Task 2: The DAO, driven by a failing DAO test

**Files:**
- Create: `app/src/main/java/com/example/nebula/data/db/dao/PlaylistDao.kt`
- Create: `app/src/androidTest/java/com/example/nebula/PlaylistDaoTest.kt`
- Modify: `app/src/main/java/com/example/nebula/data/db/NebulaDatabase.kt` (add the DAO accessor)

**Interfaces:**
- Consumes: `PlaylistEntity`, `PlaylistSongEntity` from Task 1
- Produces: `PlaylistDao` with the signatures below, and `NebulaDatabase.playlistDao()`. Task 3 depends on every one of these names exactly.

- [ ] **Step 1: Write the failing DAO test**

Create `app/src/androidTest/java/com/example/nebula/PlaylistDaoTest.kt`. Use `Room.inMemoryDatabaseBuilder(context, NebulaDatabase::class.java).allowMainThreadQueries().build()`, and `@Before`/`@After` to clear both tables between tests so ordering never leaks between methods.

Four test methods:

```kotlin
@Test fun observePlaylists_returnsZeroCountForEmptyPlaylist()
@Test fun observePlaylists_countsSongsViaSubquery()
@Test fun insertSongs_ignoresDuplicateVideoIdAndKeepsOriginalPosition()
@Test fun delete_playlist_cascadesAndRemovesItsSongs()
```

- The first inserts a playlist, collects `observePlaylists().first()`, asserts one row with `songCount == 0`.
- The second inserts a playlist and three songs, asserts `songCount == 3`. This is the N+1 guard: the count arrives from SQL, not from a Kotlin loop.
- The third inserts a song at `position = 0`, then re-inserts the same `(playlistId, videoId)` pair at `position = 5`. Asserts the table still holds one row, still at `position = 0`, and that `insertSongs` returned a list containing `-1` — Room's signal for a row the `IGNORE` strategy skipped. That `-1` is how Task 3 learns to report "0 added".
- The fourth inserts a playlist and two songs, calls `delete(id)`, then asserts `observeSongs(id).first()` is empty.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistDaoTest`

Expected: FAIL to compile — `PlaylistDao` does not exist.

- [ ] **Step 3: Create `PlaylistDao`**

Create `app/src/main/java/com/example/nebula/data/db/dao/PlaylistDao.kt` with exactly these members:

```kotlin
@Dao
interface PlaylistDao {
    @Query("SELECT p.*, (SELECT COUNT(*) FROM playlist_songs WHERE playlistId = p.id) AS songCount FROM playlists p ORDER BY p.lastUpdatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Insert suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, lastUpdatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<PlaylistSongEntity>): List<Long>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeSong(playlistId: Long, videoId: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun updatePosition(playlistId: Long, videoId: String, position: Int)
}
```

Also declare in the same file:

```kotlin
data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int
)
```

`rename` takes `now` as an explicit parameter with **no Kotlin default value** — Room's generated implementation and Kotlin default arguments on abstract interface methods do not mix reliably. Callers pass `System.currentTimeMillis()`.

- [ ] **Step 4: Expose the DAO**

Modify `app/src/main/java/com/example/nebula/data/db/NebulaDatabase.kt` to add `abstract fun playlistDao(): PlaylistDao` alongside the existing `localSongDao()`. Add the import.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistDaoTest`

Expected: PASS, 4/4.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/nebula/data/db
git commit -m "feat(db): add PlaylistDao with SQL-computed song counts"
```

---

### Task 3: The ViewModel, driven by a failing ViewModel test

**Files:**
- Create: `app/src/main/java/com/example/nebula/viewmodel/PlaylistsViewModel.kt`
- Create: `app/src/androidTest/java/com/example/nebula/PlaylistsViewModelTest.kt`

**Interfaces:**
- Consumes: `PlaylistDao` and `PlaylistWithCount` from Task 2; `SearchResult` from `app/src/main/java/com/example/nebula/data/models/SearchResult.kt` (fields `videoId`, `title`, `artist`, `thumbnailUrl`, all used; `explicit` and `isVideoSong` ignored)
- Produces:
  - `PlaylistsViewModel(app: Application) : AndroidViewModel(app)`
  - `val playlists: StateFlow<List<PlaylistWithCount>>`
  - `fun create(name: String)`
  - `fun rename(id: Long, name: String)`
  - `fun delete(id: Long)`
  - `fun addSongs(playlistId: Long, results: List<SearchResult>): Int` — returns how many were genuinely new

**Note on the test source set:** these tests live in `androidTest`, not `test`. `PlaylistsViewModel` is an `AndroidViewModel` because the DAO needs a `Context` to reach `NebulaDatabase.getDatabase(app)`, and a plain JVM `test` cannot construct one — there is no Robolectric in `app/build.gradle.kts` and this plan adds no dependencies. `androidTest` supplies a real `Application` via `ApplicationProvider.getApplicationContext()` from `androidx.junit`, which is already declared at `app/build.gradle.kts:74`.

- [ ] **Step 1: Write the failing ViewModel test**

Create `app/src/androidTest/java/com/example/nebula/PlaylistsViewModelTest.kt`. Build a real in-memory `NebulaDatabase`, then construct `PlaylistsViewModel` against an `Application`. Because the ViewModel resolves its DAO from the app context, delete the database file between tests rather than swapping in a fake DAO — the DAO is an interface but the ViewModel does not accept one.

Four test methods:

```kotlin
@Test fun create_addsPlaylistToTheList()
@Test fun create_allowsTwoPlaylistsWithTheSameName()
@Test fun delete_removesPlaylistFromTheList()
@Test fun addSongs_returnsCountOfNewlyAddedSongsOnly()
```

- The first calls `create("Road Trip")`, then asserts `playlists.value` holds one entry named `Road Trip` with `songCount == 0`. `create` launches into `viewModelScope`, so the test must wait — poll `playlists.first { it.isNotEmpty() }` with a timeout rather than asserting immediately.
- The second is Review Focus line 2: calls `create("Road Trip")` twice and asserts both rows exist. There is no unique constraint on `name`, and adding one would be wrong.
- The third creates then deletes, and asserts the list empties.
- The fourth is Review Focus line 5: adds two distinct `SearchResult`s, then adds the same two again. Asserts the return value of the second call is `0` and the playlist's `songCount` is still `2`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistsViewModelTest`

Expected: FAIL to compile — `PlaylistsViewModel` does not exist.

- [ ] **Step 3: Create `PlaylistsViewModel`**

Create `app/src/main/java/com/example/nebula/viewmodel/PlaylistsViewModel.kt`.

```kotlin
class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = NebulaDatabase.getDatabase(app).playlistDao()

    val playlists: StateFlow<List<PlaylistWithCount>> =
        dao.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        dao.insert(PlaylistEntity(name = trimmed))
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        dao.rename(id, trimmed, System.currentTimeMillis())
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    suspend fun addSongs(playlistId: Long, results: List<SearchResult>): Int { ... }
}
```

Guidance on the bodies the signature does not determine:

- `create` must trim and reject blank names — a playlist called " " is not a playlist. Rejecting duplicates is explicitly *not* wanted.
- `rename` takes the same blank check.
- `addSongs` is `suspend` rather than fire-and-forget because it returns a count the caller needs for feedback; the screen calls it inside its own `rememberCoroutineScope().launch`. Its body: read `nextPosition(playlistId)`, map each `SearchResult` to a `PlaylistSongEntity` carrying an incrementing position, call `insertSongs`, and return `result.count { it != -1L }` — the rows `OnConflictStrategy.IGNORE` actually skipped. This is Review Focus line 5's reporting half.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistsViewModelTest`

Expected: PASS, 4/4.

- [ ] **Step 5: Run the whole instrumented suite to confirm nothing regressed**

Run: `./gradlew connectedDebugAndroidTest`

Expected: PASS across all three test classes. `ExampleInstrumentedTest` must still pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/nebula/viewmodel/PlaylistsViewModel.kt app/src/androidTest/java/com/example/nebula/PlaylistsViewModelTest.kt
git commit -m "feat: add PlaylistsViewModel backed by PlaylistDao"
```

---

### Task 4: Rewire `PlaylistsScreen` and wire the tab

**Files:**
- Modify: `app/src/main/java/com/example/nebula/ui/screens/PlaylistsScreen.kt` (delete `StubPlaylist` and `stubPlaylists` at lines 45-54)
- Modify: `app/src/main/java/com/example/nebula/MainActivity.kt:213` (the `"playlists" -> PlaylistsScreen()` branch)
- Create: `app/src/androidTest/java/com/example/nebula/PlaylistsScreenTest.kt`

**Interfaces:**
- Consumes: `PlaylistsViewModel` and `PlaylistWithCount` from Task 3
- Produces: `PlaylistsScreen(vm: PlaylistsViewModel = viewModel())` — accepts an explicit ViewModel so the Compose test can inject one

- [ ] **Step 1: Write the failing Compose test**

Create `app/src/androidTest/java/com/example/nebula/PlaylistsScreenTest.kt` using `createComposeRule()` from `androidx.compose.ui.test.junit4`, already declared at `app/build.gradle.kts:72`. Wrap the content in `NebulaTheme`.

Two test methods:

```kotlin
@Test fun emptyDatabase_showsCreatePromptNotBlankList()
@Test fun longPlaylistName_truncatesWithoutBreakingTheCard()
```

- The first renders with a ViewModel over an empty database and asserts `onNodeWithText("Create your first playlist").assertIsDisplayed()`. This is Review Focus line 1 — it is the test that stops a blank first-run screen from shipping.
- The second creates a playlist whose name is 120 characters, renders, and asserts the name node exists and the card's play button is still displayed. This is Review Focus line 3. Assert on the button being displayed rather than on pixel geometry — the truncation itself is enforced by `maxLines = 1`, which the existing card already sets.

Do not add screenshot or Paparazzi testing. `createComposeRule` plus `onNodeWithText` covers both cases, and Paparazzi is not a declared dependency.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistsScreenTest`

Expected: FAIL — the current screen renders six hardcoded stub playlists, so no create prompt exists.

- [ ] **Step 3: Rewire the screen**

Modify `app/src/main/java/com/example/nebula/ui/screens/PlaylistsScreen.kt`.

- Delete the `StubPlaylist` data class and the `stubPlaylists` list entirely.
- Change the signature to `fun PlaylistsScreen(vm: PlaylistsViewModel = viewModel())`.
- Read `val playlists by vm.playlists.collectAsState()`.
- Keep the 3D banner and the pill exactly as they are. The pill's text becomes `"${playlists.size} PLAYLISTS"`.
- Replace the `LazyColumn` body with three states: when `playlists` is empty render a centered "Create your first playlist" prompt with a button calling `vm.create(...)`; otherwise render the existing `items(...)` loop over `PlaylistWithCount`.
- `PlaylistCard` changes its parameter from `StubPlaylist` to `PlaylistWithCount`. It keeps the offset shadow, the 3px `BorderBlack` border, the 16dp corner radius, and the 40dp play button.
- The 52dp accent tile: when `playlist.thumbnailUrl` is non-null render it with `AsyncImage` from `coil3.compose` (Coil 3.2.0 with `coil-network-okhttp`, both already at `app/build.gradle.kts:64-65`). When it is null, keep today's `Icons.Filled.QueueMusic` on the accent background. **Keep the existing accent color** — do not replace it with a new palette.
- Title rendering keeps `fontWeight = FontWeight.Black`, `fontSize = 14.sp`, `maxLines = 1`.
- Delete the `onClick = {}` no-ops. Card click and play button both become real actions calling into the ViewModel.
- Keep the existing `@Preview` and extend it to pass a stub ViewModel, or remove it — a preview that cannot supply its ViewModel is a preview that will not compile.

- [ ] **Step 4: Wire the tab in `MainActivity`**

Modify `app/src/main/java/com/example/nebula/MainActivity.kt`. The existing branch at line 213 is `"playlists" -> PlaylistsScreen()`. Because the new signature defaults its ViewModel via `viewModel()`, this line may not need to change at all — verify it compiles. If `viewModel()` cannot resolve in that scope, obtain the ViewModel with `viewModel()` at the call site and pass it explicitly. Do not restructure the surrounding `screen`/`detailId` navigation state; detail-screen routing is Plan B.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.nebula.PlaylistsScreenTest`

Expected: PASS, 2/2.

- [ ] **Step 6: Run the full suite and build**

Run: `./gradlew connectedDebugAndroidTest assembleDebug`

Expected: all instrumented tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 7: Manual QA in Android Studio**

Install on a device or emulator and confirm, against acceptance criteria 2 and 3 of the spec:

1. With no playlists, the Playlists tab shows the create prompt, not a blank list.
2. Creating a playlist makes it appear with a count of 0.
3. The 3D banner, the pill, the card border, and the offset shadow all still look as they did before this change. Compare against the pre-change build — this is the regression most likely to slip through, because no test asserts on visual styling.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/nebula/ui/screens/PlaylistsScreen.kt app/src/main/java/com/example/nebula/MainActivity.kt app/src/androidTest/java/com/example/nebula/PlaylistsScreenTest.kt
git commit -m "feat(ui): back PlaylistsScreen with real data, keep VoxMusic visuals"
```

---

## Out of scope for this plan

Deferred to the Plan B spec: `PlaylistDetailScreen` and `PlaylistDetailViewModel` · `playPlaylist`/`jumpTo`/`removeAt` on `PlayerViewModel` · `QueueScreen` · `AddToPlaylistDialog` and its wiring into search and album detail · the `renumberPositions` transaction after a removal.

Explicitly not done, with reasons: **no `browseId` column** — it would be permanently null until a YTM sync exists that nothing in this plan builds, and adding a nullable column later is a one-line migration. **no `durationMs`** — `SearchResult` has no duration to store. **no pinned/ordering flag** — nothing sets it. **no migration failure screen** — the migration is two `CREATE TABLE IF NOT EXISTS` against a v1 database that has neither table, so there is nothing to conflict with; a crash with a logcat stack trace is more debuggable than a friendly screen that hides it. **no on-device database backup** — unbounded growth, no cleanup policy, runs on every migration.
