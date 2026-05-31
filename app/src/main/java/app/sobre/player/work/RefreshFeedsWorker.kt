package app.sobre.player.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.sobre.player.data.db.SobreDatabase
import okhttp3.OkHttpClient
import app.sobre.player.data.repository.SubscriptionRepository
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

class RefreshFeedsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = SobreDatabase.get(applicationContext)
        val repo = SubscriptionRepository(
            channelDao = db.channelDao(),
            episodeDao = db.episodeDao(),
            httpClient = OkHttpClient()
        )
        repo.refreshAll()
        fetchMissingDurations(db)
        return Result.success()
    }

    private suspend fun fetchMissingDurations(db: SobreDatabase) {
        val episodeDao = db.episodeDao()
        val videoIds = episodeDao.getVideoIdsWithoutDuration(30)
        for (videoId in videoIds) {
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val duration = info.duration
                if (duration > 0) {
                    episodeDao.updateDuration(videoId, duration)
                }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val WORK_NAME = "refresh_feeds"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshFeedsWorker>(4, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
