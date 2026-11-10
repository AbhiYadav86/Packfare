package com.packfare.app.fares

import com.packfare.app.domain.Destination
import com.packfare.app.domain.FareEstimator
import com.packfare.app.domain.TransportMode
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One way to make a journey, either a live price from the internet or an offline estimate. */
data class FareQuote(
    val mode: TransportMode,
    /** "IndiGo 6E 2134", "Sleeper train", "AC sleeper bus"… */
    val title: String,
    val pricePaise: Long,
    /** true = real price fetched online; false = Packfare's estimate. */
    val live: Boolean,
    val provider: String,
    val hours: Double? = null,
    val departure: String? = null,
    val transfers: Int? = null,
    /** Where to book or check the real price. */
    val url: String? = null,
    val note: String? = null,
    /** Part of [pricePaise] that is getting to/from the airports (flights only). Air fare = price − this. */
    val transferPaise: Long = 0,
) {
    val airFarePaise: Long get() = pricePaise - transferPaise
}

/** One airport pair (e.g. Bengaluru → Kullu–Bhuntar): its estimate, live fares if any, and search links. */
data class FlightPlanFares(
    val plan: com.packfare.app.domain.FlightPlan,
    /** Door-to-door estimate: air fare + airport transfers. */
    val estimate: com.packfare.app.domain.RouteOption,
    /** Live fares for this airport pair, cheapest first, each including the airport transfers. */
    val live: List<FareQuote>,
    /** (site name, url) to check real prices: Google Flights, Skyscanner, ixigo, MakeMyTrip. */
    val links: List<Pair<String, String>>,
)

/** Flight choices from one place to another, one entry per sensible arrival airport, best first. */
data class FlightOptions(
    val plans: List<FlightPlanFares>,
    val notice: String?,
) {
    val estimate: com.packfare.app.domain.RouteOption? get() = plans.firstOrNull()?.estimate
    val live: List<FareQuote> get() = plans.flatMap { it.live }.sortedBy { it.pricePaise }
}

data class FareResults(
    val quotes: List<FareQuote>,
    val cheapest: FareQuote?,
    val bestValue: FareQuote?,
    val fastest: FareQuote?,
    /** Problems worth telling the user about (e.g. live flights off, offline). */
    val notices: List<String>,
)

object FarePicker {

    /** What an hour of travel is "worth" when judging value for money (₹60/hour, as in the planner). */
    private const val TIME_VALUE_PAISE_PER_HOUR = 6_000L

    fun pick(quotes: List<FareQuote>, notices: List<String> = emptyList()): FareResults {
        // Nothing below 3AC (sleeper class, ordinary state buses), even if a provider returns it.
        val priced = quotes.filter { it.pricePaise > 0 && it.mode.offered }
        return FareResults(
            quotes = priced.sortedWith(compareBy<FareQuote> { it.pricePaise }.thenByDescending { it.live }),
            // Prefer a live price over an estimate when they're within 5% of each other.
            cheapest = priced.minWithOrNull(
                compareBy<FareQuote> { if (it.live) it.pricePaise * 95 / 100 else it.pricePaise },
            ),
            bestValue = priced.minByOrNull { it.pricePaise + ((it.hours ?: 12.0) * TIME_VALUE_PAISE_PER_HOUR).toLong() },
            fastest = priced.filter { it.hours != null }.minByOrNull { it.hours!! },
            notices = notices,
        )
    }

    /** Offline estimates for every sensible mode, each with a link to check the real price. */
    fun estimates(from: Destination, to: Destination, date: LocalDate): List<FareQuote> =
        FareEstimator.options(from, to).map { o ->
            FareQuote(
                mode = o.mode,
                title = o.mode.label,
                pricePaise = o.farePaise,
                live = false,
                provider = "Packfare estimate",
                hours = o.hours,
                url = BookingLinks.forMode(o.mode, from, to, date),
                note = listOfNotNull(o.note, if (o.overnight) "Overnight: saves a night's stay" else null)
                    .joinToString(" · ").ifBlank { null },
                transferPaise = o.transferPaise,
            )
        }
}

