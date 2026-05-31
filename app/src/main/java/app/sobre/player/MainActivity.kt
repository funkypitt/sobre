package app.sobre.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sobre.player.data.db.Channel
import app.sobre.player.data.db.Episode
import app.sobre.player.ui.MainViewModel
import app.sobre.player.ui.addchannel.AddChannelScreen
import app.sobre.player.ui.channels.ChannelDetailScreen
import app.sobre.player.ui.channels.ChannelsScreen
import app.sobre.player.ui.downloads.DownloadsScreen
import app.sobre.player.ui.episode.EpisodeScreen
import app.sobre.player.ui.feed.FeedScreen
import app.sobre.player.ui.theme.SobreTheme
import app.sobre.player.work.RefreshFeedsWorker

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val content = contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@let
            viewModel.importOpml(content)
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                val opml = viewModel.exportOpml()
                contentResolver.openOutputStream(it)?.bufferedWriter()?.use { w -> w.write(opml) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshFeedsWorker.schedule(this)
        handleIncomingIntent(intent)

        setContent {
            SobreTheme {
                SobreNavHost(
                    viewModel = viewModel,
                    onExport = { exportLauncher.launch("sobre-subscriptions.opml") },
                    onImport = { importLauncher.launch(arrayOf("*/*")) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.subscribe(sharedText.trim())
            }
        }
    }
}

sealed class Screen {
    data object Tabs : Screen()
    data class ChannelDetail(val channelId: String, val channelTitle: String) : Screen()
    data class EpisodeView(val videoId: String) : Screen()
    data object AddChannel : Screen()
}

@Composable
fun SobreNavHost(viewModel: MainViewModel, onExport: () -> Unit, onImport: () -> Unit) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Tabs) }
    var previousScreen by remember { mutableStateOf<Screen?>(null) }

    fun navigateTo(screen: Screen) {
        previousScreen = currentScreen
        currentScreen = screen
    }

    fun goBack() {
        currentScreen = previousScreen ?: Screen.Tabs
        previousScreen = null
    }

    when (val screen = currentScreen) {
        is Screen.Tabs -> TabsScreen(
            viewModel = viewModel,
            onChannelClick = { navigateTo(Screen.ChannelDetail(it.channelId, it.title)) },
            onEpisodeClick = { navigateTo(Screen.EpisodeView(it.videoId)) },
            onAddChannel = { navigateTo(Screen.AddChannel) },
            onExport = onExport,
            onImport = onImport
        )
        is Screen.ChannelDetail -> {
            BackHandler { goBack() }
            ChannelDetailScreen(
                channelId = screen.channelId,
                channelTitle = screen.channelTitle,
                viewModel = viewModel,
                onEpisodeClick = { navigateTo(Screen.EpisodeView(it.videoId)) }
            )
        }
        is Screen.EpisodeView -> {
            BackHandler { goBack() }
            EpisodeScreen(
                videoId = screen.videoId,
                viewModel = viewModel
            )
        }
        is Screen.AddChannel -> {
            BackHandler { goBack() }
            AddChannelScreen(
                viewModel = viewModel,
                onDone = { goBack() }
            )
        }
    }
}

@Composable
fun TabsScreen(
    viewModel: MainViewModel,
    onChannelClick: (Channel) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onAddChannel: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = listOf("Flux", "Chaines", "Telecharges")

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> FeedScreen(
                    viewModel = viewModel,
                    onEpisodeClick = onEpisodeClick
                )
                1 -> {
                    Column(modifier = Modifier.weight(1f)) {
                        ChannelsScreen(
                            viewModel = viewModel,
                            onChannelClick = onChannelClick
                        )
                    }
                    TextButton(
                        onClick = onAddChannel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text("+ Ajouter une chaine")
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        TextButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Text("Exporter OPML")
                        }
                        TextButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Text("Importer OPML")
                        }
                    }
                }
                2 -> DownloadsScreen(
                    viewModel = viewModel,
                    onEpisodeClick = onEpisodeClick
                )
            }
        }
    }
}
