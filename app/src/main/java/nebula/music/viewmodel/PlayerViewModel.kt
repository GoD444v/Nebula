package nebula.music.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import nebula.music.data.LyricsRepository
import nebula.music.data.SearchRepository
import nebula.music.data.download.NebulaDownloads
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.db.entities.LocalSong
import nebula.music.data.db.entities.PlayHistoryEntity
import nebula.music.data.lyrics.LyricsTranslator
import nebula.music.data.lyrics.Romanizer
import nebula.music.data.models.LyricsResponse
import nebula.music.data.models.LyricsStyleStore
import nebula.music.data.models.SearchResult
import nebula.music.player.NextAction
import nebula.music.player.PlaybackModes
import nebula.music.player.PlaybackService
import nebula.music.player.PlayerManager
import nebula.music.data.listen.ListenClient
import nebula.music.data.listen.SyncManager
import nebula.music.data.listen.PlaybackActionPayload
import nebula.music.data.listen.PlaybackActions
import nebula.music.data.listen.TrackInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Where the song that is still playing in PlaybackService gets remembered, so a
// relaunch can paint it again. Fields are newline separated and URL-encoded.
private const val NOW_PLAYING_KEY = "now_playing"
// Full queue persistence (Echo's saveQueueToDisk concept): all songs + currentIndex
// + positionMs, so Next/Previous work immediately after relaunch.
private const val QUEUE_KEY = "queue_state"
// Autoplay: at the end of the queue, wrap to the top instead of stopping. ON by default —
// stopping dead after one album is the surprising outcome, not the expected one.
private const val AUTOPLAY_KEY = "autoplay_on"
// Pause history: when on, playback starts are simply not written to the History screen.
private const val PAUSE_HISTORY_KEY = "pause_history"
// Crossfade: fade the last seconds of a track down and the next track back up. Off by
// default — it changes how every single track ends, so it stays opt-in.
private const val CROSSFADE_KEY = "crossfade_on"
// The fade window. 3s matches the common player default: long enough to hear, short
// enough that skipping away from a track never spends much of its life at low volume.
private const val CROSSFADE_MS = 3000
// Saved beside the queue it describes: a queue the user built by hand must stay a
// hand-built queue across a restart, or the first tap on an album would wipe it.
private const val HAND_BUILT_KEY = "queue_hand_built"
// Remaining songs in a radio queue that triggers a top-up fetch. Big enough that the fetch
// finishes well before the user gets there, small enough that one round of ~15 new songs
// carries the queue most of the way to the next.
private const val TOPUP_AT = 5
// Marks a queue entry as a file on this device rather than a YouTube video.
private const val LOCAL_PREFIX = "local:"

class PlayerViewModel : ViewModel() {
    var isPlaying by mutableStateOf(false)
        private set
    // ponytail: plain Strings, upgrade to Song data class when real queue lands
    var currentSongTitle by mutableStateOf("Select a song to play")
        private set
    var currentArtist by mutableStateOf("")
        private set
    var currentVideoId by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    /**
     * The player has the song and is buffering it — audio has NOT started.
     *
     * Separate from [isLoading] because that one only covers "we finished asking
     * for a stream URL". Between that and the first buffered frame the old UI
     * showed the title, a still slider and a `0:00 / 0:00` pair, which reads as
     * a dead app rather than a song about to play.
     */
    var isBuffering by mutableStateOf(false)
        private set
    var upNext by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    var queueList by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    // The index of the playing song inside `queue`. Public state, not a getter, so the
    // queue screen can highlight by POSITION: comparing videoId lights up every copy of a
    // duplicated song, which radio and repeat-heavy playlists both produce.
    var currentQueueIndex by mutableStateOf(-1)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    // Position saved on restore, used when the user presses play to resume.
    private var savedPositionMs = 0L
    // Frame-rate position for the LYRICS PANEL ONLY. The slider above is happy at
    // 2Hz, but every word/character highlight style interpolates against this, and
    // at 2Hz Apple V2's per-character fill and Glow's pulse visibly stutter. Kept as
    // a separate counter so a 30fps tick never recomposes the whole player screen
    // (the Slider, the title, the whole column) thirty times a second.
    var lyricsPositionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    /**
     * Where the current queue came from: `"playlist:7"`, `"local"`, `"browse:<id>"`,
     * or null for anything that is not a set (a search tap, Home, a radio).
     *
     * Shuffle and repeat describe the queue that is playing, not the app. This is what
     * lets a playlist screen show ITS OWN mode rather than whatever the last thing you
     * played left behind, and [enterScope] is what clears the old one when you start
     * playing somewhere else. Deliberately NOT persisted: a mode that outlives the queue
     * it describes is the leak this replaced — it used to come back after a restart and
     * quietly shuffle every list in the app.
     */
    var queueScope by mutableStateOf<String?>(null)
        private set
    var isLoadingRadio by mutableStateOf(false)
        private set
    var autoplayOn by mutableStateOf(true)
        private set
    var pauseHistory by mutableStateOf(false)
        private set
    var crossfadeOn by mutableStateOf(false)
        private set
    /**
     * True once the user has edited the queue themselves ("Play next" / "Add to queue").
     *
     * While set, tapping a song inside an album or playlist plays only that song and
     * leaves the queue alone: an auto-loaded album would silently discard songs the user
     * picked on purpose. Cleared by emptying the queue, or by any fresh play* that
     * replaces the queue outright.
     */
    private var queueIsHandBuilt = false
    /**
     * True while the queue is a radio, so it may keep topping itself up.
     *
     * An album or playlist the user opened deliberately ends on its own; a radio built
     * from similar songs never does. Turned off the moment a top-up adds nothing new,
     * which is what stops the fetch-forever loop.
     */
    private var radioGrows = false

