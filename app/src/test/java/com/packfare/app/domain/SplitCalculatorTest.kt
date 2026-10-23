package com.packfare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    @Test
    fun `equal split distributes leftover paise`() {
        // ₹100 between 3 people = 3334 + 3333 + 3333 paise
        val shares = SplitCalculator.equalShares(10_000, listOf(1L, 2L, 3L))
        assertEquals(3334L, shares[1L])
        assertEquals(3333L, shares[2L])
        assertEquals(3333L, shares[3L])
        assertEquals(10_000L, shares.values.sum())
    }

    @Test
    fun `weighted split gives proportional shares that add up`() {
        // ₹1000 split 2:1:1 -> 500, 250, 250
        val even = SplitCalculator.weightedShares(100_000, mapOf(1L to 2, 2L to 1, 3L to 1))
        assertEquals(mapOf(1L to 50_000L, 2L to 25_000L, 3L to 25_000L), even)

        // ₹100 split 1:1:1 needs one leftover paisa
        val odd = SplitCalculator.weightedShares(10_000, mapOf(1L to 1, 2L to 1, 3L to 1))
        assertEquals(10_000L, odd.values.sum())

        // weight 0 means "not part of this expense"
        val skip = SplitCalculator.weightedShares(9_000, mapOf(1L to 1, 2L to 0, 3L to 2))
        assertEquals(mapOf(1L to 3_000L, 3L to 6_000L), skip)
    }

    @Test
    fun `net balances are paid minus owed and sum to zero`() {
        // A pays ₹300 dinner for A, B, C. B pays ₹90 cab for B and C.
        val net = SplitCalculator.netBalances(
            memberIds = listOf(1L, 2L, 3L),
            paid = listOf(1L to 30_000L, 2L to 9_000L),
            owed = listOf(1L to 10_000L, 2L to 10_000L, 3L to 10_000L, 2L to 4_500L, 3L to 4_500L),
        )
        assertEquals(20_000L, net[1L])
        assertEquals(-5_500L, net[2L])
        assertEquals(-14_500L, net[3L])
        assertEquals(0L, net.values.sum())
    }

    @Test
    fun `simplify settles everyone in at most n-1 payments`() {
        val net = mapOf(1L to 20_000L, 2L to -5_500L, 3L to -14_500L)
        val transfers = SplitCalculator.simplify(net)
        assertTrue(transfers.size <= 2)

        val after = net.toMutableMap()
        transfers.forEach { t ->
            after[t.fromId] = after.getValue(t.fromId) + t.amountPaise
            after[t.toId] = after.getValue(t.toId) - t.amountPaise
        }
        assertTrue(after.values.all { it == 0L })
    }

    @Test
    fun `settled group needs no payments`() {
        assertTrue(SplitCalculator.simplify(mapOf(1L to 0L, 2L to 0L)).isEmpty())
    }

    @Test
    fun `money formatting uses Indian lakh grouping`() {
        assertEquals("₹1,23,456.50", Money.format(12_345_650L))
        assertEquals("₹500", Money.format(50_000L))
        assertEquals("₹0.05", Money.format(5L))
        assertEquals("₹1,00,000", Money.format(10_000_000L))
        assertEquals("₹1,00,00,000", Money.format(1_000_000_000L))
        assertEquals("-₹999", Money.format(-99_900L))
    }

    @Test
    fun `money parsing handles rupees, paise and junk`() {
        assertEquals(25_000L, Money.parse("250"))
        assertEquals(25_050L, Money.parse("250.5"))
        assertEquals(120_075L, Money.parse("1,200.75"))
        assertEquals(9_900L, Money.parse("₹99"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertEquals("123.45", Money.toUpiAmount(12_345L))
    }
}
