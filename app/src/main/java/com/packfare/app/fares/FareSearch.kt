package com.packfare.app.fares

import com.packfare.app.domain.Destination
import com.packfare.app.domain.FareEstimator
import com.packfare.app.domain.FlightPlan
import com.packfare.app.domain.FlightPlanner
import com.packfare.app.domain.Money
import com.packfare.app.domain.TransportMode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/**
 * "What's the cheapest way from A to B on this date?"
 *
 * - Flights: live prices from Travelpayouts when a token is configured, between the nearest airports.
 * - Trains, buses, shared jeeps, ferries: Packfare's estimates, each with a link to check live
 *   availability on IRCTC / redBus. There's no free, official live API for Indian trains or buses;
 *   [extraProviders] is where a licensed one (an IRCTC partner, a bus aggregator) can plug in later.
 */
class FareSearch(
    private val flights: TravelpayoutsFlights,
    private val extraProviders: List<FareProvider> = emptyList(),
) {
    val liveFlights: Boolean get() = flights.enabled

    suspend fun search(from: Destination, to: Destination, date: LocalDate): FareResults = coroutineScope {
        val notices = mutableListOf<String>()
        val estimates = FarePicker.estimates(from, to, date)
        val liveFlights = async { liveFlights(from, to, date, notices) }
        val extra = extraProviders.map { p -> async { runCatching { p.quotes(from, to, date) }.getOrDefault(emptyList()) } }

        val live = liveFlights.await() + extra.flatMap { it.await() }
        // Drop the estimated flight when real flight prices came back.
        val merged = if (live.any { it.mode == TransportMode.FLIGHT }) {
            estimates.filter { it.mode != TransportMode.FLIGHT } + live
        } else {
            estimates + live
        }
        FarePicker.pick(merged, notices)
    }

    /**
     * Just the flights, for each sensible arrival airport (e.g. Kullu: Kullu–Bhuntar and Chandigarh):
     * the estimate always, live fares when a token is set and we're online, and links to check prices.
     */
    suspend fun flights(from: Destination, to: Destination, date: LocalDate): FlightOptions = coroutineScope {
        val notices = mutableListOf<String>()
        val plans = FlightPlanner.choicesByArrivalAirport(from, to).take(3)
        if (plans.isEmpty()) notices += "No airport within reach of ${from.name} or ${to.name}."
        if (!flights.enabled) notices += "Live flight prices are off (no Travelpayouts token). Prices are Packfare estimates; tap a site to check."
        val results = plans.map { plan ->
            async {
                FlightPlanFares(
                    plan = plan,
                    estimate = plan.toOption(),
                    live = if (flights.enabled) livePrices(plan, date, notices) else emptyList(),
                    links = BookingLinks.flightLinks(plan.from.code, plan.to.code, date),
                )
            }
        }.map { it.await() }
        FlightOptions(results, notices.distinct().firstOrNull())
    }

    /** Live fares for the best airport pair, used by the all-modes search. */
    private suspend fun liveFlights(from: Destination, to: Destination, date: LocalDate, notices: MutableList<String>): List<FareQuote> {
        if (!flights.enabled) {
            notices += "Live flight prices are off. Add a Travelpayouts token (see README) to turn them on."
            return emptyList()
        }
        val plan = FlightPlanner.plans(from, to).firstOrNull()
        if (plan == null) {
            notices += "No airport within reach of ${from.name} or ${to.name}, so no flights."
            return emptyList()
        }
        return livePrices(plan, date, notices)
    }

    /**
     * Live fares for one airport pair. Each price includes Packfare's estimate for getting to and from
     * the airports (kept in [FareQuote.transferPaise]) so it compares fairly with trains and buses.
     */
    private suspend fun livePrices(plan: FlightPlan, date: LocalDate, notices: MutableList<String>): List<FareQuote> =
        runCatching { flights.cheapest(plan.from.code, plan.to.code, date) }
            .onFailure { synchronized(notices) { notices += "Couldn't fetch live flight prices (offline?). Showing estimates." } }
            .getOrDefault(emptyList())
            .sortedBy { it.pricePaise }
            .map { q ->
                val transfer = plan.transferPaise
                if (transfer <= 0) return@map q
                val transferHours = plan.hours - 2.0 - (if (plan.via != null) 1.5 else 0.0) - plan.airKm / 650.0
                q.copy(
                    pricePaise = q.pricePaise + transfer,
                    hours = q.hours?.let { h -> h + transferHours.coerceAtLeast(0.0) },
                    note = listOfNotNull(q.note, "${Money.format(q.pricePaise)} fare + ≈${Money.format(transfer)} airport transfers")
                        .joinToString(" · "),
                    transferPaise = transfer,
                )
            }
}

/** Plug-in point for any future live source (licensed train/bus APIs). */
interface FareProvider {
    suspend fun quotes(from: Destination, to: Destination, date: LocalDate): List<FareQuote>
}
