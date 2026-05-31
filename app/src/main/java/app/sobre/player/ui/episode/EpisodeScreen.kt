package app.sobre.player.ui.episode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sobre.player.data.db.Episode
import app.sobre.player.data.repository.Chapter
import app.sobre.player.data.repository.StreamDetails
import app.sobre.player.ui.MainViewModel
import app.sobre.player.ui.util.formatDuration
import app.sobre.player.ui.util.relativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun EpisodeScreen(
    videoId: String,
    viewModel: MainViewModel
) {
    var episode by remember { mutableStateOf<Episode?>(null) }
    var streamDetails by remember { mutableStateOf<StreamDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    val playbackState by viewModel.playerController.state.collectAsState()

    LaunchedEffect(videoId) {
        episode = viewModel.episodeRepo.getEpisode(videoId)
        episode?.let { ep ->
            val result = viewModel.resolveAndPlay(ep)
            result.fold(
                onSuccess = { streamDetails = it },
                onFailure = { error = it.message }
            )
        }
        loading = false
    }

    LaunchedEffect(playbackState.isPlaying) {
        while (isActive && playbackState.currentVideoId == videoId) {
            viewModel.playerController.updateState()
            delay(1000)
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            if (playbackState.currentVideoId == videoId) {
                viewModel.savePosition(videoId)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 48.dp)
    ) {
        if (loading) {
            Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        error?.let {
            Text("Error: $it", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        val ep = episode ?: return@Column

        Text(text = ep.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${ep.channelTitle}  ·  ${relativeTime(ep.publishedAt)}",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Player controls
        PlayerControls(
            isPlaying = playbackState.isPlaying && playbackState.currentVideoId == videoId,
            positionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
            speed = playbackState.speed,
            onTogglePlay = { viewModel.playerController.togglePlayPause() },
            onSeekRelative = { viewModel.playerController.seekRelative(it) },
            onSpeedChange = { viewModel.playerController.setSpeed(it) },
            onSeekTo = { viewModel.playerController.seekTo(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Download button
        if (!ep.isDownloaded) {
            TextButton(onClick = { viewModel.downloadEpisode(videoId) }) {
                Text("Download")
            }
        } else {
            Text("Downloaded", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Description
        val desc = streamDetails?.description ?: ep.description
        if (desc.isNotBlank()) {
            val plainDesc = app.sobre.player.ui.util.htmlToPlainText(desc)
            app.sobre.player.ui.util.LinkedText(
                text = plainDesc,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Chapters
        val chapters = streamDetails?.chapters ?: emptyList()
        if (chapters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Chapters", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            chapters.forEach { chapter ->
                Text(
                    text = "${formatDuration((chapter.startTimeSec * 1000).toLong())} — ${chapter.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.playerController.seekTo(chapter.startTimeSec * 1000L)
                        }
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = formatDuration(positionMs), style = MaterialTheme.typography.labelMedium)
        Text(text = formatDuration(durationMs), style = MaterialTheme.typography.labelMedium)
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onSeekRelative(-15_000L) }) {
            Text("-15s", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        TextButton(onClick = onTogglePlay) {
            Text(
                text = if (isPlaying) "Pause" else "Lecture",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        TextButton(onClick = { onSeekRelative(15_000L) }) {
            Text("+15s", fontWeight = FontWeight.Bold)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Speed control
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        speeds.forEach { s ->
            TextButton(onClick = { onSpeedChange(s) }) {
                Text(
                    text = "${s}x",
                    fontWeight = if (speed == s) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
