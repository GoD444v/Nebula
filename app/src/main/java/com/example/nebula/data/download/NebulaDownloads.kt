package com.example.nebula.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.example.nebula.NebulaApplication
import com.example.nebula.data.SearchRepository
import com.example.nebula.data.models.SearchResult
import com.example.nebula.player.NebulaDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Offline downloads, built on Media3's own [DownloadManager] rather than a
 * hand-rolled OkHttp loop. That choice buys retry, network requirements, the
 * foreground notification and restarts for free.
 *
 * The data-source chain is the load-bearing part, and it mirrors playback's:
 *
 *   download  :  [ playerCache (read-only) -> network ]  -> writes into downloadCache
 *   playback  :  [ downloadCache (read-only) -> [ playerCache -> network ] ]
 *
 * Because the download reads the player cache first, downloading a song you
 * already streamed reuses the bytes already on disk instead of fetching them
 * again. Because playback reads the download cache first, a saved song plays
 * from disk with no localUri check and no "is this downloaded?" branch.
 *
 * Both sides key on the URL returned by [SearchRepository.getAudioStreamUrl],
 * which memoises per videoId precisely so the two resolve to the same string —
 * a mismatch there makes playback silently miss the download and re-stream.
 */
@UnstableApi
object NebulaDownloads {

    private const val MAX_PARALLEL_DOWNLOADS = 3

    /** Placeholder URI: the resolver swaps in the real, short-lived stream URL. */
    private const val IDLE_URI_PREFIX = "https://nebula.local/"

    private val searchRepository = SearchRepository()

    /** videoId -> live [Download]. Seeded from Media3's index so it survives restarts. */
    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    val downloads: Flow<Map<String, Download>> = _downloads

    private var appContext: Context? = null

    private var downloadManager: DownloadManager? = null

    /** The shared instance. [init] must have run; every caller does so first. */
    val manager: DownloadManager
        get() = checkNotNull(downloadManager) { "NebulaDownloads.init(context) was never called" }

