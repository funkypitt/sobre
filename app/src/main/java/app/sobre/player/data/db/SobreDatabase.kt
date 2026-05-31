package app.sobre.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Channel::class, Episode::class], version = 1, exportSchema = false)
abstract class SobreDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        @Volatile
        private var INSTANCE: SobreDatabase? = null

        fun get(context: Context): SobreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SobreDatabase::class.java,
                    "sobre.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
