package com.packfare.app.domain

/** One bill on a reimbursement claim. Pure data so the totals can be unit tested. */
data class ClaimLine(
    val epochDay: Long,
    val vendor: String,
    val category: Category,
    val paymentMethod: PaymentMethod,
    val amountPaise: Long,
    val hasReceipt: Boolean,
)

/** Totals for an expense claim: grand total, per category and per payment method, oldest bill first. */
data class ClaimSummary(
    val lines: List<ClaimLine>,
    val totalPaise: Long,
    val byCategory: List<Pair<Category, Long>>,
    val byMethod: List<Pair<PaymentMethod, Long>>,
    val withoutReceipt: Int,
) {
    companion object {
        fun build(input: List<ClaimLine>): ClaimSummary {
            val lines = input.sortedWith(compareBy({ it.epochDay }, { it.vendor.lowercase() }))
            return ClaimSummary(
                lines = lines,
                totalPaise = lines.sumOf { it.amountPaise },
                byCategory = lines.groupBy { it.category }
                    .map { (c, l) -> c to l.sumOf { it.amountPaise } }
                    .sortedByDescending { it.second },
                byMethod = lines.groupBy { it.paymentMethod }
                    .map { (m, l) -> m to l.sumOf { it.amountPaise } }
                    .sortedByDescending { it.second },
                withoutReceipt = lines.count { !it.hasReceipt },
            )
        }

        /** "PF-12-20261025": stable, readable claim reference. */
        fun reference(tripId: Long, epochDay: Long): String =
            "PF-$tripId-" + java.time.LocalDate.ofEpochDay(epochDay).toString().replace("-", "")
    }
}
