package com.packfare.app.domain

/** A stop on a planned trip. [costs] lets Explore override the table with live prices. */
data class PlanStop(
    val destination: Destination,
    val nights: Int,
    val costs: DailyCosts = destination.baseline,
)

data class StopEstimate(
    val stop: PlanStop,
    val perDayPaise: Long,
    val totalPaise: Long,
    val daily: DailyCosts,
)

/** Getting there from home, moving between stops, or heading back. */
enum class LegKind { OUTBOUND, BETWEEN, RETURN }

data class LegEstimate(
    val from: Destination,
    val to: Destination,
    val option: RouteOption,
    /** Night's stay this leg saves because you sleep on board. */
    val savedStayPaise: Long,
    val kind: LegKind = LegKind.BETWEEN,
    /** All the ways to do this hop, so the user can switch mode. */
    val alternatives: List<RouteOption> = listOf(option),
    /** The flight price came from the internet, not our estimate. */
    val liveFlightPrice: Boolean = false,
    /** Position in [BudgetPlanner.hops], used for mode overrides. */
    val index: Int = 0,
)

/** A full suggested budget, per person unless stated. */
data class BudgetSuggestion(
    val stops: List<StopEstimate>,
    val legs: List<LegEstimate>,
    val people: Int,
    val style: TravelStyle,
    /** Activities you picked in the itinerary beyond the day-to-day allowance, per person. */
    val extrasPaise: Long = 0,
) {
    val days: Int get() = stops.sumOf { it.stop.nights }.coerceAtLeast(1)
    val stayPaise: Long get() = stops.sumOf { it.daily.stayPaise * it.stop.nights }
    val foodPaise: Long get() = stops.sumOf { it.daily.foodPaise * it.stop.nights }
    val localPaise: Long get() = stops.sumOf { it.daily.localTransportPaise * it.stop.nights }
    val activitiesPaise: Long get() = stops.sumOf { it.daily.activitiesPaise * it.stop.nights }
    val intercityPaise: Long get() = legs.sumOf { it.option.farePaise }

    /** Home → first stop and last stop → home. */
    val toAndFromPaise: Long get() = legs.filter { it.kind != LegKind.BETWEEN }.sumOf { it.option.farePaise }

    /** Hops between stops. */
    val betweenStopsPaise: Long get() = legs.filter { it.kind == LegKind.BETWEEN }.sumOf { it.option.farePaise }

    /** Everything you'll spend getting around: there & back, between stops, and local rides. */
    val allTransportPaise: Long get() = intercityPaise + localPaise
    val overnightSavingsPaise: Long get() = legs.sumOf { it.savedStayPaise }

    /** SIM, laundry, tips, the odd emergency: a small cushion on top. */
    val bufferPaise: Long get() = ((stayPaise + foodPaise + localPaise + activitiesPaise + extrasPaise + intercityPaise) * 0.08).toLong()

    val perPersonPaise: Long
        get() = stayPaise + foodPaise + localPaise + activitiesPaise + extrasPaise + intercityPaise + bufferPaise - overnightSavingsPaise

    val groupTotalPaise: Long get() = perPersonPaise * people
    val perPersonPerDayPaise: Long get() = perPersonPaise / days
}

data class ReverseResult(
    val days: Int,
    val perDayPaise: Long,
    val reservedForTravelPaise: Long,
    val leftoverPaise: Long,
)

object BudgetPlanner {

