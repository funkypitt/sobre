package app.sobre.player.data.repository

import android.content.Context
import android.util.Log
import app.sobre.player.data.db.Episode
import app.sobre.player.data.db.EpisodeDao
import app.sobre.player.data.extractor.Ytdlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.File

data class StreamDetails(
    val description: String,
    val durationSec: Long?,
    val audioUrl: String,
    val chapters: List<Chapter>
)

data class Chapter(
    val title: String,
    val startTimeSec: Int
)

/** Thrown for what Sobre will not play at all, as opposed to what it merely failed to find. */
class LiveStreamException : IllegalStateException("Live streams are not supported")

class EpisodeRepository(
    private val context: Context,
    private val episodeDao: EpisodeDao,
    private val httpClient: OkHttpClient,
    private val audioDir: File
) {
    val allEpisodes: Flow<List<Episode>> = episodeDao.getAllByRecent()
    val downloadedEpisodes: Flow<List<Episode>> = episodeDao.getDownloaded()

    fun episodesForChannel(channelId: String): Flow<List<Episode>> =
        episodeDao.getByChannel(channelId)

    suspend fun getEpisode(videoId: String): Episode? = episodeDao.getById(videoId)

    /**
     * Where to listen from, and what is known about it.
     *
     * NewPipe first: it is quick and it costs nothing. When YouTube changes its signatures a
     * NewPipe that has not been rebuilt finds nothing at all, and the application looks broken
     * until a new version is published — so yt-dlp, which fetches its own new version on the
     * telephone, is asked next. A live stream is not a failure but a refusal, and is not retried.
     */
    suspend fun resolveStreamDetails(videoId: String): Result<StreamDetails> =
        withContext(Dispatchers.IO) {
            val viaNewPipe = fromNewPipe(videoId)
            val failure = viaNewPipe.exceptionOrNull()
            if (failure == null || failure is LiveStreamException) return@withContext viaNewPipe
            Log.w("Sobre", "NewPipe n'a rien trouvé (${failure.message}) : on passe à yt-dlp")
            runCatching {
                val audio = Ytdlp.audio(context, videoId)
                val stored = episodeDao.getById(videoId)
                val description = audio.description.ifBlank { stored?.description.orEmpty() }
                val durationSec = audio.durationSec ?: stored?.durationSec
                episodeDao.updateDetails(videoId, description, durationSec)
                StreamDetails(
                    description = description,
                    durationSec = durationSec,
                    audioUrl = audio.url,
                    chapters = parseChaptersFromDescription(description),
                )
            }
        }

    private suspend fun fromNewPipe(videoId: String): Result<StreamDetails> =
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)

                if (info.streamType == StreamType.LIVE_STREAM) throw LiveStreamException()

                Log.d("Sobre", "StreamType: ${info.streamType}")
                Log.d("Sobre", "Audio streams: ${info.audioStreams.size}")
                for (a in info.audioStreams) {
                    Log.d("Sobre", "  audio: ${a.format} ${a.deliveryMethod} bitrate=${a.averageBitrate} url=${a.content.take(80)}")
                }
                Log.d("Sobre", "Video streams: ${info.videoStreams.size}")
                for (v in info.videoStreams) {
                    Log.d("Sobre", "  video: ${v.format} ${v.deliveryMethod} ${v.resolution} url=${v.content.take(80)}")
                }
                Log.d("Sobre", "VideoOnly streams: ${info.videoOnlyStreams.size}")
                Log.d("Sobre", "DASH url: ${info.dashMpdUrl?.take(80)}")
                Log.d("Sobre", "HLS url: ${info.hlsUrl?.take(80)}")

                val description = info.description?.content ?: ""
                val durationSec = info.duration.takeIf { it > 0 }

                episodeDao.updateDetails(videoId, description, durationSec)

                val audioUrl = resolveAudioUrl(info)
                Log.d("Sobre", "Resolved audio URL: ${audioUrl?.take(80)}")
                audioUrl ?: throw IllegalStateException("No audio stream available")

                val chapters = info.streamSegments.map { seg ->
                    Chapter(title = seg.title, startTimeSec = seg.startTimeSeconds)
                }.ifEmpty {
                    parseChaptersFromDescription(description)
                }

                Result.success(
                    StreamDetails(
                        description = description,
                        durationSec = durationSec,
                        audioUrl = audioUrl,
                        chapters = chapters
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }

    /**
     * Keep one episode on the telephone. NewPipe's address over HTTP first; if that address
     * cannot be had, or the server refuses it, yt-dlp brings the track down itself — and the
     * file keeps the extension of whatever track was chosen rather than being called m4a
     * whatever it is.
     */
    suspend fun download(videoId: String, onProgress: (Int) -> Unit = {}): Result<Unit> =
        withContext(Dispatchers.IO) {
            val file = runCatching { overHttp(videoId) }.recoverCatching { failure ->
                Log.w("Sobre", "Téléchargement par NewPipe manqué (${failure.message}) : on passe à yt-dlp")
                Ytdlp.download(context, videoId, audioDir, onProgress)
            }.getOrElse { return@withContext Result.failure(it) }
            episodeDao.markDownloaded(videoId, file.absolutePath)
            Result.success(Unit)
        }

    private fun overHttp(videoId: String): File {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val downloadUrl = resolveAudioUrl(info) ?: throw IllegalStateException("No downloadable stream")
        audioDir.mkdirs()
        val file = File(audioDir, "$videoId.m4a")
        val response = httpClient.newCall(Request.Builder().url(downloadUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
            val body = it.body ?: throw IllegalStateException("réponse vide")
            body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        }
        return file
    }

    suspend fun deleteDownload(videoId: String) {
        val episode = episodeDao.getById(videoId) ?: return
        episode.localFilePath?.let { File(it).delete() }
        episodeDao.clearDownload(videoId)
    }

    suspend fun savePosition(videoId: String, positionMs: Long) {
        episodeDao.updatePosition(videoId, positionMs)
    }

    private fun resolveAudioUrl(info: StreamInfo): String? {
        // 1. Try progressive audio-only streams (best: saves bandwidth)
        val progressive = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        if (progressive.isNotEmpty()) {
            return pickBest(progressive)?.content
        }

        // 2. Try any audio stream (including DASH segments)
        if (info.audioStreams.isNotEmpty()) {
            return pickBest(info.audioStreams)?.content
        }

        // 3. Fallback: use DASH manifest URL (ExoPlayer can parse it)
        val dashUrl = info.dashMpdUrl
        if (!dashUrl.isNullOrBlank()) {
            return dashUrl
        }

        // 4. Fallback: use HLS manifest
        val hlsUrl = info.hlsUrl
        if (!hlsUrl.isNullOrBlank()) {
            return hlsUrl
        }

        // 5. Last resort: use a video stream (contains audio track, ExoPlayer will play it)
        val videoStream = info.videoStreams.firstOrNull {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        } ?: info.videoStreams.firstOrNull()
        if (videoStream != null) {
            return videoStream.content
        }

        return null
    }

    private fun pickBestProgressiveAudio(streams: List<AudioStream>): AudioStream? {
        val progressive = streams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        return pickBest(progressive.ifEmpty { streams })
    }

    private fun pickBest(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        val m4a = streams.filter { it.format?.suffix == "m4a" }
        val preferred = m4a.ifEmpty { streams }
        return preferred.maxByOrNull { it.averageBitrate }
    }

    private fun parseChaptersFromDescription(description: String): List<Chapter> {
        val pattern = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\s+(.+)""")
        return pattern.findAll(description).map { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toInt()
            val seconds = match.groupValues[3].toInt()
            val title = match.groupValues[4].trim()
            Chapter(title = title, startTimeSec = hours * 3600 + minutes * 60 + seconds)
        }.toList()
    }
}
