package com.beegear.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BeeGearApp()
            }
        }
    }
}

private data class Category(val name: String, val url: String)
private data class Entry(val name: String, val url: String, val folder: Boolean)

private val categories = listOf(
    Category("English Movies", "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/"),
    Category("English Movies 1080p", "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29/"),
    Category("IMDb Top 250", "http://172.16.50.14/DHAKA-FLIX-14/IMDb%20Top-250%20Movies/"),
    Category("Hindi Movies", "http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/"),
    Category("South Indian Hindi Dubbed", "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/"),
    Category("TV and Web Series", "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/"),
    Category("Anime and Cartoon", "http://172.16.50.10/DHAKA-FLIX-10/Anime%20%26%20Cartoon%20TV%20Series/"),
    Category("Korean TV and Web Series", "http://172.16.50.14/DHAKA-FLIX-14/KOREAN%20TV%20%26%20WEB%20Series/"),
    Category("Animation Movies", "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies/")
)

private val videoTypes = listOf(
    ".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v", ".ts"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeeGearApp() {
    var url by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("BeeGear") }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var history by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var video by remember { mutableStateOf<String?>(null) }
    var videoTitle by remember { mutableStateOf("") }

    BackHandler(enabled = video != null || url != null) {
        if (video != null) {
            video = null
        } else if (history.isNotEmpty()) {
            val previous = history.last()
            history = history.dropLast(1)
            url = previous.first
            title = previous.second
        } else {
            url = null
            title = "BeeGear"
        }
    }

    if (video != null) {
        VideoScreen(video!!, videoTitle)
        return
    }

    LaunchedEffect(url) {
        val target = url ?: return@LaunchedEffect
        loading = true
        error = null

        try {
            entries = withContext(Dispatchers.IO) {
                loadEntries(target)
            }
        } catch (e: Exception) {
            error = "তালিকা আনা যায়নি। ISP নেটওয়ার্ক পরীক্ষা করো।"
            entries = emptyList()
        }

        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (url != null) {
                        TextButton(onClick = {
                            if (history.isNotEmpty()) {
                                val previous = history.last()
                                history = history.dropLast(1)
                                url = previous.first
                                title = previous.second
                            } else {
                                url = null
                                title = "BeeGear"
                            }
                        }) {
                            Text("Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (url == null) {
                Text(
                    "DhakaFlix BDIX Library",
                    style = MaterialTheme.typography.titleMedium
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        Button(
                            onClick = {
                                history = emptyList()
                                title = category.name
                                url = category.url
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(category.name)
                        }
                    }
                }
            } else {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text("সার্ভার থেকে তালিকা আনা হচ্ছে...")
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries) { entry ->
                        Button(
                            onClick = {
                                if (entry.folder) {
                                    history = history + ((url ?: "") to title)
                                    title = entry.name
                                    url = entry.url
                                } else {
                                    videoTitle = entry.name
                                    video = entry.url
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (entry.folder) "📁 " else "▶ ") + entry.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun loadEntries(address: String): List<Entry> {
    val connection = URI(address).toURL().openConnection()
    connection.connectTimeout = 12000
    connection.readTimeout = 20000
    connection.setRequestProperty("User-Agent", "BeeGear Android")

    val html = connection.getInputStream()
        .bufferedReader()
        .use { it.readText() }

    val doc = Jsoup.parse(html, address)

    return doc.select("a[href]").mapNotNull { link ->
        val href = link.attr("href").trim()
        val name = link.text().trim()

        if (
            href.isBlank() ||
            name.isBlank() ||
            href == "../" ||
            name == ".." ||
            name.equals("Parent Directory", true)
        ) {
            return@mapNotNull null
        }

        val resolved = URI(address).resolve(href).toString()
        val folder = href.endsWith("/")
        val cleanUrl = resolved.substringBefore("?")

        val isVideo = videoTypes.any {
            cleanUrl.lowercase().endsWith(it)
        }

        if (!folder && !isVideo) {
            return@mapNotNull null
        }

        Entry(name.removeSuffix("/"), resolved, folder)
    }
        .distinctBy { it.url }
        .sortedWith(
            compareByDescending<Entry> { it.folder }
                .thenBy { it.name.lowercase() }
        )
}

@Composable
private fun VideoScreen(address: String, name: String) {
    val context = LocalContext.current

    val exoPlayer = remember(address) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(address)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            name,
            Modifier.padding(12.dp),
            maxLines = 2
        )

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            update = {
                it.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
