@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.packfare.app.ui.trip

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.packfare.app.domain.HotelBudget
import com.packfare.app.domain.Journey
import com.packfare.app.domain.LegOption
import com.packfare.app.domain.LegOptions
import com.packfare.app.fares.BookingLinks
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.packfare.app.data.LegEntity
import com.packfare.app.domain.Destinations
import com.packfare.app.ui.components.MapPin
import com.packfare.app.ui.components.PackfareMap
import com.packfare.app.ui.components.PinColors
import androidx.compose.foundation.layout.height
import com.packfare.app.domain.FareEstimator
import com.packfare.app.domain.Money
import com.packfare.app.domain.RouteOption
import com.packfare.app.domain.TransportMode
import com.packfare.app.ui.plan.formatHours
import com.packfare.app.ui.theme.BalanceColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val legDate = DateTimeFormatter.ofPattern("EEE, d MMM")

/** Feature 5 (route legs) + 6 (overnight saver). */
@Composable
internal fun RouteTab(
    state: TripDetailState,
    foreignVisitor: Boolean,
    onEditLeg: (LegEntity) -> Unit,
    onFindFares: (LegEntity) -> Unit,
    onChoose: (LegEntity, LegOption) -> Unit,
) {
    if (state.legs.isEmpty()) {
        CenteredMessage(
            "🗺️",
            "No route yet",
            "Add the hops of your trip (Delhi → Manali by night bus…) to see fares, overnight savings and spend per leg.",
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            // Stops in order: first leg's origin, then every destination.
            val names = listOf(state.legs.first().leg.fromName) + state.legs.map { it.leg.toName }
            val pins = names.mapIndexedNotNull { i, n ->
                Destinations.find(n)?.let { d -> MapPin(d.wikivoyage, d.lat, d.lon, PinColors.stop, label = d.name, number = i + 1, radius = 10f) }
            }
            if (pins.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    PackfareMap(
                        pins = pins,
                        route = pins.map { it.lat to it.lon },
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                }
            }
        }
        item {
            val fares = state.legs.sumOf { it.leg.farePaise }
            Text(
                "Getting around: ≈ ${Money.format(fares)} per person" +
                    if (state.overnightSavingsPaise > 0) " · 🌙 saves ${Money.format(state.overnightSavingsPaise)}" else "",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        items(state.legs, key = { it.leg.id }) { row ->
            LegCard(
                row, people = state.members.size.coerceAtLeast(1), style = state.style, foreignVisitor = foreignVisitor,
                onEdit = { onEditLeg(row.leg) }, onFindFares = { onFindFares(row.leg) }, onChoose = { onChoose(row.leg, it) },
            )
        }
        item {
            Text(
                "Fares are estimates (≈) from distance and typical Indian prices; the booking sites show today's price. Tap \"Use this\" to put an option in your plan, or Edit to enter what you actually paid.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegCard(
    row: LegRow,
    people: Int,
    style: com.packfare.app.domain.TravelStyle,
    foreignVisitor: Boolean,
    onEdit: () -> Unit,
    onFindFares: () -> Unit,
    onChoose: (LegOption) -> Unit,
) {
    val leg = row.leg
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val from = Destinations.find(leg.fromName)
    val to = Destinations.find(leg.toName)
    val options = remember(leg.fromName, leg.toName, people) {
        if (from != null && to != null) LegOptions.forLeg(from, to, people) else emptyMap()
    }
    val date = leg.dateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusDays(7)
    val current = Journey.of(TransportMode.fromName(leg.mode))
    var journey by remember(leg.id) { mutableStateOf(current.takeIf { it in options } ?: options.keys.firstOrNull() ?: current) }
    val defaultCap = LegOptions.defaultCapPaise(leg.farePaise, options)
    var capText by remember(leg.id, defaultCap) { mutableStateOf((defaultCap / 100).toString()) }
    val cap = (Money.parse(capText) ?: defaultCap).coerceAtLeast(0L)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${leg.fromName} → ${leg.toName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val bits = listOfNotNull(
                        leg.dateEpochDay?.let { date.format(legDate) },
                        "$people ${if (people == 1) "traveller" else "travellers"}",
                        if (leg.farePaise > 0) "in plan: ${TransportMode.fromName(leg.mode).emoji} ≈ ${Money.format(leg.farePaise)} each" else null,
                    )
                    Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }

            if (options.isEmpty()) {
                Text(
                    "No estimates for this hop (one of the places isn't in Packfare's list). Tap Edit to enter the fare yourself.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = capText, onValueChange = { capText = it }, singleLine = true,
                    label = { Text("My budget per person for this hop") }, prefix = { Text("₹ ") },
                    supportingText = { Text("Change it to see which options fit.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Journey.entries.filter { it in options }.forEach { j ->
                        val fitCount = options.getValue(j).count { HotelBudget.fits(it.perPersonPaise, cap) }
                        FilterChip(
                            selected = journey == j, onClick = { journey = j },
                            label = { Text("${j.emoji} ${j.label}" + if (fitCount > 0) " ✓" else "") },
                        )
                    }
                }
                val list = options[journey].orEmpty()
                val (fit, over) = list.partition { HotelBudget.fits(it.perPersonPaise, cap) }
                Text(
                    "${fit.size} of ${list.size} ${journey.label.lowercase()} options fit ${Money.format(cap)} each",
                    style = MaterialTheme.typography.titleSmall,
                )
                (fit + over).forEach { o ->
                    val fits = o in fit
                    val chosen = TransportMode.fromName(leg.mode) == o.mode && leg.farePaise == o.perPersonPaise
                    OptionRow(o, fits, chosen, people, over = (o.perPersonPaise - cap).takeIf { !fits }, onUse = { onChoose(o) })
                }
                Text("Book or check today's price", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                val links: List<Pair<String, String>> = when (journey) {
                    Journey.FLIGHT -> options[Journey.FLIGHT].orEmpty().firstOrNull { it.fromAirport != null }
                        ?.let { BookingLinks.flightLinks(it.fromAirport!!, it.toAirport!!, date) }.orEmpty()
                    Journey.TAXI -> BookingLinks.taxiLinks(from!!, to!!, date)
                    Journey.TRAIN -> BookingLinks.groundLinks(TransportMode.TRAIN_AC, from!!, to!!, date, foreignVisitor)
                    Journey.BUS -> BookingLinks.groundLinks(TransportMode.BUS_AC_SLEEPER, from!!, to!!, date, foreignVisitor)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    links.forEachIndexed { i, (label, url) ->
                        if (i == 0) androidx.compose.material3.Button(onClick = { uri.openUri(url) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) { Text(label) }
                        else androidx.compose.material3.OutlinedButton(onClick = { uri.openUri(url) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) { Text(label) }
                    }
                }
                if (journey == Journey.TRAIN && foreignVisitor) {
                    Text("12Go sells Indian train tickets as a PDF with no IRCTC account or Indian phone number.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (leg.overnight) {
                Text("🌙 Overnight · saves ≈ ${Money.format(row.savedStayPaise)} on stays", style = MaterialTheme.typography.bodySmall)
            }
            if (row.spentPaise > 0) {
                Text("Spent on this leg: ${Money.format(row.spentPaise)}", style = MaterialTheme.typography.bodySmall)
            }
            val notes = listOfNotNull(
                if (leg.nightsAtDestination > 0) "${leg.nightsAtDestination} nights in ${leg.toName}" else null,
                if (row.destinationFewAtms) "💵 few ATMs" else null,
                if (row.permitNote != null) "📜 permit needed" else null,
            )
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onFindFares, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("🔎 Live fares across sites")
            }
        }
    }
}

@Composable
private fun OptionRow(o: LegOption, fits: Boolean, chosen: Boolean, people: Int, over: Long?, onUse: () -> Unit) {
    val bg = if (fits) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(o.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (fits) FontWeight.SemiBold else FontWeight.Normal)
            Text(
                listOfNotNull(formatHours(o.hours), if (o.overnight) "🌙 overnight" else null,
                    if (people > 1) "group ≈ ${Money.format(o.groupPaise)}" else null).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            o.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                if (fits) "✓ Fits your budget" else "${Money.format(over ?: 0L)} over",
                style = MaterialTheme.typography.labelSmall,
                color = if (fits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("≈ ${Money.format(o.perPersonPaise)}", fontWeight = FontWeight.SemiBold)
            if (chosen) Text("In your plan ✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            else TextButton(onClick = onUse, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text("Use this") }
        }
    }
}

/** Add or edit a leg. Suggests cheapest/fastest/best options when both ends are known places. */
@Composable
internal fun LegDialog(
    initial: LegEntity?,
    defaultFrom: String,
    onDismiss: () -> Unit,
    onSave: (LegEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var from by remember { mutableStateOf(initial?.fromName ?: defaultFrom) }
    var to by remember { mutableStateOf(initial?.toName ?: "") }
    var mode by remember { mutableStateOf(TransportMode.fromName(initial?.mode ?: TransportMode.TRAIN_AC.name)) }
    var fare by remember { mutableStateOf(initial?.farePaise?.takeIf { it > 0 }?.let { Money.toUpiAmount(it).removeSuffix(".00") } ?: "") }
    var hours by remember { mutableStateOf(initial?.hours) }
    var overnight by remember { mutableStateOf(initial?.overnight ?: false) }
    var nights by remember { mutableStateOf((initial?.nightsAtDestination ?: 2).toString()) }

    val choices = remember(from, to) {
        val a = Destinations.find(from)
        val b = Destinations.find(to)
        if (a != null && b != null) FareEstimator.choices(a, b) else null
    }
    fun apply(o: RouteOption) {
        mode = o.mode
        fare = Money.toUpiAmount(o.farePaise).removeSuffix(".00")
        hours = o.hours
        overnight = o.overnight
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a leg" else "Edit leg") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = from, onValueChange = { from = it }, label = { Text("From") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = to, onValueChange = { to = it }, label = { Text("To") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                choices?.let { c ->
                    Text("Suggested", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cheapest" to c.cheapest, "Fastest" to c.fastest, "Best" to c.best)
                            .distinctBy { it.second }
                            .forEach { (label, o) ->
                                AssistChip(
                                    onClick = { apply(o) },
                                    label = { Text("$label: ${o.mode.emoji} ${Money.format(o.farePaise)} · ${formatHours(o.hours)}") },
                                )
                            }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Classes below 3AC only show up when editing an older leg that used one.
                    TransportMode.entries.filter { it.offered || it == mode }.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text("${m.emoji} ${m.label}") })
                    }
                }
                OutlinedTextField(
                    value = fare, onValueChange = { fare = it }, label = { Text("Fare per person") },
                    prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = nights, onValueChange = { nights = it.filter(Char::isDigit).take(2) },
                    label = { Text("Nights at destination") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = overnight, onCheckedChange = { overnight = it })
                    Text("Overnight journey (sleep on board)")
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete this leg", color = BalanceColors.negative) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = from.isNotBlank() && to.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: LegEntity(tripId = 0, position = 0, fromName = "", toName = "", mode = "", farePaise = 0))
                            .copy(
                                fromName = from.trim(),
                                toName = to.trim(),
                                mode = mode.name,
                                farePaise = Money.parse(fare) ?: 0L,
                                hours = hours,
                                overnight = overnight,
                                nightsAtDestination = nights.toIntOrNull() ?: 0,
                            ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
