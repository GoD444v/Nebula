package nebula.music.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import nebula.music.data.db.entities.LocalSong

@Dao
interface LocalSongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<LocalSong>)

    @Query("DELETE FROM local_songs")
    suspend fun deleteAll()

    @Query("SELECT * FROM local_songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<LocalSong>

    @Query("SELECT * FROM local_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY title ASC")
    suspend fun searchSongs(query: String): List<LocalSong>

    @Query("SELECT COUNT(*) FROM local_songs")
    suspend fun getSongCount(): Int
}