    // Batch 6b: synced lyrics for the current song. Null = none yet / none found.
    private val _lyrics = MutableStateFlow<LyricsResponse?>(null)
    val lyrics: StateFlow<LyricsResponse?> = _lyrics.asStateFlow()
    // Index into lyrics.lines whose timeMs is the closest at-or-before positionMs. -1 = none.
    private val _lyricIndex = MutableStateFlow(-1)
    val lyricIndex: StateFlow<Int> = _lyricIndex.asStateFlow()
    // Separate from lyrics==null: null means BOTH loading and not-found, which
    // stuck the UI on "Finding lyrics…" forever whenever a song had no match.
    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val playerManager = PlayerManager()
    private val searchRepository = SearchRepository()
    private val lyricsRepository = LyricsRepository()
    private var syncManager: SyncManager? = null
    private var lyricsJob: Job? = null
    private val queue = mutableListOf<SearchResult>()
    private var queueIndex = -1
    // Where Next came from, newest last. Shuffle jumps randomly with no path back,
    // so Previous pops this instead of walking the unshuffled list backwards.
    // Fresh queue = no past: every play* entry below clears it.
    // ponytail: unbounded deque; a session holds hundreds of entries max, a cap when it matters.
    private val history = ArrayDeque<Int>()
    /**
     * Publishes the queue and the playing index together.
     *
     * `queueList` and `currentQueueIndex` are two independent mutableStateOf fields. Written
     * separately, Compose can recompose between them and paint one frame where the list is
     * new but the highlight is stale. Both writes land in the same frame, so Compose
     * coalesces them. Every queue mutation calls this; nothing assigns `queueList` directly.
     */
    private fun publishQueue() {
        queueList = queue.toList()
        currentQueueIndex = queueIndex
    }
    private var ticker: Job? = null
    // The stream-URL resolution. Tracked like lyricsJob so a superseded fetch cannot land
    // late and start audio for a song the user has since skipped or deleted.
    private var streamJob: Job? = null
    private var radioJob: Job? = null
    private var topUpJob: Job? = null
    private var lyricsTicker: Job? = null
    // Crossfade ramp state. The target is remembered so a ramp still heading the
    // right way is not restarted every tick; a flipped target (a seek back out of
    // the fade window) cancels and ramps the other direction.
    private var fadeJob: Job? = null
    private var fadeTarget = 1f