/** Deep links to official or popular booking/search sites, pre-filled where the site supports it. */
object BookingLinks {
    private val human = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun forMode(mode: TransportMode, from: Destination, to: Destination, date: LocalDate): String = when (mode) {
        TransportMode.TRAIN_SLEEPER, TransportMode.TRAIN_AC, TransportMode.TRAIN_2AC, TransportMode.TRAIN_1AC -> IRCTC
        TransportMode.BUS_STATE, TransportMode.BUS_AC_SLEEPER -> redBus(from.name, to.name)
        TransportMode.FLIGHT -> googleFlights(from, to, date)
        else -> google("${mode.label} from ${from.name} to ${to.name} ${date.format(human)}")
    }

    const val IRCTC = "https://www.irctc.co.in/nget/train-search"

    fun redBus(from: String, to: String): String = "https://www.redbus.in/bus-tickets/${slug(from)}-to-${slug(to)}"

    /**
     * Google Flights for the best airport pair between two places. Always uses airport codes, never
     * town names: a town without an airport (Kullu, Ooty…) made Google show an empty page.
     */
    fun googleFlights(from: Destination, to: Destination, date: LocalDate): String {
        val plan = com.packfare.app.domain.FlightPlanner.plans(from, to).firstOrNull()
            ?: return "https://www.google.com/travel/flights?hl=en-IN&curr=INR"
        return googleFlights(plan.from.code, plan.to.code, date)
    }

    fun googleFlights(fromCode: String, toCode: String, date: LocalDate): String =
        "https://www.google.com/travel/flights?q=" + enc("Flights to $toCode from $fromCode on $date oneway") + "&curr=INR&hl=en-IN"

    /** skyscanner.co.in/transport/flights/blr/kuu/261020/ (yyMMdd). */
    fun skyscanner(fromCode: String, toCode: String, date: LocalDate): String =
        "https://www.skyscanner.co.in/transport/flights/${fromCode.lowercase()}/${toCode.lowercase()}/" +
            date.format(DateTimeFormatter.ofPattern("yyMMdd")) + "/?adultsv2=1&cabinclass=economy"

    fun ixigo(fromCode: String, toCode: String, date: LocalDate): String =
        "https://www.ixigo.com/search/result/flight?from=$fromCode&to=$toCode&date=" +
            date.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + "&adults=1&children=0&infants=0&class=e&source=Search%20Form"

    fun makeMyTrip(fromCode: String, toCode: String, date: LocalDate): String =
        "https://www.makemytrip.com/flight/search?itinerary=$fromCode-$toCode-" +
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "&tripType=O&paxType=A-1_C-0_I-0&intl=false&cabinClass=E"

    /** Several sites, so there's always one that works for a small or seasonal airport. */
    fun flightLinks(fromCode: String, toCode: String, date: LocalDate): List<Pair<String, String>> = listOf(
        "Google Flights" to googleFlights(fromCode, toCode, date),
        "Skyscanner" to skyscanner(fromCode, toCode, date),
        "ixigo" to ixigo(fromCode, toCode, date),
        "MakeMyTrip" to makeMyTrip(fromCode, toCode, date),
    )

    /** Aviasales search page, e.g. /search/DEL1010KUU1, with the affiliate marker if you have one. */
    fun aviasalesSearch(origin: String, destination: String, date: LocalDate, marker: String?): String {
        val ddmm = "%02d%02d".format(date.dayOfMonth, date.monthValue)
        val base = "https://www.aviasales.com/search/$origin$ddmm${destination}1"
        return if (marker.isNullOrBlank()) base else "$base?marker=${enc(marker)}"
    }

    fun google(query: String) = "https://www.google.com/search?q=" + enc(query)

    // ---- 12Go (trains & buses for foreign visitors: PDF ticket, no IRCTC account or Indian phone) ----

    /** 12Go's names for big cities differ from ours; everything else uses our slug. */
    private val TWELVE_GO_NAMES = mapOf(
        "Delhi" to "new-delhi", "Bengaluru" to "bangalore", "Mysuru" to "mysore", "Kochi" to "cochin",
        "Pondicherry" to "pondicherry", "Goa (North)" to "goa", "Goa (South)" to "goa", "Varanasi" to "varanasi",
        "McLeod Ganj" to "dharamshala", "Alleppey" to "alleppey", "Mangaluru" to "mangalore",
        "Thiruvananthapuram" to "trivandrum", "Kolkata" to "kolkata", "Mumbai" to "mumbai", "Chennai" to "chennai",
    )

