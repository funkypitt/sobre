package app.sobre.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class Channel(
    @PrimaryKey val channelId: String,
    val title: String,
    val rssUrl: String,
    val lastEpisodeAt: Long,
    val addedAt: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Channel::class,
            parentColumns = ["channelId"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("channelId"), Index("publishedAt")]
)
data class Episode(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val channelTitle: String,
    val title: String,
    val description: String,
    val publishedAt: Long,
    val durationSec: Long? = null,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val lastPositionMs: Long = 0
)
