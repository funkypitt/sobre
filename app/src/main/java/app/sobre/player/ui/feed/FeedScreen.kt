package app.sobre.player.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sobre.player.data.db.Episode
import app.sobre.player.ui.MainViewModel
import app.sobre.player.ui.util.formatDuration
import app.sobre.player.ui.util.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: MainViewModel,
    onEpisodeClick: (Episode) -> Unit
) {
    val episodes by viewModel.allEpisodes.collectAsState(initial = emptyList())
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (episodes.isEmpty() && !isRefreshing) {
            Text(
                text = "Aucun épisode. Abonnez-vous à des chaînes pour voir leur contenu ici.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(episodes, key = { it.videoId }) { episode ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEpisodeClick(episode) }
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
    }
}
