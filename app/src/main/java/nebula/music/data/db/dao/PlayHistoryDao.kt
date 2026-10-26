package nebula.music.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import nebula.music.data.db.entities.PlayHistoryEntity

@Dao
interface PlayHistoryDao {

    @Insert
    suspend fun insert(entry: PlayHistoryEntity)

    @Delete
    suspend fun delete(entry: PlayHistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC, id DESC")
    fun observeAll(): Flow<List<PlayHistoryEntity>>
}
