package com.packfare.app.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [offered] = false: classes priced below 3AC (sleeper class, ordinary state buses). Packfare no longer
 * suggests or budgets them; they stay in the enum so older trips and logged expenses still load.
 */
enum class TransportMode(val label: String, val emoji: String, val offered: Boolean = true) {
    TRAIN_SLEEPER("Sleeper train", "🚆", offered = false),
    TRAIN_AC("3AC train", "🚆"),
    TRAIN_2AC("2AC train", "🚆"),
    TRAIN_1AC("1AC train", "🚆"),
    BUS_STATE("State bus", "🚌", offered = false),
    BUS_AC_SLEEPER("AC bus", "🚌"),
    SHARED_JEEP("Shared jeep/taxi", "🚙"),
    TAXI("Private taxi", "🚕"),
    FLIGHT("Flight", "✈️"),
    FERRY("Ferry", "⛴️"),
    OTHER("Other", "🧭");

    companion object {
        /** Train classes Packfare offers, cheapest first (nothing below 3AC). */
        val TRAIN_CLASSES: List<TransportMode> by lazy { listOf(TRAIN_AC, TRAIN_2AC, TRAIN_1AC) }

        fun fromName(name: String?): TransportMode = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** How the traveller would rather get around. [BEST] lets Packfare weigh money, time and comfort. */
enum class TravelPref(val label: String, val emoji: String, val modes: Set<TransportMode>) {
    BEST("Best value", "⭐", emptySet()),
    AIR("Air", "✈️", setOf(TransportMode.FLIGHT)),
    TRAIN("Train", "🚆", TransportMode.TRAIN_CLASSES.toSet()),
    BUS("Bus", "🚌", setOf(TransportMode.BUS_AC_SLEEPER, TransportMode.SHARED_JEEP));

    /**
     * The preferred mode's option, or null when the route doesn't have it (fall back to best).
     * Trains use the class that suits the travel style (3AC, 2AC or 1AC); otherwise the cheapest.
     */
    fun pick(options: List<RouteOption>, style: TravelStyle = TravelStyle.BACKPACKER): RouteOption? {
        if (this == BEST) return null
        val inMode = options.filter { it.mode in modes }
        if (this == TRAIN) {
            inMode.firstOrNull { it.mode == style.trainClass }?.let { return it }
            // That class isn't on this route: the nearest class below it, else whatever runs.
            return inMode.filter { TransportMode.TRAIN_CLASSES.indexOf(it.mode) <= TransportMode.TRAIN_CLASSES.indexOf(style.trainClass) }
                .maxByOrNull { TransportMode.TRAIN_CLASSES.indexOf(it.mode) } ?: inMode.minByOrNull { it.farePaise }
        }
        return inMode.minByOrNull { it.farePaise }
    }

    companion object {
        fun fromName(name: String?): TravelPref = entries.firstOrNull { it.name == name } ?: BEST
    }
}

/** One way to get from A to B, with estimated per-person fare and duration. */
data class RouteOption(
    val mode: TransportMode,
    val distanceKm: Int,
    val farePaise: Long,
    val hours: Double,
    /** Long enough to travel overnight and skip a night's stay. */
    val overnight: Boolean,
    /** E.g. "Fly DEL→KUU, then 52 km by cab"; null for door-to-door ground modes. */
    val note: String? = null,
    /** Part of [farePaise] spent getting to/from airports (flights only). */
    val transferPaise: Long = 0,
    /** Flights only: the airports this estimate uses (IATA), for booking links and live prices. */
    val fromAirport: String? = null,
    val toAirport: String? = null,
)

/** The three picks shown to the user. "Best" balances money, time and overnight savings. */
data class RouteChoices(
    val cheapest: RouteOption,
    val fastest: RouteOption,
    val best: RouteOption,
    val all: List<RouteOption>,
)

/**
 * Offline fare and time estimates from straight-line distance and typical Indian per-km fares.
 * These are planning numbers, not live prices: always labelled "≈" in the UI.
 */
object FareEstimator {

    private data class Rate(val paisePerKm: Long, val minPaise: Long, val kmh: Double, val hillKmh: Double)

    private val rates = mapOf(
        TransportMode.TRAIN_AC to Rate(165, 30_000, 65.0, 32.0),
        // 2AC ≈ 1.45× and 1AC ≈ 2.4× the 3AC fare (Delhi–Varanasi ≈ ₹1,350 / ₹1,950 / ₹3,300).
        TransportMode.TRAIN_2AC to Rate(240, 45_000, 65.0, 32.0),
        TransportMode.TRAIN_1AC to Rate(400, 1_00_000, 65.0, 32.0),
        TransportMode.BUS_AC_SLEEPER to Rate(250, 25_000, 50.0, 32.0),
        TransportMode.SHARED_JEEP to Rate(300, 20_000, 40.0, 32.0),
    )

    /** Hours of sitting still after which an evening departure means sleeping on board. */
    private const val OVERNIGHT_HOURS = 7.0


    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    /**
     * Like [options], but with a real flight price (e.g. from Travelpayouts) replacing the estimate.
     * Adds a flight option for shorter hops too when a live fare exists.
     */
    fun options(from: Destination, to: Destination, liveFlightPaise: Long?): List<RouteOption> {
        val base = options(from, to)
        if (liveFlightPaise == null || liveFlightPaise <= 0) return base
        val flight = flightOption(from, to, liveAirFarePaise = liveFlightPaise, force = true) ?: return base
        return base.filter { it.mode != TransportMode.FLIGHT } + flight
    }

    /**
     * Cheapest / fastest / best out of a given option list. "Best" is the traveller's preferred mode
     * ([pref]) when the route has it, otherwise the best balance of money, time and comfort for [style].
     */
    fun choicesFrom(
        all: List<RouteOption>,
        to: Destination,
        style: TravelStyle = TravelStyle.BACKPACKER,
        pref: TravelPref = TravelPref.BEST,
    ): RouteChoices? {
        if (all.isEmpty()) return null
        return RouteChoices(
            cheapest = all.minBy { it.farePaise },
            fastest = all.minBy { it.hours },
            best = pref.pick(all, style) ?: all.minBy { score(it, to, style) },
            all = all.sortedBy { it.farePaise },
        )
    }

    fun options(from: Destination, to: Destination): List<RouteOption> {
        // Places added by name only (no coordinates) can't be estimated.
        if (listOf(from.lat, from.lon, to.lat, to.lon).any { it.isNaN() }) return emptyList()
        if (from.name == to.name) return emptyList()
        val straight = haversineKm(from.lat, from.lon, to.lat, to.lon)
        val hills = from.hills || to.hills
        // Mountain roads wind: short hill hops are much longer than the straight line.
        val roadKm = if (hills) maxOf(straight * 1.35, straight + 40) else straight * 1.25
        val railKm = straight * 1.2

        val result = mutableListOf<RouteOption>()

        if (from.island && to.island && from.region == to.region) {
            result += RouteOption(TransportMode.FERRY, roadKm.toInt(), 120_000, 2.5, overnight = false)
            return result
        }
        if (from.island || to.island) {
            // Mainland <-> islands: realistically only flights (ships run rarely and take days).
            flightOption(from, to, force = true)?.let { result += it }
            return result
        }

        // Nothing priced below 3AC: no sleeper class, no ordinary state buses.
        if (from.rail && to.rail) {
            result += ground(TransportMode.TRAIN_AC, railKm, hills)
            result += ground(TransportMode.TRAIN_2AC, railKm, hills)
            // First class runs on fewer, longer-distance trains.
            if (railKm >= 150) result += ground(TransportMode.TRAIN_1AC, railKm, hills)
        }
        if (roadKm <= 1_500.0) result += ground(TransportMode.BUS_AC_SLEEPER, roadKm, hills)
        if (hills && roadKm < 400) result += ground(TransportMode.SHARED_JEEP, roadKm, hills)
        flightOption(from, to)?.let { result += it }
        return result
    }

    fun choices(
        from: Destination,
        to: Destination,
        style: TravelStyle = TravelStyle.BACKPACKER,
        pref: TravelPref = TravelPref.BEST,
    ): RouteChoices? = choicesFrom(options(from, to), to, style, pref)

    /** Non-AC modes that comfort travellers usually skip when there's a decent alternative. */
    private val basicModes = setOf(TransportMode.SHARED_JEEP)

    /**
     * Lower is better: fare + value of time (depends on style) − the night's stay an overnight
     * journey saves + a comfort penalty for basic modes when the style prefers AC.
     */
    private fun score(o: RouteOption, to: Destination, style: TravelStyle): Long {
        val timeCost = (o.hours * style.timeValuePaisePerHour).roundToLong()
        val saved = if (o.overnight) to.baseline.forStyle(style).stayPaise else 0L
        val comfortPenalty = (if (style.prefersAc && o.mode in basicModes) 1_50_000L else 0L) +
            // A train class below what the style expects (3AC for a 4★ traveller) counts as less comfortable.
            (if (o.mode in TransportMode.TRAIN_CLASSES &&
                TransportMode.TRAIN_CLASSES.indexOf(o.mode) < TransportMode.TRAIN_CLASSES.indexOf(style.trainClass)) 1_00_000L else 0L)
        return o.farePaise + timeCost - saved + comfortPenalty
    }

    /** Minimum straight-line distance at which flying is worth offering (unless forced). */
    private const val MIN_FLIGHT_KM = 250.0

    /** Cheapest ground option and the flight (if any) from [home] to [to], for "From Bengaluru: ✈️ … 🚆 …" snippets. */
    data class HomeFares(val flight: RouteOption?, val ground: RouteOption?)

    fun fromHome(home: Destination, to: Destination, pref: TravelPref = TravelPref.BEST): HomeFares? {
        if (home.name == to.name) return null
        val all = options(home, to)
        val ground = all.filter { it.mode != TransportMode.FLIGHT }
        return HomeFares(
            flight = all.firstOrNull { it.mode == TransportMode.FLIGHT }.takeIf { pref == TravelPref.BEST || pref == TravelPref.AIR },
            ground = (pref.pick(ground) ?: ground.minByOrNull { it.farePaise }).takeIf { pref != TravelPref.AIR || all.none { it.mode == TransportMode.FLIGHT } },
        ).takeIf { it.flight != null || it.ground != null }
    }

    /**
     * Door-to-door flight estimate using the best airport pair ([FlightPlanner.plans]): cab/bus to the
     * departure airport, the flight (with a connection where there's no direct route), and the transfer
     * into town. Null when there's no airport within reach or the hop is too short to bother flying.
     *
     * @param liveAirFarePaise a real fare for the best plan's airport pair, replacing the estimate
     */
    fun flightOption(
        from: Destination,
        to: Destination,
        liveAirFarePaise: Long? = null,
        force: Boolean = false,
    ): RouteOption? {
        if (!force && !from.lat.isNaN() && !to.lat.isNaN() && haversineKm(from.lat, from.lon, to.lat, to.lon) < MIN_FLIGHT_KM) {
            return null
        }
        val plan = FlightPlanner.plans(from, to).firstOrNull() ?: return null
        return plan.toOption(liveAirFarePaise)
    }

    /** (cost, hours, km) between a town and its airport: cab for short hops, bus for long ones. */
    internal fun transfer(town: Destination, airport: Airport): Triple<Long, Double, Int> {
        if (town.lat.isNaN()) return Triple(0L, 0.0, 0)
        val km = haversineKm(town.lat, town.lon, airport.lat, airport.lon) * if (town.hills) 1.5 else 1.3
        if (km < 3) return Triple(0L, 0.0, 0)
        val cost = if (km <= 60) max(30_000L, (km * 20_00).roundToLong()) else max(30_000L, (km * 3_00).roundToLong())
        val hours = km / if (town.hills) 30.0 else 40.0
        return Triple(cost, hours, km.toInt())
    }

    /** Hill routes: assume up to the last 200 km are mountain road, the rest plains. */
    private const val HILL_STRETCH_KM = 200.0

    private fun ground(mode: TransportMode, km: Double, hills: Boolean): RouteOption {
        val r = rates.getValue(mode)
        val fare = max(r.minPaise, (km * r.paisePerKm).roundToLong())
        val hillKm = if (hills) minOf(km, HILL_STRETCH_KM) else 0.0
        val hours = hillKm / r.hillKmh + (km - hillKm) / r.kmh
        return RouteOption(mode, km.toInt(), roundToTen(fare), round1(hours), overnight = hours >= OVERNIGHT_HOURS)
    }

    internal fun roundToTen(paise: Long): Long = ((paise + 500) / 1_000) * 1_000 // nearest ₹10
    internal fun round1(x: Double): Double = Math.round(x * 10) / 10.0
}