    private val rehydrateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (downloadManager != null) return
        synchronized(this) {
            if (downloadManager != null) return
            appContext = context.applicationContext
            DownloadPrefs.init(context)
            downloadManager = build(context.applicationContext)
            // Rehydrate on a background thread to avoid blocking the main thread
            rehydrateScope.launch {
                rehydrateDownloads()
            }
            // Observe network state to reactively update download requirements
            observeNetworkState()
        }
    }

    private fun build(app: Context): DownloadManager {
        val httpFactory = DefaultHttpDataSource.Factory()

        // Read-only view of the STREAM cache: a song already streamed while being
        // downloaded is served from what is on disk. setCacheWriteDataSinkFactory(null)
        // is what makes it read-only — without it this would write into the
        // streaming cache and fight its LRU evictor.
        val playerCacheReader = CacheDataSource.Factory()
            .setCache(NebulaApplication.getCache())
            .setUpstreamDataSourceFactory(httpFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolver = ResolvingDataSource.Factory(
            playerCacheReader,
            ResolvingDataSource.Resolver { dataSpec -> resolveStreamUri(dataSpec) }
        )

        val dm = DownloadManager(
            app,
            NebulaApplication.getDatabaseProvider(),
            NebulaApplication.getDownloadCache(),
            resolver,
            Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS)
        )
        dm.maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
        dm.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                _downloads.value = _downloads.value.toMutableMap()
                    .apply { put(download.request.id, download) }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                _downloads.value = _downloads.value.toMutableMap()
                    .apply { remove(download.request.id) }
            }
        })

        return dm
    }

    /**
     * Rehydrate from Media3's persisted index. Without this the queue is
     * empty after an app restart even though the songs are still on disk.
     * Runs on [Dispatchers.IO] to avoid blocking the main thread.
     */
    private fun rehydrateDownloads() {
        val dm = downloadManager ?: return
        dm.downloadIndex.getDownloads().use { cursor ->
            val seeded = mutableMapOf<String, Download>()
            while (cursor.moveToNext()) {
                seeded[cursor.download.request.id] = cursor.download
            }
            _downloads.value = seeded
        }
    }

    /**
     * The DataSpec carries the videoId as its custom cache key (set by
     * enqueue via setCustomCacheKey), so swap in a currently-valid stream URL. Blocking
     * is fine here: this runs on DownloadManager's own fixed thread pool, never
     * the main thread.
     *
     * Resolution failure must THROW, never fall back to the placeholder URL:
     * a thrown IOException fails the attempt so Media3 retries with backoff
     * (and recovers on its own when the network returns), while returning
     * https://nebula.local/ guarantees a bogus UnknownHostException that
     * masks the real reason.
     */
    @Throws(IOException::class)
    private fun resolveStreamUri(dataSpec: DataSpec): DataSpec {
        // Must fail loudly. Returning the DataSpec untouched looks harmless but hands
        // the placeholder URI straight to the network, which surfaces as an
        // UnknownHostException that says nothing about the real cause.
        val videoId = dataSpec.key ?: throw IOException("Download request has no custom cache key")
        val url = runBlocking(Dispatchers.IO) {
            searchRepository.getAudioStreamUrl(videoId)
        } ?: throw IOException("Could not resolve stream URL for $videoId")
        return dataSpec.buildUpon().setUri(Uri.parse(url)).build()
    }

    // ---- Public API used by the ViewModel / UI ----

    /**
     * Takes the whole [SearchResult], not a bare id and title: the Downloaded playlist
     * writes its rows from the download index, and the index only ever stores what is
     * handed to it here. Passing id+title meant artist and artwork were discarded at
     * enqueue time and could never be recovered.
     */
    fun enqueue(song: SearchResult) {
        val request = DownloadRequest.Builder(song.videoId, Uri.parse(IDLE_URI_PREFIX + song.videoId))
            // Without this the DataSpec reaching the resolver has a null key, the
            // placeholder URI survives resolution, and the download fails on DNS.
            // Media3 takes the key from here, NOT from the request id.
            .setCustomCacheKey(song.videoId)
            .setData(song.title.toByteArray())
            .build()
        // Echo's pattern: sendAddDownload STARTS the foreground service, which is
        // what shows the progress notification (with its cancel action). A bare
        // manager.addDownload() runs headless — no service, no notification.
        DownloadService.sendAddDownload(
            checkNotNull(appContext) { "NebulaDownloads.init(context) was never called" },
            NebulaDownloadService::class.java,
            request,
            /* foreground= */ false
        )
    }

    fun observe(songId: String): Flow<Download?> = downloads.map { it[songId] }

    /**
     * Media3 1.6.1 has NO per-download pause: `DownloadManager` exposes only
     * pauseDownloads()/resumeDownloads() across the whole queue. Pausing that way
     * keeps every partial file and resumes exactly where it stopped, which is the
     * behaviour a user means by "pause". Removing a song is the destructive
     * option and is exposed separately as [remove].
     */
    fun pauseAll() = manager.pauseDownloads()

    fun resumeAll() = manager.resumeDownloads()

    val allPaused: Boolean get() = manager.downloadsPaused

    /** Deletes the song and its partial/complete cache data. */
    fun remove(songId: String) = manager.removeDownload(songId)

    fun setWifiOnly(wifiOnly: Boolean) {
        manager.requirements =
            if (wifiOnly) Requirements(Requirements.NETWORK_UNMETERED)
            else Requirements(Requirements.NETWORK)
    }

    /** Bytes held by finished downloads, for the Settings screen. */
    fun downloadedBytes(): Long =
        _downloads.value.values
            .filter { it.state == Download.STATE_COMPLETED }
            .sumOf { it.contentLength.coerceAtLeast(0L) }

    /** Display title stashed on the request at enqueue time. */
    fun titleOf(download: Download): String =
        String(download.request.data).takeIf { it.isNotBlank() } ?: download.request.id

    /**
     * Observe network state and reactively update [DownloadManager.requirements].
     * When WiFi-only is enabled and the network changes, re-apply the requirements
     * so Media3's DownloadManager picks up the new network state immediately.
     */
    fun observeNetworkState() {
        val app = appContext ?: return
        val observer = NetworkConnectivityObserver(app)
        rehydrateScope.launch {
            observer.isWifiAvailable.collect { isWifi ->
                val dm = downloadManager ?: return@collect
                val wifiOnly = DownloadPrefs.wifiOnly.value
                dm.requirements = if (wifiOnly) {
                    Requirements(Requirements.NETWORK_UNMETERED)
                } else {
                    Requirements(Requirements.NETWORK)
                }
            }
        }
    }
}

/**
 * Observes network connectivity using [ConnectivityManager.NetworkCallback].
 * Emits `true` when a WiFi network with internet capability is available.
 */
class NetworkConnectivityObserver(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isWifiAvailable: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isWifi())
            }

            override fun onLost(network: Network) {
                trySend(isWifi())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                trySend(isWifi())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        trySend(isWifi())
        try {
            awaitCancellation()
        } finally {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    private fun isWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * DataStore-backed preferences for download settings.
 */
object DownloadPrefs {
    private var appContext: Context? = null
    private val WIFI_ONLY = booleanPreferencesKey("wifi_only")

    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    fun init(context: Context) {
        appContext = context.applicationContext
        // Load the persisted value into the StateFlow
        CoroutineScope(Dispatchers.IO).launch {
            context.applicationContext.dataStore.data.collect { prefs ->
                _wifiOnly.value = prefs[WIFI_ONLY] ?: false
            }
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        checkNotNull(appContext).dataStore.edit { it[WIFI_ONLY] = enabled }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "download_prefs")
