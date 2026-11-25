package com.beegear.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.media.AudioManager
import android.view.MotionEvent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
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
import android.view.WindowInsetsController
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Slider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder

// ==== ডেটা মডেলস ====
data class Category(val name: String, val url: String)
data class MediaEntry(val name: String, val url: String, val isFolder: Boolean, val isVideo: Boolean, val isImage: Boolean)
data class FolderLocation(val title: String, val url: String, val poster: String? = null)
data class Recommendation(val title: String, val url: String, val poster: String?, val subtitle: String)
data class SearchHit(val title: String, val url: String, val poster: String?, val subtitle: String, val isVideo: Boolean)

// ==== হেল্পার ফাংশনস ====
private fun readRecommendationList(raw: String?): List<Recommendation> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            Recommendation(
                item.optString("title"),
                item.optString("url"),
                item.optString("poster").ifBlank { null },
                item.optString("subtitle")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// ==== থিম কালারস (মক) ====
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

// ==== ফাইল এক্সটেনশনস ====
private val videoExtensions = listOf(".mp4",".mkv",".avi",".mov",".webm",".m4v",".ts")
private val imageExtensions = listOf(".jpg",".jpeg",".png",".webp",".bmp")

// ==== স্ক্যান/রিপার্স ফাংশনস ====
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
        }.distinctBy { it.url }
          .sortedWith(compareByDescending<MediaEntry> { it.isFolder }.thenBy { it.name.lowercase() })
        Result.success(items)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// ==== রিকমেন্ডেশন লোডার ====
private suspend fun loadRecommendations(): List<Recommendation> {
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
                    categoryItems.add(Recommendation(folder.name, folder.url, directPoster, category.name))
                }
                continue
            }
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
        output.addAll(categoryItems.shuffled())
    }
    return output.distinctBy { it.url }.shuffled()
}

// ==== সার্চ ====
private suspend fun searchAllCategories(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
    val needle = query.trim().lowercase()
    if (needle.isBlank()) return@withContext emptyList()
    val found = mutableListOf<SearchHit>()
    val seen = mutableSetOf<String>()
    for (category in categories) {
        val root = scanFolder(category.url).getOrNull().orEmpty()
        for (entry in root.filter { it.isFolder || it.isVideo }.take(8)) {
            if (entry.isFolder) {
                val contents = scanFolder(entry.url).getOrNull().orEmpty()
                val poster = contents.firstOrNull { it.isImage }?.url
                if (entry.name.lowercase().contains(needle) && contents.any { it.isVideo } && seen.add(entry.url)) {
                    found.add(SearchHit(entry.name, entry.url, poster, category.name, false))
                }
                for (child in contents.filter { it.isFolder }.take(6)) {
                    val childItems = scanFolder(child.url).getOrNull().orEmpty()
                    if (child.name.lowercase().contains(needle) && childItems.any { it.isVideo } && seen.add(child.url)) {
                        found.add(
                            SearchHit(
                                child.name,
                                child.url,
                                childItems.firstOrNull { it.isImage }?.url ?: poster,
                                category.name,
                                false
                            )
                        )
                    }
                    for (video in childItems.filter { it.isVideo }) {
                        if (video.name.lowercase().contains(needle) && seen.add(video.url)) {
                            found.add(SearchHit(video.name, video.url, null, category.name, true))
                        }
                    }
                }
                for (video in contents.filter { it.isVideo }) {
                    if (video.name.lowercase().contains(needle) && seen.add(video.url)) {
                        found.add(SearchHit(video.name, video.url, poster, category.name, true))
                    }
                }
            } else if (entry.isVideo && entry.name.lowercase().contains(needle) && seen.add(entry.url)) {
                found.add(SearchHit(entry.name, entry.url, null, category.name, true))
            }
        }
    }
    found.take(80)
}

