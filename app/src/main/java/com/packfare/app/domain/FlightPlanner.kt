package com.packfare.app.domain

import kotlin.math.roundToLong

/**
 * One way to fly between two places: which airports, whether there's a connection, and the ground
 * transfers at each end. Kullu has no airport of its own; its plans are Kullu–Bhuntar (10 km, few and
 * pricey flights) and Chandigarh (≈250 km by road, lots of cheap flights), and the app shows both.
 */
data class FlightPlan(
    val from: Airport,
    val to: Airport,
    /** Connecting airport when there's usually no direct flight; null = direct. */
    val via: Airport?,
    /** Estimated one-way economy air fare (paise), booked ~2 weeks ahead. */
    val airPaise: Long,
    val transferFromPaise: Long,
    val transferToPaise: Long,
    val transferFromKm: Int,
    val transferToKm: Int,
    val hours: Double,
    val airKm: Int,
) {
    val transferPaise: Long get() = transferFromPaise + transferToPaise
    val totalPaise: Long get() = airPaise + transferPaise

    /** "52 km to KUU, fly BLR→DEL→KUU (1 stop), 30 km from KUU by cab/bus" */
    fun note(): String = listOfNotNull(
        if (transferFromKm >= 15) "$transferFromKm km to ${from.code}" else null,
        "fly ${from.code}→" + (via?.let { "${it.code}→" } ?: "") + to.code + (if (via != null) " (1 stop)" else ""),
        if (transferToKm >= 15) "$transferToKm km from ${to.code} by ${if (transferToKm <= 60) "cab" else "bus/cab"}" else null,
    ).joinToString(", ").replaceFirstChar { it.uppercase() }

    fun toOption(liveAirFarePaise: Long? = null): RouteOption {
        val air = liveAirFarePaise ?: airPaise
        return RouteOption(
            mode = TransportMode.FLIGHT,
            distanceKm = airKm,
            farePaise = FareEstimator.roundToTen(air + transferPaise),
            hours = FareEstimator.round1(hours),
            overnight = false,
            note = note(),
            transferPaise = transferPaise,
            fromAirport = from.code,
            toAirport = to.code,
        )
    }
}

object FlightPlanner {

    /** India's six big hubs: almost every airport has direct flights to at least one of them. */
    private val METROS = setOf("DEL", "BOM", "BLR", "MAA", "CCU", "HYD")

    /** Busy airports that usually have direct flights between each other too. */
    private val BIG = METROS + setOf(
        "AMD", "PNQ", "COK", "GOI", "GOX", "JAI", "LKO", "GAU", "IXC", "TRV", "BBI", "PAT", "IDR", "NAG",
        "CJB", "VNS", "IXB", "SXR", "ATQ", "IXE", "VTZ", "BHO", "RPR",
    )

    /** How far from town an airport is still worth considering (straight line). */
    private const val NEAREST_MAX_KM = 150.0
    private const val MAJOR_MAX_KM = 300.0

    /** Ground transfers longer than this make flying pointless. */
    private const val MAX_TRANSFER_HOURS = 9.0

    /** What an hour is "worth" when comparing airport choices (₹120). */
    private const val HOUR_VALUE_PAISE = 12_000L

    /**
     * Airports that make sense for a place: its own (or nearest) airport, plus the nearest busy airport
     * if that's a different one. For Kullu: KUU and IXC; for Ooty: CJB; for Delhi: DEL.
     */
    fun candidatesFor(d: Destination): List<Airport> {
        val own = Airports[d.airport]
        if (d.lat.isNaN()) return listOfNotNull(own)
        val nearest = own ?: Airports.nearest(d.lat, d.lon, NEAREST_MAX_KM)
        val major = Airports.nearest(d.lat, d.lon, MAJOR_MAX_KM) { !it.regional }
        return listOfNotNull(nearest, major).distinctBy { it.code }
    }

    /** Every sensible airport pair, best first (fare + transfers + time). */
    fun plans(from: Destination, to: Destination): List<FlightPlan> {
        val origins = candidatesFor(from)
        val dests = candidatesFor(to)
        val plans = mutableListOf<FlightPlan>()
        for (a in origins) for (b in dests) {
            if (a.code == b.code) continue
            plan(from, to, a, b)?.let { plans += it }
        }
        return plans.sortedBy { it.totalPaise + (it.hours * HOUR_VALUE_PAISE).roundToLong() }
    }

    /** Best plan for each destination airport (e.g. one via KUU, one via IXC), best first. */
    fun choicesByArrivalAirport(from: Destination, to: Destination): List<FlightPlan> =
        plans(from, to).distinctBy { it.to.code }

    private fun plan(from: Destination, to: Destination, a: Airport, b: Airport): FlightPlan? {
        val t1 = FareEstimator.transfer(from, a)
        val t2 = FareEstimator.transfer(to, b)
        if (t1.second > MAX_TRANSFER_HOURS || t2.second > MAX_TRANSFER_HOURS) return null
        val airKm = km(a, b)
        // Small regional airports mostly fly only to their nearest metro (Kullu–Bhuntar: Delhi).
        val regionalHub = when {
            a.regional && b.code != hubOf(a).code -> hubOf(a)
            b.regional && a.code != hubOf(b).code -> hubOf(b)
            else -> null
        }
        val direct = regionalHub == null && (a.code in METROS || b.code in METROS || (a.code in BIG && b.code in BIG))
        val (air, flyHours, via) = if (direct) {
            Triple(segmentFare(a, b), airKm / 650.0, null)
        } else {
            // Connect through the regional airport's metro, or whichever metro adds the least distance.
            val hub = regionalHub ?: METROS.mapNotNull { Airports[it] }.minBy { km(a, it) + km(it, b) }
            val fare = (segmentFare(a, hub) + segmentFare(hub, b)) * 85 / 100 // through fares are a bit cheaper
            Triple(fare, (km(a, hub) + km(hub, b)) / 650.0 + 1.5, hub)
        }
        return FlightPlan(
            from = a,
            to = b,
            via = via,
            airPaise = FareEstimator.roundToTen(air),
            transferFromPaise = t1.first,
            transferToPaise = t2.first,
            transferFromKm = t1.third,
            transferToKm = t2.third,
            hours = 2.0 + flyHours + t1.second + t2.second, // 2 h for check-in, security and boarding
            airKm = airKm.toInt(),
        )
    }

    private fun hubOf(regional: Airport): Airport = METROS.mapNotNull { Airports[it] }.minBy { km(regional, it) }

    /**
     * Typical one-way economy fare for one flight, booked ~2 weeks ahead (2026 levels):
     * ₹2,200 + ₹2.4/km; busy metro–metro routes ~8% cheaper; small regional airports (few flights,
     * small planes) cost ~80% more.
     */
    fun segmentFare(a: Airport, b: Airport): Long {
        val base = 2_200_00L + (km(a, b) * 2_40).roundToLong()
        return when {
            a.regional || b.regional -> base * 18 / 10
            a.code in METROS && b.code in METROS -> base * 92 / 100
            else -> base
        }
    }

    private fun km(a: Airport, b: Airport) = FareEstimator.haversineKm(a.lat, a.lon, b.lat, b.lon)
}