    /** 12go.asia/en/travel/new-delhi/manali?date=2026-11-20 (opens their search for the route). */
    fun twelveGo(from: Destination, to: Destination, date: LocalDate): String {
        fun name(d: Destination) = TWELVE_GO_NAMES[d.name] ?: slug(d.name)
        return "https://12go.asia/en/travel/${name(from)}/${name(to)}?date=$date&people=1"
    }

    /**
     * Where to check and book a train or bus leg: Indians get IRCTC / redBus, foreign visitors get 12Go
     * first (it sells Indian train and bus tickets with a PDF voucher, no Indian phone number needed).
     */
    fun groundLinks(mode: TransportMode, from: Destination, to: Destination, date: LocalDate, foreignVisitor: Boolean): List<Pair<String, String>> {
        val local = when (mode) {
            TransportMode.TRAIN_SLEEPER, TransportMode.TRAIN_AC, TransportMode.TRAIN_2AC, TransportMode.TRAIN_1AC -> "IRCTC" to IRCTC
            TransportMode.BUS_STATE, TransportMode.BUS_AC_SLEEPER -> "redBus" to redBus(from.name, to.name)
            TransportMode.FERRY -> "Ferry times" to google("ferry ${from.name} to ${to.name}")
            else -> "Search" to google("${mode.label} from ${from.name} to ${to.name} ${date.format(human)}")
        }
        val twelve = "12Go" to twelveGo(from, to, date)
        val offersTwelveGo = mode in TransportMode.TRAIN_CLASSES || mode in setOf(TransportMode.BUS_AC_SLEEPER, TransportMode.TRAIN_SLEEPER, TransportMode.BUS_STATE)
        return when {
            !offersTwelveGo -> listOf(local)
            foreignVisitor -> listOf(twelve, local)
            else -> listOf(local, twelve)
        }
    }

    // ---- Stays ----

    /** Booking.com search for the town and dates; group_adults and room count from the plan. */
    fun bookingCom(place: Destination, checkIn: LocalDate, checkOut: LocalDate, adults: Int, rooms: Int): String =
        "https://www.booking.com/searchresults.html?ss=" + enc("${place.name.substringBefore(" (")}, India") +
            "&checkin=$checkIn&checkout=$checkOut&group_adults=${adults.coerceAtLeast(1)}&no_rooms=${rooms.coerceAtLeast(1)}&group_children=0"

    /** Booking.com search with a per-night price ceiling in rupees (their "your budget" filter). */
    fun bookingComUnder(place: Destination, checkIn: LocalDate, checkOut: LocalDate, adults: Int, rooms: Int, maxPerNightPaise: Long): String =
        bookingCom(place, checkIn, checkOut, adults, rooms) +
            "&selected_currency=INR&nflt=" + enc("price=INR-min-${(maxPerNightPaise / 100).coerceAtLeast(100)}-1")

    /** Agoda search by place name and dates, prices in rupees. */
    fun agoda(place: Destination, checkIn: LocalDate, checkOut: LocalDate, adults: Int, rooms: Int): String =
        "https://www.agoda.com/search?textToSearch=" + enc("${place.name.substringBefore(" (")}, India") +
            "&checkIn=$checkIn&checkOut=$checkOut&adults=${adults.coerceAtLeast(1)}&rooms=${rooms.coerceAtLeast(1)}&currencyCode=INR"

    fun googleHotels(place: Destination, hostels: Boolean): String =
        "https://www.google.com/travel/search?q=" + enc((if (hostels) "hostels in " else "hotels in ") + place.name.substringBefore(" (") + ", India")

    /** Aviasales search for an airport pair (adds your Travelpayouts marker when it's set). */
    fun aviasalesLink(fromCode: String, toCode: String, date: LocalDate, marker: String?): Pair<String, String> =
        "Aviasales" to aviasalesSearch(fromCode, toCode, date, marker)

    /** "Goa (North)" -> "goa-north", "McLeod Ganj" -> "mcleod-ganj". */
    internal fun slug(name: String): String =
        name.lowercase().replace(Regex("\\(.*?\\)"), " ").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
