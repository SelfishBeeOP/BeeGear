package com.beegear.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.layout.statusBarsPadding\nimport androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLDecoder

data class Category(val name: String, val url: String)
data class MediaEntry(val name: String, val url: String, val isFolder: Boolean, val isVideo: Boolean, val isImage: Boolean)
data class FolderLocation(val title: String, val url: String, val poster: String? = null)
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

// Mockup Theme Colors
private var appDarkMode by mutableStateOf(false)
private val bgDark: Color get() = if (appDarkMode) Color(0xFF11131B) else Color(0xFFF5F7FC)
private val cardDark: Color get() = if (appDarkMode) Color(0xFF222633) else Color(0xFFE8EDF7)
private val tagDark: Color get() = if (appDarkMode) Color(0xFF30384B) else Color(0xFFE8EDFA)
private val navGlass: Color get() = if (appDarkMode) Color(0xE6222633) else Color(0xEFFFFFFF)
private val textMuted: Color get() = if (appDarkMode) Color(0xFFADB7CA) else Color(0xFF718097)
private val primaryText: Color get() = if (appDarkMode) Color(0xFFF4F6FC) else Color(0xFF263B62)
private val accentRed = Color(0xFF6579E8)
private val previewBlue = Color(0xFF7184E8)
private val starGold = Color(0xFFE0A83A)

private val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v", ".ts")
private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp")

private suspend fun scanFolder(url: String): Result<List<MediaEntry>> = withContext(Dispatchers.IO) {
    try {
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(25000).ignoreContentType(true).get()
        val host = URL(url).host
        val items = doc.select("a[href]").mapNotNull { link ->
            val href = link.attr("href").trim()
            if (href.isBlank() || href.startsWith("#") || href.startsWith("?") ||
                href == "../" || href == "./" || href.startsWith("javascript:")
            ) return@mapNotNull null
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
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private suspend fun loadRecommendations(): List<Recommendation> {
    // Build a fresh mixed recommendation pool from EVERY configured server category.
    // Shuffle categories and folders on each refresh so English Movies cannot dominate.
    val output = mutableListOf<Recommendation>()
    val seenUrls = mutableSetOf<String>()

    for (category in categories.shuffled()) {
        if (output.size >= 36) break

        val categoryItems = mutableListOf<Recommendation>()
        val topLevel = scanFolder(category.url).getOrNull().orEmpty()
        val folders = topLevel.filter { it.isFolder }.shuffled().take(8)

        for (folder in folders) {
            if (categoryItems.size >= 4) break

            val inside = scanFolder(folder.url).getOrNull().orEmpty()
            val directVideos = inside.any { it.isVideo }
            val directPoster = inside.firstOrNull { it.isImage }?.url

            if (directVideos) {
                if (seenUrls.add(folder.url)) {
                    categoryItems.add(
                        Recommendation(folder.name, folder.url, directPoster, category.name)
                    )
                }
                continue
            }

            // Many ISP libraries use an extra level (e.g. series -> seasons/episodes).
            // Sample several child folders rather than always taking the first few alphabetically.
            val nestedFolders = inside.filter { it.isFolder }.shuffled().take(5)
            for (child in nestedFolders) {
                if (categoryItems.size >= 4) break
                val childItems = scanFolder(child.url).getOrNull().orEmpty()
                if (childItems.any { it.isVideo }) {
                    if (seenUrls.add(child.url)) {
                        categoryItems.add(
                            Recommendation(
                                child.name,
                                child.url,
                                childItems.firstOrNull { it.isImage }?.url ?: directPoster,
                                category.name
                            )
                        )
                    }
                }
            }
        }

        // Add a small contribution from each category before moving to the next one.
        output.addAll(categoryItems.shuffled())
    }

    return output.distinctBy { it.url }.shuffled()
}


private val subtitleExtensions = listOf(".srt", ".vtt", ".ass", ".ssa")

private fun subtitleMimeType(uriOrName: String): String? {
    val path = uriOrName.substringBefore("?").lowercase()
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> null
    }
}

/**
 * Looks for subtitle files beside the video on the ISP directory listing.
 * Only files with a supported subtitle extension are returned.
 */
private suspend fun findSameFolderSubtitles(videoUrl: String): List<MediaItem.SubtitleConfiguration> =
    withContext(Dispatchers.IO) {
        try {
            val video = URL(videoUrl)
            val parentUrl = videoUrl.substringBeforeLast("/") + "/"
            val videoBase = video.path.substringAfterLast("/").substringBeforeLast(".")
                .lowercase().replace(Regex("[._-]+"), " ").trim()
            val doc = Jsoup.connect(parentUrl).userAgent("Mozilla/5.0")
                .timeout(15000).ignoreContentType(true).get()
            doc.select("a[href]").mapNotNull { link ->
                val address = link.absUrl("href")
                if (address.isBlank()) return@mapNotNull null
                val parsed = try { URL(address) } catch (_: Exception) { return@mapNotNull null }
                if (parsed.host != video.host) return@mapNotNull null
                val name = parsed.path.substringAfterLast("/")
                val mime = subtitleMimeType(name) ?: return@mapNotNull null
                val subtitleBase = name.substringBeforeLast(".")
                    .lowercase().replace(Regex("[._-]+"), " ").trim()
                // Prefer subtitles whose filename resembles the video filename.
                val similarity = subtitleBase == videoBase ||
                    videoBase.startsWith(subtitleBase) || subtitleBase.startsWith(videoBase)
                if (!similarity) return@mapNotNull null
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(address))
                    .setMimeType(mime)
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }.distinctBy { it.uri.toString() }
        } catch (_: Exception) {
            emptyList()
        }
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appDarkMode = getSharedPreferences("beegear_settings", MODE_PRIVATE)
            .getBoolean("dark_theme", false)
        setContent {
            MaterialTheme {
                BeeGearApp()
            }
        }
    }
}

