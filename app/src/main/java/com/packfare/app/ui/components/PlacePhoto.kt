package com.packfare.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Small open-licence photos for things to do, from Wikipedia's page images (Wikimedia Commons).
 * Found by searching "<thing> <place>", downloaded once when online, cached on the phone, and simply
 * absent when offline or when nothing matches (a "no photo" marker stops repeated lookups for a week).
 */
object PlacePhotos {
    private const val UA = "Packfare/0.15 (Android travel budget app)"
    private const val MISS_MS = 7L * 24 * 3600 * 1000

    private fun dir(context: Context) = File(context.cacheDir, "place_photos").apply { mkdirs() }

    private fun key(query: String): String =
        MessageDigest.getInstance("SHA-1").digest(query.lowercase().trim().toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun load(context: Context, query: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val k = key(query)
        val jpg = File(dir(context), "$k.jpg")
        val miss = File(dir(context), "$k.none")
        if (jpg.exists()) return@withContext decode(jpg)
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < MISS_MS) return@withContext null
        val bytes = runCatching { download(query) }.getOrNull()
        if (bytes == null) {
            // Offline or a network error: don't remember it as "no photo", just try again next time.
            return@withContext null
        }
        if (bytes.isEmpty()) {
            miss.writeText("")
            return@withContext null
        }
        jpg.writeBytes(bytes)
        decode(jpg)
    }

    private fun decode(f: File): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
        BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
    }.getOrNull()

    /** Bytes of the best match, an empty array when Wikipedia has no photo, or throws when offline. */
    private fun download(query: String): ByteArray {
        val api = "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search" +
            "&gsrnamespace=0&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=320&gsrsearch=" +
            URLEncoder.encode(query, "UTF-8")
        val body = get(api) ?: return ByteArray(0)
        val pages = JSONObject(body.toString(Charsets.UTF_8)).optJSONObject("query")?.optJSONArray("pages")
        val src = pages?.optJSONObject(0)?.optJSONObject("thumbnail")?.optString("source").orEmpty()
        if (src.isBlank()) return ByteArray(0)
        return get(src) ?: ByteArray(0)
    }

    private fun get(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.setRequestProperty("User-Agent", UA)
            c.connectTimeout = 8_000
            c.readTimeout = 12_000
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally {
            c.disconnect()
        }
    }
}

/** A rounded thumbnail for [query] when a photo is available; draws nothing otherwise. */
@Composable
fun PlacePhoto(query: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val context = LocalContext.current
    var image by remember(query) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(query) { image = PlacePhotos.load(context, query) }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)),
        )
    }
}
