package com.packfare.app.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/** The four ways the Route tab offers for a hop. */
enum class Journey(val label: String, val emoji: String) {
    TRAIN("Train", "🚆"),
    BUS("Bus", "🚌"),
    FLIGHT("Flight", "✈️"),
    TAXI("Taxi", "🚕");

    companion object {
        fun of(mode: TransportMode): Journey = when (mode) {
            TransportMode.TRAIN_SLEEPER, TransportMode.TRAIN_AC, TransportMode.TRAIN_2AC, TransportMode.TRAIN_1AC -> TRAIN
            TransportMode.BUS_STATE, TransportMode.BUS_AC_SLEEPER -> BUS
            TransportMode.FLIGHT -> FLIGHT
            TransportMode.SHARED_JEEP, TransportMode.TAXI -> TAXI
            else -> TRAIN
        }
    }
}

/** One way to cover a hop, priced per person and for the whole group. */
data class LegOption(
    val journey: Journey,
    val mode: TransportMode,
    val title: String,
    val perPersonPaise: Long,
    val groupPaise: Long,
    val hours: Double,
    val overnight: Boolean,
    val detail: String?,
    /** Flights: the airport pair this option uses, for booking links. */
    val fromAirport: String? = null,
    val toAirport: String? = null,
)

/** Everything the Route tab shows for one hop: options per journey type, cheapest first. */
object LegOptions {

    /** Private cab, outstation one way: about ₹13 a km on plains and ₹17 in the hills, minimum ₹1,500, up to 4 people a car. */
    fun taxi(from: Destination, to: Destination, people: Int): LegOption? {
        if (listOf(from.lat, from.lon, to.lat, to.lon).any { it.isNaN() } || from.name == to.name) return null
        if (from.island || to.island) return null
        val straight = FareEstimator.haversineKm(from.lat, from.lon, to.lat, to.lon)
        val hills = from.hills || to.hills
        val roadKm = if (hills) max(straight * 1.35, straight + 40) else straight * 1.25
        if (roadKm > 700) return null
        val perKm = if (hills) 17_00L else 13_00L
        val cabPaise = FareEstimator.roundToTen(max(1_500_00L, (roadKm * perKm).roundToLong()))
        val cabs = ceil(people.coerceAtLeast(1) / 4.0).toInt()
        val group = cabs * cabPaise
        val hours = FareEstimator.round1(roadKm / if (hills) 32.0 else 50.0)
        return LegOption(
            Journey.TAXI, TransportMode.TAXI,
            "Private cab" + if (cabs > 1) " (×$cabs)" else "",
            perPersonPaise = FareEstimator.roundToTen(group / people.coerceAtLeast(1)),
            groupPaise = group,
            hours = hours,
            overnight = false,
            detail = "Door to door, up to 4 people a car, ${roadKm.toInt()} km. Tolls and night charges can add to it.",
        )
    }

    fun forLeg(from: Destination, to: Destination, people: Int): Map<Journey, List<LegOption>> {
        val n = people.coerceAtLeast(1)
        val out = linkedMapOf<Journey, MutableList<LegOption>>()
        fun add(o: LegOption) { out.getOrPut(o.journey) { mutableListOf() } += o }

        for (o in FareEstimator.options(from, to)) {
            when (o.mode) {
                TransportMode.FLIGHT -> Unit // handled below, one row per airport plan
                else -> add(
                    LegOption(
                        Journey.of(o.mode), o.mode, o.mode.label,
                        perPersonPaise = o.farePaise, groupPaise = o.farePaise * n,
                        hours = o.hours, overnight = o.overnight,
                        detail = when {
                            o.mode in TransportMode.TRAIN_CLASSES -> "About ${o.distanceKm} km by rail. Book on IRCTC about 60 days ahead."
                            o.mode == TransportMode.BUS_AC_SLEEPER -> "About ${o.distanceKm} km by road, AC sleeper or seater."
                            o.mode == TransportMode.SHARED_JEEP -> "Leaves when full; fixed fare per seat."
                            else -> null
                        },
                    ),
                )
            }
        }
        for (plan in FlightPlanner.plans(from, to).take(2)) {
            val o = plan.toOption()
            add(
                LegOption(
                    Journey.FLIGHT, TransportMode.FLIGHT,
                    "${plan.from.code} → ${plan.to.code}" + if (plan.via != null) " via ${plan.via.code}" else "",
                    perPersonPaise = o.farePaise, groupPaise = o.farePaise * n,
                    hours = o.hours, overnight = false,
                    detail = plan.note() + ". Fare includes the transfers to and from the airports.",
                    fromAirport = plan.from.code, toAirport = plan.to.code,
                ),
            )
        }
        taxi(from, to, n)?.let { add(it) }
        return out.mapValues { (_, l) -> l.sortedBy { it.perPersonPaise } }
    }

    /** The per-person budget to start from: what the leg already costs, else the cheapest option. */
    fun defaultCapPaise(legFarePaise: Long, options: Map<Journey, List<LegOption>>): Long =
        if (legFarePaise > 0) legFarePaise else options.values.flatten().minOfOrNull { it.perPersonPaise } ?: 0L
}
