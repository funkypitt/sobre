package app.sobre.player.ui.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sobre.player.data.db.Episode
import app.sobre.player.ui.MainViewModel
import app.sobre.player.ui.util.formatDuration
import app.sobre.player.ui.util.relativeTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    onEpisodeClick: (Episode) -> Unit
) {
    val episodes by viewModel.downloadedEpisodes.collectAsState(initial = emptyList())
    var episodeToDelete by remember { mutableStateOf<Episode?>(null) }

    if (episodes.isEmpty()) {
        Text(
            text = "No downloads.",
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(episodes, key = { it.videoId }) { episode ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onEpisodeClick(episode) },
                            onLongClick = { episodeToDelete = episode }
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val duration = episode.durationSec?.let { formatDuration(it * 1000) } ?: ""
                    val meta = buildString {
                        append(episode.channelTitle)
                        append("  ·  ")
                        append(relativeTime(episode.publishedAt))
                        if (duration.isNotEmpty()) {
                            append("  ·  ")
                            append(duration)
                        }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }

    episodeToDelete?.let { episode ->
        AlertDialog(
            onDismissRequest = { episodeToDelete = null },
            title = { Text("Delete download") },
            text = { Text("Delete \"${episode.title}\" from local storage?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDownload(episode.videoId)
                    episodeToDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { episodeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
