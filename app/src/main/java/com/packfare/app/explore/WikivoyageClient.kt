package com.packfare.app.explore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SearchHit(val title: String, val snippet: String)

data class PlacePage(
    val title: String,
    val summary: String,
    val lat: Double?,
    val lon: Double?,
    val wikitext: String,
)

/**
 * Tiny client for the public Wikivoyage (MediaWiki) API. No backend, no key.
 *
 * Wikimedia's API etiquette: identify the app in the User-Agent with a way to contact you,
 * make requests one at a time, and cache results (we cache every place in Room).
 * Content is CC BY-SA 4.0: the UI shows attribution and a link to the article.
 */
class WikivoyageClient(
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val baseUrl: String = "https://en.wikivoyage.org/w/api.php",
) {

    suspend fun search(query: String, limit: Int = 10): List<SearchHit> {
        val json = get(
            "action" to "query",
            "list" to "search",
            "srsearch" to query,
            "srnamespace" to "0",
            "srlimit" to limit.toString(),
        )
        val results = json.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            SearchHit(o.getString("title"), stripHtml(o.optString("snippet")))
        }
    }

    /** Summary, coordinates and full wikitext for one article (follows redirects). */
    suspend fun page(title: String): PlacePage? {
        val info = get(
            "action" to "query",
            "prop" to "extracts|coordinates",
            "exintro" to "1",
            "explaintext" to "1",
            "redirects" to "1",
            "titles" to title,
        )
        val page = info.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return null
        if (page.optBoolean("missing", false)) return null
        val realTitle = page.optString("title", title)
        val coords = page.optJSONArray("coordinates")?.optJSONObject(0)

        val parsed = get("action" to "parse", "page" to realTitle, "prop" to "wikitext", "redirects" to "1")
        val wikitext = parsed.optJSONObject("parse")?.optString("wikitext").orEmpty()
        val geo = WikivoyageParser.coordinates(wikitext)

        return PlacePage(
            title = realTitle,
            summary = page.optString("extract").trim(),
            lat = coords?.optDouble("lat")?.takeIf { !it.isNaN() } ?: geo?.first,
            lon = coords?.optDouble("lon")?.takeIf { !it.isNaN() } ?: geo?.second,
            wikitext = wikitext,
        )
    }

    private suspend fun get(vararg params: Pair<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val query = (params.toList() + ("format" to "json") + ("formatversion" to "2"))
            .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val conn = URL("$baseUrl?$query").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) error("Wikivoyage returned HTTP ${conn.responseCode}")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun stripHtml(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&quot;", "\"").replace("&amp;", "&")

    companion object {
        // TODO before release: put a real contact (website or support email) here, per Wikimedia's User-Agent policy.
        const val DEFAULT_USER_AGENT = "Packfare/0.3 (Android travel budget app; contact: packfare.app)"

        fun articleUrl(title: String) = "https://en.wikivoyage.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
    }
}
