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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
data class MediaEntry(val name: String, val url: String, val isFolder: Boolean, val isVideo: Boolean, val isImage: Boolean)
data class FolderLocation(val title: String, val url: String)
data class Recommendation(val title: String, val url: String, val poster: String?, val subtitle: String)

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

private val bg = Color(0xFF09090B)
private val surface = Color(0xFF19191D)
private val muted = Color(0xFFAAAAB2)
private val red = Color(0xFFE50914)
private val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v", ".ts")
private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp")

private suspend fun scanFolder(url: String): Result<List<MediaEntry>> = withContext(Dispatchers.IO) {
    try {
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(25000).ignoreContentType(true).get()
        val host = URL(url).host
        val items = doc.select("a[href]").mapNotNull { link ->
            val href = link.attr("href").trim()
            if (href.isBlank() || href.startsWith("#") || href.startsWith("?") ||
                href == "../" || href == "./" || href.startsWith("javascript:")) return@mapNotNull null
            val address = link.absUrl("href")
            if (address.isBlank()) return@mapNotNull null
            val parsed = try { URL(address) } catch (_: Exception) { return@mapNotNull null }
            if (parsed.host != host) return@mapNotNull null
            val path = parsed.path.lowercase()
            val isVideo = videoExtensions.any { path.endsWith(it) }
            val isImage = imageExtensions.any { path.endsWith(it) }
            val isFolder = !isVideo && !isImage && (href.endsWith("/") || parsed.path.endsWith("/"))
            if (!isFolder && !isVideo && !isImage) return@mapNotNull null
            val name = link.text().trim().ifBlank {
                try { URLDecoder.decode(parsed.path.trimEnd('/').substringAfterLast("/"), "UTF-8") }
                catch (_: Exception) { parsed.path.trimEnd('/').substringAfterLast("/") }
            }
            MediaEntry(name, address, isFolder, isVideo, isImage)
        }.distinctBy { it.url }.sortedWith(compareByDescending<MediaEntry> { it.isFolder }.thenBy { it.name.lowercase() })
        Result.success(items)
    } catch (e: Exception) { Result.failure(e) }
}

/* Finds a small set of real folders from the ISP library for the home recommendations.
   It checks at most a few folders per category to avoid hammering the local server. */
private suspend fun loadRecommendations(): List<Recommendation> {
    val output = mutableListOf<Recommendation>()
    for (category in categories) {
        if (output.size >= 12) break
        val top = scanFolder(category.url).getOrNull().orEmpty()
        val folders = top.filter { it.isFolder }.take(4)
        for (folder in folders) {
            if (output.size >= 12) break
            val inside = scanFolder(folder.url).getOrNull().orEmpty()
            val directVideo = inside.firstOrNull { it.isVideo }
            val directPoster = inside.firstOrNull { it.isImage }?.url
            if (directVideo != null || directPoster != null) {
                output.add(Recommendation(folder.name, folder.url, directPoster, category.name))
            } else {
                // Movie libraries often use Category > Year > Movie folder.
                val nested = inside.filter { it.isFolder }.take(3)
                for (movie in nested) {
                    if (output.size >= 12) break
                    val movieItems = scanFolder(movie.url).getOrNull().orEmpty()
                    if (movieItems.any { it.isVideo } || movieItems.any { it.isImage }) {
                        output.add(Recommendation(movie.name, movie.url, movieItems.firstOrNull { it.isImage }?.url, category.name))
                    }
                }
            }
        }
    }
    return output.distinctBy { it.url }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BeeGearApp() } }
    }
}