@Composable
fun BeeGearApp() {
    val appContext = LocalContext.current
    var tab by remember { mutableStateOf("HOME") }
    var currentTitle by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var currentPoster by remember { mutableStateOf<String?>(null) }
    var stack by remember { mutableStateOf(emptyList<FolderLocation>()) }
    var entries by remember { mutableStateOf(emptyList<MediaEntry>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var playingTitle by remember { mutableStateOf("") }
    var recommendations by remember { mutableStateOf(emptyList<Recommendation>()) }
    var recommendationsLoading by remember { mutableStateOf(true) }
    var favorites by remember { mutableStateOf(emptyList<Recommendation>()) }

    fun openFolder(title: String, url: String, poster: String? = null) {
        if (currentUrl.isNotBlank()) {
            stack = stack + FolderLocation(currentTitle, currentUrl, currentPoster)
        }
        currentTitle = title
        currentUrl = url
        currentPoster = poster
    }

    fun back() {
        if (playingUrl != null) {
            playingUrl = null
        } else if (stack.isNotEmpty()) {
            val prev = stack.last()
            stack = stack.dropLast(1)
            currentTitle = prev.title
            currentUrl = prev.url
            currentPoster = prev.poster
        } else if (currentUrl.isNotBlank()) {
            currentUrl = ""
            currentTitle = ""
            currentPoster = null
        } else {
            tab = "HOME"
        }
    }

    BackHandler(enabled = playingUrl != null || currentUrl.isNotBlank() || tab != "HOME") { back() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val recommendationScope = rememberCoroutineScope()

    // Refresh recommendations whenever the app comes back to the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                recommendationScope.launch {
                    recommendationsLoading = true
                    recommendations = loadRecommendations()
                    recommendationsLoading = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank()) {
            loading = true
            error = ""
            entries = emptyList()
            val result = scanFolder(currentUrl)
            val fetched = result.getOrNull().orEmpty()
            entries = fetched
            if (currentPoster == null) {
                currentPoster = fetched.firstOrNull { it.isImage }?.url
            }
            if (result.isFailure) {
                error = result.exceptionOrNull()?.localizedMessage ?: "Server connection failed"
            }
            loading = false
        }
    }

    Box(
        Modifier.fillMaxSize()
            .then(if (playingUrl == null) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
            .background(bgDark)
    ) {
        when {
            playingUrl != null -> VideoPlayerScreen(playingUrl!!, playingTitle) { playingUrl = null }

            currentUrl.isNotBlank() -> MediaDetailsScreen(
                title = currentTitle,
                url = currentUrl,
                initialPoster = currentPoster,
                entries = entries,
                loading = loading,
                error = error,
                isFavorite = favorites.any { it.url == currentUrl },
                onToggleFavorite = {
                    favorites = if (favorites.any { it.url == currentUrl }) {
                        favorites.filterNot { it.url == currentUrl }
                    } else {
                        favorites + Recommendation(currentTitle, currentUrl, currentPoster, "Saved Item")
                    }
                },
                onBack = { back() },
                onFolder = { openFolder(it.name, it.url) },
                onVideo = { playingTitle = it.name; playingUrl = it.url }
            )

            tab == "BROWSE" -> BrowseScreen(
                onCategory = {
                    stack = emptyList()
                    openFolder(it.name, it.url)
                }
            )

            tab == "TRENDING" -> TrendingOrSavedScreen(
                screenTitle = "Trending Now",
                items = recommendations.asReversed(),
                onOpen = {
                    stack = emptyList()
                    openFolder(it.title, it.url, it.poster)
                }
            )

            tab == "FAVORITES" -> TrendingOrSavedScreen(
                screenTitle = "My Favorites",
                items = favorites,
                emptyMessage = "এখনও কোনো মুভি ফেভারিট লিস্টে যোগ করা হয়নি।",
                onOpen = {
                    stack = emptyList()
                    openFolder(it.title, it.url, it.poster)
                }
            )

            else -> HomeScreen(
                recommendations = recommendations,
                isDarkTheme = appDarkMode,
                onToggleTheme = {
                    appDarkMode = !appDarkMode
                    appContext.getSharedPreferences("beegear_settings", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("dark_theme", appDarkMode).apply()
                },
                loading = recommendationsLoading,
                onMenuClick = { tab = "BROWSE" },
                onSeeAll = { tab = "BROWSE" },
                onCategory = {
                    stack = emptyList()
                    openFolder(it.name, it.url)
                },
                onOpen = {
                    stack = emptyList()
                    openFolder(it.title, it.url, it.poster)
                }
            )
        }

        // Floating Bottom Navigation Bar (Matches Mockup)
        if (playingUrl == null && currentUrl.isBlank()) {
            FloatingBottomNav(
                tab = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            )
        }
    }
}

/* ============================================================================
   1. HOME SCREEN (Left Phone in Mockup)
   ============================================================================ */
@Composable
private fun HomeScreen(
    recommendations: List<Recommendation>,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    loading: Boolean,
    onMenuClick: () -> Unit,
    onSeeAll: () -> Unit,
    onOpen: (Recommendation) -> Unit,
    onCategory: (Category) -> Unit
) {
    val pageState = rememberScrollState()
    val tabState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val sectionOffsets = remember { mutableStateMapOf<String, Int>() }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(
        Modifier.fillMaxSize()
            .background(bgDark)
            .verticalScroll(pageState)
            .padding(bottom = 112.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bee", color = primaryText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("Gear", color = Color(0xFF6578E8), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (isDarkTheme) Color(0xFF30384B) else Color(0xFFE8EDFF))
                    .clickable { onToggleTheme() },
                contentAlignment = Alignment.Center
            ) { Text(if (isDarkTheme) "☀" else "☾", color = if (isDarkTheme) Color.White else Color(0xFF53688C), fontSize = 19.sp) }
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color.White, Color(0xFFE8EDFF))))
                    .border(1.dp, Color.White, CircleShape)
                    .clickable { onMenuClick() },
                contentAlignment = Alignment.Center
            ) { Text("☰", color = Color(0xFF53688C), fontSize = 20.sp) }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(tabState)
                .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("Home" to null) + categories.map { it.name to it }
            tabs.forEach { (label, category) ->
                Text(
                    label,
                    color = if (category == null) Color(0xFF596FE0) else Color(0xFF40516D),
                    fontSize = 13.sp,
                    fontWeight = if (category == null) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clickable {
                        if (category != null) onCategory(category)
                        else scope.launch { pageState.animateScrollTo(0) }
                    }
                )
            }
        }

        val hero = recommendations.firstOrNull()
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .height(205.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFDCE7FF), Color(0xFFECE5FF), Color(0xFFDDF3F2))))
                .border(1.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(24.dp))
                .clickable { if (hero != null) onOpen(hero) }
        ) {
            if (hero?.poster != null) NetworkPoster(hero.poster, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xCCFFFFFF), Color(0x44FFFFFF), Color.Transparent))
            ))
            Column(Modifier.align(Alignment.CenterStart).padding(20.dp).widthIn(max = 250.dp)) {
                Text("FEATURED FOR YOU", color = Color(0xFF6578D8), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(7.dp))
                Text(hero?.title ?: "Discover something to watch", color = Color(0xFF263B5B), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(hero?.subtitle ?: "Movies and series from your ISP library", color = Color(0xFF536783), fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF6479E8)).clickable { if (hero != null) onOpen(hero) }.padding(horizontal = 17.dp, vertical = 9.dp)) {
                    Text("▶  PLAY NOW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        GlassCategorySection(
            title = "Popular Movies",
            items = recommendations.filter { !it.subtitle.contains("Series", true) }.ifEmpty { recommendations },
            onSeeAll = onSeeAll,
            onOpen = onOpen
        )

        categories.forEach { category ->
            val sectionItems = recommendations.filter {
                it.subtitle.equals(category.name, ignoreCase = true) ||
                    it.subtitle.contains(category.name, ignoreCase = true)
            }
            GlassCategorySection(
                title = category.name,
                items = sectionItems,
                onSeeAll = { onCategory(category) },
                onOpen = onOpen,
                emptyLoading = loading && recommendations.isEmpty(),
                emptyText = if (loading) "Loading from ISP server..." else "Open to browse this category"
            )
        }

        GlassCategorySection(
            title = "New Episodes",
            items = recommendations.filter { it.subtitle.contains("Series", true) }.ifEmpty { recommendations.asReversed() },
            onSeeAll = onSeeAll,
            onOpen = onOpen
        )
    }
}

