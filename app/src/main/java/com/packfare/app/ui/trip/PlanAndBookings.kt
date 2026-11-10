@file:OptIn(ExperimentalLayoutApi::class)

package com.packfare.app.ui.trip

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.packfare.app.data.ActivityEntity
import com.packfare.app.data.DocumentEntity
import com.packfare.app.data.TripEntity
import com.packfare.app.domain.ActivityIdea
import com.packfare.app.domain.BookingItem
import com.packfare.app.domain.BudgetAfterBookings
import com.packfare.app.domain.Destinations
import com.packfare.app.domain.FlightPlanner
import com.packfare.app.domain.ItineraryDay
import com.packfare.app.domain.Money
import com.packfare.app.domain.TransportMode
import com.packfare.app.fares.BookingLinks
import com.packfare.app.ui.components.PlacePhoto
import com.packfare.app.vault.DocKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

// =====================================================================
// Today tab: budget after bookings, and the day-by-day plan with activities
// =====================================================================

/** What's left once bookings are counted. Shown on Today and at the top of Bookings. */
@Composable
fun BudgetAfterBookingsCard(budget: BudgetAfterBookings, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("💰 Budget after bookings", style = MaterialTheme.typography.titleSmall)
            Row {
                Text("Trip budget", Modifier.weight(1f)); Text(Money.format(budget.budgetPaise))
            }
            Row {
                Text("Booked so far", Modifier.weight(1f)); Text("− " + Money.format(budget.bookedPaise))
            }
            Row {
                Text("Still to book (planned)", Modifier.weight(1f)); Text("− " + Money.format(budget.stillToBookPaise))
            }
            Row {
                Text("Bills logged", Modifier.weight(1f)); Text("− " + Money.format(budget.spentPaise))
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row {
                Text("Left for food, rides and fun", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    Money.format(budget.leftPaise),
                    fontWeight = FontWeight.Bold,
                    color = if (budget.leftPaise < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                "Once you book something and enter the price, this updates. Don't log booked tickets again as bills.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Day-by-day plan: suggested things to do (with photos when online and an estimated cost), your own
 * activities for each day with a done tick, and a way to add more.
 */
@Composable
fun TripPlanCard(
    plan: List<ItineraryDay>,
    activities: List<ActivityEntity>,
    onAddSuggestion: (title: String, dayEpoch: Long?, costPaise: Long, note: String?) -> Unit,
    onSetDone: (ActivityEntity, Boolean) -> Unit,
    onDelete: (ActivityEntity) -> Unit,
    onAddActivity: () -> Unit,
    onLogBill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🗓️ Your plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onAddActivity) { Text("+ Activity") }
            }
            if (plan.isEmpty()) {
                Text(
                    "Add a place and dates to your trip (pencil icon) to get a day-by-day plan with things to do. " +
                        "You can still add your own activities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val shownIds = mutableSetOf<Long>()
            plan.forEach { d ->
                val dayEpoch = d.date?.toEpochDay()
                val mine = activities.filter { a -> dayEpoch != null && a.dayEpoch == dayEpoch }
                shownIds += mine.map { it.id }
                HorizontalDivider()
                Text(
                    "Day ${d.day}" + (d.date?.let { " · ${it.format(dayFormat)}" } ?: "") + " · ${d.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                d.travel?.let { t ->
                    Text(
                        "${t.option.mode.emoji} ${t.from.name} → ${t.to.name}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                d.activities.forEach { idea ->
                    val added = activities.any { it.title == idea.name && it.dayEpoch == dayEpoch }
                    SuggestionRow(d.place, idea, added) {
                        onAddSuggestion(idea.name, dayEpoch, idea.planPaise, idea.note.takeIf { it.isNotBlank() })
                    }
                }
                mine.forEach { a -> ActivityRow(a, onSetDone, onDelete, onLogBill) }
            }
            val others = activities.filter { it.id !in shownIds }
            if (others.isNotEmpty()) {
                HorizontalDivider()
                Text("Other activities", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                others.forEach { a -> ActivityRow(a, onSetDone, onDelete, onLogBill) }
            }
            val planned = activities.filter { !it.done }.sumOf { it.costPaise }
            if (activities.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "Activities you've added ≈ ${Money.format(activities.sumOf { it.costPaise })} per person" +
                        if (planned > 0) " (${Money.format(planned)} still to do)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                "Prices are estimates per person. Ideas marked \"est.\" are guessed from the kind of place. " +
                    "Foreign visitors often pay more at monuments and parks. Photos appear when you're online.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionRow(place: String, idea: ActivityIdea, added: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PlacePhoto(query = "${idea.name} $place", modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(idea.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(idea.costLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (idea.note.isNotBlank()) {
                Text(idea.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (added) Text("Added ✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        else TextButton(onClick = onAdd) { Text("+ Add") }
    }
}

@Composable
private fun ActivityRow(a: ActivityEntity, onSetDone: (ActivityEntity, Boolean) -> Unit, onDelete: (ActivityEntity) -> Unit, onLogBill: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = a.done, onCheckedChange = { onSetDone(a, it) })
        Column(Modifier.weight(1f)) {
            Text(
                a.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (a.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (a.costPaise > 0) "≈ ${Money.format(a.costPaise)}" else "free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (a.done && a.costPaise > 0) TextButton(onClick = onLogBill) { Text("🧾 Log bill") }
        TextButton(onClick = { onDelete(a) }) { Text("Remove") }
    }
}

@Composable
fun AddActivityDialog(plan: List<ItineraryDay>, onDismiss: () -> Unit, onAdd: (title: String, dayEpoch: Long?, costPaise: Long) -> Unit) {
    var title by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<Long?>(plan.firstOrNull()?.date?.toEpochDay()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("What are you doing?") },
                    placeholder = { Text("Visit the local museum") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it }, singleLine = true,
                    label = { Text("Cost per person (optional)") }, prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                val dated = plan.filter { it.date != null }
                if (dated.isNotEmpty()) {
                    Text("Which day?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dated.forEach { d ->
                            val epoch = d.date!!.toEpochDay()
                            FilterChip(selected = day == epoch, onClick = { day = epoch }, label = { Text("Day ${d.day}") })
                        }
                        FilterChip(selected = day == null, onClick = { day = null }, label = { Text("Any day") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onAdd(title, day, Money.parse(cost) ?: 0L) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// =====================================================================
// "Have you already booked?"
// =====================================================================

/** Asked once, the first time a planned trip is opened: tick what's booked and what you paid. */
@Composable
fun AlreadyBookedDialog(items: List<BookingItem>, onSave: (List<BookingItem>) -> Unit, onSkip: () -> Unit) {
    val ticked = remember { mutableStateMapOf<String, Boolean>().apply { items.forEach { put(it.key, it.done) } } }
    val paid = remember { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Already booked anything?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Tick what you've booked and, if you like, what you paid in total for everyone. " +
                        "Packfare will recalculate what's left of your budget.",
                    style = MaterialTheme.typography.bodySmall,
                )
                items.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ticked[b.key] == true, onCheckedChange = { ticked[b.key] = it })
                        Text(b.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (ticked[b.key] == true) {
                        OutlinedTextField(
                            value = paid[b.key].orEmpty(), onValueChange = { paid[b.key] = it }, singleLine = true,
                            label = { Text("Paid in total (planned ${Money.format(b.plannedPaise)})") }, prefix = { Text("₹ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(items.map { b ->
                    val done = ticked[b.key] == true
                    b.copy(done = done, actualPaise = if (done) paid[b.key]?.let { Money.parse(it) } else null)
                })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onSkip) { Text("Not yet") } },
    )
}

// =====================================================================
// Bookings tab: book, record what you paid, keep the tickets
// =====================================================================

@Composable
fun BookingsTab(
    trip: TripEntity?,
    legs: List<LegRow>,
    plan: List<ItineraryDay>,
    people: Int,
    spentPaise: Long,
    docs: List<DocumentEntity>,
    foreignVisitor: Boolean,
    onSetBooked: (key: String, done: Boolean, actualPaise: Long?) -> Unit,
    onAddDocument: (android.net.Uri, title: String, DocKind) -> Unit,
    onOpenDoc: (Long) -> Unit,
) {
    val uri = LocalUriHandler.current
    val items = BookingItem.decode(trip?.bookings)
    var pricing by remember { mutableStateOf<BookingItem?>(null) }
    var attachTitle by remember { mutableStateOf<Pair<String, DocKind>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        val target = attachTitle
        if (picked != null && target != null) onAddDocument(picked, target.first, target.second)
        attachTitle = null
    }
    fun attach(title: String, kind: DocKind) {
        attachTitle = title to kind
        picker.launch(arrayOf("application/pdf", "image/*"))
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (trip != null && items.isNotEmpty()) {
            BudgetAfterBookingsCard(BudgetAfterBookings.compute(trip.budgetPaise, spentPaise, items))
        }
        Text("Book and keep your tickets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Use the links to book, then tap \"I've booked this\" and enter the price. Add the ticket or confirmation PDF " +
                "so it's here offline. Booking links may earn Packfare a small commission at no cost to you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (items.isEmpty()) {
            Text(
                "This trip has no booking list yet. Trips made with Plan a trip get one for their journeys and stays.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items.forEach { b ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (b.done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(b.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (b.done) {
                        Text(
                            "Booked ✓" + (b.actualPaise?.let { " · paid ${Money.format(it)}" } ?: " · price not entered"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                attach(b.label, if (b.key.startsWith("leg")) DocKind.TICKET else DocKind.BOOKING)
                            }) { Text("📎 Add ticket / PDF") }
                            TextButton(onClick = { pricing = b }) { Text("Change price") }
                            TextButton(onClick = { onSetBooked(b.key, false, null) }) { Text("Undo") }
                        }
                    } else {
                        Text(
                            "Planned ≈ ${Money.format(b.plannedPaise)}" + if (people > 1) " for $people people" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val links = bookingLinks(b, trip, legs, plan, people, foreignVisitor)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            links.forEachIndexed { i, (site, url) ->
                                if (i == 0) androidx.compose.material3.Button(onClick = { uri.openUri(url) }, shape = RoundedCornerShape(10.dp)) { Text(site) }
                                else OutlinedButton(onClick = { uri.openUri(url) }, shape = RoundedCornerShape(10.dp)) { Text(site) }
                            }
                        }
                        FilledTonalButton(onClick = { pricing = b }) { Text("I've booked this") }
                    }
                }
            }
        }

        Text("Tickets and confirmations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        if (docs.isEmpty()) {
            Text("Nothing added yet. PDFs and photos are stored encrypted on your phone and work offline.", style = MaterialTheme.typography.bodySmall)
        }
        docs.forEach { d ->
            Card(Modifier.fillMaxWidth().clickable { onOpenDoc(d.id) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DocKind.fromName(d.kind).emoji)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, maxLines = 1, fontWeight = FontWeight.Medium)
                        Text(DocKind.fromName(d.kind).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        OutlinedButton(onClick = { attach("", DocKind.BOOKING) }, modifier = Modifier.fillMaxWidth()) { Text("📎 Add a PDF or photo") }
    }

    pricing?.let { b ->
        var text by remember(b.key) { mutableStateOf(b.actualPaise?.let { Money.toUpiAmount(it).removeSuffix(".00") }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { pricing = null },
            title = { Text("What did you pay?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(b.label, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        label = { Text("Total for everyone (planned ${Money.format(b.plannedPaise)})") },
                        prefix = { Text("₹ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Leave it empty if you'd rather not say; the planned price is used.", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { onSetBooked(b.key, true, Money.parse(text)); pricing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pricing = null }) { Text("Cancel") } },
        )
    }
}

/** Links to book one item: flights, trains and buses for journeys; hotels and hostels for stays. */
private fun bookingLinks(
    b: BookingItem,
    trip: TripEntity?,
    legs: List<LegRow>,
    plan: List<ItineraryDay>,
    people: Int,
    foreignVisitor: Boolean,
): List<Pair<String, String>> {
    val index = b.key.filter { it.isDigit() }.toIntOrNull() ?: return emptyList()
    if (b.key.startsWith("leg")) {
        val leg = legs.getOrNull(index)?.leg ?: return emptyList()
        val from = Destinations.find(leg.fromName) ?: return listOf("Search" to BookingLinks.google("${leg.fromName} to ${leg.toName} tickets"))
        val to = Destinations.find(leg.toName) ?: return listOf("Search" to BookingLinks.google("${leg.fromName} to ${leg.toName} tickets"))
        val date = leg.dateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusDays(7)
        val mode = runCatching { TransportMode.valueOf(leg.mode) }.getOrDefault(TransportMode.BUS_STATE)
        return if (mode == TransportMode.FLIGHT) {
            FlightPlanner.choicesByArrivalAirport(from, to).firstOrNull()
                ?.let { BookingLinks.flightLinks(it.from.code, it.to.code, date) }
                ?: listOf("Search" to BookingLinks.google("flights ${from.name} to ${to.name}"))
        } else {
            BookingLinks.groundLinks(mode, from, to, date, foreignVisitor).take(2)
        }
    }
    val places = trip?.destination.orEmpty().split("→").map { it.trim() }.filter { it.isNotBlank() }
    val place = places.getOrNull(index)?.let { Destinations.find(it) } ?: return emptyList()
    val days = plan.filter { it.place == place.name }
    val checkIn = days.firstOrNull()?.date ?: LocalDate.now().plusDays(7)
    val checkOut = checkIn.plusDays(days.size.coerceAtLeast(1).toLong())
    val rooms = (people + 1) / 2
    return listOf(
        "Booking.com" to BookingLinks.bookingCom(place, checkIn, checkOut, people, rooms),
        "Google Hotels" to BookingLinks.googleHotels(place, hostels = false),
        "Hostels" to BookingLinks.googleHotels(place, hostels = true),
    )
}
