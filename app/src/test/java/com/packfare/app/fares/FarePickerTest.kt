package com.packfare.app.fares

import com.packfare.app.domain.Destinations
import com.packfare.app.domain.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class FarePickerTest {

    private val date = LocalDate.of(2026, 10, 10)

    @Test
    fun `cheapest prefers live prices when close, best value weighs time`() {
        val quotes = listOf(
            FareQuote(TransportMode.TRAIN_AC, "3AC", 50_000, live = false, provider = "est", hours = 14.0),
            FareQuote(TransportMode.FLIGHT, "IndiGo", 3_20_000, live = true, provider = "tp", hours = 2.0),
            FareQuote(TransportMode.BUS_AC_SLEEPER, "Bus", 51_000, live = true, provider = "x", hours = 18.0),
            FareQuote(TransportMode.OTHER, "Free?", 0, live = false, provider = "bad"),
            FareQuote(TransportMode.TRAIN_SLEEPER, "Sleeper class", 20_000, live = true, provider = "x", hours = 14.0),
        )
        val r = FarePicker.pick(quotes)
        assertEquals(3, r.quotes.size) // zero-price and below-3AC quotes dropped
        assertEquals("Bus", r.cheapest!!.title) // live ₹510 beats estimated ₹500 (within 5%)
        assertEquals("IndiGo", r.fastest!!.title)
        // 3AC: 500 + 14h*60 = 1340; Bus: 510 + 18*60 = 1590; Flight: 3200 + 120 = 3320
        assertEquals("3AC", r.bestValue!!.title)
    }

    @Test
    fun `estimates come with booking links`() {
        val delhi = Destinations.find("Delhi")!!
        val varanasi = Destinations.find("Varanasi")!!
        val est = FarePicker.estimates(delhi, varanasi, date)
        assertTrue(est.isNotEmpty())
        assertTrue(est.all { !it.live && it.url != null })
        assertEquals(BookingLinks.IRCTC, est.first { it.mode == TransportMode.TRAIN_AC }.url)
        assertEquals(
            "https://www.redbus.in/bus-tickets/delhi-to-varanasi",
            est.first { it.mode == TransportMode.BUS_AC_SLEEPER }.url,
        )
    }

    @Test
    fun `link builders`() {
        assertEquals("goa", BookingLinks.slug("Goa (North)")) // redBus city pages use the plain city name
        assertEquals("mcleod-ganj", BookingLinks.slug("McLeod Ganj"))
        assertEquals("https://www.aviasales.com/search/DEL1010KUU1", BookingLinks.aviasalesSearch("DEL", "KUU", date, null))
        assertEquals(
            "https://www.aviasales.com/search/DEL0503IXL1?marker=12345",
            BookingLinks.aviasalesSearch("DEL", "IXL", LocalDate.of(2027, 3, 5), "12345"),
        )
    }
}