@Composable
private fun GlassCategorySection(
    title: String,
    items: List<Recommendation>,
    onSeeAll: () -> Unit,
    onOpen: (Recommendation) -> Unit,
    emptyLoading: Boolean = false,
    emptyText: String = "No preview items loaded"
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = .78f))
            .border(1.dp, Color.White, RoundedCornerShape(22.dp))
            .padding(vertical = 13.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color(0xFF293D5D), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("See all  ›", color = Color(0xFF6579E5), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSeeAll() })
        }
        if (items.isEmpty()) {
            if (emptyLoading) {
                Box(Modifier.fillMaxWidth().height(82.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF7184E8), modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            } else {
                Text(emptyText, color = Color(0xFF8A96A9), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp))
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                items(items.take(12), key = { it.url }) { item ->
                    Column(
                        Modifier.width(190.dp).clip(RoundedCornerShape(15.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFF5F7FF), Color(0xFFE9EEFA))))
                            .border(1.dp, Color.White, RoundedCornerShape(15.dp))
                            .clickable { onOpen(item) }
                            .padding(7.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFDCE4F2))) {
                            if (item.poster != null) NetworkPoster(item.poster, Modifier.fillMaxSize())
                            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("BeeGear", color = Color(0xFF7182C8), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text(item.title, color = Color(0xFF2C3E5C), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp, start = 3.dp, end = 3.dp))
                        Text(item.subtitle, color = Color(0xFF8491A6), fontSize = 10.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
                    }
                }
            }
        }
    }
}

