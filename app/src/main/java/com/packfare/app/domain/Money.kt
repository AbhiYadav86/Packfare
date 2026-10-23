package com.packfare.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All money in the app is stored as paise (Long) to avoid floating-point errors.
 * ₹1 = 100 paise.
 */
object Money {
    /**
     * 12345650 -> "₹1,23,456.50"; whole rupees drop the decimals: 50000 -> "₹500".
     * Indian grouping (lakh/crore) is done by hand so it's identical on every device.
     */
    fun format(paise: Long): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        val rupees = (abs / 100).toString()
        val fraction = abs % 100
        val grouped = if (rupees.length <= 3) {
            rupees
        } else {
            val last3 = rupees.takeLast(3)
            val rest = rupees.dropLast(3)
            rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
        }
        val decimals = if (fraction == 0L) "" else "." + fraction.toString().padStart(2, '0')
        return (if (negative) "-₹" else "₹") + grouped + decimals
    }

    /** "250", "250.5", "1,200.75" -> paise. Returns null for blank/invalid/negative input. */
    fun parse(input: String): Long? {
        val cleaned = input.trim().replace(",", "").removePrefix("₹").trim()
        if (cleaned.isEmpty()) return null
        return try {
            val value = BigDecimal(cleaned)
            if (value.signum() < 0) null
            else value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Paise -> "123.45" as UPI apps expect in the `am` parameter. */
    fun toUpiAmount(paise: Long): String =
        BigDecimal.valueOf(paise, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
}
