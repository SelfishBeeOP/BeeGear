
package com.beegear.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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

data class Category(
    val name: String,
    val url: String
)

data class MediaEntry(
    val name: String,
    val url: String,
    val isFolder: Boolean,
    val isVideo: Boolean,
    val isImage: Boolean
)

data class FolderLocation(
    val title: String,
    val url: String
)

private val categories = listOf(
    Category(
        "English Movies",
        "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/"
    ),
    Category(
        "English Movies 1080p",
        "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29/"
    ),
    Category(
        "IMDb Top 250",
        "http://172.16.50.14/DHAKA-FLIX-14/IMDb%20Top-250%20Movies/"
    ),
    Category(
        "Hindi Movies",
        "http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/"
    ),
    Category(
        "South Indian Hindi Dubbed",
        "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/"
    ),
    Category(
        "TV & Web Series",
        "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/"
    ),
    Category(
        "Anime & Cartoon",
        "http://172.16.50.10/DHAKA-FLIX-10/Anime%20%26%20Cartoon%20TV%20Series/"
    ),
    Category(
        "Korean TV & Web Series",
        "http://172.16.50.14/DHAKA-FLIX-14/KOREAN%20TV%20%26%20WEB%20Series/"
    ),
    Category(
        "Animation Movies",
        "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies/"
    )
)

private val videoExtensions = listOf(
    ".mp4", ".mkv", ".avi", ".mov",
    ".webm", ".m4v", ".ts"
)

private val imageExtensions = listOf(
    ".jpg", ".jpeg", ".png", ".webp", ".bmp"
)