    /**
     * Whether the device currently has no usable connection.
     *
     * Only used to word the lyrics empty state honestly: every lyrics provider is a
     * network API, so offline a fetch fails and "no lyrics found" would be a claim the
     * app cannot actually support. Kept coarse on purpose — this is a message, not a
     * connectivity check that gates behaviour.
     */
    val isOffline: Boolean
        get() = runCatching {
            val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return@runCatching false
            val active = cm.activeNetwork ?: return@runCatching true
            val caps = cm.getNetworkCapabilities(active) ?: return@runCatching true
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    private var appContext: Context? = null

    // Echo's resolve-on-error state: the last song handed to the player, and
    // how many times it has already been re-resolved after a failure.
    private var lastPlayedItem: SearchResult? = null
    private var streamRetryCount = 0
    // The stall clock. Separate from streamRetryCount on purpose — see its own doc.
    private val stallWatchdog = StallWatchdog()
    /**
     * Whether the user wants this song playing.
     *
     * ExoPlayer stays in STATE_BUFFERING when it is paused mid-buffer, so the watchdog
     * cannot tell "waiting to start" from "the user paused it". Without this, pausing a
     * buffering song counted as a stall and its retry called playFromUrl -> play(),
     // un-pausing a song the user had just stopped.
     */
    private var playRequested = true

    fun attach(context: Context) {
        appContext = context
        // Autoplay and the hand-built flag are restored first. Shuffle and repeat are NOT
        // read from prefs: they belong to the queue that was playing, and queueScope is
        // null on a fresh ViewModel, so the modes start off rather than belonging to a
        // list nobody is playing any more.
        context.getSharedPreferences("nebula", Context.MODE_PRIVATE).let { prefs ->
            autoplayOn = prefs.getBoolean(AUTOPLAY_KEY, true)
            pauseHistory = prefs.getBoolean(PAUSE_HISTORY_KEY, false)
            crossfadeOn = prefs.getBoolean(CROSSFADE_KEY, false)
            queueIsHandBuilt = prefs.getBoolean(HAND_BUILT_KEY, false)
        }
        playerManager.setRepeatMode(repeatMode)
        playerManager.setShuffleModeEnabled(shuffleOn)
        // Back on Home finishes the activity, but PlaybackService keeps the music
        // going — so the next launch starts with a brand-new ViewModel. Put the song
        // that is still playing back onto the UI before anything else reads it.
        restoreNowPlaying()
        // ponytail: controller events drive the glow; no polling loop for state
        playerManager.onIsPlayingChanged = { playing -> isPlaying = playing }
        // The player's own buffering state, which is NOT the same thing as finishing
        // the URL resolve. Nothing read it before, so the gap between "we have a URL"
        // and "audio is coming" rendered as a still slider and a 0:00 / 0:00 pair.
        playerManager.onPlaybackStateChanged = { state ->
            isBuffering = state == Player.STATE_BUFFERING
        }
        // Notification transport: the session timeline holds one item, so the
        // custom prev/next buttons route here instead of ExoPlayer seeks.
        PlaybackService.prevHandler = { previous() }
        PlaybackService.nextHandler = { next() }
        // Song played to the end: advance exactly like a Next tap.
        playerManager.onTrackEnded = { handleAutoAdvance() }
        // A stream that died mid-song: drop the stale URL and try once more
        // with a fresh one, Echo's expired-url handling.
        playerManager.onPlaybackError = { retryPlayback() }
        playerManager.initializePlayer(context)
        // ponytail: one 500ms ticker for the slider; nothing finer until lyrics need it
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                positionMs = playerManager.positionMs()
                durationMs = playerManager.durationMs()
                // A stream that stalls emits no PlaybackException, so onPlaybackError
                // never fires and retryPlayback() never runs — the song sat at 0:00
                // forever. The watchdog turns that silence into an action, and owns
                // its own retry budget so a stall on a downloaded song (which
                // retryPlayback refuses to touch) still stops after three.
                when (stallWatchdog.onTick(System.currentTimeMillis(), isBuffering && playRequested)) {
                    StallAction.Retry -> retryPlayback()
                    StallAction.GiveUp -> currentSongTitle = "Couldn't load audio"
                    StallAction.None -> Unit
                }
                updateLyricIndex()
                tickCrossfade()
                delay(500)
            }
        }
        // ~30fps, read only by LyricsScreen. The active LINE still changes on the
        // 500ms ticker above (a line lasts seconds — 2Hz is imperceptible there);
        // only the within-line word/character fill needs frame rate.
        lyricsTicker?.cancel()
        lyricsTicker = viewModelScope.launch {
            while (true) {
                lyricsPositionMs = playerManager.positionMs()
                delay(32)
            }
        }
    }

    // Read the saved queue back (Echo's restoreQueue concept). Restores all songs,
    // the current index, and the playback position — so Next/Previous work and the
    // song resumes from where it left off. Falls back to the old single-song key.
    private fun restoreNowPlaying() {
        val prefs = appContext?.getSharedPreferences("nebula", Context.MODE_PRIVATE) ?: return

        // Try the full queue first
        val queueRaw = prefs.getString(QUEUE_KEY, null)
        if (queueRaw != null) {
            val state = PlaybackModes.decodeQueue(queueRaw)
            if (state != null) {
                queue.clear()
                state.songs.forEach { s ->
                    queue.add(SearchResult(s.videoId, s.title, s.artist, s.thumbnailUrl))
                }
                queueIndex = state.currentIndex
                publishQueue()
                val current = queue[queueIndex]
                currentVideoId = current.videoId
                currentSongTitle = current.title
                currentArtist = current.artist
                savedPositionMs = state.positionMs
                isPlaying = false
                return
            }
        }

        // Fall back to the old single-song key
        val raw = prefs.getString(NOW_PLAYING_KEY, null) ?: return
        val f = raw.split('\n').map { Uri.decode(it) }
        if (f.size < 4) return
        val item = SearchResult(videoId = f[0], title = f[1], artist = f[2], thumbnailUrl = f[3])
        queue.clear()
        queue.add(item)
        queueIndex = 0
        publishQueue()
        currentVideoId = item.videoId
        currentSongTitle = item.title
        currentArtist = item.artist
        savedPositionMs = 0L
        isPlaying = false
    }

    // Save the full queue + currentIndex + positionMs (Echo's saveQueueToDisk).
    // Called on onCleared so the queue survives app restart.
    private fun saveQueue() {
        val prefs = appContext?.getSharedPreferences("nebula", Context.MODE_PRIVATE) ?: return
        if (queue.isEmpty()) return
        val songs = queue.map {
            PlaybackModes.SongInfo(it.videoId, it.title, it.artist, it.thumbnailUrl)
        }
        val encoded = PlaybackModes.encodeQueue(songs, queueIndex, positionMs)
        prefs.edit().putString(QUEUE_KEY, encoded).putBoolean(HAND_BUILT_KEY, queueIsHandBuilt).apply()
    }

    // Remember whatever starts playing, so a relaunch can show it again.
    private fun saveNowPlaying(item: SearchResult) {
        val prefs = appContext
            ?.getSharedPreferences("nebula", Context.MODE_PRIVATE) ?: return
        prefs.edit()
            .putString(
                NOW_PLAYING_KEY,
                listOf(item.videoId, item.title, item.artist, item.thumbnailUrl)
                    .joinToString("\n") { Uri.encode(it) }
            )
            .apply()
    }

    /**
     * Adopts [scope] as the source of the queue about to be built, clearing shuffle and
     * repeat if it is a different source than the one already playing.
     *
     * Called by every entry point that replaces the queue. [PlaybackModes.onEnterScope]
     * owns the rule; this only applies it to the live player. Re-entering the same scope
     * is a no-op, so tapping a second song inside the playlist you are already playing
     * from keeps the modes you just set there.
     */
    private fun enterScope(scope: String?) {
        val (shuffle, repeat) = PlaybackModes.onEnterScope(
            currentScope = queueScope,
            newScope = scope,
            shuffle = shuffleOn,
            repeat = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> PlaybackModes.REPEAT_ONE
                Player.REPEAT_MODE_ALL -> PlaybackModes.REPEAT_ALL
                else -> PlaybackModes.REPEAT_OFF
            }
        )
        queueScope = scope
        if (shuffle != shuffleOn) {
            shuffleOn = shuffle
            playerManager.setShuffleModeEnabled(shuffle)
        }
        val mapped = when (repeat) {
            PlaybackModes.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            PlaybackModes.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        if (mapped != repeatMode) {
            repeatMode = mapped
            playerManager.setRepeatMode(mapped)
        }
    }

    /** Shuffle as [scope] should show it: on only while that source owns the queue. */
    fun shuffleFor(scope: String?): Boolean = shuffleOn && queueScope == scope

    /** Repeat as [scope] should show it, for the same reason as [shuffleFor]. */
    fun repeatFor(scope: String?): Int = if (queueScope == scope) repeatMode else Player.REPEAT_MODE_OFF

    /**
     * The shuffle-ON branch both playlist screens share, or the OFF branch when the tap was
     * a second one. Returns whether the caller should now start playing.
     *
     * Split out because "turn shuffle on" is identical for a streamed list and a local one,
     * and duplicating it would be how the two screens' shuffle buttons drift apart.
     */
    private fun beginShuffle(scope: String?): Boolean {
        if (shuffleFor(scope)) {
            toggleShuffle()
            return false
        }
        enterScope(scope)
        shuffleOn = true
        playerManager.setShuffleModeEnabled(true)
        return true
    }

    /** [shufflePlayAll] for songs on the device. */
    fun shufflePlayLocalSongs(songs: List<LocalSong>, scope: String?) {
        if (beginShuffle(scope)) playLocalSongs(songs.shuffled(), 0, scope)
    }

    /**
     * Playlist Shuffle: starts this list at a random song with shuffle on.
     *
     * Tapping while this scope already has shuffle on turns it OFF and leaves playback
     * running — Shuffle is a button that starts music, not a mute, so it must never stop
     * what is playing. [items] is shuffled on the way in purely so the FIRST song is
     * random; ExoPlayer randomises every later song via [shuffleOn].
     */
    fun shufflePlayAll(items: List<SearchResult>, scope: String?) {
        if (beginShuffle(scope)) playAll(items.shuffled(), scope)
    }

    /**
     * Plays one song, replacing whatever was queued.
     *
     * The entry point for tapping a song with no meaningful neighbours — a search result,
     * a single track. The previous call site used [playYouTubeSong], which takes loose
     * strings; taking the whole [SearchResult] keeps the artwork and matches every other
     * entry point.
     */
    fun play(item: SearchResult) {
        enterScope(null)
        queue.clear()
        history.clear()
        queue.add(item)
        queueIndex = 0
        queueIsHandBuilt = false
        publishQueue()
        resolveAndPlay(item)
        // A song tapped on its own has no neighbours, so fetch them now instead of waiting
        // for it to end: Up Next is already populated while the first song plays.
        startRadio()
    }

    fun playYouTubeSong(videoId: String, title: String, artist: String, artUrl: String = "") {
        play(SearchResult(videoId, title, artist, artUrl))
    }

    /** "Play All" on an album/playlist page: the whole list becomes the queue, first track plays. */
    fun playAll(items: List<SearchResult>, scope: String? = null) {
        if (items.isEmpty()) return
        enterScope(scope)
        queue.clear()
        history.clear()
        queue.addAll(items)
        queueIndex = 0
        queueIsHandBuilt = false
        radioGrows = false
        publishQueue()
        resolveAndPlay(queue[0])
    }

    /**
     * Plays [item] and queues similar songs behind it.
     *
     * The entry point for tapping a song outside a playlist — search results, home rows,
     * an album or artist track list. Playing exactly one song and stopping there is the
     * wrong outcome from a list: the user has signalled they want to keep listening.
     *
     * Falls back to playing the song alone if the related fetch fails, so a radio with no
     * related tracks is still audible rather than silent.
     */
    fun startRadioFrom(item: SearchResult) {
        enterScope(null)
        queue.clear()
        history.clear()
        queue.add(item)
        queueIndex = 0
        publishQueue()
        // startRadio() reads currentSong(), which is the queue head — so the item must be
        // in place first. Calling it before this seeded the radio off the *previous* song.
        startRadio()
        resolveAndPlay(item)
    }

    /**
     * Plays [item] now, with [rest] queued behind it in the order given.
     *
     * The tapped song goes at the HEAD, not the tail. Appending it and calling
     * [playAll] played `queue[0]` — the first song — so tapping the second song in a
     * playlist played the first one. "Keep the rest queued behind it" means behind the
     * tapped song, not behind the list.
     *
     * This is also the auto-load point for album and playlist screens: one call, so the
     * lockout below covers every screen that queues a whole set behind the tapped song.
     */
    fun playFromHere(item: SearchResult, rest: List<SearchResult>, scope: String? = null) {
        enterScope(scope)
        // A queue the user assembled is theirs. Reloading the album discards songs they
        // chose on purpose, and falling back to play() would too — it clears the queue.
        // So the tapped song goes in right after the current one and plays: the hand-built
        // queue keeps every song, and Previous still walks back through it.
        if (queueIsHandBuilt && queue.isNotEmpty()) {
            val at = (queueIndex + 1).coerceIn(0, queue.size)
            queue.add(at, item)
            history.addLast(queueIndex)
            queueIndex = at
            publishQueue()
            resolveAndPlay(item)
            return
        }
        queue.clear()
        history.clear()
        queue.add(item)
        // Filter rather than bail out. Returning early when the tapped song is also in
        // [rest] left the queue empty, so tapping a row emptied the player instead.
        queue.addAll(rest.filter { it.videoId != item.videoId })
        queueIndex = 0
        radioGrows = false
        publishQueue()
        resolveAndPlay(item)
    }

    /** The queue as the UI sees it, so a menu can show what is coming. */
