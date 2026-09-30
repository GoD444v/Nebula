package com.example.nebula.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.nebula.data.LyricsRepository
import com.example.nebula.data.SearchRepository
import com.example.nebula.data.models.LyricsResponse
import com.example.nebula.data.models.SearchResult
import com.example.nebula.player.NextAction
import com.example.nebula.player.PlaybackModes
import com.example.nebula.player.PlayerManager
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
// Echo persists repeat/shuffle the same way (its RepeatModeKey / ShuffleModeKey);
// a relaunch restores the mode instead of silently resetting to sequential.
private const val REPEAT_MODE_KEY = "repeat_mode"
private const val SHUFFLE_KEY = "shuffle_on"
// Full queue persistence (Echo's saveQueueToDisk concept): all songs + currentIndex
// + positionMs, so Next/Previous work immediately after relaunch.
private const val QUEUE_KEY = "queue_state"

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
    var upNext by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    var queueList by mutableStateOf<List<SearchResult>>(emptyList())
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
    var isLoadingRadio by mutableStateOf(false)
        private set

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
    private var lyricsJob: Job? = null
    private val queue = mutableListOf<SearchResult>()
    private var queueIndex = -1
    private var ticker: Job? = null
    private var lyricsTicker: Job? = null
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context
        // Restore the saved playback mode first, so the controller gets it on
        // connect (PlayerManager holds it as pending until then).
        context.getSharedPreferences("nebula", Context.MODE_PRIVATE).let { prefs ->
            repeatMode = prefs.getInt(REPEAT_MODE_KEY, Player.REPEAT_MODE_OFF)
            shuffleOn = prefs.getBoolean(SHUFFLE_KEY, false)
        }
        playerManager.setRepeatMode(repeatMode)
        playerManager.setShuffleModeEnabled(shuffleOn)
        // Back on Home finishes the activity, but PlaybackService keeps the music
        // going — so the next launch starts with a brand-new ViewModel. Put the song
        // that is still playing back onto the UI before anything else reads it.
        restoreNowPlaying()
        // ponytail: controller events drive the glow; no polling loop for state
        playerManager.onIsPlayingChanged = { playing -> isPlaying = playing }
        // Song played to the end: advance exactly like a Next tap.
        playerManager.onTrackEnded = { handleAutoAdvance() }
        playerManager.initializePlayer(context)
        // ponytail: one 500ms ticker for the slider; nothing finer until lyrics need it
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                positionMs = playerManager.positionMs()
                durationMs = playerManager.durationMs()
                updateLyricIndex()
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
                queueList = queue.toList()
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
        queueList = queue.toList()
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
        prefs.edit().putString(QUEUE_KEY, encoded).apply()
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

    fun playYouTubeSong(videoId: String, title: String, artist: String, artUrl: String = "") {
        queue.clear()
        queue.add(SearchResult(videoId, title, artist, artUrl))
        queueIndex = 0
        queueList = queue.toList()
        resolveAndPlay(queue[0])
    }

    /** "Play All" on an album/playlist page: the whole list becomes the queue, first track plays. */
    fun playAll(items: List<SearchResult>) {
        if (items.isEmpty()) return
        queue.clear()
        queue.addAll(items)
        queueIndex = 0
        queueList = queue.toList()
        resolveAndPlay(queue[0])
    }

    fun playQueueItem(item: SearchResult) {
        queue.add(item)
        queueIndex = queue.lastIndex
        queueList = queue.toList()
        resolveAndPlay(item)
    }

    fun next() {
        when (val action = decideNext()) {
            is NextAction.PlayQueue -> {
                queueIndex = action.index
                resolveAndPlay(queue[queueIndex])
            }
            is NextAction.PlayUpNext -> {
                val current = queue.getOrNull(queueIndex)?.videoId
                val item = upNext.getOrNull(action.index)?.takeIf { it.videoId != current }
                    ?: upNext.firstOrNull { it.videoId != current }
                    ?: return
                queue.add(item)
                queueIndex = queue.lastIndex
                queueList = queue.toList()
                resolveAndPlay(item)
            }
            NextAction.ReplayCurrent -> queue.getOrNull(queueIndex)?.let { resolveAndPlay(it) }
            NextAction.Stop -> Unit
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
        shuffleOn = shuffleOn
    )

    fun previous() {
        if (queueIndex > 0) {
            queueIndex--
            resolveAndPlay(queue[queueIndex])
        }
    }

    fun seekTo(ms: Long) {
        playerManager.seekTo(ms)
        positionMs = ms
        updateLyricIndex()
    }

    fun toggleRepeat() {
        val next = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repeatMode = next
        appContext?.getSharedPreferences("nebula", Context.MODE_PRIVATE)
            ?.edit()?.putInt(REPEAT_MODE_KEY, next)?.apply()
        playerManager.setRepeatMode(next)
    }

    fun toggleShuffle() {
        shuffleOn = !shuffleOn
        appContext?.getSharedPreferences("nebula", Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(SHUFFLE_KEY, shuffleOn)?.apply()
        playerManager.setShuffleModeEnabled(shuffleOn)
    }

    // Echo's Start Radio: fresh related songs replace everything after the
    // current one. Empty or failed fetch leaves the queue untouched.
    fun startRadio() {
        val current = queue.getOrNull(queueIndex) ?: return
        isLoadingRadio = true
        viewModelScope.launch {
            val radio = try {
                searchRepository.getRelatedSongs(current.videoId)
            } catch (_: Exception) {
                emptyList()
            }
            isLoadingRadio = false
            if (radio.isEmpty()) return@launch
            val merged = PlaybackModes.mergeRadio(
                queue = queue.map { it.videoId },
                queueIndex = queueIndex,
                radio = radio.map { it.videoId }
            )
            val byId = (queue + radio).associateBy { it.videoId }
            queue.clear()
            merged.forEach { id -> byId[id]?.let(queue::add) }
            queueList = queue.toList()
            upNext = emptyList()
        }
    }

    private fun resolveAndPlay(item: SearchResult, positionMs: Long = 0L) {
        currentVideoId = item.videoId
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
        lyricsJob = viewModelScope.launch {
            try {
                // Duration still belongs to the previous song here (the controller
                // hasn't loaded the new item), and stale values skew matching —
                // so fetch as unknown; providers rank by relevance instead.
                _lyrics.value = try {
                    lyricsRepository.getSyncedLyrics(item.title, item.artist, 0L)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                updateLyricIndex()
            } finally {
                // A stale fetch landing after a skip must not clear the new one.
                if (lyricsJob == coroutineContext[Job]) _isLoadingLyrics.value = false
            }
        }
        viewModelScope.launch {
            val url = try {
                searchRepository.getAudioStreamUrl(item.videoId)
            } catch (_: Exception) {
                null
            }
            isLoading = false
            if (url != null) {
                currentSongTitle = item.title
                playerManager.playFromUrl(url, item.title, item.artist, item.thumbnailUrl, positionMs)
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
        } else {
            playerManager.resumeAudio()
            // Reopened after Android killed the service: there is nothing left to
            // resume, so replay the restored song instead of tapping a dead button.
            if (playerManager.currentMediaItem() == null) {
                queue.getOrNull(queueIndex)?.let { resolveAndPlay(it, savedPositionMs) }
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
