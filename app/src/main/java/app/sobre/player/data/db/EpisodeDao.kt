package app.sobre.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM Episode ORDER BY publishedAt DESC")
    fun getAllByRecent(): Flow<List<Episode>>

    @Query("SELECT * FROM Episode WHERE channelId = :channelId ORDER BY publishedAt DESC")
    fun getByChannel(channelId: String): Flow<List<Episode>>

    @Query("SELECT * FROM Episode WHERE isDownloaded = 1 ORDER BY publishedAt DESC")
    fun getDownloaded(): Flow<List<Episode>>

    @Query("SELECT * FROM Episode WHERE videoId = :videoId")
    suspend fun getById(videoId: String): Episode?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<Episode>)

    @Query("UPDATE Episode SET description = :description, durationSec = :durationSec WHERE videoId = :videoId")
    suspend fun updateDetails(videoId: String, description: String, durationSec: Long?)

    @Query("UPDATE Episode SET lastPositionMs = :positionMs WHERE videoId = :videoId")
    suspend fun updatePosition(videoId: String, positionMs: Long)

    @Query("UPDATE Episode SET isDownloaded = 1, localFilePath = :path WHERE videoId = :videoId")
    suspend fun markDownloaded(videoId: String, path: String)

    @Query("UPDATE Episode SET isDownloaded = 0, localFilePath = NULL WHERE videoId = :videoId")
    suspend fun clearDownload(videoId: String)

    @Query("SELECT videoId FROM Episode WHERE durationSec IS NULL ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getVideoIdsWithoutDuration(limit: Int = 30): List<String>

    @Query("UPDATE Episode SET durationSec = :durationSec WHERE videoId = :videoId")
    suspend fun updateDuration(videoId: String, durationSec: Long)
}
