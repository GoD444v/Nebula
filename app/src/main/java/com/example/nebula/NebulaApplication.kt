package com.example.nebula

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.nebula.data.download.NebulaDownloads
import java.io.File

@UnstableApi
class NebulaApplication : Application() {

    companion object {
        private const val STREAM_CACHE_DIR = "media_cache"
        private const val DOWNLOAD_CACHE_DIR = "download_cache"
        private const val STREAM_CACHE_SIZE_BYTES = 500L * 1024 * 1024

        @Volatile
        private var cacheInstance: SimpleCache? = null

        @Volatile
        private var downloadCacheInstance: SimpleCache? = null

        @Volatile
        private var cacheDataSourceFactoryInstance: CacheDataSource.Factory? = null

        @Volatile
        private var appInstance: NebulaApplication? = null

        /**
         * ONE database provider shared by both caches and by Media3's own
         * DownloadIndex. Media3's documented pattern — SimpleCache and
         * DownloadManager must share it, or the download index lands in a
         * second, clashing database.
         */
        @Volatile
        private var databaseProviderInstance: StandaloneDatabaseProvider? = null

        private fun databaseProvider(app: Application): StandaloneDatabaseProvider =
            databaseProviderInstance ?: synchronized(this) {
                databaseProviderInstance
                    ?: StandaloneDatabaseProvider(app).also { databaseProviderInstance = it }
            }

        /**
         * The shared provider. Media3's DownloadIndex MUST be built on the same
         * provider as the SimpleCache instances — a second StandaloneDatabaseProvider
         * opens the same default-named SQLite file and the two fight over it.
         */
        fun getDatabaseProvider(): StandaloneDatabaseProvider =
            databaseProvider(appInstance ?: throw IllegalStateException("NebulaApplication not initialized"))

        fun getCache(): SimpleCache {
            return cacheInstance ?: synchronized(this) {
                cacheInstance ?: createCache().also { cacheInstance = it }
            }
        }

        /**
         * The offline store. Deliberately a SEPARATE cache from [getCache] and
         * deliberately NO eviction: a song the user asked to keep must not be
         * dropped because they streamed enough other music to trip the LRU. The
         * split is what makes that possible — the streaming cache can stay a
         * cheap 500MB LRU while downloads are kept until the user removes them.
         */
        fun getDownloadCache(): SimpleCache {
            return downloadCacheInstance ?: synchronized(this) {
                downloadCacheInstance ?: createDownloadCache().also { downloadCacheInstance = it }
            }
        }

        fun getCacheDataSourceFactory(): CacheDataSource.Factory {
            return cacheDataSourceFactoryInstance ?: synchronized(this) {
                cacheDataSourceFactoryInstance ?: createCacheDataSourceFactory()
                    .also { cacheDataSourceFactoryInstance = it }
            }
        }

        private fun createCache(): SimpleCache {
            val app = appInstance ?: throw IllegalStateException("NebulaApplication not initialized")
            val cacheDir = File(app.cacheDir, STREAM_CACHE_DIR)
            val evictor = LeastRecentlyUsedCacheEvictor(STREAM_CACHE_SIZE_BYTES)
            return SimpleCache(cacheDir, evictor, databaseProvider(app))
        }

        private fun createDownloadCache(): SimpleCache {
            val app = appInstance ?: throw IllegalStateException("NebulaApplication not initialized")
            val cacheDir = File(app.cacheDir, DOWNLOAD_CACHE_DIR)
            return SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider(app))
        }

        private fun createCacheDataSourceFactory(): CacheDataSource.Factory {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            return CacheDataSource.Factory()
                .setCache(getCache())
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
        }

        fun getCacheDirectory(): File {
            val app = appInstance ?: throw IllegalStateException("NebulaApplication not initialized")
            return File(app.cacheDir, STREAM_CACHE_DIR)
        }

        fun getDownloadCacheDirectory(): File {
            val app = appInstance ?: throw IllegalStateException("NebulaApplication not initialized")
            return File(app.cacheDir, DOWNLOAD_CACHE_DIR)
        }

        /**
         * Clears the STREAMING cache only. Downloads live in their own cache and
         * are not touched — the Settings button is labelled "Clear cache", and
         * silently deleting the songs someone explicitly saved offline would be
         * the worst possible reading of that label.
         */
        fun clearCache() {
            cacheInstance?.release()
            cacheInstance = null
            cacheDataSourceFactoryInstance = null
            appInstance?.let { File(it.cacheDir, STREAM_CACHE_DIR).deleteRecursively() }
        }

        /** Removes every downloaded song. Separate from [clearCache] on purpose. */
        fun clearDownloads() {
            downloadCacheInstance?.release()
            downloadCacheInstance = null
            appInstance?.let { File(it.cacheDir, DOWNLOAD_CACHE_DIR).deleteRecursively() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appInstance = this
        // Initialize downloads at app startup so the download button in the player
        // sheet works even if the user never opened the Downloads tab
        NebulaDownloads.init(this)
    }
}
