# Home playlists, per-song menus, and sort default — design

Date: 2026-10-02
Status: awaiting review

## Goal

Five changes the human partner asked for, from screenshots of the running app:

1. Playlist sort defaults to **Date Added**.
2. Playlist cards show **real artwork** instead of a coloured tile with a letter.
3. The browse/detail screen's song rows **lose the pink play button** and gain a
   three-dot menu.
4. **Your playlists appear on the Home tab**, above the YouTube feed.
5. The player tab's **play/pause matches the MiniPlayer's** exactly.

## Non-goals

- The missing per-song thumbnails on the detail screen. Deferred by request
  (screenshot 2 shows teal letter tiles; the header artwork loads fine, so it is a
  separate parse path).
- Drag-to-reorder. Arrows remain; the reorderable dependency was removed in
  `ac92ef4` and does not come back without a working gesture.
- Converting Nebula's Home sections to Echo's horizontal `LazyRow`. The partner
  chose a **vertical** list to match the sections already on the screen.

## Findings that shaped this

- `PlaylistEntity.thumbnailUrl` is **never written**. `PlaylistsViewModel.create`
  (line 122) and `createWithSongs` (line 182) both insert `PlaylistEntity(name = …)`.
  So `PlaylistCard`'s `AsyncImage` at `PlaylistsScreen.kt:262` is always handed null
  and always falls back to the accent tile. This is the missing artwork, not a
  rendering fault.
- `PlaylistSortType.DATE_ADDED -> songs` (`PlaylistSort.kt:58`) returns the list
  untouched. It is a placeholder, not a sort.
- `SearchResult` has no `addedAt`, so the stored timestamp cannot reach the sort.
- `PlaylistsViewModel.queueItems` does **not** exist, despite the SDD ledger
  claiming it does. "Play all" from a Home card needs it added.
- Echo's Home taps a playlist card to **open** it, not play
  (`HomeScreen.kt:1329`); play and radio live in the card's expanded panel
  (`:320-352`). Its `AccountPlaylists` section renders before the community feed
  (`:806-809`).

## Changes

### 1. Date Added is a real sort, and the default

Add `addedAt: Long = 0L` to `SearchResult`. A default keeps every existing
construction site compiling; only `DownloadedPlaylistScreen.toSearchResult` sets it
from the entity.

`sortSongs` then sorts `DATE_ADDED` by that field. `DownloadedPlaylistScreen` reads
its default from `PlaylistSortChoice`, which now defaults to `DATE_ADDED` instead of
`CUSTOM`.

Songs added in the same millisecond share a stamp, so the sort falls back to stored
`position` as the tie-break. Without it two songs with equal stamps could swap places
between reads, which would look like the reorder reset bug returning.

**Accepted consequence:** a manual reorder is invisible until the user picks
"Custom order". The partner was told this and chose Date Added deliberately. The
stored positions are never rewritten by a sort, so Custom order still restores the
arrangement intact — one tap, not a recovery.

### 2. Playlist card artwork

`PlaylistDao.observePlaylists` gains one subquery column:

```sql
(SELECT thumbnailUrl FROM playlist_songs WHERE playlistId = p.id
 ORDER BY position LIMIT 1) AS coverThumbnailUrl
```

`PlaylistWithCount` gains a matching field. **No migration** — this is a query
result, not a table, so playlists that already exist immediately get a cover.

`PlaylistCard` prefers `coverThumbnailUrl` and keeps the accent tile as the
fallback for a genuinely empty playlist.

### 3. Detail-screen song rows

`AlbumDetailScreen.TrackRow`: delete the pink play `Box` (lines 352-377). The row is
already `.clickable(onClick = onPlay)`, so the button was a duplicate control for the
same action.

Add an `IconButton` with `MoreVert` opening the existing `SongMenuSheet` — Start
radio, Add to playlist, Share, Download. `AlbumDetailScreen` gains a
`playlistsVm: PlaylistsViewModel` parameter so "Add to playlist" has somewhere to
write; `MainActivity` already holds `playlistsVm`.

### 4. "Your playlists" on Home

`HomeScreen` gains a `playlistsVm` and an `onOpenPlaylist: (Long) -> Unit`.

Inside the existing `LazyColumn`, after the chip row and **before**
`currentFeed.sections`, render one `SectionBanner("Your playlists")` and a row per
playlist. Empty list renders nothing — no empty banner.

Each row reuses the screen's existing card shape (cover, name, "N tracks") so Home
keeps one visual language. Tap calls `onOpenPlaylist(id)`.

Each row also gets a three-dot menu:

- **Play all** — needs a new `suspend fun songsOf(id): List<SearchResult>` on
  `PlaylistsViewModel`, feeding `PlayerViewModel.playAll`.
- **Rename** and **Delete** — hidden for the built-in Downloaded playlist.
  `dao.delete` refuses `isSystem` rows, and a control that looks enabled but does
  nothing is worse than no control. `PlaylistsScreen` omits these for the same reason.

`MainActivity` routes `onOpenPlaylist` to the existing `playlistId`/`screen =
"playlist"` path, which is already wired.

### 5. Player play/pause matches MiniPlayer

MiniPlayer (`MiniPlayer.kt:126-134`) uses `secondary` background with an
`onSecondary` ink. `FullSheetPlayer` passes `colorScheme.primary` (line 163) into a
`ChunkyBtn` that hardcodes an `onSurface` tint.

`ChunkyBtn` gains a `tint` parameter; the play/pause call site passes
`secondary` / `onSecondary`.

## Data and migration

None. The only schema-shaped change is a query projection, not a table. `addedAt`
already exists on `playlist_songs`.

## Testing

- `PlaylistDaoTest`: the cover subquery returns the first song's thumbnail, and null
  for an empty playlist.
- `PlaylistSortTest`: two songs added in one order but stamped with different
  `addedAt` sort by the stamp, not by insertion.
- `assembleDebug` and `testDebugUnitTest` must stay green.
- **Every visual change is unverified by automation.** `emulator-5554` has dropped
  off; only `d9f99d20` remains and it is unauthorized. Items 3, 4 and 5 need eyes on
  a real device.

## Risks

- **Item 1 trades visibility for intent.** If reorder confusion returns, the fix is
  one default in `PlaylistSortChoice`.
- **Item 4 grows `HomeScreen`**, already the largest screen in the app. The playlist
  row is a separate composable to keep it from sprawling further.
- **`SongMenuSheet` on a browse-page track** may expose actions that do nothing for
  that track (it carries a videoId but no browseId, so View artist/View album stay
  unwired). Those are already defaulted to no-ops in the sheet and were already
  shipped that way on the search screen.