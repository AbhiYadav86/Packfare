package com.packfare.app.data

import com.packfare.app.domain.Category
import com.packfare.app.domain.PaymentMethod
import com.packfare.app.receipt.ReceiptStorage

/**
 * Single entry point for the UI. Today it only talks to Room; in a later phase this is
 * where sync with PocketBase plugs in, without the screens having to change.
 */
class TripRepository(
    private val dao: TripDao,
    private val receipts: ReceiptStorage,
) {

    fun tripSummaries() = dao.tripSummaries()
    fun trip(tripId: Long) = dao.trip(tripId)
    fun members(tripId: Long) = dao.members(tripId)
    fun expenses(tripId: Long) = dao.expenses(tripId)
    fun shares(tripId: Long) = dao.shares(tripId)
    fun legs(tripId: Long) = dao.legs(tripId)
    fun cashTopUps(tripId: Long) = dao.cashTopUps(tripId)
    fun activities(tripId: Long) = dao.activities(tripId)

    suspend fun addActivity(a: ActivityEntity): Long = dao.insertActivity(a)
    suspend fun updateActivity(a: ActivityEntity) = dao.updateActivity(a)
    suspend fun deleteActivity(id: Long) = dao.deleteActivity(id)

    suspend fun createTrip(name: String, destination: String, budgetPaise: Long): Long =
        dao.insertTrip(TripEntity(name = name.trim(), destination = destination.trim(), budgetPaise = budgetPaise))

    /** From the planner: trip + "me" + the suggested route, in one transaction. */
    suspend fun createPlannedTrip(trip: TripEntity, myName: String, legs: List<LegEntity>): Long =
        dao.createPlannedTrip(trip.copy(name = trip.name.trim()), myName.trim().ifBlank { "Me" }, legs)

    /** An event (conference, client visit...) with just the organiser; bills and claims work as for trips. */
    suspend fun createEvent(name: String, company: String, place: String, myName: String): Long =
        dao.createPlannedTrip(
            TripEntity(
                name = name.trim(),
                destination = place.trim(),
                budgetPaise = 0,
                startEpochDay = java.time.LocalDate.now().toEpochDay(),
                kind = TripEntity.KIND_EVENT,
                company = company.trim().ifBlank { null },
            ),
            myName.trim().ifBlank { "Me" },
            emptyList(),
        )

    suspend fun updateTrip(trip: TripEntity) = dao.updateTrip(trip)

    suspend fun tripOnce(tripId: Long) = dao.tripOnce(tripId)

    // ---- Route ----

    suspend fun addLeg(leg: LegEntity): Long =
        dao.insertLeg(leg.copy(position = dao.maxLegPosition(leg.tripId) + 1))

    suspend fun updateLeg(leg: LegEntity) = dao.updateLeg(leg)

    suspend fun deleteLeg(legId: Long) = dao.deleteLeg(legId)

    // ---- Cash ----

    suspend fun addCash(tripId: Long, amountPaise: Long, note: String?) =
        dao.insertCashTopUp(CashTopUpEntity(tripId = tripId, amountPaise = amountPaise, note = note?.ifBlank { null }))

    suspend fun deleteCash(id: Long) = dao.deleteCashTopUp(id)

    suspend fun deleteTrip(tripId: Long) {
        val photos = dao.receiptPathsForTrip(tripId)
        dao.deleteTrip(tripId)
        photos.forEach { receipts.delete(it) }
    }

    // ---- People ----

    suspend fun addMember(tripId: Long, name: String, upiId: String?): Long =
        dao.insertMember(
            MemberEntity(tripId = tripId, name = name.trim(), upiId = upiId?.trim()?.ifBlank { null }),
        )

    suspend fun updateMember(member: MemberEntity, name: String, upiId: String?) =
        dao.updateMember(member.copy(name = name.trim(), upiId = upiId?.trim()?.ifBlank { null }))

    /**
     * Removes a person only if they aren't part of any expense (otherwise balances would break).
     * @return true if removed, false if they still have expenses.
     */
    suspend fun removeMemberIfUnused(memberId: Long): Boolean {
        if (dao.memberUsageCount(memberId) > 0) return false
        dao.deleteMember(memberId)
        return true
    }

    // ---- Expenses ----

    /** An expense plus its shares, for the edit screen. */
    suspend fun expenseWithShares(expenseId: Long): Pair<ExpenseEntity, Map<Long, Long>>? {
        val expense = dao.expense(expenseId) ?: return null
        return expense to dao.sharesForExpense(expenseId).associate { it.memberId to it.sharePaise }
    }

    /**
     * Creates a new expense ([existingId] == null) or replaces an existing one.
     * [sharesByMember] must add up to [amountPaise]; the add-expense screen guarantees this.
     */
    suspend fun saveExpense(
        existingId: Long?,
        tripId: Long,
        title: String,
        amountPaise: Long,
        paidById: Long,
        category: Category,
        sharesByMember: Map<Long, Long>,
        receiptPath: String?,
        paymentMethod: PaymentMethod = PaymentMethod.UPI,
        legId: Long? = null,
    ): Long {
        require(sharesByMember.values.sum() == amountPaise) { "Shares must add up to the amount" }
        if (existingId == null) {
            return dao.insertExpense(
                ExpenseEntity(
                    tripId = tripId,
                    title = title.trim(),
                    amountPaise = amountPaise,
                    paidById = paidById,
                    category = category.name,
                    receiptPath = receiptPath,
                    paymentMethod = paymentMethod.name,
                    legId = legId,
                ),
                sharesByMember,
            )
        }
        val old = dao.expense(existingId) ?: error("Expense $existingId not found")
        dao.updateExpense(
            old.copy(
                title = title.trim(),
                amountPaise = amountPaise,
                paidById = paidById,
                category = category.name,
                receiptPath = receiptPath,
                paymentMethod = paymentMethod.name,
                legId = legId,
            ),
            sharesByMember,
        )
        if (old.receiptPath != null && old.receiptPath != receiptPath) receipts.delete(old.receiptPath)
        return existingId
    }

    /** Records "[fromId] paid [toId] back", which zeroes out that debt. */
    suspend fun recordSettlement(tripId: Long, fromId: Long, toId: Long, amountPaise: Long): Long =
        dao.insertExpense(
            ExpenseEntity(
                tripId = tripId,
                title = "Settlement",
                amountPaise = amountPaise,
                paidById = fromId,
                category = Category.OTHER.name,
                isSettlement = true,
            ),
            mapOf(toId to amountPaise),
        )

    suspend fun deleteExpense(expenseId: Long) {
        val path = dao.expense(expenseId)?.receiptPath
        dao.deleteExpense(expenseId)
        path?.let { receipts.delete(it) }
    }
}