// ==== সাবটাইটেল পাসার ====
private val subtitleExtensions = listOf(".srt",".vtt",".ass",".ssa")
private fun subtitleMimeType(uriOrName: String): String? {
    val path = uriOrName.substringBefore("?").lowercase()
    return when {
        path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> null
    }
}
private suspend fun findSameFolderSubtitles(videoUrl: String): List<MediaItem.SubtitleConfiguration> =
    withContext(Dispatchers.IO) {
        try {
            val video = URL(videoUrl)
            val parentUrl = videoUrl.substringBeforeLast("/") + "/"
            val videoBase = video.path.substringAfterLast("/")
                .substringBeforeLast(".")
                .lowercase()
                .replace(Regex("[._-]+"), " ")
                .trim()
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
                    .lowercase()
                    .replace(Regex("[._-]+"), " ")
                    .trim()
                val similarity = subtitleBase == videoBase ||
                        videoBase.startsWith(subtitleBase) ||
                        subtitleBase.startsWith(videoBase)
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

// ==== ভিডিও প্লেয়ার স্ক্রিন (ইন্টিগ্রেটেড) ====
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    title: String,
    onClose: () -> Unit
) {
    // সিস্টেম UI রং টেমার সাথে সামঞ্জস্য
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val window = (ctx as? Activity)?.window ?: return@DisposableEffect onDispose {}
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = window.insetsController
        if (controller != null) {
            val isDark = MaterialTheme.colorScheme.surfaceVariant.luminance() < 0.5
            if (isDark) {
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                window.statusBarColor = Color.Black.toArgb()
                window.navigationBarColor = Color.Black.toArgb()
            } else {
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
                window.statusBarColor = Color.White.toArgb()
                window.navigationBarColor = Color.White.toArgb()
            }
        }
        onDispose {}
    }

    // ExoPlayer সেট‑আপ
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(AndroidView(factory = { player })) {
        onDispose { player.release() }
    }

    var volume by remember { mutableStateOf(player.volume * 100) }
    var playbackSpeed by remember { mutableStateOf(player.playbackParameters.speed * 100) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(volume) { player.volume = volume / 100f }
    LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed / 100f) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .systemBarsPadding()
                    .clickable { showControls = !showControls }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = this@VideoPlayerScreen.player
                            useController = false
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )
                if (showControls) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .padding(16.dp)
                    ) {
                        // ভলিউম
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (volume > 0) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Volume"
                            )
                            Slider(
                                value = volume,
                                onValueChange = { volume = it },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(text = "${volume.roundToInt()}%")
                        }
                        // ব্রাইটনেস (প্লেব্যাক‑স্পিড ডেমো)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (playbackSpeed > 100) Icons.Default.BrightnessHigh else Icons.Default.BrightnessLow,
                                contentDescription = "Brightness"
                            )
                            Slider(
                                value = playbackSpeed,
                                onValueChange = { playbackSpeed = it },
                                valueRange = 50f..150f,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(text = "${playbackSpeed.roundToInt()}%")
                        }
                        TextButton(
                            onClick = onClose,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    )
}

// Helper for playback speed
private fun ExoPlayer.setPlaybackSpeed(speed: Float) {
    setPlaybackParameters(playbackParameters.withSpeed(speed))
}

// ==== MainActivity (অবিকৃত) ====
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appDarkMode = getSharedPreferences("beegear_settings", MODE_PRIVATE)
            .getBoolean("dark_theme", false)

        setContent {
            // সিস্টেম‑বার রঙ থিম‑সাপেক্ষে
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val activity = view.context as Activity
                    val window = activity.window
                    val barColor = if (appDarkMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    window.statusBarColor = barColor
                    window.navigationBarColor = barColor
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !appDarkMode
                    controller.isAppearanceLightNavigationBars = !appDarkMode
                }
            }

            MaterialTheme {
                BeeGearApp()
            }
        }
    }
}

// ==== UI কম্পোজেবলস (Home, Details, ইত্যাদি) ====
// (বাকি ফাংশনগুলো আপনার রিপোজিটরিতে ঠিক আছে – এখানে কেবল পরিবর্তিত অংশ দেখানো হয়েছে)
