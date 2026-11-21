Package com.beegear.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL

data class Category(val name: String, val url: String)

data class Movie(
    val name: String,
    val url: String,
    val image: String?,
    val category: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BeeGearHome()
            }
        }
    }
}

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

private suspend fun loadMovies(category: Category): List<Movie> {
    return withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(category.url)
                .userAgent("Mozilla/5.0")
                .timeout(20000)
                .get()

            document.select("a[href]").mapNotNull { link ->
                val address = link.absUrl("href")
                val fileName = link.text().trim()
                    .ifBlank { link.selectFirst("img")?.attr("alt") ?: "" }

                val isVideo = address.substringBefore("?")
                    .lowercase()
                    .let {
                        listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v", ".ts")
                            .any { ext -> it.endsWith(ext) }
                    }

                if (isVideo && address.isNotBlank()) {
                    val imageElement = link.selectFirst("img")
                    val imageUrl = imageElement?.let {
                        it.absUrl("src").ifBlank { it.absUrl("data-src") }
                    }?.takeIf { it.isNotBlank() }

                    Movie(
                        name = fileName.ifBlank {
                            address.substringAfterLast("/").substringBeforeLast(".")
                        },
                        url = address,
                        image = imageUrl,
                        category = category.name
                    )
                } else null
            }.distinctBy { it.url }.take(40)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Composable
fun BeeGearHome() {
    var movieRows by remember { mutableStateOf<Map<String, List<Movie>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        val result = mutableMapOf<String, List<Movie>>()
        categories.forEach { category ->
            result[category.name] = loadMovies(category)
        }
        movieRows = result
        loading = false
    }

    val allMovies = movieRows.values.flatten()
    val featured = allMovies.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF050713),
                        Color(0xFF10152D),
                        Color(0xFF080817),
                        Color(0xFF160D2B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x553A63FF),
                            Color.Transparent
                        ),
                        radius = 850f
                    )
                )
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BEE",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "GEAR",
                    color = Color(0xFF53C8FF),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "HOME",
                    color = Color(0xFF9BDFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF55C8FF))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Connecting to your ISP server...",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (featured != null) {
                FeaturedSection(featured)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0x55333B60))
                        .padding(22.dp)
                ) {
                    Text(
                        "No videos found. Connect to your ISP network and try again.",
                        color = Color.White
                    )
                }
            }

            if (!loading) {
                categories.forEach { category ->
                    val movies = movieRows[category.name].orEmpty()
                    if (movies.isNotEmpty()) {
                        MovieRow(category.name, movies)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun FeaturedSection(movie: Movie) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(270.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF263A70),
                        Color(0xFF17172F),
                        Color(0xFF44245D)
                    )
                )
            )
            .border(
                1.dp,
                Color(0x6659CFFF),
                RoundedCornerShape(25.dp)
            )
    ) {
        if (movie.image != null) {
            PosterImage(
                url = movie.image,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xDD080A18)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp)
        ) {
            Text(
                "FEATURED",
                color = Color(0xFF67D5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                movie.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                movie.category,
                color = Color.LightGray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun MovieRow(title: String, movies: List<Movie>) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 18.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            movies.forEach { movie ->
                MovieCard(movie)
            }
        }
    }
}

@Composable
fun MovieCard(movie: Movie) {
    Column(
        modifier = Modifier
            .width(145.dp)
            .padding(horizontal = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x55333D64))
            .border(
                1.dp,
                Color(0x5557BFFF),
                RoundedCornerShape(16.dp)
            )
            .focusable()
            .clickable { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF253B72),
                            Color(0xFF17172D),
                            Color(0xFF45255B)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (movie.image != null) {
                PosterImage(
                    url = movie.image,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "BEE\nGEAR",
                    color = Color(0xFF7CCFFF),
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp
                )
            }
        }

        Text(
            text = movie.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            modifier = Modifier.padding(9.dp)
        )
    }
}

@Composable
fun PosterImage(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                URL(url).openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 10000
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
            contentScale = ContentScale.Crop
        )
    }
}
