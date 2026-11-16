package com.packfare.app.ui.components

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * A short plain-language description of a place or sight, from Wikipedia's article intro.
 * Looked up once when online, cached on the phone, and absent when offline or when the best match
 * doesn't really look like the thing asked about (so a museum never gets a different town's blurb).
 */
object PlaceBlurbs {
    private const val UA = "Packfare/0.17 (Android travel budget app)"
    private const val MISS_MS = 7L * 24 * 3600 * 1000

    private fun dir(context: Context) = File(context.cacheDir, "place_blurbs").apply { mkdirs() }

    private fun key(query: String): String =
        MessageDigest.getInstance("SHA-1").digest(query.lowercase().trim().toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun load(context: Context, query: String, subject: String): String? = withContext(Dispatchers.IO) {
        val k = key("$query|$subject")
        val txt = File(dir(context), "$k.txt")
        val miss = File(dir(context), "$k.none")
        if (txt.exists()) return@withContext txt.readText()
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < MISS_MS) return@withContext null
        val found = runCatching { fetch(query, subject) }.getOrElse { return@withContext null } // offline: retry later
        if (found == null) { miss.writeText(""); return@withContext null }
        txt.writeText(found)
        found
    }

    private fun fetch(query: String, subject: String): String? {
        val api = "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search" +
            "&gsrnamespace=0&gsrlimit=1&prop=extracts&exintro=1&explaintext=1&exlimit=1&gsrsearch=" +
            URLEncoder.encode(query, "UTF-8")
        val c = URL(api).openConnection() as HttpURLConnection
        val body = try {
            c.setRequestProperty("User-Agent", UA)
            c.connectTimeout = 8_000
            c.readTimeout = 12_000
            if (c.responseCode != 200) return null
            c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
        val page = JSONObject(body).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return null
        val title = page.optString("title")
        val extract = page.optString("extract")
        if (extract.isBlank() || "may refer to" in extract || !com.packfare.app.explore.BlurbText.matches(subject, title)) return null
        return com.packfare.app.explore.BlurbText.tidy(extract)
    }
}

/** A short description of [subject], searched as [query]; draws nothing when none is found. */
@Composable
fun PlaceBlurb(query: String, subject: String, modifier: Modifier = Modifier, maxLines: Int = 3) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by remember(query) { mutableStateOf<String?>(null) }
    LaunchedEffect(query) { text = PlaceBlurbs.load(context, query, subject) }
    text?.let {
        Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}
