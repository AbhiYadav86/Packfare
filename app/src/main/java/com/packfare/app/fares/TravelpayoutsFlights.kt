package com.packfare.app.fares

import com.packfare.app.domain.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Live flight prices from the Travelpayouts (Aviasales) Data API.
 *
 * - Free: sign up at travelpayouts.com, join the Aviasales program, copy your API token.
 * - Prices are real fares other travellers found in the last few days (cached, not a live seat
 *   check), which is exactly right for "what's the cheapest way to get there?".
 * - Bookings through the returned links earn you an affiliate commission (with your marker).
 *
 * Docs: https://support.travelpayouts.com/hc/en-us/articles/203956163-Aviasales-Data-API
 */
class TravelpayoutsFlights(
    private val token: String,
    private val marker: String?,
) {
    val enabled: Boolean get() = token.isNotBlank()

    /** Cheapest one-way fares for the date; falls back to the whole month if that day has none. */
    suspend fun cheapest(origin: String, destination: String, date: LocalDate): List<FareQuote> {
        if (!enabled || origin == destination) return emptyList()
        val exact = query(origin, destination, date.toString())
        if (exact.isNotEmpty()) return exact
        return query(origin, destination, date.toString().substring(0, 7)) // YYYY-MM
            .map { it.copy(note = listOfNotNull(it.note, "Cheapest day this month, not your exact date").joinToString(" · ")) }
    }

    private suspend fun query(origin: String, destination: String, departureAt: String): List<FareQuote> =
        withContext(Dispatchers.IO) {
            val params = listOf(
                "origin" to origin,
                "destination" to destination,
                "departure_at" to departureAt,
                "one_way" to "true",
                "direct" to "false",
                "sorting" to "price",
                "currency" to "inr",
                "limit" to "10",
            ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            val conn = URL("https://api.travelpayouts.com/aviasales/v3/prices_for_dates?$params").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("X-Access-Token", token)
                conn.setRequestProperty("Accept-Encoding", "identity")
                if (conn.responseCode !in 200..299) error("Flight prices unavailable (HTTP ${conn.responseCode})")
                parse(conn.inputStream.bufferedReader().use { it.readText() }, origin, destination)
            } finally {
                conn.disconnect()
            }
        }

    private fun parse(body: String, origin: String, destination: String): List<FareQuote> {
        val json = JSONObject(body)
        if (!json.optBoolean("success", true)) return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val price = o.optDouble("price", Double.NaN)
            if (price.isNaN() || price <= 0) return@mapNotNull null
            val airline = o.optString("airline")
            val number = o.optString("flight_number")
            val departure = o.optString("departure_at").takeIf { it.isNotBlank() }
            val minutes = o.optInt("duration_to", o.optInt("duration", 0))
            val transfers = o.optInt("transfers", 0)
            val link = o.optString("link").takeIf { it.isNotBlank() }
            val date = departure?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }
            FareQuote(
                mode = TransportMode.FLIGHT,
                title = listOf(airlineName(airline), number).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Flight" },
                pricePaise = (price * 100).toLong(),
                live = true,
                provider = "Aviasales",
                hours = if (minutes > 0) minutes / 60.0 else null,
                departure = departure?.let { formatDeparture(it) },
                transfers = transfers,
                url = link?.let { withMarker("https://www.aviasales.com$it") }
                    ?: date?.let { BookingLinks.aviasalesSearch(origin, destination, it, marker) },
                note = if (transfers == 0) "Direct" else "$transfers stop" + if (transfers > 1) "s" else "",
            )
        }
    }

    private fun withMarker(url: String): String =
        if (marker.isNullOrBlank()) url else url + (if ('?' in url) "&" else "?") + "marker=" + URLEncoder.encode(marker, "UTF-8")

    private fun formatDeparture(iso: String): String =
        runCatching { OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")) }.getOrDefault(iso)

    /** Friendly names for common Indian carriers' IATA codes. */
    private fun airlineName(code: String): String = when (code) {
        "6E" -> "IndiGo"
        "AI" -> "Air India"
        "IX" -> "Air India Express"
        "QP" -> "Akasa Air"
        "SG" -> "SpiceJet"
        "9I" -> "Alliance Air"
        "S5" -> "Star Air"
        "UK" -> "Vistara"
        "I5" -> "AIX Connect"
        else -> code
    }
}
