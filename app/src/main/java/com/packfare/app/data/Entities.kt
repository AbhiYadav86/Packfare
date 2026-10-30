package com.packfare.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Short description, e.g. "Delhi → Manali → Kasol". */
    val destination: String,
    /** Whole-group budget. 0 = not set. */
    val budgetPaise: Long,
    /** First day of the trip as epoch day (LocalDate.toEpochDay), null = not set. */
    val startEpochDay: Long? = null,
    val plannedDays: Int = 0,
    /** Name of a [com.packfare.app.domain.TravelStyle]. */
    val style: String = "BACKPACKER",
    /** The member who is "me" on this phone: their cash wallet is tracked. */
    val ownerMemberId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** "TRIP" or "EVENT" (conference, client visit, offsite). Events skip planning but keep bills and claims. */
    val kind: String = KIND_TRIP,
    /** Company or organiser to claim from. Used on the reimbursement PDF. */
    val company: String? = null,
) {
    val isEvent: Boolean get() = kind == KIND_EVENT

    companion object {
        const val KIND_TRIP = "TRIP"
        const val KIND_EVENT = "EVENT"
    }
}

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val upiId: String? = null,
)

/** One hop of the route, e.g. Delhi → Manali by overnight bus. */
@Entity(
    tableName = "legs",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class LegEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val position: Int,
    val fromName: String,
    val toName: String,
    /** Name of a [com.packfare.app.domain.TransportMode]. */
    val mode: String,
    /** Expected per-person fare (estimate or what you paid). */
    val farePaise: Long,
    val hours: Double? = null,
    val dateEpochDay: Long? = null,
    val overnight: Boolean = false,
    /** Nights planned at the destination of this leg. */
    val nightsAtDestination: Int = 0,
    val notes: String? = null,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["paidById"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LegEntity::class, parentColumns = ["id"], childColumns = ["legId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tripId"), Index("paidById"), Index("legId")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val amountPaise: Long,
    val paidById: Long,
    /** Name of a [com.packfare.app.domain.Category]. */
    val category: String,
    val isSettlement: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val receiptPath: String? = null,
    /** Name of a [com.packfare.app.domain.PaymentMethod]. */
    val paymentMethod: String = "UPI",
    /** Optional: which route leg this was spent on. */
    val legId: Long? = null,
)

@Entity(
    tableName = "expense_shares",
    primaryKeys = ["expenseId", "memberId"],
    foreignKeys = [
        ForeignKey(entity = ExpenseEntity::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("memberId")],
)
data class ExpenseShareEntity(
    val expenseId: Long,
    val memberId: Long,
    val sharePaise: Long,
)

/** Cash you took out of an ATM (or changed from foreign currency). */
@Entity(
    tableName = "cash_topups",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class CashTopUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val amountPaise: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A file in the encrypted vault. The file itself lives encrypted in app storage. */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Name of a [com.packfare.app.vault.DocKind]. */
    val kind: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Offline copy of what Explore learned about a place from Wikivoyage. */
@Entity(tableName = "place_cache")
data class PlaceCacheEntity(
    @PrimaryKey val title: String,
    val summary: String,
    val lat: Double?,
    val lon: Double?,
    /** Raw article wikitext, re-parsed on load so parser improvements apply to cached places. */
    val wikitext: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** Row for the trip list: a trip plus totals computed in SQL. */
data class TripSummary(
    @Embedded val trip: TripEntity,
    val spentPaise: Long,
    val memberCount: Int,
)
