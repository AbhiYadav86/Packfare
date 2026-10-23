package com.packfare.app.domain

/** One suggested payment: [fromId] pays [toId] the given amount. */
data class Transfer(val fromId: Long, val toId: Long, val amountPaise: Long)

/** Pure Kotlin, no Android dependencies, so it is easy to unit test. */
object SplitCalculator {

    /**
     * Splits [totalPaise] equally between [memberIds]. Leftover paise (when the total
     * doesn't divide evenly) go one each to the first members, so shares always add up exactly.
     */
    fun equalShares(totalPaise: Long, memberIds: List<Long>): Map<Long, Long> {
        require(memberIds.isNotEmpty()) { "Need at least one member to split between" }
        require(totalPaise >= 0) { "Amount can't be negative" }
        val base = totalPaise / memberIds.size
        val remainder = (totalPaise % memberIds.size).toInt()
        return memberIds.mapIndexed { index, id ->
            id to base + if (index < remainder) 1 else 0
        }.toMap()
    }

    /**
     * Splits [totalPaise] by weights, e.g. {A:2, B:1, C:1} gives A half and B, C a quarter each.
     * Members with weight 0 are left out. Rounding uses the largest-remainder method so the
     * shares always add up exactly to the total.
     */
    fun weightedShares(totalPaise: Long, weights: Map<Long, Int>): Map<Long, Long> {
        val active = weights.filter { it.value > 0 }
        require(active.isNotEmpty()) { "Need at least one person with a share" }
        require(totalPaise >= 0) { "Amount can't be negative" }
        val totalWeight = active.values.sum().toLong()

        val base = active.mapValues { (_, w) -> totalPaise * w / totalWeight }.toMutableMap()
        var leftover = totalPaise - base.values.sum()
        // Hand out leftover paise to the largest fractional remainders first (ties: map order).
        val byRemainder = active.entries.sortedByDescending { (_, w) -> (totalPaise * w) % totalWeight }
        for ((id, _) in byRemainder) {
            if (leftover == 0L) break
            base[id] = base.getValue(id) + 1
            leftover--
        }
        return base
    }

    /**
     * Net balance per member: what they paid minus what they owe.
     * Positive = the group owes them. Negative = they owe the group.
     *
     * @param paid list of (payerId, amountPaise), one per expense
     * @param owed list of (memberId, sharePaise), one per expense share
     */
    fun netBalances(
        memberIds: Collection<Long>,
        paid: List<Pair<Long, Long>>,
        owed: List<Pair<Long, Long>>,
    ): Map<Long, Long> {
        val net = memberIds.associateWith { 0L }.toMutableMap()
        paid.forEach { (id, amount) -> net[id] = (net[id] ?: 0L) + amount }
        owed.forEach { (id, share) -> net[id] = (net[id] ?: 0L) - share }
        return net
    }

    /**
     * Turns net balances into a short list of payments that settles everyone.
     * Greedy: the biggest debtor pays the biggest creditor, repeat. This needs at most
     * (people - 1) payments and in practice is close to the minimum.
     */
    fun simplify(net: Map<Long, Long>): List<Transfer> {
        val creditors = net.filter { it.value > 0 }
            .map { it.key to it.value }
            .sortedByDescending { it.second }
            .toMutableList()
        val debtors = net.filter { it.value < 0 }
            .map { it.key to -it.value }
            .sortedByDescending { it.second }
            .toMutableList()

        val transfers = mutableListOf<Transfer>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val (debtorId, owes) = debtors[i]
            val (creditorId, due) = creditors[j]
            val amount = minOf(owes, due)
            if (amount > 0) transfers += Transfer(debtorId, creditorId, amount)
            debtors[i] = debtorId to owes - amount
            creditors[j] = creditorId to due - amount
            if (debtors[i].second == 0L) i++
            if (creditors[j].second == 0L) j++
        }
        return transfers
    }
}
