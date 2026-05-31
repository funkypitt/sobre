package app.sobre.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM Channel ORDER BY lastEpisodeAt DESC")
    fun getAllByRecent(): Flow<List<Channel>>

    @Query("SELECT * FROM Channel WHERE channelId = :id")
    suspend fun getById(id: String): Channel?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(channel: Channel): Long

    @Query("UPDATE Channel SET lastEpisodeAt = :timestamp WHERE channelId = :channelId")
    suspend fun updateLastEpisodeAt(channelId: String, timestamp: Long)

    @Query("SELECT * FROM Channel ORDER BY lastEpisodeAt DESC")
    suspend fun getAllByRecentSnapshot(): List<Channel>

    @Query("DELETE FROM Channel WHERE channelId = :channelId")
    suspend fun delete(channelId: String)
}
