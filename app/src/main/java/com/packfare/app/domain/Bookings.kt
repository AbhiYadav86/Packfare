package com.packfare.app.domain

/**
 * One thing to book (a journey or a stay) and whether it is done.
 * [plannedPaise] is what the plan expected to pay for everyone; [actualPaise] is what was really paid
 * once booked (null = not entered).
 */
data class BookingItem(
    val key: String,
    val label: String,
    val done: Boolean,
    val plannedPaise: Long = 0,
    val actualPaise: Long? = null,
) {
    /** What counts against the budget: the real price once known, otherwise the plan. */
    val costPaise: Long get() = if (done) (actualPaise ?: plannedPaise) else plannedPaise

    companion object {
        /** One line per item: key, label, 0/1, planned, actual (empty if none), separated by tabs. */
        fun encode(items: List<BookingItem>): String =
            items.joinToString("\n") {
                listOf(
                    it.key,
                    it.label.replace('\t', ' ').replace('\n', ' '),
                    if (it.done) "1" else "0",
                    it.plannedPaise.toString(),
                    it.actualPaise?.toString().orEmpty(),
                ).joinToString("\t")
            }

        fun decode(text: String?): List<BookingItem> =
            text.orEmpty().lines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 3 || p[0].isBlank()) null
                else BookingItem(
                    key = p[0],
                    label = p[1],
                    done = p[2] == "1",
                    plannedPaise = p.getOrNull(3)?.toLongOrNull() ?: 0L,
                    actualPaise = p.getOrNull(4)?.toLongOrNull(),
                )
            }

        fun toggle(items: List<BookingItem>, key: String): List<BookingItem> =
            items.map { if (it.key == key) it.copy(done = !it.done) else it }

        /** Mark [key] booked (with the price paid, if known) or not booked. */
        fun setBooked(items: List<BookingItem>, key: String, done: Boolean, actualPaise: Long? = null): List<BookingItem> =
            items.map { if (it.key == key) it.copy(done = done, actualPaise = if (done) actualPaise else null) else it }
    }
}

/** Where the budget stands once some things are booked. All in paise for the whole group. */
data class BudgetAfterBookings(
    val budgetPaise: Long,
    val bookedPaise: Long,
    val stillToBookPaise: Long,
    val spentPaise: Long,
) {
    /** Left for food, rides, activities and surprises once bookings and spending are covered. */
    val leftPaise: Long get() = budgetPaise - bookedPaise - stillToBookPaise - spentPaise

    companion object {
        fun compute(budgetPaise: Long, spentPaise: Long, items: List<BookingItem>) = BudgetAfterBookings(
            budgetPaise = budgetPaise,
            bookedPaise = items.filter { it.done }.sumOf { it.costPaise },
            stillToBookPaise = items.filter { !it.done }.sumOf { it.plannedPaise },
            spentPaise = spentPaise,
        )
    }
}
