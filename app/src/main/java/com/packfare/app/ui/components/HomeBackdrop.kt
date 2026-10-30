package com.packfare.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A faded wallpaper photo of a famous Indian destination. */
class WallpaperPhoto(val title: String, val image: ImageBitmap)

/**
 * Open-licence destination photos for the home wallpaper. They come from Wikipedia's page images
 * (Wikimedia Commons: public domain or Creative Commons), are downloaded once, then cached on the
 * phone and refreshed every couple of weeks. Offline the first time round, the compass stands alone.
 */
object DestinationPhotos {
    private val titles = listOf(
        "Taj Mahal", "Hawa Mahal", "Hampi", "Munnar", "Pangong Tso", "Harmandir Sahib",
        "Varanasi", "Jaisalmer Fort", "Kerala backwaters", "Valley of Flowers National Park",
        "Konark Sun Temple", "Darjeeling",
    )
    private const val REFRESH_MS = 14L * 24 * 3600 * 1000
    private const val UA = "Packfare/0.11 (Android travel budget app; contact: packfare.app)"

    private fun dir(context: Context) = File(context.cacheDir, "wallpapers").apply { mkdirs() }

    suspend fun load(context: Context): List<WallpaperPhoto> = withContext(Dispatchers.IO) {
        val d = dir(context)
        val existing = d.listFiles { f -> f.extension == "jpg" }.orEmpty()
        val stale = existing.isEmpty() || existing.minOf { it.lastModified() } < System.currentTimeMillis() - REFRESH_MS
        if (stale) runCatching { download(d) }
        d.listFiles { f -> f.extension == "jpg" }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
            runCatching {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
                BitmapFactory.decodeFile(f.path, opts)?.let { WallpaperPhoto(f.nameWithoutExtension.replace('_', ' '), it.asImageBitmap()) }
            }.getOrNull()
        }
    }

    private fun download(d: File) {
        val q = "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=pageimages" +
            "&piprop=thumbnail&pithumbsize=900&redirects=1&titles=" + URLEncoder.encode(titles.joinToString("|"), "UTF-8")
        val body = httpGet(q)?.toString(Charsets.UTF_8) ?: return
        val pages = JSONObject(body).optJSONObject("query")?.optJSONArray("pages") ?: return
        for (i in 0 until pages.length()) {
            val p = pages.getJSONObject(i)
            val src = p.optJSONObject("thumbnail")?.optString("source").orEmpty()
            if (src.isBlank()) continue
            val name = p.optString("title").replace(Regex("[^A-Za-z0-9 ]"), "").trim().replace(' ', '_')
            if (name.isBlank()) continue
            val bytes = httpGet(src) ?: continue
            File(d, "$name.jpg").writeBytes(bytes)
        }
    }

    private fun httpGet(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.setRequestProperty("User-Agent", UA)
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally {
            c.disconnect()
        }
    }
}

/**
 * Home wallpaper: faded destination photos that cross-fade slowly, under an old-school
 * navigation compass whose needle gently sways. Purely decorative, never blocks touches.
 */
@Composable
fun HomeBackdrop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<WallpaperPhoto>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    val bg = MaterialTheme.colorScheme.background

    LaunchedEffect(Unit) { photos = DestinationPhotos.load(context) }
    LaunchedEffect(photos.size) {
        while (photos.size > 1) {
            delay(9_000)
            index = (index + 1) % photos.size
        }
    }

    Box(modifier.fillMaxSize().background(bg)) {
        if (photos.isNotEmpty()) {
            Crossfade(targetState = index % photos.size, animationSpec = tween(2500), label = "photo") { i ->
                Image(
                    bitmap = photos[i].image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.20f),
                )
            }
        }
        // Wash the photo out toward the page colour so cards and text stay readable.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(bg.copy(alpha = 0.55f), bg.copy(alpha = 0.15f), bg.copy(alpha = 0.7f))),
            ),
        )
        CompassBackdrop(
            Modifier.fillMaxSize(),
            ink = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
            needle = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f),
        )
    }
}

/** A vintage brass-style compass rose drawn in vectors: rings, degree ticks, 8-point star, N/E/S/W, swaying needle. */
@Composable
fun CompassBackdrop(modifier: Modifier = Modifier, ink: Color, needle: Color) {
    val sway by rememberInfiniteTransition(label = "needle").animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.42f)
        val r = min(size.width, size.height) * 0.40f
        val thin = 1.dp.toPx()
        drawCircle(ink, r, c, style = Stroke(3.dp.toPx()))
        drawCircle(ink, r * 0.93f, c, style = Stroke(thin))
        drawCircle(ink, r * 0.64f, c, style = Stroke(thin))
        drawCircle(ink, r * 0.08f, c)

        // Degree ring: a tick every 5 degrees, longer every 30.
        for (i in 0 until 72) {
            val a = Math.toRadians(i * 5.0 - 90.0)
            val len = when {
                i % 6 == 0 -> r * 0.13f
                i % 2 == 0 -> r * 0.07f
                else -> r * 0.04f
            }
            val outer = r * 0.93f
            drawLine(
                ink,
                Offset(c.x + cos(a).toFloat() * (outer - len), c.y + sin(a).toFloat() * (outer - len)),
                Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                strokeWidth = if (i % 6 == 0) 2.dp.toPx() else thin,
            )
        }

        // 8-point compass rose: long cardinal points, shorter intercardinals.
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0 - 90.0)
            val side = Math.toRadians(k * 45.0 - 90.0 + 90.0)
            val len = if (k % 2 == 0) r * 0.86f else r * 0.55f
            val w = if (k % 2 == 0) r * 0.07f else r * 0.045f
            val tip = Offset(c.x + cos(a).toFloat() * len, c.y + sin(a).toFloat() * len)
            val l = Offset(c.x + cos(side).toFloat() * w, c.y + sin(side).toFloat() * w)
            val rr = Offset(c.x - cos(side).toFloat() * w, c.y - sin(side).toFloat() * w)
            val p = Path().apply {
                moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(rr.x, rr.y); close()
            }
            drawPath(p, ink)
        }

        // Cardinal letters just inside the degree ring.
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((ink.alpha * 255 * 1.4f).toInt().coerceAtMost(255), (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt())
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = r * 0.13f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        }
        val lr = r * 1.1f
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawText("N", c.x, c.y - lr + paint.textSize * 0.35f, paint)
        canvas.drawText("S", c.x, c.y + lr + paint.textSize * 0.35f, paint)
        canvas.drawText("E", c.x + lr, c.y + paint.textSize * 0.35f, paint)
        canvas.drawText("W", c.x - lr, c.y + paint.textSize * 0.35f, paint)

        // Magnetic needle, swaying a little as if settling.
        rotate(sway, pivot = c) {
            val half = r * 0.07f
            val north = Path().apply {
                moveTo(c.x, c.y - r * 0.72f); lineTo(c.x + half, c.y); lineTo(c.x - half, c.y); close()
            }
            val south = Path().apply {
                moveTo(c.x, c.y + r * 0.72f); lineTo(c.x + half, c.y); lineTo(c.x - half, c.y); close()
            }
            drawPath(north, needle)
            drawPath(south, ink)
        }
        drawCircle(ink.copy(alpha = ink.alpha * 1.5f), r * 0.035f, c)
    }
}
