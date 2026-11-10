package com.packfare.app.domain

/** Where an activity idea came from, so the app can say how much to trust its price. */
enum class IdeaSource(val label: String) {
    /** Hand-checked for a headline destination (still approximate: prices change by season). */
    CURATED("Checked"),
    /** A See/Do listing in the Wikivoyage guide, with the price the guide gives. */
    GUIDE("From the guide"),
    /** A typical activity for this kind of place, with a typical price range. Not a quote. */
    TYPICAL("Typical"),
}

/**
 * Something to do at a stop, with an approximate per-person cost in paise.
 * [lowPaise] is the cheapest realistic price (shared vehicle, Indian ticket); [highPaise] the upper end.
 * [lowPaise] == null means we know the place but not the price.
 */
data class ActivityIdea(
    val name: String,
    val lowPaise: Long? = null,
    val highPaise: Long? = null,
    val note: String = "",
    val source: IdeaSource = IdeaSource.CURATED,
    /** The price was guessed from the kind of place (no price in the guide). */
    val estimated: Boolean = false,
) {
    /** What goes into the budget: the low end, because backpacker trips plan on cheap options. */
    val planPaise: Long get() = lowPaise ?: 0L

    /** "free", "≈₹1,100", "≈₹600–1,200", with "est." when guessed from the kind of place. */
    val costLabel: String
        get() {
            val low = lowPaise ?: return "≈₹50–300 est."
            val high = highPaise
            val label = when {
                low == 0L && (high == null || high == 0L) -> "free"
                low == 0L && high != null -> "free to ≈${Money.format(high)}"
                high != null && high > low -> "≈${Money.format(low)}–${Money.format(high).removePrefix("₹")}"
                else -> "≈${Money.format(low)}"
            }
            return if (estimated) "$label est." else label
        }
}

/**
 * Things to do and what they cost. Three layers, best first:
 *  1. hand-checked ideas for headline destinations,
 *  2. priced See/Do listings from the Wikivoyage guide,
 *  3. typical activities for the kind of place (so every stop gets something, clearly labelled as typical).
 */
object Activities {

    private fun r(rupees: Long) = rupees * 100

    private fun curated(name: String, lowRs: Long?, highRs: Long? = null, note: String = "") =
        ActivityIdea(name, lowRs?.let { r(it) }, highRs?.let { r(it) }, note, IdeaSource.CURATED)

    private fun typical(name: String, lowRs: Long, highRs: Long? = null, note: String = "") =
        ActivityIdea(name, r(lowRs), highRs?.let { r(it) }, note, IdeaSource.TYPICAL)

    /** Headline destinations. Prices per person; jeeps are shared between up to six. */
    private val checked: Map<String, List<ActivityIdea>> = mapOf(
        "Jim Corbett" to listOf(
            curated(
                "Jeep safari in a Corbett zone", 600, 1_200,
                "₹3,500–7,000 a jeep for up to 6. Book the zone permit online well ahead.",
            ),
            curated(
                "Canter safari (shared open truck)", 1_500, 2_000,
                "Per seat, 16–20 on board. Cheaper per person than a private jeep, busier.",
            ),
            curated(
                "Park entry and guide fees", 50, 200,
                "Indians ₹50–200 by zone; foreigners ₹500–1,500. Usually on top of the safari fare.",
            ),
            curated("Garjiya Devi Temple, on a rock in the Kosi river", 0, null, "Free; a short walk up from the road."),
            curated("Corbett Falls and the forest road to it", 0, 100, "Free or a small parking and entry fee."),
            curated("Corbett Museum, Kaladhungi (Jim Corbett's old house)", 50, 100, "Nominal entry."),
            curated("Birdwatching walk with a local guide", 500, 1_000, "Guide fees vary; dawn is best."),
        ),
    )

    /** Typical ideas per theme, used to fill in places with little data. */
    private fun typicalFor(themes: Set<Theme>): List<ActivityIdea> = buildList {
        if (Theme.NATURE in themes) {
            add(typical("Guided nature walk or jungle trail", 300, 1_000))
            add(typical("Safari or wildlife drive (shared vehicle)", 800, 2_500, "Permits often need booking ahead."))
            add(typical("Boat ride on the river or lake", 200, 800))
        }
        if (Theme.MOUNTAINS in themes) {
            add(typical("Day trek with a local guide", 500, 1_500))
            add(typical("Sightseeing by shared taxi", 400, 1_000, "Viewpoints and nearby villages."))
            add(typical("Adventure sport: paragliding, rafting or zipline", 1_000, 4_000))
            add(typical("Monastery, temple or viewpoint visits", 0, 200))
        }
        if (Theme.HERITAGE in themes) {
            add(typical("Fort or monument entry", 50, 600, "Foreign visitors usually pay several times more."))
            add(typical("Guided heritage walk", 300, 1_000))
            add(typical("Museum or palace visit", 50, 500))
        }
        if (Theme.SPIRITUAL in themes) {
            add(typical("Temple, ghat or gurdwara visits", 0, 100, "Often free; shoes and head covering rules apply."))
            add(typical("Evening aarti or prayers", 0, 0))
            add(typical("Boat or walk along the sacred river or lake", 200, 600))
        }
        if (Theme.BEACHES in themes) {
            add(typical("Beach day and a shack lunch", 0, 600))
            add(typical("Water sports: jet ski, parasailing, banana boat", 500, 2_500))
            add(typical("Boat trip or snorkelling", 500, 1_500))
        }
        if (Theme.CITY in themes) {
            add(typical("Food or street-food walk", 500, 1_500))
            add(typical("Museum or gallery", 50, 500))
            add(typical("Market and neighbourhood stroll", 0, 0))
        }
        if (isEmpty()) {
            add(typical("Local sightseeing on foot or by auto", 0, 400))
            add(typical("Market and street-food stroll", 0, 300))
        }
    }

