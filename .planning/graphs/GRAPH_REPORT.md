# Graph Report - .  (2026-10-18)

## Corpus Check
- 178 files · ~479,442 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1621 nodes · 3157 edges · 100 communities (75 shown, 25 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 347 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Search & Repository
- Playlist Migration Tests
- Local Music Repository
- Blocked Songs
- Download Queue UI
- Project Docs & Design
- Nebula Downloads
- Playback Mode Tests
- YouTube Auth/Cookies
- Update Checker
- Playback Service
- Now Playing Themes
- Downloaded Playlist Affordances Tests
- Player ViewModel
- Play History Dao
- Listen Together Sync
- Downloaded Playlist Sync Tests
- Settings Screen
- Lyrics Screen
- Playlists View Model
- Equalizer
- Player Manager
- Listen Protocol
- Download All Dialog
- Chunk Window Tests
- Playlist Dao Tests
- Main Activity
- Lyrics Providers
- Nebula Application
- Settings Sections
- Download Notifications
- Lyrics Styles
- Playlist Sort Tests
- Playlist Dao
- Playlists Create Tests
- Playlists Reorder Tests
- Onboarding Store
- Reorder Gap Tests
- Lyrics Repository
- Chunked Data Source
- Download Service
- Player ViewModel Stall
- Downloaded Playlist Sync
- Word Timing
- Playback Modes
- Downloaded Playlist Screen
- Downloaded Playlist Dao Tests
- Home Prefs
- Listen Client
- Onboarding & Lyrics Pickers
- Search & Album Screens
- Equalizer Screen
- Playlists ViewModel Tests
- Playlists & Artist Screens
- Speed Dial Dao
- Playlists Screen
- Korean Romanizer Tests
- Lyrics Provider Registry
- Search View Model
- Lyrics Language Tests
- YouTube Music Lyrics
- Play From Here Tests
- Kugou Lyrics
- Lyrics Translator
- Song Stats & Info
- Update Settings
- Blocked Songs Tests
- Chunky Window
- Playlists Screen Tests
- Full Sheet Player
- Home Screen
- Theme Store
- Romanizer
- Playlist Csv
- Playback Modes Enum
- Queue Sheet
- Downloads Icon Tests
- Player Cache Key Tests
- Word Timing Utils
- Now Playing Screen
- Source Add Enum
- Tab Customizer
- Playlist Csv Tests
- Song Codec
- Playback Action Payload
- Mini Player
- Song Menu Sheet
- Albums Screen
- Update Download Worker
- Backup Manager Tests
- Room Role Enum
- Gradle Wrapper
- Download Request Key Test
- Example Instrumented Test
- Example Unit Test

## God Nodes (most connected - your core abstractions)
1. `PlayerViewModel` - 85 edges
2. `SearchResult` - 72 edges
3. `SearchRepository` - 61 edges
4. `PlaylistsViewModel` - 48 edges
5. `PlaybackModeTest` - 42 edges
6. `LyricLine` - 31 edges
7. `ProtoWire` - 29 edges
8. `SyncManager` - 28 edges
9. `NebulaDownloads` - 24 edges
10. `PlaylistDao` - 23 edges

## Surprising Connections (you probably didn't know these)
- `Architecture Boundaries (theme/screens/CHANGE.md)` --references--> `Nebula Change Log`  [EXTRACTED]
  AGENTS.md → CHANGE.md
- `Nebula Change Log` --conceptually_related_to--> `MVVM No-DI Architecture`  [INFERRED]
  CHANGE.md → design.md
- `importPlaylistUrl()` --calls--> `DetailPage`  [INFERRED]
  app/src/main/java/nebula/music/ui/screens/PlaylistsScreen.kt → app/src/main/java/nebula/music/data/SearchRepository.kt
- `SongInfoDialog()` --calls--> `SearchRepository`  [INFERRED]
  app/src/main/java/nebula/music/ui/components/SongInfoDialog.kt → app/src/main/java/nebula/music/data/SearchRepository.kt
- `ArtistDetailScreen()` --calls--> `SearchRepository`  [INFERRED]
  app/src/main/java/nebula/music/ui/screens/ArtistDetailScreen.kt → app/src/main/java/nebula/music/data/SearchRepository.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Playlist Subsystem (local + downloaded)** — pl_schema, pl_dao, pl_viewmodel, dp_is_system, dp_sync, hp_your_playlists_home [EXTRACTED 0.90]
- **Reference Apps (conceptual inspiration only)** — ref_voxmusic, ref_echomusic, ref_metrolist, ref_kugou [EXTRACTED 1.00]

## Communities (100 total, 25 thin omitted)

### Community 0 - "Search & Repository"
Cohesion: 0.07
Nodes (23): AccountLibrary, StateFlow, HomeCard, HomeChip, HomeFeed, HomeSection, ArtistDetailPage, CachedStreamUrl (+15 more)

### Community 1 - "Playlist Migration Tests"
Cohesion: 0.06
Nodes (26): Context, RoomDatabase, PlaylistMigrationTest, PlaylistSongV2Entity, PlaylistV2Entity, V1FixtureDatabase, V2FixtureDao, V2FixtureDatabase (+18 more)

### Community 2 - "Local Music Repository"
Cohesion: 0.06
Nodes (27): LocalSongDao, LocalSong, Context, LocalMusicRepository, LocalSongScanConfig, LocalSongScanSummary, collator(), localAlbums() (+19 more)

### Community 3 - "Blocked Songs"
Cohesion: 0.10
Nodes (14): BlockedSongs, Entry, Context, DataStore, Preferences, TrackInfo, Bytes, Fixed32 (+6 more)

### Community 4 - "Download Queue UI"
Cohesion: 0.07
Nodes (32): ChunkyBanner(), ChunkyIconBtn(), ChunkyProgressBar(), detail(), DownloadQueueContent(), DownloadQueueScreen(), DownloadRow(), DownloadsAmoledPreview() (+24 more)

### Community 5 - "Project Docs & Design"
Cohesion: 0.06
Nodes (42): Architecture Boundaries (theme/screens/CHANGE.md), Clean Room Policy (no copy-paste from reference apps), VoxMusic Design System (Cream/AMOLED, 3px borders, accents), Nebula Project, Android Tech Stack (Kotlin, Compose, Media3, Ktor, Room), Blacklist Feature (BlockedSongs), Nebula Change Log, Crossfade Feature (3s fade) (+34 more)

### Community 6 - "Nebula Downloads"
Cohesion: 0.09
Nodes (11): DownloadPrefs, Context, DataSpec, Download, DownloadManager, Flow, StateFlow, NebulaDownloads (+3 more)

### Community 8 - "YouTube Auth/Cookies"
Cohesion: 0.10
Nodes (14): YtAccount, androidx, Context, DataStore, Preferences, YtCookieStore, Context, JsonObject (+6 more)

### Community 9 - "Update Checker"
Cohesion: 0.08
Nodes (13): Error, Context, UpdateAvailable, UpdateChecker, UpdateResult, UpToDate, isNewerVersion(), Context (+5 more)

### Community 10 - "Playback Service"
Cohesion: 0.09
Nodes (16): HeaderInjectingDataSource, Bundle, ByteArray, DataSource, DataSpec, Intent, TransferListener, Uri (+8 more)

### Community 11 - "Now Playing Themes"
Cohesion: 0.11
Nodes (29): fromId(), NowPlayingTheme, Adaptive, Blur, BlurCard, Card, Circle, Classic (+21 more)

### Community 12 - "Downloaded Playlist Affordances Tests"
Cohesion: 0.15
Nodes (4): DownloadedPlaylistScreenAffordancesTest, DownloadedPlaylistScreenTest, Application, PlaylistScreenTestSupport

### Community 13 - "Player ViewModel"
Cohesion: 0.11
Nodes (5): Context, Job, StateFlow, ViewModel, PlayerViewModel

### Community 14 - "Play History Dao"
Cohesion: 0.15
Nodes (8): Flow, PlayHistoryDao, PlayHistoryEntity, historyBuckets(), HistoryRow(), HistoryScreen(), toSearchResult(), HistoryBucketsTest

### Community 15 - "Listen Together Sync"
Cohesion: 0.17
Nodes (4): ListenMessage, parseMessage(), Job, SyncManager

### Community 17 - "Settings Screen"
Cohesion: 0.21
Nodes (22): AboutRow(), AppearanceSection(), BackupUpdateSection(), ColourDialog(), currentIcon(), GroupDivider(), hexOf(), HubRow() (+14 more)

### Community 18 - "Lyrics Screen"
Cohesion: 0.28
Nodes (21): AnnotatedString, ActiveLine(), appleLine(), appleV2Line(), buildWordLine(), fadeLine(), glowLine(), karaokeLine() (+13 more)

### Community 19 - "Playlists View Model"
Cohesion: 0.14
Nodes (5): AddToPlaylistResult, AndroidViewModel, StateFlow, PlaylistSortChoice, PlaylistsViewModel

### Community 20 - "Equalizer"
Cohesion: 0.15
Nodes (7): Band, Context, SharedPreferences, NebulaEqualizer, Equalizer, PresetReverb, Virtualizer

### Community 21 - "Player Manager"
Cohesion: 0.12
Nodes (5): Context, PlayerManager, MediaController, MediaItem, PlaybackException

### Community 22 - "Listen Protocol"
Cohesion: 0.12
Nodes (18): ApproveJoinPayload, CreateRoomPayload, ErrorPayload, HostChangedPayload, JoinApprovedPayload, JoinRejectedPayload, JoinRequestPayload, JoinRoomPayload (+10 more)

### Community 23 - "Download All Dialog"
Cohesion: 0.14
Nodes (17): DownloadAllDialog(), BigLetter(), DetailHeader(), HeaderMenu(), android, nebula, LikedSongsTile(), RenameDialog() (+9 more)

### Community 24 - "Chunk Window Tests"
Cohesion: 0.11
Nodes (6): ChunkWindowTest, androidx, ByteArray, DataSource, Uri, UnusedDataSource

### Community 26 - "Main Activity"
Cohesion: 0.14
Nodes (10): StateFlow, YtAuth, AppNavBar(), Bundle, ImageVector, MainActivity, NavButton(), LoginScreen() (+2 more)

### Community 27 - "Lyrics Providers"
Cohesion: 0.18
Nodes (5): KpoeLyrics, JsonObject, UnisonLyrics, LyricsResponse, LyricWord

### Community 28 - "Nebula Application"
Cohesion: 0.18
Nodes (13): createCache(), createCacheDataSourceFactory(), createDownloadCache(), databaseProvider(), getCache(), getCacheDataSourceFactory(), getDatabaseProvider(), getDownloadCache() (+5 more)

### Community 29 - "Settings Sections"
Cohesion: 0.20
Nodes (18): ChunkyWindow(), AccountSection(), BackRow(), calculateCacheSize(), NavigationSection(), PlaybackHomeSection(), sectionTitle(), SettingsHeader() (+10 more)

### Community 30 - "Download Notifications"
Cohesion: 0.22
Nodes (4): DownloadNotificationManager, Context, Notification, NotificationManager

### Community 31 - "Lyrics Styles"
Cohesion: 0.17
Nodes (9): Context, LyricHighlight, APPLE, APPLE_V2, FADE, GLOW, KARAOKE, LINE (+1 more)

### Community 34 - "Playlists Create Tests"
Cohesion: 0.27
Nodes (3): Application, Job, PlaylistsCreateWithSongsTest

### Community 35 - "Playlists Reorder Tests"
Cohesion: 0.27
Nodes (3): Application, Job, PlaylistsReorderTest

### Community 36 - "Onboarding Store"
Cohesion: 0.23
Nodes (4): Context, DataStore, Preferences, OnboardingStore

### Community 37 - "Reorder Gap Tests"
Cohesion: 0.25
Nodes (3): Application, Job, ReorderGapDiagnosticTest

### Community 39 - "Chunked Data Source"
Cohesion: 0.15
Nodes (13): addTransferListener(), ChunkedDataSource, bytesRemaining, close(), getUri(), ByteArray, DataSource, DataSpec (+5 more)

### Community 40 - "Download Service"
Cohesion: 0.14
Nodes (8): Download, DownloadManager, Intent, Notification, NebulaDownloadService, DownloadNotificationHelper, DownloadService, Scheduler

### Community 41 - "Player ViewModel Stall"
Cohesion: 0.19
Nodes (6): StallAction, GiveUp, None, Retry, StallWatchdog, StallWatchdogTest

### Community 42 - "Downloaded Playlist Sync"
Cohesion: 0.22
Nodes (4): PlaylistSongEntity, DownloadedPlaylistSync, DownloadedSong, from()

### Community 44 - "Playback Modes"
Cohesion: 0.20
Nodes (3): PlaybackModes, QueueState, SongInfo

### Community 45 - "Downloaded Playlist Screen"
Cohesion: 0.31
Nodes (13): ChunkyIconButton(), CoverArt(), DownloadedPlaylistContent(), DownloadedPlaylistScreen(), DownloadedSongRow(), androidx, label(), PlaylistCover() (+5 more)

### Community 47 - "Home Prefs"
Cohesion: 0.23
Nodes (4): HomePrefs, Context, DataStore, Preferences

### Community 48 - "Listen Client"
Cohesion: 0.17
Nodes (8): ConnectionState, CONNECTED, CONNECTING, DISCONNECTED, Job, ListenClient, SharedFlow, WebSocket

### Community 49 - "Onboarding & Lyrics Pickers"
Cohesion: 0.35
Nodes (10): LyricsProvidersSection(), LyricsStyleSection(), ArtistPage(), GenrePage(), ImportPage(), OnboardCard(), OnboardingScreen(), OnboardingShell() (+2 more)

### Community 50 - "Search & Album Screens"
Cohesion: 0.31
Nodes (12): AlbumDetailScreen(), detailBrowseId(), FilterChip(), FilterOption, groupSearchResults(), Context, RecentSearches(), ResultCard() (+4 more)

### Community 51 - "Equalizer Screen"
Cohesion: 0.28
Nodes (12): EqCard(), EqChip(), EqFader(), EqIconBtn(), EqualizerScreen(), freqLabel(), ImageVector, Modifier (+4 more)

### Community 53 - "Playlists ViewModel Tests"
Cohesion: 0.18
Nodes (3): Application, Job, PlaylistsViewModelTest

### Community 54 - "Playlists & Artist Screens"
Cohesion: 0.30
Nodes (9): PlaylistWithCount, AddToPlaylistDialog(), CreateAndAddDialog(), CreatePlaylistRow(), PlaylistPickRow(), ArtistDetailScreen(), ArtistTrackRow(), SectionBanner() (+1 more)

### Community 55 - "Speed Dial Dao"
Cohesion: 0.18
Nodes (4): Flow, PinnedPlaylist, SpeedDialDao, SpeedDialItem

### Community 56 - "Playlists Screen"
Cohesion: 0.32
Nodes (11): AddPlaylistWindow(), DownloadedEntryCard(), DownloadsIconButton(), HelpText(), importPlaylistUrl(), LikedEntryCard(), LocalEntryCard(), NewPlaylistButton() (+3 more)

### Community 58 - "Lyrics Provider Registry"
Cohesion: 0.25
Nodes (4): Context, SharedPreferences, LyricsProviderRegistry, ProviderInfo

### Community 59 - "Search View Model"
Cohesion: 0.24
Nodes (4): Job, StateFlow, ViewModel, SearchViewModel

### Community 64 - "Lyrics Translator"
Cohesion: 0.31
Nodes (4): await(), T, LyricsTranslator, Translator

### Community 65 - "Song Stats & Info"
Cohesion: 0.39
Nodes (7): SongStats, formatBytes(), formatDate(), formatDuration(), InfoPair(), InfoRow(), SongInfoDialog()

### Community 66 - "Update Settings"
Cohesion: 0.47
Nodes (7): UpdateDialog(), UpdateSettingsSection(), autoClearOldApks(), clearDownloadedApks(), getDownloadedApkCount(), getDownloadedApksDir(), Context

### Community 69 - "Chunky Window"
Cohesion: 0.29
Nodes (7): Activity, ChunkyAction(), findActivity(), ImmersiveMode(), Dp, ImageVector, Modifier

### Community 70 - "Playlists Screen Tests"
Cohesion: 0.25
Nodes (3): Application, Job, PlaylistsScreenTest

### Community 71 - "Full Sheet Player"
Cohesion: 0.39
Nodes (7): ArtBox(), ChunkyBtn(), fmtTime(), FullSheetPlayer(), Dp, ImageVector, SheetLetter()

### Community 72 - "Home Screen"
Cohesion: 0.43
Nodes (7): HomeScreen(), nebula, MyPlaylistRow(), RenamePlaylistDialog(), SectionBanner(), SectionMenu(), SpeedDialRow()

### Community 73 - "Theme Store"
Cohesion: 0.36
Nodes (4): Context, DataStore, Preferences, ThemeStore

### Community 76 - "Playback Modes Enum"
Cohesion: 0.38
Nodes (5): NextAction, PlayQueue, PlayUpNext, ReplayCurrent, Stop

### Community 77 - "Queue Sheet"
Cohesion: 0.52
Nodes (6): ChunkyDragHandle(), QueueLetter(), QueueRow(), QueueSheet(), QueueSheetContent(), SaveQueueButton()

### Community 81 - "Now Playing Screen"
Cohesion: 0.60
Nodes (5): ArtBox(), ChunkyButton(), LetterArt(), NowPlayingPreviewDark(), NowPlayingScreen()

### Community 82 - "Source Add Enum"
Cohesion: 0.33
Nodes (6): AddSource, NEBULA, NEW, OTHERS, SPOTIFY, YOUTUBE

### Community 83 - "Tab Customizer"
Cohesion: 0.53
Nodes (5): ArrowButton(), ImageVector, NavTab, TabCard(), TabCustomizerScreen()

### Community 86 - "Playback Action Payload"
Cohesion: 0.40
Nodes (4): encodeMessage(), T, payloadOf(), PlaybackActionPayload

### Community 87 - "Mini Player"
Cohesion: 0.70
Nodes (4): MiniLetter(), MiniPlayer(), MiniPlayerPreview(), MiniPlayerPreviewAmoled()

### Community 88 - "Song Menu Sheet"
Cohesion: 0.60
Nodes (4): ImageVector, MenuItem(), SheetHeader(), SongMenuSheet()

### Community 89 - "Albums Screen"
Cohesion: 0.60
Nodes (4): AlbumCard(), AlbumsPreviewLight(), AlbumsScreen(), StubAlbum

### Community 90 - "Update Download Worker"
Cohesion: 0.40
Nodes (3): UpdateDownloadWorker, CoroutineWorker, Result

### Community 92 - "Room Role Enum"
Cohesion: 0.50
Nodes (4): RoomRole, GUEST, HOST, NONE

### Community 93 - "Gradle Wrapper"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **92 isolated node(s):** `SearchFilter`, `Error`, `DISCONNECTED`, `CONNECTING`, `CONNECTED` (+87 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PlayerViewModel` connect `Player ViewModel` to `Local Music Repository`, `Download Queue UI`, `Downloaded Playlist Affordances Tests`, `Play History Dao`, `Listen Together Sync`, `Downloaded Playlist Sync Tests`, `Lyrics Screen`, `Download All Dialog`, `Main Activity`, `Lyrics Providers`, `Settings Sections`, `Player ViewModel Stall`, `Downloaded Playlist Screen`, `Search & Album Screens`, `Player Queue Actions`, `Playlists & Artist Screens`, `Player Shuffle/Play`, `Full Sheet Player`, `Home Screen`, `Queue Sheet`, `Now Playing Screen`, `Mini Player`?**
  _High betweenness centrality (0.257) - this node is a cross-community bridge._
- **Why does `SearchResult` connect `Downloaded Playlist Sync Tests` to `Search & Repository`, `Blocked Songs`, `Download Queue UI`, `Nebula Downloads`, `Player ViewModel`, `Play History Dao`, `Settings Screen`, `Playlists View Model`, `Download All Dialog`, `Playlist Sort Tests`, `Playlists Create Tests`, `Downloaded Playlist Sync`, `Downloaded Playlist Screen`, `Search & Album Screens`, `Player Queue Actions`, `Playlists ViewModel Tests`, `Playlists & Artist Screens`, `Search View Model`, `Play From Here Tests`, `Player Shuffle/Play`, `Blocked Songs Tests`, `Home Screen`, `Queue Sheet`, `Song Codec`, `Song Menu Sheet`?**
  _High betweenness centrality (0.185) - this node is a cross-community bridge._
- **Why does `PlaylistsViewModel` connect `Playlists View Model` to `Download Queue UI`, `Downloaded Playlist Affordances Tests`, `Settings Screen`, `Playlist Dao Tests`, `Settings Sections`, `Playlists Create Tests`, `Playlists Reorder Tests`, `Reorder Gap Tests`, `Downloaded Playlist Sync`, `Downloaded Playlist Screen`, `Search & Album Screens`, `Playlists ViewModel Tests`, `Playlists & Artist Screens`, `Speed Dial Dao`, `Playlists Screen`, `Playlists Screen Tests`, `Full Sheet Player`, `Home Screen`, `Queue Sheet`?**
  _High betweenness centrality (0.125) - this node is a cross-community bridge._
- **Are the 17 inferred relationships involving `SearchResult` (e.g. with `.onCompleted_afterARenameStillLandsInTheSamePlaylist()` and `.onCompleted_appendsInOrder()`) actually correct?**
  _`SearchResult` has 17 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `SearchRepository` (e.g. with `.load()` and `SongInfoDialog()`) actually correct?**
  _`SearchRepository` has 8 INFERRED edges - model-reasoned connections that need verification._
- **What connects `SearchFilter`, `Error`, `DISCONNECTED` to the rest of the system?**
  _92 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Search & Repository` be split into smaller, more focused modules?**
  _Cohesion score 0.07108081791626096 - nodes in this community are weakly interconnected._