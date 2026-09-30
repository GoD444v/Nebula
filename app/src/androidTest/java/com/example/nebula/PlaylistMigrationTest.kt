package com.example.nebula

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nebula.data.db.NebulaDatabase
import com.example.nebula.data.db.dao.LocalSongDao
import com.example.nebula.data.db.entities.LocalSong
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1 of NebulaDatabase as it shipped: one table, `local_songs`, no playlist tables.
 *
 * Declared here rather than checked in as a binary .db asset so the fixture cannot
 * drift from the real [LocalSong] entity. Safe because versionCode is 1, so no v1
 * database exists in the wild — this asserts against the schema the app actually had.
 */
@Database(entities = [LocalSong::class], version = 1, exportSchema = false)
abstract class V1FixtureDatabase : RoomDatabase() {
    abstract fun localSongDao(): LocalSongDao
}

/**
 * MIGRATION_1_2 must not silently drop a user's local songs. These seed a real v1
 * database, open the production [NebulaDatabase] over the same file, and check both
 * the new tables and the surviving rows.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistMigrationTest {

    private val dbName = "playlist_migration_test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Writes a genuine version-1 database file to disk, then closes it. */
    private fun seedV1() {
        val fixture = Room.databaseBuilder(context, V1FixtureDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            fixture.localSongDao().insertAll(
                listOf(
                    LocalSong(
                        title = "Kept Through Migration",
                        artist = "Artist",
                        album = "Album",
                        duration = 210_000L,
                        filePath = "/storage/emulated/0/song.mp3",
                        thumbnailUri = ""
                    )
                )
            )
        }
        fixture.close()
    }

    private fun openMigrated(): NebulaDatabase =
        NebulaDatabase.builder(context, dbName).allowMainThreadQueries().build()

    @Test
    fun migratesV1ToV2_createsBothPlaylistTables() {
        seedV1()
        val migrated = openMigrated()
        migrated.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('playlists','playlist_songs')")
            .use { cursor ->
                val found = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertEquals(setOf("playlists", "playlist_songs"), found.toSet())
            }
        migrated.close()
    }

    @Test
    fun migratesV1ToV2_preservesExistingLocalSongs() {
        seedV1()
        val migrated = openMigrated()
        val songs = runBlocking { migrated.localSongDao().getAllSongs() }
        assertEquals(1, songs.size)
        assertEquals("Kept Through Migration", songs.first().title)
        migrated.close()
    }

    @Test
    fun migratesV1ToV2_playlistSongsCascadeFromPlaylists() {
        seedV1()
        val migrated = openMigrated()
        val ddl = migrated.openHelper.writableDatabase
            .query("SELECT sql FROM sqlite_master WHERE type='table' AND name='playlist_songs'")
            .use { cursor ->
                assertTrue("playlist_songs table missing", cursor.moveToFirst())
                cursor.getString(0)
            }
        // Assert on the statement itself rather than PRAGMA integer codes, so a failure
        // prints the real DDL instead of a bare number.
        assertTrue(
            "playlist_songs must cascade from playlists, but DDL was: $ddl",
            ddl.contains("ON DELETE CASCADE", ignoreCase = true)
        )
        assertTrue(
            "playlist_songs must reference playlists(id), but DDL was: $ddl",
            ddl.contains("REFERENCES `playlists`(`id`)", ignoreCase = true)
        )
        migrated.close()
    }
}
