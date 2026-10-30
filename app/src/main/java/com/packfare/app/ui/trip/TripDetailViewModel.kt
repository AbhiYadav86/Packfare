package com.packfare.app.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packfare.app.data.CashTopUpEntity
import com.packfare.app.data.ExpenseEntity
import com.packfare.app.data.LegEntity
import com.packfare.app.data.MemberEntity
import com.packfare.app.data.TripEntity
import com.packfare.app.data.TripRepository
import com.packfare.app.domain.CashReport
import com.packfare.app.domain.CashWallet
import com.packfare.app.domain.Category
import com.packfare.app.domain.CostTier
import com.packfare.app.domain.DailyCosts
import com.packfare.app.domain.Destinations
import com.packfare.app.domain.PaymentMethod
import com.packfare.app.domain.Runway
import com.packfare.app.domain.RunwayReport
import com.packfare.app.domain.SplitCalculator
import com.packfare.app.domain.TravelStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ExpenseRow(
    val expense: ExpenseEntity,
    val paidByName: String,
    /** For settlements: who received the money. */
    val paidToName: String?,
    val splitCount: Int,
)

data class MemberBalance(
    val member: MemberEntity,
    val netPaise: Long,
    /** What they paid for the group (excluding settlements). */
    val paidPaise: Long,
    /** Their share of the trip's spending (excluding settlements). */
    val sharePaise: Long,
)

data class SettlementRow(val from: MemberEntity, val to: MemberEntity, val amountPaise: Long)

/** A route leg plus what was actually spent on it and the stay it saves. */
data class LegRow(
    val leg: LegEntity,
    val spentPaise: Long,
    val savedStayPaise: Long,
    val destinationFewAtms: Boolean,
    val permitNote: String?,
)

data class TripDetailState(
    val loaded: Boolean = false,
    val trip: TripEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val expenses: List<ExpenseRow> = emptyList(),
    val balances: List<MemberBalance> = emptyList(),
    val settlements: List<SettlementRow> = emptyList(),
    val spentPaise: Long = 0,
    val spentByCategory: List<Pair<Category, Long>> = emptyList(),
    val spentByMethod: Map<PaymentMethod, Long> = emptyMap(),
    val legs: List<LegRow> = emptyList(),
    val overnightSavingsPaise: Long = 0,
    val runway: RunwayReport? = null,
    val cash: CashReport? = null,
    val cashTopUps: List<CashTopUpEntity> = emptyList(),
    val style: TravelStyle = TravelStyle.BACKPACKER,
)

private data class Core(
    val trip: TripEntity?,
    val members: List<MemberEntity>,
    val expenses: List<ExpenseEntity>,
    val shares: List<com.packfare.app.data.ExpenseShareEntity>,
)

