package com.example.nebula.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.nebula.data.db.dao.LocalSongDao
import com.example.nebula.data.db.entities.LocalSong

@Database(entities = [LocalSong::class], version = 1, exportSchema = false)
abstract class NebulaDatabase : RoomDatabase() {

    abstract fun localSongDao(): LocalSongDao

    companion object {
        @Volatile
        private var INSTANCE: NebulaDatabase? = null

        fun getDatabase(context: Context): NebulaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NebulaDatabase::class.java,
                    "nebula_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