fun currentQueue(): List<SearchResult> = queue.toList()

/**
 * Queues [item] to play immediately after the current song.
 *
 * Distinct from adding to the end: "Play next" inserts after the playing track, which is
 * what someone means when they reach for it from a song they just found.
 */
fun playNext(item: SearchResult) {
    if (queue.isEmpty()) {
        play(item)
    } else {
        val at = (queueIndex + 1).coerceIn(0, queue.size)
        queue.add(at, item)
        publishQueue()
    }
    // After play(), never before: play() clears the lockout because a plain tap replaces
    // the queue, but choosing a song by hand is the one case that must SET it.
    queueIsHandBuilt = true
}

/**
     * [items] play next as a block, in the order given.
     *
     * ONE insert of the whole block, not a call per song: [playNext] puts each song at the
     * same slot (just after the current one), so inserting them one by one would leave the
     * LAST song of the playlist playing first. Inserting the block in order avoids having to
     * reverse it and sidesteps that whole question.
     */
    fun playNext(items: List<SearchResult>) {
        if (items.isEmpty()) return
        if (queue.isEmpty()) {
            // Nothing playing: the first song becomes the current one and the rest go
            // behind it. play() clears the hand-built lockout, so the flag is set after.
            play(items.first())
            addToQueue(items.drop(1))
            return
        }
        val at = (queueIndex + 1).coerceIn(0, queue.size)
        queue.addAll(at, items)
        publishQueue()
        queueIsHandBuilt = true
    }

    /** [items] appended to the end of the queue, in order. */
    fun addToQueue(items: List<SearchResult>) {
        if (items.isEmpty()) return
        items.forEach { addToQueue(it) }
    }

    /** Queues [item] at the end of the list. */
