package com.packfare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class PlannerTest {

    private fun d(name: String) = Destinations.find(name)!!

    @Test
    fun `delhi to manali by road is overnight and best pick saves a night`() {
        val choices = FareEstimator.choices(d("Delhi"), d("Manali"))!!
        // Manali has no railhead, so no trains offered.
        assertTrue(choices.all.none { it.mode == TransportMode.TRAIN_AC })
        assertTrue(choices.cheapest.overnight)
        // Distance should be in a sane range (real road ≈ 530 km).
        assertTrue(choices.cheapest.distanceKm in 400..800)
        assertEquals(TransportMode.BUS_AC_SLEEPER, choices.cheapest.mode)
    }

    @Test
    fun `3AC between two rail towns is cheaper than the bus and nothing is priced below 3AC`() {
        val choices = FareEstimator.choices(d("Delhi"), d("Varanasi"))!!
        assertEquals(TransportMode.TRAIN_AC, choices.cheapest.mode)
        assertTrue(choices.all.all { it.mode.offered })
    }

    @Test
    fun `islands need flights from the mainland and ferries between islands`() {
        val toIsland = FareEstimator.options(d("Chennai"), d("Port Blair"))
        assertEquals(listOf(TransportMode.FLIGHT), toIsland.map { it.mode })
        val hop = FareEstimator.options(d("Port Blair"), d("Havelock"))
        assertEquals(listOf(TransportMode.FERRY), hop.map { it.mode })
    }

    @Test
    fun `suggested budget adds up and backpacker is cheaper than flashpacker`() {
        val stops = listOf(PlanStop(d("Delhi"), 2), PlanStop(d("Manali"), 4), PlanStop(d("Kasol"), 3))
        val back = BudgetPlanner.suggest(stops, TravelStyle.BACKPACKER, people = 2)
        assertEquals(9, back.days)
        assertEquals(2, back.legs.size)
        assertEquals(back.perPersonPaise * 2, back.groupTotalPaise)
        val parts = back.stayPaise + back.foodPaise + back.localPaise + back.activitiesPaise +
            back.intercityPaise + back.bufferPaise - back.overnightSavingsPaise
        assertEquals(parts, back.perPersonPaise)
        // Backpacker in North India: roughly ₹1,200–2,500 a day all-in.
        assertTrue(back.perPersonPerDayPaise in 1_200_00L..2_500_00L)

        val posh = BudgetPlanner.suggest(stops, TravelStyle.FLASHPACKER, people = 2)
        assertTrue(back.perPersonPaise < posh.perPersonPaise)
    }

    @Test
    fun `reverse planner turns 30k into a few weeks in Himachal`() {
        val r = BudgetPlanner.reverse(30_000_00L, Destinations.inRegion("Himachal"), TravelStyle.BACKPACKER)!!
        assertTrue("got ${r.days} days", r.days in 14..25)
        assertEquals(4_500_00L, r.reservedForTravelPaise)
        assertTrue(r.leftoverPaise in 0 until r.perDayPaise)
        assertNull(BudgetPlanner.reverse(0, Destinations.inRegion("Himachal"), TravelStyle.BACKPACKER))
    }

    @Test
    fun `runway tracks pace and remaining allowance`() {
        val start = LocalDate.of(2026, 10, 1)
        // ₹21,000 for 14 days = ₹1,500/day. Day 5, spent ₹9,000 -> ₹1,800/day avg, over pace.
        val r = Runway.compute(start, 14, 21_000_00L, 9_000_00L, 1_000_00L, LocalDate.of(2026, 10, 5))
        assertEquals(5, r.dayNumber)
        assertEquals(1_500_00L, r.dailyTargetPaise)
        assertEquals(1_800_00L, r.avgPerDayPaise)
        assertEquals(PaceStatus.OVER, r.status)
        assertEquals(12_000_00L, r.remainingPaise)
        assertEquals(1_200_00L, r.allowancePerDayPaise) // 10 days incl. today
        assertEquals(6, r.runwayDays)

        val early = Runway.compute(start, 14, 21_000_00L, 0, 0, LocalDate.of(2026, 9, 28))
        assertEquals(PaceStatus.NOT_STARTED, early.status)
        assertEquals(3, early.daysUntilStart)
        assertNull(early.runwayDays)
    }

    @Test
    fun `cash wallet warns before a no-ATM stretch`() {
        val ok = CashWallet.report(10_000_00L, 2_000_00L, 1_500_00L, null)
        assertEquals(CashAlert.NONE, ok.alert)
        assertEquals(8_000_00L, ok.cashOnHandPaise)

        val low = CashWallet.report(3_000_00L, 1_000_00L, 1_500_00L, null)
        assertEquals(CashAlert.LOW, low.alert)

        val spiti = CashWallet.report(5_000_00L, 0, 1_500_00L, "Kaza (Spiti)" to 4)
        assertEquals(CashAlert.CARRY_FOR_REMOTE, spiti.alert)
        assertEquals(6_000_00L, spiti.suggestedCashPaise)
    }

    @Test
    fun `south and east hubs are in the table with airports and sane coordinates`() {
        listOf("Ooty", "Kodaikanal", "Puri", "Kaziranga", "Gangtok", "Hampi", "Majuli", "Pondicherry").forEach { n ->
            val d = Destinations.find(n)
            assertTrue("missing $n", d != null)
            // Everything is inside India's bounding box.
            assertTrue("$n lat", d!!.lat in 6.0..36.0)
            assertTrue("$n lon", d.lon in 68.0..98.0)
        }
        assertEquals("CJB", Destinations.find("Ooty")!!.airport)
        // Nearest hub to Coimbatore airport should be in the Nilgiris.
        val near = Destinations.hubs.sortedBy { FareEstimator.haversineKm(11.03, 77.04, it.lat, it.lon) }.take(3).map { it.name }
        assertTrue(near.toString(), near.any { it == "Ooty" || it == "Coonoor" || it == "Valparai" })
        // All names unique.
        assertEquals(Destinations.all.size, Destinations.all.map { it.name }.toSet().size)
    }
}
