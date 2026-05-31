package app.sobre.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import app.sobre.player.data.db.Channel
import app.sobre.player.data.db.Episode
import app.sobre.player.data.db.SobreDatabase
import app.sobre.player.data.opml.OpmlManager
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import app.sobre.player.data.repository.EpisodeRepository
import app.sobre.player.data.repository.StreamDetails
import app.sobre.player.data.repository.SubscriptionRepository
import app.sobre.player.playback.PlayerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SobreDatabase.get(application)
    private val httpClient = OkHttpClient()

    val subscriptionRepo = SubscriptionRepository(
        channelDao = db.channelDao(),
        episodeDao = db.episodeDao(),
        httpClient = httpClient
    )

    val episodeRepo = EpisodeRepository(
        episodeDao = db.episodeDao(),
        httpClient = httpClient,
        audioDir = File(application.filesDir, "audio")
    )

    val playerController = PlayerController(application)

    val channels: Flow<List<Channel>> = subscriptionRepo.channels
    val allEpisodes: Flow<List<Episode>> = episodeRepo.allEpisodes
    val downloadedEpisodes: Flow<List<Episode>> = episodeRepo.downloadedEpisodes

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _subscribeStatus = MutableStateFlow<SubscribeStatus>(SubscribeStatus.Idle)
    val subscribeStatus: StateFlow<SubscribeStatus> = _subscribeStatus.asStateFlow()

    fun subscribe(url: String) {
        viewModelScope.launch {
            _subscribeStatus.value = SubscribeStatus.Loading
            val result = subscriptionRepo.subscribe(url)
            _subscribeStatus.value = result.fold(
                onSuccess = { SubscribeStatus.Success(it.title) },
                onFailure = { SubscribeStatus.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun resetSubscribeStatus() {
        _subscribeStatus.value = SubscribeStatus.Idle
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            subscriptionRepo.refreshAll()
            _isRefreshing.value = false
            fetchMissingDurations()
        }
    }

    private fun fetchMissingDurations() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val videoIds = db.episodeDao().getVideoIdsWithoutDuration(20)
            for (videoId in videoIds) {
                try {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                    if (info.duration > 0) {
                        db.episodeDao().updateDuration(videoId, info.duration)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch {
            subscriptionRepo.unsubscribe(channelId)
        }
    }

    fun episodesForChannel(channelId: String): Flow<List<Episode>> =
        episodeRepo.episodesForChannel(channelId)

    suspend fun resolveAndPlay(episode: Episode): Result<StreamDetails> {
        val result = episodeRepo.resolveStreamDetails(episode.videoId)
        result.onSuccess { details ->
            playerController.play(
                audioUrl = details.audioUrl,
                videoId = episode.videoId,
                title = episode.title,
                channelName = episode.channelTitle,
                startPositionMs = episode.lastPositionMs
            )
        }
        return result
    }

    fun downloadEpisode(videoId: String) {
        viewModelScope.launch {
            episodeRepo.download(videoId)
        }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            episodeRepo.deleteDownload(videoId)
        }
    }

    fun savePosition(videoId: String) {
        viewModelScope.launch {
            episodeRepo.savePosition(videoId, playerController.getPosition())
        }
    }

    suspend fun exportOpml(): String {
        val channelList = db.channelDao().getAllByRecentSnapshot()
        return OpmlManager.export(channelList)
    }

    fun importOpml(opmlContent: String) {
        viewModelScope.launch {
            val entries = OpmlManager.parse(opmlContent)
            for (entry in entries) {
                val existing = db.channelDao().getById(entry.channelId)
                if (existing == null) {
                    val channel = Channel(
                        channelId = entry.channelId,
                        title = entry.title,
                        rssUrl = entry.rssUrl,
                        lastEpisodeAt = 0L,
                        addedAt = System.currentTimeMillis()
                    )
                    db.channelDao().insert(channel)
                    subscriptionRepo.refreshChannel(channel)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}

sealed class SubscribeStatus {
    data object Idle : SubscribeStatus()
    data object Loading : SubscribeStatus()
    data class Success(val channelTitle: String) : SubscribeStatus()
    data class Error(val message: String) : SubscribeStatus()
}
