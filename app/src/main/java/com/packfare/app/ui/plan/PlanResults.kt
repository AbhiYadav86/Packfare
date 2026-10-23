@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packfare.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packfare.app.BuildConfig
import com.packfare.app.domain.BudgetSuggestion
import com.packfare.app.domain.Destinations
import com.packfare.app.domain.FlightPlanner
import com.packfare.app.domain.ItineraryDay
import com.packfare.app.domain.LegEstimate
import com.packfare.app.domain.LegKind
import com.packfare.app.domain.Money
import com.packfare.app.domain.Pace
import com.packfare.app.domain.Theme
import com.packfare.app.domain.TransportMode
import com.packfare.app.domain.TravelStyle
import com.packfare.app.fares.BookingLinks
import com.packfare.app.ui.components.MapPin
import com.packfare.app.ui.components.PackfareMap
import com.packfare.app.ui.components.PinColors
import com.packfare.app.ui.theme.HeroGradient
import java.time.LocalDate

private enum class ResultTab(val label: String) { ITINERARY("Itinerary"), GETTING_THERE("Getting there"), STAYS("Stays"), BUDGET("Budget") }

@Composable
internal fun ResultsPage(vm: PlanTripViewModel, onCreated: (Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pickingLeg by remember { mutableStateOf<LegEstimate?>(null) }
    var editingTravellers by remember { mutableStateOf(false) }
    val s = vm.suggestion
    val search = vm.currentSearch()

    Scaffold(
        topBar = {
            Column(Modifier.background(HeroGradient).statusBarsPadding()) {
                Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::editSearch) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to search", tint = Color.White)
                    }
                    Column(Modifier.weight(1f).clickable { vm.editSearch() }) {
                        Text(search.title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(search.subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { editingTravellers = true }) { Text("Edit", color = Color.White) }
                }
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ) {
                    ResultTab.entries.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label, maxLines = 1) })
                    }
                }
            }
        },
        bottomBar = { TotalBar(vm, s, onCreate = { vm.create(onCreated) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (vm.stops.isEmpty()) {
                Text("No stops yet. Add one below or go back and search again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (ResultTab.entries[tab]) {
                ResultTab.ITINERARY -> ItineraryTab(vm, onPickLeg = { pickingLeg = it })
                ResultTab.GETTING_THERE -> GettingThereTab(vm, s, onPickLeg = { pickingLeg = it })
                ResultTab.STAYS -> StaysTab(vm, s)
                ResultTab.BUDGET -> BudgetTab(vm, s)
            }
        }
    }

    pickingLeg?.let { leg ->
        ModePickerDialog(
            leg = leg,
            onDismiss = { pickingLeg = null },
            onPick = { mode -> vm.chooseMode(leg.from, leg.to, mode); pickingLeg = null },
        )
    }
    if (editingTravellers) TravellersDialog(vm, onDismiss = { editingTravellers = false })
}

/** Always-visible total and the main action, like a checkout bar. */
@Composable
private fun TotalBar(vm: PlanTripViewModel, s: BudgetSuggestion?, onCreate: () -> Unit) {
    Surface(tonalElevation = 6.dp, shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (s != null) {
                    Text("${Money.format(s.perPersonPaise)} per person", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        (if (s.people > 1) "Group of ${s.people}: ${Money.format(s.groupTotalPaise)} · " else "") +
                            "${s.days} nights · fares included",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Add a stop to see prices", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = onCreate, enabled = vm.canCreate, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)) {
                Text("Create trip", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// =====================================================================
// Itinerary tab: route, map, stops, day by day
// =====================================================================

@Composable
private fun ItineraryTab(vm: PlanTripViewModel, onPickLeg: (LegEstimate) -> Unit) {
    PlanMap(vm)
    StopsSection(vm, onPickLeg)
    ItinerarySection(vm.itinerary, onLoadIdeas = { vm.loadHighlights(allowNetwork = true) })
    SuggestAgainCard(vm)
}

@Composable
private fun SuggestAgainCard(vm: PlanTripViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    var showRegions by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨ Suggest a different route", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Show") }
            }
            if (!open) return@Column
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nights", Modifier.weight(1f))
                Stepper("−", enabled = vm.suggestNights > 2) { vm.changeSuggestNights(-1) }
                Text("${vm.suggestNights}", Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Stepper("+") { vm.changeSuggestNights(+1) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Theme.entries.forEach { t ->
                    FilterChip(selected = t in vm.suggestThemes, onClick = { vm.toggleTheme(t) }, label = { Text("${t.emoji} ${t.label}") })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pace.entries.forEach { p ->
                    FilterChip(selected = vm.pace == p, onClick = { vm.pace = p }, label = { Text("${p.label} (${p.nightsPerStop}n/stop)") })
                }
            }
            TextButton(onClick = { showRegions = !showRegions }) {
                Text(if (vm.suggestRegions.isEmpty()) "Regions: anywhere within reach" else "Regions: ${vm.suggestRegions.joinToString()}")
            }
            if (showRegions) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Destinations.regions.forEach { r ->
                        FilterChip(selected = r in vm.suggestRegions, onClick = { vm.toggleRegion(r) }, label = { Text(r) })
                    }
                }
            }
            Button(onClick = { vm.suggestItinerary() }, modifier = Modifier.fillMaxWidth()) { Text("Suggest my route") }
            vm.suggestMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PlanMap(vm: PlanTripViewModel) {
    val stopNames = vm.stops.map { it.destination.name }.toSet()
    val hubPins = remember(stopNames) {
        Destinations.all.filter { it.name !in stopNames }.map { d ->
            MapPin(d.wikivoyage, d.lat, d.lon, PinColors.faded, label = d.name, radius = if (d.hub) 4.5f else 3f)
        }
    }
    val stopPins = vm.stops.mapIndexedNotNull { i, s ->
        val d = s.destination
        if (d.lat.isNaN() || d.lon.isNaN()) null
        else MapPin(d.wikivoyage, d.lat, d.lon, PinColors.stop, label = d.name, number = i + 1, radius = 10f)
    }
    val origin = vm.origin
    val homePin = origin?.let { MapPin("home:${it.name}", it.lat, it.lon, Color(0xFF1A73E8), label = "🏠 ${it.name}", radius = 8f) }
    val line = buildList {
        homePin?.let { add(it.lat to it.lon) }
        addAll(stopPins.map { it.lat to it.lon })
        if (vm.returnHome && homePin != null && stopPins.isNotEmpty()) add(homePin.lat to homePin.lon)
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        PackfareMap(
            pins = hubPins + listOfNotNull(homePin) + stopPins,
            route = line,
            myLocation = vm.myLocation,
            focus = line.ifEmpty { null },
            onPinClick = { id -> if (!id.startsWith("home:")) vm.toggleStopFromMap(id) },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
    }
    Text(
        "Tap grey dots to add stops, numbered ones to remove them",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StopsSection(vm: PlanTripViewModel, onPickLeg: (LegEstimate) -> Unit) {
    val legs = vm.suggestion?.legs.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your route", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        vm.origin?.takeIf { vm.stops.isNotEmpty() }?.let { o -> Text("🏠 ${o.name}", fontWeight = FontWeight.Medium) }
        vm.stops.forEachIndexed { i, stop ->
            val incoming = legs.firstOrNull { it.to.name == stop.destination.name && it.kind != LegKind.RETURN }
            if (i > 0 || incoming?.kind == LegKind.OUTBOUND) LegLine(incoming, onPickLeg)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stop.destination.name, fontWeight = FontWeight.Medium, maxLines = 1)
                        val live = if (stop.liveSamples > 0) " · live prices" else ""
                        Text(
                            "≈ ${Money.format(stop.costs.forStyle(vm.style).totalPaise)}/day$live",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stop.destination.fewAtms) Text("💵 Few ATMs: carry cash", style = MaterialTheme.typography.bodySmall)
                        stop.destination.permitNote?.let { Text("📜 Permit needed", style = MaterialTheme.typography.bodySmall) }
                    }
                    Stepper("−", enabled = stop.nights > 1) { vm.changeNights(i, -1) }
                    Text("${stop.nights}n", Modifier.width(34.dp), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    Stepper("+") { vm.changeNights(i, +1) }
                    if (i > 0) IconButton(onClick = { vm.moveUp(i) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                    IconButton(onClick = { vm.removeStop(i) }) { Icon(Icons.Filled.Close, contentDescription = "Remove stop") }
                }
            }
        }
        legs.firstOrNull { it.kind == LegKind.RETURN }?.let { back ->
            LegLine(back, onPickLeg)
            Text("🏠 ${back.to.name}", fontWeight = FontWeight.Medium)
        }
        OutlinedTextField(
            value = vm.stopQuery,
            onValueChange = { vm.stopQuery = it },
            label = { Text("Add a stop") },
            placeholder = { Text("Search places") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        vm.stopSuggestions.forEach { d -> SuggestionRow(d) { vm.addStop(d) } }
        if (vm.stopQuery.trim().length >= 2) {
            OutlinedButton(onClick = vm::addCustomStop) { Text("Add \"${vm.stopQuery.trim()}\"") }
        }
    }
}

@Composable
private fun LegLine(leg: LegEstimate?, onPick: (LegEstimate) -> Unit) {
    if (leg == null) {
        Text("↓  No fare estimate for this hop", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp))
        return
    }
    val o = leg.option
    val night = if (o.overnight && leg.savedStayPaise > 0) " · 🌙 saves ${Money.format(leg.savedStayPaise)}" else if (o.overnight) " · 🌙 overnight" else ""
    Text(
        "↓  ${o.mode.emoji} ${o.mode.label} · ${if (leg.liveFlightPrice) "LIVE " else "≈ "}${Money.format(o.farePaise)} · ${formatHours(o.hours)}$night  ✎",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(leg) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun ItinerarySection(days: List<ItineraryDay>, onLoadIdeas: () -> Unit) {
    if (days.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🗓️ Day by day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            days.forEach { d ->
                Row {
                    Text("Day ${d.day}", Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(d.title + (d.date?.let { " · ${it.format(shortDate)}" } ?: ""), fontWeight = FontWeight.Medium)
                        d.travel?.let { t ->
                            Text(
                                "${t.option.mode.emoji} ${t.from.name} → ${t.to.name} · ${formatHours(t.option.hours)} · " +
                                    "${if (t.liveFlightPrice) "" else "≈ "}${Money.format(t.option.farePaise)}" +
                                    if (t.option.overnight) " · overnight" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        d.highlights.forEach { h -> Text("• $h", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (days.none { it.highlights.isNotEmpty() }) {
                TextButton(onClick = onLoadIdeas) { Text("Fill days with ideas from the guide (needs internet)") }
            }
        }
    }
}

// =====================================================================
// Getting there tab: every leg with its options and where to book
// =====================================================================

@Composable
private fun GettingThereTab(vm: PlanTripViewModel, s: BudgetSuggestion?, onPickLeg: (LegEstimate) -> Unit) {
    if (s == null || s.legs.isEmpty()) {
        Text(
            if (vm.origin == null) "Add where you're starting from (Edit search) to see fares there and back." else "No journeys yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    com.packfare.app.ui.components.TravelPrefChips(vm.travelPref, vm::chooseTravelPref, title = "Preferred way to travel")
    LiveFlightsBar(vm, s)
    val dates = vm.legDates(s)
    s.legs.forEachIndexed { i, leg -> LegCard(vm, leg, dates[i], onPickLeg) }
    Text(
        "Links open each site with your route and date filled in. Prices marked ≈ are Packfare estimates; " +
            "LIVE prices are real fares found recently.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LiveFlightsBar(vm: PlanTripViewModel, s: BudgetSuggestion) {
    if (s.legs.none { FlightPlanner.plans(it.from, it.to).isNotEmpty() }) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("✈️ Live flight prices", style = MaterialTheme.typography.titleSmall)
                Text(
                    vm.flightMessage ?: if (vm.liveFlightsAvailable) "Fetch real fares for your dates." else
                        "Off until a Travelpayouts token is added (see README). Estimates shown.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (vm.checkingFlights) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else OutlinedButton(onClick = vm::checkLiveFlights) { Text("Check") }
        }
    }
}

@Composable
private fun LegCard(vm: PlanTripViewModel, leg: LegEstimate, date: LocalDate, onPickLeg: (LegEstimate) -> Unit) {
    val uri = LocalUriHandler.current
    val o = leg.option
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (leg.kind) { LegKind.OUTBOUND -> "Getting there"; LegKind.RETURN -> "Heading home"; LegKind.BETWEEN -> "Next stop" } +
                    " · ${date.format(shortDate)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("${leg.from.name} → ${leg.to.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.mode.emoji, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.mode.label, fontWeight = FontWeight.Medium)
                    Text(
                        formatHours(o.hours) + (if (o.overnight) " · overnight" else "") +
                            (if (leg.savedStayPaise > 0) " · saves ${Money.format(leg.savedStayPaise)} stay" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text((if (leg.liveFlightPrice) "" else "≈ ") + Money.format(o.farePaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (leg.liveFlightPrice) "LIVE · per person" else "per person", style = MaterialTheme.typography.labelSmall)
                }
            }
            o.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (leg.alternatives.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    leg.alternatives.forEach { a ->
                        FilterChip(
                            selected = a.mode == o.mode,
                            onClick = { vm.chooseMode(leg.from, leg.to, a.mode) },
                            label = { Text("${a.mode.emoji} ≈${Money.format(a.farePaise)}") },
                        )
                    }
                }
            }

            HorizontalDivider()
            if (o.mode == TransportMode.FLIGHT) {
                // One row of sites per sensible airport pair (e.g. Kullu–Bhuntar and Chandigarh).
                FlightPlanner.choicesByArrivalAirport(leg.from, leg.to).take(2).forEach { plan ->
                    Text(
                        "${plan.from.code} → ${plan.to.code}" + (plan.via?.let { " via ${it.code}" } ?: "") +
                            " · air ≈${Money.format(plan.airPaise)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    val marker = BuildConfig.TRAVELPAYOUTS_MARKER.ifBlank { null }
                    val aviasales = BookingLinks.aviasalesLink(plan.from.code, plan.to.code, date, marker)
                    val links = BookingLinks.flightLinks(plan.from.code, plan.to.code, date)
                    LinkRow(if (marker != null) listOf(aviasales) + links else links + aviasales) { uri.openUri(it) }
                }
            } else {
                Text(if (vm.foreignVisitor) "Book or check seats (12Go works without an Indian phone)" else "Book or check seats",
                    style = MaterialTheme.typography.labelLarge)
                LinkRow(BookingLinks.groundLinks(o.mode, leg.from, leg.to, date, vm.foreignVisitor)) { uri.openUri(it) }
            }
            TextButton(onClick = { onPickLeg(leg) }) { Text("Compare all options") }
        }
    }
}

@Composable
private fun LinkRow(links: List<Pair<String, String>>, open: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEachIndexed { i, (site, url) ->
            if (i == 0) Button(onClick = { open(url) }, shape = RoundedCornerShape(10.dp)) { Text(site) }
            else OutlinedButton(onClick = { open(url) }, shape = RoundedCornerShape(10.dp)) { Text(site) }
        }
    }
}

// =====================================================================
// Stays tab: where you sleep each night, price by comfort level, links
// =====================================================================

@Composable
private fun StaysTab(vm: PlanTripViewModel, s: BudgetSuggestion?) {
    val uri = LocalUriHandler.current
    Text("Where you'll stay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TravelStyle.entries.forEach { st -> FilterChip(selected = vm.style == st, onClick = { vm.style = st }, label = { Text(st.label) }) }
    }
    val sharing = vm.sharingRoom ?: (vm.people > 1)
    if (vm.style.rooms) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = sharing, onCheckedChange = { vm.sharingRoom = it })
            Text(if (sharing) "Sharing rooms (twin share)" else "Room to myself", style = MaterialTheme.typography.bodySmall)
        }
    }
    var checkIn = vm.startDate
    vm.stops.forEachIndexed { i, stop ->
        val nights = stop.nights
        val inDate = checkIn
        val outDate = inDate.plusDays(nights.toLong())
        checkIn = outDate
        val rooms = if (!vm.style.rooms) vm.people else if (sharing) (vm.people + 1) / 2 else vm.people
        val estimate = s?.stops?.getOrNull(i)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stop.destination.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${inDate.format(shortDate)} – ${outDate.format(shortDate)} · $nights night${if (nights == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Price per night at each comfort level; the chosen one is highlighted.
                TravelStyle.entries.forEach { st ->
                    val price = if (st.rooms) stop.costs.roomPaise(st) else stop.costs.forStyle(st).stayPaise
                    val unit = if (st.rooms) "room/night" else "bed/night"
                    val chosen = st == vm.style
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { vm.style = st }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(st.label, Modifier.weight(1f), fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
                        Text("≈ ${Money.format(price)} $unit", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                estimate?.let {
                    Text(
                        "Your stay here: ≈ ${Money.format(it.daily.stayPaise * nights)} per person" +
                            if (vm.style.rooms) (if (sharing) " (twin share)" else " (own room)") else " (dorm bed)",
                        fontWeight = FontWeight.Medium,
                    )
                }
                LinkRow(
                    listOf(
                        "Booking.com" to BookingLinks.bookingCom(stop.destination, inDate, outDate, vm.people, rooms),
                        (if (vm.style.rooms) "Google Hotels" else "Hostels") to BookingLinks.googleHotels(stop.destination, hostels = !vm.style.rooms),
                    ),
                ) { uri.openUri(it) }
            }
        }
    }
    Text(
        "Room prices are typical for the town; Wikivoyage listings refine them when you've opened the place in Explore.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// =====================================================================
// Budget tab: full breakdown, your budget, trip name
// =====================================================================

@Composable
private fun BudgetTab(vm: PlanTripViewModel, s: BudgetSuggestion?) {
    if (s != null) {
        SuggestionCard(s)
        OutlinedTextField(
            value = vm.budgetText,
            onValueChange = vm::onBudgetChange,
            label = { Text(if (vm.people > 1) "Trip budget (whole group, incl. travel)" else "Trip budget (incl. travel)") },
            prefix = { Text("₹ ") },
            singleLine = true,
            isError = vm.budgetPaise <= 0,
            supportingText = { Text("≈ ${Money.format(vm.budgetPaise / s.days.coerceAtLeast(1))} a day · you can change this any time") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (vm.budgetOverride != null) TextButton(onClick = vm::resetBudget) { Text("Use the suggested budget") }
    }
    OutlinedTextField(
        value = vm.name,
        onValueChange = { vm.name = it },
        label = { Text("Trip name") },
        placeholder = { Text("Himachal loop") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    OutlinedTextField(
        value = vm.myName,
        onValueChange = { vm.myName = it },
        label = { Text("Your name") },
        supportingText = { Text("Add travel buddies after creating the trip.") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
}

@Composable
private fun SuggestionCard(s: BudgetSuggestion) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroGradient).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Suggested budget per person", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            Text(Money.format(s.perPersonPaise), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("${s.days} nights · ≈ ${Money.format(s.perPersonPerDayPaise)}/day · ${s.style.label}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
            Spacer(Modifier.height(4.dp))
            Text("Getting around", color = Color.White, fontWeight = FontWeight.SemiBold)
            if (s.toAndFromPaise > 0) WhiteLine("✈️🚆 Getting there & back", s.toAndFromPaise)
            if (s.betweenStopsPaise > 0) WhiteLine("🚌 Between stops", s.betweenStopsPaise)
            WhiteLine("🛺 Local rides", s.localPaise)
            WhiteLine("= All transport", s.allTransportPaise, bold = true)
            Spacer(Modifier.height(6.dp))
            Text("On the ground", color = Color.White, fontWeight = FontWeight.SemiBold)
            WhiteLine("🛏️ Stay", s.stayPaise)
            WhiteLine("🍛 Food", s.foodPaise)
            WhiteLine("🎟️ Sights & activities", s.activitiesPaise)
            WhiteLine("🧯 Buffer (SIM, laundry, surprises)", s.bufferPaise)
            if (s.overnightSavingsPaise > 0) WhiteLine("🌙 Saved by overnight journeys", -s.overnightSavingsPaise)
            if (s.people > 1) {
                Spacer(Modifier.height(4.dp))
                Text("Group of ${s.people}: ${Money.format(s.groupTotalPaise)}", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Fares are estimates (≈) unless marked LIVE.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun WhiteLine(label: String, paise: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(Money.format(paise), color = Color.White, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}
