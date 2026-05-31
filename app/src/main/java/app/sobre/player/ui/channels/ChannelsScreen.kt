package app.sobre.player.ui.channels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import app.sobre.player.data.db.Channel
import app.sobre.player.ui.MainViewModel
import app.sobre.player.ui.util.relativeTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelsScreen(
    viewModel: MainViewModel,
    onChannelClick: (Channel) -> Unit
) {
    val channels by viewModel.channels.collectAsState(initial = emptyList())
    var channelToDelete by remember { mutableStateOf<Channel?>(null) }

    if (channels.isEmpty()) {
        Text(
            text = "No subscriptions yet. Add a channel using the button below.",
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(channels, key = { it.channelId }) { channel ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onChannelClick(channel) },
                            onLongClick = { channelToDelete = channel }
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (channel.lastEpisodeAt > 0) {
                        Text(
                            text = relativeTime(channel.lastEpisodeAt),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    channelToDelete?.let { channel ->
        AlertDialog(
            onDismissRequest = { channelToDelete = null },
            title = { Text("Unsubscribe") },
            text = { Text("Unsubscribe from ${channel.title}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unsubscribe(channel.channelId)
                    channelToDelete = null
                }) {
                    Text("Unsubscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { channelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
