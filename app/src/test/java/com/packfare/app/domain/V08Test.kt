package com.packfare.app.domain

import com.packfare.app.fares.BookingLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V08Test {
    private fun d(name: String) = Destinations.find(name)!!

    @Test
    fun `about a thousand searchable places with unique names and known regions`() {
        val all = Destinations.all
        assertTrue(all.size in 950..1000)
        assertEquals(all.size, all.map { it.name.lowercase() }.toSet().size)
        assertTrue(all.all { it.region in Destinations.regions })
        assertTrue(all.all { it.lat in 6.0..37.5 && it.lon in 68.0..97.5 })
        listOf("Kullu", "Chitkul", "Hampi", "Ludhiana", "Agonda", "Tawang", "Kargil", "Shirdi", "Nalanda").forEach {
            assertTrue(Destinations.find(it) != null)
        }
        // Hubs come first in search
        assertEquals("Ahmedabad", Destinations.search("ahm").first().name)
        assertTrue(Destinations.hubs.size < Destinations.all.size)
    }

    @Test
    fun `Kullu has no airport of its own but two sensible airports`() {
        val kullu = d("Kullu")
        assertTrue(kullu.airport == null)
        val codes = FlightPlanner.candidatesFor(kullu).map { it.code }
        assertTrue("KUU" in codes && "IXC" in codes)
        val plans = FlightPlanner.choicesByArrivalAirport(d("Bengaluru"), kullu)
        assertTrue(plans.size >= 2)
        // Bengaluru → Kullu–Bhuntar needs a connection (via Delhi)
        val kuu = plans.first { it.to.code == "KUU" }
        assertEquals("DEL", kuu.via?.code)
        assertTrue(kuu.note().contains("BLR→DEL→KUU"))
        // Chandigarh is direct from Bengaluru, with a long-ish bus to Kullu
        val ixc = plans.first { it.to.code == "IXC" }
        assertTrue(ixc.via == null && ixc.transferToKm > 150)
        // Flights show up as an option and the budget can use them
        assertTrue(FareEstimator.options(d("Bengaluru"), kullu).any { it.mode == TransportMode.FLIGHT })
    }

    @Test
    fun `small regional airports cost more than big ones`() {
        val del = Airports["DEL"]!!
        assertTrue(FlightPlanner.segmentFare(del, Airports["KUU"]!!) > FlightPlanner.segmentFare(del, Airports["IXC"]!!))
        // Delhi–Mumbai lands in a realistic ₹4,000–6,500 range
        assertTrue(FlightPlanner.segmentFare(del, Airports["BOM"]!!) in 4_000_00L..6_500_00L)
    }

    @Test
    fun `flight links always use airport codes, never town names`() {
        val date = LocalDate.of(2026, 11, 20)
        val g = BookingLinks.googleFlights(d("Bengaluru"), d("Kullu"), date)
        assertTrue(!g.contains("Kullu") && (g.contains("KUU") || g.contains("IXC")))
        val links = BookingLinks.flightLinks("BLR", "IXC", date).toMap()
        assertEquals("https://www.skyscanner.co.in/transport/flights/blr/ixc/261120/?adultsv2=1&cabinclass=economy", links["Skyscanner"])
        assertTrue(links["ixigo"]!!.contains("date=20112026"))
        assertTrue(links["MakeMyTrip"]!!.contains("itinerary=BLR-IXC-20/11/2026"))
    }

    @Test
    fun `extra towns get rail and hill flags from data`() {
        assertTrue(!d("Kullu").rail && d("Kullu").hills)
        assertTrue(d("Ludhiana").rail)
        assertTrue(!d("Ludhiana").hub)
    }
}

class V09Test {
    private fun d(name: String) = Destinations.find(name)!!

    @Test
    fun `trip searches survive a round trip through storage`() {
        val s = TripSearch(
            from = "Bengaluru", stops = listOf("Kullu" to 3, "Manali" to 4), startDate = LocalDate.of(2026, 11, 20),
            nights = 7, people = 2, style = TravelStyle.COMFORT, pref = TravelPref.AIR, returnHome = true,
            themes = setOf(Theme.MOUNTAINS), pace = Pace.RELAXED, name = "Himachal",
        )
        assertEquals(s, TripSearch.decode(TripSearch.encode(s)))
        assertEquals(listOf(s), TripSearch.decodeList(TripSearch.encodeList(listOf(s))))
        assertEquals(null, TripSearch.decode("garbage"))
        assertEquals("Bengaluru → Kullu, Manali", s.title)
        assertTrue(s.subtitle.startsWith("20 Nov – 27 Nov · 7 nights · 2 travellers"))
    }

    @Test
    fun `recent searches keep the newest, no repeats, max six`() {
        fun search(to: String) = TripSearch("Delhi", listOf(to to 2), LocalDate.of(2026, 12, 1), 2, 1, TravelStyle.BACKPACKER, TravelPref.BEST, true)
        var recent = emptyList<TripSearch>()
        listOf("Agra", "Jaipur", "Agra", "A1", "A2", "A3", "A4", "A5").forEach { recent = TripSearch.remember(recent, search(it)) }
        assertEquals(6, recent.size)
        assertEquals("A5", recent.first().stops.first().first)
        // Agra was searched twice but appears once; Jaipur (oldest) fell off the end
        assertEquals(1, recent.count { it.stops.first().first == "Agra" })
        assertEquals(0, recent.count { it.stops.first().first == "Jaipur" })
    }

    @Test
    fun `nights are shared across stops`() {
        assertEquals(listOf(3, 2, 2), TripSearch.splitNights(7, 3))
        assertEquals(listOf(1, 1), TripSearch.splitNights(1, 2))
        assertEquals(listOf(5), TripSearch.splitNights(5, 1))
    }

    @Test
    fun `train and bus links suit Indian and foreign travellers`() {
        val date = LocalDate.of(2026, 11, 20)
        val indian = BookingLinks.groundLinks(TransportMode.TRAIN_AC, d("Delhi"), d("Varanasi"), date, foreignVisitor = false)
        assertEquals("IRCTC", indian.first().first)
        val visitor = BookingLinks.groundLinks(TransportMode.TRAIN_AC, d("Delhi"), d("Varanasi"), date, foreignVisitor = true)
        assertEquals("12Go", visitor.first().first)
        assertEquals("https://12go.asia/en/travel/new-delhi/varanasi?date=2026-11-20&people=1", visitor.first().second)
        val stays = BookingLinks.bookingCom(d("Kullu"), date, date.plusDays(3), adults = 2, rooms = 1)
        assertTrue(stays.contains("checkin=2026-11-20") && stays.contains("checkout=2026-11-23") && stays.contains("group_adults=2"))
    }
}