class TripDetailViewModel(
    private val repository: TripRepository,
    private val tripId: Long,
) : ViewModel() {

    val state: StateFlow<TripDetailState> = combine(
        combine(
            repository.trip(tripId),
            repository.members(tripId),
            repository.expenses(tripId),
            repository.shares(tripId),
        ) { trip, members, expenses, shares -> Core(trip, members, expenses, shares) },
        repository.legs(tripId),
        repository.cashTopUps(tripId),
    ) { core, legs, topUps -> build(core, legs, topUps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailState())

    private fun build(core: Core, legs: List<LegEntity>, topUps: List<CashTopUpEntity>): TripDetailState {
        val (trip, members, expenses, shares) = core
        val byId = members.associateBy { it.id }
        val sharesByExpense = shares.groupBy { it.expenseId }
        val style = TravelStyle.fromName(trip?.style)
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val rows = expenses.map { e ->
            val expenseShares = sharesByExpense[e.id].orEmpty()
            ExpenseRow(
                expense = e,
                paidByName = byId[e.paidById]?.name ?: "?",
                paidToName = if (e.isSettlement) expenseShares.firstOrNull()?.let { byId[it.memberId]?.name } else null,
                splitCount = expenseShares.size,
            )
        }

        val net = SplitCalculator.netBalances(
            memberIds = members.map { it.id },
            paid = expenses.map { it.paidById to it.amountPaise },
            owed = shares.map { it.memberId to it.sharePaise },
        )

        val real = expenses.filterNot { it.isSettlement }
        val realIds = real.map { it.id }.toSet()
        val paidTotals = real.groupBy { it.paidById }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        val shareTotals = shares.filter { it.expenseId in realIds }
            .groupBy { it.memberId }.mapValues { (_, l) -> l.sumOf { it.sharePaise } }
        val spent = real.sumOf { it.amountPaise }
        val todaySpent = real.filter { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == today }
            .sumOf { it.amountPaise }

        // ---- Route legs (features 5 & 6) ----
        val spentByLeg = real.filter { it.legId != null }.groupBy { it.legId!! }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        val stopNames = trip?.destination?.split(" → ")?.toSet().orEmpty()
        val legRows = legs.map { leg ->
            val dest = Destinations.find(leg.toName)
            val stay = (dest?.baseline ?: DailyCosts.forTier(CostTier.MID)).forStyle(style).stayPaise
            LegRow(
                leg = leg,
                spentPaise = spentByLeg[leg.id] ?: 0L,
                // Only saves money when you'd otherwise pay for a bed at a stop (not on the way home).
                savedStayPaise = if (leg.overnight && leg.toName in stopNames) stay * members.size.coerceAtLeast(1) else 0L,
                destinationFewAtms = dest?.fewAtms == true,
                permitNote = dest?.permitNote,
            )
        }

        // ---- Runway (feature 1) ----
        val runway = trip?.let {
            Runway.compute(
                startDate = it.startEpochDay?.let(LocalDate::ofEpochDay),
                plannedDays = it.plannedDays,
                budgetPaise = it.budgetPaise,
                spentPaise = spent,
                todaySpentPaise = todaySpent,
                today = today,
            )
        }

        // ---- Cash wallet (feature 3) ----
        val ownerId = trip?.ownerMemberId
        val cashSpent = expenses
            .filter { PaymentMethod.fromName(it.paymentMethod) == PaymentMethod.CASH }
            .filter { ownerId == null || it.paidById == ownerId }
            .sumOf { it.amountPaise }
        val upcomingRemote = upcomingRemoteStop(trip, legs, today)
        // Cash budget is per person: the group budget split by heads.
        val myDaily = (runway?.dailyTargetPaise ?: 0L) / members.size.coerceAtLeast(1)
        val cash = CashWallet.report(topUps.sumOf { it.amountPaise }, cashSpent, myDaily, upcomingRemote)

        return TripDetailState(
            loaded = true,
            trip = trip,
            members = members,
            expenses = rows,
            balances = members.map {
                MemberBalance(it, net[it.id] ?: 0L, paidTotals[it.id] ?: 0L, shareTotals[it.id] ?: 0L)
            },
            settlements = SplitCalculator.simplify(net).mapNotNull { t ->
                val from = byId[t.fromId]
                val to = byId[t.toId]
                if (from != null && to != null) SettlementRow(from, to, t.amountPaise) else null
            },
            spentPaise = spent,
            spentByCategory = real.groupBy { Category.fromName(it.category) }
                .map { (cat, list) -> cat to list.sumOf { it.amountPaise } }
                .sortedByDescending { it.second },
            spentByMethod = real.groupBy { PaymentMethod.fromName(it.paymentMethod) }
                .mapValues { (_, l) -> l.sumOf { it.amountPaise } },
            legs = legRows,
            overnightSavingsPaise = legRows.sumOf { it.savedStayPaise },
            runway = runway,
            cash = cash,
            cashTopUps = topUps,
            style = style,
        )
    }

    /** The next stop with few ATMs (and nights planned there), if any is coming up. */
    private fun upcomingRemoteStop(trip: TripEntity?, legs: List<LegEntity>, today: LocalDate): Pair<String, Int>? {
        val upcoming = legs.filter { it.dateEpochDay == null || it.dateEpochDay >= today.toEpochDay() }
        upcoming.firstOrNull { Destinations.find(it.toName)?.fewAtms == true }?.let {
            return it.toName to it.nightsAtDestination.coerceAtLeast(1)
        }
        // The first stop isn't a leg destination; check it before the trip starts.
        val start = trip?.startEpochDay
        if (trip != null && start != null && start >= today.toEpochDay()) {
            val first = trip.destination.split(" → ").firstOrNull()?.let { Destinations.find(it) }
            if (first?.fewAtms == true) {
                val nights = legs.minByOrNull { it.position }?.dateEpochDay?.let { (it - start).toInt() } ?: trip.plannedDays
                return first.name to nights.coerceAtLeast(1)
            }
        }
        return null
    }

    // ---- People ----

    fun addMember(name: String, upiId: String?) {
        viewModelScope.launch { repository.addMember(tripId, name, upiId) }
    }

    fun updateMember(member: MemberEntity, name: String, upiId: String?) {
        viewModelScope.launch { repository.updateMember(member, name, upiId) }
    }

    /** [onResult] gets false if the person is part of expenses and can't be removed. */
    fun removeMember(member: MemberEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.removeMemberIfUnused(member.id)) }
    }

    fun setMe(member: MemberEntity) {
        val trip = state.value.trip ?: return
        viewModelScope.launch { repository.updateTrip(trip.copy(ownerMemberId = member.id)) }
    }

    // ---- Expenses ----

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch { repository.deleteExpense(expenseId) }
    }

    fun markSettled(row: SettlementRow) {
        viewModelScope.launch {
            repository.recordSettlement(tripId, row.from.id, row.to.id, row.amountPaise)
        }
    }

    // ---- Trip settings ----

    fun setCompany(company: String) {
        val trip = state.value.trip ?: return
        viewModelScope.launch { repository.updateTrip(trip.copy(company = company.ifBlank { null })) }
    }

    fun updateTripPlan(budgetPaise: Long, plannedDays: Int, start: LocalDate?, style: TravelStyle) {
        val trip = state.value.trip ?: return
        viewModelScope.launch {
            repository.updateTrip(
                trip.copy(
                    budgetPaise = budgetPaise,
                    plannedDays = plannedDays,
                    startEpochDay = start?.toEpochDay(),
                    style = style.name,
                ),
            )
        }
    }

    fun deleteTrip(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
            onDeleted()
        }
    }

    // ---- Route ----

    fun saveLeg(leg: LegEntity) {
        viewModelScope.launch {
            if (leg.id == 0L) repository.addLeg(leg.copy(tripId = tripId)) else repository.updateLeg(leg)
        }
    }

    fun deleteLeg(legId: Long) {
        viewModelScope.launch { repository.deleteLeg(legId) }
    }

    // ---- Cash ----

    fun addCash(amountPaise: Long, note: String?) {
        viewModelScope.launch { repository.addCash(tripId, amountPaise, note) }
    }

    fun deleteCash(id: Long) {
        viewModelScope.launch { repository.deleteCash(id) }
    }
}
