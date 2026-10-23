package com.packfare.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Everything the "Plan a trip" search box holds, so it can be shown under "Recent searches" or
 * resumed later as a draft. Plain text encoding (no JSON library) so it's easy to unit-test.
 */
data class TripSearch(
    val from: String?,
    /** Destination name -> nights. Empty = "suggest a route for me". */
    val stops: List<Pair<String, Int>>,
    val startDate: LocalDate,
    val nights: Int,
    val people: Int,
    val style: TravelStyle,
    val pref: TravelPref,
    val returnHome: Boolean,
    val themes: Set<Theme> = emptySet(),
    val pace: Pace = Pace.STEADY,
    val name: String = "",
) {
    /** "Bengaluru → Kullu, Manali" or "Bengaluru → Anywhere (mountains)". */
    val title: String
        get() {
            val to = if (stops.isEmpty()) {
                "Suggested route" + if (themes.isNotEmpty()) " (${themes.joinToString { it.label.lowercase() }})" else ""
            } else {
                stops.joinToString(", ") { it.first }
            }
            return (from?.let { "$it → " } ?: "") + to
        }

    /** "20 Nov – 27 Nov · 7 nights · 2 travellers · Budget hotel" */
    val subtitle: String
        get() {
            val end = startDate.plusDays(nights.toLong())
            return "${startDate.format(SHORT)} – ${end.format(SHORT)} · $nights night${if (nights == 1) "" else "s"} · " +
                "$people traveller${if (people == 1) "" else "s"} · ${style.label}"
        }

    /** Same where-to and where-from: a repeated search replaces the older one. */
    fun sameTrip(other: TripSearch) = from == other.from && stops.map { it.first } == other.stops.map { it.first } &&
        (stops.isNotEmpty() || themes == other.themes)

    companion object {
        private val SHORT = DateTimeFormatter.ofPattern("d MMM")
        private const val F = '\u001F' // field separator
        private const val S = '\u001E' // stop separator
        private const val N = '\u001D' // name/nights separator

        fun encode(s: TripSearch): String = listOf(
            s.from.orEmpty(),
            s.stops.joinToString(S.toString()) { "${it.first}$N${it.second}" },
            s.startDate.toEpochDay().toString(),
            s.nights.toString(),
            s.people.toString(),
            s.style.name,
            s.pref.name,
            s.returnHome.toString(),
            s.themes.joinToString(",") { it.name },
            s.pace.name,
            s.name,
        ).joinToString(F.toString())

        /** Null for anything unreadable (older format, corrupted prefs). */
        fun decode(text: String): TripSearch? = runCatching {
            val p = text.split(F)
            TripSearch(
                from = p[0].ifBlank { null },
                stops = p[1].split(S).filter { it.isNotBlank() }.map { val (n, k) = it.split(N); n to k.toInt() },
                startDate = LocalDate.ofEpochDay(p[2].toLong()),
                nights = p[3].toInt(),
                people = p[4].toInt(),
                style = TravelStyle.fromName(p[5]),
                pref = TravelPref.fromName(p[6]),
                returnHome = p[7].toBoolean(),
                themes = p[8].split(',').filter { it.isNotBlank() }.mapNotNull { n -> Theme.entries.firstOrNull { it.name == n } }.toSet(),
                pace = Pace.entries.firstOrNull { it.name == p[9] } ?: Pace.STEADY,
                name = p.getOrElse(10) { "" },
            )
        }.getOrNull()

        /** Newest first, no repeats, at most [max]. */
        fun remember(recent: List<TripSearch>, search: TripSearch, max: Int = 6): List<TripSearch> =
            (listOf(search) + recent.filterNot { it.sameTrip(search) }).take(max)

        fun encodeList(list: List<TripSearch>): String = list.joinToString("\n") { encode(it) }
        fun decodeList(text: String?): List<TripSearch> = text.orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { decode(it) }

        /** Splits [total] nights across [count] stops, extra nights to the first stops: 7 over 3 -> 3, 2, 2. */
        fun splitNights(total: Int, count: Int): List<Int> {
            if (count <= 0) return emptyList()
            val base = (total / count).coerceAtLeast(1)
            val extra = (total - base * count).coerceAtLeast(0)
            return List(count) { i -> base + if (i < extra) 1 else 0 }
        }
    }
}
