package nebula.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import nebula.music.data.db.dao.LocalSongDao
import nebula.music.data.db.dao.PlayHistoryDao
import nebula.music.data.db.dao.PlaylistDao
import nebula.music.data.db.dao.SpeedDialDao
import nebula.music.data.db.entities.LocalSong
import nebula.music.data.db.entities.PlayHistoryEntity
import nebula.music.data.db.entities.PlaylistEntity
import nebula.music.data.db.entities.PlaylistSongEntity
import nebula.music.data.db.entities.SpeedDialItem

@Database(
    entities = [
        LocalSong::class, PlaylistEntity::class, PlaylistSongEntity::class,
        PlayHistoryEntity::class, SpeedDialItem::class
    ],
    version = 8,
    exportSchema = false
)
abstract class NebulaDatabase : RoomDatabase() {

    abstract fun localSongDao(): LocalSongDao

    abstract fun playHistoryDao(): PlayHistoryDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun speedDialDao(): SpeedDialDao

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
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                )

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
 * v4 -> v5 adds the play-history table. Fresh table only, so every statement is
 * `IF NOT EXISTS` and existing data is untouched. No fallbackToDestructiveMigration:
 * it would wipe the user's playlists and scanned local songs over a new table.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `play_history` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`videoId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, " +
                "`thumbnailUrl` TEXT, " +
                "`playedAt` INTEGER NOT NULL)"
        )
    }
}

/**
 * v6 -> v7 adds folderPath, the "Folders" browse. RELATIVE_PATH is what modern
 * devices answer ("Music/Albums"); DATA's parent is the fallback. Deliberately
 * no backfill: DEFAULT '' leaves every existing row unknown until the next
 * scan fills it — a rescan is one pull away, same as the dateModified one.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `local_songs` ADD COLUMN `folderPath` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v7 -> v8 adds albumArtist, the "Album artists" browse. ALBUM_ARTIST is what the
 * scan reads (R and up); deliberately no backfill: DEFAULT '' leaves every existing
 * row on the track-artist fallback until the next scan fills real tags — which is
 * exactly the grouping that behavior would show anyway.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `local_songs` ADD COLUMN `albumArtist` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v5 -> v6 adds the speed dial: playlists pinned for one-tap access from Home.
 *
 * Empty table, so this is a pure CREATE — nobody's pin history exists yet to lose. The
 * foreign key is declared in SQL as well as in the entity, because Room only enforces a
 * key it can find in the schema it migrates; without it, deleting a playlist would leave a
 * pin pointing at a row that is gone and the dial would render a blank tile.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `speed_dial` (" +
                "`playlistId` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlistId`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }
}

/**
 * v3 -> v4 adds dateModified, the "Date updated" sort. No backfill: the DEFAULT 0 puts
 * every existing row at the epoch, so they sort last until the next scan writes real
 * values. A rescan is one tap away, so nothing is stranded.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `local_songs` ADD COLUMN `dateModified` INTEGER NOT NULL DEFAULT 0")
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
