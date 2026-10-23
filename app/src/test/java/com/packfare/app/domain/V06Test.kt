package com.packfare.app.domain

import com.packfare.app.explore.WikivoyageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V06Test {
    private fun d(name: String) = Destinations.find(name)!!

    @Test
    fun `new towns like Ahmedabad are covered with airports and themes`() {
        val names = listOf("Ahmedabad", "Pune", "Srinagar", "Lucknow", "Indore", "Chandigarh", "Agatti (Lakshadweep)")
        names.forEach { n ->
            val p = Destinations.find(n)
            assertTrue(p != null)
            assertTrue(Airports[p!!.airport] != null)
            assertTrue(Themes.of(p).isNotEmpty())
        }
        assertTrue(Destinations.all.size >= 170)
        assertTrue("Gujarat" in Destinations.regions)
        assertEquals("Ahmedabad", Destinations.search("ahm").first().name)
    }

    @Test
    fun `hotel styles cost more and solo travellers pay the whole room`() {
        val c = d("Ahmedabad").baseline
        val back = c.forStyle(TravelStyle.BACKPACKER).stayPaise
        val budget = c.forStyle(TravelStyle.FLASHPACKER).stayPaise
        val three = c.forStyle(TravelStyle.COMFORT).stayPaise
        val four = c.forStyle(TravelStyle.PREMIUM).stayPaise
        assertTrue(back < budget && budget < three && three < four)
        assertEquals(c.forStyle(TravelStyle.COMFORT, solo = true).stayPaise, three * 2)
        // A 4★ room in a mid-tier city is in the ₹5,000–11,000 range
        assertTrue(c.roomPaise(TravelStyle.PREMIUM) in 5_000_00L..11_000_00L)
    }

    @Test
    fun `sharing a room changes the per person budget`() {
        val stops = listOf(PlanStop(d("Udaipur"), 3))
        val shared = BudgetPlanner.suggest(stops, TravelStyle.COMFORT, people = 1, sharingRoom = true)
        val solo = BudgetPlanner.suggest(stops, TravelStyle.COMFORT, people = 1, sharingRoom = false)
        assertTrue(solo.stayPaise > shared.stayPaise)
    }

    @Test
    fun `flights include airport transfers and skip short hops`() {
        val f = FareEstimator.flightOption(d("Delhi"), d("Kasol"))!!
        assertEquals(TransportMode.FLIGHT, f.mode)
        assertTrue(f.transferPaise > 0)
        // Kasol: fly to Kullu–Bhuntar or (often cheaper) Chandigarh and take the bus
        assertTrue(f.toAirport == "KUU" || f.toAirport == "IXC")
        // Delhi → Agra is too short to fly
        assertTrue(FareEstimator.flightOption(d("Delhi"), d("Agra")) == null)
        // Ahmedabad → Kolkata offers a flight among the options
        assertTrue(FareEstimator.options(d("Ahmedabad"), d("Kolkata")).any { it.mode == TransportMode.FLIGHT })
    }

    @Test
    fun `premium travellers prefer flights on long routes`() {
        val c = FareEstimator.choices(d("Delhi"), d("Kochi"), TravelStyle.PREMIUM)!!
        assertEquals(TransportMode.FLIGHT, c.best.mode)
    }

    @Test
    fun `wikivoyage mid-range and splurge sections give 3 and 4 star prices`() {
        val text = """
            ==Sleep==
            ===Budget===
            * {{sleep | name=Hostel | price=₹600 dorm }}
            ===Mid-range===
            * {{sleep | name=Hotel A | price=₹3,500 }}
            * {{sleep | name=Hotel B | price=₹4,500 }}
            ===Splurge===
            * {{sleep | name=Palace | price=₹9,000 }}
            * {{sleep | name=Resort | price=₹11,000 }}
        """.trimIndent()
        val stats = WikivoyageParser.parse(text)
        assertEquals(4_000_00L, stats.midRoomMedianPaise)
        assertEquals(10_000_00L, stats.splurgeRoomMedianPaise)
        val refined = WikivoyageParser.refine(d("Ahmedabad"), stats)
        assertEquals(4_000_00L, refined.roomPaise(TravelStyle.COMFORT))
        assertEquals(10_000_00L, refined.roomPaise(TravelStyle.PREMIUM))
    }
}

class V07Test {
    private fun d(name: String) = Destinations.find(name)!!

    @Test
    fun `no option anywhere is priced below 3AC`() {
        val pairs = listOf("Delhi" to "Manali", "Delhi" to "Varanasi", "Kochi" to "Alleppey", "Mumbai" to "Goa (North)", "Manali" to "Kasol")
        pairs.forEach { (a, b) ->
            val opts = FareEstimator.options(d(a), d(b))
            assertTrue(opts.isNotEmpty())
            assertTrue(opts.none { it.mode == TransportMode.TRAIN_SLEEPER || it.mode == TransportMode.BUS_STATE })
        }
    }

    @Test
    fun `travel preference picks air, train or bus for every leg`() {
        val stops = listOf(PlanStop(d("Varanasi"), 2), PlanStop(d("Kolkata"), 2))
        val delhi = d("Delhi")
        fun modes(p: TravelPref) =
            BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER, origin = delhi, pref = p).legs.map { it.option.mode }.toSet()
        assertEquals(setOf(TransportMode.FLIGHT), modes(TravelPref.AIR))
        assertEquals(setOf(TransportMode.TRAIN_AC), modes(TravelPref.TRAIN))
        assertEquals(setOf(TransportMode.BUS_AC_SLEEPER), modes(TravelPref.BUS))
        // Kolkata → Delhi is too far for a bus, so a bus fan still gets a sensible way home
        val home = BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER, origin = delhi, returnTo = delhi, pref = TravelPref.BUS)
        assertTrue(home.legs.last().option.mode != TransportMode.BUS_AC_SLEEPER)
        // Flying costs more than the train
        val air = BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER, origin = delhi, returnTo = delhi, pref = TravelPref.AIR)
        val train = BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER, origin = delhi, returnTo = delhi, pref = TravelPref.TRAIN)
        assertTrue(air.intercityPaise > train.intercityPaise)
    }

    @Test
    fun `preference falls back when the route doesn't have that mode`() {
        // No railhead in Manali: train lovers get the best road option
        val c = FareEstimator.choices(d("Delhi"), d("Manali"), pref = TravelPref.TRAIN)!!
        assertTrue(c.best.mode != TransportMode.TRAIN_AC)
        // Air preference adds a flight even for a short hop with airports at both ends
        val s = BudgetPlanner.suggest(listOf(PlanStop(d("Jaipur"), 2)), TravelStyle.BACKPACKER, origin = d("Delhi"), pref = TravelPref.AIR)
        assertEquals(TransportMode.FLIGHT, s.legs.first().option.mode)
    }
}
