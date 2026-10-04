package nebula.music

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import nebula.music.data.db.NebulaDatabase
import nebula.music.data.db.dao.LocalSongDao
import nebula.music.data.db.dao.PlaylistDao
import nebula.music.data.db.entities.LocalSong
import nebula.music.data.db.entities.PlaylistEntity
import nebula.music.data.db.entities.PlaylistSongEntity
import kotlinx.coroutines.flow.first
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
 * v2 of NebulaDatabase, as it shipped: the two playlist tables, but NO `isSystem`
 * column.
 *
 * These are deliberately *not* the production entities. Room derives a fixture's
 * schema from the entity classes it is given, so a fixture built on today's
 * [PlaylistEntity] would already contain `isSystem` and MIGRATION_2_3 would fail
 * with "duplicate column name". A migration test has to describe the shape the
 * database actually had, which means a snapshot of the old entity.
 */
@Entity(tableName = "playlists")
data class PlaylistV2Entity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistV2Entity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSongV2Entity(
    val playlistId: Long,
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface V2FixtureDao {
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistV2Entity): Long

    @Insert
    suspend fun insertSong(song: PlaylistSongV2Entity)
}

@Database(
    entities = [LocalSong::class, PlaylistV2Entity::class, PlaylistSongV2Entity::class],
    version = 2,
    exportSchema = false
)
abstract class V2FixtureDatabase : RoomDatabase() {
    abstract fun localSongDao(): LocalSongDao
    abstract fun fixtureDao(): V2FixtureDao
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

    // ---- v2 -> v3: the isSystem column ----

    private fun seedV2() {
        val fixture = Room.databaseBuilder(context, V2FixtureDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val id = fixture.fixtureDao().insertPlaylist(PlaylistV2Entity(name = "Existing Playlist"))
            fixture.fixtureDao().insertSong(
                PlaylistSongV2Entity(
                    playlistId = id,
                    videoId = "vid-x",
                    title = "Kept Song",
                    artist = "Artist",
                    thumbnailUrl = null,
                    position = 0
                )
            )
        }
        fixture.close()
    }

    private fun openV2SeedThenMigrate(): NebulaDatabase =
        NebulaDatabase.builder(context, dbName).allowMainThreadQueries().build()

    @Test
    fun migratesV2ToV3_addsIsSystemDefaultingToZero() {
        seedV2()

        val migrated = openV2SeedThenMigrate()
        migrated.openHelper.writableDatabase
            .query("PRAGMA table_info(playlists)")
            .use { cursor ->
                var sawIsSystem = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "isSystem") {
                        sawIsSystem = true
                    }
                }
                assertTrue("playlists has no isSystem column after v2->v3", sawIsSystem)
            }
        // Existing playlists are user playlists, not the system one.
        val rows = runBlocking { migrated.playlistDao().observePlaylists().first() }
        assertEquals(1, rows.size)
        assertTrue("existing playlists must default to isSystem = false", !rows.single().playlist.isSystem)
        migrated.close()
    }

    @Test
    fun migratesV2ToV3_preservesExistingPlaylistsAndSongs() {
        seedV2()

        val migrated = openV2SeedThenMigrate()
        val playlists = runBlocking { migrated.playlistDao().observePlaylists().first() }
        val songs = runBlocking {
            migrated.playlistDao().observeSongs(playlists.single().playlist.id).first()
        }
        assertEquals("Existing Playlist", playlists.single().playlist.name)
        assertEquals(1, songs.size)
        assertEquals("Kept Song", songs.single().title)
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
