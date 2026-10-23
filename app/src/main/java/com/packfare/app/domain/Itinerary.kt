package com.packfare.app.domain

import java.time.LocalDate
import kotlin.math.max

/** What a traveller is in the mood for. Used to suggest stops. */
enum class Theme(val label: String, val emoji: String) {
    MOUNTAINS("Mountains", "🏔️"),
    BEACHES("Beaches", "🏖️"),
    HERITAGE("Heritage", "🏰"),
    SPIRITUAL("Spiritual", "🛕"),
    NATURE("Nature & wildlife", "🌿"),
    CITY("Food & city life", "🍜"),
}

/** How fast to move: nights per stop. */
enum class Pace(val label: String, val nightsPerStop: Int) {
    RELAXED("Relaxed", 3),
    STEADY("Steady", 2),
    FAST("Fast", 1),
}

object Themes {
    private val M = Theme.MOUNTAINS
    private val B = Theme.BEACHES
    private val H = Theme.HERITAGE
    private val S = Theme.SPIRITUAL
    private val N = Theme.NATURE
    private val C = Theme.CITY

    private val tags: Map<String, Set<Theme>> = mapOf(
        "Manali" to setOf(M, N), "Kasol" to setOf(M, N), "McLeod Ganj" to setOf(M, S), "Bir" to setOf(M, N),
        "Shimla" to setOf(M, H), "Kaza (Spiti)" to setOf(M, S, N), "Rishikesh" to setOf(S, N, M), "Mussoorie" to setOf(M),
        "Leh" to setOf(M, S), "Nubra Valley" to setOf(M, N),
        "Jaipur" to setOf(H, C), "Pushkar" to setOf(S, H), "Udaipur" to setOf(H), "Jodhpur" to setOf(H), "Jaisalmer" to setOf(H, N),
        "Delhi" to setOf(C, H), "Agra" to setOf(H), "Varanasi" to setOf(S, H), "Amritsar" to setOf(S, H, C),
        "Khajuraho" to setOf(H), "Orchha" to setOf(H), "Bhuj" to setOf(H, N),
        "Goa (North)" to setOf(B, C), "Gokarna" to setOf(B, S),
        "Hampi" to setOf(H, N), "Badami" to setOf(H), "Mysuru" to setOf(H, C), "Coorg" to setOf(M, N),
        "Chikmagalur" to setOf(M, N), "Sakleshpur" to setOf(M, N), "Udupi" to setOf(S, B, C),
        "Kochi" to setOf(H, C), "Munnar" to setOf(M, N), "Thekkady" to setOf(N), "Alleppey" to setOf(N),
        "Varkala" to setOf(B), "Kovalam" to setOf(B), "Kollam" to setOf(N, B), "Wayanad" to setOf(M, N),
        "Ooty" to setOf(M, N), "Coonoor" to setOf(M, N), "Kodaikanal" to setOf(M, N), "Valparai" to setOf(M, N),
        "Yercaud" to setOf(M), "Pondicherry" to setOf(B, C, H), "Mahabalipuram" to setOf(H, B),
        "Tiruvannamalai" to setOf(S), "Thanjavur" to setOf(H, S), "Madurai" to setOf(S, H, C),
        "Rameswaram" to setOf(S, B), "Kanyakumari" to setOf(S, B),
        "Hyderabad" to setOf(C, H), "Visakhapatnam" to setOf(B, C), "Araku Valley" to setOf(M, N),
        "Gandikota" to setOf(N, H), "Tirupati" to setOf(S),
        "Puri" to setOf(S, B), "Bhubaneswar" to setOf(H, S), "Konark" to setOf(H, B), "Chilika Lake" to setOf(N),
        "Koraput" to setOf(M, N),
        "Kolkata" to setOf(C, H), "Sundarbans" to setOf(N), "Shantiniketan" to setOf(H), "Darjeeling" to setOf(M),
        "Kalimpong" to setOf(M), "Gangtok" to setOf(M, S), "Pelling" to setOf(M, S), "Lachung" to setOf(M, N),
        "Bodh Gaya" to setOf(S), "Rajgir" to setOf(S, H), "Patna" to setOf(C), "Ranchi" to setOf(N),
        "Guwahati" to setOf(C, S), "Kaziranga" to setOf(N), "Majuli" to setOf(N, S), "Shillong" to setOf(M, C),
        "Cherrapunji" to setOf(N, M), "Dawki" to setOf(N), "Mawlynnong" to setOf(N), "Kohima" to setOf(M, H),
        "Imphal" to setOf(N, H), "Aizawl" to setOf(M), "Agartala" to setOf(H), "Tawang" to setOf(M, S),
        "Dirang" to setOf(M), "Ziro" to setOf(M, N), "Mechuka" to setOf(M, N),
        "Port Blair" to setOf(B, H), "Havelock (Swaraj Dweep)" to setOf(B, N), "Neil Island (Shaheed Dweep)" to setOf(B),
        "Mumbai" to setOf(C), "Bengaluru" to setOf(C), "Chennai" to setOf(C, B),
        // v0.6 additions
        "Ahmedabad" to setOf(H, C), "Vadodara" to setOf(H, C), "Statue of Unity" to setOf(N), "Surat" to setOf(C),
        "Rajkot" to setOf(C), "Dwarka" to setOf(S, B), "Somnath" to setOf(S, B), "Sasan Gir" to setOf(N),
        "Diu" to setOf(B, H), "Rann of Kutch" to setOf(N),
        "Pune" to setOf(C, H), "Lonavala" to setOf(M, N), "Mahabaleshwar" to setOf(M, N), "Nashik" to setOf(S),
        "Aurangabad (Ajanta & Ellora)" to setOf(H), "Kolhapur" to setOf(S, H), "Tarkarli (Malvan)" to setOf(B),
        "Nagpur" to setOf(C), "Alibaug" to setOf(B),
        "Bhopal" to setOf(H, C), "Indore" to setOf(C), "Ujjain" to setOf(S), "Mandu" to setOf(H),
        "Pachmarhi" to setOf(M, N), "Jabalpur" to setOf(N), "Kanha" to setOf(N), "Bandhavgarh" to setOf(N),
        "Raipur" to setOf(C), "Jagdalpur (Bastar)" to setOf(N), "Gwalior" to setOf(H),
        "Srinagar" to setOf(M, N), "Gulmarg" to setOf(M), "Pahalgam" to setOf(M, N), "Sonamarg" to setOf(M, N),
        "Jammu" to setOf(S, C), "Katra (Vaishno Devi)" to setOf(S, M), "Chandigarh" to setOf(C),
        "Lucknow" to setOf(C, H), "Ayodhya" to setOf(S), "Prayagraj" to setOf(S), "Vrindavan" to setOf(S),
        "Haridwar" to setOf(S), "Dehradun" to setOf(C, M), "Nainital" to setOf(M), "Almora" to setOf(M),
        "Jim Corbett" to setOf(N), "Auli" to setOf(M), "Dalhousie" to setOf(M), "Kasauli" to setOf(M),
        "Tirthan Valley" to setOf(M, N),
        "Mandawa" to setOf(H), "Ranthambore" to setOf(N, H), "Bundi" to setOf(H), "Chittorgarh" to setOf(H),
        "Mount Abu" to setOf(M, S), "Bikaner" to setOf(H),
        "Goa (South)" to setOf(B), "Mangaluru" to setOf(B, C), "Murudeshwar" to setOf(B, S), "Dandeli" to setOf(N),
        "Kumarakom" to setOf(N), "Thiruvananthapuram" to setOf(C, S), "Kozhikode" to setOf(B, C),
        "Kannur" to setOf(B), "Bekal" to setOf(B, H),
        "Kanchipuram" to setOf(S, H), "Coimbatore" to setOf(C), "Tiruchirappalli" to setOf(S, H),
        "Chettinad (Karaikudi)" to setOf(H),
        "Warangal" to setOf(H), "Vijayawada" to setOf(C, S),
        "Digha" to setOf(B), "Bishnupur" to setOf(H), "Siliguri" to setOf(C), "Deoghar" to setOf(S),
        "Netarhat" to setOf(M, N), "Tezpur" to setOf(N), "Sivasagar" to setOf(H), "Dibrugarh" to setOf(N),
        "Itanagar" to setOf(M), "Agatti (Lakshadweep)" to setOf(B, N),
    )

