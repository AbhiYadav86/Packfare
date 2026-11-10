package com.packfare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V013Test {
    private fun day(n: Int, vararg costs: Long) = ItineraryDay(
        day = n, date = LocalDate.of(2026, 6, 12 + n), place = "Leh", title = "Day $n",
        activities = costs.mapIndexed { i, c -> ActivityIdea("Idea $n.$i", lowPaise = c, highPaise = c) },
    )

    @Test
    fun ticked_ideas_only_add_beyond_the_allowance() {
        val days = listOf(day(1, 10_000, 20_000), day(2, 30_000))
        assertEquals(60_000L, ActivityPicks.pickedPaise(days, emptySet()))
        assertEquals(0L, ActivityPicks.extraPaise(days, emptySet(), allowancePaise = 80_000))
        assertEquals(10_000L, ActivityPicks.extraPaise(days, emptySet(), allowancePaise = 50_000))
        val skipped = setOf(ActivityPicks.key(2, days[1].activities[0]))
        assertEquals(30_000L, ActivityPicks.pickedPaise(days, skipped))
        assertEquals(0L, ActivityPicks.extraPaise(days, skipped, allowancePaise = 50_000))
    }

    @Test
    fun extras_raise_the_trip_total() {
        val stop = Destinations.search("Manali", limit = 1).first()
        val plan = BudgetPlanner.suggest(listOf(PlanStop(stop, 3)), TravelStyle.BACKPACKER, 1)
        val more = plan.copy(extrasPaise = 100_000)
        assertTrue(more.perPersonPaise > plan.perPersonPaise + 100_000)
    }

    @Test
    fun bookings_round_trip_and_toggle() {
        val items = listOf(BookingItem("leg0", "Delhi → Leh · Flight", false), BookingItem("stay0", "Stay in Leh\t5 nights", true))
        val back = BookingItem.decode(BookingItem.encode(items))
        assertEquals(2, back.size)
        assertEquals("Stay in Leh 5 nights", back[1].label)
        assertTrue(back[1].done)
        val t = BookingItem.toggle(back, "leg0")
        assertTrue(t[0].done)
        assertEquals(emptyList<BookingItem>(), BookingItem.decode(null))
    }
}
