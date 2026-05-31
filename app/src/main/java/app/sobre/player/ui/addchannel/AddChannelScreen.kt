package app.sobre.player.ui.addchannel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.sobre.player.ui.MainViewModel
import app.sobre.player.ui.SubscribeStatus

@Composable
fun AddChannelScreen(
    viewModel: MainViewModel,
    onDone: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    val status by viewModel.subscribeStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Add a channel",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Paste a channel or video URL from YouTube.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                if (url.isNotBlank()) {
                    viewModel.subscribe(url.trim())
                }
            },
            enabled = status !is SubscribeStatus.Loading
        ) {
            Text("Add")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val s = status) {
            is SubscribeStatus.Loading -> Text("Resolving…")
            is SubscribeStatus.Success -> {
                Text("Subscribed to ${s.channelTitle}")
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    viewModel.resetSubscribeStatus()
                    onDone()
                }) {
                    Text("OK")
                }
            }
            is SubscribeStatus.Error -> {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.resetSubscribeStatus() }) {
                    Text("Retry")
                }
            }
            is SubscribeStatus.Idle -> {}
        }
    }
}