// SERVER FOLDER SCANNER
private suspend fun scanFolder(url: String): Result<List<MediaEntry>> {
    return withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(25000)
                .ignoreContentType(true)
                .get()

            val baseHost = URL(url).host

            val entries = document.select("a[href]")
                .mapNotNull { link ->

                    val rawHref = link.attr("href").trim()

                    if (
                        rawHref.isBlank() ||
                        rawHref.startsWith("#") ||
                        rawHref.startsWith("?") ||
                        rawHref == "../" ||
                        rawHref == "./" ||
                        rawHref.startsWith("javascript:")
                    ) {
                        return@mapNotNull null
                    }

                    val address = link.absUrl("href")

                    if (address.isBlank()) {
                        return@mapNotNull null
                    }

                    val parsedUrl = try {
                        URL(address)
                    } catch (e: Exception) {
                        return@mapNotNull null
                    }

                    if (parsedUrl.host != baseHost) {
                        return@mapNotNull null
                    }

                    val path = parsedUrl.path.lowercase()

                    val isVideo = videoExtensions.any {
                        path.endsWith(it)
                    }

                    val isImage = imageExtensions.any {
                        path.endsWith(it)
                    }

                    val isFolder =
                        !isVideo &&
                        !isImage &&
                        (
                            rawHref.endsWith("/") ||
                            parsedUrl.path.endsWith("/")
                        )

                    if (!isFolder && !isVideo && !isImage) {
                        return@mapNotNull null
                    }

                    val fileName = link.text().trim()
                        .ifBlank {
                            try {
                                URLDecoder.decode(
                                    parsedUrl.path
                                        .trimEnd('/')
                                        .substringAfterLast("/"),
                                    "UTF-8"
                                )
                            } catch (e: Exception) {
                                parsedUrl.path
                                    .trimEnd('/')
                                    .substringAfterLast("/")
                            }
                        }

                    MediaEntry(
                        name = fileName,
                        url = address,
                        isFolder = isFolder,
                        isVideo = isVideo,
                        isImage = isImage
                    )
                }
                .distinctBy { it.url }
                .sortedWith(
                    compareByDescending<MediaEntry> { it.isFolder }
                        .thenBy { it.name.lowercase() }
                )

            Result.success(entries)

        } catch (e: Exception) {
            Result.failure(e)
        }
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

    var selectedCategory by remember {
        mutableStateOf<Category?>(null)
    }

    var currentTitle by remember {
        mutableStateOf("BEEGEAR")
    }

    var currentUrl by remember {
        mutableStateOf("")
    }

    var folderStack by remember {
        mutableStateOf(listOf<FolderLocation>())
    }

    var entries by remember {
        mutableStateOf<List<MediaEntry>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var playingUrl by remember {
        mutableStateOf<String?>(null)
    }

    var playingTitle by remember {
        mutableStateOf("")
    }

    fun goBack() {
        if (playingUrl != null) {
            playingUrl = null
            return
        }

        if (folderStack.isNotEmpty()) {
            val newStack = folderStack.dropLast(1)
            folderStack = newStack

            if (newStack.isNotEmpty()) {
                val previous = newStack.last()
                currentTitle = previous.title
                currentUrl = previous.url
            } else {
                val category = selectedCategory
                if (category != null) {
                    currentTitle = category.name
                    currentUrl = category.url
                }
            }
        } else {
            selectedCategory = null
            currentTitle = "BEEGEAR"
            currentUrl = ""
            entries = emptyList()
        }
    }

    BackHandler(
        enabled = playingUrl != null ||
            selectedCategory != null
    ) {
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
                errorMessage = result.exceptionOrNull()
                    ?.localizedMessage
                    ?: "Unable to read server folder."
            }

            loading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF050713),
                        Color(0xFF10152D),
                        Color(0xFF080817),
                        Color(0xFF160D2B)
                    )
                )
            )
    ) {

        if (playingUrl != null) {

            VideoPlayerScreen(
                url = playingUrl!!,
                title = playingTitle,
                onBack = { playingUrl = null }
            )

        } else {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(16.dp)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "BEE",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        text = "GEAR",
                        color = Color(0xFF53C8FF),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = "ISP STREAMING",
                        color = Color(0xFF75CFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedCategory == null) {

                    Text(
                        "Explore Categories",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            top = 20.dp,
                            bottom = 16.dp
                        )
                    )

                    categories.forEach { category ->

                        CategoryCard(
                            name = category.name,
                            onClick = {
                                selectedCategory = category
                                currentTitle = category.name
                                currentUrl = category.url
                                folderStack = emptyList()
                            }
                        )
                    }

                } else {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 12.dp,
                                bottom = 20.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            "‹",
                            color = Color(0xFF59CFFF),
                            fontSize = 34.sp,
                            modifier = Modifier
                                .clickable { goBack() }
                                .padding(end = 12.dp)
                        )

                        Column {
                            Text(
                                currentTitle,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                selectedCategory?.name ?: "",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (loading) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF55C8FF)
                                )

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    "Reading ISP server...",
                                    color = Color.LightGray
                                )
                            }
                        }

                    } else if (errorMessage.isNotBlank()) {

                        Text(
                            "Server error:\n$errorMessage",
                            color = Color(0xFFFF8B8B),
                            modifier = Modifier.padding(16.dp)
                        )

                    } else {

                        val poster = entries.firstOrNull {
                            it.isImage
                        }

                        if (poster != null) {
                            PosterImage(
                                url = poster.url,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                                    .clip(
                                        RoundedCornerShape(18.dp)
                                    )
                            )

                            Spacer(Modifier.height(18.dp))
                        }

                        val folders = entries.filter {
                            it.isFolder
                        }

                        val videos = entries.filter {
                            it.isVideo
                        }

                        if (folders.isNotEmpty()) {

                            Text(
                                "FOLDERS",
                                color = Color(0xFF62CAFF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    top = 10.dp,
                                    bottom = 10.dp
                                )
                            )

                            folders.forEach { folder ->

                                FolderCard(
                                    name = folder.name,
                                    onClick = {

                                        folderStack =
                                            folderStack + FolderLocation(
                                                currentTitle,
                                                currentUrl
                                            )

                                        currentTitle = folder.name
                                        currentUrl = folder.url
                                    }
                                )
                            }
                        }

                        if (videos.isNotEmpty()) {

                            Text(
                                "VIDEOS / EPISODES",
                                color = Color(0xFF62CAFF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    top = 22.dp,
                                    bottom = 10.dp
                                )
                            )

                            videos.forEach { video ->

                                VideoCard(
                                    name = video.name,
                                    onClick = {
                                        playingTitle = video.name
                                        playingUrl = video.url
                                    }
                                )
                            }
                        }

                        if (
                            folders.isEmpty() &&
                            videos.isEmpty()
                        ) {
                            Text(
                                "এই ফোল্ডারে কোনো ফোল্ডার বা ভিডিও পাওয়া যায়নি।",
                                color = Color.LightGray,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun CategoryCard(
    name: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0x55333D64))
            .clickable { onClick() }
            .focusable()
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            "▣",
            color = Color(0xFF59CFFF),
            fontSize = 23.sp
        )

        Spacer(Modifier.width(14.dp))

        Text(
            name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.weight(1f))

        Text(
            "›",
            color = Color(0xFF59CFFF),
            fontSize = 25.sp
        )
    }
}

@Composable
fun FolderCard(
    name: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x55333D64))
            .clickable { onClick() }
            .focusable()
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            "📁",
            fontSize = 22.sp
        )

        Spacer(Modifier.width(14.dp))

        Text(
            name,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            "›",
            color = Color(0xFF59CFFF),
            fontSize = 23.sp
        )
    }
}

@Composable
fun VideoCard(
    name: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x55333D64))
            .clickable { onClick() }
            .focusable()
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            "▶",
            color = Color(0xFF59CFFF),
            fontSize = 21.sp
        )

        Spacer(Modifier.width(14.dp))

        Text(
            name,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun PosterImage(
    url: String,
    modifier: Modifier = Modifier
) {

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = url
    ) {

        value = withContext(Dispatchers.IO) {
            try {
                URL(url).openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                }.getInputStream().use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {

        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {

        Box(
            modifier = modifier
                .background(Color(0xFF202844)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "BEEGEAR",
                color = Color(0xFF59CFFF),
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun VideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    val player = remember(url) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Text(
            "‹  BACK",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable { onBack() }
                .focusable()
                .padding(16.dp)
        )

        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        )

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    keepScreenOn = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