fun addToQueue(item: SearchResult) {
    if (queue.isEmpty()) {
        play(item)
    } else {
        queue.add(item)
        publishQueue()
    }
    queueIsHandBuilt = true
}

    /**
     * Plays the queue entry at [index] without disturbing anything else in the queue.
     *
     * Replaces the inline queue list in FullSheetPlayer, which called playYouTubeSong ->
     * play() -> queue.clear(); history.clear(). Tapping a queue row deleted the entire
     * queue. Tapping the row that is already playing toggles play/pause instead of
     * restarting the song from zero.
     */
    fun playAtQueueIndex(index: Int) {
        val item = queue.getOrNull(index) ?: return
        if (index == queueIndex) {
            togglePlay()
            return
        }
        history.addLast(queueIndex)
        queueIndex = index
        publishQueue()
        resolveAndPlay(item)
    }

    /**
     * Removes the queue entry at [index].
     *
     * Repair happens in the same operation as the deletion, because `history` holds
     * INDICES: leaving it stale would make previous() land on the wrong song with no
     * visible cause. Removing the song that is playing advances into the vacated slot;
     * removing any other song does not restart playback. Emptying the queue stops playback
     * and clears the header fields, which is what makes MainActivity hide the MiniPlayer.
     */
    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        val wasCurrent = index == queueIndex
        // Both helpers take the PRE-removal size and derive the post-removal size
        // themselves. Captured before removeAt, because reading queue.size afterwards
        // passed the already-shortened size: removing the playing song of a 4-song queue
        // then computed sizeAfter=2 and rewound to index 1, replaying the previous track
        // and skipping the song that had taken its slot.
        val sizeBefore = queue.size
        val repaired = PlaybackModes.repairHistory(
            history = history.toList(),
            queueSizeAfterRemoval = sizeBefore - 1,
            removedIndex = index
        )
        queue.removeAt(index)
        history.clear()
        history.addAll(repaired)
        queueIndex = PlaybackModes.newIndexAfterRemoval(sizeBefore, queueIndex, index)
        publishQueue()
        when {
            queue.isEmpty() -> {
                playerManager.stopPlayback()
                queueIsHandBuilt = false
                radioGrows = false
                // An in-flight URL resolution for the song just deleted would otherwise
                // land after stopPlayback and start audio with no mini player showing.
                streamJob?.cancel()
                currentVideoId = ""
                currentSongTitle = "Select a song to play"
                currentArtist = ""
                isPlaying = false
                isLoading = false
                savedPositionMs = 0L
                _lyrics.value = null
                _lyricIndex.value = -1
                lyricsJob?.cancel()
                _isLoadingLyrics.value = false
            }
            wasCurrent -> queue.getOrNull(queueIndex)?.let { resolveAndPlay(it) }
        }
    }

    fun playQueueItem(item: SearchResult) {
        queue.add(item)
        history.clear()
        queueIndex = queue.lastIndex
        publishQueue()
        resolveAndPlay(item)
    }

    fun next() {
        when (val action = decideNext()) {
            is NextAction.PlayQueue -> {
                history.addLast(queueIndex)
                queueIndex = action.index
                // The queue list itself is unchanged here — only which entry is current.
                // Without this the queue sheet's highlight stays frozen on the old row
                // forever, because currentQueueIndex is written only by publishQueue().
                publishQueue()
                resolveAndPlay(queue[queueIndex])
            }
            is NextAction.PlayUpNext -> {
                val current = queue.getOrNull(queueIndex)?.videoId
                val item = upNext.getOrNull(action.index)?.takeIf { it.videoId != current }
                    ?: upNext.firstOrNull { it.videoId != current }
                    ?: return
                history.addLast(queueIndex)
                queue.add(item)
                queueIndex = queue.lastIndex
                publishQueue()
                resolveAndPlay(item)
            }
            NextAction.ReplayCurrent -> queue.getOrNull(queueIndex)?.let { resolveAndPlay(it) }
            NextAction.Stop -> if (autoplayOn) startRadio(advanceAfter = true)
        }
        // Keep the radio stocked rather than waiting for it to run dry: the tail is
        // replaced before the user ever hears the end of it.
        if (radioGrows && queue.size - queueIndex <= TOPUP_AT) topUpRadio()
    }

    private fun topUpRadio() {
        val seed = queue.getOrNull(queueIndex) ?: return
        if (topUpJob?.isActive == true) return
        topUpJob = viewModelScope.launch {
            val more = try {
                searchRepository.getRelatedSongs(seed.videoId)
            } catch (_: Exception) {
                emptyList()
            }
            if (more.isEmpty()) {
                radioGrows = false
                return@launch
            }
            val merged = PlaybackModes.appendRadio(
                queue = queue.map { it.videoId },
                more = more.map { it.videoId }
            )
            if (merged == null) {
                // Same tracks as the last fetch. Retrying would fetch forever and grow
                // nothing, so stop treating this queue as an endless radio.
                radioGrows = false
                return@launch
            }
            val byId = queue.associateBy { it.videoId } + more.associateBy { it.videoId }
            queue.clear()
            merged.forEach { id -> byId[id]?.let(queue::add) }
            publishQueue()
        }
    }

    // Song ended on its own: same decision as a manual Next tap.
    private fun handleAutoAdvance() = next()

    private fun decideNext(): NextAction = PlaybackModes.next(
        queueSize = queue.size,
        queueIndex = queueIndex,
        upNextSize = upNext.size,
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> PlaybackModes.REPEAT_ONE
            Player.REPEAT_MODE_ALL -> PlaybackModes.REPEAT_ALL
            else -> PlaybackModes.REPEAT_OFF
        },
        shuffleOn = shuffleOn,
        autoplayOn = autoplayOn
    )

    fun previous() {
        val back = history.removeLastOrNull()
        if (back != null && back != queueIndex) {
            queueIndex = back
            publishQueue()
            queue.getOrNull(queueIndex)?.let { resolveAndPlay(it) }
        } else if (queueIndex > 0) {
            queueIndex--
            publishQueue()
            resolveAndPlay(queue[queueIndex])
        }
    }

    fun seekTo(ms: Long) {
        playerManager.seekTo(ms)
        positionMs = ms
        updateLyricIndex()
        broadcastIfHost(PlaybackActions.SEEK, ms)
    }

    /**
     * Off → all → one → off.
     *
     * [scope] is the screen asking. A non-null scope CLAIMS ownership first, without
     * clearing anything, so that playlist's button lights up and matches the live mode:
     * pressing Repeat on a playlist you are not currently playing must not silently throw
     * away the shuffle you set on the one that is. The default null is the player sheet,
     * which acts on whatever is playing and therefore leaves ownership alone.
     */
    fun toggleRepeat(scope: String? = null) {
        if (scope != null) queueScope = scope
        val next = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repeatMode = next
        playerManager.setRepeatMode(next)
    }

    fun toggleShuffle() {
        shuffleOn = !shuffleOn
        playerManager.setShuffleModeEnabled(shuffleOn)
    }

    fun toggleAutoplay() {
        autoplayOn = !autoplayOn
        appContext?.getSharedPreferences("nebula", Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(AUTOPLAY_KEY, autoplayOn)?.apply()
    }

    fun togglePauseHistory() {
        pauseHistory = !pauseHistory
        appContext?.getSharedPreferences("nebula", Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(PAUSE_HISTORY_KEY, pauseHistory)?.apply()
    }

    fun toggleCrossfade() {
        crossfadeOn = !crossfadeOn
        appContext?.getSharedPreferences("nebula", Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(CROSSFADE_KEY, crossfadeOn)?.apply()
        // Switching it off mid-ramp must not leave the player stuck below full volume.
        if (!crossfadeOn) {
            fadeJob?.cancel()
            fadeJob = null
            fadeTarget = 1f
            playerManager.setVolume(1f)
        }
    }

    /**
     * Simple crossfade, two ticker rules and nothing else.
     *
     * Near the end of a playing track, ramp the volume down over what is left of it.
     * On a playing track that is NOT near its end while the volume is still low — a
     * new song after the fade-out, a seek back out of the window, a resume — ramp
     * back up. Every path (natural end, manual Next during a fade, pause/resume,
     * seek) lands on one of the two rules, so there is no per-event hook to miss.
     * Fade-out-then-fade-in on one player, not a true overlap — the agreed scope.
     */
    private fun tickCrossfade() {
        if (!crossfadeOn) {
            if (playerManager.volume() != 1f) playerManager.setVolume(1f)
            return
        }
        val remaining = durationMs - positionMs
        val nearEnd = durationMs > 0 && positionMs in 1..durationMs && remaining in 0..CROSSFADE_MS
        when {
            nearEnd && isPlaying ->
                fadeVolumeTo(0f, remaining.coerceIn(150L, CROSSFADE_MS.toLong()))
            !nearEnd && isPlaying && playerManager.volume() < 1f ->
                fadeVolumeTo(1f, CROSSFADE_MS.toLong())
            // Paused or idle: hold the current volume; the next tick re-picks a rule.
            else -> Unit
        }
    }

    /** Ramp the player volume to `target` over `durationMs` in 50ms steps. */
    private fun fadeVolumeTo(target: Float, durationMs: Long) {
        if (fadeJob?.isActive == true && fadeTarget == target) return
        fadeJob?.cancel()
        fadeTarget = target
        val start = playerManager.volume()
        if (start == target) return
        val steps = (durationMs / 50).coerceAtLeast(1L)
        fadeJob = viewModelScope.launch {
            for (i in 1..steps) {
                playerManager.setVolume(start + (target - start) * i / steps)
                delay(durationMs / steps)
            }
        }
    }

    /**
     * The playing song with all its metadata. The separate `currentSongTitle` /
     * `currentArtist` / `currentVideoId` fields have no thumbnail, so anything that
     * needs artwork — saving for offline, sharing — has to come from the queue entry
     * rather than be reassembled from scalars.
     */
    fun currentSong(): SearchResult? = queue.getOrNull(queueIndex)

    // Echo's Start Radio: fresh related songs replace everything after the
    // current one. Empty or failed fetch leaves the queue untouched.
    fun startRadio(advanceAfter: Boolean = false) {
        // Explicit rather than leaning on the getOrNull below: an emptied queue leaves
        // queueIndex at -1, and mergeRadio would compute subList(0, 0) — safe, meaningless.
        if (queueIndex < 0) return
        val current = queue.getOrNull(queueIndex) ?: return
        isLoadingRadio = true
        // Only one radio fetch at a time. Two overlapping ones merge against whatever queue
        // exists when they LAND, not when they started.
        radioJob?.cancel()
        radioJob = viewModelScope.launch {
            val radio = try {
                searchRepository.getRelatedSongs(current.videoId)
            } catch (_: Exception) {
                emptyList()
            }
            isLoadingRadio = false
            if (radio.isEmpty()) return@launch
            // playAll / playFromHere / next can replace the queue while this fetch is in
            // flight, and merging then would staple radio songs onto an unrelated album.
            if (queue.getOrNull(queueIndex)?.videoId != current.videoId) return@launch
            val merged = PlaybackModes.mergeRadio(
                queue = queue.map { it.videoId },
                queueIndex = queueIndex,
                radio = radio.map { it.videoId }
            )
            val byId = (queue + radio).associateBy { it.videoId }
            queue.clear()
            merged.forEach { id -> byId[id]?.let(queue::add) }
            publishQueue()
            upNext = emptyList()
            radioGrows = true
            // mergeRadio leaves queueIndex on the current song, so the fetch alone would
            // refill the queue in silence. advanceAfter is the autoplay path: step into the
            // first radio song. Safe to recurse — the queue now holds 2+ songs, so next()
            // takes the wrap branch instead of coming back here.
            if (advanceAfter && queue.size > queueIndex + 1) next()
        }
    }

    /**
     * Plays [songs] from the device, starting at [startIndex].
     *
     * The whole list becomes the queue so next/previous and Up Next behave exactly as they
     * do for a playlist. Radio is switched off for these: a related-songs lookup needs a
     * real videoId, and asking for one with "local:/storage/..." just wastes a round trip.
     */
    fun playLocalSongs(songs: List<LocalSong>, startIndex: Int = 0, scope: String? = null) {
        if (songs.isEmpty()) return
        enterScope(scope)
        queue.clear()
        history.clear()
        queue.addAll(songs.map { song ->
            SearchResult(
                videoId = LOCAL_PREFIX + song.filePath,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUri
            )
        })
        queueIndex = startIndex.coerceIn(0, queue.lastIndex)
        queueIsHandBuilt = false
        radioGrows = false
        publishQueue()
        resolveAndPlay(queue[queueIndex])
    }

    /** The song playing right now, when it came from the device rather than the network. */
    fun isPlayingLocal(): Boolean =
        queue.getOrNull(queueIndex)?.videoId?.startsWith(LOCAL_PREFIX) == true

    private fun resolveAndPlay(item: SearchResult, positionMs: Long = 0L) {
        currentVideoId = item.videoId
        // A fresh user-initiated play gets a fresh error-retry budget.
        lastPlayedItem = item
        streamRetryCount = 0
        playRequested = true
        saveNowPlaying(item)
        currentSongTitle = "Loading..."
        currentArtist = item.artist
        isLoading = true
        isPlaying = false
        // New song → drop old lyrics immediately, then fetch in the background.
        // Every entry point (play, playAll, next, previous, queue tap) routes here.
        _lyrics.value = null
        _lyricIndex.value = -1
        lyricsJob?.cancel()
        _isLoadingLyrics.value = true
        // Any track change announces itself to the room — next/previous/pick all
        // funnel through here, and nothing else announces CHANGE_TRACK. Callers
        // set queueIndex before this runs, so currentSong() IS the new track.
        broadcastIfHost(PlaybackActions.CHANGE_TRACK, positionMs)
        lyricsJob = viewModelScope.launch {
            try {
                // Duration still belongs to the previous song here (the controller
                // hasn't loaded the new item), and stale values skew matching —
                // so fetch as unknown; providers rank by relevance instead.
                val fetched = try {
                    lyricsRepository.getSyncedLyrics(item.title, item.artist, 0L, videoId = item.videoId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                // The reading is filled in BEFORE anything is published: it is pure
                // string work with no model to download, so unlike the English below
                // it is ready the moment the lyrics are, and there is no reason to
                // push a second copy of the list just to add it.
                val romanizeOn = appContext?.let { LyricsStyleStore.getRomanize(it) } ?: true
                val reading = if (fetched != null && romanizeOn) Romanizer.fill(fetched) else fetched
                _lyrics.value = reading
                // Remember it. Every provider here is a network API, so without this a
                // downloaded song showed "No lyrics found" offline even after it had been
                // fetched successfully moments earlier — the fetch result was thrown away.
                if (reading != null) lyricsRepository.remember(item.title, item.artist, reading)
                updateLyricIndex()
                // English above the original. Deliberately runs AFTER the untranslated
                // lines are published and AFTER the loading flag is cleared: LyricsScreen
                // shows its spinner whenever that flag is set, even with lines in hand, so
                // translating first would hide the Japanese behind "Finding lyrics…" for
                // the whole first-time model download.
                // Read per song, not once at startup: the Settings switch has to be able
                // to turn this off, and the next song is when that lands. No context yet
                // means the pref was never readable, so fall in with its default.
                val translationOn = appContext?.let { LyricsStyleStore.getTranslation(it) } ?: true
                if (reading != null && reading.isSynced && translationOn) {
                    if (lyricsJob == coroutineContext[Job]) _isLoadingLyrics.value = false
                    // ponytail: only the synced lines get a translation. A plain-text
                    // response is one undivided blob with no line starts to hang a
                    // translation on; split on newlines if that ever matters.
                    // Translate the READING, not `fetched`: translate() rebuilds every
                    // line through a copy, so handing it the instance from before the
                    // reading was filled in would quietly drop the romanization that
                    // was just published.
                    val translated = LyricsTranslator.translate(reading)
                    // translate() hands back the identical instance when there was
                    // nothing to do, which keeps a needless list rebuild off the frame.
                    if (translated !== reading && lyricsJob == coroutineContext[Job]) {
                        _lyrics.value = translated
                    }
                }
            } finally {
                // A stale fetch landing after a skip must not clear the new one.
                if (lyricsJob == coroutineContext[Job]) _isLoadingLyrics.value = false
            }
        }
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            // A local song has no YouTube videoId, so it is queued under "local:<path>"
            // and recognised here. Its bytes are already on disk, so resolving a stream
            // URL first would only add a way to fail. Living inside resolveAndPlay means
            // next(), previous(), the history stack and queue taps all work on local
            // songs with no special cases anywhere else.
            if (item.videoId.startsWith(LOCAL_PREFIX)) {
                isLoading = false
                currentSongTitle = item.title
                playerManager.playFromUrl(
                    url = item.videoId.removePrefix(LOCAL_PREFIX),
                    videoId = item.videoId,
                    title = item.title,
                    artist = item.artist,
                    artUrl = item.thumbnailUrl,
                    positionMs = positionMs
                )
                recordPlay(item)
                return@launch
            }
            // A downloaded song does not need a stream URL to start: its bytes are already
            // in the download cache under the bare videoId, which is the custom cache key
            // the player now uses. Resolving a URL first would put a hard network
            // dependency in front of every play, so a downloaded song failed with the
            // radio off even though it was on disk.
            val offlineReady = NebulaDownloads.isDownloaded(item.videoId)
            val url = if (offlineReady) {
                NebulaDownloads.offlinePlaceholderUri(item.videoId)
            } else {
                try {
                    searchRepository.getAudioStreamUrl(item.videoId)
                } catch (_: Exception) {
                    null
                }
            }
            isLoading = false
            if (url != null) {
                currentSongTitle = item.title
                playerManager.playFromUrl(
                    url = url,
                    videoId = item.videoId,
                    title = item.title,
                    artist = item.artist,
                    artUrl = item.thumbnailUrl,
                    positionMs = positionMs
                )
                recordPlay(item)
                isPlaying = true
            } else {
                currentSongTitle = "Couldn't load audio"
            }
            viewModelScope.launch {
                upNext = try {
                    searchRepository.getRelatedSongs(item.videoId)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    /**
     * Echo's resolve-on-error. The stream URL died — expired, refused, or
     * dropped mid-song — so the cached URL is stale: drop it, resolve a fresh
     * one, and resume where playback stopped. Three tries per song, and then
     * it fails honestly instead of looping; the counter resets only when a new
     * song is requested, never on a brief "playing" blip, so a song that dies
     * two seconds in cannot retry forever. Local and downloaded songs are bytes
     * on disk — a failure there is not a URL problem, so they are left alone.
     *
     * `internal`, not private: Home and Search both put a Retry control on screen and
     * call this directly.
     */
    internal fun retryPlayback() {
        val item = lastPlayedItem ?: return
        if (item.videoId.startsWith(LOCAL_PREFIX) || NebulaDownloads.isDownloaded(item.videoId)) return
        if (streamRetryCount >= 3) {
            streamRetryCount = 0
            currentSongTitle = "Couldn't load audio"
            return
        }
        streamRetryCount++
        // Where it died, so the retry resumes instead of restarting.
        val resumeFrom = playerManager.positionMs()
        SearchRepository.invalidateStream(item.videoId)
        viewModelScope.launch {
            val url = try {
                searchRepository.getAudioStreamUrl(item.videoId)
            } catch (_: Exception) {
                null
            }
            // The user skipped to another song while this resolve was in flight —
            // the fresh URL belongs to a song that is no longer current.
            if (currentVideoId != item.videoId) return@launch
            if (url == null) {
                currentSongTitle = "Couldn't load audio"
                streamRetryCount = 0
                return@launch
            }
            currentSongTitle = item.title
            playerManager.playFromUrl(
                url = url,
                videoId = item.videoId,
                title = item.title,
                artist = item.artist,
                artUrl = item.thumbnailUrl,
                positionMs = resumeFrom
            )
        }
    }

    /**
     * One row per play for the History screen. Called only where playback
     * actually starts (both playFromUrl call sites in resolveAndPlay), so a
     * song whose URL failed to resolve never lands in history. A retry of the
     * same song deliberately does not record again — it is one play.
     *
     * The row snapshots title/artist/artwork rather than looking them up
     * later: history must render offline and survive the video disappearing
     * upstream. A DB failure must never break playback, hence runCatching.
     */
    private fun recordPlay(item: SearchResult) {
        if (pauseHistory) return
        if (item.videoId.isBlank()) return
        val dao = appContext?.let { NebulaDatabase.getDatabase(it).playHistoryDao() } ?: return
        viewModelScope.launch {
            runCatching {
                dao.insert(
                    PlayHistoryEntity(
                        videoId = item.videoId,
                        title = item.title,
                        artist = item.artist,
                        thumbnailUrl = item.thumbnailUrl.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    /**
     * positionMs → lyricIndex: the last line whose start time has passed.
     * Lines are sorted by timeMs, so this is one reverse scan. Runs on the
     * existing 500ms ticker plus seek + fetch — no extra timer.
     */
    private fun updateLyricIndex() {
        val lines = _lyrics.value?.lines
        if (lines.isNullOrEmpty()) {
            if (_lyricIndex.value != -1) _lyricIndex.value = -1
            return
        }
        val pos = positionMs
        var idx = lines.size - 1
        while (idx >= 0 && lines[idx].timeMs > pos) idx--
        if (idx != _lyricIndex.value) _lyricIndex.value = idx
    }

    fun togglePlay() {
        if (isPlaying) {
            playerManager.pauseAudio()
            isPlaying = false
            playRequested = false
            broadcastIfHost(PlaybackActions.PAUSE, positionMs)
        } else {
            playerManager.resumeAudio()
            playRequested = true
            // Reopened after Android killed the service: there is nothing left to
            // resume, so replay the restored song instead of tapping a dead button.
            if (playerManager.currentMediaItem() == null) {
                queue.getOrNull(queueIndex)?.let { resolveAndPlay(it, savedPositionMs) }
            } else {
                broadcastIfHost(PlaybackActions.PLAY, positionMs)
            }
        }
    }

    /** Wire the sync manager so this ViewModel can broadcast/apply Listen Together actions. */
    fun setSyncManager(manager: SyncManager) {
        syncManager = manager
        // Guest side: apply remote host actions to the local player. The socket
        // delivers on a background thread, but every branch here touches the
        // Media3 controller and Compose state — both need the main thread.
        manager.onApplyPlaybackAction = { action ->
            viewModelScope.launch { applyRemoteAction(action) }
        }
    }

    /** Host: push the room's current track/queue/position to a newly joined guest. */
    fun broadcastCurrentTrackToRoom() {
        val manager = syncManager ?: return
        if (manager.role != nebula.music.data.listen.RoomRole.HOST) return
        broadcastIfHost(PlaybackActions.CHANGE_TRACK, positionMs)
    }

    private fun broadcastIfHost(action: String, positionMs: Long) {        val manager = syncManager ?: return
        if (manager.role != nebula.music.data.listen.RoomRole.HOST) return
        val track = currentSong()
        manager.hostPlaybackChanged(
            action = action,
            positionMs = positionMs,
            trackInfo = track?.let {
                TrackInfo(id = it.videoId, title = it.title, artist = it.artist, thumbnailUrl = it.thumbnailUrl)
            },
            queue = queueList.map {
                TrackInfo(id = it.videoId, title = it.title, artist = it.artist, thumbnailUrl = it.thumbnailUrl)
            }
        )
    }

    private fun applyRemoteAction(action: PlaybackActionPayload) {
        val remotePosition = action.positionMs ?: 0L
        when (action.action) {
            PlaybackActions.PLAY -> {
                if (!isPlaying) playerManager.resumeAudio()
                isPlaying = true
                if (syncManager?.needsDriftCorrection(positionMs) == true) {
                    playerManager.seekTo(remotePosition)
                    positionMs = remotePosition
                }
            }
            PlaybackActions.PAUSE -> {
                playerManager.pauseAudio()
                isPlaying = false
                if (syncManager?.needsDriftCorrection(positionMs) == true) {
                    playerManager.seekTo(remotePosition)
                    positionMs = remotePosition
                }
            }
            PlaybackActions.SEEK -> {
                if (syncManager?.needsDriftCorrection(positionMs) == true) {
                    playerManager.seekTo(remotePosition)
                    positionMs = remotePosition
                }
            }
            PlaybackActions.CHANGE_TRACK -> {
                action.trackInfo?.let { track ->
                    val item = SearchResult(track.id, track.title, track.artist, track.thumbnailUrl.orEmpty())
                    queue.clear()
                    history.clear()
                    queue.add(item)
                    queueIndex = 0
                    publishQueue()
                    resolveAndPlay(item, remotePosition)
                }
            }
        }
    }

    fun release() {
        ticker?.cancel()
        lyricsTicker?.cancel()
        playerManager.releasePlayer()
    }

    override fun onCleared() {
        saveQueue()
        ticker?.cancel()
        lyricsTicker?.cancel()
        playerManager.releasePlayer()
        super.onCleared()
    }
}

/** What the watchdog wants the player to do about a song that will not start. */
internal enum class StallAction { None, Retry, GiveUp }

/**
 * Notices a song that is buffering far longer than any network hiccup explains.
 *
 * ExoPlayer stays in STATE_BUFFERING indefinitely when the server accepts the
 * connection and then sends nothing: it emits no error, so onPlaybackError never
 * fires, and the screen sits at 0:00 / 0:00 forever with nothing to tap. This is
 * the clock that turns "still waiting" into an action.
 *
 * Its own retry budget, deliberately separate from retryPlayback()'s. That counter
 * is reset by retryPlayback() whenever a fresh URL resolves successfully — and a
 * stalled stream resolves a URL fine every time, so sharing the counter means the
 * budget is restored mid-stall and this would re-resolve forever, every 10s, for
 * as long as the user left the app open.
 *
 * Pure: no clock, no Android, no player. The caller supplies nowMs, which is what
 * makes the whole state machine testable.
 */
internal class StallWatchdog(
    private val stallMs: Long = STALL_MS,
    private val maxRetries: Int = MAX_RETRIES
) {
    private companion object {
        /** Longer than a re-buffer on a bad network; shorter than a user giving up. */
        const val STALL_MS = 10_000L
        const val MAX_RETRIES = 3
    }

    private var stalledSinceMs = -1L
    private var retries = 0

    fun onTick(nowMs: Long, buffering: Boolean): StallAction = when {
        // Playing, idle or ended: the clock is meaningless, so forget everything.
        // This is what keeps a working song's earlier stall from costing retries.
        !buffering -> {
            stalledSinceMs = -1L
            retries = 0
            StallAction.None
        }
        stalledSinceMs < 0L -> {
            stalledSinceMs = nowMs
            StallAction.None
        }
        nowMs - stalledSinceMs < stallMs -> StallAction.None
        // Budget spent. Deliberately does NOT reset `retries`, so this stays GiveUp
        // on every later tick instead of oscillating back into Retry forever.
        retries >= maxRetries -> StallAction.GiveUp
        else -> {
            retries++
            // Restart the clock so the next retry gets a full window to succeed in.
            stalledSinceMs = nowMs
            StallAction.Retry
        }
    }
}
