package com.packfare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaimTest {
    private fun line(day: Long, v: String, c: Category, p: Long, r: Boolean = true) =
        ClaimLine(day, v, c, PaymentMethod.UPI, p, r)

    @Test
    fun totalsAndCategoriesAddUp() {
        val s = ClaimSummary.build(
            listOf(
                line(20, "Taxi", Category.TRAVEL, 50_000),
                line(19, "Hotel", Category.STAY, 400_000),
                line(19, "Lunch", Category.FOOD, 30_000, r = false),
                line(21, "Dinner", Category.FOOD, 70_000),
            ),
        )
        assertEquals(550_000, s.totalPaise)
        assertEquals(listOf(19L, 19L, 20L, 21L), s.lines.map { it.epochDay })
        assertEquals(Category.STAY, s.byCategory.first().first)
        assertEquals(100_000, s.byCategory.first { it.first == Category.FOOD }.second)
        assertEquals(1, s.withoutReceipt)
        assertEquals(s.totalPaise, s.byCategory.sumOf { it.second })
    }

    @Test
    fun referenceFormat() {
        assertEquals("PF-7-19700102", ClaimSummary.reference(7, 1))
    }
}
