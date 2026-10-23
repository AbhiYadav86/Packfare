package com.packfare.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TripDao {

    // ---- Trips ----

    @Query(
        """
        SELECT t.*,
            COALESCE((SELECT SUM(e.amountPaise) FROM expenses e
                      WHERE e.tripId = t.id AND e.isSettlement = 0), 0) AS spentPaise,
            (SELECT COUNT(*) FROM members m WHERE m.tripId = t.id) AS memberCount
        FROM trips t
        ORDER BY t.createdAt DESC
        """,
    )
    abstract fun tripSummaries(): Flow<List<TripSummary>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    abstract fun trip(tripId: Long): Flow<TripEntity?>

    @Insert
    abstract suspend fun insertTrip(trip: TripEntity): Long

    @Update
    abstract suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :tripId")
    abstract suspend fun tripOnce(tripId: Long): TripEntity?

    /** Creates a trip, its "me" member and its planned route in one go. Returns the trip id. */
    @Transaction
    open suspend fun createPlannedTrip(trip: TripEntity, myName: String, legs: List<LegEntity>): Long {
        val tripId = insertTrip(trip)
        val meId = insertMember(MemberEntity(tripId = tripId, name = myName))
        updateTrip(trip.copy(id = tripId, ownerMemberId = meId))
        if (legs.isNotEmpty()) insertLegs(legs.map { it.copy(id = 0, tripId = tripId) })
        return tripId
    }

    @Query("DELETE FROM trips WHERE id = :tripId")
    abstract suspend fun deleteTrip(tripId: Long)

    /** Receipt photos of a trip, so their files can be removed when the trip is deleted. */
    @Query("SELECT receiptPath FROM expenses WHERE tripId = :tripId AND receiptPath IS NOT NULL")
    abstract suspend fun receiptPathsForTrip(tripId: Long): List<String>

    // ---- Members ----

    @Query("SELECT * FROM members WHERE tripId = :tripId ORDER BY id")
    abstract fun members(tripId: Long): Flow<List<MemberEntity>>

    @Insert
    abstract suspend fun insertMember(member: MemberEntity): Long

    @Update
    abstract suspend fun updateMember(member: MemberEntity)

    @Query("DELETE FROM members WHERE id = :memberId")
    abstract suspend fun deleteMember(memberId: Long)

    /** How many expenses or shares involve this person. 0 means they can be removed safely. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM expenses WHERE paidById = :memberId)
             + (SELECT COUNT(*) FROM expense_shares WHERE memberId = :memberId)
        """,
    )
    abstract suspend fun memberUsageCount(memberId: Long): Int

    // ---- Route legs ----

    @Query("SELECT * FROM legs WHERE tripId = :tripId ORDER BY position, id")
    abstract fun legs(tripId: Long): Flow<List<LegEntity>>

    @Insert
    abstract suspend fun insertLeg(leg: LegEntity): Long

    @Insert
    abstract suspend fun insertLegs(legs: List<LegEntity>)

    @Update
    abstract suspend fun updateLeg(leg: LegEntity)

    @Query("DELETE FROM legs WHERE id = :legId")
    abstract suspend fun deleteLeg(legId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM legs WHERE tripId = :tripId")
    abstract suspend fun maxLegPosition(tripId: Long): Int

    // ---- Cash wallet ----

    @Query("SELECT * FROM cash_topups WHERE tripId = :tripId ORDER BY createdAt DESC")
    abstract fun cashTopUps(tripId: Long): Flow<List<CashTopUpEntity>>

    @Insert
    abstract suspend fun insertCashTopUp(topUp: CashTopUpEntity): Long

    @Query("DELETE FROM cash_topups WHERE id = :id")
    abstract suspend fun deleteCashTopUp(id: Long)

    // ---- Expenses ----

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY createdAt DESC")
    abstract fun expenses(tripId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    abstract suspend fun expense(expenseId: Long): ExpenseEntity?

    @Query(
        """
        SELECT s.* FROM expense_shares s
        INNER JOIN expenses e ON e.id = s.expenseId
        WHERE e.tripId = :tripId
        """,
    )
    abstract fun shares(tripId: Long): Flow<List<ExpenseShareEntity>>

    @Query("SELECT * FROM expense_shares WHERE expenseId = :expenseId")
    abstract suspend fun sharesForExpense(expenseId: Long): List<ExpenseShareEntity>

    @Insert
    abstract suspend fun insertExpenseRow(expense: ExpenseEntity): Long

    @Update
    abstract suspend fun updateExpenseRow(expense: ExpenseEntity)

    @Insert
    abstract suspend fun insertShares(shares: List<ExpenseShareEntity>)

    @Query("DELETE FROM expense_shares WHERE expenseId = :expenseId")
    abstract suspend fun deleteSharesForExpense(expenseId: Long)

    /** Saves an expense and its per-person shares atomically. */
    @Transaction
    open suspend fun insertExpense(expense: ExpenseEntity, sharesByMember: Map<Long, Long>): Long {
        val id = insertExpenseRow(expense)
        insertShares(sharesByMember.map { (memberId, share) -> ExpenseShareEntity(id, memberId, share) })
        return id
    }

    /** Replaces an existing expense and all of its shares atomically. */
    @Transaction
    open suspend fun updateExpense(expense: ExpenseEntity, sharesByMember: Map<Long, Long>) {
        updateExpenseRow(expense)
        deleteSharesForExpense(expense.id)
        insertShares(sharesByMember.map { (memberId, share) -> ExpenseShareEntity(expense.id, memberId, share) })
    }

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    abstract suspend fun deleteExpense(expenseId: Long)
}
