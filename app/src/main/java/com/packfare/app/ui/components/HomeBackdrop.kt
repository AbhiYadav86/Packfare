package com.packfare.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.isSystemInDarkTheme
import com.packfare.app.ui.theme.pageColor
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
 * Faded destination photos that cross-fade slowly, for the Home tab. The page colour and the
 * compass are drawn once behind the whole app by [PageBackdrop]; this layer only adds the photos.
 */
@Composable
fun HomeBackdrop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<WallpaperPhoto>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    val bg = pageColor(isSystemInDarkTheme())

    LaunchedEffect(Unit) { photos = DestinationPhotos.load(context) }
    LaunchedEffect(photos.size) {
        while (photos.size > 1) {
            delay(9_000)
            index = (index + 1) % photos.size
        }
    }

    Box(modifier.fillMaxSize()) {
        if (photos.isNotEmpty()) {
            Crossfade(targetState = index % photos.size, animationSpec = tween(2500), label = "photo") { i ->
                Image(
                    bitmap = photos[i].image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.18f),
                )
            }
            // Wash the photo toward the page colour so cards and text stay readable.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(bg.copy(alpha = 0.55f), bg.copy(alpha = 0.15f), bg.copy(alpha = 0.7f))),
                ),
            )
        }
    }
}

/**
 * Solid page colour plus the friendly compass, painted once behind every screen. Screens use a
 * transparent theme background, so the compass shows through on all of them. It sits a little above
 * the middle, which is where the Scan a bill tile lives on Home.
 */
@Composable
fun PageBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(pageColor(dark))) {
        CompassBackdrop(
            Modifier.fillMaxSize(),
            ink = scheme.primary.copy(alpha = if (dark) 0.16f else 0.17f),
            needle = scheme.tertiary.copy(alpha = if (dark) 0.34f else 0.40f),
            page = pageColor(dark),
        )
        content()
    }
}

/**
 * A vintage compass with a bit of personality: it swings its needle on arrival and wobbles to a
 * jaunty tilt, the whole dial bobs gently, and the hub has a little face that blinks. A dotted trail
 * and two sparkles keep it company. Decorative only; it never takes touches.
 */
