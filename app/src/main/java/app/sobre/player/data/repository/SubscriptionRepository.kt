package app.sobre.player.data.repository

import app.sobre.player.data.db.Channel
import app.sobre.player.data.db.ChannelDao
import app.sobre.player.data.db.EpisodeDao
import app.sobre.player.data.extractor.ChannelResolver
import app.sobre.player.data.rss.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SubscriptionRepository(
    private val channelDao: ChannelDao,
    private val episodeDao: EpisodeDao,
    private val httpClient: OkHttpClient
) {
    val channels: Flow<List<Channel>> = channelDao.getAllByRecent()

    suspend fun subscribe(url: String): Result<Channel> = withContext(Dispatchers.IO) {
        try {
            val resolved = ChannelResolver.resolve(url)

            val existing = channelDao.getById(resolved.channelId)
            if (existing != null) {
                return@withContext Result.success(existing)
            }

            val channel = Channel(
                channelId = resolved.channelId,
                title = resolved.title,
                rssUrl = resolved.rssUrl,
                lastEpisodeAt = 0L,
                addedAt = System.currentTimeMillis()
            )
            channelDao.insert(channel)
            refreshChannel(channel)
            Result.success(channel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshChannel(channel: Channel) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(channel.rssUrl).build()
            val response = httpClient.newCall(request).execute()
            val xml = response.body?.string() ?: return@withContext
            val rssEpisodes = RssParser.parse(xml)
            val episodes = RssParser.toEpisodes(rssEpisodes, channel.channelId, channel.title)
            episodeDao.insertAll(episodes)
            val maxTimestamp = episodes.maxOfOrNull { it.publishedAt } ?: 0L
            if (maxTimestamp > channel.lastEpisodeAt) {
                channelDao.updateLastEpisodeAt(channel.channelId, maxTimestamp)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        val snapshot = channelDao.getAllByRecentSnapshot()
        for (channel in snapshot) {
            refreshChannel(channel)
        }
    }

    suspend fun unsubscribe(channelId: String) {
        channelDao.delete(channelId)
    }
}
