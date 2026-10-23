package com.packfare.app.explore

import com.packfare.app.domain.DailyCosts
import com.packfare.app.domain.Destination

/** Wikivoyage splits "Sleep" into Budget / Mid-range / Splurge sub-sections. */
enum class Bracket { BUDGET, MID, SPLURGE, UNKNOWN }

/** One priced listing pulled from a Wikivoyage article. */
data class PriceSample(
    val name: String,
    val pricePaise: Long,
    val isDorm: Boolean,
    val bracket: Bracket = Bracket.UNKNOWN,
)

/** What we learned about a place's prices from its article. */
data class PriceStats(
    val sleep: List<PriceSample>,
    val eat: List<PriceSample>,
) {
    val dormMedianPaise: Long? get() = median(sleep.filter { it.isDorm }.map { it.pricePaise }, min = 3)
    /** Typical cheap room/bed: Budget (or unlabelled) listings only. */
    val sleepMedianPaise: Long?
        get() = median(sleep.filter { it.bracket == Bracket.BUDGET || it.bracket == Bracket.UNKNOWN }.map { it.pricePaise }, min = 3)

    /** Typical 3★-ish room (Mid-range listings), per room per night. */
    val midRoomMedianPaise: Long? get() = median(sleep.filter { it.bracket == Bracket.MID }.map { it.pricePaise }, min = 2)

    /** Typical upscale room (Splurge listings), per room per night. */
    val splurgeRoomMedianPaise: Long? get() = median(sleep.filter { it.bracket == Bracket.SPLURGE }.map { it.pricePaise }, min = 2)
    val mealMedianPaise: Long? get() = median(eat.map { it.pricePaise }, min = 3)
    val sampleCount: Int get() = sleep.size + eat.size

    private fun median(values: List<Long>, min: Int): Long? {
        if (values.size < min) return null
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}

/**
 * Turns Wikivoyage wikitext into price samples. Pure Kotlin (no Android), so it's unit tested.
 *
 * Wikivoyage listings look like:
 *   * {{sleep | name=Zostel | ... | price=Dorm ₹550, private ₹1,800 | content=... }}
 * We read the `price=` field of sleep/eat listings and keep only rupee prices.
 */
object WikivoyageParser {

    private val listingStart = Regex("""\{\{\s*(sleep|eat|drink|see|do|listing)\b""", RegexOption.IGNORE_CASE)
    private val rupeeAmount = Regex(
        """(?:₹|\brs\.?|\binr)\s*(\d{1,3}(?:,\d{2,3})+|\d+)(?:\s*(?:-|–|to)\s*(?:₹|\brs\.?|\binr)?\s*(\d{1,3}(?:,\d{2,3})+|\d+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val rupeeSuffix = Regex("""(\d{1,3}(?:,\d{2,3})+|\d+)\s*(?:rupees|rs\b|inr\b)""", RegexOption.IGNORE_CASE)
    private val dormWords = Regex("""\b(dorm|dormitory|bed in|per bed|bunk)\b""", RegexOption.IGNORE_CASE)
    private val geo = Regex("""\{\{\s*geo\s*\|\s*(-?\d+(?:\.\d+)?)\s*\|\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    fun parse(wikitext: String): PriceStats {
        val sleep = mutableListOf<PriceSample>()
        val eat = mutableListOf<PriceSample>()
        val headings = subHeadings(wikitext)
        for (template in listingTemplates(wikitext)) {
            val fields = fields(template.body)
            val type = (if (template.kind == "listing") fields["type"] else template.kind)?.lowercase() ?: continue
            val priceText = fields["price"].orEmpty()
            val amount = cheapestRupees(priceText) ?: continue
            val name = fields["name"].orEmpty().ifBlank { "?" }
            val dorm = dormWords.containsMatchIn(priceText) || dormWords.containsMatchIn(fields["content"].orEmpty())
            when (type) {
                "sleep" -> if (amount in 100_00L..60_000_00L) {
                    sleep += PriceSample(name, amount, dorm, bracketAt(headings, template.start))
                }
                "eat", "drink" -> if (amount in 20_00L..5_000_00L) eat += PriceSample(name, amount, false)
            }
        }
        return PriceStats(sleep, eat)
    }

    /**
     * Names of the article's "See" and "Do" listings, in order: the things travellers go there for.
     * Used to fill the day-by-day itinerary.
     */
    fun highlights(wikitext: String, max: Int = 12): List<String> =
        listingTemplates(wikitext).mapNotNull { t ->
            val f = fields(t.body)
            val type = (if (t.kind == "listing") f["type"] else t.kind)?.lowercase()
            if (type != "see" && type != "do") return@mapNotNull null
            cleanName(f["name"].orEmpty()).takeIf { it.length in 2..60 }
        }.distinct().take(max)

    /** "[[Hadimba Temple|Hadimba Devi Temple]]" -> "Hadimba Devi Temple"; drops wiki bold/italics and HTML. */
    private fun cleanName(raw: String): String = raw
        .replace(Regex("""\[\[(?:[^|\]]*\|)?([^\]]*)]]"""), "$1")
        .replace("'''", "").replace("''", "")
        .replace(Regex("<[^>]+>"), "")
        .trim()

    /** {{geo|lat|long}} near the top of most articles. */
    fun coordinates(wikitext: String): Pair<Double, Double>? =
        geo.find(wikitext)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }

    /**
     * The lowest rupee amount in a price string ("₹300-400" -> 300, "dorm ₹550, room ₹1,800" -> 550).
     * Backpackers care about the cheapest option. Returns paise.
     */
    internal fun cheapestRupees(text: String): Long? {
        val amounts = rupeeAmount.findAll(text).map { it.groupValues[1] }.toMutableList()
        rupeeSuffix.findAll(text).forEach { amounts += it.groupValues[1] }
        return amounts.mapNotNull { it.replace(",", "").toLongOrNull() }
            .filter { it > 0 }
            .minOrNull()
            ?.let { it * 100 }
    }

    /**
     * Daily backpacker costs for a place: live medians where there's enough data,
     * otherwise the bundled table. Food = three meals at the median listing price,
     * capped so one fancy restaurant can't blow up the estimate.
     */
    fun refine(base: DailyCosts, stats: PriceStats): DailyCosts {
        val stay = stats.dormMedianPaise
            ?: stats.sleepMedianPaise?.let { (it / 2).coerceIn(base.stayPaise / 2, base.stayPaise * 2) }
            ?: base.stayPaise
        val food = stats.mealMedianPaise?.let { (it * 3).coerceIn(base.foodPaise / 2, base.foodPaise * 2) }
            ?: base.foodPaise
        // Hotel classes: keep live medians within a sane band around the tier tables.
        val mid = stats.midRoomMedianPaise?.let {
            it.coerceIn(DailyCosts.midRoom(base.tier) / 2, DailyCosts.midRoom(base.tier) * 2)
        }
        val upscale = stats.splurgeRoomMedianPaise?.let {
            it.coerceIn(DailyCosts.upscaleRoom(base.tier) / 2, DailyCosts.upscaleRoom(base.tier) * 16 / 10)
        }
        return base.copy(stayPaise = stay, foodPaise = food, midRoomPaise = mid, upscaleRoomPaise = upscale)
    }

    fun refine(destination: Destination, stats: PriceStats): DailyCosts = refine(destination.baseline, stats)

    // ---- template scanning ----

    private data class Template(val kind: String, val body: String, val start: Int = 0)

    private val subHeading = Regex("""(?m)^(={2,4})\s*([^=\n]+?)\s*={2,4}\s*$""")

    /** (position, bracket) for every ===Budget=== / ===Mid-range=== / ===Splurge=== heading. */
    private fun subHeadings(text: String): List<Pair<Int, Bracket>> =
        subHeading.findAll(text).map { m ->
            val h = m.groupValues[2].lowercase()
            val b = when {
                m.groupValues[1].length == 2 -> Bracket.UNKNOWN // a new main section (==Sleep==) resets

                "budget" in h || "cheap" in h -> Bracket.BUDGET
                "mid" in h -> Bracket.MID
                "splurge" in h || "luxury" in h || "upscale" in h || "expensive" in h -> Bracket.SPLURGE
                else -> Bracket.UNKNOWN
            }
            m.range.first to b
        }.toList()

    private fun bracketAt(headings: List<Pair<Int, Bracket>>, pos: Int): Bracket =
        headings.lastOrNull { it.first < pos }?.second ?: Bracket.UNKNOWN

    /** Finds listing templates, respecting nested {{...}} inside them. */
    private fun listingTemplates(text: String): List<Template> {
        val out = mutableListOf<Template>()
        var match = listingStart.find(text)
        while (match != null) {
            val start = match.range.first
            var depth = 0
            var i = start
            var end = -1
            while (i < text.length - 1) {
                if (text[i] == '{' && text[i + 1] == '{') { depth++; i += 2; continue }
                if (text[i] == '}' && text[i + 1] == '}') {
                    depth--; i += 2
                    if (depth == 0) { end = i; break }
                    continue
                }
                i++
            }
            if (end == -1) break
            out += Template(match.groupValues[1].lowercase(), text.substring(match.range.last + 1, end - 2), start)
            match = listingStart.find(text, end)
        }
        return out
    }

    /** Splits "| key=value | key2=value2" at top level (ignoring | inside nested templates/links). */
    private fun fields(body: String): Map<String, String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            val next = if (i + 1 < body.length) body[i + 1] else ' '
            when {
                (c == '{' && next == '{') || (c == '[' && next == '[') -> { depth++; sb.append(c).append(next); i += 2; continue }
                (c == '}' && next == '}') || (c == ']' && next == ']') -> { depth--; sb.append(c).append(next); i += 2; continue }
                c == '|' && depth == 0 -> { parts += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        parts += sb.toString()
        return parts.mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).trim().lowercase() to p.substring(eq + 1).trim()
        }.toMap()
    }
}
