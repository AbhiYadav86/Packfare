package com.packfare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class ItineraryTest {

    private fun d(name: String) = Destinations.find(name)!!

    @Test
    fun `suggests a mountain loop from Delhi that uses all the nights`() {
        val stops = ItineraryBuilder.suggestStops(
            start = d("Delhi"), nights = 10, themes = setOf(Theme.MOUNTAINS),
            regions = setOf("Himachal"), pace = Pace.STEADY, style = TravelStyle.BACKPACKER,
        )
        assertEquals(10, stops.sumOf { it.nights })
        assertEquals(5, stops.size) // 10 nights / 2 per stop
        assertTrue(stops.all { it.destination.region == "Himachal" })
        assertTrue(stops.all { it.nights >= 1 })
    }

    @Test
    fun `without regions it stays within reach and skips islands`() {
        val stops = ItineraryBuilder.suggestStops(
            start = d("Chennai"), nights = 6, themes = setOf(Theme.BEACHES, Theme.HERITAGE),
            regions = emptySet(), pace = Pace.STEADY, style = TravelStyle.BACKPACKER,
        )
        assertTrue(stops.isNotEmpty())
        assertTrue(stops.none { it.destination.island })
        stops.forEach {
            val km = FareEstimator.haversineKm(13.08, 80.27, it.destination.lat, it.destination.lon)
            assertTrue("${it.destination.name} is $km km away", km < 1_100)
        }
    }

    @Test
    fun `budget includes getting there and back`() {
        val stops = listOf(PlanStop(d("Manali"), 3), PlanStop(d("Kasol"), 2))
        val plain = BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER)
        val withHome = BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER, origin = d("Delhi"), returnTo = d("Delhi"))
        assertEquals(1, plain.legs.size)
        assertEquals(3, withHome.legs.size)
        assertEquals(LegKind.OUTBOUND, withHome.legs.first().kind)
        assertEquals(LegKind.RETURN, withHome.legs.last().kind)
        assertTrue(withHome.toAndFromPaise > 0)
        assertEquals(withHome.intercityPaise, withHome.toAndFromPaise + withHome.betweenStopsPaise)
        assertTrue(withHome.perPersonPaise > plain.perPersonPaise)
        // Return overnight bus doesn't "save" a stay (you're going home).
        assertEquals(0L, withHome.legs.last().savedStayPaise)
    }

    @Test
    fun `user can switch a leg's mode and live flight prices are used`() {
        val stops = listOf(PlanStop(d("Goa (North)"), 3))
        val delhi = d("Delhi")
        val train = BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER, origin = delhi)
        assertEquals(TransportMode.TRAIN_AC, train.legs.first().option.mode)

        val key = BudgetPlanner.key(delhi, d("Goa (North)"))
        val flown = BudgetPlanner.suggest(
            stops, TravelStyle.BACKPACKER, origin = delhi,
            modeOverrides = mapOf(0 to TransportMode.FLIGHT),
            liveFlights = mapOf(key to 4_199_00L),
        )
        val leg = flown.legs.first()
        assertEquals(TransportMode.FLIGHT, leg.option.mode)
        // Live air fare plus getting to and from the airports
        assertTrue(leg.option.transferPaise > 0)
        assertTrue(kotlin.math.abs(4_199_00L + leg.option.transferPaise - leg.option.farePaise) <= 1_000L) // rounded to ₹10
        assertTrue(leg.liveFlightPrice)
    }

    @Test
    fun `day by day plan covers every night plus the journey home`() {
        val stops = listOf(PlanStop(d("Manali"), 3), PlanStop(d("Kasol"), 2))
        val s = BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER, origin = d("Delhi"), returnTo = d("Delhi"))
        val start = LocalDate.of(2026, 11, 1)
        val days = ItineraryBuilder.days(
            s, start,
            mapOf("Manali" to listOf("Hadimba Temple", "Old Manali", "Solang Valley").map { ActivityIdea(it, 10_000L) }),
        )
        assertEquals(6, days.size) // 5 nights + travel-home day
        assertEquals("Travel to Manali", days[0].title)
        assertEquals(LegKind.OUTBOUND, days[0].travel!!.kind)
        // The arrival day is lighter (one idea); explore days get two.
        assertEquals(listOf("Hadimba Temple"), days[0].highlights)
        assertEquals(listOf("Old Manali", "Solang Valley"), days[1].highlights)
        assertEquals(20_000L, days[1].activitiesPaise)
        assertEquals("Travel to Kasol", days[3].title)
        assertEquals("Head home to Delhi", days.last().title)
        assertEquals(start.plusDays(5), days.last().date)
    }
}
