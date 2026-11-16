package com.packfare.app.ui.trip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packfare.app.data.ActivityEntity
import com.packfare.app.data.CashTopUpEntity
import com.packfare.app.data.DocumentEntity
import com.packfare.app.data.ExpenseEntity
import com.packfare.app.data.LegEntity
import com.packfare.app.data.MemberEntity
import com.packfare.app.data.TripEntity
import com.packfare.app.data.TripRepository
import com.packfare.app.domain.Activities
import com.packfare.app.domain.BookingItem
import com.packfare.app.domain.BudgetPlanner
import com.packfare.app.domain.ItineraryBuilder
import com.packfare.app.domain.ItineraryDay
import com.packfare.app.domain.PlanStop
import com.packfare.app.vault.DocKind
import com.packfare.app.vault.Vault
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
import kotlinx.coroutines.flow.first
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
    /** Day-by-day suggestions for the trip, with things to do and what they cost. */
    val plan: List<ItineraryDay> = emptyList(),
)

private data class Core(
    val trip: TripEntity?,
    val members: List<MemberEntity>,
    val expenses: List<ExpenseEntity>,
    val shares: List<com.packfare.app.data.ExpenseShareEntity>,
)

class TripDetailViewModel(
    private val repository: TripRepository,
    private val vault: Vault,
    private val explore: com.packfare.app.explore.ExploreRepository,
    private val tripId: Long,
) : ViewModel() {

    /** Priced places to sleep from the guide, by destination name. Filled in by [loadStays]. */
    var stays by androidx.compose.runtime.mutableStateOf<Map<String, List<com.packfare.app.explore.PriceSample>>>(emptyMap())
        private set

    fun loadStays(places: List<com.packfare.app.domain.Destination>) {
        places.filter { it.name !in stays }.forEach { d ->
            viewModelScope.launch {
                val found = runCatching { explore.stays(d, allowNetwork = true) }.getOrDefault(emptyList())
                stays = stays + (d.name to found)
            }
        }
    }


    /** The guide's See/Do listings per stop (real places with notes), loaded once the trip's stops are known. */
    private val guide = kotlinx.coroutines.flow.MutableStateFlow<Map<String, List<com.packfare.app.domain.ActivityIdea>>>(emptyMap())

    init {
        viewModelScope.launch {
            val trip = repository.trip(tripId).first { it != null } ?: return@launch
            trip.destination.split("→").map { it.trim() }.filter { it.isNotBlank() }
                .mapNotNull { Destinations.find(it) }.distinctBy { it.name }
                .forEach { d ->
                    val found = runCatching { explore.guideListings(d, allowNetwork = true) }.getOrDefault(emptyList())
                    guide.value = guide.value + (d.name to found)
                }
        }
    }

    val activities: StateFlow<List<ActivityEntity>> = repository.activities(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val documents: StateFlow<List<DocumentEntity>> = vault.documentsForTrip(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var message by androidx.compose.runtime.mutableStateOf<String?>(null)


    val state: StateFlow<TripDetailState> = combine(
        combine(
            repository.trip(tripId),
            repository.members(tripId),
            repository.expenses(tripId),
            repository.shares(tripId),
        ) { trip, members, expenses, shares -> Core(trip, members, expenses, shares) },
        repository.legs(tripId),
        repository.cashTopUps(tripId),
        guide,
    ) { core, legs, topUps, guideIdeas -> build(core, legs, topUps, guideIdeas) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailState())

    private fun build(core: Core, legs: List<LegEntity>, topUps: List<CashTopUpEntity>, guideIdeas: Map<String, List<com.packfare.app.domain.ActivityIdea>>): TripDetailState {
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
            plan = buildPlan(trip, legs, style, guideIdeas),
        )
    }

    /** Day-by-day suggestions from the trip's places and nights. Empty for events or trips without known places. */
    private fun buildPlan(trip: TripEntity?, legs: List<LegEntity>, style: TravelStyle, guideIdeas: Map<String, List<com.packfare.app.domain.ActivityIdea>>): List<ItineraryDay> {
        if (trip == null || trip.isEvent) return emptyList()
        val places = trip.destination.split("→").map { it.trim() }.filter { it.isNotBlank() }
            .mapNotNull { Destinations.find(it) }
            .distinctBy { it.name }
        if (places.isEmpty()) return emptyList()
        val planned = trip.plannedDays.coerceAtLeast(places.size)
        val stops = places.mapIndexed { i, d ->
            val fromLegs = legs.firstOrNull { it.toName == d.name && it.nightsAtDestination > 0 }?.nightsAtDestination
            val even = planned / places.size + if (i < planned % places.size) 1 else 0
            PlanStop(d, (fromLegs ?: even).coerceAtLeast(1))
        }
        val suggestion = BudgetPlanner.suggest(stops, style, 1)
        val ideas = places.associate { it.name to Activities.ideasFor(it, guideIdeas[it.name].orEmpty(), max = 16) }
        return ItineraryBuilder.days(suggestion, trip.startEpochDay?.let { LocalDate.ofEpochDay(it) }, ideas)
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

    // ---- Activities ----

    fun addActivity(title: String, dayEpoch: Long?, costPaise: Long, note: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addActivity(ActivityEntity(tripId = tripId, title = title.trim(), dayEpoch = dayEpoch, costPaise = costPaise, note = note))
        }
    }

    fun setActivityDone(a: ActivityEntity, done: Boolean) {
        viewModelScope.launch { repository.updateActivity(a.copy(done = done)) }
    }

    fun deleteActivity(a: ActivityEntity) {
        viewModelScope.launch { repository.deleteActivity(a.id) }
    }

    // ---- Bookings ----

    private fun saveBookings(items: List<BookingItem>, asked: Boolean = true) {
        val trip = state.value.trip ?: return
        viewModelScope.launch {
            repository.updateTrip(trip.copy(bookings = BookingItem.encode(items), bookingsAsked = asked || trip.bookingsAsked))
        }
    }

    /** Answers to "have you already booked any of these?": done flags with the price paid, if given. */
    fun saveBookingAnswers(items: List<BookingItem>) = saveBookings(items)

    fun skipBookingQuestion() {
        val trip = state.value.trip ?: return
        viewModelScope.launch { repository.updateTrip(trip.copy(bookingsAsked = true)) }
    }

    /** Mark one booking done with what you paid (null if you'd rather not say), or undo it. */
    fun setBooked(key: String, done: Boolean, actualPaise: Long?) {
        val trip = state.value.trip ?: return
        saveBookings(BookingItem.setBooked(BookingItem.decode(trip.bookings), key, done, actualPaise))
    }

    fun addDocument(uri: android.net.Uri, title: String, kind: DocKind) {
        viewModelScope.launch {
            runCatching { vault.add(uri, title, kind, tripId) }
                .onFailure { message = it.message ?: "Couldn't add that file" }
        }
    }

    fun toggleBooking(key: String) {
        val trip = state.value.trip ?: return
        val items = com.packfare.app.domain.BookingItem.decode(trip.bookings)
        val updated = com.packfare.app.domain.BookingItem.toggle(items, key)
        viewModelScope.launch { repository.updateTrip(trip.copy(bookings = com.packfare.app.domain.BookingItem.encode(updated))) }
    }

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
