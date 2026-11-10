package com.packfare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V015Test {
    @Test
    fun unpriced_guide_listings_get_an_estimate_not_price_varies() {
        val fort = ActivityEstimator.withEstimate(ActivityIdea("Jaisalmer Fort"))
        assertNotNull(fort.lowPaise)
        assertTrue(fort.estimated)
        assertFalse(fort.costLabel.contains("varies"))
        assertTrue(fort.costLabel.endsWith("est."))
        val lake = ActivityEstimator.estimate("Gadisar Lake")
        assertEquals(0L, lake.first)
        val dunes = ActivityEstimator.estimate("Sam Sand Dunes")
        assertTrue(dunes.second >= 100_000L)
        // Unknown things still get a modest range.
        assertEquals(5_000L to 30_000L, ActivityEstimator.estimate("Something unusual"))
    }

    @Test
    fun priced_ideas_are_left_alone() {
        val a = ActivityIdea("Museum", lowPaise = 500L, highPaise = 500L)
        assertEquals(a, ActivityEstimator.withEstimate(a))
        assertFalse(a.estimated)
    }

    @Test
    fun ideasFor_never_returns_unpriced_ideas() {
        val d = Destinations.find("Jaisalmer")!!
        val guide = listOf(ActivityIdea("Jaisalmer Fort"), ActivityIdea("Gadisar Lake"), ActivityIdea("Sam Sand Dunes"))
        val ideas = Activities.ideasFor(d, guide)
        assertTrue(ideas.isNotEmpty())
        assertTrue(ideas.all { it.lowPaise != null })
    }

    @Test
    fun booking_items_keep_amounts_and_old_lines_still_decode() {
        val items = listOf(
            BookingItem("leg0", "Delhi → Leh · Flight", true, plannedPaise = 900_000, actualPaise = 750_000),
            BookingItem("stay0", "Stay in Leh", false, plannedPaise = 600_000),
        )
        val back = BookingItem.decode(BookingItem.encode(items))
        assertEquals(items, back)
        // v0.14 lines had only three fields.
        val old = BookingItem.decode("leg0\tDelhi → Leh\t1")
        assertEquals(1, old.size)
        assertTrue(old[0].done)
        assertEquals(0L, old[0].plannedPaise)
    }

    @Test
    fun budget_is_recalculated_when_a_flight_is_booked() {
        val items = listOf(
            BookingItem("leg0", "Flight", false, plannedPaise = 900_000),
            BookingItem("stay0", "Hotel", false, plannedPaise = 600_000),
        )
        val before = BudgetAfterBookings.compute(3_000_000, 100_000, items)
        assertEquals(3_000_000L - 1_500_000L - 100_000L, before.leftPaise)
        val booked = BookingItem.setBooked(items, "leg0", true, actualPaise = 750_000)
        val after = BudgetAfterBookings.compute(3_000_000, 100_000, booked)
        assertEquals(750_000L, after.bookedPaise)
        assertEquals(600_000L, after.stillToBookPaise)
        assertEquals(before.leftPaise + 150_000L, after.leftPaise)
        // No price entered: the planned price counts.
        val noPrice = BookingItem.setBooked(items, "leg0", true, null)
        assertEquals(before.leftPaise, BudgetAfterBookings.compute(3_000_000, 100_000, noPrice).leftPaise)
        // Undoing returns to the plan.
        val undone = BookingItem.setBooked(booked, "leg0", false)
        assertEquals(before.leftPaise, BudgetAfterBookings.compute(3_000_000, 100_000, undone).leftPaise)
    }
}
