package com.beegear.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLDecoder

data class Category(val name: String, val url: String)

data class MediaEntry(
    val name: String,
    val url: String,
    val isFolder: Boolean,
    val isVideo: Boolean,
    val isImage: Boolean
)

data class FolderLocation(val title: String, val url: String)

private val categories = listOf(
    Category("English Movies", "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/"),
    Category("English Movies 1080p", "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29/"),
    Category("IMDb Top 250", "http://172.16.50.14/DHAKA-FLIX-14/IMDb%20Top-250%20Movies/"),
    Category("Hindi Movies", "http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/"),
    Category("South Indian Hindi Dubbed", "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/"),
    Category("TV & Web Series", "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/"),
    Category("Anime & Cartoon", "http://172.16.50.10/DHAKA-FLIX-10/Anime%20%26%20Cartoon%20TV%20Series/"),
    Category("Korean TV & Web Series", "http://172.16.50.14/DHAKA-FLIX-14/KOREAN%20TV%20%26%20WEB%20Series/"),
    Category("Animation Movies", "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies/")
)

private val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v", ".ts")
private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp")

private val bg = Color(0xFF080914)
private val panel = Color(0xFF171A2B)
private val accent = Color(0xFF8B5CF6)
private val cyan = Color(0xFF35C9F5)

private suspend fun scanFolder(url: String): Result<List<MediaEntry>> =
    withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(25000)
                .ignoreContentType(true)
                .get()

            val host = URL(url).host

            val items = document.select("a[href]").mapNotNull { link ->
                val href = link.attr("href").trim()

                if (href.isBlank() || href.startsWith("#") ||
                    href.startsWith("?") || href == "../" ||
                    href == "./" || href.startsWith("javascript:")
                ) return@mapNotNull null

                val address = link.absUrl("href")
                if (address.isBlank()) return@mapNotNull null

                val parsed = try {
                    URL(address)
                } catch (_: Exception) {
                    return@mapNotNull null
                }

                if (parsed.host != host) return@mapNotNull null

                val path = parsed.path.lowercase()
                val isVideo = videoExtensions.any { path.endsWith(it) }
                val isImage = imageExtensions.any { path.endsWith(it) }
                val isFolder = !isVideo && !isImage &&
                    (href.endsWith("/") || parsed.path.endsWith("/"))

                if (!isFolder && !isVideo && !isImage) return@mapNotNull null

                val name = link.text().trim().ifBlank {
                    try {
                        URLDecoder.decode(
                            parsed.path.trimEnd('/').substringAfterLast("/"),
                            "UTF-8"
                        )
                    } catch (_: Exception) {
                        parsed.path.trimEnd('/').substringAfterLast("/")
                    }
                }

                MediaEntry(name, address, isFolder, isVideo, isImage)
            }.distinctBy { it.url }
                .sortedWith(
                    compareByDescending<MediaEntry> { it.isFolder }
                        .thenBy { it.name.lowercase() }
                )

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

