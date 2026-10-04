package nebula.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import nebula.music.data.db.dao.LocalSongDao
import nebula.music.data.db.dao.PlaylistDao
import nebula.music.data.db.entities.LocalSong
import nebula.music.data.db.entities.PlaylistEntity
import nebula.music.data.db.entities.PlaylistSongEntity

@Database(
    entities = [LocalSong::class, PlaylistEntity::class, PlaylistSongEntity::class],
    version = 3,
    exportSchema = false
)
abstract class NebulaDatabase : RoomDatabase() {

    abstract fun localSongDao(): LocalSongDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DB_NAME = "nebula_database"

        @Volatile
        private var INSTANCE: NebulaDatabase? = null

        /**
         * The single place the database is configured, so the app's singleton and the
         * instrumented migration test exercise the same migration list. A test that
         * built its own builder would pass while the shipped app crashed on upgrade.
         */
        fun builder(
            context: Context,
            name: String = DB_NAME
        ): RoomDatabase.Builder<NebulaDatabase> =
            Room.databaseBuilder(context.applicationContext, NebulaDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)

        fun getDatabase(context: Context): NebulaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = builder(context).build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Drops the cached instance so an instrumented test can start from an empty
         * database. `getDatabase` memoises for the process lifetime, which would
         * otherwise leak state between test methods.
         */
        @androidx.annotation.VisibleForTesting
        fun closeForTests() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}


/**
 * v1 -> v2 adds the two playlist tables. Every statement is `IF NOT EXISTS`, so the
 * migration is idempotent. `fallbackToDestructiveMigration()` is deliberately absent:
 * it would silently wipe the user's scanned `local_songs` rows.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`thumbnailUrl` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`lastUpdatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` (" +
                "`playlistId` INTEGER NOT NULL, " +
                "`videoId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, " +
                "`thumbnailUrl` TEXT, " +
                "`position` INTEGER NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlistId`, `videoId`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` " +
                "ON `playlist_songs` (`playlistId`)"
        )
    }
}

/**
 * v2 -> v3 adds the isSystem flag that marks the built-in Downloaded playlist.
 * One column, no backfill: the NOT NULL DEFAULT 0 makes every existing playlist a
 * user playlist, which is what they all are.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `isSystem` INTEGER NOT NULL DEFAULT 0")
    }
}
