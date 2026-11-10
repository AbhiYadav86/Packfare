@file:OptIn(ExperimentalMaterial3Api::class)

package com.packfare.app.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packfare.app.data.LegEntity
import com.packfare.app.data.MemberEntity
import com.packfare.app.ui.theme.BalanceColors
import kotlinx.coroutines.launch

private enum class TripTab(val label: String) {
    TODAY("Today"),
    BOOKINGS("Bookings"),
    HOTELS("Hotels"),
    EXPENSES("Expenses"),
    ROUTE("Route"),
    BALANCES("Settle up"),
    PEOPLE("People"),
}

@Composable
fun TripDetailScreen(
    vm: TripDetailViewModel,
    onBack: () -> Unit,
    onAddExpense: (scan: Boolean) -> Unit,
    onEditExpense: (Long) -> Unit,
    onTripDeleted: () -> Unit,
    onFindFares: (from: String, to: String, epochDay: Long?) -> Unit,
    onOpenDoc: (Long) -> Unit,
    foreignVisitor: Boolean,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activities by vm.activities.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()
    var addingActivity by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAddPerson by rememberSaveable { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<MemberEntity?>(null) }
    var confirmDeleteTrip by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf(false) }
    var addingCash by remember { mutableStateOf(false) }
    var legDialog by remember { mutableStateOf<LegEntity?>(null) }
    var addingLeg by remember { mutableStateOf(false) }
    var claimOpen by remember { mutableStateOf(false) }
    val isEvent = state.trip?.isEvent == true
    // Events have no route to plan, so that tab is hidden.
    val tabs = if (isEvent) TripTab.entries.filter { it != TripTab.ROUTE } else TripTab.entries.toList()
    val currentTab = tabs[tab.coerceIn(0, tabs.lastIndex)]

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.trip?.name ?: "", maxLines = 1)
                        state.trip?.destination?.takeIf { it.isNotBlank() }?.let {
                            Text("📍 $it", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { claimOpen = true }, enabled = state.trip != null) {
                        Text("📄", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { editingPlan = true }, enabled = state.trip != null) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit budget and dates")
                    }
                    IconButton(onClick = { shareTrip(context, buildShareText(state)) }, enabled = state.trip != null) {
                        Icon(Icons.Filled.Share, contentDescription = "Share trip summary")
                    }
                    IconButton(onClick = { confirmDeleteTrip = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete trip")
                    }
                },
            )
        },
        floatingActionButton = {
            when (currentTab) {
                TripTab.TODAY, TripTab.EXPENSES -> if (state.members.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (currentTab == TripTab.TODAY) {
                            ExtendedFloatingActionButton(
                                onClick = { addingActivity = true },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text("Add activity") },
                            )
                        }
                        SmallFloatingActionButton(onClick = { onAddExpense(true) }) {
                            Text("🧾", style = MaterialTheme.typography.titleMedium)
                        }
                        ExtendedFloatingActionButton(
                            onClick = { onAddExpense(false) },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text("Add expense") },
                        )
                    }
                }
                TripTab.ROUTE -> ExtendedFloatingActionButton(
                    onClick = { addingLeg = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add leg") },
                )
                TripTab.PEOPLE -> ExtendedFloatingActionButton(
                    onClick = { showAddPerson = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add person") },
                )
                TripTab.BALANCES, TripTab.BOOKINGS, TripTab.HOTELS -> Unit
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab.coerceIn(0, tabs.lastIndex), edgePadding = 8.dp) {
                tabs.forEachIndexed { index, t ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(t.label) })
                }
            }
            if (!state.loaded) return@Column
            when (currentTab) {
                TripTab.TODAY -> OverviewTab(
                    state = state,
                    onEditPlan = { editingPlan = true },
                    onAddCash = { addingCash = true },
                    onDeleteCash = vm::deleteCash,
                    activities = activities,
                    onAddSuggestion = { title, day, cost, note -> vm.addActivity(title, day, cost, note) },
                    onSetActivityDone = vm::setActivityDone,
                    onDeleteActivity = vm::deleteActivity,
                    onAddActivity = { addingActivity = true },
                    onLogBill = { onAddExpense(false) },
                )
                TripTab.BOOKINGS -> BookingsTab(
                    trip = state.trip,
                    legs = state.legs,
                    plan = state.plan,
                    people = state.members.size.coerceAtLeast(1),
                    spentPaise = state.spentPaise,
                    docs = docs,
                    foreignVisitor = foreignVisitor,
                    onSetBooked = vm::setBooked,
                    onAddDocument = { uri, title, kind -> vm.addDocument(uri, title, kind) },
                    onOpenDoc = onOpenDoc,
                )
                TripTab.HOTELS -> HotelsTab(
                    trip = state.trip,
                    plan = state.plan,
                    people = state.members.size.coerceAtLeast(1),
                    style = state.style,
                    stays = vm.stays,
                    onLoadStays = vm::loadStays,
                )
                TripTab.EXPENSES -> ExpensesTab(
                    state = state,
                    onGoToPeople = { tab = tabs.indexOf(TripTab.PEOPLE) },
                    onEditExpense = onEditExpense,
                    onDeleteSettlement = vm::deleteExpense,
                )
                TripTab.ROUTE -> RouteTab(
                    state,
                    onEditLeg = { legDialog = it },
                    onFindFares = { leg -> onFindFares(leg.fromName, leg.toName, leg.dateEpochDay) },
                )
                TripTab.BALANCES -> BalancesTab(state, onMarkSettled = vm::markSettled)
                TripTab.PEOPLE -> PeopleTab(state, onPersonClick = { editingPerson = it })
            }
        }
    }

    // ---- Dialogs ----

    if (claimOpen) {
        val ownerName = state.members.firstOrNull { it.id == state.trip?.ownerMemberId }?.name.orEmpty()
        ClaimDialog(
            state = state,
            initialClaimant = ownerName,
            initialCompany = state.trip?.company.orEmpty(),
            onDismiss = { claimOpen = false },
            onCreate = { header, summary, company ->
                claimOpen = false
                vm.setCompany(company)
                val file = com.packfare.app.report.ClaimPdf.outputFile(context, header.reference)
                val ok = runCatching { com.packfare.app.report.ClaimPdf.write(file, header, summary) }.isSuccess
                if (ok) {
                    com.packfare.app.report.ClaimPdf.shareFile(context, file, "Expense claim: ${header.eventName}")
                } else {
                    scope.launch { snackbar.showSnackbar("Couldn't create the PDF") }
                }
            },
        )
    }

    if (addingActivity) {
        AddActivityDialog(
            plan = state.plan,
            onDismiss = { addingActivity = false },
            onAdd = { title, day, cost ->
                vm.addActivity(title, day, cost)
                addingActivity = false
            },
        )
    }

    // The first time a planned trip is opened, ask whether anything is already booked.
    state.trip?.let { t ->
        val items = com.packfare.app.domain.BookingItem.decode(t.bookings)
        if (state.loaded && items.isNotEmpty() && !t.bookingsAsked) {
            AlreadyBookedDialog(items, onSave = vm::saveBookingAnswers, onSkip = vm::skipBookingQuestion)
        }
    }

    if (editingPlan) {
        state.trip?.let { trip ->
            EditPlanDialog(
                trip = trip,
                onDismiss = { editingPlan = false },
                onSave = { budget, days, start, style ->
                    vm.updateTripPlan(budget, days, start, style)
                    editingPlan = false
                },
            )
        }
    }

    if (addingCash) {
        CashDialog(
            onDismiss = { addingCash = false },
            onSave = { amount, note ->
                vm.addCash(amount, note)
                addingCash = false
            },
        )
    }

    if (addingLeg || legDialog != null) {
        val editing = legDialog
        LegDialog(
            initial = editing,
            defaultFrom = state.legs.lastOrNull()?.leg?.toName
                ?: state.trip?.destination?.split(" → ")?.firstOrNull().orEmpty(),
            onDismiss = {
                addingLeg = false
                legDialog = null
            },
            onSave = { leg ->
                vm.saveLeg(leg)
                addingLeg = false
                legDialog = null
            },
            onDelete = editing?.let { leg ->
                {
                    vm.deleteLeg(leg.id)
                    legDialog = null
                }
            },
        )
    }

    if (showAddPerson) {
        PersonDialog(
            title = "Add person",
            onDismiss = { showAddPerson = false },
            onSave = { name, upi ->
                vm.addMember(name, upi)
                showAddPerson = false
            },
        )
    }

    editingPerson?.let { person ->
        val isMe = person.id == state.trip?.ownerMemberId
        PersonDialog(
            title = "Edit ${person.name}",
            initialName = person.name,
            initialUpi = person.upiId.orEmpty(),
            onDismiss = { editingPerson = null },
            onSave = { name, upi ->
                vm.updateMember(person, name, upi)
                editingPerson = null
            },
            onRemove = {
                editingPerson = null
                vm.removeMember(person) { removed ->
                    scope.launch {
                        snackbar.showSnackbar(
                            if (removed) "${person.name} removed"
                            else "${person.name} is part of some expenses. Edit or delete those first.",
                        )
                    }
                }
            },
            onSetMe = if (isMe) null else {
                {
                    vm.setMe(person)
                    editingPerson = null
                }
            },
        )
    }

    if (confirmDeleteTrip) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTrip = false },
            title = { Text("Delete this trip?") },
            text = { Text("All its people, expenses, route, receipts and cash records will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteTrip = false
                    vm.deleteTrip(onTripDeleted)
                }) { Text("Delete", color = BalanceColors.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteTrip = false }) { Text("Cancel") } },
        )
    }
}
