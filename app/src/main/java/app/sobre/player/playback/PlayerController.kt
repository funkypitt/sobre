package app.sobre.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentVideoId: String? = null,
    val currentTitle: String? = null,
    val currentChannel: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f
)

class PlayerController(context: Context) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                }
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    updateState()
                }
            })
        }, MoreExecutors.directExecutor())
    }

    fun play(audioUrl: String, videoId: String, title: String, channelName: String, startPositionMs: Long = 0) {
        val mediaItem = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaId(videoId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(channelName)
                    .build()
            )
            .build()

        controller?.let { ctrl ->
            ctrl.setMediaItem(mediaItem)
            ctrl.seekTo(startPositionMs)
            ctrl.prepare()
            ctrl.play()
            _state.value = _state.value.copy(
                isPlaying = true,
                currentVideoId = videoId,
                currentTitle = title,
                currentChannel = channelName,
                positionMs = startPositionMs
            )
        }
    }

    fun togglePlayPause() {
        controller?.let { ctrl ->
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun seekRelative(deltaMs: Long) {
        controller?.let { ctrl ->
            val target = (ctrl.currentPosition + deltaMs).coerceAtLeast(0)
            ctrl.seekTo(target)
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun getPosition(): Long = controller?.currentPosition ?: 0L
    fun getDuration(): Long = controller?.duration ?: 0L

    fun updateState() {
        controller?.let { ctrl ->
            _state.value = _state.value.copy(
                isPlaying = ctrl.isPlaying,
                positionMs = ctrl.currentPosition,
                durationMs = ctrl.duration.coerceAtLeast(0)
            )
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