@Composable
fun BeeGearApp() {
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var currentTitle by remember { mutableStateOf("BEEGEAR") }
    var currentUrl by remember { mutableStateOf("") }
    var folderStack by remember { mutableStateOf(listOf<FolderLocation>()) }
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var playingTitle by remember { mutableStateOf("") }

    fun openFolder(title: String, url: String) {
        folderStack = folderStack + FolderLocation(currentTitle, currentUrl)
        currentTitle = title
        currentUrl = url
    }

    fun goBack() {
        if (playingUrl != null) {
            playingUrl = null
        } else if (folderStack.isNotEmpty()) {
            val previous = folderStack.last()
            folderStack = folderStack.dropLast(1)
            currentTitle = previous.title
            currentUrl = previous.url
        } else {
            selectedCategory = null
            currentTitle = "BEEGEAR"
            currentUrl = ""
            entries = emptyList()
        }
    }

    BackHandler(enabled = playingUrl != null || selectedCategory != null) {
        goBack()
    }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank()) {
            loading = true
            errorMessage = ""
            entries = emptyList()
            val result = scanFolder(currentUrl)
            if (result.isSuccess) {
                entries = result.getOrDefault(emptyList())
            } else {
                errorMessage = result.exceptionOrNull()?.localizedMessage
                    ?: "Unable to read server folder."
            }
            loading = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        if (playingUrl != null) {
            VideoPlayerScreen(
                url = playingUrl!!,
                title = playingTitle,
                onBack = { playingUrl = null }
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101222))
                        .padding(horizontal = 20.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BEE", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("GEAR", color = cyan, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "● ISP STREAMING",
                        color = Color(0xFF7DD3FC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedCategory == null) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(225.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF24164A),
                                        Color(0xFF172B50),
                                        Color(0xFF101321)
                                    )
                                )
                            )
                    ) {
                        Column(
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(22.dp)
                        ) {
                            Text(
                                "YOUR ENTERTAINMENT HUB",
                                color = cyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Movies. Series.\nUnlimited Stories.",
                                color = Color.White,
                                fontSize = 27.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Explore your ISP library",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(accent)
                                    .clickable {
                                        val category = categories.first()
                                        selectedCategory = category
                                        currentTitle = category.name
                                        currentUrl = category.url
                                        folderStack = emptyList()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text("Explore Now  ›", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "BG",
                            Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            fontSize = 95.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Text(
                        "Browse Categories",
                        Modifier.padding(start = 18.dp, top = 25.dp, bottom = 12.dp),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(Modifier.padding(horizontal = 14.dp)) {
                        categories.chunked(2).forEach { rowItems ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { category ->
                                    CategoryTile(
                                        category.name,
                                        Modifier.weight(1f)
                                    ) {
                                        selectedCategory = category
                                        currentTitle = category.name
                                        currentUrl = category.url
                                        folderStack = emptyList()
                                    }
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }

                    Text(
                        "Welcome to BeeGear",
                        Modifier.padding(start = 18.dp, top = 22.dp, bottom = 8.dp),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your local ISP movie and series collection.",
                        Modifier.padding(horizontal = 18.dp),
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "‹",
                            color = cyan,
                            fontSize = 34.sp,
                            modifier = Modifier.clickable { goBack() }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(currentTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(selectedCategory?.name ?: "", color = Color.Gray, fontSize = 11.sp)
                        }
                    }

                    if (loading) {
                        Box(
                            Modifier.fillMaxWidth().height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = cyan)
                                Spacer(Modifier.height(12.dp))
                                Text("Loading ISP library...", color = Color.LightGray)
                            }
                        }
                    } else if (errorMessage.isNotBlank()) {
                        Text(
                            "Server error:\n$errorMessage",
                            Modifier.padding(20.dp),
                            color = Color(0xFFFF8585)
                        )
                    } else {
                        val folders = entries.filter { it.isFolder }
                        val videos = entries.filter { it.isVideo }
                        val images = entries.filter { it.isImage }

                        if (images.isNotEmpty()) {
                            PosterImage(
                                images.first().url,
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        if (folders.isNotEmpty()) {
                            Text(
                                if (currentTitle.contains("Season", true)) "EPISODES" else "EXPLORE",
                                Modifier.padding(start = 18.dp, bottom = 12.dp, top = 8.dp),
                                color = cyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            folders.chunked(2).forEach { rowItems ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { folder ->
                                        FolderPosterCard(
                                            folder = folder,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            openFolder(folder.name, folder.url)
                                        }
                                    }
                                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        if (videos.isNotEmpty()) {
                            Text(
                                "PLAY VIDEOS",
                                Modifier.padding(start = 18.dp, top = 18.dp, bottom = 12.dp),
                                color = cyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            videos.forEach { video ->
                                VideoRow(video.name) {
                                    playingTitle = video.name
                                    playingUrl = video.url
                                }
                            }
                        }

                        if (folders.isEmpty() && videos.isEmpty() && images.isEmpty()) {
                            Text(
                                "এই ফোল্ডারে কোনো কনটেন্ট পাওয়া যায়নি।",
                                Modifier.padding(20.dp),
                                color = Color.LightGray
                            )
                        }
                    }
                }
                Spacer(Modifier.height(35.dp))
            }
        }
    }
}

@Composable
fun CategoryTile(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(65.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF252044), Color(0xFF17263D))
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF493A78)),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = Color.White, fontSize = 13.sp)
        }
        Spacer(Modifier.width(9.dp))
        Text(
            name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FolderPosterCard(
    folder: MediaEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var posterUrl by remember(folder.url) { mutableStateOf<String?>(null) }
    var checked by remember(folder.url) { mutableStateOf(false) }

    LaunchedEffect(folder.url) {
        val result = scanFolder(folder.url)
        posterUrl = result.getOrNull()?.firstOrNull { it.isImage }?.url
        checked = true
    }

    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(panel)
            .clickable { onClick() }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF34365A), Color(0xFF171A2B))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (posterUrl != null) {
                PosterImage(
                    posterUrl!!,
                    Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (checked) "▶" else "✦",
                        color = cyan,
                        fontSize = 34.sp
                    )
                    Text(
                        if (checked) "BEEGEAR" else "LOADING",
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            folder.name,
            Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VideoRow(name: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(panel)
            .clickable { onClick() }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▶", color = cyan, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text("›", color = accent, fontSize = 22.sp)
    }
}

@Composable
fun PosterImage(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) {
            try {
                URL(url).openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                }.getInputStream().use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier.background(Color(0xFF20243A)),
            contentAlignment = Alignment.Center
        ) {
            Text("BEEGEAR", color = cyan, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun VideoPlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "‹  BACK",
            Modifier.clickable { onBack() }.padding(16.dp),
            color = Color.White,
            fontSize = 16.sp
        )
        Text(
            title,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 14.sp
        )
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