    /**
     * Polarsteps-style suggestion: stops + nights + style -> per-person budget with a breakdown,
     * including the fares to get there from [origin] and back to [returnTo].
     *
     * @param modeOverrides leg index -> mode the user picked (legs are numbered in travel order,
     *   starting with the outbound leg if there is one)
     * @param liveFlights "From>To" (destination names) -> live cheapest flight fare in paise
     */
    fun suggest(
        stops: List<PlanStop>,
        style: TravelStyle,
        people: Int = 1,
        pickBest: Boolean = true,
        origin: Destination? = null,
        returnTo: Destination? = null,
        modeOverrides: Map<Int, TransportMode> = emptyMap(),
        liveFlights: Map<String, Long> = emptyMap(),
        /** Null = decide from [people] (1 person pays the whole room). */
        sharingRoom: Boolean? = null,
        /** Air, train or bus: used for every leg that has it, unless the user switched that leg. */
        pref: TravelPref = TravelPref.BEST,
    ): BudgetSuggestion {
        // Hotel rooms: two people share a room; a solo traveller pays for the whole room.
        val solo = sharingRoom?.not() ?: (people <= 1)
        val stopEstimates = stops.map { s ->
            val daily = s.costs.forStyle(style, solo)
            StopEstimate(s, daily.totalPaise, daily.totalPaise * s.nights, daily)
        }
        val hops = hops(stops, origin, returnTo)
        val legs = hops.mapIndexedNotNull { i, (a, b, kind) ->
            val live = liveFlights[key(a, b)]
            val base = FareEstimator.options(a, b, live)
            // Someone who'd rather fly gets a flight on shorter hops too, if both ends have an airport.
            val all = if (pref == TravelPref.AIR && base.none { it.mode == TransportMode.FLIGHT }) {
                base + listOfNotNull(FareEstimator.flightOption(a, b, live, force = true))
            } else {
                base
            }
            val choices = FareEstimator.choicesFrom(all, b, style, pref) ?: return@mapIndexedNotNull null
            val option = modeOverrides[i]?.let { m -> all.firstOrNull { it.mode == m } }
                ?: if (pickBest) choices.best else choices.cheapest
            // Sleeping on board replaces a night you'd pay for at the next stop (not at home).
            val stopCosts = stops.firstOrNull { it.destination.name == b.name }?.costs
            val saved = if (option.overnight && kind != LegKind.RETURN && stopCosts != null) {
                stopCosts.forStyle(style, solo).stayPaise
            } else {
                0L
            }
            LegEstimate(
                from = a,
                to = b,
                option = option,
                savedStayPaise = saved,
                kind = kind,
                alternatives = choices.all,
                liveFlightPrice = live != null && option.mode == TransportMode.FLIGHT,
                index = i,
            )
        }
        return BudgetSuggestion(stopEstimates, legs, people.coerceAtLeast(1), style)
    }

    /** All hops in travel order: home → stop 1 → … → stop n → home. */
    fun hops(stops: List<PlanStop>, origin: Destination?, returnTo: Destination?): List<Triple<Destination, Destination, LegKind>> {
        if (stops.isEmpty()) return emptyList()
        val out = mutableListOf<Triple<Destination, Destination, LegKind>>()
        val first = stops.first().destination
        val last = stops.last().destination
        if (origin != null && origin.name != first.name) out += Triple(origin, first, LegKind.OUTBOUND)
        stops.zipWithNext().forEach { (a, b) -> out += Triple(a.destination, b.destination, LegKind.BETWEEN) }
        if (returnTo != null && returnTo.name != last.name) out += Triple(last, returnTo, LegKind.RETURN)
        return out
    }

    fun key(a: Destination, b: Destination) = "${a.name}>${b.name}"

    /**
     * "I have ₹X. How long can I travel in this region?"
     * Keeps [travelReservePct] aside for long-distance hops, then divides by the region's average daily cost.
     */
    fun reverse(
        totalPaise: Long,
        places: List<Destination>,
        style: TravelStyle,
        travelReservePct: Int = 15,
    ): ReverseResult? {
        if (places.isEmpty() || totalPaise <= 0) return null
        val perDay = places.map { it.baseline.forStyle(style).totalPaise }.average().toLong()
        if (perDay <= 0) return null
        val reserve = totalPaise * travelReservePct / 100
        val spendable = totalPaise - reserve
        // Include the same 8% cushion the forward planner uses.
        val perDayWithBuffer = (perDay * 1.08).toLong()
        val days = (spendable / perDayWithBuffer).toInt()
        return ReverseResult(days, perDayWithBuffer, reserve, spendable - days * perDayWithBuffer)
    }
}
