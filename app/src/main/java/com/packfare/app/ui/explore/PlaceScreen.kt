@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.packfare.app.ui.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import com.packfare.app.domain.LegKind
import com.packfare.app.ui.plan.formatHours
import com.packfare.app.util.rememberLocationRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.packfare.app.domain.DailyCosts
import com.packfare.app.domain.Money
import com.packfare.app.domain.TravelStyle
import com.packfare.app.domain.Destinations
import com.packfare.app.explore.PlaceInfo
import com.packfare.app.ui.components.MapPin
import com.packfare.app.ui.components.PackfareMap
import com.packfare.app.ui.components.PinColors
import androidx.compose.runtime.remember
import com.packfare.app.explore.WikivoyageClient
import com.packfare.app.ui.theme.HeroGradient
import java.text.DateFormat
import java.util.Date

@Composable
fun PlaceScreen(
    vm: PlaceViewModel,
    title: String,
    onBack: () -> Unit,
    onPlan: (place: String, from: String?, nights: Int, style: TravelStyle) -> Unit,
    onOpenPlace: (String) -> Unit,
    onFindFares: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.place?.title ?: title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.load(force = true) }, enabled = !vm.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh from Wikivoyage")
                    }
                },
            )
        },
    ) { padding ->
        val place = vm.place
        when {
            place != null -> PlaceContent(vm, place, Modifier.padding(padding), onPlan, onOpenPlace, onFindFares)
            vm.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Reading the Wikivoyage guide…")
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.error ?: "Something went wrong")
                    Button(onClick = { vm.load(force = false) }) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun PlaceContent(
    vm: PlaceViewModel,
    place: PlaceInfo,
    modifier: Modifier,
    onPlan: (place: String, from: String?, nights: Int, style: TravelStyle) -> Unit,
    onOpenPlace: (String) -> Unit,
    onFindFares: (String) -> Unit,
) {
    val uri = LocalUriHandler.current
    val costs = place.costs
    val stats = place.stats

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero: what a day costs here
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroGradient).padding(20.dp),
        ) {
            Column {
                Text(
                    place.local?.region ?: "India",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(place.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "≈ ${Money.format(costs.forStyle(TravelStyle.BACKPACKER).totalPaise)} a day for backpackers",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Hotels: budget ≈${Money.format(costs.roomPaise(TravelStyle.FLASHPACKER))} · " +
                        "3★ ≈${Money.format(costs.roomPaise(TravelStyle.COMFORT))} · " +
                        "4★ ≈${Money.format(costs.roomPaise(TravelStyle.PREMIUM))} a room a night",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (place.lat != null && place.lon != null) {
            val here = MapPin("here", place.lat, place.lon, PinColors.stop, label = place.title)
            val around = remember(place.title) {
                Destinations.nearest(place.lat, place.lon, limit = 6)
                    .filter { it.first.name != place.local?.name }
                    .map { (d, _) -> MapPin(d.wikivoyage, d.lat, d.lon, PinColors.faded, label = d.name, radius = 4f) }
            }
            Card(Modifier.fillMaxWidth()) {
                PackfareMap(
                    pins = around + here,
                    focus = listOf(place.lat to place.lon) + around.take(2).map { it.lat to it.lon },
                    onPinClick = { id -> if (id != "here") onOpenPlace(id) },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
        }

        TripCostCard(vm, onFindFares)

        FlightsCard(vm)

        place.local?.let { local ->
            if (local.fewAtms || local.permitNote != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Before you go", style = MaterialTheme.typography.titleSmall)
                        if (local.fewAtms) Text("💵 ATMs are scarce or unreliable here. Carry enough cash for your whole stay.")
                        local.permitNote?.let { Text("📜 $it") }
                    }
                }
            }
        }

        ActivitiesCard(place)

        val cheapSleeps = stats.sleep.sortedBy { it.pricePaise }.take(5)
        val cheapEats = stats.eat.sortedBy { it.pricePaise }.take(5)
        if (cheapSleeps.isNotEmpty() || cheapEats.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (cheapSleeps.isNotEmpty()) {
                        Text("Cheapest places to sleep", style = MaterialTheme.typography.titleSmall)
                        cheapSleeps.forEach { PriceLine(it.name + if (it.isDorm) " (dorm)" else "", it.pricePaise) }
                    }
                    if (cheapEats.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Cheap eats", style = MaterialTheme.typography.titleSmall)
                        cheapEats.forEach { PriceLine(it.name, it.pricePaise) }
                    }
                }
            }
        }

        if (place.summary.isNotBlank()) {
            Text(place.summary.take(900) + if (place.summary.length > 900) "…" else "", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = { onPlan(place.local?.name ?: place.title, vm.origin?.name, vm.nights, vm.style) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Plan a trip here (with itinerary)")
        }
        OutlinedButton(onClick = { onFindFares(place.local?.name ?: place.title) }, modifier = Modifier.fillMaxWidth()) {
            Text("✈️🚆🚌  Find the cheapest way to get here")
        }
        OutlinedButton(
            onClick = { uri.openUri(WikivoyageClient.articleUrl(place.title)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Read the full guide on Wikivoyage") }

        Text(
            "Guide text and prices from Wikivoyage contributors, licensed CC BY-SA 4.0. " +
                (if (place.fromCache) "Saved on your phone " else "Updated ") +
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(place.fetchedAt)) + ".",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by-sa/4.0/") }) {
            Text("About the licence", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun BreakdownRows(day: DailyCosts) {
    PriceLine("🛏️ Stay", day.stayPaise)
    PriceLine("🍛 Food", day.foodPaise)
    PriceLine("🛺 Local transport", day.localTransportPaise)
    PriceLine("🎟️ Sights & activities", day.activitiesPaise)
}

@Composable
private fun PriceLine(label: String, paise: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(Money.format(paise), style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Your trip here": fares there and back plus everything on the ground, for N nights. */
@Composable
private fun TripCostCard(vm: PlaceViewModel, onFindFares: (String) -> Unit) {
    val requestLocation = rememberLocationRequest(vm::nearMe)
    val plan = vm.tripBudget
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your trip here, all in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            StyleSelector(vm)
            OutlinedTextField(
                value = vm.originText,
                onValueChange = { vm.originText = it },
                label = { Text("Travelling from") },
                placeholder = { Text("Your city") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            vm.originSuggestions.forEach { d ->
                Text(
                    "📍 ${d.name}  ·  ${d.region}",
                    Modifier.fillMaxWidth().clickable { vm.pickOrigin(d) }.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = requestLocation) { Text("📍 Near me") }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = { vm.changeNights(-1) }, enabled = vm.nights > 1) { Text("−") }
                Text("${vm.nights} nights", Modifier.padding(horizontal = 8.dp))
                FilledTonalIconButton(onClick = { vm.changeNights(+1) }) { Text("+") }
            }
            com.packfare.app.ui.components.TravelPrefChips(vm.travelPref, vm::chooseTravelPref, title = "Getting there by")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.returnHome, onCheckedChange = { vm.returnHome = it })
                Text("Return trip", style = MaterialTheme.typography.bodySmall)
                if (vm.style.rooms) {
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = vm.sharingRoom, onCheckedChange = { vm.sharingRoom = it })
                    Text("Sharing a room", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (plan != null) {
                if (plan.legs.isEmpty() && vm.origin == null) {
                    Text(
                        "Add where you're travelling from to include fares in the budget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                plan.legs.forEach { leg ->
                    Text(
                        (if (leg.kind == LegKind.RETURN) "Back: " else "There: ") + "${leg.from.name} → ${leg.to.name}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        leg.alternatives.forEach { o ->
                            FilterChip(
                                selected = o.mode == leg.option.mode,
                                onClick = { vm.chooseMode(leg.from, leg.to, o.mode) },
                                label = {
                                    val live = leg.liveFlightPrice && o.mode == leg.option.mode
                                    Text("${o.mode.emoji} ${if (live) "" else "≈"}${Money.format(o.farePaise)} · ${formatHours(o.hours)}")
                                },
                            )
                        }
                    }
                    leg.option.note?.let { Text("↳ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider()
                PriceRow("✈️🚆 Fares there & back", plan.intercityPaise)
                PriceRow(
                    "🛏️ Stay (${vm.nights} nights" + when {
                        !vm.style.rooms -> ", dorm)"
                        vm.sharingRoom -> ", twin share)"
                        else -> ", own room)"
                    },
                    plan.stayPaise,
                )
                PriceRow("🍛 Food", plan.foodPaise)
                PriceRow("🛺 Local rides", plan.localPaise)
                PriceRow("🎟️ Sights & activities", plan.activitiesPaise)
                PriceRow("🧯 Buffer", plan.bufferPaise)
                if (plan.overnightSavingsPaise > 0) PriceRow("🌙 Overnight saving", -plan.overnightSavingsPaise)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth()) {
                    Text("Total per person", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(Money.format(plan.perPersonPaise), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onFindFares(vm.place?.local?.name ?: vm.place?.title.orEmpty()) }) {
                        Text("All fares")
                    }
                }
                vm.fareNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            vm.place?.stats?.let { PriceBasisNote(it) }
        }
    }
}

/**
 * Pick where you'll stay. Each option shows what a night's room costs here and the all-in daily cost,
 * so choosing a hotel class and seeing the estimate are one step, right at the top of the trip card.
 */
@Composable
private fun StyleSelector(vm: PlaceViewModel) {
    val costs = vm.place?.costs ?: return
    Text("Where you'll stay", style = MaterialTheme.typography.labelLarge)
    TravelStyle.entries.forEach { style ->
        val selected = vm.style == style
        val day = costs.forStyle(style, solo = !vm.sharingRoom)
        Surface(
            onClick = { vm.style = style },
            shape = RoundedCornerShape(12.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(style.label, fontWeight = FontWeight.Medium)
                    Text(style.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (style.rooms) {
                        Text(
                            "Room ≈${Money.format(costs.roomPaise(style))}/night",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(day.totalPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("a day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Where the numbers above come from: Wikivoyage listings where the guide has prices, typical prices otherwise. */
@Composable
private fun PriceBasisNote(stats: com.packfare.app.explore.PriceStats) {
    Text(
        if (stats.sampleCount > 0) {
            buildString {
                append("Based on ${stats.sampleCount} prices in Wikivoyage listings")
                stats.dormMedianPaise?.let { append(" · dorm beds ~${Money.format(it)}") }
                stats.midRoomMedianPaise?.let { append(" · mid-range rooms ~${Money.format(it)}") }
                stats.splurgeRoomMedianPaise?.let { append(" · splurge rooms ~${Money.format(it)}") }
                stats.mealMedianPaise?.let { append(" · meals ~${Money.format(it)}") }
                append(". Missing data uses typical prices.")
            }
        } else {
            "No prices listed in the guide yet, so these are typical prices for this kind of place."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Things to do here and what each costs per person. Checked ideas first, then guide listings, then typical ones. */
@Composable
private fun ActivitiesCard(place: PlaceInfo) {
    val ideas = place.activities
    if (ideas.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎟️ Things to do & what they cost", style = MaterialTheme.typography.titleSmall)
            ideas.forEach { a ->
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            a.costLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    val sub = listOfNotNull(a.note.takeIf { it.isNotBlank() }, if (a.estimated) "estimated from the kind of place" else if (a.source == com.packfare.app.domain.IdeaSource.TYPICAL) "typical price" else null)
                        .joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Per person, approximate. \"Plan a trip here\" spreads these over your days. " +
                    "Foreign visitors often pay more at parks and monuments.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "✈️ Flights from Bengaluru": one block per sensible arrival airport (Kullu: Kullu–Bhuntar and
 * Chandigarh), each with a door-to-door estimate, live fares once fetched, and links to four flight sites.
 */
@Composable
private fun FlightsCard(vm: PlaceViewModel) {
    val origin = vm.origin ?: return
    val plans = vm.flightPlans
    val uri = LocalUriHandler.current
    val loaded = vm.flights?.takeIf { vm.flightsFrom == origin.name }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✈️ Flights from ${origin.name}", style = MaterialTheme.typography.titleSmall)
            if (plans.isEmpty()) {
                Text(
                    "No airport within reach of here or ${origin.name}. Trains and buses are in the trip above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            plans.forEachIndexed { i, plan ->
                if (i > 0) HorizontalDivider()
                val estimate = plan.toOption()
                val live = loaded?.plans?.firstOrNull { it.plan.to.code == plan.to.code && it.plan.from.code == plan.from.code }?.live.orEmpty()
                Text(
                    (if (i == 0) "Best: " else "Or: ") + "${plan.from.name} → ${plan.to.name} (${plan.to.code})",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(plan.note(), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Air ≈${Money.format(plan.airPaise)}" +
                                (if (plan.transferPaise > 0) " + transfers ≈${Money.format(plan.transferPaise)}" else "") +
                                (if (plan.to.regional || plan.from.regional) " · small airport, few flights" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("≈ ${Money.format(estimate.farePaise)}", fontWeight = FontWeight.SemiBold)
                        Text(formatHours(estimate.hours), style = MaterialTheme.typography.bodySmall)
                    }
                }
                live.take(3).forEach { q ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = q.url != null) { q.url?.let(uri::openUri) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "LIVE ${q.title}" + (q.departure?.let { " · $it" } ?: ""),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(Money.format(q.pricePaise), fontWeight = FontWeight.SemiBold)
                    }
                }
                // Each site quotes its own price and none offers a free public price feed, so show the
                // range these sites usually fall in, from our estimate. "Find live fares" shows real ones.
                val low = plan.airPaise * 85 / 100
                val high = plan.airPaise * 125 / 100
                Text(
                    "Expect ≈${Money.format(low)}–${Money.format(high).removePrefix("₹")} for the flight on Google Flights, " +
                        "Skyscanner, ixigo or MakeMyTrip (estimate; fares move with date and demand).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.packfare.app.fares.BookingLinks.flightLinks(plan.from.code, plan.to.code, vm.travelDate).forEach { (site, url) ->
                        TextButton(onClick = { uri.openUri(url) }) { Text(site, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            loaded?.notice?.let { Text("ℹ️ $it", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = vm::findFlights, enabled = !vm.loadingFlights) {
                Text(if (vm.loadingFlights) "Searching…" else if (loaded == null) "Find live fares" else "Refresh live fares")
            }
            Text(
                "Estimates include getting to and from the airport. Prices change daily; check a site before you pay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PriceRow(label: String, paise: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(Money.format(paise), style = MaterialTheme.typography.bodyMedium)
    }
}