/* ============================================================================
   2. DETAILS & FOLDER SCREEN (Right Phone in Mockup)
   ============================================================================ */
@Composable
private fun MediaDetailsScreen(
    title: String,
    url: String,
    initialPoster: String?,
    entries: List<MediaEntry>,
    loading: Boolean,
    error: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onFolder: (MediaEntry) -> Unit,
    onVideo: (MediaEntry) -> Unit
) {
    val folders = entries.filter { it.isFolder }
    val videos = entries.filter { it.isVideo }
    val firstVideo = videos.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(bgDark)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 30.dp)
    ) {
        // Top Backdrop Header with Centered Play Button
        Box(
            Modifier
                .fillMaxWidth()
                .height(310.dp)
        ) {
            if (initialPoster != null) {
                NetworkPoster(initialPoster, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF1F1F26)))
            }

            // Top & Bottom Gradient Fade
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                bgDark
                            )
                        )
                    )
            )

            // Top Navigation Row (Back Button + Brand Icon)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = primaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(accentRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(36.dp))
            }

            // Center Circular Play Button (Plays first video if available)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable(enabled = firstVideo != null) {
                        if (firstVideo != null) onVideo(firstVideo)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = primaryText, fontSize = 20.sp)
            }
        }

        // Overlapping Rounded Sheet Content (Matches Right Phone Mockup)
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (-24).dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(bgDark)
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            // Centered Movie / Folder Title
            Text(
                text = title,
                color = primaryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (videos.isNotEmpty()) "${videos.size} Video File(s) Ready to Stream" else "ISP Media Library Directory",
                color = textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Tag Chips + Share & Heart Icons Row (Matches Mockup)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaChip("HD Stream")
                Spacer(Modifier.width(8.dp))
                MetaChip("16+")
                Spacer(Modifier.width(8.dp))
                MetaChip("★ 4.5", textColor = starGold)

                Spacer(Modifier.weight(1f))

                // Heart / Favorite Toggle Button
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tagDark)
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isFavorite) "♥" else "♡",
                        color = if (isFavorite) accentRed else Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Story Line Section (Matches Mockup)
            Text("Story Line", color = primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Streaming directly from your local ISP media server ($title). Select any playable video or season folder below to start instant playback.",
                color = textMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(22.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentRed)
                }
            } else if (error.isNotBlank()) {
                Text("Server error: $error", color = Color(0xFFFF7777), fontSize = 13.sp)
            } else {
                // Playable Videos List
                if (videos.isNotEmpty()) {
                    Text(
                        "Playable Files (${videos.size})",
                        color = primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    videos.forEach { video ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardDark)
                                .clickable { onVideo(video) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(accentRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▶", color = primaryText, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                video.name,
                                color = primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Sub-Folders shown in "Star Cast / Seasons" Horizontal Cards Style
                if (folders.isNotEmpty()) {
                    Text(
                        "Folders & Seasons (${folders.size})",
                        color = primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    folders.chunked(2).forEach { pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { folder ->
                                FolderCard(folder, Modifier.weight(1f)) { onFolder(folder) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (folders.isEmpty() && videos.isEmpty()) {
                    Text("এই ফোল্ডারে কোনো কনটেন্ট পাওয়া যায়নি।", color = textMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MetaChip(label: String, textColor: Color = Color.White) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tagDark)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FolderCard(folder: MediaEntry, modifier: Modifier, onClick: () -> Unit) {
    var poster by remember(folder.url) { mutableStateOf<String?>(null) }
    LaunchedEffect(folder.url) {
        poster = scanFolder(folder.url).getOrNull()?.firstOrNull { it.isImage }?.url
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardDark)
            .clickable { onClick() }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color(0xFF25252D)),
            contentAlignment = Alignment.Center
        ) {
            if (poster != null) {
                NetworkPoster(poster!!, Modifier.fillMaxSize())
            } else {
                Text("BEEGEAR", color = accentRed, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        Text(
            folder.name,
            Modifier.padding(10.dp),
            color = primaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ============================================================================
   3. BROWSE & SAVED SCREENS
   ============================================================================ */
@Composable
private fun BrowseScreen(onCategory: (Category) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        Text(
            "Browse Categories",
            Modifier.padding(start = 20.dp, top = 24.dp, bottom = 16.dp),
            color = primaryText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
        categories.forEach { category ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardDark)
                    .clickable { onCategory(category) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accentRed.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = accentRed, fontSize = 13.sp)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    category.name,
                    color = primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = textMuted, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun TrendingOrSavedScreen(
    screenTitle: String,
    items: List<Recommendation>,
    emptyMessage: String = "লোড হচ্ছে...",
    onOpen: (Recommendation) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        Text(
            screenTitle,
            Modifier.padding(start = 20.dp, top = 24.dp, bottom = 16.dp),
            color = primaryText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
        if (items.isEmpty()) {
            Text(emptyMessage, Modifier.padding(20.dp), color = textMuted, fontSize = 13.sp)
        } else {
            items.chunked(2).forEach { pair ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { item ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardDark)
                                .clickable { onOpen(item) }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                                    .background(Color(0xFF25252D)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.poster != null) NetworkPoster(item.poster, Modifier.fillMaxSize())
                                else Text("BEEGEAR", color = accentRed, fontWeight = FontWeight.Black)
                            }
                            Text(
                                item.title,
                                Modifier.padding(10.dp),
                                color = primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/* ============================================================================
   4. FLOATING PILL BOTTOM NAVIGATION BAR (Matches Mockup)
   ============================================================================ */
@Composable
private fun FloatingBottomNav(
    tab: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        Triple("HOME", "⌂", "Home"),
        Triple("BROWSE", "◈", "Explore"),
        Triple("TRENDING", "✦", "Trending"),
        Triple("FAVORITES", "♡", "My List")
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = navGlass,
        border = BorderStroke(1.dp, if (appDarkMode) Color(0xFF3A4050) else Color.White),
        shadowElevation = 18.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { (key, icon, label) ->
                val selected = tab == key
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(key) }.padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(43.dp).clip(CircleShape)
                            .background(if (selected) Brush.linearGradient(listOf(Color(0xFFE4E9FF), Color(0xFFF1E8FF))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .border(if (selected) 1.dp else 0.dp, if (selected) Color(0xFFDCE3FF) else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, color = if (selected) Color(0xFF596FE0) else Color(0xFF718097), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(label, color = if (selected) Color(0xFF596FE0) else Color(0xFF718097), fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/* ============================================================================
   5. IMAGE LOADER & EXOPLAYER SCREEN
   ============================================================================ */
@Composable
private fun NetworkPoster(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                URL(url).openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                }.getInputStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Color(0xFF24242C)), contentAlignment = Alignment.Center) {
            Text("BEEGEAR", color = accentRed, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}

@Composable
private fun VideoPlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var playerError by remember(url) { mutableStateOf("") }
    var subtitleDialog by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var embeddedSubtitleTracks by remember { mutableStateOf(emptyList<Pair<String, Pair<androidx.media3.common.TrackGroup, Int>>>()) }
    var externalSubtitleName by remember { mutableStateOf("") }
    var autoSubtitleCount by remember { mutableIntStateOf(0) }
    var autoSubtitleConfigs by remember { mutableStateOf(emptyList<MediaItem.SubtitleConfiguration>()) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }

    // Automatically attach matching .srt/.vtt/.ass/.ssa files found beside the video.
    LaunchedEffect(player, url) {
        val subtitles = findSameFolderSubtitles(url)
        if (subtitles.isNotEmpty()) {
            val position = player.currentPosition
            val wasPlaying = player.playWhenReady
            val item = MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setSubtitleConfigurations(subtitles)
                .build()
            player.setMediaItem(item, position)
            player.prepare()
            player.playWhenReady = wasPlaying
            autoSubtitleConfigs = subtitles
            autoSubtitleCount = subtitles.size
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    val externalSubtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val mime = subtitleMimeType(uri.lastPathSegment ?: uri.toString())
                ?: when (context.contentResolver.getType(uri)) {
                    "text/vtt" -> MimeTypes.TEXT_VTT
                    "application/x-subrip", "application/srt" -> MimeTypes.APPLICATION_SUBRIP
                    else -> null
                }
            if (mime == null) {
                playerError = "এই subtitle format সমর্থিত নয়। .srt অথবা .vtt ফাইল নির্বাচন করুন।"
            } else {
                val position = player.currentPosition
                val wasPlaying = player.playWhenReady
                val currentItem = MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setSubtitleConfigurations(
                        buildList {
                            // Keep automatically discovered subtitles when adding an external file.
                            addAll(autoSubtitleConfigs)
                            add(
                                MediaItem.SubtitleConfiguration.Builder(uri)
                                    .setMimeType(mime)
                                    .setLanguage("und")
                                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                    .build()
                            )
                        }
                    )
                    .build()
                player.setMediaItem(currentItem, position)
                player.prepare()
                player.playWhenReady = wasPlaying
                externalSubtitleName = uri.lastPathSegment ?: "External subtitle"
                playerError = ""
            }
        }
    }

    fun refreshSubtitleTracks() {
        val tracks = mutableListOf<Pair<String, Pair<androidx.media3.common.TrackGroup, Int>>>()
        player.currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (index in 0 until group.length) {
                    if (group.isTrackSupported(index)) {
                        val format = group.getTrackFormat(index)
                        val label = format.label
                            ?: format.language
                            ?: "Subtitle ${tracks.size + 1}"
                        tracks.add(label to (group.mediaTrackGroup to index))
                    }
                }
            }
        }
        embeddedSubtitleTracks = tracks
    }

    LaunchedEffect(player) {
        while (true) {
            refreshSubtitleTracks()
            kotlinx.coroutines.delay(700)
        }
    }

    DisposableEffect(fullscreen) {
        val activity = context as? Activity
        val window = activity?.window
        val decor = window?.decorView
        val oldSystemUi = decor?.systemUiVisibility ?: 0
        val oldOrientation = activity?.requestedOrientation

        if (fullscreen) {
            decor?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            decor?.systemUiVisibility = oldSystemUi
            if (oldOrientation != null) activity?.requestedOrientation = oldOrientation
        }
        onDispose {
            decor?.systemUiVisibility = oldSystemUi
            if (oldOrientation != null) activity?.requestedOrientation = oldOrientation
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!fullscreen) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("‹", color = Color.White, fontSize = 30.sp, modifier = Modifier.clickable { onBack() })
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "CC",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { refreshSubtitleTracks(); subtitleDialog = true }
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "⛶",
                        color = Color.White,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { fullscreen = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            keepScreenOn = true
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        keepScreenOn = true
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "CC",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { refreshSubtitleTracks(); subtitleDialog = true }
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                )
                Text(
                    "⛶",
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { fullscreen = false }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Text(
                "‹",
                color = Color.White,
                fontSize = 30.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                    .clickable { fullscreen = false; onBack() }
            )
        }
        if (playerError.isNotBlank()) {
            Text(
                playerError,
                color = Color(0xFFFF8A80),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            )
        }
    }

    if (subtitleDialog) {
        AlertDialog(
            onDismissRequest = { subtitleDialog = false },
            containerColor = Color(0xFF202027),
            title = { Text("Subtitle settings", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        player.trackSelectionParameters =
                            player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .build()
                        subtitleDialog = false
                    }) {
                        Text("Off", color = Color.White)
                    }
                    if (embeddedSubtitleTracks.isEmpty()) {
                        Text(
                            "No embedded subtitle track detected.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    } else {
                        embeddedSubtitleTracks.forEach { (label, target) ->
                            TextButton(onClick = {
                                val (mediaTrackGroup, trackIndex) = target
                                player.trackSelectionParameters =
                                    player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        .addOverride(TrackSelectionOverride(mediaTrackGroup, listOf(trackIndex)))
                                        .build()
                                subtitleDialog = false
                            }) {
                                Text(label, color = Color.White)
                            }
                        }
                    }
                    TextButton(onClick = {
                        subtitleDialog = false
                        externalSubtitlePicker.launch(
                            arrayOf("application/x-subrip", "text/vtt", "text/*", "*/*")
                        )
                    }) {
                        Text("＋ Open external subtitle", color = Color(0xFFFF6B75))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { subtitleDialog = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }
}