    /**
     * Ideas for one stop, up to [max], in this order: checked, priced guide listings, other guide
     * listings, then typical ones to fill the rest. Names are de-duplicated.
     */
    fun ideasFor(destination: Destination, guide: List<ActivityIdea>, max: Int = 10): List<ActivityIdea> {
        val seen = mutableSetOf<String>()
        fun List<ActivityIdea>.fresh() = filter { seen.add(it.name.lowercase().trim()) }

        val out = mutableListOf<ActivityIdea>()
        out += (checked[destination.name].orEmpty()).fresh()
        out += guide.filter { it.lowPaise != null }.fresh()
        // Listings without a price get an estimate from the kind of place, clearly marked "est.".
        out += guide.filter { it.lowPaise == null }.map { ActivityEstimator.withEstimate(it) }.fresh()
        // Typical ideas only top up a thin list; places with good data aren't diluted.
        if (out.size < 6) out += typicalFor(Themes.of(destination)).fresh()
        return out.take(max)
    }

    /** Plain-text version for sharing: "• Jeep safari  ≈₹600–1,200". */
    fun line(a: ActivityIdea) = "• ${a.name}  ${a.costLabel}"
}

/** Which suggested activities are ticked, and what that adds to the trip total. */
object ActivityPicks {
    fun key(day: Int, idea: ActivityIdea) = "$day|${idea.name}"

    /** Low-end cost of the ideas still ticked (not in [skipped]), per person. */
    fun pickedPaise(days: List<ItineraryDay>, skipped: Set<String>): Long =
        days.sumOf { d -> d.activities.filter { key(d.day, it) !in skipped }.sumOf { it.planPaise } }

    /**
     * The trip already carries a daily activities allowance. Ticked ideas only add to the total
     * when together they cost more than that allowance; unticking never drops below it.
     */
    fun extraPaise(days: List<ItineraryDay>, skipped: Set<String>, allowancePaise: Long): Long =
        (pickedPaise(days, skipped) - allowancePaise).coerceAtLeast(0L)
}

/** A rough price for things to do that the guide lists without one, guessed from the name. Rupees, per person. */
object ActivityEstimator {
    private data class Rule(val words: List<String>, val lowRs: Long, val highRs: Long)

    // First match wins, so specific things come before general ones.
    private val rules = listOf(
        Rule(listOf("camel", "desert safari", "dune"), 500, 2_000),
        Rule(listOf("safari", "national park", "sanctuary", "wildlife", "tiger reserve"), 300, 2_500),
        Rule(listOf("rafting", "paragliding", "bungee", "zipline", "scuba", "snorkel", "parasail", "water sport"), 1_000, 4_000),
        Rule(listOf("trek", "hike", "hiking"), 300, 1_500),
        Rule(listOf("boat", "cruise", "ferry ride", "shikara"), 200, 800),
        Rule(listOf("light and sound", "sound and light", "show"), 100, 500),
        Rule(listOf("museum", "gallery", "haveli"), 20, 300),
        Rule(listOf("fort", "palace", "monument", "tomb", "mahal", "citadel", "qila"), 50, 600),
        Rule(listOf("cave", "caves"), 50, 300),
        Rule(listOf("zoo", "aquarium"), 50, 300),
        Rule(listOf("temple", "mandir", "gurdwara", "mosque", "masjid", "church", "monastery", "gompa", "ashram", "shrine", "dargah"), 0, 100),
        Rule(listOf("waterfall", "falls"), 0, 100),
        Rule(listOf("lake", "ghat", "beach", "viewpoint", "sunset", "sunrise", "point", "market", "bazaar", "square", "street", "walk"), 0, 100),
        Rule(listOf("garden", "park"), 20, 100),
    )

    /** (low, high) in paise for [name]. Falls back to a modest sightseeing range. */
    fun estimate(name: String): Pair<Long, Long> {
        val n = name.lowercase()
        val rule = rules.firstOrNull { r -> r.words.any { it in n } }
        val (lo, hi) = rule?.let { it.lowRs to it.highRs } ?: (50L to 300L)
        return lo * 100 to hi * 100
    }

    fun withEstimate(idea: ActivityIdea): ActivityIdea {
        if (idea.lowPaise != null) return idea
        val (lo, hi) = estimate(idea.name)
        return idea.copy(lowPaise = lo, highPaise = hi, estimated = true)
    }
}