@Composable
fun CompassBackdrop(
    modifier: Modifier = Modifier,
    ink: Color,
    needle: Color,
    page: Color,
    centerYFraction: Float = 0.36f,
) {
    val swing = remember { Animatable(-70f) }
    LaunchedEffect(Unit) {
        // Overshoot and settle like a real needle finding north-ish, then drift gently.
        swing.animateTo(26f, spring(dampingRatio = 0.22f, stiffness = Spring.StiffnessVeryLow))
        while (true) {
            swing.animateTo(19f, tween(4500, easing = FastOutSlowInEasing))
            swing.animateTo(31f, tween(4500, easing = FastOutSlowInEasing))
        }
    }
    val inf = rememberInfiniteTransition(label = "compass")
    val bob by inf.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    // 1 = eyes open; dips to 0.1 for a quick blink every ~5 seconds.
    val eyes by inf.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes { durationMillis = 5000; 1f at 0; 1f at 4600; 0.1f at 4750; 1f at 4900 },
        ),
        label = "blink",
    )
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 14f))

    Canvas(modifier) {
        val c = Offset(size.width * 0.58f, size.height * centerYFraction)
        val r = min(size.width, size.height) * 0.46f
        val thin = 1.dp.toPx()

        // Dotted trail wandering in from the left, as if the compass had been somewhere.
        val trail = Path().apply {
            moveTo(-20f, c.y + r * 0.95f)
            cubicTo(size.width * 0.12f, c.y + r * 0.40f, size.width * 0.22f, c.y + r * 1.25f, c.x - r * 0.35f, c.y + r * 0.88f)
        }
        drawPath(trail, needle, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))

        rotate(bob, pivot = c) {
            drawCircle(ink, r, c, style = Stroke(3.dp.toPx()))
            drawCircle(ink, r * 0.93f, c, style = Stroke(thin))
            drawCircle(ink, r * 0.64f, c, style = Stroke(thin))

            for (i in 0 until 72) {
                val a = Math.toRadians(i * 5.0 - 90.0)
                val len = when { i % 6 == 0 -> r * 0.13f; i % 2 == 0 -> r * 0.07f; else -> r * 0.04f }
                val outer = r * 0.93f
                drawLine(
                    ink,
                    Offset(c.x + cos(a).toFloat() * (outer - len), c.y + sin(a).toFloat() * (outer - len)),
                    Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                    strokeWidth = if (i % 6 == 0) 2.dp.toPx() else thin,
                )
            }
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0 - 90.0)
                val side = Math.toRadians(k * 45.0)
                val len = if (k % 2 == 0) r * 0.86f else r * 0.55f
                val w = if (k % 2 == 0) r * 0.07f else r * 0.045f
                val tip = Offset(c.x + cos(a).toFloat() * len, c.y + sin(a).toFloat() * len)
                val l = Offset(c.x + cos(side).toFloat() * w, c.y + sin(side).toFloat() * w)
                val rr = Offset(c.x - cos(side).toFloat() * w, c.y - sin(side).toFloat() * w)
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(rr.x, rr.y); close() }, ink)
            }

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(
                    (ink.alpha * 255 * 1.6f).toInt().coerceAtMost(255),
                    (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt(),
                )
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = r * 0.13f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            }
            val lr = r * 1.12f
            val canvas = drawContext.canvas.nativeCanvas
            val dy = paint.textSize * 0.35f
            canvas.drawText("N", c.x, c.y - lr + dy, paint)
            canvas.drawText("S", c.x, c.y + lr + dy, paint)
            canvas.drawText("E", c.x + lr, c.y + dy, paint)
            canvas.drawText("W", c.x - lr, c.y + dy, paint)

            rotate(swing.value, pivot = c) {
                val half = r * 0.075f
                drawPath(Path().apply { moveTo(c.x, c.y - r * 0.74f); lineTo(c.x + half, c.y); lineTo(c.x - half, c.y); close() }, needle)
                drawPath(Path().apply { moveTo(c.x, c.y + r * 0.74f); lineTo(c.x + half, c.y); lineTo(c.x - half, c.y); close() }, ink)
            }

            // The hub is a little face.
            val hub = r * 0.14f
            drawCircle(page, hub, c)
            drawCircle(ink.copy(alpha = ink.alpha * 2f), hub, c, style = Stroke(2.dp.toPx()))
            val ex = hub * 0.38f
            val ey = c.y - hub * 0.18f
            val eyeW = hub * 0.17f
            val eyeH = hub * 0.24f * eyes
            val face = ink.copy(alpha = (ink.alpha * 3.2f).coerceAtMost(0.7f))
            drawOval(face, Offset(c.x - ex - eyeW, ey - eyeH), androidx.compose.ui.geometry.Size(eyeW * 2, eyeH * 2))
            drawOval(face, Offset(c.x + ex - eyeW, ey - eyeH), androidx.compose.ui.geometry.Size(eyeW * 2, eyeH * 2))
            drawArc(
                face, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(c.x - hub * 0.42f, c.y - hub * 0.05f),
                size = androidx.compose.ui.geometry.Size(hub * 0.84f, hub * 0.62f),
                style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(needle.copy(alpha = needle.alpha * 0.6f), hub * 0.15f, Offset(c.x - hub * 0.72f, c.y + hub * 0.28f))
            drawCircle(needle.copy(alpha = needle.alpha * 0.6f), hub * 0.15f, Offset(c.x + hub * 0.72f, c.y + hub * 0.28f))
        }

        // Two sparkles.
        fun sparkle(p: Offset, s: Float, col: Color) {
            val path = Path().apply {
                moveTo(p.x, p.y - s); quadraticBezierTo(p.x, p.y, p.x + s, p.y)
                quadraticBezierTo(p.x, p.y, p.x, p.y + s); quadraticBezierTo(p.x, p.y, p.x - s, p.y)
                quadraticBezierTo(p.x, p.y, p.x, p.y - s); close()
            }
            drawPath(path, col)
        }
        sparkle(Offset(c.x + r * 1.02f, c.y - r * 0.92f), r * 0.11f, needle)
        sparkle(Offset(c.x - r * 1.08f, c.y - r * 0.55f), r * 0.07f, ink.copy(alpha = ink.alpha * 2f))
    }
}