@Composable
fun BeeGearApp() {
    var tab by remember { mutableStateOf("HOME") }
    var currentTitle by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var stack by remember { mutableStateOf(emptyList<FolderLocation>()) }
    var entries by remember { mutableStateOf(emptyList<MediaEntry>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var playingTitle by remember { mutableStateOf("") }
    var recommendations by remember { mutableStateOf(emptyList<Recommendation>()) }
    var recommendationsLoading by remember { mutableStateOf(true) }

    fun openFolder(title: String, url: String) {
        stack = stack + FolderLocation(currentTitle, currentUrl)
        currentTitle = title
        currentUrl = url
    }
    fun back() {
        if (playingUrl != null) playingUrl = null
        else if (stack.isNotEmpty()) {
            val p = stack.last()
            stack = stack.dropLast(1)
            currentTitle = p.title
            currentUrl = p.url
        } else if (currentUrl.isNotBlank()) {
            currentUrl = ""
            currentTitle = ""
            tab = "BROWSE"
        } else tab = "HOME"
    }

    BackHandler(enabled = playingUrl != null || currentUrl.isNotBlank() || tab != "HOME") { back() }

    LaunchedEffect(Unit) {
        recommendationsLoading = true
        recommendations = loadRecommendations()
        recommendationsLoading = false
    }
    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank()) {
            loading = true
            error = ""
            entries = emptyList()
            val result = scanFolder(currentUrl)
            entries = result.getOrNull().orEmpty()
            if (result.isFailure) error = result.exceptionOrNull()?.localizedMessage ?: "Server connection failed"
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        when {
            playingUrl != null -> VideoPlayerScreen(playingUrl!!, playingTitle) { playingUrl = null }
            currentUrl.isNotBlank() -> FolderScreen(
                title = currentTitle, entries = entries, loading = loading, error = error,
                onBack = { back() },
                onFolder = { openFolder(it.name, it.url) },
                onVideo = { playingTitle = it.name; playingUrl = it.url }
            )
            tab == "BROWSE" -> BrowseScreen(
                onCategory = { currentTitle = it.name; currentUrl = it.url; stack = emptyList() }
            )
            else -> HomeScreen(
                recommendations = recommendations,
                loading = recommendationsLoading,
                onBrowse = { tab = "BROWSE" },
                onOpen = { currentTitle = it.title; currentUrl = it.url; stack = emptyList() }
            )
        }
        if (playingUrl == null && currentUrl.isBlank()) {
            BottomNav(tab = tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun HomeScreen(
    recommendations: List<Recommendation>,
    loading: Boolean,
    onBrowse: () -> Unit,
    onOpen: (Recommendation) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 82.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BEE", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("GEAR", color = red, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("ISP STREAMING", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Text("MAJOR MOVIE PREMIERES", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 12.dp),
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        val hero = recommendations.firstOrNull()
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(390.dp)
                .clip(RoundedCornerShape(18.dp)).background(surface)
                .clickable { if (hero != null) onOpen(hero) }
        ) {
            if (hero?.poster != null) NetworkPoster(hero.poster, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.95f)), startY = 180f)))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("BEEGEAR FEATURED", color = red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(hero?.title ?: "Discover your next favourite",
                    color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, maxLines = 2)
                Text(hero?.subtitle ?: "Movies and series from your ISP library",
                    color = Color.LightGray, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(red).clickable { if (hero != null) onOpen(hero) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text("▶  PLAY / OPEN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("＋  MY LIST", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        SectionTitle("Popular Movies")
        RecommendationRail(recommendations.filter { !it.subtitle.contains("Series", true) }.ifEmpty { recommendations }, onOpen)
        SectionTitle("Recommended for You")
        RecommendationRail(recommendations.asReversed(), onOpen)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Browse the full ISP library", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("SEE ALL  ›", color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBrowse() })
        }
        if (loading) Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = red)
        }
        if (recommendations.isEmpty() && !loading) Text(
            "সার্ভার থেকে রেকমেন্ডেশন পাওয়া যায়নি। Browse থেকে ক্যাটাগরি খুলে দেখুন।",
            Modifier.padding(20.dp), color = muted, fontSize = 13.sp
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp),
        color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun RecommendationRail(items: List<Recommendation>, onOpen: (Recommendation) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.take(12), key = { it.url }) { item ->
            Column(Modifier.width(132.dp).clickable { onOpen(item) }) {
                Box(Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(10.dp)).background(surface)) {
                    if (item.poster != null) NetworkPoster(item.poster, Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("BEEGEAR", color = red, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
                Text(item.title, Modifier.padding(top = 8.dp), color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, color = muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BrowseScreen(onCategory: (Category) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
        Text("Browse", Modifier.padding(start = 20.dp, top = 24.dp, bottom = 18.dp),
            color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        categories.forEach { category ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(12.dp)).background(surface).clickable { onCategory(category) }.padding(17.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF29292F)), contentAlignment = Alignment.Center) {
                    Text("▶", color = red, fontSize = 15.sp)
                }
                Spacer(Modifier.width(13.dp))
                Text(category.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("›", color = muted, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun FolderScreen(
    title: String, entries: List<MediaEntry>, loading: Boolean, error: String,
    onBack: () -> Unit, onFolder: (MediaEntry) -> Unit, onVideo: (MediaEntry) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 25.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = red, fontSize = 34.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (loading) Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = red) }
        else if (error.isNotBlank()) Text("Server error: $error", Modifier.padding(20.dp), color = Color(0xFFFF7777))
        else {
            val folders = entries.filter { it.isFolder }
            val videos = entries.filter { it.isVideo }
            folders.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { folder ->
                        FolderCard(folder, Modifier.weight(1f)) { onFolder(folder) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
            videos.forEach { video ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(surface).clickable { onVideo(video) }.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("▶", color = red)
                    Spacer(Modifier.width(12.dp))
                    Text(video.name, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
            if (folders.isEmpty() && videos.isEmpty()) Text("এই ফোল্ডারে কোনো কনটেন্ট পাওয়া যায়নি।", Modifier.padding(20.dp), color = muted)
        }
    }
}

@Composable
private fun FolderCard(folder: MediaEntry, modifier: Modifier, onClick: () -> Unit) {
    var poster by remember(folder.url) { mutableStateOf<String?>(null) }
    LaunchedEffect(folder.url) { poster = scanFolder(folder.url).getOrNull()?.firstOrNull { it.isImage }?.url }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(surface).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF25252B)), contentAlignment = Alignment.Center) {
            if (poster != null) NetworkPoster(poster!!, Modifier.fillMaxSize())
            else Text("BEEGEAR", color = red, fontWeight = FontWeight.Black)
        }
        Text(folder.name, Modifier.padding(10.dp), color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BottomNav(tab: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(Color(0xF20D0D10)).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf("HOME", "BROWSE", "MY LIST").forEach { item ->
            Column(Modifier.clickable { if (item != "MY LIST") onSelect(item) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when (item) { "HOME" -> "⌂"; "BROWSE" -> "▦"; else -> "♡" },
                    color = if (tab == item) red else muted, fontSize = 21.sp)
                Text(item, color = if (tab == item) Color.White else muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NetworkPoster(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                URL(url).openConnection().apply { connectTimeout = 10000; readTimeout = 15000 }
                    .getInputStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Color(0xFF24242A)), contentAlignment = Alignment.Center) {
        Text("BEEGEAR", color = red, fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

@Composable
private fun VideoPlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Color.White, fontSize = 30.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; keepScreenOn = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
