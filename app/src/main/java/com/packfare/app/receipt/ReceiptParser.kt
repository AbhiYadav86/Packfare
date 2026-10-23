package com.packfare.app.receipt

import com.packfare.app.domain.Category
import com.packfare.app.domain.Money

/** One line of OCR text with its position on the photo (pixels). Position helps pair "TOTAL" with its amount. */
data class OcrLine(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

data class ParsedReceipt(
    val merchant: String?,
    val totalPaise: Long?,
    val category: Category?,
)

/**
 * Rule-based parser for Indian bills (restaurants, fuel, shops, hotels).
 * Pure Kotlin so it can be unit tested with plain strings.
 *
 * It's deliberately simple: good at the grand total, decent at the merchant name,
 * rough at the category. The user always confirms before saving.
 */
object ReceiptParser {

    // Strongest signals first. Matched against the lowercased line.
    private val totalTier1 = listOf(
        "grand total", "net payable", "amount payable", "total payable", "net amount",
        "bill amount", "total amount", "amount due", "total due", "net total", "to pay", "payable",
    )
    private val totalTier2 = listOf("total")
    private val notTheTotal = listOf(
        "sub total", "subtotal", "sub-total", "total qty", "total quantity", "total item", "total items",
        "total tax", "total gst", "total discount", "total savings", "you saved", "round off", "roundoff",
    )
    private val junkLineWords = listOf(
        "gstin", "gst no", "fssai", "phone", "ph:", "ph.", "mob", "tel", "date", "time", "invoice no", "bill no",
        "table", "cashier", "order no", "token", "pin", "card", "upi ref", "txn", "ref no", "hsn",
    )
    private val notMerchant = listOf(
        "tax invoice", "invoice", "bill of supply", "cash memo", "receipt", "welcome", "duplicate",
        "original", "gstin", "fssai", "customer copy", "estimate", "kot",
    )

    // Amount like 450, 450.5, 1,250.00, 1,23,456.75 with an optional ₹ / Rs / INR in front.
    private val amountRegex =
        Regex("""(?:₹|rs\.?|inr)?\s*(\d{1,3}(?:,\d{2,3})+|\d+)(\.\d{1,2})?(?!\d)""", RegexOption.IGNORE_CASE)

    private const val MAX_REASONABLE_PAISE = 10_00_000_00L // ₹10 lakh

    fun parse(lines: List<OcrLine>): ParsedReceipt {
        val ordered = lines.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.top }, { it.left }))
        return ParsedReceipt(
            merchant = findMerchant(ordered),
            totalPaise = findTotal(ordered),
            category = guessCategory(ordered.joinToString("\n") { it.text }),
        )
    }

    /** Convenience for plain text (tests, or OCR without positions). */
    fun parseText(text: String): ParsedReceipt =
        parse(text.lines().mapIndexed { i, line -> OcrLine(line, 0, i * 10, 100, i * 10 + 8) })

    // ---------- Total ----------

    internal fun amountsIn(text: String): List<Long> =
        amountRegex.findAll(text).mapNotNull { m ->
            val intPart = m.groupValues[1]
            // Skip long digit runs: phone numbers, GSTINs, bill numbers.
            if (intPart.replace(",", "").length > 7) return@mapNotNull null
            Money.parse(intPart + m.groupValues[2])
        }.filter { it in 1..MAX_REASONABLE_PAISE }.toList()

    private fun findTotal(lines: List<OcrLine>): Long? {
        fun candidatesFor(keywords: List<String>): List<Long> =
            lines.filter { line ->
                val lower = line.text.lowercase()
                keywords.any { it in lower } && notTheTotal.none { it in lower }
            }.mapNotNull { amountOnSameRow(it, lines) }

        candidatesFor(totalTier1).maxOrNull()?.let { return it }
        candidatesFor(totalTier2).maxOrNull()?.let { return it }

        // Fallback: the biggest "money-looking" number (has decimals or ₹/Rs) on a non-junk line.
        return lines
            .filter { line -> !isJunkLine(line.text) }
            .flatMap { line ->
                val looksLikeMoney = line.text.contains('.') || line.text.contains('₹') ||
                    line.text.contains("rs", ignoreCase = true)
                if (looksLikeMoney) amountsIn(line.text) else emptyList()
            }
            .maxOrNull()
    }

    /**
     * The amount printed on the same row as a "TOTAL" label. Receipts are two columns, and OCR
     * often returns the label and the number as separate lines, so we also look to the right.
     */
    private fun amountOnSameRow(label: OcrLine, all: List<OcrLine>): Long? {
        amountsIn(label.text).lastOrNull()?.let { return it }
        val sameRow = all.filter { other ->
            other !== label &&
                other.left >= label.left &&
                kotlin.math.abs(other.centerY - label.centerY) <= label.height
        }.sortedBy { it.left }
        sameRow.forEach { other -> amountsIn(other.text).lastOrNull()?.let { return it } }
        // Last resort: the next line down (label and value stacked).
        val next = all.firstOrNull { it.top > label.top && it !== label }
        return next?.let { amountsIn(it.text).lastOrNull() }
    }

    // ---------- Merchant ----------

    private fun findMerchant(lines: List<OcrLine>): String? =
        lines.take(6).map { it.text.trim() }.firstOrNull { text ->
            val lower = text.lowercase()
            val letters = text.count { it.isLetter() }
            val digits = text.count { it.isDigit() }
            letters >= 3 && digits <= letters / 2 &&
                notMerchant.none { it in lower } && !isJunkLine(text) &&
                !lower.contains("www") && !lower.contains(".com") && !lower.contains("@")
        }?.let(::tidyName)

    /** "HOTEL SAI PALACE" -> "Hotel Sai Palace"; mixed-case names are kept as printed. */
    private fun tidyName(raw: String): String {
        val name = raw.trim(' ', '*', '-', '=', '.', ':').replace(Regex("\\s+"), " ")
        val isShouting = name.filter { it.isLetter() }.all { it.isUpperCase() }
        val tidy = if (isShouting) {
            name.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        } else {
            name
        }
        return tidy.take(40)
    }

    // ---------- Category ----------

    private val categoryWords: Map<Category, List<String>> = mapOf(
        Category.STAY to listOf(
            "resort", "hostel", "homestay", "home stay", "lodge", "guest house", "guesthouse", "oyo", "zostel",
            "room rent", "room charges", "room tariff", "check in", "check-in", "checkout", "inn", "stay",
        ),
        Category.TRAVEL to listOf(
            "irctc", "railway", "railways", "fuel", "petrol", "diesel", "indian oil", "hpcl", "bpcl", "uber", "ola",
            "rapido", "taxi", "cab", "bus", "travels", "toll", "fastag", "airline", "airlines", "parking", "auto",
        ),
        Category.FOOD to listOf(
            "restaurant", "cafe", "café", "dhaba", "food", "foods", "kitchen", "biryani", "sweets", "bakery", "chai",
            "tea", "coffee", "swiggy", "zomato", "thali", "dine", "bar", "veg", "paneer", "roti", "naan", "dosa",
            "idli", "lassi", "juice", "pizza", "burger", "bhojanalaya", "mess", "canteen",
        ),
        Category.ACTIVITIES to listOf(
            "ticket", "tickets", "entry", "entrance", "museum", "park", "boating", "rafting", "paragliding", "adventure",
            "tour", "tours", "safari", "fort", "zoo", "cinema", "show", "trek", "trekking", "scuba",
        ),
        Category.SHOPPING to listOf(
            "mart", "store", "stores", "supermarket", "bazaar", "bazar", "emporium", "dmart", "reliance", "garments",
            "textiles", "handicrafts", "fashion", "boutique", "general store", "kirana",
        ),
    )

    private val wordRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    /** Whole-word check, so "hotel" doesn't count as a "tel" (phone) line. */
    private fun isJunkLine(text: String): Boolean {
        val lower = text.lowercase()
        return junkLineWords.any { wordRegex(it).containsMatchIn(lower) }
    }

    private fun wordRegex(word: String): Regex =
        wordRegexCache.getOrPut(word) { Regex("""(?<![a-z])${Regex.escape(word)}(?![a-z])""") }

    /** Counts whole-word keyword hits per category; the most hits wins. Null if nothing matched. */
    fun guessCategory(text: String): Category? {
        val lower = text.lowercase()
        val scores = categoryWords.mapValues { (_, words) ->
            words.sumOf { w -> wordRegex(w).findAll(lower).count() }
        }
        val best = scores.maxByOrNull { it.value } ?: return null
        return if (best.value > 0) best.key else null
    }
}