    /** Hand-tagged hubs; for the other towns, hills mean mountains and islands mean beaches. */
    fun of(d: Destination): Set<Theme> = tags[d.name] ?: buildSet {
        if (d.hills) add(M)
        if (d.island) { add(B); add(N) }
    }
}

/** One day of the plan. [travel] is set on days you move between places. */
data class ItineraryDay(
    val day: Int,
    val date: LocalDate?,
    val place: String,
    val title: String,
    val travel: LegEstimate? = null,
    val highlights: List<String> = emptyList(),
)

object ItineraryBuilder {

    /**
     * Suggests stops (in travel order) and nights for a trip.
     *
     * Greedy and explainable: starting from [start], repeatedly go to the nearby, theme-matching,
     * budget-friendly hub that isn't too far, until the nights run out. Islands are only used when
     * the user picks the Andaman region.
     */
    fun suggestStops(
        start: Destination,
        nights: Int,
        themes: Set<Theme>,
        regions: Set<String>,
        pace: Pace,
        style: TravelStyle,
    ): List<PlanStop> {
        val total = nights.coerceIn(1, 90)
        val maxStops = max(1, total / pace.nightsPerStop)
        // How far afield to look: longer trips can roam further.
        val radiusKm = (300 + total * 120).coerceAtMost(2_500)

        var pool = Destinations.hubs.filter { d ->
            val regionOk = if (regions.isEmpty()) {
                !d.island && dist(start, d) <= radiusKm
            } else {
                d.region in regions
            }
            regionOk && d.name != start.name
        }
        if (themes.isNotEmpty()) {
            val themed = pool.filter { Themes.of(it).any { t -> t in themes } }
            if (themed.size >= 2 || (themed.isNotEmpty() && maxStops == 1)) pool = themed
        }
        // If the start itself is a good stop (e.g. starting in Manali for a Himachal trip), include it.
        val startQualifies = (regions.isEmpty() || start.region in regions) &&
            (themes.isEmpty() || Themes.of(start).any { it in themes })

        val chosen = mutableListOf<Destination>()
        if (startQualifies) chosen += start
        var current = start
        while (chosen.size < maxStops) {
            val next = pool.filter { it !in chosen }.minByOrNull { score(current, it, themes, style) } ?: break
            chosen += next
            current = next
        }
        if (chosen.isEmpty()) return emptyList()

        // Share the nights out, giving leftovers to the best theme matches.
        val base = total / chosen.size
        val extra = total - base * chosen.size
        val ranked = chosen.sortedByDescending { Themes.of(it).count { t -> t in themes } }
        val bonus = ranked.take(extra).toSet()
        return chosen.map { d ->
            val n = (base + if (d in bonus) 1 else 0).coerceAtLeast(1)
            PlanStop(d, n)
        }
    }

