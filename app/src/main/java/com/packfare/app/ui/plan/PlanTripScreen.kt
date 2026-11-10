@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packfare.app.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packfare.app.domain.Destination
import com.packfare.app.domain.LegEstimate
import com.packfare.app.domain.Money
import com.packfare.app.domain.Pace
import com.packfare.app.domain.Theme
import com.packfare.app.domain.TravelStyle
import com.packfare.app.domain.TripSearch
import com.packfare.app.ui.explore.CityPickerDialog
import com.packfare.app.ui.theme.HeroGradient
import com.packfare.app.util.rememberLocationRequest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
internal val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Plan a trip, travel-website style: a search box first (from, to, dates, travellers), then results
 * in tabs (Itinerary, Getting there, Stays, Budget) with the total always visible at the bottom.
 */
@Composable
fun PlanTripScreen(vm: PlanTripViewModel, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    when (vm.phase) {
        PlanPhase.SEARCH -> SearchPage(vm, onBack)
        PlanPhase.RESULTS -> {
            val back = { if (vm.openedOnResults) onBack() else vm.editSearch() }
            BackHandler { back() }
            ResultsPage(vm, onCreated, onBack = back)
        }
    }
}

// =====================================================================
// Search page
// =====================================================================

@Composable
private fun SearchPage(vm: PlanTripViewModel, onBack: () -> Unit) {
    var editingProfile by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Gradient hero; the search card overlaps its lower edge.
            Box(Modifier.fillMaxWidth().height(230.dp).background(HeroGradient))
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    if (vm.profileName.isNotBlank()) "Hi ${vm.profileName.substringBefore(' ')}," else "Hi there,",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Where to next?",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Budget trips across India · fares, stays and day plans in one place",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                SearchCard(vm)
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vm.draft?.let { d -> DraftCard(d, onResume = { vm.applySearch(d, resume = true) }, onDiscard = vm::discardDraft) }

            if (vm.recentSearches.isNotEmpty()) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                vm.recentSearches.forEach { s ->
                    RecentSearchRow(s, onClick = { vm.applySearch(s, resume = false) }, onRemove = { vm.removeRecent(s) })
                }
            }

            ProfileCard(vm, onEdit = { editingProfile = true })

            Text(
                "Every budget includes getting there and back. Trains from 3AC, AC buses, flights with airport transfers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (editingProfile) {
        ProfileDialog(
            name = vm.profileName,
            travellers = vm.usualTravellers,
            visitor = vm.foreignVisitor,
            onSave = { n, t, v -> vm.saveProfile(n, t, v); editingProfile = false },
            onDismiss = { editingProfile = false },
        )
    }
}

