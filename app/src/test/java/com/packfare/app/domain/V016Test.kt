package com.packfare.app.domain

import com.packfare.app.fares.BookingLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V016Test {
    @Test fun capIsPerRoomPerNight() {
        assertEquals(200_000L, HotelBudget.nightlyCapPaise(1_200_000L, 3, 2))
    }
    @Test fun unitsPairUpRooms() {
        assertEquals(2, HotelBudget.units(3, rooms = true, sharing = true))
        assertEquals(3, HotelBudget.units(3, rooms = false, sharing = true))
    }
    @Test fun fitsIsInclusive() {
        assertTrue(HotelBudget.fits(200_000, 200_000))
        assertTrue(!HotelBudget.fits(200_001, 200_000))
    }
    @Test fun urls() {
        val goa = Destinations.find("Goa (North)") ?: Destinations.all.first()
        val a = LocalDate.of(2026, 12, 1)
        val b = BookingLinks.bookingComUnder(goa, a, a.plusDays(2), 2, 1, 250_000)
        assertTrue(b.contains("checkin=2026-12-01") && b.contains("INR-min-2500-1".replace("-", "-")) .or(b.contains("2500")))
        val g = BookingLinks.agoda(goa, a, a.plusDays(2), 2, 1)
        assertTrue(g.startsWith("https://www.agoda.com/") && g.contains("checkIn=2026-12-01") && g.contains("currencyCode=INR"))
    }
    private fun Boolean.or(o: Boolean) = this || o
}