    /** Lower is better: distance (with a big penalty for long hops) minus theme and budget bonuses. */
    private fun score(from: Destination, to: Destination, themes: Set<Theme>, style: TravelStyle): Double {
        val km = dist(from, to)
        val longHopPenalty = if (km > 600) (km - 600) * 1.5 else 0.0
        val themeBonus = Themes.of(to).count { it in themes } * 120.0
        val cheapBonus = if (style == TravelStyle.BACKPACKER && to.tier == CostTier.LOW) 60.0 else 0.0
        return km + longHopPenalty - themeBonus - cheapBonus
    }

    private fun dist(a: Destination, b: Destination) = FareEstimator.haversineKm(a.lat, a.lon, b.lat, b.lon)

    /**
     * Day-by-day plan. Each stop's first day is the arrival (with the journey), the rest are for
     * exploring, filled with Wikivoyage highlights where we have them.
     */
    fun days(
        suggestion: BudgetSuggestion,
        startDate: LocalDate?,
        highlights: Map<String, List<String>> = emptyMap(),
    ): List<ItineraryDay> {
        val out = mutableListOf<ItineraryDay>()
        var day = 1
        suggestion.stops.forEachIndexed { i, s ->
            val name = s.stop.destination.name
            val incoming = suggestion.legs.firstOrNull { it.to.name == name && it.kind != LegKind.RETURN }
                ?.takeIf { i > 0 || it.kind == LegKind.OUTBOUND }
            val things = highlights[name].orEmpty()
            for (n in 0 until s.stop.nights) {
                val picks = things.drop(n * 2).take(2)
                out += ItineraryDay(
                    day = day,
                    date = startDate?.plusDays((day - 1).toLong()),
                    place = name,
                    title = when {
                        n == 0 && incoming != null -> "Travel to $name"
                        n == 0 -> "Arrive in $name"
                        else -> "Explore $name"
                    },
                    travel = if (n == 0) incoming else null,
                    highlights = picks,
                )
                day++
            }
        }
        val back = suggestion.legs.firstOrNull { it.kind == LegKind.RETURN }
        val lastPlace = suggestion.stops.lastOrNull()?.stop?.destination?.name ?: ""
        out += ItineraryDay(
            day = day,
            date = startDate?.plusDays((day - 1).toLong()),
            place = back?.to?.name ?: lastPlace,
            title = if (back != null) "Head home to ${back.to.name}" else "Trip ends in $lastPlace",
            travel = back,
        )
        return out
    }
}