@Composable
private fun SearchCard(vm: PlanTripViewModel) {
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var editingTravellers by remember { mutableStateOf(false) }
    val requestLocation = rememberLocationRequest(vm::startNearMe)

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // From
            SearchField(
                icon = "🏠",
                label = "From",
                value = vm.origin?.name ?: "Your city",
                muted = vm.origin == null,
                onClick = { pickingFrom = true },
                trailing = { TextButton(onClick = requestLocation) { Text("📍 Near me") } },
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 40.dp)) {
                Text("Return here at the end", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = vm.returnHome, onCheckedChange = { vm.returnHome = it })
            }
            vm.locationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 40.dp)) }
            HorizontalDivider()

            // To
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Text("📍", fontSize = 20.sp, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text("To", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.stops.forEachIndexed { i, s ->
                            InputChip(
                                selected = false,
                                onClick = { vm.removeStop(i) },
                                label = { Text(s.destination.name) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", Modifier.size(16.dp)) },
                            )
                        }
                        AssistChip(onClick = { pickingTo = true }, label = { Text(if (vm.stops.isEmpty()) "+ Add a destination" else "+ Add another") })
                    }
                    if (vm.stops.isEmpty()) {
                        Text(
                            "Or leave it empty and Packfare suggests a route near you. What are you into?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Theme.entries.forEach { t ->
                                FilterChip(selected = t in vm.suggestThemes, onClick = { vm.toggleTheme(t) }, label = { Text("${t.emoji} ${t.label}") })
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pace.entries.forEach { p ->
                                FilterChip(selected = vm.pace == p, onClick = { vm.pace = p }, label = { Text("${p.label} pace") })
                            }
                        }
                    }
                }
            }
            HorizontalDivider()

            // Dates
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🗓️", fontSize = 20.sp, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { pickingDate = true }) {
                    Text("Dates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val end = vm.startDate.plusDays(vm.suggestNights.toLong())
                    Text(
                        "${vm.startDate.format(shortDate)} – ${end.format(shortDate)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Stepper("−", enabled = vm.suggestNights > 1) { vm.changeTripNights(-1) }
                Text("${vm.suggestNights}n", Modifier.width(40.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                Stepper("+") { vm.changeTripNights(+1) }
            }
            HorizontalDivider()

            // Travellers & style
            SearchField(
                icon = "🧳",
                label = "Travellers & style",
                value = "${vm.people} traveller${if (vm.people == 1) "" else "s"} · ${vm.style.label} · ${vm.travelPref.emoji} ${vm.travelPref.label}",
                onClick = { editingTravellers = true },
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::search,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (vm.stops.isEmpty()) "✨ Suggest my trip" else "Search trips", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            vm.suggestMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (pickingFrom) {
        CityPickerDialog(title = "Where are you starting from?", onPick = { vm.pickOrigin(it); pickingFrom = false }, onDismiss = { pickingFrom = false })
    }
    if (pickingTo) {
        CityPickerDialog(title = "Where do you want to go?", onPick = { vm.addStop(it); pickingTo = false }, onDismiss = { pickingTo = false })
    }
    if (pickingDate) {
        TripDatePicker(vm, onDone = { pickingDate = false })
    }
    if (editingTravellers) {
        TravellersDialog(vm, onDismiss = { editingTravellers = false })
    }
}

@Composable
private fun SearchField(
    icon: String,
    label: String,
    value: String,
    onClick: () -> Unit,
    muted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
internal fun TripDatePicker(vm: PlanTripViewModel, onDone: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = vm.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDone,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { vm.startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                onDone()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

/** Who's going, how comfortably, and how they'd like to get around. */
@Composable
internal fun TravellersDialog(vm: PlanTripViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Travellers & style") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Travellers", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Stepper("−", enabled = vm.people > 1) { vm.changePeople(-1) }
                    Text("${vm.people}", Modifier.width(32.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    Stepper("+") { vm.changePeople(+1) }
                }
                Text("Where you'll stay", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TravelStyle.entries.forEach { s ->
                        FilterChip(selected = vm.style == s, onClick = { vm.style = s }, label = { Text(s.label) })
                    }
                }
                Text(vm.style.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.style.rooms) {
                    val sharing = vm.sharingRoom ?: (vm.people > 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sharing, onCheckedChange = { vm.sharingRoom = it })
                        Text(if (sharing) "Sharing rooms (twin share)" else "Room to myself", style = MaterialTheme.typography.bodySmall)
                    }
                }
                com.packfare.app.ui.components.TravelPrefChips(vm.travelPref, vm::chooseTravelPref)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun DraftCard(d: TripSearch, onResume: () -> Unit, onDiscard: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Continue planning", style = MaterialTheme.typography.labelLarge)
            Text(d.name.ifBlank { d.title }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(d.subtitle, style = MaterialTheme.typography.bodySmall)
            Row {
                Button(onClick = onResume) { Text("Resume") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDiscard) { Text("Discard") }
            }
        }
    }
}

@Composable
private fun RecentSearchRow(s: TripSearch, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🕘", fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
        }
    }
}

@Composable
private fun ProfileCard(vm: PlanTripViewModel, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your traveller profile", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (vm.profileName.isBlank()) "Add your name, usual group size and whether you're visiting India."
                    else "${vm.profileName} · usually ${vm.usualTravellers} traveller${if (vm.usualTravellers == 1) "" else "s"} · " +
                        if (vm.foreignVisitor) "visiting India (12Go links)" else "Indian traveller (IRCTC links)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onEdit) { Text(if (vm.profileName.isBlank()) "Set up" else "Edit") }
        }
    }
}

@Composable
private fun ProfileDialog(name: String, travellers: Int, visitor: Boolean, onSave: (String, Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var t by remember { mutableStateOf(travellers) }
    var v by remember { mutableStateOf(visitor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your traveller profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = n,
                    onValueChange = { n = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Usually travelling as", Modifier.weight(1f))
                    Stepper("−", enabled = t > 1) { t-- }
                    Text("$t", Modifier.width(32.dp), textAlign = TextAlign.Center)
                    Stepper("+", enabled = t < 20) { t++ }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("I'm visiting India")
                        Text(
                            "Shows 12Go for trains and buses (no IRCTC account or Indian phone needed).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = v, onCheckedChange = { v = it })
                }
                Text(
                    "Saved on this phone only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n, t, v) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// =====================================================================
// Shared bits (also used by the results tabs)
// =====================================================================

@Composable
internal fun SuggestionRow(d: Destination, onClick: () -> Unit) {
    Text(
        "📍 ${d.name}  ·  ${d.region}",
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
internal fun ModePickerDialog(leg: LegEstimate, onDismiss: () -> Unit, onPick: (com.packfare.app.domain.TransportMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${leg.from.name} → ${leg.to.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("How do you want to travel? The budget updates instantly.", style = MaterialTheme.typography.bodySmall)
                leg.alternatives.forEach { o ->
                    val selected = o.mode == leg.option.mode
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { onPick(o.mode) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(o.mode.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.mode.label, fontWeight = FontWeight.Medium)
                            Text(
                                formatHours(o.hours) + (if (o.overnight) " · overnight" else "") + (o.note?.let { "\n$it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(Money.format(o.farePaise), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun Stepper(symbol: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Text(symbol, fontSize = 18.sp)
    }
}

fun formatHours(h: Double): String {
    val whole = h.toInt()
    val mins = ((h - whole) * 60).toInt()
    return if (whole == 0) "${mins}m" else if (mins < 10) "${whole}h" else "${whole}h ${mins}m"
}
