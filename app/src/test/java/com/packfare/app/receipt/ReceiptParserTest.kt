package com.packfare.app.receipt

import com.packfare.app.domain.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun `restaurant bill picks grand total, not subtotal`() {
        val text = """
            SHREE KRISHNA DHABA
            NH 48, Near Toll Plaza
            GSTIN: 27AABCU9603R1ZM
            TAX INVOICE
            Bill No: 1045   Date: 12/10/2026
            Paneer Butter Masala   1   280.00
            Butter Naan            4   160.00
            Sweet Lassi            2   120.00
            Sub Total                  560.00
            CGST 2.5%                   14.00
            SGST 2.5%                   14.00
            Grand Total            ₹ 588.00
        """.trimIndent()
        val r = ReceiptParser.parseText(text)
        assertEquals(58_800L, r.totalPaise)
        assertEquals("Shree Krishna Dhaba", r.merchant)
        assertEquals(Category.FOOD, r.category)
    }

    @Test
    fun `label and amount in separate OCR lines on the same row`() {
        val lines = listOf(
            OcrLine("Zostel Manali", 10, 10, 300, 40),
            OcrLine("Dorm bed x 2 nights", 10, 60, 250, 80),
            OcrLine("1,398.00", 400, 60, 480, 80),
            OcrLine("TOTAL", 10, 100, 90, 120),
            OcrLine("Rs. 1,398.00", 380, 102, 480, 122),
        )
        val r = ReceiptParser.parse(lines)
        assertEquals(139_800L, r.totalPaise)
        assertEquals("Zostel Manali", r.merchant)
        assertEquals(Category.STAY, r.category)
    }

    @Test
    fun `fuel receipt without the word total falls back to biggest money value`() {
        val text = """
            INDIAN OIL - SAI FUELS
            Petrol   Rate 102.50
            Volume   19.51 L
            Amount   2000.00
            Mob: 9876543210
        """.trimIndent()
        val r = ReceiptParser.parseText(text)
        assertEquals(200_000L, r.totalPaise)
        assertEquals(Category.TRAVEL, r.category)
    }

    @Test
    fun `phone numbers and gstins are never amounts`() {
        assertEquals(emptyList<Long>(), ReceiptParser.amountsIn("Ph 9876543210"))
        assertEquals(listOf(45_000L), ReceiptParser.amountsIn("Total Rs.450"))
        assertEquals(listOf(12_345_675L), ReceiptParser.amountsIn("Net Amount 1,23,456.75"))
    }

    @Test
    fun `nothing useful gives nulls`() {
        val r = ReceiptParser.parseText("")
        assertNull(r.totalPaise)
        assertNull(r.merchant)
        assertNull(r.category)
    }
}
